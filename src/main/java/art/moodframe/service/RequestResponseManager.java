package art.moodframe.service;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationJobInfo;
import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.repository.ChatRepository;
import art.moodframe.util.ImageEncoder;
import art.moodframe.util.TrustStoreManager;
import art.moodframe.config.CorporateProxyConfig;

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
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javafx.application.Platform;
import javax.net.ssl.SSLContext;

/**
 * Central manager for handling BFL API requests and responses.
 * 
 * <p>
 * This service manages the complete lifecycle of image generation requests:
 * - Request submission to BFL API
 * - Progress tracking with polling
 * - Error handling and retry logic
 * - Image download and storage
 * - UI notification events
 * </p>
 * 
 * @since 1.0.0
 */
public class RequestResponseManager implements RequestResponseService {

    private static final Logger logger = LoggerFactory.getLogger(RequestResponseManager.class);

    private final ApplicationConfig config;
    private final ScheduledExecutorService scheduler;
    private final BflGenerationFlow bflFlow;
    private final GeminiApiService geminiApiService;
    private final OpenAiApiService openAiApiService;
    private final ChatRepository chatRepository;
    private final HttpClient downloadClient;  // Simple client for downloading images from CDN
    private final ImageFileStore imageFiles;

    private final ChatEntryTracker tracker;

    /**
     * Creates a new RequestResponseManager with the given configuration.
     */
    public RequestResponseManager(ApplicationConfig config, ChatRepository chatRepository) {
        this.config = config;
        this.chatRepository = chatRepository;
        this.geminiApiService = ServiceFactory.getInstance().getService(GeminiApiService.class);
        this.openAiApiService = ServiceFactory.getInstance().getService(OpenAiApiService.class);

        // Create HttpClient with corporate SSL configuration for downloading images
        this.downloadClient = createDownloadClient();
        this.imageFiles = new ImageFileStore(config, downloadClient);
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.tracker = new ChatEntryTracker(config, chatRepository, scheduler);
        this.bflFlow = new BflGenerationFlow(config, ServiceFactory.getInstance().getService(BflApiService.class),
                chatRepository, tracker, imageFiles, scheduler);

        if (config.getLoggingEnabled()) {
            logger.info("RequestResponseManager initialized with timeout: {}s, proxy: {}",
                    config.getApiTimeoutSeconds(), config.isProxyEnabled() ? "enabled" : "disabled");
        }
    }

    /**
     * Creates HttpClient with corporate SSL configuration for downloading images.
     * This ensures that image downloads work behind corporate proxies with custom certificates.
     */
    private HttpClient createDownloadClient() {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL);
        
        try {
            // Get proxy configuration
            CorporateProxyConfig proxyConfig = ServiceFactory.getInstance().getService(CorporateProxyConfig.class);
            
            if (proxyConfig == null) {
                logger.warn("CorporateProxyConfig not available - using default SSL context for download client");
                return clientBuilder.build();
            }
            
            // Load corporate certificates if available
            var certificatePaths = proxyConfig.getCertificatePaths();
            
            if (!certificatePaths.isEmpty()) {
                logger.info("Configuring download client with {} corporate certificate(s)", certificatePaths.size());
                
                // Create trust store manager
                TrustStoreManager trustStoreManager = new TrustStoreManager();
                
                // Create corporate trust store
                trustStoreManager.createCorporateKeyStore(certificatePaths);
                
                // Create SSL context with corporate certificates
                SSLContext sslContext = trustStoreManager.createSSLContext();
                clientBuilder.sslContext(sslContext);
                
                logger.info("Download client SSL context configured successfully with corporate certificates");
            } else {
                logger.debug("Using default SSL context for download client (no corporate certificates configured)");
            }
        } catch (Exception e) {
            logger.error("Failed to configure SSL context for download client", e);
            logger.warn("Download client will use default SSL context - image downloads may fail behind corporate proxy");
        }
        
