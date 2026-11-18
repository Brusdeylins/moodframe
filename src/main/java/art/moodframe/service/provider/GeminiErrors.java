package art.moodframe.service.provider;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps the error answers of the Gemini API to an {@link ApiHttpException} with its status and a message for the user
 * from the i18n resources, the one place for all Gemini calls: image, Omni video, video analysis and the chat.
 *
 * @since 2.1.3
 */
final class GeminiErrors {

    private static final Logger logger = LoggerFactory.getLogger(GeminiErrors.class);

    /** The kind of call, which decides the wording of the message. */
    enum Call {
        /** an image generation */
        IMAGE,
        /** a video generation with Omni: the text of the API tells the cause best */
        OMNI,
        /** the analysis of a YouTube video */
        VIDEO_ANALYSIS,
        /** the brainstorm chat, its research and title */
        CHAT
    }

    private GeminiErrors() {
    }

    /**
     * Maps an error answer.
     *
     * @param status the HTTP status code
     * @param body   the answer body (may be null)
     * @param call   the kind of call
     * @return the exception to throw
     */
    static ApiHttpException http(int status, String body, Call call) {
        String message = apiMessage(body);
        return switch (call) {
            case VIDEO_ANALYSIS -> videoAnalysis(status, message);
            case CHAT -> ApiHttpException.localized(status, "brainstorm.error.http", status);
            case OMNI -> status != 429 && status != 401 && status != 403 && message != null && !message.isEmpty()
                    ? new ApiHttpException(status, message.length() > 300 ? message.substring(0, 300) + "..." : message)
                    : image(status, message);
            case IMAGE -> image(status, message);
        };
    }

    /*  An image generation: quota, key, model and request problems by status and the text of the API  */
    private static ApiHttpException image(int status, String message) {
        String text = message != null ? message : "";
        if (status == 429 || text.contains("quota")) {
            int start = text.indexOf("Try again in");
            int end = start >= 0 ? text.indexOf('.', start) : -1;
            return end > start
                    ? ApiHttpException.localized(status, "gemini.error.quota.retry", text.substring(start, end))
                    : ApiHttpException.localized(status, "gemini.error.quota");
        }
        if (status == 401 || status == 403)
            return ApiHttpException.localized(status, "gemini.error.auth");
        if (status == 404)
            return ApiHttpException.localized(status, text.contains("model") || message == null
                    ? "gemini.error.model.unavailable" : "gemini.error.not.found");
        if (status == 400) {
            if (text.contains("prompt"))
                return ApiHttpException.localized(status, "gemini.error.invalid.prompt");
            if (text.contains("image"))
                return ApiHttpException.localized(status, "gemini.error.invalid.image");
            return message != null
                    ? ApiHttpException.localized(status, "gemini.error.invalid.request", shortMessage(message))
                    : ApiHttpException.localized(status, "gemini.error.bad.request");
        }
        if (!text.isEmpty())
            return ApiHttpException.localized(status, "gemini.error.generation", shortMessage(text));
        return status >= 500 && status <= 504
                ? ApiHttpException.localized(status, "gemini.error.server")
                : ApiHttpException.localized(status, "gemini.error.http", String.valueOf(status));
    }

    /*  The analysis of a YouTube video: the video is missing, private, or the URL is wrong  */
    private static ApiHttpException videoAnalysis(int status, String message) {
        if (message != null) {
            if (message.contains("Video not found") || message.contains("not available"))
                return ApiHttpException.localized(status, "brainstorm.video.error.not.found");
            if (message.contains("private") || message.contains("not public"))
                return ApiHttpException.localized(status, "brainstorm.video.error.not.public");
            if (message.contains("quota"))
                return ApiHttpException.localized(status, "brainstorm.video.error.quota");
            if (message.contains("Invalid YouTube URL") || message.contains("invalid URL"))
                return ApiHttpException.localized(status, "brainstorm.video.error.invalid.url");
            if (!message.isEmpty())
                return ApiHttpException.localized(status, "brainstorm.video.error.api", shortMessage(message));
        }
        return switch (status) {
            case 400 -> ApiHttpException.localized(status, "brainstorm.video.error.invalid.url");
            case 401, 403 -> ApiHttpException.localized(status, "brainstorm.video.error.auth");
            case 404 -> ApiHttpException.localized(status, "brainstorm.video.error.not.found");
            case 429 -> ApiHttpException.localized(status, "brainstorm.video.error.quota");
            case 500, 502, 503, 504 -> ApiHttpException.localized(status, "brainstorm.video.error.server");
            default -> ApiHttpException.localized(status, "brainstorm.video.error.http", String.valueOf(status));
        };
    }

    /**
     * Gets the message of the error object of an answer ({@code {"error": {"message": ...}}}).
     *
     * @param body the answer body (may be null)
     * @return the message, empty if the error object has none, null if the body has no error object
     */
    static String apiMessage(String body) {
        if (body == null || !body.contains("{"))
            return null;
        try {
            JSONObject error = new JSONObject(body).optJSONObject("error");
            return error != null ? error.optString("message", "") : null;
        } catch (JSONException e) {
            logger.debug("Could not parse Gemini error response as JSON", e);
            return null;
        }
    }

    /**
     * Shortens a message of the API to its first sentence or line, at most 150 characters.
     *
     * @param fullMessage the message
     * @return the short message
     */
    static String shortMessage(String fullMessage) {
        if (fullMessage == null || fullMessage.isEmpty())
            return ProviderException.localized("gemini.error.unknown").getMessage();
        int endIndex = fullMessage.length();
        int dotIndex = fullMessage.indexOf('.');
        int newlineIndex = fullMessage.indexOf('\n');
        if (dotIndex > 0 && dotIndex < 150)
            endIndex = Math.min(endIndex, dotIndex);
        if (newlineIndex > 0 && newlineIndex < endIndex)
            endIndex = newlineIndex;
        return fullMessage.substring(0, Math.min(endIndex, 150)).trim();
    }
}
