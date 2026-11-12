package art.moodframe.repository;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryFrame;
import art.moodframe.model.FrameGroup;
import art.moodframe.model.GeneratedImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against a fresh SQLite database of its own, so migration V024 is applied together with all earlier ones and
 * the counts of the groups by frame stay apart from the entries of {@link ChatRepositoryTest}.
 */
class ChatRepositoryFrameTest {

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
    void storesTheFrameAndTheTargetOfAnEntry() throws Exception {
        EntryFrame frame = new EntryFrame("std:frame:social-post", "Social Post", "4:5", "2K");
        ChatEntry entry = save(new ChatEntry.Builder().chatId(chat().getId()).userPrompt("a harbor at dawn")
                .frame(frame).build());

        assertEquals(frame, repository.findChatEntryById(entry.getId()).orElseThrow().getFrame());
        assertEquals("std:frame:social-post", frameIdColumn(entry.getId()));

        entry.setStatus(ChatEntry.Status.COMPLETED);
        repository.saveChatEntry(entry);
        assertEquals(frame, repository.findChatEntryById(entry.getId()).orElseThrow().getFrame(),
                "an update keeps the copy");
    }

    @Test
    void storesASettingOfItsOwnWithoutFrameId() throws Exception {
        EntryFrame setting = new EntryFrame(null, null, "21:9", null);
        ChatEntry entry = save(new ChatEntry.Builder().chatId(chat().getId()).userPrompt("a wide street")
                .frame(setting).build());

        assertEquals(setting, repository.findChatEntryById(entry.getId()).orElseThrow().getFrame());
        assertNull(frameIdColumn(entry.getId()));

        ChatEntry plain = save(new ChatEntry.Builder().chatId(chat().getId()).userPrompt("no frame").build());
        assertNull(repository.findChatEntryById(plain.getId()).orElseThrow().getFrame());
        assertNull(EntryFrame.fromJson("{not json"), "an unreadable copy is ignored");
    }

