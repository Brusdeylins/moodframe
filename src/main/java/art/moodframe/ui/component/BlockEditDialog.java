package art.moodframe.ui.component;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import art.moodframe.config.ServiceFactory;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.PromptBlockService;
import art.moodframe.util.I18nChangeListener;
import art.moodframe.util.I18nManager;

import java.util.Locale;

/**
 * Dialog for creating and editing a text building block: a style, an atmosphere or a light.
 *
 * <p>
 * It shows the name and the prompt fragment, for a style also whether it is photographic. A block of the user is
 * saved in place or as a new block; a standard block stays unchanged, the changes become a new block.
 * </p>
 *
 * @since 1.0.0
 */
public class BlockEditDialog extends Dialog<BlockEditDialog.Result> implements I18nChangeListener {

    /** How the block is stored. */
    public enum Save {
        /** A new block, or the edited block of the user in place. */
        SAVE,
        /** A new block next to the edited one, under the next free name if the name is taken. */
        SAVE_AS_NEW
    }

    /**
     * Result of the dialog.
     *
     * @param save         how the block is stored
     * @param name         the name
     * @param text         the prompt fragment
     * @param photographic whether a style describes a photograph
     */
    public record Result(Save save, String name, String text, boolean photographic) {
    }

    private final PromptBlock.Type type;
    private final PromptBlock block;
    private final TextField nameField;
    private final TextArea promptTextArea;
    private final CheckBox photographicCheckBox;
    private final PromptBlockService blockService;
    private final ButtonType saveButtonType;
    private final ButtonType saveAsNewButtonType;

    // UI Elements for localization
    private Label nameLabel;
    private Label promptLabel;
    private Label helpLabel;
    private Label validationLabel;

