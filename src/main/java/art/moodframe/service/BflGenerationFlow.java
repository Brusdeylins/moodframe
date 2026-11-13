package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationJobInfo;
import art.moodframe.model.GenerationJobResult;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.repository.ChatRepository;
import art.moodframe.util.Secrets;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The asynchronous BFL flow of a generation: submit a job per image, poll its status until the image is ready,
 * download it. The images of the chat entry are changed on the FX thread, files and requests run on workers.
 *
 * @since 1.9.1
 */
public class BflGenerationFlow {

    private static final Logger logger = LoggerFactory.getLogger(BflGenerationFlow.class);

    private final ApplicationConfig config;
    private final BflApiService bflApiService;
    private final ChatRepository chatRepository;
    private final ChatEntryTracker tracker;
    private final ImageFileStore imageFiles;
    private final ScheduledExecutorService scheduler;
    private final Map<String, PendingRequest> activeRequests = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom(); // High-entropy random for seed generation

    /**
     * Creates the flow.
     *
     * @param config         the application configuration (timeout, logging)
     * @param bflApiService  the BFL API
     * @param chatRepository where images are stored
     * @param tracker        keeps the chat entry in step with its images
     * @param imageFiles     writes the downloaded images
     * @param scheduler      runs the status polling and the database writes
     */
    public BflGenerationFlow(ApplicationConfig config, BflApiService bflApiService, ChatRepository chatRepository,
            ChatEntryTracker tracker, ImageFileStore imageFiles, ScheduledExecutorService scheduler) {
        this.config = config;
        this.bflApiService = bflApiService;
        this.chatRepository = chatRepository;
        this.tracker = tracker;
        this.imageFiles = imageFiles;
        this.scheduler = scheduler;
    }

    /**
     * Cancels a pending job.
     *
     * @param jobId the job ID to cancel
     * @return true if cancelled successfully
     */
    public boolean cancel(String jobId) {
        PendingRequest request = activeRequests.remove(jobId);
        if (request != null && request.pollingTask != null) {
            request.pollingTask.cancel(true);
            return true;
        }
        return false;
    }

    /**
     * Checks if a job is currently polled.
     *
     * @param jobId the job ID to check
     * @return true if the job is active
     */
    public boolean isActive(String jobId) {
        return activeRequests.containsKey(jobId);
    }

    /** Stops the polling of all jobs. */
    public void cancelAll() {
        for (PendingRequest request : activeRequests.values()) {
            request.cancelPolling();
        }
        activeRequests.clear();
    }

