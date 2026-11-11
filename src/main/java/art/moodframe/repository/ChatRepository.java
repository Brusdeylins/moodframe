package art.moodframe.repository;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryMood;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.Mood;
import art.moodframe.model.MoodGroup;
import art.moodframe.model.PromptBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
    private final ApplicationConfig config;

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
        this.config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
    }

    // ========== Chat Operations ==========

    /**
     * Saves a chat to the database (insert or update).
     * 
     * @param chat the chat to save
     * @throws SQLException if database operation fails
     */
    public void saveChat(Chat chat) throws SQLException {
        if (findChatById(chat.getId()).isPresent()) {
            updateChat(chat);
        } else {
            insertChat(chat);
        }
    }

    /**
     * Inserts a new chat into the database.
     * 
     * @param chat the chat to insert
     * @throws SQLException if insert fails
     */
    private void insertChat(Chat chat) throws SQLException {
        String sql = """
                INSERT INTO chats (id, name, created_at, updated_at, deleted_at, sort_order)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

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
     * Updates an existing chat in the database.
     * 
     * @param chat the chat to update
     * @throws SQLException if update fails
     */
    private void updateChat(Chat chat) throws SQLException {
        String sql = """
                UPDATE chats SET name = ?, updated_at = ?, deleted_at = ?, sort_order = ?
                WHERE id = ?
                """;

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, chat.getName());
            stmt.setTimestamp(2, Timestamp.valueOf(chat.getUpdatedAt()));
            stmt.setTimestamp(3, chat.getDeletedAt() != null ? Timestamp.valueOf(chat.getDeletedAt()) : null);
            stmt.setInt(4, chat.getSortOrder());
            stmt.setString(5, chat.getId());

            stmt.executeUpdate();
        }
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
        String sql = "SELECT * FROM chats WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC";

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
     * Soft deletes a chat by setting the deleted_at timestamp.
     * Files are moved to trash instead of being deleted.
     * 
     * @param chatId the chat ID to soft delete
     * @return true if chat was marked as deleted
     * @throws SQLException if database operation fails
     */
    public boolean softDeleteChat(String chatId) throws SQLException {
        String sql = "UPDATE chats SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, LocalDateTime.now());
            stmt.setString(2, chatId);
            int updatedRows = stmt.executeUpdate();
            
            if (updatedRows > 0) {
                // Move files to trash
                moveFilesToTrash(chatId);
                
                if (config != null && config.getLoggingEnabled()) {
                    logger.info("Soft deleted chat: {} (moved to trash)", chatId);
                }
                return true;
            }
            return false;
        }
    }
    
    /**
     * Restores a soft-deleted chat by clearing the deleted_at timestamp.
     * Files are restored from trash back to active directories.
     * 
     * @param chatId the chat ID to restore
     * @return true if chat was restored
     * @throws SQLException if database operation fails
     */
    public boolean restoreChat(String chatId) throws SQLException {
        String sql = "UPDATE chats SET deleted_at = NULL WHERE id = ? AND deleted_at IS NOT NULL";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, chatId);
            int updatedRows = stmt.executeUpdate();
            
            if (updatedRows > 0) {
                // Restore files from trash
                restoreFilesFromTrash(chatId);
                
                if (config != null && config.getLoggingEnabled()) {
                    logger.info("Restored chat: {} (files restored from trash)", chatId);
                }
                return true;
            }
            return false;
        }
    }
    
    /**
     * Moves all images and thumbnails for a chat to the trash directory.
     */
    private void moveFilesToTrash(String chatId) {
        try {
            chatFiles.moveToTrash(chatId, findAllGeneratedImagesForChat(chatId));
        } catch (Exception e) {
            logger.error("Error moving files to trash for chat: {}", chatId, e);
        }
    }

    /**
     * Restores all images and thumbnails for a chat from the trash directory.
     * Only restores images that are not individually deleted.
     */
    private void restoreFilesFromTrash(String chatId) {
        try {
            chatFiles.restoreFromTrash(chatId, findAllNonDeletedImagesForChat(chatId));
        } catch (Exception e) {
            logger.error("Error restoring files from trash for chat: {}", chatId, e);
        }
    }

    /**
     * Permanently deletes a chat and all its associated data.
     * This is called when the chat expires from trash after 60 days.
     * 
     * @param chatId the chat ID to delete
     * @return true if chat was deleted
     * @throws SQLException if database operation fails
     */
    public boolean deleteChatPermanently(String chatId) throws SQLException {
        if (config != null && config.getLoggingEnabled()) {
            logger.info("Starting permanent deletion of chat: {}", chatId);
        }

        boolean filesDeletionSuccessful = true;

        // First, get all generated images for this chat to delete their files
        try {
            filesDeletionSuccessful = chatFiles.deletePermanently(chatId, findAllGeneratedImagesForChat(chatId));
        } catch (Exception e) {
            logger.error("Error deleting files for chat: {}", chatId, e);
            filesDeletionSuccessful = false;
        }

        // Delete from database (this will cascade to entries and images due to foreign
        // keys)
        String sql = "DELETE FROM chats WHERE id = ?";
        boolean databaseDeletionSuccessful = false;

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, chatId);
            int deletedRows = stmt.executeUpdate();

            if (deletedRows > 0) {
                if (config != null && config.getLoggingEnabled()) {
                    logger.info("Permanently deleted chat from database: {}", chatId);
                }
                databaseDeletionSuccessful = true;
                
                // Log warning if some files couldn't be deleted but database deletion succeeded
                if (!filesDeletionSuccessful) {
                    logger.warn("Chat {} successfully deleted from database, but some image files could not be removed from disk", chatId);
                }
            } else {
                logger.warn("No chat found to delete with ID: {}", chatId);
            }
        }

        // Return success based on database deletion - files are secondary
        return databaseDeletionSuccessful;
    }

    /**
     * Finds all generated images for a specific chat (across all entries).
     * 
     * @param chatId the chat ID
     * @return list of all generated images for the chat
     * @throws SQLException if query fails
     */
    private List<GeneratedImage> findAllGeneratedImagesForChat(String chatId) throws SQLException {
        String sql = """
                SELECT gi.* FROM generated_images gi
                JOIN chat_entries ce ON gi.chat_entry_id = ce.id
                WHERE ce.chat_id = ?
                ORDER BY gi.created_at
                """;

        List<GeneratedImage> images = new ArrayList<>();

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

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
     * @param chatId the chat ID
     * @return list of non-deleted generated images for the chat
     * @throws SQLException if query fails
     */
    private List<GeneratedImage> findAllNonDeletedImagesForChat(String chatId) throws SQLException {
        String sql = """
                SELECT gi.* FROM generated_images gi
                JOIN chat_entries ce ON gi.chat_entry_id = ce.id
                WHERE ce.chat_id = ? AND gi.deleted_at IS NULL
                ORDER BY gi.created_at
                """;

        List<GeneratedImage> images = new ArrayList<>();

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

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
        if (findChatEntryById(entry.getId()).isPresent()) {
            updateChatEntry(entry);
        } else {
            insertChatEntry(entry);
        }
    }

    /**
     * Inserts a new chat entry into the database.
     * 
     * @param entry the chat entry to insert
     * @throws SQLException if insert fails
     */
    private void insertChatEntry(ChatEntry entry) throws SQLException {
        String sql = """
                INSERT INTO chat_entries (
                    id, chat_id, user_prompt, enhanced_prompt, status, error_message,
                    created_at, completed_at, sort_order, model, size, format, style,
                    prompt_upsampling, steps, guidance, seed, cost, mood_json, mood_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

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

            stmt.executeUpdate();
        }
    }

    /**
     * Updates an existing chat entry in the database.
     * 
     * @param entry the chat entry to update
     * @throws SQLException if update fails
     */
    private void updateChatEntry(ChatEntry entry) throws SQLException {
        String sql = """
                UPDATE chat_entries SET
                    user_prompt = ?, enhanced_prompt = ?, status = ?, error_message = ?,
                    completed_at = ?, sort_order = ?, model = ?, size = ?, format = ?, style = ?,
                    prompt_upsampling = ?, steps = ?, guidance = ?, seed = ?, cost = ?, mood_json = ?, mood_id = ?
                WHERE id = ?
                """;

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

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
            stmt.setString(18, entry.getId());

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
        String sql = "SELECT * FROM chat_entries WHERE id = ?";

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, entryId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToChatEntry(rs));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Finds all chat entries for a specific chat.
     * 
     * @param chatId the chat ID
     * @return list of chat entries
     * @throws SQLException if query fails
     */
    public List<ChatEntry> findChatEntriesByChat(String chatId) throws SQLException {
        return findChatEntriesByChatId(chatId);
    }
    
    /**
     * Finds all chat entries for a specific chat (alternative method name).
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
        String condition = switch (group.kind()) {
            case MOOD -> "ce.mood_id = ?";
            case CUSTOM -> "ce.mood_id IS NULL AND ce.mood_json IS NOT NULL";
            case NONE -> "ce.mood_json IS NULL";
        };
        String sql = "SELECT ce.id FROM chat_entries ce JOIN chats c ON c.id = ce.chat_id "
                + "WHERE ce.deleted_at IS NULL AND c.deleted_at IS NULL AND " + condition + " ORDER BY ce.created_at";

        List<String> ids = new ArrayList<>();

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (group.kind() == MoodGroup.Kind.MOOD)
                stmt.setString(1, group.moodId());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next())
                    ids.add(rs.getString("id"));
            }
        }

        return ids;
    }

    // ========== Generated Image Operations ==========

    /**
     * Checks if a chat entry exists and is not deleted.
     * 
     * @param entryId the entry ID to check
     * @return true if exists and not deleted
     * @throws SQLException if query fails
     */
    private boolean chatEntryExistsAndActive(String entryId) throws SQLException {
        if (entryId == null) {
            return false;
        }
        
        String sql = "SELECT id FROM chat_entries WHERE id = ? AND deleted_at IS NULL";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, entryId);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }

    /**
     * Saves a generated image to the database.
     * 
     * @param image the image to save
     * @throws SQLException if database operation fails
     */
    public void saveGeneratedImage(GeneratedImage image) throws SQLException {
        // First check if the chat entry exists and is not deleted
        if (!chatEntryExistsAndActive(image.getChatEntryId())) {
            logger.warn("Cannot save image {} - chat entry {} is deleted or does not exist", 
                image.getId(), image.getChatEntryId());
            return;
        }
        
        if (findGeneratedImageById(image.getId()).isPresent()) {
            updateGeneratedImage(image);
        } else {
            insertGeneratedImage(image);
        }
    }

    /**
     * Inserts a new generated image into the database.
     * 
     * @param image the image to insert
     * @throws SQLException if insert fails
     */
    private void insertGeneratedImage(GeneratedImage image) throws SQLException {
        // Try without the extra columns that might be causing issues
        String sql = """
                INSERT INTO generated_images (
                    id, chat_entry_id, image_url, local_file_path, file_name,
                    download_status, created_at, downloaded_at, file_size_bytes,
                    image_width, image_height, mime_type, sort_order,
                    generation_job_id, prompt, model, seed, prompt_upsampling,
                    base_cost, search_cost, reasoning_cost, thinking_tokens, thought_summary,
                    is_video, video_duration_seconds, video_resolution, has_audio, negative_prompt,
                    output_format
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        Connection conn = null;
        PreparedStatement stmt = null;
        try {
            conn = databaseManager.getConnection();
            
            // Temporarily disable foreign keys for this connection
            try (Statement s = conn.createStatement()) {
                s.execute("PRAGMA foreign_keys = OFF");
            }
            
            stmt = conn.prepareStatement(sql);

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

            stmt.executeUpdate();
            
            // Re-enable foreign keys
            try (Statement s = conn.createStatement()) {
                s.execute("PRAGMA foreign_keys = ON");
            }
        } finally {
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        }
    }

    /**
     * Updates an existing generated image in the database.
     * 
     * @param image the image to update
     * @throws SQLException if update fails
     */
    private void updateGeneratedImage(GeneratedImage image) throws SQLException {
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

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

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
     * Finds all generated images for a specific chat entry.
     * 
     * @param chatEntryId the chat entry ID
     * @return list of generated images
     * @throws SQLException if query fails
     */
    public List<GeneratedImage> findGeneratedImagesByEntry(String chatEntryId) throws SQLException {
        String sql = "SELECT * FROM generated_images WHERE chat_entry_id = ? AND deleted_at IS NULL ORDER BY sort_order, created_at";

        List<GeneratedImage> images = new ArrayList<>();

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

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
    private Chat mapRowToChat(ResultSet rs) throws SQLException {
        Chat.Builder builder = new Chat.Builder()
                .id(rs.getString("id"))
                .name(rs.getString("name"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .sortOrder(rs.getInt("sort_order"))
                .deletedCost(rs.getDouble("deleted_cost"));

        // Handle deleted_at with more flexible parsing
        try {
            String deletedAtStr = rs.getString("deleted_at");
            if (deletedAtStr != null && !rs.wasNull()) {
                // Parse ISO-8601 format directly
                builder.deletedAt(LocalDateTime.parse(deletedAtStr.replace(" ", "T")));
            }
        } catch (Exception e) {
            // Fallback to timestamp parsing if string parsing fails
            try {
                Timestamp deletedAt = rs.getTimestamp("deleted_at");
                if (deletedAt != null) {
                    builder.deletedAt(deletedAt.toLocalDateTime());
                }
            } catch (Exception ex) {
                // Log but don't fail - deleted_at is optional
                logger.debug("Could not parse deleted_at timestamp", ex);
            }
        }

        return builder.build();
    }

    /**
     * Maps a database row to a ChatEntry object.
     * 
     * @param rs the result set
     * @return the mapped chat entry
     * @throws SQLException if mapping fails
     */
    private ChatEntry mapRowToChatEntry(ResultSet rs) throws SQLException {
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
                .promptUpsampling(rs.getBoolean("prompt_upsampling"))
                .safetyTolerance((int) rs.getDouble("guidance")) // repurposed guidance column
                .seed(rs.getLong("seed"))
                .cost(rs.getDouble("cost"))
                .deletedCost(rs.getDouble("deleted_cost"));

        Timestamp completedAt = rs.getTimestamp("completed_at");
        if (completedAt != null) {
            builder.completedAt(completedAt.toLocalDateTime());
        }
        
        // Handle deleted_at with more flexible parsing
        try {
            String deletedAtStr = rs.getString("deleted_at");
            if (deletedAtStr != null && !rs.wasNull()) {
                // Parse ISO-8601 format directly
                builder.deletedAt(LocalDateTime.parse(deletedAtStr.replace(" ", "T")));
            }
        } catch (Exception e) {
            // Fallback to timestamp parsing if string parsing fails
            try {
                Timestamp deletedAt = rs.getTimestamp("deleted_at");
                if (deletedAt != null) {
                    builder.deletedAt(deletedAt.toLocalDateTime());
                }
            } catch (Exception ex) {
                // Log but don't fail - deleted_at is optional
                logger.debug("Could not parse deleted_at timestamp", ex);
            }
        }

        return builder.build();
    }

    /**
     * Maps a database row to a GeneratedImage object.
     * 
     * @param rs the result set
     * @return the mapped generated image
     * @throws SQLException if mapping fails
     */
    private GeneratedImage mapRowToGeneratedImage(ResultSet rs) throws SQLException {
        GeneratedImage.Builder builder = new GeneratedImage.Builder()
                .id(rs.getString("id"))
                .chatEntryId(rs.getString("chat_entry_id"))
                .imageUrl(rs.getString("image_url"))
                .localFilePath(rs.getString("local_file_path"))
                .fileName(rs.getString("file_name"))
                .downloadStatus(GeneratedImage.DownloadStatus.valueOf(rs.getString("download_status")))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .fileSizeBytes(rs.getLong("file_size_bytes"))
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
                .stylePreset(rs.getString("style_preset"))
                .width(rs.getInt("width"))
                .height(rs.getInt("height"));

        // Prefer the actually delivered mime_type over the requested output_format,
        // so the UI always shows what the API really returned
        String storedMimeType = rs.getString("mime_type");
        if (storedMimeType == null || storedMimeType.isEmpty()) {
            String outputFormat = rs.getString("output_format");
            if (outputFormat != null) builder.format(outputFormat);
        }

        // Gemini Phase 2 cost tracking - safe column access with fallback
        try {
            builder.baseCost(rs.getDouble("base_cost"));
        } catch (SQLException e) {
            // Column doesn't exist yet, use default
            builder.baseCost(0.0);
        }

        try {
            builder.searchCost(rs.getDouble("search_cost"));
        } catch (SQLException e) {
            // Column doesn't exist yet, use default
            builder.searchCost(0.0);
        }

        try {
            builder.reasoningCost(rs.getDouble("reasoning_cost"));
        } catch (SQLException e) {
            builder.reasoningCost(0.0);
        }

        try {
            builder.thinkingTokens((Integer) rs.getObject("thinking_tokens"));
        } catch (SQLException e) {
            builder.thinkingTokens(null);
        }

        try {
            builder.thoughtSummary(rs.getString("thought_summary"));
        } catch (SQLException e) {
            builder.thoughtSummary(null);
        }

        // video fields
        try {
            builder.isVideo(rs.getBoolean("is_video"));
        } catch (SQLException e) {
            builder.isVideo(false);
        }
        try {
            builder.videoDurationSeconds(rs.getInt("video_duration_seconds"));
        } catch (SQLException e) {
            builder.videoDurationSeconds(0);
        }
        try {
            builder.videoResolution(rs.getString("video_resolution"));
        } catch (SQLException e) {
            builder.videoResolution(null);
        }
        try {
            builder.hasAudio(rs.getBoolean("has_audio"));
        } catch (SQLException e) {
            builder.hasAudio(false);
        }

        Timestamp downloadedAt = rs.getTimestamp("downloaded_at");
        if (downloadedAt != null) {
            builder.downloadedAt(downloadedAt.toLocalDateTime());
        }

        return builder.build();
    }
    
    // ============= SOFT DELETE METHODS =============
    
    /**
     * Soft deletes a single image and moves it to trash.
     * Updates the entry's deleted_cost.
     * 
     * @param imageId the image ID to delete
     * @return true if successfully deleted
     */
    public boolean softDeleteImage(String imageId) throws SQLException {
        logger.info("Soft deleting image: {}", imageId);
        
        // Get the image details first
        GeneratedImage image = findGeneratedImageByIdInternal(imageId);
        if (image == null) {
            logger.warn("Image not found for soft delete: {}", imageId);
            return false;
        }
        
        // Calculate the cost of this image
        double imageCost = calculateImageCost(image);
        
        // Start transaction
        Connection conn = databaseManager.getConnection();
        try {
            conn.setAutoCommit(false);
            
            // 1. Mark image as deleted
            String sqlImage = """
                UPDATE generated_images 
                SET deleted_at = CURRENT_TIMESTAMP,
                    deleted_cost = ?,
                    is_in_trash = TRUE
                WHERE id = ?
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sqlImage)) {
                stmt.setDouble(1, imageCost);
                stmt.setString(2, imageId);
                stmt.executeUpdate();
            }
            
            // 2. Update entry's deleted_cost
            String sqlEntry = """
                UPDATE chat_entries 
                SET deleted_cost = deleted_cost + ?
                WHERE id = ?
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sqlEntry)) {
                stmt.setDouble(1, imageCost);
                stmt.setString(2, image.getChatEntryId());
                stmt.executeUpdate();
            }
            
            // 3. Check if all images in entry are deleted
            String sqlCheck = """
                SELECT COUNT(*) as active_count
                FROM generated_images
                WHERE chat_entry_id = ? AND deleted_at IS NULL
                """;
            
            int activeCount = 0;
            try (PreparedStatement stmt = conn.prepareStatement(sqlCheck)) {
                stmt.setString(1, image.getChatEntryId());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    activeCount = rs.getInt("active_count");
                }
            }
            
            // 4. If no active images left, mark entry as deleted
            if (activeCount == 0) {
                String sqlDeleteEntry = """
                    UPDATE chat_entries
                    SET deleted_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """;
                
                try (PreparedStatement stmt = conn.prepareStatement(sqlDeleteEntry)) {
                    stmt.setString(1, image.getChatEntryId());
                    stmt.executeUpdate();
                }
            }
            
            // NOTE: We do NOT update the chat's deleted_cost here!
            // Chat.deleted_cost only tracks fully deleted entries, not individual deleted images.
            // Individual deleted images are tracked in chat_entries.deleted_cost
            
            conn.commit();
            logger.info("Successfully soft deleted image: {} (cost: {})", imageId, imageCost);
            return true;
            
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
            conn.close();
        }
    }
    
    /**
     * Soft deletes a chat entry and all its images.
     * 
     * @param entryId the entry ID to delete
     * @return true if successfully deleted
     */
    public boolean softDeleteChatEntry(String entryId) throws SQLException {
        logger.info("Soft deleting chat entry: {}", entryId);
        
        Connection conn = databaseManager.getConnection();
        try {
            conn.setAutoCommit(false);
            
            // Check if entry is already deleted
            String checkSql = "SELECT deleted_at FROM chat_entries WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                stmt.setString(1, entryId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next() && rs.getTimestamp("deleted_at") != null) {
                    logger.info("Entry {} is already deleted, skipping", entryId);
                    conn.rollback();
                    return false;  // Already deleted
                }
            }
            
            // 1. Get all images for this entry
            List<GeneratedImage> images = findGeneratedImagesByEntryId(entryId);
            double totalCost = 0.0;
            
            // 2. Mark all images as deleted
            String sqlImages = """
                UPDATE generated_images
                SET deleted_at = CURRENT_TIMESTAMP,
                    is_in_trash = TRUE
                WHERE chat_entry_id = ? AND deleted_at IS NULL
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sqlImages)) {
                stmt.setString(1, entryId);
                stmt.executeUpdate();
            }
            
            // 3. Calculate cost of ACTIVE (non-deleted) images only
            // Images already marked as deleted should not be counted again
            for (GeneratedImage image : images) {
                // Only count images that are not already in trash
                if (!image.isInTrash()) {
                    totalCost += calculateImageCost(image);
                }
            }
            
            // 4. Mark entry as deleted and set deleted_cost to the entry's total cost
            // Since all images are now deleted, deleted_cost should equal the total cost
            String sqlEntry = """
                UPDATE chat_entries
                SET deleted_at = CURRENT_TIMESTAMP,
                    deleted_cost = cost
                WHERE id = ?
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sqlEntry)) {
                stmt.setString(1, entryId);
                stmt.executeUpdate();
            }
            
            // 5. Update chat's deleted_cost
            String sqlChat = """
                UPDATE chats
                SET deleted_cost = deleted_cost + ?
                WHERE id = (SELECT chat_id FROM chat_entries WHERE id = ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sqlChat)) {
                stmt.setDouble(1, totalCost);
                stmt.setString(2, entryId);
                stmt.executeUpdate();
            }
            
            conn.commit();
            logger.info("Successfully soft deleted entry: {} with {} images (total cost: {})", 
                entryId, images.size(), totalCost);
            return true;
            
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
            conn.close();
        }
    }
    
    /**
     * Finds generated images by entry ID for soft delete operations.
     * 
     * @param entryId the entry ID
     * @return list of generated images
     * @throws SQLException if query fails
     */
    public List<GeneratedImage> findGeneratedImagesByEntryId(String entryId) throws SQLException {
        return findGeneratedImagesByEntry(entryId);
    }
    
    /**
     * Finds a generated image by ID.
     * 
     * @param imageId the image ID
     * @return optional containing the image if found
     * @throws SQLException if query fails
     */
    public Optional<GeneratedImage> findGeneratedImageById(String imageId) throws SQLException {
        GeneratedImage image = findGeneratedImageByIdInternal(imageId);
        return image != null ? Optional.of(image) : Optional.empty();
    }
    
    /**
     * Finds a generated image by ID (internal method).
     */
    private GeneratedImage findGeneratedImageByIdInternal(String imageId) throws SQLException {
        String sql = "SELECT * FROM generated_images WHERE id = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, imageId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapRowToGeneratedImage(rs);
            }
        }
        
        return null;
    }
    
    /**
     * Calculates the cost of a single image based on model pricing.
     */
    private double calculateImageCost(GeneratedImage image) {
        String model = image.getModel();
        if (model == null) {
            return 0.0;
        }
        
        // Get pricing from model
        return switch (model.toLowerCase()) {
            case "flux.1-dev", "flux-dev" -> 0.025;
            case "flux.1-pro", "flux-pro" -> 0.04;
            case "flux.1.1-pro" -> 0.04;
            case "flux.1.1-pro-ultra" -> 0.06;
            case "flux.1-kontext-pro" -> 0.04;
            case "flux.1-kontext-max" -> 0.08;
            default -> 0.025; // Default price
        };
    }
    
    /**
     * Marks an image as being in trash (for migration).
     */
    public void markImageAsInTrash(String imageId) throws SQLException {
        String sql = "UPDATE generated_images SET is_in_trash = TRUE WHERE id = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, imageId);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Checks if there are deleted chats with images not in trash.
     */
    public boolean hasDeletedChatsNotInTrash() throws SQLException {
        String sql = """
            SELECT COUNT(*) as count
            FROM generated_images gi
            JOIN chat_entries ce ON gi.chat_entry_id = ce.id
            JOIN chats c ON ce.chat_id = c.id
            WHERE c.deleted_at IS NOT NULL
            AND gi.is_in_trash = FALSE
            """;
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count") > 0;
            }
        }
        
        return false;
    }
    
    /**
     * Gets the deleted cost for a chat entry.
     */
    public double getDeletedCostForEntry(String entryId) throws SQLException {
        String sql = "SELECT deleted_cost FROM chat_entries WHERE id = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, entryId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getDouble("deleted_cost");
            }
        }
        
        return 0.0;
    }
}