package art.moodframe.service;

import art.moodframe.model.BrainstormTurn;
import art.moodframe.model.GenerationJobInfo;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.model.ResearchNote;
import org.json.JSONArray;

import java.util.concurrent.CompletableFuture;

/**
 * Service interface for Google Gemini API interactions.
 * Handles image generation requests and API connectivity testing.
 *
 * @author Matthias Brusdeylins
 * @since 1.5.0
 */
public interface GeminiApiService {

    /**
     * Tests the connection to the Google Gemini API.
     * This method validates the API key and checks if the API is accessible.
     *
     * @return a CompletableFuture that resolves to true if connection is successful, false otherwise
     */
    CompletableFuture<Boolean> testConnection();

    /**
     * Submits an image generation request to the Google Gemini API.
     *
     * <p>Note: This method is not yet implemented in Phase 1.
     * It will be implemented in a future release.</p>
     *
     * @param request the image generation request
     * @return a CompletableFuture containing the generation job information
     * @throws UnsupportedOperationException in Phase 1
     */
    CompletableFuture<GenerationJobInfo> submitGenerationRequest(ImageGenerationRequest request);

    /**
     * Gets the currently configured API key.
     *
     * @return the API key, or null if not configured
     */
    String getApiKey();

    /**
     * Sets the API key for this service.
     *
     * @param apiKey the API key to use
     */
    void setApiKey(String apiKey);

    /**
     * Checks if the service is properly configured with an API key.
     *
     * @return true if an API key is configured, false otherwise
     */
    boolean isConfigured();

    /**
     * Analyzes a YouTube video using Google Gemini's video understanding capabilities.
     *
     * <p>This method sends the YouTube URL to Gemini API which analyzes the video content
     * and returns a text description based on the provided analysis prompt.</p>
     *
     * <p><strong>Limitations:</strong></p>
     * <ul>
     * <li>Only public YouTube videos are supported</li>
     * <li>Free tier limit: 8 hours of YouTube video per day</li>
     * <li>Video is sampled at 1 frame per second</li>
     * </ul>
     *
     * @param youtubeUrl the YouTube video URL (youtube.com or youtu.be format)
     * @param startSeconds start time in seconds (0 for beginning of video)
     * @param endSeconds end time in seconds (0 for end of video)
     * @param analysisPrompt optional prompt describing what to extract from the video;
     *                       if null or empty, a default prompt will be used
     * @return a CompletableFuture containing the text analysis result
     * @throws IllegalArgumentException if the URL is invalid
     * @throws IllegalStateException if API key is not configured
     * @since 1.3.0
     */
    CompletableFuture<String> analyzeYouTubeVideo(
        String youtubeUrl,
        int startSeconds,
        int endSeconds,
        String analysisPrompt
    );

    /**
     * Sends one request of the brainstorm chat to the Gemini text model. The model is asked for a JSON answer
     * ({@code responseMimeType application/json}); with tools it may answer with function calls
     * ({@code research}, {@code analyze_video}) instead, which the caller runs and answers with the next request.
     *
     * @param systemPrompt the system instruction
     * @param contents the conversation so far as Gemini contents, including the function calls and responses of
     *                 the current turn; a user turn may carry an image as {@code inlineData} part
     * @param withTools whether the functions are offered to the model
     * @param noMoreCalls whether the model must answer now and may not call a function (only with tools)
     * @return a CompletableFuture containing the answer
     * @throws IllegalStateException if the API key is not configured
     * @since 1.8.1
     */
    CompletableFuture<BrainstormTurn> chatTurn(String systemPrompt, JSONArray contents, boolean withTools,
            boolean noMoreCalls);

    /**
     * Asks the Gemini text model for a short name (one or two words) for a brainstorm session.
     *
     * @param content what the session is about (the first message and the current prompt)
     * @param language the language name the title is written in, for example "German"
     * @return a CompletableFuture containing the title, which may be empty
     * @throws IllegalStateException if the API key is not configured
     * @since 1.8.1
     */
    CompletableFuture<String> suggestTitle(String content, String language);

    /**
     * Researches a question on the web with the built-in Gemini tools Google Search and URL context.
     *
     * @param query what to find out
     * @return a CompletableFuture containing the found text and its sources
     * @throws IllegalStateException if the API key is not configured
     * @since 1.8.1
     */
    CompletableFuture<ResearchNote> research(String query);

    /**
     * Generates a video with Gemini Omni through the Interactions API. The call is synchronous: it returns
     * when the video exists, so the result is a completed job with the download URL (or a failed job).
     * The duration is not an API parameter, it is put into the prompt.
     *
     * @param request the generation request with the video parameters (resolution, duration, aspect ratio)
     * @return a CompletableFuture containing the completed or failed job info
     * @since 1.8.0
     */
    CompletableFuture<GenerationJobInfo> submitOmniGenerationRequest(ImageGenerationRequest request);
}
