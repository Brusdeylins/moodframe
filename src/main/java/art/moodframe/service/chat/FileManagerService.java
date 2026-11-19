package art.moodframe.service.chat;

import art.moodframe.model.GeneratedImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Deletes the image files of a chat directly, with their thumbnails, when there is no trash
 * ({@link ChatFileService} without a {@code TrashManager}). Creating it creates no folder.
 *
 * @since 1.0.0
 */
public class FileManagerService {

    private static final Logger logger = LoggerFactory.getLogger(FileManagerService.class);

    private final Path baseImageDirectory;

    /*  the thumbnails deleted with their images, null for none  */
    private final ThumbnailManager thumbnails;

    /**
     * Creates a new file manager service that deletes no thumbnails.
     *
     * @param baseImageDirectory the base directory for storing images, {@code LibraryPaths.images()}
     */
    public FileManagerService(Path baseImageDirectory) {
        this(baseImageDirectory, null);
    }

    /**
     * Creates a new file manager service.
     *
     * @param baseImageDirectory the base directory for storing images, {@code LibraryPaths.images()}
     * @param thumbnails         the thumbnails, deleted with their images (may be null: none)
     */
    public FileManagerService(Path baseImageDirectory, ThumbnailManager thumbnails) {
        this.baseImageDirectory = baseImageDirectory;
        this.thumbnails = thumbnails;
    }

    /**
     * Sanitizes a file name by removing invalid characters.
     *
     * @param fileName the original file name
     * @return the sanitized file name
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) return "unknown";

        // Replace invalid characters with underscores
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Deletes a single image file from the file system.
     * Also deletes the corresponding thumbnail if it exists.
     *
     * @param image the generated image to delete
     * @return true if the file was deleted successfully or didn't exist
     */
    public boolean deleteImageFile(GeneratedImage image) {
        String localFilePath = image.getLocalFilePath();
        if (localFilePath == null || localFilePath.trim().isEmpty()) {
            return false;
        }

        try {
            Path filePath = Paths.get(localFilePath);
            boolean originalDeleted = Files.deleteIfExists(filePath);

            if (originalDeleted) {
                logger.debug("Deleted original image file: {}", filePath);
            }

            // Also delete the corresponding thumbnail
            boolean thumbnailDeleted = deleteThumbnailFile(localFilePath);
            if (thumbnailDeleted) {
                logger.debug("Deleted thumbnail for: {}", localFilePath);
            }

            // Try to clean up empty parent directories
            cleanupEmptyDirectories(filePath.getParent());

            return true;

        } catch (IOException e) {
            logger.error("Failed to delete image file: {}", localFilePath, e);
            return false;
        }
    }

    /**
     * Deletes the thumbnail file corresponding to an original image.
     *
     * @param originalImagePath the path to the original image
     * @return true if the thumbnail was deleted successfully or didn't exist
     */
    private boolean deleteThumbnailFile(String originalImagePath) {
        try {
            ThumbnailManager thumbnailManager = thumbnails;
            if (thumbnailManager == null) {
                return true; // Not an error - service might not be initialized
            }

            // Get the thumbnail path
            String thumbnailPath = thumbnailManager.getThumbnailPath(originalImagePath);
            if (thumbnailPath == null) {
                return true;
            }

            // Delete the thumbnail file
            Path thumbnailFilePath = Paths.get(thumbnailPath);
            boolean existed = Files.deleteIfExists(thumbnailFilePath);

            if (existed) {
                logger.debug("Deleted thumbnail file: {}", thumbnailFilePath);
            }

            return true;

        } catch (Exception e) {
            logger.warn("Failed to delete thumbnail for: {}", originalImagePath, e);
            return false; // Non-critical error, continue with other operations
        }
    }

    /**
     * Deletes all thumbnails for a specific chat.
     * This is a cleanup method that ensures no orphaned thumbnails remain.
     *
     * @param chatId the chat ID
     * @param allImagesForChat list of all images belonging to the chat
     */
    public void deleteAllThumbnailsForChat(String chatId, java.util.List<GeneratedImage> allImagesForChat) {
        logger.info("Deleting all thumbnails for chat: {}", chatId);

        int deletedThumbnails = 0;
        for (GeneratedImage image : allImagesForChat) {
            String localPath = image.getLocalFilePath();
            if (localPath != null && !localPath.trim().isEmpty()) {
                if (deleteThumbnailFile(localPath)) {
                    deletedThumbnails++;
                }
            }
        }

        logger.info("Deleted {} thumbnails for chat: {}", deletedThumbnails, chatId);
    }

    /**
     * Deletes multiple image files from the file system.
     * Also deletes corresponding thumbnails.
     *
     * @param images the list of images to delete
     * @return the number of files successfully deleted
     */
    public int deleteImageFiles(List<GeneratedImage> images) {
        int deletedCount = 0;

        for (GeneratedImage image : images) {
            if (deleteImageFile(image)) {
                deletedCount++;
            }
        }

        logger.info("Deleted {} of {} image files", deletedCount, images.size());
        return deletedCount;
    }

    /**
     * Deletes all images associated with a chat.
     *
     * @param chatId the chat ID
     * @return true if all files were deleted successfully
     */
    public boolean deleteChatImages(String chatId) {
        try {
            Path chatDir = baseImageDirectory.resolve(sanitizeFileName(chatId));

            if (!Files.exists(chatDir)) {
                return true; // Already deleted
            }

            // Delete all files and subdirectories recursively
            Files.walk(chatDir)
                 .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                 .forEach(path -> {
                     try {
                         Files.delete(path);
                     } catch (IOException e) {
                         logger.error("Failed to delete: {}", path, e);
                     }
                 });

            logger.info("Deleted all images for chat: {}", chatId);
            return true;

        } catch (IOException e) {
            logger.error("Failed to delete images for chat: {}", chatId, e);
            return false;
        }
    }

    /**
     * Cleans up empty parent directories recursively.
     *
     * @param directory the directory to check and potentially delete
     */
    private void cleanupEmptyDirectories(Path directory) {
        if (directory == null || !Files.exists(directory) || directory.equals(baseImageDirectory)) {
            return;
        }

        try {
            // Check if directory is empty
            if (Files.list(directory).findAny().isEmpty()) {
                Files.delete(directory);
                // Recursively check parent directory
                cleanupEmptyDirectories(directory.getParent());
            }
        } catch (IOException e) {
            logger.warn("Failed to clean up empty directory: {}", directory, e);
        }
    }

}