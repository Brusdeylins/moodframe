package art.moodframe.service.provider.gemini;

import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The shared transport of the Gemini clients: the HTTP client, the base URL, the key header and the executor of the
 * blocking requests. The key and the base URL are asked for on every request, so a client with other credentials
 * shares everything else.
 *
 * @since 2.1.3
 */
final class GeminiTransport {

    private static final String KEY_HEADER = "x-goog-api-key";

    private final Supplier<OkHttpClient> httpClient;
    private final Supplier<String> baseUrl;
    private final Supplier<String> apiKey;
    private final Executor io;

    /**
     * Creates the transport.
     *
     * @param httpClient the HTTP client, built again when the network settings change
     * @param baseUrl    the base URL of the Gemini API
     * @param apiKey     the key, null or blank if none is set
     * @param io         runs the blocking requests
     */
    GeminiTransport(Supplier<OkHttpClient> httpClient, Supplier<String> baseUrl, Supplier<String> apiKey,
            Executor io) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.io = io;
    }

    String baseUrl() {
        return baseUrl.get();
    }

    String apiKey() {
        return apiKey.get();
    }

    boolean hasKey() {
        String key = apiKey.get();
        return key != null && !key.isBlank();
    }

    /** The HTTP client with its default timeouts. */
    OkHttpClient client() {
        return httpClient.get();
    }

    /** The HTTP client with a read timeout of its own. */
    OkHttpClient client(int readTimeoutSeconds) {
        return httpClient.get().newBuilder().readTimeout(readTimeoutSeconds, TimeUnit.SECONDS).build();
    }

    /**
     * Starts a request to a path of the API, with the key as header (never in the URL, so it stays out of logs).
     *
     * @param path the path after the base URL, e.g. {@code /models}
     * @return the request builder
     */
    Request.Builder request(String path) {
        return new Request.Builder().url(baseUrl() + path).header(KEY_HEADER, apiKey());
    }

    /**
     * Runs blocking work on the executor of the requests.
     *
     * @param work the work
     * @param <T>  the result
     * @return its future
     */
    <T> CompletableFuture<T> async(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, io);
    }
}
