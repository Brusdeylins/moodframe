package art.moodframe.service.provider;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.model.ModelParameters;

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
public interface BflApiService extends ImageProvider {

    @Override
    default ApiProvider getProvider() {
        return ApiProvider.BFL;
    }

    /** The prompt limit of FLUX: 512 tokens, about 2,000 characters. */
    int MAX_PROMPT_LENGTH = 2000;

    /** BFL reports its prices in credits of one cent. */
    double USD_PER_CREDIT = 0.01;

    @Override
    BflApiService withCredentials(String apiKey, String endpoint);

    @Override
    default int maxPromptLength(String apiModelId) {
        return MAX_PROMPT_LENGTH;
    }

    /**
     * Sets the parameters the model takes ({@link GenerationModel#parameters()}: the safety tolerance of FLUX.2 and
     * FLUX 3, guidance and steps of FLUX.2 [flex]), the resolution tier of FLUX 3 and the reference images (FLUX 3 up to 10, FLUX.2 up to 8); FLUX.2 [max] grounds on its own by the
     * prompt.
     */
    @Override
    default void prepare(ImageGenerationRequest request, GenerationModel model, RequestOptions options) {
        List<String> referenceImages = options.referenceImages();
        if (model.parameter(ModelParameters.SAFETY_TOLERANCE.name()) != null)
            request.setSafetyTolerance(options.fluxSafetyTolerance());

        if (model.isFlux3Model()) {
            request.setImageSize(model.getFlux3Resolution());
            if (!referenceImages.isEmpty())
                request.setInputImages(referenceImages);
        }

        if (model.isFlux2Model()) {
            if (options.guidance() != null && model.parameter(ModelParameters.GUIDANCE.name()) != null)
                request.setGuidance(options.guidance());
            if (options.steps() != null && model.parameter(ModelParameters.STEPS.name()) != null)
                request.setSteps(options.steps());
            if (model.supportsMultiImage() && !referenceImages.isEmpty())
                request.setInputImages(referenceImages);
        }
    }

    /**
     * BFL answers a submission with a job, which the generation polls ({@link #pollJob}) until the image is ready.
     */
    @Override
    default boolean answersWithImage() {
        return false;
    }

    /**
     * Polls the job at the polling URL BFL answered the submission with, else by its id.
     */
    @Override
    CompletableFuture<GenerationJobResult> pollJob(GenerationJobInfo job);

    /**
     * FLUX.2 takes a safety tolerance up to 5 only (FLUX 3 up to 7), FLUX 3 Klein seeds from 0 to 4294967295 only
     * (FLUX 3 Image takes none).
     */
    @Override
    default void prepareJob(ImageGenerationRequest job) {
        String lower = job.getModel() != null ? job.getModel().toLowerCase() : "";
        if (lower.startsWith("flux.2-") && job.getSafetyTolerance() != null && job.getSafetyTolerance() > 5)
            job.setSafetyTolerance(5);
        if (lower.startsWith("flux.3-") && job.getSeed() != null)
            job.setSeed(job.getSeed() & 0xFFFFFFFFL);
    }

    /**
     * A new account is refused a safety tolerance it is not approved for, with its approved maximum.
     */
    @Override
    default Integer approvedSafetyTolerance(Throwable failure) {
        return ProviderException.unwrap(failure) instanceof BflApiException refusal
                ? refusal.getMaximumApprovedSafetyTolerance() : null;
    }

    /**
     * BFL reports the price of a finished job in credits of one cent.
     */
    @Override
    default Double reportedCost(GenerationJobResult result) {
        Double credits = result != null ? result.getCost() : null;
        return credits != null && credits > 0 ? credits * USD_PER_CREDIT : null;
    }

    @Override
    default boolean usesSeeds() {
        return true;
    }

    /**
     * Only FLUX 3 and FLUX.2 take an attached image.
     */
    @Override
    default void checkImageInput(String modelId) {
        String lower = modelId != null ? modelId.toLowerCase() : "";
        if (!lower.startsWith("flux.3-") && !lower.startsWith("flux.2-"))
            throw new IllegalArgumentException("Image input is only supported with FLUX 3 and FLUX.2 models");
    }

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
     * Gets the status of a job from the polling URL BFL answered the submission with; the URL leads to the region
     * that runs the job, the job id alone may answer HTTP 404.
     *
     * @param pollingUrl the polling URL of the submission
     * @return a CompletableFuture containing the current job status and results
     * @since 2.1.3
     */
    CompletableFuture<GenerationJobResult> getJobResultWithPollingUrl(String pollingUrl);

    /**
     * Gets the current API endpoint base URL being used.
     * 
     * @return the base URL for BFL API requests
     */
    String getApiBaseUrl();
}
