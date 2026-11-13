package art.moodframe.ui.component;

import javafx.beans.property.*;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A reusable in-place editing component for text labels.
 * 
 * <p>This component switches between Label (display mode) and TextField (edit mode)
 * on user interaction. It supports double-click-to-edit, Enter to confirm, ESC to cancel,
 * and focus-loss auto-save functionality.</p>
 * 
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * InPlaceEditableLabel editableLabel = new InPlaceEditableLabel("Initial Text");
 * editableLabel.setOnTextChanged(newText -> {
 *     // Handle text change - save to database etc.
 *     System.out.println("Text changed to: " + newText);
 * });
 * }</pre>
 * 
 * <p><strong>Features:</strong></p>
 * <ul>
 * <li>Double-click-to-edit activation</li>
 * <li>Enter key confirms changes</li>
 * <li>ESC key cancels editing</li>
 * <li>Focus-loss auto-saves changes</li>
 * <li>Visual feedback with CSS styling</li>
 * <li>Text validation support</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class InPlaceEditableLabel extends StackPane {
    
    private static final Logger logger = LoggerFactory.getLogger(InPlaceEditableLabel.class);
    
    // UI Components
    private final Label displayLabel;
    private final TextField editField;
    
    // Properties
    private final StringProperty text;
    private final BooleanProperty editMode;
    private final BooleanProperty editable;
    
    // Event handling
    private EventHandler<TextChangeEvent> onTextChanged;
    private EventHandler<EditStartEvent> onEditStart;
    private EventHandler<EditCancelEvent> onEditCancel;
    
    // Validation
    private TextValidator validator;
    
    /**
     * Creates a new in-place editable label with empty text.
     */
    public InPlaceEditableLabel() {
        this("");
    }
    
    /**
     * Creates a new in-place editable label with the specified initial text.
     * 
     * @param initialText the initial text to display
     */
    public InPlaceEditableLabel(String initialText) {
        getStyleClass().add("in-place-editable-label");
        
        // Set alignment for the container
        setAlignment(Pos.CENTER_LEFT);
        
        // Initialize properties
        text = new SimpleStringProperty(initialText != null ? initialText : "");
        editMode = new SimpleBooleanProperty(false);
        editable = new SimpleBooleanProperty(true);
        
        // Create UI components
        displayLabel = createDisplayLabel();
        editField = createEditField();
        
        // Setup initial state
        setupBindings();
        updateVisualState();
    }
    
    /**
     * Creates and configures the display label.
     */
    private Label createDisplayLabel() {
        Label label = new Label();
        label.textProperty().bind(text);
        label.getStyleClass().addAll("display-label", "text-default");
        label.setMaxWidth(Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER_LEFT);
        
        // Improve visual appearance - no border in display mode
        label.setPadding(new javafx.geometry.Insets(2, 4, 2, 4));
        
        // Double-click to edit
        label.setOnMouseClicked(this::handleLabelClick);

        /*  Hover shows the hand cursor only: a background would put the name into a box  */
        label.setOnMouseEntered(e -> {
            if (editable.get())
                label.setCursor(javafx.scene.Cursor.HAND);
        });

        label.setOnMouseExited(e -> {
            label.setCursor(javafx.scene.Cursor.DEFAULT);
        });
        
        return label;
    }
    
    /**
     * Creates and configures the edit text field.
     */
    private TextField createEditField() {
        TextField field = new TextField();
        field.getStyleClass().addAll("edit-field", "text-input");
        field.setMaxWidth(Double.MAX_VALUE);
        
        // Light border for edit mode
        field.setPadding(new javafx.geometry.Insets(2, 4, 2, 4));
        field.setStyle("-fx-border-color: rgba(128, 128, 128, 0.5); -fx-border-width: 1px; -fx-border-radius: 3px; -fx-background-radius: 3px;");
        
        // Key event handling
        field.setOnKeyPressed(this::handleKeyPressed);
        
        // Focus loss handling
        field.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal && editMode.get()) {
                // Focus lost - auto-save
                confirmEdit();
            }
        });
        
        return field;
    }
    
    /**
     * Sets up property bindings and listeners.
     */
    private void setupBindings() {
        // Update visual state when edit mode changes
        editMode.addListener((obs, oldVal, newVal) -> {
            updateVisualState();
            if (newVal) {
                // Entering edit mode
                fireEditStartEvent();
            }
        });
        
        // Disable editing when not editable
        editField.editableProperty().bind(editable);
        displayLabel.disableProperty().bind(editable.not());
    }
    
    /**
     * Updates the visual state based on current mode.
     */
    private void updateVisualState() {
        getChildren().clear();
        
        if (editMode.get()) {
            // Edit mode - show text field
            editField.setText(text.get()); // Set the current text value in the field
            getChildren().add(editField);
            getStyleClass().removeAll("display-mode");
            getStyleClass().add("edit-mode");
            
            // Focus and select text
            Platform.runLater(() -> {
                editField.requestFocus();
                editField.selectAll();
            });
        } else {
            // Display mode - show label
            getChildren().add(displayLabel);
            getStyleClass().removeAll("edit-mode");
            getStyleClass().add("display-mode");
        }
    }
    
    /**
     * Handles mouse clicks on the display label.
     */
    private void handleLabelClick(MouseEvent event) {
        if (event.getClickCount() == 2 && editable.get() && !editMode.get()) {
            startEdit();
            event.consume();
        }
    }
    
    /**
     * Handles key presses in edit mode.
     */
    private void handleKeyPressed(KeyEvent event) {
        if (!editMode.get()) return;
        
        KeyCode code = event.getCode();
        if (code == KeyCode.ENTER) {
            confirmEdit();
            event.consume();
        } else if (code == KeyCode.ESCAPE) {
            cancelEdit();
            event.consume();
        }
    }
    
    /**
     * Starts editing mode.
     */
    public void startEdit() {
        if (!editable.get() || editMode.get()) return;
        
        editMode.set(true);
    }
    
    /**
     * Confirms the edit and saves the new text.
     */
    public void confirmEdit() {
        if (!editMode.get()) return;
        
        String newText = editField.getText() != null ? editField.getText().trim() : "";
        String oldText = text.get();
        
        // Validate text
        if (validator != null) {
            ValidationResult result = validator.validate(newText);
            if (!result.isValid()) {
                logger.warn("Text validation failed: {}", result.getMessage());
                // Keep in edit mode and show error
                editField.getStyleClass().add("error");
                return;
            }
        }
        
        // Clear any error styling
        editField.getStyleClass().remove("error");
        
        // Update text if changed
        boolean textChanged = !newText.equals(oldText);
        if (textChanged) {
            text.set(newText);
            // Fire the text change event so listeners are notified
            fireTextChangeEvent(oldText, newText);
        }
        
        // Exit edit mode
        editMode.set(false);
    }
    
    /**
     * Cancels the edit without saving changes.
     */
    public void cancelEdit() {
        if (!editMode.get()) return;
        
        editMode.set(false);
        fireEditCancelEvent();
    }
    
    // Property accessors
    
    /**
     * Returns the text property.
     */
    public StringProperty textProperty() {
        return text;
    }
    
    /**
     * Gets the current text value.
     */
    public String getText() {
        return text.get();
    }
    
    /**
     * Sets the text value.
     */
    public void setText(String text) {
        this.text.set(text != null ? text : "");
    }
    
    /**
     * Returns the edit mode property.
     */
    public ReadOnlyBooleanProperty editModeProperty() {
        return editMode;
    }
    
    /**
     * Checks if currently in edit mode.
     */
    public boolean isInEditMode() {
        return editMode.get();
    }
    
    /**
     * Returns the editable property.
     */
    public BooleanProperty editableProperty() {
        return editable;
    }
    
    /**
     * Checks if editing is enabled.
     */
    public boolean isEditable() {
        return editable.get();
    }
    
    /**
     * Sets whether editing is enabled.
     */
    public void setEditable(boolean editable) {
        this.editable.set(editable);
    }
    
    // Event handling
    
    /**
     * Sets the text change event handler.
     */
    public void setOnTextChanged(EventHandler<TextChangeEvent> handler) {
        this.onTextChanged = handler;
    }
    
    /**
     * Sets the edit start event handler.
     */
    public void setOnEditStart(EventHandler<EditStartEvent> handler) {
        this.onEditStart = handler;
    }
    
    /**
     * Sets the edit cancel event handler.
     */
    public void setOnEditCancel(EventHandler<EditCancelEvent> handler) {
        this.onEditCancel = handler;
    }
    
    /**
     * Sets the text validator.
     */
    public void setValidator(TextValidator validator) {
        this.validator = validator;
    }
    
    // Event firing methods
    
    private void fireTextChangeEvent(String oldText, String newText) {
        if (onTextChanged != null) {
            TextChangeEvent event = new TextChangeEvent(this, oldText, newText);
            onTextChanged.handle(event);
        }
    }
    
    private void fireEditStartEvent() {
        if (onEditStart != null) {
            EditStartEvent event = new EditStartEvent(this);
            onEditStart.handle(event);
        }
    }
    
    private void fireEditCancelEvent() {
        if (onEditCancel != null) {
            EditCancelEvent event = new EditCancelEvent(this);
            onEditCancel.handle(event);
        }
    }
    
    // Inner classes for events
    
    /**
     * Event fired when text changes.
     */
    public static class TextChangeEvent extends Event {
        public static final EventType<TextChangeEvent> TEXT_CHANGED = new EventType<>("TEXT_CHANGED");
        
        private final String oldText;
        private final String newText;
        
        public TextChangeEvent(Object source, String oldText, String newText) {
            super(source, null, TEXT_CHANGED);
            this.oldText = oldText;
            this.newText = newText;
        }
        
        public String getOldText() { return oldText; }
        public String getNewText() { return newText; }
    }
    
    /**
     * Event fired when edit mode starts.
     */
    public static class EditStartEvent extends Event {
        public static final EventType<EditStartEvent> EDIT_START = new EventType<>("EDIT_START");
        
        public EditStartEvent(Object source) {
            super(source, null, EDIT_START);
        }
    }
    
    /**
     * Event fired when edit is cancelled.
     */
    public static class EditCancelEvent extends Event {
        public static final EventType<EditCancelEvent> EDIT_CANCEL = new EventType<>("EDIT_CANCEL");
        
        public EditCancelEvent(Object source) {
            super(source, null, EDIT_CANCEL);
        }
    }
    
    // Validation interfaces
    
    /**
     * Interface for text validation.
     */
    @FunctionalInterface
    public interface TextValidator {
        ValidationResult validate(String text);
    }
    
    /**
     * Result of text validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        
        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
        
        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }
}