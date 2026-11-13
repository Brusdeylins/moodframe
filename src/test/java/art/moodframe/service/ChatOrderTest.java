package art.moodframe.service;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.repository.ChatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The order of the chats in the list: moved by the user, new chats on top, kept over a restart. */
class ChatOrderTest {

    @TempDir
    Path tempDir;

    private Path databasePath;
    private DatabaseManager databaseManager;
    private ChatRepository repository;
    private ChatHistoryService history;
    private List<Chat> chats;

    /*  A fresh database per test with the chats A, B, C and D in this order  */
    @BeforeEach
    void createChats() throws Exception {
        databasePath = tempDir.resolve("db").resolve("test.db");
        databaseManager = new DatabaseManager(databasePath);
        databaseManager.initialize();
        repository = new ChatRepository(databaseManager);
        history = new ChatHistoryService(repository, null, null);
        chats = new ArrayList<>();
        for (String name : List.of("A", "B", "C", "D")) {
            Chat chat = new Chat.Builder().name(name).sortOrder(chats.size()).build();
            repository.saveChat(chat);
            chats.add(chat);
        }
    }

    @Test
    void movesAChatToTheTop() throws Exception {
        history.moveChat(chats, chat("C"), chat("A"));
        assertOrder("CABD");
    }

    @Test
    void movesAChatIntoTheMiddle() throws Exception {
        history.moveChat(chats, chat("A"), chat("D"));
        assertOrder("BCAD");
    }

    @Test
    void movesAChatToTheBottom() throws Exception {
        history.moveChat(chats, chat("B"), null);
        assertOrder("ACDB");
    }

    @Test
    void putsANewChatOnTop() throws Exception {
        history.moveChat(chats, chat("D"), chat("A"));
        Chat added = new Chat.Builder().name("E").sortOrder(ChatHistoryService.sortOrderOnTop(chats)).build();
        repository.saveChat(added);

        assertEquals(-1, added.getSortOrder(), "one less than the smallest number");
        assertEquals("EDABC", names(repository.findAllActiveChats()));
        assertEquals(0, ChatHistoryService.sortOrderOnTop(List.of()), "the first chat of all");
    }

    @Test
    void loadsTheChatsInTheStoredOrderWithTheTrashedOnesInPlace() throws Exception {
        repository.saveChatEntry(new ChatEntry.Builder().chatId(chat("B").getId()).userPrompt("a harbor").build());
        repository.softDeleteChat(chat("B").getId());
        history.moveChat(chats, chat("D"), chat("B"));

        assertEquals("ADBC", names(history.load().chats()), "the chat in the trash keeps its place");
        assertEquals("ADC", names(repository.findAllActiveChats()));
    }

    @Test
    void keepsTheOrderOfAnExistingLibraryOnItsFirstStart() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        int age = 0;
        for (String name : List.of("C", "A", "D", "B")) {
            Chat chat = chat(name);
            chat.setUpdatedAt(now.minusMinutes(age++));
            chat.setSortOrder(0);
            repository.saveChat(chat);
        }

        /*  The library as 2.0.0 left it: all chats on 0, the migration not applied yet  */
        try (Connection connection = databaseManager.getConnection();
                Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM schema_migrations WHERE version = '025'");
        }
        new DatabaseManager(databasePath).initialize();

        List<Chat> loaded = repository.findAllActiveChats();
        assertEquals("CADB", names(loaded), "the latest changed chat first, as before");
        assertEquals(List.of(0, 1, 2, 3), loaded.stream().map(Chat::getSortOrder).toList());
    }

    private Chat chat(String name) {
        return chats.stream().filter((chat) -> chat.getName().equals(name)).findFirst().orElseThrow();
    }

    private void assertOrder(String expected) throws Exception {
        List<Chat> loaded = repository.findAllActiveChats();
        assertEquals(expected, names(loaded));
        assertEquals(List.of(0, 1, 2, 3), loaded.stream().map(Chat::getSortOrder).toList(), "numbered from 0 on");
    }

    private static String names(List<Chat> chats) {
        return String.join("", chats.stream().map(Chat::getName).toList());
    }
}
