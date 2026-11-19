package art.moodframe.repository;

import art.moodframe.model.GeneratedImage;

import java.util.List;

/**
 * What happens to the files of a chat, an entry or an image when it is deleted, restored or removed for good, and to
 * those of an image a generation delivered after its entry or chat went to the trash.
 * {@link ChatTrashRepository} decides when (after the commit, with the rows it changed), an implementation (see
 * {@code ChatFileService}) knows where the files are.
 *
 * @since 1.9.1
 */
public interface ChatFiles {

    /** Does nothing: the files stay where they are (tests, no file handling). */
    ChatFiles NONE = new ChatFiles() {
        @Override
        public void moveToTrash(String chatId, List<GeneratedImage> images) {
        }

        @Override
        public void restoreFromTrash(String chatId, List<GeneratedImage> images) {
        }

        @Override
        public boolean deletePermanently(String chatId, List<GeneratedImage> images) {
            return true;
        }

        @Override
        public void removeDropped(List<GeneratedImage> images) {
        }
    };

    /**
     * Moves the images and thumbnails of a deleted chat, entry or image to the trash.
     *
     * @param chatId the chat of the images, for the log; null if it is not known
     * @param images the images that went to the trash
     */
    void moveToTrash(String chatId, List<GeneratedImage> images);

    /**
     * Moves the images and thumbnails of a restored chat, entry or image back from the trash.
     *
     * @param chatId the chat of the images, for the log; null if it is not known
     * @param images the images that came back; of a chat those not deleted on their own
     */
    void restoreFromTrash(String chatId, List<GeneratedImage> images);

    /**
     * Removes the files of a chat, an entry or an image that is removed from the trash for good.
     *
     * @param chatId the chat when the whole chat is removed, null for an entry or an image (the folder of the chat
     *               stays)
     * @param images the images removed
     * @return true if all files are gone
     */
    boolean deletePermanently(String chatId, List<GeneratedImage> images);

    /**
     * Removes the files and thumbnails of images a generation wrote into the folder of the images in use after their
     * entry or chat went to the trash: they have no row, so no deletion moved them.
     *
     * @param images the dropped images
     */
    void removeDropped(List<GeneratedImage> images);
}
