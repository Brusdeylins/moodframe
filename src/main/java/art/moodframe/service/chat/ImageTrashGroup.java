package art.moodframe.service.chat;

import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.repository.ChatRepository;
import art.moodframe.repository.ChatTrashRepository;
import art.moodframe.service.trash.TrashEntry;
import art.moodframe.service.trash.TrashGroup;
import art.moodframe.service.trash.TrashManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The images in the central trash that were deleted on their own or with their entry: one row per entry, with its
 * deleted images as parts. A row restores or removes all of them, the ID of an image only that image. The images
 * of a deleted chat stay in the group of the chats.
 *
 * @since 2.0.0
 */
public class ImageTrashGroup implements TrashGroup {

    private static final Logger logger = LoggerFactory.getLogger(ImageTrashGroup.class);

    /** The length of the beginning of the prompt that names a row. */
    private static final int PROMPT_LENGTH = 60;

    private final ChatRepository chatRepository;
    private final ChatTrashRepository trash;
    private final TrashManager trashManager;
    private final ThumbnailManager thumbnailManager;

    /**
     * Creates the group.
     *
     * @param chatRepository   where chats are stored
     * @param trash            the trash of the images, which moves their files back and removes them
     * @param trashManager     locates the files in the trash for the previews (may be null: no previews)
     * @param thumbnailManager locates the thumbnails (may be null: the images themselves are the previews)
     */
    public ImageTrashGroup(ChatRepository chatRepository, ChatTrashRepository trash, TrashManager trashManager,
            ThumbnailManager thumbnailManager) {
        this.chatRepository = chatRepository;
        this.trash = trash;
        this.trashManager = trashManager;
        this.thumbnailManager = thumbnailManager;
    }

    @Override
    public Kind kind() {
        return Kind.IMAGES;
    }

    /**
     * Lists the entries with deleted images: the beginning of the prompt, the chat, the number of the deleted
     * images and what they had cost.
     *
     * @param locale the language of the context, null for that of the user interface
     * @return the entries in the trash, the latest deletion first
     */
    @Override
    public List<TrashEntry> list(Locale locale) {
        List<TrashEntry> entries = new ArrayList<>();
        try {
            Map<String, String> chatNames = new HashMap<>();
            for (Chat chat : chatRepository.findAllActiveChats())
                chatNames.put(chat.getId(), chat.getName());

            for (ChatEntry entry : trash.findEntriesWithDeletedImages()) {
                List<TrashEntry.Part> parts = entry.getImages().stream()
                        .map((image) -> new TrashEntry.Part(image.getId(), image.getDeletedAt(), preview(image)))
                        .toList();

                /*  The row runs out with its first image, a deleted entry without images on its own  */
                LocalDateTime deletedAt = parts.stream().map(TrashEntry.Part::deletedAt).filter(Objects::nonNull)
                        .min(Comparator.naturalOrder()).orElse(entry.getDeletedAt());

                entries.add(new TrashEntry(entry.getId(), prompt(entry), deletedAt, TrashGroup.text(locale,
                        parts.size() == 1 ? "trash.context.images.singular" : "trash.context.images.plural",
                        chatNames.get(entry.getChatId()), parts.size(),
                        TrashGroup.cost(locale, entry.getDeletedCost())), parts));
            }
        } catch (SQLException e) {
            logger.error("Failed to list the deleted images", e);
        }
        entries.sort(Comparator.comparing(TrashEntry::deletedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return entries;
    }

    /*  the beginning of the prompt on one line, of the text the user typed if there is one  */
    private String prompt(ChatEntry entry) {
        String prompt = entry.getUserPrompt() != null && !entry.getUserPrompt().isBlank()
                ? entry.getUserPrompt() : entry.getEnhancedPrompt();
        prompt = prompt != null ? prompt.strip().replaceAll("\\s+", " ") : "";
        return prompt.length() > PROMPT_LENGTH ? prompt.substring(0, PROMPT_LENGTH).strip() + "…" : prompt;
    }

    /*  the thumbnail of an image in the trash, or the image itself without one; null if neither is there  */
    private String preview(GeneratedImage image) {
        String imagePath = image.getLocalFilePath();
        if (imagePath == null || trashManager == null)
            return null;

        String thumbnail = trashManager.getTrashPath(
                thumbnailManager != null ? thumbnailManager.getThumbnailPath(imagePath) : null, true);
        if (thumbnail != null && Files.exists(Path.of(thumbnail)))
            return thumbnail;
        String file = trashManager.getTrashPath(imagePath, false);
        return file != null && Files.exists(Path.of(file)) ? file : null;
    }

    /**
     * Takes an entry back with all its deleted images, or a single image; a deleted entry comes back with it.
     *
     * @param id the ID of the entry or of one of its images
     * @return true if something came back
     */
    @Override
    public boolean restore(String id) {
        try {
            return chatRepository.findGeneratedImageById(id).isPresent() ? trash.restoreImage(id)
                    : trash.restoreChatEntry(id);
        } catch (SQLException e) {
            logger.error("Failed to restore images: {}", id, e);
            return false;
        }
    }

    /**
     * Removes the deleted images of an entry for good, or a single one; a deleted entry goes with its last image.
     * The files go only after their record (see {@link ChatTrashRepository}), so no file of an image in use is
     * touched.
     *
     * @param id the ID of the entry or of one of its images
     * @return true if something is gone
     */
    @Override
    public boolean deletePermanently(String id) {
        try {
            return chatRepository.findGeneratedImageById(id).isPresent() ? trash.deleteImagePermanently(id)
                    : trash.deleteChatEntryPermanently(id);
        } catch (SQLException e) {
            logger.error("Failed to permanently delete images: {}", id, e);
            return false;
        }
    }
}
