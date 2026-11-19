package art.moodframe.service.chat;

import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryGroup;
import art.moodframe.model.FrameGroup;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.MoodGroup;
import art.moodframe.repository.ChatGroupQueries;
import art.moodframe.repository.ChatRepository;
import art.moodframe.repository.ChatTrashRepository;
import art.moodframe.service.trash.TrashManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    private final ChatTrashRepository trash;
    private final ChatGroupQueries groups;
    private final TrashManager trashManager;

    /**
     * Creates the service.
     *
     * @param chatRepository where chats are stored
     * @param trash          the trash of the chats, entries and images, which moves their files
     * @param groups         the entries grouped by mood and by frame
     * @param trashManager   locates the files of deleted chats (may be null: then only the original place counts)
     */
    public ChatHistoryService(ChatRepository chatRepository, ChatTrashRepository trash, ChatGroupQueries groups,
            TrashManager trashManager) {
        this.chatRepository = chatRepository;
        this.trash = trash;
        this.groups = groups;
        this.trashManager = trashManager;
    }

    /**
     * Moves an entry with its images to the trash; the files of the images it deleted follow after the commit. An
     * entry already deleted (e.g. by an agent) or not stored is left alone.
     *
     * @param entryId the entry
     * @return true if this call deleted it
     * @throws SQLException if the entry cannot be marked
     */
    public boolean deleteEntry(String entryId) throws SQLException {
        return trash.softDeleteChatEntry(entryId);
    }

    /**
     * Moves an image to the trash, its file after the commit; the entry is deleted too when it was its last image.
     * An image already deleted (e.g. by an agent) or not stored is left alone; the answer tells the state of its
     * entry as stored, so the caller follows it.
     *
     * @param entryId the entry of the image
     * @param image   the image
     * @return whether the entry is deleted now and its deleted cost as stored
     * @throws SQLException if the database cannot be updated
     */
    public ImageDeletion deleteImage(String entryId, GeneratedImage image) throws SQLException {
        return deleteImage(entryId, image, false);
    }

    /**
     * Moves an image to the trash like {@link #deleteImage(String, GeneratedImage)}, optionally keeping its entry in
     * use: the running generation of the entry still makes images that are not stored yet.
     *
     * @param entryId   the entry of the image
     * @param image     the image
     * @param keepEntry true to keep the entry in use, also without stored images left
     * @return whether the entry is deleted now and its deleted cost as stored
     * @throws SQLException if the database cannot be updated
     */
    public ImageDeletion deleteImage(String entryId, GeneratedImage image, boolean keepEntry) throws SQLException {
        if (!trash.softDeleteImage(image.getId(), keepEntry))
            logger.info("Image {} was not deleted now: it is not stored or already in the trash", image.getId());

        /*  An entry that is not stored leaves its chat, as before  */
        Optional<ChatEntry> entry = chatRepository.findChatEntryById(entryId);
        boolean entryDeleted = entry.map((ChatEntry stored) -> stored.getDeletedAt() != null).orElse(true);
        if (entryDeleted)
            logger.info("Entry {} is deleted with its last image", entryId);
        return new ImageDeletion(entryDeleted, entry.map(ChatEntry::getDeletedCost).orElse(0.0));
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
                                    chatRepository.saveRepairedImage(image);
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
                                    chatRepository.saveRepairedImage(image);
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
                                        chatRepository.saveRepairedImage(image);
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
                                // for every image at startup (on the startup thread, behind the splash), and
                                // an unconditional save (two connections and a 36-column UPDATE per image)
                                // took about 13 s
                                if (imageFile.exists()) {
                                    if (img.getDownloadStatus()
                                            != GeneratedImage.DownloadStatus.CACHED) {
                                        // Update download status to CACHED if file exists
                                        img.setDownloadStatus(
                                                GeneratedImage.DownloadStatus.CACHED);
                                        try {
                                            chatRepository.saveRepairedImage(img);
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
                                            chatRepository.saveRepairedImage(img);
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
        /*  The chats in the trash keep their place in the order, so a restored chat shows where it was  */
        List<Chat> chatsToKeep = persistedChats.stream()
                .filter(chat -> !chat.isDeleted() || !chat.getEntries().isEmpty())
                .sorted(Comparator.comparingInt(Chat::getSortOrder))
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

    /**
     * Finds the thumbnail of an image in the trash, for an image whose file lies there.
     *
     * @param thumbnailPath the path of the thumbnail in the thumbnails folder
     * @return the path in the trash, null if the thumbnail is not there
     */
    public String actualThumbnailPath(String thumbnailPath) {
        String trashPath = trashManager != null ? trashManager.getTrashPath(thumbnailPath, true) : null;
        return trashPath != null && new java.io.File(trashPath).exists() ? trashPath : null;
    }

    // ===== Storing and deleting, for the UI that does not talk to the repository itself =====

    public void saveChat(Chat chat) throws SQLException {
        chatRepository.saveChat(chat);
    }

    /**
     * Moves a chat to another place in the list and numbers all chats of the list anew, from 0 on, as they follow;
     * the order of the window goes through {@link ChatLibrary#moveChat(Chat, Chat)}.
     *
     * @param chats  all chats of the list in their order, also the hidden ones in the trash
     * @param chat   the chat to move
     * @param before the chat it goes in front of, null for the end of the list
     * @throws SQLException if the order cannot be stored; the chats keep their new numbers anyway
     */
    void moveChat(List<Chat> chats, Chat chat, Chat before) throws SQLException {
        if (chat == before)
            return;
        saveChatOrder(order(chats, chat, before));
    }

    /**
     * Moves a chat to another place in the list and numbers all chats of the list anew, from 0 on, as they follow;
     * the order of the window goes through {@link ChatLibrary#moveChat(Chat, Chat)}.
     * without storing anything.
     *
     * @param chats  all chats of the list in their order, also the hidden ones in the trash
     * @param chat   the chat to move
     * @param before the chat it goes in front of, null for the end of the list
     * @return the chats in their new order
     */
    public static List<Chat> order(List<Chat> chats, Chat chat, Chat before) {
        List<Chat> order = new ArrayList<>(chats);
        order.remove(chat);
        order.add(before != null ? order.indexOf(before) : order.size(), chat);
        for (int i = 0; i < order.size(); i++)
            order.get(i).setSortOrder(i);
        return order;
    }

    /**
     * Stores the sort orders of the chats.
     *
     * @param chats the chats with their numbers
     * @throws SQLException if the order cannot be stored
     */
    public void saveChatOrder(List<Chat> chats) throws SQLException {
        chatRepository.updateChatSortOrders(chats);
    }

    /** The sort order that puts a new chat on top of the list: one less than the smallest one. */
    public static int sortOrderOnTop(List<Chat> chats) {
        return chats.stream().mapToInt(Chat::getSortOrder).min().orElse(1) - 1;
    }

    public void saveChatEntry(ChatEntry entry) throws SQLException {
        chatRepository.saveChatEntry(entry);
    }

    /** Moves a chat and its files to the trash. */
    public boolean softDeleteChat(String chatId) throws SQLException {
        return trash.softDeleteChat(chatId);
    }

    /** Finds a chat without its entries, like an empty one that comes back from the trash; also one in the trash. */
    public Optional<Chat> findChat(String chatId) throws SQLException {
        return chatRepository.findChatById(chatId);
    }

    /** Finds an entry without its images, also one in the trash (see {@link ChatRepository#findChatEntryById}). */
    public Optional<ChatEntry> findChatEntryById(String entryId) throws SQLException {
        return chatRepository.findChatEntryById(entryId);
    }

    /** Groups the entries of the active chats by their mood, for the left sidebar by mood. */
    public List<MoodGroup> findMoodGroups() throws SQLException {
        return groups.findMoodGroups();
    }

    /** Groups the entries of the active chats by their frame, for the left sidebar by frame. */
    public List<FrameGroup> findFrameGroups() throws SQLException {
        return groups.findFrameGroups();
    }

    /** Lists the ids of the entries of a group by mood or by frame, in the order of their creation. */
    public List<String> findChatEntryIds(EntryGroup group) throws SQLException {
        return switch (group) {
            case MoodGroup mood -> groups.findChatEntryIdsByMood(mood);
            case FrameGroup frame -> groups.findChatEntryIdsByFrame(frame);
        };
    }

    /** Lists the images of an entry that are not in the trash. */
    public List<GeneratedImage> findGeneratedImagesByEntry(String entryId) throws SQLException {
        return chatRepository.findGeneratedImagesByEntry(entryId);
    }

    /** Finds an image, also one in the trash (see {@link ChatRepository#findGeneratedImageById}). */
    public Optional<GeneratedImage> findGeneratedImageById(String imageId) throws SQLException {
        return chatRepository.findGeneratedImageById(imageId);
    }

    /**
     * Loads an entry that got images back from the trash, with the images it shows now.
     *
     * @param id the ID of the entry or of one of its images, as the trash of the images reports it
     * @return the entry, empty if it is gone
     * @throws SQLException if the entry cannot be read
     */
    public Optional<ChatEntry> findRestoredEntry(String id) throws SQLException {
        Optional<GeneratedImage> image = chatRepository.findGeneratedImageById(id);
        Optional<ChatEntry> entry = chatRepository.findChatEntryById(
                image.map(GeneratedImage::getChatEntryId).orElse(id));
        if (entry.isPresent())
            entry.get().getImages().setAll(chatRepository.findGeneratedImagesByEntry(entry.get().getId()));
        return entry;
    }
}
