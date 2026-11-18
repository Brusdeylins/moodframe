package art.moodframe.database;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Opens the database of the application at startup and makes it known in the {@link ServiceFactory}.
 *
 * <p>The application hands the {@link DatabaseManager} to the services it creates; the convenience constructors of
 * the services (tests, tools) take the registered one from {@link #getDatabase()}.</p>
 *
 * @since 1.0.0
 */
public final class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    private DatabaseConfig() {
    }

    /**
     * Opens the database, brings its schema up to date and registers it as the database of the application.
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
            ServiceFactory.getInstance().registerService(DatabaseManager.class, database);

            ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
            if (config != null && config.getLoggingEnabled()) {
                logger.info("Database initialized: {}", databasePath);
            }
            return database;

        } catch (Exception e) {
            logger.error("Database initialization failed", e);
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    /**
     * Returns the database of the application.
     *
     * @return the database opened by {@link #initialize(String)}
     * @throws IllegalStateException if the database has not been initialized
     * @since 2.1.3
     */
    public static DatabaseManager getDatabase() {
        DatabaseManager database = ServiceFactory.getInstance().getService(DatabaseManager.class);
        if (database == null) {
            throw new IllegalStateException("Database not initialized. Call initialize() first.");
        }
        return database;
    }
}
