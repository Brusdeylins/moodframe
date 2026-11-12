package art.moodframe.service;

import art.moodframe.database.DatabaseConfig;
import art.moodframe.model.CameraSettings;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.EntryMood;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Standard moods and the moods of the user in a fresh SQLite database. */
class MoodServiceTest {

    /** The former style "Business Photography" of 1.x, word for word. */
    private static final String FORMER_BUSINESS_PHOTOGRAPHY = "realistic, detailed photography, people are authentic "
            + "and natural-looking with friendly and approachable expressions, wearing business casual attire, diverse "
            + "ethnicities and body types, authentic business situation in a modern and dynamic setting, clean "
            + "composition, soft natural lighting, open, future-oriented, modern atmosphere";

    @TempDir
    static Path tempDir;

    private static PromptBlockService blocks;
    private static ColorStyleService colors;
    private static MoodService moods;

    @BeforeAll
    static void createDatabase() {
        DatabaseConfig.initialize(tempDir.resolve("db").resolve("test.db").toString());
        blocks = new PromptBlockService();
        colors = new ColorStyleService();
        moods = new MoodService(blocks, colors);
    }

    @Test
    void offersFourteenStandardMoodsThatReferToExistingStandardBlocks() {
        List<Mood> defaults = moods.getDefaults();

        assertEquals(List.of("Business Photography", "Film Noir", "Cinematic", "Blockbuster", "Arthouse",
                "Sci-Fi Cinema", "Retro 80s", "Vintage Film", "Studio Portrait", "Quiet Morning", "Dreamy Pastel",
                "Candle Glow", "Wild North", "Clean Product"), defaults.stream().map(Mood::getName).toList());
        for (Mood mood : defaults) {
            assertTrue(mood.isDefault(), mood.getName());
            assertTrue(mood.getId().startsWith("std:mood:"), mood.getName());
            assertEquals(mood.getReferences(), moods.apply(mood, Map.of(), Set.of()),
                    mood.getName() + " refers to a block that does not exist");
        }

        Mood noir = moods.find("std:mood:film-noir");
        assertEquals("std:style:black-and-white-film", noir.getReference(Mood.Field.STYLE));
        assertEquals("std:atmosphere:menacing", noir.getReference(Mood.Field.ATMOSPHERE));
        assertEquals("std:light:chiaroscuro", noir.getReference(Mood.Field.LIGHT));
        assertEquals("std:color:silver-screen", noir.getReference(Mood.Field.COLOR));
        assertNull(noir.getReference(Mood.Field.CAMERA));
        assertNull(moods.find("std:mood:cinematic").getReference(Mood.Field.ATMOSPHERE));
        assertEquals("std:camera:product-tabletop", moods.find("std:mood:clean-product").getReference(Mood.Field.CAMERA));
    }

    @Test
    void keepsEveryPhraseOfTheFormerStyleBusinessPhotography() {
        Mood mood = moods.find("std:mood:business-photography");
        String prompt = new PromptComposer(10000).compose("A team meeting in an office", null, moods.selection(mood));

        for (String phrase : FORMER_BUSINESS_PHOTOGRAPHY.split(","))
            assertTrue(prompt.contains(phrase.trim()), "the prompt of the mood lacks \"" + phrase.trim() + "\"");
    }

    @Test
    void setsOnlyTheFieldsThatAreNotLocked() {
        Mood noir = moods.find("std:mood:film-noir");
        Map<Mood.Field, String> current = Map.of(Mood.Field.STYLE, "std:style:watercolor",
                Mood.Field.LIGHT, "std:light:moonlight");

        Map<Mood.Field, String> fields = moods.apply(noir, current, Set.of(Mood.Field.STYLE, Mood.Field.COLOR));
        assertEquals("std:style:watercolor", fields.get(Mood.Field.STYLE), "a locked field keeps its block");
        assertNull(fields.get(Mood.Field.COLOR), "a locked empty field stays empty");
        assertEquals("std:light:chiaroscuro", fields.get(Mood.Field.LIGHT));
        assertEquals("std:atmosphere:menacing", fields.get(Mood.Field.ATMOSPHERE));

        Map<Mood.Field, String> none = moods.apply(null, current, Set.of(Mood.Field.LIGHT));
        assertEquals(Map.of(Mood.Field.LIGHT, "std:light:moonlight"), none, "\"No mood\" empties the unlocked fields");

        Mood cinematic = moods.find("std:mood:cinematic");
        assertNull(moods.apply(cinematic, current, Set.of()).get(Mood.Field.ATMOSPHERE),
                "a field the mood leaves empty is emptied");
    }

