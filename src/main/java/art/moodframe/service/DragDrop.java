package art.moodframe.service;

import javafx.beans.property.BooleanProperty;
import java.util.function.Consumer;

/**
 * Interface for handling drag and drop operations.
 * 
 * @since 1.0.0
 */
public interface DragDrop {
    
    /**
     * Drag states for visual feedback.
     */
    enum DragState {
        IDLE("drag-idle"),
        DRAG_OVER_VALID("drag-over-valid"),
        DRAG_OVER_INVALID("drag-over-invalid"),
        SUCCESS("drag-success");
        
        private final String cssClass;
        
        DragState(String cssClass) {
            this.cssClass = cssClass;
        }
        
        public String getCssClass() {
            return cssClass;
        }
    }
    
    /**
     * Initializes drag and drop event handlers.
     */
    void initializeDragAndDrop();
    
    /**
     * Sets callback for state changes.
     * 
     * @param onStateChanged callback to invoke with new state
     */
    void setOnStateChanged(Consumer<DragState> onStateChanged);
    
    /**
     * Gets the current drag state.
     * 
     * @return current drag state
     */
    DragState getCurrentState();
    
    /**
     * Checks if drag is currently over the target.
     * 
     * @return true if drag is over target
     */
    boolean isDragOver();
    
    /**
     * Gets the drag over property for binding.
     * 
     * @return drag over property
     */
    BooleanProperty dragOverProperty();
    
    /**
     * Cleans up resources and removes event handlers.
     */
    void cleanup();
}