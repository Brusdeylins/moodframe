package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Service for managing the trash directory and moving/deleting files.
 * Handles moving files to trash, restoring from trash, and permanent deletion.
 */
public class TrashManager implements TrashService {
    private static final Logger logger = LoggerFactory.getLogger(TrashManager.class);
    
    private final Path baseDirectory;
    private final Path trashDirectory;
    private final Path trashImagesDirectory;
    private final Path trashThumbnailsDirectory;
    private final Path activeImagesDirectory;
    private final Path activeThumbnailsDirectory;
    
    private final ExecutorService executorService;
    private final int trashRetentionDays;
    
    /**
     * Creates a new TrashManager with default settings.
     * 
     * <p>Uses a default retention period of 60 days for items in trash
     * before automatic deletion.</p>
     * 
     * <p><strong>Thread-safety:</strong> This class is thread-safe and uses
     * an internal executor service for asynchronous operations.</p>
     */
    public TrashManager() {
        this(60); // Default 60 days retention
    }
    
    /**
     * Moves multiple files to the trash directory asynchronously.
     * 
     * @param imagePaths list of image file paths to move to trash
     * @param thumbnailPaths list of corresponding thumbnail paths (can be shorter than imagePaths)
     * @return CompletableFuture that completes when all files are moved
     * @throws RuntimeException if any move operation fails
     */
    @Override
    public CompletableFuture<Void> moveToTrash(List<String> imagePaths, List<String> thumbnailPaths) {
        return CompletableFuture.runAsync(() -> {
            for (int i = 0; i < imagePaths.size(); i++) {
                String imagePath = imagePaths.get(i);
                String thumbnailPath = i < thumbnailPaths.size() ? thumbnailPaths.get(i) : null;
                moveToTrash(imagePath, thumbnailPath);
            }
        }, executorService);
    }
    
    /**
     * Restores multiple files from trash back to their original locations.
     * 
     * @param imagePaths list of image file paths in trash to restore
     * @param thumbnailPaths list of corresponding thumbnail paths to restore
     * @return CompletableFuture that completes when all files are restored
     * @throws RuntimeException if any restore operation fails
     */
    @Override
    public CompletableFuture<Void> restoreFromTrash(List<String> imagePaths, List<String> thumbnailPaths) {
        return CompletableFuture.runAsync(() -> {
            for (int i = 0; i < imagePaths.size(); i++) {
                String imagePath = imagePaths.get(i);
                String thumbnailPath = i < thumbnailPaths.size() ? thumbnailPaths.get(i) : null;
                restoreFromTrash(imagePath, thumbnailPath);
            }
        }, executorService);
    }
    
    /**
     * Permanently deletes files from trash.
     * 
     * <p><strong>Warning:</strong> This operation cannot be undone. Files will be
     * permanently removed from the file system.</p>
     * 
     * @param imagePaths list of image file paths in trash to delete
     * @param thumbnailPaths list of corresponding thumbnail paths to delete
     * @return CompletableFuture that completes when all files are deleted
     * @throws RuntimeException if any delete operation fails
     */
    @Override
    public CompletableFuture<Void> permanentlyDelete(List<String> imagePaths, List<String> thumbnailPaths) {
        return CompletableFuture.runAsync(() -> {
            for (int i = 0; i < imagePaths.size(); i++) {
                String imagePath = imagePaths.get(i);
                String thumbnailPath = i < thumbnailPaths.size() ? thumbnailPaths.get(i) : null;
                permanentlyDelete(imagePath, thumbnailPath);
            }
        }, executorService);
    }
    
