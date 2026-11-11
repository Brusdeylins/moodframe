package tools.moodframe.ui.component;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import tools.moodframe.config.ServiceFactory;
import tools.moodframe.service.StylePresetService;
import tools.moodframe.util.I18nChangeListener;
import tools.moodframe.util.I18nManager;

/**
 * Dialog for adding and editing Style Presets.
 * 
 * <p>
 * This dialog provides a user interface for creating new style presets or
 * editing existing ones. It includes fields for the style name and prompt text,
 * with validation to ensure required fields are filled.
 * </p>
 * 
 * <p>
 * <strong>Phase 1 Implementation:</strong> This dialog works with mock data
 * and provides immediate UI feedback. In Phase 2, it will be integrated with
 * persistence layer for permanent storage.
 * </p>
 * 
 * @since 1.0.0
 */
public class StyleEditDialog extends Dialog<StyleEditDialog.StyleEditResult> implements I18nChangeListener {

    private final TextField nameField;
    private final TextArea promptTextArea;
    private final boolean isEdit;
    private final String originalName; // For edit mode - the name we're editing
    private final StylePresetService styleService;

    // UI Elements for localization
    private Label nameLabel;
    private Label promptLabel;
    private Label helpLabel;
    private Label validationLabel;

    /**
     * Result record for style edit operations.
     * 
     * @param name       the style name
     * @param promptText the style prompt text
     */
    public record StyleEditResult(String name, String promptText) {
    }

    /**
     * Creates a new StyleEditDialog with the specified title and initial values.
     *
     * @param title             the dialog title
     * @param initialName       the initial name value (empty for new styles)
     * @param initialPromptText the initial prompt text (empty for new styles)
     */
    public StyleEditDialog(String title, String initialName, String initialPromptText) {
        this.isEdit = !initialName.isEmpty();
        this.originalName = initialName;
        this.styleService = ServiceFactory.getInstance().getService(StylePresetService.class);

        // Register for i18n updates
        I18nManager.addChangeListener(this);

        // Set initial title and header
        updateTexts();

        // Create the dialog pane
        DialogPane dialogPane = getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Create the content grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // Style name field
        nameField = new TextField();
        nameField.setText(initialName);
        nameField.setPrefWidth(400);
        GridPane.setHgrow(nameField, Priority.ALWAYS);

        // Prompt text area
        promptTextArea = new TextArea();
        promptTextArea.setText(initialPromptText);
        promptTextArea.setPrefRowCount(4);
        promptTextArea.setWrapText(true);
        GridPane.setHgrow(promptTextArea, Priority.ALWAYS);
        GridPane.setVgrow(promptTextArea, Priority.ALWAYS);

        // Create labels with proper width
        nameLabel = new Label();
        nameLabel.setPrefWidth(100);
        nameLabel.setMinWidth(100);

        promptLabel = new Label();
        promptLabel.setPrefWidth(100);
        promptLabel.setMinWidth(100);

        // Add components to grid
        grid.add(nameLabel, 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(promptLabel, 0, 1);
        grid.add(promptTextArea, 1, 1);

        // Add validation message label (initially hidden)
        validationLabel = new Label();
        validationLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #d32f2f;");
        validationLabel.setWrapText(true);
        validationLabel.setPrefWidth(400);
        validationLabel.setVisible(false);
        validationLabel.setManaged(false);
        GridPane.setHgrow(validationLabel, Priority.ALWAYS);
        grid.add(validationLabel, 1, 2);

        // Add help text
        helpLabel = new Label();
        helpLabel.setStyle("-fx-font-size: 11px;");
        helpLabel.setWrapText(true);
        helpLabel.setPrefWidth(400);
        GridPane.setHgrow(helpLabel, Priority.ALWAYS);
        grid.add(helpLabel, 1, 3);

        // Set localized text for all labels
        updateTexts();

        dialogPane.setContent(grid);

        // Enable/disable OK button based on name field
        Button okButton = (Button) dialogPane.lookupButton(ButtonType.OK);
        okButton.setDisable(initialName.trim().isEmpty());

        // Add validation listener
        nameField.textProperty().addListener((obs, oldText, newText) -> {
            validateName(newText.trim(), okButton);
        });

        // Focus on name field
        Platform.runLater(() -> nameField.requestFocus());

        // Set result converter
        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                String name = nameField.getText().trim();
                String promptText = promptTextArea.getText().trim();

                if (name.isEmpty()) {
                    return null; // Should not happen due to validation
                }

                return new StyleEditResult(name, promptText);
            }
            return null;
        });
    }

    /**
     * Validates the name field and updates the OK button and validation message.
     *
     * @param name     the name to validate
     * @param okButton the OK button to enable/disable
     */
    private void validateName(String name, Button okButton) {
        if (name.isEmpty()) {
            okButton.setDisable(true);
            hideValidationMessage();
            return;
        }

        // Check if name conflicts with a default style (only for new styles or name changes)
        if (styleService != null) {
            boolean isNameChanged = !name.equals(originalName);

            if (isNameChanged && styleService.isDefaultStyle(name)) {
                // Name conflicts with a default style
                okButton.setDisable(true);
                showValidationMessage(I18nManager.getText("style.dialog.validation.default_exists",
                        "This name is already used by a default style."));
                return;
            }

            if (isNameChanged && styleService.styleExists(name)) {
                // Name conflicts with another custom style
                okButton.setDisable(true);
                showValidationMessage(I18nManager.getText("style.dialog.validation.name_exists",
                        "A style with this name already exists."));
                return;
            }
        }

        // Name is valid
        okButton.setDisable(false);
        hideValidationMessage();
    }

    /**
     * Shows a validation error message.
     */
    private void showValidationMessage(String message) {
        validationLabel.setText(message);
        validationLabel.setVisible(true);
        validationLabel.setManaged(true);
    }

    /**
     * Hides the validation error message.
     */
    private void hideValidationMessage() {
        validationLabel.setVisible(false);
        validationLabel.setManaged(false);
    }
    
    /**
     * Updates all text elements with localized strings.
     */
    private void updateTexts() {
        // Set dialog title and header
        setTitle(I18nManager.getText(isEdit ? "style.dialog.edit.title" : "style.dialog.add.title"));
        setHeaderText(I18nManager.getText("style.dialog.header"));
        
        // Update labels
        if (nameLabel != null) {
            nameLabel.setText(I18nManager.getText("style.dialog.name.label"));
        }
        if (promptLabel != null) {
            promptLabel.setText(I18nManager.getText("style.dialog.prompt.label"));
        }
        if (helpLabel != null) {
            helpLabel.setText(I18nManager.getText("style.dialog.help"));
        }
        
        // Update prompt texts
        if (nameField != null) {
            nameField.setPromptText(I18nManager.getText("style.dialog.name.placeholder"));
        }
        if (promptTextArea != null) {
            promptTextArea.setPromptText(I18nManager.getText("style.dialog.prompt.placeholder"));
        }
    }
    
    @Override
    public void onLanguageChanged() {
        Platform.runLater(this::updateTexts);
    }
}