package art.moodframe.database;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Database configuration and initialization for SQLite.
 * 
 * <p>
 * This class handles SQLite database setup, schema creation, and connection
 * management.
 * It creates a comprehensive database schema for storing chat sessions,
 * messages, generated
 * images, and configuration data.
 * </p>
 * 
 * <p>
 * <strong>Database Schema:</strong>
 * </p>
 * <ul>
 * <li>{@code chats} - Chat session metadata</li>
 * <li>{@code chat_entries} - Individual messages and prompts</li>
 * <li>{@code generated_images} - Generated image metadata and file paths</li>
 * <li>{@code configuration} - Application configuration key-value pairs</li>
 * <li>{@code api_requests} - API request/response logging for monitoring</li>
 * </ul>
 * 
 * <p>
 * <strong>Features:</strong>
 * </p>
 * <ul>
 * <li>Automatic schema creation and migration</li>
 * <li>Foreign key constraint enforcement</li>
 * <li>Optimized indexes for query performance</li>
 * <li>Connection pooling through JDBC</li>
 * </ul>
 * 
 * <p>
 * <strong>External References:</strong>
 * </p>
 * <ul>
 * <li><a href="https://www.sqlite.org/docs.html">SQLite Documentation</a></li>
 * <li><a href="https://github.com/xerial/sqlite-jdbc">SQLite JDBC
 * Driver</a></li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    private static String databaseUrl;

    /**
     * Initializes the database connection and schema.
     * 
     * <p>
     * This method performs the following operations:
     * </p>
     * <ol>
     * <li>Creates the database directory if it doesn't exist</li>
     * <li>Establishes the SQLite connection URL</li>
     * <li>Initializes the database schema with all required tables</li>
     * <li>Sets up indexes for optimal query performance</li>
     * </ol>
     * 
     * @param databasePath the absolute path to the SQLite database file
     * @throws RuntimeException if database initialization fails
     * @see <a href="https://www.sqlite.org/pragma.html#pragma_foreign_keys">SQLite
     *      Foreign Keys</a>
     * @since 1.0.0
     */
    public static void initialize(String databasePath) {
        try {
            // Ensure the directory exists
            Path dbPath = Paths.get(databasePath);
            Files.createDirectories(dbPath.getParent());

            databaseUrl = "jdbc:sqlite:" + databasePath;

            // Initialisiere Schema
            initializeSchema(databaseUrl);

            ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
            if (config != null && config.getLoggingEnabled()) {
                logger.info("Database initialized: {}", databasePath);
            }

        } catch (Exception e) {
            logger.error("Database initialization failed", e);
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    /**
     * Uses DatabaseManager to initialize schema via migrations.
     * 
     * @param url the JDBC URL for the SQLite database
     * @throws RuntimeException if schema creation fails
     * @see art.moodframe.database.DatabaseManager
     * @since 1.0.0
     * @deprecated Use {@link art.moodframe.database.DatabaseManager} directly for
     *             proper migration handling
     */
    @Deprecated
    private static void initializeSchema(String url) {
        try {
            // Extract database path from JDBC URL
            String dbPath = url.replace("jdbc:sqlite:", "");
            Path databasePath = Paths.get(dbPath);

            // Use DatabaseManager for proper migration handling
            art.moodframe.database.DatabaseManager dbManager = new art.moodframe.database.DatabaseManager(databasePath);
            dbManager.initialize();

            ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
            if (config != null && config.getLoggingEnabled()) {
                logger.info("Database schema initialized via migrations");
            }

        } catch (Exception e) {
            logger.error("Failed to initialize database schema", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates and returns a new database connection.
     * 
     * <p>
     * <strong>Important:</strong> The caller is responsible for closing the
     * returned
     * connection to prevent resource leaks. Consider using try-with-resources:
     * </p>
     * 
     * <pre>{@code
     * try (Connection conn = DatabaseConfig.getConnection()) {
     *     // Use the connection
     * }
     * }</pre>
     * 
     * @return a new SQLite database connection
     * @throws SQLException          if the connection cannot be established
     * @throws IllegalStateException if the database has not been initialized
     * @see <a href=
     *      "https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/Connection.html">JDBC
     *      Connection API</a>
     * @since 1.0.0
     */
    public static Connection getConnection() throws SQLException {
        if (databaseUrl == null) {
            throw new IllegalStateException("Database not initialized. Call initialize() first.");
        }
        return DriverManager.getConnection(databaseUrl);
    }

    /**
     * Returns the JDBC URL for the SQLite database.
     * 
     * @return the database URL, or null if not initialized
     * @since 1.0.0
     */
    public static String getDatabaseUrl() {
        return databaseUrl;
    }

    /**
     * Closes the database configuration.
     * 
     * <p>
     * <strong>Warning:</strong> This method is intended for testing purposes only.
     * It resets the database configuration, which may cause application failures
     * if called in production code.
     * </p>
     * 
     * @since 1.0.0
     */
    public static void close() {
        databaseUrl = null;
    }
}