package art.moodframe.service.library;

import art.moodframe.database.DatabaseManager;
import art.moodframe.database.TestDatabase;
import art.moodframe.repository.ColorStyleRepository;
import art.moodframe.repository.FavoriteRepository;
import art.moodframe.repository.FrameRepository;
import art.moodframe.repository.MoodRepository;
import art.moodframe.repository.PromptBlockRepository;

/**
 * Library services for tests on the database a test opened with {@code DatabaseConfig.initialize}, on repositories of
 * their own, as {@code MoodframeApplication} wires them. Without {@link LibraryEvents} each reports to events of its
 * own; a test that watches the events passes its own.
 */
public final class TestLibraryServices {

    private TestLibraryServices() {
    }

    /** @return the database of the test */
    public static DatabaseManager database() {
        return TestDatabase.get();
    }

    /** @return the building blocks */
    public static PromptBlockService blocks() {
        return blocks(new LibraryEvents());
    }

    /**
     * @param events where the writes are reported
     * @return the building blocks
     */
    public static PromptBlockService blocks(LibraryEvents events) {
        return new PromptBlockService(new PromptBlockRepository(database()), events);
    }

    /** @return the palettes */
    public static ColorStyleService palettes() {
        return palettes(new LibraryEvents());
    }

    /**
     * @param events where the writes are reported
     * @return the palettes
     */
    public static ColorStyleService palettes(LibraryEvents events) {
        return new ColorStyleService(new ColorStyleRepository(database()), events);
    }

    /** @return the frames */
    public static FrameService frames() {
        return frames(new LibraryEvents());
    }

    /**
     * @param events where the writes are reported
     * @return the frames
     */
    public static FrameService frames(LibraryEvents events) {
        return new FrameService(new FrameRepository(database()), events);
    }

    /** @return the favorites */
    public static FavoriteService favorites() {
        return favorites(new LibraryEvents());
    }

    /**
     * @param events where a switched favorite is reported
     * @return the favorites
     */
    public static FavoriteService favorites(LibraryEvents events) {
        return new FavoriteService(new FavoriteRepository(database()), events);
    }

    /**
     * @param blocks the building blocks the moods refer to
     * @param colors the palettes the moods refer to
     * @return the moods
     */
    public static MoodService moods(PromptBlockService blocks, ColorStyleService colors) {
        return moods(blocks, colors, new LibraryEvents());
    }

    /**
     * @param blocks the building blocks the moods refer to
     * @param colors the palettes the moods refer to
     * @param events where the writes are reported
     * @return the moods
     */
    public static MoodService moods(PromptBlockService blocks, ColorStyleService colors, LibraryEvents events) {
        return new MoodService(new MoodRepository(database()), blocks, colors, events);
    }
}
