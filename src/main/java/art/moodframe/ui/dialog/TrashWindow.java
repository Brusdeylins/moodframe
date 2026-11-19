package art.moodframe.ui.dialog;

import art.moodframe.service.trash.TrashCenter;
import art.moodframe.service.trash.TrashCleanupService;
import art.moodframe.service.trash.TrashEntry;
import art.moodframe.service.trash.TrashGroup;
import art.moodframe.ui.cell.TrashEntryCell;
import art.moodframe.ui.component.DialogButtons;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Window of the central trash: one tab per {@link TrashGroup.Kind} with the number of its deleted items. Every
 * item can be restored or deleted for good, and a tab can be emptied as a whole.
 *
 * <p>The window only talks to the {@link TrashCenter}: a kind without a docked group has an empty tab, and
 * whoever listens there learns what was restored or deleted here. The trash reads the database and moves files, so
 * the window asks it on a worker and disables its buttons until the answer is shown.</p>
 *
 * @since 2.0.0
 */
public class TrashWindow extends Stage {

    private static final double WINDOW_WIDTH = 720;
    private static final double WINDOW_HEIGHT = 520;

    private static final Logger logger = LoggerFactory.getLogger(TrashWindow.class);

    private final TrashCenter trashCenter;
    private final Executor worker;
    private final TabPane tabPane = new TabPane();
    private final Map<TrashGroup.Kind, ListView<TrashEntry>> lists = new EnumMap<>(TrashGroup.Kind.class);

    /*  The item to mark in a tab once its items are loaded  */
    private final Map<TrashGroup.Kind, String> marks = new EnumMap<>(TrashGroup.Kind.class);

    /*  Whether a task of the trash runs on the worker (their number in running): the buttons wait meanwhile  */
    private final BooleanProperty busy = new SimpleBooleanProperty();
    private int running;

