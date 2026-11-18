package art.moodframe.database;

import art.moodframe.model.BrainstormDraft;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every connection of the one factory waits for locks and enforces the foreign keys of the schema. */
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
        BrainstormSession session = repository.createSession("Weg").orElseThrow();
        repository.addMessage(session.getId(), new BrainstormMessage(BrainstormMessage.ROLE_USER, "x"));
        assertEquals(1, repository.addDraft(session.getId(), new BrainstormDraft()));

        assertTrue(repository.deleteSession(session.getId()));

        try (Connection connection = database.getConnection(); Statement stmt = connection.createStatement()) {
            assertEquals(0, count(stmt, "SELECT COUNT(*) FROM brainstorm_messages"));
            assertEquals(0, count(stmt, "SELECT COUNT(*) FROM brainstorm_drafts"));
        }
    }

    @Test
    void storesNoMessageOrDraftOfABrainstormSessionThatDoesNotExist() throws Exception {
        BrainstormRepository repository = new BrainstormRepository(database);

        repository.addMessage("deleted-session", new BrainstormMessage(BrainstormMessage.ROLE_ASSISTANT, "late"));

        assertEquals(0, repository.addDraft("deleted-session", new BrainstormDraft()));
        try (Connection connection = database.getConnection(); Statement stmt = connection.createStatement()) {
            assertEquals(0, count(stmt, "SELECT COUNT(*) FROM brainstorm_messages"));
            assertEquals(0, count(stmt, "SELECT COUNT(*) FROM brainstorm_drafts"));
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
