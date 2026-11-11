package art.moodframe.ui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import art.moodframe.model.ColorStyleItem;

/**
 * Custom ListCell for displaying color styles with a two-line layout.
 *
 * <p>This cell renderer displays:</p>
 * <ul>
 *   <li>Line 1: Style name, with a palette icon for the palettes of the user</li>
 *   <li>Line 2: 5 color boxes showing the defined colors (or crossed out if empty)</li>
 * </ul>
 *
 * <p>Used in the color list of the main window, which also shows its actions through {@link BlockChoice},
 * and in the "Manage…" dialog.</p>
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
    private final BlockChoice<ColorStyleItem> choice;

    /**
     * Creates a new ColorStyleListCell.
     */
    public ColorStyleListCell() {
        this(null);
    }

    /**
     * Creates a cell of the color list that also draws its actions and line.
     *
     * @param choice the list, null for a cell that shows palettes only
     */
    public ColorStyleListCell(BlockChoice<ColorStyleItem> choice) {
        super();
        this.choice = choice;

        // Icon marking the palettes of the user
        icon = new FontIcon("mdi2p-palette");
        icon.getStyleClass().addAll("ikonli-font-icon", "success");
        icon.setIconSize(12);

        // Name label
        nameLabel = new Label();

        // Header row: Icon + Name
        headerRow = new HBox(6);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.getChildren().addAll(icon, nameLabel);

        // Color boxes row (5 boxes)
        colorBoxes = new ColorBox[5];
        colorBoxesRow = new HBox(3);
        colorBoxesRow.setAlignment(Pos.CENTER_LEFT);
        colorBoxesRow.setPadding(new Insets(0, 0, 0, 18)); // Indent to align with name

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

        if (choice != null && choice.render(this, item))
            return;

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            /*  Only the palettes of the user carry the icon, the others keep its space  */
            icon.setVisible(!item.isDefault());

            // Set name
            nameLabel.setText(item.getName());

            // Set color boxes
            for (int i = 0; i < 5; i++) {
                colorBoxes[i].setColor(item.getColor(i));
            }

            setText(null);
            setGraphic(content);
        }
    }
}