    /**
     * Creates the dialog.
     *
     * @param type  the block type
     * @param block the block to edit, null for a new one
     */
    public BlockEditDialog(PromptBlock.Type type, PromptBlock block) {
        this.type = type;
        this.block = block;
        this.blockService = ServiceFactory.getInstance().getService(PromptBlockService.class);

        // Register for i18n updates
        I18nManager.addChangeListener(this);

        /*  A new block and a block of the user are saved, a standard block only as a new one  */
        DialogPane dialogPane = getDialogPane();
        saveButtonType = block == null || !block.isDefault() ? new ButtonType("", ButtonBar.ButtonData.OK_DONE) : null;
        saveAsNewButtonType = block != null ? new ButtonType("", ButtonBar.ButtonData.OTHER) : null;
        if (saveAsNewButtonType != null)
            dialogPane.getButtonTypes().add(saveAsNewButtonType);
        if (saveButtonType != null)
            dialogPane.getButtonTypes().add(saveButtonType);
        dialogPane.getButtonTypes().add(ButtonType.CANCEL);

        // Create the content grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));

        // Name field
        nameField = new TextField(block != null ? block.getName() : "");
        nameField.setPrefWidth(400);
        GridPane.setHgrow(nameField, Priority.ALWAYS);

        // Prompt fragment
        promptTextArea = new TextArea(block != null ? block.getText() : "");
        promptTextArea.setPrefRowCount(4);
        promptTextArea.setWrapText(true);
        GridPane.setHgrow(promptTextArea, Priority.ALWAYS);
        GridPane.setVgrow(promptTextArea, Priority.ALWAYS);

        /*  Styles of the user count as photographic until deselected  */
        photographicCheckBox = new CheckBox();
        photographicCheckBox.setSelected(type == PromptBlock.Type.STYLE && (block == null || block.isPhotographic()));
        photographicCheckBox.setWrapText(true);
        photographicCheckBox.setPrefWidth(400);
        photographicCheckBox.setVisible(type == PromptBlock.Type.STYLE);
        photographicCheckBox.setManaged(type == PromptBlock.Type.STYLE);

        // Create labels that keep their full width
        nameLabel = new Label();
        nameLabel.setMinWidth(Region.USE_PREF_SIZE);

        promptLabel = new Label();
        promptLabel.setMinWidth(Region.USE_PREF_SIZE);

        // Add components to grid
        grid.add(nameLabel, 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(promptLabel, 0, 1);
        grid.add(promptTextArea, 1, 1);
        grid.add(photographicCheckBox, 1, 2);

        // Add validation message label (initially hidden)
        validationLabel = new Label();
        validationLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #d32f2f;");
        validationLabel.setWrapText(true);
        validationLabel.setPrefWidth(400);
        validationLabel.setVisible(false);
        validationLabel.setManaged(false);
        GridPane.setHgrow(validationLabel, Priority.ALWAYS);
        grid.add(validationLabel, 1, 3);

        // Add help text
        helpLabel = new Label();
        helpLabel.setStyle("-fx-font-size: 11px;");
        helpLabel.setWrapText(true);
        helpLabel.setPrefWidth(400);
        GridPane.setHgrow(helpLabel, Priority.ALWAYS);
        grid.add(helpLabel, 1, 4);

        dialogPane.setContent(grid);

        // Set localized text for all labels and buttons
        updateTexts();

        validateName(nameField.getText().trim());
        nameField.textProperty().addListener((obs, oldText, newText) -> validateName(newText.trim()));

        // Focus on name field
        Platform.runLater(() -> nameField.requestFocus());

        setResultConverter((buttonType) -> {
            if (buttonType == null || buttonType == ButtonType.CANCEL)
                return null;
            return new Result(buttonType == saveButtonType ? Save.SAVE : Save.SAVE_AS_NEW, nameField.getText().trim(),
                    promptTextArea.getText().trim(), photographicCheckBox.isSelected());
        });
    }

    /**
     * Validates the name: it must not be empty, and saving in place needs a name no other block has. Saving as a new
     * block takes the next free name instead.
     *
     * @param name the name to validate
     */
    private void validateName(String name) {
        boolean taken = !name.isEmpty() && (block == null || !name.equals(block.getName())) && blockService != null
                && blockService.exists(type, name);

        if (saveButtonType != null)
            getDialogPane().lookupButton(saveButtonType).setDisable(name.isEmpty() || taken);
        if (saveAsNewButtonType != null)
            getDialogPane().lookupButton(saveAsNewButtonType).setDisable(name.isEmpty());

        validationLabel.setText(taken ? I18nManager.getText(saveAsNewButtonType != null
                ? "block.dialog.validation.taken.copy" : "block.dialog.validation.taken") : "");
        validationLabel.setVisible(taken);
        validationLabel.setManaged(taken);
    }

    /**
     * Updates all text elements with localized strings.
     */
    private void updateTexts() {
        String typeKey = type.name().toLowerCase(Locale.ROOT);
        setTitle(block == null ? I18nManager.getText("block.dialog.new." + typeKey)
                : I18nManager.getText("block.dialog.edit", I18nManager.getText("block.type." + typeKey)));
        setHeaderText(block != null && block.isDefault()
                ? I18nManager.getText("block.dialog.header.default", block.getName()) : null);

        nameLabel.setText(I18nManager.getText("block.dialog.name"));
        promptLabel.setText(I18nManager.getText("block.dialog.text"));
        helpLabel.setText(I18nManager.getText("block.dialog.help." + typeKey));
        photographicCheckBox.setText(I18nManager.getText("style.dialog.photographic.label"));
        nameField.setPromptText(I18nManager.getText("block.dialog.name.placeholder"));
        promptTextArea.setPromptText(I18nManager.getText("block.dialog.text.placeholder." + typeKey));

        if (saveButtonType != null)
            ((Button) getDialogPane().lookupButton(saveButtonType)).setText(I18nManager.getText("block.dialog.save"));
        if (saveAsNewButtonType != null)
            ((Button) getDialogPane().lookupButton(saveAsNewButtonType)).setText(
                    I18nManager.getText("block.dialog.save.new"));
    }

    @Override
    public void onLanguageChanged() {
        Platform.runLater(this::updateTexts);
    }
}
