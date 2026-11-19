package art.moodframe.service.generation;

import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Sends the requests of a generation to the providers and reports the progress of the entries.
 * 
 * @since 1.0.0
 */
public interface RequestResponseService {
    
    /**
     * Adds a listener for ChatEntry updates.
     * 
     * <p>Every listener is invoked on the FX thread whenever a chat entry is updated
     * during the image generation process.</p>
     * 
     * @param listener the listener to invoke with updated ChatEntry
     */
    void addEntryListener(Consumer<ChatEntry> listener);

    /**
     * Removes a listener for ChatEntry updates.
     *
     * @param listener the listener added before
     */
    void removeEntryListener(Consumer<ChatEntry> listener);
    
    /**
     * Adds a listener for GeneratedImage updates.
     * 
     * <p>Every listener is invoked on the FX thread whenever a generated image is updated
     * with new status or data.</p>
     * 
     * @param listener the listener to invoke with updated GeneratedImage
     */
    void addImageListener(Consumer<GeneratedImage> listener);

    /**
     * Removes a listener for GeneratedImage updates.
     *
     * @param listener the listener added before
     */
    void removeImageListener(Consumer<GeneratedImage> listener);

    /**
     * Sets the callback for a FLUX.2 safety tolerance lowered to the maximum the account allows.
     *
     * <p>A provider rejects a safety tolerance the account is not approved for (see
     * {@link art.moodframe.service.provider.ImageProvider#approvedSafetyTolerance}); the request is then repeated once
     * with the approved maximum, which is also stored as the new FLUX.2 safety tolerance. The callback is
     * invoked on a worker thread.</p>
     *
     * @param callback the callback to invoke with the lowered safety tolerance
     */
    void setOnSafetyToleranceLowered(IntConsumer callback);
    
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
     * Marks an entry failed whose submission failed: it is stored with the reason (in English for an entry of the
     * MCP server, else in the language of the user interface), then the listeners are told. A cancelled entry stays
     * cancelled.
     *
     * @param chatEntry the entry
     * @param failure   why the submission failed, also wrapped in a {@code CompletionException}
     */
    void markFailed(ChatEntry chatEntry, Throwable failure);
    
    /**
     * Cancels a running chat entry: the polling of its jobs stops, its images not finished yet fail and it ends
     * as cancelled. A request Gemini or OpenAI got already cannot be called back; an image it still delivers is
     * kept, the entry stays cancelled.
     *
     * @param chatEntry the entry
     * @return completes with true if the entry was pending or processing
     */
    CompletableFuture<Boolean> cancelEntry(ChatEntry chatEntry);

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