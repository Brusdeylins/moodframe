package art.moodframe.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents the result of a completed image generation job.
 * 
 * <p>This class contains the job status, generated image URLs,
 * metadata, and any error information for a BFL API generation job.</p>
 * 
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerationJobResult {
    
    @JsonProperty("id")
    private String jobId;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("result")
    private GenerationResult result;
    
    @JsonProperty("error")
    private String error;
    
    @JsonProperty("progress")
    private Integer progressPercentage;
    
    @JsonProperty("queue_position")
    private Integer queuePosition;
    
    @JsonProperty("estimated_wait_time")
    private Integer estimatedWaitTimeSeconds;
    
    @JsonProperty("started_at")
    private String startedAt;
    
    @JsonProperty("completed_at")
    private String completedAt;
    
    @JsonProperty("cost")
    private Double cost;
    
    @JsonProperty("details")
    private Object details;

    // Gemini-specific field for Base64 encoded images
    private List<String> base64Images; // Gemini returns images as Base64 in response

    /**
     * Default constructor for JSON deserialization.
     */
    public GenerationJobResult() {}
    
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
     * Gets the generation result containing image URLs and metadata.
     * 
     * @return the result, or null if not yet completed
     */
    public GenerationResult getResult() {
        return result;
    }
    
    /**
     * Sets the generation result.
     * 
     * @param result the result
     */
    public void setResult(GenerationResult result) {
        this.result = result;
    }
    
    /**
     * Gets the error message if the job failed.
     * 
     * @return the error message, or null if no error
     */
    public String getError() {
        return error;
    }
    
    /**
     * Sets the error message.
     * 
     * @param error the error message
     */
    public void setError(String error) {
        this.error = error;
    }
    
    /**
     * Gets the current progress percentage.
     * 
     * @return the progress (0-100), or null if not available
     */
    public Integer getProgressPercentage() {
        return progressPercentage;
    }
    
    /**
     * Sets the current progress percentage.
     * 
     * @param progressPercentage the progress (0-100)
     */
    public void setProgressPercentage(Integer progressPercentage) {
        this.progressPercentage = progressPercentage;
    }
    
    /**
     * Gets the current position in the generation queue.
     * 
     * @return the queue position, or null if not queued
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
     * Sets the estimated wait time.
     * 
     * @param estimatedWaitTimeSeconds the wait time in seconds
     */
    public void setEstimatedWaitTimeSeconds(Integer estimatedWaitTimeSeconds) {
        this.estimatedWaitTimeSeconds = estimatedWaitTimeSeconds;
    }
    
    /**
     * Gets the timestamp when generation started.
     * 
     * @return the start timestamp in ISO format, or null if not started
     */
    public String getStartedAt() {
        return startedAt;
    }
    
    /**
     * Sets the generation start timestamp.
     * 
     * @param startedAt the start timestamp
     */
    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }
    
    /**
     * Gets the timestamp when generation completed.
     * 
     * @return the completion timestamp in ISO format, or null if not completed
     */
    public String getCompletedAt() {
        return completedAt;
    }
    
    /**
     * Sets the generation completion timestamp.
     * 
     * @param completedAt the completion timestamp
     */
    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }
    
    /**
     * Gets the cost of the generation in credits.
     * 
     * @return the cost, or null if not available
     */
    public Double getCost() {
        return cost;
    }
    
    /**
     * Sets the generation cost.
     * 
     * @param cost the cost in credits
     */
    public void setCost(Double cost) {
        this.cost = cost;
    }
    
    /**
     * Gets additional details about the job.
     * 
     * @return additional job details, or null if not available
     */
    public Object getDetails() {
        return details;
    }
    
    /**
     * Sets additional details about the job.
     * 
     * @param details additional job details
     */
    public void setDetails(Object details) {
        this.details = details;
    }
    
    /**
     * Checks if the job is in a pending state.
     * 
     * @return true if the job is pending or queued
     */
    public boolean isPending() {
        return "pending".equalsIgnoreCase(status) || "queued".equalsIgnoreCase(status) ||
               "Pending".equalsIgnoreCase(status) || "Task not found".equalsIgnoreCase(status);
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
        return "completed".equalsIgnoreCase(status) || "success".equalsIgnoreCase(status) || 
               "Ready".equalsIgnoreCase(status);
    }
    
    /**
     * Checks if the job has failed.
     * 
     * @return true if the job failed or was cancelled
     */
    public boolean isFailed() {
        return "failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status) || 
               "cancelled".equalsIgnoreCase(status) || "Error".equalsIgnoreCase(status) ||
               "Request Moderated".equalsIgnoreCase(status) || "Content Moderated".equalsIgnoreCase(status);
    }
    
    /**
     * Checks if the job is in a terminal state.
     *
     * @return true if the job is finished (won't change status further)
     */
    public boolean isFinished() {
        return isCompleted() || isFailed();
    }

    /**
     * Gets the Base64 encoded images for Gemini responses.
     *
     * @return list of Base64 encoded images, or null if not available
     */
    public List<String> getBase64Images() {
        return base64Images;
    }

    /**
     * Sets the Base64 encoded images for Gemini responses.
     *
     * @param base64Images list of Base64 encoded images
     */
    public void setBase64Images(List<String> base64Images) {
        this.base64Images = base64Images;
    }

    /**
     * Gets the generated image URLs if available.
     *
     * @return list of image URLs, or null if not completed
     */
    public List<String> getImageUrls() {
        return result != null ? result.getImageUrls() : null;
    }
    
    @Override
    public String toString() {
        return String.format("GenerationJobResult{jobId='%s', status='%s', progress=%d%%, error='%s'}", 
            jobId, status, progressPercentage, error);
    }
    
    /**
     * Nested class representing the actual generation result.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GenerationResult {
        
        private List<String> imageUrls;
        
        @JsonProperty("prompt")
        private String prompt;
        
        @JsonProperty("seed")
        private Long seed;
        
        @JsonProperty("safety_tolerance")
        private Integer safetyTolerance;
        
        @JsonProperty("start_time")
        private String startTime;
        
        /**
         * Gets the list of generated image URLs.
         * 
         * @return the image URLs
         */
        public List<String> getImageUrls() {
            return imageUrls;
        }
        
        /**
         * Sets the list of generated image URLs.
         * 
         * @param imageUrls the image URLs
         */
        public void setImageUrls(List<String> imageUrls) {
            this.imageUrls = imageUrls;
        }
        
        /**
         * Jackson setter for sample field that handles both string and array.
         * BFL API sometimes returns a single string instead of an array.
         * 
         * @param sample the sample data (string or array)
         */
        @JsonProperty("sample")
        public void setSample(Object sample) {
            if (sample instanceof String) {
                this.imageUrls = List.of((String) sample);
            } else if (sample instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> urls = (List<String>) sample;
                this.imageUrls = urls;
            } else {
                this.imageUrls = null;
            }
        }
        
        /**
         * Gets the prompt used for generation.
         * 
         * @return the prompt
         */
        public String getPrompt() {
            return prompt;
        }
        
        /**
         * Sets the prompt used for generation.
         * 
         * @param prompt the prompt
         */
        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }
        
        /**
         * Gets the seed used for generation.
         * 
         * @return the seed value
         */
        public Long getSeed() {
            return seed;
        }
        
        /**
         * Sets the seed used for generation.
         * 
         * @param seed the seed value
         */
        public void setSeed(Long seed) {
            this.seed = seed;
        }
        
        /**
         * Gets the safety tolerance used for generation.
         * 
         * @return the safety tolerance
         */
        public Integer getSafetyTolerance() {
            return safetyTolerance;
        }
        
        /**
         * Sets the safety tolerance.
         * 
         * @param safetyTolerance the safety tolerance
         */
        public void setSafetyTolerance(Integer safetyTolerance) {
            this.safetyTolerance = safetyTolerance;
        }
    }
}