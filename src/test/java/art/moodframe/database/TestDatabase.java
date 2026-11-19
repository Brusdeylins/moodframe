package art.moodframe.database;

import art.moodframe.config.ServiceFactory;

/**
 * The database a test opened with {@link DatabaseConfig#initialize(String)}, for the repositories and services the
 * test creates itself; the application hands its database to every service instead.
 */
public final class TestDatabase {

    private TestDatabase() {
    }

    /**
     * Returns the database the test opened last.
     *
     * @return the database
     * @throws IllegalStateException if no test opened one
     */
    public static DatabaseManager get() {
        DatabaseManager database = ServiceFactory.getInstance().getService(DatabaseManager.class);
        if (database == null)
            throw new IllegalStateException("Database not initialized. Call DatabaseConfig.initialize() first.");
        return database;
    }
}
