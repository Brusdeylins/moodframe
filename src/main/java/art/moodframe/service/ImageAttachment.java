package art.moodframe.service;

import art.moodframe.model.AttachedImage;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for managing image attachments.
 * 
 * @since 1.0.0
 */
public interface ImageAttachment {
    
    /**
     * Creates an attachment from a file.
     * 
     * @param file the image file
     * @return the created attachment
     * @throws IOException if file cannot be read
     * @throws IllegalArgumentException if file is invalid
     */
    AttachedImage createAttachment(File file) throws IOException, IllegalArgumentException;
    
    /**
     * Encodes an attachment to Base64.
     * 
     * @param attachment the attachment to encode
     * @throws IOException if encoding fails
     */
    void encodeToBase64(AttachedImage attachment) throws IOException;
    
    /**
     * Creates an attachment asynchronously.
     * 
     * @param file the image file
     * @return CompletableFuture with the attachment
     */
    CompletableFuture<AttachedImage> createAttachmentAsync(File file);
    
    /**
     * Creates an attachment task for JavaFX.
     * 
     * @param file the image file
     * @return Task that creates the attachment
     */
    Task<AttachedImage> createAttachmentTask(File file);
    
    /**
     * Creates a preview image for an attachment.
     * 
     * @param attachment the attachment
     * @param maxWidth maximum preview width
     * @param maxHeight maximum preview height
     * @return the preview image
     * @throws IOException if preview cannot be created
     */
    Image createPreviewImage(AttachedImage attachment, double maxWidth, double maxHeight) throws IOException;
    
    /**
     * Validates an image file.
     * 
     * @param file the file to validate
     * @return validation result
     */
    ValidationResult validateFile(File file);
    
    /**
     * Checks if a format is supported.
     * 
     * @param extension the file extension
     * @return true if supported
     */
    boolean isSupportedFormat(String extension);
    
    /**
     * Gets display string for supported formats.
     * 
     * @return supported formats display string
     */
    String getSupportedFormatsDisplay();
    
    /**
     * Gets file chooser filter string.
     * 
     * @return file chooser filter
     */
    String getFileChooserFilter();
    
    /**
     * Attaches an image file.
     * 
     * @param file the image file
     * @return the attached image
     * @throws IOException if attachment fails
     */
    AttachedImage attachImage(File file) throws IOException;
    
    /**
     * Validation result for file validation.
     */
    class ValidationResult {
        private final boolean valid;
        private final String message;
        
        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
    }
}