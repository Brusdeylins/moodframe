package art.moodframe.service.generation;

import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.service.provider.GenerationJobInfo;
import art.moodframe.service.provider.ImageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The flow of a video model ({@link art.moodframe.model.GenerationModel#isVideoModel()}): the provider answers once the
 * video exists, so there is no polling; the video is downloaded with the {@link ImageProvider#downloadHeaders headers}
 * of the provider and saved as .mp4. The placeholder is stored only at the end, so a crash leaves no pending row. The
 * chat entry is changed on the FX thread through {@link FxModel}, the download runs on the I/O executor.
 *
 * @since 2.1.3
 */
public class VideoFlow {

    private static final Logger logger = LoggerFactory.getLogger(VideoFlow.class);

    private final ChatEntryTracker tracker;
    private final ImageFileStore imageFiles;
    private final Executor io;

    /**
     * Creates the flow.
     *
     * @param tracker    keeps the chat entry in step with its video and stores it
     * @param imageFiles downloads and writes the video
     * @param io         runs the download
     */
    public VideoFlow(ChatEntryTracker tracker, ImageFileStore imageFiles, Executor io) {
        this.tracker = tracker;
        this.imageFiles = imageFiles;
        this.io = io;
    }

    /**
     * Generates the video of an entry and stores it in its placeholder.
     *
     * @param provider   the provider of the video model
     * @param request    the request with the video parameters
     * @param chatEntry  the entry with a placeholder for the video
     * @param imageIndex the index of the video in the entry (always 0)
     */
    public void submit(ImageProvider provider, ImageGenerationRequest request, ChatEntry chatEntry, int imageIndex) {
        logger.info("Starting video generation: model={}, resolution={}, duration={}s",
                request.getModel(), request.getVideoResolution(), request.getVideoDuration());

        /*  Queued behind the creation of the placeholders  */
        FxModel.change(() -> {
            GeneratedImage placeholder = ChatEntryTracker.placeholder(chatEntry, imageIndex);
            if (placeholder == null) {
                logger.error("No placeholder found for video at index {}", imageIndex);
                return;
            }
            placeholder.setPrompt(request.getPrompt());
            placeholder.setModel(request.getModel());
            placeholder.setAspectRatio(request.getAspectRatio() != null ? request.getAspectRatio() : "16:9");
            placeholder.setBaseCost(request.getBaseCostPerImage() != null ? request.getBaseCostPerImage() : 0.0);
            placeholder.setIsVideo(true);
            placeholder.setVideoDurationSeconds(request.getVideoDuration() != null ? request.getVideoDuration() : 0);
            placeholder.setHasAudio(true);
            placeholder.setVideoResolution(request.getVideoResolution());
            tracker.notifyImageUpdated(placeholder);
        });

        provider.submitGenerationRequest(request)
                .thenAccept((GenerationJobInfo jobInfo) -> {
                    if (jobInfo.isFailed()) {
                        tracker.failImage(chatEntry, imageIndex, ChatEntryTracker.errorText(jobInfo, chatEntry));
                        return;
                    }
                    FxModel.change(() -> {
                        GeneratedImage placeholder = ChatEntryTracker.placeholder(chatEntry, imageIndex);
                        if (placeholder == null) {
                            logger.error("No placeholder found for video at index {}", imageIndex);
                            return;
                        }
                        placeholder.setGenerationJobId(jobInfo.getJobId());
                        placeholder.setDownloadStatus(GeneratedImage.DownloadStatus.DOWNLOADING);
                        tracker.notifyImageUpdated(placeholder);
                        String videoUrl = jobInfo.getVideoDownloadUrl();
                        download(provider, videoUrl, provider.downloadHeaders(videoUrl), placeholder, chatEntry,
                                request);
                    });
                })
                .exceptionally((Throwable throwable) -> {
                    logger.error("Video generation failed", throwable);
                    tracker.failImage(chatEntry, imageIndex, ChatEntryTracker.errorText(throwable, chatEntry));
                    return null;
                });
    }

    /**
     * Downloads a video through the {@link ImageFileStore} and applies it to its placeholder.
     *
     * @param provider       the provider of the video
     * @param videoUrl       the URL to download the video from
     * @param headers        the headers the provider needs for its files
     * @param generatedImage the placeholder to update with the file
     * @param chatEntry      the entry of the video
     * @param request        the request, for the cost
     */
    private void download(ImageProvider provider, String videoUrl, Map<String, String> headers,
            GeneratedImage generatedImage, ChatEntry chatEntry, ImageGenerationRequest request) {
        CompletableFuture.runAsync(() -> {
            try {
                ImageFileStore.StoredImage stored = imageFiles.downloadVideo(videoUrl, headers, generatedImage);

                /*  The video is bound to the UI, so it is changed on the FX thread, then stored  */
                FxModel.change(() -> {
                    stored.applyTo(generatedImage);
                    generatedImage.setDownloadedAt(LocalDateTime.now());
                    if (!provider.usesSeeds())
                        generatedImage.setSeed(0);
                    generatedImage.setBaseCost(request.getBaseCostPerImage() != null
                            ? request.getBaseCostPerImage() : 0.0);

                    tracker.saveImage(generatedImage);
                    logger.info("Video saved: {} ({} bytes, {}s, audio={})", stored.filename(), stored.size(),
                            generatedImage.getVideoDurationSeconds(), generatedImage.hasAudio());

                    tracker.notifyEntryUpdated(chatEntry);
                    tracker.notifyImageUpdated(generatedImage);
                    tracker.refresh(chatEntry);
                });
            }
            catch (Exception e) {
                String reason;
                if (e instanceof ImageFileStore.HttpStatusException status) {
                    logger.error("Failed to download video: HTTP {}", status.getStatusCode());
                    reason = "HTTP " + status.getStatusCode();
                }
                else {
                    logger.error("Failed to download video", e);
                    reason = e.getMessage();
                }
                String error = ChatEntryTracker.errorText(chatEntry, e, "generation.error.video.download", reason);
                FxModel.change(() -> {
                    generatedImage.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
                    generatedImage.setErrorMessage(error);
                    tracker.saveImage(generatedImage);
                    tracker.notifyImageUpdated(generatedImage);
                    tracker.refresh(chatEntry);
                });
            }
        }, io);
    }
}
