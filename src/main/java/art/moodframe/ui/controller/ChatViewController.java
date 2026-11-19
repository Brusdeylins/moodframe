package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryGroup;
import art.moodframe.model.FrameGroup;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.MoodGroup;
import art.moodframe.service.chat.ChatHistoryService;
import art.moodframe.service.chat.ChatLibrary;
import art.moodframe.service.chat.ModelLock;
import art.moodframe.ui.cell.FrameGroupListCell;
import art.moodframe.ui.cell.MoodGroupListCell;
import art.moodframe.ui.entry.ChatEntryCard;
import art.moodframe.ui.component.DialogButtons;
import art.moodframe.ui.entry.ImageGrid;
import art.moodframe.ui.support.BoxInset;
import art.moodframe.ui.support.StatusSink;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The chat area of the main window ({@code ChatView.fxml}): the header with the name and the cost of the chat, the size
 * of the images and the switch of the views; the entries of the chosen chat or of the open group as chat or as images,
 * the full view of their images, and below, the input area ({@link InputBarController}), whose height the divider
 * keeps. Downloads and deletes of images run on the worker of the window.
 *
 * @since 2.1.3
 */
public class ChatViewController {

    private static final Logger logger = LoggerFactory.getLogger(ChatViewController.class);

    /** What the entries ask of the window: the actions at an entry or an image that reach beyond the chat area. */
    public interface Actions {

        /**
         * Takes over prompt, model, frame and mood of an entry, or one of them.
         *
         * @param entry the entry
         * @param part  what to take over
         */
        void reuse(ChatEntry entry, ChatEntryCard.Reuse part);

        /**
         * Attaches an image to edit it.
         *
         * @param imagePath the file of the image
         */
        void edit(String imagePath);

        /**
         * Takes over the seed of an image.
         *
         * @param seed the seed
         */
        void copySeed(long seed);

        /** Whether a seed applies to the chosen model, a model of BFL. */
        boolean seedApplies();

        /**
         * Takes over the mood of an entry, or saves it as a new mood first.
         *
         * @param entry the entry
         * @param save  whether to save it as a new mood
         * @return whether the mood was taken over
         */
        boolean takeOverMood(ChatEntry entry, boolean save);

        /**
         * Saves aspect ratio and resolution of an entry as a new frame.
         *
         * @param entry the entry
         */
        void saveFrame(ChatEntry entry);

        /**
         * Opens "Mood from image" on an image.
         *
         * @param image the image
         */
        void moodFromImage(File image);

        /**
         * "Use mood" at an image: takes over the mood of its entry.
         *
         * @param entry the entry of the image
         */
        void useImageMood(ChatEntry entry);

        /**
         * Opens the chat an entry of the open group was generated in.
         *
         * @param entry the entry
         */
        void openChatOf(ChatEntry entry);
    }

    // Chat Header
    @FXML
    private HBox chatHeader;
    @FXML
    private Label chatTitleLabel;
    @FXML
    private Label chatTotalCostLabel;
    @FXML
    private Slider imageSizeSlider;
    @FXML
    private Tooltip imageSizeTooltip;
    @FXML
    private ToggleButton chatViewButton;
    @FXML
    private Tooltip chatViewTooltip;
    @FXML
    private ToggleButton imagesViewButton;
    @FXML
    private Tooltip imagesViewTooltip;

    // Entries and input area
    @FXML
    private SplitPane chatInputSplitPane;
    @FXML
    private StackPane chatAreaStackPane;
    @FXML
    private ListView<ChatEntry> chatEntriesListView;
    @FXML
    private HBox welcomeContainer;
    @FXML
    private Label welcomeLabel;
    @FXML
    private InputBarController inputBarController;

    private ImageGrid imageGrid;

    private ApplicationConfig config;
    private ChatLibrary chatLibrary;
    private ChatHistoryService chatHistory;
    private Executor worker;
    private StatusSink status;
    private Actions actions;

    /*  the chat that takes the new prompts and the open group of the sidebar, whose entries show in front of it  */
    private Supplier<Chat> chat;
    private Supplier<EntryGroup> group;

    /*  The chat keeps its end in view while the images of a new entry arrive: from sending until the user scrolls
        away from the end  */
    private boolean wasScrolledToBottom = false;
    private int lastChatEntriesCount = 0; // Track number of entries to detect new ones

    /*  The full view of the images, with the steps from entry to entry  */
    private EntryFullView fullView;

    /*  the cards the cells show now, which listen to the language until they are disposed  */
    private final java.util.Set<ChatEntryCard> shownCards =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private EventHandler<KeyEvent> reuseFilter;

    @FXML
    private void initialize() {
        /*  the title starts on the left edge of the cards and of the first column of images, 1 px further out for the
            side bearing of its letters, the view switch ends on their right edge  */
        chatHeader.setPadding(new javafx.geometry.Insets(chatHeader.getPadding().getTop(), BoxInset.RIGHT,
                chatHeader.getPadding().getBottom(), BoxInset.LEFT - 1.0));
    }

    /*  the thumbnails and the size check of the images, handed by the main window before init  */
    private art.moodframe.ui.entry.ImageSources imageSources = art.moodframe.ui.entry.ImageSources.NONE;

    /**
     * Sets where the cards and the images view get the pictures of the images; call it before {@link #init}.
     *
     * @param sources the thumbnails and the size check
     */
    public void useImageSources(art.moodframe.ui.entry.ImageSources sources) {
        this.imageSources = sources;
    }

