package art.moodframe.service.chat;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.repository.ChatFiles;
import art.moodframe.repository.ChatGroupQueries;
import art.moodframe.repository.ChatRepository;
import art.moodframe.repository.ChatTrashRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The files of a deleted entry or image follow the rows the trash really changed, once, with the chat they belong
 * to; an image or entry another caller (an agent) deleted before moves no file again.
 */
class ChatHistoryDeletionTest {

    @TempDir
    static Path tempDir;

    private static DatabaseManager database;
    private static ChatRepository repository;

    /*  every move to the trash: the chat and the ids of the images  */
    private final List<String> moved = new ArrayList<>();
    private ChatTrashRepository trash;
    private ChatHistoryService history;

    @BeforeAll
    static void createDatabase() throws Exception {
        database = new DatabaseManager(tempDir.resolve("db").resolve("test.db"));
        database.initialize();
        repository = new ChatRepository(database);
    }

    @BeforeEach
    void recordTheFiles() {
        trash = new ChatTrashRepository(database, new ChatFiles() {
            @Override
            public void moveToTrash(String chatId, List<GeneratedImage> images) {
                moved.add(chatId + ":" + String.join(",", images.stream().map(GeneratedImage::getId).toList()));
            }

            @Override
            public void restoreFromTrash(String chatId, List<GeneratedImage> images) {
            }

            @Override
            public boolean deletePermanently(String chatId, List<GeneratedImage> images) {
                return true;
            }

            @Override
            public void removeDropped(List<GeneratedImage> images) {
            }
        });
        history = new ChatHistoryService(repository, trash, new ChatGroupQueries(database), null);
    }

    @Test
    void anImageMovesOnceWithItsChatAndItsEntryGoesWithTheLastOne() throws Exception {
        Chat chat = chat("Bilder");
        ChatEntry entry = entry(chat, 0.08, 2);
        List<GeneratedImage> images = repository.findGeneratedImagesByEntry(entry.getId());

        ChatHistoryService.ImageDeletion first = history.deleteImage(entry.getId(), images.get(0));
        assertFalse(first.entryDeleted());
        assertEquals(0.04, first.entryDeletedCost(), 1e-9);

        /*  the agent deleted it already: no second move, no second booking  */
        ChatHistoryService.ImageDeletion again = history.deleteImage(entry.getId(), images.get(0));
        assertFalse(again.entryDeleted());
        assertEquals(0.04, again.entryDeletedCost(), 1e-9);

        ChatHistoryService.ImageDeletion last = history.deleteImage(entry.getId(), images.get(1));
        assertTrue(last.entryDeleted());
        assertEquals(0.08, last.entryDeletedCost(), 1e-9);
        assertEquals(List.of(chat.getId() + ":" + images.get(0).getId(), chat.getId() + ":" + images.get(1).getId()),
                moved);
        assertEquals(0.08, repository.findChatById(chat.getId()).orElseThrow().getDeletedCost(), 1e-9,
                "the chat books the entry once");
    }

    @Test
    void anEntryMovesTheImagesItDeletedOnceAndNoneTwice() throws Exception {
        Chat chat = chat("Eintrag");
        ChatEntry entry = entry(chat, 0.12, 3);
        List<GeneratedImage> images = repository.findGeneratedImagesByEntry(entry.getId());
        history.deleteImage(entry.getId(), images.get(0));
        moved.clear();

        assertTrue(history.deleteEntry(entry.getId()));
        assertFalse(history.deleteEntry(entry.getId()), "deleted before, e.g. by an agent");

        assertEquals(List.of(chat.getId() + ":" + images.get(1).getId() + "," + images.get(2).getId()), moved);
    }

    @Test
    void aChatMovesOnlyTheFilesOfItsImagesInUse() throws Exception {
        Chat chat = chat("Ganz");
        ChatEntry entry = entry(chat, 0.08, 2);
        ChatEntry deleted = entry(chat, 0.04, 1);
        List<GeneratedImage> images = repository.findGeneratedImagesByEntry(entry.getId());
        history.deleteImage(entry.getId(), images.get(0));
        history.deleteEntry(deleted.getId());
        moved.clear();

        assertTrue(history.softDeleteChat(chat.getId()));

        assertEquals(List.of(chat.getId() + ":" + images.get(1).getId()), moved,
                "the images deleted on their own lie in the trash already");
    }

    @Test
    void anImageThatIsNotStoredMovesNoFile() throws Exception {
        Chat chat = chat("Ungespeichert");
        ChatEntry entry = entry(chat, 0.04, 1);
        GeneratedImage unsaved = new GeneratedImage.Builder().chatEntryId(entry.getId()).build();

        ChatHistoryService.ImageDeletion deletion = history.deleteImage(entry.getId(), unsaved);

        assertFalse(deletion.entryDeleted());
        assertEquals(List.of(), moved);
        assertEquals(1, repository.findGeneratedImagesByEntry(entry.getId()).size());
    }

    private Chat chat(String name) throws Exception {
        Chat chat = new Chat.Builder().name(name).build();
        repository.saveChat(chat);
        return chat;
    }

    private ChatEntry entry(Chat chat, double cost, int images) throws Exception {
        ChatEntry entry = new ChatEntry.Builder().chatId(chat.getId()).userPrompt("prompt").cost(cost).build();
        repository.saveChatEntry(entry);
        for (int i = 0; i < images; i++)
            repository.saveGeneratedImage(new GeneratedImage.Builder().chatEntryId(entry.getId()).model("flux-pro")
                    .localFilePath(tempDir.resolve(entry.getId() + "-" + i + ".png").toString()).sortOrder(i)
                    .build());
        return entry;
    }
}
