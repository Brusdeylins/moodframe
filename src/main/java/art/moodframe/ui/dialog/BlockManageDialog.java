package art.moodframe.ui.dialog;

import art.moodframe.config.ServiceFactory;
import art.moodframe.service.BlockUsage;
import art.moodframe.service.TrashCenter;
import art.moodframe.service.TrashCleanupService;
import art.moodframe.service.TrashGroup;
import art.moodframe.util.I18nManager;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Callback;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The "Manage…" dialog of a building block list: the entries of the user of one kind. They can be deleted into the
 * central trash, reordered, and exported to or imported from a JSON file; a link opens the trash on its blocks.
 *
 * @param <T> the item type of the entries
 * @since 2.0.0
 */
public class BlockManageDialog<T> extends Dialog<Void> {

    private static final Logger logger = LoggerFactory.getLogger(BlockManageDialog.class);

    /**
     * The entries of the user of one kind of building block.
     *
     * @param <T> the item type of the entries
     */
    public interface Library<T> {

        /**
         * Lists the entries of the user.
         *
         * @return the entries in their order
         */
        List<T> list();

        /**
         * Tells the ID of an entry.
         *
         * @param entry the entry
         * @return its ID
         */
        String id(T entry);

        /**
         * Tells the name of an entry.
         *
         * @param entry the entry
         * @return its name
         */
        String name(T entry);

        /**
         * Moves an entry to the trash.
         *
         * @param entry the entry
         * @return true if it is in the trash
         */
        boolean delete(T entry);

        /**
         * Stores the order of the entries.
         *
         * @param entries the entries in their new order
         * @return true if the order is stored
         */
        boolean reorder(List<T> entries);

        /**
         * Writes the entries into a JSON file.
         *
         * @param file the target file
         * @throws IOException if the file cannot be written
         */
        void exportTo(File file) throws IOException;

        /**
         * Adds the entries of a JSON file.
         *
         * @param file the source file
         * @return the number of added entries
         * @throws IOException if the file cannot be read
         */
        int importFrom(File file) throws IOException;
    }

    private final Library<T> library;
    private final String fileName;
    private final ListView<T> list = new ListView<>();

    /**
     * Creates the dialog.
     *
     * @param owner    the owner window
     * @param title    the title, naming the kind of building block
     * @param fileName the suggested name of an export file
     * @param library  the entries of the user
     * @param cells    draws an entry
     */
    public BlockManageDialog(Window owner, String title, String fileName, Library<T> library,
            Callback<ListView<T>, ListCell<T>> cells) {
        this.library = library;
        this.fileName = fileName;

        initOwner(owner);
        setTitle(title);
        setHeaderText(null);

        list.setCellFactory(cells);
        list.setPlaceholder(new Label(I18nManager.getText("block.manage.placeholder")));
        list.setPrefSize(360, 360);
        HBox.setHgrow(list, Priority.ALWAYS);

        Button upButton = button("block.manage.up", "mdi2a-arrow-up", () -> move(-1));
        Button downButton = button("block.manage.down", "mdi2a-arrow-down", () -> move(1));
        Button deleteButton = button("block.manage.delete", "mdi2t-trash-can-outline", this::delete);
        Button importButton = button("block.manage.import", "mdi2f-file-import-outline", this::importFile);
        Button exportButton = button("block.manage.export", "mdi2f-file-export-outline", this::exportFile);
        upButton.disableProperty().bind(list.getSelectionModel().selectedIndexProperty().lessThan(1));
        downButton.disableProperty().bind(list.getSelectionModel().selectedIndexProperty().lessThan(0)
                .or(list.getSelectionModel().selectedIndexProperty().greaterThanOrEqualTo(
                        Bindings.size(list.getItems()).subtract(1))));
        deleteButton.disableProperty().bind(list.getSelectionModel().selectedIndexProperty().lessThan(0));
        exportButton.disableProperty().bind(Bindings.isEmpty(list.getItems()));

        Region gap = new Region();
        VBox.setVgrow(gap, Priority.ALWAYS);
        Hyperlink trashLink = new Hyperlink(I18nManager.getText("block.manage.trash"));
        trashLink.setGraphic(new FontIcon("mdi2d-delete-outline"));
        trashLink.setOnAction((event) -> openTrash());
        VBox buttons = new VBox(8, upButton, downButton, deleteButton, gap, importButton, exportButton, trashLink);
        buttons.setAlignment(Pos.TOP_LEFT);

        HBox content = new HBox(12, list, buttons);
        content.setPadding(new Insets(10));
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().add(new ButtonType(I18nManager.getText("block.manage.close"),
                ButtonBar.ButtonData.CANCEL_CLOSE));
        setResultConverter((buttonType) -> null);

        reload(null);
    }