    /**
     * Submits a single image generation request to the BFL API.
     */
    public void submit(ImageGenerationRequest request, ChatEntry chatEntry, int imageIndex) {
        try {
            // Create a copy of the request with a unique seed for this image
            ImageGenerationRequest imageRequest = new ImageGenerationRequest(request.getPrompt(), request.getModel());
            imageRequest.setWidth(request.getWidth());
            imageRequest.setHeight(request.getHeight());
            imageRequest.setPromptUpsampling(request.getPromptUpsampling());
            imageRequest.setOutputFormat(request.getOutputFormat());

            // FLUX.2 models only support safety_tolerance 0-5 (FLUX 3: 0-7)
            Integer safetyTolerance = request.getSafetyTolerance();
            String modelId = request.getModel();
            boolean isFlux2Model = modelId != null && modelId.toLowerCase().startsWith("flux.2-");
            boolean isFlux3Model = modelId != null && modelId.toLowerCase().startsWith("flux.3-");

            if (safetyTolerance != null && isFlux2Model && safetyTolerance > 5) {
                logger.info("Capping safety tolerance from {} to 5 for FLUX.2 model: {}", safetyTolerance, modelId);
                safetyTolerance = 5; // Cap at 5 for FLUX.2 models
            }
            imageRequest.setSafetyTolerance(safetyTolerance);

            logger.debug("Safety tolerance set to {} for model {} (isFlux2: {})",
                safetyTolerance, modelId, isFlux2Model);

            imageRequest.setImageSize(request.getImageSize()); // FLUX 3 resolution tier
            imageRequest.setAspectRatio(request.getAspectRatio()); // Copy aspect ratio for proper model handling
            imageRequest.setStylePreset(request.getStylePreset()); // Copy style preset if present

            // Copy FLUX.2 specific parameters
            imageRequest.setGuidance(request.getGuidance()); // FLUX.2 [flex] guidance
            imageRequest.setSteps(request.getSteps()); // FLUX.2 [flex] steps
            // Note: grounding_search is NOT a parameter - FLUX.2 [max] has built-in grounding
            // that activates automatically based on prompt content

            // Copy input image for image-to-image generation (FLUX.2 up to 8 images, FLUX 3 up to 10)
            if (request.hasInputImage()) {
                imageRequest.setInputImage(request.getInputImage());
                // Copy additional FLUX.2 multi-image inputs (input_image_2 through input_image_8)
                if (request.getInputImage2() != null && !request.getInputImage2().isEmpty()) {
                    imageRequest.setInputImage2(request.getInputImage2());
                }
                if (request.getInputImage3() != null && !request.getInputImage3().isEmpty()) {
                    imageRequest.setInputImage3(request.getInputImage3());
                }
                if (request.getInputImage4() != null && !request.getInputImage4().isEmpty()) {
                    imageRequest.setInputImage4(request.getInputImage4());
                }
                if (request.getInputImage5() != null && !request.getInputImage5().isEmpty()) {
                    imageRequest.setInputImage5(request.getInputImage5());
                }
                if (request.getInputImage6() != null && !request.getInputImage6().isEmpty()) {
                    imageRequest.setInputImage6(request.getInputImage6());
                }
                if (request.getInputImage7() != null && !request.getInputImage7().isEmpty()) {
                    imageRequest.setInputImage7(request.getInputImage7());
                }
                if (request.getInputImage8() != null && !request.getInputImage8().isEmpty()) {
                    imageRequest.setInputImage8(request.getInputImage8());
                }
                if (request.getInputImage9() != null && !request.getInputImage9().isEmpty()) {
                    imageRequest.setInputImage9(request.getInputImage9());
                }
                if (request.getInputImage10() != null && !request.getInputImage10().isEmpty()) {
                    imageRequest.setInputImage10(request.getInputImage10());
                }
                int imageCount = request.getInputImageCount();
                StringBuilder imgSizes = new StringBuilder();
                if (request.getInputImage() != null) imgSizes.append("img1=").append(request.getInputImage().length()).append(" ");
                if (request.getInputImage2() != null) imgSizes.append("img2=").append(request.getInputImage2().length()).append(" ");
                if (request.getInputImage3() != null) imgSizes.append("img3=").append(request.getInputImage3().length()).append(" ");
                if (request.getInputImage4() != null) imgSizes.append("img4=").append(request.getInputImage4().length()).append(" ");
                if (request.getInputImage5() != null) imgSizes.append("img5=").append(request.getInputImage5().length()).append(" ");
                if (request.getInputImage6() != null) imgSizes.append("img6=").append(request.getInputImage6().length()).append(" ");
                if (request.getInputImage7() != null) imgSizes.append("img7=").append(request.getInputImage7().length()).append(" ");
                if (request.getInputImage8() != null) imgSizes.append("img8=").append(request.getInputImage8().length());
                logger.info("Copied {} input images to imageRequest ({})", imageCount, imgSizes.toString().trim());
            }

            // Handle seed generation for multiple images: user seed with index offset, else a random seed
            long seed = request.getSeed() != null ? request.getSeed() + imageIndex : generateRandomSeed(imageIndex);

            /*  FLUX 3 Klein takes seeds from 0 to 4294967295 only (FLUX 3 Image takes none)  */
            final Long imageSeed = isFlux3Model ? seed & 0xFFFFFFFFL : seed;
            imageRequest.setSeed(imageSeed);

            // Submit request through BflApiService with timeout
            bflApiService.submitGenerationRequest(imageRequest)
                .orTimeout(30, TimeUnit.SECONDS) // 30 second timeout
                .thenAccept(jobInfo -> {
                    // Handle successful submission
                    handleSubmissionSuccess(jobInfo, chatEntry, imageIndex, imageRequest, imageSeed);
                })
                .exceptionally(throwable -> {
                    if (throwable instanceof java.util.concurrent.TimeoutException) {
                        logger.error("Timeout submitting generation request for image {}", imageIndex);
                        tracker.markFailed(chatEntry, "Request timeout after 30 seconds");
                    } else {
                        logger.error("Failed to submit generation request for image {}", imageIndex, throwable);
                        tracker.markFailed(chatEntry, throwable.getMessage());
                    }
                    return null;
                });

        } catch (Exception e) {
            logger.error("Failed to create generation request for image {}", imageIndex, e);
            tracker.markFailed(chatEntry, e.getMessage());
        }
    }

