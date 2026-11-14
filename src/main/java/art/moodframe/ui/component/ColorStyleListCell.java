package art.moodframe.ui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import art.moodframe.model.ColorStyleItem;

/**
 * Custom ListCell for displaying color styles with a two-line layout.
 *
 * <p>This cell renderer displays:</p>
 * <ul>
 *   <li>Line 1: Style name, with a palette icon for the palettes of the user and, in a list, the favorite star at
 *   the right ({@link FavoriteStar})</li>
 *   <li>Line 2: 5 color boxes showing the defined colors (or crossed out if empty)</li>
 * </ul>
 *
 * <p>As the button cell of a field, it leaves out the icon column, so the name starts on the edge of the other
 * fields.</p>
 *
 * <p>Used in the color list of the main window and in the "Manage…" dialog.</p>
 *
 * @since 1.3.0
 */
public class ColorStyleListCell extends ListCell<ColorStyleItem> {

    private final VBox content;
    private final HBox headerRow;
    private final HBox colorBoxesRow;
    private final FontIcon icon;
    private final Label nameLabel;
    private final ColorBox[] colorBoxes;
    private final FavoriteStar<ColorStyleItem> star;

    /**
     * Creates the button cell of a field, without icon column.
     */
    public ColorStyleListCell() {
        this(null);
    }

    /**
     * Creates a row of a palette list.
     *
     * @param favorites the favorites of the list, null for the button cell
     */
    public ColorStyleListCell(FavoriteStar.Favorites<ColorStyleItem> favorites) {
        super();

        // Icon marking the palettes of the user
        icon = new FontIcon("mdi2p-palette");
        icon.getStyleClass().addAll("ikonli-font-icon", "success");
        icon.setIconSize(12);

        // Name label
        nameLabel = new Label();

        // Header row: Icon + Name + Star, only the name in the button cell
        headerRow = new HBox(6);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.getChildren().add(nameLabel);
        star = favorites != null ? new FavoriteStar<>(this, favorites) : null;
        if (star != null) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            headerRow.getChildren().add(0, icon);
            headerRow.getChildren().addAll(spacer, star);
        }

        // Color boxes row (5 boxes)
        colorBoxes = new ColorBox[5];
        colorBoxesRow = new HBox(3);
        colorBoxesRow.setAlignment(Pos.CENTER_LEFT);
        colorBoxesRow.setPadding(new Insets(0, 0, 0, star != null ? 18 : 0)); // Indent to align with name

        for (int i = 0; i < 5; i++) {
            colorBoxes[i] = new ColorBox(null, 14);
            colorBoxesRow.getChildren().add(colorBoxes[i]);
        }

        // Main content: VBox with header and colors
        content = new VBox(2);
        content.setPadding(new Insets(2, 0, 2, 0));
        content.getChildren().addAll(headerRow, colorBoxesRow);
    }

    @Override
    protected void updateItem(ColorStyleItem item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            /*  Only the palettes of the user carry the icon, the others keep its space  */
            icon.setVisible(!item.isDefault());

            // Set name
            nameLabel.setText(item.getName());
            if (star != null)
                star.show(item.getId() != null ? item : null);

            // Set color boxes
            for (int i = 0; i < 5; i++) {
                colorBoxes[i].setColor(item.getColor(i));
            }

            setText(null);
            setGraphic(content);
        }
    }
}
