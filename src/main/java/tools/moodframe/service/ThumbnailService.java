package tools.moodframe.service;

import java.io.File;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for thumbnail generation and management service.
 * 
 * @since 1.0.0
 */
public interface ThumbnailService {
    
    /**
     * Gets or generates a thumbnail for the given image file.
     * 
     * @param originalImageFile the original image file
     * @return CompletableFuture containing the thumbnail file
     */
    CompletableFuture<File> getThumbnail(File originalImageFile);
    
    /**
     * Generates thumbnails for all existing images that don't have thumbnails.
     * 
     * @return CompletableFuture with the count of thumbnails generated
     */
    CompletableFuture<Integer> generateThumbnailsForExistingImages();
    
    /**
     * Cleans up orphaned thumbnails that no longer have original images.
     * 
     * @return CompletableFuture with the count of thumbnails deleted
     */
    CompletableFuture<Integer> cleanupOrphanedThumbnails();
    
    /**
     * Shuts down the thumbnail generation service.
     */
    void shutdown();
    
    /**
     * Gets statistics about thumbnail generation.
     * 
     * @return formatted statistics string
     */
    String getStatistics();
}