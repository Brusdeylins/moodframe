package art.moodframe.service.provider;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.BrainstormTurn;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.model.ModelFamilies;
import art.moodframe.model.ResearchNote;
import org.json.JSONArray;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for the OpenAI Images API (or an OpenAI-compatible gateway) with bearer token authentication.
 * A generation answers with the finished image as Base64: {@code POST /images/generations}, or
 * {@code POST /images/edits} if the request has input images.
 *
 * @author Matthias Brusdeylins
 * @since 1.9.0
 */
public interface OpenAiApiService extends ImageProvider {

    @Override
    default ApiProvider getProvider() {
        return ApiProvider.OPENAI;
    }

    /**
     * Serves the OpenAI image models and every model of the gateway the user chose, see {@link #getImageModels()}.
     */
    @Override
    default boolean servesModel(String modelId) {
        return GenerationModel.isOpenAiImageModelId(modelId) || getImageModels().contains(modelId);
    }

    /**
     * Tests the connection by listing the models ({@code GET /models}). A successful test stores the models of the
     * API and the image models, see {@link #getImageModels()}; a client of {@link #withCredentials(String, String)}
     * stores nothing.
     *
     * @return a future with true if the API accepted the bearer token, false on a definite refusal (HTTP 400, 401, 403
     *         or 404); it fails on a transient failure (no network, timeout, HTTP 429 or 5xx)
     */
    @Override
    CompletableFuture<Boolean> testConnection();

    @Override
    OpenAiApiService withCredentials(String apiKey, String endpoint);

    /** The prompt limit of the model: 1,000 to 32,000 characters, see {@link #promptLimitOf(String)}. */
    @Override
    default int maxPromptLength(String apiModelId) {
        return promptLimitOf(apiModelId);
    }

    /**
     * Gets the prompt limit in characters of an OpenAI image model.
     *
     * @param modelId the OpenAI model id (may be null)
     * @return 32,000 for gpt-image models and the models of a gateway, 4,000 for dall-e-3 and 1,000 for dall-e-2
     */
    static int promptLimitOf(String modelId) {
        String lower = modelId == null ? "" : modelId.toLowerCase(java.util.Locale.ROOT);
        if (lower.equals("dall-e-2"))
            return 1000;
        return lower.startsWith("dall-e") ? 4000 : 32000;
    }

    /**
     * Sets the size of a model of an OpenAI-compatible gateway, the free size closest to the target resolution, and
     * the reference images, which go to {@code /images/edits}.
     */
    @Override
    default void prepare(ImageGenerationRequest request, GenerationModel model, RequestOptions options) {
        if (model != GenerationModel.OPENAI_IMAGE)
            return;
        List<String> freeResolutions = ModelFamilies.freeResolutions(options.item());
        if (freeResolutions != null)
            request.setImageSize(ModelFamilies.nearest(options.targetResolution(), freeResolutions));
        if (!options.referenceImages().isEmpty())
            request.setInputImages(options.referenceImages());
    }

    /**
     * What a test of a token and a base URL found.
     *
     * @param works    true if the API accepted the token
     * @param baseUrl  the base URL that answered, with {@code /v1} added if the given one has no path and answers
     *                 there
     * @param models   the model ids of the API, sorted
     * @param selected the image models to preselect: the ones selected so far that the API has, else those with a
     *                 known image name
     * @since 2.1.3
     */
    record Account(boolean works, String baseUrl, List<String> models, List<String> selected) {
    }

    /**
     * Tests a token and a base URL that are not saved yet, without changing this client or the configuration.
     *
     * @param apiKey  the bearer token
     * @param baseUrl the base URL, null for the configured one
     * @return a future with what the test found; it fails on a transient failure (no network, timeout, HTTP 429 or
     *         5xx)
     * @since 2.1.3
     */
    CompletableFuture<Account> testAccount(String apiKey, String baseUrl);

    /**
     * Gets the image models (for example {@code gpt-image-1}, {@code dall-e-3}) of the last successful connection test.
     *
     * @return the model ids, empty if the API was not connected yet
     */
    List<String> getImageModels();

    /**
     * Asks the language model one question of a tool-calling conversation ({@code POST /chat/completions}). The
     * conversation is given in the Gemini format of the brainstorm, with the tool calls and results of the current
     * turn as {@code functionCall} and {@code functionResponse} parts, and images as {@code inlineData} parts of a
     * user turn.
     *
     * @param model        the model id
     * @param systemPrompt the system prompt
     * @param contents     the conversation, see {@link GeminiApiService#chatTurn}
     * @param withTools    offer the brainstorm tools
     * @param noMoreCalls  the model has to answer now, no more tool calls
     * @param videoTool    also offer {@code analyze_video} (it runs on Gemini)
     * @return the answer: text, or tool calls
     */
    CompletableFuture<BrainstormTurn> chatTurn(String model, String systemPrompt, JSONArray contents,
            boolean withTools, boolean noMoreCalls, boolean videoTool);

    /**
     * Checks that a language model can serve the brainstorm: it answers a plain question and calls a function.
     *
     * @param model the model id
     * @return the reason why the model does not work, or null if it works; completes exceptionally if the check
     *         could not decide (no network, timeout, HTTP 429 or 5xx)
     */
    CompletableFuture<String> checkChatModel(String model);

    /**
     * Researches a question with the built-in web search of the Responses API ({@code POST /responses}).
     *
     * @param model the model id
     * @param query what to find out
     * @return the found text and its sources
     * @since 2.1.2
     */
    CompletableFuture<ResearchNote> research(String model, String query);

    /**
     * Checks that a language model can search the web with the built-in web search of the Responses API.
     *
     * @param model the model id
     * @return the reason why the search does not work, or null if it works; completes exceptionally if the check
     *         could not decide (no network, timeout, HTTP 429 or 5xx)
     * @since 2.1.2
     */
    CompletableFuture<String> checkWebSearch(String model);

    /**
     * Asks the language model a single question without a conversation.
     *
     * @param model  the model id
     * @param prompt the question
     * @return the answer text
     */
    CompletableFuture<String> complete(String model, String prompt);

    /**
     * Gets the base URL: the configured one, with {@code /v1} if a test or request found the API there.
     *
     * @return the base URL without trailing slash
     */
    String getBaseUrl();
}
