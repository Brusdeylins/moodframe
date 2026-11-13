package art.moodframe.database;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Database manager for SQLite database operations and migrations.
 * 
 * <p>
 * This class handles database initialization, connection management,
 * and migration execution for the MoodFrame application.
 * </p>
 * 
 * @since 1.0.0
 */
public class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    private static final String DATABASE_NAME = "moodframe.db";
    private static final String MIGRATIONS_PATH = "/db/migration";
    // private static final Pattern MIGRATION_PATTERN =
    // Pattern.compile("V(\\d+)__(.+)\\.sql");

    private final Path databasePath;
    private final String connectionUrl;
    private final ApplicationConfig config;

    /**
     * Creates a new database manager with the default database location.
     */
    public DatabaseManager() {
        this(getDefaultDatabasePath());
    }

    /**
     * Creates a new database manager with a custom database path.
     * 
     * @param databasePath the path to the database file
     */
    public DatabaseManager(Path databasePath) {
        this.databasePath = databasePath;
        this.connectionUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
        this.config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        if (config != null && config.getLoggingEnabled()) {
            logger.info("Database manager initialized with path: {}", databasePath);
        }
    }

    /**
     * Gets the default database path in the user's home directory.
     * 
     * @return the default database path
     */
    private static Path getDefaultDatabasePath() {
        String userHome = System.getProperty("user.home");
        Path appDir = Paths.get(userHome, ".moodframe");
        return appDir.resolve(DATABASE_NAME);
    }

    /**
     * Initializes the database by creating the directory structure and running
     * migrations.
     * 
     * @throws SQLException if database operations fail
     */
    public void initialize() throws SQLException {
        if (config != null && config.getLoggingEnabled()) {
            logger.info("Initializing database...");
        }

        try {
            // Create database directory if it doesn't exist
            Files.createDirectories(databasePath.getParent());

            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Create database connection and run migrations
            boolean isNewDatabase = !Files.exists(databasePath);
            if (isNewDatabase && config != null && config.getLoggingEnabled()) {
                logger.info("Creating new database at: {}", databasePath);
            }

            runMigrations();

            if (config != null && config.getLoggingEnabled()) {
                logger.info("Database initialization completed successfully");
            }

        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        } catch (IOException e) {
            throw new SQLException("Failed to create database directory", e);
        }
    }

    /**
     * Gets a new database connection.
     * 
     * @return a new database connection
     * @throws SQLException if connection fails
     */
    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(connectionUrl);

        // Enable foreign key constraints
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }

        return connection;
    }

    /**
     * Runs all pending database migrations.
     * 
     * @throws SQLException if migration fails
     */
    private void runMigrations() throws SQLException {
        try (Connection connection = getConnection()) {
            // Create migration tracking table if it doesn't exist
            createMigrationTable(connection);

            // Get applied migrations
            List<String> appliedMigrations = getAppliedMigrations(connection);

            // Get available migrations
            List<Migration> availableMigrations = getAvailableMigrations();

            // Check if this is a legacy database (has tables but no migration records)
            if (appliedMigrations.isEmpty() && hasExistingTables(connection)) {
                logger.info("Detected legacy database - applying all migrations with duplicate detection");
                // Try to run each migration. Early migrations may fail because the schema
                // already exists (expected for legacy databases). New migrations (like video
                // support) that don't exist yet will be applied successfully.
                for (Migration migration : availableMigrations) {
                    try {
                        applyMigration(connection, migration);
                        logger.info("Migration {} applied to legacy database", migration.getVersion());
                    } catch (SQLException e) {
                        // Only "already there" errors count as applied, a real failure (disk full, lock) must surface
                        if (!isAlreadyReflected(e))
                            throw e;

                        logger.info("Migration {} already reflected in schema (legacy): {}",
                                migration.getVersion(), e.getMessage());
                    }
                    recordMigration(connection, migration);
                }
                return;
            }

            // Repair BEFORE the normal loop.
            // If a migration was partially applied (e.g. some ALTER TABLE columns exist,
            // others don't), the normal loop would call applyMigration() and crash on
            // "duplicate column". Running repair first ensures every column is present
            // and the migration is recorded, so the normal loop skips it cleanly.
            // Guard: only run when the base table already exists (not on fresh install).
            if (tableExists(connection, "generated_images")) {
                repairIncorrectlySkippedMigrations(connection, availableMigrations);
                // Re-fetch so the normal loop sees any migrations repair just recorded.
                appliedMigrations = getAppliedMigrations(connection);
            }

            // Apply pending migrations
            for (Migration migration : availableMigrations) {
                if (!appliedMigrations.contains(migration.getVersion())) {
                    logger.info("Applying migration: {}", migration.getFileName());
                    applyMigration(connection, migration);
                    recordMigration(connection, migration);
                    logger.info("Migration {} applied successfully", migration.getVersion());
                }
            }
        }
    }

    /**
     * Returns true if the given table exists in the database.
     */
    private boolean tableExists(Connection connection, String tableName) {
        try {
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, tableName, null)) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.warn("Could not check table existence for {}: {}", tableName, e.getMessage());
            return false;
        }
    }

    /** True if SQLite refused a statement because its change is already in the schema. */
    private static boolean isAlreadyReflected(SQLException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        return message.contains("already exists") || message.contains("duplicate column name")
                || message.contains("UNIQUE constraint failed");
    }

    /**
     * Detects and repairs migrations that were recorded in schema_migrations
     * but whose schema changes were never actually applied to the database.
     * This can happen when the legacy-database detection incorrectly marks
     * new migrations (e.g. V011 video support) as applied without running them.
     *
     * @param connection         the database connection
     * @param availableMigrations all known migrations, used to locate the file
     */
    private void repairIncorrectlySkippedMigrations(Connection connection,
            List<Migration> availableMigrations) {
        // V011 – video support: check if any of its columns are missing.
        // SQLite does not roll back ALTER TABLE inside a transaction, so V011 may have
        // been partially applied (some columns exist, others don't). We add each
        // column individually and silently skip ones that already exist.
        boolean anyV011ColumnMissing =
                !columnExists(connection, "generated_images", "is_video")
                || !columnExists(connection, "generated_images", "video_duration_seconds")
                || !columnExists(connection, "generated_images", "video_resolution")
                || !columnExists(connection, "generated_images", "has_audio")
                || !columnExists(connection, "generated_images", "negative_prompt");

        if (anyV011ColumnMissing) {
            logger.warn("Schema inconsistency: one or more V011 columns missing – repairing column by column");
            String[] v011Statements = {
                "ALTER TABLE generated_images ADD COLUMN is_video BOOLEAN NOT NULL DEFAULT FALSE",
                "ALTER TABLE generated_images ADD COLUMN video_duration_seconds INTEGER DEFAULT 0",
                "ALTER TABLE generated_images ADD COLUMN video_resolution TEXT",
                "ALTER TABLE generated_images ADD COLUMN has_audio BOOLEAN NOT NULL DEFAULT FALSE",
                "ALTER TABLE generated_images ADD COLUMN negative_prompt TEXT",
                "CREATE INDEX IF NOT EXISTS idx_generated_images_is_video ON generated_images(is_video)"
            };
            for (String sql : v011Statements) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(sql);
                } catch (SQLException e) {
                    if (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate column")) {
                        logger.info("V011 repair: column already exists, skipping – {}", e.getMessage());
                    } else {
                        logger.error("V011 repair: unexpected error executing '{}': {}", sql, e.getMessage());
                    }
                }
            }
            // Ensure V011 is recorded as applied
            try {
                List<String> applied = getAppliedMigrations(connection);
                if (!applied.contains("011")) {
                    Migration v011 = availableMigrations.stream()
                            .filter(m -> "011".equals(m.getVersion()))
                            .findFirst()
                            .orElseGet(() -> new Migration("011", "Add_video_support",
                                    "V011__Add_video_support.sql"));
                    recordMigration(connection, v011);
                }
            } catch (SQLException e) {
                logger.error("V011 repair: could not record migration in schema_migrations", e);
            }
            logger.info("V011 repair complete – video support schema restored");
        }
    }

    /**
     * Checks whether a specific column exists in a table.
     *
     * @param connection the database connection
     * @param tableName  the table to check
     * @param columnName the column to look for
     * @return true if the column exists
     */
    private boolean columnExists(Connection connection, String tableName, String columnName) {
        try {
            java.sql.DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, tableName, columnName)) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.warn("Could not check column existence for {}.{}: {}", tableName, columnName,
                    e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the database has existing tables (indicating a legacy database).
     * 
     * @param connection the database connection
     * @return true if tables exist
     * @throws SQLException if query fails
     */
    private boolean hasExistingTables(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'schema_migrations'";

        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        }

        return false;
    }

    /**
     * Creates the migration tracking table.
     * 
     * @param connection the database connection
     * @throws SQLException if table creation fails
     */
    private void createMigrationTable(Connection connection) throws SQLException {
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
    private List<String> getAppliedMigrations(Connection connection) throws SQLException {
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
     * Gets the list of available migration files.
     * 
     * @return list of available migrations
     * @throws SQLException if migration files cannot be read
     */
    private List<Migration> getAvailableMigrations() throws SQLException {
        List<Migration> migrations = new ArrayList<>();

        try {
            // In a real application, you would scan the classpath for migration files
            // For now, we'll manually add the known migrations
            migrations.add(new Migration("001", "Create_chat_tables", "V001__Create_chat_tables.sql"));
            migrations.add(
                    new Migration("002", "Add_style_presets_and_config", "V002__Add_style_presets_and_config.sql"));
            migrations.add(new Migration("003", "Fix_image_url_nullable", "V003__Fix_image_url_nullable.sql"));
            migrations.add(new Migration("004", "Add_cost_to_chat_entries", "V004__Add_cost_to_chat_entries.sql"));
            migrations.add(new Migration("005", "Add_image_error_tracking_and_parameters",
                    "V005__Add_image_error_tracking_and_parameters.sql"));
            migrations.add(new Migration("006", "Add_soft_delete_and_cost_tracking",
                    "V006__Add_soft_delete_and_cost_tracking.sql"));
            migrations.add(new Migration("007", "Add_gemini_advanced_features",
                    "V007__Add_gemini_advanced_features.sql"));
            migrations.add(new Migration("008", "Add_base_cost_column",
                    "V008__Add_base_cost_column.sql"));
            migrations.add(new Migration("009", "Add_style_to_chat_entries",
                    "V009__Add_style_to_chat_entries.sql"));
            migrations.add(new Migration("010", "Add_color_styles",
                    "V010__Add_color_styles.sql"));
            migrations.add(new Migration("011", "Add_video_support",
                    "V011__Add_video_support.sql"));
            migrations.add(new Migration("013", "Drop_task_presets",
                    "V013__Drop_task_presets.sql"));
            migrations.add(new Migration("014", "Add_brainstorm_sessions",
                    "V014__Add_brainstorm_sessions.sql"));
            migrations.add(new Migration("015", "Shift_brainstorm_steps",
                    "V015__Shift_brainstorm_steps.sql"));
            migrations.add(new Migration("016", "Add_brainstorm_session_engine",
                    "V016__Add_brainstorm_session_engine.sql"));
            migrations.add(new Migration("017", "Add_prompt_blocks",
                    "V017__Add_prompt_blocks.sql"));
            migrations.add(new Migration("018", "Add_deleted_at_to_color_styles",
                    "V018__Add_deleted_at_to_color_styles.sql"));
            migrations.add(new Migration("019", "Add_moods",
                    "V019__Add_moods.sql"));
            migrations.add(new Migration("020", "Add_mood_to_chat_entries",
                    "V020__Add_mood_to_chat_entries.sql"));
            migrations.add(new Migration("021", "Add_frames",
                    "V021__Add_frames.sql"));
            migrations.add(new Migration("022", "Drop_frame_models",
                    "V022__Drop_frame_models.sql"));
            migrations.add(new Migration("023", "Add_frame_resolution",
                    "V023__Add_frame_resolution.sql"));
            migrations.add(new Migration("024", "Add_frame_to_chat_entries",
                    "V024__Add_frame_to_chat_entries.sql"));
            migrations.add(new Migration("025", "Number_chats",
                    "V025__Number_chats.sql"));
            migrations.add(new Migration("026", "Order_brainstorm_sessions",
                    "V026__Order_brainstorm_sessions.sql"));

            migrations.sort((a, b) -> a.getVersion().compareTo(b.getVersion()));

        } catch (Exception e) {
            throw new SQLException("Failed to load migration files", e);
        }

        return migrations;
    }

    /**
     * Applies a single migration.
     * 
     * @param connection the database connection
     * @param migration  the migration to apply
     * @throws SQLException if migration fails
     */
    private void applyMigration(Connection connection, Migration migration) throws SQLException {
        String migrationSql = loadMigrationSql(migration.getFileName());

        // Parse SQL statements properly handling triggers and multi-line statements
        List<String> statements = parseSqlStatements(migrationSql);

        connection.setAutoCommit(false);
        try (Statement stmt = connection.createStatement()) {
            for (String statement : statements) {
                if (!statement.trim().isEmpty() && !statement.trim().startsWith("--")) {
                    stmt.execute(statement);
                }
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Parses SQL statements from migration content, handling triggers and
     * multi-line statements.
     * 
     * @param migrationSql the complete migration SQL
     * @return list of individual SQL statements
     */
    private List<String> parseSqlStatements(String migrationSql) {
        List<String> statements = new ArrayList<>();

        // Remove comments and normalize line endings
        String cleanSql = migrationSql.replaceAll("--[^\r\n]*", "").replaceAll("\r\n", "\n");

        StringBuilder currentStatement = new StringBuilder();
        boolean inTrigger = false;
        String[] lines = cleanSql.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty())
                continue;

            // Check for trigger start
            if (line.toUpperCase().startsWith("CREATE TRIGGER")) {
                inTrigger = true;
                currentStatement.append(line).append("\n");
            }
            // Check for trigger end
            else if (inTrigger && line.toUpperCase().equals("END")) {
                currentStatement.append(line).append(";");
                statements.add(currentStatement.toString().trim());
                currentStatement.setLength(0);
                inTrigger = false;
            }
            // Inside trigger
            else if (inTrigger) {
                currentStatement.append(line).append("\n");
            }
            // Regular statement
            else {
                currentStatement.append(line).append(" ");

                // Check for statement end (semicolon not inside quotes)
                if (line.endsWith(";") && !isInsideQuotes(line, line.length() - 1)) {
                    statements.add(currentStatement.toString().trim());
                    currentStatement.setLength(0);
                }
            }
        }

        // Add remaining statement if any
        String remaining = currentStatement.toString().trim();
        if (!remaining.isEmpty()) {
            statements.add(remaining);
        }

        return statements;
    }

    /**
     * Checks if a character position is inside quotes.
     * 
     * @param text     the text to check
     * @param position the position to check
     * @return true if inside quotes
     */
    private boolean isInsideQuotes(String text, int position) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < position; i++) {
            char c = text.charAt(i);
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            }
        }

        return inSingleQuote || inDoubleQuote;
    }

    /**
     * Records a migration as applied.
     * 
     * @param connection the database connection
     * @param migration  the migration that was applied
     * @throws SQLException if recording fails
     */
    private void recordMigration(Connection connection, Migration migration) throws SQLException {
        String sql = "INSERT INTO schema_migrations (version, description) VALUES (?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, migration.getVersion());
            stmt.setString(2, migration.getDescription());
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
    private String loadMigrationSql(String fileName) throws SQLException {
        try {
            String resourcePath = MIGRATIONS_PATH + "/" + fileName;
            var resource = getClass().getResourceAsStream(resourcePath);

            if (resource == null) {
                throw new SQLException("Migration file not found: " + resourcePath);
            }

            return new String(resource.readAllBytes());

        } catch (IOException e) {
            throw new SQLException("Failed to read migration file: " + fileName, e);
        }
    }

    /**
     * Gets the database file path.
     * 
     * @return the database file path
     */
    public Path getDatabasePath() {
        return databasePath;
    }

    /**
     * Gets the JDBC connection URL.
     * 
     * @return the connection URL
     */
    public String getConnectionUrl() {
        return connectionUrl;
    }

    /**
     * Checks if the database exists.
     * 
     * @return true if the database file exists
     */
    public boolean databaseExists() {
        return Files.exists(databasePath);
    }

    /**
     * Represents a database migration.
     */
    private static class Migration {
        private final String version;
        private final String description;
        private final String fileName;

        public Migration(String version, String description, String fileName) {
            this.version = version;
            this.description = description;
            this.fileName = fileName;
        }

        public String getVersion() {
            return version;
        }

        public String getDescription() {
            return description;
        }

        public String getFileName() {
            return fileName;
        }
    }
}