    /**
     * Creates the window.
     *
     * @param owner        the owner window
     * @param trashCenter  the central trash with its groups
     * @param trashCleanup knows how long an item stays in the trash
     * @param worker       runs the reads and changes of the trash, off the JavaFX thread
     */
    public TrashWindow(Window owner, TrashCenter trashCenter, TrashCleanupService trashCleanup, Executor worker) {
        this.trashCenter = trashCenter;
        this.worker = worker;

        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(I18nManager.getText("trash.title"));

        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        for (TrashGroup.Kind kind : TrashGroup.Kind.values()) {
            ListView<TrashEntry> list = new ListView<>();
            list.setPlaceholder(new Label(I18nManager.getText("trash.placeholder")));
            list.setCellFactory((view) -> {
                TrashEntryCell cell = new TrashEntryCell(
                        (entry) -> trashCleanup.getDaysUntilAutoDeletion(entry.deletedAt()),
                        (entry) -> restore(kind, entry, null),
                        (entry) -> deletePermanently(kind, List.of(entry)),
                        (entry, part) -> restore(kind, entry, part));
                cell.disableActions(busy);
                return cell;
            });
            VBox.setVgrow(list, Priority.ALWAYS);
            lists.put(kind, list);

            Button clearButton = new Button(I18nManager.getText("trash.clear"));
            clearButton.getStyleClass().addAll("button-outlined", "danger");
            clearButton.disableProperty().bind(Bindings.isEmpty(list.getItems()).or(busy));
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
     * Opens the trash windows: the central trash with how long an item stays there and the worker that asks it,
     * handed from the coordinator of a window to its parts.
     *
     * @param center  the central trash with its groups
     * @param cleanup knows how long an item stays in the trash
     * @param worker  runs the reads and changes of the trash, off the JavaFX thread
     */
    public record Opener(TrashCenter center, TrashCleanupService cleanup, Executor worker) {

        /**
         * Creates the trash on the tab of a kind, not shown yet.
         *
         * @param owner   the owner window
         * @param kind    the group to show
         * @param entryId the ID of the item to mark, or null for none
         * @return the window
         */
        public TrashWindow window(Window owner, TrashGroup.Kind kind, String entryId) {
            TrashWindow window = new TrashWindow(owner, center, cleanup, worker);
            window.select(kind, entryId);
            return window;
        }

        /**
         * Opens the trash on the tab of a kind.
         *
         * @param owner   the owner window
         * @param kind    the group to show
         * @param entryId the ID of the item to mark, or null for none
         */
        public void open(Window owner, TrashGroup.Kind kind, String entryId) {
            window(owner, kind, entryId).show();
        }
    }

    /**
     * Shows the tab of a kind and marks an item there, once the items of the tab are loaded.
     *
     * @param kind    the group to show
     * @param entryId the ID of the item to mark, or null for none
     */
    public void select(TrashGroup.Kind kind, String entryId) {
        tabPane.getSelectionModel().select(kind.ordinal());
        if (entryId != null) {
            marks.put(kind, entryId);
            mark(kind);
        }
    }

    /*  Marks the item that waits for the items of its tab, once they hold it  */
    private void mark(TrashGroup.Kind kind) {
        String entryId = marks.get(kind);
        ListView<TrashEntry> list = lists.get(kind);
        list.getItems().stream().filter((entry) -> entry.id().equals(entryId)).findFirst().ifPresent((entry) -> {
            marks.remove(kind);
            list.getSelectionModel().select(entry);
            list.scrollTo(entry);
        });
    }

    /** Loads the items of a kind again and shows their number in the tab. */
    private void refresh(TrashGroup.Kind kind) {
        inBackground(kind, () -> null, (nothing) -> { });
    }

    /*  The result of a task of the trash with the items of its kind afterwards  */
    private record Done<T>(T result, List<TrashEntry> items) { }

    /*  Runs a task of the trash on the worker and loads the items of its kind afterwards; the JavaFX thread shows
        them and hands the result on. The buttons are disabled meanwhile.  */
    private <T> void inBackground(TrashGroup.Kind kind, Supplier<T> task, Consumer<T> then) {
        running++;
        busy.set(true);
        try {
            CompletableFuture.supplyAsync(() -> {
                T result = task.get();
                return new Done<>(result, trashCenter.list(kind));
            }, worker).whenComplete((done, failure) -> Platform.runLater(() -> {
                finished();
                if (failure != null) {
                    logger.error("The trash of {} failed", kind, failure);
                    return;
                }
                show(kind, done.items());
                then.accept(done.result());
            }));
        } catch (RejectedExecutionException closed) {
            logger.warn("The trash of {} was asked after its window closed", kind);
            finished();
        }
    }

    private void finished() {
        busy.set(--running > 0);
    }

    /*  Shows the items of a kind with their number in the tab  */
    private void show(TrashGroup.Kind kind, List<TrashEntry> items) {
        ListView<TrashEntry> list = lists.get(kind);
        list.getItems().setAll(items);
        tabPane.getTabs().get(kind.ordinal()).setText(
                I18nManager.getText("trash.tab." + kind.name().toLowerCase(), list.getItems().size()));
        if (marks.containsKey(kind))
            mark(kind);
    }

    /** Takes an item out of the trash, back into use, or only a part of it if one is given. */
    void restore(TrashGroup.Kind kind, TrashEntry entry, TrashEntry.Part part) {
        String id = part != null ? part.id() : entry.id();
        inBackground(kind, () -> trashCenter.restore(kind, id), (restored) -> {
            if (!restored)
                showError(I18nManager.getText("trash.error.restore", entry.name()));
        });
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
        alert.getButtonTypes().setAll(deleteButton, DialogButtons.cancel());
        if (alert.showAndWait().orElse(null) != deleteButton)
            return;

        List<String> ids = entries.stream().map(TrashEntry::id).toList();
        inBackground(kind, () -> trashCenter.deletePermanently(kind, ids), (deleted) -> {
            if (deleted.size() < ids.size())
                showError(I18nManager.getText("trash.error.delete"));
        });
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, DialogButtons.ok());
        alert.initOwner(this);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
