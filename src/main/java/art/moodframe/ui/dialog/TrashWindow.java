package art.moodframe.ui.dialog;

import art.moodframe.config.ServiceFactory;
import art.moodframe.service.TrashCenter;
import art.moodframe.service.TrashCleanup;
import art.moodframe.service.TrashCleanupService;
import art.moodframe.service.TrashEntry;
import art.moodframe.service.TrashGroup;
import art.moodframe.ui.cell.TrashEntryCell;
import art.moodframe.util.I18nManager;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Window of the central trash: one tab per {@link TrashGroup.Kind} with the number of its deleted items. Every
 * item can be restored or deleted for good, and a tab can be emptied as a whole.
 *
 * <p>The window only talks to the {@link TrashCenter}: a kind without a docked group has an empty tab, and
 * whoever listens there learns what was restored or deleted here.</p>
 *
 * @since 2.0.0
 */
public class TrashWindow extends Stage {

    private static final double WINDOW_WIDTH = 720;
    private static final double WINDOW_HEIGHT = 520;

    private final TrashCenter trashCenter;
    private final TabPane tabPane = new TabPane();
    private final Map<TrashGroup.Kind, ListView<TrashEntry>> lists = new EnumMap<>(TrashGroup.Kind.class);

    /**
     * Creates the window.
     *
     * @param owner        the owner window
     * @param trashCenter  the central trash with its groups
     * @param trashCleanup knows how long an item stays in the trash
     */
    public TrashWindow(Window owner, TrashCenter trashCenter, TrashCleanup trashCleanup) {
        this.trashCenter = trashCenter;

        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(I18nManager.getText("trash.title"));

        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        for (TrashGroup.Kind kind : TrashGroup.Kind.values()) {
            ListView<TrashEntry> list = new ListView<>();
            list.setPlaceholder(new Label(I18nManager.getText("trash.placeholder")));
            list.setCellFactory((view) -> new TrashEntryCell(
                    (entry) -> trashCleanup.getDaysUntilAutoDeletion(entry.deletedAt()),
                    (entry) -> restore(kind, entry),
                    (entry) -> deletePermanently(kind, List.of(entry))));
            VBox.setVgrow(list, Priority.ALWAYS);
            lists.put(kind, list);

            Button clearButton = new Button(I18nManager.getText("trash.clear"));
            clearButton.getStyleClass().addAll("button-outlined", "danger");
            clearButton.disableProperty().bind(Bindings.isEmpty(list.getItems()));
            clearButton.setOnAction((event) -> deletePermanently(kind, List.copyOf(list.getItems())));
            HBox actions = new HBox(clearButton);
            actions.setAlignment(Pos.CENTER_RIGHT);

            VBox content = new VBox(10.0, list, actions);
            content.setPadding(new Insets(10.0));
            tabPane.getTabs().add(new Tab(null, content));
            refresh(kind);
        }
        setScene(new Scene(tabPane, WINDOW_WIDTH, WINDOW_HEIGHT));
        setMinWidth(480);
        setMinHeight(320);

        if (owner != null) {
            setX(owner.getX() + (owner.getWidth() - WINDOW_WIDTH) / 2);
            setY(owner.getY() + (owner.getHeight() - WINDOW_HEIGHT) / 2);
        }
    }

    /**
     * Opens the trash on the tab of a kind.
     *
     * @param owner   the owner window
     * @param kind    the group to show
     * @param entryId the ID of the item to mark, or null for none
     */
    public static void open(Window owner, TrashGroup.Kind kind, String entryId) {
        TrashWindow window = new TrashWindow(owner, ServiceFactory.getInstance().getService(TrashCenter.class),
                ServiceFactory.getInstance().getService(TrashCleanupService.class));
        window.select(kind, entryId);
        window.show();
    }

    /**
     * Shows the tab of a kind and marks an item there.
     *
     * @param kind    the group to show
     * @param entryId the ID of the item to mark, or null for none
     */
    public void select(TrashGroup.Kind kind, String entryId) {
        tabPane.getSelectionModel().select(kind.ordinal());
        ListView<TrashEntry> list = lists.get(kind);
        list.getItems().stream().filter((entry) -> entry.id().equals(entryId)).findFirst().ifPresent((entry) -> {
            list.getSelectionModel().select(entry);
            list.scrollTo(entry);
        });
    }

    /** Loads the items of a kind again and shows their number in the tab. */
    private void refresh(TrashGroup.Kind kind) {
        ListView<TrashEntry> list = lists.get(kind);
        list.getItems().setAll(trashCenter.list(kind));
        tabPane.getTabs().get(kind.ordinal()).setText(
                I18nManager.getText("trash.tab." + kind.name().toLowerCase(), list.getItems().size()));
    }

    /** Takes an item out of the trash, back into use. */
    void restore(TrashGroup.Kind kind, TrashEntry entry) {
        if (!trashCenter.restore(kind, entry.id()))
            showError(I18nManager.getText("trash.error.restore", entry.name()));
        refresh(kind);
    }

    /** Removes items for good, after a question. */
    private void deletePermanently(TrashGroup.Kind kind, List<TrashEntry> entries) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(this);
        alert.setTitle(I18nManager.getText("trash.delete"));
        alert.setHeaderText(entries.size() == 1
                ? I18nManager.getText("trash.delete.confirm.single", entries.get(0).name())
                : I18nManager.getText("trash.delete.confirm.all", entries.size()));

        /*  A kind may warn about what else is lost with its items  */
        String note = "trash.delete.confirm.note." + kind.name().toLowerCase();
        alert.setContentText(I18nManager.getText(I18nManager.hasText(note) ? note : "trash.delete.confirm.note"));

        ButtonType deleteButton = new ButtonType(I18nManager.getText("trash.delete"), ButtonBar.ButtonData.YES);
        alert.getButtonTypes().setAll(deleteButton, ButtonType.CANCEL);
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != deleteButton)
            return;

        List<String> ids = entries.stream().map(TrashEntry::id).toList();
        if (trashCenter.deletePermanently(kind, ids).size() < ids.size())
            showError(I18nManager.getText("trash.error.delete"));
        refresh(kind);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.initOwner(this);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
