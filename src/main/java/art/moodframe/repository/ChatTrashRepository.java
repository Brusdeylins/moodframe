package art.moodframe.repository;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.ChatCost;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static art.moodframe.repository.ChatRepository.entryPlace;
import static art.moodframe.repository.ChatRepository.findChatEntryById;
import static art.moodframe.repository.ChatRepository.findGeneratedImageById;
import static art.moodframe.repository.ChatRepository.findGeneratedImagesByEntry;
import static art.moodframe.repository.ChatRepository.mapRowToChatEntry;
import static art.moodframe.repository.ChatRepository.mapRowToGeneratedImage;
import static art.moodframe.repository.ChatRepository.now;

/**
 * The trash of the chats, their entries and their images: soft delete, restore and removal for good, with the
 * booking of the deleted costs (see {@link ChatCost}).
 *
 * <p>It is the one owner of the files of these deletions: each write reads what it changes inside its transaction,
 * guards its update ({@code AND deleted_at IS NULL}) and hands {@link ChatFiles} the files that follow the rows it
 * changed after the commit (of a chat the images in use, as those deleted on their own lie in the trash already); the
 * file of an image a generation delivered after its entry or chat went to the trash, which no deletion moved, it
 * removes as well ({@link #dropLateImage}). The writes run one after the other, each with the moves of its files, so
 * the files follow the commits in their order. A file that does not move is logged, the rows stay as committed.</p>
 *
 * @since 2.1.3
 */
public class ChatTrashRepository {

    private static final Logger logger = LoggerFactory.getLogger(ChatTrashRepository.class);

    private final DatabaseManager databaseManager;
    private final ChatFiles chatFiles;

    /*  Every write runs its transaction and the moves of its files under this lock, so the files follow the rows in
        the order of the commits: a deletion and an immediate restore from another thread never move them the other
        way round  */
    private final ReentrantLock writes = new ReentrantLock(true);

    /**
     * Creates the trash on the database, handing the files to the given handler.
     *
     * @param databaseManager the database of the chats
     * @param chatFiles       moves, restores and removes the files of chats, entries and images
     */
    public ChatTrashRepository(DatabaseManager databaseManager, ChatFiles chatFiles) {
        this.databaseManager = databaseManager;
        this.chatFiles = chatFiles;
    }

