package art.moodframe.ui.component;

import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A building block field of the main window (atmosphere, style, color, light, camera). Its list holds the entries,
 * optionally under the headers of their groups; its actions "Edit" (the selected entry), "New" and "Manage" are icons
 * in the row of the field name, left of the lock, and like the lock they show while the pointer is over the field.
 *
 * <p>Headers are rows of the item type, made by a row factory and known by identity; the cells of the list draw
 * them through {@link #render}.</p>
 *
 * @param <T> the item type of the list
 * @since 2.0.0
 */
public class BlockChoice<T> {

    /** What the actions of the field do. */
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
    private final Map<T, String> rows = new IdentityHashMap<>();
    private final Button editButton = action("mdi2p-pencil");
    private final Button newButton = action("mdi2p-plus");
    private final Button manageButton = action("mdi2c-cog-outline");
    private T entry;

    /**
     * Sets the field up and puts its action icons into the row of its lock, right in front of the lock.
     *
     * @param comboBox   the list
     * @param lock       the lock of the field
     * @param rowFactory makes an item for a group header, from an internal name
     * @param editable   tells if an entry can be edited ("None" cannot)
     * @param actions    what the actions do
     */
    public BlockChoice(ComboBox<T> comboBox, Node lock, Function<String, T> rowFactory, Predicate<T> editable,
            Actions<T> actions) {
        this.comboBox = comboBox;
        this.rowFactory = rowFactory;

        /*  A header, reached by the arrow keys, passes on to the next entry in that direction  */
        comboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || isEntry(newVal)) {
                entry = newVal;
                return;
            }
            T selected = entry;
            T next = nextEntry(selected, newVal);
            Platform.runLater(() -> comboBox.setValue(next != null ? next : selected));
        });

        /*  The actions are off with the list, "Edit" also while no entry that can be edited is selected  */
        editButton.disableProperty().bind(Bindings.createBooleanBinding(() -> comboBox.isDisable()
                || comboBox.getValue() == null || !isEntry(comboBox.getValue()) || !editable.test(comboBox.getValue()),
                comboBox.disableProperty(), comboBox.valueProperty()));
        newButton.disableProperty().bind(comboBox.disableProperty());
        manageButton.disableProperty().bind(comboBox.disableProperty());
        editButton.setOnAction((event) -> actions.edit(comboBox.getValue()));
        newButton.setOnAction((event) -> actions.create());
        manageButton.setOnAction((event) -> actions.manage());

        Pane row = (Pane) lock.getParent();
        row.getChildren().addAll(row.getChildren().indexOf(lock), List.of(editButton, newButton, manageButton));
        updateTexts();
    }

    private static Button action(String iconLiteral) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add("ikonli-font-icon");
        icon.setIconSize(14);
        Button button = new Button(null, icon);
        button.getStyleClass().addAll("icon-button", "block-action");
        return button;
    }

    /** Sets the tooltips of the actions in the current language. */
    public void updateTexts() {
        editButton.setTooltip(new Tooltip(I18nManager.getText("block.action.edit")));
        newButton.setTooltip(new Tooltip(I18nManager.getText("block.action.new")));
        manageButton.setTooltip(new Tooltip(I18nManager.getText("block.action.manage")));
    }

    /*  the first entry behind a header, seen from the selected entry  */
    private T nextEntry(T selected, T row) {
        List<T> items = comboBox.getItems();
        int from = indexOf(items, selected);
        int index = indexOf(items, row);
        int step = index < from ? -1 : 1;
        for (index += step; index >= 0 && index < items.size(); index += step) {
            T item = items.get(index);
            if (isEntry(item))
                return item;
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
     * Tells if an item is an entry, not a group header.
     *
     * @param item the item
     * @return true for an entry
     */
    public boolean isEntry(T item) {
        return !rows.containsKey(item);
    }

    /**
     * Fills the list with the entries and selects the entry with the given name, or else the first entry.
     *
     * @param entries  the entries, already in the order of their groups
     * @param groupKey tells the i18n key of the header of an entry's group, null for a list without groups
     * @param name     the name of the entry to select, null for the first entry
     */
    public void setEntries(List<T> entries, Function<T, String> groupKey, String name) {
        rows.clear();
        List<T> items = new ArrayList<>();
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
     * Draws a group header into a cell of the list.
     *
     * @param cell the cell
     * @param item the item of the cell
     * @return true if the item was a header, false for an entry, which the cell draws itself
     */
    public boolean render(ListCell<T> cell, T item) {
        String key = item != null ? rows.get(item) : null;
        cell.getStyleClass().removeAll("block-row-header");
        cell.setDisable(false);
        if (key == null)
            return false;

        cell.setText(null);
        cell.setGraphic(new Label(I18nManager.getText(key)));
        cell.getStyleClass().add("block-row-header");
        cell.setDisable(true);
        return true;
    }
}