    /**
     * Handles successful submission of generation request.
     */
    private void handleSubmissionSuccess(GenerationJobInfo jobInfo, ChatEntry chatEntry, int imageIndex,
            ImageGenerationRequest request, Long imageSeed) {
        try {
            String jobId = jobInfo.getJobId();

                // Create GeneratedImage with pending status and all generation parameters
                GeneratedImage generatedImage = new GeneratedImage.Builder()
                        .chatEntryId(chatEntry.getId())
                        .generationJobId(jobId)
                        .prompt(request.getPrompt())
                        .model(request.getModel())
                        .seed(imageSeed) // Always has a valid seed (user or generated)
                        .safetyTolerance(request.getSafetyTolerance() != null ? request.getSafetyTolerance() : 2)
                        .promptUpsampling(request.getPromptUpsampling() != null ? request.getPromptUpsampling() : true)
                        .width(request.getWidth() != null ? request.getWidth() : 0)
                        .height(request.getHeight() != null ? request.getHeight() : 0)
                        .format(request.getOutputFormat() != null ? request.getOutputFormat() : "png")
                        // Store additional parameters for recreation
                        .aspectRatio(request.getAspectRatio() != null ? request.getAspectRatio() : "1:1")
                        .stylePreset(request.getStylePreset()) // Can be null
                        .downloadStatus(GeneratedImage.DownloadStatus.PENDING)
                        .sortOrder(imageIndex)
                        .baseCost(request.getBaseCostPerImage() != null ? request.getBaseCostPerImage() : 0.0)
                        .build();

                // Replace placeholder or add to chat entry (on FX thread) and persist to
                // database (async)
                Platform.runLater(() -> {
                    // Find and replace the placeholder with sortOrder matching imageIndex
                    boolean replacedPlaceholder = false;
                    for (int j = 0; j < chatEntry.getImages().size(); j++) {
                        GeneratedImage existingImage = chatEntry.getImages().get(j);
                        if (existingImage.getSortOrder() == imageIndex &&
                                existingImage.getDownloadStatus() == GeneratedImage.DownloadStatus.PENDING &&
                                existingImage.getImageUrl() == null) {
                            // Replace placeholder
                            chatEntry.getImages().set(j, generatedImage);
                            replacedPlaceholder = true;
                            break;
                        }
                    }

                    // If no placeholder found, add normally
                    if (!replacedPlaceholder) {
                        chatEntry.getImages().add(generatedImage);
                    }

                    // Notify listeners immediately for UI update
                    tracker.notifyEntryUpdated(chatEntry);
                    tracker.notifyImageUpdated(generatedImage);
                });

                // Persist to database asynchronously (don't block UI)
                CompletableFuture.runAsync(() -> {
                    if (chatRepository != null) {
                        try {
                            chatRepository.saveGeneratedImage(generatedImage);
                        } catch (Exception e) {
                            logger.error("Failed to save GeneratedImage to database: {}", generatedImage.getId(), e);
                        }
                    }
                }, scheduler);

                // Start polling for this job
                PendingRequest pendingRequest = new PendingRequest(jobId, chatEntry, generatedImage, request);
                // CRITICAL: Store the polling URL from BFL response to avoid 404 errors
                pendingRequest.setPollingUrl(jobInfo.getPollingUrl());
                activeRequests.put(jobId, pendingRequest);
                startPolling(jobId, pendingRequest);


        } catch (Exception e) {
            logger.error("Failed to handle submission success", e);
            tracker.markFailed(chatEntry, "Failed to process submission: " + e.getMessage());
        }
    }

