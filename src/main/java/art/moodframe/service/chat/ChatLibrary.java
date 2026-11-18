package art.moodframe.service.chat;

import art.moodframe.model.Chat;
import art.moodframe.model.ChatCost;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.trash.TrashCenter;
import art.moodframe.service.trash.TrashGroup;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The chats with their entries, shared by the main window and later callers. It owns the list of the chats: they are
 * loaded once at the start, and every change goes through here, also what leaves the trash, and is reported as
 * {@link LibraryEvents.Kind#CHAT} or {@link LibraryEvents.Kind#ENTRY}.
 *
 * <p>Chats, entries and images are JavaFX models, they change on the JavaFX thread only. The methods may be called
 * from any thread: they access the database on the calling thread and hand the change of the model to the JavaFX
 * thread, waiting for it.</p>
 *
 * @since 2.1.0
 */
public class ChatLibrary implements TrashCenter.Listener {

    private static final Logger logger = LoggerFactory.getLogger(ChatLibrary.class);

    private final ChatHistoryService history;
    private final LibraryEvents events;
    private final ObservableList<Chat> chats = FXCollections.observableArrayList();

    /**
     * Creates the library.
     *
     * @param history stores the chats, null to keep them in memory only
     * @param events  where the changes are reported
     */
    public ChatLibrary(ChatHistoryService history, LibraryEvents events) {
        this.history = history;
        this.events = events;
    }

    /*  Runs a change of the model on the JavaFX thread and waits for it, at once on the JavaFX thread itself  */
    private static <T> T onFxThread(Supplier<T> change) {
        if (Platform.isFxApplicationThread())
            return change.get();
        FutureTask<T> task = new FutureTask<>(change::get);
        Platform.runLater(task);
        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the JavaFX thread", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to change the chats", e.getCause());
        }
    }

    private static void runOnFxThread(Runnable change) {
        onFxThread(() -> {
            change.run();
            return null;
        });
    }

    /**
     * The chats in their order, also the deleted ones that still hold entries (hidden until restored). Read it on the
     * JavaFX thread only and change it through the library only.
     *
     * @return the chats
     */
    public ObservableList<Chat> getChats() {
        return chats;
    }

    /**
     * Reads the chats on the JavaFX thread and waits for the result, so a caller on another thread sees them in a
     * consistent state. The reader must not change them.
     *
     * @param reader reads the chats and returns what it found
     * @param <T>    the type of the result
     * @return the result of the reader
     */
    public <T> T read(Function<List<Chat>, T> reader) {
        return onFxThread(() -> reader.apply(chats));
    }

    /**
     * Loads the chats once at the start; what a restart left behind is repaired there (see
     * {@link ChatHistoryService#load()}). Without any stored chat, a welcome chat is created.
     */
    public void load() {
        try {
            if (history == null)
                logger.warn("ChatRepository not available - creating empty chat list");
            ChatHistoryService.History loaded = history != null ? history.load() : null;
            runOnFxThread(() -> chats.setAll(loaded != null ? loaded.chats() : List.of()));
            if (loaded == null || loaded.nothingStored())
                addWelcomeChat();
        } catch (Exception e) {
            logger.error("Failed to load chat data from database", e);
            runOnFxThread(chats::clear);
            addWelcomeChat();
        }
    }

    /*  the chat of first-time users, kept in memory if it cannot be stored  */
    private void addWelcomeChat() {
        Chat welcomeChat = new Chat.Builder().name("Welcome to MoodFrame").createdAt(LocalDateTime.now()).build();
        if (history != null) {
            try {
                history.saveChat(welcomeChat);
            } catch (Exception e) {
                logger.warn("Failed to save welcome chat to database", e);
            }
        }
        runOnFxThread(() -> chats.add(welcomeChat));
    }

    /**
     * Finds a chat of the list.
     *
     * @param chatId the ID of the chat
     * @return the chat, null if it is not in the list
     */
    public Chat find(String chatId) {
        return onFxThread(() -> chats.stream().filter((Chat chat) -> chat.getId().equals(chatId)).findFirst()
                .orElse(null));
    }

    /**
     * Finds an entry of a chat of the list.
     *
     * @param entryId the ID of the entry
     * @return the entry, null if no chat of the list holds it
     */
    public ChatEntry findEntry(String entryId) {
        return onFxThread(() -> chats.stream().flatMap((Chat chat) -> chat.getEntries().stream())
                .filter((ChatEntry entry) -> entry.getId().equals(entryId)).findFirst().orElse(null));
    }

    /**
     * Creates a chat on top of the list and stores it at once.
     *
     * @param name the name of the chat
     * @return the chat
     * @throws SQLException if it cannot be stored; it does not show then
     */
    public Chat createChat(String name) throws SQLException {
        Chat chat = addOnTop(name);
        if (history != null) {
            try {
                history.saveChat(chat);
            } catch (SQLException e) {
                runOnFxThread(() -> chats.remove(chat));
                throw e;
            }
        }
        events.changed(LibraryEvents.Kind.CHAT, chat.getId());
        return chat;
    }

    /**
     * Creates a chat on top of the list without storing it, as the main window does: it is stored with its first
     * entry (see {@link #addEntry}).
     *
     * @param name the name of the chat
     * @return the chat
     */
    public Chat newChat(String name) {
        Chat chat = addOnTop(name);
        events.changed(LibraryEvents.Kind.CHAT, chat.getId());
        return chat;
    }

    private Chat addOnTop(String name) {
        Chat chat = new Chat.Builder().name(name).build();
        runOnFxThread(() -> {
            chat.setSortOrder(ChatHistoryService.sortOrderOnTop(chats));
            chats.add(0, chat);
        });
        return chat;
    }

    /**
     * Renames a chat and stores it.
     *
     * @param chat the chat
     * @param name the new name
     * @throws SQLException if it cannot be stored; it keeps the new name anyway
     */
    public void renameChat(Chat chat, String name) throws SQLException {
        runOnFxThread(() -> {
            chat.setName(name);
            chat.setUpdatedAt(LocalDateTime.now());
        });
        if (history != null)
            history.saveChat(chat);
        events.changed(LibraryEvents.Kind.CHAT, chat.getId());
    }

    /**
     * Moves a chat and its files to the trash. A chat without entries leaves the list, so it does not come back later;
     * a new chat that was never stored is just removed.
     *
     * @param chat the chat
     * @return true if it is gone from the shown chats
     * @throws SQLException if it cannot be marked as deleted
     */
    public boolean moveToTrash(Chat chat) throws SQLException {
        boolean stored = history != null && history.softDeleteChat(chat.getId());
        boolean gone = onFxThread(() -> {
            if (!stored && !chat.getEntries().isEmpty())
                return false;
            chat.markAsDeleted();
            if (chat.getEntries().isEmpty())
                chats.remove(chat);
            return true;
        });
        if (gone)
            events.changed(LibraryEvents.Kind.CHAT, chat.getId());
        return gone;
    }

    /**
     * Moves a chat in front of another one, numbers all chats anew and stores their order; the list sorts itself anew.
     *
     * @param chat   the chat to move
     * @param before the chat it goes in front of, null for the end of the list
     * @throws SQLException if the order cannot be stored; the chats keep their new order anyway
     */
    public void moveChat(Chat chat, Chat before) throws SQLException {
        if (chat == before)
            return;
        List<Chat> order = onFxThread(() -> {
            List<Chat> moved = ChatHistoryService.order(chats, chat, before);
            FXCollections.sort(chats, Comparator.comparingInt(Chat::getSortOrder));
            return moved;
        });
        events.changed(LibraryEvents.Kind.CHAT, chat.getId());
        if (history != null)
            history.saveChatOrder(order);
    }

    /**
     * Adds an entry to a chat and stores both; if storing fails, the entry is kept in memory only.
     *
     * @param chat  the chat
     * @param entry the new entry
     */
    public void addEntry(Chat chat, ChatEntry entry) {
        if (history != null) {
            try {
                history.saveChat(chat);
                history.saveChatEntry(entry);
                logger.debug("Saved Chat and ChatEntry: {}", entry.getId());
            } catch (Exception e) {
                logger.error("Failed to save ChatEntry to database", e);
            }
        }
        runOnFxThread(() -> {
            chat.getEntries().add(entry);
            chat.setUpdatedAt(LocalDateTime.now());
        });
        if (history != null) {
            try {
                history.saveChat(chat);
            } catch (Exception e) {
                logger.warn("Failed to update chat timestamp", e);
            }
        }
        events.changed(LibraryEvents.Kind.ENTRY, entry.getId());
    }

    /**
     * Moves an entry with its images to the trash; it leaves its chat, whose deleted cost grows by its whole cost
     * (see {@link ChatCost}).
     *
     * @param entry the entry of a chat of the list
     * @throws SQLException if it cannot be marked as deleted
     */
    public void deleteEntry(ChatEntry entry) throws SQLException {
        if (history != null)
            history.deleteEntry(entry.getId(), history.findGeneratedImagesByEntry(entry.getId()));
        Optional<Double> deletedCost = deletedCost(entry.getChatId());
        runOnFxThread(() -> owner(entry).ifPresent((Chat chat) -> leave(chat, entry, deletedCost)));
        events.changed(LibraryEvents.Kind.ENTRY, entry.getId());
    }

    /**
     * Moves an image to the trash; with its last image the entry leaves its chat as well.
     *
     * @param entry the entry of the image
     * @param image the image
     * @return true if the entry went to the trash with it
     * @throws SQLException if it cannot be marked as deleted
     */
    public boolean deleteImage(ChatEntry entry, GeneratedImage image) throws SQLException {
        ChatHistoryService.ImageDeletion deletion = history != null ? history.deleteImage(entry.getId(), image)
                : inMemory(entry, image);
        Optional<Double> deletedCost = deletedCost(entry.getChatId());
        runOnFxThread(() -> {
            entry.getImages().remove(image);
            entry.setDeletedCost(deletion.entryDeletedCost());
            if (deletion.entryDeleted())
                owner(entry).ifPresent((Chat chat) -> leave(chat, entry, deletedCost));
        });
        events.changed(LibraryEvents.Kind.ENTRY, entry.getId());
        return deletion.entryDeleted();
    }

    /*  A deleted entry leaves its chat, which takes the deleted cost as stored, without database it books the
        cost of the entry itself; on the JavaFX thread  */
    private static void leave(Chat chat, ChatEntry entry, Optional<Double> deletedCost) {
        boolean removed = chat.getEntries().remove(entry);
        if (deletedCost.isPresent())
            chat.setDeletedCost(deletedCost.get());
        else if (removed)
            chat.setDeletedCost(chat.getDeletedCost() + entry.getCost());
    }

    /*  the deletion of an image without database: as ChatRepository#softDeleteImage books it  */
    private static ChatHistoryService.ImageDeletion inMemory(ChatEntry entry, GeneratedImage image) {
        return onFxThread(() -> entry.getImages().size() == 1
                ? new ChatHistoryService.ImageDeletion(true, entry.getCost())
                : new ChatHistoryService.ImageDeletion(false, entry.getDeletedCost()
                        + ChatCost.imageShare(entry.getCost() - entry.getDeletedCost(), entry.getImages(), image)));
    }

    /*  the chat of an entry in the list, on the JavaFX thread  */
    private Optional<Chat> owner(ChatEntry entry) {
        return chats.stream().filter((Chat chat) -> chat.getId().equals(entry.getChatId())).findFirst();
    }

    /*  the deleted cost of a chat as stored, empty without database  */
    private Optional<Double> deletedCost(String chatId) throws SQLException {
        return history != null ? history.findChat(chatId).map(Chat::getDeletedCost) : Optional.empty();
    }

    /**
     * Shows a chat, an entry or an image again that came back from the trash, at its place.
     *
     * @param kind the group of the item
     * @param id   the ID of the item
     */
    @Override
    public void restored(TrashGroup.Kind kind, String id) {
        try {
            if (kind == TrashGroup.Kind.CHATS)
                restoreChat(id);
            else if (kind == TrashGroup.Kind.IMAGES)
                restoreImages(id);
        } catch (Exception e) {
            logger.error("Failed to show the restored {} {}", kind, id, e);
        }
    }

    /**
     * Removes chats that are gone for good from the list.
     *
     * @param kind the group of the items
     * @param ids  the IDs of the items
     */
    @Override
    public void deleted(TrashGroup.Kind kind, List<String> ids) {
        if (kind != TrashGroup.Kind.CHATS)
            return;
        runOnFxThread(() -> chats.removeIf((Chat chat) -> ids.contains(chat.getId())));
        ids.forEach((String id) -> events.changed(LibraryEvents.Kind.CHAT, id));
    }

    /*  A chat in the list is shown again; a chat without entries left the list and comes back from the database to its
        place in the order  */
    private void restoreChat(String chatId) throws SQLException {
        Chat stored = history != null ? history.findChat(chatId).orElse(null) : null;
        boolean shown = onFxThread(() -> {
            Chat chat = chats.stream().filter((Chat listed) -> listed.getId().equals(chatId)).findFirst().orElse(null);
            if (chat != null)
                chat.restore();
            else if (stored != null)
                chats.add((int) chats.stream().filter((Chat listed) -> listed.getSortOrder() <= stored.getSortOrder())
                        .count(), stored);
            return chat != null || stored != null;
        });
        if (shown)
            events.changed(LibraryEvents.Kind.CHAT, chatId);
    }

    /*  An entry in use keeps its object, as running requests may hold it, and gets its images back; a deleted entry is
        inserted again at its place  */
    private void restoreImages(String id) throws SQLException {
        ChatEntry restored = history != null ? history.findRestoredEntry(id).orElse(null) : null;
        if (restored == null)
            return;
        Optional<Double> deletedCost = history.findChat(restored.getChatId()).map(Chat::getDeletedCost);
        ChatEntry shown = onFxThread(() -> {
            Chat chat = chats.stream().filter((Chat listed) -> listed.getId().equals(restored.getChatId())).findFirst()
                    .orElse(null);
            if (chat == null)
                return null;
            Comparator<ChatEntry> order = Comparator.comparingInt(ChatEntry::getSortOrder)
                    .thenComparing(ChatEntry::getCreatedAt);
            ChatEntry entry = chat.getEntries().stream().filter((ChatEntry other) -> other.getId()
                    .equals(restored.getId())).findFirst().orElse(null);
            if (entry != null) {
                entry.getImages().setAll(restored.getImages());
                entry.setDeletedCost(restored.getDeletedCost());
            }
            else {
                entry = restored;
                chat.getEntries().add((int) chat.getEntries().stream()
                        .filter((ChatEntry other) -> order.compare(other, restored) <= 0).count(), entry);
            }
            deletedCost.ifPresent(chat::setDeletedCost);
            return entry;
        });
        if (shown != null)
            events.changed(LibraryEvents.Kind.ENTRY, shown.getId());
    }
}
