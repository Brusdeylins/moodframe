package tools.moodframe.service;

import tools.moodframe.model.BrainstormTurn;
import tools.moodframe.model.GenerationJobInfo;
import tools.moodframe.model.ImageGenerationRequest;
import org.json.JSONArray;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for the OpenAI Images API (or an OpenAI-compatible gateway) with bearer token authentication.
 *
 * @author Matthias Brusdeylins
 * @since 1.9.0
 */
public interface OpenAiApiService {

    /**
     * Tests the connection by listing the models ({@code GET /models}). A successful test stores the image
     * models, see {@link #getImageModels()}.
     *
     * @return a future with true if the API accepted the bearer token
     */
    CompletableFuture<Boolean> testConnection();

    /**
     * Gets the image models (for example {@code gpt-image-1}, {@code dall-e-3}) of the last successful connection test.
     *
     * @return the model ids, empty if the API was not connected yet
     */
    List<String> getImageModels();

    /**
     * Generates one image ({@code POST /images/generations}), or edits the attached images
     * ({@code POST /images/edits}) if the request has input images. The call is synchronous: the result carries
     * the image as Base64.
     *
     * @param request the generation request, {@link ImageGenerationRequest#getModel()} is the OpenAI model id
     * @return a future with the finished job
     */
    CompletableFuture<GenerationJobInfo> submitGenerationRequest(ImageGenerationRequest request);

    /**
     * Asks the language model one question of a tool-calling conversation ({@code POST /chat/completions}). The
     * conversation is given in the Gemini format of the brainstorm, with the tool calls and results of the current
     * turn as {@code functionCall} and {@code functionResponse} parts.
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
     * @return the reason why the model does not work, or null if it works
     */
    CompletableFuture<String> checkChatModel(String model);

    /**
     * Asks the language model a single question without a conversation.
     *
     * @param model  the model id
     * @param prompt the question
     * @return the answer text
     */
    CompletableFuture<String> complete(String model, String prompt);

    /**
     * Gets the bearer token.
     *
     * @return the token, or null
     */
    String getApiKey();

    /**
     * Sets the bearer token, for example to test a token that is not saved yet.
     *
     * @param apiKey the bearer token
     */
    void setApiKey(String apiKey);

    /**
     * Gets the base URL.
     *
     * @return the base URL without trailing slash
     */
    String getBaseUrl();

    /**
     * Sets the base URL, for example to test a URL that is not saved yet.
     *
     * @param baseUrl the base URL, for example {@code https://api.openai.com/v1}
     */
    void setBaseUrl(String baseUrl);

    /**
     * Checks if a bearer token is set.
     *
     * @return true if the service can send requests
     */
    boolean isConfigured();
}
