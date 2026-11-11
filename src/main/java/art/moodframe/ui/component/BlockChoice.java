package art.moodframe.ui.component;

import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A building block list of the main window (atmosphere, style, color, light). It starts with the actions
 * "Edit…" (the selected entry), "New…" and "Manage…" and a line, followed by the entries, optionally under the
 * headers of their groups. Choosing an action keeps the selected entry and runs the action.
 *
 * <p>Actions, line and headers are rows of the item type, made by a row factory and known by identity; the cells
 * of the list draw them through {@link #render}.</p>
 *
 * @param <T> the item type of the list
 * @since 2.0.0
 */
public class BlockChoice<T> {

    /** What the actions on top of the list do. */
    public interface Actions<T> {

        /**
         * Edits an entry.
         *
         * @param entry the selected entry
         */
        void edit(T entry);

        /** Creates a new entry. */
        void create();

        /** Manages the entries of the user. */
        void manage();
    }

    private final ComboBox<T> comboBox;
    private final Function<String, T> rowFactory;
    private final Predicate<T> editable;
    private final Map<T, String> rows = new IdentityHashMap<>();
    private final T editRow;
    private final T newRow;
    private final T manageRow;
    private final T separatorRow;
    private T entry;

    /**
     * Sets the list up.
     *
     * @param comboBox   the list
     * @param rowFactory makes an item for a row that is no entry, from an internal name
     * @param editable   tells if an entry can be edited ("None" cannot)
     * @param actions    what the actions do
     */
    public BlockChoice(ComboBox<T> comboBox, Function<String, T> rowFactory, Predicate<T> editable,
            Actions<T> actions) {
        this.comboBox = comboBox;
        this.rowFactory = rowFactory;
        this.editable = editable;
        this.editRow = row("block.action.edit");
        this.newRow = row("block.action.new");
        this.manageRow = row("block.action.manage");
        this.separatorRow = row("");

        /*  An action is no choice: the entry stays selected, and the action runs once the list is closed.
            The line or a header, reached by the arrow keys, passes on to the next entry in that direction.  */
        comboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || isEntry(newVal)) {
                entry = newVal;
                return;
            }
            T selected = entry;
            T next = newVal == editRow || newVal == newRow || newVal == manageRow ? null : nextEntry(selected, newVal);
            Platform.runLater(() -> {
                comboBox.setValue(next != null ? next : selected);
                if (newVal == editRow && selected != null && editable.test(selected))
                    actions.edit(selected);
                else if (newVal == newRow)
                    actions.create();
                else if (newVal == manageRow)
                    actions.manage();
            });
        });

        /*  "Edit…" follows the selected entry, so its cell is drawn anew each time the list opens  */
        comboBox.addEventHandler(ComboBoxBase.ON_SHOWING, (event) -> {
            if (comboBox.getSkin() instanceof ComboBoxListViewSkin<?> skin
                    && skin.getPopupContent() instanceof ListView<?> list)
                list.refresh();
        });
    }

    /*  the first entry behind a row, seen from the selected entry; none behind the line, where the actions are  */
    private T nextEntry(T selected, T row) {
        List<T> items = comboBox.getItems();
        int from = indexOf(items, selected);
        int index = indexOf(items, row);
        int step = index < from ? -1 : 1;
        for (index += step; index >= 0 && index < items.size(); index += step) {
            T item = items.get(index);
            if (isEntry(item))
                return item;
            if (item == editRow || item == newRow || item == manageRow)
                return null;
        }
        return null;
    }

    private static <T> int indexOf(List<T> items, T item) {
        for (int i = 0; i < items.size(); i++)
            if (items.get(i) == item)
                return i;
        return -1;
    }

    private T row(String key) {
        T row = rowFactory.apply("\0" + key);
        rows.put(row, key);
        return row;
    }

    /**
     * Tells if an item is an entry, not an action, the line or a group header.
     *
     * @param item the item
     * @return true for an entry
     */
    public boolean isEntry(T item) {
        return !rows.containsKey(item);
    }

    /**
     * Fills the list with the actions and the entries and selects the entry with the given name, or else the first
     * entry.
     *
     * @param entries  the entries, already in the order of their groups
     * @param groupKey tells the i18n key of the header of an entry's group, null for a list without groups
     * @param name     the name of the entry to select, null for the first entry
     */
    public void setEntries(List<T> entries, Function<T, String> groupKey, String name) {
        rows.keySet().removeIf((row) -> row != editRow && row != newRow && row != manageRow && row != separatorRow);
        List<T> items = new ArrayList<>(List.of(editRow, newRow, manageRow, separatorRow));
        String group = null;
        for (T item : entries) {
            String key = groupKey != null ? groupKey.apply(item) : null;
            if (key != null && !key.equals(group))
                items.add(row(key));
            group = key;
            items.add(item);
        }
        comboBox.getItems().setAll(items);
        comboBox.setValue(entries.stream().filter((item) -> Objects.equals(item.toString(), name)).findFirst()
                .orElse(entries.isEmpty() ? null : entries.get(0)));
    }

    /**
     * Draws an action, the line or a group header into a cell of the list.
     *
     * @param cell the cell
     * @param item the item of the cell
     * @return true if the item was such a row, false for an entry, which the cell draws itself
     */
    public boolean render(ListCell<T> cell, T item) {
        String key = item != null ? rows.get(item) : null;
        cell.getStyleClass().removeAll("block-row-header");
        cell.setDisable(false);
        if (key == null)
            return false;

        cell.setText(null);
        if (item == separatorRow) {
            cell.setGraphic(new Separator());
            cell.setDisable(true);
        } else if (item == editRow || item == newRow || item == manageRow) {
            FontIcon icon = new FontIcon(item == editRow ? "mdi2p-pencil-outline"
                    : item == newRow ? "mdi2p-plus" : "mdi2c-cog-outline");
            icon.getStyleClass().add("ikonli-font-icon");
            icon.setIconSize(14);
            HBox content = new HBox(6, icon, new Label(I18nManager.getText(key)));
            content.setAlignment(Pos.CENTER_LEFT);
            cell.setGraphic(content);
            cell.setDisable(item == editRow && (entry == null || !editable.test(entry)));
        } else {
            cell.setGraphic(new Label(I18nManager.getText(key)));
            cell.getStyleClass().add("block-row-header");
            cell.setDisable(true);
        }
        return true;
    }
}