    /**
     * Connects the chat area to the library and the selection of the sidebar.
     *
     * @param config      remembers view, image size and height of the input area (may be null)
     * @param chatLibrary the chats, whose entries the area shows
     * @param chatHistory finds the files of the images and the entries of a group (may be null)
     * @param worker      runs the downloads and the deletes off the JavaFX thread
     * @param overlayHost the stack of the whole window, which takes the full view of the images
     * @param chat        the chat that takes the new prompts
     * @param group       the open group of the sidebar, null while the chat is shown
     * @param status      shows what the area did
     * @param actions     carries out what reaches beyond the chat area
     */
    public void init(ApplicationConfig config, ChatLibrary chatLibrary, ChatHistoryService chatHistory, Executor worker,
                     StackPane overlayHost, Supplier<Chat> chat, Supplier<EntryGroup> group, StatusSink status,
                     Actions actions) {
        this.config = config;
        this.chatLibrary = chatLibrary;
        this.chatHistory = chatHistory;
        this.worker = worker;
        this.chat = chat;
        this.group = group;
        this.status = status;
        this.actions = actions;
        setupChatEntriesListView();
        setupImagesView();
        setupChatInputHeight();
        optimizeScrollPane();
        fullView = new EntryFullView(config, overlayHost, fullViewHost());
    }

    /*  The full view pages through the shown entries and hands its actions to the window  */
    private EntryFullView.Host fullViewHost() {
        return new EntryFullView.Host() {
            @Override
            public Chat chat() {
                return chat.get();
            }

            @Override
            public EntryGroup group() {
                return group.get();
            }

            @Override
            public List<ChatEntry> shownEntries() {
                return chatEntriesListView.getItems();
            }

            @Override
            public String imagePath(GeneratedImage image, boolean isChatDeleted) {
                return ChatViewController.this.imagePath(image, isChatDeleted);
            }

            @Override
            public void edit(String path) {
                actions.edit(path);
            }

            @Override
            public void download(String path) {
                downloadImageFromPath(path);
            }

            @Override
            public void status(String message) {
                status.status(message);
            }
        };
    }

    /** The input area below the chat. */
    public InputBarController inputBar() {
        return inputBarController;
    }

    /** Shortcut+C over an entry or an image reuses it ("Reuse"), unless a text field has a selection to copy. */
    public EventHandler<KeyEvent> reuseKeyFilter() {
        return reuseFilter;
    }

    /** Shows the chosen chat, or the open group in front of it, anew. */
    public void showSelection() {
        updateChatView(chat.get());
    }

    /**
     * Shows a new entry at once, before the library reports it: at the end of the chat, or of the open group if it
     * belongs to it.
     *
     * @param entry the entry
     */
    public void addEntry(ChatEntry entry) {
        addNewChatEntryToUI(entry);
    }

    /** Renders the cards anew, e.g. once another model decides whether "Copy seed" applies. */
    public void refreshEntries() {
        chatEntriesListView.refresh();
    }

    /**
     * Scrolls an entry into view.
     *
     * @param entry the entry
     */
    public void scrollTo(ChatEntry entry) {
        chatEntriesListView.scrollTo(entry);
    }

    /** Closes the full view of the images, if open. */
    public void closePreview() {
        if (fullView != null)
            fullView.close();
    }

    /**
     * Lets go of what the chat area registered beyond the window, once it closes: the cards of the entries leave their
     * cells and stop listening to the language, the full view closes and stops listening as well.
     */
    public void dispose() {
        shownCards.forEach(ChatEntryCard::dispose);
        shownCards.clear();
        chatEntriesListView.setItems(null);
        if (fullView != null)
            fullView.dispose();
    }

    /** Shows the texts of the chat area in the current language. */
    public void updateTexts() {
        welcomeLabel.setText(I18nManager.getText("app.welcome"));
        Chat current = chat.get();
        String title = chatTitleLabel.getText();
        if ("Select a chat".equals(title) || "Chat auswählen".equals(title) || current == null || current.isDeleted())
            chatTitleLabel.setText(I18nManager.getText("chat.select"));
        chatViewTooltip.setText(I18nManager.getText("chat.view.chat"));
        imagesViewTooltip.setText(I18nManager.getText("chat.view.images"));
        imageSizeTooltip.setText(I18nManager.getText("chat.image.size.tooltip"));

        /*  The tiles of the images view carry texts of their own  */
        if (imageGrid != null)
            imageGrid.refresh();
        if (current != null)
            updateChatTotalCost(current);

        /*  An open group keeps its name in the header  */
        if (group.get() != null)
            updateGroupHeader();
    }

    /**
     * Configures the divider above the input area: the input area keeps its height in pixels, restored after the first
     * layout and remembered when the user drags the divider.
     */
    private void setupChatInputHeight() {
        chatInputSplitPane.heightProperty().addListener((obs, oldHeight, newHeight) -> {
            if (oldHeight.doubleValue() == 0 && newHeight.doubleValue() > 0)
                Platform.runLater(() -> restoreChatInputHeight(config));
        });
        if (config != null)
            chatInputSplitPane.addEventFilter(MouseEvent.MOUSE_RELEASED, (event) -> {
                Node node = event.getTarget() instanceof Node target ? target : null;
                while (node != null && !node.getStyleClass().contains("split-pane-divider"))
                    node = node.getParent();
                if (node != null) {
                    chatInputSplitPane.layout();
                    config.ui().setChatInputHeight(inputBarController.area().getHeight());
                }
            });
    }

