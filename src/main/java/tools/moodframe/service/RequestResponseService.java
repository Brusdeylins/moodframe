package tools.moodframe.service;

import tools.moodframe.model.ImageGenerationRequest;
import tools.moodframe.model.ChatEntry;
import tools.moodframe.model.GeneratedImage;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Interface for managing BFL API requests and responses.
 * 
 * @since 1.0.0
 */
public interface RequestResponseService {
    
    /**
     * Sets the callback for ChatEntry updates.
     * 
     * <p>This callback will be invoked whenever a chat entry is updated
     * during the image generation process.</p>
     * 
     * @param callback the callback to invoke with updated ChatEntry
     */
    void setOnEntryUpdated(Consumer<ChatEntry> callback);
    
    /**
     * Sets the callback for GeneratedImage updates.
     * 
     * <p>This callback will be invoked whenever a generated image is updated
     * with new status or data.</p>
     * 
     * @param callback the callback to invoke with updated GeneratedImage
     */
    void setOnImageUpdated(Consumer<GeneratedImage> callback);
    
    /**
     * Submits an image generation request to the BFL API.
     * 
     * @param request the generation request containing parameters
     * @param chatEntry the chat entry to update with results
     * @return CompletableFuture that completes when the request is submitted
     * @throws IllegalArgumentException if request or chatEntry is null
     * @throws RuntimeException if the API service is not configured
     */
    CompletableFuture<Void> submitImageGeneration(ImageGenerationRequest request, ChatEntry chatEntry);
    
    /**
     * Submits an image generation request with optional image attachment.
     * 
     * @param request the generation request containing parameters
     * @param chatEntry the chat entry to update with results
     * @param attachedImageFile optional image file for image-to-image generation (can be null)
     * @return CompletableFuture that completes when the request is submitted
     * @throws IllegalArgumentException if request or chatEntry is null
     * @throws IOException if the attached image file cannot be read
     * @throws RuntimeException if the API service is not configured
     */
    CompletableFuture<Void> submitImageGeneration(ImageGenerationRequest request, ChatEntry chatEntry, 
            java.io.File attachedImageFile);
    
    /**
     * Cancels a pending generation request.
     * 
     * @param jobId the job ID to cancel
     * @return true if cancelled successfully
     */
    boolean cancelGeneration(String jobId);
    
    /**
     * Checks if a generation request is currently active.
     * 
     * @param jobId the job ID to check
     * @return true if the request is active
     */
    boolean isGenerationActive(String jobId);
    
    /**
     * Shuts down the service and releases resources.
     * 
     * <p>This method should be called during application shutdown to ensure
     * all pending requests are cancelled and resources are released.</p>
     * 
     * <p><strong>Thread-safety:</strong> This method is thread-safe but should
     * only be called once during application shutdown.</p>
     */
    void shutdown();
}