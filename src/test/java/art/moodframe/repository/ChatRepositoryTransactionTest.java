package art.moodframe.repository;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the repository reads and writes in one transaction, against a fresh SQLite database: concurrent deletions book
 * once, a stale chat does not undo the trash, files follow the database, and the time of a deletion reads as the
 * local time whatever format an older version stored.
 */
class ChatRepositoryTransactionTest {

    private static final int THREADS = 8;

    @TempDir
    static Path tempDir;

    private static DatabaseManager databaseManager;
    private static ChatRepository repository;
    private static ChatTrashRepository trash;

    @BeforeAll
    static void createDatabase() throws Exception {
        databaseManager = new DatabaseManager(tempDir.resolve("db").resolve("test.db"));
        databaseManager.initialize();
        repository = new ChatRepository(databaseManager);
        trash = new ChatTrashRepository(databaseManager, ChatFiles.NONE);
    }

    /*  runs the work on several threads at once and returns their results  */
    private static <T> List<T> inParallel(Callable<T> work) throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++)
                futures.add(threads.submit(() -> {
                    start.await();
                    return work.call();
                }));
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures)
                results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            threads.shutdownNow();
        }
    }

    @Test
    void anImageDeletedTwiceAtTheSameTimeIsBookedOnce() throws Exception {
        Chat chat = chat("Doppelt");
        ChatEntry entry = entry(chat, 0.09, 0.03, 0.06);
        GeneratedImage large = repository.findGeneratedImagesByEntry(entry.getId()).get(1);

        List<Boolean> deleted = inParallel(() -> trash.softDeleteImage(large.getId()));

        assertEquals(1, deleted.stream().filter(Boolean::booleanValue).count(), "one deletion wins");
        assertEquals(0.06, trash.getDeletedCostForEntry(entry.getId()), 1e-9, "its cost is booked once");
        assertEquals(1, repository.findGeneratedImagesByEntry(entry.getId()).size());
    }

    @Test
    void theLastImageDeletedTwiceAtTheSameTimeBooksItsEntryOnceAtTheChat() throws Exception {
        Chat chat = chat("Letztes");
        ChatEntry entry = entry(chat, 0.04, 0.04);
        GeneratedImage image = repository.findGeneratedImagesByEntry(entry.getId()).get(0);

        List<Boolean> deleted = inParallel(() -> trash.softDeleteImage(image.getId()));

        assertEquals(1, deleted.stream().filter(Boolean::booleanValue).count());
        assertNotNull(repository.findChatEntryById(entry.getId()).orElseThrow().getDeletedAt());
        assertEquals(0.04, repository.findChatById(chat.getId()).orElseThrow().getDeletedCost(), 1e-9);
    }

    @Test
    void anEntryDeletedTwiceAtTheSameTimeIsBookedOnceAtItsChat() throws Exception {
        Chat chat = chat("Eintrag doppelt");
        ChatEntry entry = entry(chat, 0.50, 0.20, 0.30);

        List<Boolean> deleted = inParallel(() -> trash.softDeleteChatEntry(entry.getId()));

        assertEquals(1, deleted.stream().filter(Boolean::booleanValue).count());
        assertEquals(0.50, repository.findChatById(chat.getId()).orElseThrow().getDeletedCost(), 1e-9);
        assertFalse(trash.softDeleteChatEntry("no-such-entry"), "an entry that is not stored is not deleted");
    }

    @Test
    void aNewChatSavedFromSeveralThreadsIsInsertedOnce() throws Exception {
        Chat chat = new Chat.Builder().name("Parallel").build();

        inParallel(() -> {
            repository.saveChat(chat);
            return null;
        });

        assertEquals("Parallel", repository.findChatById(chat.getId()).orElseThrow().getName());
        ChatEntry entry = new ChatEntry.Builder().chatId(chat.getId()).userPrompt("parallel").build();
        inParallel(() -> {
            repository.saveChatEntry(entry);
            return null;
        });
        assertEquals(1, repository.findChatEntriesByChatId(chat.getId()).size());
    }

    @Test
    void savingAStaleChatKeepsItInTheTrash() throws Exception {
        Chat chat = chat("Veraltet");
        assertTrue(trash.softDeleteChat(chat.getId()));

        /*  The chat in memory still shows it in use, as a window that missed the deletion holds it  */
        chat.setName("Umbenannt");
        repository.saveChat(chat);

        Chat stored = repository.findChatById(chat.getId()).orElseThrow();
        assertEquals("Umbenannt", stored.getName());
        assertNotNull(stored.getDeletedAt(), "the chat stays in the trash");

        assertTrue(trash.restoreChat(chat.getId()));
        chat.markAsDeleted();
        repository.saveChat(chat);
        assertNull(repository.findChatById(chat.getId()).orElseThrow().getDeletedAt(), "and stays restored");
    }

    @Test
    void theFilesOfADeletionAndAnImmediateRestoreMoveInTheOrderOfTheCommits() throws Exception {
        List<String> moves = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicReference<ChatTrashRepository> files = new AtomicReference<>();
        AtomicReference<Future<Boolean>> restore = new AtomicReference<>();
        ExecutorService other = Executors.newSingleThreadExecutor();
        Chat chat = chat("Reihenfolge");
        entry(chat, 0.04, 0.04);
        files.set(new ChatTrashRepository(databaseManager, new ChatFiles() {
            @Override
            public void moveToTrash(String chatId, List<GeneratedImage> images) {

                /*  a restore from another thread while the files of the deletion still move  */
                restore.set(other.submit(() -> files.get().restoreChat(chatId)));
                try {
                    restore.get().get(300, TimeUnit.MILLISECONDS);
                }
                catch (Exception e) {
                    moves.add("waiting");
                }
                moves.add("to trash");
            }

            @Override
            public void restoreFromTrash(String chatId, List<GeneratedImage> images) {
                moves.add("back");
            }

            @Override
            public boolean deletePermanently(String chatId, List<GeneratedImage> images) {
                return true;
            }

            @Override
            public void removeDropped(List<GeneratedImage> images) {
            }
        }));

        try {
            assertTrue(files.get().softDeleteChat(chat.getId()));
            assertTrue(restore.get().get(10, TimeUnit.SECONDS));
        }
        finally {
            other.shutdownNow();
        }

        assertEquals(List.of("waiting", "to trash", "back"), moves, "the restore waits for the moves of the deletion");
        assertNull(repository.findChatById(chat.getId()).orElseThrow().getDeletedAt());
    }

    @Test
    void theFilesOfAChatFollowTheDatabaseAndTheirFailureDoesNotUndoIt() throws Exception {
        AtomicReference<Boolean> rowGoneBeforeFiles = new AtomicReference<>();
        ChatTrashRepository withFiles = new ChatTrashRepository(databaseManager, new ChatFiles() {
            @Override
            public void moveToTrash(String chatId, List<GeneratedImage> images) {
                assertEquals(1, images.size());
                throw new IllegalStateException("disk full");
            }

            @Override
            public void restoreFromTrash(String chatId, List<GeneratedImage> images) {
                throw new IllegalStateException("disk full");
            }

            @Override
            public boolean deletePermanently(String chatId, List<GeneratedImage> images) {
                try {
                    rowGoneBeforeFiles.set(repository.findChatById(chatId).isEmpty() && images.size() == 1);
                } catch (Exception e) {
                    rowGoneBeforeFiles.set(false);
                }
                return false;
            }

            @Override
            public void removeDropped(List<GeneratedImage> images) {
            }
        });
        Chat chat = chat("Dateien");
        entry(chat, 0.04, 0.04);

        assertTrue(withFiles.softDeleteChat(chat.getId()), "the chat is in the trash although its files are not");
        assertNotNull(repository.findChatById(chat.getId()).orElseThrow().getDeletedAt());
        assertTrue(withFiles.restoreChat(chat.getId()));
        assertNull(repository.findChatById(chat.getId()).orElseThrow().getDeletedAt());
        assertFalse(withFiles.deleteChatPermanently(chat.getId()), "a restored chat survives a removal from the trash");
        assertNull(rowGoneBeforeFiles.get(), "and keeps its files");
        assertTrue(repository.findChatById(chat.getId()).isPresent());

        assertTrue(trash.softDeleteChat(chat.getId()));
        assertTrue(withFiles.deleteChatPermanently(chat.getId()), "removed although a file stays on disk");
        assertEquals(Boolean.TRUE, rowGoneBeforeFiles.get(), "the files go after the rows");
        assertFalse(withFiles.deleteChatPermanently(chat.getId()));
    }

    @Test
    void anImageOfADeletedEntryIsNotSaved() throws Exception {
        ChatEntry entry = entry(chat("Spät"), 0.04, 0.04);
        assertTrue(trash.softDeleteChatEntry(entry.getId()));

        GeneratedImage late = new GeneratedImage.Builder().chatEntryId(entry.getId()).build();
        repository.saveGeneratedImage(late);

        assertTrue(repository.findGeneratedImageById(late.getId()).isEmpty());
    }

    @Test
    void readsTheThinkingTokensOfAnImageAndTheirAbsence() throws Exception {
        ChatEntry entry = entry(chat("Denken"), 0.0);
        GeneratedImage with = new GeneratedImage.Builder().chatEntryId(entry.getId()).thinkingTokens(1234).build();
        GeneratedImage without = new GeneratedImage.Builder().chatEntryId(entry.getId()).thinkingTokens(null).build();
        repository.saveGeneratedImage(with);
        repository.saveGeneratedImage(without);

        assertEquals(1234, repository.findGeneratedImageById(with.getId()).orElseThrow().getThinkingTokens());
        assertNull(repository.findGeneratedImageById(without.getId()).orElseThrow().getThinkingTokens());
    }

    @Test
    void writesEveryDeletionAsEpochMilliseconds() throws Exception {
        Chat chat = chat("Format");
        ChatEntry entry = entry(chat, 0.08, 0.04, 0.04);
        ChatEntry other = entry(chat, 0.04, 0.04);
        String image = repository.findGeneratedImagesByEntry(entry.getId()).get(0).getId();
        String imageOfOther = repository.findGeneratedImagesByEntry(other.getId()).get(0).getId();
        LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);

        trash.softDeleteImage(image);
        trash.softDeleteChatEntry(other.getId());
        trash.softDeleteChat(chat.getId());

        assertEquals("integer", typeOfDeletedAt("generated_images", image));
        assertEquals("integer", typeOfDeletedAt("chat_entries", other.getId()));
        assertEquals("integer", typeOfDeletedAt("generated_images", imageOfOther));
        assertEquals("integer", typeOfDeletedAt("chats", chat.getId()));
        LocalDateTime deletedAt = repository.findChatById(chat.getId()).orElseThrow().getDeletedAt();
        assertFalse(deletedAt.isBefore(before));
        assertTrue(deletedAt.isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void readsTheTimeOfADeletionInEveryFormatOlderVersionsStored() throws Exception {
        LocalDateTime local = LocalDateTime.now().minusHours(3).truncatedTo(ChronoUnit.SECONDS);
        Instant instant = local.atZone(ZoneId.systemDefault()).toInstant();
        String utcText = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC).format(instant);

        Chat chat = chat("Alt");
        ChatEntry millis = entry(chat, 0.0);
        ChatEntry isoText = entry(chat, 0.0);
        ChatEntry currentTimestamp = entry(chat, 0.0);
        setDeletedAt("chat_entries", millis.getId(), Timestamp.valueOf(local));
        setDeletedAt("chat_entries", isoText.getId(), local.withNano(512_000_000).toString());
        setDeletedAt("chat_entries", currentTimestamp.getId(), utcText);
        setDeletedAt("chats", chat.getId(), local.toString());

        assertEquals(local, repository.findChatEntryById(millis.getId()).orElseThrow().getDeletedAt());
        assertEquals(local.withNano(512_000_000),
                repository.findChatEntryById(isoText.getId()).orElseThrow().getDeletedAt(),
                "a chat put in the trash by an older version: local ISO text");
        assertEquals(local, repository.findChatEntryById(currentTimestamp.getId()).orElseThrow().getDeletedAt(),
                "an entry or image put in the trash by an older version: UTC text of CURRENT_TIMESTAMP");
        assertEquals(local, repository.findChatById(chat.getId()).orElseThrow().getDeletedAt());
    }

    @Test
    void parsesEveryStoredFormatOfADeletion() {
        LocalDateTime local = LocalDateTime.of(2026, 1, 15, 10, 30, 0);
        String utc = local.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        assertNull(ChatRepository.parseDeletedAt(null));
        assertEquals(local, ChatRepository.parseDeletedAt(Timestamp.valueOf(local).getTime()));
        assertEquals(local, ChatRepository.parseDeletedAt(String.valueOf(Timestamp.valueOf(local).getTime())));
        assertEquals(local, ChatRepository.parseDeletedAt("2026-01-15T10:30"));
        assertEquals(local, ChatRepository.parseDeletedAt(utc));
        assertNotNull(ChatRepository.parseDeletedAt("yesterday"), "an unreadable value still means deleted");
    }

    @Test
    void aChatWithAnUnreadableDeletionStaysInTheTrash() throws Exception {
        Chat chat = chat("Unlesbar");
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement("UPDATE chats SET deleted_at = ? WHERE id = ?")) {
            stmt.setString(1, "yesterday");
            stmt.setString(2, chat.getId());
            stmt.executeUpdate();
        }

        Chat stored = repository.findDeletedChats().stream().filter((Chat deleted) -> deleted.getId()
                .equals(chat.getId())).findFirst().orElseThrow();
        assertTrue(stored.isDeleted(), "it does not come back into use");
        assertTrue(repository.findChatById(chat.getId()).orElseThrow().isDeleted());
    }

    @Test
    void listsTheDeletedChatsLatestFirstAcrossFormats() throws Exception {
        Chat older = chat("Älter");
        Chat newer = chat("Neuer");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        setDeletedAt("chats", older.getId(), Timestamp.valueOf(now.minusDays(400)));
        setDeletedAt("chats", newer.getId(), now.minusDays(399).toString());

        List<String> ids = repository.findDeletedChats().stream().map(Chat::getId)
                .filter((String id) -> id.equals(older.getId()) || id.equals(newer.getId())).toList();
        assertEquals(List.of(newer.getId(), older.getId()), ids);
    }

    private Chat chat(String name) throws Exception {
        Chat chat = new Chat.Builder().name(name).build();
        repository.saveChat(chat);
        return chat;
    }

    /** Stores an entry with its cost and an image for each image cost. */
    private ChatEntry entry(Chat chat, double cost, double... imageCosts) throws Exception {
        ChatEntry entry = new ChatEntry.Builder().chatId(chat.getId()).userPrompt("prompt").cost(cost).build();
        repository.saveChatEntry(entry);
        for (int i = 0; i < imageCosts.length; i++)
            repository.saveGeneratedImage(new GeneratedImage.Builder().chatEntryId(entry.getId()).model("flux-2-pro")
                    .baseCost(imageCosts[i]).sortOrder(i).build());
        return entry;
    }

    private String typeOfDeletedAt(String table, String id) throws Exception {
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        "SELECT typeof(deleted_at) FROM " + table + " WHERE id = ?")) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    private void setDeletedAt(String table, String id, Object value) throws Exception {
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE " + table + " SET deleted_at = ? WHERE id = ?")) {
            stmt.setObject(1, value);
            stmt.setString(2, id);
            assertEquals(1, stmt.executeUpdate());
        }
    }
}
