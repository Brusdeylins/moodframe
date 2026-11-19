package art.moodframe.repository;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.EntryFrame;
import art.moodframe.model.EntryMood;
import art.moodframe.model.FrameGroup;
import art.moodframe.model.Mood;
import art.moodframe.model.MoodGroup;
import art.moodframe.model.PromptBlock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read models of the chats for the sidebar: the entries of the active chats grouped by the mood or the frame stored
 * at them, and the entries of such a group. It only reads.
 *
 * @since 2.1.3
 */
public class ChatGroupQueries {

    private final DatabaseManager databaseManager;

    /**
     * Creates the queries.
     *
     * @param databaseManager the database of the chats
     */
    public ChatGroupQueries(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Groups the entries of the active chats by the mood stored at them: one group per mood with at least one image,
     * the latest use first, followed by the combinations of their own and the entries without blocks, which are always
     * listed. Only images that were generated and downloaded count; chats, entries and images in the trash do not.
     *
     * @return the groups
     * @throws SQLException if query fails
     */
    public List<MoodGroup> findMoodGroups() throws SQLException {
        String sql = """
                SELECT ce.mood_id, ce.mood_json, ce.created_at, m.name AS mood_name,
                       m.id IS NULL OR m.deleted_at IS NOT NULL AS mood_deleted,
                       (SELECT COUNT(*) FROM generated_images gi
                        WHERE gi.chat_entry_id = ce.id AND gi.deleted_at IS NULL
                        AND gi.download_status = 'CACHED') AS image_count
                FROM chat_entries ce
                JOIN chats c ON c.id = ce.chat_id
                LEFT JOIN moods m ON m.id = ce.mood_id
                WHERE ce.deleted_at IS NULL AND c.deleted_at IS NULL
                ORDER BY ce.created_at DESC
                """;

        /*  The entries come latest first: the first one of a group names it and gives its palette  */
        Map<String, MoodGroup> groups = new LinkedHashMap<>();
        Map<String, Integer> imageCounts = new HashMap<>();

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String moodId = rs.getString("mood_id");
                String json = rs.getString("mood_json");
                MoodGroup.Kind kind = moodId != null ? MoodGroup.Kind.MOOD
                        : json != null ? MoodGroup.Kind.CUSTOM : MoodGroup.Kind.NONE;
                String key = kind + ":" + moodId;
                imageCounts.merge(key, rs.getInt("image_count"), Integer::sum);
                if (groups.containsKey(key))
                    continue;

                /*  A standard mood is never stored, so only a mood of the user can be deleted  */
                EntryMood mood = EntryMood.fromJson(json);
                EntryMood.Block palette = mood != null ? mood.blocks().get(Mood.Field.COLOR) : null;
                boolean deleted = kind == MoodGroup.Kind.MOOD && !moodId.startsWith(PromptBlock.STANDARD_PREFIX)
                        && rs.getBoolean("mood_deleted");
                String current = rs.getString("mood_name");
                String name = kind != MoodGroup.Kind.MOOD ? null
                        : !deleted && current != null ? current : mood != null ? mood.moodName() : null;
                groups.put(key, new MoodGroup(kind, moodId, name, deleted,
                        kind == MoodGroup.Kind.MOOD && palette != null ? palette.colors() : List.of(), 0,
                        rs.getTimestamp("created_at").toLocalDateTime()));
            }
        }

