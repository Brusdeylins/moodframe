package art.moodframe.repository;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatCost;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryFrame;
import art.moodframe.model.EntryMood;
import art.moodframe.model.FrameGroup;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.Mood;
import art.moodframe.model.MoodGroup;
import art.moodframe.model.PromptBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for Chat entities providing database operations.
 * 
 * <p>
 * This repository handles all database operations for chats, chat entries,
 * and generated images using the repository pattern.
 * </p>
 * 
 * @since 1.0.0
 */
public class ChatRepository {

    private static final Logger logger = LoggerFactory.getLogger(ChatRepository.class);

    private final DatabaseManager databaseManager;
    private final ChatFiles chatFiles;

    /**
     * Creates a new chat repository.
     * 
     * @param databaseManager the database manager
     */
    public ChatRepository(DatabaseManager databaseManager) {
        this(databaseManager, ChatFiles.NONE);
    }

    /**
     * Creates a new chat repository that hands the files of deleted chats to the given handler.
     * 
     * @param databaseManager the database manager
     * @param chatFiles       moves, restores and removes the files of a chat
     */
    public ChatRepository(DatabaseManager databaseManager, ChatFiles chatFiles) {
        this.databaseManager = databaseManager;
        this.chatFiles = chatFiles;
    }

    // ========== Chat Operations ==========

    /**
     * Saves a chat to the database (insert or update).
     * 
     * @param chat the chat to save
     * @throws SQLException if database operation fails
     */
    public void saveChat(Chat chat) throws SQLException {
        databaseManager.inTransaction((Connection conn) -> {
            if (exists(conn, "chats", chat.getId()))
                updateChat(conn, chat);
            else
                insertChat(conn, chat);
            return null;
        });
    }

    /*  whether a row of the table has the id; the table is a constant of this class  */
    private static boolean exists(Connection conn, String table, String id) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM " + table + " WHERE id = ?")) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /*  the time of a deletion, now, in the format of all time columns (see package-info)  */
    private static Timestamp now() {
        return Timestamp.valueOf(LocalDateTime.now());
    }

    /**
     * Inserts a new chat into the database.
     *
     * @param conn the connection of the transaction
     * @param chat the chat to insert
     * @throws SQLException if insert fails
     */
    private static void insertChat(Connection conn, Chat chat) throws SQLException {
        String sql = """
                INSERT INTO chats (id, name, created_at, updated_at, deleted_at, sort_order)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, chat.getId());
            stmt.setString(2, chat.getName());
            stmt.setTimestamp(3, Timestamp.valueOf(chat.getCreatedAt()));
            stmt.setTimestamp(4, Timestamp.valueOf(chat.getUpdatedAt()));
            stmt.setTimestamp(5, chat.getDeletedAt() != null ? Timestamp.valueOf(chat.getDeletedAt()) : null);
            stmt.setInt(6, chat.getSortOrder());

            stmt.executeUpdate();
        }
    }

    /**
     * Updates an existing chat in the database. It leaves deleted_at alone: a chat goes to the trash and comes back
     * only by {@link #softDeleteChat} and {@link #restoreChat}, so a stale chat in memory undoes neither.
     *
     * @param conn the connection of the transaction
     * @param chat the chat to update
     * @throws SQLException if update fails
     */
    private static void updateChat(Connection conn, Chat chat) throws SQLException {
        String sql = """
                UPDATE chats SET name = ?, updated_at = ?, sort_order = ?
                WHERE id = ?
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, chat.getName());
            stmt.setTimestamp(2, Timestamp.valueOf(chat.getUpdatedAt()));
            stmt.setInt(3, chat.getSortOrder());
            stmt.setString(4, chat.getId());

