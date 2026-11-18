package art.moodframe.service.chat;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.repository.ChatRepository;
import art.moodframe.service.library.LibraryEvents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The library needs no JavaFX thread: callers on several threads at once create chats and entries, each on its own
 * thread, and every change arrives in the list, the database and the readers without a lost or doubled item.
 */
class ChatLibraryThreadsTest {

    private static final int CALLERS = 6;
    private static final int CHATS_EACH = 5;
    private static final int ENTRIES_EACH = 4;

    @TempDir
    Path tempDir;

    @Test
    void callersOnSeveralThreadsChangeTheChatsAtOnce() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("db").resolve("threads.db"));
        database.initialize();
        ChatRepository repository = new ChatRepository(database);
        ChatLibrary library = new ChatLibrary(new ChatHistoryService(repository, null, null), new LibraryEvents());
        library.load();
        int before = library.chats().size();

        ExecutorService callers = Executors.newFixedThreadPool(CALLERS);
        List<CompletableFuture<Void>> work = new ArrayList<>();
        for (int caller = 0; caller < CALLERS; caller++) {
            int number = caller;
            work.add(CompletableFuture.runAsync(() -> {
                try {
                    for (int c = 0; c < CHATS_EACH; c++) {
                        Chat chat = library.createChat("Caller " + number + " chat " + c);
                        for (int e = 0; e < ENTRIES_EACH; e++) {
                            ChatEntry entry = new ChatEntry.Builder().chatId(chat.getId()).userPrompt("prompt " + e)
                                    .build();
                            library.addEntry(chat, entry);
                            assertSame(entry, library.findEntry(entry.getId()));
                        }
                        library.read((List<Chat> all) -> all.stream().mapToInt((Chat each) -> each.getEntries()
                                .size()).sum());
                    }
                }
                catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }, callers));
        }
        CompletableFuture.allOf(work.toArray(CompletableFuture[]::new)).get(60, TimeUnit.SECONDS);
        callers.shutdown();

        List<Chat> chats = library.chats();
        assertEquals(before + CALLERS * CHATS_EACH, chats.size());
        assertEquals(chats.size(), chats.stream().map(Chat::getSortOrder).distinct().count(),
                "every chat got a place of its own on top");
        assertEquals(CALLERS * CHATS_EACH * ENTRIES_EACH, (int) library.read((List<Chat> all) -> all.stream()
                .mapToInt((Chat chat) -> chat.getEntries().size()).sum()));
        int stored = 0;
        for (Chat chat : repository.findAllActiveChats())
            stored += repository.findChatEntriesByChatId(chat.getId()).size();
        assertEquals(CALLERS * CHATS_EACH * ENTRIES_EACH, stored);
        assertNotSame(library.chats(), library.chats(), "a caller gets a copy of the list");
    }
}