        /*  A mood is listed once an image of it exists, the two fixed groups always  */
        List<MoodGroup> result = new ArrayList<>();
        for (MoodGroup.Kind kind : MoodGroup.Kind.values()) {
            groups.forEach((String key, MoodGroup group) -> {
                int count = imageCounts.get(key);
                if (group.kind() == kind && (kind != MoodGroup.Kind.MOOD || count > 0))
                    result.add(new MoodGroup(kind, group.moodId(), group.name(), group.deleted(), group.colors(),
                            count, group.lastUsed()));
            });
            if (kind != MoodGroup.Kind.MOOD && !groups.containsKey(kind + ":null"))
                result.add(new MoodGroup(kind, null, null, false, List.of(), 0, null));
        }
        return result;
    }

    /**
     * Lists the entries of a group (see {@link #findMoodGroups()}) in the active chats, in the order of their creation.
     * Entries in the trash do not count.
     *
     * @param group the group
     * @return the ids of the entries
     * @throws SQLException if query fails
     */
    public List<String> findChatEntryIdsByMood(MoodGroup group) throws SQLException {
        return findChatEntryIds(switch (group.kind()) {
            case MOOD -> "ce.mood_id = ?";
            case CUSTOM -> "ce.mood_id IS NULL AND ce.mood_json IS NOT NULL";
            case NONE -> "ce.mood_json IS NULL";
        }, group.moodId());
    }

    /**
     * Groups the entries of the active chats by the frame stored at them: one group per frame with at least one image,
     * the latest use first, followed by the settings of their own and the entries without copy, which are always
     * listed. Only images that were generated and downloaded count; chats, entries and images in the trash do not.
     *
     * @return the groups
     * @throws SQLException if query fails
     */
    public List<FrameGroup> findFrameGroups() throws SQLException {
        String sql = """
                SELECT ce.frame_id, ce.frame_json, ce.created_at, f.name AS frame_name,
                       f.id IS NULL OR f.deleted_at IS NOT NULL AS frame_deleted,
                       (SELECT COUNT(*) FROM generated_images gi
                        WHERE gi.chat_entry_id = ce.id AND gi.deleted_at IS NULL
                        AND gi.download_status = 'CACHED') AS image_count
                FROM chat_entries ce
                JOIN chats c ON c.id = ce.chat_id
                LEFT JOIN frames f ON f.id = ce.frame_id
                WHERE ce.deleted_at IS NULL AND c.deleted_at IS NULL
                ORDER BY ce.created_at DESC
                """;

        /*  The entries come latest first: the first one of a group names it and gives its target  */
        Map<String, FrameGroup> groups = new LinkedHashMap<>();
        Map<String, Integer> imageCounts = new HashMap<>();

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String frameId = rs.getString("frame_id");
                String json = rs.getString("frame_json");
                FrameGroup.Kind kind = frameId != null ? FrameGroup.Kind.FRAME
                        : json != null ? FrameGroup.Kind.CUSTOM : FrameGroup.Kind.NONE;
                String key = kind + ":" + frameId;
                imageCounts.merge(key, rs.getInt("image_count"), Integer::sum);
                if (groups.containsKey(key))
                    continue;

                /*  A standard frame is never stored, so only a frame of the user can be deleted  */
                EntryFrame frame = kind == FrameGroup.Kind.FRAME ? EntryFrame.fromJson(json) : null;
                boolean deleted = kind == FrameGroup.Kind.FRAME && !frameId.startsWith(PromptBlock.STANDARD_PREFIX)
                        && rs.getBoolean("frame_deleted");
                String current = rs.getString("frame_name");
                String name = kind != FrameGroup.Kind.FRAME ? null
                        : !deleted && current != null ? current : frame != null ? frame.frameName() : null;
                groups.put(key, new FrameGroup(kind, frameId, name, deleted, frame != null ? frame.aspectRatio() : null,
                        frame != null ? frame.resolution() : null, 0, rs.getTimestamp("created_at").toLocalDateTime()));
            }
        }

        /*  A frame is listed once an image of it exists, the two fixed groups always  */
        List<FrameGroup> result = new ArrayList<>();
        for (FrameGroup.Kind kind : FrameGroup.Kind.values()) {
            groups.forEach((String key, FrameGroup group) -> {
                int count = imageCounts.get(key);
                if (group.kind() == kind && (kind != FrameGroup.Kind.FRAME || count > 0))
                    result.add(new FrameGroup(kind, group.frameId(), group.name(), group.deleted(),
                            group.aspectRatio(), group.resolution(), count, group.lastUsed()));
            });
            if (kind != FrameGroup.Kind.FRAME && !groups.containsKey(kind + ":null"))
                result.add(new FrameGroup(kind, null, null, false, null, null, 0, null));
        }
        return result;
    }

    /**
     * Lists the entries of a group (see {@link #findFrameGroups()}) in the active chats, in the order of their
     * creation. Entries in the trash do not count.
     *
     * @param group the group
     * @return the ids of the entries
     * @throws SQLException if query fails
     */
    public List<String> findChatEntryIdsByFrame(FrameGroup group) throws SQLException {
        return findChatEntryIds(switch (group.kind()) {
            case FRAME -> "ce.frame_id = ?";
            case CUSTOM -> "ce.frame_id IS NULL AND ce.frame_json IS NOT NULL";
            case NONE -> "ce.frame_json IS NULL";
        }, group.frameId());
    }

    /**
     * Lists the entries of the active chats that meet a condition, in the order of their creation.
     *
     * @param condition the condition on {@code ce}, the entries, with at most one parameter
     * @param id the value of the parameter, null for a condition without one
     * @return the ids of the entries
     * @throws SQLException if query fails
     */
    private List<String> findChatEntryIds(String condition, String id) throws SQLException {
        String sql = "SELECT ce.id FROM chat_entries ce JOIN chats c ON c.id = ce.chat_id "
                + "WHERE ce.deleted_at IS NULL AND c.deleted_at IS NULL AND " + condition + " ORDER BY ce.created_at";

        List<String> ids = new ArrayList<>();

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (id != null)
                stmt.setString(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next())
                    ids.add(rs.getString("id"));
            }
        }

        return ids;
    }
}
