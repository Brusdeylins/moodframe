package art.moodframe.service;

import art.moodframe.model.GeneratedImage;
import art.moodframe.repository.ChatFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Moves the files of a chat to the trash and back, and removes them for good. Without a trash manager the
 * files stay where they are, or are deleted directly when the chat is removed for good.
 *
 * @since 1.9.1
 */
public class ChatFileService implements ChatFiles {

    private static final Logger logger = LoggerFactory.getLogger(ChatFileService.class);

    private final TrashManager trashManager;
    private final ThumbnailManager thumbnailManager;
    private final FileManagerService fileManagerService;

    /**
     * Creates the service.
     *
     * @param trashManager       the trash (may be null)
     * @param thumbnailManager   locates the thumbnails (may be null)
     * @param fileManagerService deletes files directly if there is no trash
     */
    public ChatFileService(TrashManager trashManager, ThumbnailManager thumbnailManager,
            FileManagerService fileManagerService) {
        this.trashManager = trashManager;
        this.thumbnailManager = thumbnailManager;
        this.fileManagerService = fileManagerService;
    }

    @Override
    public void moveToTrash(String chatId, List<GeneratedImage> images) {
        if (trashManager == null) {
            logger.warn("TrashManager not available - files will remain in original location");
            return;
        }

        for (GeneratedImage image : images) {
            String imagePath = image.getLocalFilePath();
            if (imagePath == null)
                continue;

            String thumbnailPath = thumbnailManager != null ? thumbnailManager.getThumbnailPath(imagePath) : null;
            trashManager.moveToTrash(imagePath, thumbnailPath);
        }
        logger.info("Moved {} images to trash for chat: {}", images.size(), chatId);
    }

    @Override
    public void restoreFromTrash(String chatId, List<GeneratedImage> images) {
        if (trashManager == null) {
            logger.warn("TrashManager not available - files cannot be restored");
            return;
        }

        int restoredCount = 0;
        for (GeneratedImage image : images) {
            String imagePath = image.getLocalFilePath();
            if (imagePath == null)
                continue;

            String trashImagePath = trashManager.getTrashPath(imagePath, false);
            String trashThumbnailPath = null;
            if (thumbnailManager != null) {
                String thumbnailPath = thumbnailManager.getThumbnailPath(imagePath);
                if (thumbnailPath != null)
                    trashThumbnailPath = trashManager.getTrashPath(thumbnailPath, true);
            }

            if (trashManager.restoreFromTrash(trashImagePath, trashThumbnailPath))
                restoredCount++;
        }
        logger.info("Restored {} non-deleted images from trash for chat: {} (skipped individually deleted images)",
                restoredCount, chatId);
    }

    @Override
    public boolean deletePermanently(String chatId, List<GeneratedImage> images) {
        if (trashManager != null) {
            for (GeneratedImage image : images) {
                String imagePath = image.getLocalFilePath();
                String trashImagePath = trashManager.getTrashPath(imagePath, false);
                String trashThumbnailPath = null;
                if (thumbnailManager != null) {
                    String thumbnailPath = thumbnailManager.getThumbnailPath(imagePath);
                    trashThumbnailPath = trashManager.getTrashPath(thumbnailPath, true);
                }
                trashManager.permanentlyDelete(trashImagePath, trashThumbnailPath);
            }
            return true;
        }

        // Fallback to direct deletion if TrashManager not available
        int deletedFiles = fileManagerService.deleteImageFiles(images);
        boolean allDeleted = deletedFiles == images.size();
        if (!allDeleted) {
            logger.warn("Could not delete all image files for chat: {} ({} of {} deleted)", chatId, deletedFiles,
                    images.size());
        }

        // Ensure all thumbnails are deleted (cleanup for any missed thumbnails)
        fileManagerService.deleteAllThumbnailsForChat(chatId, images);

        // Also try to delete the entire chat directory
        fileManagerService.deleteChatImages(chatId);
        return allDeleted;
    }
}