    @Test
    void groupsTheEntriesByTheirFrameAndCountsTheImages() throws Exception {
        LocalDateTime start = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        Chat chat = chat();
        Chat trashed = chat();
        insertFrame("frame-renamed", "Print Night", null);
        insertFrame("frame-trashed", "Old Print Renamed", LocalDateTime.now());

        /*  The other tests add entries without images only, which leave the counts alone  */
        ChatEntry first = entry(chat, frame("frame-renamed", "Print Dusk", "2:3", "4K"), start, 2);
        ChatEntry second = entry(chat, frame("frame-renamed", "Print Dusk", "3:2", null), start.plusMinutes(1), 1);
        ChatEntry removed = entry(chat, frame("frame-renamed", "Print Dusk", "2:3", "4K"), start.plusSeconds(30), 1);
        entry(chat, frame("frame-gone", "Gone Frame", "1:1", "1K"), start.plusMinutes(2), 1);
        entry(chat, frame("frame-trashed", "Old Print", "4:3", "2K"), start.plusMinutes(3), 1);
        entry(chat, frame("std:frame:social-post", "Social Post", "4:5", "2K"), start.plusMinutes(4), 3);
        entry(chat, frame("frame-without-image", "Failed", "1:1", "1K"), start.plusMinutes(5), 0);
        ChatEntry setting = entry(chat, frame(null, null, "16:9", "2K"), start.plusMinutes(6), 2);
        ChatEntry plain = entry(chat, null, start.plusMinutes(7), 1);
        entry(trashed, frame("frame-renamed", "Print Dusk", "2:3", "4K"), start.plusMinutes(8), 5);
        entry(trashed, frame("frame-only-trashed", "Hidden", "1:1", "1K"), start.plusMinutes(9), 1);

        GeneratedImage deletedImage = image(first);
        repository.softDeleteImage(deletedImage.getId());
        repository.saveGeneratedImage(new GeneratedImage.Builder().chatEntryId(second.getId())
                .downloadStatus(GeneratedImage.DownloadStatus.FAILED).build());
        repository.softDeleteChatEntry(removed.getId());
        repository.softDeleteChat(trashed.getId());

        List<FrameGroup> groups = repository.findFrameGroups();
        assertEquals(List.of("std:frame:social-post", "frame-trashed", "frame-gone", "frame-renamed"),
                groups.stream().filter((FrameGroup group) -> group.kind() == FrameGroup.Kind.FRAME)
                        .map(FrameGroup::frameId).toList(), "the latest use first, only frames with images");
        assertEquals(List.of(FrameGroup.Kind.CUSTOM, FrameGroup.Kind.NONE),
                groups.subList(groups.size() - 2, groups.size()).stream().map(FrameGroup::kind).toList());

        FrameGroup renamed = group(groups, "frame-renamed");
        assertEquals("Print Night", renamed.name(), "a renamed frame shows its current name");
        assertFalse(renamed.deleted());
        assertEquals(3, renamed.imageCount(),
                "neither the trashed chat nor deleted entries, deleted images or failed images count");
        assertEquals("3:2", renamed.aspectRatio(), "the target of the latest entry");
        assertNull(renamed.resolution());
        assertEquals(start.plusMinutes(1), renamed.lastUsed());

        FrameGroup inTrash = group(groups, "frame-trashed");
        assertTrue(inTrash.deleted());
        assertEquals("Old Print", inTrash.name(), "a frame in the trash keeps the name stored at the entry");
        FrameGroup gone = group(groups, "frame-gone");
        assertTrue(gone.deleted());
        assertEquals("Gone Frame", gone.name());
        FrameGroup standard = group(groups, "std:frame:social-post");
        assertFalse(standard.deleted(), "a standard frame is never stored and never deleted");
        assertEquals("Social Post", standard.name());
        assertEquals("4:5", standard.aspectRatio());
        assertEquals("2K", standard.resolution());
        assertEquals(3, standard.imageCount());

        FrameGroup ownSettings = groups.get(groups.size() - 2);
        FrameGroup withoutFrame = groups.get(groups.size() - 1);
        assertEquals(2, ownSettings.imageCount(), "the settings of their own");
        assertNull(ownSettings.aspectRatio(), "a fixed group has no target of its own");
        assertEquals(1, withoutFrame.imageCount(), "the entries without copy");

        assertEquals(List.of(first.getId(), second.getId()), repository.findChatEntryIdsByFrame(renamed));
        assertTrue(repository.findChatEntryIdsByFrame(ownSettings).contains(setting.getId()));
        assertTrue(repository.findChatEntryIdsByFrame(withoutFrame).contains(plain.getId()));
        assertTrue(renamed.holds(first));
        assertTrue(ownSettings.holds(setting));
        assertTrue(withoutFrame.holds(plain));
        assertFalse(renamed.holds(setting));
        assertTrue(renamed.sameAs(new FrameGroup(FrameGroup.Kind.FRAME, "frame-renamed", "Other", false, null, null, 0,
                null)), "name, target, count and time do not matter");
        assertFalse(ownSettings.sameAs(withoutFrame));
    }

