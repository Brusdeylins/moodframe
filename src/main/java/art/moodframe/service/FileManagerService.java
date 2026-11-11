package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.GeneratedImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import art.moodframe.config.ServiceFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Service for managing file system operations related to generated images.
 * 
 * <p>This service handles the creation, deletion, and organization of
 * image files on the local file system, including cleanup operations
 * when chats or entries are deleted.</p>
 * 
 * @since 1.0.0
 */
public class FileManagerService implements FileManager {
    
    private static final Logger logger = LoggerFactory.getLogger(FileManagerService.class);
    
    private final Path baseImageDirectory;
    private final ApplicationConfig config;
    
    /**
     * Creates a new file manager service with the default image directory.
     */
    public FileManagerService() {
        this(getDefaultImageDirectory());
    }
    
    /**
     * Creates a new file manager service with a custom image directory.
     * 
     * @param baseImageDirectory the base directory for storing images
     */
    public FileManagerService(Path baseImageDirectory) {
        this.baseImageDirectory = baseImageDirectory;
        this.config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        ensureDirectoryExists(baseImageDirectory);
    }
    
    /**
     * Gets the default image directory in the user's home folder.
     * 
     * @return the default image directory path
     */
    private static Path getDefaultImageDirectory() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".moodframe", "images");
    }
    
    /**
     * Ensures that the specified directory exists, creating it if necessary.
     * 
     * @param directory the directory to create
     */
    private void ensureDirectoryExists(Path directory) {
        try {
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }
        } catch (IOException e) {
            logger.error("Failed to create directory: {}", directory, e);
            throw new RuntimeException("Failed to create image directory", e);
        }
    }
    
    /**
     * Gets the recommended file path for storing an image.
     * 
     * @param chatId the chat ID
     * @param entryId the chat entry ID  
     * @param imageId the image ID
     * @param fileExtension the file extension (e.g., "png")
     * @return the recommended file path
     */
    @Override
    public Path getImageFilePath(String chatId, String entryId, String imageId, String fileExtension) {
        // Organize images in subdirectories: images/chatId/entryId/imageId.ext
        Path chatDir = baseImageDirectory.resolve(sanitizeFileName(chatId));
        Path entryDir = chatDir.resolve(sanitizeFileName(entryId));
        
        String fileName = sanitizeFileName(imageId) + "." + fileExtension.toLowerCase();
        return entryDir.resolve(fileName);
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
     * Creates the directory structure for storing an image.
     * 
     * @param imagePath the image file path
     * @return true if the directory was created successfully
     */
    @Override
    public boolean ensureImageDirectoryExists(Path imagePath) {
        try {
            Path parentDir = imagePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            return true;
        } catch (IOException e) {
            logger.error("Failed to create image directory for: {}", imagePath, e);
            return false;
        }
    }
    
    /**
     * Deletes a single image file from the file system.
     * Also deletes the corresponding thumbnail if it exists.
     * 
     * @param image the generated image to delete
     * @return true if the file was deleted successfully or didn't exist
     */
    @Override
    public boolean deleteImageFile(GeneratedImage image) {
        String localFilePath = image.getLocalFilePath();
        if (localFilePath == null || localFilePath.trim().isEmpty()) {
            return false;
        }
        
        try {
            Path filePath = Paths.get(localFilePath);
            boolean originalDeleted = Files.deleteIfExists(filePath);
            
            if (originalDeleted && config != null && config.getLoggingEnabled()) {
                logger.info("Deleted original image file: {}", filePath);
            }
            
            // Also delete the corresponding thumbnail
            boolean thumbnailDeleted = deleteThumbnailFile(localFilePath);
            if (thumbnailDeleted && config != null && config.getLoggingEnabled()) {
                logger.info("Deleted thumbnail for: {}", localFilePath);
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
            // Get ThumbnailManager from ServiceFactory
            ThumbnailManager thumbnailManager = ServiceFactory.getInstance()
                .getService(ThumbnailManager.class);
            
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
            
            if (existed && config != null && config.getLoggingEnabled()) {
                logger.info("Deleted thumbnail file: {}", thumbnailFilePath);
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
    @Override
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
    @Override
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
     * Deletes all images associated with a chat entry.
     * 
     * @param chatId the chat ID
     * @param entryId the entry ID
     * @return true if all files were deleted successfully
     */
    @Override
    public boolean deleteEntryImages(String chatId, String entryId) {
        try {
            Path entryDir = baseImageDirectory.resolve(sanitizeFileName(chatId))
                                              .resolve(sanitizeFileName(entryId));
            
            if (!Files.exists(entryDir)) {
                return true; // Already deleted
            }
            
            // Delete all files in the entry directory
            Files.walk(entryDir)
                 .filter(Files::isRegularFile)
                 .forEach(file -> {
                     try {
                         Files.delete(file);
                     } catch (IOException e) {
                         logger.error("Failed to delete entry image file: {}", file, e);
                     }
                 });
            
            // Delete the empty directory
            Files.deleteIfExists(entryDir);
            
            // Clean up empty parent directory
            cleanupEmptyDirectories(entryDir.getParent());
            
            logger.info("Deleted all images for entry: {} in chat: {}", entryId, chatId);
            return true;
            
        } catch (IOException e) {
            logger.error("Failed to delete images for entry: {} in chat: {}", entryId, chatId, e);
            return false;
        }
    }
    
    /**
     * Deletes all images associated with a chat.
     * 
     * @param chatId the chat ID
     * @return true if all files were deleted successfully
     */
    @Override
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
    
    /**
     * Gets the base image directory.
     * 
     * @return the base directory path
     */
    @Override
    public Path getBaseImageDirectory() {
        return baseImageDirectory;
    }
    
    /**
     * Gets the total size of all stored images in bytes.
     * 
     * @return the total size in bytes
     */
    @Override
    public long getTotalImageSize() {
        try {
            return Files.walk(baseImageDirectory)
                       .filter(Files::isRegularFile)
                       .mapToLong(file -> {
                           try {
                               return Files.size(file);
                           } catch (IOException e) {
                               return 0L;
                           }
                       })
                       .sum();
        } catch (IOException e) {
            logger.error("Failed to calculate total image size", e);
            return 0;
        }
    }
    
    /**
     * Gets the number of stored image files.
     * 
     * @return the number of image files
     */
    @Override
    public long getImageFileCount() {
        try {
            return Files.walk(baseImageDirectory)
                       .filter(Files::isRegularFile)
                       .count();
        } catch (IOException e) {
            logger.error("Failed to count image files", e);
            return 0;
        }
    }
}