package art.moodframe.repository;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryMood;
import art.moodframe.model.Mood;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Runs against a fresh SQLite database, so migration V020 is applied together with all earlier ones. */
class ChatRepositoryTest {

    @TempDir
    static Path tempDir;

    private static DatabaseManager databaseManager;
    private static ChatRepository repository;

    @BeforeAll
    static void createDatabase() throws Exception {
        databaseManager = new DatabaseManager(tempDir.resolve("db").resolve("test.db"));
        databaseManager.initialize();
        repository = new ChatRepository(databaseManager);
    }

    @Test
    void storesTheMoodAndTheCopiesOfTheBlocksOfAnEntry() throws Exception {
        Map<Mood.Field, EntryMood.Block> blocks = new EnumMap<>(Mood.Field.class);
        blocks.put(Mood.Field.STYLE, new EntryMood.Block("std:style:analog-film", "Analog Film",
                "analog film photograph, visible grain", "Photo", true, List.of()));
        blocks.put(Mood.Field.ATMOSPHERE, new EntryMood.Block("atmosphere-1", "Harbor Fog",
                "quiet, cold, a sense of distance", null, false, List.of()));
        blocks.put(Mood.Field.COLOR, new EntryMood.Block("palette-1", "Harbor", null, null, false,
                List.of("#1A1A1A", "#C0C0C0")));
        EntryMood mood = new EntryMood("mood-1", "Harbor Dawn", blocks);
        ChatEntry entry = save(new ChatEntry.Builder().chatId(chat().getId()).userPrompt("a harbor at dawn")
                .style("Analog Film").mood(mood).build());

        ChatEntry stored = repository.findChatEntryById(entry.getId()).orElseThrow();
        assertEquals(mood, stored.getMood());
        assertEquals("Analog Film", stored.getStyle());
        assertEquals("mood-1", moodIdColumn(entry.getId()));

        entry.setStatus(ChatEntry.Status.COMPLETED);
        repository.saveChatEntry(entry);
        assertEquals(mood, repository.findChatEntryById(entry.getId()).orElseThrow().getMood(),
                "an update keeps the copy");
        assertEquals(1, repository.findChatEntriesByChatId(entry.getChatId()).size());
    }

    @Test
    void storesACombinationOfItsOwnWithoutMoodId() throws Exception {
        EntryMood combination = new EntryMood(null, null, Map.of(Mood.Field.LIGHT,
                new EntryMood.Block("light-1", "Sodium Lamp", "a single sodium street lamp", null, true, List.of())));
        ChatEntry entry = save(new ChatEntry.Builder().chatId(chat().getId()).userPrompt("a street at night")
                .mood(combination).build());

        EntryMood stored = repository.findChatEntryById(entry.getId()).orElseThrow().getMood();
        assertNull(stored.moodId());
        assertNull(stored.moodName());
        assertEquals(combination.blocks(), stored.blocks());
        assertNull(moodIdColumn(entry.getId()));
    }

    @Test
    void readsEntriesWithoutCopyAndKeepsTheirStyle() throws Exception {
        Chat chat = chat();
        ChatEntry plain = save(new ChatEntry.Builder().chatId(chat.getId()).userPrompt("no blocks").build());
        insertOldEntry(chat, "old-entry", "Film Noir", null);
        insertOldEntry(chat, "broken-entry", "Pixel Art", "{not json");

        assertNull(repository.findChatEntryById(plain.getId()).orElseThrow().getMood());
        ChatEntry old = repository.findChatEntryById("old-entry").orElseThrow();
        assertNull(old.getMood(), "an entry from before 2.0 has no copy");
        assertEquals("Film Noir", old.getStyle());
        ChatEntry broken = repository.findChatEntryById("broken-entry").orElseThrow();
        assertNull(broken.getMood(), "an unreadable copy is ignored");
        assertEquals("Pixel Art", broken.getStyle());
        assertEquals(3, repository.findChatEntriesByChatId(chat.getId()).size());
    }

    private Chat chat() throws Exception {
        Chat chat = new Chat.Builder().name("Hafen").build();
        repository.saveChat(chat);
        return chat;
    }

    private ChatEntry save(ChatEntry entry) throws Exception {
        repository.saveChatEntry(entry);
        return entry;
    }

    /** Inserts an entry directly, as 1.x stored it with a style; {@code moodJson} null leaves the copy out. */
    private void insertOldEntry(Chat chat, String id, String style, String moodJson) throws Exception {
        String sql = "INSERT INTO chat_entries (id, chat_id, user_prompt, created_at, style, mood_json) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = databaseManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.setString(2, chat.getId());
            stmt.setString(3, "an old prompt");
            stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(5, style);
            stmt.setString(6, moodJson);
            stmt.executeUpdate();
        }
    }

    private String moodIdColumn(String entryId) throws Exception {
        try (Connection connection = databaseManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement("SELECT mood_id FROM chat_entries WHERE id = ?")) {
            stmt.setString(1, entryId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getString("mood_id");
            }
        }
    }
}
