package art.moodframe.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Opens the database of the application at startup.
 *
 * <p>The application hands the {@link DatabaseManager} to the repositories it creates; nothing looks it up.</p>
 *
 * @since 1.0.0
 */
public final class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    private DatabaseConfig() {
    }

    /**
     * Opens the database and brings its schema up to date.
     *
     * @param databasePath the absolute path to the SQLite database file
     * @return the database
     * @throws RuntimeException if database initialization fails
     * @since 1.0.0
     */
    public static DatabaseManager initialize(String databasePath) {
        try {
            DatabaseManager database = new DatabaseManager(Path.of(databasePath));
            database.initialize();

            logger.debug("Database initialized: {}", databasePath);
            return database;

        } catch (Exception e) {
            logger.error("Database initialization failed", e);
            throw new RuntimeException("Failed to initialize database", e);
        }
    }
}
