package art.moodframe.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The frame and the target aspect ratio and resolution a chat entry was generated with, copied at the time of
 * generation, so the entry stays traceable when the frame is changed or deleted later.
 *
 * <p>Stored as JSON in the column {@code frame_json} of the table {@code chat_entries}, the frame id also in the
 * indexed column {@code frame_id}. A setting of its own (no frame chosen, or the chosen one changed) carries no
 * frame; a changed one keeps the frame it started from as base.</p>
 *
 * @param frameId the id of the chosen frame, null for a setting of its own
 * @param frameName the name of the chosen frame at the time of generation, null for a setting of its own
 * @param aspectRatio the target aspect ratio, for example {@code 16:9}, null for none
 * @param resolution the target resolution, for example {@code 2K}, null for none
 * @param baseId the id of the frame a changed setting started from, null if unchanged or unknown
 * @param baseName the name of that frame at the time of generation, null if unchanged or unknown
 * @since 2.0.0
 */
public record EntryFrame(String frameId, String frameName, String aspectRatio, String resolution, String baseId,
        String baseName) {

    /**
     * Creates the copy of a chosen frame or of a setting without base.
     *
     * @param frameId the id of the chosen frame, null for a setting of its own
     * @param frameName the name of the chosen frame, null for a setting of its own
     * @param aspectRatio the target aspect ratio
     * @param resolution the target resolution
     */
    public EntryFrame(String frameId, String frameName, String aspectRatio, String resolution) {
        this(frameId, frameName, aspectRatio, resolution, null, null);
    }

    /**
     * Copies the frame and the target of the main window.
     *
     * @param frame the chosen frame, null for "No frame"
     * @param changed whether the target differs from the chosen frame, which then becomes the base
     * @param aspectRatio the target aspect ratio
     * @param resolution the target resolution
     * @return the copy
     */
    public static EntryFrame of(Frame frame, boolean changed, String aspectRatio, String resolution) {
        Frame chosen = changed ? null : frame;
        Frame base = changed ? frame : null;
        return new EntryFrame(chosen != null ? chosen.getId() : null, chosen != null ? chosen.getName() : null,
                aspectRatio, resolution, base != null ? base.getId() : null, base != null ? base.getName() : null);
    }

    /**
     * The copy as JSON, for example
     * {@code {"frame": {"id": "std:frame:social-post", "name": "Social Post"}, "aspectRatio": "4:5",
     * "resolution": "2K"}}; a changed setting carries {@code "base": {"id": ..., "name": ...}} instead of
     * {@code "frame"}.
     *
     * @return the JSON text
     */
    public String toJson() {
        JSONObject json = new JSONObject();
        if (frameId != null)
            json.put("frame", new JSONObject().put("id", frameId).put("name", frameName));
        if (baseId != null)
            json.put("base", new JSONObject().put("id", baseId).put("name", baseName));
        return json.putOpt("aspectRatio", aspectRatio).putOpt("resolution", resolution).toString();
    }

    /**
     * Reads a copy from JSON (see {@link #toJson()}).
     *
     * @param json the JSON text, null for an entry from before the frames were copied; a copy from before the base
     *        was kept reads as a setting without base
     * @return the copy, null for none or for unreadable JSON
     */
    public static EntryFrame fromJson(String json) {
        if (json == null || json.isBlank())
            return null;
        try {
            JSONObject root = new JSONObject(json);
            JSONObject frame = root.optJSONObject("frame");
            JSONObject base = root.optJSONObject("base");
            return new EntryFrame(frame != null ? frame.optString("id", null) : null,
                    frame != null ? frame.optString("name", null) : null, root.optString("aspectRatio", null),
                    root.optString("resolution", null), base != null ? base.optString("id", null) : null,
                    base != null ? base.optString("name", null) : null);
        } catch (JSONException e) {
            return null;
        }
    }
}
