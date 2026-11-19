package art.moodframe.service.generation;

import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.service.provider.GenerationJobInfo;
import art.moodframe.service.provider.ImageProvider;
import art.moodframe.service.provider.ProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The flow of a generation for a provider that answers with the image ({@link ImageProvider#answersWithImage()},
 * today Gemini and OpenAI): one request per image, the answer decoded into its file and applied to the placeholder of
 * the image. The costs beyond the price per image come from the provider ({@link ImageProvider#extraCosts}). The
 * images of the chat entry are changed on the FX thread through {@link FxModel}, the files are written on the I/O
 * executor, the database writes run on the worker of the {@link ChatEntryTracker}.
 *
 * @since 2.1.3
 */
public class SynchronousImageFlow {

    private static final Logger logger = LoggerFactory.getLogger(SynchronousImageFlow.class);

    private final ChatEntryTracker tracker;
    private final ImageFileStore imageFiles;
    private final Executor io;

    /**
     * Creates the flow.
     *
     * @param tracker    keeps the chat entry in step with its images and stores them
     * @param imageFiles writes the decoded images
     * @param io         runs the decoding and the file writing
     */
    public SynchronousImageFlow(ChatEntryTracker tracker, ImageFileStore imageFiles, Executor io) {
        this.tracker = tracker;
        this.imageFiles = imageFiles;
        this.io = io;
    }

    /**
     * Sends the request of one image and stores the image of the answer in its placeholder.
     *
     * @param provider   the provider of the model, one that answers with the image
     * @param request    the request of all images of the entry
     * @param chatEntry  the entry with a placeholder for the image
     * @param imageIndex the index of the image in the entry
     */
    public void submit(ImageProvider provider, ImageGenerationRequest request, ChatEntry chatEntry, int imageIndex) {
        String name = provider.getProvider().getDisplayName();
        provider.submitGenerationRequest(request)
                .thenAccept((GenerationJobInfo jobInfo) -> {
                    if (jobInfo.isSynchronous() && jobInfo.isFailed()) {

                        /*  Blocked content: the placeholder fails with the reason, a blocked image costs nothing  */
                        logger.info("{} image {} blocked, stored with its error message", name, imageIndex);
                        failImage(chatEntry, imageIndex, ChatEntryTracker.errorText(jobInfo, chatEntry), true);
                    }
                    else if (jobInfo.isSynchronous() && jobInfo.getBase64Image() != null)
                        storeImage(provider, jobInfo, request, chatEntry, imageIndex);
                })
                .exceptionally((Throwable throwable) -> {
                    logger.error("{} generation failed for image index {}", name, imageIndex, throwable);
                    failImage(chatEntry, imageIndex, ChatEntryTracker.errorText(throwable, chatEntry), false);
                    return null;
                });
    }

    /**
     * Marks the placeholder of an image as failed (found by its sort order, as the answers arrive in any order),
     * stores it and settles the entry once all its images are finished.
     *
     * @param free true if the image costs nothing (blocked content)
     */
    private void failImage(ChatEntry chatEntry, int imageIndex, String errorMessage, boolean free) {
        FxModel.change(() -> {
            GeneratedImage img = ChatEntryTracker.placeholder(chatEntry, imageIndex);
            if (img != null) {
                img.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
                img.setErrorMessage(errorMessage);
                if (free)
                    img.setBaseCost(0.0);
                tracker.saveImage(img);
            }
            tracker.refresh(chatEntry);
        });
    }

    /**
     * Stores the image of the answer. The placeholder is a JavaFX object the UI is bound to, so it is only changed on
     * the FX thread; the decoding and the file writing in between run on a worker. The existing placeholder is updated
     * in place to keep the property bindings of the UI.
     */
    private void storeImage(ImageProvider provider, GenerationJobInfo jobInfo, ImageGenerationRequest request,
            ChatEntry chatEntry, int imageIndex) {
        CompletableFuture<GeneratedImage> prepared = FxModel.changeAndGet(() -> {
            GeneratedImage placeholder = ChatEntryTracker.placeholder(chatEntry, imageIndex);
            if (placeholder != null) {
                placeholder.setGenerationJobId(jobInfo.getJobId());
                placeholder.setPrompt(request.getPrompt());
                placeholder.setModel(request.getModel());
                placeholder.setDownloadStatus(GeneratedImage.DownloadStatus.DOWNLOADING);
                placeholder.setAspectRatio(request.getAspectRatio() != null ? request.getAspectRatio() : "1:1");
                placeholder.setBaseCost(request.getBaseCostPerImage() != null ? request.getBaseCostPerImage() : 0.0);
            }
            return placeholder;
        });

        prepared.thenAcceptAsync((GeneratedImage placeholder) -> {
            if (placeholder == null) {
                logger.warn("Placeholder not found for imageIndex={}, cannot update", imageIndex);
                return;
            }

            ImageFileStore.StoredImage stored = null;
            String error = null;
            try {
                stored = imageFiles.writeBase64(jobInfo.getBase64Image(), placeholder);
            }
            catch (Exception e) {
                logger.error("Failed to save the {} image", provider.getProvider().getDisplayName(), e);
                error = ChatEntryTracker.errorText(chatEntry, e, "generation.error.save", e.getMessage());
            }

            ImageFileStore.StoredImage result = stored;
            if (error != null)
                tracker.markFailed(chatEntry, error);
            FxModel.change(() -> {
                if (result != null)
                    applyStoredImage(provider, placeholder, result, jobInfo, request, chatEntry);
                else
                    placeholder.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
                tracker.notifyEntryUpdated(chatEntry);
                tracker.notifyImageUpdated(placeholder);
                tracker.refresh(chatEntry);
            });
        }, io);
    }

    /**
     * Applies a written image file to its placeholder with the costs the provider adds to the price per image, and
     * has it stored. Runs on the FX thread; an image that cannot be stored fails.
     */
    private void applyStoredImage(ImageProvider provider, GeneratedImage generatedImage,
            ImageFileStore.StoredImage stored, GenerationJobInfo jobInfo, ImageGenerationRequest request,
            ChatEntry chatEntry) {
        stored.applyTo(generatedImage);

        /*  A provider without seeds stores none, whatever the placeholder held  */
        if (!provider.usesSeeds())
            generatedImage.setSeed(0);

        double baseCost = request.getBaseCostPerImage() != null ? request.getBaseCostPerImage() : 0.0;
        ImageProvider.ExtraCosts extra = provider.extraCosts(request, jobInfo);
        generatedImage.setBaseCost(baseCost);
        generatedImage.setSearchCost(extra.search());
        generatedImage.setReasoningCost(extra.reasoning());
        generatedImage.setThinkingTokens(jobInfo.getThinkingTokens());
        generatedImage.setThoughtSummary(jobInfo.getThoughtSummary());
        String name = provider.getProvider().getDisplayName();
        if (logger.isInfoEnabled())
            logger.info("{} costs - Base: ${}, Search: ${}, Reasoning: ${}", name, String.format("%.4f", baseCost),
                    String.format("%.4f", extra.search()), String.format("%.4f", extra.reasoning()));

        tracker.saveImage(generatedImage).exceptionally((Throwable failure) -> {
            Throwable cause = ProviderException.unwrap(failure);
            FxModel.change(() -> generatedImage.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED));
            tracker.markFailed(chatEntry, ChatEntryTracker.errorText(chatEntry, cause, "generation.error.save",
                    cause.getMessage()));
            return null;
        });

        logger.info("{} image stored: {} ({}x{}, {}, {} bytes)", name, stored.filename(), generatedImage.getWidth(),
                generatedImage.getHeight(), generatedImage.getFormat(), generatedImage.getFileSizeBytes());
    }
}
