package art.moodframe.ui.brainstorm;

import art.moodframe.model.BrainstormSession;
import art.moodframe.service.brainstorm.BrainstormService;
import art.moodframe.ui.support.BoxInset;
import art.moodframe.ui.support.ListReorder;
import art.moodframe.util.I18nManager;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * The history column of the brainstorm window: the stored sessions, which the user opens, renames, moves and deletes,
 * and the button for a new chat. The window decides what opening and deleting do. The sessions are read and
 * written on the worker of the window.
 *
 * @since 2.1.3
 */
final class BrainstormHistory extends VBox {

    /** What the window does for the history. */
    interface Listener {

        /** Opens a session the user selected. */
        void open(BrainstormSession session);

        /** Shows a new, empty chat. */
        void startNew();

        /** Deletes a session, after a question if the AI still works on it. */
        void delete(BrainstormSession session);

        /** The session of the shown chat, null for a new one. */
        BrainstormSession shown();

        /** The list was shown anew, a title may have changed. */
        void listed();
    }

    private final BrainstormService service;
    private final BrainstormWorker worker;
    private final Listener listener;
    private final ListView<BrainstormSession> list = new ListView<>();
    private final Label title = new Label();
    private final Button newButton = new Button();
    private boolean updating;

    /**
     * Creates the column.
     *
     * @param service  the brainstorm service with the stored sessions
     * @param worker   the worker of the window, which reads and writes the sessions
     * @param markerOf the marker of a session: running, unread or failed
     * @param listener what the window does
     */
    BrainstormHistory(BrainstormService service, BrainstormWorker worker,
            Function<BrainstormSession, ObjectProperty<BrainstormMarker>> markerOf, Listener listener) {
        this.service = service;
        this.worker = worker;
        this.listener = listener;

        FontIcon newIcon = new FontIcon("mdi2p-plus");
        newIcon.setIconSize(20);
        newButton.setGraphic(newIcon);
        newButton.getStyleClass().add("icon-button");
        newButton.setOnAction((event) -> listener.startNew());

        ListReorder<BrainstormSession> reorder = new ListReorder<>(list, this::move);
        list.setCellFactory((view) -> {
            BrainstormSessionListCell cell = new BrainstormSessionListCell(listener::delete, this::rename, markerOf);
            reorder.attach(cell, cell.getGrip());
            return cell;
        });
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            BrainstormSession shown = listener.shown();
            if (!updating && selected != null && (shown == null || !selected.getId().equals(shown.getId())))
                listener.open(selected);
        });
        VBox.setVgrow(list, Priority.ALWAYS);
        relabel();

        /*  The heading starts on the inset of the boxes, the line of the titles in the list. The gap to the chat is
            the divider, the list ends at the bottom with the input field and "Adopt".  */
        getChildren().addAll(BrainstormHeading.row(title, BoxInset.LEFT, 0, newButton), list);
        getStyleClass().add("bg-inset");
        setPadding(new Insets(0, 0, 15.0, 10.0));
        setPrefWidth(240.0);
        setMinWidth(180.0);
        setMaxWidth(420.0);
    }

    /** Sets the texts in the current language; the rows of the list are drawn anew. */
    void relabel() {
        title.setText(I18nManager.getText("brainstorm.history.title"));
        newButton.setTooltip(new Tooltip(I18nManager.getText("brainstorm.history.new")));
        list.refresh();
    }

    /** Lists the stored sessions anew, after the work of the window asked for before, and selects the shown one. */
    void refresh() {
        worker.run(service::listSessions, this::show);
    }

    /** Lists the stored sessions and selects the one the user worked on last, wherever it is in the list; it opens. */
    void refreshAndSelectLast() {
        worker.run(service::listSessions, (List<BrainstormSession> sessions) -> {
            show(sessions);
            list.getItems().stream().max(Comparator.comparing(BrainstormSession::getUpdatedAt))
                    .ifPresent((BrainstormSession last) -> list.getSelectionModel().select(last));
        });
    }

    /** Selects no session, for a new chat. */
    void clearSelection() {
        list.getSelectionModel().clearSelection();
    }

    /**
     * The title of a session as listed (the AI or the user may have renamed it).
     *
     * @param session the session
     * @return the listed title, else the title of the session
     */
    String titleOf(BrainstormSession session) {
        return list.getItems().stream().filter((BrainstormSession listed) -> listed.getId().equals(session.getId()))
                .map(BrainstormSession::getTitle).findFirst().orElse(session.getTitle());
    }

    /** Lists the sessions and selects the one of the shown chat among them, none for a new chat. */
    private void show(List<BrainstormSession> sessions) {
        BrainstormSession select = listener.shown();
        updating = true;
        list.getItems().setAll(sessions);
        if (select != null)
            list.getItems().stream().filter((BrainstormSession listed) -> listed.getId().equals(select.getId()))
                    .findFirst().ifPresent((BrainstormSession listed) -> list.getSelectionModel().select(listed));
        else
            list.getSelectionModel().clearSelection();
        updating = false;
        listener.listed();
    }

    /** Moves a session to a gap of the list (0 above the first one) and stores the order; the shown chat stays. */
    private void move(BrainstormSession moved, int gap) {
        List<BrainstormSession> listed = List.copyOf(list.getItems());
        BrainstormSession before = gap < listed.size() ? listed.get(gap) : null;
        worker.run(() -> service.moveSession(listed, moved, before), this::show);
    }

    private void rename(BrainstormSession toRename, String title) {
        toRename.setTitle(title);
        worker.run(() -> service.renameSession(toRename.getId(), title));
        refresh();
    }
}