    /**
     * Gives the input area below the chat its remembered height, or else the default one, which shows the button
     * column and about five lines of the prompt.
     *
     * @param config the configuration holding the remembered height, or null
     */
    private void restoreChatInputHeight(art.moodframe.config.ApplicationConfig config) {
        /*  The items of the SplitPane get their styles after its first layout, the default height needs them  */
        chatInputSplitPane.applyCss();
        double savedHeight = config != null ? config.ui().getChatInputHeight() : 0;
        resizeChatInputArea(savedHeight > 0 ? savedHeight : inputBarController.area().prefHeight(inputBarController.area().getWidth()));
    }

    /**
     * Moves the divider above the input area below the chat, so that the input area gets the given height.
     *
     * @param height the height in pixels
     */
    private void resizeChatInputArea(double height) {
        /*  The divider position is a share of the SplitPane height: shift it by the missing pixels  */
        chatInputSplitPane.layout();
        javafx.geometry.Insets insets = chatInputSplitPane.getInsets();
        double size = chatInputSplitPane.getHeight() - insets.getTop() - insets.getBottom();
        if (size > 0)
            chatInputSplitPane.setDividerPosition(0,
                    chatInputSplitPane.getDividerPositions()[0] + (inputBarController.area().getHeight() - height) / size);
    }

    /**
     * Shows a new entry or one that got images back from the trash at its place in the open chat or group; an entry
     * shown already is rendered anew, one that went to the trash leaves.
     */
    public void showEntryChange(String entryId) {
        ChatEntry entry = chatLibrary.findEntry(entryId);
        if (entry == null) {
            removeEntryCardFromUI(entryId);
            updateChatHeader();
            updateChatTotalCost(chat.get());
            return;
        }
        boolean inChat = chat.get() != null && chat.get().getId().equals(entry.getChatId());
        boolean belongs = group.get() != null ? group.get().holds(entry) : inChat;
        ObservableList<ChatEntry> items = chatEntriesListView.getItems();
        int index = items != null ? items.indexOf(entry) : -1;

        /*  Setting the item again renders its card anew, also for an entry that got back a single image  */
        if (index >= 0)
            items.set(index, entry);
        else if (belongs && items != null) {
            java.util.Comparator<ChatEntry> order = java.util.Comparator.comparingInt(ChatEntry::getSortOrder)
                    .thenComparing(ChatEntry::getCreatedAt);
            items.add((int) items.stream().filter((other) -> order.compare(other, entry) <= 0).count(), entry);
            if (group.get() == null)
                lastChatEntriesCount = ChatLibrary.entriesOf(chat.get()).size();
        }
        else if (belongs)
            addNewChatEntryToUI(entry);
        if (group.get() == null && inChat)
            updateChatHeader();
    }

    /**
     * Shows a change of an entry during its generation, on the JavaFX thread.
     *
     * @param chatEntry the entry
     */
    public void entryUpdated(ChatEntry chatEntry) {
        try {
            // Only update if this is the currently displayed chat and entry exists (an open group shows all chats)
            java.util.List<ChatEntry> shown = group.get() != null ? chatEntriesListView.getItems()
                    : chat.get() != null ? ChatLibrary.entriesOf(chat.get()) : null;
            if (shown != null && shown.contains(chatEntry)) {
                // Find and update only the specific ChatEntryCard instead of reloading entire
                // chat
                updateSpecificChatEntryCard(chatEntry);

            }
        } catch (Exception e) {
            logger.error("Error updating UI for ChatEntry", e);
        }
    }

    /**
     * Shows a change of an image during its generation, on the JavaFX thread.
     *
     * @param image the image
     */
    public void imageUpdated(GeneratedImage image) {
        try {
            // Only update if this image belongs to the currently displayed chat (an open group shows all chats)
            java.util.List<ChatEntry> shown = group.get() != null ? chatEntriesListView.getItems()
                    : chat.get() != null ? ChatLibrary.entriesOf(chat.get()) : null;
            if (shown != null) {
                // Find the ChatEntry that contains this image
                ChatEntry parentEntry = shown.stream()
                        .filter(entry -> ChatLibrary.imagesOf(entry).stream()
                                .anyMatch(img -> img.getId().equals(image.getId())))
                        .findFirst()
                        .orElse(null);

                if (parentEntry != null) {
                    // Update only the specific ChatEntryCard instead of reloading entire chat
                    updateSpecificChatEntryCard(parentEntry);

                } else {
                    // Image not found in current chat, no UI update needed
                }
            } else {
                // No current chat, no UI update needed
            }
        } catch (Exception e) {
            logger.error("Error updating UI for GeneratedImage", e);
        }
    }

    /**
     * Updates a specific ChatEntryCard without reloading the entire chat view.
     * With virtualized ListView, we refresh the ListView items to trigger cell update.
     */
    private void updateSpecificChatEntryCard(ChatEntry chatEntry) {
        if (chatEntriesListView == null || chatEntriesListView.getItems() == null) {
            return;
        }

        // Force refresh by updating the entry in the list
        // ListView will automatically re-render the visible cell
        chatEntriesListView.refresh();
        if (wasScrolledToBottom)
            scrollToBottom("entry updated");

        // Update total cost after entry update
        if (chat.get() != null) {
            updateChatTotalCost(chat.get());
        }
    }

