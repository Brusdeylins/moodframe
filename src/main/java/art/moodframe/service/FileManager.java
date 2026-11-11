package art.moodframe.service;

import art.moodframe.model.GeneratedImage;
import java.nio.file.Path;
import java.util.List;

/**
 * Interface for managing file system operations related to generated images.
 * 
 * @since 1.0.0
 */
public interface FileManager {
    
    /**
     * Gets the recommended file path for storing an image.
     * 
     * @param chatId the chat ID
     * @param entryId the chat entry ID  
     * @param imageId the image ID
     * @param fileExtension the file extension (e.g., "png")
     * @return the recommended file path
     */
    Path getImageFilePath(String chatId, String entryId, String imageId, String fileExtension);
    
    /**
     * Creates the directory structure for storing an image.
     * 
     * @param imagePath the image file path
     * @return true if the directory was created successfully
     */
    boolean ensureImageDirectoryExists(Path imagePath);
    
    /**
     * Deletes a single image file from the file system.
     * 
     * @param image the generated image to delete
     * @return true if the file was deleted successfully or didn't exist
     */
    boolean deleteImageFile(GeneratedImage image);
    
    /**
     * Deletes multiple image files from the file system.
     * 
     * @param images the list of images to delete
     * @return the number of files successfully deleted
     */
    int deleteImageFiles(List<GeneratedImage> images);
    
    /**
     * Deletes all images associated with a chat entry.
     * 
     * @param chatId the chat ID
     * @param entryId the entry ID
     * @return true if all files were deleted successfully
     */
    boolean deleteEntryImages(String chatId, String entryId);
    
    /**
     * Deletes all images associated with a chat.
     * 
     * @param chatId the chat ID
     * @return true if all files were deleted successfully
     */
    boolean deleteChatImages(String chatId);
    
    /**
     * Deletes all thumbnails for a specific chat.
     * 
     * @param chatId the chat ID
     * @param allImagesForChat list of all images belonging to the chat
     */
    void deleteAllThumbnailsForChat(String chatId, List<GeneratedImage> allImagesForChat);
    
    /**
     * Gets the base image directory.
     * 
     * @return the base directory path
     */
    Path getBaseImageDirectory();
    
    /**
     * Gets the total size of all stored images in bytes.
     * 
     * @return the total size in bytes
     */
    long getTotalImageSize();
    
    /**
     * Gets the number of stored image files.
     * 
     * @return the number of image files
     */
    long getImageFileCount();
}