    @Test
    void savesTheFieldsAsTheyAreTheCameraToo() {
        Mood product = moods.find("std:mood:clean-product");
        Map<Mood.Field, String> fields = Map.of(Mood.Field.STYLE, "std:style:watercolor");

        assertEquals(fields, moods.capture(product, fields), "an empty field stays empty, the camera too");
        assertEquals(fields, moods.capture(null, fields));
        Map<Mood.Field, String> withCamera = Map.of(Mood.Field.STYLE, "std:style:watercolor",
                Mood.Field.CAMERA, "std:camera:macro-detail");
        assertEquals(withCamera, moods.capture(product, withCamera));
    }

    @Test
    void putsTheCameraOfTheMoodIntoThePromptAndItsName() {
        Mood product = moods.find("std:mood:clean-product");
        String prompt = new PromptComposer(10000).compose("a perfume bottle", null, moods.selection(product));

        assertTrue(prompt.contains("\nCamera: shot on Hasselblad X2D, a medium format camera, "), prompt);
        assertTrue(moods.describe(product).endsWith(" · Product Tabletop"), moods.describe(product));
    }

    @Test
    void leavesTheFieldOfABlockInTheTrashEmptyUntilTheBlockIsRestored() throws Exception {
        PromptBlock light = new PromptBlock(null, PromptBlock.Type.LIGHT, "Lantern", "a single lantern", null, true);
        assertTrue(blocks.addCustom(light));
        ColorStyle palette = new ColorStyle("Lantern Glow", List.of("#FFAA00", "#331100"), false);
        assertTrue(colors.addCustomStyle(palette));
        Mood mood = new Mood(null, "Lantern Night");
        mood.setReference(Mood.Field.LIGHT, light.getId());
        mood.setReference(Mood.Field.COLOR, palette.getId());
        mood.setReference(Mood.Field.STYLE, "std:style:analog-film");
        assertTrue(moods.add(mood));

        execute("UPDATE prompt_blocks SET deleted_at = CURRENT_TIMESTAMP WHERE id = '" + light.getId() + "'");
        execute("UPDATE color_styles SET deleted_at = CURRENT_TIMESTAMP WHERE id = '" + palette.getId() + "'");
        Mood stored = moods.find(mood.getId());
        assertEquals(List.of(palette.getId(), light.getId()), moods.trashedBlocks(stored));
        assertEquals(Map.of(Mood.Field.STYLE, "std:style:analog-film"), moods.apply(stored, Map.of(), Set.of()));
        assertEquals(light.getId(), stored.getReference(Mood.Field.LIGHT), "the mood keeps the reference");
        assertEquals("Analog Film · Lantern Glow · Lantern", moods.describe(stored));
        assertEquals(Map.of(Mood.Field.STYLE, "std:style:watercolor", Mood.Field.COLOR, palette.getId(),
                Mood.Field.LIGHT, light.getId()), moods.capture(stored, Map.of(Mood.Field.STYLE, "std:style:watercolor")),
                "saving over the mood keeps the blocks in the trash for the fields they leave empty");

        execute("UPDATE prompt_blocks SET deleted_at = NULL WHERE id = '" + light.getId() + "'");
        execute("UPDATE color_styles SET deleted_at = NULL WHERE id = '" + palette.getId() + "'");
        assertTrue(moods.trashedBlocks(stored).isEmpty());
        assertEquals(stored.getReferences(), moods.apply(stored, Map.of(), Set.of()));
    }

    @Test
    void dropsTheReferencesOfBlocksDeletedForGoodAndReportsAMoodLeftEmptyOnce() {
        Mood lonely = new Mood(null, "Only Fog");
        lonely.setReference(Mood.Field.ATMOSPHERE, "atmosphere-gone");
        Mood rich = new Mood(null, "Fog and Film");
        rich.setReference(Mood.Field.ATMOSPHERE, "atmosphere-gone");
        rich.setReference(Mood.Field.STYLE, "std:style:analog-film");
        Mood blank = new Mood(null, "Saved Blank");
        assertTrue(moods.add(lonely));
        assertTrue(moods.add(rich));
        assertTrue(moods.add(blank));

        moods.blocksDeleted(List.of("atmosphere-gone"));
        List<Mood> emptied = moods.takeEmptied();
        assertEquals(List.of("Only Fog"), emptied.stream().map(Mood::getName).toList(),
                "only a mood that lost its last block is reported, not one saved empty");
        assertTrue(emptied.get(0).isEmpty());
        assertTrue(moods.takeEmptied().isEmpty(), "a mood is reported once");
        assertEquals(Map.of(Mood.Field.STYLE, "std:style:analog-film"), moods.find(rich.getId()).getReferences());
    }

