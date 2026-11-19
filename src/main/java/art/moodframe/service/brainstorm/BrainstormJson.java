package art.moodframe.service.brainstorm;

import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.BrainstormFact;
import art.moodframe.model.CameraSettings;
import art.moodframe.model.Mood;
import art.moodframe.model.ResearchNote;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The JSON forms the brainstorm exchanges with the language model: the draft of a reply (with its building blocks
 * and facts), which is also how a session stores its drafts ({@code brainstorm_drafts.draft_json}), and the app event
 * of a research result, which a session stores as a message and the assistant reads in later turns.
 *
 * @since 2.1.3
 */
public final class BrainstormJson {

    /** The name of the app event of a research result. */
    public static final String RESEARCH_EVENT = "research_result";

    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9A-F]{6}");
    private static final int MAX_COLORS = 5;

    private BrainstormJson() {
    }

    /**
     * Reads a draft from its JSON form (the "draft" object of an AI reply, or a stored draft). Older drafts name the
     * style and the palette only ({@code "style"}, {@code "color_style"}); they are read as references by name. A
     * mood, a frame and a saved image are referred to by name or as an object with id and name.
     *
     * @param json the draft JSON object
     * @return the parsed draft
     */
    public static BrainstormDraft draft(JSONObject json) {
        BrainstormDraft draft = new BrainstormDraft();
        draft.setMediaType("video".equals(json.optString("media_type")) ? "video" : "image");
        draft.setPrompt(json.optString("prompt", ""));
        draft.setMood(block(json.opt("mood")));
        for (Mood.Field field : BrainstormDraft.FIELDS)
            draft.setBlock(field, block(json.opt(key(field))));

        /*  older drafts name the palette as "color_style"  */
        if (!json.has(key(Mood.Field.COLOR)))
            draft.setBlock(Mood.Field.COLOR, block(json.opt("color_style")));
        draft.setStyleReason(json.optString("style_reason", ""));
        draft.setFrame(block(json.opt("frame")));
        JSONArray imageArray = json.optJSONArray("saved_images");
        for (int i = 0; imageArray != null && i < imageArray.length(); i++) {
            BrainstormDraft.Block image = block(imageArray.opt(i));
            if (image != null && !image.isNew())
                draft.getSavedImages().add(image);
        }
        draft.setAspectRatio(optText(json, "aspect_ratio"));
        draft.setTargetModel(optText(json, "target_model"));
        draft.setResolution(optText(json, "resolution"));
        draft.setModelReason(json.optString("model_reason", ""));

        JSONArray factArray = json.optJSONArray("facts");
        for (int i = 0; factArray != null && i < factArray.length(); i++) {
            JSONObject factJson = factArray.optJSONObject(i);
            BrainstormFact fact = factJson != null ? fact(factJson) : null;
            if (fact != null)
                draft.getFacts().add(fact);
        }

        JSONObject video = json.optJSONObject("video");
        if (video != null)
            draft.setDurationSeconds(video.has("duration_seconds") && !video.isNull("duration_seconds")
                    ? video.optInt("duration_seconds") : null);
        return draft;
    }

    /**
     * Returns the JSON form of a draft, readable again by {@link #draft(JSONObject)}.
     *
     * @param draft the draft
     * @return the JSON object
     */
    public static JSONObject toJson(BrainstormDraft draft) {
        JSONObject video = new JSONObject();
        video.put("duration_seconds", draft.getDurationSeconds() == null ? JSONObject.NULL
                : draft.getDurationSeconds());

        JSONObject json = new JSONObject();
        json.put("media_type", draft.getMediaType());
        json.put("prompt", draft.getPrompt());
        json.put("mood", draft.getMood() == null ? JSONObject.NULL : toJson(draft.getMood()));
        for (Mood.Field field : BrainstormDraft.FIELDS)
            json.put(key(field), draft.getBlock(field) != null ? toJson(draft.getBlock(field)) : JSONObject.NULL);
        json.put("style_reason", draft.getStyleReason());
        json.put("frame", draft.getFrame() == null ? JSONObject.NULL : toJson(draft.getFrame()));
        JSONArray imageArray = new JSONArray();
        draft.getSavedImages().forEach((BrainstormDraft.Block image) -> imageArray.put(toJson(image)));
        json.put("saved_images", imageArray);
        json.put("aspect_ratio", orNull(draft.getAspectRatio()));
        json.put("target_model", orNull(draft.getTargetModel()));
        json.put("resolution", orNull(draft.getResolution()));
        json.put("model_reason", draft.getModelReason());
        json.put("video", video);
        JSONArray factArray = new JSONArray();
        draft.getFacts().forEach((BrainstormFact fact) -> factArray.put(toJson(fact)));
        json.put("facts", factArray);
        return json;
    }

    /**
     * Reads a block: a plain name (older drafts, a mood) or an object with id, name, text, colors, settings and
     * is_new. Without is_new, a block with a text, colors or settings counts as new; settings that leave every value
     * "any" count as none.
     *
     * @param value the JSON value, a string or an object
     * @return the block, null for a missing value, a missing name or a new block without content
     */
    public static BrainstormDraft.Block block(Object value) {
        if (value instanceof String name)
            return isText(name) ? new BrainstormDraft.Block(null, name.trim(), null, List.of(), false) : null;
        if (!(value instanceof JSONObject json))
            return null;

        String name = optText(json, "name");
        String text = optText(json, "text");
        List<String> colors = new ArrayList<>();
        JSONArray colorArray = json.optJSONArray("colors");
        for (int i = 0; colorArray != null && i < colorArray.length() && colors.size() < MAX_COLORS; i++) {
            String color = colorArray.optString(i, "").trim().toUpperCase(Locale.ROOT);
            if (HEX_COLOR.matcher(color).matches())
                colors.add(color);
        }
        JSONObject settingsJson = json.optJSONObject("settings");
        CameraSettings settings = settingsJson != null ? CameraSettings.fromJson(settingsJson.toString()) : null;
        settings = CameraSettings.ANY.equals(settings) ? null : settings;
        boolean isNew = json.optBoolean("is_new", text != null || !colors.isEmpty() || settings != null);
        if (name == null || isNew && text == null && colors.isEmpty() && settings == null)
            return null;
        return isNew ? new BrainstormDraft.Block(null, name, text, List.copyOf(colors), settings, true)
                : new BrainstormDraft.Block(optText(json, "id"), name, null, List.of(), false);
    }

    /**
     * Returns the JSON form of a block, readable again by {@link #block(Object)}.
     *
     * @param block the block
     * @return the JSON object
     */
    public static JSONObject toJson(BrainstormDraft.Block block) {
        JSONObject json = new JSONObject().put("name", block.name()).put("is_new", block.isNew());
        if (block.id() != null)
            json.put("id", block.id());
        if (block.text() != null)
            json.put("text", block.text());
        if (!block.colors().isEmpty())
            json.put("colors", new JSONArray(block.colors()));
        if (block.settings() != null)
            json.put("settings", new JSONObject(block.settings().toJson()));
        return json;
    }

    /**
     * Reads a fact from its JSON form.
     *
     * @param json the fact JSON object
     * @return the fact, or {@code null} if it has no text
     */
    static BrainstormFact fact(JSONObject json) {
        String text = json.optString("text", "").trim();
        if (text.isEmpty())
            return null;
        BrainstormFact fact = new BrainstormFact(text, json.optString("domain", ""), json.optString("url", ""),
                json.optString("origin"), json.optBoolean("confirmed", true));
        fact.setVerified(json.optBoolean("verified", true));
        fact.setTime(json.optString("time", "").trim());
        return fact;
    }

    private static JSONObject toJson(BrainstormFact fact) {
        return new JSONObject()
                .put("text", fact.getText())
                .put("domain", fact.getDomain())
                .put("url", fact.getUrl())
                .put("origin", fact.isFromUser() ? BrainstormFact.ORIGIN_USER : BrainstormFact.ORIGIN_SOURCE)
                .put("confirmed", fact.isConfirmed())
                .put("verified", fact.isVerified())
                .put("time", fact.getTime());
    }

    /**
     * Returns the app event of a research result, stored with the session and sent to the AI in later turns.
     *
     * @param note            the research result
     * @param maxResultLength the result text is cut to this length
     * @return the event JSON
     */
    public static JSONObject researchEvent(ResearchNote note, int maxResultLength) {
        JSONArray sourceArray = new JSONArray();
        note.sources().forEach((ResearchNote.Source source) -> sourceArray.put(
                new JSONObject().put("domain", source.domain()).put("url", source.url())));
        String result = note.result();
        return new JSONObject()
                .put("event", RESEARCH_EVENT)
                .put("query", note.query())
                .put("result", result.length() > maxResultLength ? result.substring(0, maxResultLength) + "..." : result)
                .put("sources", sourceArray);
    }

    /**
     * Reads a research result from its app event.
     *
     * @param event the event JSON written by {@link #researchEvent}
     * @return the research result
     */
    public static ResearchNote researchNote(JSONObject event) {
        List<ResearchNote.Source> sources = new ArrayList<>();
        JSONArray array = event.optJSONArray("sources");
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject source = array.optJSONObject(i);
            if (source != null)
                sources.add(new ResearchNote.Source(source.optString("domain"), source.optString("url")));
        }
        return new ResearchNote(event.optString("query"), event.optString("result"), sources);
    }

    /*  The text of a string key, or null for a missing, null or blank value  */
    private static String optText(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key))
            return null;
        String value = json.optString(key, "").trim();
        return isText(value) ? value : null;
    }

    /*  Whether a value is a real text, not blank and not the word "null"  */
    private static boolean isText(String value) {
        return !value.isBlank() && !"null".equalsIgnoreCase(value.trim());
    }

    private static Object orNull(String value) {
        return value == null ? JSONObject.NULL : value;
    }

    /*  The JSON key of the block of a field, for example "atmosphere"  */
    private static String key(Mood.Field field) {
        return field.name().toLowerCase(Locale.ROOT);
    }
}
