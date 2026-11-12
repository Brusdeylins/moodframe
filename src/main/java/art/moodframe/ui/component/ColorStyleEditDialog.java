package art.moodframe.ui.component;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import art.moodframe.config.ServiceFactory;
import art.moodframe.model.ColorStyle;
import art.moodframe.service.ColorStyleService;
import art.moodframe.util.I18nChangeListener;
import art.moodframe.util.I18nManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for adding and editing Color Styles.
 *
 * <p>This dialog provides a user interface for creating new color styles or
 * editing existing ones. It includes a name field and 5 clickable color boxes
 * that open color pickers. A palette of the user is saved in place or as a new one;
 * a default palette stays unchanged, the changes become a new palette.</p>
 *
 * <p>Each color box shows the selected color or a crossed-out state if no color
 * is defined. Colors can be cleared individually.</p>
 *
 * @since 1.3.0
 */
public class ColorStyleEditDialog extends Dialog<ColorStyleEditDialog.ColorStyleEditResult>
        implements I18nChangeListener {

    private final TextField nameField;
    private final ColorPickerBox[] colorPickers;
    private final boolean isEdit;
    private final String originalName;
    private final ColorStyleService colorStyleService;
    private final ButtonType saveButtonType;
    private final ButtonType saveAsNewButtonType;

    // UI Elements for localization
    private Label nameLabel;
    private Label colorsLabel;
    private Label helpLabel;
    private Label validationLabel;

    /**
     * Result record for color style edit operations.
     *
     * @param save   how the palette is stored
     * @param name   the style name
     * @param colors the list of hex color codes (1-5 colors)
     */
    public record ColorStyleEditResult(BlockEditDialog.Save save, String name, List<String> colors) {
    }

    /**
     * Creates a new ColorStyleEditDialog for a palette.
     *
     * @param style the palette to edit, null for a new one
     */
    public ColorStyleEditDialog(ColorStyle style) {
        String initialName = style != null ? style.getName() : "";
        List<String> initialColors = style != null ? style.getColors() : null;
        this.isEdit = style != null;
        this.originalName = initialName;
        this.colorPickers = new ColorPickerBox[5];
        this.colorStyleService = ServiceFactory.getInstance().getService(ColorStyleService.class);

        // Register for i18n updates
        I18nManager.addChangeListener(this);

        /*  A new palette and a palette of the user are saved, a default palette only as a new one: then "As New
            Entry" is the main button, else it stands at the far left  */
        DialogPane dialogPane = getDialogPane();
        saveButtonType = style == null || !style.isDefault() ? new ButtonType("", ButtonBar.ButtonData.OK_DONE) : null;
        saveAsNewButtonType = style != null ? new ButtonType("", saveButtonType != null ? ButtonBar.ButtonData.LEFT
                : ButtonBar.ButtonData.OK_DONE) : null;
        if (saveAsNewButtonType != null)
            dialogPane.getButtonTypes().add(saveAsNewButtonType);
        if (saveButtonType != null)
            dialogPane.getButtonTypes().add(saveButtonType);
        dialogPane.getButtonTypes().add(ButtonType.CANCEL);

        // Create the content grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new Insets(20, 20, 10, 20));

        // Style name field
        nameLabel = new Label();
        nameLabel.setPrefWidth(80);
        nameLabel.setMinWidth(80);

        nameField = new TextField();
        nameField.setText(initialName);
        nameField.setPrefWidth(300);
        GridPane.setHgrow(nameField, Priority.ALWAYS);

        grid.add(nameLabel, 0, 0);
        grid.add(nameField, 1, 0);

        // Validation message label (initially hidden)
        validationLabel = new Label();
        validationLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #d32f2f;");
        validationLabel.setWrapText(true);
        validationLabel.setPrefWidth(300);
        validationLabel.setVisible(false);
        validationLabel.setManaged(false);
        GridPane.setHgrow(validationLabel, Priority.ALWAYS);
        grid.add(validationLabel, 1, 1);

        // Color pickers row
        colorsLabel = new Label();
        colorsLabel.setPrefWidth(80);
        colorsLabel.setMinWidth(80);

        HBox colorPickersBox = new HBox(10);
        colorPickersBox.setAlignment(Pos.CENTER_LEFT);

        for (int i = 0; i < 5; i++) {
            String initialColor = (initialColors != null && i < initialColors.size())
                    ? initialColors.get(i)
                    : null;
            colorPickers[i] = new ColorPickerBox(i + 1, initialColor);
            colorPickersBox.getChildren().add(colorPickers[i]);
        }

        grid.add(colorsLabel, 0, 2);
        grid.add(colorPickersBox, 1, 2);

        // Help text
        helpLabel = new Label();
        helpLabel.setStyle("-fx-font-size: 11px;");
        helpLabel.setWrapText(true);
        helpLabel.setPrefWidth(300);
        GridPane.setHgrow(helpLabel, Priority.ALWAYS);
        grid.add(helpLabel, 1, 3);

        dialogPane.setContent(grid);

        // Set localized text for all labels and buttons
        updateTexts();

        validateName(initialName.trim());
        nameField.textProperty().addListener((obs, oldText, newText) -> validateName(newText.trim()));

        // Focus on name field
        Platform.runLater(() -> nameField.requestFocus());

        // Set result converter
        setResultConverter(buttonType -> {
            if (buttonType == null || buttonType == ButtonType.CANCEL) {
                return null;
            }

            List<String> colors = new ArrayList<>();
            for (ColorPickerBox picker : colorPickers) {
                if (picker.hasColor()) {
                    colors.add(picker.getHexColor());
                }
            }
            return new ColorStyleEditResult(buttonType == saveButtonType ? BlockEditDialog.Save.SAVE
                    : BlockEditDialog.Save.SAVE_AS_NEW, nameField.getText().trim(), colors);
        });
    }

    /**
     * Validates the name: it must not be empty, and saving in place needs a name no other palette has
     * (a palette in the trash keeps its name). Saving as a new palette takes the next free name instead.
     *
     * @param name the name to validate
     */
    private void validateName(String name) {
        boolean taken = !name.isEmpty() && !name.equals(originalName) && colorStyleService != null
                && colorStyleService.styleExists(name);

        if (saveButtonType != null)
            getDialogPane().lookupButton(saveButtonType).setDisable(name.isEmpty() || taken);
        if (saveAsNewButtonType != null)
            getDialogPane().lookupButton(saveAsNewButtonType).setDisable(name.isEmpty());

        if (taken)
            showValidationMessage(I18nManager.getText(saveAsNewButtonType != null
                    ? "block.dialog.validation.taken.copy" : "block.dialog.validation.taken"));
        else
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
        setTitle(I18nManager.getText(isEdit ? "colorstyle.dialog.edit.title" : "colorstyle.dialog.add.title"));
        setHeaderText(isEdit && saveButtonType == null
                ? I18nManager.getText("block.dialog.header.default", originalName)
                : I18nManager.getText("colorstyle.dialog.header"));
        if (saveButtonType != null)
            ((Button) getDialogPane().lookupButton(saveButtonType)).setText(I18nManager.getText("block.dialog.save"));
        if (saveAsNewButtonType != null)
            ((Button) getDialogPane().lookupButton(saveAsNewButtonType)).setText(
                    I18nManager.getText("block.dialog.save.new"));

        if (nameLabel != null) {
            nameLabel.setText(I18nManager.getText("colorstyle.dialog.name"));
        }
        if (colorsLabel != null) {
            colorsLabel.setText(I18nManager.getText("colorstyle.dialog.colors"));
        }
        if (helpLabel != null) {
            helpLabel.setText(I18nManager.getText("colorstyle.dialog.help"));
        }
        if (nameField != null) {
            nameField.setPromptText(I18nManager.getText("colorstyle.dialog.name.placeholder"));
        }
    }

    @Override
    public void onLanguageChanged() {
        Platform.runLater(this::updateTexts);
    }

    /**
     * Inner class: Clickable color box with color picker popup.
     */
    private static class ColorPickerBox extends VBox {

        private final ColorBox colorBox;
        private final Label numberLabel;
        private final Button clearButton;
        private String hexColor;
        private final int colorNumber;

        public ColorPickerBox(int number, String initialColor) {
            this.colorNumber = number;
            this.hexColor = initialColor;

            setAlignment(Pos.CENTER);
            setSpacing(3);

            // Larger color box for the editor (24x24)
            colorBox = new ColorBox(initialColor, 24);
            colorBox.setStyle("-fx-cursor: hand;");
            colorBox.setOnMouseClicked(e -> showColorPicker());

            // Number label below the box
            numberLabel = new Label(String.valueOf(number));
            numberLabel.setStyle("-fx-font-size: 10px;");

            // Clear button (small X)
            clearButton = new Button("x");
            clearButton.setStyle("-fx-font-size: 8px; -fx-padding: 0 3 0 3; -fx-min-width: 16px; -fx-min-height: 14px;");
            clearButton.setOnAction(e -> clearColor());
            clearButton.setVisible(hexColor != null && !hexColor.isEmpty());

            getChildren().addAll(colorBox, numberLabel, clearButton);
        }

        private void showColorPicker() {
            ColorPicker picker = new ColorPicker();
            if (hexColor != null && !hexColor.isEmpty()) {
                try {
                    picker.setValue(Color.web(hexColor));
                } catch (Exception ignored) {
                    picker.setValue(Color.WHITE);
                }
            } else {
                picker.setValue(Color.WHITE);
            }

            Dialog<Color> dialog = new Dialog<>();
            dialog.setTitle(I18nManager.getText("colorstyle.picker.title") + " " + colorNumber);
            dialog.setHeaderText(I18nManager.getText("colorstyle.picker.header"));

            VBox content = new VBox(10);
            content.setPadding(new Insets(10));
            content.setAlignment(Pos.CENTER);
            content.getChildren().add(picker);

            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            dialog.setResultConverter(bt -> {
                if (bt == ButtonType.OK) {
                    return picker.getValue();
                }
                return null;
            });

            dialog.showAndWait().ifPresent(color -> {
                hexColor = String.format("#%02X%02X%02X",
                        (int) (color.getRed() * 255),
                        (int) (color.getGreen() * 255),
                        (int) (color.getBlue() * 255));
                colorBox.setColor(hexColor);
                clearButton.setVisible(true);
            });
        }

        private void clearColor() {
            hexColor = null;
            colorBox.clearColor();
            clearButton.setVisible(false);
        }

        public boolean hasColor() {
            return hexColor != null && !hexColor.isEmpty();
        }

        public String getHexColor() {
            return hexColor;
        }
    }
}
