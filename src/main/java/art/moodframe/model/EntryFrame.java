package art.moodframe.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The frame and the target aspect ratio and resolution a chat entry was generated with, copied at the time of
 * generation, so the entry stays traceable when the frame is changed or deleted later.
 *
 * <p>Stored as JSON in the column {@code frame_json} of the table {@code chat_entries}, the frame id also in the
 * indexed column {@code frame_id}. A setting of its own (no frame chosen, or the chosen one changed) carries no
 * frame.</p>
 *
 * @param frameId the id of the chosen frame, null for a setting of its own
 * @param frameName the name of the chosen frame at the time of generation, null for a setting of its own
 * @param aspectRatio the target aspect ratio, for example {@code 16:9}, null for none
 * @param resolution the target resolution, for example {@code 2K}, null for none
 * @since 2.0.0
 */
public record EntryFrame(String frameId, String frameName, String aspectRatio, String resolution) {

    /**
     * Copies the frame and the target of the main window.
     *
     * @param frame the chosen frame, null for "No frame" or a changed one
     * @param aspectRatio the target aspect ratio
     * @param resolution the target resolution
     * @return the copy
     */
    public static EntryFrame of(Frame frame, String aspectRatio, String resolution) {
        return new EntryFrame(frame != null ? frame.getId() : null, frame != null ? frame.getName() : null,
                aspectRatio, resolution);
    }

    /**
     * The copy as JSON, for example
     * {@code {"frame": {"id": "std:frame:social-post", "name": "Social Post"}, "aspectRatio": "4:5",
     * "resolution": "2K"}}.
     *
     * @return the JSON text
     */
    public String toJson() {
        JSONObject json = new JSONObject();
        if (frameId != null)
            json.put("frame", new JSONObject().put("id", frameId).put("name", frameName));
        return json.putOpt("aspectRatio", aspectRatio).putOpt("resolution", resolution).toString();
    }

    /**
     * Reads a copy from JSON (see {@link #toJson()}).
     *
     * @param json the JSON text, null for an entry from before the frames were copied
     * @return the copy, null for none or for unreadable JSON
     */
    public static EntryFrame fromJson(String json) {
        if (json == null || json.isBlank())
            return null;
        try {
            JSONObject root = new JSONObject(json);
            JSONObject frame = root.optJSONObject("frame");
            return new EntryFrame(frame != null ? frame.optString("id", null) : null,
                    frame != null ? frame.optString("name", null) : null, root.optString("aspectRatio", null),
                    root.optString("resolution", null));
        } catch (JSONException e) {
            return null;
        }
    }
}
