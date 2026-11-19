package art.moodframe.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The migration files are split into statements without their comments, the text of strings stays; a migration and
 * its record are one transaction, and all libraries end with the same triggers.
 */
class MigrationRunnerTest {

    /*  the triggers every library has after all migrations: the favorites cleanup of V027  */
    private static final List<String> TRIGGERS = List.of("delete_favorite_after_block_delete",
            "delete_favorite_after_color_delete", "delete_favorite_after_frame_delete",
            "delete_favorite_after_mood_delete");

    @TempDir
    Path tempDir;

    @Test
    void keepsTwoDashesInsideAStringOrQuotedName() {
        String sql = """
                -- Migration: a comment with an apostrophe, it's ignored
                UPDATE moods SET name = 'Before -- after' WHERE id = 'a--b'; -- the rest goes
                UPDATE frames SET name = "x--y";
                """;

        assertEquals(List.of("UPDATE moods SET name = 'Before -- after' WHERE id = 'a--b';",
                "UPDATE frames SET name = \"x--y\";"), MigrationRunner.parseSqlStatements(sql));
    }

    @Test
    void keepsADoubledQuoteInsideAString() {
        assertEquals("SELECT 'it''s -- here' AS a \nSELECT 2",
                MigrationRunner.stripComments("SELECT 'it''s -- here' AS a -- x\nSELECT 2"));
    }

    @Test
    void joinsTheLinesOfAStatementAndDropsCommentLinesAndInlineComments() {
        String sql = """
                CREATE TABLE IF NOT EXISTS color_styles (
                    id TEXT PRIMARY KEY,
                    color1 TEXT,           -- Hex color code (e.g., "#48BDFF")
                    name TEXT NOT NULL
                );

                -- Index
                CREATE INDEX idx ON color_styles(name);
                """;

        assertEquals(List.of("CREATE TABLE IF NOT EXISTS color_styles ( id TEXT PRIMARY KEY, color1 TEXT, "
                + "name TEXT NOT NULL );", "CREATE INDEX idx ON color_styles(name);"),
                MigrationRunner.parseSqlStatements(sql));
    }

    @Test
    void endsATriggerOnABareEndLine() {
        String sql = """
                CREATE TRIGGER IF NOT EXISTS delete_favorite_after_mood_delete
                AFTER DELETE ON moods
                BEGIN
                    DELETE FROM favorites WHERE kind = 'MOOD' AND entry_id = OLD.id;
                END

                CREATE INDEX idx ON favorites(kind);
                """;

        assertEquals(List.of("""
                CREATE TRIGGER IF NOT EXISTS delete_favorite_after_mood_delete
                AFTER DELETE ON moods
                BEGIN
                DELETE FROM favorites WHERE kind = 'MOOD' AND entry_id = OLD.id;
                END;""", "CREATE INDEX idx ON favorites(kind);"), MigrationRunner.parseSqlStatements(sql));
    }

    @Test
    void endsATriggerOnAnEndLineWithSemicolonInAnyCase() {
        String sql = """
                CREATE TRIGGER first
                AFTER INSERT ON chat_entries
                BEGIN
                    UPDATE chats SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.chat_id;
                END;

                CREATE TRIGGER second
                AFTER DELETE ON chat_entries
                begin
                    UPDATE chats SET updated_at = CURRENT_TIMESTAMP WHERE id = OLD.chat_id;
                end ;\t

                CREATE INDEX idx ON chats(name);
                """;

        assertEquals(List.of("""
                CREATE TRIGGER first
                AFTER INSERT ON chat_entries
                BEGIN
                UPDATE chats SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.chat_id;
                END;""", """
                CREATE TRIGGER second
                AFTER DELETE ON chat_entries
                begin
                UPDATE chats SET updated_at = CURRENT_TIMESTAMP WHERE id = OLD.chat_id;
                END;""", "CREATE INDEX idx ON chats(name);"), MigrationRunner.parseSqlStatements(sql));
    }

