package art.moodframe.ui.cell;

import art.moodframe.model.Chat;
import art.moodframe.ui.BoxInset;
import art.moodframe.ui.component.InPlaceEditableLabel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import java.util.function.Consumer;

/**
 * Custom ListView cell for displaying Chat objects.
 * 
 * <p>
 * This cell displays the name of an active chat and provides the
 * actions to rename it and to move it to the trash. Its grip on the left
 * moves the chat to another place in the list (see {@link ListReorder}).
 * </p>
 * 
 * @since 1.0.0
 */
public class ChatListCell extends ListCell<Chat> {

    private final VBox content;
    private final StackPane root;
    private final HBox headerRow;
    private final InPlaceEditableLabel nameLabel;
    private final Label statusLabel;
    private final Button trashButton;
    private final Label grip;

    private Node trashColumn;
    private Consumer<Chat> onDeleteRequested;
    private Consumer<Chat> onChatRenamed;

    /**
     * Creates a new chat list cell.
     */
    public ChatListCell() {
        getStyleClass().add("chat-list-cell");

        // Create UI components
        nameLabel = new InPlaceEditableLabel();
        nameLabel.getStyleClass().addAll("text-default", "text-bold");
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        nameLabel.setAlignment(Pos.CENTER_LEFT); // Horizontal alignment
        nameLabel.setPrefHeight(Region.USE_COMPUTED_SIZE); // Use natural height
        // No CSS alignment needed - handled by HBox alignment

        /*  Trash button for active chats: the icon and its size of the trash in the header of the list, muted and
            in the color of danger while hovered (see icon-button.css)  */
        trashButton = new Button();
        FontIcon trashIcon = new FontIcon("mdi2t-trash-can-outline");
        trashIcon.setIconSize(20);
        trashButton.setGraphic(trashIcon);
        trashButton.getStyleClass().addAll("icon-button", "text-danger-hover");
        trashButton.setVisible(false);
        trashButton.managedProperty().bind(trashButton.visibleProperty());
        trashButton.setTooltip(new Tooltip("Move to trash (can be restored)"));

        statusLabel = new Label();
        statusLabel.getStyleClass().addAll("text-muted", "text-small");
        statusLabel.setVisible(false);

        /*  The grip shows on hover only, like the trash button  */
        grip = ListReorder.grip();

        // Create layout
        headerRow = new HBox(6.0); // Reduced spacing for compactness
        headerRow.setAlignment(Pos.CENTER_LEFT); // Center children vertically, left align horizontally
        headerRow.setFillHeight(true); // Fill the available height to allow vertical centering
        headerRow.setMinHeight(32.0); // Match container height
        headerRow.setPrefHeight(32.0); // Match container height
        headerRow.setMaxHeight(32.0); // Match container height
        // Vertical centering now works correctly with fillHeight(true) and matching
        // heights

        // Make sure HBox fills the width
        VBox.setVgrow(headerRow, Priority.ALWAYS); // Let HBox expand vertically in VBox

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Set vertical alignment for all header components
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        headerRow.getChildren().addAll(nameLabel, spacer, trashButton);

        // Vertical centering is handled by HBox.setAlignment(Pos.CENTER_LEFT)

        content = new VBox(0); // No spacing between VBox children
        content.setAlignment(Pos.CENTER_LEFT); // Center content vertically

        /*  The name starts on the inset of the boxes, behind the 4 px padding of the editable label  */
        content.setPadding(new Insets(0, 12.0, 0, BoxInset.LEFT - BoxInset.LIST - 4.0));
        content.setMinHeight(32.0); // Match HBox height
        content.setPrefHeight(32.0); // Set preferred height for compact layout
        content.setMaxHeight(32.0); // Prevent expansion
        // VBox and HBox now have matching heights for proper alignment

        // Only add headerRow, statusLabel is rarely used and takes space
        content.getChildren().add(headerRow);
        // statusLabel will be added dynamically only when needed

        root = new StackPane(content, grip);
        StackPane.setAlignment(grip, Pos.CENTER_LEFT);

        // Set up mouse handling and event listeners
        setupMouseHandling();
        setupEventHandlers();

        // The trash button and the grip are only visible while the mouse is over the row
        hoverProperty().addListener((obs, wasHover, isHover) -> {
            if (getItem() != null) {
                trashButton.setVisible(isHover);
                grip.setVisible(isHover);
            }
        });
    }

