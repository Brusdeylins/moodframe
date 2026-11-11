package art.moodframe.ui.cell;

import art.moodframe.model.Chat;
import art.moodframe.service.TrashCleanupService;
import art.moodframe.ui.component.InPlaceEditableLabel;
import art.moodframe.util.I18nChangeListener;
import art.moodframe.util.I18nManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;

/**
 * Custom ListView cell for displaying Chat objects.
 * 
 * <p>
 * This cell displays chat information including name, timestamp,
 * and provides context menu actions for rename/delete operations.
 * </p>
 * 
 * @since 1.0.0
 */
public class ChatListCell extends ListCell<Chat> implements I18nChangeListener {

    private final VBox content;
    private final HBox headerRow;
    private final InPlaceEditableLabel nameLabel;
    private final Label timeLabel;
    private final Label statusLabel;
    private final Button trashButton;
    private final Button restoreButton;
    private final Button permanentDeleteButton;

    private Consumer<Chat> onDeleteRequested;
    private Consumer<Chat> onRestoreRequested;
    private Consumer<Chat> onPermanentDeleteRequested;
    private Consumer<Chat> onChatRenamed;
    private TrashCleanupService trashCleanupService;

    /**
     * Creates a new chat list cell.
     */
    public ChatListCell() {
        getStyleClass().add("chat-list-cell");
        
        // Register for language change notifications
        I18nManager.addChangeListener(this);

        // Create UI components
        nameLabel = new InPlaceEditableLabel();
        nameLabel.getStyleClass().addAll("text-default", "text-bold");
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        nameLabel.setAlignment(Pos.CENTER_LEFT); // Horizontal alignment
        nameLabel.setPrefHeight(Region.USE_COMPUTED_SIZE); // Use natural height
        // No CSS alignment needed - handled by HBox alignment

        timeLabel = new Label();
        timeLabel.getStyleClass().addAll("text-muted", "text-small");
        timeLabel.setVisible(false);
        timeLabel.managedProperty().bind(timeLabel.visibleProperty());
        timeLabel.setAlignment(Pos.CENTER_LEFT); // Horizontal alignment
        timeLabel.setPrefHeight(Region.USE_COMPUTED_SIZE); // Use natural height
        // No CSS alignment needed - handled by HBox alignment

        // Trash button for active chats (no borders)
        trashButton = new Button();
        FontIcon trashIcon = new FontIcon("mdi2d-delete");
        trashIcon.setIconSize(12);
        trashButton.setGraphic(trashIcon);
        trashButton.getStyleClass().addAll("button-icon", "chat-action-button");
        trashButton.setStyle(
                "-fx-border-color: transparent; -fx-border-width: 0; -fx-background-insets: 0; -fx-border-insets: 0; -fx-padding: 2;");
        trashButton.setVisible(false);
        trashButton.managedProperty().bind(trashButton.visibleProperty());
        trashButton.setMinSize(18, 18);
        trashButton.setMaxSize(18, 18);
        trashButton.setPrefSize(18, 18);
        trashButton.setTooltip(new Tooltip("Move to trash (can be restored)"));

        // Restore button for deleted chats (no borders)
        restoreButton = new Button();
        FontIcon restoreIcon = new FontIcon("mdi2r-restore");
        restoreIcon.setIconSize(12);
        restoreButton.setGraphic(restoreIcon);
        restoreButton.getStyleClass().addAll("button-icon", "chat-action-button");
        restoreButton.setStyle(
                "-fx-border-color: transparent; -fx-border-width: 0; -fx-background-insets: 0; -fx-border-insets: 0; -fx-padding: 2;");
        restoreButton.setVisible(false);
        restoreButton.managedProperty().bind(restoreButton.visibleProperty());
        restoreButton.setMinSize(18, 18);
        restoreButton.setMaxSize(18, 18);
        restoreButton.setPrefSize(18, 18);
        restoreButton.setTooltip(new Tooltip("Restore chat from trash"));

        // Permanent delete button for deleted chats (red trash icon)
        permanentDeleteButton = new Button();
        FontIcon permanentDeleteIcon = new FontIcon("mdi2d-delete-forever");
        permanentDeleteIcon.setIconSize(12);
        permanentDeleteIcon.getStyleClass().add("danger"); // Red coloring
        permanentDeleteButton.setGraphic(permanentDeleteIcon);
        permanentDeleteButton.getStyleClass().addAll("button-icon", "chat-action-button", "danger");
        permanentDeleteButton.setStyle(
                "-fx-border-color: transparent; -fx-border-width: 0; -fx-background-insets: 0; -fx-border-insets: 0; -fx-padding: 2;");
        permanentDeleteButton.setVisible(false);
        permanentDeleteButton.managedProperty().bind(permanentDeleteButton.visibleProperty());
        permanentDeleteButton.setMinSize(18, 18);
        permanentDeleteButton.setMaxSize(18, 18);
        permanentDeleteButton.setPrefSize(18, 18);
        permanentDeleteButton.setTooltip(new Tooltip("Permanently delete chat and all images"));

        statusLabel = new Label();
        statusLabel.getStyleClass().addAll("text-muted", "text-small");
        statusLabel.setVisible(false);

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

        headerRow.getChildren().addAll(nameLabel, spacer, trashButton, timeLabel, restoreButton, permanentDeleteButton);

        // Vertical centering is handled by HBox.setAlignment(Pos.CENTER_LEFT)

        content = new VBox(0); // No spacing between VBox children
        content.setAlignment(Pos.CENTER_LEFT); // Center content vertically
        content.setPadding(new Insets(0, 12.0, 0, 12.0)); // Remove top/bottom padding, keep left/right
        content.setMinHeight(32.0); // Match HBox height
        content.setPrefHeight(32.0); // Set preferred height for compact layout
        content.setMaxHeight(32.0); // Prevent expansion
        // VBox and HBox now have matching heights for proper alignment

        // Only add headerRow, statusLabel is rarely used and takes space
        content.getChildren().add(headerRow);
        // statusLabel will be added dynamically only when needed

        // Set up mouse handling and event listeners
        setupMouseHandling();
        setupEventHandlers();

        // The trash button of an active chat is only visible while the mouse is over the row
        hoverProperty().addListener((obs, wasHover, isHover) -> {
            Chat hovered = getItem();
            if (hovered != null && !hovered.isDeleted()) {
                trashButton.setVisible(isHover);
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

        // Handle restore button clicks
        restoreButton.setOnAction(event -> {
            Chat chat = getItem();
            if (chat != null && onRestoreRequested != null) {
                onRestoreRequested.accept(chat);
            }
            event.consume();
        });

        // Handle permanent delete button clicks
        permanentDeleteButton.setOnAction(event -> {
            Chat chat = getItem();
            if (chat != null && onPermanentDeleteRequested != null) {
                onPermanentDeleteRequested.accept(chat);
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

        if (chat.isDeleted()) {
            // Deleted chat menu
            MenuItem restoreItem = new MenuItem("Restore");
            restoreItem.setGraphic(new FontIcon("mdi2r-restore"));
            restoreItem.setOnAction(e -> {
                if (onRestoreRequested != null) {
                    onRestoreRequested.accept(chat);
                }
            });

            MenuItem permanentDeleteItem = new MenuItem("Delete Permanently");
            permanentDeleteItem.setGraphic(new FontIcon("mdi2d-delete-forever"));
            permanentDeleteItem.getStyleClass().add("danger");
            permanentDeleteItem.setOnAction(e -> {
                if (onPermanentDeleteRequested != null) {
                    onPermanentDeleteRequested.accept(chat);
                }
            });

            contextMenu.getItems().addAll(restoreItem, new SeparatorMenuItem(), permanentDeleteItem);

        } else {
            // Active chat menu (no rename item since we have in-place editing)
            MenuItem deleteItem = new MenuItem("Move to Trash");
            deleteItem.setGraphic(new FontIcon("mdi2d-delete"));
            deleteItem.setOnAction(e -> {
                if (onDeleteRequested != null) {
                    onDeleteRequested.accept(chat);
                }
            });

            contextMenu.getItems().add(deleteItem);
        }

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
            setGraphic(content);
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

        // Update button visibility and status based on chat state
        if (chat.isDeleted()) {
            // Deleted chat: show restore and permanent delete buttons, hide trash button
            trashButton.setVisible(false);
            restoreButton.setVisible(true);
            permanentDeleteButton.setVisible(true);
            nameLabel.setEditable(false); // Disable editing for deleted chats

            // Calculate and show days left for deleted chats using TrashCleanupService
            String daysLeftText;
            if (trashCleanupService != null) {
                int daysRemaining = trashCleanupService.getDaysUntilAutoDeletion(chat);

                if (daysRemaining <= 0) {
                    daysLeftText = I18nManager.getText("status.trash.expires.today");
                    timeLabel.getStyleClass().removeAll("text-muted", "text-warning");
                    timeLabel.getStyleClass().add("text-danger");
                } else if (daysRemaining == 1) {
                    daysLeftText = java.text.MessageFormat.format(
                        I18nManager.getText("status.trash.days.left.singular"), daysRemaining);
                    timeLabel.getStyleClass().removeAll("text-muted", "text-danger");
                    timeLabel.getStyleClass().add("text-warning");
                } else if (daysRemaining <= 7) {
                    daysLeftText = java.text.MessageFormat.format(
                        I18nManager.getText("status.trash.days.left.plural"), daysRemaining);
                    timeLabel.getStyleClass().removeAll("text-muted", "text-danger");
                    timeLabel.getStyleClass().add("text-warning");
                } else {
                    daysLeftText = java.text.MessageFormat.format(
                        I18nManager.getText("status.trash.days.left.plural"), daysRemaining);
                    timeLabel.getStyleClass().removeAll("text-warning", "text-danger");
                    timeLabel.getStyleClass().add("text-muted");
                }
            } else {
                // Fallback to original calculation if service not available
                long daysInTrash = ChronoUnit.DAYS.between(chat.getDeletedAt(),
                        java.time.LocalDateTime.now());
                int daysRemaining = TrashCleanupService.DEFAULT_RETENTION_DAYS - (int) daysInTrash;
                daysLeftText = daysRemaining + " days left";
                timeLabel.getStyleClass().removeAll("text-warning", "text-danger");
                timeLabel.getStyleClass().add("text-muted");
            }

            timeLabel.setText(daysLeftText);
            timeLabel.setVisible(true);

            // Hide status label since we show everything in header now
            statusLabel.setVisible(false);
            content.getStyleClass().add("deleted");
            nameLabel.getStyleClass().add("text-muted");
        } else {
            // Active chat: show trash button only while hovered, hide restore and permanent delete buttons
            trashButton.setVisible(isHover());
            restoreButton.setVisible(false);
            permanentDeleteButton.setVisible(false);
            timeLabel.setVisible(false); // Hide time for active chats
            nameLabel.setEditable(true); // Enable editing for active chats

            // Clean up styling for active chats
            statusLabel.setVisible(false);
            timeLabel.getStyleClass().removeAll("text-danger", "text-warning");
            timeLabel.getStyleClass().add("text-muted");
            content.getStyleClass().remove("deleted");
            nameLabel.getStyleClass().remove("text-muted");
        }

        // Update selection styling
        selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                content.getStyleClass().add("selected");
            } else {
                content.getStyleClass().remove("selected");
            }
        });
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
     * Sets the callback for when a chat restore is requested.
     * 
     * @param callback the restore callback
     */
    public void setOnRestoreRequested(Consumer<Chat> callback) {
        this.onRestoreRequested = callback;
    }

    /**
     * Sets the callback for when a permanent delete is requested.
     * 
     * @param callback the permanent delete callback
     */
    public void setOnPermanentDeleteRequested(Consumer<Chat> callback) {
        this.onPermanentDeleteRequested = callback;
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
     * Sets the trash cleanup service for accurate retention calculations.
     * 
     * @param trashCleanupService the trash cleanup service
     */
    public void setTrashCleanupService(TrashCleanupService trashCleanupService) {
        this.trashCleanupService = trashCleanupService;
    }
    
    @Override
    public void onLanguageChanged() {
        // Update the current cell if it's showing a deleted chat
        Chat currentChat = getItem();
        if (currentChat != null && currentChat.isDeleted()) {
            // Force re-update of the deleted chat UI with new language
            updateItem(currentChat, false);
        }
    }
}