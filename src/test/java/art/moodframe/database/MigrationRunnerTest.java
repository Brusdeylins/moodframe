package art.moodframe.database;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The migration files are split into statements without their comments, the text of strings stays. */
class MigrationRunnerTest {

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
}
