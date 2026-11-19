package art.moodframe.service.library;

import art.moodframe.database.DatabaseManager;
import art.moodframe.database.TestDatabase;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.Frame;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.repository.ColorStyleRepository;
import art.moodframe.repository.FavoriteRepository;
import art.moodframe.repository.FrameRepository;
import art.moodframe.repository.MoodRepository;
import art.moodframe.repository.PromptBlockRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The order of the lists with favorites, and the favorites in a fresh SQLite database. */
class FavoriteServiceTest {

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void createDatabase() {
        TestDatabase.open(tempDir.resolve("db").resolve("test.db").toString());
    }

    /** An entry of a list with groups: its name and the place of its group. */
    private record Entry(String name, int group) {
    }

    @Test
    void ordersTheFavoritesFirstThenTheOthersEachAlphabeticallyIgnoringCase() {
        List<String> names = List.of("delta", "Alpha", "charlie", "Bravo", "echo", "Äther");
        Set<String> favorites = Set.of("echo", "Bravo");

        assertEquals(List.of("Bravo", "echo", "Alpha", "Äther", "charlie", "delta"),
                FavoriteService.order(names, favorites::contains, (name) -> 0, (name) -> name));
        assertEquals(List.of("Alpha", "Äther", "Bravo", "charlie", "delta", "echo"),
                FavoriteService.order(names, (name) -> false, (name) -> 0, (name) -> name), "without favorites");
    }

    @Test
    void ordersTheOthersByTheirGroupsBehindTheFavoritesEachAlphabetically() {
        List<Entry> entries = List.of(new Entry("Watercolor", 1), new Entry("cinematic", 0), new Entry("Poster", 2),
                new Entry("anime", 1), new Entry("Analog", 0), new Entry("Collage", 2));
        Set<String> favorites = Set.of("Poster", "Watercolor");

        /*  a favorite shows in the favorites only, whatever its group  */
        assertEquals(List.of("Poster", "Watercolor", "Analog", "cinematic", "anime", "Collage"),
                FavoriteService.order(entries, (entry) -> favorites.contains(entry.name()), Entry::group, Entry::name)
                        .stream().map(Entry::name).toList());
    }

    @Test
    void keepsTheFavoritesOfEachKindAcrossARestart() {
        FavoriteService favorites = TestLibraryServices.favorites();
        assertFalse(favorites.isFavorite(FavoriteService.Kind.MOOD, "std:mood:noir"));
        assertTrue(favorites.toggle(FavoriteService.Kind.MOOD, "std:mood:noir"));
        assertTrue(favorites.toggle(FavoriteService.Kind.STYLE, "my-style"));
        assertTrue(favorites.toggle(FavoriteService.Kind.FRAME, "std:frame:social-post"));
        assertFalse(favorites.toggle(FavoriteService.Kind.FRAME, "std:frame:social-post"),
                "a second click takes it away");

        FavoriteService restarted = TestLibraryServices.favorites();
        assertTrue(restarted.isFavorite(FavoriteService.Kind.MOOD, "std:mood:noir"));
        assertTrue(restarted.isFavorite(FavoriteService.Kind.STYLE, "my-style"));
        assertFalse(restarted.isFavorite(FavoriteService.Kind.FRAME, "std:frame:social-post"));
        assertFalse(restarted.isFavorite(FavoriteService.Kind.LIGHT, "my-style"),
                "a favorite counts for its kind only");
        assertFalse(restarted.isFavorite(FavoriteService.Kind.MOOD, null), "\"None\" is no favorite");
    }

