package art.moodframe.service.provider;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.util.I18nManager;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * The image generation of Gemini (Nano Banana): builds the generateContent request, reads the image or the reason
 * it was blocked, and tests the connection.
 *
 * @since 2.1.3
 */
final class GeminiImageClient {

    private static final Logger logger = LoggerFactory.getLogger(GeminiImageClient.class);

    private final GeminiTransport transport;

    GeminiImageClient(GeminiTransport transport) {
        this.transport = transport;
    }

    /**
     * Tests the connection and the key by listing the models.
     *
     * @return a future with true if the key works, false on a definite refusal; it fails on a transient failure
     */
    CompletableFuture<Boolean> testConnection() {
        return transport.async(() -> {
            if (!transport.hasKey()) {
                logger.warn("Cannot test Gemini API connection: No API key configured");
                return false;
            }

            /*  Listing the models is a lightweight call that validates the key  */
            Request request = transport.request("/models").get().build();
            logger.info("Testing Gemini API connection...");
            try (Response response = transport.client().newCall(request).execute()) {
                if (response.isSuccessful()) {
                    logger.info("Gemini API connection test successful");
                    return true;
                }
                logger.warn("Gemini API connection test failed with status: {} - {}", response.code(),
                        response.message());
                if (response.body() != null)
                    logger.debug("Gemini API error response: {}", response.body().string());

                /*  Only a definite answer (HTTP 400, 401, 403, 404) decides, any other failure is transient  */
                if (!ApiHttpException.isDefinite(response.code()))
                    throw ApiHttpException.localized(response.code(), "brainstorm.error.http", response.code());
                return false;
            } catch (java.io.IOException e) {
                logger.error("Gemini API connection test failed with exception", e);
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    /**
     * Generates one image; the answer carries it.
     *
     * @param request the request
     * @return a future with the completed job, or a failed one if the content was blocked
     */
    CompletableFuture<GenerationJobInfo> submit(ImageGenerationRequest request) {
        return transport.async(() -> {
            if (!transport.hasKey()) {
                throw ProviderException.noKey("Gemini");
            }

            try {
                JSONObject requestBody = buildRequestBody(request);
                String path = "/models/" + getGeminiModelName(request.getModel()) + ":generateContent";
                logger.info("Submitting Gemini generation request to: {}{}", transport.baseUrl(), path);

                Request httpRequest = transport.request(path)
                    .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json; charset=utf-8")))
                    .build();

                try (Response response = transport.client().newCall(httpRequest).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        logger.info("Gemini API response received: {} bytes", responseBody.length());

                        // Log response structure (without image data for debugging)
                        if (logger.isDebugEnabled())
                            logResponseStructure(responseBody);

                        // Check for content blocking before extracting image
                        String blockingReason = checkForContentBlocking(responseBody, request.hasInputImage(),
                                request.hasVideoSource());
                        if (blockingReason != null) {
                            logger.info("Gemini content blocked: {}", blockingReason);
                            return new GenerationJobInfo.Builder()
                                .jobId(java.util.UUID.randomUUID().toString())
                                .status("failed")
                                .errorMessage(blockingReason)
                                .isSynchronous(true)
                                .provider(ApiProvider.GOOGLE_GEMINI)
                                .build();
                        }

                        // NOTE: Thinking metadata extraction disabled until Gemini Image API supports it, the
                        // database stores null values for thinkingTokens and thoughtSummary until then
                        return new GenerationJobInfo.Builder()
                            .jobId(java.util.UUID.randomUUID().toString()) // Generate fake ID for consistency
                            .status("completed")
                            .base64Image(extractBase64FromResponse(responseBody))
                            .isSynchronous(true)
                            .provider(ApiProvider.GOOGLE_GEMINI)
                            .thinkingTokens(null)
                            .thoughtSummary(null)
                            .build();
                    }
                    String errorBody = response.body() != null ? response.body().string() : "No error details";
                    logger.error("Gemini API request failed: HTTP {} - {}", response.code(), errorBody);
                    throw GeminiErrors.http(response.code(), errorBody, GeminiErrors.Call.IMAGE);
                }
            } catch (java.net.SocketTimeoutException e) {
                logger.error("Gemini API request timed out", e);
                throw ProviderException.localized(e, "gemini.error.timeout");
            } catch (ProviderException e) {
                throw e;
            } catch (Exception e) {
                logger.error("Failed to submit Gemini generation request", e);
                throw ProviderException.localized(e, "gemini.error.failed", e.getMessage());
            }
        });
    }

    /**
     * Builds the Gemini API request body in JSON format.
     *
     * @param request the image generation request
     * @return JSON request body
     */
    static JSONObject buildRequestBody(ImageGenerationRequest request) {
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
        int imageCount = request.getInputImageCount();
        for (String image : request.getInputImages())
            addGeminiImagePart(parts, image);

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
    static String checkForContentBlocking(String responseBody, boolean hasInputImage, boolean hasVideoSource) {
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
    static String extractBase64FromResponse(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);

            // Check if response contains an error field
            if (json.has("error")) {
                JSONObject error = json.getJSONObject("error");
                String errorMessage = error.optString("message", "");
                int errorCode = error.optInt("code", 0);
                logger.error("Gemini API returned error: [{}] {}", errorCode, errorMessage);
                throw ProviderException.localized("gemini.error.api", errorMessage.isEmpty()
                        ? ProviderException.localized("gemini.error.unknown").getMessage() : errorMessage);
            }

            // Check if candidates array exists
            if (!json.has("candidates")) {
                logger.error("Gemini response missing 'candidates' field. Response: {}",
                    responseBody.substring(0, Math.min(500, responseBody.length())));
                throw ProviderException.localized("gemini.error.no.candidates");
            }

            // Guard: candidate without content parts (e.g. unknown finishReason with empty content)
            // Prevents a raw JSONException and surfaces Google's finishMessage instead
            JSONObject firstCandidate = json.getJSONArray("candidates").getJSONObject(0);
            if (!firstCandidate.has("content") || !firstCandidate.getJSONObject("content").has("parts")) {
                String finishReason = firstCandidate.optString("finishReason", "UNKNOWN");
                String finishMessage = firstCandidate.optString("finishMessage", "");
                logger.error("Gemini candidate contains no image parts - finishReason={}, finishMessage={}",
                        finishReason, finishMessage);
                throw ProviderException.localized("gemini.error.default", finishReason);
            }

            // Navigate: candidates[0].content.parts
            // NOTE: Gemini 3 Pro Image uses a thinking process that generates up to 2 intermediate images
            // The LAST image in the parts array is the final rendered image
            // See: https://ai.google.dev/gemini-api/docs/image-generation#thinking-process
            JSONArray parts = firstCandidate.getJSONObject("content")
                                    .getJSONArray("parts");

            if (parts.length() == 0) {
                throw ProviderException.localized("gemini.error.no.images");
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
            throw ProviderException.localized(e, "gemini.error.format", e.getMessage());
        } catch (RuntimeException e) {
            // Re-throw our custom exceptions
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error extracting Base64 from Gemini response", e);
            throw ProviderException.localized(e, "gemini.error.processing", e.getMessage());
        }
    }

    /**
     * Adds an image part to the Gemini request parts array.
     * Used for multi-image support (up to 14 reference images).
     *
     * @param parts the JSONArray of parts to add the image to
     * @param base64Image the Base64-encoded image data
     */
    private static void addGeminiImagePart(JSONArray parts, String base64Image) {
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
    private static void addGeminiVideoPart(JSONArray parts, String videoUrl, Integer startSec, Integer endSec) {
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
    static String getGeminiModelName(String modelId) {
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
    static String mapToGeminiAspectRatio(String aspectRatio) {
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
     * Logs the structure of a Gemini API response without including large image data.
     * This helps with debugging API responses while keeping logs readable.
     *
     * @param responseBody the full API response body
     */
    private static void logResponseStructure(String responseBody) {
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
}
