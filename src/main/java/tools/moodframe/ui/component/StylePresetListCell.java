package tools.moodframe.ui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

import tools.moodframe.model.StylePresetItem;

/**
 * Custom ListCell for displaying style presets with icon.
 *
 * <p>This cell renderer displays:</p>
 * <ul>
 *   <li>Icon: lock for defaults, brush for custom</li>
 *   <li>Style name</li>
 * </ul>
 *
 * <p>Used in the main window style dropdown.</p>
 *
 * @since 1.3.0
 */
public class StylePresetListCell extends ListCell<StylePresetItem> {

    private final HBox content;
    private final FontIcon icon;
    private final Label nameLabel;

    /**
     * Creates a new StylePresetListCell.
     */
    public StylePresetListCell() {
        super();

        // Icon for default/custom distinction
        icon = new FontIcon();
        icon.getStyleClass().add("ikonli-font-icon");
        icon.setIconSize(14);

        // Name label
        nameLabel = new Label();

        // Content row: Icon + Name
        content = new HBox(6);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(2, 0, 2, 0));
        content.getChildren().addAll(icon, nameLabel);
    }

    @Override
    protected void updateItem(StylePresetItem item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            // Set icon based on whether it's a default style
            if (item.isDefault()) {
                icon.setIconLiteral("mdi2l-lock");
                icon.getStyleClass().removeAll("accent", "success");
                icon.getStyleClass().add("accent");
                nameLabel.setStyle("-fx-font-weight: normal;");
            } else {
                icon.setIconLiteral("mdi2b-brush");
                icon.getStyleClass().removeAll("accent", "success");
                icon.getStyleClass().add("success");
                nameLabel.setStyle("-fx-font-weight: bold;");
            }

            // Set name
            nameLabel.setText(item.getName());

            setText(null);
            setGraphic(content);
        }
    }
}
