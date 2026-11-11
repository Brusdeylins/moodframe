package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.model.AttachedImage;
import art.moodframe.model.GenerationModel;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Manages the state of image attachments and their impact on model selection
 * and generation parameters. Handles model filtering, automatic parameter
 * adjustments, and UI state synchronization.
 * 
 * @since 1.0.0
 */
public class AttachmentStateManager implements AttachmentState {
    
    private static final Logger logger = LoggerFactory.getLogger(AttachmentStateManager.class);
    
    // Current attachment state
    private final ObjectProperty<AttachedImage> currentAttachment = new SimpleObjectProperty<>();
    private final BooleanProperty hasAttachment = new SimpleBooleanProperty(false);
    
    // Callbacks for UI updates
    private Consumer<List<GenerationModel>> onModelFilterChanged;
    private Consumer<GenerationModel> onAutoModelSelection;
    private Consumer<Integer> onImageCountChanged;
    private Runnable onAttachmentStateChanged;
    
    /**
     * Creates a new AttachmentStateManager.
     */
    public AttachmentStateManager() {
        // Bind hasAttachment to currentAttachment
        hasAttachment.bind(currentAttachment.isNotNull());
        
        // Listen for attachment changes
        currentAttachment.addListener((obs, oldAttachment, newAttachment) -> {
            handleAttachmentChanged(oldAttachment, newAttachment);
        });
    }
    
    /**
     * Sets the current image attachment.
     * 
     * @param attachment the attachment, or null to remove
     */
    @Override
    public void setAttachment(AttachedImage attachment) {
        currentAttachment.set(attachment);
    }
    
    /**
     * Gets the current image attachment.
     * 
     * @return the current attachment, or null if none
     */
    @Override
    public AttachedImage getAttachment() {
        return currentAttachment.get();
    }
    
    /**
     * Gets the attachment property for binding.
     * 
     * @return the attachment property
     */
    @Override
    public ObjectProperty<AttachedImage> attachmentProperty() {
        return currentAttachment;
    }
    
    /**
     * Checks if an attachment is currently loaded.
     * 
     * @return true if an attachment exists
     */
    @Override
    public boolean hasAttachment() {
        return hasAttachment.get();
    }
    
    /**
     * Gets the hasAttachment property for binding.
     * 
     * @return the hasAttachment property
     */
    @Override
    public BooleanProperty hasAttachmentProperty() {
        return hasAttachment;
    }
    
    /**
     * Removes the current attachment.
     */
    @Override
    public void removeAttachment() {
        setAttachment(null);
    }
    
    /**
     * Gets the list of models compatible with the current attachment state.
     * 
     * @return list of available models
     */
    @Override
    public List<GenerationModel> getAvailableModels() {
        if (hasAttachment()) {
            // Only return models that support image attachments
            return GenerationModel.selectable().stream()
                    .filter(GenerationModel::supportsImageAttachment)
                    .toList();
        } else {
            // All models are available when no attachment
            return GenerationModel.selectable();
        }
    }
    
    /**
     * Checks if a specific model is compatible with the current attachment state.
     * 
     * @param model the model to check
     * @return true if the model is compatible
     */
    @Override
    public boolean isModelCompatible(GenerationModel model) {
        if (!hasAttachment()) {
            return true; // All models compatible without attachment
        }
        
        // Check if model supports image attachments
        return model.supportsImageAttachment();
    }
    
    /**
     * Gets the default model to use when an attachment is added.
     * 
     * @return the default compatible model
     */
    @Override
    public GenerationModel getDefaultCompatibleModel() {
        return GenerationModel.getDefault(); // FLUX 3 Image edits attached images
    }
    
