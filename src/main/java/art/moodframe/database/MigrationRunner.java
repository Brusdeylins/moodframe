package art.moodframe.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Brings the schema of a database up to date: applies the migrations not yet recorded in
 * {@code schema_migrations}, each in its own transaction together with its record. A new migration must be listed in
 * {@link #MIGRATIONS}, a file alone is not applied.
 *
 * <p>The SQL files lie in {@code /db/migration}; a data migration that needs Java runs as code and carries the
 * prices and formulas it books with, frozen, instead of using the model of the application, so a later version books
 * the same. An applied migration is never changed, a new one gets the next version.</p>
 *
 * <p>Two instances of the application may start on the same library at once: each migration reads its record again
 * inside its IMMEDIATE transaction, under the write lock, and skips itself if the other instance applied it
 * meanwhile.</p>
 *
 * @since 2.1.3
 */
final class MigrationRunner {

    private static final Logger logger = LoggerFactory.getLogger(MigrationRunner.class);

    private static final String MIGRATIONS_PATH = "/db/migration";

    /*  the last line of a trigger, trimmed  */
    private static final Pattern TRIGGER_END = Pattern.compile("(?i)END\\s*;?");

    /** All migrations in the order of their versions. */
    private static final List<Migration> MIGRATIONS = List.of(
            new Migration("001", "Create_chat_tables"),
            new Migration("002", "Add_style_presets_and_config"),
            new Migration("003", "Fix_image_url_nullable"),
            new Migration("004", "Add_cost_to_chat_entries"),
            new Migration("005", "Add_image_error_tracking_and_parameters"),
            new Migration("006", "Add_soft_delete_and_cost_tracking"),
            new Migration("007", "Add_gemini_advanced_features"),
            new Migration("008", "Add_base_cost_column"),
            new Migration("009", "Add_style_to_chat_entries"),
            new Migration("010", "Add_color_styles"),
            new Migration("011", "Add_video_support"),
            new Migration("013", "Drop_task_presets"),
            new Migration("014", "Add_brainstorm_sessions"),
            new Migration("015", "Shift_brainstorm_steps"),
            new Migration("016", "Add_brainstorm_session_engine"),
            new Migration("017", "Add_prompt_blocks"),
            new Migration("018", "Add_deleted_at_to_color_styles"),
            new Migration("019", "Add_moods"),
            new Migration("020", "Add_mood_to_chat_entries"),
            new Migration("021", "Add_frames"),
            new Migration("022", "Drop_frame_models"),
            new Migration("023", "Add_frame_resolution"),
            new Migration("024", "Add_frame_to_chat_entries"),
            new Migration("025", "Number_chats"),
            new Migration("026", "Order_brainstorm_sessions"),
            new Migration("027", "Add_favorites"),
            new Migration("028", "Add_source_to_chat_entries"),
            new Migration("029", "Backfill_flux_costs", "FluxCostBackfill.java", FluxCostBackfill::apply),
            new Migration("030", "Correct_deleted_costs", "DeletedCostCorrection.java", DeletedCostCorrection::apply),
            new Migration("031", "Drop_chat_timestamp_triggers"),
            new Migration("032", "Add_saved_images"));

    private MigrationRunner() {
    }

    /**
     * Applies all pending migrations.
     *
     * @param connection the connection, with foreign keys on
     * @throws SQLException if a migration fails, its changes are rolled back
     */
    static void run(Connection connection) throws SQLException {
        createMigrationTable(connection);
        List<String> appliedMigrations = getAppliedMigrations(connection);

        // Check if this is a legacy database (has tables but no migration records)
        if (appliedMigrations.isEmpty() && hasExistingTables(connection)) {
            logger.info("Detected legacy database - applying all migrations, skipping what the schema already has");
            /*  The schema may have any part of a migration, also only its first statements: each statement whose
                object is already there is skipped, the others are applied  */
            for (Migration migration : MIGRATIONS) {
                if (applyMigration(connection, migration, true))
                    logger.info("Migration {} applied to legacy database", migration.version());
            }
            return;
        }

        // Repair BEFORE the normal loop: a partially applied migration would crash it on "duplicate column".
        // Only when the base table already exists (not on fresh install).
        if (tableExists(connection, "generated_images")) {
            repairIncorrectlySkippedMigrations(connection);
            appliedMigrations = getAppliedMigrations(connection);
        }

        for (Migration migration : MIGRATIONS) {
            if (!appliedMigrations.contains(migration.version())) {
                logger.info("Applying migration: {}", migration.fileName());
                if (applyMigration(connection, migration, false))
                    logger.info("Migration {} applied successfully", migration.version());
            }
        }
    }

    /**
     * Returns true if the given table exists in the database.
     *
     * @throws SQLException if the schema cannot be read; a failure is no answer
     */
    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    /*  True if SQLite refused a statement because the table, index, trigger or column it creates is already in the
        schema; a failed constraint is no such case, the rows may differ from those of the migration  */
    private static boolean isAlreadyReflected(SQLException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        return message.contains("already exists") || message.contains("duplicate column name");
    }

    /*  The columns of V011, video support, with their definitions as V011 adds them  */
    private static final String[][] V011_COLUMNS = {
            {"is_video", "BOOLEAN NOT NULL DEFAULT FALSE"},
            {"video_duration_seconds", "INTEGER DEFAULT 0"},
            {"video_resolution", "TEXT"},
            {"has_audio", "BOOLEAN NOT NULL DEFAULT FALSE"},
            {"negative_prompt", "TEXT"}};

    /**
     * Detects and repairs migrations that were recorded in schema_migrations but whose schema changes were never
     * applied, as the former legacy detection could mark V011 (video support) as applied without running it.
     *
     * <p>The repair adds only the columns that are missing, so it can run again; it runs in one transaction with
     * the record of V011. Like the legacy path, a real failure (disk full, lock) surfaces and rolls the repair
     * back, instead of leaving a half repaired schema behind.</p>
     *
     * @param connection the database connection
     * @throws SQLException if the schema cannot be read or the repair fails
     */
    static void repairIncorrectlySkippedMigrations(Connection connection) throws SQLException {
        List<String> missing = new ArrayList<>();
        for (String[] column : V011_COLUMNS)
            if (!columnExists(connection, "generated_images", column[0]))
                missing.add(column[0]);
        if (missing.isEmpty())
            return;

        logger.warn("Schema inconsistency: V011 columns {} missing – repairing them", missing);
        DatabaseManager.inTransaction(connection, (Connection transaction) -> {

            /*  read again under the write lock, another instance may have repaired them meanwhile  */
            try (Statement stmt = transaction.createStatement()) {
                for (String[] column : V011_COLUMNS)
                    if (!columnExists(transaction, "generated_images", column[0]))
                        stmt.execute("ALTER TABLE generated_images ADD COLUMN " + column[0] + " " + column[1]);
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_generated_images_is_video ON generated_images(is_video)");
            }
            if (!getAppliedMigrations(transaction).contains("011"))
                recordMigration(transaction, MIGRATIONS.stream()
                        .filter((Migration m) -> "011".equals(m.version())).findFirst().orElseThrow());
            return null;
        });
        logger.info("V011 repair complete – video support schema restored");
    }

    /**
     * Checks whether a specific column exists in a table.
     *
     * @param connection the database connection
     * @param tableName  the table to check
     * @param columnName the column to look for
     * @return true if the column exists
     * @throws SQLException if the schema cannot be read; a failure is no answer
     */
    private static boolean columnExists(Connection connection, String tableName, String columnName)
            throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }

    /**
     * Checks if the database has existing tables (indicating a legacy database).
     *
     * @param connection the database connection
     * @return true if tables exist
     * @throws SQLException if query fails
     */
    private static boolean hasExistingTables(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'schema_migrations'";

        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /**
     * Creates the migration tracking table.
     *
     * @param connection the database connection
     * @throws SQLException if table creation fails
     */
    private static void createMigrationTable(Connection connection) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version TEXT PRIMARY KEY,
                    description TEXT NOT NULL,
                    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * Gets the list of applied migrations.
     *
     * @param connection the database connection
     * @return list of applied migration versions
     * @throws SQLException if query fails
     */
    private static List<String> getAppliedMigrations(Connection connection) throws SQLException {
        List<String> migrations = new ArrayList<>();
        String sql = "SELECT version FROM schema_migrations ORDER BY version";

        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                migrations.add(rs.getString("version"));
            }
        }

        return migrations;
    }

    /**
     * Applies a single migration and records it in the same transaction, so a migration is never applied without its
     * record or recorded without its changes.
     *
     * <p>On a legacy database, a statement whose table, index, trigger or column is already in the schema is
     * skipped and the others are applied; the migration is recorded once each statement is applied or skipped so.
     * Any other failure rolls back the whole migration and surfaces.</p>
     *
     * @param connection the database connection
     * @param migration  the migration to apply
     * @param legacy     whether the database is a legacy one, whose schema may already have parts of the migration
     * @return true if it was applied now, false if it was recorded already (by another instance of the application)
     * @throws SQLException if migration fails
     */
    private static boolean applyMigration(Connection connection, Migration migration, boolean legacy)
            throws SQLException {
        /*  A migration in code has no SQL file, it runs in the same transaction  */
        List<String> statements = parseSqlStatements(
                migration.step() == null ? loadMigrationSql(migration.fileName()) : "");

        return DatabaseManager.inTransaction(connection, (Connection transaction) -> {
            if (getAppliedMigrations(transaction).contains(migration.version())) {
                logger.info("Migration {} was applied meanwhile by another instance", migration.version());
                return false;
            }
            try (Statement stmt = transaction.createStatement()) {
                for (String statement : statements) {
                    if (legacy)
                        executeUnlessReflected(transaction, stmt, migration, statement);
                    else
                        stmt.execute(statement);
                }
            }
            if (migration.step() != null)
                migration.step().apply(transaction);
            recordMigration(transaction, migration);
            return true;
        });
    }

    /*  Runs a statement of a migration on a legacy database, or skips it if its object is already in the schema; it
        runs in a savepoint, so a skipped statement leaves nothing behind  */
    private static void executeUnlessReflected(Connection connection, Statement stmt, Migration migration,
            String statement) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        try {
            stmt.execute(statement);
        } catch (SQLException e) {
            if (!isAlreadyReflected(e))
                throw e;
            connection.rollback(savepoint);
            logger.info("Migration {}: statement already reflected in schema (legacy): {}", migration.version(),
                    e.getMessage());
        }
        connection.releaseSavepoint(savepoint);
    }

    /**
     * Splits the content of a migration file into its statements, without the {@code --} comments.
     *
     * <p>The lines of a statement are trimmed and joined, so the schema keeps the text it always had. A trigger
     * starts with a line {@code CREATE TRIGGER} and ends on a line {@code END} or {@code END;}, in any case; the
     * statements of its body end with {@code ;} and stay inside it.</p>
     *
     * @param migrationSql the complete migration SQL
     * @return the statements, none empty
     */
    static List<String> parseSqlStatements(String migrationSql) {
        List<String> statements = new ArrayList<>();
        String cleanSql = stripComments(migrationSql.replace("\r\n", "\n"));

        StringBuilder currentStatement = new StringBuilder();
        boolean inTrigger = false;

        for (String line : cleanSql.split("\n")) {
            line = line.trim();
            if (line.isEmpty())
                continue;

            if (line.toUpperCase().startsWith("CREATE TRIGGER")) {
                inTrigger = true;
                currentStatement.append(line).append("\n");
            } else if (inTrigger && TRIGGER_END.matcher(line).matches()) {
                currentStatement.append("END;");
                statements.add(currentStatement.toString().trim());
                currentStatement.setLength(0);
                inTrigger = false;
            } else if (inTrigger) {
                currentStatement.append(line).append("\n");
            } else {
                currentStatement.append(line).append(" ");
                if (line.endsWith(";") && !isInsideQuotes(line, line.length() - 1)) {
                    statements.add(currentStatement.toString().trim());
                    currentStatement.setLength(0);
                }
            }
        }

        String remaining = currentStatement.toString().trim();
        if (!remaining.isEmpty())
            statements.add(remaining);
        return statements;
    }

    /**
     * Removes the {@code --} comments up to the end of their lines; inside a string or quoted name they are text.
     *
     * @param sql the SQL with comments
     * @return the SQL without them
     */
    static String stripComments(String sql) {
        StringBuilder result = new StringBuilder(sql.length());
        char quote = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (quote != 0) {
                /*  a doubled quote inside closes and opens again, which keeps the state  */
                if (c == quote)
                    quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                while (i + 1 < sql.length() && sql.charAt(i + 1) != '\n')
                    i++;
                continue;
            }
            result.append(c);
        }
        return result.toString();
    }

    /*  whether a string or quoted name of the line is open at the position  */
    private static boolean isInsideQuotes(String line, int position) {
        char quote = 0;
        for (int i = 0; i < position; i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote)
                    quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
            }
        }
        return quote != 0;
    }

    /**
     * Records a migration as applied.
     *
     * @param connection the database connection
     * @param migration  the migration that was applied
     * @throws SQLException if recording fails
     */
    private static void recordMigration(Connection connection, Migration migration) throws SQLException {
        String sql = "INSERT INTO schema_migrations (version, description) VALUES (?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, migration.version());
            stmt.setString(2, migration.description());
            stmt.executeUpdate();
        }
    }

    /**
     * Loads migration SQL from resources.
     *
     * @param fileName the migration file name
     * @return the migration SQL content
     * @throws SQLException if file cannot be read
     */
    private static String loadMigrationSql(String fileName) throws SQLException {
        String resourcePath = MIGRATIONS_PATH + "/" + fileName;
        try (InputStream resource = MigrationRunner.class.getResourceAsStream(resourcePath)) {
            if (resource == null) {
                throw new SQLException("Migration file not found: " + resourcePath);
            }
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("Failed to read migration file: " + fileName, e);
        }
    }

    /**
     * A migration done in code instead of an SQL file, for data that needs Java (prices, formulas); it carries what it
     * needs itself and uses no model of the application.
     */
    @FunctionalInterface
    private interface Step {
        void apply(Connection connection) throws SQLException;
    }

    /**
     * A database migration: an SQL file or a step in code.
     *
     * @param version     the version, three digits
     * @param description the description recorded with the version
     * @param fileName    the SQL file, or the class of the step
     * @param step        the step in code, null for an SQL file
     */
    private record Migration(String version, String description, String fileName, Step step) {

        Migration(String version, String description) {
            this(version, description, "V" + version + "__" + description + ".sql", null);
        }
    }
}