    /**
     * Starts polling for job status updates with proper initial delay.
     */
    private void startPolling(String jobId, PendingRequest pendingRequest) {
        ScheduledFuture<?> pollingTask = scheduler.scheduleAtFixedRate(() -> {
            PendingRequest currentRequest = activeRequests.get(jobId);
            if (currentRequest != null) {
                pollJobStatus(jobId, currentRequest);
            }
        }, 5, 3, TimeUnit.SECONDS); // Start after 5s, then every 3s (increased initial delay)

        pendingRequest.setPollingTask(pollingTask);
        
        if (config.getLoggingEnabled()) {
            logger.info("Started polling for job {} (initial delay: 5s, interval: 3s)", 
                jobId.substring(0, Math.min(8, jobId.length())));
        }
    }

    /**
     * Polls the BFL API for job status updates with improved error handling.
     */
    private void pollJobStatus(String jobId, PendingRequest pendingRequest) {
        // Increment polling attempts
        pendingRequest.incrementPollingAttempts();
        
        // Calculate max attempts based on configured timeout
        // Polling interval is 3 seconds, so max attempts = timeout_seconds / 3
        int timeoutSeconds = config != null ? config.getApiTimeoutSeconds() : 600;
        int maxPollingAttempts = timeoutSeconds / 3;
        
        // Stop polling if we've tried too many times
        if (pendingRequest.pollingAttempts > maxPollingAttempts) {
            logger.warn("Stopping polling for job {} after {} attempts ({} seconds)", 
                jobId.substring(0, Math.min(8, jobId.length())), 
                pendingRequest.pollingAttempts,
                pendingRequest.pollingAttempts * 3);
            pendingRequest.cancelPolling();
            activeRequests.remove(jobId);
            Platform.runLater(() -> handleJobFailed(jobId, pendingRequest, "Polling timeout"));
            return;
        }
        
        try {
            CompletableFuture<GenerationJobResult> pollingFuture;
            
            // Use the correct polling URL from the BFL response instead of constructing one
            if (pendingRequest.pollingUrl != null && !pendingRequest.pollingUrl.isEmpty()) {
                pollingFuture = ((BflApiServiceImpl) bflApiService).getJobResultWithPollingUrl(pendingRequest.pollingUrl);
            } else {
                // Fallback to old method (will likely cause 404)
                logger.warn("No polling URL available for job {}, using fallback method", jobId);
                pollingFuture = bflApiService.getJobStatus(jobId);
            }
            
            /*  The image is bound to the UI, so its status is handled on the FX thread  */
            pollingFuture.thenAccept(jobResult -> {
                    Platform.runLater(() -> handleJobStatusUpdate(jobResult, jobId, pendingRequest));
                })
                .exceptionally(throwable -> {
                    // Handle HTTP 404 (job not found) as normal - BFL API returns 404 until job is complete
                    // This is expected behavior, not an error condition
                    if (throwable.getCause() instanceof BflApiServiceImpl.BflApiException bflError 
                        && bflError.getStatusCode() == 404) {
                        
                        logger.debug("Job {} status check returned 404 (attempt {}/{}) - job still processing", 
                            jobId.substring(0, Math.min(8, jobId.length())), 
                            pendingRequest.pollingAttempts, maxPollingAttempts);
                        return null; // Continue polling - 404 is normal until job completes
                    }
                    
                    // Other errors - log but continue polling (network issues, etc.)
                    logger.warn("Polling error for job {} (attempt {}): {}", 
                        jobId.substring(0, Math.min(8, jobId.length())), 
                        pendingRequest.pollingAttempts, throwable.getMessage());
                    return null;
                });
        } catch (Exception e) {
            logger.error("Failed to poll status for job: {}", jobId, e);
        }
    }

