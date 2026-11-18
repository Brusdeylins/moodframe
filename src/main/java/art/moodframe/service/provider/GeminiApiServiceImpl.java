package art.moodframe.service.provider;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.BrainstormTurn;
import art.moodframe.model.GenerationJobInfo;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.model.ResearchNote;
import art.moodframe.net.HttpClients;
import art.moodframe.net.IoExecutor;
import okhttp3.OkHttpClient;
import org.json.JSONArray;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Implementation of the Gemini client with OkHttp: a facade over the image, Omni video and chat clients, which share
 * one {@link GeminiTransport} and the error mapping of {@link GeminiErrors}. The blocking requests run on the
 * executor of the constructor. The key is the saved one of the configuration, read for every request.
 *
 * @author Matthias Brusdeylins
 * @since 1.5.0
 */
public class GeminiApiServiceImpl implements GeminiApiService {

    private static final String GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final int TIMEOUT_SECONDS = 120; // 2 minutes for image generation

    private final ApplicationConfig config;
    private final Supplier<OkHttpClient> httpClient;
    private final Executor io;
    private final Supplier<String> baseUrl;
    private final String fixedKey;

    private final GeminiImageClient image;
    private final GeminiOmniClient omni;
    private final GeminiChatClient chat;

    /**
     * Creates a new Gemini API service implementation that sends its requests on the {@link IoExecutor}.
     *
     * @param config the application configuration
     */
    public GeminiApiServiceImpl(ApplicationConfig config) {
        this(config, IoExecutor.get());
    }

    /**
     * Creates a new Gemini API service implementation.
     *
     * @param config the application configuration
     * @param io     runs the blocking requests
     */
    public GeminiApiServiceImpl(ApplicationConfig config, Executor io) {
        // HTTP client with timeouts, corporate proxy and certificates, built again when the settings change
        this(config, new HttpClients(config).rebuilding(clients -> clients.okHttp()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()), io, null, null);
    }

    /*  a client of its own key, sharing the HTTP client, the executor and the base URL of the origin  */
    private GeminiApiServiceImpl(ApplicationConfig config, Supplier<OkHttpClient> httpClient, Executor io,
            Supplier<String> baseUrl, String fixedKey) {
        this.config = config;
        this.httpClient = httpClient;
        this.io = io;
        this.baseUrl = baseUrl != null ? baseUrl : this::getApiBaseUrl;
        this.fixedKey = fixedKey;

        GeminiTransport transport = new GeminiTransport(httpClient, this.baseUrl, this::getApiKey, io);
        this.image = new GeminiImageClient(transport);
        this.omni = new GeminiOmniClient(transport);
        this.chat = new GeminiChatClient(transport);
    }

    @Override
    public GeminiApiService withCredentials(String apiKey, String endpoint) {
        return new GeminiApiServiceImpl(config, httpClient, io, baseUrl, apiKey);
    }

    /**
     * The files of the Gemini API (an Omni video) answer HTTP 403 without the key.
     */
    @Override
    public Map<String, String> downloadHeaders(String url) {
        String key = getApiKey();
        return url != null && url.startsWith("https://generativelanguage.googleapis.com/") && key != null
                && !key.isBlank() ? Map.of("x-goog-api-key", key) : Map.of();
    }

    @Override
    public CompletableFuture<Boolean> testConnection() {
        return image.testConnection();
    }

    @Override
    public CompletableFuture<GenerationJobInfo> submitGenerationRequest(ImageGenerationRequest request) {
        return image.submit(request);
    }

    @Override
    public CompletableFuture<GenerationJobInfo> submitOmniGenerationRequest(ImageGenerationRequest request) {
        return omni.submit(request);
    }

    @Override
    public CompletableFuture<String> analyzeYouTubeVideo(String youtubeUrl, int startSeconds, int endSeconds,
            String analysisPrompt) {
        return chat.analyzeYouTubeVideo(youtubeUrl, startSeconds, endSeconds, analysisPrompt);
    }

    @Override
    public CompletableFuture<BrainstormTurn> chatTurn(String systemPrompt, JSONArray contents, boolean withTools,
            boolean noMoreCalls) {
        return chat.chatTurn(systemPrompt, contents, withTools, noMoreCalls);
    }

    @Override
    public CompletableFuture<String> suggestTitle(String content, String language) {
        return chat.suggestTitle(content, language);
    }

    @Override
    public CompletableFuture<ResearchNote> research(String query) {
        return chat.research(query);
    }

    @Override
    public CompletableFuture<String> checkChatModel() {
        return chat.checkChatModel();
    }

    @Override
    public CompletableFuture<String> checkWebSearch() {
        return chat.checkWebSearch();
    }

    /**
     * Returns the base URL of the Gemini API. The tests override it to answer the requests themselves.
     */
    String getApiBaseUrl() {
        return GEMINI_API_BASE_URL;
    }

    @Override
    public String getApiKey() {
        return fixedKey != null ? fixedKey : config.accounts().getApiKey(ApiProvider.GOOGLE_GEMINI);
    }

    @Override
    public boolean isConfigured() {
        String apiKey = getApiKey();
        return apiKey != null && !apiKey.trim().isEmpty();
    }
}
