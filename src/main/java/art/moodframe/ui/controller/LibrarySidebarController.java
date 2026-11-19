package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryGroup;
import art.moodframe.model.FrameGroup;
import art.moodframe.model.MoodGroup;
import art.moodframe.service.chat.ChatHistoryService;
import art.moodframe.service.chat.ChatLibrary;
import art.moodframe.ui.support.BoxInset;
import art.moodframe.ui.cell.ChatListCell;
import art.moodframe.ui.cell.FrameGroupListCell;
import art.moodframe.ui.support.ListReorder;
import art.moodframe.ui.cell.MoodGroupListCell;
import art.moodframe.ui.support.CoalescedLoad;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * The left sidebar of the main window ({@code LibrarySidebar.fxml}): the chats, or the moods or the frames images were
 * generated with, as groups of entries from all chats. It holds the selection of the window: the chat that takes the
 * new prompts and the open group, whose entries the chat view shows in front of the chat.
 *
 * @since 2.1.3
 */
public class LibrarySidebarController {

    private static final Logger logger = LoggerFactory.getLogger(LibrarySidebarController.class);

    /** What the sidebar asks of the window. */
    public interface Listener {

        /**
         * A group of the list by mood or by frame is about to open: a mood or frame in use goes into the fields.
         *
         * @param group the group
         */
        void groupChosen(EntryGroup group);

        /** The chat or the open group changed: the view shows it anew. */
        void showSelection();

        /** The name or the cost of the shown chat, or the name of the open group changed. */
        void showHeader();
    }

    @FXML
    private HBox sidebarHeader;
    @FXML
    private Label sidebarTitleLabel;
    @FXML
    private Tooltip sidebarModeTooltip;
    @FXML
    private RadioMenuItem byChatMenuItem;
    @FXML
    private RadioMenuItem byMoodMenuItem;
    @FXML
    private RadioMenuItem byFrameMenuItem;
    @FXML
    private Button newChatButton;
    @FXML
    private Tooltip newChatTooltip;
    @FXML
    private Button trashButton;
    @FXML
    private Tooltip trashTooltip;
    @FXML
    private ListView<Chat> chatListView;
    @FXML
    private ListView<MoodGroup> moodGroupListView;
    @FXML
    private ListView<FrameGroup> frameGroupListView;

    private ChatLibrary chatLibrary;
    private ChatHistoryService chatHistory;
    private ApplicationConfig config;
    private StatusSink status;
    private Listener listener;

    /*  the chats of the library as the sidebar shows them, following its reports on the JavaFX thread  */
    private final ObservableList<Chat> chatList = FXCollections.observableArrayList();
    private FilteredList<Chat> filteredChatList;

    /*  the chat that takes the new prompts  */
    private Chat currentChat;

    /*  A guard while the chats get a new order: the list keeps its selection without opening the chat again  */
    private boolean reorderingChats;

    /*  The group whose entries are shown (null while a chat is shown, which keeps taking the new prompts as the last
        active one) and a guard while the list is filled again  */
    private EntryGroup shownGroup;
    private boolean updatingGroups;

    /*  The groups are queried on the worker, one query for a burst of library changes  */
    private CoalescedLoad groupsLoad;

    @FXML
    private void initialize() {
        /*  the heading starts on the inset of the boxes, the line of the names in the list  */
        sidebarHeader.setPadding(new Insets(sidebarHeader.getPadding().getTop(),
                sidebarHeader.getPadding().getRight(), sidebarHeader.getPadding().getBottom(), BoxInset.LEFT));
    }

    /*  opens the central trash, handed by the main window (null: no trash)  */
    private art.moodframe.ui.dialog.TrashWindow.Opener trash;

    /**
     * Sets what opens the central trash of the button of the sidebar.
     *
     * @param trash opens the trash windows
     */
    public void useTrash(art.moodframe.ui.dialog.TrashWindow.Opener trash) {
        this.trash = trash;
    }

