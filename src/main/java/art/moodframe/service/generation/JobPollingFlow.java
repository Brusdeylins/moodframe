package art.moodframe.service.generation;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.service.provider.ApiHttpException;
import art.moodframe.service.provider.GenerationJobInfo;
import art.moodframe.service.provider.GenerationJobResult;
import art.moodframe.service.provider.ImageProvider;
import art.moodframe.service.provider.ProviderException;
import art.moodframe.util.Secrets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * The asynchronous flow of a generation for a provider that answers a submission with a job instead of the image
 * ({@link ImageProvider#answersWithImage()} false, today BFL): submit a job per image, poll its status with
 * {@link ImageProvider#pollJob} until the image is ready, download it. What differs per provider (the request of a
 * job, the retry with an approved safety tolerance, the reported price) comes from the provider; the statuses are
 * those {@link ImageProvider#pollJob} names. The images of the chat entry are changed on the FX thread through
 * {@link FxModel}, files and requests run on workers, the database writes on the worker of the
 * {@link ChatEntryTracker}.
 *
 * @since 1.9.1
 */
public class JobPollingFlow {

    private static final Logger logger = LoggerFactory.getLogger(JobPollingFlow.class);

    private final ApplicationConfig config;
    private final ChatEntryTracker tracker;
    private final ImageFileStore imageFiles;
    private final ScheduledExecutorService scheduler;
    private final Executor io;
    private final Map<String, PendingRequest> activeRequests = new ConcurrentHashMap<>();
    /*  The first poll of a job and the pause after each answer  */
    private static final long FIRST_POLL_SECONDS = 5;
    private static final long POLL_INTERVAL_SECONDS = 3;

    private volatile IntConsumer onSafetyToleranceLowered = (value) -> { };

    /**
     * Creates the flow.
     *
     * @param config         the application configuration (timeout, FLUX.2 safety tolerance)
     * @param tracker        keeps the chat entry in step with its images and stores them
     * @param imageFiles     writes the downloaded images
     * @param scheduler      runs the status polling
     * @param io             runs the downloads of the images
     */
    public JobPollingFlow(ApplicationConfig config, ChatEntryTracker tracker, ImageFileStore imageFiles,
            ScheduledExecutorService scheduler, Executor io) {
        this.config = config;
        this.tracker = tracker;
        this.imageFiles = imageFiles;
        this.scheduler = scheduler;
        this.io = io;
    }

    /**
     * Stops the polling of all jobs of a chat entry.
     *
     * @param entryId the ID of the entry
     */
    public void cancelEntry(String entryId) {
        activeRequests.values().removeIf((PendingRequest request) -> {
            boolean ofEntry = request.chatEntry.getId().equals(entryId);
            if (ofEntry)
                request.cancelPolling();
            return ofEntry;
        });
    }

    /**
     * Sets the callback for a FLUX.2 safety tolerance lowered to the maximum the account allows.
     * It is called on a worker thread.
     *
     * @param callback receives the lowered safety tolerance
     */
    public void setOnSafetyToleranceLowered(IntConsumer callback) {
        this.onSafetyToleranceLowered = callback;
    }

    /** Stops the polling of all jobs. */
    public void cancelAll() {
        for (PendingRequest request : activeRequests.values()) {
            request.cancelPolling();
        }
        activeRequests.clear();
    }

    /**
     * Submits the job of a single image to its provider and polls it.
     *
     * @param provider   the provider of the model, one that answers with a job
     * @param request    the request of all images of the entry
     * @param chatEntry  the entry
     * @param imageIndex the index of the image in the entry
     */
    public void submit(ImageProvider provider, ImageGenerationRequest request, ChatEntry chatEntry, int imageIndex) {
        try {
            // Create a copy of the request with a unique seed for this image
            ImageGenerationRequest imageRequest = new ImageGenerationRequest(request.getPrompt(), request.getModel());
            imageRequest.setWidth(request.getWidth());
            imageRequest.setHeight(request.getHeight());
            imageRequest.setPromptUpsampling(request.getPromptUpsampling());
            imageRequest.setOutputFormat(request.getOutputFormat());
            imageRequest.setSafetyTolerance(request.getSafetyTolerance());
            imageRequest.setImageSize(request.getImageSize()); // FLUX 3 resolution tier
            imageRequest.setAspectRatio(request.getAspectRatio()); // Copy aspect ratio for proper model handling
            imageRequest.setStylePreset(request.getStylePreset()); // Copy style preset if present

            /*  The price per image becomes the base cost of the image, the entry gets the sum of its images  */
            imageRequest.setBaseCostPerImage(request.getBaseCostPerImage());

            // Copy the parameters of the model
            imageRequest.setGuidance(request.getGuidance()); // FLUX.2 [flex] guidance
            imageRequest.setSteps(request.getSteps()); // FLUX.2 [flex] steps
            // Note: FLUX.2 [max] has built-in grounding, no parameter

            // Copy the input images for image-to-image generation (FLUX.2 up to 8 images, FLUX 3 up to 10)
            if (request.hasInputImage()) {
                imageRequest.setInputImages(request.getInputImages());
                StringBuilder imgSizes = new StringBuilder();
                List<String> images = request.getInputImages();
                for (int i = 0; i < images.size(); i++)
                    imgSizes.append("img").append(i + 1).append('=').append(images.get(i).length()).append(' ');
                logger.info("Copied {} input images to imageRequest ({})", images.size(), imgSizes.toString().trim());
            }

            // Handle seed generation for multiple images: user seed with index offset, else a random seed
            imageRequest.setSeed(request.getSeed() != null ? request.getSeed() + imageIndex : Seeds.random(imageIndex));

            /*  What the model limits in a single job, e.g. the range of the seed or the safety tolerance  */
            provider.prepareJob(imageRequest);
            final Long imageSeed = imageRequest.getSeed();
            logger.debug("Job of model {}: safety tolerance {}, seed {}", imageRequest.getModel(),
                imageRequest.getSafetyTolerance(), imageSeed);

            // Submit the job with timeout
            provider.submitGenerationRequest(imageRequest)
                .exceptionallyCompose((throwable) -> {
                    /*  An account without approval for the safety tolerance gets one retry with its approved
                        maximum, which also becomes the value of the FLUX.2 slider  */
                    Integer approved = provider.approvedSafetyTolerance(throwable);
                    if (approved == null)
                        return CompletableFuture.failedFuture(throwable);
                    logger.info("Safety tolerance {} not approved for the {} account, retrying with {}",
                        imageRequest.getSafetyTolerance(), provider.getProvider().getDisplayName(), approved);
                    imageRequest.setSafetyTolerance(approved);
                    config.setFlux2SafetyTolerance(approved);
                    onSafetyToleranceLowered.accept(approved);
                    return provider.submitGenerationRequest(imageRequest);
                })
                .orTimeout(30, TimeUnit.SECONDS) // 30 second timeout
                .thenAccept(jobInfo -> {
                    // Handle successful submission
                    handleSubmissionSuccess(provider, jobInfo, chatEntry, imageIndex, imageRequest, imageSeed);
                })
                .exceptionally(throwable -> {
                    if (ProviderException.unwrap(throwable) instanceof java.util.concurrent.TimeoutException) {
                        logger.error("Timeout submitting generation request for image {}", imageIndex);
                        tracker.markFailed(chatEntry, "Request timeout after 30 seconds");
                    } else {
                        logger.error("Failed to submit generation request for image {}", imageIndex, throwable);
                        tracker.markFailed(chatEntry, ChatEntryTracker.errorText(throwable, chatEntry));
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
    private void handleSubmissionSuccess(ImageProvider provider, GenerationJobInfo jobInfo, ChatEntry chatEntry,
            int imageIndex, ImageGenerationRequest request, Long imageSeed) {
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

                /*  The image replaces its placeholder on the FX thread, unless the entry was cancelled meanwhile:
                    then the job is left alone, so no image arrives in a cancelled entry  */
                FxModel.changeAndGet(() -> {
                    if (chatEntry.getStatus() == ChatEntry.Status.CANCELLED)
                        return false;

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

                    /*  Stored after the change, then polled  */
                    tracker.saveImage(generatedImage);
                    return true;
                }).thenAccept((Boolean wanted) -> {
                    if (!wanted) {
                        logger.info("Job {} of a cancelled entry is not polled", jobId);
                        return;
                    }
                    PendingRequest pendingRequest = new PendingRequest(provider, jobInfo, chatEntry, generatedImage);
                    activeRequests.put(jobId, pendingRequest);
                    startPolling(jobId, pendingRequest);
                });

        } catch (Exception e) {
            logger.error("Failed to handle submission success", e);
            tracker.markFailed(chatEntry, "Failed to process submission: " + e.getMessage());
        }
    }

    /**
     * Starts polling for job status updates with proper initial delay.
     */
    private void startPolling(String jobId, PendingRequest pendingRequest) {
        schedulePoll(jobId, pendingRequest, FIRST_POLL_SECONDS);
        logger.debug("Started polling for job {} (initial delay: {}s, interval: {}s)",
            jobId.substring(0, Math.min(8, jobId.length())), FIRST_POLL_SECONDS, POLL_INTERVAL_SECONDS);
    }

    /*  Polls a job once after a delay. The next poll is scheduled once this one is answered, so at most one poll of a
        job is in flight, however slow the answer  */
    private void schedulePoll(String jobId, PendingRequest pendingRequest, long delaySeconds) {
        if (activeRequests.get(jobId) != pendingRequest)
            return;
        try {
            pendingRequest.setPollingTask(scheduler.schedule(() -> pollJobStatus(jobId, pendingRequest),
                    delaySeconds, TimeUnit.SECONDS));
        } catch (RejectedExecutionException e) {
            logger.debug("Polling of job {} ends with the scheduler", jobId);
        }
    }

    /*  Takes a job out of the polling to handle its end: true only for the first caller while it is still polled,
        so a late answer after a cancellation, a timeout or another end changes nothing  */
    private boolean claim(String jobId, PendingRequest pendingRequest) {
        if (!activeRequests.remove(jobId, pendingRequest))
            return false;
        pendingRequest.cancelPolling();
        return true;
    }

    /**
     * Polls the provider for the status of a job.
     */
    private void pollJobStatus(String jobId, PendingRequest pendingRequest) {
        if (activeRequests.get(jobId) != pendingRequest)
            return;

        // Increment polling attempts
        pendingRequest.incrementPollingAttempts();

        // Calculate max attempts based on configured timeout
        int timeoutSeconds = config != null ? config.getApiTimeoutSeconds() : 600;
        int maxPollingAttempts = (int) (timeoutSeconds / POLL_INTERVAL_SECONDS);

        // Stop polling if we've tried too many times
        if (pendingRequest.pollingAttempts > maxPollingAttempts) {
            logger.warn("Stopping polling for job {} after {} attempts ({} seconds)",
                jobId.substring(0, Math.min(8, jobId.length())),
                pendingRequest.pollingAttempts,
                pendingRequest.pollingAttempts * POLL_INTERVAL_SECONDS);
            if (claim(jobId, pendingRequest))
                FxModel.change(() -> handleJobFailed(jobId, pendingRequest, "Polling timeout"));
            return;
        }

        try {
            pendingRequest.provider.pollJob(pendingRequest.job).whenComplete((GenerationJobResult jobResult, Throwable throwable) -> {
                if (throwable == null) {
                    handleJobStatusUpdate(jobResult, jobId, pendingRequest);
                    return;
                }

                // Handle HTTP 404 (job not found) as normal - BFL answers 404 until the job is visible
                // This is expected behavior, not an error condition
                if (ProviderException.unwrap(throwable) instanceof ApiHttpException http
                        && http.getStatusCode() == 404) {
                    logger.debug("Job {} status check returned 404 (attempt {}/{}) - job still processing",
                        jobId.substring(0, Math.min(8, jobId.length())),
                        pendingRequest.pollingAttempts, maxPollingAttempts);
                } else {
                    // Other errors - log but continue polling (network issues, etc.)
                    logger.warn("Polling error for job {} (attempt {}): {}",
                        jobId.substring(0, Math.min(8, jobId.length())),
                        pendingRequest.pollingAttempts, throwable.getMessage());
                }
                schedulePoll(jobId, pendingRequest, POLL_INTERVAL_SECONDS);
            });
        } catch (Exception e) {
            logger.error("Failed to poll status for job: {}", jobId, e);
            schedulePoll(jobId, pendingRequest, POLL_INTERVAL_SECONDS);
        }
    }

    /**
     * Handles a status answered by the provider, on the thread of the answer: a job that ended is claimed here, so it is handled
     * once; the image, bound to the UI, is changed on the FX thread. A job still running is polled again.
     */
    private void handleJobStatusUpdate(GenerationJobResult jobResult, String jobId, PendingRequest pendingRequest) {
        String status = jobResult != null ? jobResult.getStatus() : null;
        if (status == null) {
            logger.error("No status in the answer for job: {}", jobId);
            schedulePoll(jobId, pendingRequest, POLL_INTERVAL_SECONDS);
            return;
        }

        // Job status checked - handle the statuses ImageProvider.pollJob names
        switch (status.toLowerCase()) {
            case "pending" -> {
                // Job is queued and waiting to be processed
                FxModel.change(() -> showProgress(pendingRequest, jobId, "pending"));
                if (pendingRequest.pollingAttempts % 5 == 0) {
                    logger.debug("Job {} still pending (attempt {})",
                        jobId.substring(0, Math.min(8, jobId.length())), pendingRequest.pollingAttempts);
                }
                schedulePoll(jobId, pendingRequest, POLL_INTERVAL_SECONDS);
            }
            case "running", "reasoning", "generating" -> {
                // Job is currently being processed (FLUX 3 reports Reasoning and Generating)
                FxModel.change(() -> showProgress(pendingRequest, jobId, "running"));
                logger.debug("Job {} is running (attempt {})",
                    jobId.substring(0, Math.min(8, jobId.length())), pendingRequest.pollingAttempts);
                schedulePoll(jobId, pendingRequest, POLL_INTERVAL_SECONDS);
            }
            case "ready" -> {
                // Image is ready for download
                if (claim(jobId, pendingRequest))
                    FxModel.change(() -> handleJobCompleted(jobId, pendingRequest, jobResult));
            }
            case "failed", "error", "content moderated", "request moderated", "task not found" -> {
                // Job failed
                if (claim(jobId, pendingRequest))
                    FxModel.change(() -> handleJobFailed(jobId, pendingRequest, status));
            }
            default -> {
                // Unknown status - log and continue polling for a while
                logger.warn("Unknown job status '{}' for job {} (attempt {})",
                    status, jobId.substring(0, Math.min(8, jobId.length())), pendingRequest.pollingAttempts);

                // If we've been polling for too long with unknown status, treat as failure
                if (pendingRequest.pollingAttempts > 20) {
                    logger.error("Stopping polling for job {} after {} unknown status responses",
                        jobId.substring(0, Math.min(8, jobId.length())), pendingRequest.pollingAttempts);
                    if (claim(jobId, pendingRequest))
                        FxModel.change(() -> handleJobFailed(jobId, pendingRequest, "Unknown status: " + status));
                } else {
                    schedulePoll(jobId, pendingRequest, POLL_INTERVAL_SECONDS);
                }
            }
        }
    }

    /*  Shows the progress of a job still polled; only a change of the status reaches the UI. Call on the FX thread  */
    private void showProgress(PendingRequest pendingRequest, String jobId, String apiStatus) {
        if (activeRequests.get(jobId) != pendingRequest)
            return;
        if (!apiStatus.equals(pendingRequest.generatedImage.getApiStatus())) {
            pendingRequest.generatedImage.setDownloadStatus(GeneratedImage.DownloadStatus.PENDING);
            pendingRequest.generatedImage.setApiStatus(apiStatus);
            tracker.notifyImageUpdated(pendingRequest.generatedImage);
        }
    }

    /**
     * Handles completed image generation jobs, claimed before (see {@link #claim}). Call on the FX thread.
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

                    // Update the image cost with the price the provider reports, if any
                    tracker.updateCost(pendingRequest.generatedImage, pendingRequest.provider.reportedCost(jobResult));

                    // Start actual image download
                    downloadImage(pendingRequest.generatedImage, pendingRequest.chatEntry);

                    logger.debug("Job {} completed successfully with image URL: {}", jobId, Secrets.redact(imageUrl));
            } else {
                logger.error("No image URL found in result for job: {}", jobId);
                handleJobFailed(jobId, pendingRequest, "No image URL in response");
            }

            // Check if all images for this chat entry are done
            FxModel.change(() -> tracker.refresh(pendingRequest.chatEntry));

        } catch (Exception e) {
            logger.error("Failed to handle job completion for: {}", jobId, e);
            handleJobFailed(jobId, pendingRequest, "Processing error: " + e.getMessage());
        }
    }

    /**
     * Handles failed image generation jobs, claimed before (see {@link #claim}). Call on the FX thread.
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

        /*  A job is billed once finished only, a failed or moderated one costs nothing  */
        pendingRequest.generatedImage.setBaseCost(0.0);

        tracker.notifyImageUpdated(pendingRequest.generatedImage);

        // Persist error information to database
        tracker.saveImage(pendingRequest.generatedImage);

        // Check chat entry completion
        FxModel.change(() -> tracker.refresh(pendingRequest.chatEntry));
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
            FxModel.change(() -> {
                if (result != null)
                    result.applyTo(generatedImage);
                else
                    generatedImage.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
                tracker.saveImage(generatedImage);

                tracker.notifyImageUpdated(generatedImage);
                tracker.refresh(chatEntry);
            });
        }, io);
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
        final ImageProvider provider;
        final GenerationJobInfo job;  // with the polling URL the provider answered
        final ChatEntry chatEntry;
        final GeneratedImage generatedImage;
        volatile ScheduledFuture<?> pollingTask;
        volatile int pollingAttempts = 0;

        PendingRequest(ImageProvider provider, GenerationJobInfo job, ChatEntry chatEntry,
                GeneratedImage generatedImage) {
            this.provider = provider;
            this.job = job;
            this.chatEntry = chatEntry;
            this.generatedImage = generatedImage;
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
