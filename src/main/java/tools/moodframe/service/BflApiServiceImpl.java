package tools.moodframe.service;

import tools.moodframe.config.ApplicationConfig;
import tools.moodframe.config.CorporateProxyConfig;
import tools.moodframe.config.ServiceFactory;
import tools.moodframe.model.*;
import tools.moodframe.config.EncryptionUtil;
import tools.moodframe.util.TrustStoreManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

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
    private static final String USER_AGENT = "Moodframe/2.0";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(600); // 10 minutes default for image generation
    
    // API Endpoints
    private static final String ENDPOINT_MY_FINETUNES = "/v1/my_finetunes";
    private static final String ENDPOINT_GET_RESULT = "/v1/get_result";

    // API Endpoints - FLUX 3 Models
    private static final String ENDPOINT_FLUX3_IMAGE = "/v1/flux-3-image";
    private static final String ENDPOINT_FLUX3_IMAGE_KLEIN = "/v1/flux-3-image-klein";

    // API Endpoints - FLUX.2 Models (NEW)
    private static final String ENDPOINT_FLUX2_PRO = "/v1/flux-2-pro";
    private static final String ENDPOINT_FLUX2_MAX = "/v1/flux-2-max";
    private static final String ENDPOINT_FLUX2_FLEX = "/v1/flux-2-flex";
    private static final String ENDPOINT_FLUX2_KLEIN_4B = "/v1/flux-2-klein-4b";
    private static final String ENDPOINT_FLUX2_KLEIN_9B = "/v1/flux-2-klein-9b";
    
    private HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ApplicationConfig config;
    private CorporateProxyConfig proxyConfig;
    private TrustStoreManager trustStoreManager;
    private String cachedApiKey;
    
    /**
     * Creates a new BFL API service implementation with corporate proxy support.
     * 
     * <p>This constructor initializes the HTTP client with full corporate proxy
     * support including ZScaler certificates and proxy authentication.</p>
     */
    public BflApiServiceImpl() {
        this.config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        this.objectMapper = new ObjectMapper();
        this.trustStoreManager = new TrustStoreManager();
        this.proxyConfig = new CorporateProxyConfig(config);
        
        // Initialize HTTP client with corporate proxy support
        initializeHttpClient();
        
    }
    
    @Override
    public void initializeHttpClient() {
        
        try {
            HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(DEFAULT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL);
            
            // Configure corporate proxy if enabled
            if (proxyConfig.isProxyEnabled() && proxyConfig.isProxyConfigurationValid()) {
                configureProxySupport(clientBuilder);
            }
            
            // Configure SSL context with corporate certificates
            configureSSLContext(clientBuilder);
            
            this.httpClient = clientBuilder.build();
            
            
        } catch (Exception e) {
            logger.error("Failed to initialize HTTP client with corporate proxy support", e);
            
            // Fallback to basic HTTP client
            this.httpClient = HttpClient.newBuilder()
                .connectTimeout(DEFAULT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
                
            logger.warn("Using fallback HTTP client without corporate proxy support");
        }
    }
    
    /**
     * Configures proxy support for the HTTP client.
     * 
     * @param clientBuilder the HTTP client builder to configure
     * @since 1.0.0
     */
    private void configureProxySupport(HttpClient.Builder clientBuilder) {
        
        // Create proxy selector for corporate proxy
        ProxySelector proxySelector = new CorporateProxySelector();
        clientBuilder.proxy(proxySelector);
        
        // Configure proxy authentication if credentials are available
        if (proxyConfig.hasProxyAuthentication()) {
            configureProxyAuthentication();
        }
        
    }
    
    /**
     * Configures proxy authentication.
     * 
     * @since 1.0.0
     */
    private void configureProxyAuthentication() {
        // Set up authenticator for proxy credentials
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() == RequestorType.PROXY) {
                    String username = proxyConfig.getProxyUsername();
                    String password = proxyConfig.getProxyPassword();
                    
                    if (username != null && password != null) {
                        return new PasswordAuthentication(username, password.toCharArray());
                    }
                }
                return null;
            }
        });
    }
    
    /**
     * Configures SSL context with corporate certificates.
     * 
     * @param clientBuilder the HTTP client builder to configure
     * @since 1.0.0
     */
    private void configureSSLContext(HttpClient.Builder clientBuilder) {
        try {
            // Load corporate certificates if available
            var certificatePaths = proxyConfig.getCertificatePaths();
            
            if (!certificatePaths.isEmpty()) {
                logger.info("Configuring SSL context with {} corporate certificate(s)", certificatePaths.size());
                
                // Create corporate trust store
                trustStoreManager.createCorporateKeyStore(certificatePaths);
                
                // Create SSL context with corporate certificates
                SSLContext sslContext = trustStoreManager.createSSLContext();
                clientBuilder.sslContext(sslContext);
                
                logger.info("SSL context configured successfully with corporate certificates");
            } else {
                logger.info("Using default SSL context (no corporate certificates configured)");
            }
            
        } catch (Exception e) {
            logger.error("Failed to configure SSL context with corporate certificates", e);
            logger.warn("Proceeding with default SSL context");
        }
    }
    
    /**
     * Custom proxy selector for corporate proxy.
     */
    private class CorporateProxySelector extends ProxySelector {
        @Override
        public java.util.List<Proxy> select(URI uri) {
            if (proxyConfig.isProxyEnabled() && proxyConfig.isProxyConfigurationValid()) {
                Proxy proxy = new Proxy(Proxy.Type.HTTP, 
                    new InetSocketAddress(proxyConfig.getProxyHost(), proxyConfig.getProxyPort()));
                return java.util.List.of(proxy);
            }
            return java.util.List.of(Proxy.NO_PROXY);
        }
        
        @Override
        public void connectFailed(URI uri, SocketAddress sa, java.io.IOException ioe) {
            logger.error("Corporate proxy connection failed for {}: {}", uri, ioe.getMessage());
        }
    }
    
    @Override
    public void refreshProxyConfiguration() {
        
        // Reload proxy configuration
        proxyConfig.loadProxyConfiguration();
        
        // Reinitialize HTTP client with new configuration
        initializeHttpClient();
        
    }
    
    
    @Override
    public String getApiKey() {
        if (cachedApiKey != null && !cachedApiKey.trim().isEmpty()) {
            return cachedApiKey;
        }
        return config.getApiKey();
    }
    
    @Override
    public CompletableFuture<Boolean> testConnectionWithProxy() {
        
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("API key not configured"));
        }
        
        if (!proxyConfig.isProxyConfigured()) {
            if (config.getLoggingEnabled()) {
                logger.warn("Corporate proxy not configured - falling back to regular connection test");
            }
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
                
                HttpResponse<String> response = httpClient.send(request, 
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
        });
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

                HttpResponse<Void> response = httpClient.send(request,
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
        });
    }

    @Override
    public CompletableFuture<Boolean> testConnection() {
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("API key not configured"));
        }
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(getApiBaseUrl() + ENDPOINT_MY_FINETUNES))
            .header("X-Key", getApiKey())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .timeout(getTimeout())
            .GET()
            .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenCompose(this::handleResponse)
            .thenApply(body -> {
                return true;
            })
            .exceptionally(throwable -> {
                logger.warn("API connection test failed: {}", throwable.getMessage());
                return false;
            });
    }
    
    @Override
    public CompletableFuture<String> getMyFinetunes() {
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("API key not configured"));
        }
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(getApiBaseUrl() + ENDPOINT_MY_FINETUNES))
            .header("X-Key", getApiKey())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .timeout(getTimeout())
            .GET()
            .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenCompose(this::handleResponse); // Return the response body
    }
    
    @Override
    public CompletableFuture<GenerationJobInfo> submitGenerationRequest(ImageGenerationRequest request) {
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("API key not configured"));
        }
        
        // Validate request
        try {
            request.validate();
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }
        
        
        // Determine the correct endpoint based on model
        String endpoint = getEndpointForModel(request.getModel());
        
        if (config.getLoggingEnabled()) {
            logger.info("Submitting to endpoint: {} for model: {}", endpoint, request.getModel());
            logger.info("Request dimensions: {}x{}", request.getWidth(), request.getHeight());
        }
        
        try {
            // Build model-specific request body
            // FLUX 3 and FLUX.2 models have different parameter sets
            String requestBody = buildModelSpecificRequestBody(request);

            // Log input images
            int imageCount = request.getInputImageCount();
            if (imageCount > 0) {
                logger.info("Request includes {} input image(s)", imageCount);
                if (request.getInputImage() != null) {
                    logger.debug("  - input_image: {} chars", request.getInputImage().length());
                }
                if (request.getInputImage2() != null) {
                    logger.debug("  - input_image_2: {} chars", request.getInputImage2().length());
                }
                if (request.getInputImage3() != null) {
                    logger.debug("  - input_image_3: {} chars", request.getInputImage3().length());
                }
                // Additional images 4-8 logged at trace level
                if (imageCount > 3) {
                    logger.trace("  - Additional images 4-8 present");
                }
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
            
            return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenCompose(this::handleResponse)
                .thenApply(body -> {
                    try {
                        GenerationJobInfo jobInfo = objectMapper.readValue(body, GenerationJobInfo.class);
                        return jobInfo;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse generation job response", e);
                    }
                });
                
        } catch (Exception e) {
            return CompletableFuture.failedFuture(
                new RuntimeException("Failed to serialize generation request", e));
        }
    }
    
    @Override
    public CompletableFuture<GenerationJobResult> getJobStatus(String jobId) {
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("API key not configured"));
        }
        
        if (jobId == null || jobId.trim().isEmpty()) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Job ID cannot be null or empty"));
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
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenCompose(this::handlePollingResponse)
            .thenApply(body -> {
                try {
                    GenerationJobResult result = objectMapper.readValue(body, GenerationJobResult.class);
                    return result;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse job status response", e);
                }
            });
    }
    
    @Override
    public CompletableFuture<Boolean> cancelJob(String jobId) {
        // Note: BFL API may not support job cancellation
        // This is a placeholder implementation
        if (config.getLoggingEnabled()) {
            logger.warn("Job cancellation not supported by BFL API: {}", jobId);
        }
        return CompletableFuture.completedFuture(false);
    }
    
    @Override
    public boolean isConfigured() {
        return getApiKey() != null && !getApiKey().trim().isEmpty();
    }
    
    @Override
    public String getApiBaseUrl() {
        if (config != null) {
            // Use API region to determine the correct endpoint
            String region = config.getApiRegion();
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
    
    @Override
    public void setApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        
        this.cachedApiKey = apiKey;
        
        // Also update in config if available
        if (config != null) {
            try {
                String encrypted = EncryptionUtil.encrypt(apiKey);
                config.setProperty("api.key", encrypted);
            } catch (Exception e) {
                logger.error("Failed to encrypt and store API key", e);
            }
        }
        
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
     * Determines the correct API endpoint based on the model name.
     * 
     * @param model the model identifier
     * @return the API endpoint path
     */
    private String getEndpointForModel(String model) {
        if (model == null) {
            return ENDPOINT_FLUX3_IMAGE; // Default to the latest model
        }

        switch (model.toLowerCase()) {
            // FLUX 3 Models
            case "flux.3-image-klein":
                return ENDPOINT_FLUX3_IMAGE_KLEIN;
            // FLUX.2 Models
            case "flux.2-pro":
                return ENDPOINT_FLUX2_PRO;
            case "flux.2-max":
                return ENDPOINT_FLUX2_MAX;
            case "flux.2-flex":
                return ENDPOINT_FLUX2_FLEX;
            case "flux.2-klein-4b":
                return ENDPOINT_FLUX2_KLEIN_4B;
            case "flux.2-klein-9b":
                return ENDPOINT_FLUX2_KLEIN_9B;
            case "flux.3-image":
            default:
                return ENDPOINT_FLUX3_IMAGE;
        }
    }
    
    /**
     * Gets job result using the correct polling URL from the original response.
     * This method should be used instead of getJobResult(String jobId) to avoid 404 errors.
     * 
     * @param pollingUrl the polling URL returned from the original generation request
     * @return CompletableFuture with GenerationJobResult
     */
    public CompletableFuture<GenerationJobResult> getJobResultWithPollingUrl(String pollingUrl) {
        if (pollingUrl == null || pollingUrl.trim().isEmpty()) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Polling URL cannot be null or empty"));
        }
        
        if (config.getLoggingEnabled()) {
            logger.debug("Polling job status using correct polling URL: {}", pollingUrl);
        }
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(pollingUrl))
            .header("X-Key", getApiKey())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .timeout(getTimeout())
            .GET()
            .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenCompose(this::handlePollingResponse)
            .thenApply(body -> {
                try {
                    GenerationJobResult result = objectMapper.readValue(body, GenerationJobResult.class);
                    return result;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse job status response", e);
                }
            })
            .exceptionally(throwable -> {
                if (config.getLoggingEnabled()) {
                    logger.error("Failed to get job result using polling URL: {}", pollingUrl, throwable);
                }
                throw new RuntimeException("Failed to get job result", throwable);
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
        
        if (config.getLoggingEnabled()) {
            logger.debug("BFL polling response: HTTP {}", statusCode);
        }
        
        if (statusCode >= 200 && statusCode < 300) {
            return CompletableFuture.completedFuture(body);
        }

        /*  FLUX 3 can report a failed task as HTTP 503 with a normal body, its status ends the polling  */
        if (statusCode == 503 && body != null && body.contains("\"status\"")) {
            return CompletableFuture.completedFuture(body);
        }

        // All error responses (including 404) are failures - no synthetic "not_found" status
        String errorMessage = extractErrorMessage(body, statusCode);
        
        if (statusCode == 404) {
            logger.warn("Job not found (HTTP 404) - job may not be registered yet or has expired");
        } else {
            logger.error("BFL API error [{}]: {}", statusCode, errorMessage);
        }
        
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
        
        // Handle error responses
        String errorMessage = extractErrorMessage(body, statusCode);
        logger.error("BFL API error [{}]: {}", statusCode, errorMessage);
        
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
    
    /**
     * Builds a model-specific request body JSON.
     * FLUX 3 and FLUX.2 accept different parameters; both reject unknown fields with HTTP 422.
     *
     * @param request the image generation request
     * @return JSON string with only valid parameters for the model
     * @throws Exception if serialization fails
     */
    private String buildModelSpecificRequestBody(ImageGenerationRequest request) throws Exception {
        String model = request.getModel();

        /*  FLUX 3 (OpenAPI Flux3ImageInputs, Klein adds seed and output_format): size by resolution tier, images
            as one list, no width/height, upsampling or input_image fields  */
        if (model != null && model.toLowerCase().startsWith("flux.3-")) {
            boolean klein = model.equalsIgnoreCase("flux.3-image-klein");
            var flux3Request = new java.util.LinkedHashMap<String, Object>();
            flux3Request.put("prompt", request.getPrompt());
            if (request.getAspectRatio() != null)
                flux3Request.put("aspect_ratio", request.getAspectRatio());
            if (request.getImageSize() != null)
                flux3Request.put("resolution", request.getImageSize());
            if (request.getSafetyTolerance() != null)
                flux3Request.put("safety_tolerance", request.getSafetyTolerance());
            if (klein && request.getSeed() != null)
                flux3Request.put("seed", request.getSeed());
            if (klein && request.getOutputFormat() != null)
                flux3Request.put("output_format", request.getOutputFormat());

            java.util.List<String> images = java.util.stream.Stream.of(request.getInputImage(),
                    request.getInputImage2(), request.getInputImage3(), request.getInputImage4(),
                    request.getInputImage5(), request.getInputImage6(), request.getInputImage7(),
                    request.getInputImage8(), request.getInputImage9(), request.getInputImage10())
                    .filter((String image) -> image != null && !image.isEmpty()).toList();
            if (!images.isEmpty())
                flux3Request.put("images", images);

            if (config.getLoggingEnabled()) {
                logger.info("FLUX 3 request body: model={}, resolution={}, aspect_ratio={}, safety={}, images={}",
                    model, request.getImageSize(), request.getAspectRatio(), request.getSafetyTolerance(), images.size());
            }
            return objectMapper.writeValueAsString(flux3Request);
        }
        boolean isFlux2 = model != null && model.toLowerCase().startsWith("flux.2-");
        boolean isFlux2Flex = model != null && model.toLowerCase().equals("flux.2-flex");

        if (isFlux2) {
            // FLUX.2 models only accept specific parameters
            // Do NOT send: aspect_ratio, raw, grounding_search (prompt upsampling: see below)
            var flux2Request = new java.util.LinkedHashMap<String, Object>();

            // Required
            flux2Request.put("prompt", request.getPrompt());

            // Optional dimensions
            if (request.getWidth() != null) {
                flux2Request.put("width", request.getWidth());
            }
            if (request.getHeight() != null) {
                flux2Request.put("height", request.getHeight());
            }

            // Optional parameters
            if (request.getSeed() != null) {
                flux2Request.put("seed", request.getSeed());
            }
            if (request.getSafetyTolerance() != null) {
                flux2Request.put("safety_tolerance", request.getSafetyTolerance());
            }
            if (request.getOutputFormat() != null) {
                flux2Request.put("output_format", request.getOutputFormat());
            }

            // Prompt upsampling (OpenAPI): [pro] and [max] upsample by default and only offer the opt-out
            // disable_pup, [flex] has prompt_upsampling (default true), [klein] has neither
            if (request.getPromptUpsampling() != null) {
                if (isFlux2Flex) {
                    flux2Request.put("prompt_upsampling", request.getPromptUpsampling());
                } else if (!model.toLowerCase().startsWith("flux.2-klein")) {
                    flux2Request.put("disable_pup", !request.getPromptUpsampling());
                }
            }

            // FLUX.2 [flex] exclusive parameters
            if (isFlux2Flex) {
                if (request.getGuidance() != null) {
                    flux2Request.put("guidance", request.getGuidance());
                }
                if (request.getSteps() != null) {
                    flux2Request.put("steps", request.getSteps());
                }
            }

            // Multi-image support (up to 8 images)
            // Count how many images are being added for logging
            int imageCount = 0;
            if (request.getInputImage() != null && !request.getInputImage().isEmpty()) {
                flux2Request.put("input_image", request.getInputImage());
                imageCount++;
                logger.debug("FLUX.2 input_image: {} chars (first 50: {}...)",
                    request.getInputImage().length(),
                    request.getInputImage().substring(0, Math.min(50, request.getInputImage().length())));
            }
            if (request.getInputImage2() != null && !request.getInputImage2().isEmpty()) {
                flux2Request.put("input_image_2", request.getInputImage2());
                imageCount++;
                logger.debug("FLUX.2 input_image_2: {} chars", request.getInputImage2().length());
            }
            if (request.getInputImage3() != null && !request.getInputImage3().isEmpty()) {
                flux2Request.put("input_image_3", request.getInputImage3());
                imageCount++;
                logger.debug("FLUX.2 input_image_3: {} chars", request.getInputImage3().length());
            }
            if (request.getInputImage4() != null && !request.getInputImage4().isEmpty()) {
                flux2Request.put("input_image_4", request.getInputImage4());
                imageCount++;
                logger.debug("FLUX.2 input_image_4: {} chars", request.getInputImage4().length());
            }
            if (request.getInputImage5() != null && !request.getInputImage5().isEmpty()) {
                flux2Request.put("input_image_5", request.getInputImage5());
                imageCount++;
                logger.debug("FLUX.2 input_image_5: {} chars", request.getInputImage5().length());
            }
            if (request.getInputImage6() != null && !request.getInputImage6().isEmpty()) {
                flux2Request.put("input_image_6", request.getInputImage6());
                imageCount++;
                logger.debug("FLUX.2 input_image_6: {} chars", request.getInputImage6().length());
            }
            if (request.getInputImage7() != null && !request.getInputImage7().isEmpty()) {
                flux2Request.put("input_image_7", request.getInputImage7());
                imageCount++;
                logger.debug("FLUX.2 input_image_7: {} chars", request.getInputImage7().length());
            }
            if (request.getInputImage8() != null && !request.getInputImage8().isEmpty()) {
                flux2Request.put("input_image_8", request.getInputImage8());
                imageCount++;
                logger.debug("FLUX.2 input_image_8: {} chars", request.getInputImage8().length());
            }

            String json = objectMapper.writeValueAsString(flux2Request);
            if (config.getLoggingEnabled()) {
                logger.info("FLUX.2 request body: prompt='{}...', width={}, height={}, safety={}, images={}",
                    request.getPrompt().substring(0, Math.min(50, request.getPrompt().length())),
                    request.getWidth(), request.getHeight(), request.getSafetyTolerance(), imageCount);
            }
            return json;
        }

        throw new IllegalArgumentException("Unsupported BFL model: " + model);
    }

    /**
     * Custom exception for BFL API errors.
     */
    public static class BflApiException extends RuntimeException {
        private final int statusCode;
        private final String responseBody;
        
        public BflApiException(int statusCode, String message, String responseBody) {
            super(message);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }
        
        public int getStatusCode() {
            return statusCode;
        }
        
        public String getResponseBody() {
            return responseBody;
        }
        
        @Override
        public String toString() {
            return String.format("BflApiException{statusCode=%d, message='%s'}", 
                statusCode, getMessage());
        }
    }
}