    /**
     * Connects the sidebar to the library, whose chats were loaded at the start. Later changes (what leaves the trash,
     * what other callers change) update the list of the sidebar instead of reloading it, so running requests keep
     * their chat objects.
     *
     * @param chatLibrary the chats
     * @param chatHistory queries the groups (may be null: no groups)
     * @param config      remembers the view of the list
     * @param worker      runs the queries of the groups
     * @param status      shows what the sidebar did
     * @param listener    shows the selection
     */
    public void init(ChatLibrary chatLibrary, ChatHistoryService chatHistory, ApplicationConfig config,
                     Executor worker, StatusSink status, Listener listener) {
        this.chatLibrary = chatLibrary;
        this.chatHistory = chatHistory;
        this.config = config;
        this.status = status;
        this.listener = listener;
        groupsLoad = new CoalescedLoad("the groups of the entries", worker, Platform::runLater,
                this::prepareGroupsLoad);

        syncChats();

        /*  trashed chats are only listed in the central trash  */
        filteredChatList = new FilteredList<>(chatList, LibrarySidebarController::shouldShowChat);
        chatListView.setItems(filteredChatList);
        ListReorder<Chat> reorder = new ListReorder<>(chatListView, this::moveChat);
        chatListView.setCellFactory(listView -> {
            ChatListCell cell = new ChatListCell();
            cell.setOnDeleteRequested(this::onChatDeleteRequested);
            cell.setOnChatRenamed(this::onChatRenamed);
            cell.setTrashColumn(trashButton);
            reorder.attach(cell, cell.getGrip());
            return cell;
        });
        chatListView.getSelectionModel().selectedItemProperty().addListener((obs, oldChat, newChat) -> {
            if (!reorderingChats)
                onChatSelected(newChat);
        });
        Platform.runLater(() -> {
            if (currentChat != null)
                listener.showSelection();
        });
    }

    /*  Brings the list of the sidebar in line with the chats of the library: removes, inserts and sorts only what
        differs, so the list keeps its selection  */
    private void syncChats() {
        List<Chat> library = chatLibrary.chats();
        Map<Chat, Integer> order = new IdentityHashMap<>();
        for (int i = 0; i < library.size(); i++)
            order.put(library.get(i), i);
        chatList.removeIf((chat) -> !order.containsKey(chat));
        Set<Chat> listed = Collections.newSetFromMap(new IdentityHashMap<>());
        listed.addAll(chatList);
        for (int i = 0; i < library.size(); i++)
            if (!listed.contains(library.get(i)))
                chatList.add(Math.min(i, chatList.size()), library.get(i));
        for (int i = 0; i < chatList.size(); i++)
            if (chatList.get(i) != library.get(i)) {
                FXCollections.sort(chatList, Comparator.comparingInt(order::get));
                break;
            }
    }

    /** The chat that takes the new prompts, null if none is chosen. */
    public Chat currentChat() {
        return currentChat;
    }

    /** The group whose entries are shown, null while the chat is shown. */
    public EntryGroup shownGroup() {
        return shownGroup;
    }

    /** Whether a chat is chosen that can take new prompts, one not in the trash. */
    public boolean hasActiveChat() {
        return currentChat != null && !currentChat.isDeleted();
    }

    /**
     * Enables "New chat", which needs a key of any provider.
     *
     * @param enabled whether it is enabled
     */
    public void setNewChatEnabled(boolean enabled) {
        newChatButton.setDisable(!enabled);
    }

    /*  trashed chats are only listed in the central trash  */
    private static boolean shouldShowChat(Chat chat) {
        return !chat.isDeleted();
    }

    /**
     * Opens a chat as if chosen in the list, for the tests of the window.
     *
     * @param chat the chat
     */
    void chooseChat(Chat chat) {
        onChatSelected(chat);
    }

    /** Opens a chosen chat, null if none is chosen. */
    private void onChatSelected(Chat chat) {
        if (currentChat == chat)
            return;
        currentChat = chat;
        listener.showSelection();
    }

    /**
     * Moves a chat to the trash without asking, it can be restored from there. The library moves the files to the
     * trash; a new chat without any entry is stored in the database only with its first message, so there is nothing
     * to move to the trash: it is just removed from the list, as are all chats without entries, so they do not
     * reappear later. The open chat then shows its images from the trash.
     *
     * @param chat the chat to delete
     */
    private void onChatDeleteRequested(Chat chat) {
        try {
            if (chatLibrary.moveToTrash(chat)) {
                syncChats();
                if (!chat.getEntries().isEmpty())
                    filteredChatList.setPredicate(LibrarySidebarController::shouldShowChat);
                if (currentChat == chat)
                    listener.showSelection();
                status.status(I18nManager.getText("status.chat.moved.trash"));
            } else {
                logger.error("Failed to soft delete chat: {}", chat.getName());
                status.error(I18nManager.getText("status.error.chat.deletion"));
            }
        } catch (Exception e) {
            logger.error("Failed to save chat deletion status to database: {}", chat.getName(), e);
            status.error(I18nManager.getText("status.error.chat.deletion"));
        }
    }

