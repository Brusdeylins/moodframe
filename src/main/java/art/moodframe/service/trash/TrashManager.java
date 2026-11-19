package art.moodframe.service.trash;

import art.moodframe.config.LibraryPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;

/**
 * Service for managing the trash directory and moving/deleting files.
 * Handles moving files to trash, restoring from trash, and permanent deletion.
 */
public class TrashManager {
    private static final Logger logger = LoggerFactory.getLogger(TrashManager.class);
    
    private final Path trashDirectory;
    private final Path trashImagesDirectory;
    private final Path trashThumbnailsDirectory;
    private final Path activeImagesDirectory;
    private final Path activeThumbnailsDirectory;
    
    private final int trashRetentionDays;
    
    /**
     * Creates a new TrashManager with specified retention period.
     * 
     * <p>It creates no folder: a file moved to the trash or back creates the folders it needs. Files in trash older
     * than the retention period will be automatically deleted.</p>
     *
     * @param paths the folders of the library: images, thumbnails and the trash
     * @param trashRetentionDays number of days to keep items in trash (must be positive)
     */
    public TrashManager(LibraryPaths paths, int trashRetentionDays) {
        this.trashDirectory = paths.trash();
        this.trashImagesDirectory = trashDirectory.resolve("images");
        this.trashThumbnailsDirectory = trashDirectory.resolve("thumbnails");
        this.activeImagesDirectory = paths.images();
        this.activeThumbnailsDirectory = paths.thumbnails();
        this.trashRetentionDays = trashRetentionDays;

        /*  The folders of the trash are created by the first file moved there  */
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
    
}