    /**
     * Handles job status update from BFL API.
     */
    private void handleJobStatusUpdate(GenerationJobResult jobResult, String jobId, PendingRequest pendingRequest) {
        try {
            String status = jobResult.getStatus();

            // Job status checked - handle all valid BFL API status values
            switch (status.toLowerCase()) {
                case "pending" -> {
                    // Job is queued and waiting to be processed
                    // Only notify UI if status actually changed (avoid unnecessary UI updates during polling)
                    String currentApiStatus = pendingRequest.generatedImage.getApiStatus();
                    if (!"pending".equals(currentApiStatus)) {
                        pendingRequest.generatedImage.setDownloadStatus(GeneratedImage.DownloadStatus.PENDING);
                        pendingRequest.generatedImage.setApiStatus("pending");
                        tracker.notifyImageUpdated(pendingRequest.generatedImage);
                    }

                    if (config.getLoggingEnabled() && pendingRequest.pollingAttempts % 5 == 0) {
                        logger.debug("Job {} still pending (attempt {})",
                            jobId.substring(0, Math.min(8, jobId.length())), pendingRequest.pollingAttempts);
                    }
                }
                case "running", "reasoning", "generating" -> {
                    // Job is currently being processed (FLUX 3 reports Reasoning and Generating)
                    // Only notify UI if status actually changed (avoid unnecessary UI updates during polling)
                    String currentApiStatus = pendingRequest.generatedImage.getApiStatus();
                    if (!"running".equals(currentApiStatus)) {
                        pendingRequest.generatedImage.setDownloadStatus(GeneratedImage.DownloadStatus.PENDING);
                        pendingRequest.generatedImage.setApiStatus("running");
                        tracker.notifyImageUpdated(pendingRequest.generatedImage);
                    }

                    if (config.getLoggingEnabled()) {
                        logger.debug("Job {} is running (attempt {})",
                            jobId.substring(0, Math.min(8, jobId.length())), pendingRequest.pollingAttempts);
                    }
                }
                case "ready" -> {
                    // Image is ready for download
                    handleJobCompleted(jobId, pendingRequest, jobResult);
                }
                case "failed", "error", "content moderated", "request moderated", "task not found" -> {
                    // Job failed
                    handleJobFailed(jobId, pendingRequest, status);
                }
                default -> {
                    // Unknown status - log and continue polling for a while
                    logger.warn("Unknown job status '{}' for job {} (attempt {})", 
                        status, jobId.substring(0, Math.min(8, jobId.length())), pendingRequest.pollingAttempts);
                    
                    // If we've been polling for too long with unknown status, treat as failure
                    if (pendingRequest.pollingAttempts > 20) {
                        logger.error("Stopping polling for job {} after {} unknown status responses", 
                            jobId.substring(0, Math.min(8, jobId.length())), pendingRequest.pollingAttempts);
                        
                        pendingRequest.cancelPolling();
                        activeRequests.remove(jobId);
                        handleJobFailed(jobId, pendingRequest, "Unknown status: " + status);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Failed to handle status update for job: {}", jobId, e);
        }
    }

    /**
     * Handles completed image generation jobs.
     */
    private void handleJobCompleted(String jobId, PendingRequest pendingRequest, GenerationJobResult jobResult) {
        try {
            // Extract image URL from response
            List<String> imageUrls = jobResult.getImageUrls();
            String imageUrl = (imageUrls != null && !imageUrls.isEmpty()) ? imageUrls.get(0) : null;

            if (imageUrl != null && !imageUrl.isEmpty()) {
                    // Update generated image
                    pendingRequest.generatedImage.setImageUrl(imageUrl);
                    pendingRequest.generatedImage.setDownloadStatus(GeneratedImage.DownloadStatus.DOWNLOADING);
                    pendingRequest.generatedImage.setDownloadedAt(LocalDateTime.now());

                    // Update seed from API response (this is the actual seed used for generation)
                    if (jobResult.getResult() != null && jobResult.getResult().getSeed() != null) {
                        pendingRequest.generatedImage.setSeed(jobResult.getResult().getSeed());
                    }

                    tracker.notifyImageUpdated(pendingRequest.generatedImage);

                    // Update ChatEntry cost with actual API cost if available
                    tracker.updateCostFromApi(pendingRequest.chatEntry, jobResult);

                    // Start actual image download
                    downloadImage(pendingRequest.generatedImage, pendingRequest.chatEntry);

                    if (config.getLoggingEnabled()) {
                        logger.info("Job {} completed successfully with image URL: {}", jobId, Secrets.redact(imageUrl));
                    }
            } else {
                logger.error("No image URL found in result for job: {}", jobId);
                handleJobFailed(jobId, pendingRequest, "No image URL in response");
            }

            // Stop polling and remove from active requests
            pendingRequest.cancelPolling();
            activeRequests.remove(jobId);

            // Check if all images for this chat entry are done
            Platform.runLater(() -> tracker.refresh(pendingRequest.chatEntry));

        } catch (Exception e) {
            logger.error("Failed to handle job completion for: {}", jobId, e);
            handleJobFailed(jobId, pendingRequest, "Processing error: " + e.getMessage());
        }
    }

    /**
     * Handles failed image generation jobs.
     */
    private void handleJobFailed(String jobId, PendingRequest pendingRequest, String reason) {
        logger.warn("Job {} failed: {}", jobId, reason);

        // Set detailed error information
        pendingRequest.generatedImage.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
        pendingRequest.generatedImage.setApiStatus(reason);

        // Set user-friendly error message based on the failure reason
        String userFriendlyMessage = getUserFriendlyErrorMessage(reason);
        pendingRequest.generatedImage.setFailureReason(userFriendlyMessage);
        pendingRequest.generatedImage.setErrorMessage(userFriendlyMessage);

        tracker.notifyImageUpdated(pendingRequest.generatedImage);

        // Persist error information to database
        CompletableFuture.runAsync(() -> {
            if (chatRepository != null) {
                try {
                    chatRepository.saveGeneratedImage(pendingRequest.generatedImage);
                } catch (Exception ex) {
                    logger.error("Failed to save failed GeneratedImage to database: {}",
                            pendingRequest.generatedImage.getId(), ex);
                }
            }
        });

        // Stop polling and remove from active requests
        pendingRequest.cancelPolling();
        activeRequests.remove(jobId);

        // Check chat entry completion
        Platform.runLater(() -> tracker.refresh(pendingRequest.chatEntry));
    }

    /**
     * Downloads the generated image from the URL.
     */
    private void downloadImage(GeneratedImage generatedImage, ChatEntry chatEntry) {
        CompletableFuture.runAsync(() -> {
            ImageFileStore.StoredImage stored = null;
            try {
                stored = imageFiles.download(generatedImage);
            } catch (Exception e) {
                logger.error("Failed to download image: {}", generatedImage.getId(), e);
            }

            /*  The image is bound to the UI, so it is changed on the FX thread; the database write goes back to a worker  */
            ImageFileStore.StoredImage result = stored;
            Platform.runLater(() -> {
                if (result != null)
                    result.applyTo(generatedImage);
                else
                    generatedImage.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);

                if (chatRepository != null) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            chatRepository.saveGeneratedImage(generatedImage);
                        } catch (Exception e) {
                            logger.error("Failed to update GeneratedImage in database: {}", generatedImage.getId(), e);
                        }
                    });
                }

                tracker.notifyImageUpdated(generatedImage);
                tracker.refresh(chatEntry);
            });
        });
    }


    /**
     * Generates a cryptographically strong random seed for better image variety.
     * Uses multiple entropy sources to ensure maximum variety between images.
     * 
     * @param imageIndex the index of the image in the batch
     * @return a strong random seed value that is guaranteed to be unique
     */
    public long generateRandomSeed(int imageIndex) {
        // Add small delay to ensure different nanoTime values for consecutive calls
        try {
            Thread.sleep(imageIndex + 1); // Variable delay based on index
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Combine multiple entropy sources for maximum variety
        long timeSeed = System.nanoTime(); // High-resolution timestamp
        long randomSeed1 = secureRandom.nextLong(); // First strong random number
        long randomSeed2 = secureRandom.nextLong(); // Second strong random number for more entropy
        long indexSeed = (long) imageIndex * 982451653L; // Large prime for index offset
        long threadSeed = (long) Thread.currentThread().hashCode() * 1009L; // Thread variety
        long memSeed = Runtime.getRuntime().freeMemory(); // Memory state as entropy

        // XOR combine all entropy sources for truly unique seeds
        long finalSeed = timeSeed ^ randomSeed1 ^ randomSeed2 ^ indexSeed ^ threadSeed ^ memSeed;

        // Ensure seed is never 0 (which might be treated as "no seed")
        if (finalSeed == 0) {
            finalSeed = secureRandom.nextLong();
        }

        // Generated unique seed for image

        return finalSeed;
    }

    /**
     * Converts technical API error messages to user-friendly messages.
     * 
     * @param apiReason the raw API error reason
     * @return user-friendly error message
     */
    private String getUserFriendlyErrorMessage(String apiReason) {
        if (apiReason == null) {
            return "Unknown error occurred";
        }

        String reason = apiReason.toLowerCase().trim();

        return switch (reason) {
            case "request moderated", "content moderated" ->
                "Content blocked by safety filters. Please modify your prompt and try again.";
            case "error", "failed" ->
                "Generation failed. Please try again with different parameters.";
            case "timeout" ->
                "Generation timed out. Please try again later.";
            case "polling timeout" ->
                "Generation is taking too long. The server may be overloaded. Please try again later.";
            case "rate limited", "rate_limited" ->
                "Too many requests. Please wait a moment before trying again.";
            case "insufficient credits", "quota exceeded" ->
                "Insufficient credits or quota exceeded. Please check your account.";
            case "invalid parameters", "invalid_parameters" ->
                "Invalid generation parameters. Please check your settings.";
            case "no image url in response" ->
                "Server error: No image was generated. Please try again.";
            case "no result in response" ->
                "Server error: Invalid response received. Please try again.";
            default -> {
                // Capitalize first letter and add period if not present
                String message = apiReason.substring(0, 1).toUpperCase() + apiReason.substring(1);
                if (!message.endsWith(".") && !message.endsWith("!")) {
                    message += ".";
                }
                yield "Generation failed: " + message;
            }
        };
    }




    /**
     * Internal class to track pending requests.
     */
    private static class PendingRequest {
        final ChatEntry chatEntry;
        final GeneratedImage generatedImage;
        ScheduledFuture<?> pollingTask;
        int pollingAttempts = 0;
        String pollingUrl; // CRITICAL: Store the polling URL from BFL response

        PendingRequest(String jobId, ChatEntry chatEntry, GeneratedImage generatedImage,
                ImageGenerationRequest originalRequest) {
            this.chatEntry = chatEntry;
            this.generatedImage = generatedImage;
        }
        
        void setPollingUrl(String pollingUrl) {
            this.pollingUrl = pollingUrl;
        }

        void setPollingTask(ScheduledFuture<?> task) {
            this.pollingTask = task;
        }

        void cancelPolling() {
            if (pollingTask != null && !pollingTask.isCancelled()) {
                pollingTask.cancel(false);
            }
        }
        
        void incrementPollingAttempts() {
            this.pollingAttempts++;
        }
    }
}