            stmt.executeUpdate();
        }
    }

    /**
     * Stores the place of each chat in the list in one transaction, leaving its other columns alone; a chat that is
     * not stored yet is skipped.
     *
     * @param chats the chats with their sort order
     * @throws SQLException if the update fails
     */
    public void updateChatSortOrders(List<Chat> chats) throws SQLException {
        String sql = "UPDATE chats SET sort_order = ? WHERE id = ?";

        databaseManager.inTransaction((Connection conn) -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Chat chat : chats) {
                    stmt.setInt(1, chat.getSortOrder());
                    stmt.setString(2, chat.getId());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
            return null;
        });
    }

    /**
     * Finds a chat by ID.
     * 
     * @param chatId the chat ID
     * @return optional containing the chat if found
     * @throws SQLException if query fails
     */
    public Optional<Chat> findChatById(String chatId) throws SQLException {
        String sql = "SELECT * FROM chats WHERE id = ?";

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, chatId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToChat(rs));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Finds all chats that are not deleted.
     * 
     * @return list of active chats
     * @throws SQLException if query fails
     */
    public List<Chat> findAllActiveChats() throws SQLException {
        String sql = "SELECT * FROM chats WHERE deleted_at IS NULL ORDER BY sort_order, updated_at DESC";

        List<Chat> chats = new ArrayList<>();

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                chats.add(mapRowToChat(rs));
            }
        }

        return chats;
    }

    /**
     * Finds all deleted chats (in trash).
     * 
     * @return list of deleted chats
     * @throws SQLException if query fails
     */
    public List<Chat> findDeletedChats() throws SQLException {
        String sql = "SELECT * FROM chats WHERE deleted_at IS NOT NULL";

        List<Chat> chats = new ArrayList<>();

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                chats.add(mapRowToChat(rs));
            }
        }

        /*  sorted here: older rows hold deleted_at in other formats (see readDeletedAt), which SQL sorts apart  */
        chats.sort(Comparator.comparing(Chat::getDeletedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return chats;
    }

    /**
     * Soft deletes a chat by setting the deleted_at timestamp. After the commit its files move to the trash; a file
     * that does not move is logged, the chat is in the trash anyway.
     *
     * @param chatId the chat ID to soft delete
     * @return true if chat was marked as deleted
     * @throws SQLException if database operation fails
     */
    public boolean softDeleteChat(String chatId) throws SQLException {
        List<GeneratedImage> images = databaseManager.inTransaction((Connection conn) -> {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE chats SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL")) {
                stmt.setTimestamp(1, now());
                stmt.setString(2, chatId);
                if (stmt.executeUpdate() == 0)
                    return null;
            }
            return findAllGeneratedImagesForChat(conn, chatId);
        });
        if (images == null)
            return false;

        try {
            chatFiles.moveToTrash(chatId, images);
        } catch (RuntimeException e) {
            logger.error("Chat {} is in the trash, but its {} images could not be moved to the trash directory",
                    chatId, images.size(), e);
        }
        logger.debug("Soft deleted chat: {} (moved to trash)", chatId);
        return true;
    }

    /**
     * Restores a soft-deleted chat by clearing the deleted_at timestamp. After the commit its files come back from
     * the trash; a file that does not come back is logged, the chat is in use anyway.
     *
     * @param chatId the chat ID to restore
     * @return true if chat was restored
     * @throws SQLException if database operation fails
     */
    public boolean restoreChat(String chatId) throws SQLException {
        List<GeneratedImage> images = databaseManager.inTransaction((Connection conn) -> {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE chats SET deleted_at = NULL WHERE id = ? AND deleted_at IS NOT NULL")) {
                stmt.setString(1, chatId);
                if (stmt.executeUpdate() == 0)
                    return null;
            }
            /*  only the images not deleted on their own  */
            return findAllNonDeletedImagesForChat(conn, chatId);
        });
        if (images == null)
            return false;

        try {
            chatFiles.restoreFromTrash(chatId, images);
        } catch (RuntimeException e) {
            logger.error("Chat {} is restored, but its {} images could not be moved back from the trash directory",
                    chatId, images.size(), e);
        }
        logger.debug("Restored chat: {} (files restored from trash)", chatId);
        return true;
    }

    /**
     * Permanently deletes a chat and all its associated data, when it expires from the trash. The rows go first, in
     * one transaction (cascading to entries and images); the files follow after the commit, as a file left over is
     * harmless and a row without its file is not.
     *
     * @param chatId the chat ID to delete
     * @return true if chat was deleted
     * @throws SQLException if database operation fails
     */
    public boolean deleteChatPermanently(String chatId) throws SQLException {
        logger.debug("Starting permanent deletion of chat: {}", chatId);

        List<GeneratedImage> images = databaseManager.inTransaction((Connection conn) -> {
            List<GeneratedImage> all = findAllGeneratedImagesForChat(conn, chatId);
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM chats WHERE id = ?")) {
                stmt.setString(1, chatId);
                return stmt.executeUpdate() > 0 ? all : null;
            }
        });
        if (images == null) {
            logger.warn("No chat found to delete with ID: {}", chatId);
            return false;
        }
        logger.debug("Permanently deleted chat from database: {}", chatId);

        try {
            if (!chatFiles.deletePermanently(chatId, images))
                logger.warn("Chat {} is deleted from the database, but some of its {} images could not be removed "
                        + "from disk", chatId, images.size());
        } catch (RuntimeException e) {
            logger.error("Chat {} is deleted from the database, but its {} images could not be removed from disk",
                    chatId, images.size(), e);
        }
        return true;
    }

    /**
     * Finds all generated images for a specific chat (across all entries).
     *
     * @param conn   the connection
     * @param chatId the chat ID
     * @return list of all generated images for the chat
     * @throws SQLException if query fails
     */
    private static List<GeneratedImage> findAllGeneratedImagesForChat(Connection conn, String chatId)
            throws SQLException {
        String sql = """
                SELECT gi.* FROM generated_images gi
                JOIN chat_entries ce ON gi.chat_entry_id = ce.id
                WHERE ce.chat_id = ?
                ORDER BY gi.created_at
                """;

        List<GeneratedImage> images = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, chatId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    images.add(mapRowToGeneratedImage(rs));
                }
            }
        }

        return images;
    }
    
    /**
     * Finds only non-deleted generated images for a specific chat.
     *
     * @param conn   the connection
     * @param chatId the chat ID
     * @return list of non-deleted generated images for the chat
     * @throws SQLException if query fails
     */
    private static List<GeneratedImage> findAllNonDeletedImagesForChat(Connection conn, String chatId)
            throws SQLException {
        String sql = """
                SELECT gi.* FROM generated_images gi
                JOIN chat_entries ce ON gi.chat_entry_id = ce.id
                WHERE ce.chat_id = ? AND gi.deleted_at IS NULL
                ORDER BY gi.created_at
                """;

        List<GeneratedImage> images = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, chatId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    images.add(mapRowToGeneratedImage(rs));
                }
            }
        }

        return images;
    }

    // ========== Chat Entry Operations ==========

    /**
     * Saves a chat entry to the database.
     * 
     * @param entry the chat entry to save
     * @throws SQLException if database operation fails
     */
    public void saveChatEntry(ChatEntry entry) throws SQLException {
        databaseManager.inTransaction((Connection conn) -> {
            if (exists(conn, "chat_entries", entry.getId()))
                updateChatEntry(conn, entry);
            else
                insertChatEntry(conn, entry);
            return null;
        });
    }

    /**
     * Inserts a new chat entry into the database.
     *
     * @param conn  the connection of the transaction
     * @param entry the chat entry to insert
     * @throws SQLException if insert fails
     */
    private static void insertChatEntry(Connection conn, ChatEntry entry) throws SQLException {
        String sql = """
                INSERT INTO chat_entries (
                    id, chat_id, user_prompt, enhanced_prompt, status, error_message,
                    created_at, completed_at, sort_order, model, size, format, style,
                    prompt_upsampling, steps, guidance, seed, cost, mood_json, mood_id, frame_json, frame_id, source
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, entry.getId());
            stmt.setString(2, entry.getChatId());
            stmt.setString(3, entry.getUserPrompt());
            stmt.setString(4, entry.getEnhancedPrompt());
            stmt.setString(5, entry.getStatus().name());
            stmt.setString(6, entry.getErrorMessage());
            stmt.setTimestamp(7, Timestamp.valueOf(entry.getCreatedAt()));
            stmt.setTimestamp(8, entry.getCompletedAt() != null ? Timestamp.valueOf(entry.getCompletedAt()) : null);
            stmt.setInt(9, entry.getSortOrder());
            stmt.setString(10, entry.getModel());
            stmt.setString(11, entry.getSize());
            stmt.setString(12, entry.getFormat());
            stmt.setString(13, entry.getStyle() != null ? entry.getStyle() : "None");
            stmt.setBoolean(14, entry.isPromptUpsampling());
            stmt.setInt(15, 0); // steps removed - placeholder
            stmt.setDouble(16, entry.getSafetyTolerance()); // repurpose guidance column for safety_tolerance
            stmt.setLong(17, entry.getSeed());
            stmt.setDouble(18, entry.getCost());
            stmt.setString(19, entry.getMood() != null ? entry.getMood().toJson() : null);
            stmt.setString(20, entry.getMood() != null ? entry.getMood().moodId() : null);
            stmt.setString(21, entry.getFrame() != null ? entry.getFrame().toJson() : null);
            stmt.setString(22, entry.getFrame() != null ? entry.getFrame().frameId() : null);
            stmt.setString(23, entry.getSource().name());

            stmt.executeUpdate();
        }
    }

    /**
     * Updates an existing chat entry in the database.
     *
     * @param conn  the connection of the transaction
     * @param entry the chat entry to update
     * @throws SQLException if update fails
     */
    private static void updateChatEntry(Connection conn, ChatEntry entry) throws SQLException {
        String sql = """
                UPDATE chat_entries SET
                    user_prompt = ?, enhanced_prompt = ?, status = ?, error_message = ?,
                    completed_at = ?, sort_order = ?, model = ?, size = ?, format = ?, style = ?,
                    prompt_upsampling = ?, steps = ?, guidance = ?, seed = ?, cost = ?, mood_json = ?, mood_id = ?,
                    frame_json = ?, frame_id = ?
                WHERE id = ?
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, entry.getUserPrompt());
            stmt.setString(2, entry.getEnhancedPrompt());
            stmt.setString(3, entry.getStatus().name());
            stmt.setString(4, entry.getErrorMessage());
            stmt.setTimestamp(5, entry.getCompletedAt() != null ? Timestamp.valueOf(entry.getCompletedAt()) : null);
            stmt.setInt(6, entry.getSortOrder());
            stmt.setString(7, entry.getModel());
            stmt.setString(8, entry.getSize());
            stmt.setString(9, entry.getFormat());
            stmt.setString(10, entry.getStyle() != null ? entry.getStyle() : "None");
            stmt.setBoolean(11, entry.isPromptUpsampling());
            stmt.setInt(12, 0); // steps removed - placeholder
            stmt.setDouble(13, entry.getSafetyTolerance()); // repurpose guidance column for safety_tolerance
            stmt.setLong(14, entry.getSeed());
            stmt.setDouble(15, entry.getCost());
            stmt.setString(16, entry.getMood() != null ? entry.getMood().toJson() : null);
            stmt.setString(17, entry.getMood() != null ? entry.getMood().moodId() : null);
            stmt.setString(18, entry.getFrame() != null ? entry.getFrame().toJson() : null);
            stmt.setString(19, entry.getFrame() != null ? entry.getFrame().frameId() : null);
            stmt.setString(20, entry.getId());

            stmt.executeUpdate();
        }
    }

    /**
     * Finds a chat entry by ID.
     * 
     * @param entryId the entry ID
     * @return optional containing the entry if found
     * @throws SQLException if query fails
     */
    public Optional<ChatEntry> findChatEntryById(String entryId) throws SQLException {
        try (Connection conn = databaseManager.getConnection()) {
            return findChatEntryById(conn, entryId);
        }
    }

    private static Optional<ChatEntry> findChatEntryById(Connection conn, String entryId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM chat_entries WHERE id = ?")) {
            stmt.setString(1, entryId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapRowToChatEntry(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Finds all chat entries for a specific chat.
     * 
     * @param chatId the chat ID
     * @return list of chat entries
     * @throws SQLException if query fails
     */
    public List<ChatEntry> findChatEntriesByChatId(String chatId) throws SQLException {
        String sql = "SELECT * FROM chat_entries WHERE chat_id = ? AND deleted_at IS NULL ORDER BY sort_order, created_at";

        List<ChatEntry> entries = new ArrayList<>();

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, chatId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(mapRowToChatEntry(rs));
                }
            }
        }

        return entries;
    }

    // ========== Mood Group Operations ==========

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

    // ========== Frame Group Operations ==========

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

    // ========== Generated Image Operations ==========

    /*  whether the entry is stored and not deleted  */
    private static boolean chatEntryExistsAndActive(Connection conn, String entryId) throws SQLException {
        if (entryId == null)
            return false;

        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM chat_entries WHERE id = ? AND deleted_at IS NULL")) {
            stmt.setString(1, entryId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Saves a generated image to the database (insert or update). An image whose entry is deleted or not stored is
     * not saved, on purpose and without an error: a generation that finishes after its entry went to the trash
     * leaves no image behind. Only a debug message tells about it.
     *
     * @param image the image to save
     * @throws SQLException if database operation fails
     */
    public void saveGeneratedImage(GeneratedImage image) throws SQLException {
        databaseManager.inTransaction((Connection conn) -> {
            if (!chatEntryExistsAndActive(conn, image.getChatEntryId())) {
                logger.debug("Image {} not saved: its entry {} is deleted or not stored", image.getId(),
                        image.getChatEntryId());
                return null;
            }
            if (exists(conn, "generated_images", image.getId()))
                updateGeneratedImage(conn, image);
            else
                insertGeneratedImage(conn, image);
            return null;
        });
    }

    /**
     * Inserts a new generated image into the database; its entry is stored (see {@link #saveGeneratedImage}).
     *
     * @param conn  the connection of the transaction
     * @param image the image to insert
     * @throws SQLException if insert fails
     */
    private static void insertGeneratedImage(Connection conn, GeneratedImage image) throws SQLException {
        String sql = """
                INSERT INTO generated_images (
                    id, chat_entry_id, image_url, local_file_path, file_name,
                    download_status, created_at, downloaded_at, file_size_bytes,
                    image_width, image_height, mime_type, sort_order,
                    generation_job_id, prompt, model, seed, prompt_upsampling,
                    base_cost, search_cost, reasoning_cost, thinking_tokens, thought_summary,
                    is_video, video_duration_seconds, video_resolution, has_audio, negative_prompt,
                    output_format, width, height
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, image.getId());
            stmt.setString(2, image.getChatEntryId());
            stmt.setString(3, image.getImageUrl());
            stmt.setString(4, image.getLocalFilePath());
            stmt.setString(5, image.getFileName());
            stmt.setString(6, image.getDownloadStatus().name());
            stmt.setTimestamp(7, Timestamp.valueOf(image.getCreatedAt()));
            stmt.setTimestamp(8, image.getDownloadedAt() != null ? Timestamp.valueOf(image.getDownloadedAt()) : null);
            stmt.setLong(9, image.getFileSizeBytes());
            stmt.setInt(10, image.getImageWidth());
            stmt.setInt(11, image.getImageHeight());
            stmt.setString(12, image.getMimeType());
            stmt.setInt(13, image.getSortOrder());
            stmt.setString(14, image.getGenerationJobId());
            stmt.setString(15, image.getPrompt());
            stmt.setString(16, image.getModel());
            stmt.setLong(17, image.getSeed());
            stmt.setBoolean(18, image.isPromptUpsampling());

            // Gemini Phase 2 cost tracking
            stmt.setDouble(19, image.getBaseCost() != null ? image.getBaseCost() : 0.0);
            stmt.setDouble(20, image.getSearchCost() != null ? image.getSearchCost() : 0.0);
            stmt.setDouble(21, image.getReasoningCost() != null ? image.getReasoningCost() : 0.0);
            stmt.setObject(22, image.getThinkingTokens()); // NULL if not set
            stmt.setString(23, image.getThoughtSummary());

            // video fields
            stmt.setBoolean(24, image.isVideo());
            stmt.setInt(25, image.getVideoDurationSeconds());
            stmt.setString(26, image.getVideoResolution());
            stmt.setBoolean(27, image.hasAudio());
            stmt.setString(28, null); // negative_prompt - not stored in GeneratedImage model
            stmt.setString(29, image.getFormat());
            stmt.setInt(30, image.getWidth());
            stmt.setInt(31, image.getHeight());

            stmt.executeUpdate();
        }
    }

    /**
     * Updates an existing generated image in the database.
     *
     * @param conn  the connection of the transaction
     * @param image the image to update
     * @throws SQLException if update fails
     */
    private static void updateGeneratedImage(Connection conn, GeneratedImage image) throws SQLException {
        String sql = """
                UPDATE generated_images SET
                    image_url = ?, local_file_path = ?, file_name = ?, download_status = ?,
                    downloaded_at = ?, file_size_bytes = ?, image_width = ?, image_height = ?,
                    mime_type = ?, sort_order = ?, generation_job_id = ?, prompt = ?,
                    model = ?, seed = ?, steps = ?, guidance = ?, prompt_upsampling = ?,
                    error_message = ?, failure_reason = ?, api_status = ?, aspect_ratio = ?,
                    style_preset = ?, safety_tolerance = ?, output_format = ?, width = ?, height = ?,
                    base_cost = ?, search_cost = ?, reasoning_cost = ?, thinking_tokens = ?, thought_summary = ?,
                    is_video = ?, video_duration_seconds = ?, video_resolution = ?, has_audio = ?
                WHERE id = ?
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, image.getImageUrl());
            stmt.setString(2, image.getLocalFilePath());
            stmt.setString(3, image.getFileName());
            stmt.setString(4, image.getDownloadStatus().name());
            stmt.setTimestamp(5, image.getDownloadedAt() != null ? Timestamp.valueOf(image.getDownloadedAt()) : null);
            stmt.setLong(6, image.getFileSizeBytes());
            stmt.setInt(7, image.getImageWidth());
            stmt.setInt(8, image.getImageHeight());
            stmt.setString(9, image.getMimeType());
            stmt.setInt(10, image.getSortOrder());
            stmt.setString(11, image.getGenerationJobId());
            stmt.setString(12, image.getPrompt());
            stmt.setString(13, image.getModel());
            stmt.setLong(14, image.getSeed());
            stmt.setInt(15, 0); // steps removed - placeholder
            stmt.setDouble(16, image.getSafetyTolerance()); // repurpose guidance column for safety_tolerance
            stmt.setBoolean(17, image.isPromptUpsampling());
            // Error tracking fields
            stmt.setString(18, image.getErrorMessage());
            stmt.setString(19, image.getFailureReason());
            stmt.setString(20, image.getApiStatus());
            // Additional parameters for recreation
            stmt.setString(21, image.getAspectRatio());
            stmt.setString(22, image.getStylePreset());
            stmt.setInt(23, image.getSafetyTolerance());
            stmt.setString(24, image.getFormat());
            stmt.setInt(25, image.getWidth());
            stmt.setInt(26, image.getHeight());
            // Gemini Phase 2 cost tracking
            stmt.setDouble(27, image.getBaseCost() != null ? image.getBaseCost() : 0.0);
            stmt.setDouble(28, image.getSearchCost() != null ? image.getSearchCost() : 0.0);
            stmt.setDouble(29, image.getReasoningCost() != null ? image.getReasoningCost() : 0.0);
            stmt.setObject(30, image.getThinkingTokens()); // NULL if not set
            stmt.setString(31, image.getThoughtSummary());
            // video fields
            stmt.setBoolean(32, image.isVideo());
            stmt.setInt(33, image.getVideoDurationSeconds());
            stmt.setString(34, image.getVideoResolution());
            stmt.setBoolean(35, image.hasAudio());
            stmt.setString(36, image.getId());

            stmt.executeUpdate();
        }
    }

    /**
     * Stores the pixel size of an image as read from its file, also for an image in the trash.
     *
     * @param imageId the image ID
     * @param width   the width in pixels
     * @param height  the height in pixels
     * @throws SQLException if the update fails
     */
    public void updateImageSize(String imageId, int width, int height) throws SQLException {
        String sql = "UPDATE generated_images SET image_width = ?, image_height = ?, width = ?, height = ? WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, width);
            stmt.setInt(2, height);
            stmt.setInt(3, width);
            stmt.setInt(4, height);
            stmt.setString(5, imageId);
            stmt.executeUpdate();
        }
    }


    /**
     * Finds all generated images for a specific chat entry.
     * 
     * @param chatEntryId the chat entry ID
     * @return list of generated images
     * @throws SQLException if query fails
     */
    public List<GeneratedImage> findGeneratedImagesByEntry(String chatEntryId) throws SQLException {
        try (Connection conn = databaseManager.getConnection()) {
            return findGeneratedImagesByEntry(conn, chatEntryId);
        }
    }

    private static List<GeneratedImage> findGeneratedImagesByEntry(Connection conn, String chatEntryId)
            throws SQLException {
        String sql = "SELECT * FROM generated_images WHERE chat_entry_id = ? AND deleted_at IS NULL ORDER BY sort_order, created_at";

        List<GeneratedImage> images = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, chatEntryId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    images.add(mapRowToGeneratedImage(rs));
                }
            }
        }

        return images;
    }

    /**
     * Finds all generated images for multiple chat entries in a single query.
     * This solves the N+1 problem by loading all images at once instead of
     * one query per entry.
     *
     * @param entryIds list of chat entry IDs
     * @return map of entry ID to list of generated images
     * @throws SQLException if query fails
     */
    public Map<String, List<GeneratedImage>> findImagesByEntryIds(List<String> entryIds) throws SQLException {
        if (entryIds == null || entryIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // Build IN clause: WHERE chat_entry_id IN (?, ?, ?, ...)
        String placeholders = String.join(",", Collections.nCopies(entryIds.size(), "?"));
        String sql = "SELECT * FROM generated_images WHERE chat_entry_id IN (" + placeholders + ") " +
                     "AND deleted_at IS NULL ORDER BY chat_entry_id, sort_order, created_at";

        Map<String, List<GeneratedImage>> result = new HashMap<>();

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < entryIds.size(); i++) {
                stmt.setString(i + 1, entryIds.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    GeneratedImage img = mapRowToGeneratedImage(rs);
                    result.computeIfAbsent(img.getChatEntryId(), k -> new ArrayList<>()).add(img);
                }
            }
        }

        logger.debug("Batch loaded {} images for {} entries in single query",
                     result.values().stream().mapToInt(List::size).sum(), entryIds.size());

        return result;
    }

    // ========== Helper Methods ==========

    /**
     * Maps a database row to a Chat object.
     *
     * @param rs the result set
     * @return the mapped chat
     * @throws SQLException if mapping fails
     */
    private static Chat mapRowToChat(ResultSet rs) throws SQLException {
        Chat.Builder builder = new Chat.Builder()
                .id(rs.getString("id"))
                .name(rs.getString("name"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .sortOrder(rs.getInt("sort_order"))
                .deletedCost(rs.getDouble("deleted_cost"))
                .deletedAt(readDeletedAt(rs));

        return builder.build();
    }

    /**
     * Maps a database row to a ChatEntry object.
     * 
     * @param rs the result set
     * @return the mapped chat entry
     * @throws SQLException if mapping fails
     */
    private static ChatEntry mapRowToChatEntry(ResultSet rs) throws SQLException {
        ChatEntry.Builder builder = new ChatEntry.Builder()
                .id(rs.getString("id"))
                .chatId(rs.getString("chat_id"))
                .userPrompt(rs.getString("user_prompt"))
                .enhancedPrompt(rs.getString("enhanced_prompt"))
                .status(ChatEntry.Status.valueOf(rs.getString("status")))
                .errorMessage(rs.getString("error_message"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .sortOrder(rs.getInt("sort_order"))
                .model(rs.getString("model"))
                .size(rs.getString("size"))
                .format(rs.getString("format"))
                .style(rs.getString("style"))
                .mood(EntryMood.fromJson(rs.getString("mood_json")))
                .frame(EntryFrame.fromJson(rs.getString("frame_json")))
                .source(ChatEntry.Source.valueOf(rs.getString("source")))
                .promptUpsampling(rs.getBoolean("prompt_upsampling"))
                .safetyTolerance((int) rs.getDouble("guidance")) // repurposed guidance column
                .seed(rs.getLong("seed"))
                .cost(rs.getDouble("cost"))
                .deletedCost(rs.getDouble("deleted_cost"));

        Timestamp completedAt = rs.getTimestamp("completed_at");
        if (completedAt != null) {
            builder.completedAt(completedAt.toLocalDateTime());
        }
        builder.deletedAt(readDeletedAt(rs));

        return builder.build();
    }

    /**
     * Reads the deleted_at column of a chat, an entry or an image (see {@link #parseDeletedAt}).
     *
     * @param rs the result set
     * @return when the row went to the trash, null as long as it is in use
     * @throws SQLException if the column cannot be read
     */
    private static LocalDateTime readDeletedAt(ResultSet rs) throws SQLException {
        return parseDeletedAt(rs.getObject("deleted_at"));
    }

    /**
     * Turns a stored deleted_at into the local time. It is written as epoch milliseconds (see package-info); rows put
     * in the trash before 2.1.3 hold text: a chat the local time as ISO-8601 ({@code 2026-11-18T16:36:13.512}), an
     * entry or an image the UTC time of SQLite's {@code CURRENT_TIMESTAMP} ({@code 2026-11-18 15:36:13}).
     *
     * @param value the value of the column
     * @return the local time, null for none or for a value that cannot be read
     */
    static LocalDateTime parseDeletedAt(Object value) {
        if (value == null)
            return null;
        if (value instanceof Number millis)
            return new Timestamp(millis.longValue()).toLocalDateTime();

        String text = value.toString().trim();
        try {
            if (!text.isEmpty() && text.chars().allMatch(Character::isDigit))
                return new Timestamp(Long.parseLong(text)).toLocalDateTime();
            if (text.indexOf('T') >= 0)
                return LocalDateTime.parse(text);
            return LocalDateTime.parse(text.replace(' ', 'T')).atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (RuntimeException e) {
            logger.warn("deleted_at '{}' cannot be read, taken as not deleted", text);
            return null;
        }
    }

    /**
     * Maps a database row to a GeneratedImage object.
     * 
     * @param rs the result set
     * @return the mapped generated image
     * @throws SQLException if mapping fails
     */
    private static GeneratedImage mapRowToGeneratedImage(ResultSet rs) throws SQLException {
        GeneratedImage.Builder builder = new GeneratedImage.Builder()
                .id(rs.getString("id"))
                .chatEntryId(rs.getString("chat_entry_id"))
                .imageUrl(rs.getString("image_url"))
                .localFilePath(rs.getString("local_file_path"))
                .fileName(rs.getString("file_name"))
                .downloadStatus(GeneratedImage.DownloadStatus.valueOf(rs.getString("download_status")))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .fileSizeBytes(rs.getLong("file_size_bytes"))
                /*  The size is image_width/image_height alone: the columns width/height held 1024 for an image
                    stored without them, and the builder's width()/height() would overwrite the real size  */
                .imageWidth(rs.getInt("image_width"))
                .imageHeight(rs.getInt("image_height"))
                .mimeType(rs.getString("mime_type"))
                .sortOrder(rs.getInt("sort_order"))
                .generationJobId(rs.getString("generation_job_id"))
                .prompt(rs.getString("prompt"))
                .model(rs.getString("model"))
                .seed(rs.getLong("seed"))
                .safetyTolerance((int) rs.getDouble("guidance")) // repurposed guidance column
                .promptUpsampling(rs.getBoolean("prompt_upsampling"))
                // Error tracking fields
                .errorMessage(rs.getString("error_message"))
                .failureReason(rs.getString("failure_reason"))
                .apiStatus(rs.getString("api_status"))
                // Additional parameters for recreation
                .aspectRatio(rs.getString("aspect_ratio"))
                .stylePreset(rs.getString("style_preset"));

        // Prefer the actually delivered mime_type over the requested output_format,
        // so the UI always shows what the API really returned
        String storedMimeType = rs.getString("mime_type");
        if (storedMimeType == null || storedMimeType.isEmpty()) {
            String outputFormat = rs.getString("output_format");
            if (outputFormat != null) builder.format(outputFormat);
        }

        /*  the cost columns come from V007, the video columns from V011  */
        builder.baseCost(rs.getDouble("base_cost"))
                .searchCost(rs.getDouble("search_cost"))
                .reasoningCost(rs.getDouble("reasoning_cost"));
        int thinkingTokens = rs.getInt("thinking_tokens");
        builder.thinkingTokens(rs.wasNull() ? null : thinkingTokens)
                .thoughtSummary(rs.getString("thought_summary"))
                .isVideo(rs.getBoolean("is_video"))
                .videoDurationSeconds(rs.getInt("video_duration_seconds"))
                .videoResolution(rs.getString("video_resolution"))
                .hasAudio(rs.getBoolean("has_audio"));

        Timestamp downloadedAt = rs.getTimestamp("downloaded_at");
        if (downloadedAt != null) {
            builder.downloadedAt(downloadedAt.toLocalDateTime());
        }
        builder.deletedAt(readDeletedAt(rs));

        return builder.build();
    }
    
    // ============= SOFT DELETE METHODS =============

    /**
     * Soft deletes a single image and moves it to trash.
     * The entry's deleted_cost grows by the image's share of the entry cost (see {@link ChatCost#imageShare});
     * with its last image the entry is deleted, and its cost goes to the chat's deleted_cost. Reading the costs and
     * booking them is one transaction, so of two deletions at the same time (main window and agent) one books.
     *
     * @param imageId the image ID to delete
     * @return true if successfully deleted, false if it is not stored or already deleted
     * @throws SQLException if database operation fails
     */
    public boolean softDeleteImage(String imageId) throws SQLException {
        logger.info("Soft deleting image: {}", imageId);
        Timestamp deletedAt = now();

        Double imageCost = databaseManager.inTransaction((Connection conn) -> {
            GeneratedImage image = findGeneratedImageById(conn, imageId).orElse(null);
            if (image == null || image.getDeletedAt() != null)
                return null;

            // The image's share of what its entry cost, less the images deleted before
            ChatEntry entry = findChatEntryById(conn, image.getChatEntryId()).orElse(null);
            double cost = entry != null ? ChatCost.imageShare(entry.getCost() - entry.getDeletedCost(),
                    findGeneratedImagesByEntry(conn, entry.getId()), image) : 0.0;

            // 1. Mark image as deleted
            String sqlImage = """
                UPDATE generated_images
                SET deleted_at = ?,
                    deleted_cost = ?,
                    is_in_trash = TRUE
                WHERE id = ? AND deleted_at IS NULL
                """;
            try (PreparedStatement stmt = conn.prepareStatement(sqlImage)) {
                stmt.setTimestamp(1, deletedAt);
                stmt.setDouble(2, cost);
                stmt.setString(3, imageId);
                if (stmt.executeUpdate() == 0)
                    return null;
            }

            // 2. Update entry's deleted_cost
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE chat_entries SET deleted_cost = deleted_cost + ? WHERE id = ?")) {
                stmt.setDouble(1, cost);
                stmt.setString(2, image.getChatEntryId());
                stmt.executeUpdate();
            }

            // 3. If no active images are left, mark the entry as deleted; its cost goes to the chat
            String sqlCheck = "SELECT COUNT(*) FROM generated_images WHERE chat_entry_id = ? AND deleted_at IS NULL";
            try (PreparedStatement stmt = conn.prepareStatement(sqlCheck)) {
                stmt.setString(1, image.getChatEntryId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0)
                        markEntryDeleted(conn, image.getChatEntryId(), deletedAt);
                }
            }
            return cost;
        });

        if (imageCost == null) {
            logger.warn("Image not found or already deleted for soft delete: {}", imageId);
            return false;
        }
        logger.info("Successfully soft deleted image: {} (cost: {})", imageId, imageCost);
        return true;
    }

    /**
     * Soft deletes a chat entry and all its images.
     *
     * @param entryId the entry ID to delete
     * @return true if successfully deleted, false if it is not stored or already deleted
     * @throws SQLException if database operation fails
     */
    public boolean softDeleteChatEntry(String entryId) throws SQLException {
        logger.info("Soft deleting chat entry: {}", entryId);
        Timestamp deletedAt = now();

        boolean deleted = databaseManager.inTransaction((Connection conn) -> {
            // 1. Mark entry as deleted; its whole cost goes to the chat
            if (!markEntryDeleted(conn, entryId, deletedAt))
                return false;

            // 2. Mark all images as deleted; they keep the deleted_cost 0, as the entry books their cost
            String sqlImages = """
                UPDATE generated_images
                SET deleted_at = ?,
                    is_in_trash = TRUE
                WHERE chat_entry_id = ? AND deleted_at IS NULL
                """;
            try (PreparedStatement stmt = conn.prepareStatement(sqlImages)) {
                stmt.setTimestamp(1, deletedAt);
                stmt.setString(2, entryId);
                stmt.executeUpdate();
            }
            return true;
        });

        if (deleted)
            logger.info("Successfully soft deleted entry: {}", entryId);
        else
            logger.info("Entry {} is not stored or already deleted, skipping", entryId);
        return deleted;
    }

    /*  Marks an entry in use as deleted: its whole cost is deleted now, and its chat books it, as the entry leaves
        the entries of the chat (see ChatCost); false if it is not stored or already deleted  */
    private static boolean markEntryDeleted(Connection conn, String entryId, Timestamp deletedAt)
            throws SQLException {
        String sqlEntry = """
            UPDATE chat_entries
            SET deleted_at = ?,
                deleted_cost = cost
            WHERE id = ? AND deleted_at IS NULL
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sqlEntry)) {
            stmt.setTimestamp(1, deletedAt);
            stmt.setString(2, entryId);
            if (stmt.executeUpdate() == 0)
                return false;
        }

        String sqlChat = """
            UPDATE chats
            SET deleted_cost = deleted_cost + (SELECT cost FROM chat_entries WHERE id = ?)
            WHERE id = (SELECT chat_id FROM chat_entries WHERE id = ?)
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sqlChat)) {
            stmt.setString(1, entryId);
            stmt.setString(2, entryId);
            stmt.executeUpdate();
        }
        return true;
    }

    /**
     * Finds a generated image by ID, also one in the trash.
     *
     * @param imageId the image ID
     * @return optional containing the image if found
     * @throws SQLException if query fails
     */
    public Optional<GeneratedImage> findGeneratedImageById(String imageId) throws SQLException {
        try (Connection conn = databaseManager.getConnection()) {
            return findGeneratedImageById(conn, imageId);
        }
    }

    private static Optional<GeneratedImage> findGeneratedImageById(Connection conn, String imageId)
            throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM generated_images WHERE id = ?")) {
            stmt.setString(1, imageId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapRowToGeneratedImage(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Gets the deleted cost for a chat entry.
     *
     * @param entryId the entry
     * @return its deleted cost, 0 if it is not stored
     * @throws SQLException if query fails
     */
    public double getDeletedCostForEntry(String entryId) throws SQLException {
        String sql = "SELECT deleted_cost FROM chat_entries WHERE id = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, entryId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getDouble("deleted_cost") : 0.0;
            }
        }
    }

    // ============= IMAGES IN THE TRASH =============

    /**
     * Finds the entries of the chats in use that have images in the trash: entries deleted as a whole and entries
     * in use that lost single images. The images of a deleted chat stay with the chat.
     *
     * @return the entries, each carrying its deleted images (not the ones in use)
     * @throws SQLException if query fails
     */
    public List<ChatEntry> findEntriesWithDeletedImages() throws SQLException {
        String sqlEntries = """
                SELECT ce.* FROM chat_entries ce
                JOIN chats c ON ce.chat_id = c.id
                WHERE c.deleted_at IS NULL
                AND (ce.deleted_at IS NOT NULL OR EXISTS (SELECT 1 FROM generated_images gi
                    WHERE gi.chat_entry_id = ce.id AND gi.deleted_at IS NOT NULL))
                ORDER BY ce.sort_order, ce.created_at
                """;
        String sqlImages = """
                SELECT gi.* FROM generated_images gi
                JOIN chat_entries ce ON gi.chat_entry_id = ce.id
                JOIN chats c ON ce.chat_id = c.id
                WHERE c.deleted_at IS NULL AND gi.deleted_at IS NOT NULL
                ORDER BY gi.sort_order, gi.created_at
                """;

        Map<String, ChatEntry> entries = new LinkedHashMap<>();
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(sqlEntries);
                    ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ChatEntry entry = mapRowToChatEntry(rs);
                    entries.put(entry.getId(), entry);
                }
            }

            /*  An image deleted between both queries has no entry here and waits for the next listing  */
            try (PreparedStatement stmt = conn.prepareStatement(sqlImages);
                    ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    GeneratedImage image = mapRowToGeneratedImage(rs);
                    ChatEntry entry = entries.get(image.getChatEntryId());
                    if (entry != null)
                        entry.getImages().add(image);
                }
            }
        }
        return new ArrayList<>(entries.values());
    }

    /**
     * Finds the deleted images of an entry.
     *
     * @param entryId the entry
     * @return the images of the entry in the trash
     * @throws SQLException if query fails
     */
    public List<GeneratedImage> findDeletedImagesByEntry(String entryId) throws SQLException {
        try (Connection conn = databaseManager.getConnection()) {
            return findDeletedImagesByEntry(conn, entryId);
        }
    }

    private static List<GeneratedImage> findDeletedImagesByEntry(Connection conn, String entryId)
            throws SQLException {
        String sql = "SELECT * FROM generated_images WHERE chat_entry_id = ? AND deleted_at IS NOT NULL "
                + "ORDER BY sort_order, created_at";

        List<GeneratedImage> images = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, entryId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next())
                    images.add(mapRowToGeneratedImage(rs));
            }
        }
        return images;
    }

    /**
     * Takes an entry out of the trash with all its deleted images: a deleted entry is back in use, an entry in use
     * gets back the images it lost. Only the database is touched, the files are up to the caller.
     *
     * @param entryId the entry
     * @return true if the entry or some of its images came back
     * @throws SQLException if database operation fails
     */
    public boolean restoreChatEntry(String entryId) throws SQLException {
        return databaseManager.inTransaction((Connection conn) -> restoreImages(conn, entryId, null));
    }

    /**
     * Takes a single deleted image out of the trash; a deleted entry comes back with it, its other images stay in
     * the trash. Only the database is touched, the files are up to the caller.
     *
     * @param imageId the image
     * @return true if the image came back
     * @throws SQLException if database operation fails
     */
    public boolean restoreImage(String imageId) throws SQLException {
        return databaseManager.inTransaction((Connection conn) -> {
            GeneratedImage image = findGeneratedImageById(conn, imageId).orElse(null);
            return image != null && restoreImages(conn, image.getChatEntryId(), imageId);
        });
    }

    /**
     * Restores the deleted images of an entry, all of them or a single one, and the entry with them if it is
     * deleted. The costs follow the deletion backwards: a deleted entry leaves the deleted cost of its chat, and
     * the deleted cost of the entry is what its images still in the trash had cost. Runs in the transaction of the
     * caller, so what it reads is what it changes.
     */
    private static boolean restoreImages(Connection conn, String entryId, String imageId) throws SQLException {
        ChatEntry entry = findChatEntryById(conn, entryId).orElse(null);
        if (entry == null)
            return false;
        List<GeneratedImage> deleted = findDeletedImagesByEntry(conn, entryId);
        boolean nothingToRestore = imageId != null
                ? deleted.stream().noneMatch((image) -> image.getId().equals(imageId))
                : entry.getDeletedAt() == null && deleted.isEmpty();
        if (nothingToRestore)
            return false;

        /*  The chat booked the whole cost of a deleted entry (see markEntryDeleted) and drops it; the images
            deleted together with the entry, those without a cost of their own, share what the images deleted
            before left of the entry cost  */
        if (entry.getDeletedAt() != null) {
            double booked = 0.0;
            List<GeneratedImage> together = new ArrayList<>();
            try (PreparedStatement select = conn.prepareStatement(
                    "SELECT * FROM generated_images WHERE chat_entry_id = ? AND deleted_at IS NOT NULL")) {
                select.setString(1, entryId);
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        double imageCost = rs.getDouble("deleted_cost");
                        booked += imageCost;
                        if (imageCost == 0)
                            together.add(mapRowToGeneratedImage(rs));
                    }
                }
            }
            try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE generated_images SET deleted_cost = ? WHERE id = ?")) {
                for (GeneratedImage image : together) {
                    update.setDouble(1, ChatCost.imageShare(entry.getCost() - booked, together, image));
                    update.setString(2, image.getId());
                    update.executeUpdate();
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE chats SET deleted_cost = MAX(0, deleted_cost - ?) WHERE id = ?")) {
                stmt.setDouble(1, entry.getCost());
                stmt.setString(2, entry.getChatId());
                stmt.executeUpdate();
            }
        }

        String sqlImages = "UPDATE generated_images SET deleted_at = NULL, deleted_cost = 0, is_in_trash = FALSE "
                + (imageId != null ? "WHERE id = ?" : "WHERE chat_entry_id = ? AND deleted_at IS NOT NULL");
        try (PreparedStatement stmt = conn.prepareStatement(sqlImages)) {
            stmt.setString(1, imageId != null ? imageId : entryId);
            stmt.executeUpdate();
        }

        String sqlEntry = """
            UPDATE chat_entries
            SET deleted_at = NULL,
                deleted_cost = (SELECT COALESCE(SUM(deleted_cost), 0) FROM generated_images
                    WHERE chat_entry_id = ? AND deleted_at IS NOT NULL)
            WHERE id = ?
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sqlEntry)) {
            stmt.setString(1, entryId);
            stmt.setString(2, entryId);
            stmt.executeUpdate();
        }

        logger.info("Restored {} from the trash: entry {}", imageId != null ? "image " + imageId : "all images",
                entryId);
        return true;
    }

    /**
     * Removes what an entry has in the trash for good: its deleted images, and the entry itself if it is deleted.
     * The costs stay as they are, as the images were paid for. Only the database is touched, the files are up to
     * the caller.
     *
     * @param entryId the entry
     * @return true if something was removed
     * @throws SQLException if database operation fails
     */
    public boolean deleteChatEntryPermanently(String entryId) throws SQLException {
        return databaseManager.inTransaction((Connection conn) -> {
            try (PreparedStatement images = conn.prepareStatement(
                        "DELETE FROM generated_images WHERE chat_entry_id = ? AND deleted_at IS NOT NULL");
                    PreparedStatement entry = conn.prepareStatement(
                        "DELETE FROM chat_entries WHERE id = ? AND deleted_at IS NOT NULL")) {
                images.setString(1, entryId);
                entry.setString(1, entryId);
                return images.executeUpdate() + entry.executeUpdate() > 0;
            }
        });
    }

    /**
     * Removes a deleted image for good; a deleted entry goes with its last image. The costs stay as they are, as
     * the image was paid for. Only the database is touched, the files are up to the caller.
     *
     * @param imageId the image
     * @return true if the image was removed
     * @throws SQLException if database operation fails
     */
    public boolean deleteImagePermanently(String imageId) throws SQLException {
        return databaseManager.inTransaction((Connection conn) -> {
            GeneratedImage image = findGeneratedImageById(conn, imageId).orElse(null);
            if (image == null)
                return false;

            try (PreparedStatement deleteImage = conn.prepareStatement(
                        "DELETE FROM generated_images WHERE id = ? AND deleted_at IS NOT NULL");
                    PreparedStatement deleteEntry = conn.prepareStatement(
                        "DELETE FROM chat_entries WHERE id = ? AND deleted_at IS NOT NULL "
                        + "AND NOT EXISTS (SELECT 1 FROM generated_images WHERE chat_entry_id = ?)")) {
                deleteImage.setString(1, imageId);
                if (deleteImage.executeUpdate() == 0)
                    return false;

                deleteEntry.setString(1, image.getChatEntryId());
                deleteEntry.setString(2, image.getChatEntryId());
                deleteEntry.executeUpdate();
                return true;
            }
        });
    }
}
