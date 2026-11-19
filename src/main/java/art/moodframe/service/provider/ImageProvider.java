package art.moodframe.service.provider;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationJobInfo;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A provider of image and video generation: what the generation, the account checks and the MCP server need of every
 * provider. The client interface of a provider extends it and keeps its own extras (the polling of BFL jobs, the
 * brainstorm chat of Gemini and OpenAI); the {@link ProviderRegistry} finds the provider of an {@link ApiProvider}.
 *
 * <p><strong>Threading:</strong> the methods returning a future do not block, the blocking HTTP work runs on
 * {@link art.moodframe.net.IoExecutor} (BFL: on the threads of its HTTP client). The futures complete on such a
 * worker thread, never on the JavaFX application thread: a caller that touches the UI or a JavaFX-bound object hops
 * to it with {@code Platform.runLater}. Callbacks chained without an executor run on the completing worker.</p>
 *
 * @since 2.1.3
 */
public interface ImageProvider {

    /**
     * Gets the provider this client talks to.
     *
     * @return the provider
     */
    ApiProvider getProvider();

    /**
     * Checks if the client has a key and can send requests.
     *
     * @return true if a key is set
     */
    boolean isConfigured();

    /**
     * Gets the key the client sends.
     *
     * @return the key, or null if none is set
     */
    String getApiKey();

    /**
     * Tests the connection and the key with a lightweight request.
     *
     * @return a future with true if the key works, false on a definite refusal (HTTP 400, 401, 403 or 404); it fails
     *         on a transient failure (no network, timeout, HTTP 429 or 5xx)
     */
    CompletableFuture<Boolean> testConnection();

    /**
     * Gets a client like this one that sends another key, e.g. to test a key that is not saved yet. This client keeps
     * its own key and endpoint, so generation and brainstorm are not affected.
     *
     * @param apiKey   the key
     * @param endpoint where the copy sends to: the region of BFL ({@code Global}, {@code EU}, {@code US}), the base
     *                 URL of OpenAI; null for the configured one; Gemini has a single endpoint and ignores it
     * @return the copy
     */
    ImageProvider withCredentials(String apiKey, String endpoint);

    /**
     * Tests a key that is not saved yet, without changing this client: see {@link #withCredentials(String, String)}
     * and {@link #testConnection()}.
     *
     * @param apiKey   the key to test
     * @param endpoint the region of BFL or the base URL of OpenAI, null for the configured one
     * @return a future with true if the key works, false on a definite refusal; it fails on a transient failure
     */
    default CompletableFuture<Boolean> testConnection(String apiKey, String endpoint) {
        return withCredentials(apiKey, endpoint).testConnection();
    }

    /**
     * Gets the prompt limit of a model in characters.
     *
     * @param apiModelId the API model id (may be null)
     * @return the limit
     */
    int maxPromptLength(String apiModelId);

    /**
     * Checks that the prompt of a request is within the {@link #maxPromptLength(String) limit} of its model.
     *
     * @param request the request, {@link ImageGenerationRequest#getModel()} is the API model id
     * @throws IllegalArgumentException if the prompt is longer
     */
    default void checkPromptLength(ImageGenerationRequest request) {
        int limit = maxPromptLength(request.getModel());
        if (request.getPrompt() != null && request.getPrompt().length() > limit)
            throw new IllegalArgumentException(String.format("Prompt cannot exceed %d characters (%s limit)", limit,
                    getProvider().getDisplayName()));
    }

    /**
     * Sets what the model of this provider takes on a request whose prompt, size and general parameters are set:
     * resolution tier, reference images, Google Search, thinking level, FLUX settings, video parameters.
     *
     * @param request the request, {@link ImageGenerationRequest#getModel()} is the API model id
     * @param model   the model
     * @param options what the order asks for
     */
    void prepare(ImageGenerationRequest request, GenerationModel model, RequestOptions options);

    /**
     * Gets the headers a download of a file of this provider needs, e.g. the key for a file of the provider's API.
     * The key goes as a header, so it does not show up in the URL or the log.
     *
     * @param url the URL of the file
     * @return the headers, empty if none
     */
    default Map<String, String> downloadHeaders(String url) {
        return Map.of();
    }

    /**
     * Submits one generation request. A provider that {@link #answersWithImage() answers with the image} completes
     * with the finished (or failed) job, the others with the job to poll. For a video model
     * ({@link GenerationModel#isVideoModel()}) the future completes when the video exists, with the job that carries
     * its {@link GenerationJobInfo#getVideoDownloadUrl() download URL} (or a failed job); the download takes the
     * {@link #downloadHeaders(String) headers} of the provider.
     *
     * @param request the request, {@link ImageGenerationRequest#getModel()} is the API model id
     * @return a future with the job
     */
    CompletableFuture<GenerationJobInfo> submitGenerationRequest(ImageGenerationRequest request);

    /**
     * Checks if {@link #submitGenerationRequest(ImageGenerationRequest)} answers with the finished image instead of a
     * job that is polled until it is done.
     *
     * @return true if the answer carries the image
     */
    default boolean answersWithImage() {
        return true;
    }

    /**
     * Checks if the images of this provider have a seed that repeats the image.
     *
     * @return true if the provider takes and reports seeds
     */
    default boolean usesSeeds() {
        return false;
    }

    /**
     * Checks if this provider serves a model id that is no constant of
     * {@link art.moodframe.model.GenerationModel}, e.g. a model listed by an OpenAI-compatible gateway.
     *
     * @param modelId the API model id, not null
     * @return true if the id is one of this provider
     */
    default boolean servesModel(String modelId) {
        return false;
    }

    /**
     * Checks that a model of this provider takes an attached image.
     *
     * @param modelId the API model id (may be null)
     * @throws IllegalArgumentException if the model takes no image
     */
    default void checkImageInput(String modelId) {
    }
}
