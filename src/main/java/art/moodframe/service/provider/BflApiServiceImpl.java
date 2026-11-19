package art.moodframe.service.provider;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationJobInfo;
import art.moodframe.model.GenerationJobResult;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.net.HttpClients;
import art.moodframe.net.IoExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Implementation of the BFL API service using java.net.http with corporate proxy support.
 * 
 * <p>This service provides HTTP client functionality for interacting with
 * the BlackForest Labs API, including authentication, request/response
 * handling, error management, and full corporate proxy integration.</p>
 * 
 * <p><strong>Authentication:</strong> Uses X-Key header with API key from
 * ApplicationConfig. Supports multi-source API key resolution (environment,
 * system properties, configuration file).</p>
 * 
 * <p><strong>Corporate Proxy Support:</strong> Full integration with corporate
 * proxies including ZScaler, with custom certificate support and proxy authentication.</p>
 * 
 * <p><strong>Error Handling:</strong> Comprehensive error handling with
 * retry logic, timeout management, and detailed error responses.</p>
 * 
 * <p><strong>External References:</strong></p>
 * <ul>
 * <li><a href="https://docs.bfl.ai">BFL API Documentation</a></li>
 * <li><a href="https://docs.anthropic.com/claude-code/corporate-proxy">Corporate Proxy Documentation</a></li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class BflApiServiceImpl implements BflApiService {
    
    private static final Logger logger = LoggerFactory.getLogger(BflApiServiceImpl.class);
    
    private static final String DEFAULT_BASE_URL = "https://api.bfl.ai";
    private static final String USER_AGENT = "MoodFrame/2.0";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(600); // 10 minutes default for image generation
    
    // API Endpoints
    private static final String ENDPOINT_MY_FINETUNES = "/v1/my_finetunes";
    private static final String ENDPOINT_GET_RESULT = "/v1/get_result";

    // API Endpoint of the default model, the others follow from the model id (see getEndpointForModel)
    private static final String ENDPOINT_FLUX3_IMAGE = "/v1/flux-3-image";
    
    private final Supplier<HttpClient> httpClient;
    private final ObjectMapper objectMapper;
    private final ApplicationConfig config;
    private final HttpClients httpClients;
    private final Executor io;

    /*  the key and region of a client of withCredentials(), null for the configured ones  */
    private final String fixedKey;
    private final String fixedRegion;
    
    /**
     * Creates a new BFL API service implementation with corporate proxy support.
     * 
     * <p>This constructor initializes the HTTP client with full corporate proxy
     * support including ZScaler certificates and proxy authentication.</p>
     *
     * @param config the application configuration (key, region, timeouts, proxy)
     */
    public BflApiServiceImpl(ApplicationConfig config) {
        this(config, IoExecutor.get());
    }

    /**
     * Creates a new BFL API service implementation with corporate proxy support.
     *
     * @param config the application configuration (key, region, timeouts, proxy)
     * @param io     runs the blocking requests; the asynchronous ones run on the threads of the HTTP client
     */
    public BflApiServiceImpl(ApplicationConfig config, Executor io) {
        this.config = config;
        this.io = io;
        this.objectMapper = new ObjectMapper();
        this.httpClients = new HttpClients(config);
        this.fixedKey = null;
        this.fixedRegion = null;

        // HTTP client with corporate proxy support, built again when the proxy settings change
        this.httpClient = httpClients.rebuilding(clients -> clients.javaHttp()
            .connectTimeout(DEFAULT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build());
    }

    /*  a client with its own key and region, sharing the HTTP client and the executor of the origin  */
    private BflApiServiceImpl(BflApiServiceImpl origin, String apiKey, String region) {
        this.config = origin.config;
        this.io = origin.io;
        this.objectMapper = origin.objectMapper;
        this.httpClients = origin.httpClients;
        this.httpClient = origin.httpClient;
        this.fixedKey = apiKey;
        this.fixedRegion = region;
    }

    @Override
    public BflApiService withCredentials(String apiKey, String endpoint) {
        return new BflApiServiceImpl(this, apiKey, endpoint);
    }

    @Override
    public String getApiKey() {
        if (fixedKey != null)
            return fixedKey;
        return config != null ? config.accounts().getApiKey(ApiProvider.BFL) : null;
    }
    
    @Override
    public CompletableFuture<Boolean> testConnectionWithProxy() {
        
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(ProviderException.noKey("BFL"));
        }
        
        if (!httpClients.getProxyConfig().isProxyConfigured()) {
            logger.debug("Corporate proxy not configured - falling back to regular connection test");
            return testConnection();
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getApiBaseUrl() + ENDPOINT_MY_FINETUNES))
                    .header("X-Key", getApiKey())
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(15)) // Shorter timeout for proxy test
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.get().send(request,
                    HttpResponse.BodyHandlers.ofString());
                
                long responseTime = System.currentTimeMillis() - startTime;
                
                boolean success = response.statusCode() == 200;
                
                if (success) {
                    // Corporate proxy connection test successful
                } else {
                    logger.warn("Corporate proxy connection test failed: HTTP {} (Response time: {} ms)", 
                        response.statusCode(), responseTime);
                }
                
                return success;
                
            } catch (Exception e) {
                logger.error("Corporate proxy connection test failed: {}", e.getMessage(), e);
                return false;
            }
        }, io);
    }

    @Override
    public CompletableFuture<Boolean> testSslConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                /*  No API key needed — just verify SSL handshake with BFL API host  */
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getApiBaseUrl() + "/"))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(15))
                    .build();

                HttpResponse<Void> response = httpClient.get().send(request,
                    HttpResponse.BodyHandlers.discarding());

                /*  Any HTTP response means SSL handshake succeeded  */
                logger.info("SSL connection test succeeded (HTTP {})", response.statusCode());
                return true;

            }
            catch (javax.net.ssl.SSLException e) {
                logger.warn("SSL connection test failed (SSL error): {}", e.getMessage());
                throw new RuntimeException(e);
            }
            catch (Exception e) {
                logger.warn("SSL connection test failed: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }, io);
    }

    @Override
    public CompletableFuture<Boolean> testConnection() {
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(ProviderException.noKey("BFL"));
        }
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(getApiBaseUrl() + ENDPOINT_MY_FINETUNES))
            .header("X-Key", getApiKey())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .timeout(getTimeout())
            .GET()
            .build();
        
        return httpClient.get().sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenCompose(this::handleResponse)
            .thenApply(body -> {
                return true;
            })
            .exceptionally(throwable -> {
                logger.warn("API connection test failed: {}", throwable.getMessage());

                /*  Only a definite answer (HTTP 400, 401, 403, 404) decides, any other failure is transient  */
                Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                if (ApiHttpException.isDefinite(cause))
                    return false;
                throw throwable instanceof java.util.concurrent.CompletionException completion
                        ? completion : new java.util.concurrent.CompletionException(throwable);
            });
    }

    @Override
    public CompletableFuture<GenerationJobInfo> submitGenerationRequest(ImageGenerationRequest request) {
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(ProviderException.noKey("BFL"));
        }
        
        // Validate request
        try {
            request.validate();
            checkPromptLength(request);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }
        
        
        // Determine the correct endpoint based on model
        String endpoint = getEndpointForModel(request.getModel());
        
        logger.debug("Submitting to endpoint: {} for model: {}", endpoint, request.getModel());
        logger.debug("Request dimensions: {}x{}", request.getWidth(), request.getHeight());
        
        try {
            // Build the model-specific request body: FLUX 3 and FLUX.2 have different parameter sets
            String requestBody = BflRequestBody.json(request, objectMapper);

            // Log input images
            int imageCount = request.getInputImageCount();
            if (imageCount > 0) {
                logger.info("Request includes {} input image(s)", imageCount);
            } else {
                logger.info("No input images in request");
            }

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(getApiBaseUrl() + endpoint))
                .header("X-Key", getApiKey())
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(getTimeout())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            
            return httpClient.get().sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenCompose(this::handleResponse)
                .thenApply(body -> {
                    try {
                        GenerationJobInfo jobInfo = objectMapper.readValue(body, GenerationJobInfo.class);
                        return jobInfo;
                    } catch (Exception e) {
                        throw ProviderException.localized(e, "bfl.error.response", e.getMessage());
                    }
                });
                
        } catch (Exception e) {
            return CompletableFuture.failedFuture(ProviderException.localized(e, "bfl.error.request", e.getMessage()));
        }
    }
    
    @Override
    public CompletableFuture<GenerationJobResult> pollJob(GenerationJobInfo job) {
        String pollingUrl = job.getPollingUrl();
        if (pollingUrl != null && !pollingUrl.isEmpty())
            return getJobResultWithPollingUrl(pollingUrl);

        /*  The job id alone may answer HTTP 404 if another region runs the job  */
        logger.warn("No polling URL available for job {}, using fallback method", job.getJobId());
        return getJobStatus(job.getJobId());
    }

    @Override
    public CompletableFuture<GenerationJobResult> getJobStatus(String jobId) {
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(ProviderException.noKey("BFL"));
        }
        
        if (jobId == null || jobId.trim().isEmpty()) {
            return CompletableFuture.failedFuture(ProviderException.localized("bfl.error.no.job"));
        }
        
        // CRITICAL: We need the polling_url from the original request, not a constructed URL
        // This method should be called with the polling_url, not just the job ID
        // For now, we'll construct the URL as fallback, but this causes 404 errors!
        String encodedJobId = java.net.URLEncoder.encode(jobId, java.nio.charset.StandardCharsets.UTF_8);
        String statusUrl = getApiBaseUrl() + ENDPOINT_GET_RESULT + "?id=" + encodedJobId;
        
        logger.warn("Using constructed polling URL instead of polling_url from response - this may cause 404 errors: {}", statusUrl);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(statusUrl))
            .header("X-Key", getApiKey())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .timeout(getTimeout())
            .GET()
            .build();
        
        return httpClient.get().sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenCompose(this::handlePollingResponse)
            .thenApply(body -> {
                try {
                    GenerationJobResult result = objectMapper.readValue(body, GenerationJobResult.class);
                    return result;
                } catch (Exception e) {
                    throw ProviderException.localized(e, "bfl.error.response", e.getMessage());
                }
            });
    }
    
    @Override
    public boolean isConfigured() {
        return getApiKey() != null && !getApiKey().trim().isEmpty();
    }
    
    @Override
    public String getApiBaseUrl() {
        if (config != null) {
            // Use API region to determine the correct endpoint
            String region = fixedRegion != null ? fixedRegion : config.getApiRegion();
            switch (region) {
                case "EU":
                    return "https://api.eu.bfl.ai";
                case "US":
                    return "https://api.us.bfl.ai";
                case "Global":
                default:
                    return DEFAULT_BASE_URL; // Global endpoint
            }
        }
        return DEFAULT_BASE_URL;
    }
    
    /**
     * Gets the configured timeout duration.
     * 
     * @return the timeout duration
     */
    private Duration getTimeout() {
        if (config != null) {
            int timeoutSeconds = config.getApiTimeoutSeconds();
            logger.debug("Using configured timeout: {} seconds", timeoutSeconds);
            return Duration.ofSeconds(timeoutSeconds);
        }
        logger.debug("Using default timeout: {} seconds", DEFAULT_TIMEOUT.getSeconds());
        return DEFAULT_TIMEOUT;
    }
    
    /**
     * Determines the API endpoint of a model: {@code /v1/} and the id with dashes for dots, e.g.
     * {@code flux.2-max} gives {@code /v1/flux-2-max}.
     * 
     * @param model the model identifier
     * @return the API endpoint path, that of FLUX 3 Image for an unknown model
     */
    static String getEndpointForModel(String model) {
        GenerationModel known = model != null ? GenerationModel.fromApiModelId(model.toLowerCase()) : null;
        if (known == null || known.getProvider() != ApiProvider.BFL)
            return ENDPOINT_FLUX3_IMAGE; // Default to the latest model
        return "/v1/" + known.getApiModelId().replace('.', '-');
    }
    
    /**
     * Gets job result using the correct polling URL from the original response.
     * This method should be used instead of getJobResult(String jobId) to avoid 404 errors.
     * 
     * @param pollingUrl the polling URL returned from the original generation request
     * @return CompletableFuture with GenerationJobResult
     */
    @Override
    public CompletableFuture<GenerationJobResult> getJobResultWithPollingUrl(String pollingUrl) {
        if (pollingUrl == null || pollingUrl.trim().isEmpty()) {
            return CompletableFuture.failedFuture(ProviderException.localized("bfl.error.no.job"));
        }
        
        logger.debug("Polling job status using correct polling URL: {}", pollingUrl);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(pollingUrl))
            .header("X-Key", getApiKey())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .timeout(getTimeout())
            .GET()
            .build();
        
        return httpClient.get().sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenCompose(this::handlePollingResponse)
            .thenApply(body -> {
                try {
                    GenerationJobResult result = objectMapper.readValue(body, GenerationJobResult.class);
                    return result;
                } catch (Exception e) {
                    throw ProviderException.localized(e, "bfl.error.response", e.getMessage());
                }
            });
    }
    
    /**
     * Handles HTTP response for polling requests. All HTTP errors including 404 are treated as failures.
     * 
     * @param response the HTTP response
     * @return CompletableFuture with response body or failure for any error status
     */
    private CompletableFuture<String> handlePollingResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        String body = response.body();
        
        logger.debug("BFL polling response: HTTP {}", statusCode);
        
        if (statusCode >= 200 && statusCode < 300) {
            return CompletableFuture.completedFuture(body);
        }

        /*  FLUX 3 can report a failed task as HTTP 503 with a normal body, its status ends the polling  */
        if (statusCode == 503 && body != null && body.contains("\"status\"")) {
            return CompletableFuture.completedFuture(body);
        }

        // All error responses (including 404) are failures - no synthetic "not_found" status; the polling flow
        // logs them: a 404 is normal until the job is registered
        String errorMessage = extractErrorMessage(body, statusCode);
        logger.debug("BFL polling error [{}]: {}", statusCode, errorMessage);
        
        return CompletableFuture.failedFuture(
            new BflApiException(statusCode, errorMessage, body));
    }
    
    /**
     * Handles HTTP responses and error conditions.
     * 
     * @param response the HTTP response
     * @return a CompletableFuture with the response body
     */
    private CompletableFuture<String> handleResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        String body = response.body();
        
        
        if (statusCode >= 200 && statusCode < 300) {
            return CompletableFuture.completedFuture(body);
        }
        
        // Handle error responses; the caller logs the failure
        String errorMessage = extractErrorMessage(body, statusCode);
        logger.debug("BFL API error [{}]: {}", statusCode, errorMessage);
        
        return CompletableFuture.failedFuture(
            new BflApiException(statusCode, errorMessage, body));
    }
    
    /**
     * Extracts error message from response body.
     * 
     * @param body the response body
     * @param statusCode the HTTP status code
     * @return a descriptive error message
     */
    private String extractErrorMessage(String body, int statusCode) {
        try {
            // Try to parse error response JSON
            var errorNode = objectMapper.readTree(body);
            if (errorNode.has("error")) {
                return errorNode.get("error").asText();
            }
            if (errorNode.has("message")) {
                return errorNode.get("message").asText();
            }
        } catch (Exception e) {
            // If JSON parsing fails, fall back to raw body
        }
        
        // Fallback error messages based on status code
        switch (statusCode) {
            case 400:
                return "Bad Request - Invalid request parameters";
            case 401:
                return "Unauthorized - Invalid or missing API key";
            case 403:
                return "Forbidden - Insufficient permissions or quota exceeded";
            case 404:
                return "Not Found - Resource or endpoint not found";
            case 429:
                return "Too Many Requests - Rate limit exceeded";
            case 500:
                return "Internal Server Error - BFL API service error";
            case 502:
                return "Bad Gateway - BFL API service unavailable";
            case 503:
                return "Service Unavailable - BFL API temporarily unavailable";
            default:
                return String.format("HTTP %d - %s", statusCode, 
                    body.isEmpty() ? "Unknown error" : body);
        }
    }
}
