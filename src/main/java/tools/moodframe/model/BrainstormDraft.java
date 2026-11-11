package tools.moodframe.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The current result of a brainstorm session: the prompt and the suggested settings.
 *
 * <p>All settings are optional ({@code null} or empty means "no suggestion").</p>
 *
 * @since 1.7.0
 */
public class BrainstormDraft {

    private String mediaType = "image";
    private String prompt = "";
    private String style;
    private String styleReason = "";
    private String colorStyle;
    private String aspectRatio;
    private String targetModel;
    private String resolution;
    private String modelReason = "";
    private Integer durationSeconds;
    private List<BrainstormFact> facts = new ArrayList<>();

    /**
     * Reads a draft from its JSON form (the "draft" object of an AI reply, or a stored draft).
     *
     * @param json the draft JSON object
     * @return the parsed draft
     */
    public static BrainstormDraft fromJson(JSONObject json) {
        BrainstormDraft draft = new BrainstormDraft();
        draft.mediaType = "video".equals(json.optString("media_type")) ? "video" : "image";
        draft.prompt = json.optString("prompt", "");
        draft.style = optText(json, "style");
        draft.styleReason = json.optString("style_reason", "");
        draft.colorStyle = optText(json, "color_style");
        draft.aspectRatio = optText(json, "aspect_ratio");
        draft.targetModel = optText(json, "target_model");
        draft.resolution = optText(json, "resolution");
        draft.modelReason = json.optString("model_reason", "");

        JSONArray factArray = json.optJSONArray("facts");
        for (int i = 0; factArray != null && i < factArray.length(); i++) {
            JSONObject factJson = factArray.optJSONObject(i);
            BrainstormFact fact = factJson != null ? BrainstormFact.fromJson(factJson) : null;
            if (fact != null) {
                draft.facts.add(fact);
            }
        }

        JSONObject video = json.optJSONObject("video");
        if (video != null) {
            draft.durationSeconds = video.has("duration_seconds") && !video.isNull("duration_seconds")
                    ? video.optInt("duration_seconds") : null;
        }
        return draft;
    }

    /**
     * Returns the JSON form of this draft, readable again by {@link #fromJson(JSONObject)}.
     *
     * @return the JSON object
     */
    public JSONObject toJson() {
        JSONObject video = new JSONObject();
        video.put("duration_seconds", durationSeconds == null ? JSONObject.NULL : durationSeconds);

        JSONObject json = new JSONObject();
        json.put("media_type", mediaType);
        json.put("prompt", prompt);
        json.put("style", style == null ? JSONObject.NULL : style);
        json.put("style_reason", styleReason);
        json.put("color_style", colorStyle == null ? JSONObject.NULL : colorStyle);
        json.put("aspect_ratio", aspectRatio == null ? JSONObject.NULL : aspectRatio);
        json.put("target_model", targetModel == null ? JSONObject.NULL : targetModel);
        json.put("resolution", resolution == null ? JSONObject.NULL : resolution);
        json.put("model_reason", modelReason);
        json.put("video", video);
        JSONArray factArray = new JSONArray();
        facts.forEach((BrainstormFact fact) -> factArray.put(fact.toJson()));
        json.put("facts", factArray);
        return json;
    }

    /** Returns the text of a string key, or {@code null} for a missing, null or blank value. */
    private static String optText(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        String value = json.optString(key, "").trim();
        return value.isEmpty() || "null".equalsIgnoreCase(value) ? null : value;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public boolean isVideo() {
        return "video".equals(mediaType);
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public String getStyleReason() {
        return styleReason;
    }

    public String getColorStyle() {
        return colorStyle;
    }

    public void setColorStyle(String colorStyle) {
        this.colorStyle = colorStyle;
    }

    public String getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(String aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    public String getTargetModel() {
        return targetModel;
    }

    public void setTargetModel(String targetModel) {
        this.targetModel = targetModel;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getModelReason() {
        return modelReason;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public List<BrainstormFact> getFacts() {
        return facts;
    }

}