    /**
     * Empties the entire trash directory.
     * 
     * <p><strong>Warning:</strong> This operation permanently deletes all files
     * in the trash directory and cannot be undone.</p>
     * 
     * <p><strong>Performance:</strong> This operation walks the entire trash
     * directory tree, so performance depends on the number of files.</p>
     * 
     * @return CompletableFuture with the number of files deleted
     * @throws RuntimeException if the operation fails
     */
    @Override
    public CompletableFuture<Integer> emptyTrash() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return (int) Files.walk(trashDirectory)
                    .filter(Files::isRegularFile)
                    .peek(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            logger.error("Failed to delete: {}", path, e);
                        }
                    })
                    .count();
            } catch (IOException e) {
                logger.error("Failed to empty trash", e);
                return 0;
            }
        }, executorService);
    }
    
    /**
     * Gets the retention period for items in trash.
     * 
     * @return retention period in days
     */
    @Override
    public int getRetentionPeriod() {
        return trashRetentionDays;
    }
    
    /**
     * Creates a new TrashManager with specified retention period.
     * 
     * <p>Initializes the trash directory structure and creates necessary
     * directories if they don't exist. Files in trash older than the
     * retention period will be automatically deleted.</p>
     * 
     * @param trashRetentionDays number of days to keep items in trash (must be positive)
     * @throws RuntimeException if directories cannot be created
     */
    public TrashManager(int trashRetentionDays) {
        String userHome = System.getProperty("user.home");
        this.baseDirectory = Paths.get(userHome, ".moodframe");
        this.trashDirectory = baseDirectory.resolve("trash");
        this.trashImagesDirectory = trashDirectory.resolve("images");
        this.trashThumbnailsDirectory = trashDirectory.resolve("thumbnails");
        this.activeImagesDirectory = baseDirectory.resolve("images");
        this.activeThumbnailsDirectory = baseDirectory.resolve("thumbnails");
        this.trashRetentionDays = trashRetentionDays;
        
        // Create thread pool for async operations
        this.executorService = Executors.newFixedThreadPool(2,
            r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("TrashManager-" + System.currentTimeMillis());
                return t;
            });
        
        // Ensure directories exist
        try {
            Files.createDirectories(trashImagesDirectory);
            Files.createDirectories(trashThumbnailsDirectory);
            
            // Only log if logging is enabled
            ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
            if (config != null && config.getLoggingEnabled()) {
                logger.info("Trash directories initialized at: {}", trashDirectory);
            }
        } catch (IOException e) {
            logger.error("Failed to create trash directories", e);
        }
    }
    
    /**
     * Moves an image and its thumbnail to trash.
     * 
     * @param imagePath path to the image file
     * @param thumbnailPath path to the thumbnail file (can be null)
     * @return true if successfully moved to trash
     */
    public boolean moveToTrash(String imagePath, String thumbnailPath) {
        boolean success = true;
        
        // Move main image to trash
        if (imagePath != null && !imagePath.isEmpty()) {
            Path source = Paths.get(imagePath);
            if (Files.exists(source)) {
                try {
                    // Try to get relative path, but handle absolute paths too
                    Path relativePath;
                    try {
                        if (source.startsWith(activeImagesDirectory)) {
                            relativePath = activeImagesDirectory.relativize(source);
                        } else {
                            // Use just the filename if path is not under active directory
                            relativePath = source.getFileName();
                        }
                    } catch (IllegalArgumentException e) {
                        // Fallback to just filename if relativize fails
                        relativePath = source.getFileName();
                    }
                    
                    Path target = trashImagesDirectory.resolve(relativePath);
                    
                    // Create parent directories in trash if needed
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    
                    // Move file to trash
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    logger.debug("Moved image to trash: {} -> {}", source, target);
                } catch (IOException e) {
                    logger.error("Failed to move image to trash: {}", imagePath, e);
                    success = false;
                }
            } else {
                logger.debug("Image file does not exist, may already be in trash: {}", imagePath);
            }
        }
        
        // Move thumbnail to trash
        if (thumbnailPath != null && !thumbnailPath.isEmpty()) {
            Path source = Paths.get(thumbnailPath);
            if (Files.exists(source)) {
                try {
                    // Try to get relative path, but handle absolute paths too
                    Path relativePath;
                    try {
                        if (source.startsWith(activeThumbnailsDirectory)) {
                            relativePath = activeThumbnailsDirectory.relativize(source);
                        } else {
                            // Use just the filename if path is not under active directory
                            relativePath = source.getFileName();
                        }
                    } catch (IllegalArgumentException e) {
                        // Fallback to just filename if relativize fails
                        relativePath = source.getFileName();
                    }
                    
                    Path target = trashThumbnailsDirectory.resolve(relativePath);
                    
                    // Create parent directories in trash if needed
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    
                    // Move file to trash
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    logger.debug("Moved thumbnail to trash: {} -> {}", source, target);
                } catch (IOException e) {
                    logger.error("Failed to move thumbnail to trash: {}", thumbnailPath, e);
                    success = false;
                }
            } else {
                logger.debug("Thumbnail file does not exist, may already be in trash: {}", thumbnailPath);
            }
        }
        
        return success;
    }
    
    /**
     * Moves all images for a chat to trash.
     * 
     * @param chatId the chat ID
     * @param images list of image paths
     * @param thumbnails list of thumbnail paths
     * @return number of files successfully moved
     */
    public int moveChatToTrash(String chatId, List<String> images, List<String> thumbnails) {
        int movedCount = 0;
        
        // Move all images
        for (int i = 0; i < images.size(); i++) {
            String imagePath = images.get(i);
            String thumbnailPath = (i < thumbnails.size()) ? thumbnails.get(i) : null;
            
            if (moveToTrash(imagePath, thumbnailPath)) {
                movedCount++;
            }
        }
        
        logger.info("Moved {} files to trash for chat: {}", movedCount, chatId);
        return movedCount;
    }
    
    /**
     * Restores an image from trash back to active directory.
     * 
     * @param trashedImagePath path to image in trash
     * @param trashedThumbnailPath path to thumbnail in trash
     * @return true if successfully restored
     */
    public boolean restoreFromTrash(String trashedImagePath, String trashedThumbnailPath) {
        boolean success = true;
        
        // Restore main image
        if (trashedImagePath != null) {
            Path source = Paths.get(trashedImagePath);
            if (Files.exists(source)) {
                try {
                    Path relativePath;
                    try {
                        if (source.startsWith(trashImagesDirectory)) {
                            relativePath = trashImagesDirectory.relativize(source);
                        } else {
                            // Use just the filename if not under trash directory
                            relativePath = source.getFileName();
                        }
                    } catch (IllegalArgumentException e) {
                        relativePath = source.getFileName();
                    }
                    
                    Path target = activeImagesDirectory.resolve(relativePath);
                    
                    // Create parent directories if needed
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    
                    // Move file back to active
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    logger.debug("Restored image from trash: {} -> {}", source, target);
                } catch (IOException e) {
                    logger.error("Failed to restore image from trash: {}", trashedImagePath, e);
                    success = false;
                }
            } else {
                logger.debug("Image not found in trash: {}", trashedImagePath);
            }
        }
        
        // Restore thumbnail
        if (trashedThumbnailPath != null) {
            Path source = Paths.get(trashedThumbnailPath);
            if (Files.exists(source)) {
                try {
                    Path relativePath;
                    try {
                        if (source.startsWith(trashThumbnailsDirectory)) {
                            relativePath = trashThumbnailsDirectory.relativize(source);
                        } else {
                            // Use just the filename if not under trash directory
                            relativePath = source.getFileName();
                        }
                    } catch (IllegalArgumentException e) {
                        relativePath = source.getFileName();
                    }
                    
                    Path target = activeThumbnailsDirectory.resolve(relativePath);
                    
                    // Create parent directories if needed
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    
                    // Move file back to active
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    logger.debug("Restored thumbnail from trash: {} -> {}", source, target);
                } catch (IOException e) {
                    logger.error("Failed to restore thumbnail from trash: {}", trashedThumbnailPath, e);
                    success = false;
                }
            } else {
                logger.debug("Thumbnail not found in trash: {}", trashedThumbnailPath);
            }
        }
        
        return success;
    }
    
    /**
     * Permanently deletes files from trash.
     * 
     * @param trashedImagePath path to image in trash
     * @param trashedThumbnailPath path to thumbnail in trash
     * @return true if successfully deleted
     */
    public boolean permanentlyDelete(String trashedImagePath, String trashedThumbnailPath) {
        boolean success = true;
        
        // Delete image
        if (trashedImagePath != null) {
            try {
                Path path = Paths.get(trashedImagePath);
                if (Files.exists(path)) {
                    Files.delete(path);
                    logger.debug("Permanently deleted image: {}", trashedImagePath);
                }
            } catch (IOException e) {
                logger.error("Failed to permanently delete image: {}", trashedImagePath, e);
                success = false;
            }
        }
        
        // Delete thumbnail
        if (trashedThumbnailPath != null) {
            try {
                Path path = Paths.get(trashedThumbnailPath);
                if (Files.exists(path)) {
                    Files.delete(path);
                    logger.debug("Permanently deleted thumbnail: {}", trashedThumbnailPath);
                }
            } catch (IOException e) {
                logger.error("Failed to permanently delete thumbnail: {}", trashedThumbnailPath, e);
                success = false;
            }
        }
        
        return success;
    }
    
    /**
     * Cleans up old items in trash that exceed retention period.
     * This should be called periodically (e.g., on app startup).
     * 
     * @return CompletableFuture with number of files deleted
     */
    public CompletableFuture<Integer> cleanupOldTrashItems() {
        return CompletableFuture.supplyAsync(() -> {
            int deletedCount = 0;
            LocalDateTime cutoffDate = LocalDateTime.now().minus(trashRetentionDays, ChronoUnit.DAYS);
            
            try {
                // Clean up old images
                deletedCount += cleanupDirectory(trashImagesDirectory, cutoffDate);
                
                // Clean up old thumbnails
                deletedCount += cleanupDirectory(trashThumbnailsDirectory, cutoffDate);
                
                logger.info("Cleanup completed: {} old files removed from trash", deletedCount);
            } catch (IOException e) {
                logger.error("Failed to cleanup old trash items", e);
            }
            
            return deletedCount;
        }, executorService);
    }
    
    /**
     * Cleans up old files in a directory.
     */
    private int cleanupDirectory(Path directory, LocalDateTime cutoffDate) throws IOException {
        int deletedCount = 0;
        
        if (!Files.exists(directory)) {
            return 0;
        }
        
        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> filesToDelete = paths
                .filter(Files::isRegularFile)
                .filter(path -> {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                        LocalDateTime lastModified = LocalDateTime.ofInstant(
                            attrs.lastModifiedTime().toInstant(),
                            java.time.ZoneId.systemDefault()
                        );
                        return lastModified.isBefore(cutoffDate);
                    } catch (IOException e) {
                        logger.warn("Could not read attributes for: {}", path, e);
                        return false;
                    }
                })
                .toList();
            
            for (Path file : filesToDelete) {
                try {
                    Files.delete(file);
                    deletedCount++;
                    logger.trace("Deleted old trash file: {}", file);
                } catch (IOException e) {
                    logger.warn("Failed to delete old trash file: {}", file, e);
                }
            }
        }
        
        return deletedCount;
    }
    
    /**
     * Migrates existing deleted chats to trash directory.
     * This is called once after the migration to move already-deleted items.
     * 
     * @param imagePaths list of image paths to migrate
     * @param thumbnailPaths list of thumbnail paths to migrate
     * @return number of files migrated
     */
    public int migrateDeletedChatsToTrash(List<String> imagePaths, List<String> thumbnailPaths) {
        int migratedCount = 0;
        
        logger.info("Starting migration of {} deleted items to trash", imagePaths.size());
        
        for (int i = 0; i < imagePaths.size(); i++) {
            String imagePath = imagePaths.get(i);
            String thumbnailPath = (i < thumbnailPaths.size()) ? thumbnailPaths.get(i) : null;
            
            if (moveToTrash(imagePath, thumbnailPath)) {
                migratedCount++;
            }
        }
        
        logger.info("Migration complete: {} files moved to trash", migratedCount);
        return migratedCount;
    }
    
    /**
     * Gets the trash path for a given active file path.
     */
    public String getTrashPath(String activePath, boolean isThumbnail) {
        if (activePath == null || activePath.isEmpty()) {
            return null;
        }
        
        Path source = Paths.get(activePath);
        Path activeDir = isThumbnail ? activeThumbnailsDirectory : activeImagesDirectory;
        Path trashDir = isThumbnail ? trashThumbnailsDirectory : trashImagesDirectory;
        
        try {
            Path relativePath;
            if (source.startsWith(activeDir)) {
                relativePath = activeDir.relativize(source);
            } else {
                // Use just the filename if path is not under active directory
                relativePath = source.getFileName();
            }
            return trashDir.resolve(relativePath).toString();
        } catch (IllegalArgumentException e) {
            // Fallback to just filename in trash
            Path fileName = source.getFileName();
            if (fileName != null) {
                return trashDir.resolve(fileName).toString();
            }
            logger.warn("Could not determine trash path for: {}", activePath);
            return null;
        }
    }
    
    /**
     * Shuts down the executor service.
     * 
     * <p>This method should be called during application shutdown to ensure
     * all pending async operations are completed gracefully.</p>
     * 
     * <p><strong>Thread-safety:</strong> This method is thread-safe but should
     * only be called once during application shutdown.</p>
     */
    public void shutdown() {
        executorService.shutdown();
    }
}