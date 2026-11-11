package art.moodframe.service;

import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.repository.ChatFiles;
import art.moodframe.repository.ChatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Loads the chats with their entries and images from the database and settles what a restart left behind:
 * requests that were still running, images whose files are gone, entries without images. The settled state
 * is written back, so the repair happens once.
 *
 * @since 1.9.1
 */
public class ChatHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(ChatHistoryService.class);

    /**
     * The loaded chats.
     *
     * @param chats         all active chats and the deleted ones that still hold entries (hidden until restored)
     * @param nothingStored true if the database held no chat at all
     */
    public record History(List<Chat> chats, boolean nothingStored) {
    }

    /**
     * The result of deleting one image.
     *
     * @param entryDeleted     true if it was the last image, so the whole entry is deleted too
     * @param entryDeletedCost the cost of all deleted images of the entry
     */
    public record ImageDeletion(boolean entryDeleted, double entryDeletedCost) {
    }

    private final ChatRepository chatRepository;
    private final TrashManager trashManager;
    private final ChatFiles chatFiles;

    /**
     * Creates the service.
     *
     * @param chatRepository where chats are stored
     * @param trashManager   locates the files of deleted chats (may be null: then only the original place counts)
     * @param chatFiles      moves the files of deleted entries and images to the trash
     */
    public ChatHistoryService(ChatRepository chatRepository, TrashManager trashManager, ChatFiles chatFiles) {
        this.chatRepository = chatRepository;
        this.trashManager = trashManager;
        this.chatFiles = chatFiles != null ? chatFiles : ChatFiles.NONE;
    }

    /**
     * Moves the images of an entry to the trash and marks the entry as deleted.
     *
     * @param entryId the entry
     * @param images  its images
     * @throws SQLException if the entry cannot be marked
     */
    public void deleteEntry(String entryId, List<GeneratedImage> images) throws SQLException {
        chatFiles.moveToTrash(entryId, images);
        chatRepository.softDeleteChatEntry(entryId);
    }

    /**
     * Moves an image to the trash and marks it as deleted; the entry is deleted too when it was its last image.
     *
     * @param entryId the entry of the image
     * @param image   the image
     * @return what was deleted and the deleted cost of the entry now
     * @throws SQLException if the database cannot be updated
     */
    public ImageDeletion deleteImage(String entryId, GeneratedImage image) throws SQLException {
        chatFiles.moveToTrash(entryId, List.of(image));
        chatRepository.softDeleteImage(image.getId());

        boolean lastImage = chatRepository.findGeneratedImagesByEntry(entryId).stream()
                .allMatch(GeneratedImage::isInTrash);
        if (lastImage) {
            logger.info("All images deleted from entry {}, soft-deleting the entry", entryId);
            chatRepository.softDeleteChatEntry(entryId);
        }
        return new ImageDeletion(lastImage, chatRepository.getDeletedCostForEntry(entryId));
    }

    /**
     * Loads all chats from the database, once at the start: entries that are still pending or processing were
     * interrupted by the restart and are marked as failed.
     *
     * @return the chats to show
     * @throws java.sql.SQLException if the chats cannot be read
     */
    public History load() throws java.sql.SQLException {
        // Load ALL chats from database (both active and deleted)
        List<Chat> persistedChats = new ArrayList<>();
        persistedChats.addAll(chatRepository.findAllActiveChats());
        persistedChats.addAll(chatRepository.findDeletedChats());

        // Load chat entries and images for each chat
        for (Chat chat : persistedChats) {
            try {
                // Load chat entries for this chat (excludes soft-deleted entries)
                List<ChatEntry> chatEntries = chatRepository.findChatEntriesByChatId(chat.getId());
                chat.getEntries().clear();
                chat.getEntries().addAll(chatEntries);

                // OPTIMIZED: Load ALL images for ALL entries in ONE query (fixes N+1 problem)
                List<String> entryIds = chatEntries.stream()
                        .map(ChatEntry::getId)
                        .collect(Collectors.toList());
                Map<String, List<GeneratedImage>> imagesByEntry =
                        chatRepository.findImagesByEntryIds(entryIds);

                // Process each chat entry
                for (ChatEntry entry : chatEntries) {
                    // Fix unfinished requests after application restart
                    if (entry.getStatus() == ChatEntry.Status.PROCESSING
                            || entry.getStatus() == ChatEntry.Status.PENDING) {
                        logger.warn("Found {} entry after restart - marking as FAILED: {} ({})",
                                entry.getStatus(), entry.getId(), entry.getUserPrompt());
                        entry.setStatus(ChatEntry.Status.FAILED);
                        entry.setErrorMessage("Request was interrupted by application restart");
                        // Save the corrected status to database
                        try {
                            chatRepository.saveChatEntry(entry);
                        } catch (Exception e) {
                            logger.warn("Failed to save corrected status for entry {}", entry.getId(), e);
                        }
                    }

                    // Assign pre-loaded images to entry (from batch query)
                    List<GeneratedImage> images =
                            imagesByEntry.getOrDefault(entry.getId(), Collections.emptyList());
                    entry.getImages().clear();
                    entry.getImages().addAll(images);

                    // Fix image download status for failed entries after application restart
                    if (entry.getStatus() == ChatEntry.Status.FAILED) {
                        for (GeneratedImage image : entry.getImages()) {
                            if (image.getDownloadStatus() == GeneratedImage.DownloadStatus.PENDING ||
                                    image.getDownloadStatus() == GeneratedImage.DownloadStatus.DOWNLOADING) {
                                image.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
                                image.setErrorMessage("Request was interrupted by application restart");
                                // Save the corrected image status to database
                                try {
                                    chatRepository.saveGeneratedImage(image);
                                } catch (Exception e) {
                                    logger.warn("Failed to save corrected image status for image {}",
                                            image.getId(), e);
                                }
                            }
                        }
                    }

                    // Fix completed entries that have pending/downloading images after restart
                    if (entry.getStatus() == ChatEntry.Status.COMPLETED) {
                        for (GeneratedImage image : entry.getImages()) {
                            if (image.getDownloadStatus() == GeneratedImage.DownloadStatus.PENDING ||
                                    image.getDownloadStatus() == GeneratedImage.DownloadStatus.DOWNLOADING) {
                                image.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
                                image.setErrorMessage("Image download was interrupted by application restart");
                                // Save the corrected image status to database
                                try {
                                    chatRepository.saveGeneratedImage(image);
                                } catch (Exception e) {
                                    logger.warn("Failed to save corrected image status for image {}",
                                            image.getId(), e);
                                }
                            }

                            // Also check for cached images with missing files (check both active and trash)
                            if (image.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED) {
                                String actualPath = actualImagePath(image, chat.isDeleted());
                                if (actualPath == null) {
                                    image.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
                                    image.setErrorMessage(
                                            "Image file not found (checked both active and trash): "
                                                    + image.getLocalFilePath());
                                    // Save the corrected image status to database
                                    try {
                                        chatRepository.saveGeneratedImage(image);
                                    } catch (Exception e) {
                                        logger.warn("Failed to save corrected status for missing image {}",
                                                image.getId(), e);
                                    }
                                }
                            }
                        }

                    }

                    // Fix status for completed entries that have downloaded images
                    if (!images.isEmpty()) {
                        boolean allImagesCompleted = images.stream().allMatch(img -> {
                            // Check if image file exists (considering trash) and set correct download
                            // status
                            String actualPath = actualImagePath(img, chat.isDeleted());

                            if (actualPath != null) {
                                java.io.File imageFile = new java.io.File(actualPath);
                                // Write to the database only when the status really changes: this runs
                                // for every image at startup on the UI thread, and an unconditional save
                                // (two connections and a 36-column UPDATE per image) took about 13 s
                                if (imageFile.exists()) {
                                    if (img.getDownloadStatus()
                                            != GeneratedImage.DownloadStatus.CACHED) {
                                        // Update download status to CACHED if file exists
                                        img.setDownloadStatus(
                                                GeneratedImage.DownloadStatus.CACHED);
                                        try {
                                            chatRepository.saveGeneratedImage(img);
                                        } catch (Exception e) {
                                            logger.warn("Failed to save image status update for {}",
                                                    img.getId(), e);
                                        }
                                    }
                                    return true;
                                } else {
                                    // File missing in both active and trash, mark as failed
                                    if (img.getDownloadStatus()
                                            != GeneratedImage.DownloadStatus.FAILED) {
                                        img.setDownloadStatus(
                                                GeneratedImage.DownloadStatus.FAILED);
                                        logger.warn(
                                                "Image file missing for {} (checked both active and trash): original={}",
                                                img.getId(), img.getLocalFilePath());
                                        try {
                                            chatRepository.saveGeneratedImage(img);
                                        } catch (Exception e) {
                                            logger.warn("Failed to save image status update for {}",
                                                    img.getId(), e);
                                        }
                                    }
                                    return true; // Still counts as "completed" (not pending)
                                }
                            } else {
                                // No file path, likely still pending or failed: nothing changed, nothing to save
                                return true; // Still considered "completed" (not pending)
                            }
                        });

                        // Set chat entry status based on image completion
                        if (allImagesCompleted) {
                            boolean entryChanged = entry.getStatus() != ChatEntry.Status.COMPLETED
                                    || entry.getCompletedAt() == null;
                            entry.setStatus(ChatEntry.Status.COMPLETED);
                            if (entry.getCompletedAt() == null) {
                                entry.setCompletedAt(LocalDateTime.now());
                            }
                            // Save the updated status to database only if it changed
                            if (entryChanged) {
                                try {
                                    chatRepository.saveChatEntry(entry);
                                } catch (Exception e) {
                                    logger.warn("Failed to save chat entry status update for {}", entry.getId(), e);
                                }
                            }
                        }
                    } else {
                        // Handle COMPLETED entries without any images - this indicates a problem
                        if (entry.getStatus() == ChatEntry.Status.COMPLETED) {
                            logger.warn(
                                    "Found COMPLETED chat entry without images - marking as FAILED: {} ({})",
                                    entry.getId(), entry.getUserPrompt());

                            entry.setStatus(ChatEntry.Status.FAILED);
                            entry.setErrorMessage("Image generation completed but no images were found");

                            // Save the corrected status to database
                            try {
                                chatRepository.saveChatEntry(entry);
                            } catch (Exception e) {
                                logger.warn("Failed to save corrected status for entry {}", entry.getId(), e);
                            }
                        }
                    }
                }

            } catch (Exception e) {
                logger.error("Failed to load content for chat: {}", chat.getId(), e);
            }
        }

        // Filter out empty deleted chats before adding to the list
        // Empty deleted chats should not be kept in memory to prevent UI issues
        List<Chat> chatsToKeep = persistedChats.stream()
                .filter(chat -> !chat.isDeleted() || !chat.getEntries().isEmpty())
                .collect(Collectors.toList());

        return new History(chatsToKeep, persistedChats.isEmpty());
    }

    /**
     * Gets the actual path for an image, considering whether it might be in trash.
     * This is used during startup validation to check if images exist.
     * 
     * @param image         the generated image
     * @param isChatDeleted whether the parent chat is deleted
     * @return the actual path to the image file
     */
    public String actualImagePath(GeneratedImage image, boolean isChatDeleted) {
        String originalPath = image.getLocalFilePath();
        if (originalPath == null)
            return null;

        // If chat is deleted, check trash directory first
        if (isChatDeleted) {
            if (trashManager != null) {
                String trashPath = trashManager.getTrashPath(originalPath, false);
                if (trashPath != null && new java.io.File(trashPath).exists()) {
                    logger.debug("Found image in trash: {}", trashPath);
                    return trashPath;
                }
            }
        }

        // Check original location
        if (new java.io.File(originalPath).exists()) {
            return originalPath;
        }

        // If not deleted chat but file not in original location, check trash as
        // fallback
        if (!isChatDeleted) {
            if (trashManager != null) {
                String trashPath = trashManager.getTrashPath(originalPath, false);
                if (trashPath != null && new java.io.File(trashPath).exists()) {
                    logger.debug("Found image in trash (fallback): {}", trashPath);
                    return trashPath;
                }
            }
        }

        return null; // Not found anywhere
    }

    // ===== Storing and deleting, for the UI that does not talk to the repository itself =====

    public void saveChat(Chat chat) throws SQLException {
        chatRepository.saveChat(chat);
    }

    public void saveChatEntry(ChatEntry entry) throws SQLException {
        chatRepository.saveChatEntry(entry);
    }

    /** Moves a chat and its files to the trash. */
    public boolean softDeleteChat(String chatId) throws SQLException {
        return chatRepository.softDeleteChat(chatId);
    }

    /** Finds a chat without its entries, like an empty one that comes back from the trash. */
    public Optional<Chat> findChat(String chatId) throws SQLException {
        return chatRepository.findChatById(chatId);
    }

    public Optional<ChatEntry> findChatEntryById(String entryId) throws SQLException {
        return chatRepository.findChatEntryById(entryId);
    }

    public List<GeneratedImage> findGeneratedImagesByEntry(String entryId) throws SQLException {
        return chatRepository.findGeneratedImagesByEntry(entryId);
    }

    public Optional<GeneratedImage> findGeneratedImageById(String imageId) throws SQLException {
        return chatRepository.findGeneratedImageById(imageId);
    }

    public boolean softDeleteChatEntry(String entryId) throws SQLException {
        return chatRepository.softDeleteChatEntry(entryId);
    }

    public boolean softDeleteImage(String imageId) throws SQLException {
        return chatRepository.softDeleteImage(imageId);
    }

    public double getDeletedCostForEntry(String entryId) throws SQLException {
        return chatRepository.getDeletedCostForEntry(entryId);
    }
}
