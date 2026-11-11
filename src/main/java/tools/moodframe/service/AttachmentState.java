package tools.moodframe.service;

import tools.moodframe.model.AttachedImage;
import tools.moodframe.model.GenerationModel;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for managing the state of image attachments.
 * 
 * @since 1.0.0
 */
public interface AttachmentState {
    
    /**
     * Sets the current attachment.
     * 
     * @param attachment the attachment to set
     */
    void setAttachment(AttachedImage attachment);
    
    /**
     * Gets the current attachment.
     * 
     * @return the current attachment, or null if none
     */
    AttachedImage getAttachment();
    
    /**
     * Gets the attachment property for binding.
     * 
     * @return the attachment property
     */
    ObjectProperty<AttachedImage> attachmentProperty();
    
    /**
     * Checks if there is an attachment.
     * 
     * @return true if there is an attachment
     */
    boolean hasAttachment();
    
    /**
     * Gets the has attachment property for binding.
     * 
     * @return the has attachment property
     */
    BooleanProperty hasAttachmentProperty();
    
    /**
     * Removes the current attachment.
     */
    void removeAttachment();
    
    /**
     * Gets the list of compatible models for the current state.
     * 
     * @return list of available models
     */
    List<GenerationModel> getAvailableModels();
    
    /**
     * Checks if a model is compatible with the current state.
     * 
     * @param model the model to check
     * @return true if the model is compatible
     */
    boolean isModelCompatible(GenerationModel model);
    
    /**
     * Gets the default compatible model for the current state.
     * 
     * @return the default compatible model
     */
    GenerationModel getDefaultCompatibleModel();
    
    /**
     * Sets callback for model filter changes.
     * 
     * @param callback the callback to invoke with filtered models
     */
    void setOnModelFilterChanged(Consumer<List<GenerationModel>> callback);
    
    /**
     * Sets callback for automatic model selection.
     * 
     * @param callback the callback to invoke with selected model
     */
    void setOnAutoModelSelection(Consumer<GenerationModel> callback);
    
    /**
     * Sets callback for image count changes.
     * 
     * @param callback the callback to invoke with image count
     */
    void setOnImageCountChanged(Consumer<Integer> callback);
    
    /**
     * Sets callback for attachment state changes.
     * 
     * @param callback the callback to invoke on state change
     */
    void setOnAttachmentStateChanged(Runnable callback);
    
    /**
     * Validates if generation can proceed with the given model.
     * 
     * @param selectedModel the selected model
     * @return validation result
     */
    ValidationResult validateForGeneration(GenerationModel selectedModel);
    
    /**
     * Validation result for generation checks.
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