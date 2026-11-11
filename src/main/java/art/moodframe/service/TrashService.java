package art.moodframe.service;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for managing soft-deleted items (trash).
 * 
 * @since 1.0.0
 */
public interface TrashService {

    /**
     * Moves files to trash directory.
     * 
     * @param imagePaths     list of image paths to move to trash
     * @param thumbnailPaths list of thumbnail paths to move to trash
     * @return CompletableFuture that completes when files are moved
     */
    CompletableFuture<Void> moveToTrash(java.util.List<String> imagePaths, java.util.List<String> thumbnailPaths);

    /**
     * Restores files from trash.
     * 
     * @param imagePaths     list of image paths to restore
     * @param thumbnailPaths list of thumbnail paths to restore
     * @return CompletableFuture that completes when files are restored
     */
    CompletableFuture<Void> restoreFromTrash(java.util.List<String> imagePaths, java.util.List<String> thumbnailPaths);

    /**
     * Permanently deletes files from trash.
     * 
     * @param imagePaths     list of image paths to delete
     * @param thumbnailPaths list of thumbnail paths to delete
     * @return CompletableFuture that completes when files are deleted
     */
    CompletableFuture<Void> permanentlyDelete(java.util.List<String> imagePaths, java.util.List<String> thumbnailPaths);

    /**
     * Empties the entire trash directory.
     * 
     * @return CompletableFuture with number of files deleted
     */
    CompletableFuture<Integer> emptyTrash();

    /**
     * Gets the retention period in days.
     * 
     * @return retention period in days
     */
    int getRetentionPeriod();
}