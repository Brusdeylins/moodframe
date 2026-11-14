package art.moodframe.service;

import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

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
     * Sets the callback for a FLUX.2 safety tolerance lowered to the maximum the BFL account allows.
     *
     * <p>BFL rejects a safety tolerance the account is not approved for; the request is then repeated once
     * with the approved maximum, which is also stored as the new FLUX.2 safety tolerance. The callback is
     * invoked on a worker thread.</p>
     *
     * @param callback the callback to invoke with the lowered safety tolerance
     */
    void setOnSafetyToleranceLowered(IntConsumer callback);
    
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