    private static Button button(String key, String icon, Runnable action) {
        Button button = new Button(I18nManager.getText(key), new FontIcon(icon));
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction((event) -> action.run());
        return button;
    }

    /*  loads the entries again and selects the one with the given ID, if any  */
    private void reload(String selectId) {
        list.getItems().setAll(library.list());
        list.getSelectionModel().clearSelection();
        list.getItems().stream().filter((entry) -> library.id(entry).equals(selectId)).findFirst()
                .ifPresent((entry) -> list.getSelectionModel().select(entry));
    }

    /*  moves the selected entry one place up or down  */
    private void move(int offset) {
        int index = list.getSelectionModel().getSelectedIndex();
        if (index < 0 || index + offset < 0 || index + offset >= list.getItems().size())
            return;

        List<T> entries = new ArrayList<>(list.getItems());
        T entry = entries.remove(index);
        entries.add(index + offset, entry);
        if (!library.reorder(entries))
            showError(I18nManager.getText("block.manage.error.reorder"));
        reload(library.id(entry));
    }

    /*  moves the selected entry to the trash, after a question that names the moods using it  */
    private void delete() {
        T entry = list.getSelectionModel().getSelectedItem();
        if (entry == null)
            return;

        BlockUsage usage = ServiceFactory.getInstance().getService(BlockUsage.class);
        List<String> moods = usage != null ? usage.moodsUsing(library.id(entry)) : List.of();
        Alert alert = new Alert(moods.isEmpty() ? Alert.AlertType.CONFIRMATION : Alert.AlertType.WARNING);
        alert.initOwner(getDialogPane().getScene().getWindow());
        alert.setTitle(I18nManager.getText("block.manage.delete"));
        alert.setHeaderText(I18nManager.getText("block.manage.delete.confirm", library.name(entry)));
        alert.setContentText(moods.isEmpty() ? I18nManager.getText("block.manage.delete.note")
                : I18nManager.getText("block.manage.delete.moods", String.join(", ", moods)));
        ButtonType deleteButton = new ButtonType(I18nManager.getText("block.manage.delete"), ButtonBar.ButtonData.YES);
        alert.getButtonTypes().setAll(deleteButton, ButtonType.CANCEL);
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != deleteButton)
            return;

        if (!library.delete(entry))
            showError(I18nManager.getText("block.manage.error.delete", library.name(entry)));
        reload(null);
    }

    private void importFile() {
        FileChooser chooser = fileChooser();
        File file = chooser.showOpenDialog(getDialogPane().getScene().getWindow());
        if (file == null)
            return;

        try {
            int imported = library.importFrom(file);
            reload(null);
            showInfo(I18nManager.getText("block.manage.imported", imported));
        } catch (IOException | RuntimeException e) {
            logger.error("Failed to import building blocks from {}", file, e);
            showError(I18nManager.getText("block.manage.error.import", file.getName()));
        }
    }

    private void exportFile() {
        FileChooser chooser = fileChooser();
        chooser.setInitialFileName(fileName);
        File file = chooser.showSaveDialog(getDialogPane().getScene().getWindow());
        if (file == null)
            return;

        try {
            library.exportTo(file);
        } catch (IOException | RuntimeException e) {
            logger.error("Failed to export building blocks to {}", file, e);
            showError(I18nManager.getText("block.manage.error.export", file.getName()));
        }
    }

    private static FileChooser fileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        return chooser;
    }

    /*  the trash on its blocks; what is restored there shows up here once the trash is closed  */
    private void openTrash() {
        TrashWindow window = new TrashWindow(getDialogPane().getScene().getWindow(),
                ServiceFactory.getInstance().getService(TrashCenter.class),
                ServiceFactory.getInstance().getService(TrashCleanupService.class));
        window.select(TrashGroup.Kind.BLOCKS, null);
        window.setOnHidden((event) -> reload(null));
        window.show();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.initOwner(getDialogPane().getScene().getWindow());
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.initOwner(getDialogPane().getScene().getWindow());
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