    /**
     * Adds a new ChatEntry directly to the UI without reloading the entire chat.
     * With virtualized ListView, we add to the items list and scroll to bottom.
     */
    private void addNewChatEntryToUI(ChatEntry chatEntry) {
        try {
            /*  An open group shows the entry if it belongs to it, the chat takes it in any case  */
            if (group.get() != null) {
                if (group.get().holds(chatEntry)) {
                    chatEntriesListView.getItems().add(chatEntry);
                    wasScrolledToBottom = true;
                    scrollToBottom("new entry added");
                }
                return;
            }

            // Only add if this is the current chat
            if (chat.get() != null && chat.get().equals(getCurrentChatFromEntry(chatEntry))) {
                if (chatEntriesListView.getItems() == null) {
                    chatEntriesListView.setItems(javafx.collections.FXCollections.observableArrayList());
                }

                // Hide welcome if visible
                welcomeContainer.setVisible(false);
                showEntries(true);

                // Add new entry to the ListView items
                chatEntriesListView.getItems().add(chatEntry);

                // Update chat header to reflect new message count and cost
                updateChatHeader();

                // Auto-scroll to bottom for new entries
                wasScrolledToBottom = true;
                scrollToBottom("new entry added");

                logger.debug("Added new ChatEntry to ListView - total entries: {}",
                        chatEntriesListView.getItems().size());
            }
        } finally {
            // UI operation completed
        }
    }

