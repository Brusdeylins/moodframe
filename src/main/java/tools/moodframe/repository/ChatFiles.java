package tools.moodframe.repository;

import tools.moodframe.model.GeneratedImage;

import java.util.List;

/**
 * What happens to the files of a chat when the chat is deleted, restored or removed for good. The repository
 * decides when, an implementation (see {@code ChatFileService}) knows where the files are.
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
    };

    /**
     * Moves the images and thumbnails of a deleted chat to the trash.
     *
     * @param chatId the chat
     * @param images all images of the chat
     */
    void moveToTrash(String chatId, List<GeneratedImage> images);

    /**
     * Moves the images and thumbnails of a restored chat back from the trash.
     *
     * @param chatId the chat
     * @param images the images of the chat that were not deleted on their own
     */
    void restoreFromTrash(String chatId, List<GeneratedImage> images);

    /**
     * Removes the files of a chat that expires from the trash.
     *
     * @param chatId the chat
     * @param images all images of the chat
     * @return true if all files are gone
     */
    boolean deletePermanently(String chatId, List<GeneratedImage> images);
}