    @Test
    void mapsTheFormerLookStylesToTheStandardMoodsOfTheSameName() {
        for (String look : List.of("Business Photography", "Film Noir", "Cinematic", "Blockbuster", "Arthouse",
                "Sci-Fi Cinema", "Retro 80s", "Vintage Film", "Studio Portrait")) {
            assertTrue(MoodService.isLegacyLook(look), look);
            Mood mood = moods.findForLegacyStyle(look);
            assertNotNull(mood, look);
            assertEquals(look, mood.getName());
            assertTrue(mood.isDefault());
        }
        assertNull(moods.findForLegacyStyle("Quiet Morning"), "a new standard mood was no style");
        assertNull(moods.findForLegacyStyle("Watercolor"));
        assertNull(moods.findForLegacyStyle(null));
    }

    @Test
    void savesRenamesReordersAndTrashesMoodsOfTheUser() {
        Mood first = new Mood(null, "Order First");
        Mood second = new Mood(null, "Order Second");
        assertTrue(moods.add(first));
        assertTrue(moods.add(second));
        assertFalse(moods.add(new Mood(null, "Film Noir")), "a standard mood keeps its name");
        assertFalse(moods.add(new Mood(null, "Order First")));
        assertEquals("Film Noir (1)", moods.freeName("Film Noir"));

        moods.reorder(List.of(second.getId(), first.getId()));
        List<String> names = moods.getCustom().stream().map(Mood::getName)
                .filter((String name) -> name.startsWith("Order ")).toList();
        assertEquals(List.of("Order Second", "Order First"), names);

        Mood noir = moods.find("std:mood:film-noir");
        assertFalse(moods.update(noir), "a standard mood cannot be changed");
        assertFalse(moods.moveToTrash(noir.getId()), "a standard mood cannot be deleted");

        first.setName("Order Renamed");
        assertTrue(moods.update(first));
        assertTrue(moods.moveToTrash(first.getId()));
        assertNull(moods.find(first.getId()));
        assertTrue(new MoodTrashGroup(moods).list().stream().anyMatch((TrashEntry entry) ->
                entry.id().equals(first.getId()) && entry.name().equals("Order Renamed")));

        assertTrue(moods.add(new Mood(null, "Order Renamed")));
        assertTrue(moods.restore(first.getId()));
        assertEquals("Order Renamed (1)", moods.find(first.getId()).getName(), "a restored mood yields its name");
        assertTrue(moods.moveToTrash(first.getId()));
        assertTrue(moods.deletePermanently(first.getId()));
        assertFalse(moods.restore(first.getId()));
    }

    @Test
    void exportsAndImportsTheMoodsOfTheUser() {
        Mood mood = new Mood(null, "Export Me");
        mood.setReference(Mood.Field.LIGHT, "std:light:golden-hour");
        mood.setReference(Mood.Field.CAMERA, "std:camera:street-35mm");
        assertTrue(moods.add(mood));
        File file = tempDir.resolve("moods.json").toFile();
        assertTrue(moods.exportMoods(file));

        int before = moods.getCustom().size();
        int imported = moods.importMoods(file);
        assertEquals(before, imported, "every mood comes back, under a new id and name");
        Mood copy = moods.getCustom().stream().filter((Mood custom) -> custom.getName().equals("Export Me (1)"))
                .findFirst().orElseThrow();
        assertFalse(copy.getId().equals(mood.getId()));
        assertEquals(mood.getReferences(), copy.getReferences());
        assertEquals(-1, moods.importMoods(tempDir.resolve("missing.json").toFile()));
    }

