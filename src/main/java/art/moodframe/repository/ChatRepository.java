package art.moodframe.repository;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryFrame;
import art.moodframe.model.EntryMood;
import art.moodframe.model.GeneratedImage;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores and finds the chats, their entries and their images, and maps their rows.
 *
 * <p>Their trash with the booking of the deleted costs and the files lives in {@link ChatTrashRepository}, the
 * groups of the entries by mood and by frame in {@link ChatGroupQueries}; both use the mapping of this class.</p>
 *
 * <p>The trash: the finders of one row by id ({@link #findChatById}, {@link #findChatEntryById},
 * {@link #findGeneratedImageById}) also answer a row in the trash, with its {@code deleted_at} set, as the trash, its
 * restore and a deletion look the rows up by id; the finders of lists skip the rows in the trash, except
 * {@link #findDeletedChats()}.</p>
 *
 * <p>Former columns: {@code guidance} of the entries and images holds the safety tolerance of the generation (the
 * column of the former FLUX guidance, reused); {@code steps} is always written as 0.</p>
 *
 * @since 1.0.0
 */
public class ChatRepository {

    private static final Logger logger = LoggerFactory.getLogger(ChatRepository.class);

    private final DatabaseManager databaseManager;

    /**
     * Creates a new chat repository.
     *
     * @param databaseManager the database manager
     */
    public ChatRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
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
    static Timestamp now() {
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
     * only by {@link ChatTrashRepository#softDeleteChat} and {@link ChatTrashRepository#restoreChat}, so a stale chat in memory undoes neither.
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
     * Finds a chat by ID, also one in the trash (its deleted_at set).
     *
     * @param chatId the chat ID
     * @return optional containing the chat if it is stored, in use or in the trash
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
     * Finds a chat entry by ID, also one in the trash or of a chat in the trash.
     *
     * @param entryId the entry ID
     * @return optional containing the entry if it is stored, in use or in the trash
     * @throws SQLException if query fails
     */
    public Optional<ChatEntry> findChatEntryById(String entryId) throws SQLException {
        try (Connection conn = databaseManager.getConnection()) {
            return findChatEntryById(conn, entryId);
        }
    }

    static Optional<ChatEntry> findChatEntryById(Connection conn, String entryId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM chat_entries WHERE id = ?")) {
            stmt.setString(1, entryId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapRowToChatEntry(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Finds the chat entries of a chat that are not in the trash on their own.
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
     * Finds the generated images of a chat entry that are not in the trash on their own.
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

    static List<GeneratedImage> findGeneratedImagesByEntry(Connection conn, String chatEntryId)
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
     * Finds the generated images of several chat entries that are not in the trash, in a single query.
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

    /**
     * Finds a generated image by ID, also one in the trash or of an entry or chat in the trash.
     *
     * @param imageId the image ID
     * @return optional containing the image if it is stored, in use or in the trash
     * @throws SQLException if query fails
     */
    public Optional<GeneratedImage> findGeneratedImageById(String imageId) throws SQLException {
        try (Connection conn = databaseManager.getConnection()) {
            return findGeneratedImageById(conn, imageId);
        }
    }

    static Optional<GeneratedImage> findGeneratedImageById(Connection conn, String imageId)
            throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM generated_images WHERE id = ?")) {
            stmt.setString(1, imageId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapRowToGeneratedImage(rs)) : Optional.empty();
            }
        }
    }

    // ========== Helper Methods ==========

    /**
     * Maps a database row to a Chat object.
     *
     * @param rs the result set
     * @return the mapped chat
     * @throws SQLException if mapping fails
     */
    static Chat mapRowToChat(ResultSet rs) throws SQLException {
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
    static ChatEntry mapRowToChatEntry(ResultSet rs) throws SQLException {
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
     * A value that cannot be read still marks a row in the trash: it is logged and taken as deleted now, so the row
     * neither comes back into use nor runs out of the trash before the user decides.
     *
     * @param value the value of the column
     * @return the local time, null for none
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
            logger.warn("deleted_at '{}' cannot be read, taken as deleted now", text);
            return LocalDateTime.now();
        }
    }

    /**
     * Maps a database row to a GeneratedImage object.
     *
     * @param rs the result set
     * @return the mapped generated image
     * @throws SQLException if mapping fails
     */
    static GeneratedImage mapRowToGeneratedImage(ResultSet rs) throws SQLException {
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
}
