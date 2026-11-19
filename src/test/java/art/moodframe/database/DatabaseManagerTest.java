package art.moodframe.database;

import art.moodframe.model.BrainstormMessage;
import art.moodframe.model.BrainstormSession;
import art.moodframe.repository.BrainstormRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every connection of the one factory waits for locks and enforces the foreign keys of the schema; a transaction
 * begins IMMEDIATE, so one that reads before it writes waits for another writer instead of failing.
 */
class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    private DatabaseManager database;

    @BeforeEach
    void createDatabase() throws Exception {
        database = new DatabaseManager(tempDir.resolve("db").resolve("test.db"));
        database.initialize();
    }

    @Test
    void everyConnectionWaitsForLocksAndEnforcesForeignKeysInWalMode() throws Exception {
        try (Connection connection = database.getConnection()) {
            assertEquals("1", pragma(connection, "foreign_keys"));
            assertEquals("5000", pragma(connection, "busy_timeout"));
            assertEquals("wal", pragma(connection, "journal_mode"));
        }
    }

    @Test
    void refusesAChatEntryOrImageWithoutItsParent() throws Exception {
        try (Connection connection = database.getConnection(); Statement stmt = connection.createStatement()) {
            assertThrows(SQLException.class, () -> stmt.executeUpdate(
                    "INSERT INTO chat_entries (id, chat_id, user_prompt) VALUES ('e', 'no-chat', 'p')"));
            assertThrows(SQLException.class, () -> stmt.executeUpdate(
                    "INSERT INTO generated_images (id, chat_entry_id) VALUES ('i', 'no-entry')"));
        }
    }

    @Test
    void deletingAChatCascadesToItsEntriesAndImages() throws Exception {
        try (Connection connection = database.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("INSERT INTO chats (id, name) VALUES ('c', 'Chat')");
            stmt.executeUpdate("INSERT INTO chat_entries (id, chat_id, user_prompt) VALUES ('e', 'c', 'p')");
            stmt.executeUpdate("INSERT INTO generated_images (id, chat_entry_id) VALUES ('i', 'e')");

            stmt.executeUpdate("DELETE FROM chats WHERE id = 'c'");

            assertEquals(0, count(stmt, "SELECT COUNT(*) FROM chat_entries WHERE id = 'e'"));
            assertEquals(0, count(stmt, "SELECT COUNT(*) FROM generated_images WHERE id = 'i'"));
        }
    }

    @Test
    void deletingABrainstormSessionCascadesToItsMessagesAndDrafts() throws Exception {
        BrainstormRepository repository = new BrainstormRepository(database);
        BrainstormSession session = repository.createSession("Weg");
        repository.addMessage(session.getId(), new BrainstormMessage(BrainstormMessage.ROLE_USER, "x"));
        assertEquals(1, repository.addDraft(session.getId(), "{}"));

        assertTrue(repository.deleteSession(session.getId()));

        try (Connection connection = database.getConnection(); Statement stmt = connection.createStatement()) {
            assertEquals(0, count(stmt, "SELECT COUNT(*) FROM brainstorm_messages"));
            assertEquals(0, count(stmt, "SELECT COUNT(*) FROM brainstorm_drafts"));
        }
    }

    @Test
    void storesNoMessageOrDraftOfABrainstormSessionThatDoesNotExist() throws Exception {
        BrainstormRepository repository = new BrainstormRepository(database);

        assertThrows(SQLException.class, () -> repository.addMessage("deleted-session",
                new BrainstormMessage(BrainstormMessage.ROLE_ASSISTANT, "late")));
        assertThrows(SQLException.class, () -> repository.addDraft("deleted-session", "{}"));

        try (Connection connection = database.getConnection(); Statement stmt = connection.createStatement()) {
            assertEquals(0, count(stmt, "SELECT COUNT(*) FROM brainstorm_messages"));
            assertEquals(0, count(stmt, "SELECT COUNT(*) FROM brainstorm_drafts"));
        }
    }

    @Test
    void twoTransactionsThatReadBeforeTheyWriteBothSucceedAndLoseNoUpdate() throws Exception {
        try (Connection connection = database.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE counter (id INTEGER PRIMARY KEY, value INTEGER NOT NULL)");
            stmt.executeUpdate("INSERT INTO counter VALUES (1, 0)");
        }

        /*  Each reads, waits a moment for the other to read too, then writes what it read plus one. Deferred, both
            would read 0 and the second writer would fail with SQLITE_BUSY_SNAPSHOT; immediate, the second waits  */
        CountDownLatch bothRead = new CountDownLatch(2);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++)
                results.add(threads.submit(() -> database.inTransaction((Connection connection) -> {
                    int value;
                    try (Statement stmt = connection.createStatement();
                         ResultSet rs = stmt.executeQuery("SELECT value FROM counter WHERE id = 1")) {
                        value = rs.getInt(1);
                    }
                    bothRead.countDown();
                    await(bothRead, 500);
                    try (Statement stmt = connection.createStatement()) {
                        stmt.executeUpdate("UPDATE counter SET value = " + (value + 1) + " WHERE id = 1");
                    }
                    return value;
                })));
            List<Integer> read = new ArrayList<>();
            for (Future<Integer> result : results)
                read.add(result.get(10, TimeUnit.SECONDS));
            assertEquals(Set.of(0, 1), Set.copyOf(read), "the second one reads what the first one wrote");
        } finally {
            threads.shutdownNow();
        }
        try (Connection connection = database.getConnection(); Statement stmt = connection.createStatement()) {
            assertEquals(2, count(stmt, "SELECT value FROM counter WHERE id = 1"));
        }
    }

    @Test
    void aReaderDoesNotWaitForAWriteTransaction() throws Exception {
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch read = new CountDownLatch(1);
        ExecutorService thread = Executors.newSingleThreadExecutor();
        try {
            Future<Object> writer = thread.submit(() -> database.inTransaction((Connection connection) -> {
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate("INSERT INTO chats (id, name) VALUES ('w', 'Writing')");
                }
                writing.countDown();
                assertTrue(await(read, 10_000));
                return null;
            }));
            assertTrue(writing.await(10, TimeUnit.SECONDS));

            long start = System.nanoTime();
            try (Connection connection = database.getConnection(); Statement stmt = connection.createStatement()) {
                assertEquals(0, count(stmt, "SELECT COUNT(*) FROM chats WHERE id = 'w'"),
                        "the reader sees the state before the open transaction");
            }
            assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2), "and does not wait for it");
            read.countDown();
            writer.get(10, TimeUnit.SECONDS);
        } finally {
            thread.shutdownNow();
        }
    }

    @Test
    void aTransactionThatFailsStoresNothing() throws Exception {
        assertThrows(SQLException.class, () -> database.inTransaction((Connection connection) -> {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("INSERT INTO chats (id, name) VALUES ('t', 'Rolled back')");
                stmt.executeUpdate("INSERT INTO chats (id, name) VALUES ('t', 'Duplicate')");
            }
            return null;
        }));

        try (Connection connection = database.getConnection(); Statement stmt = connection.createStatement()) {
            assertEquals(0, count(stmt, "SELECT COUNT(*) FROM chats WHERE id = 't'"));
        }
    }

    /*  waits inside a transaction, which may throw SQLException only  */
    private static boolean await(CountDownLatch latch, long millis) {
        try {
            return latch.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String pragma(Connection connection, String name) throws SQLException {
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("PRAGMA " + name)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static int count(Statement stmt, String sql) throws SQLException {
        try (ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }
}
