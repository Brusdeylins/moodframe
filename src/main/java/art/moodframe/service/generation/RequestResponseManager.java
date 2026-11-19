package art.moodframe.service.generation;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.service.provider.ImageProvider;
import art.moodframe.service.provider.ProviderRegistry;
import art.moodframe.util.ImageEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Dispatches the request of a generation to the flow of its provider: it encodes an attached image, checks the
 * request, creates the placeholders of the images and hands them to the {@link VideoFlow} for a video model, to the
 * {@link SynchronousImageFlow} for a provider that answers with the image and to the {@link JobPollingFlow} for one
 * that answers with a job. The {@link ChatEntryTracker} tells the listeners about the progress.
 *
 * <p><strong>Threading:</strong> the dispatch runs on the I/O executor, the chat entry and its images are changed on
 * the FX thread through {@link FxModel}, and the database writes run on the worker of the tracker after the change they
 * store ({@link ChatEntryTracker#saveImage}), so a generation of the MCP server needs the FX thread only for the change
 * of the objects the user interface is bound to. The composition root hands in the infrastructure: the tracker on the
 * store thread of the {@link GenerationWorkers}, the file store with its download client, the I/O executor.</p>
 *
 * @since 1.0.0
 */
public class RequestResponseManager implements RequestResponseService {

    private static final Logger logger = LoggerFactory.getLogger(RequestResponseManager.class);

    private final ProviderRegistry providers;
    private final ChatEntryTracker tracker;
    private final GenerationWorkers workers;
    private final Executor io;

    private final JobPollingFlow pollingFlow;
    private final SynchronousImageFlow imageFlow;
    private final VideoFlow videoFlow;

    /**
     * Creates the manager with its flows.
     *
     * @param config     the application configuration (timeout and FLUX.2 safety tolerance of the polled jobs)
     * @param providers  the providers the requests go to
     * @param tracker    keeps the entries in step with their images and stores them
     * @param imageFiles writes the images and videos, downloads them with its client
     * @param workers    the polling and the database writes; shut down with this manager
     * @param io         runs the blocking work: submission, downloads, file writing
     */
    public RequestResponseManager(ApplicationConfig config, ProviderRegistry providers, ChatEntryTracker tracker,
            ImageFileStore imageFiles, GenerationWorkers workers, Executor io) {
        this.providers = providers;
        this.tracker = tracker;
        this.workers = workers;
        this.io = io;
        this.pollingFlow = new JobPollingFlow(config, tracker, imageFiles, workers.polling(), io);
        this.imageFlow = new SynchronousImageFlow(tracker, imageFiles, io);
        this.videoFlow = new VideoFlow(tracker, imageFiles, io);
    }

    @Override
    public void addEntryListener(Consumer<ChatEntry> listener) {
        tracker.addEntryListener(listener);
    }

    @Override
    public void removeEntryListener(Consumer<ChatEntry> listener) {
        tracker.removeEntryListener(listener);
    }

    @Override
    public void addImageListener(Consumer<GeneratedImage> listener) {
        tracker.addImageListener(listener);
    }

    @Override
    public void removeImageListener(Consumer<GeneratedImage> listener) {
        tracker.removeImageListener(listener);
    }

    @Override
    public void setOnSafetyToleranceLowered(IntConsumer callback) {
        pollingFlow.setOnSafetyToleranceLowered(callback);
    }

    @Override
    public CompletableFuture<Void> submitImageGeneration(ImageGenerationRequest request, ChatEntry chatEntry,
            File attachedImageFile) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (attachedImageFile != null)
                    attachImage(request, attachedImageFile);

                /*  The request and its prompt against the limit of the provider, which decides seeds and flow  */
                request.validate();
                ImageProvider provider = providers.forModel(request.getModel());
                provider.checkPromptLength(request);
                int imageCount = request.getImageCount() != null ? request.getImageCount() : 1;

                FxModel.change(() -> addPlaceholders(chatEntry, imageCount, provider, request.getSeed()));

                GenerationModel model = GenerationModel.fromApiModelId(request.getModel());
                if (model != null && model.isVideoModel())
                    videoFlow.submit(provider, request, chatEntry, 0);
                else if (provider.answersWithImage())
                    for (int i = 0; i < imageCount; i++)
                        imageFlow.submit(provider, request, chatEntry, i);
                else
                    for (int i = 0; i < imageCount; i++)
                        pollingFlow.submit(provider, request, chatEntry, i);
            }
            catch (Exception e) {
                logger.error("Failed to submit image generation request", e);
                markFailed(chatEntry, e);
            }
        }, io);
    }

    /*  The entry is processing with a placeholder per image for the UI, with the seed of a provider with seeds: that
        of the user plus the index, else a random one. Call on the FX thread  */
    private void addPlaceholders(ChatEntry chatEntry, int imageCount, ImageProvider provider, Long seed) {
        chatEntry.setStatus(ChatEntry.Status.PROCESSING);
        for (int i = 0; i < imageCount; i++) {
            GeneratedImage.Builder builder = new GeneratedImage.Builder()
                    .chatEntryId(chatEntry.getId())
                    .downloadStatus(GeneratedImage.DownloadStatus.PENDING)
                    .sortOrder(i);
            if (provider.usesSeeds())
                builder.seed(seed != null ? seed + i : Seeds.random(i));
            chatEntry.getImages().add(builder.build());
        }
        tracker.notifyEntryUpdated(chatEntry);
    }

    @Override
    public void markFailed(ChatEntry chatEntry, Throwable failure) {
        tracker.markFailed(chatEntry, ChatEntryTracker.errorText(failure, chatEntry));
    }

    @Override
    public CompletableFuture<Boolean> cancelEntry(ChatEntry chatEntry) {
        return FxModel.changeAndGet(() -> {
            boolean running = tracker.cancel(chatEntry);
            if (running)
                pollingFlow.cancelEntry(chatEntry.getId());
            return running;
        });
    }

    @Override
    public void shutdown() {

        /*  The polling stops, the database writes asked for still end  */
        pollingFlow.cancelAll();
        workers.shutdown();
    }

    /**
     * Encodes an attached image into the request, for a model that takes one.
     *
     * @throws IllegalArgumentException if the model takes no image or the file cannot be encoded
     */
    private void attachImage(ImageGenerationRequest request, File attachedImageFile) {
        try {
            String model = request.getModel();
            ApiProvider provider = providers.providerOf(model);
            providers.get(provider).checkImageInput(model);
            String base64Image = ImageEncoder.encodeImageToBase64(attachedImageFile);
            request.setInputImage(base64Image);
            logger.info("Attached an image to the request: Base64 length {}, model {}, provider {}",
                    base64Image.length(), model, provider);
        }
        catch (Exception e) {
            logger.error("Failed to process attached image: {}", attachedImageFile.getName(), e);
            throw new IllegalArgumentException("Failed to process image attachment: " + e.getMessage(), e);
        }
    }
}
