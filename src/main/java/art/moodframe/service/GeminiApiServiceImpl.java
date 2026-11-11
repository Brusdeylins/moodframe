package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.CorporateProxyConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.BrainstormTurn;
import art.moodframe.model.GenerationJobInfo;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.model.ResearchNote;
import art.moodframe.util.I18nManager;
import art.moodframe.util.TrustStoreManager;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of Google Gemini API service.
 * Currently supports API key validation via connection testing.
 * Image generation will be implemented in Phase 2.
 *
 * @author Matthias Brusdeylins
 * @since 1.5.0
 */
public class GeminiApiServiceImpl implements GeminiApiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiApiServiceImpl.class);

    private static final String GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final int TIMEOUT_SECONDS = 120; // 2 minutes for image generation
    private static final String BRAINSTORM_MODEL = "gemini-3.8-flash";
    private static final int RESEARCH_TIMEOUT_SECONDS = 60;
    /* Without the demand to search, the model often answers from memory: no sources, so the facts cannot be cited */
    private static final String RESEARCH_INSTRUCTION = "You MUST search the web with Google Search for this question "
            + "and base your answer only on what the search results say, never on memory. Research the following "
            + "for an image or video brief. Answer concisely in the language of the question with concrete, "
            + "checkable facts (numbers, names, terms), at most 300 words. Question: ";
    private static final String RESEARCH_RETRY_INSTRUCTION = "Use the Google Search tool now, you did not search. ";
    static final JSONArray BRAINSTORM_TOOLS = new JSONArray()
            .put(new JSONObject()
                    .put("name", "research")
                    .put("description", "Searches the web and reads web pages. Use it when the topic needs facts "
                            + "(data, terms, how something looks or works). Returns a short factual summary.")
                    .put("parameters", new JSONObject()
                            .put("type", "object")
                            .put("properties", new JSONObject().put("query", new JSONObject()
                                    .put("type", "string")
                                    .put("description", "What to find out, as a complete question or search request")))
                            .put("required", new JSONArray().put("query"))))
            .put(new JSONObject()
                    .put("name", "analyze_video")
                    .put("description", "Analyzes a public YouTube video and returns its content as text. "
                            + "Use it only when the user gave a YouTube link.")
                    .put("parameters", new JSONObject()
                            .put("type", "object")
                            .put("properties", new JSONObject()
                                    .put("url", new JSONObject().put("type", "string"))
                                    .put("start_seconds", new JSONObject().put("type", "integer"))
                                    .put("end_seconds", new JSONObject().put("type", "integer"))
                                    .put("question", new JSONObject().put("type", "string")
                                            .put("description", "What to extract from the video")))
                            .put("required", new JSONArray().put("url").put("question"))));
    private static final int OMNI_TIMEOUT_SECONDS = 600; // Omni answers when the video is finished
    /* Without a task and a tag, Omni ignores an attached image (live check: the logo never showed up) */
    private static final String OMNI_REFERENCE_NOTE = "The attached image <IMAGE_REF_0> is a reference (for example "
            + "a logo or product). Show it faithfully wherever the prompt mentions the attached image or logo. ";
    private static final int OMNI_FILE_POLL_ATTEMPTS = 40; // video file becomes ACTIVE shortly after the answer
    private static final int OMNI_FILE_POLL_INTERVAL_MS = 3000;
    private static final java.util.regex.Pattern OMNI_FILE_ID = java.util.regex.Pattern.compile("files/([^:/?]+)");

    private final OkHttpClient httpClient;
    private final TrustStoreManager trustStoreManager;
    private final CorporateProxyConfig proxyConfig;
    private String apiKey;

    /**
     * Creates a new Gemini API service implementation.
     *
     * @param config the application configuration
     */
    public GeminiApiServiceImpl(ApplicationConfig config) {
        this.apiKey = config.getGeminiApiKey();
        this.trustStoreManager = new TrustStoreManager();
        this.proxyConfig = new CorporateProxyConfig(config);

        // Create HTTP client with timeouts and SSL configuration
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Configure SSL context with corporate certificates
        configureSSLContext(clientBuilder);

        this.httpClient = clientBuilder.build();
    }

    /**
     * Configures SSL context with corporate certificates for OkHttp client.
     *
     * @param clientBuilder the OkHttpClient builder to configure
     */
    private void configureSSLContext(OkHttpClient.Builder clientBuilder) {
        try {
            var certificatePaths = proxyConfig.getCertificatePaths();

            if (!certificatePaths.isEmpty()) {
                logger.info("Configuring SSL context with {} corporate certificate(s)", certificatePaths.size());

                // Create corporate trust store
                KeyStore corporateKeyStore = trustStoreManager.createCorporateKeyStore(certificatePaths);

                // Create trust manager factory with corporate KeyStore
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(corporateKeyStore);

                // Get trust managers
                TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

                // Create SSL context with corporate certificates
                SSLContext sslContext = trustStoreManager.createSSLContext();

                // Configure OkHttp with SSL context and trust manager
                if (trustManagers.length > 0 && trustManagers[0] instanceof X509TrustManager) {
                    X509TrustManager trustManager = (X509TrustManager) trustManagers[0];
                    SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

                    clientBuilder.sslSocketFactory(sslSocketFactory, trustManager);
                    logger.info("SSL context configured successfully with corporate certificates");
                } else {
                    logger.warn("Could not get X509TrustManager from TrustManagerFactory");
                }
            } else {
                logger.info("Using default SSL context (no corporate certificates configured)");
            }

        } catch (Exception e) {
            logger.error("Failed to configure SSL context with corporate certificates", e);
            logger.warn("Proceeding with default SSL context");
        }
    }

    @Override
    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                logger.warn("Cannot test Gemini API connection: No API key configured");
                return false;
            }

            try {
                // Test connection by listing available models
                // This is a lightweight API call that validates the key
                String url = GEMINI_API_BASE_URL + "/models?key=" + apiKey;

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .build();

                logger.info("Testing Gemini API connection...");

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        logger.info("Gemini API connection test successful");
                        return true;
                    } else {
                        logger.warn("Gemini API connection test failed with status: {} - {}",
                                response.code(), response.message());

                        if (response.body() != null) {
                            String body = response.body().string();
                            logger.debug("Gemini API error response: {}", body);
                        }

                        return false;
                    }
                }

            } catch (Exception e) {
                logger.error("Gemini API connection test failed with exception", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<GenerationJobInfo> submitGenerationRequest(ImageGenerationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException("Gemini API key not configured");
            }

            try {
                // Build Gemini request body
                JSONObject requestBody = buildGeminiRequestBody(request);

                // Determine model name from request
                String geminiModelName = getGeminiModelName(request.getModel());

                // Build API endpoint
                String endpoint = GEMINI_API_BASE_URL + "/models/" + geminiModelName +
                                ":generateContent?key=" + apiKey;

                logger.info("Submitting Gemini generation request to: {}", endpoint);

                // Create HTTP request
                RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    MediaType.parse("application/json; charset=utf-8")
                );

                Request httpRequest = new Request.Builder()
                    .url(endpoint)
                    .post(body)
                    .build();

                // Execute synchronous API call
                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        logger.info("Gemini API response received: {} bytes", responseBody.length());

                        // Log response structure (without image data for debugging)
                        logResponseStructure(responseBody);

                        // Check for content blocking before extracting image
                        String blockingReason = checkForContentBlocking(responseBody, request.hasInputImage(), request.hasVideoSource());
                        if (blockingReason != null) {
                            // Content was blocked - return failed job with clean message
                            logger.info("Gemini content blocked: {}", blockingReason);
                            return new GenerationJobInfo.Builder()
                                .jobId(java.util.UUID.randomUUID().toString())
                                .status("failed")
                                .errorMessage(blockingReason)
                                .isSynchronous(true)
                                .provider(ApiProvider.GOOGLE_GEMINI)
                                .build();
                        }

                        // Extract Base64 image from response
                        String base64Image = extractBase64FromResponse(responseBody);

                        // NOTE: Thinking metadata extraction disabled until Gemini Image API supports it
                        // The UI will store null values for thinkingTokens and thoughtSummary
                        // This allows the database schema to be ready when the API adds support

                        // Create GenerationJobInfo with synchronous result
                        return new GenerationJobInfo.Builder()
                            .jobId(java.util.UUID.randomUUID().toString()) // Generate fake ID for consistency
                            .status("completed")
                            .base64Image(base64Image)
                            .isSynchronous(true)
                            .provider(ApiProvider.GOOGLE_GEMINI)
                            .thinkingTokens(null) // Not yet supported by Image API
                            .thoughtSummary(null) // Not yet supported by Image API
                            .build();

                    } else {
                        String errorBody = response.body() != null ? response.body().string() : "No error details";
                        logger.error("Gemini API request failed: HTTP {} - {}", response.code(), errorBody);

                        // Extract user-friendly error message
                        String userFriendlyError = extractUserFriendlyError(response.code(), errorBody);
                        throw new RuntimeException(userFriendlyError);
                    }
                }

            } catch (java.net.SocketTimeoutException e) {
                logger.error("Gemini API request timed out", e);
                throw new RuntimeException("Gemini Bildgenerierung dauerte zu lange. Bitte versuchen Sie es erneut oder wählen Sie ein schnelleres Modell (Flash 1K).", e);
            } catch (Exception e) {
                logger.error("Failed to submit Gemini generation request", e);
                throw new RuntimeException("Gemini generation failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Builds the Gemini API request body in JSON format.
     *
     * @param request the image generation request
     * @return JSON request body
     */
    private JSONObject buildGeminiRequestBody(ImageGenerationRequest request) {
        JSONObject body = new JSONObject();

        // Contents Array
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();

        // Text Part (Prompt)
        JSONObject textPart = new JSONObject();
        textPart.put("text", request.getPrompt());
        parts.put(textPart);

        // Image Parts (Gemini supports up to 14 reference images)
        // Add primary image and additional images (input_image_2 through input_image_8)
        int imageCount = 0;
        if (request.hasInputImage()) {
            addGeminiImagePart(parts, request.getInputImage());
            imageCount++;
        }
        if (request.getInputImage2() != null && !request.getInputImage2().isEmpty()) {
            addGeminiImagePart(parts, request.getInputImage2());
            imageCount++;
        }
        if (request.getInputImage3() != null && !request.getInputImage3().isEmpty()) {
            addGeminiImagePart(parts, request.getInputImage3());
            imageCount++;
        }
        if (request.getInputImage4() != null && !request.getInputImage4().isEmpty()) {
            addGeminiImagePart(parts, request.getInputImage4());
            imageCount++;
        }
        if (request.getInputImage5() != null && !request.getInputImage5().isEmpty()) {
            addGeminiImagePart(parts, request.getInputImage5());
            imageCount++;
        }
        if (request.getInputImage6() != null && !request.getInputImage6().isEmpty()) {
            addGeminiImagePart(parts, request.getInputImage6());
            imageCount++;
        }
        if (request.getInputImage7() != null && !request.getInputImage7().isEmpty()) {
            addGeminiImagePart(parts, request.getInputImage7());
            imageCount++;
        }
        if (request.getInputImage8() != null && !request.getInputImage8().isEmpty()) {
            addGeminiImagePart(parts, request.getInputImage8());
            imageCount++;
        }
        if (request.getInputImage9() != null && !request.getInputImage9().isEmpty()) {
            addGeminiImagePart(parts, request.getInputImage9());
            imageCount++;
        }
        if (request.getInputImage10() != null && !request.getInputImage10().isEmpty()) {
            addGeminiImagePart(parts, request.getInputImage10());
            imageCount++;
        }
        if (request.getInputImage11() != null && !request.getInputImage11().isEmpty()) {
            addGeminiImagePart(parts, request.getInputImage11());
            imageCount++;
        }
        if (request.getInputImage12() != null && !request.getInputImage12().isEmpty()) {
            addGeminiImagePart(parts, request.getInputImage12());
            imageCount++;
        }
        if (request.getInputImage13() != null && !request.getInputImage13().isEmpty()) {
            addGeminiImagePart(parts, request.getInputImage13());
            imageCount++;
        }
        if (request.getInputImage14() != null && !request.getInputImage14().isEmpty()) {
            addGeminiImagePart(parts, request.getInputImage14());
            imageCount++;
        }

        if (imageCount > 0) {
            logger.info("Gemini request includes {} input image(s)", imageCount);
        }

        // Direct video-to-image context (Nano Banana 2 and Lite only):
        // pass a YouTube video as multimodal context alongside the prompt.
        if (request.hasVideoSource()) {
            GenerationModel videoModel = GenerationModel.fromApiModelId(request.getModel());
            if (videoModel != null && videoModel.supportsVideoSource()) {
                addGeminiVideoPart(parts, request.getVideoSourceUrl(),
                        request.getVideoStartOffsetSeconds(), request.getVideoEndOffsetSeconds());
                logger.info("Gemini request includes a direct video source: {}", request.getVideoSourceUrl());
            } else {
                logger.warn("Ignoring video source for model {} (only Nano Banana 2 and Lite support video-to-image)",
                        request.getModel());
            }
        }

        content.put("parts", parts);
        contents.put(content);
        body.put("contents", contents);

        // Generation Config
        JSONObject genConfig = new JSONObject();
        genConfig.put("responseModalities", new JSONArray().put("IMAGE"));

        JSONObject imageConfig = new JSONObject();
        String validAspectRatio = mapToGeminiAspectRatio(request.getAspectRatio());
        imageConfig.put("aspectRatio", validAspectRatio);

        // Model-specific parameters
        String modelId = request.getModel();

        // Models that support imageSize parameter:
        // - gemini-3-pro-image (Gemini 3 Pro)
        // - gemini-3.1-flash-image (Nano Banana 2)
        // - gemini-3.1-flash-lite-image (Nano Banana 2 Lite, capped at 1K)
        // gemini-2.5-flash-image does NOT support imageSize
        if (modelId != null && (modelId.equals("gemini-3-pro-image")
                || modelId.equals("gemini-3.1-flash-image")
                || modelId.equals("gemini-3.1-flash-lite-image"))) {
            // Image size: 512, 1K, 2K, or 4K
            imageConfig.put("imageSize", request.getImageSize() != null ? request.getImageSize() : "1K");

        }

        // Thinking level: only Nano Banana 2 and Lite accept it, and only "minimal" or "high"
        // (the API answers HTTP 400 "Thinking level LOW is not supported for this model")
        GenerationModel thinkingModel = GenerationModel.fromApiModelId(modelId);
        String thinkingLevel = request.getThinkingMode();
        if (thinkingModel != null && thinkingModel.supportsThinkingLevel()
                && ("minimal".equals(thinkingLevel) || "high".equals(thinkingLevel))) {
            genConfig.put("thinkingConfig", new JSONObject().put("thinkingLevel", thinkingLevel));
        }

        genConfig.put("imageConfig", imageConfig);
        body.put("generationConfig", genConfig);

        // Google Search Grounding (Phase 2). Nano Banana 2 Lite does not support it: the API answers
        // HTTP 400 "Search as tool is not enabled for this model"
        GenerationModel requestedModel = GenerationModel.fromApiModelId(request.getModel());
        if (Boolean.TRUE.equals(request.getEnableGoogleSearch())
                && (requestedModel == null || requestedModel.supportsGroundingSearch())) {
            JSONArray tools = new JSONArray();
            JSONObject searchTool = new JSONObject();
            searchTool.put("google_search", new JSONObject());
            tools.put(searchTool);
            body.put("tools", tools);
            logger.debug("Enabled Google Search grounding");
        }

        String imageSizeForLog = (modelId != null && (modelId.equals("gemini-3-pro-image")
                || modelId.equals("gemini-3.1-flash-image")))
                ? request.getImageSize()
                : "N/A (Flash model)";
        logger.debug("Built Gemini request body: model={}, aspectRatio={}, imageSize={}, hasInputImage={}, searchEnabled={}, thinkingMode={}",
                    modelId, request.getAspectRatio(), imageSizeForLog, request.hasInputImage(),
                    request.getEnableGoogleSearch(), request.getThinkingMode());

        return body;
    }

    /**
     * Checks if content was blocked by Gemini safety filters.
     * Returns the user-friendly blocking message, or null if content was not blocked.
     *
     * @param responseBody the JSON response from Gemini API
     * @param hasInputImage whether an input image was attached to the request
     * @return blocking message if blocked, null if not blocked
     */
    private String checkForContentBlocking(String responseBody, boolean hasInputImage, boolean hasVideoSource) {
        try {
            JSONObject json = new JSONObject(responseBody);

            // FIRST: Check for prompt-level blocking (when there are no candidates at all)
            // This happens when the prompt itself is rejected before generation starts
            if (json.has("promptFeedback")) {
                JSONObject promptFeedback = json.getJSONObject("promptFeedback");
                if (promptFeedback.has("blockReason")) {
                    String blockReason = promptFeedback.getString("blockReason");
                    logger.warn("Prompt blocked at input level - blockReason={}", blockReason);

                    // Return localized user-friendly message with solution hints
                    return switch (blockReason) {
                        case "SAFETY" ->
                            I18nManager.getText(hasInputImage ? "gemini.error.safety.with.image" : "gemini.error.safety");
                        case "PROHIBITED_CONTENT" ->
                            I18nManager.getText(hasInputImage ? "gemini.error.prohibited.with.image" : "gemini.error.prohibited");
                        case "BLOCKLIST" ->
                            I18nManager.getText(hasInputImage ? "gemini.error.blocklist.with.image" : "gemini.error.blocklist");
                        case "SPII" ->
                            I18nManager.getText(hasInputImage ? "gemini.error.spii.with.image" : "gemini.error.spii");
                        case "OTHER" ->
                            I18nManager.getText(hasInputImage ? "gemini.error.other.with.image" : "gemini.error.other");
                        default ->
                            I18nManager.getText("gemini.error.default", blockReason);
                    };
                }
            }

            // SECOND: Check for candidate-level blocking (finishReason in candidates array)
            if (json.has("candidates")) {
                JSONArray candidates = json.getJSONArray("candidates");
                if (candidates.length() > 0) {
                    JSONObject candidate = candidates.getJSONObject(0);

                    if (candidate.has("finishReason")) {
                        String finishReason = candidate.getString("finishReason");

                        // Check for all blocking/failure reasons
                        // Complete list from: https://pub.dev/documentation/gemini_box/latest/gemini_box/FinishReason.html
                        // Note: STOP is normal completion, FINISH_REASON_UNSPECIFIED is default
                        if ("SAFETY".equals(finishReason) || "IMAGE_SAFETY".equals(finishReason)
                            || "RECITATION".equals(finishReason) || "IMAGE_RECITATION".equals(finishReason)
                            || "OTHER".equals(finishReason)
                            || "PROHIBITED_CONTENT".equals(finishReason) || "IMAGE_PROHIBITED_CONTENT".equals(finishReason)
                            || "NO_IMAGE".equals(finishReason) || "IMAGE_OTHER".equals(finishReason)
                            || "BLOCKLIST".equals(finishReason) || "SPII".equals(finishReason)
                            || "BLOCKED_REASON".equals(finishReason)
                            || "MAX_TOKENS".equals(finishReason)
                            || "LANGUAGE".equals(finishReason)
                            || "MALFORMED_FUNCTION_CALL".equals(finishReason)) {

                            // Log detailed safety ratings for debugging
                            if (candidate.has("safetyRatings")) {
                                JSONArray safetyRatings = candidate.getJSONArray("safetyRatings");
                                StringBuilder detailsLog = new StringBuilder("Safety ratings: ");
                                for (int i = 0; i < safetyRatings.length(); i++) {
                                    JSONObject rating = safetyRatings.getJSONObject(i);
                                    String category = rating.optString("category", "UNKNOWN");
                                    String probability = rating.optString("probability", "UNKNOWN");
                                    detailsLog.append(category).append("=").append(probability);
                                    if (i < safetyRatings.length() - 1) {
                                        detailsLog.append(", ");
                                    }
                                }
                                logger.warn("Content blocked - finishReason={}, {}", finishReason, detailsLog);
                            } else {
                                logger.warn("Content blocked - finishReason={}, no safety ratings provided", finishReason);
                            }

                            // Log the original finishMessage from Google for debugging
                            if (candidate.has("finishMessage")) {
                                String finishMessage = candidate.getString("finishMessage");
                                logger.info("Gemini finishMessage: {}", finishMessage);
                            }

                            // Return localized user-friendly message with solution hints
                            return switch (finishReason) {
                                case "SAFETY", "IMAGE_SAFETY" ->
                                    I18nManager.getText("gemini.error.safety");
                                case "PROHIBITED_CONTENT", "IMAGE_PROHIBITED_CONTENT" ->
                                    I18nManager.getText("gemini.error.prohibited");
                                case "IMAGE_OTHER" ->
                                    I18nManager.getText("gemini.error.image.other");
                                case "NO_IMAGE" ->
                                    I18nManager.getText(hasVideoSource ? "gemini.error.no.image.video" : "gemini.error.no.image");
                                case "RECITATION", "IMAGE_RECITATION" ->
                                    I18nManager.getText("gemini.error.recitation");
                                case "OTHER" ->
                                    I18nManager.getText("gemini.error.other");
                                case "BLOCKLIST" ->
                                    I18nManager.getText("gemini.error.blocklist");
                                case "SPII" ->
                                    I18nManager.getText("gemini.error.spii");
                                case "MAX_TOKENS" ->
                                    I18nManager.getText("gemini.error.max.tokens");
                                case "LANGUAGE" ->
                                    I18nManager.getText("gemini.error.language");
                                case "MALFORMED_FUNCTION_CALL" ->
                                    I18nManager.getText("gemini.error.malformed.function");
                                default ->
                                    I18nManager.getText("gemini.error.default", finishReason);
                            };
                        }
                    }
                }
            }

            return null; // Not blocked
        } catch (Exception e) {
            logger.warn("Could not check for content blocking", e);
            return null;
        }
    }

    /**
     * Extracts Base64 encoded image from Gemini API response.
     * Note: Content blocking should be checked first using checkForContentBlocking().
     *
     * @param responseBody the JSON response from Gemini API
     * @return Base64 encoded image data
     */
    private String extractBase64FromResponse(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);

            // Check if response contains an error field
            if (json.has("error")) {
                JSONObject error = json.getJSONObject("error");
                String errorMessage = error.optString("message", "Unbekannter API-Fehler");
                int errorCode = error.optInt("code", 0);
                logger.error("Gemini API returned error: [{}] {}", errorCode, errorMessage);
                throw new RuntimeException("Gemini API Fehler: " + errorMessage);
            }

            // Check if candidates array exists
            if (!json.has("candidates")) {
                logger.error("Gemini response missing 'candidates' field. Response: {}",
                    responseBody.substring(0, Math.min(500, responseBody.length())));
                throw new RuntimeException("Gemini API gab keine Kandidaten zurück. Bitte versuchen Sie es erneut.");
            }

            // Guard: candidate without content parts (e.g. unknown finishReason with empty content)
            // Prevents a raw JSONException and surfaces Google's finishMessage instead
            JSONObject firstCandidate = json.getJSONArray("candidates").getJSONObject(0);
            if (!firstCandidate.has("content") || !firstCandidate.getJSONObject("content").has("parts")) {
                String finishReason = firstCandidate.optString("finishReason", "UNKNOWN");
                String finishMessage = firstCandidate.optString("finishMessage", "");
                logger.error("Gemini candidate contains no image parts - finishReason={}, finishMessage={}",
                        finishReason, finishMessage);
                throw new RuntimeException(I18nManager.getText("gemini.error.default", finishReason));
            }

            // Navigate: candidates[0].content.parts
            // NOTE: Gemini 3 Pro Image uses a thinking process that generates up to 2 intermediate images
            // The LAST image in the parts array is the final rendered image
            // See: https://ai.google.dev/gemini-api/docs/image-generation#thinking-process
            JSONArray parts = firstCandidate.getJSONObject("content")
                                    .getJSONArray("parts");

            if (parts.length() == 0) {
                throw new RuntimeException("Gemini API gab keine Bilder zurück (parts array ist leer)");
            }

            // Get the LAST image (final rendered result after thinking process)
            int lastIndex = parts.length() - 1;
            String base64Image = parts.getJSONObject(lastIndex)
                                    .getJSONObject("inlineData")
                                    .getString("data");

            logger.info("Extracted Base64 image from Gemini response: {} bytes (image {} of {}, final result)",
                        base64Image.length(), lastIndex + 1, parts.length());
            return base64Image;

        } catch (JSONException e) {
            logger.error("Failed to parse Gemini response structure: {}", e.getMessage());
            // Log response excerpt for debugging (first 800 chars to see full error)
            logger.error("Response excerpt: {}", responseBody.substring(0, Math.min(800, responseBody.length())));
            throw new RuntimeException("Gemini API gab unerwartetes Format zurück: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            // Re-throw our custom exceptions
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error extracting Base64 from Gemini response", e);
            throw new RuntimeException("Fehler beim Verarbeiten der Gemini-Antwort: " + e.getMessage(), e);
        }
    }

    /**
     * Adds an image part to the Gemini request parts array.
     * Used for multi-image support (up to 14 reference images).
     *
     * @param parts the JSONArray of parts to add the image to
     * @param base64Image the Base64-encoded image data
     */
    private void addGeminiImagePart(JSONArray parts, String base64Image) {
        JSONObject imagePart = new JSONObject();
        JSONObject inlineData = new JSONObject();
        inlineData.put("mimeType", "image/png"); // Gemini accepts PNG format
        inlineData.put("data", base64Image);
        imagePart.put("inline_data", inlineData);
        parts.put(imagePart);
    }

    /**
     * Adds a video file_data part (YouTube URL) to the parts array for
     * video-to-image generation with gemini-3.1-flash-image.
     *
     * <p>Mirrors the structure used by {@code buildYouTubeAnalysisRequestBody}:
     * a part holding {@code file_data.fileUri} plus an optional sibling
     * {@code video_metadata} with {@code start_offset}/{@code end_offset}.</p>
     *
     * @param parts the JSONArray of parts to add the video to
     * @param videoUrl the YouTube URL
     * @param startSec optional start offset in seconds (null/0 = from start)
     * @param endSec optional end offset in seconds (null/0 = until end)
     */
    private void addGeminiVideoPart(JSONArray parts, String videoUrl, Integer startSec, Integer endSec) {
        JSONObject fileDataPart = new JSONObject();
        JSONObject fileData = new JSONObject();
        fileData.put("fileUri", videoUrl.trim());
        fileDataPart.put("file_data", fileData);

        int start = startSec != null ? startSec : 0;
        int end = endSec != null ? endSec : 0;
        if (start > 0 || end > 0) {
            JSONObject videoMetadata = new JSONObject();
            if (start > 0) {
                JSONObject startOffset = new JSONObject();
                startOffset.put("seconds", start);
                videoMetadata.put("start_offset", startOffset);
            }
            if (end > 0) {
                JSONObject endOffset = new JSONObject();
                endOffset.put("seconds", end);
                videoMetadata.put("end_offset", endOffset);
            }
            fileDataPart.put("video_metadata", videoMetadata);
        }

        parts.put(fileDataPart);
    }

    /**
     * Maps model identifier to Gemini API model name.
     *
     * @param modelId the model identifier from UI
     * @return Gemini API model name
     */
    private String getGeminiModelName(String modelId) {
        if (modelId == null) {
            return "gemini-2.5-flash-image"; // Default model
        }

        // Map UI model names to Gemini API names
        // Based on https://ai.google.dev/gemini-api/docs/models/gemini
        return switch (modelId.toLowerCase()) {
            case "gemini-flash-1k", "gemini-2.5-flash" -> "gemini-2.5-flash-image";
            case "gemini-pro-2k", "gemini-pro-4k" -> "gemini-3-pro-image";
            case "gemini-3.1-flash-image" -> "gemini-3.1-flash-image";
            default -> modelId; // Fallback to provided name
        };
    }

    /**
     * Maps aspect ratio to valid Gemini API format.
     * Standard Gemini: 1:1, 2:3, 3:2, 3:4, 4:3, 4:5, 5:4, 9:16, 16:9, 21:9
     * Nano Banana 2 adds: 1:4, 4:1, 1:8, 8:1
     * BFL supports additional formats like 9:21 which need conversion.
     *
     * @param aspectRatio the aspect ratio from the request
     * @return valid Gemini aspect ratio, or 1:1 if invalid
     */
    private String mapToGeminiAspectRatio(String aspectRatio) {
        if (aspectRatio == null || aspectRatio.isEmpty()) {
            return "1:1";
        }

        // Valid Gemini aspect ratios (including Nano Banana 2 extended ratios)
        return switch (aspectRatio) {
            case "1:1", "2:3", "3:2", "3:4", "4:3", "4:5", "5:4", "9:16", "16:9", "21:9",
                 "1:4", "4:1", "1:8", "8:1" -> aspectRatio;
            case "9:21" -> "21:9"; // Convert BFL's 9:21 to equivalent Gemini format (reversed)
            default -> {
                logger.warn("Unsupported aspect ratio '{}', defaulting to 1:1", aspectRatio);
                yield "1:1";
            }
        };
    }

    /**
     * Extracts user-friendly error message from Gemini API error response.
     * Parses JSON error responses and provides actionable feedback to users.
     *
     * @param statusCode the HTTP status code
     * @param errorBody the raw error response body
     * @return user-friendly error message
     */
    private String extractUserFriendlyError(int statusCode, String errorBody) {
        try {
            // Try to parse JSON error response
            if (errorBody != null && errorBody.contains("{")) {
                JSONObject json = new JSONObject(errorBody);
                if (json.has("error")) {
                    JSONObject error = json.getJSONObject("error");
                    String message = error.optString("message", "");

                    // Handle quota exceeded (429)
                    if (statusCode == 429 || message.contains("quota")) {
                        // Try to extract retry time
                        if (message.contains("Try again in")) {
                            int startIdx = message.indexOf("Try again in");
                            int endIdx = message.indexOf(".", startIdx);
                            if (endIdx > startIdx) {
                                String retryInfo = message.substring(startIdx, endIdx);
                                return "Gemini API Quota überschritten. " + retryInfo + ".";
                            }
                        }
                        return "Gemini API Quota überschritten. Bitte warten Sie einen Moment und versuchen Sie es erneut.";
                    }

                    // Handle authentication errors (401/403)
                    if (statusCode == 401 || statusCode == 403) {
                        return "Gemini API Authentifizierung fehlgeschlagen. Bitte überprüfen Sie Ihren API-Schlüssel in den Einstellungen.";
                    }

                    // Handle not found (404)
                    if (statusCode == 404) {
                        if (message.contains("model")) {
                            return "Das gewählte Gemini-Modell ist nicht verfügbar. Bitte wählen Sie ein anderes Modell.";
                        }
                        return "Gemini API Ressource nicht gefunden.";
                    }

                    // Handle bad request (400)
                    if (statusCode == 400) {
                        if (message.contains("prompt")) {
                            return "Ungültiger Prompt. Bitte überprüfen Sie Ihre Eingabe.";
                        }
                        if (message.contains("image")) {
                            return "Ungültiges Bild. Bitte verwenden Sie ein anderes Bild.";
                        }
                        return "Ungültige Anfrage: " + extractShortMessage(message);
                    }

                    // Generic error with extracted message
                    if (!message.isEmpty()) {
                        return "Gemini Bildgenerierung fehlgeschlagen: " + extractShortMessage(message);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not parse Gemini error response as JSON", e);
        }

        // Fallback based on status code only
        return switch (statusCode) {
            case 429 -> "Gemini API Quota überschritten. Bitte warten Sie einen Moment.";
            case 401, 403 -> "Gemini API Authentifizierung fehlgeschlagen. Bitte überprüfen Sie Ihren API-Schlüssel.";
            case 404 -> "Gemini Ressource nicht gefunden. Bitte wählen Sie ein anderes Modell.";
            case 400 -> "Ungültige Anfrage an Gemini API.";
            case 500, 502, 503, 504 -> "Gemini API Server-Fehler. Bitte versuchen Sie es später erneut.";
            default -> "Gemini Bildgenerierung fehlgeschlagen (HTTP " + statusCode + ").";
        };
    }

    /**
     * Extracts a short, user-friendly portion of an error message.
     * Removes technical details and limits length.
     *
     * @param fullMessage the full error message
     * @return shortened user-friendly message
     */
    private String extractShortMessage(String fullMessage) {
        if (fullMessage == null || fullMessage.isEmpty()) {
            return "Unbekannter Fehler";
        }

        // Take first sentence or up to 150 characters
        int dotIndex = fullMessage.indexOf('.');
        int newlineIndex = fullMessage.indexOf('\n');

        int endIndex = fullMessage.length();
        if (dotIndex > 0 && dotIndex < 150) {
            endIndex = Math.min(endIndex, dotIndex);
        }
        if (newlineIndex > 0 && newlineIndex < endIndex) {
            endIndex = newlineIndex;
        }
        endIndex = Math.min(endIndex, 150);

        return fullMessage.substring(0, endIndex).trim();
    }

    @Override
    public String getApiKey() {
        return apiKey;
    }

    @Override
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        logger.info("Gemini API key updated");
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    /**
     * Logs the structure of a Gemini API response without including large image data.
     * This helps with debugging API responses while keeping logs readable.
     *
     * @param responseBody the full API response body
     */
    private void logResponseStructure(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);

            // Create a summary of the response structure
            StringBuilder summary = new StringBuilder("Gemini API response structure:\n");

            // Check for error response first
            if (json.has("error")) {
                JSONObject error = json.getJSONObject("error");
                summary.append("  - ERROR RESPONSE:\n");
                summary.append("      code: ").append(error.optInt("code", 0)).append("\n");
                summary.append("      message: ").append(error.optString("message", "unknown")).append("\n");
                if (error.has("status")) {
                    summary.append("      status: ").append(error.getString("status")).append("\n");
                }
                logger.error(summary.toString());
                return;
            }

            // Log candidates array info
            if (json.has("candidates")) {
                JSONArray candidates = json.getJSONArray("candidates");
                summary.append("  - candidates: ").append(candidates.length()).append(" item(s)\n");

                for (int i = 0; i < candidates.length(); i++) {
                    JSONObject candidate = candidates.getJSONObject(i);
                    summary.append("    [").append(i).append("]:\n");

                    // Log content parts without image data
                    if (candidate.has("content")) {
                        JSONObject content = candidate.getJSONObject("content");
                        if (content.has("parts")) {
                            JSONArray parts = content.getJSONArray("parts");
                            summary.append("      - parts: ").append(parts.length()).append(" item(s)\n");

                            for (int j = 0; j < parts.length(); j++) {
                                JSONObject part = parts.getJSONObject(j);
                                summary.append("        [").append(j).append("]: ");

                                if (part.has("inlineData")) {
                                    JSONObject inlineData = part.getJSONObject("inlineData");
                                    String mimeType = inlineData.optString("mimeType", "unknown");
                                    int dataLength = inlineData.optString("data", "").length();
                                    summary.append("inlineData (").append(mimeType).append(", ").append(dataLength).append(" bytes)\n");
                                } else if (part.has("text")) {
                                    String text = part.getString("text");
                                    summary.append("text (").append(text.length()).append(" chars): ")
                                           .append(text.substring(0, Math.min(100, text.length()))).append("...\n");
                                } else {
                                    summary.append(part.keys().toString()).append("\n");
                                }
                            }
                        }
                    }

                    // Log safety ratings if present
                    if (candidate.has("safetyRatings")) {
                        JSONArray safetyRatings = candidate.getJSONArray("safetyRatings");
                        summary.append("      - safetyRatings: ").append(safetyRatings.length()).append(" item(s)\n");
                    }
                }
            }

            // Log usage metadata if present
            if (json.has("usageMetadata")) {
                JSONObject usage = json.getJSONObject("usageMetadata");
                summary.append("  - usageMetadata:\n");
                usage.keys().forEachRemaining(key -> {
                    summary.append("      ").append(key).append(": ").append(usage.opt(key)).append("\n");
                });
            }

            logger.debug(summary.toString());

        } catch (Exception e) {
            logger.debug("Could not parse response structure for logging", e);
        }
    }

    @Override
    public CompletableFuture<String> analyzeYouTubeVideo(
            String youtubeUrl,
            int startSeconds,
            int endSeconds,
            String analysisPrompt) {

        return CompletableFuture.supplyAsync(() -> {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException(I18nManager.getText("youtube.dialog.error.no.api.key"));
            }

            if (youtubeUrl == null || youtubeUrl.trim().isEmpty()) {
                throw new IllegalArgumentException(I18nManager.getText("youtube.dialog.error.empty.url"));
            }

            try {
                // Build request body for video analysis
                JSONObject requestBody = buildYouTubeAnalysisRequestBody(
                    youtubeUrl, startSeconds, endSeconds, analysisPrompt);

                // Use text generation model for video understanding
                // gemini-3.5-flash supports video understanding (gemini-2.0-flash EOL June 2026)
                String modelName = "gemini-3.5-flash";
                String endpoint = GEMINI_API_BASE_URL + "/models/" + modelName +
                                ":generateContent?key=" + apiKey;

                logger.info("Submitting YouTube video analysis request to: {}", endpoint);
                logger.debug("Analyzing video: {} ({}s - {}s)",
                    youtubeUrl, startSeconds, endSeconds > 0 ? endSeconds : "end");

                // Create HTTP request
                RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    MediaType.parse("application/json; charset=utf-8")
                );

                // Use longer timeout for video analysis (5 minutes)
                OkHttpClient videoClient = httpClient.newBuilder()
                    .readTimeout(300, TimeUnit.SECONDS)
                    .build();

                Request httpRequest = new Request.Builder()
                    .url(endpoint)
                    .post(body)
                    .build();

                // Execute synchronous API call
                try (Response response = videoClient.newCall(httpRequest).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        logger.info("YouTube video analysis response received: {} bytes", responseBody.length());

                        // Extract text response
                        String analysisResult = extractTextFromVideoResponse(responseBody);
                        logger.info("Video analysis completed: {} characters extracted", analysisResult.length());

                        return analysisResult;

                    } else {
                        String errorBody = response.body() != null ? response.body().string() : "No error details";
                        logger.error("YouTube video analysis failed: HTTP {} - {}", response.code(), errorBody);

                        // Extract user-friendly error message
                        String userFriendlyError = extractVideoAnalysisError(response.code(), errorBody);
                        throw new RuntimeException(userFriendlyError);
                    }
                }

            } catch (java.net.SocketTimeoutException e) {
                logger.error("YouTube video analysis timed out", e);
                throw new RuntimeException(I18nManager.getText("youtube.dialog.error.timeout"), e);
            } catch (IllegalStateException | IllegalArgumentException e) {
                throw e; // Re-throw validation errors
            } catch (RuntimeException e) {
                throw e; // Re-throw runtime errors with user-friendly messages
            } catch (Exception e) {
                logger.error("Failed to analyze YouTube video", e);
                throw new RuntimeException(I18nManager.getText("youtube.dialog.error.analysis.failed", e.getMessage()), e);
            }
        });
    }

    /**
     * Builds the request body for YouTube video analysis.
     *
     * @param youtubeUrl the YouTube video URL
     * @param startSeconds start time in seconds (0 for beginning)
     * @param endSeconds end time in seconds (0 for entire video)
     * @param analysisPrompt optional custom analysis prompt
     * @return JSON request body
     */
    private JSONObject buildYouTubeAnalysisRequestBody(
            String youtubeUrl,
            int startSeconds,
            int endSeconds,
            String analysisPrompt) {

        JSONObject body = new JSONObject();

        // Contents Array
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();

        // File Data Part (YouTube URL)
        JSONObject fileDataPart = new JSONObject();
        JSONObject fileData = new JSONObject();
        fileData.put("fileUri", youtubeUrl.trim());
        fileDataPart.put("file_data", fileData);

        // Add video metadata with time offsets if specified
        if (startSeconds > 0 || endSeconds > 0) {
            JSONObject videoMetadata = new JSONObject();

            if (startSeconds > 0) {
                JSONObject startOffset = new JSONObject();
                startOffset.put("seconds", startSeconds);
                videoMetadata.put("start_offset", startOffset);
            }

            if (endSeconds > 0) {
                JSONObject endOffset = new JSONObject();
                endOffset.put("seconds", endSeconds);
                videoMetadata.put("end_offset", endOffset);
            }

            fileDataPart.put("video_metadata", videoMetadata);
            logger.debug("Video analysis with time range: {}s - {}s",
                startSeconds, endSeconds > 0 ? endSeconds : "end");
        }

        parts.put(fileDataPart);

        // Text Part (Analysis Prompt)
        JSONObject textPart = new JSONObject();
        String prompt = (analysisPrompt != null && !analysisPrompt.trim().isEmpty())
            ? analysisPrompt.trim()
            : I18nManager.getText("youtube.dialog.prompt.default");
        textPart.put("text", prompt);
        parts.put(textPart);

        content.put("parts", parts);
        contents.put(content);
        body.put("contents", contents);

        // Generation Config - request TEXT output only
        JSONObject genConfig = new JSONObject();
        genConfig.put("responseModalities", new JSONArray().put("TEXT"));
        body.put("generationConfig", genConfig);

        logger.debug("Built YouTube analysis request body: url={}, prompt={} chars",
            youtubeUrl, prompt.length());

        return body;
    }

    /**
     * Extracts text content from a Gemini video analysis response.
     *
     * @param responseBody the JSON response from Gemini API
     * @return the extracted text analysis
     */
    private String extractTextFromVideoResponse(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);

            // Check for error
            if (json.has("error")) {
                JSONObject error = json.getJSONObject("error");
                String errorMessage = error.optString("message", "Unknown API error");
                throw new RuntimeException(I18nManager.getText("youtube.dialog.error.api", errorMessage));
            }

            // Check for candidates
            if (!json.has("candidates")) {
                // Check if there's a promptFeedback with blockReason
                if (json.has("promptFeedback")) {
                    JSONObject promptFeedback = json.getJSONObject("promptFeedback");
                    if (promptFeedback.has("blockReason")) {
                        String blockReason = promptFeedback.getString("blockReason");
                        logger.warn("Video analysis blocked: {}", blockReason);
                        throw new RuntimeException(I18nManager.getText("youtube.dialog.error.blocked", blockReason));
                    }
                }
                throw new RuntimeException(I18nManager.getText("youtube.dialog.error.no.response"));
            }

            // Navigate: candidates[0].content.parts
            JSONArray candidates = json.getJSONArray("candidates");
            if (candidates.length() == 0) {
                throw new RuntimeException(I18nManager.getText("youtube.dialog.error.no.response"));
            }

            JSONObject candidate = candidates.getJSONObject(0);

            // Check for finish reason indicating an issue
            if (candidate.has("finishReason")) {
                String finishReason = candidate.getString("finishReason");
                if (!"STOP".equals(finishReason) && !"MAX_TOKENS".equals(finishReason)) {
                    logger.warn("Video analysis finished with reason: {}", finishReason);
                    if ("SAFETY".equals(finishReason)) {
                        throw new RuntimeException(I18nManager.getText("youtube.dialog.error.safety"));
                    }
                }
            }

            JSONArray parts = candidate.getJSONObject("content").getJSONArray("parts");

            // Extract all text parts and concatenate
            StringBuilder textBuilder = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.getJSONObject(i);
                if (part.has("text")) {
                    if (textBuilder.length() > 0) {
                        textBuilder.append("\n\n");
                    }
                    textBuilder.append(part.getString("text"));
                }
            }

            if (textBuilder.length() == 0) {
                throw new RuntimeException(I18nManager.getText("youtube.dialog.error.no.text"));
            }

            return textBuilder.toString().trim();

        } catch (JSONException e) {
            logger.error("Failed to parse video analysis response: {}", e.getMessage());
            throw new RuntimeException(I18nManager.getText("youtube.dialog.error.parse"), e);
        }
    }

    /**
     * Extracts user-friendly error message from video analysis error response.
     *
     * @param statusCode the HTTP status code
     * @param errorBody the raw error response body
     * @return user-friendly error message
     */
    private String extractVideoAnalysisError(int statusCode, String errorBody) {
        try {
            if (errorBody != null && errorBody.contains("{")) {
                JSONObject json = new JSONObject(errorBody);
                if (json.has("error")) {
                    JSONObject error = json.getJSONObject("error");
                    String message = error.optString("message", "");

                    // Check for common video-specific errors
                    if (message.contains("Video not found") || message.contains("not available")) {
                        return I18nManager.getText("youtube.dialog.error.not.found");
                    }
                    if (message.contains("private") || message.contains("not public")) {
                        return I18nManager.getText("youtube.dialog.error.not.public");
                    }
                    if (message.contains("quota")) {
                        return I18nManager.getText("youtube.dialog.error.quota");
                    }
                    if (message.contains("Invalid YouTube URL") || message.contains("invalid URL")) {
                        return I18nManager.getText("youtube.dialog.error.invalid.url");
                    }

                    if (!message.isEmpty()) {
                        return I18nManager.getText("youtube.dialog.error.api", extractShortMessage(message));
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not parse video analysis error response", e);
        }

        // Fallback based on status code
        return switch (statusCode) {
            case 400 -> I18nManager.getText("youtube.dialog.error.invalid.url");
            case 401, 403 -> I18nManager.getText("youtube.dialog.error.auth");
            case 404 -> I18nManager.getText("youtube.dialog.error.not.found");
            case 429 -> I18nManager.getText("youtube.dialog.error.quota");
            case 500, 502, 503, 504 -> I18nManager.getText("youtube.dialog.error.server");
            default -> I18nManager.getText("youtube.dialog.error.http", statusCode);
        };
    }

    @Override
    public CompletableFuture<BrainstormTurn> chatTurn(String systemPrompt, JSONArray contents, boolean withTools,
            boolean noMoreCalls) {
        return CompletableFuture.supplyAsync(() -> {
            requireApiKey();

            JSONObject requestBody = new JSONObject()
                    .put("systemInstruction", new JSONObject()
                            .put("parts", new JSONArray().put(new JSONObject().put("text", systemPrompt))))
                    .put("contents", contents)
                    .put("generationConfig", new JSONObject().put("responseMimeType", "application/json"));
            if (withTools) {
                requestBody.put("tools", new JSONArray().put(new JSONObject().put("functionDeclarations", BRAINSTORM_TOOLS)));
                if (noMoreCalls) {
                    requestBody.put("toolConfig", new JSONObject()
                            .put("functionCallingConfig", new JSONObject().put("mode", "NONE")));
                }
            }

            JSONObject candidate = postBrainstorm(BRAINSTORM_MODEL, requestBody, TIMEOUT_SECONDS);
            JSONArray parts = candidate.optJSONObject("content") != null
                    ? candidate.getJSONObject("content").optJSONArray("parts") : null;
            if (parts == null) {
                throw new RuntimeException(I18nManager.getText("brainstorm.error.no.response"));
            }

            StringBuilder text = new StringBuilder();
            List<BrainstormTurn.Call> calls = new ArrayList<>();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.getJSONObject(i);
                text.append(part.optString("text", ""));
                JSONObject call = part.optJSONObject("functionCall");
                if (call != null) {
                    calls.add(new BrainstormTurn.Call(call.optString("name"), call.optJSONObject("args") != null
                            ? call.getJSONObject("args") : new JSONObject()));
                }
            }
            if (text.length() == 0 && calls.isEmpty()) {
                throw new RuntimeException(I18nManager.getText("brainstorm.error.no.response"));
            }
            return new BrainstormTurn(text.toString(), parts, calls);
        });
    }

    @Override
    public CompletableFuture<String> suggestTitle(String content, String language) {
        return CompletableFuture.supplyAsync(() -> {
            requireApiKey();

            JSONObject requestBody = new JSONObject()
                    .put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts",
                            new JSONArray().put(new JSONObject().put("text", "Name this session in " + language
                                    + " with one or two words (at most 24 characters), based on what the user wants "
                                    + "to create. Answer with the name only, no quotes, no punctuation.\n\n" + content)))))
                    .put("generationConfig", new JSONObject()
                            .put("thinkingConfig", new JSONObject().put("thinkingLevel", "low")));

            JSONObject candidate = postBrainstorm(BRAINSTORM_MODEL, requestBody, TIMEOUT_SECONDS);
            JSONArray parts = candidate.optJSONObject("content") != null
                    ? candidate.getJSONObject("content").optJSONArray("parts") : null;
            StringBuilder text = new StringBuilder();
            for (int i = 0; parts != null && i < parts.length(); i++) {
                text.append(parts.getJSONObject(i).optString("text", ""));
            }
            return text.toString();
        });
    }

    @Override
    public CompletableFuture<ResearchNote> research(String query) {
        return CompletableFuture.supplyAsync(() -> {
            requireApiKey();

            ResearchNote note = researchOnce(RESEARCH_INSTRUCTION + query, query);
            if (note.sources().isEmpty()) {
                logger.info("Research without sources, asking again: {}", query);
                note = researchOnce(RESEARCH_RETRY_INSTRUCTION + RESEARCH_INSTRUCTION + query, query);
            }
            return note;
        });
    }

    private ResearchNote researchOnce(String prompt, String query) {
        JSONObject requestBody = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts",
                        new JSONArray().put(new JSONObject().put("text", prompt)))))
                .put("tools", new JSONArray()
                        .put(new JSONObject().put("google_search", new JSONObject()))
                        .put(new JSONObject().put("url_context", new JSONObject())))
                .put("generationConfig", new JSONObject()
                        .put("thinkingConfig", new JSONObject().put("thinkingLevel", "low")));

        JSONObject candidate = postBrainstorm(BRAINSTORM_MODEL, requestBody, RESEARCH_TIMEOUT_SECONDS);
        JSONArray parts = candidate.optJSONObject("content") != null
                ? candidate.getJSONObject("content").optJSONArray("parts") : null;
        StringBuilder text = new StringBuilder();
        for (int i = 0; parts != null && i < parts.length(); i++) {
            text.append(parts.getJSONObject(i).optString("text", ""));
        }
        if (text.length() == 0) {
            throw new RuntimeException(I18nManager.getText("brainstorm.error.no.response"));
        }
        return new ResearchNote(query, text.toString().trim(), extractSources(candidate));
    }

    private void requireApiKey() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException(I18nManager.getText("brainstorm.error.no.api.key"));
        }
    }

    /**
     * Sends a generateContent request of the brainstorm assistant and returns the first candidate.
     *
     * @throws RuntimeException with a user-friendly message if the request fails or has no candidate
     */
    private JSONObject postBrainstorm(String model, JSONObject requestBody, int readTimeoutSeconds) {
        Request httpRequest = new Request.Builder()
                .url(GEMINI_API_BASE_URL + "/models/" + model + ":generateContent")
                .header("x-goog-api-key", apiKey)
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json; charset=utf-8")))
                .build();
        OkHttpClient client = httpClient.newBuilder().readTimeout(readTimeoutSeconds, TimeUnit.SECONDS).build();

        try (Response response = client.newCall(httpRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                logger.error("Brainstorm request failed: HTTP {} - {}", response.code(), responseBody);
                throw new RuntimeException(I18nManager.getText("brainstorm.error.http", response.code()));
            }
            JSONArray candidates = new JSONObject(responseBody).optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                throw new RuntimeException(I18nManager.getText("brainstorm.error.no.response"));
            }
            return candidates.getJSONObject(0);
        } catch (java.io.IOException e) {
            logger.error("Brainstorm request failed", e);
            throw new RuntimeException(I18nManager.getText("brainstorm.error.request", e.getMessage()), e);
        } catch (JSONException e) {
            logger.error("Failed to parse brainstorm response: {}", e.getMessage());
            throw new RuntimeException(I18nManager.getText("brainstorm.error.no.response"), e);
        }
    }

    /**
     * Collects the pages a researched answer is based on: the grounding chunks of the Google search (the URI is a
     * Google redirect, the title is the domain) and the pages read by the URL context.
     */
    private List<ResearchNote.Source> extractSources(JSONObject candidate) {
        List<ResearchNote.Source> sources = new ArrayList<>();

        JSONObject grounding = candidate.optJSONObject("groundingMetadata");
        JSONArray chunks = grounding != null ? grounding.optJSONArray("groundingChunks") : null;
        for (int i = 0; chunks != null && i < chunks.length(); i++) {
            JSONObject web = chunks.getJSONObject(i).optJSONObject("web");
            if (web != null && !web.optString("uri").isEmpty()) {
                addSource(sources, web.optString("title"), web.getString("uri"));
            }
        }

        JSONObject urlContext = candidate.optJSONObject("urlContextMetadata");
        JSONArray pages = urlContext != null ? urlContext.optJSONArray("urlMetadata") : null;
        for (int i = 0; pages != null && i < pages.length(); i++) {
            JSONObject page = pages.getJSONObject(i);
            if (page.optString("urlRetrievalStatus").endsWith("SUCCESS")) {
                addSource(sources, "", page.optString("retrievedUrl"));
            }
        }
        return sources;
    }

    private static void addSource(List<ResearchNote.Source> sources, String title, String url) {
        if (url.isEmpty() || sources.stream().anyMatch((ResearchNote.Source source) -> source.url().equals(url))) {
            return;
        }
        String domain = title;
        if (domain.isEmpty()) {
            String host = java.net.URI.create(url).getHost();
            domain = host != null ? host : url;
        }
        sources.add(new ResearchNote.Source(domain.startsWith("www.") ? domain.substring(4) : domain, url));
    }

    // ===== GEMINI OMNI VIDEO GENERATION =====

    /**
     * Generates a video with Gemini Omni through the Interactions API (synchronous, no operation polling).
     * The video is delivered as a file URI, which needs the stored interaction ({@code store: true}); the
     * file becomes ACTIVE shortly after the answer, so it is waited for before the URL is handed over.
     *
     * @param request the generation request with video-specific parameters
     * @return a CompletableFuture containing the completed or failed job info
     */
    @Override
    public CompletableFuture<GenerationJobInfo> submitOmniGenerationRequest(ImageGenerationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("Gemini API key is not configured");
            }

            try {
                JSONObject requestBody = buildOmniRequestBody(request);

                logger.info("Submitting Omni video generation: model={}, resolution={}, duration={}s, aspect={}",
                        request.getModel(), request.getVideoResolution(), request.getVideoDuration(),
                        request.getAspectRatio());

                OkHttpClient omniClient = httpClient.newBuilder()
                        .readTimeout(OMNI_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .build();
                Request httpRequest = new Request.Builder()
                        .url(GEMINI_API_BASE_URL + "/interactions")
                        .header("x-goog-api-key", apiKey)
                        .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                        .build();

                try (Response response = omniClient.newCall(httpRequest).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (!response.isSuccessful()) {
                        String errorMsg = extractOmniHttpError(response.code(), responseBody);
                        logger.error("Omni generation failed: HTTP {} - {}", response.code(), responseBody);
                        return failedOmniJob(errorMsg);
                    }

                    JSONObject json = new JSONObject(responseBody);
                    String videoUri = extractOmniVideoUri(json);
                    if (videoUri == null) {
                        String errorMsg = extractOmniError(json);
                        logger.error("Omni generation returned no video: {}", errorMsg);
                        return failedOmniJob(errorMsg);
                    }

                    waitForOmniFile(videoUri);
                    logger.info("Omni generation completed: interaction={}", json.optString("id", "?"));

                    return new GenerationJobInfo.Builder()
                            .jobId(json.optString("id", videoUri))
                            .status("completed")
                            .videoDownloadUrl(videoUri)
                            .isSynchronous(false)
                            .isVideoGeneration(true)
                            .provider(ApiProvider.GOOGLE_GEMINI)
                            .build();
                }
            } catch (Exception e) {
                logger.error("Failed to generate Omni video", e);
                return failedOmniJob("Omni request failed: " + e.getMessage());
            }
        });
    }

    /**
     * Returns the message of the API error as it is, the generic mapping of {@link #extractUserFriendlyError}
     * hides the cause (a 400 about the input image would read "invalid prompt"). Quota and key errors keep
     * their friendly text.
     */
    private String extractOmniHttpError(int statusCode, String errorBody) {
        if (statusCode == 429 || statusCode == 401 || statusCode == 403) {
            return extractUserFriendlyError(statusCode, errorBody);
        }
        try {
            JSONObject error = new JSONObject(errorBody).getJSONObject("error");
            String message = error.optString("message", "");
            if (!message.isEmpty()) {
                return message.length() > 300 ? message.substring(0, 300) + "..." : message;
            }
        } catch (JSONException e) {
            logger.debug("Could not parse Omni error response as JSON", e);
        }
        return extractUserFriendlyError(statusCode, errorBody);
    }

    private GenerationJobInfo failedOmniJob(String errorMessage) {
        return new GenerationJobInfo.Builder()
                .status("failed")
                .errorMessage(errorMessage)
                .isSynchronous(false)
                .isVideoGeneration(true)
                .provider(ApiProvider.GOOGLE_GEMINI)
                .build();
    }

    /**
     * Builds the Interactions API request body for Gemini Omni: the input (text, with an optional start image),
     * the video response format and {@code store: true} (required for delivery by URI).
     */
    private JSONObject buildOmniRequestBody(ImageGenerationRequest request) {
        JSONObject body = new JSONObject();
        body.put("model", request.getModel());

        String prompt = request.getPrompt();
        if (request.hasInputImage()) {
            // The attached image is a reference (task reference_to_video, tag <IMAGE_REF_0> in the prompt),
            // or the first frame if the prompt says <FIRST_FRAME> (task image_to_video)
            boolean firstFrame = prompt.contains("<FIRST_FRAME>");
            if (!firstFrame && !prompt.contains("<IMAGE_REF_0>")) {
                prompt = OMNI_REFERENCE_NOTE + prompt;
            }
            body.put("generation_config", new JSONObject().put("video_config", new JSONObject()
                    .put("task", firstFrame ? "image_to_video" : "reference_to_video")));
        }

        // The duration is no parameter, it is steered by the prompt ("A 5-second video: ...")
        if (request.getVideoDuration() != null) {
            prompt = "A " + request.getVideoDuration() + "-second video: " + prompt;
        }

        if (request.hasInputImage()) {
            JSONArray input = new JSONArray();
            input.put(new JSONObject()
                    .put("type", "image")
                    .put("data", request.getInputImage())
                    .put("mime_type", detectImageMimeType(request.getInputImage())));
            input.put(new JSONObject().put("type", "text").put("text", prompt));
            body.put("input", input);
        } else {
            body.put("input", prompt);
        }

        // Only 16:9 and 9:16 exist
        String aspectRatio = "9:16".equals(request.getAspectRatio()) ? "9:16" : "16:9";
        String resolution = request.getVideoResolution() != null ? request.getVideoResolution() : "720p";
        body.put("response_format", new JSONObject()
                .put("type", "video")
                .put("aspect_ratio", aspectRatio)
                .put("resolution", resolution)
                .put("delivery", "uri"));
        body.put("store", true);

        return body;
    }

    /**
     * Detects the MIME type of a Base64 encoded image from its first characters (JPEG, PNG or WebP).
     */
    private String detectImageMimeType(String base64Image) {
        if (base64Image.startsWith("/9j/")) return "image/jpeg";
        if (base64Image.startsWith("UklGR")) return "image/webp";
        return "image/png";
    }

    /**
     * Extracts the video file URI from a completed interaction. Expected structure:
     * { "steps": [{ "type": "model_output", "content": [{ "type": "video", "uri": "..." }] }] }
     */
    private String extractOmniVideoUri(JSONObject interaction) {
        JSONArray steps = interaction.optJSONArray("steps");
        if (steps == null) return null;

        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.optJSONObject(i);
            JSONArray content = step != null && "model_output".equals(step.optString("type"))
                    ? step.optJSONArray("content") : null;
            if (content == null) continue;

            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                String uri = part != null ? part.optString("uri", "") : "";
                if (!uri.isEmpty()) return uri;
            }
        }
        return null;
    }

    /**
     * Describes why an interaction has no video: the {@code errors} of a failed interaction, else the text the
     * model answered with (for example a refusal), else the status.
     */
    private String extractOmniError(JSONObject interaction) {
        JSONArray errors = interaction.optJSONArray("errors");
        if (errors != null && !errors.isEmpty()) {
            JSONObject first = errors.optJSONObject(0);
            String message = first != null ? first.optString("message", "") : "";
            if (!message.isEmpty()) return message;
        }

        JSONArray steps = interaction.optJSONArray("steps");
        for (int i = 0; steps != null && i < steps.length(); i++) {
            JSONObject step = steps.optJSONObject(i);
            JSONArray content = step != null && "model_output".equals(step.optString("type"))
                    ? step.optJSONArray("content") : null;
            for (int j = 0; content != null && j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                String text = part != null ? part.optString("text", "") : "";
                if (!text.isEmpty()) return text.length() > 300 ? text.substring(0, 300) + "..." : text;
            }
        }
        return "No video in response (status: " + interaction.optString("status", "unknown") + ")";
    }

    /**
     * Waits until the video file of an Omni interaction is ACTIVE, which takes a few seconds after the answer.
     * A file that does not become ACTIVE in time is handed over anyway, the download then reports the problem.
     */
    private void waitForOmniFile(String videoUri) throws Exception {
        java.util.regex.Matcher matcher = OMNI_FILE_ID.matcher(videoUri);
        if (!matcher.find()) return;

        Request request = new Request.Builder()
                .url(GEMINI_API_BASE_URL + "/files/" + matcher.group(1))
                .header("x-goog-api-key", apiKey)
                .get()
                .build();

        for (int attempt = 0; attempt < OMNI_FILE_POLL_ATTEMPTS; attempt++) {
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                String state = response.isSuccessful() ? new JSONObject(responseBody).optString("state", "") : "";

                if ("ACTIVE".equals(state)) return;
                if ("FAILED".equals(state)) throw new IllegalStateException("Video file processing failed");
            }
            Thread.sleep(OMNI_FILE_POLL_INTERVAL_MS);
        }
        logger.warn("Omni video file {} did not become ACTIVE in time", videoUri);
    }
}