    /**
     * Soft deletes a chat by setting the deleted_at timestamp. After the commit the files of its images in use move to
     * the trash (those deleted on their own are there already); a file that does not move is logged, the chat is in
     * the trash anyway.
     *
     * @param chatId the chat ID to soft delete
     * @return true if chat was marked as deleted
     * @throws SQLException if database operation fails
     */
    public boolean softDeleteChat(String chatId) throws SQLException {
        writes.lock();
        try {
            List<GeneratedImage> images = databaseManager.inTransaction((Connection conn) -> {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE chats SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL")) {
                    stmt.setTimestamp(1, now());
                    stmt.setString(2, chatId);
                    if (stmt.executeUpdate() == 0)
                        return null;
                }
                return findAllNonDeletedImagesForChat(conn, chatId);
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
        finally {
            writes.unlock();
        }
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
        writes.lock();
        try {
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
        finally {
            writes.unlock();
        }
    }

    /**
     * Permanently deletes a chat in the trash and all its associated data, e.g. when it expires from the trash. The
     * rows go first, in one transaction (cascading to entries and images); the files follow after the commit, as a
     * file left over is harmless and a row without its file is not. A chat in use, e.g. restored meanwhile, stays.
     *
     * @param chatId the chat ID to delete
     * @return true if the chat was in the trash and is deleted
     * @throws SQLException if database operation fails
     */
    public boolean deleteChatPermanently(String chatId) throws SQLException {
        writes.lock();
        try {
            logger.debug("Starting permanent deletion of chat: {}", chatId);

            List<GeneratedImage> images = databaseManager.inTransaction((Connection conn) -> {
                List<GeneratedImage> all = findAllGeneratedImagesForChat(conn, chatId);
                try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM chats WHERE id = ? AND deleted_at IS NOT NULL")) {
                    stmt.setString(1, chatId);
                    return stmt.executeUpdate() > 0 ? all : null;
                }
            });
            if (images == null) {
                logger.warn("No chat in the trash to delete with ID: {}", chatId);
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
        finally {
            writes.unlock();
        }
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

    /*  The rows a write changed, with the chat they belong to, whose files follow after the commit  */
    private record Changed(String chatId, List<GeneratedImage> images) {
    }

    /*  Hands the files of committed rows to the handler; a failure is logged, the rows stay as committed  */
    private void files(String what, String id, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            logger.error("{} {}: the database is changed, but the files could not follow", what, id, e);
        }
    }

    /**
     * Soft deletes a single image and moves its file to the trash after the commit.
     * The entry's deleted_cost grows by the image's share of the entry cost (see
     * {@link ChatCost#imageShare(ChatEntry, List, GeneratedImage)});
     * with its last image the entry is deleted, and its cost goes to the chat's deleted_cost. Reading the costs and
     * booking them is one transaction, so of two deletions at the same time (main window and agent) one books.
     *
     * @param imageId the image ID to delete
     * @return true if successfully deleted, false if it is not stored or already deleted
     * @throws SQLException if database operation fails
     */
    public boolean softDeleteImage(String imageId) throws SQLException {
        writes.lock();
        try {
            logger.info("Soft deleting image: {}", imageId);
            Timestamp deletedAt = now();

            Changed deleted = databaseManager.inTransaction((Connection conn) -> {
                GeneratedImage image = findGeneratedImageById(conn, imageId).orElse(null);
                if (image == null || image.getDeletedAt() != null)
                    return null;

                // The image's share of what its entry cost, less the images deleted before
                ChatEntry entry = findChatEntryById(conn, image.getChatEntryId()).orElse(null);
                double cost = entry != null ? ChatCost.imageShare(entry,
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
                String sqlCheck =
                        "SELECT COUNT(*) FROM generated_images WHERE chat_entry_id = ? AND deleted_at IS NULL";
                try (PreparedStatement stmt = conn.prepareStatement(sqlCheck)) {
                    stmt.setString(1, image.getChatEntryId());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) == 0)
                            markEntryDeleted(conn, image.getChatEntryId(), deletedAt);
                    }
                }
                return new Changed(entry != null ? entry.getChatId() : null, List.of(image));
            });

            if (deleted == null) {
                logger.warn("Image not found or already deleted for soft delete: {}", imageId);
                return false;
            }
            logger.info("Successfully soft deleted image: {}", imageId);
            files("Soft deleted image", imageId, () -> chatFiles.moveToTrash(deleted.chatId(), deleted.images()));
            return true;
        }
        finally {
            writes.unlock();
        }
    }

    /**
     * Soft deletes a chat entry and all its images; the files of the images it deleted move to the trash after the
     * commit (those deleted on their own before are there already).
     *
     * @param entryId the entry ID to delete
     * @return true if successfully deleted, false if it is not stored or already deleted
     * @throws SQLException if database operation fails
     */
    public boolean softDeleteChatEntry(String entryId) throws SQLException {
        writes.lock();
        try {
            logger.info("Soft deleting chat entry: {}", entryId);
            Timestamp deletedAt = now();

            Changed deleted = databaseManager.inTransaction((Connection conn) -> {
                ChatEntry entry = findChatEntryById(conn, entryId).orElse(null);
                List<GeneratedImage> images = findGeneratedImagesByEntry(conn, entryId);

                // 1. Mark entry as deleted; its whole cost goes to the chat
                if (entry == null || !markEntryDeleted(conn, entryId, deletedAt))
                    return null;

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
                return new Changed(entry.getChatId(), images);
            });

            if (deleted == null) {
                logger.info("Entry {} is not stored or already deleted, skipping", entryId);
                return false;
            }
            logger.info("Successfully soft deleted entry: {}", entryId);
            files("Soft deleted entry", entryId, () -> chatFiles.moveToTrash(deleted.chatId(), deleted.images()));
            return true;
        }
        finally {
            writes.unlock();
        }
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
     * Removes the file and thumbnail of an image a generation delivered after its entry or chat went to the trash,
     * which {@link ChatRepository#saveGeneratedImage} did not save: the file was written after the deletion, so no
     * deletion moved it out of the folder of the images in use (a row of the image in the trash, e.g. the placeholder
     * a cancellation stored, does not know it). It runs after the writes before, like every write of the trash, and
     * only while the entry or chat is still in the trash; an entry restored meanwhile keeps the file, which is logged.
     *
     * @param image the image that was not saved
     * @return true if its files were handed over for removal
     * @throws SQLException if database operation fails
     */
    public boolean dropLateImage(GeneratedImage image) throws SQLException {
        writes.lock();
        try {
            boolean dropped;
            try (Connection conn = databaseManager.getConnection()) {
                dropped = entryPlace(conn, image.getChatEntryId(), true) == ChatRepository.EntryPlace.IN_TRASH;
            }
            if (!dropped) {
                logger.warn("Image {} of entry {} was not saved, but its entry is no longer in the trash: its file "
                        + "stays", image.getId(), image.getChatEntryId());
                return false;
            }
            if (image.getLocalFilePath() != null)
                files("Dropped image", image.getId(), () -> chatFiles.removeDropped(List.of(image)));
            return true;
        }
        finally {
            writes.unlock();
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

    /*  the deleted images of an entry  */
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
     * gets back the images it lost. Their files come back from the trash after the commit.
     *
     * @param entryId the entry
     * @return true if the entry or some of its images came back
     * @throws SQLException if database operation fails
     */
    public boolean restoreChatEntry(String entryId) throws SQLException {
        writes.lock();
        try {
            Changed restored = databaseManager.inTransaction((Connection conn) -> {
                List<GeneratedImage> images = findDeletedImagesByEntry(conn, entryId);
                String chatId = findChatEntryById(conn, entryId).map(ChatEntry::getChatId).orElse(null);
                return restoreImages(conn, entryId, null) ? new Changed(chatId, images) : null;
            });
            if (restored == null)
                return false;
            files("Restored entry", entryId, () -> chatFiles.restoreFromTrash(restored.chatId(), restored.images()));
            return true;
        }
        finally {
            writes.unlock();
        }
    }

    /**
     * Takes a single deleted image out of the trash; a deleted entry comes back with it, its other images stay in
     * the trash. Its file comes back from the trash after the commit.
     *
     * @param imageId the image
     * @return true if the image came back
     * @throws SQLException if database operation fails
     */
    public boolean restoreImage(String imageId) throws SQLException {
        writes.lock();
        try {
            Changed restored = databaseManager.inTransaction((Connection conn) -> {
                GeneratedImage image = findGeneratedImageById(conn, imageId).orElse(null);
                if (image == null || !restoreImages(conn, image.getChatEntryId(), imageId))
                    return null;
                return new Changed(findChatEntryById(conn, image.getChatEntryId()).map(ChatEntry::getChatId)
                        .orElse(null), List.of(image));
            });
            if (restored == null)
                return false;
            files("Restored image", imageId, () -> chatFiles.restoreFromTrash(restored.chatId(), restored.images()));
            return true;
        }
        finally {
            writes.unlock();
        }
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
     * The costs stay as they are, as the images were paid for. The files go after the commit, as a file left over is
     * harmless and a row without its file is not.
     *
     * @param entryId the entry
     * @return true if something was removed
     * @throws SQLException if database operation fails
     */
    public boolean deleteChatEntryPermanently(String entryId) throws SQLException {
        writes.lock();
        try {
            Changed removed = databaseManager.inTransaction((Connection conn) -> {
                List<GeneratedImage> deleted = findDeletedImagesByEntry(conn, entryId);
                try (PreparedStatement images = conn.prepareStatement(
                            "DELETE FROM generated_images WHERE chat_entry_id = ? AND deleted_at IS NOT NULL");
                        PreparedStatement entry = conn.prepareStatement(
                            "DELETE FROM chat_entries WHERE id = ? AND deleted_at IS NOT NULL")) {
                    images.setString(1, entryId);
                    entry.setString(1, entryId);
                    return images.executeUpdate() + entry.executeUpdate() > 0 ? new Changed(null, deleted) : null;
                }
            });
            if (removed == null)
                return false;
            files("Removed entry", entryId, () -> chatFiles.deletePermanently(null, removed.images()));
            return true;
        }
        finally {
            writes.unlock();
        }
    }

    /**
     * Removes a deleted image for good; a deleted entry goes with its last image. The costs stay as they are, as
     * the image was paid for. Its files go after the commit.
     *
     * @param imageId the image
     * @return true if the image was removed
     * @throws SQLException if database operation fails
     */
    public boolean deleteImagePermanently(String imageId) throws SQLException {
        writes.lock();
        try {
            Changed removed = databaseManager.inTransaction((Connection conn) -> {
                GeneratedImage image = findGeneratedImageById(conn, imageId).orElse(null);
                if (image == null)
                    return null;

                try (PreparedStatement deleteImage = conn.prepareStatement(
                            "DELETE FROM generated_images WHERE id = ? AND deleted_at IS NOT NULL");
                        PreparedStatement deleteEntry = conn.prepareStatement(
                            "DELETE FROM chat_entries WHERE id = ? AND deleted_at IS NOT NULL "
                            + "AND NOT EXISTS (SELECT 1 FROM generated_images WHERE chat_entry_id = ?)")) {
                    deleteImage.setString(1, imageId);
                    if (deleteImage.executeUpdate() == 0)
                        return null;

                    deleteEntry.setString(1, image.getChatEntryId());
                    deleteEntry.setString(2, image.getChatEntryId());
                    deleteEntry.executeUpdate();
                    return new Changed(null, List.of(image));
                }
            });
            if (removed == null)
                return false;
            files("Removed image", imageId, () -> chatFiles.deletePermanently(null, removed.images()));
            return true;
        }
        finally {
            writes.unlock();
        }
    }
}