    /**
     * Moves a chat to a gap between the shown chats (0 above the first one) and stores the order of all chats. The
     * list sorts itself anew, keeping the selected chat without opening it again.
     *
     * @param chat the chat to move
     * @param gap  the gap it goes to
     */
    private void moveChat(Chat chat, int gap) {
        if (chatHistory == null)
            return;
        Chat before = gap < filteredChatList.size() ? filteredChatList.get(gap) : null;
        reorderingChats = true;
        try {
            chatLibrary.moveChat(chat, before);
        } catch (Exception e) {
            logger.error("Failed to store the order of the chats", e);
        }
        syncChats();
        reorderingChats = false;
    }

    /**
     * Stores the new name of a chat; the header follows if it is the chosen chat.
     *
     * @param chat the chat that was renamed
     */
    private void onChatRenamed(Chat chat) {
        try {
            chatLibrary.renameChat(chat, chat.getName());

            /*  by ID, the list may hold another object of the same chat  */
            if (currentChat != null && currentChat.getId().equals(chat.getId())) {
                currentChat = chat;
                listener.showHeader();
            }
            status.status(java.text.MessageFormat.format(I18nManager.getText("status.chat.renamed"), chat.getName()));
        } catch (Exception e) {
            logger.error("Failed to save chat rename to database: {}", chat.getName(), e);
            status.error(java.text.MessageFormat.format(I18nManager.getText("status.error.chat.rename"),
                    e.getMessage()));
        }
    }

    /**
     * Shows a change of a chat: a new one, one that left the trash or went there, a new name or order, a chat gone for
     * good. Only what is not shown yet changes, as the changes made here are shown already.
     *
     * @param chatId the ID of the chat
     */
    public void showChatChange(String chatId) {
        syncChats();
        Chat chat = chatLibrary.find(chatId);

        /*  The open chat is gone for good: the view shows the welcome or the open group, which held no entry of a
            chat in the trash  */
        if (chat == null && currentChat != null && currentChat.getId().equals(chatId)) {
            chatListView.getSelectionModel().clearSelection();
            currentChat = null;
            listener.showHeader();
        }

        /*  A chat that left the trash or went there; the open one shows its images from their place now  */
        if (chat != null && filteredChatList.contains(chat) != shouldShowChat(chat)) {
            filteredChatList.setPredicate(LibrarySidebarController::shouldShowChat);
            if (currentChat == chat || shownGroup != null)
                listener.showSelection();
        }
        else if (chat != null && chat == currentChat)
            listener.showHeader();
        chatListView.refresh();
        refreshGroups();
    }