    @Test
    void copiesTheBlocksOfAnEntryAndCreatesThemAnewFromTheCopy() throws Exception {
        PromptBlock light = new PromptBlock(null, PromptBlock.Type.LIGHT, "Harbor Lamp", "a single sodium lamp", null,
                true);
        assertTrue(blocks.addCustom(light));
        ColorStyle palette = new ColorStyle("Harbor Night", List.of("#0A1A2A", "#FFAA00"), false);
        assertTrue(colors.addCustomStyle(palette));
        PromptBlock style = blocks.find(PromptBlock.Type.STYLE, "std:style:analog-film");
        Mood noir = moods.find("std:mood:film-noir");

        /*  The main window hands over its palette without id  */
        PromptComposer.Selection selection = new PromptComposer.Selection(style, null, light, null,
                new ColorStyle("Harbor Night", List.of("#0A1A2A", "#FFAA00"), false));
        EntryMood copy = moods.snapshot(noir, selection);
        assertEquals("std:mood:film-noir", copy.moodId());
        assertEquals("Film Noir", copy.moodName());
        assertEquals(Set.of(Mood.Field.STYLE, Mood.Field.LIGHT, Mood.Field.COLOR), copy.blocks().keySet());
        assertEquals(palette.getId(), copy.blocks().get(Mood.Field.COLOR).id());
        assertEquals("a single sodium lamp", copy.blocks().get(Mood.Field.LIGHT).text());
        assertNull(moods.snapshot(null, selection).moodId(), "a combination of its own has no mood");
        assertNull(moods.snapshot(noir, new PromptComposer.Selection(null, null, null, null, null)),
                "without blocks there is no copy");

        assertTrue(moods.missingBlocks(copy).isEmpty());
        assertEquals(Map.of(Mood.Field.STYLE, style.getId(), Mood.Field.LIGHT, light.getId(), Mood.Field.COLOR,
                palette.getId()), moods.references(copy, true), "blocks in use stay themselves");

        execute("DELETE FROM prompt_blocks WHERE id = '" + light.getId() + "'");
        execute("UPDATE color_styles SET deleted_at = CURRENT_TIMESTAMP WHERE id = '" + palette.getId() + "'");
        assertEquals(List.of(Mood.Field.COLOR, Mood.Field.LIGHT), moods.missingBlocks(copy));
        assertEquals(Map.of(Mood.Field.STYLE, style.getId()), moods.references(copy, false));

        Map<Mood.Field, String> references = moods.references(copy, true);
        PromptBlock recreated = blocks.find(PromptBlock.Type.LIGHT, references.get(Mood.Field.LIGHT));
        assertEquals("Harbor Lamp", recreated.getName());
        assertEquals("a single sodium lamp", recreated.getText());
        assertFalse(recreated.isDefault());
        ColorStyle recreatedPalette = colors.getStyleById(references.get(Mood.Field.COLOR));
        assertTrue(recreatedPalette.getName().startsWith("Harbor Night"));
        assertEquals(List.of("#0A1A2A", "#FFAA00"), recreatedPalette.getColors());
        assertFalse(recreatedPalette.getId().equals(palette.getId()), "the palette in the trash stays there");
    }

    @Test
    void copiesTheCameraOfAnEntryWithItsSettingsTakesItOverAndCreatesItAnew() throws Exception {
        PromptBlock camera = PromptBlockService.camera(null, "Harbor Tele", new CameraSettings(
                CameraSettings.Body.FULL_FRAME, null, CameraSettings.Lens.MM_135, CameraSettings.Aperture.F_4, null,
                null, null, null));
        assertTrue(blocks.addCustom(camera));
        Mood product = moods.find("std:mood:clean-product");

        EntryMood.Block tabletop = moods.snapshot(product, moods.selection(product)).blocks().get(Mood.Field.CAMERA);
        assertEquals("std:camera:product-tabletop", tabletop.id());
        assertEquals(blocks.find(PromptBlock.Type.CAMERA, tabletop.id()).getParams(), tabletop.params());

        /*  "Take over mood" sets the camera of the copy, a locked camera keeps its block  */
        EntryMood copy = moods.snapshot(null, new PromptComposer.Selection(null, null, null, camera, null));
        Mood combination = new Mood(null, null);
        moods.references(copy, false).forEach(combination::setReference);
        assertEquals(Map.of(Mood.Field.CAMERA, camera.getId()), moods.apply(combination, Map.of(), Set.of()));
        assertEquals(Map.of(Mood.Field.CAMERA, "std:camera:macro-detail"), moods.apply(combination,
                Map.of(Mood.Field.CAMERA, "std:camera:macro-detail"), Set.of(Mood.Field.CAMERA)));

        execute("DELETE FROM prompt_blocks WHERE id = '" + camera.getId() + "'");
        assertEquals(List.of(Mood.Field.CAMERA), moods.missingBlocks(copy));
        PromptBlock recreated = blocks.find(PromptBlock.Type.CAMERA,
                moods.references(copy, true).get(Mood.Field.CAMERA));
        assertEquals("Harbor Tele", recreated.getName());
        assertEquals(camera.getParams(), recreated.getParams(), "the camera comes back with its settings");
    }

    private static void execute(String sql) throws Exception {
        try (Connection conn = DatabaseConfig.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    @Test
    void namesTheMoodsThatUseABlock() {
        MoodService moods = new MoodService(new PromptBlockService(), new ColorStyleService());
        Mood mood = new Mood(null, "Uses Watercolor");
        mood.setReference(Mood.Field.STYLE, "std:style:watercolor");
        assertTrue(moods.add(mood));
        assertTrue(moods.moodsUsing("std:style:watercolor").contains("Uses Watercolor"));
        assertTrue(moods.moodsUsing("std:style:does-not-exist").isEmpty());
    }
}
