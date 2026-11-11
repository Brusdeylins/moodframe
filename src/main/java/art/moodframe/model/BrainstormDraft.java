package art.moodframe.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The current result of a brainstorm session: the prompt and the suggested settings.
 *
 * <p>All settings are optional ({@code null} or empty means "no suggestion"). The look comes from a mood and the
 * building blocks of {@link #FIELDS}: each is an entry of the lists or a new block the AI proposes, a new camera with
 * its settings instead of a text. Older drafts name only the style and the palette ({@code "style"},
 * {@code "color_style"}); they are read as references by name.</p>
 *
 * @since 1.7.0
 */
public class BrainstormDraft {

    /** The building blocks the brainstorm suggests, each under its lower-case name as key. */
    public static final List<Mood.Field> FIELDS = List.of(Mood.Field.STYLE, Mood.Field.ATMOSPHERE, Mood.Field.LIGHT,
            Mood.Field.COLOR, Mood.Field.CAMERA);

    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9A-F]{6}");
    private static final int MAX_COLORS = 5;

    /**
     * A building block of the draft: an entry of the lists, referred to by id or name, or a new block the AI proposes
     * with its text, for a palette with its colors, for a camera with its settings.
     *
     * @param id the id of the entry, null for a new block or a name that is not resolved yet
     * @param name the name of the entry, or the proposed name of a new block
     * @param text the prompt fragment of a new text block, null otherwise
     * @param colors the colors of a new palette as {@code #RRGGBB}, empty otherwise
     * @param settings the settings of a new camera, null otherwise
     * @param isNew whether the AI proposes the block as a new entry
     */
    public record Block(String id, String name, String text, List<String> colors, CameraSettings settings,
            boolean isNew) {

        /** Creates a block without camera settings. */
        public Block(String id, String name, String text, List<String> colors, boolean isNew) {
            this(id, name, text, colors, null, isNew);
        }

        /**
         * Reads a block: a plain name (older drafts, a mood) or an object with id, name, text, colors, settings and
         * is_new. Without is_new, a block with a text, colors or settings counts as new; settings that leave every
         * value "any" count as none.
         *
         * @param value the JSON value, a string or an object
         * @return the block, null for a missing value, a missing name or a new block without content
         */
        public static Block fromJson(Object value) {
            if (value instanceof String name)
                return isText(name) ? new Block(null, name.trim(), null, List.of(), false) : null;
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
            return isNew ? new Block(null, name, text, List.copyOf(colors), settings, true)
                    : new Block(optText(json, "id"), name, null, List.of(), false);
        }

        /**
         * Returns the JSON form of this block, readable again by {@link #fromJson(Object)}.
         *
         * @return the JSON object
         */
        public JSONObject toJson() {
            JSONObject json = new JSONObject().put("name", name).put("is_new", isNew);
            if (id != null)
                json.put("id", id);
            if (text != null)
                json.put("text", text);
            if (!colors.isEmpty())
                json.put("colors", new JSONArray(colors));
            if (settings != null)
                json.put("settings", new JSONObject(settings.toJson()));
            return json;
        }

        /**
         * The same block, referring to its entry by id.
         *
         * @param entryId the id of the entry
         * @return the block with the id
         */
        public Block withId(String entryId) {
            return new Block(entryId, name, text, colors, settings, isNew);
        }
    }

    private String mediaType = "image";
    private String prompt = "";
    private Block mood;
    private final Map<Mood.Field, Block> blocks = new EnumMap<>(Mood.Field.class);
    private String styleReason = "";
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
        draft.mood = Block.fromJson(json.opt("mood"));
        for (Mood.Field field : FIELDS)
            draft.setBlock(field, Block.fromJson(json.opt(key(field))));

        /*  older drafts name the palette as "color_style"  */
        if (!json.has(key(Mood.Field.COLOR)))
            draft.setBlock(Mood.Field.COLOR, Block.fromJson(json.opt("color_style")));
        draft.styleReason = json.optString("style_reason", "");
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
        json.put("mood", mood == null ? JSONObject.NULL : mood.toJson());
        for (Mood.Field field : FIELDS)
            json.put(key(field), blocks.containsKey(field) ? blocks.get(field).toJson() : JSONObject.NULL);
        json.put("style_reason", styleReason);
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
        return isText(value) ? value : null;
    }

    /** Whether a value is a real text, not blank and not the word "null". */
    private static boolean isText(String value) {
        return !value.isBlank() && !"null".equalsIgnoreCase(value.trim());
    }

    /** The JSON key of the block of a field, for example {@code "atmosphere"}. */
    private static String key(Mood.Field field) {
        return field.name().toLowerCase(Locale.ROOT);
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

    /** The mood the AI chose (an entry of the list), null for none. */
    public Block getMood() {
        return mood;
    }

    public void setMood(Block mood) {
        this.mood = mood;
    }

    /**
     * The building block of a field.
     *
     * @param field one of {@link #FIELDS}
     * @return the block, null if the draft suggests none
     */
    public Block getBlock(Mood.Field field) {
        return blocks.get(field);
    }

    /**
     * Sets the building block of a field.
     *
     * @param field one of {@link #FIELDS}
     * @param block the block, null for none
     */
    public void setBlock(Mood.Field field, Block block) {
        if (block == null)
            blocks.remove(field);
        else
            blocks.put(field, block);
    }

    /** The reason for the look (mood and building blocks), one sentence or empty. */
    public String getStyleReason() {
        return styleReason;
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
