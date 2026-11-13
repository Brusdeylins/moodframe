package art.moodframe.repository;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryMood;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.Mood;
import art.moodframe.model.MoodGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                "analog film photograph, visible grain", "Photo", true, List.of(), null));
        blocks.put(Mood.Field.ATMOSPHERE, new EntryMood.Block("atmosphere-1", "Harbor Fog",
                "quiet, cold, a sense of distance", null, false, List.of(), null));
        blocks.put(Mood.Field.COLOR, new EntryMood.Block("palette-1", "Harbor", null, null, false,
                List.of("#1A1A1A", "#C0C0C0"), null));
        blocks.put(Mood.Field.CAMERA, new EntryMood.Block("camera-1", "Harbor Tele",
                "shot on a full-frame digital camera, 200mm telephoto lens", null, false, List.of(),
                "{\"body\":\"FULL_FRAME\",\"lens\":\"MM_200\"}"));
        EntryMood mood = new EntryMood("mood-1", "Harbor Dawn", blocks);
        ChatEntry entry = save(new ChatEntry.Builder().chatId(chat().getId()).userPrompt("a harbor at dawn")
                .style("Analog Film").mood(mood).build());

        ChatEntry stored = repository.findChatEntryById(entry.getId()).orElseThrow();
        assertEquals(mood, stored.getMood(), "the camera keeps its settings");
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
                new EntryMood.Block("light-1", "Sodium Lamp", "a single sodium street lamp", null, true, List.of(),
                        null)));
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

    @Test
    void groupsTheEntriesByTheirMoodAndCountsTheImages() throws Exception {
        LocalDateTime start = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        Chat chat = chat();
        Chat trashed = chat();
        insertMood("group-renamed", "Harbor Night", null);
        insertMood("group-trashed", "Old Harbor Renamed", LocalDateTime.now());

        /*  The other tests add entries without images only, which leave the counts alone  */
        ChatEntry first = entry(chat, mood("group-renamed", "Harbor Dusk", "#102030"), start, 2);
        ChatEntry second = entry(chat, mood("group-renamed", "Harbor Dusk", "#405060"), start.plusMinutes(1), 1);
        ChatEntry removed = entry(chat, mood("group-renamed", "Harbor Dusk", "#708090"), start.plusSeconds(30), 1);
        entry(chat, mood("group-gone", "Gone Mood", "#111111"), start.plusMinutes(2), 1);
        entry(chat, mood("group-trashed", "Old Harbor", "#222222"), start.plusMinutes(3), 1);
        entry(chat, mood("std:mood:film-noir", "Film Noir", "#333333"), start.plusMinutes(4), 3);
        entry(chat, mood("group-without-image", "Failed", "#444444"), start.plusMinutes(5), 0);
        ChatEntry combination = entry(chat, mood(null, null, "#555555"), start.plusMinutes(6), 2);
        ChatEntry plain = entry(chat, null, start.plusMinutes(7), 1);
        entry(trashed, mood("group-renamed", "Harbor Dusk", "#666666"), start.plusMinutes(8), 5);
        entry(trashed, mood("group-only-trashed", "Hidden", "#777777"), start.plusMinutes(9), 1);

        GeneratedImage deletedImage = image(first);
        repository.softDeleteImage(deletedImage.getId());
        repository.saveGeneratedImage(new GeneratedImage.Builder().chatEntryId(second.getId())
                .downloadStatus(GeneratedImage.DownloadStatus.FAILED).build());
        repository.softDeleteChatEntry(removed.getId());
        repository.softDeleteChat(trashed.getId());

        List<MoodGroup> groups = repository.findMoodGroups();
        assertEquals(List.of("std:mood:film-noir", "group-trashed", "group-gone", "group-renamed"),
                groups.stream().filter((MoodGroup group) -> group.kind() == MoodGroup.Kind.MOOD)
                        .map(MoodGroup::moodId).toList(), "the latest use first, only moods with images");
        assertEquals(List.of(MoodGroup.Kind.CUSTOM, MoodGroup.Kind.NONE),
                groups.subList(groups.size() - 2, groups.size()).stream().map(MoodGroup::kind).toList());

        MoodGroup renamed = group(groups, "group-renamed");
        assertEquals("Harbor Night", renamed.name(), "a renamed mood shows its current name");
        assertFalse(renamed.deleted());
        assertEquals(3, renamed.imageCount(),
                "neither the trashed chat nor deleted entries, deleted images or failed images count");
        assertEquals(List.of("#405060"), renamed.colors(), "the palette of the latest entry");
        assertEquals(start.plusMinutes(1), renamed.lastUsed());

        MoodGroup inTrash = group(groups, "group-trashed");
        assertTrue(inTrash.deleted());
        assertEquals("Old Harbor", inTrash.name(), "a deleted mood keeps the name stored at the entry");
        MoodGroup gone = group(groups, "group-gone");
        assertTrue(gone.deleted());
        assertEquals("Gone Mood", gone.name());
        MoodGroup standard = group(groups, "std:mood:film-noir");
        assertFalse(standard.deleted(), "a standard mood is never stored and never deleted");
        assertEquals("Film Noir", standard.name());
        assertEquals(3, standard.imageCount());

        assertEquals(2, groups.get(groups.size() - 2).imageCount(), "the combinations of their own");
        assertEquals(1, groups.get(groups.size() - 1).imageCount(), "the entries without blocks");

        assertEquals(List.of(first.getId(), second.getId()), repository.findChatEntryIdsByMood(renamed));
        assertTrue(repository.findChatEntryIdsByMood(groups.get(groups.size() - 2)).contains(combination.getId()));
        assertTrue(repository.findChatEntryIdsByMood(groups.get(groups.size() - 1)).contains(plain.getId()));
        assertTrue(renamed.holds(first));
        assertTrue(groups.get(groups.size() - 2).holds(combination));
        assertTrue(groups.get(groups.size() - 1).holds(plain));
        assertFalse(renamed.holds(combination));
    }

    @Test
    void keepsTheSizeOfAnImageAcrossSavingAndLoading() throws Exception {
        GeneratedImage image = sized(save(new ChatEntry.Builder().chatId(chat().getId()).userPrompt("a coast")
                .build()), 2752, 1536);

        assertEquals("2752x1536", repository.findGeneratedImageById(image.getId()).orElseThrow().getDimensions());
    }

    @Test
    void loadsAnImageInItsSizeAlthoughWidthAndHeightHoldTheirDefault() throws Exception {
        GeneratedImage image = sized(save(new ChatEntry.Builder().chatId(chat().getId()).userPrompt("a coast")
                .build()), 2752, 1536);

        /*  As for an image whose insert left the columns out, so they took their default  */
        try (Connection connection = databaseManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(
                        "UPDATE generated_images SET width = 1024, height = 1024 WHERE id = ?")) {
            stmt.setString(1, image.getId());
            stmt.executeUpdate();
        }

        assertEquals("2752x1536", repository.findGeneratedImageById(image.getId()).orElseThrow().getDimensions());
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

    /** Stores an entry with a mood (null for none) and the given number of images. */
    private ChatEntry entry(Chat chat, EntryMood mood, LocalDateTime createdAt, int images) throws Exception {
        ChatEntry entry = save(new ChatEntry.Builder().chatId(chat.getId()).userPrompt("a harbor").mood(mood)
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

    /** Stores a downloaded image of an entry with the size read from its file. */
    private GeneratedImage sized(ChatEntry entry, int width, int height) throws Exception {
        GeneratedImage image = new GeneratedImage.Builder().chatEntryId(entry.getId())
                .downloadStatus(GeneratedImage.DownloadStatus.CACHED).build();
        image.setImageWidth(width);
        image.setImageHeight(height);
        image.setWidth(width);
        image.setHeight(height);
        repository.saveGeneratedImage(image);
        return image;
    }

    /** A mood with a palette of one color; a null id stands for a combination of its own. */
    private static EntryMood mood(String id, String name, String color) {
        return new EntryMood(id, name, Map.of(Mood.Field.COLOR,
                new EntryMood.Block("palette-" + color, "Palette", null, null, false, List.of(color), null)));
    }

    private static MoodGroup group(List<MoodGroup> groups, String moodId) {
        return groups.stream().filter((MoodGroup group) -> moodId.equals(group.moodId())).findFirst().orElseThrow();
    }

    /** Inserts a mood of the user directly; {@code deletedAt} null keeps it in use. */
    private void insertMood(String id, String name, LocalDateTime deletedAt) throws Exception {
        try (Connection connection = databaseManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(
                        "INSERT INTO moods (id, name, deleted_at) VALUES (?, ?, ?)")) {
            stmt.setString(1, id);
            stmt.setString(2, name);
            stmt.setTimestamp(3, deletedAt != null ? Timestamp.valueOf(deletedAt) : null);
            stmt.executeUpdate();
        }
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
