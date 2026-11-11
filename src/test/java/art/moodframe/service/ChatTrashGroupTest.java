package art.moodframe.service;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.repository.ChatRepository;
import art.moodframe.util.I18nManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against a fresh SQLite database, so the group works on the soft delete of the real repository. */
class ChatTrashGroupTest {

    @TempDir
    static Path tempDir;

    private static ChatRepository repository;

    private final ChatTrashGroup group = new ChatTrashGroup(repository);

    @BeforeAll
    static void createDatabase() throws Exception {
        DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve("db").resolve("test.db"));
        databaseManager.initialize();
        repository = new ChatRepository(databaseManager);
    }

    @Test
    void listsOnlyDeletedChatsWithTheNumberOfTheirImages() throws Exception {
        Chat active = chat("Aktiv");
        entry(active, 0.10, 1);
        Chat deleted = chat("Regen");
        entry(deleted, 0.08, 2);
        entry(deleted, 0.04, 1);
        repository.softDeleteChat(deleted.getId());

        assertTrue(trashed(active).isEmpty());
        TrashEntry entry = trashed(deleted).orElseThrow();
        assertEquals("Regen", entry.name());
        assertNotNull(entry.deletedAt());
        assertEquals(I18nManager.getText("trash.context.chat.plural", 3, String.format("%.2f", 0.12)), entry.context());
    }

    @Test
    void restoresAChatAndItsCostDisplayStaysAsItWas() throws Exception {
        Chat chat = chat("Kosten");
        ChatEntry first = entry(chat, 0.50, 2);
        ChatEntry second = entry(chat, 0.20, 1);
        repository.softDeleteImage(repository.findGeneratedImagesByEntry(first.getId()).get(0).getId());
        repository.softDeleteChatEntry(second.getId());
        double headerCost = headerCost(chat);
        assertEquals(0.54, headerCost, 0.0001);

        repository.softDeleteChat(chat.getId());
        assertEquals(I18nManager.getText("trash.context.chat.singular", 1, String.format("%.2f", headerCost)),
                trashed(chat).orElseThrow().context());

        assertTrue(group.restore(chat.getId()));

        assertTrue(trashed(chat).isEmpty());
        assertFalse(repository.findChatById(chat.getId()).orElseThrow().isDeleted());
        assertEquals(headerCost, headerCost(chat), 0.0001);
        assertEquals(1, repository.findGeneratedImagesByEntry(first.getId()).size());
    }

    @Test
    void deletesAChatForGoodWithItsEntries() throws Exception {
        Chat chat = chat("Weg");
        entry(chat, 0.10, 1);
        repository.softDeleteChat(chat.getId());

        assertTrue(group.deletePermanently(chat.getId()));

        assertTrue(trashed(chat).isEmpty());
        assertTrue(repository.findChatById(chat.getId()).isEmpty());
        assertTrue(repository.findChatEntriesByChatId(chat.getId()).isEmpty());
        assertFalse(group.deletePermanently(chat.getId()));
    }

    @Test
    void doesNotRestoreAChatThatIsNotInTheTrash() throws Exception {
        Chat active = chat("Aktiv");

        assertFalse(group.restore(active.getId()));
        assertFalse(group.restore("unknown"));
    }

    private Chat chat(String name) throws Exception {
        Chat chat = new Chat.Builder().name(name).build();
        repository.saveChat(chat);
        return chat;
    }

    /** Stores an entry with images of a model that costs 0.04 per image when deleted. */
    private ChatEntry entry(Chat chat, double cost, int images) throws Exception {
        ChatEntry entry = new ChatEntry.Builder().chatId(chat.getId()).userPrompt("prompt").cost(cost).build();
        repository.saveChatEntry(entry);
        for (int i = 0; i < images; i++) {
            repository.saveGeneratedImage(new GeneratedImage.Builder().chatEntryId(entry.getId())
                    .model("flux-pro").build());
        }
        return entry;
    }

    private Optional<TrashEntry> trashed(Chat chat) {
        List<TrashEntry> entries = group.list();
        return entries.stream().filter((entry) -> entry.id().equals(chat.getId())).findFirst();
    }

    /** The total the header of the main window shows for a chat: its entries plus the cost of its deleted entries. */
    private double headerCost(Chat chat) throws Exception {
        return repository.findChatEntriesByChatId(chat.getId()).stream().mapToDouble(ChatEntry::getCost).sum()
                + repository.findChatById(chat.getId()).orElseThrow().getDeletedCost();
    }
}