    @Test
    void dropsTheFavoriteOfAnEntryOfTheUserDeletedForGood() throws Exception {
        Mood mood = new Mood(null, "Favorite Mood");
        Frame frame = new Frame(null, "Favorite Frame");
        frame.setAspectRatio("1:1");
        PromptBlock light = new PromptBlock(null, PromptBlock.Type.LIGHT, "Favorite Light", "soft light", null, true);
        ColorStyle palette = new ColorStyle("Favorite Palette", List.of("#000000"));
        MoodRepository moods = new MoodRepository(TestDatabase.get());
        FrameRepository frames = new FrameRepository(TestDatabase.get());
        PromptBlockRepository blocks = new PromptBlockRepository(TestDatabase.get());
        ColorStyleRepository palettes = new ColorStyleRepository(TestDatabase.get());
        assertTrue(moods.save(mood));
        assertTrue(frames.save(frame));
        assertTrue(blocks.save(light));
        assertTrue(palettes.save(palette));

        FavoriteService favorites = TestLibraryServices.favorites();
        favorites.toggle(FavoriteService.Kind.MOOD, mood.getId());
        favorites.toggle(FavoriteService.Kind.FRAME, frame.getId());
        favorites.toggle(FavoriteService.Kind.LIGHT, light.getId());
        favorites.toggle(FavoriteService.Kind.COLOR, palette.getId());

        /*  in the trash, an entry keeps its favorite, so a restored one comes back as it was  */
        assertTrue(moods.moveToTrash(mood.getId()));
        assertTrue(TestLibraryServices.favorites().isFavorite(FavoriteService.Kind.MOOD, mood.getId()));

        assertTrue(frames.moveToTrash(frame.getId()));
        assertTrue(blocks.moveToTrash(light.getId()));
        assertTrue(palettes.moveToTrash(palette.getId()));
        assertTrue(moods.delete(mood.getId()));
        assertTrue(frames.delete(frame.getId()));
        assertTrue(blocks.delete(light.getId()));
        assertTrue(palettes.delete(palette.getId()));
        FavoriteService restarted = TestLibraryServices.favorites();
        assertFalse(restarted.isFavorite(FavoriteService.Kind.MOOD, mood.getId()));
        assertFalse(restarted.isFavorite(FavoriteService.Kind.FRAME, frame.getId()));
        assertFalse(restarted.isFavorite(FavoriteService.Kind.LIGHT, light.getId()));
        assertFalse(restarted.isFavorite(FavoriteService.Kind.COLOR, palette.getId()));
    }

    @Test
    void answersWhetherAFavoriteWhileItsWriteWaitsForTheDatabase() throws Exception {
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FavoriteService favorites = new FavoriteService(new FavoriteRepository(TestDatabase.get()) {
            @Override
            public boolean add(String kind, String id) throws SQLException {
                writing.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return super.add(kind, id);
            }
        }, new LibraryEvents());
        String id = "slow-" + System.nanoTime();
        ExecutorService toggler = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> toggled = toggler.submit(() -> favorites.toggle(FavoriteService.Kind.MOOD, id));
            assertTrue(writing.await(10, TimeUnit.SECONDS));

            /*  the write holds no lock that the readers take, and they see the switch at once  */
            assertTrue(CompletableFuture.supplyAsync(() -> favorites.isFavorite(FavoriteService.Kind.MOOD, id))
                    .get(2, TimeUnit.SECONDS));
            assertFalse(toggled.isDone());
            release.countDown();
            assertTrue(toggled.get(10, TimeUnit.SECONDS));
        }
        finally {
            release.countDown();
            toggler.shutdownNow();
        }
        assertTrue(TestLibraryServices.favorites().isFavorite(FavoriteService.Kind.MOOD, id));
    }

    @Test
    void storesTheLastOfConcurrentTogglesWhateverOrderTheirWritesRunIn() throws Exception {
        FavoriteService favorites = TestLibraryServices.favorites();
        String even = "even-" + System.nanoTime();
        String odd = "odd-" + System.nanoTime();
        ExecutorService threads = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> toggles = new ArrayList<>();
            for (int i = 0; i < 40; i++)
                toggles.add(threads.submit(() -> favorites.toggle(FavoriteService.Kind.FRAME, even)));
            for (int i = 0; i < 41; i++)
                toggles.add(threads.submit(() -> favorites.toggle(FavoriteService.Kind.FRAME, odd)));
            for (Future<Boolean> toggle : toggles)
                toggle.get(20, TimeUnit.SECONDS);
        }
        finally {
            threads.shutdownNow();
        }

        assertFalse(favorites.isFavorite(FavoriteService.Kind.FRAME, even));
        assertTrue(favorites.isFavorite(FavoriteService.Kind.FRAME, odd));
        FavoriteService restarted = TestLibraryServices.favorites();
        assertFalse(restarted.isFavorite(FavoriteService.Kind.FRAME, even), "the database agrees");
        assertTrue(restarted.isFavorite(FavoriteService.Kind.FRAME, odd), "the database agrees");
    }

    @Test
    void answersADatabaseErrorWithNoFavoriteAndKeepsTheState() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("broken").resolve("test.db"));
        database.initialize();
        assertTrue(new FavoriteService(new FavoriteRepository(database), new LibraryEvents())
                .toggle(FavoriteService.Kind.MOOD, "own-mood"));

        try (Connection conn = database.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE favorites");
        }

        assertThrows(SQLException.class, () -> new FavoriteRepository(database).findIds("MOOD"));
        FavoriteService restarted = new FavoriteService(new FavoriteRepository(database), new LibraryEvents());
        assertFalse(restarted.isFavorite(FavoriteService.Kind.MOOD, "own-mood"));
        assertFalse(restarted.toggle(FavoriteService.Kind.STYLE, "own-style"), "the former state");
    }
}
