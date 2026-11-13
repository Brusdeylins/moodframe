package art.moodframe.ui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

import art.moodframe.model.StylePresetItem;

/**
 * Custom ListCell for displaying text building blocks (style, atmosphere, light).
 *
 * <p>This cell renderer displays:</p>
 * <ul>
 *   <li>Icon: a brush for the blocks of the user, none for the standard blocks</li>
 *   <li>Block name</li>
 * </ul>
 *
 * <p>Used in the building block lists of the main window, which also show the headers of their groups through
 * {@link BlockChoice}, and in the "Manage…" dialog.</p>
 *
 * @since 1.3.0
 */
public class StylePresetListCell extends ListCell<StylePresetItem> {

    private final HBox content;
    private final FontIcon icon;
    private final Label nameLabel;
    private final BlockChoice<StylePresetItem> choice;

    /**
     * Creates a new StylePresetListCell.
     */
    public StylePresetListCell() {
        this(null);
    }

    /**
     * Creates a cell of a building block list that also draws its group headers.
     *
     * @param choice the list, null for a cell that shows entries only
     */
    public StylePresetListCell(BlockChoice<StylePresetItem> choice) {
        super();
        this.choice = choice;

        // Icon marking the blocks of the user
        icon = new FontIcon("mdi2b-brush");
        icon.getStyleClass().addAll("ikonli-font-icon", "success");
        icon.setIconSize(12);

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

        if (choice != null && choice.render(this, item))
            return;

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            /*  Only the blocks of the user carry the icon, the others keep its space; "None" is no block  */
            icon.setVisible(item.getBlock() != null && !item.getBlock().isDefault());
            nameLabel.setText(item.getName());

            setText(null);
            setGraphic(content);
        }
    }
}
