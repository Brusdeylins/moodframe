package art.moodframe.service;

import art.moodframe.database.DatabaseConfig;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.Frame;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.repository.ColorStyleRepository;
import art.moodframe.repository.FrameRepository;
import art.moodframe.repository.MoodRepository;
import art.moodframe.repository.PromptBlockRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The order of the lists with favorites, and the favorites in a fresh SQLite database. */
class FavoriteServiceTest {

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void createDatabase() {
        DatabaseConfig.initialize(tempDir.resolve("db").resolve("test.db").toString());
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
        FavoriteService favorites = new FavoriteService();
        assertFalse(favorites.isFavorite(FavoriteService.Kind.MOOD, "std:mood:noir"));
        assertTrue(favorites.toggle(FavoriteService.Kind.MOOD, "std:mood:noir"));
        assertTrue(favorites.toggle(FavoriteService.Kind.STYLE, "my-style"));
        assertTrue(favorites.toggle(FavoriteService.Kind.FRAME, "std:frame:social-post"));
        assertFalse(favorites.toggle(FavoriteService.Kind.FRAME, "std:frame:social-post"),
                "a second click takes it away");

        FavoriteService restarted = new FavoriteService();
        assertTrue(restarted.isFavorite(FavoriteService.Kind.MOOD, "std:mood:noir"));
        assertTrue(restarted.isFavorite(FavoriteService.Kind.STYLE, "my-style"));
        assertFalse(restarted.isFavorite(FavoriteService.Kind.FRAME, "std:frame:social-post"));
        assertFalse(restarted.isFavorite(FavoriteService.Kind.LIGHT, "my-style"),
                "a favorite counts for its kind only");
        assertFalse(restarted.isFavorite(FavoriteService.Kind.MOOD, null), "\"None\" is no favorite");
    }

    @Test
    void dropsTheFavoriteOfAnEntryOfTheUserDeletedForGood() {
        Mood mood = new Mood(null, "Favorite Mood");
        Frame frame = new Frame(null, "Favorite Frame");
        frame.setAspectRatio("1:1");
        PromptBlock light = new PromptBlock(null, PromptBlock.Type.LIGHT, "Favorite Light", "soft light", null, true);
        ColorStyle palette = new ColorStyle("Favorite Palette", List.of("#000000"));
        MoodRepository moods = new MoodRepository();
        FrameRepository frames = new FrameRepository();
        PromptBlockRepository blocks = new PromptBlockRepository();
        ColorStyleRepository palettes = new ColorStyleRepository();
        assertTrue(moods.save(mood));
        assertTrue(frames.save(frame));
        assertTrue(blocks.save(light));
        assertTrue(palettes.save(palette));

        FavoriteService favorites = new FavoriteService();
        favorites.toggle(FavoriteService.Kind.MOOD, mood.getId());
        favorites.toggle(FavoriteService.Kind.FRAME, frame.getId());
        favorites.toggle(FavoriteService.Kind.LIGHT, light.getId());
        favorites.toggle(FavoriteService.Kind.COLOR, palette.getId());

        /*  in the trash, an entry keeps its favorite, so a restored one comes back as it was  */
        assertTrue(moods.moveToTrash(mood.getId()));
        assertTrue(new FavoriteService().isFavorite(FavoriteService.Kind.MOOD, mood.getId()));

        assertTrue(moods.delete(mood.getId()));
        assertTrue(frames.delete(frame.getId()));
        assertTrue(blocks.delete(light.getId()));
        assertTrue(palettes.delete(palette.getId()));
        FavoriteService restarted = new FavoriteService();
        assertFalse(restarted.isFavorite(FavoriteService.Kind.MOOD, mood.getId()));
        assertFalse(restarted.isFavorite(FavoriteService.Kind.FRAME, frame.getId()));
        assertFalse(restarted.isFavorite(FavoriteService.Kind.LIGHT, light.getId()));
        assertFalse(restarted.isFavorite(FavoriteService.Kind.COLOR, palette.getId()));
    }
}
