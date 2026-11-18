package art.moodframe.service;

import art.moodframe.database.DatabaseConfig;
import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.Mood;
import art.moodframe.repository.ChatRepository;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Several threads write into the library at the same time, as the main window and an agent will: no write fails with
 * {@code SQLITE_BUSY} and none gets lost. The chats change on the JavaFX thread of the Monocle headless platform.
 */
class ParallelWritesTest {

    private static final int THREADS = 8;
    private static final int WRITES = 15;

    @TempDir
    static Path tempDir;

    private static Path database;

    @BeforeAll
    static void startToolkitAndCreateDatabase() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await(10, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);

        database = tempDir.resolve("db").resolve("test.db");
        DatabaseConfig.initialize(database.toString());
    }

    /*  runs the same work on several threads at once and returns the results of all of them  */
    private static <T> List<T> inParallel(Callable<List<T>> work) throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<List<T>>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++)
                futures.add(threads.submit(() -> {
                    start.await();
                    return work.call();
                }));
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<List<T>> future : futures)
                results.addAll(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            threads.shutdownNow();
        }
    }

    @Test
    void theConnectionsWaitForLocksAndTheDatabaseRunsInWalMode() throws Exception {
        try (Connection connection = DatabaseConfig.getDatabase().getConnection();
             Statement stmt = connection.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("PRAGMA busy_timeout")) {
                assertEquals(5000, rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
                assertEquals("wal", rs.getString(1));
            }
        }
        try (Connection connection = new DatabaseManager(database).getConnection();
             Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("PRAGMA busy_timeout")) {
            assertEquals(5000, rs.getInt(1));
        }
    }

    @Test
    void storesEveryMoodWhileOthersReadAndTakesAContestedNameOnce() throws Exception {
        MoodService moods = new MoodService(new PromptBlockService(), new ColorStyleService());
        int before = moods.getCustom().size();

        List<Boolean> added = inParallel(() -> {
            List<Boolean> results = new ArrayList<>();
            for (int i = 0; i < WRITES; i++) {
                results.add(moods.add(new Mood(null, "Parallel " + Thread.currentThread().getName() + " " + i)));
                moods.getAll();
            }
            return results;
        });
        assertEquals(THREADS * WRITES, added.stream().filter(Boolean::booleanValue).count());
        assertEquals(before + THREADS * WRITES, moods.getCustom().size(), "no mood is lost");

        List<Boolean> contested = inParallel(() -> List.of(moods.add(new Mood(null, "Contested"))));
        assertEquals(1, contested.stream().filter(Boolean::booleanValue).count(), "a name is taken once only");
    }

    @Test
    void storesEveryFavorite() throws Exception {
        FavoriteService favorites = new FavoriteService();
        List<String> ids = inParallel(() -> {
            List<String> stored = new ArrayList<>();
            for (int i = 0; i < WRITES; i++) {
                String id = Thread.currentThread().getName() + "-" + i;
                if (favorites.toggle(FavoriteService.Kind.MOOD, id))
                    stored.add(id);
            }
            return stored;
        });
        assertEquals(THREADS * WRITES, ids.size());

        /*  the favorites read anew from the database  */
        FavoriteService restarted = new FavoriteService();
        assertTrue(ids.stream().allMatch((String id) -> restarted.isFavorite(FavoriteService.Kind.MOOD, id)),
                "no favorite is lost");
    }

    @Test
    void storesAndShowsEveryEntryOfAChat() throws Exception {
        ChatRepository repository = new ChatRepository(new DatabaseManager(database));
        ChatHistoryService history = new ChatHistoryService(repository, null, null);
        ChatLibrary library = new ChatLibrary(history, new LibraryEvents());
        Chat chat = library.createChat("Parallel");

        inParallel(() -> {
            for (int i = 0; i < WRITES; i++)
                library.addEntry(chat, new ChatEntry.Builder().chatId(chat.getId()).userPrompt("prompt " + i).build());
            return List.of();
        });
        assertEquals(THREADS * WRITES, library.find(chat.getId()).getEntries().size(), "every entry shows");
        assertEquals(THREADS * WRITES, repository.findChatEntriesByChatId(chat.getId()).size(),
                "every entry is stored");
    }
}
