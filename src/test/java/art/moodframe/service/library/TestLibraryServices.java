package art.moodframe.service.library;

import art.moodframe.database.DatabaseConfig;
import art.moodframe.database.DatabaseManager;

/**
 * Library services for tests on the database a test opened with {@code DatabaseConfig.initialize}, each reporting to
 * events of its own; a test that watches the events creates the service with its {@link LibraryEvents} itself.
 */
public final class TestLibraryServices {

    private TestLibraryServices() {
    }

    /** @return the database of the test */
    public static DatabaseManager database() {
        return DatabaseConfig.getDatabase();
    }

    /** @return the building blocks */
    public static PromptBlockService blocks() {
        return new PromptBlockService(database(), new LibraryEvents());
    }

    /** @return the palettes */
    public static ColorStyleService palettes() {
        return new ColorStyleService(database(), new LibraryEvents());
    }

    /** @return the frames */
    public static FrameService frames() {
        return new FrameService(database(), new LibraryEvents());
    }

    /** @return the favorites */
    public static FavoriteService favorites() {
        return new FavoriteService(database(), new LibraryEvents());
    }

    /**
     * @param blocks the building blocks the moods refer to
     * @param colors the palettes the moods refer to
     * @return the moods
     */
    public static MoodService moods(PromptBlockService blocks, ColorStyleService colors) {
        return new MoodService(database(), blocks, colors, new LibraryEvents());
    }
}