    /**
     * Handles automatic parameter adjustments when attachment state changes.
     * 
     * @param oldAttachment the previous attachment
     * @param newAttachment the new attachment
     */
    private void handleAttachmentChanged(AttachedImage oldAttachment, AttachedImage newAttachment) {
        boolean hadAttachment = oldAttachment != null;
        boolean hasNewAttachment = newAttachment != null;
        
        if (!hadAttachment && hasNewAttachment) {
            // Image was attached - apply restrictions
            handleImageAttached();
        } else if (hadAttachment && !hasNewAttachment) {
            // Image was removed - remove restrictions
            handleImageRemoved();
        }
        
        // Notify UI of state change
        if (onAttachmentStateChanged != null) {
            onAttachmentStateChanged.run();
        }
    }
    
    /**
     * Handles the logic when an image is attached.
     */
    private void handleImageAttached() {
        ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        if (config != null && config.getLoggingEnabled()) {
            logger.info("Image attached - applying parameter restrictions");
        }
        
        // Update available models
        List<GenerationModel> compatibleModels = getAvailableModels();
        if (onModelFilterChanged != null) {
            onModelFilterChanged.accept(compatibleModels);
        }
        
        // Auto-select a compatible model if needed
        if (onAutoModelSelection != null) {
            onAutoModelSelection.accept(getDefaultCompatibleModel());
        }
        
        // Set image count to 1 (image editing typically generates single images)
        if (onImageCountChanged != null) {
            onImageCountChanged.accept(1);
        }
    }
    
    /**
     * Handles the logic when an image is removed.
     */
    private void handleImageRemoved() {
        ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        if (config != null && config.getLoggingEnabled()) {
            logger.info("Image removed - removing parameter restrictions");
        }
        
        // Restore all models
        List<GenerationModel> allModels = GenerationModel.selectable();
        if (onModelFilterChanged != null) {
            onModelFilterChanged.accept(allModels);
        }
        
        // Note: Don't auto-change image count when removing attachment
        // User may want to keep their count setting
    }
    
    /**
     * Sets the callback for when the available models list changes.
     * 
     * @param callback callback that receives the new model list
     */
    @Override
    public void setOnModelFilterChanged(Consumer<List<GenerationModel>> callback) {
        this.onModelFilterChanged = callback;
    }
    
    /**
     * Sets the callback for automatic model selection.
     * 
     * @param callback callback that receives the model to select
     */
    @Override
    public void setOnAutoModelSelection(Consumer<GenerationModel> callback) {
        this.onAutoModelSelection = callback;
    }
    
    /**
     * Sets the callback for automatic image count changes.
     * 
     * @param callback callback that receives the new image count
     */
    @Override
    public void setOnImageCountChanged(Consumer<Integer> callback) {
        this.onImageCountChanged = callback;
    }
    
    /**
     * Sets the callback for general attachment state changes.
     * 
     * @param callback callback to run when attachment state changes
     */
    @Override
    public void setOnAttachmentStateChanged(Runnable callback) {
        this.onAttachmentStateChanged = callback;
    }
    
    /**
     * Validates that the current state is compatible for generation.
     * 
     * @param selectedModel the currently selected model
     * @return validation result
     */
    @Override
    public ValidationResult validateForGeneration(GenerationModel selectedModel) {
        if (hasAttachment()) {
            // Ensure the attachment is ready
            AttachedImage attachment = getAttachment();
            if (!attachment.isValid()) {
                String error = attachment.getValidationError();
                return new ValidationResult(false, "Attachment invalid: " + error);
            }
            
            if (!attachment.isReadyForTransmission()) {
                return new ValidationResult(false, "Attachment is not encoded for transmission");
            }
            
            // Ensure the selected model supports attachments
            if (!isModelCompatible(selectedModel)) {
                return new ValidationResult(false, 
                    "Model " + selectedModel.getDisplayName() + " does not support image attachments");
            }
        }
        
        return new ValidationResult(true, "State is valid for generation");
    }
    
    // Keep ValidationResult for backward compatibility
    public static class ValidationResult extends AttachmentState.ValidationResult {
        public ValidationResult(boolean valid, String message) {
            super(valid, message);
        }
        
        @Override
        public String toString() {
            return String.format("ValidationResult{valid=%s, message='%s'}", isValid(), getMessage());
        }
    }
}