package tools.moodframe.ui.cell;

import tools.moodframe.model.BrainstormSession;
import tools.moodframe.util.I18nManager;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * List cell of the brainstorm history: the session title and a trash button that is only visible
 * while the mouse is over the row (same look as {@link ChatListCell}). A click on the title (the session is
 * opened by the same click, like the chats in the main window) or the context menu entry renames the session in place (Enter confirms, Escape cancels).
 *
 * <p>A marker in the left margin, in front of the title, shows what happens in the background: a spinner while the AI
 * works, a dot for an unread answer (red for a failure). The margin is a bit wider than in the main window chat list,
 * so the marker has some space to the title and the titles do not move.</p>
 *
 * @since 1.7.0
 */
public class BrainstormSessionListCell extends ListCell<BrainstormSession> {

    private final Label titleLabel = new Label();
    private final TextField renameField = new TextField();
    private final StackPane titleBox = new StackPane(titleLabel, renameField);
    private final ContextMenu contextMenu = new ContextMenu();
    private final Button trashButton = new Button();
    private final HBox row = new HBox(6.0);
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final Circle dot = new Circle(4.0);
    private final StackPane markerLayer = new StackPane(spinner, dot);
    private final StackPane root = new StackPane(row, markerLayer);
    private final Function<BrainstormSession, ObjectProperty<BrainstormMarker>> markerOf;
    private final Tooltip markerTooltip = new Tooltip();
    private final ChangeListener<BrainstormMarker> markerListener = (obs, old, marker) -> showMarker(marker);
    private ObjectProperty<BrainstormMarker> boundMarker;

    /**
     * Creates the cell.
     *
     * @param onDelete called with the session when its trash button is clicked
     * @param onRename called with the session and its new, non-empty title when the user renamed it
     * @param markerOf gives the marker property of a session
     */
    public BrainstormSessionListCell(Consumer<BrainstormSession> onDelete, BiConsumer<BrainstormSession, String> onRename,
            Function<BrainstormSession, ObjectProperty<BrainstormMarker>> markerOf) {
        this.markerOf = markerOf;
        titleLabel.getStyleClass().addAll("text-default", "text-bold");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                startRename();
                event.consume();
            }
        });

        renameField.setVisible(false);
        renameField.setOnAction(event -> finishRename(onRename, true));
        renameField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                finishRename(onRename, false);
                event.consume();
            }
        });
        renameField.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused && renameField.isVisible()) {
                finishRename(onRename, true);
            }
        });
        titleBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        MenuItem renameItem = new MenuItem(I18nManager.getText("brainstorm.history.rename"));
        renameItem.setOnAction(event -> startRename());
        contextMenu.getItems().add(renameItem);

        FontIcon trashIcon = new FontIcon("mdi2d-delete");
        trashIcon.setIconSize(12);
        trashButton.setGraphic(trashIcon);
        trashButton.getStyleClass().addAll("button-icon", "chat-action-button");
        trashButton.setStyle(
                "-fx-border-color: transparent; -fx-border-width: 0; -fx-background-insets: 0; -fx-border-insets: 0; -fx-padding: 2;");
        trashButton.setMinSize(18, 18);
        trashButton.setMaxSize(18, 18);
        trashButton.setPrefSize(18, 18);
        trashButton.setVisible(false);
        trashButton.setOnAction(event -> {
            BrainstormSession session = getItem();
            if (session != null) {
                onDelete.accept(session);
            }
        });

        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(0, 6.0, 0, 18.0));
        row.setMinHeight(32.0);
        row.setPrefHeight(32.0);
        row.setMaxHeight(32.0);
        Region spacer = new Region();
        row.getChildren().addAll(titleBox, spacer, trashButton);

        // The marker sits in the left margin and does not take part in the layout of the row
        spinner.setPrefSize(10.0, 10.0);
        spinner.setMinSize(10.0, 10.0);
        spinner.setMaxSize(10.0, 10.0);
        markerLayer.setAlignment(Pos.CENTER_LEFT);
        markerLayer.setPadding(new Insets(0, 0, 0, 3.0));
        markerLayer.setMouseTransparent(true);
        markerLayer.setPickOnBounds(false);
        spinner.setVisible(false);
        dot.setVisible(false);

        hoverProperty().addListener((obs, wasHover, isHover) -> trashButton.setVisible(isHover && getItem() != null));
    }

    private void startRename() {
        BrainstormSession session = getItem();
        if (session == null) {
            return;
        }
        renameField.setText(session.getTitle());
        renameField.setVisible(true);
        titleLabel.setVisible(false);
        renameField.requestFocus();
        renameField.selectAll();
    }

    /** Ends the in-place editing, and reports the new title if confirmed, changed and not empty. */
    private void finishRename(BiConsumer<BrainstormSession, String> onRename, boolean confirmed) {
        if (!renameField.isVisible()) {
            return;
        }
        renameField.setVisible(false);
        titleLabel.setVisible(true);

        BrainstormSession session = getItem();
        String newTitle = renameField.getText().trim();
        if (confirmed && session != null && !newTitle.isEmpty() && !newTitle.equals(session.getTitle())) {
            onRename.accept(session, newTitle);
        }
    }

    private void showMarker(BrainstormMarker marker) {
        BrainstormMarker shown = marker == null ? BrainstormMarker.IDLE : marker;
        spinner.setVisible(shown == BrainstormMarker.RUNNING);
        dot.setVisible(shown == BrainstormMarker.UNREAD || shown == BrainstormMarker.FAILED);
        dot.setStyle(shown == BrainstormMarker.FAILED ? "-fx-fill: -color-danger-emphasis;" : "-fx-fill: -color-accent-emphasis;");

        Tooltip.uninstall(row, markerTooltip);
        if (shown != BrainstormMarker.IDLE) {
            markerTooltip.setText(I18nManager.getText("brainstorm.history." + switch (shown) {
                case RUNNING -> "running";
                case UNREAD -> "unread";
                default -> "failed";
            }));
            Tooltip.install(row, markerTooltip);
        }
    }

    @Override
    protected void updateItem(BrainstormSession session, boolean empty) {
        super.updateItem(session, empty);
        // Cells are reused: the marker of the previous item must not stay bound
        if (boundMarker != null) {
            boundMarker.removeListener(markerListener);
            boundMarker = null;
        }
        if (empty || session == null) {
            showMarker(BrainstormMarker.IDLE);
            setGraphic(null);
            setContextMenu(null);
            return;
        }
        boundMarker = markerOf.apply(session);
        boundMarker.addListener(markerListener);
        showMarker(boundMarker.get());
        setContextMenu(contextMenu);
        renameField.setVisible(false);
        titleLabel.setVisible(true);
        titleLabel.setTooltip(new Tooltip(I18nManager.getText("brainstorm.history.rename.tooltip")));
        titleLabel.setText(session.getTitle());
        trashButton.setTooltip(new Tooltip(I18nManager.getText("brainstorm.history.delete")));
        trashButton.setVisible(isHover());
        setGraphic(root);
    }
}
