package art.moodframe.service;

import art.moodframe.database.DatabaseConfig;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.PromptBlock;
import art.moodframe.repository.PromptBlockRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Standard blocks and palettes, the blocks of the user in a fresh SQLite database. */
class PromptBlockServiceTest {

    /** Artists, studios, magazines and brands the standard style texts must not name. */
    private static final List<String> BLOCKED_NAMES = List.of("ghibli", "frank miller", "vogue", "national geographic",
            "a24", "michael bay", "warhol", "lichtenstein", "dalí", "dali", "magritte", "magnum", "pulitzer",
            "calvin and hobbes", "tintin", "asterix", "watchmen", "sin city", "blade runner", "miami vice", "tron",
            "wired", "vox", "helvetica", "artstation", "imax", "las vegas", "canon", "sony", "leica", "hasselblad",
            "arri", "nikon", "fujifilm");

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void createDatabase() {
        DatabaseConfig.initialize(tempDir.resolve("db").resolve("test.db").toString());
    }

    @Test
    void givesTheStandardBlocksFixedIdsThatStayStableOverTwoStarts() {
        List<String> first = new PromptBlockService().getDefaults(PromptBlock.Type.STYLE).stream()
                .map(PromptBlock::getId).toList();
        List<String> second = new PromptBlockService().getDefaults(PromptBlock.Type.STYLE).stream()
                .map(PromptBlock::getId).toList();

        assertEquals(first, second);
        assertTrue(first.contains("std:style:watercolor"));
        assertTrue(first.contains("std:style:2000s-digicam"));
        assertTrue(first.contains("std:style:black-and-white-film"));
        assertEquals(first.size(), first.stream().distinct().count());

        List<String> palettes = new ColorStyleService().getDefaultStyles().stream().map(ColorStyle::getId).toList();
        assertEquals(palettes, new ColorStyleService().getDefaultStyles().stream().map(ColorStyle::getId).toList());
        assertTrue(palettes.contains("std:color:ocean-breeze"));
    }

    @Test
    void writesTheStandardStylesWithoutQualityPhrasesNegationsOrNames() {
        Pattern negation = Pattern.compile("\\bno\\s", Pattern.CASE_INSENSITIVE);
        List<PromptBlock> styles = new PromptBlockService().getDefaults(PromptBlock.Type.STYLE);

        assertEquals(38, styles.size());
        for (PromptBlock style : styles) {
            String text = style.getText().toLowerCase(Locale.ROOT);
            assertFalse(text.contains("8k"), style.getName());
            assertFalse(text.contains("professional"), style.getName());
            assertFalse(negation.matcher(text).find(), style.getName());
            for (String name : BLOCKED_NAMES)
                assertFalse(Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(text).find(),
                        style.getName() + " names " + name);
        }
    }

    @Test
    void groupsTheStylesAndMarksOnlyThePhotoGroupAsPhotographic() {
        List<PromptBlock> styles = new PromptBlockService().getDefaults(PromptBlock.Type.STYLE);

        assertEquals(11, styles.stream().filter((PromptBlock s) -> PromptBlock.GROUP_PHOTO.equals(s.getGroup())).count());
        assertEquals(13, styles.stream().filter((PromptBlock s) -> PromptBlock.GROUP_ART.equals(s.getGroup())).count());
        assertEquals(14, styles.stream().filter(PromptBlock::isLayout).count());
        assertTrue(styles.stream().allMatch((PromptBlock s) -> s.isPhotographic() == PromptBlock.GROUP_PHOTO.equals(s.getGroup())));
        assertTrue(styles.stream().allMatch(PromptBlock::isDefault));
    }

    @Test
    void resolvesAFormerInfographicStyleToTheOneItWentInto() {
        PromptBlockService service = new PromptBlockService();

        assertEquals("Infographic Flat", service.find(PromptBlock.Type.STYLE, "Infographic Modern").getName());
        assertEquals("Infographic Minimal", service.find(PromptBlock.Type.STYLE, "Infographic Minimalist").getName());
        assertEquals("Infographic Sketch", service.find(PromptBlock.Type.STYLE, "Infographic Educational").getName());
        for (String former : List.of("Infographic Timeline", "Infographic Flowchart", "Infographic Mind Map",
                "Infographic Comparison", "Infographic Data"))
            assertEquals("Infographic Flat", service.find(PromptBlock.Type.STYLE, former).getName(), former);
        assertEquals("Watercolor", service.find(PromptBlock.Type.STYLE, "std:style:watercolor").getName());
        assertNull(service.find(PromptBlock.Type.STYLE, "Cinematic"), "looks become moods, not styles");
    }

