package art.moodframe.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * The SQLite database of the library: the one factory of its connections, and its schema (see
 * {@link #initialize()}).
 *
 * <p>Every connection waits up to 5 s for the locks of other connections and enforces the foreign keys, so a
 * {@code ON DELETE CASCADE} of the schema applies to every repository. A transaction ({@code setAutoCommit(false)},
 * {@link #inTransaction}) begins IMMEDIATE: it takes the write lock at its start, so a transaction that reads before
 * it writes waits for another writer instead of failing with {@code SQLITE_BUSY_SNAPSHOT} on a stale snapshot. A
 * statement in auto-commit mode reads without a lock, so readers are not serialized.</p>
 *
 * @since 1.0.0
 */
public class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    /*  how long a connection waits for the lock of another one instead of failing at once with SQLITE_BUSY  */
    private static final String BUSY_TIMEOUT_MILLIS = "5000";

    private final Path databasePath;
    private final String connectionUrl;

    /**
     * Creates the database manager of a database file.
     *
     * @param databasePath the path to the database file
     */
    public DatabaseManager(Path databasePath) {
        this.databasePath = databasePath;
        this.connectionUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
        logger.debug("Database manager initialized with path: {}", databasePath);
    }

    /**
     * Initializes the database by creating the directory structure and running
     * migrations.
     *
     * @throws SQLException if database operations fail
     */
    public void initialize() throws SQLException {
        logger.debug("Initializing database...");

        try {
            // Create database directory if it doesn't exist
            Files.createDirectories(databasePath.getParent());

            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            boolean isNewDatabase = !Files.exists(databasePath);
            if (isNewDatabase) {
                logger.debug("Creating new database at: {}", databasePath);
            }

            /*  In WAL mode, readers and a writer do not wait for each other; the mode stays with the file  */
            try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode = WAL");
            }

            try (Connection connection = getConnection()) {
                MigrationRunner.run(connection);
            }

            logger.debug("Database initialization completed successfully");

        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        } catch (IOException e) {
            throw new SQLException("Failed to create database directory", e);
        }
    }

    /**
     * Gets a new database connection, which waits up to 5 s for the locks of other connections and enforces the
     * foreign keys. The caller closes it.
     *
     * @return a new database connection
     * @throws SQLException if connection fails
     */
    public Connection getConnection() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("busy_timeout", BUSY_TIMEOUT_MILLIS);
        properties.setProperty("foreign_keys", "true");
        properties.setProperty("transaction_mode", "IMMEDIATE");
        return DriverManager.getConnection(connectionUrl, properties);
    }

    /**
     * Runs a write transaction on a new connection: it begins IMMEDIATE, so what the work reads stays valid until it
     * commits. The work is committed if it returns and rolled back if it throws.
     *
     * @param work the reads and writes of the transaction
     * @param <T>  the result of the work
     * @return what the work returned
     * @throws SQLException if the work or the commit fails; nothing of the work is stored then
     */
    public <T> T inTransaction(Transaction<T> work) throws SQLException {
        try (Connection connection = getConnection()) {
            return inTransaction(connection, work);
        }
    }

    /**
     * Runs a write transaction on a connection the caller holds, as {@link #inTransaction(Transaction)} does; the
     * connection is in auto-commit mode again afterwards.
     *
     * @param connection the connection, in auto-commit mode
     * @param work       the reads and writes of the transaction
     * @param <T>        the result of the work
     * @return what the work returned
     * @throws SQLException if the work or the commit fails; nothing of the work is stored then
     */
    static <T> T inTransaction(Connection connection, Transaction<T> work) throws SQLException {
        connection.setAutoCommit(false);
        T result;
        try {
            result = work.run(connection);
        } catch (SQLException | RuntimeException e) {
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException rollback) {
                e.addSuppressed(rollback);
            }
            throw e;
        }
        /*  commits without commit(), which would begin the next transaction and take the write lock again;
            if it fails, closing the connection rolls back  */
        connection.setAutoCommit(true);
        return result;
    }

    /**
     * The work of a transaction (see {@link #inTransaction}).
     *
     * @param <T> its result
     */
    @FunctionalInterface
    public interface Transaction<T> {

        /**
         * Reads and writes in the transaction.
         *
         * @param connection the connection of the transaction; the work neither commits nor closes it
         * @return the result
         * @throws SQLException if a statement fails, the transaction is rolled back
         */
        T run(Connection connection) throws SQLException;
    }
}
