package art.moodframe.database;

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
     * Opens a database as the application does ({@link DatabaseConfig#initialize(String)}) and keeps it as the
     * database of the test.
     *
     * @param databasePath the path of the database file
     * @return the database
     */
    public static DatabaseManager open(String databasePath) {
        database = DatabaseConfig.initialize(databasePath);
        return database;
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
