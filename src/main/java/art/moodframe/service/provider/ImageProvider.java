package art.moodframe.service.provider;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationJobInfo;
import art.moodframe.model.ImageGenerationRequest;

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
     * Sets the key the client sends, e.g. to test a key that is not saved yet.
     *
     * @param apiKey the key
     */
    void setApiKey(String apiKey);

    /**
     * Tests the connection and the key with a lightweight request.
     *
     * @return a future with true if the key works, false on a definite refusal (HTTP 400, 401, 403 or 404); it fails
     *         on a transient failure (no network, timeout, HTTP 429 or 5xx)
     */
    CompletableFuture<Boolean> testConnection();

    /**
     * Submits one generation request. A provider that {@link #answersWithImage() answers with the image} completes
     * with the finished (or failed) job, the others with the job to poll.
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
