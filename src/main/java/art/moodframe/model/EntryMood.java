package art.moodframe.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The mood and the building blocks a chat entry was generated with, copied at the time of generation, so the entry
 * stays traceable when a block is changed or deleted later.
 *
 * <p>Stored as JSON in the column {@code mood_json} of the table {@code chat_entries}, the mood id also in the indexed
 * column {@code mood_id}. A combination of its own (no mood chosen, or the chosen one changed) carries no mood; a
 * changed one keeps the mood it started from as base, with the fields that differ from it.</p>
 *
 * @param moodId the id of the chosen mood, null for a combination of its own
 * @param moodName the name of the chosen mood at the time of generation, null for a combination of its own
 * @param blocks the copies of the blocks per field; a missing field was empty
 * @param baseId the id of the mood a changed combination started from, null if unchanged or unknown
 * @param baseName the name of that mood at the time of generation, null if unchanged or unknown
 * @param changed the fields whose block differs from the base mood, empty without base
 * @since 2.0.0
 */
public record EntryMood(String moodId, String moodName, Map<Mood.Field, Block> blocks, String baseId, String baseName,
        Set<Mood.Field> changed) {

    /**
     * Creates the copy of a chosen mood or of a combination without base.
     *
     * @param moodId the id of the chosen mood, null for a combination of its own
     * @param moodName the name of the chosen mood, null for a combination of its own
     * @param blocks the copies of the blocks per field
     */
    public EntryMood(String moodId, String moodName, Map<Mood.Field, Block> blocks) {
        this(moodId, moodName, blocks, null, null, Set.of());
    }

    /**
     * The copy of a building block.
     *
     * @param id the id of the block
     * @param name the name of the block
     * @param text the prompt fragment of a text block, null for a palette
     * @param group the group of a text block, null for none
     * @param photographic whether a style describes a photograph
     * @param colors the hex colors of a palette, empty for a text block
     * @param params the parameters of the block as JSON text (the settings of a camera), null for none
     */
    public record Block(String id, String name, String text, String group, boolean photographic, List<String> colors,
            String params) {
    }

    /**
     * The copy as JSON, for example
     * {@code {"mood": {"id": "std:mood:film-noir", "name": "Film Noir"}, "blocks": {"style": {"id": ..., "name": ...,
     * "text": ..., "photographic": true}, "color": {"id": ..., "name": ..., "colors": ["#1A1A1A"]},
     * "camera": {"id": ..., "name": ..., "text": ..., "params": ...}}}}; a changed combination carries
     * {@code "base": {"id": ..., "name": ..., "changed": ["light"]}} instead of {@code "mood"}.
     *
     * @return the JSON text
     */
    public String toJson() {
        JSONObject json = new JSONObject();
        if (moodId != null)
            json.put("mood", new JSONObject().put("id", moodId).put("name", moodName));
        if (baseId != null)
            json.put("base", new JSONObject().put("id", baseId).put("name", baseName).put("changed",
                    new JSONArray(changed.stream().map((Mood.Field field) -> field.name().toLowerCase(Locale.ROOT))
                            .toList())));
        JSONObject fields = new JSONObject();
        blocks.forEach((Mood.Field field, Block block) -> {
            JSONObject copy = new JSONObject().put("id", block.id()).put("name", block.name());
            if (block.text() != null)
                copy.put("text", block.text()).put("group", block.group()).put("photographic", block.photographic());
            else
                copy.put("colors", new JSONArray(block.colors()));
            copy.putOpt("params", block.params());
            fields.put(field.name().toLowerCase(Locale.ROOT), copy);
        });
        return json.put("blocks", fields).toString();
    }

    /**
     * Reads a copy from JSON (see {@link #toJson()}).
     *
     * @param json the JSON text, null for an entry from before 2.0 or without blocks; a copy from before the base was
     *        kept reads as a combination without base
     * @return the copy, null for none or for unreadable JSON
     */
    public static EntryMood fromJson(String json) {
        if (json == null || json.isBlank())
            return null;
        try {
            JSONObject root = new JSONObject(json);
            JSONObject mood = root.optJSONObject("mood");
            JSONObject base = root.optJSONObject("base");
            JSONObject fields = root.optJSONObject("blocks");
            Map<Mood.Field, Block> blocks = new EnumMap<>(Mood.Field.class);
            for (Mood.Field field : Mood.Field.values()) {
                JSONObject copy = fields != null ? fields.optJSONObject(field.name().toLowerCase(Locale.ROOT)) : null;
                if (copy == null)
                    continue;
                JSONArray colors = copy.optJSONArray("colors");
                blocks.put(field, new Block(copy.optString("id", null), copy.optString("name", null),
                        copy.optString("text", null), copy.optString("group", null), copy.optBoolean("photographic"),
                        colors != null ? colors.toList().stream().map(String::valueOf).toList() : List.of(),
                        copy.optString("params", null)));
            }
            JSONArray changedFields = base != null ? base.optJSONArray("changed") : null;
            List<Object> changedNames = changedFields != null ? changedFields.toList() : List.of();
            Set<Mood.Field> changed = EnumSet.noneOf(Mood.Field.class);
            for (Mood.Field field : Mood.Field.values()) {
                if (changedNames.contains(field.name().toLowerCase(Locale.ROOT)))
                    changed.add(field);
            }
            return new EntryMood(mood != null ? mood.optString("id", null) : null,
                    mood != null ? mood.optString("name", null) : null, blocks,
                    base != null ? base.optString("id", null) : null,
                    base != null ? base.optString("name", null) : null, changed);
        } catch (JSONException e) {
            return null;
        }
    }
}
