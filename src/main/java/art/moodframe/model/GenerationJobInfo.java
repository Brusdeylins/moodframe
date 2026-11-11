package art.moodframe.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents information about a submitted image generation job.
 *
 * <p>This class contains the job ID and initial status information
 * returned when a generation request is successfully submitted to
 * the BFL API.</p>
 *
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerationJobInfo {
    
    @JsonProperty("id")
    private String jobId;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("created_at")
    private String createdAt;
    
    @JsonProperty("queue_position")
    private Integer queuePosition;
    
    @JsonProperty("estimated_wait_time")
    private Integer estimatedWaitTimeSeconds;
    
    @JsonProperty("polling_url")
    private String pollingUrl;

    @JsonProperty("cost")
    private Double cost;

    // Gemini-specific fields for synchronous response
    private String base64Image; // Gemini returns Base64 encoded image directly
    private boolean isSynchronous = false; // true for Gemini, false for BFL
    private ApiProvider provider; // Which provider this job belongs to

    // Gemini Phase 2 Advanced Features - Thinking metadata
    private Integer thinkingTokens; // Number of thinking tokens used
    private String thoughtSummary; // AI reasoning summary (if includeThoughts enabled)

    // video-specific fields
    private String operationName; // Long-running operation ID from predictLongRunning
    private boolean isVideoGeneration = false; // true for video jobs
    private String videoDownloadUrl; // Download URL for completed video

    // Error message for failed generations (e.g., content blocked)
    private String errorMessage;

    /**
     * Default constructor for JSON deserialization.
     */
    public GenerationJobInfo() {}
    
    /**
     * Creates job info with the specified ID and status.
     * 
     * @param jobId the unique job identifier
     * @param status the initial job status
     */
    public GenerationJobInfo(String jobId, String status) {
        this.jobId = jobId;
        this.status = status;
    }
    
    /**
     * Gets the unique job identifier.
     * 
     * @return the job ID
     */
    public String getJobId() {
        return jobId;
    }
    
    /**
     * Sets the unique job identifier.
     * 
     * @param jobId the job ID
     */
    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
    
    /**
     * Gets the current job status.
     * 
     * @return the status (e.g., "pending", "running", "completed", "failed")
     */
    public String getStatus() {
        return status;
    }
    
    /**
     * Sets the current job status.
     * 
     * @param status the status
     */
    public void setStatus(String status) {
        this.status = status;
    }
    
    /**
     * Gets the job creation timestamp.
     * 
     * @return the creation timestamp in ISO format
     */
    public String getCreatedAt() {
        return createdAt;
    }
    
    /**
     * Sets the job creation timestamp.
     * 
     * @param createdAt the creation timestamp
     */
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
    
    /**
     * Gets the current position in the generation queue.
     * 
     * @return the queue position, or null if not available
     */
    public Integer getQueuePosition() {
        return queuePosition;
    }
    
    /**
     * Sets the current position in the generation queue.
     * 
     * @param queuePosition the queue position
     */
    public void setQueuePosition(Integer queuePosition) {
        this.queuePosition = queuePosition;
    }
    
    /**
     * Gets the estimated wait time until generation starts.
     * 
     * @return the estimated wait time in seconds, or null if not available
     */
    public Integer getEstimatedWaitTimeSeconds() {
        return estimatedWaitTimeSeconds;
    }
    
    /**
     * Sets the estimated wait time until generation starts.
     * 
     * @param estimatedWaitTimeSeconds the wait time in seconds
     */
    public void setEstimatedWaitTimeSeconds(Integer estimatedWaitTimeSeconds) {
        this.estimatedWaitTimeSeconds = estimatedWaitTimeSeconds;
    }
    
    /**
     * Gets the polling URL for checking job status.
     * 
     * @return the polling URL, or null if not provided
     */
    public String getPollingUrl() {
        return pollingUrl;
    }
    
    /**
     * Sets the polling URL for checking job status.
     *
     * @param pollingUrl the polling URL
     */
    public void setPollingUrl(String pollingUrl) {
        this.pollingUrl = pollingUrl;
    }

    /**
     * Gets the cost of the generation job.
     *
     * @return the cost in USD, or null if not available
     */
    public Double getCost() {
        return cost;
    }

    /**
     * Sets the cost of the generation job.
     *
     * @param cost the cost in USD
     */
    public void setCost(Double cost) {
        this.cost = cost;
    }

    /**
     * Checks if the job is in a pending state.
     * 
     * @return true if the job is pending or queued
     */
    public boolean isPending() {
        return "pending".equalsIgnoreCase(status) || "queued".equalsIgnoreCase(status);
    }
    
    /**
     * Checks if the job is currently running.
     * 
     * @return true if the job is actively being processed
     */
    public boolean isRunning() {
        return "running".equalsIgnoreCase(status) || "processing".equalsIgnoreCase(status);
    }
    
    /**
     * Checks if the job has completed successfully.
     * 
     * @return true if the job completed successfully
     */
    public boolean isCompleted() {
        return "completed".equalsIgnoreCase(status) || "success".equalsIgnoreCase(status);
    }
    
    /**
     * Checks if the job has failed.
     * 
     * @return true if the job failed or was cancelled
     */
    public boolean isFailed() {
        return "failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status) || 
               "cancelled".equalsIgnoreCase(status);
    }
    
    /**
     * Checks if the job is in a terminal state (completed or failed).
     *
     * @return true if the job is finished (won't change status further)
     */
    public boolean isFinished() {
        return isCompleted() || isFailed();
    }

    /**
     * Gets the Base64 encoded image for synchronous Gemini responses.
     *
     * @return the Base64 image data, or null if not a synchronous response
     */
    public String getBase64Image() {
        return base64Image;
    }

    /**
     * Sets the Base64 encoded image for synchronous Gemini responses.
     *
     * @param base64Image the Base64 image data
     */
    public void setBase64Image(String base64Image) {
        this.base64Image = base64Image;
    }

    /**
     * Checks if this is a synchronous response (Gemini) or asynchronous (BFL).
     *
     * @return true if synchronous (Gemini), false if asynchronous (BFL)
     */
    public boolean isSynchronous() {
        return isSynchronous;
    }

    /**
     * Sets whether this is a synchronous response.
     *
     * @param synchronous true for synchronous (Gemini), false for asynchronous (BFL)
     */
    public void setSynchronous(boolean synchronous) {
        this.isSynchronous = synchronous;
    }

    /**
     * Gets the API provider for this job.
     *
     * @return the API provider (BFL or GOOGLE_GEMINI)
     */
    public ApiProvider getProvider() {
        return provider;
    }

    /**
     * Sets the API provider for this job.
     *
     * @param provider the API provider
     */
    public void setProvider(ApiProvider provider) {
        this.provider = provider;
    }

    /**
     * Gets the number of thinking tokens used (Gemini Phase 2).
     *
     * @return the thinking token count, or null if not applicable
     */
    public Integer getThinkingTokens() {
        return thinkingTokens;
    }

    /**
     * Sets the number of thinking tokens used (Gemini Phase 2).
     *
     * @param thinkingTokens the thinking token count
     */
    public void setThinkingTokens(Integer thinkingTokens) {
        this.thinkingTokens = thinkingTokens;
    }

    /**
     * Gets the AI's thought process summary (Gemini Phase 2).
     *
     * @return the thought summary, or null if not included
     */
    public String getThoughtSummary() {
        return thoughtSummary;
    }

    /**
     * Sets the AI's thought process summary (Gemini Phase 2).
     *
     * @param thoughtSummary the thought summary
     */
    public void setThoughtSummary(String thoughtSummary) {
        this.thoughtSummary = thoughtSummary;
    }

    // ===== Video Getters/Setters =====

    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }

    public boolean isVideoGeneration() { return isVideoGeneration; }
    public void setVideoGeneration(boolean videoGeneration) { this.isVideoGeneration = videoGeneration; }

    public String getVideoDownloadUrl() { return videoDownloadUrl; }
    public void setVideoDownloadUrl(String videoDownloadUrl) { this.videoDownloadUrl = videoDownloadUrl; }

    /**
     * Gets the error message for failed generations.
     *
     * @return the error message, or null if not failed
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets the error message for failed generations.
     *
     * @param errorMessage the error message
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return String.format("GenerationJobInfo{jobId='%s', status='%s', queuePosition=%d}",
            jobId, status, queuePosition);
    }

    /**
     * Builder class for creating GenerationJobInfo instances.
     */
    public static class Builder {
        private String jobId;
        private String status;
        private String createdAt;
        private Integer queuePosition;
        private Integer estimatedWaitTimeSeconds;
        private String pollingUrl;
        private Double cost;
        private String base64Image;
        private boolean isSynchronous = false;
        private ApiProvider provider;
        private Integer thinkingTokens;
        private String thoughtSummary;
        private String errorMessage;
        private String operationName;
        private boolean isVideoGeneration = false;
        private String videoDownloadUrl;

        public Builder jobId(String jobId) {
            this.jobId = jobId;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(String createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder queuePosition(Integer queuePosition) {
            this.queuePosition = queuePosition;
            return this;
        }

        public Builder estimatedWaitTimeSeconds(Integer estimatedWaitTimeSeconds) {
            this.estimatedWaitTimeSeconds = estimatedWaitTimeSeconds;
            return this;
        }

        public Builder pollingUrl(String pollingUrl) {
            this.pollingUrl = pollingUrl;
            return this;
        }

        public Builder cost(Double cost) {
            this.cost = cost;
            return this;
        }

        public Builder base64Image(String base64Image) {
            this.base64Image = base64Image;
            return this;
        }

        public Builder isSynchronous(boolean isSynchronous) {
            this.isSynchronous = isSynchronous;
            return this;
        }

        public Builder provider(ApiProvider provider) {
            this.provider = provider;
            return this;
        }

        public Builder thinkingTokens(Integer thinkingTokens) {
            this.thinkingTokens = thinkingTokens;
            return this;
        }

        public Builder thoughtSummary(String thoughtSummary) {
            this.thoughtSummary = thoughtSummary;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }

        public Builder isVideoGeneration(boolean isVideoGeneration) {
            this.isVideoGeneration = isVideoGeneration;
            return this;
        }

        public Builder videoDownloadUrl(String videoDownloadUrl) {
            this.videoDownloadUrl = videoDownloadUrl;
            return this;
        }

        public GenerationJobInfo build() {
            GenerationJobInfo info = new GenerationJobInfo();
            info.jobId = this.jobId;
            info.status = this.status;
            info.createdAt = this.createdAt;
            info.queuePosition = this.queuePosition;
            info.estimatedWaitTimeSeconds = this.estimatedWaitTimeSeconds;
            info.pollingUrl = this.pollingUrl;
            info.cost = this.cost;
            info.base64Image = this.base64Image;
            info.isSynchronous = this.isSynchronous;
            info.provider = this.provider;
            info.thinkingTokens = this.thinkingTokens;
            info.thoughtSummary = this.thoughtSummary;
            info.errorMessage = this.errorMessage;
            info.operationName = this.operationName;
            info.isVideoGeneration = this.isVideoGeneration;
            info.videoDownloadUrl = this.videoDownloadUrl;
            return info;
        }
    }
}