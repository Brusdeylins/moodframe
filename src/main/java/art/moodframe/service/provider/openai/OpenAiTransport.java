package art.moodframe.service.provider.openai;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.net.HttpClients;
import art.moodframe.service.provider.ApiHttpException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The HTTP plumbing the parts of the OpenAI client share: the HTTP clients, the token and base URL (configured, or
 * fixed for a client of {@link OpenAiApiService#withCredentials(String, String)}), the posts with the retry under
 * {@code /v1} and the one mapping of an error answer.
 *
 * @since 2.1.3
 */
final class OpenAiTransport {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiTransport.class);

    /*  A gateway queues the images of one request series, the last one answers after minutes  */
    private static final int TIMEOUT_SECONDS = 3600;
    private static final int RESEARCH_TIMEOUT_SECONDS = 60;
    static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final ApplicationConfig config;
    private final Supplier<OkHttpClient> httpClient;
    private final Supplier<OkHttpClient> researchClient;
    private final Executor io;

    /*  set for a client of withCredentials(), which neither reads nor writes the configuration  */
    private final String fixedKey;
    private volatile String fixedBaseUrl;
    private final boolean detached;

    /*  the configured base URL without path and the one with "/v1" a test found the API at  */
    private volatile String[] versioned = {null, null};

    /**
     * Creates the transport with the token and base URL of the configuration.
     *
     * @param config the application configuration
     * @param io     runs the blocking requests
     */
    OpenAiTransport(ApplicationConfig config, Executor io) {
        this.config = config;
        this.io = io;
        this.fixedKey = null;
        this.fixedBaseUrl = null;
        this.detached = false;

        /*  Built again when the proxy or certificate settings change  */
        HttpClients clients = new HttpClients(config);
        this.httpClient = clients.rebuilding(factory -> factory.okHttp()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build());
        this.researchClient = clients.rebuilding(factory -> httpClient.get().newBuilder()
                .readTimeout(RESEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build());
    }

    /*  a transport with its own token and base URL, sharing the HTTP clients and the executor of the origin  */
    private OpenAiTransport(OpenAiTransport origin, String apiKey, String baseUrl) {
        this.config = origin.config;
        this.io = origin.io;
        this.httpClient = origin.httpClient;
        this.researchClient = origin.researchClient;
        this.fixedKey = apiKey;
        this.fixedBaseUrl = normalized(baseUrl != null ? baseUrl : origin.getBaseUrl());
        this.detached = true;
    }

    /**
     * Gets a transport with another token and base URL, which neither reads nor writes the configuration.
     *
     * @param apiKey  the token
     * @param baseUrl the base URL, null for the one of this transport
     * @return the transport
     */
    OpenAiTransport withCredentials(String apiKey, String baseUrl) {
        return new OpenAiTransport(this, apiKey, baseUrl);
    }

    /** The application configuration. */
    ApplicationConfig config() {
        return config;
    }

    /** Runs the blocking requests. */
    Executor io() {
        return io;
    }

    /** Whether this transport has a token and base URL of its own instead of the configured ones. */
    boolean detached() {
        return detached;
    }

    /** The client of the image generation and the chat. */
    OkHttpClient client() {
        return httpClient.get();
    }

    /** The client of a research, which waits at most 60 s for the answer. */
    Supplier<OkHttpClient> researchClient() {
        return researchClient;
    }

    /**
     * Notes the base URL with {@code /v1} a test found the API of the configured base URL at.
     *
     * @param baseUrl the base URL that answered
     */
    void foundVersioned(String baseUrl) {
        versioned = new String[] {config.getOpenAiBaseUrl(), baseUrl};
    }

    static String normalized(String baseUrl) {
        return baseUrl == null || baseUrl.isBlank() ? ApplicationConfig.OPENAI_DEFAULT_BASE_URL
                : baseUrl.trim().replaceAll("/+$", "");
    }

    Response post(String path, java.util.function.Supplier<RequestBody> body) throws java.io.IOException {
        return post(httpClient.get(), path, body);
    }

    Response post(OkHttpClient client, String path, java.util.function.Supplier<RequestBody> body)
            throws java.io.IOException {
        String baseUrl = getBaseUrl();
        Response response = client.newCall(request(baseUrl, path, body)).execute();
        if (response.code() != 404 || !java.net.URI.create(baseUrl).getPath().isEmpty())
            return response;

        /*  A base URL without a path is often meant as ".../v1"; the model list answers there, the rest does not  */
        String withVersion = baseUrl + "/v1";
        Response retry = client.newCall(request(withVersion, path, body)).execute();
        response.close();
        if (retry.code() == 404)
            return retry;

        logger.info("OpenAI base URL {} has no endpoint {}, using {}", baseUrl, path, withVersion);
        if (detached)
            fixedBaseUrl = withVersion;
        else
            config.setOpenAiBaseUrl(withVersion);
        return retry;
    }

    private Request request(String base, String path, java.util.function.Supplier<RequestBody> body) {
        return new Request.Builder()
                .url(base + path)
                .header("Authorization", "Bearer " + getApiKey())
                .post(body.get())
                .build();
    }

    /**
     * Maps an error answer of the API to an exception with its status and a message for the user, the one error
     * mapping of the OpenAI client.
     *
     * @param code the HTTP status code
     * @param body the answer body
     * @return the exception to throw
     */
    static ApiHttpException httpError(int code, String body) {
        String message = null;
        try {
            message = new JSONObject(body).getJSONObject("error").optString("message", null);
        } catch (JSONException e) {
            /* no JSON error object, the status text below is enough */
        }
        return switch (code) {
            case 401 -> ApiHttpException.localized(code, "openai.error.auth");
            case 429 -> ApiHttpException.localized(code, "openai.error.quota", message != null ? " - " + message : "");
            default -> ApiHttpException.localized(code, "openai.error.http", String.valueOf(code),
                    message != null ? ": " + message : "");
        };
    }

    String getApiKey() {
        return detached ? fixedKey : config.accounts().getApiKey(ApiProvider.OPENAI);
    }

    String getBaseUrl() {
        if (detached)
            return fixedBaseUrl;
        String configured = config.getOpenAiBaseUrl();
        String[] found = versioned;
        return configured.equals(found[0]) ? found[1] : configured;
    }

    boolean isConfigured() {
        String apiKey = getApiKey();
        return apiKey != null && !apiKey.trim().isEmpty();
    }
}