    @Test
    void migrationV024KeepsTheEntriesOfADatabaseWithoutFrameAsEntriesWithoutFrame() throws Exception {
        Path file = tempDir.resolve("v023").resolve("v023.db");
        DatabaseManager older = new DatabaseManager(file);
        older.initialize();

        /*  A database as V023 left it: no frame columns, V024 not recorded, an entry with its mood only  */
        try (Connection conn = older.getConnection();
             Statement stmt = conn.createStatement();
             PreparedStatement insert = conn.prepareStatement("INSERT INTO chat_entries (id, chat_id, user_prompt, "
                     + "created_at, style, mood_json) VALUES ('old-entry', 'old-chat', 'an old prompt', ?, 'None', "
                     + "'{\"blocks\": {}}')")) {
            stmt.execute("DROP INDEX idx_chat_entries_frame_id");
            stmt.execute("ALTER TABLE chat_entries DROP COLUMN frame_id");
            stmt.execute("ALTER TABLE chat_entries DROP COLUMN frame_json");
            stmt.execute("DELETE FROM schema_migrations WHERE version = '024'");
            new ChatRepository(older).saveChat(new Chat.Builder().id("old-chat").name("Old Chat").build());
            insert.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            insert.executeUpdate();
        }
        assertFalse(columns(older).contains("frame_json"));

        DatabaseManager migrated = new DatabaseManager(file);
        migrated.initialize();
        assertTrue(columns(migrated).containsAll(List.of("frame_json", "frame_id")));

        ChatRepository chats = new ChatRepository(migrated);
        chats.saveGeneratedImage(new GeneratedImage.Builder().chatEntryId("old-entry")
                .downloadStatus(GeneratedImage.DownloadStatus.CACHED).build());
        ChatEntry old = chats.findChatEntryById("old-entry").orElseThrow();
        assertNull(old.getFrame(), "an entry from before V024 has no copy");
        assertNotNull(old.getMood(), "the mood stays");

        FrameGroup withoutFrame = chats.findFrameGroups().getLast();
        assertEquals(FrameGroup.Kind.NONE, withoutFrame.kind());
        assertEquals(1, withoutFrame.imageCount());
        assertEquals(List.of("old-entry"), chats.findChatEntryIdsByFrame(withoutFrame));
    }

    private Chat chat() throws Exception {
        Chat chat = new Chat.Builder().name("Druck").build();
        repository.saveChat(chat);
        return chat;
    }

    private ChatEntry save(ChatEntry entry) throws Exception {
        repository.saveChatEntry(entry);
        return entry;
    }

    /** Stores an entry with a frame (null for none) and the given number of images. */
    private ChatEntry entry(Chat chat, EntryFrame frame, LocalDateTime createdAt, int images) throws Exception {
        ChatEntry entry = save(new ChatEntry.Builder().chatId(chat.getId()).userPrompt("a print").frame(frame)
                .createdAt(createdAt).build());
        for (int i = 0; i < images; i++)
            image(entry);
        return entry;
    }

    /** Stores a downloaded image of an entry. */
    private GeneratedImage image(ChatEntry entry) throws Exception {
        GeneratedImage image = new GeneratedImage.Builder().chatEntryId(entry.getId())
                .downloadStatus(GeneratedImage.DownloadStatus.CACHED).build();
        repository.saveGeneratedImage(image);
        return image;
    }

    /** A frame with its target; a null id stands for a setting of its own. */
    private static EntryFrame frame(String id, String name, String aspectRatio, String resolution) {
        return new EntryFrame(id, name, aspectRatio, resolution);
    }

    private static FrameGroup group(List<FrameGroup> groups, String frameId) {
        return groups.stream().filter((FrameGroup group) -> frameId.equals(group.frameId())).findFirst().orElseThrow();
    }

    /** Inserts a frame of the user directly; {@code deletedAt} null keeps it in use. */
    private void insertFrame(String id, String name, LocalDateTime deletedAt) throws Exception {
        try (Connection connection = databaseManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(
                        "INSERT INTO frames (id, name, aspect_ratio, deleted_at) VALUES (?, ?, '1:1', ?)")) {
            stmt.setString(1, id);
            stmt.setString(2, name);
            stmt.setTimestamp(3, deletedAt != null ? Timestamp.valueOf(deletedAt) : null);
            stmt.executeUpdate();
        }
    }

    private String frameIdColumn(String entryId) throws Exception {
        try (Connection connection = databaseManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(
                        "SELECT frame_id FROM chat_entries WHERE id = ?")) {
            stmt.setString(1, entryId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getString("frame_id");
            }
        }
    }

    /** The column names of the table of the chat entries. */
    private static List<String> columns(DatabaseManager manager) throws Exception {
        List<String> columns = new ArrayList<>();
        try (Connection conn = manager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(chat_entries)")) {
            while (rs.next())
                columns.add(rs.getString("name"));
        }
        return columns;
    }
}