    /**
     * Sets up mouse event handling for the cell.
     */
    private void setupMouseHandling() {
        setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY && !isEmpty()) {
                // Right-click: show context menu
                showContextMenu(event.getScreenX(), event.getScreenY());
            }
        });
    }

    /**
     * Sets up event handlers for UI components.
     */
    private void setupEventHandlers() {
        // Handle chat name changes from in-place editing
        nameLabel.setOnTextChanged(event -> {
            Chat chat = getItem();
            if (chat != null && !event.getOldText().equals(event.getNewText())) {
                // Update the chat name and timestamp
                chat.setName(event.getNewText());
                chat.setUpdatedAt(java.time.LocalDateTime.now());

                // Notify callback for database persistence FIRST
                if (onChatRenamed != null) {
                    onChatRenamed.accept(chat);
                }

                // Then refresh the ListView to ensure proper display
                if (getListView() != null) {
                    getListView().refresh();
                }
            }
        });

        // Handle trash button clicks
        trashButton.setOnAction(event -> {
            Chat chat = getItem();
            if (chat != null && onDeleteRequested != null) {
                onDeleteRequested.accept(chat);
            }
            event.consume();
        });
    }

    /**
     * Shows the context menu for chat actions.
     * 
     * @param screenX the screen X coordinate
     * @param screenY the screen Y coordinate
     */
    private void showContextMenu(double screenX, double screenY) {
        Chat chat = getItem();
        if (chat == null)
            return;

        ContextMenu contextMenu = new ContextMenu();

        // No rename item since we have in-place editing
        MenuItem deleteItem = new MenuItem("Move to Trash");
        deleteItem.setGraphic(new FontIcon("mdi2d-delete"));
        deleteItem.setOnAction(e -> {
            if (onDeleteRequested != null) {
                onDeleteRequested.accept(chat);
            }
        });

        contextMenu.getItems().add(deleteItem);

        contextMenu.show(this, screenX, screenY);
    }

    @Override
    protected void updateItem(Chat chat, boolean empty) {
        super.updateItem(chat, empty);

        if (empty || chat == null) {
            setGraphic(null);
            setText(null);
        } else {
            updateCellContent(chat);
            setGraphic(root);
            setText(null);
        }
    }

    /**
     * Updates the cell content with chat information.
     * 
     * @param chat the chat to display
     */
    private void updateCellContent(Chat chat) {
        // Update name
        nameLabel.setText(chat.getName());

        // The trash button and the grip are only visible while hovered
        trashButton.setVisible(isHover());
        grip.setVisible(isHover());

        // Update selection styling
        selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                content.getStyleClass().add("selected");
            } else {
                content.getStyleClass().remove("selected");
            }
        });
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();

        /*  The trash button ends the row, whose right edge moves with the scroll bar of the list: shift it from
            there onto the center line of the column  */
        if (trashColumn != null) {
            double columnCenter = trashColumn.localToScene(trashColumn.getLayoutBounds().getCenterX(), 0).getX();
            double rowRight     = content.localToScene(content.getWidth() - content.snappedRightInset(), 0).getX();
            trashButton.setTranslateX(columnCenter - (rowRight - trashButton.prefWidth(-1) / 2));
        }
    }

    /** The grip that drags the chat to another place in the list. */
    public Node getGrip() {
        return grip;
    }

    // Callback setters

    /**
     * Sets the callback for when a chat delete is requested.
     * 
     * @param callback the delete callback
     */
    public void setOnDeleteRequested(Consumer<Chat> callback) {
        this.onDeleteRequested = callback;
    }

    /**
     * Sets the callback for when a chat is renamed.
     * 
     * @param callback the rename callback
     */
    public void setOnChatRenamed(Consumer<Chat> callback) {
        this.onChatRenamed = callback;
    }

    /**
     * Sets the column of the trash button: it keeps the center line of the given node, the trash button in the
     * header of the list, whether the scroll bar of the list shows or not.
     *
     * @param column the node whose center line the trash button keeps
     */
    public void setTrashColumn(Node column) {
        this.trashColumn = column;
    }
}