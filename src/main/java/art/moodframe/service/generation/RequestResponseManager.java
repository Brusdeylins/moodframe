package art.moodframe.service.generation;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationCost;
import art.moodframe.model.GenerationJobInfo;
import art.moodframe.model.GenerationModel;
import art.moodframe.config.ApplicationConfig;
import art.moodframe.repository.ChatRepository;
import art.moodframe.service.provider.BflApiService;
import art.moodframe.service.provider.GeminiApiService;
import art.moodframe.service.provider.ImageProvider;
import art.moodframe.service.provider.ProviderRegistry;
import art.moodframe.util.ImageEncoder;
import art.moodframe.net.HttpClients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Central manager for handling the requests of a generation and their answers: submission to the provider of the
 * model, the polling of BFL jobs (see {@link BflGenerationFlow}), download and storage of the images and videos, and
 * the events for the user interface.
 *
 * <p><strong>Threading:</strong> requests, downloads and files run on the I/O executor, the chat entry and its
 * images are changed on the FX thread through {@link FxModel}, and the database writes run on a worker of their own
 * after the change they store ({@link ChatEntryTracker#saveImage}), so a generation of the MCP server needs the FX
 * thread only for the change of the objects the user interface is bound to.</p>
 *
 * @since 1.0.0
 */
public class RequestResponseManager implements RequestResponseService {

    private static final Logger logger = LoggerFactory.getLogger(RequestResponseManager.class);

    private final ApplicationConfig config;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService store;
    private final BflGenerationFlow bflFlow;
    private final ProviderRegistry providers;
    private final Executor io;
    private final Supplier<HttpClient> downloadClient;  // Simple client for downloading images from CDN
    private final ImageFileStore imageFiles;

    private final ChatEntryTracker tracker;

    /**
     * Creates a new RequestResponseManager with the given configuration.
     *
     * @param config         the application configuration
     * @param chatRepository where the entries and images are stored
     * @param providers      the providers the requests go to
     * @param io             runs the blocking work: submission, downloads, file writing
     */
    public RequestResponseManager(ApplicationConfig config, ChatRepository chatRepository, ProviderRegistry providers,
            Executor io) {
        this.config = config;
        this.providers = providers;
        this.io = io;

        // Create HttpClient with corporate proxy and SSL configuration for downloading images
        this.downloadClient = new HttpClients(config).rebuilding(clients -> clients.javaHttp()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
        this.imageFiles = new ImageFileStore(config, downloadClient);
        this.scheduler = Executors.newScheduledThreadPool(2, daemonThreads("generation-poll"));

        /*  One thread for the database writes keeps them in the order of the changes they store  */
        this.store = Executors.newSingleThreadExecutor(daemonThreads("generation-store"));
        this.tracker = new ChatEntryTracker(chatRepository, store);
        this.bflFlow = new BflGenerationFlow(config, providers.get(ApiProvider.BFL, BflApiService.class),
                tracker, imageFiles, scheduler, io);

        logger.debug("RequestResponseManager initialized with timeout: {}s, proxy: {}",
                config.getApiTimeoutSeconds(), config.isProxyEnabled() ? "enabled" : "disabled");
    }

    /*  named daemon threads, so the workers never keep the application alive  */
    private static ThreadFactory daemonThreads(String name) {
        AtomicInteger count = new AtomicInteger();
        return (Runnable runnable) -> {
            Thread thread = new Thread(runnable, name + "-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
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

    /**
     * Sets the callback for a FLUX.2 safety tolerance lowered to the maximum the BFL account allows.
     */
    @Override
    public void setOnSafetyToleranceLowered(IntConsumer callback) {
        bflFlow.setOnSafetyToleranceLowered(callback);
    }

    /**
     * Submits an image generation request to the appropriate provider (BFL or Gemini)
     * with optional image attachment.
     *
     * @param request           the generation request
     * @param chatEntry         the chat entry to update with results
     * @param attachedImageFile optional image file for image-to-image generation
     * @return CompletableFuture that completes when the request is submitted
     */
    @Override
    public CompletableFuture<Void> submitImageGeneration(ImageGenerationRequest request, ChatEntry chatEntry,
            java.io.File attachedImageFile) {

        return CompletableFuture.runAsync(() -> {
            try {
                // Process attached image if provided
                if (attachedImageFile != null) {
                    processAttachedImage(request, attachedImageFile);
                }

                // Validate request
                request.validate();

                // The provider of the model decides about seeds and the flow
                ImageProvider provider = providers.forModel(request.getModel());

                // Update chat entry status and create placeholder images (ensure on FX thread)
                FxModel.change(() -> {
                    chatEntry.setStatus(ChatEntry.Status.PROCESSING);

                    // Create placeholder images for immediate UI feedback with pre-generated seeds
                    // Both BFL and Gemini now support multiple images (1-4)
                    int imageCount = request.getImageCount() != null ? request.getImageCount() : 1;

                    for (int i = 0; i < imageCount; i++) {
                        // Generate seed for each placeholder image (BFL only, Gemini doesn't use seeds)
                        Long placeholderSeed = null;
                        if (provider.usesSeeds()) {
                            if (request.getSeed() != null) {
                                placeholderSeed = request.getSeed() + i;
                            } else {
                                placeholderSeed = bflFlow.generateRandomSeed(i);
                            }
                        }

                        GeneratedImage.Builder builder = new GeneratedImage.Builder()
                                .chatEntryId(chatEntry.getId())
                                .downloadStatus(GeneratedImage.DownloadStatus.PENDING)
                                .sortOrder(i);

                        // Only set seed for a provider with seeds (BFL, not Gemini)
                        if (placeholderSeed != null) {
                            builder.seed(placeholderSeed);
                        }

                        GeneratedImage placeholderImage = builder.build();
                        chatEntry.getImages().add(placeholderImage);
                    }

                    tracker.notifyEntryUpdated(chatEntry);
                });

                // Route to appropriate provider - both now support multiple images

                int imageCount = request.getImageCount() != null ? request.getImageCount() : 1;

                GenerationModel model = GenerationModel.fromApiModelId(request.getModel());
                if (model != null && model.isOmniModel()) {
                    // Gemini Omni: synchronous video generation (always 1 video per request)
                    handleOmniGeneration(request, chatEntry, 0);
                } else if (provider.answersWithImage()) {
                    // Gemini, OpenAI: Synchronous flow - submit multiple parallel requests, one image per request
                    for (int i = 0; i < imageCount; i++) {
                        handleSynchronousGeneration(provider.submitGenerationRequest(request), request, chatEntry, i,
                                provider.getProvider().getDisplayName());
                    }
                } else {
                    // BFL: Asynchronous polling flow
                    for (int i = 0; i < imageCount; i++) {
                        bflFlow.submit(request, chatEntry, i);
                    }
                }

            } catch (Exception e) {
                logger.error("Failed to submit image generation request", e);
                String error = ChatEntryTracker.errorText(e, chatEntry);
                FxModel.change(() -> {
                    chatEntry.setStatus(ChatEntry.Status.FAILED);
                    chatEntry.setErrorMessage(error);
                    tracker.notifyEntryUpdated(chatEntry);
                });
            }
        }, io);
    }

    @Override
    public CompletableFuture<Boolean> cancelEntry(ChatEntry chatEntry) {
        return FxModel.read(() -> {
            boolean running = tracker.cancel(chatEntry);
            if (running)
                bflFlow.cancelEntry(chatEntry.getId());
            return running;
        });
    }

    @Override
    public void shutdown() {
        bflFlow.cancelAll();
        scheduler.shutdown();
        store.shutdown();

        /*  The polling stops, the database writes asked for still end  */
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!store.awaitTermination(5, TimeUnit.SECONDS)) {
                store.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            store.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Handles the result of a provider call that answers with the finished image (Gemini, OpenAI).
     *
     * @param submission the running provider call
     * @param request    the image generation request
     * @param chatEntry  the chat entry to update
     * @param imageIndex the index of the image in the batch (0-based)
     * @param provider   the provider name for the log
     */
    private void handleSynchronousGeneration(CompletableFuture<GenerationJobInfo> submission,
            ImageGenerationRequest request, ChatEntry chatEntry, int imageIndex, String provider) {
        submission
            .thenAccept(jobInfo -> {
                if (jobInfo.isSynchronous() && jobInfo.isFailed()) {
                    // Content was blocked - the placeholder fails with the reason, a blocked image costs nothing
                    logger.info("{} image {} blocked, stored with its error message", provider, imageIndex);
                    failImage(chatEntry, imageIndex, jobInfo.getErrorMessage(), true);
                } else if (jobInfo.isSynchronous() && jobInfo.getBase64Image() != null) {
                    // NO POLLING NEEDED - Image is already ready!
                    storeSynchronousImage(jobInfo, request, chatEntry, imageIndex);
                }
            })
            .exceptionally(throwable -> {
                logger.error("{} generation failed for image index {}", provider, imageIndex, throwable);
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
            GeneratedImage img = placeholder(chatEntry, imageIndex);
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
     * Handles Gemini Omni video generation. The API call is synchronous and returns when the video exists,
     * so there is no polling: generate → download video from URL → save as .mp4.
     * The placeholder is only persisted at the end (like Gemini images), a crash leaves no pending row behind.
     *
     * @param request    the image generation request with video parameters
     * @param chatEntry  the chat entry to update
     * @param imageIndex the index of the video in the batch (always 0 for Omni)
     */
    private void handleOmniGeneration(ImageGenerationRequest request, ChatEntry chatEntry, int imageIndex) {
        logger.info("Starting Gemini Omni video generation: model={}, resolution={}, duration={}s",
                request.getModel(), request.getVideoResolution(), request.getVideoDuration());

        // Queued behind the placeholder creation of submitImageGeneration()
        FxModel.change(() -> {
            GeneratedImage placeholder = placeholder(chatEntry, imageIndex);
            if (placeholder == null) {
                logger.error("No placeholder found for Omni video at index {}", imageIndex);
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

        providers.get(ApiProvider.GOOGLE_GEMINI, GeminiApiService.class).submitOmniGenerationRequest(request)
            .thenAccept(jobInfo -> {
                if (jobInfo.isFailed()) {
                    failOmniVideo(chatEntry, imageIndex, jobInfo.getErrorMessage());
                    return;
                }

                FxModel.change(() -> {
                    GeneratedImage placeholder = placeholder(chatEntry, imageIndex);
                    if (placeholder == null) {
                        logger.error("No placeholder found for Omni video at index {}", imageIndex);
                        return;
                    }

                    placeholder.setGenerationJobId(jobInfo.getJobId());
                    placeholder.setDownloadStatus(GeneratedImage.DownloadStatus.DOWNLOADING);
                    tracker.notifyImageUpdated(placeholder);
                    downloadVideoFromUrl(jobInfo.getVideoDownloadUrl(), placeholder, chatEntry, request);
                });
            })
            .exceptionally(throwable -> {
                logger.error("Omni generation failed", throwable);
                failOmniVideo(chatEntry, imageIndex, ChatEntryTracker.errorText(throwable, chatEntry));
                return null;
            });
    }

    /*  the placeholder of an image by its sort order (not its list position); call on the FX thread  */
    private static GeneratedImage placeholder(ChatEntry chatEntry, int imageIndex) {
        for (GeneratedImage img : chatEntry.getImages()) {
            if (img.getSortOrder() == imageIndex) {
                return img;
            }
        }
        return null;
    }

    /**
     * Marks the placeholder of an Omni video as failed and stores it, so the error stays visible.
     */
    private void failOmniVideo(ChatEntry chatEntry, int imageIndex, String errorMessage) {
        FxModel.change(() -> {
            GeneratedImage img = placeholder(chatEntry, imageIndex);
            if (img != null) {
                img.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
                img.setErrorMessage(errorMessage);
                img.setBaseCost(0.0);
                tracker.saveImage(img);
                tracker.notifyImageUpdated(img);
            }
            tracker.refresh(chatEntry);
        });
    }

    /**
     * Downloads a video file from a URL and saves it as .mp4.
     *
     * @param videoUrl       the URL to download the video from
     * @param generatedImage the GeneratedImage to update with file info
     * @param chatEntry      the parent ChatEntry
     * @param request        the original request for cost metadata
     */
    private void downloadVideoFromUrl(String videoUrl, GeneratedImage generatedImage,
                                       ChatEntry chatEntry, ImageGenerationRequest request) {
        CompletableFuture.runAsync(() -> {
            try {
                // Create images directory (videos stored alongside images)
                Path imagesDir = config.paths().images();
                Files.createDirectories(imagesDir);

                // Generate filename for the video
                String baseFilename = ImageFileStore.filenameWithoutExtension(generatedImage);
                String filename = baseFilename + ".mp4";
                Path videoPath = imagesDir.resolve(filename);

                logger.info("Downloading video from: {} to: {}", videoUrl, videoPath);

                // Download video file, with the headers the provider needs for its files (e.g. its key)
                HttpRequest.Builder downloadBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(videoUrl))
                        .timeout(Duration.ofSeconds(120)); // Longer timeout for video download
                providers.forModel(request.getModel()).downloadHeaders(videoUrl).forEach(downloadBuilder::header);
                HttpRequest downloadRequest = downloadBuilder.GET().build();

                HttpResponse<InputStream> downloadResponse = downloadClient.get().send(
                        downloadRequest,
                        HttpResponse.BodyHandlers.ofInputStream());

                if (downloadResponse.statusCode() == 200) {
                    // Save video to file
                    try (InputStream inputStream = downloadResponse.body()) {
                        Files.copy(inputStream, videoPath, StandardCopyOption.REPLACE_EXISTING);
                    }

                    long fileSize = Files.size(videoPath);

                    /*  The video is bound to the UI, so it is changed on the FX thread, then stored  */
                    FxModel.change(() -> {
                        generatedImage.setDownloadStatus(GeneratedImage.DownloadStatus.CACHED);
                        generatedImage.setLocalFilePath(videoPath.toString());
                        generatedImage.setFileName(filename);
                        generatedImage.setFileSizeBytes(fileSize);
                        generatedImage.setFormat("mp4");
                        generatedImage.setMimeType("video/mp4");
                        generatedImage.setDownloadedAt(LocalDateTime.now());
                        generatedImage.setSeed(0); // video has no seed
                        generatedImage.setBaseCost(request.getBaseCostPerImage() != null
                                ? request.getBaseCostPerImage() : 0.0);

                        tracker.saveImage(generatedImage);
                        logger.info("Video saved: {} ({} bytes, {}s, audio={})",
                                filename, fileSize,
                                generatedImage.getVideoDurationSeconds(),
                                generatedImage.hasAudio());

                        tracker.notifyEntryUpdated(chatEntry);
                        tracker.notifyImageUpdated(generatedImage);
                        tracker.refresh(chatEntry);
                    });

                } else {
                    logger.error("Failed to download video: HTTP {}", downloadResponse.statusCode());
                    FxModel.change(() -> {
                        generatedImage.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
                        generatedImage.setErrorMessage("Video download failed: HTTP " + downloadResponse.statusCode());
                        tracker.saveImage(generatedImage);
                        tracker.notifyImageUpdated(generatedImage);
                        tracker.refresh(chatEntry);
                    });
                }

            } catch (Exception e) {
                logger.error("Failed to download video", e);
                FxModel.change(() -> {
                    generatedImage.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
                    generatedImage.setErrorMessage("Video download error: " + e.getMessage());
                    tracker.saveImage(generatedImage);
                    tracker.notifyImageUpdated(generatedImage);
                    tracker.refresh(chatEntry);
                });
            }
        }, io);
    }

    /**
     * Stores the image of a synchronous generation (Gemini, OpenAI). The placeholder is a JavaFX object the UI
     * is bound to, so it is only changed on the FX thread; the decoding and the file writing in between run on
     * a worker. The existing placeholder is updated in place to keep the property bindings of the UI.
     */
    private void storeSynchronousImage(GenerationJobInfo jobInfo, ImageGenerationRequest request,
                                       ChatEntry chatEntry, int imageIndex) {
        CompletableFuture<GeneratedImage> prepared = FxModel.read(() -> {
            GeneratedImage placeholder = placeholder(chatEntry, imageIndex);
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
            } catch (Exception e) {
                logger.error("Failed to save Gemini image", e);
                error = "Failed to save image: " + e.getMessage();
            }

            ImageFileStore.StoredImage result = stored;
            if (error != null)
                tracker.markFailed(chatEntry, error);
            FxModel.change(() -> {
                if (result != null)
                    applyStoredImage(placeholder, result, jobInfo, request, chatEntry);
                else
                    placeholder.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
                tracker.notifyEntryUpdated(chatEntry);
                tracker.notifyImageUpdated(placeholder);
                tracker.refresh(chatEntry);
            });
        }, io);
    }

    /**
     * Applies a written image file to its placeholder, including the costs of Google Search grounding and thinking
     * tokens, and has it stored. Runs on the FX thread; an image that cannot be stored fails.
     */
    private void applyStoredImage(GeneratedImage generatedImage, ImageFileStore.StoredImage stored, GenerationJobInfo jobInfo,
                                  ImageGenerationRequest request, ChatEntry chatEntry) {
        stored.applyTo(generatedImage);
        generatedImage.setSeed(0); // Gemini doesn't use seeds - clear any placeholder value

        double baseCost = request.getBaseCostPerImage() != null ? request.getBaseCostPerImage() : 0.0;
        Integer thinkingTokens = jobInfo.getThinkingTokens();
        double searchCost = GenerationCost.search(GenerationModel.fromApiModelId(request.getModel()),
                request.getEnableGoogleSearch());
        double reasoningCost = GenerationCost.reasoning(thinkingTokens);

        generatedImage.setBaseCost(baseCost);
        generatedImage.setSearchCost(searchCost);
        generatedImage.setReasoningCost(reasoningCost);
        generatedImage.setThinkingTokens(thinkingTokens);
        generatedImage.setThoughtSummary(jobInfo.getThoughtSummary());

        logger.info("Gemini costs - Base: ${}, Search: ${}, Reasoning: ${} ({} tokens)",
                String.format("%.4f", baseCost), String.format("%.4f", searchCost),
                String.format("%.4f", reasoningCost), thinkingTokens != null ? thinkingTokens : 0);

        tracker.saveImage(generatedImage).exceptionally((Throwable failure) -> {
            Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
            FxModel.change(() -> generatedImage.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED));
            tracker.markFailed(chatEntry, "Failed to save image: " + cause.getMessage());
            return null;
        });

        logger.info("Gemini image stored: {} ({}x{}, {}, {} bytes)", stored.filename(), generatedImage.getWidth(),
                generatedImage.getHeight(), generatedImage.getFormat(), generatedImage.getFileSizeBytes());
    }



    /**
     * Processes an attached image file for image-to-image generation.
     * Validates the model supports image input and encodes the image to Base64.
     *
     * @param request           the image generation request to update
     * @param attachedImageFile the image file to process
     * @throws IllegalArgumentException if image processing fails or model is
     *                                  incompatible
     */
    private void processAttachedImage(ImageGenerationRequest request, java.io.File attachedImageFile) {
        try {
            // Validate model supports image input (BFL: FLUX 3 and FLUX.2; Gemini and OpenAI: all models)
            String model = request.getModel();
            ApiProvider provider = providers.providerOf(model);
            providers.get(provider).checkImageInput(model);

            // Processing attached image

            // Encode image to Base64 (validation happens inside ImageEncoder)
            String base64Image = ImageEncoder.encodeImageToBase64(attachedImageFile);
            request.setInputImage(base64Image);

            // Successfully encoded attached image
            logger.info("Successfully attached image to request. Base64 length: {}, Model: {}, Provider: {}",
                        base64Image.length(), model, provider);

        } catch (Exception e) {
            logger.error("Failed to process attached image: {}", attachedImageFile.getName(), e);
            throw new IllegalArgumentException("Failed to process image attachment: " + e.getMessage(), e);
        }
    }
}