    /**
     * Scrolls the chat scroll pane to the bottom with robust layout handling.
     *
     * @param reason the reason for scrolling (for logging)
     */
    private void scrollToBottom(String reason) {
        // Multiple scroll attempts with different delays to handle dynamic content
        int[] delays = { 0, 50, 150, 300, 600 };

        for (int delay : delays) {
            if (delay == 0) {
                // Immediate attempt
                Platform.runLater(() -> {
                    performScroll(reason, "immediate");
                });
            } else {
                // Delayed attempts
                javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(javafx.util.Duration.millis(delay), e -> {
                            performScroll(reason, delay + "ms delay");
                        }));
                timeline.play();
            }
        }
    }

    /**
     * Performs the actual scroll operation.
     *
     * @param reason the reason for scrolling
     * @param timing timing description
     */
    private void performScroll(String reason, String timing) {
        /*  At position 1 the list lays its cells out from the end; scrollTo(last) misjudges cells not measured yet  */
        if (wasScrolledToBottom && chatEntriesListView != null
                && chatEntriesListView.lookup(".virtual-flow") instanceof VirtualFlow<?> flow)
            flow.setPosition(1.0);
    }

    /**
     * Whether the end of the last entry is in view, so the chat keeps it in view while images arrive.
     *
     * @return true if the end is in view or the list shows nothing yet
     */
    private boolean isChatAtEnd() {
        if (!(chatEntriesListView.lookup(".virtual-flow") instanceof VirtualFlow<?> flow))
            return true;
        IndexedCell<?> last = flow.getLastVisibleCell();
        return last == null || last.getIndex() == flow.getCellCount() - 1
                && last.getLayoutY() + last.getHeight() <= flow.getHeight() + 1.0;
    }

    /**
     * Helper method to get the chat that contains a given entry.
     */
    private Chat getCurrentChatFromEntry(ChatEntry entry) {
        return chatLibrary.chats().stream()
                .filter(chat -> chat.getId().equals(entry.getChatId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Sets up the virtualized ListView for ChatEntry display.
     * This replaces the previous VBox-based approach for better performance with large chats.
     */
    private void setupChatEntriesListView() {
        if (chatEntriesListView == null) {
            logger.warn("chatEntriesListView is null - skipping setup");
            return;
        }

        // Configure ListView for optimal performance
        chatEntriesListView.setFocusTraversable(false);
        chatEntriesListView.getStyleClass().add("chat-entries-list");

        // CRITICAL: Fix horizontal scrolling - cells must fill width
        // This makes cells take full width and prevents horizontal scroll
        chatEntriesListView.setFixedCellSize(-1); // Variable height cells

        /*  Scrolling away from the end, by wheel or scroll bar, lets the chat stay where the user left it; while the
            user scrolls, the chat does not follow its end  */
        chatEntriesListView.addEventFilter(ScrollEvent.SCROLL, (event) -> {
            wasScrolledToBottom = false;
            Platform.runLater(() -> wasScrolledToBottom = isChatAtEnd());
        });
        chatEntriesListView.addEventFilter(MouseEvent.MOUSE_PRESSED, (event) -> wasScrolledToBottom = false);
        chatEntriesListView.addEventFilter(MouseEvent.MOUSE_RELEASED,
                (event) -> Platform.runLater(() -> wasScrolledToBottom = isChatAtEnd()));

        /*  Shortcut+C over an entry or over an image of the images view reuses the entry ("Reuse"), unless a text
            field has a selection to copy  */
        KeyCombination reuseKey = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
        reuseFilter = (event) -> {
            if (!reuseKey.match(event)
                    || event.getTarget() instanceof TextInputControl text && !text.getSelectedText().isEmpty())
                return;
            for (Node cell : chatEntriesListView.lookupAll(".list-cell"))
                if (cell.isHover() && cell instanceof ListCell<?> listCell
                        && listCell.getItem() instanceof ChatEntry entry) {
                    actions.reuse(entry, ChatEntryCard.Reuse.ALL);
                    event.consume();
                    return;
                }
            ChatEntry imageEntry = imageGrid.isVisible() ? imageGrid.getHoveredEntry() : null;
            if (imageEntry != null) {
                actions.reuse(imageEntry, ChatEntryCard.Reuse.ALL);
                event.consume();
            }
        };

        // CellFactory creates ChatEntryCard for each visible cell
        chatEntriesListView.setCellFactory(listView -> new javafx.scene.control.ListCell<ChatEntry>() {
            private art.moodframe.ui.entry.ChatEntryCard currentCard = null;

            {
                // CRITICAL: Cell must fill entire width to prevent horizontal scroll
                setMaxWidth(Double.MAX_VALUE);
                setPrefWidth(0); // Let it grow with ListView
                /*  The cards start on the inset of the boxes, behind the 1 px border of the list, and end on the right
                    edge of the view switch; the list is wider than the cell by its two borders and the scroll bar  */
                paddingProperty().bind(Bindings.createObjectBinding(() -> new javafx.geometry.Insets(5.0,
                        Math.max(0.0, BoxInset.RIGHT + 1.0 - (listView.getWidth() - getWidth())), 5.0,
                        BoxInset.LEFT - 1.0), widthProperty(), listView.widthProperty()));
            }

            @Override
            protected void updateItem(ChatEntry entry, boolean empty) {
                super.updateItem(entry, empty);

                // Dispose previous card to prevent memory leaks
                if (currentCard != null) {
                    currentCard.dispose();
                    shownCards.remove(currentCard);
                    currentCard = null;
                }

                if (empty || entry == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    // Create new ChatEntryCard for this entry
                    // Pass isFluxModelSelected supplier to constructor so it's available during image card creation
                    boolean isChatDeleted = group.get() == null && chat.get() != null && chat.get().isDeleted();
                    currentCard = new ChatEntryCard(entry, isChatDeleted, actions::seedApplies, chatHistory,
                            imageSources);
                    shownCards.add(currentCard);

                    // CRITICAL: Card must also fill width
                    currentCard.setMaxWidth(Double.MAX_VALUE);

                    // Set up callbacks
                    currentCard.setOnSeedCopy(actions::copySeed);

                    currentCard.setOnReuse((reusedEntry, part) -> actions.reuse(reusedEntry, part));

                    /*  The images share the width of the cell, which a reused cell knows from the start, each at most
                        as large as the image size of the chat header  */
                    currentCard.bindAvailableWidth(this);
                    currentCard.bindTileSize(imageSizeSlider.valueProperty());

                    currentCard.setOnImageClick((clickedEntry, imageIndex) -> {
                        fullView.showEntry(clickedEntry, imageIndex);
                    });

                    currentCard.setOnImageDownload(filePath -> {
                        downloadImageFromPath(filePath);
                    });

                    currentCard.setOnImageEdit(filePath -> {
                        actions.edit(filePath);
                    });

                    currentCard.setOnEntryDelete(entryId -> {
                        handleDeleteEntry(entryId);
                    });

                    currentCard.setOnImageDelete((entryId, imageId) -> {
                        handleDeleteImage(entryId, imageId);
                    });

                    currentCard.setOnMoodTakeOver(actions::takeOverMood);
                    currentCard.setOnFrameSave(actions::saveFrame);
                    currentCard.setOnMoodFromImage(actions::moodFromImage);
                    currentCard.setOnImageMood(actions::useImageMood);

                    /*  The entries of a group name their chat and lead to it  */
                    Chat origin = group.get() != null ? getCurrentChatFromEntry(entry) : null;
                    if (origin != null)
                        currentCard.showOrigin(origin.getName(), () -> actions.openChatOf(entry));

                    setGraphic(currentCard);
                    setText(null);
                }
            }
        });
    }

    /*  The image size reaches from 100 px to the size of the thumbnails in screen pixels, at most 480, so the images
        stay sharp; Cmd/Ctrl + and - change it in steps  */
    private static final double MIN_IMAGE_SIZE = 100.0;

    private static final double IMAGE_SIZE_STEP = 20.0;

    /**
     * Sets up the images view of the chat area, the switch between it and the chat in the chat header and the size
     * of the images both views share; view and size are remembered for all chats and groups.
     */
    private void setupImagesView() {
        imageGrid = new ImageGrid(this::shownImagePath, imageSources);
        imageGrid.setVisible(false);
        chatAreaStackPane.getChildren().add(chatAreaStackPane.getChildren().indexOf(chatEntriesListView) + 1,
                imageGrid);
        imageGrid.entriesProperty().bind(chatEntriesListView.itemsProperty());

        imageGrid.setOnOpen(this::showGallery);
        imageGrid.setOnAction((item, action) -> {
            String path = shownImagePath(item.image());
            switch (action) {
                case REUSE -> actions.reuse(item.entry(), ChatEntryCard.Reuse.ALL);
                case VIEW -> showGallery(item);
                case EDIT -> actions.edit(path);
                case DOWNLOAD -> downloadImageFromPath(path);
                case MOOD -> {
                    if (item.entry().getMood() != null)
                        actions.useImageMood(item.entry());
                    else if (path != null && new File(path).exists())
                        actions.moodFromImage(new File(path));
                }
                case DELETE -> handleDeleteImage(item.entry().getId(), item.image().getId());
            }
        });
        imageGrid.setOnReuse((entry) -> actions.reuse(entry, ChatEntryCard.Reuse.ALL));
        imageGrid.setOnSeedCopy(actions::copySeed, actions::seedApplies);

        /*  The largest sizes follow the scale of the screen the window is on, also when it moves to another screen  */
        DoubleProperty outputScale = new SimpleDoubleProperty(Screen.getPrimary().getOutputScaleX());
        imageSizeSlider.setMin(MIN_IMAGE_SIZE);
        imageSizeSlider.maxProperty().bind(ImageGrid.largestChoice(outputScale));
        imageSizeSlider.setValue(config != null ? config.ui().getImageTileSize() : 180.0);
        imageGrid.tileSizeProperty().bind(imageSizeSlider.valueProperty());
        imageGrid.maxTileSizeProperty().bind(ImageGrid.sharpSize(outputScale));
        imageSizeSlider.valueProperty().addListener((obs, oldSize, size) -> {
            if (!imageSizeSlider.isValueChanging() && config != null)
                config.ui().setImageTileSize(size.doubleValue());
        });
        imageSizeSlider.valueChangingProperty().addListener((obs, wasChanging, changing) -> {
            if (!changing && config != null)
                config.ui().setImageTileSize(imageSizeSlider.getValue());
        });
        chatAreaStackPane.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene == null)
                return;
            scene.addEventFilter(KeyEvent.KEY_PRESSED, this::changeImageSize);
            scene.windowProperty().addListener((windowObs, oldWindow, window) -> {
                if (window != null)
                    outputScale.bind(window.outputScaleXProperty());
            });
        });

        /*  One of the views stays chosen  */
        ToggleGroup views = new ToggleGroup();
        chatViewButton.setToggleGroup(views);
        imagesViewButton.setToggleGroup(views);
        imagesViewButton.setSelected(config != null && "images".equals(config.ui().getChatView()));
        views.selectedToggleProperty().addListener((obs, oldView, view) -> {
            if (view == null) {
                oldView.setSelected(true);
                return;
            }
            if (config != null)
                config.ui().setChatView(view == imagesViewButton ? "images" : "chat");
            showEntries(chatEntriesListView.isVisible() || imageGrid.isVisible());
        });
    }

    /**
     * Shows the entries of the chat or of the open group as chat or as images, as chosen in the chat header, or hides
     * both views.
     *
     * @param shown whether entries are shown
     */
    private void showEntries(boolean shown) {
        boolean images = imagesViewButton.isSelected();
        chatEntriesListView.setVisible(shown && !images);
        imageGrid.setVisible(shown && images);
    }

    /** Cmd/Ctrl + and - change the image size in steps, but not in the full view, where + and - zoom. */
    private void changeImageSize(KeyEvent event) {
        if (!event.isShortcutDown() || fullView != null && fullView.isShowing())
            return;
        switch (event.getCode()) {
            case PLUS, ADD, EQUALS -> imageSizeSlider.setValue(imageSizeSlider.getValue() + IMAGE_SIZE_STEP);
            case MINUS, SUBTRACT -> imageSizeSlider.setValue(imageSizeSlider.getValue() - IMAGE_SIZE_STEP);
            default -> {
                return;
            }
        }
        event.consume();
    }

    /** The file of an image of the shown entries, in the trash for a chat there. */
    private String shownImagePath(GeneratedImage image) {
        return imagePath(image, group.get() == null && chat.get() != null && chat.get().isDeleted());
    }

    /**
     * Opens the full view on an image of the images view, paging through all images of the grid whose file is there,
     * each with its mood, frame and model.
     *
     * @param item the image to start with
     */
    private void showGallery(ImageGrid.Item item) {
        List<String> paths = new java.util.ArrayList<>();
        List<String> captions = new java.util.ArrayList<>();
        int start = -1;
        for (ImageGrid.Item image : imageGrid.getImages()) {
            String path = image.image().getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED
                    ? shownImagePath(image.image()) : null;
            if (path == null || !new File(path).exists())
                continue;
            if (image.equals(item))
                start = paths.size();
            paths.add(path);
            captions.add(ImageGrid.caption(image.entry()));
        }
        if (start >= 0)
            fullView.showGallery(paths, captions, start);
    }

    /**
     * Updates the chat view area with the selected chat's content.
     *
     * @param shown the chat to display, or null to show welcome message
     */
    private void updateChatView(Chat shown) {
        /*  An open group stays in front, the chat only takes the new prompts  */
        if (group.get() != null) {
            showGroup();
            return;
        }

        // Update header labels
        if (shown != null) {
            if (chatTitleLabel != null) {
                chatTitleLabel.setText(shown.getName());
            }
            updateChatTotalCost(shown);
        } else {
            if (chatTitleLabel != null) {
                chatTitleLabel.setText(I18nManager.getText("chat.select"));
            }
            if (chatTotalCostLabel != null) {
                chatTotalCostLabel.setText("");
            }
        }

        // Check if we should auto-scroll to bottom
        boolean shouldAutoScroll = false;
        if (shown != null && shown == chat.get()) {
            int currentEntriesCount = ChatLibrary.entriesOf(shown).size();
            boolean hasNewEntries = currentEntriesCount > lastChatEntriesCount;
            shouldAutoScroll = hasNewEntries;
            lastChatEntriesCount = currentEntriesCount;
        } else if (shown != null) {
            shouldAutoScroll = true;
            lastChatEntriesCount = ChatLibrary.entriesOf(shown).size();
        }

        if (shown == null) {
            // Show welcome message, hide ListView
            welcomeContainer.setVisible(true);
            welcomeLabel.setText(I18nManager.getText("app.welcome"));
            showEntries(false);
            chatEntriesListView.setItems(null);
            lastChatEntriesCount = 0;
        } else {
            // Hide welcome, show ListView with chat entries
            welcomeContainer.setVisible(false);
            showEntries(true);

            // Create ObservableList from chat entries for virtualized ListView
            javafx.collections.ObservableList<ChatEntry> entries =
                javafx.collections.FXCollections.observableArrayList(ChatLibrary.entriesOf(shown));
            chatEntriesListView.setItems(entries);

            logger.debug("Loading chat {} with {} entries (virtualized ListView)",
                    shown.getName(), entries.size());

            // Auto-scroll to bottom if needed
            if (shouldAutoScroll && !entries.isEmpty()) {
                Platform.runLater(() -> {
                    chatEntriesListView.scrollTo(entries.size() - 1);
                });
            }
        }
    }

    /**
     * Updates the chat header (title, message count and cost) without reloading the
     * entire chat. With virtualized ListView, we just update the title label and cost.
     */
    public void updateChatHeader() {
        if (group.get() != null)
            updateGroupHeader();
        else if (chat.get() != null) {
            // Update title label if present
            if (chatTitleLabel != null) {
                chatTitleLabel.setText(chat.get().getName());
            }
            // Update cost
            updateChatTotalCost(chat.get());
        }
    }

    /**
     * Updates the total cost display in the chat header.
     *
     * @param chat the chat to calculate cost for
     */
    private void updateChatTotalCost(Chat chat) {
        /*  An open group names the chat of the new prompts there instead  */
        if (group.get() != null)
            updateGroupHeader();
        else if (chatTotalCostLabel != null && chat != null)
            chatTotalCostLabel.setText(costText(chat));
    }

    /*  The cost of a chat for its header: all it cost, the deleted part named; empty for a chat without cost  */
    private static String costText(Chat chat) {
        /*  both under the lock of the models, whose entries another thread may change meanwhile  */
        double[] cost = ModelLock.read(() -> new double[] {art.moodframe.model.ChatCost.total(chat),
                art.moodframe.model.ChatCost.deleted(chat)});
        double total = cost[0];
        if (total <= 0)
            return "";
        double deleted = cost[1];
        return deleted > 0
                ? I18nManager.getText("chat.total.cost.with.deleted", String.format("%.2f", total),
                        String.format("%.2f", deleted))
                : I18nManager.getText("chat.total.cost", String.format("%.2f", total));
    }

    /**
     * Shows the entries of the open group, the oldest first as in a chat. They are the entries of the loaded chats, so
     * running requests keep updating them. The worker queries which entries the group holds; the entries show once
     * the answer is there, unless another group or a chat was opened meanwhile.
     */
    private void showGroup() {
        EntryGroup shown = group.get();

        /*  Back in a chat, the view scrolls to its latest entry  */
        lastChatEntriesCount = 0;
        updateGroupHeader();
        welcomeContainer.setVisible(false);
        showEntries(true);
        worker.execute(() -> {
            List<String> ids = List.of();
            try {
                if (chatHistory != null)
                    ids = chatHistory.findChatEntryIds(shown);
            } catch (Exception e) {
                logger.error("Failed to load the entries of the group {}", shown, e);
            }
            List<String> found = ids;
            Platform.runLater(() -> {
                if (group.get() == shown)
                    showGroupEntries(found);
            });
        });
    }

    /*  Shows the entries of the open group in the order of their IDs, as far as the loaded chats hold them  */
    private void showGroupEntries(List<String> ids) {
        java.util.Map<String, ChatEntry> loaded = chatLibrary.read((chats) -> {
            java.util.Map<String, ChatEntry> entries = new java.util.HashMap<>();
            chats.stream().filter((chat) -> !chat.isDeleted())
                    .forEach((chat) -> chat.getEntries().forEach((entry) -> entries.put(entry.getId(), entry)));
            return entries;
        });
        ObservableList<ChatEntry> entries = FXCollections.observableArrayList();
        ids.stream().map(loaded::get).filter(java.util.Objects::nonNull).forEach(entries::add);
        chatEntriesListView.setItems(entries);
        if (!entries.isEmpty())
            Platform.runLater(() -> chatEntriesListView.scrollTo(entries.size() - 1));
    }

    /** Shows the name of the open group above its entries and the chat that takes the new prompts. */
    private void updateGroupHeader() {
        chatTitleLabel.setText(switch (group.get()) {
            case MoodGroup group -> MoodGroupListCell.nameOf(group);
            case FrameGroup group -> FrameGroupListCell.nameOf(group);
        });
        chatTotalCostLabel.setText(chat.get() != null && !chat.get().isDeleted()
                ? I18nManager.getText("sidebar.group.target", chat.get().getName())
                : I18nManager.getText("sidebar.group.target.none"));
    }

    /**
     * Downloads an image from the given file path to the Downloads folder, named by its chat and its creation time,
     * which the shown entries know.
     */
    private void downloadImageFromPath(String filePath) {
        if (filePath == null || filePath.isEmpty() || !new File(filePath).exists()) {
            logger.warn("Image download requested but the file does not exist: {}", filePath);
            status.failure(I18nManager.getText("download.image.notfound"));
            return;
        }

        /*  The file is found by its name, which is the same in the images folder and in the trash  */
        String name = new File(filePath).getName();
        String chatName = null;
        java.time.LocalDateTime createdAt = null;
        for (ChatEntry entry : chatEntriesListView.getItems()) {
            for (GeneratedImage image : ChatLibrary.imagesOf(entry)) {
                if (image.getLocalFilePath() != null && new File(image.getLocalFilePath()).getName().equals(name)) {
                    Chat chat = getCurrentChatFromEntry(entry);
                    chatName = chat != null ? chat.getName() : null;
                    createdAt = image.getCreatedAt();
                }
            }
        }

        /*  The copy runs on the worker, the status follows on the FX thread  */
        String copyChatName = chatName;
        java.time.LocalDateTime copyCreatedAt = createdAt;
        worker.execute(() -> {
            try {
                String filename = art.moodframe.util.DownloadsFolder.copy(new File(filePath), copyChatName,
                        copyCreatedAt, config.paths().downloads().toFile());
                Platform.runLater(() -> status.status(
                        java.text.MessageFormat.format(I18nManager.getText("download.image.success"), filename)));
            } catch (Exception e) {
                logger.error("Failed to download image to Downloads folder", e);
                status.failure(java.text.MessageFormat.format(
                        I18nManager.getText("download.image.error"), e.getMessage()));
            }
        });
    }

    /**
     * Handles the deletion of a chat entry after confirmation. The library books the cost and reports the change,
     * which removes the card and updates the cost display (see {@link #showEntryChange}).
     *
     * @param entryId the entry ID to delete
     */
    private void handleDeleteEntry(String entryId) {
        ChatEntry entry = chatLibrary.findEntry(entryId);
        if (entry == null) {
            logger.warn("Entry not found for deletion: {}", entryId);
            return;
        }

        // Check if at least one image was successfully generated (CACHED status)
        boolean hasAnySuccessfulImage = ChatLibrary.imagesOf(entry).stream()
                .anyMatch(img -> img.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED
                        && img.getLocalFilePath() != null
                        && !img.getLocalFilePath().isEmpty());

        // Only show confirmation dialog if at least one image was successfully generated
        boolean confirmed = true;
        if (hasAnySuccessfulImage) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            ButtonType ok = DialogButtons.ok();
            alert.getButtonTypes().setAll(ok, DialogButtons.cancel());
            alert.setTitle(I18nManager.getText("delete.entry.title"));
            alert.setHeaderText(I18nManager.getText("delete.entry.header"));
            alert.setContentText(I18nManager.getText("delete.entry.content"));

            Optional<ButtonType> result = alert.showAndWait();
            confirmed = result.isPresent() && result.get() == ok;
        }

        if (confirmed) {
            worker.execute(() -> {
                try {
                    chatLibrary.deleteEntry(entry);
                    logger.info("Notification: {}", I18nManager.getText("delete.entry.success"));
                } catch (Exception e) {
                    logger.error("Failed to delete entry: {}", entryId, e);
                    Platform.runLater(() -> {
                        status.failure(I18nManager.getText("delete.entry.error") + ": " + e.getMessage());
                    });
                }
            });
        }
    }

    /**
     * Handles the deletion of a single image after confirmation. The library books the cost and reports the change,
     * which renders the card anew or removes it with its last image (see {@link #showEntryChange}).
     *
     * @param entryId the entry ID containing the image
     * @param imageId the image ID to delete
     */
    private void handleDeleteImage(String entryId, String imageId) {
        ChatEntry entry = chatLibrary.findEntry(entryId);
        GeneratedImage image = entry == null ? null : ChatLibrary.imagesOf(entry).stream()
                .filter(img -> img.getId().equals(imageId)).findFirst().orElse(null);
        if (image == null) {
            logger.warn("Image not found for deletion - entry: {}, image: {}", entryId, imageId);
            return;
        }

        // Check if image was successfully downloaded (CACHED status means image file exists)
        boolean imageExists = image.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED
                && image.getLocalFilePath() != null
                && !image.getLocalFilePath().isEmpty();

        // Only show confirmation dialog if image actually exists
        boolean confirmed = true;
        if (imageExists) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            ButtonType ok = DialogButtons.ok();
            alert.getButtonTypes().setAll(ok, DialogButtons.cancel());
            alert.setTitle(I18nManager.getText("delete.image.title"));
            alert.setHeaderText(I18nManager.getText("delete.image.header"));
            alert.setContentText(I18nManager.getText("delete.image.content"));

            Optional<ButtonType> result = alert.showAndWait();
            confirmed = result.isPresent() && result.get() == ok;
        }

        if (confirmed) {
            worker.execute(() -> {
                try {
                    logger.info("Deleting image with ID: {}", imageId);
                    chatLibrary.deleteImage(entry, image);
                    logger.info("Notification: {}", I18nManager.getText("delete.image.success"));
                } catch (Exception e) {
                    logger.error("Failed to delete image: {}", imageId, e);
                    Platform.runLater(() -> {
                        status.failure(I18nManager.getText("delete.image.error") + ": " + e.getMessage());
                    });
                }
            });
        }
    }

    /**
     * Removes an entry card from the UI.
     *
     * @param entryId the entry ID to remove
     */
    private void removeEntryCardFromUI(String entryId) {
        if (chatEntriesListView == null || chatEntriesListView.getItems() == null) {
            return;
        }

        // Find and remove the entry from ListView
        chatEntriesListView.getItems().removeIf(entry -> entry.getId().equals(entryId));
    }

    /**
     * Optimizes ListView for better scrolling performance with GPU acceleration.
     * NOTE: ListView with virtualization is already optimized - this is minimal setup.
     */
    private void optimizeScrollPane() {
        if (chatEntriesListView == null) {
            return;
        }

        // Enable GPU caching for the ListView
        chatEntriesListView.setCache(true);
        chatEntriesListView.setCacheHint(javafx.scene.CacheHint.SPEED);

        // Add performance CSS class
        chatEntriesListView.getStyleClass().add("chat-entries-list");
    }

    /*  the file of an image, in the trash for a chat there; without the history where it was generated  */
    private String imagePath(GeneratedImage image, boolean isChatDeleted) {
        return chatHistory != null ? chatHistory.actualImagePath(image, isChatDeleted) : image.getLocalFilePath();
    }
}
