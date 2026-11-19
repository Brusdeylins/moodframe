package art.moodframe.service.provider;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.BrainstormTurn;
import art.moodframe.model.GenerationCost;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.model.ModelParameters;
import art.moodframe.model.ResearchNote;
import org.json.JSONArray;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for Google Gemini API interactions: image generation (an answer carries the finished image) and
 * Gemini Omni video through {@link #submitGenerationRequest}, the brainstorm chat and its research.
 *
 * @author Matthias Brusdeylins
 * @since 1.5.0
 */
public interface GeminiApiService extends ImageProvider {

    @Override
    default ApiProvider getProvider() {
        return ApiProvider.GOOGLE_GEMINI;
    }

    /** The prompt limit of Gemini: 65,536 tokens, about 250,000 characters. */
    int MAX_PROMPT_LENGTH = 250_000;

    @Override
    GeminiApiService withCredentials(String apiKey, String endpoint);

    @Override
    default int maxPromptLength(String apiModelId) {
        return MAX_PROMPT_LENGTH;
    }

    /**
     * Sets the image size of the variant, Google Search grounding, the thinking level (only Nano Banana 2 and Lite
     * take one, Nano Banana Pro always thinks) and up to 14 reference images; for an Omni video its resolution, the
     * length (8 seconds without choice), audio and a single video.
     */
    @Override
    default void prepare(ImageGenerationRequest request, GenerationModel model, RequestOptions options) {
        request.setImageSize(imageSize(model));
        if (options.googleSearch() != null)
            request.setEnableGoogleSearch(options.googleSearch());
        request.setThinkingMode(model.parameter(ModelParameters.THINKING_LEVEL.name()) != null
                && options.thinkingLevel() != null
                ? options.thinkingLevel().toLowerCase() : null);
        List<String> referenceImages = options.referenceImages();
        if (model.supportsMultiImage() && !referenceImages.isEmpty())
            request.setInputImages(referenceImages);

        if (model.isVideoModel()) {
            request.setVideoResolution(model.getVideoResolution());
            request.setVideoDuration(options.videoSeconds() != null ? options.videoSeconds()
                    : ModelParameters.DEFAULT_VIDEO_SECONDS);
            request.setGenerateAudio(true);
            request.setImageCount(1);
        }
    }

    /**
     * Gets the imageSize parameter of the Gemini API for a variant: 512, 1K, 2K or 4K; Lite is capped at 1K.
     *
     * @param model the model
     * @return the image size
     */
    static String imageSize(GenerationModel model) {
        return switch (model) {
            case GEMINI_PRO_2K, NANO_BANANA2_2K -> "2K";
            case GEMINI_PRO_4K, NANO_BANANA2_4K -> "4K";
            case NANO_BANANA2_512 -> "512";
            default -> "1K";
        };
    }

    /** The Google Search grounding of the request and the thinking tokens the answer reports. */
    @Override
    default ExtraCosts extraCosts(ImageGenerationRequest request, GenerationJobInfo job) {
        return new ExtraCosts(GenerationCost.search(GenerationModel.fromApiModelId(request.getModel()),
                request.getEnableGoogleSearch()), GenerationCost.reasoning(job.getThinkingTokens()));
    }

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
     * Checks that the brainstorm language model answers a minimal request.
     *
     * @return a CompletableFuture containing the reason why the model does not answer, or null if it works; it
     *         completes exceptionally if the check could not decide (no network, timeout, HTTP 429 or 5xx)
     * @since 2.1.2
     */
    CompletableFuture<String> checkChatModel();

    /**
     * Checks that the brainstorm language model can search the web with Google Search: a research request for the
     * current time has to yield at least one source, asked again once like {@link #research(String)}.
     *
     * @return a CompletableFuture containing the reason why the search does not work, or null if it works; it
     *         completes exceptionally if the check could not decide (no network, timeout, HTTP 429 or 5xx)
     * @since 2.1.2
     */
    CompletableFuture<String> checkWebSearch();
}
