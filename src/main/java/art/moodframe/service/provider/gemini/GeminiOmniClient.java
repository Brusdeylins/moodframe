package art.moodframe.service.provider.gemini;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.service.provider.GenerationJobInfo;
import art.moodframe.service.provider.ProviderException;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * The video generation of Gemini Omni through the Interactions API: synchronous, the answer comes when the video
 * exists, as a file URI that becomes ACTIVE shortly after.
 *
 * @since 2.1.3
 */
final class GeminiOmniClient {

    private static final Logger logger = LoggerFactory.getLogger(GeminiOmniClient.class);

    private static final int OMNI_TIMEOUT_SECONDS = 600; // Omni answers when the video is finished
    /* Without a task and a tag, Omni ignores an attached image (live check: the logo never showed up) */
    private static final String OMNI_REFERENCE_NOTE = "The attached image <IMAGE_REF_0> is a reference (for example "
            + "a logo or product). Show it faithfully wherever the prompt mentions the attached image or logo. ";
    private static final int OMNI_FILE_POLL_ATTEMPTS = 40; // video file becomes ACTIVE shortly after the answer
    private static final int OMNI_FILE_POLL_INTERVAL_MS = 3000;
    private static final java.util.regex.Pattern OMNI_FILE_ID = java.util.regex.Pattern.compile("files/([^:/?]+)");

    private final GeminiTransport transport;

    GeminiOmniClient(GeminiTransport transport) {
        this.transport = transport;
    }

    /**
     * Generates a video with Gemini Omni through the Interactions API (synchronous, no operation polling).
     * The video is delivered as a file URI, which needs the stored interaction ({@code store: true}); the
     * file becomes ACTIVE shortly after the answer, so it is waited for before the URL is handed over.
     *
     * @param request the generation request with video-specific parameters
     * @return a CompletableFuture containing the completed or failed job info
     */
    CompletableFuture<GenerationJobInfo> submit(ImageGenerationRequest request) {
        return transport.async(() -> {
            if (!transport.hasKey()) {
                throw ProviderException.noKey("Gemini");
            }

            try {
                JSONObject requestBody = buildOmniRequestBody(request);

                logger.info("Submitting Omni video generation: model={}, resolution={}, duration={}s, aspect={}",
                        request.getModel(), request.getVideoResolution(), request.getVideoDuration(),
                        request.getAspectRatio());

                Request httpRequest = transport.request("/interactions")
                        .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                        .build();

                try (Response response = transport.client(OMNI_TIMEOUT_SECONDS).newCall(httpRequest).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (!response.isSuccessful()) {
                        logger.error("Omni generation failed: HTTP {} - {}", response.code(), responseBody);
                        return failedOmniJob(GeminiErrors.http(response.code(), responseBody,
                                GeminiErrors.Call.OMNI));
                    }

                    JSONObject json = new JSONObject(responseBody);
                    String videoUri = extractOmniVideoUri(json);
                    if (videoUri == null) {
                        ProviderException error = extractOmniError(json);
                        logger.error("Omni generation returned no video: {}", error.getMessage(Locale.ENGLISH));
                        return failedOmniJob(error);
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
                return failedOmniJob(ProviderException.localized(e, "gemini.omni.error.request", e.getMessage()));
            }
        });
    }

    private static GenerationJobInfo failedOmniJob(ProviderException failure) {
        return new GenerationJobInfo.Builder()
                .failure(failure)
                .isSynchronous(false)
                .isVideoGeneration(true)
                .provider(ApiProvider.GOOGLE_GEMINI)
                .build();
    }

    /**
     * Builds the Interactions API request body for Gemini Omni: the input (text, with an optional start image),
     * the video response format and {@code store: true} (required for delivery by URI).
     */
    static JSONObject buildOmniRequestBody(ImageGenerationRequest request) {
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
    private static String detectImageMimeType(String base64Image) {
        if (base64Image.startsWith("/9j/")) return "image/jpeg";
        if (base64Image.startsWith("UklGR")) return "image/webp";
        return "image/png";
    }

    /**
     * Extracts the video file URI from a completed interaction. Expected structure:
     * { "steps": [{ "type": "model_output", "content": [{ "type": "video", "uri": "..." }] }] }
     */
    static String extractOmniVideoUri(JSONObject interaction) {
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
     * model answered with (for example a refusal), both as Google wrote them, else the status in a localizable
     * message.
     */
    static ProviderException extractOmniError(JSONObject interaction) {
        JSONArray errors = interaction.optJSONArray("errors");
        if (errors != null && !errors.isEmpty()) {
            JSONObject first = errors.optJSONObject(0);
            String message = first != null ? first.optString("message", "") : "";
            if (!message.isEmpty()) return new ProviderException(message, null);
        }

        JSONArray steps = interaction.optJSONArray("steps");
        for (int i = 0; steps != null && i < steps.length(); i++) {
            JSONObject step = steps.optJSONObject(i);
            JSONArray content = step != null && "model_output".equals(step.optString("type"))
                    ? step.optJSONArray("content") : null;
            for (int j = 0; content != null && j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                String text = part != null ? part.optString("text", "") : "";
                if (!text.isEmpty())
                    return new ProviderException(text.length() > 300 ? text.substring(0, 300) + "..." : text, null);
            }
        }
        return ProviderException.localized("gemini.omni.error.no.video", interaction.optString("status", "unknown"));
    }

    /**
     * Waits until the video file of an Omni interaction is ACTIVE, which takes a few seconds after the answer.
     * A file that does not become ACTIVE in time is handed over anyway, the download then reports the problem.
     */
    private void waitForOmniFile(String videoUri) throws Exception {
        java.util.regex.Matcher matcher = OMNI_FILE_ID.matcher(videoUri);
        if (!matcher.find()) return;

        Request request = transport.request("/files/" + matcher.group(1)).get().build();

        for (int attempt = 0; attempt < OMNI_FILE_POLL_ATTEMPTS; attempt++) {
            try (Response response = transport.client().newCall(request).execute()) {
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
