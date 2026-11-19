package art.moodframe.database;

import java.nio.file.Path;
import java.sql.SQLException;

/**
 * The database a test opened with {@link #open(String)}, for the repositories and services the test creates itself;
 * the application hands its database to every service instead.
 */
public final class TestDatabase {

    /*  the database the test opened last  */
    private static volatile DatabaseManager database;

    private TestDatabase() {
    }

    /**
     * Opens a database as the application does, with its schema up to date, and keeps it as the database of the
     * test.
     *
     * @param databasePath the path of the database file
     * @return the database
     * @throws IllegalStateException if the database cannot be opened
     */
    public static DatabaseManager open(String databasePath) {
        DatabaseManager opened = new DatabaseManager(Path.of(databasePath));
        try {
            opened.initialize();
        }
        catch (SQLException e) {
            throw new IllegalStateException("Failed to open the test database " + databasePath, e);
        }
        database = opened;
        return opened;
    }

    /**
     * Returns the database the test opened last.
     *
     * @return the database
     * @throws IllegalStateException if no test opened one
     */
    public static DatabaseManager get() {
        DatabaseManager opened = database;
        if (opened == null)
            throw new IllegalStateException("Database not initialized. Call TestDatabase.open() first.");
        return opened;
    }
}