    @Test
    void aNewLibraryHasOnlyTheFavoriteTriggersAndAllMigrationsRecorded() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("new.db"));
        database.initialize();

        try (Connection connection = database.getConnection()) {
            assertEquals(TRIGGERS, triggers(connection));
            assertTrue(versions(connection).contains("031"));
        }
    }

    @Test
    void dropsTheTriggerOfALibraryCreatedBeforeAndConvertsTheTimesItWrote() throws Exception {
        Path path = tempDir.resolve("before-v031.db");
        DatabaseManager database = new DatabaseManager(path);
        database.initialize();
        LocalDateTime local = LocalDateTime.now().minusDays(2).truncatedTo(ChronoUnit.SECONDS);
        String utcText = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .format(local.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC));

        /*  A library created by 2.1.2: the first trigger of V001 and the UTC text it wrote; a chat of the code  */
        try (Connection connection = database.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TRIGGER update_chat_timestamp_after_entry_insert
                    AFTER INSERT ON chat_entries
                    BEGIN
                    UPDATE chats SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.chat_id;
                    END;""");
            stmt.executeUpdate("INSERT INTO chats (id, name, updated_at) VALUES ('trigger', 'T', '" + utcText + "')");
            stmt.executeUpdate("DELETE FROM schema_migrations WHERE version = '031'");
        }
        try (Connection connection = database.getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "INSERT INTO chats (id, name, updated_at) VALUES ('code', 'C', ?)")) {
            stmt.setTimestamp(1, Timestamp.valueOf(local));
            stmt.executeUpdate();
        }

        new DatabaseManager(path).initialize();

        try (Connection connection = database.getConnection(); Statement stmt = connection.createStatement()) {
            assertEquals(TRIGGERS, triggers(connection));
            for (String id : List.of("trigger", "code"))
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT typeof(updated_at), updated_at FROM chats WHERE id = '" + id + "'")) {
                    assertTrue(rs.next());
                    assertEquals("integer", rs.getString(1));
                    assertEquals(local, rs.getTimestamp(2).toLocalDateTime(), id);
                }
        }
    }

    @Test
    void aMigrationWhoseRecordFailsIsRolledBackWithIt() throws Exception {
        Path path = tempDir.resolve("record-fails.db");
        DatabaseManager database = new DatabaseManager(path);
        database.initialize();
        try (Connection connection = database.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TRIGGER update_chat_timestamp_after_entry_insert
                    AFTER INSERT ON chat_entries
                    BEGIN
                    UPDATE chats SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.chat_id;
                    END;""");
            stmt.executeUpdate("DELETE FROM schema_migrations WHERE version = '031'");
            stmt.executeUpdate("""
                    CREATE TRIGGER refuse_record
                    BEFORE INSERT ON schema_migrations WHEN NEW.version = '031'
                    BEGIN
                    SELECT RAISE(ABORT, 'record refused');
                    END;""");
        }

        assertThrows(SQLException.class, () -> new DatabaseManager(path).initialize());
        try (Connection connection = database.getConnection()) {
            assertTrue(triggers(connection).contains("update_chat_timestamp_after_entry_insert"),
                    "the migration is not applied without its record");
            assertFalse(versions(connection).contains("031"));
        }

        try (Connection connection = database.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DROP TRIGGER refuse_record");
        }
        new DatabaseManager(path).initialize();
        try (Connection connection = database.getConnection()) {
            assertEquals(TRIGGERS, triggers(connection));
            assertTrue(versions(connection).contains("031"));
        }
    }

    @Test
    void repairsTheMissingVideoColumnsOfARecordedV011AgainAndAgain() throws Exception {
        Path path = tempDir.resolve("v011.db");
        DatabaseManager database = new DatabaseManager(path);
        database.initialize();
        try (Connection connection = database.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DROP INDEX idx_generated_images_is_video");
            stmt.executeUpdate("ALTER TABLE generated_images DROP COLUMN video_resolution");
            stmt.executeUpdate("ALTER TABLE generated_images DROP COLUMN negative_prompt");
        }

        new DatabaseManager(path).initialize();
        new DatabaseManager(path).initialize();

        try (Connection connection = database.getConnection()) {
            List<String> columns = names(connection, "SELECT name FROM pragma_table_info('generated_images')");
            assertTrue(columns.containsAll(List.of("is_video", "video_resolution", "negative_prompt")));
            assertEquals(List.of("idx_generated_images_is_video"), names(connection,
                    "SELECT name FROM sqlite_master WHERE name = 'idx_generated_images_is_video'"));
            assertEquals(1, versions(connection).stream().filter("011"::equals).count());
        }
    }

    @Test
    void aFailedRepairSurfacesAndLeavesTheSchemaAsItWas() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v011-fails.db"));
        database.initialize();
        try (Connection connection = database.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE generated_images DROP COLUMN video_resolution");
            stmt.executeUpdate("ALTER TABLE generated_images DROP COLUMN negative_prompt");

            /*  the disk refuses every write: the repair must not log and go on  */
            stmt.execute("PRAGMA query_only = ON");
            assertThrows(SQLException.class, () -> MigrationRunner.repairIncorrectlySkippedMigrations(connection));
            stmt.execute("PRAGMA query_only = OFF");
            assertFalse(names(connection, "SELECT name FROM pragma_table_info('generated_images')")
                    .contains("video_resolution"));
        }
    }

    private static List<String> triggers(Connection connection) throws SQLException {
        return names(connection, "SELECT name FROM sqlite_master WHERE type = 'trigger' ORDER BY name");
    }

    private static List<String> versions(Connection connection) throws SQLException {
        return names(connection, "SELECT version FROM schema_migrations ORDER BY version");
    }

    private static List<String> names(Connection connection, String sql) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next())
                names.add(rs.getString(1));
        }
        return names;
    }
}
