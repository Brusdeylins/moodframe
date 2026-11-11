package tools.moodframe.service;

import java.util.concurrent.CompletableFuture;
import tools.moodframe.model.GenerationJobInfo;
import tools.moodframe.model.GenerationJobResult;
import tools.moodframe.model.ImageGenerationRequest;

/**
 * Service interface for interacting with the BlackForest Labs (BFL) API.
 * 
 * <p>
 * This service provides methods for authenticating with the BFL API,
 * validating API keys, retrieving account information, and generating images
 * using FLUX models with full corporate proxy support.
 * </p>
 * 
 * <p>
 * <strong>API Documentation:</strong> https://docs.bfl.ai
 * </p>
 * <p>
 * <strong>Corporate Proxy Documentation:</strong>
 * https://docs.anthropic.com/claude-code/corporate-proxy
 * </p>
 * 
 * <p>
 * <strong>Supported Operations:</strong>
 * </p>
 * <ul>
 * <li>API key validation and testing</li>
 * <li>Account information and credit balance retrieval</li>
 * <li>Text-to-image generation with FLUX models</li>
 * <li>Job status polling and result retrieval</li>
 * <li>Corporate proxy support with ZScaler certificate integration</li>
 * <li>Custom SSL context with corporate trust stores</li>
 * </ul>
 * 
 * <p>
 * <strong>Corporate Proxy Features:</strong>
 * </p>
 * <ul>
 * <li>ZScaler root certificate support (.crt, .cer, .pem)</li>
 * <li>HTTP/HTTPS proxy with authentication</li>
 * <li>Custom trust store integration</li>
 * <li>Multi-source proxy configuration</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public interface BflApiService {

    /**
     * Tests the API connection and validates the provided API key.
     * 
     * <p>
     * This method performs a lightweight API call to verify that the API key
     * is valid and the service is accessible. It includes full corporate proxy
     * support and custom certificate validation.
     * </p>
     * 
     * <p>
     * <strong>Corporate Proxy Support:</strong> This method automatically
     * uses configured corporate proxy settings and ZScaler certificates if
     * available.
     * </p>
     * 
     * @return a CompletableFuture that completes with true if the API key is valid,
     *         false otherwise
     * @throws IllegalStateException if no API key is configured
     * @throws RuntimeException      if corporate proxy connection fails
     */
    CompletableFuture<Boolean> testConnection();

    /**
     * Tests the API connection specifically through corporate proxy.
     * 
     * <p>
     * This method explicitly tests the connection using corporate proxy
     * settings and provides detailed feedback about proxy connectivity.
     * </p>
     * 
     * @return a CompletableFuture that completes with true if proxy connection
     *         works
     * @since 1.0.0
     */
    CompletableFuture<Boolean> testConnectionWithProxy();

    /**
     * Tests SSL/TLS connectivity without requiring an API key.
     *
     * <p>
     * Performs an HTTPS HEAD request to the BFL API host to verify that
     * the SSL handshake succeeds with the configured certificates and proxy.
     * This allows testing ZScaler certificate configuration independently
     * of API key availability.
     * </p>
     *
     * @return a CompletableFuture that completes with true if SSL handshake succeeds
     */
    CompletableFuture<Boolean> testSslConnection();

    /**
     * Gets the currently configured API key.
     * 
     * @return the current API key, or null if not configured
     * @since 1.0.0
     */
    String getApiKey();

    /**
     * Initializes the HTTP client with corporate proxy support.
     * 
     * <p>
     * This method configures the HTTP client with:
     * </p>
     * <ul>
     * <li>Corporate proxy settings (if configured)</li>
     * <li>ZScaler root certificates (if available)</li>
     * <li>Custom SSL context with corporate trust store</li>
     * <li>Proper timeout and retry configuration</li>
     * </ul>
     * 
     * @since 1.0.0
     */
    void initializeHttpClient();

    /**
     * Refreshes the corporate proxy configuration.
     * 
     * <p>
     * This method reloads proxy settings and reinitializes the HTTP client
     * with updated corporate proxy configuration.
     * </p>
     * 
     * @since 1.0.0
     */
    void refreshProxyConfiguration();

    /**
     * Retrieves the current user's finetunes list (for API validation).
     * 
     * <p>
     * This method calls the BFL API's my_finetunes endpoint to validate
     * API key access and get finetune information if available. Automatically
     * uses corporate proxy settings if configured.
     * </p>
     * 
     * @return a CompletableFuture containing the finetunes list as JSON string
     * @throws IllegalStateException if no API key is configured
     */
    CompletableFuture<String> getMyFinetunes();

    /**
     * Initiates a text-to-image generation request using the specified FLUX model.
     * 
     * <p>
     * This method submits a generation request to the BFL API and returns
     * a job ID that can be used to poll for completion status and retrieve
     * the generated images.
     * </p>
     * 
     * @param request the image generation request parameters
     * @return a CompletableFuture containing the job information
     * @throws IllegalArgumentException if the request parameters are invalid
     * @throws IllegalStateException    if no API key is configured
     */
    CompletableFuture<GenerationJobInfo> submitGenerationRequest(ImageGenerationRequest request);

    /**
     * Polls the status of a generation job and retrieves results if completed.
     * 
     * <p>
     * This method checks the current status of a previously submitted
     * generation job. If the job is completed, it returns the generated
     * image URLs and metadata.
     * </p>
     * 
     * @param jobId the unique identifier of the generation job
     * @return a CompletableFuture containing the current job status and results
     * @throws IllegalArgumentException if the job ID is null or empty
     * @throws IllegalStateException    if no API key is configured
     */
    CompletableFuture<GenerationJobResult> getJobStatus(String jobId);

    /**
     * Cancels a pending or running generation job.
     * 
     * <p>
     * This method attempts to cancel a generation job that is currently
     * queued or in progress. Jobs that have already completed cannot be cancelled.
     * </p>
     * 
     * @param jobId the unique identifier of the generation job to cancel
     * @return a CompletableFuture that completes with true if the job was
     *         successfully cancelled, false otherwise
     * @throws IllegalArgumentException if the job ID is null or empty
     * @throws IllegalStateException    if no API key is configured
     */
    CompletableFuture<Boolean> cancelJob(String jobId);

    /**
     * Checks if the service is properly configured and ready to use.
     * 
     * @return true if an API key is configured and the service is ready
     */
    boolean isConfigured();

    /**
     * Gets the current API endpoint base URL being used.
     * 
     * @return the base URL for BFL API requests
     */
    String getApiBaseUrl();

    /**
     * Sets the API key to use for authentication.
     * 
     * <p>
     * This method updates the API key used for all subsequent requests.
     * The key should be obtained from the BFL dashboard.
     * </p>
     * 
     * @param apiKey the BFL API key
     * @throws IllegalArgumentException if the API key is null or empty
     */
    void setApiKey(String apiKey);
}