    @Test
    void keepsBlocksOfTheUserNextToTheStandardOnes() {
        PromptBlockService service = new PromptBlockService();
        PromptBlock custom = new PromptBlock(null, PromptBlock.Type.STYLE, "My Linocut", "linocut print", null, true);

        assertTrue(service.addCustom(custom));
        assertFalse(service.addCustom(new PromptBlock(null, PromptBlock.Type.STYLE, "Watercolor", "x", null, true)));
        List<PromptBlock> all = service.getAll(PromptBlock.Type.STYLE);
        assertTrue(all.subList(38, all.size()).stream().anyMatch((PromptBlock b) -> "My Linocut".equals(b.getName())));
        assertTrue(custom.getSortOrder() >= 1000);
        assertEquals(custom.getId(), service.find(PromptBlock.Type.STYLE, "My Linocut").getId());

        custom.setPhotographic(false);
        assertTrue(service.updateCustom(custom));
        assertFalse(service.find(PromptBlock.Type.STYLE, custom.getId()).isPhotographic());
        assertFalse(service.updateCustom(service.find(PromptBlock.Type.STYLE, "Watercolor")), "standard blocks stay");
        assertFalse(service.deleteCustom("std:style:watercolor"));

        assertTrue(service.deleteCustom(custom.getId()));
        assertNull(service.find(PromptBlock.Type.STYLE, "My Linocut"));
    }

    @Test
    void renamesAStyleOfTheUserThatIsNamedLikeAStandardStyleAndKeepsItsText() {
        PromptBlockRepository repository = new PromptBlockRepository();
        PromptBlock clash = new PromptBlock(null, PromptBlock.Type.STYLE, "Infographic Flat", "my own flat look", null,
                true);
        repository.save(clash);

        new PromptBlockService();

        PromptBlock renamed = repository.findById(clash.getId());
        assertEquals("Infographic Flat (1)", renamed.getName());
        assertEquals("my own flat look", renamed.getText());
        assertEquals("std:style:infographic-flat",
                new PromptBlockService().find(PromptBlock.Type.STYLE, "Infographic Flat").getId());
    }

    @Test
    void renamesTwoPalettesAndAddsThreeNewOnes() {
        ColorStyleService service = new ColorStyleService();
        List<String> names = service.getDefaultStyles().stream().map(ColorStyle::getName).toList();

        assertEquals(20, names.size());
        assertTrue(names.containsAll(List.of("Sunset Amber", "Faded Cream", "Silver Screen", "Warm Skin Studio",
                "Fresh Corporate")));
        assertFalse(names.contains("Golden Hour") || names.contains("Vintage Film"));
        assertEquals("Sunset Amber", service.getStyleByName("Golden Hour").getName());
        assertEquals("Faded Cream", service.getStyleByName("Vintage Film").getName());
        assertEquals(List.of("#FFB347", "#FF9F1C", "#E85D04", "#D00000", "#370617"),
                service.getStyleByName("Sunset Amber").getColors());
        assertEquals(List.of("#0A0A0A", "#3D3D3D", "#808080", "#C4C4C4", "#F5F5F5"),
                service.getStyleByName("Silver Screen").getColors());
        assertNotNull(service.getStyleByName("Warm Skin Studio"));
    }

    @Test
    void aTakenNameGetsTheNextFreeCounter() {
        PromptBlockService service = new PromptBlockService();
        assertEquals("My Own Look", service.freeName(PromptBlock.Type.STYLE, "My Own Look"));
        assertEquals("Analog Film (1)", service.freeName(PromptBlock.Type.STYLE, "Analog Film"));
        assertTrue(service.addCustom(new PromptBlock(null, PromptBlock.Type.STYLE, "Analog Film (1)", "x", null, true)));
        assertEquals("Analog Film (2)", service.freeName(PromptBlock.Type.STYLE, "Analog Film"));
    }

    @Test
    void aPaletteOfTheUserNamedLikeANewStandardPaletteIsRenamed() {
        ColorStyle own = new ColorStyle("Silver Screen", List.of("#112233", "#445566"), false);
        own.setId("own-silver-screen");
        own.setSortOrder(150);
        assertTrue(new art.moodframe.repository.ColorStyleRepository().save(own));

        ColorStyleService service = new ColorStyleService();
        assertTrue(service.isDefaultStyle("Silver Screen"));
        ColorStyle renamed = service.getStyleByName("Silver Screen (1)");
        assertNotNull(renamed);
        assertEquals(List.of("#112233", "#445566"), renamed.getColors());
    }
}
