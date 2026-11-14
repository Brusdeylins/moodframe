package art.moodframe.ui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.javafx.FontIcon;

import art.moodframe.model.StylePresetItem;

/**
 * Custom ListCell for displaying text building blocks (style, atmosphere, light, camera).
 *
 * <p>As a row of a list, this cell renderer displays:</p>
 * <ul>
 *   <li>Icon: a brush for the blocks of the user, none for the standard blocks</li>
 *   <li>Block name</li>
 *   <li>The favorite star at the right ({@link FavoriteStar})</li>
 * </ul>
 *
 * <p>As the button cell of a field, it shows the name only, on the edge of the other fields.</p>
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
    private final FavoriteStar<StylePresetItem> star;

    /**
     * Creates the button cell of a field, which shows the name only.
     */
    public StylePresetListCell() {
        this(null, null);
    }

    /**
     * Creates a row of a building block list, which also draws the group headers of a field.
     *
     * @param choice    the field, null for a list in a dialog or the button cell
     * @param favorites the favorites of the list, null for the button cell
     */
    public StylePresetListCell(BlockChoice<StylePresetItem> choice,
            FavoriteStar.Favorites<StylePresetItem> favorites) {
        super();
        this.choice = choice;

        // Icon marking the blocks of the user
        icon = new FontIcon("mdi2b-brush");
        icon.getStyleClass().addAll("ikonli-font-icon", "success");
        icon.setIconSize(12);

        // Name label
        nameLabel = new Label();

        // Content row: Icon + Name + Star, only the name in the button cell
        content = new HBox(6);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(2, 0, 2, 0));
        content.getChildren().add(nameLabel);
        star = favorites != null ? new FavoriteStar<>(this, favorites) : null;
        if (star != null) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            content.getChildren().add(0, icon);
            content.getChildren().addAll(spacer, star);
            DropdownBounds.showCutName(nameLabel);
        }
    }

    @Override
    protected double computePrefWidth(double height) {
        return DropdownBounds.rowWidth(this, super.computePrefWidth(height));
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
            if (star != null)
                star.show(item.getBlock() != null ? item : null);

            setText(null);
            setGraphic(content);
        }
    }
}
