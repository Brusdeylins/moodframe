package art.moodframe.service;

import art.moodframe.database.DatabaseConfig;
import art.moodframe.model.CameraSettings;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.PromptBlock;
import art.moodframe.repository.PromptBlockRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
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
    void offersTenStandardCamerasFromSettingsOnly() {
        PromptBlockService service = new PromptBlockService();
        List<PromptBlock> cameras = service.getDefaults(PromptBlock.Type.CAMERA);

        assertEquals(List.of("Street 35mm", "Classic Portrait", "Epic Wide Low Angle", "Telephoto Compression",
                "Macro Detail", "Drone Overview", "Cinematic Anamorphic", "Product Tabletop", "Long Exposure Landscape",
                "Phone Snapshot"), cameras.stream().map(PromptBlock::getName).toList());
        for (PromptBlock camera : cameras) {
            assertTrue(camera.isDefault(), camera.getName());
            assertEquals("std:camera:" + PromptBlockService.slug(camera.getName()), camera.getId());
            assertEquals(CameraPromptBuilder.build(CameraSettings.fromJson(camera.getParams()), true), camera.getText(),
                    camera.getName() + " carries the fragment of its settings");
            assertEquals(CameraSettings.fromJson(camera.getParams()).withoutConflicts(),
                    CameraSettings.fromJson(camera.getParams()), camera.getName() + " has no contradiction");
        }

        PromptBlock street = service.find(PromptBlock.Type.CAMERA, "std:camera:street-35mm");
        assertEquals("""
                Image style: realistic photograph, sharp focus, natural detail and texture.
                a market at dawn
                Camera: shot on Leica M6, a 35mm compact film camera, candid analog snapshot look, 35mm lens, natural \
                documentary perspective, f/11, deep depth of field, front and back in sharp focus, shot at eye \
                level.""", new PromptComposer(2000).compose("a market at dawn", null, new PromptComposer.Selection(
                service.find(PromptBlock.Type.STYLE, "Photography"), null, null, street, null)));
    }

    @Test
    void writesTheStandardLightsWithoutMediumOrPalette() {
        Pattern medium = Pattern.compile("\\b(photo\\w*|film|paint\\w*|watercolor|oil|pencil|sketch\\w*|drawing|"
                + "illustration|digital|render\\w*|anime|comic|vector|canvas|analog|35mm)\\b");
        Pattern palette = Pattern.compile("#|\\b(hex|palette|colou?rs?|red|orange|yellow|green|blue|purple|violet|"
                + "pink|magenta|cyan|teal|turquoise|amber|gold|golden)\\b");
        Pattern temperature = Pattern.compile("\\b(warm|cool)\\b");
        List<PromptBlock> lights = new PromptBlockService().getDefaults(PromptBlock.Type.LIGHT);

        assertEquals(17, lights.size());
        assertEquals("std:light:golden-hour", lights.get(1).getId());
        assertEquals("std:light:high-key", lights.get(16).getId());
        for (PromptBlock light : lights) {
            String text = light.getText().toLowerCase(Locale.ROOT);
            assertTrue(light.isDefault(), light.getName());
            assertFalse(medium.matcher(text).find(), light.getName() + " names a medium");
            assertFalse(palette.matcher(text).find(), light.getName() + " names a color");

            /*  A color temperature belongs to the golden and the blue hour only  */
            if (!List.of("Golden Hour", "Blue Hour").contains(light.getName()))
                assertFalse(temperature.matcher(text).find(), light.getName() + " names a color temperature");
        }
    }

    @Test
    void composesAStandardLightAsTheLightingLine() {
        PromptBlockService service = new PromptBlockService();
        PromptBlock goldenHour = service.find(PromptBlock.Type.LIGHT, "std:light:golden-hour");
        String prompt = new PromptComposer(2000).compose("a lighthouse on a cliff", null, new PromptComposer.Selection(
                service.find(PromptBlock.Type.STYLE, "Photography"), null, goldenHour, null, null));

        assertEquals("Golden Hour", service.find(PromptBlock.Type.LIGHT, "Golden Hour").getName());
        assertEquals("""
                Image style: realistic photograph, sharp focus, natural detail and texture.
                a lighthouse on a cliff
                Lighting: low sun shortly before sunset, warm directional light raking across the scene, long soft \
                shadows, a gentle glow on edges.""", prompt);
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
    void givesTheStandardAtmospheresFixedIdsInTheListedOrder() {
        List<PromptBlock> atmospheres = new PromptBlockService().getDefaults(PromptBlock.Type.ATMOSPHERE);
        List<String> ids = atmospheres.stream().map(PromptBlock::getId).toList();

        assertEquals(List.of("Melancholic", "Euphoric", "Longing", "Menacing", "Calm", "Playful", "Epic", "Intimate",
                "Nostalgic", "Clean and Factual", "Dreamlike", "Raw", "Mysterious", "Glamorous", "Futuristic",
                "Open Business", "Fog", "Rain"), atmospheres.stream().map(PromptBlock::getName).toList());
        assertEquals(ids, new PromptBlockService().getDefaults(PromptBlock.Type.ATMOSPHERE).stream()
                .map(PromptBlock::getId).toList());
        assertTrue(ids.contains("std:atmosphere:clean-and-factual"));
        assertTrue(ids.contains("std:atmosphere:open-business"));
        assertEquals(ids.size(), ids.stream().distinct().count());
        for (PromptBlock atmosphere : atmospheres) {
            assertEquals("std:atmosphere:" + PromptBlockService.slug(atmosphere.getName()), atmosphere.getId());
            assertEquals(PromptBlock.Type.ATMOSPHERE, atmosphere.getType());
            assertTrue(atmosphere.isDefault());
        }
    }

    @Test
    void writesTheStandardAtmospheresWithoutPersonsTechniqueLightOrColors() {
        Pattern persons = Pattern.compile("\\b(people|persons?|m[ae]n|wom[ae]n|child(ren)?|faces?|expressions?|smil\\w*|"
                + "eyes)\\b");
        Pattern technique = Pattern.compile("\\b(camera|lens|shot|photo\\w*|film|painting|illustration|render\\w*|"
                + "bokeh|focus|exposure)\\b");
        Pattern light = Pattern.compile("\\b(light|lights|lighting|lit|sun\\w*|shadows?|glow\\w*|backlit)\\b");
        Pattern colors = Pattern.compile("\\b(colou?rs?|red|orange|yellow|green|blue|purple|violet|pink|brown|black|"
                + "white|gr[ae]y|gold\\w*|silver|teal|pastel)\\b");

        for (PromptBlock atmosphere : new PromptBlockService().getDefaults(PromptBlock.Type.ATMOSPHERE)) {
            String text = atmosphere.getText().toLowerCase(Locale.ROOT);

            /*  "Open Business" keeps the persons of the former style "Business Photography"  */
            if (!"Open Business".equals(atmosphere.getName()))
                assertFalse(persons.matcher(text).find(), atmosphere.getName() + " names persons");
            assertFalse(technique.matcher(text).find(), atmosphere.getName() + " names technique");
            assertFalse(light.matcher(text).find(), atmosphere.getName() + " names light");
            assertFalse(colors.matcher(text).find(), atmosphere.getName() + " names colors");
        }
    }

    @Test
    void takesOverThePersonAndMoodPartsOfTheFormerBusinessPhotographyWordForWord() {
        List<String> former = List.of(("realistic, detailed photography, people are authentic and natural-looking with "
                + "friendly and approachable expressions, wearing business casual attire, diverse ethnicities and body "
                + "types, authentic business situation in a modern and dynamic setting, clean composition, soft natural "
                + "lighting, open, future-oriented, modern atmosphere").split(", "));
        List<String> openBusiness = List.of(new PromptBlockService().find(PromptBlock.Type.ATMOSPHERE, "Open Business")
                .getText().split(", "));

        assertTrue(former.containsAll(openBusiness), "no new or smoothed wording");
        List<String> rest = new ArrayList<>(former);
        rest.removeAll(openBusiness);
        assertEquals(List.of("realistic", "detailed photography", "clean composition", "soft natural lighting"), rest,
                "only the style and light parts stay out");
    }

    @Test
    void composesEveryStandardAtmosphereAsOneLabeledLineBehindTheSubject() {
        PromptBlockService service = new PromptBlockService();
        PromptComposer composer = new PromptComposer(2000);
        PromptBlock photo = service.find(PromptBlock.Type.STYLE, "Photography");

        for (PromptBlock atmosphere : service.getDefaults(PromptBlock.Type.ATMOSPHERE))
            assertEquals("Image style: " + photo.getText() + ".\na lighthouse on a cliff\nAtmosphere: "
                    + atmosphere.getText() + ".", composer.compose("a lighthouse on a cliff", null,
                    new PromptComposer.Selection(photo, atmosphere, null, null, null)), atmosphere.getName());
        assertEquals("a lighthouse\nAtmosphere: dense fog drifting through the scene, distant shapes fading into haze, "
                + "muffled and quiet.", composer.compose("a lighthouse", null, new PromptComposer.Selection(null,
                service.find(PromptBlock.Type.ATMOSPHERE, "std:atmosphere:fog"), null, null, null)));
    }

    @Test
    void renamesAnAtmosphereOfTheUserThatIsNamedLikeAStandardAtmosphere() {
        PromptBlockRepository repository = new PromptBlockRepository();
        PromptBlock clash = new PromptBlock(null, PromptBlock.Type.ATMOSPHERE, "Fog", "my own fog", null, true);
        repository.save(clash);

        new PromptBlockService();

        PromptBlock renamed = repository.findById(clash.getId());
        assertEquals("Fog (1)", renamed.getName());
        assertEquals("my own fog", renamed.getText());
        assertEquals("std:atmosphere:fog", new PromptBlockService().find(PromptBlock.Type.ATMOSPHERE, "Fog").getId());
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
