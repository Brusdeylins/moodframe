package art.moodframe.ui.cell;

import art.moodframe.service.TrashEntry;
import art.moodframe.util.I18nManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * List cell of the central trash: the name of a deleted item, its context and the time it has left, with the
 * buttons to restore it and to delete it for good.
 *
 * @since 2.0.0
 */
public class TrashEntryCell extends ListCell<TrashEntry> {

    private final Label nameLabel = new Label();
    private final Label contextLabel = new Label();
    private final Label timeLabel = new Label();
    private final Button restoreButton = new Button();
    private final Button deleteButton = new Button();
    private final HBox row = new HBox(8.0);
    private final ToIntFunction<TrashEntry> daysLeftOf;

    /**
     * Creates the cell.
     *
     * @param daysLeftOf gives the days an item has left before it is deleted automatically
     * @param onRestore  called with the item when its restore button is clicked
     * @param onDelete   called with the item when its delete button is clicked
     */
    public TrashEntryCell(ToIntFunction<TrashEntry> daysLeftOf, Consumer<TrashEntry> onRestore,
            Consumer<TrashEntry> onDelete) {
        this.daysLeftOf = daysLeftOf;

        /*  The cell follows the width of the list, so a long name is shortened instead of scrolling sideways  */
        setPrefWidth(0);

        nameLabel.getStyleClass().addAll("text-default", "text-bold");
        contextLabel.getStyleClass().addAll("text-muted", "text-small");
        VBox texts = new VBox(2.0, nameLabel, contextLabel);
        texts.setMinWidth(0);
        HBox.setHgrow(texts, Priority.ALWAYS);

        timeLabel.getStyleClass().add("text-small");
        timeLabel.setMinWidth(Label.USE_PREF_SIZE);

        FontIcon restoreIcon = new FontIcon("mdi2r-restore");
        restoreIcon.setIconSize(16);
        restoreButton.setGraphic(restoreIcon);
        restoreButton.getStyleClass().addAll("button-icon", "flat");
        restoreButton.setTooltip(new Tooltip(I18nManager.getText("trash.restore")));
        restoreButton.setOnAction((event) -> {
            TrashEntry entry = getItem();
            if (entry != null)
                onRestore.accept(entry);
        });

        FontIcon deleteIcon = new FontIcon("mdi2d-delete-forever");
        deleteIcon.setIconSize(16);
        deleteButton.setGraphic(deleteIcon);
        deleteButton.getStyleClass().addAll("button-icon", "flat", "danger");
        deleteButton.setTooltip(new Tooltip(I18nManager.getText("trash.delete")));
        deleteButton.setOnAction((event) -> {
            TrashEntry entry = getItem();
            if (entry != null)
                onDelete.accept(entry);
        });

        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4.0, 6.0, 4.0, 6.0));
        row.getChildren().addAll(texts, timeLabel, restoreButton, deleteButton);
    }

    @Override
    protected void updateItem(TrashEntry entry, boolean empty) {
        super.updateItem(entry, empty);
        if (empty || entry == null) {
            setGraphic(null);
            return;
        }
        nameLabel.setText(entry.name());
        contextLabel.setText(entry.context());

        /*  The closer the automatic deletion, the louder the color  */
        int daysLeft = daysLeftOf.applyAsInt(entry);
        timeLabel.setText(daysLeft <= 0 ? I18nManager.getText("status.trash.expires.today")
                : I18nManager.getText(daysLeft == 1 ? "status.trash.days.left.singular"
                        : "status.trash.days.left.plural", daysLeft));
        timeLabel.getStyleClass().removeAll("text-muted", "text-warning", "text-danger");
        timeLabel.getStyleClass().add(daysLeft <= 0 ? "text-danger" : daysLeft <= 7 ? "text-warning" : "text-muted");
        setGraphic(row);
    }
}