    /**
     * Sets up the menu of the sidebar that switches between the chats and the moods or frames images were generated
     * with; the choice is remembered. Opening a mood or frame shows its entries from all chats on the right.
     */
    public void setupSidebarMode() {
        ToggleGroup modes = new ToggleGroup();
        byChatMenuItem.setToggleGroup(modes);
        byMoodMenuItem.setToggleGroup(modes);
        byFrameMenuItem.setToggleGroup(modes);
        byChatMenuItem.setUserData("chat");
        byMoodMenuItem.setUserData("mood");
        byFrameMenuItem.setUserData("frame");

        /*  One of the entries stays selected  */
        modes.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null)
                oldToggle.setSelected(true);
            else
                showSidebarMode(newToggle);
        });

        moodGroupListView.setCellFactory((listView) -> new MoodGroupListCell());
        moodGroupListView.getSelectionModel().selectedItemProperty().addListener((obs, oldGroup, newGroup) -> {
            if (!updatingGroups && newGroup != null)
                openGroup(newGroup);
        });
        frameGroupListView.setCellFactory((listView) -> new FrameGroupListCell());
        frameGroupListView.getSelectionModel().selectedItemProperty().addListener((obs, oldGroup, newGroup) -> {
            if (!updatingGroups && newGroup != null)
                openGroup(newGroup);
        });

        String mode = config != null ? config.ui().getSidebarMode() : "chat";
        modes.getToggles().stream().filter((toggle) -> mode.equals(toggle.getUserData())).findFirst()
                .ifPresent((toggle) -> toggle.setSelected(true));
    }

    /** Lists the chats, the moods or the frames in the sidebar and remembers the choice; an open group closes. */
    private void showSidebarMode(Toggle mode) {
        ListView<?> shownList = mode == byMoodMenuItem ? moodGroupListView
                : mode == byFrameMenuItem ? frameGroupListView : chatListView;
        if (shownList.isVisible())
            return;
        for (ListView<?> list : List.of(chatListView, moodGroupListView, frameGroupListView)) {
            list.setVisible(list == shownList);
            list.setManaged(list == shownList);
        }
        updateSidebarTitle();
        if (config != null)
            config.ui().setSidebarMode((String) mode.getUserData());

        /*  The chat shows again, the other list holds the open group no more  */
        if (shownGroup != null) {
            shownGroup = null;
            moodGroupListView.getSelectionModel().clearSelection();
            frameGroupListView.getSelectionModel().clearSelection();
            listener.showSelection();
        }
        refreshGroups();
    }

    /** Names the current view of the sidebar in its header. */
    private void updateSidebarTitle() {
        sidebarTitleLabel.setText(I18nManager.getText(moodGroupListView.isVisible() ? "sidebar.moods"
                : frameGroupListView.isVisible() ? "sidebar.frames" : "sidebar.chats"));
    }

    /**
     * Opens a group of the list by mood or by frame: a mood in use goes into the fields that are not locked, a frame in
     * use is chosen, and the entries of the group from all chats are shown. The last active chat keeps taking the new
     * prompts.
     */
    private void openGroup(EntryGroup group) {
        listener.groupChosen(group);
        shownGroup = group;
        listener.showSelection();
    }

    /**
     * Lists the moods or the frames again while the sidebar shows them, keeping the open group selected without opening
     * it again; the open group takes over its new name, for example once it was renamed or deleted.
     */
    public void refreshGroups() {
        groupsLoad.request();
    }

    /** Prepares the query of the groups the sidebar shows, null while it shows the chats. */
    private Callable<Runnable> prepareGroupsLoad() {
        ChatHistoryService history = chatHistory;
        if (history == null)
            return null;
        if (moodGroupListView.isVisible())
            return loadGroups(moodGroupListView, history::findMoodGroups);
        if (frameGroupListView.isVisible())
            return loadGroups(frameGroupListView, history::findFrameGroups);
        return null;
    }

    /*  Queries the groups on the worker and shows them on the FX thread, unless the sidebar shows another list now  */
    private <T extends EntryGroup> Callable<Runnable> loadGroups(ListView<T> listView, Callable<List<T>> query) {
        return () -> {
            List<T> groups = query.call();
            return () -> {
                if (!listView.isVisible())
                    return;
                refreshGroups(listView, groups);
                if (shownGroup != null)
                    listener.showHeader();
            };
        };
    }

    /** Fills a list of groups, keeping the open one selected without opening it again. */
    private <T extends EntryGroup> void refreshGroups(ListView<T> listView, List<T> groups) {
        updatingGroups = true;
        listView.getItems().setAll(groups);
        groups.stream().filter((group) -> group.sameAs(shownGroup)).findFirst().ifPresent((group) -> {
            shownGroup = group;
            listView.getSelectionModel().select(group);
        });
        updatingGroups = false;
    }

    /**
     * Chooses the chat an entry of the open group was generated in, in the list of the chats.
     *
     * @param entry the entry
     * @return whether the chat is in the list
     */
    public boolean openChatOf(ChatEntry entry) {
        Chat chat = chatList.stream().filter((candidate) -> candidate.getId().equals(entry.getChatId())).findFirst()
                .orElse(null);
        if (chat == null)
            return false;
        byChatMenuItem.setSelected(true);
        chatListView.getSelectionModel().select(chat);
        chatListView.scrollTo(chat);
        return true;
    }

    /** Creates a new chat at the beginning of the list, stored with its first message, and opens it. */
    @FXML
    private void onNewChatClicked() {
        status.status(I18nManager.getText("status.creating.chat"));

        /*  The new chat shows in the list of chats  */
        byChatMenuItem.setSelected(true);
        Chat newChat = chatLibrary.newChat("New Chat " + (chatList.size() + 1));
        syncChats();
        chatListView.getSelectionModel().select(0);

        logger.debug("Created new chat: {}", newChat.getName());
        status.status(I18nManager.getText("status.chat.created"));
    }

    /** Opens the central trash. */
    @FXML
    private void onTrashClicked() {
        try {
            if (trash != null)
                trash.open(chatListView.getScene().getWindow(), art.moodframe.service.trash.TrashGroup.Kind.CHATS,
                        null);
        } catch (Exception e) {
            logger.error("Failed to open trash window", e);
        }
    }

    /** Shows the texts of the sidebar in the current language. */
    public void updateTexts() {
        newChatTooltip.setText(I18nManager.getText("chat.new.tooltip"));
        trashTooltip.setText(I18nManager.getText("chat.trash.tooltip"));
        sidebarModeTooltip.setText(I18nManager.getText("sidebar.mode.tooltip"));
        byChatMenuItem.setText(I18nManager.getText("sidebar.by.chat"));
        byMoodMenuItem.setText(I18nManager.getText("sidebar.by.mood"));
        byFrameMenuItem.setText(I18nManager.getText("sidebar.by.frame"));
        updateSidebarTitle();
    }
}