        return clientBuilder.build();
    }

    /**
     * Sets the callback for ChatEntry updates.
     */
    @Override
    public void setOnEntryUpdated(Consumer<ChatEntry> callback) {
        tracker.setOnEntryUpdated(callback);
    }

    /**
     * Sets the callback for GeneratedImage updates.
     */
    @Override
    public void setOnImageUpdated(Consumer<GeneratedImage> callback) {
        tracker.setOnImageUpdated(callback);
    }

    /**
     * Sets the callback for a FLUX.2 safety tolerance lowered to the maximum the BFL account allows.
     */
    @Override
    public void setOnSafetyToleranceLowered(IntConsumer callback) {
        bflFlow.setOnSafetyToleranceLowered(callback);
    }

    /**
     * Submits an image generation request to the BFL API.
     * 
     * @param request   the generation request
     * @param chatEntry the chat entry to update with results
     * @return CompletableFuture that completes when the request is submitted
     */
    @Override
    public CompletableFuture<Void> submitImageGeneration(ImageGenerationRequest request, ChatEntry chatEntry) {
        return submitImageGeneration(request, chatEntry, null);
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

                // Detect provider first to determine correct placeholder count
                ApiProvider provider = detectProvider(request.getModel());

                // Update chat entry status and create placeholder images (ensure on FX thread)
                Platform.runLater(() -> {
                    chatEntry.setStatus(ChatEntry.Status.PROCESSING);

                    // Create placeholder images for immediate UI feedback with pre-generated seeds
                    // Both BFL and Gemini now support multiple images (1-4)
                    int imageCount = request.getImageCount() != null ? request.getImageCount() : 1;

                    for (int i = 0; i < imageCount; i++) {
                        // Generate seed for each placeholder image (BFL only, Gemini doesn't use seeds)
                        Long placeholderSeed = null;
                        if (provider == ApiProvider.BFL) {
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

                        // Only set seed for BFL (Gemini doesn't use seeds)
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

                if (isOmniModel(request.getModel())) {
                    // Gemini Omni: synchronous video generation (always 1 video per request)
                    handleOmniGeneration(request, chatEntry, 0);
                } else if (provider == ApiProvider.GOOGLE_GEMINI) {
                    // Gemini: Synchronous flow - submit multiple parallel requests
                    for (int i = 0; i < imageCount; i++) {
                        handleGeminiGeneration(request, chatEntry, i);
                    }
                } else if (provider == ApiProvider.OPENAI) {
                    // OpenAI: Synchronous flow like Gemini, one image per request
                    for (int i = 0; i < imageCount; i++) {
                        handleSynchronousGeneration(openAiApiService.submitGenerationRequest(request), request,
                                chatEntry, i, "OpenAI");
                    }
                } else {
                    // BFL: Asynchronous polling flow
                    for (int i = 0; i < imageCount; i++) {
                        bflFlow.submit(request, chatEntry, i);
                    }
                }

            } catch (Exception e) {
                logger.error("Failed to submit image generation request", e);
                Platform.runLater(() -> {
                    chatEntry.setStatus(ChatEntry.Status.FAILED);
                    chatEntry.setErrorMessage(e.getMessage());
                    tracker.notifyEntryUpdated(chatEntry);
                });
            }
        });
    }

    @Override
    public boolean cancelGeneration(String jobId) {
        return bflFlow.cancel(jobId);
    }

    @Override
    public boolean isGenerationActive(String jobId) {
        return bflFlow.isActive(jobId);
    }

    @Override
    public void shutdown() {
        bflFlow.cancelAll();
        scheduler.shutdown();

        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Detects the API provider based on the model name.
     *
     * @param model the model identifier
     * @return the detected API provider (BFL, GOOGLE_GEMINI or OPENAI)
     */
    private ApiProvider detectProvider(String model) {
        if (model == null) {
            return ApiProvider.BFL; // Default to BFL
        }
        String modelLower = model.toLowerCase();
        if (modelLower.contains("gemini")) {
            return ApiProvider.GOOGLE_GEMINI;
        }
        if (art.moodframe.model.GenerationModel.isOpenAiImageModelId(model)
                || (openAiApiService != null && openAiApiService.getImageModels().contains(model))) {
            return ApiProvider.OPENAI;
        }
        return ApiProvider.BFL;
    }

    /**
     * Checks if the given model identifier is a Gemini Omni video generation model.
     *
     * @param model the model identifier
     * @return true if this is an Omni model
     */
    private boolean isOmniModel(String model) {
        return model != null && model.toLowerCase().startsWith("gemini-omni");
    }

    /**
     * Handles Gemini image generation with synchronous flow.
     * Note: Placeholder image is already created in submitImageGeneration()
     *
     * @param request    the image generation request
     * @param chatEntry  the chat entry to update
     * @param imageIndex the index of the image in the batch (0-based)
     */
    private void handleGeminiGeneration(ImageGenerationRequest request, ChatEntry chatEntry, int imageIndex) {
        handleSynchronousGeneration(geminiApiService.submitGenerationRequest(request), request, chatEntry,
                imageIndex, "Gemini");
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
                    // Content was blocked - mark placeholder as failed with error message
                    Platform.runLater(() -> {
                        for (GeneratedImage img : chatEntry.getImages()) {
                            if (img.getSortOrder() == imageIndex) {
                                img.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
                                img.setErrorMessage(jobInfo.getErrorMessage());
                                img.setBaseCost(0.0); // No cost for blocked images

                                // Save to database
                                try {
                                    chatRepository.saveGeneratedImage(img);
                                    logger.info("Saved blocked image to database with error message");
                                } catch (Exception e) {
                                    logger.error("Failed to save blocked image to database", e);
                                }
                                break;
                            }
                        }

                        // Check if all images are finished and update ChatEntry status
                        tracker.refresh(chatEntry);
                    });
                } else if (jobInfo.isSynchronous() && jobInfo.getBase64Image() != null) {
                    // NO POLLING NEEDED - Image is already ready!

                    storeSynchronousImage(jobInfo, request, chatEntry, imageIndex);
                }
            })
            .exceptionally(throwable -> {
                logger.error("{} generation failed for image index {}", provider, imageIndex, throwable);

                // Mark placeholder as failed at the correct index
                Platform.runLater(() -> {
                    // Find placeholder by sortOrder (not list position) to handle parallel async completions
                    for (GeneratedImage img : chatEntry.getImages()) {
                        if (img.getSortOrder() == imageIndex) {
                            img.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
                            Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                            img.setErrorMessage(cause.getMessage());

                            // Save to database
                            try {
                                chatRepository.saveGeneratedImage(img);
                            } catch (Exception e) {
                                logger.error("Failed to save failed image to database", e);
                            }
                            break;
                        }
                    }

                    // Check if all images are finished and update ChatEntry status
                    tracker.refresh(chatEntry);
                });

                return null;
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
        Platform.runLater(() -> {
            GeneratedImage placeholder = findOmniPlaceholder(chatEntry, imageIndex);
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

        geminiApiService.submitOmniGenerationRequest(request)
            .thenAccept(jobInfo -> {
                if (jobInfo.isFailed()) {
                    failOmniVideo(chatEntry, imageIndex, jobInfo.getErrorMessage());
                    return;
                }

                Platform.runLater(() -> {
                    GeneratedImage placeholder = findOmniPlaceholder(chatEntry, imageIndex);
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
                failOmniVideo(chatEntry, imageIndex, throwable.getMessage());
                return null;
            });
    }

    private GeneratedImage findOmniPlaceholder(ChatEntry chatEntry, int imageIndex) {
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
        Platform.runLater(() -> {
            GeneratedImage img = findOmniPlaceholder(chatEntry, imageIndex);
            if (img != null) {
                img.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
                img.setErrorMessage(errorMessage);
                img.setBaseCost(0.0);
                try {
                    chatRepository.saveGeneratedImage(img);
                } catch (Exception e) {
                    logger.error("Failed to save failed Omni video to database", e);
                }
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
                String imagesPath = config.getImagesPath();
                Path imagesDir = Paths.get(imagesPath);
                Files.createDirectories(imagesDir);

                // Generate filename for the video
                String baseFilename = ImageFileStore.filenameWithoutExtension(generatedImage);
                String filename = baseFilename + ".mp4";
                Path videoPath = imagesDir.resolve(filename);

                logger.info("Downloading video from: {} to: {}", videoUrl, videoPath);

                // Download video file. The Google file URL answers HTTP 403 without the API key, which is
                // sent as a header so that it does not show up in the URL or the log.
                HttpRequest.Builder downloadBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(videoUrl))
                        .timeout(Duration.ofSeconds(120)); // Longer timeout for video download
                String geminiKey = config.getGeminiApiKey();
                if (videoUrl.startsWith("https://generativelanguage.googleapis.com/")
                        && geminiKey != null && !geminiKey.isBlank()) {
                    downloadBuilder.header("x-goog-api-key", geminiKey);
                }
                HttpRequest downloadRequest = downloadBuilder.GET().build();

                HttpResponse<InputStream> downloadResponse = downloadClient.send(
                        downloadRequest,
                        HttpResponse.BodyHandlers.ofInputStream());

                if (downloadResponse.statusCode() == 200) {
                    // Save video to file
                    try (InputStream inputStream = downloadResponse.body()) {
                        Files.copy(inputStream, videoPath, StandardCopyOption.REPLACE_EXISTING);
                    }

                    long fileSize = Files.size(videoPath);

                    /*  The video is bound to the UI, so it is changed on the FX thread  */
                    Platform.runLater(() -> {
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

                        try {
                            if (chatRepository != null)
                                chatRepository.saveGeneratedImage(generatedImage);
                        } catch (Exception e) {
                            logger.error("Failed to save video to database", e);
                        }

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
                    Platform.runLater(() -> {
                        generatedImage.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
                        generatedImage.setErrorMessage("Video download failed: HTTP " + downloadResponse.statusCode());
                        try {
                            chatRepository.saveGeneratedImage(generatedImage);
                        } catch (Exception e) {
                            logger.error("Failed to save failed video to database", e);
                        }
                        tracker.notifyImageUpdated(generatedImage);
                        tracker.refresh(chatEntry);
                    });
                }

            } catch (Exception e) {
                logger.error("Failed to download video", e);
                Platform.runLater(() -> {
                    generatedImage.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
                    generatedImage.setErrorMessage("Video download error: " + e.getMessage());
                    try {
                        chatRepository.saveGeneratedImage(generatedImage);
                    } catch (Exception ex) {
                        logger.error("Failed to save failed video to database", ex);
                    }
                    tracker.notifyImageUpdated(generatedImage);
                    tracker.refresh(chatEntry);
                });
            }
        });
    }

    /**
     * Stores the image of a synchronous generation (Gemini, OpenAI). The placeholder is a JavaFX object the UI
     * is bound to, so it is only changed on the FX thread; the decoding and the file writing in between run on
     * a worker. The existing placeholder is updated in place to keep the property bindings of the UI.
     */
    private void storeSynchronousImage(GenerationJobInfo jobInfo, ImageGenerationRequest request,
                                       ChatEntry chatEntry, int imageIndex) {
        CompletableFuture<GeneratedImage> prepared = new CompletableFuture<>();
        Platform.runLater(() -> {
            GeneratedImage placeholder = null;
            for (GeneratedImage img : chatEntry.getImages()) {
                if (img.getSortOrder() == imageIndex) {
                    placeholder = img;
                    break;
                }
            }
            if (placeholder != null) {
                placeholder.setGenerationJobId(jobInfo.getJobId());
                placeholder.setPrompt(request.getPrompt());
                placeholder.setModel(request.getModel());
                placeholder.setDownloadStatus(GeneratedImage.DownloadStatus.DOWNLOADING);
                placeholder.setAspectRatio(request.getAspectRatio() != null ? request.getAspectRatio() : "1:1");
                placeholder.setBaseCost(request.getBaseCostPerImage() != null ? request.getBaseCostPerImage() : 0.0);
            }
            prepared.complete(placeholder);
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
            Platform.runLater(() -> {
                if (result != null)
                    applyStoredImage(placeholder, result, jobInfo, request, chatEntry);
                else
                    placeholder.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
                tracker.notifyEntryUpdated(chatEntry);
                tracker.notifyImageUpdated(placeholder);
                tracker.refresh(chatEntry);
            });
        });
    }

    /**
     * Applies a written image file to its placeholder and stores it, including the costs of Google Search
     * grounding and thinking tokens. Runs on the FX thread.
     */
    private void applyStoredImage(GeneratedImage generatedImage, ImageFileStore.StoredImage stored, GenerationJobInfo jobInfo,
                                  ImageGenerationRequest request, ChatEntry chatEntry) {
        stored.applyTo(generatedImage);
        generatedImage.setSeed(0); // Gemini doesn't use seeds - clear any placeholder value

        double baseCost = request.getBaseCostPerImage() != null ? request.getBaseCostPerImage() : 0.0;
        double searchCost = Boolean.TRUE.equals(request.getEnableGoogleSearch()) ? 0.03 : 0.0;
        double reasoningCost = 0.0;

        Integer thinkingTokens = jobInfo.getThinkingTokens();
        if (thinkingTokens != null && thinkingTokens > 0) {
            // Reasoning cost: (tokens / 1,000,000) * $12 per 1M tokens
            reasoningCost = (thinkingTokens / 1_000_000.0) * 12.0;
        }

        generatedImage.setBaseCost(baseCost);
        generatedImage.setSearchCost(searchCost);
        generatedImage.setReasoningCost(reasoningCost);
        generatedImage.setThinkingTokens(thinkingTokens);
        generatedImage.setThoughtSummary(jobInfo.getThoughtSummary());

        logger.info("Gemini costs - Base: ${}, Search: ${}, Reasoning: ${} ({} tokens)",
                String.format("%.4f", baseCost), String.format("%.4f", searchCost),
                String.format("%.4f", reasoningCost), thinkingTokens != null ? thinkingTokens : 0);

        try {
            if (chatRepository != null)
                chatRepository.saveGeneratedImage(generatedImage);
        } catch (Exception e) {
            logger.error("Failed to save Gemini image", e);
            generatedImage.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
            tracker.markFailed(chatEntry, "Failed to save image: " + e.getMessage());
            return;
        }

        logger.info("Gemini image saved: {} ({}x{}, {}, {} bytes)", stored.filename(), generatedImage.getWidth(),
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
            // Validate model supports image input
            String model = request.getModel();
            ApiProvider provider = detectProvider(model);

            if (provider == ApiProvider.BFL) {
                // BFL: FLUX 3 and FLUX.2 models support image input
                String modelLower = model != null ? model.toLowerCase() : "";
                boolean isFlux = modelLower.startsWith("flux.3-") || modelLower.startsWith("flux.2-");

                if (!isFlux) {
                    throw new IllegalArgumentException(
                            "Image input is only supported with FLUX 3 and FLUX.2 models");
                }
            } else if (provider == ApiProvider.GOOGLE_GEMINI) {
                // Gemini: ALL models support image input - no additional check needed
                logger.info("Gemini model '{}' supports image input", model);
            } else if (provider == ApiProvider.OPENAI) {
                // OpenAI: gpt-image models edit images, the service rejects dall-e-3
                logger.info("OpenAI model '{}' gets the image as edit input", model);
            }

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
