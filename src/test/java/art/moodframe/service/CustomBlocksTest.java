package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.database.DatabaseConfig;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.PromptBlock;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** The building blocks and palettes of the user as the main window maintains them, in a fresh SQLite database. */
class CustomBlocksTest {

    @TempDir
    static Path tempDir;

    private final PromptBlockService blocks = new PromptBlockService();
    private final ColorStyleService palettes = new ColorStyleService();

    @BeforeAll
    static void createDatabase() {
        DatabaseConfig.initialize(tempDir.resolve("db").resolve("test.db").toString());
    }

    @BeforeEach
    void clear() {
        for (PromptBlock.Type type : PromptBlock.Type.values())
            blocks.resetToDefaults(type);
        palettes.resetToDefaults();
    }

    private PromptBlock add(PromptBlock.Type type, String name, String text) {
        PromptBlock block = new PromptBlock(null, type, name, text, null, type == PromptBlock.Type.STYLE);
        assertTrue(blocks.addCustom(block), name);
        return block;
    }

    private static List<String> names(List<PromptBlock> list) {
        return list.stream().map(PromptBlock::getName).toList();
    }

    @ParameterizedTest
    @EnumSource(PromptBlock.Type.class)
    void addsCopiesAndRenamesBlocksOfTheUserWithoutNameConflicts(PromptBlock.Type type) {
        PromptBlock mine = add(type, "Mine", "my fragment");
        assertFalse(blocks.addCustom(new PromptBlock(null, type, "Mine", "again", null, false)));

        /*  "As new entry" of a standard block: a new id, the next free name, the group of its origin  */
        PromptBlock standard = blocks.getDefaults(type).get(0);
        PromptBlock copy = new PromptBlock(standard.getId(), type, standard.getName(), "changed", standard.getGroup(),
                standard.isPhotographic());
        assertTrue(blocks.addCopy(copy));
        assertEquals(standard.getName() + " (1)", copy.getName());
        assertFalse(copy.isDefault());
        assertEquals(standard.getGroup(), blocks.find(type, copy.getId()).getGroup());
        assertEquals(standard.getText(), blocks.find(type, standard.getId()).getText());

        /*  "Save" keeps the block in place; another block's name is refused  */
        mine.setName(copy.getName());
        assertFalse(blocks.updateCustom(mine));
        mine.setName("Mine renamed");
        mine.setText("new fragment");
        assertTrue(blocks.updateCustom(mine));
        assertEquals("new fragment", blocks.find(type, "Mine renamed").getText());
        assertFalse(blocks.updateCustom(standard));

        assertEquals(List.of("Mine renamed", standard.getName() + " (1)"), names(blocks.getCustom(type)));
    }

    @ParameterizedTest
    @EnumSource(PromptBlock.Type.class)
    void movesADeletedBlockToTheTrashAndRestoresItUnderAFreeName(PromptBlock.Type type) {
        PromptBlock mine = add(type, "Mine", "my fragment");
        assertFalse(blocks.deleteCustom(blocks.getDefaults(type).get(0).getId()));

        assertTrue(blocks.deleteCustom(mine.getId()));
        assertTrue(blocks.getCustom(type).isEmpty());
        assertEquals(List.of(mine.getId()), blocks.getDeleted().stream().map(PromptBlock::getId).toList());
        assertFalse(blocks.deleteCustom(mine.getId()));

        /*  The name of a block in the trash is free; the restored block gets a counter  */
        add(type, "Mine", "a new one");
        assertTrue(blocks.restore(mine.getId()));
        assertEquals(List.of("Mine", "Mine (1)"), names(blocks.getCustom(type)));
        assertTrue(blocks.getDeleted().isEmpty());

        assertTrue(blocks.deleteCustom(mine.getId()));
        assertTrue(blocks.deletePermanently(mine.getId()));
        assertTrue(blocks.getDeleted().isEmpty());
        assertFalse(blocks.restore(mine.getId()));
    }

    @ParameterizedTest
    @EnumSource(PromptBlock.Type.class)
    void exportsAndImportsTheBlocksOfATypeAsJsonInTheirOrder(PromptBlock.Type type) throws Exception {
        PromptBlock first = add(type, "First", "first fragment");
        PromptBlock second = add(type, "Second", "second fragment");
        assertTrue(blocks.reorderCustom(type, List.of(second.getId(), first.getId())));
        assertEquals(List.of("Second", "First"), names(blocks.getCustom(type)));

        File file = tempDir.resolve(type + ".json").toFile();
        blocks.exportCustom(type, file);

        /*  Into the same library: the ids are taken and get new ones, the names a counter  */
        assertEquals(2, blocks.importCustom(type, file));
        List<PromptBlock> custom = blocks.getCustom(type);
        assertEquals(List.of("Second", "First", "Second (1)", "First (1)"), names(custom));
        assertNotEquals(second.getId(), custom.get(2).getId());
        assertEquals("second fragment", custom.get(2).getText());

        /*  Into an empty library: the ids stay, so references to them still hold  */
        blocks.resetToDefaults(type);
        assertEquals(2, blocks.importCustom(type, file));
        assertEquals(List.of(second.getId(), first.getId()), blocks.getCustom(type).stream().map(PromptBlock::getId)
                .toList());

        /*  The blocks of another type in the file are left out  */
        PromptBlock.Type other = type == PromptBlock.Type.STYLE ? PromptBlock.Type.LIGHT : PromptBlock.Type.STYLE;
        assertEquals(0, blocks.importCustom(other, file));
    }

    @Test
    void importsAStyleOfAnOlderExportAsPhotographic() {
        assertEquals(1, blocks.importCustom(PromptBlock.Type.STYLE,
                List.of(Map.of("name", "Old Style", "promptText", "old fragment"))));

        PromptBlock style = blocks.find(PromptBlock.Type.STYLE, "Old Style");
        assertEquals("old fragment", style.getText());
        assertTrue(style.isPhotographic());
    }

    @Test
    void copiesRenamesDeletesAndReordersPalettesOfTheUser() {
        ColorStyle mine = new ColorStyle("Mine", List.of("#112233", "#445566"), false);
        assertTrue(palettes.addCustomStyle(mine));

        ColorStyle standard = palettes.getDefaultStyles().get(0);
        ColorStyle copy = new ColorStyle(standard.getName(), standard.getColors(), true);
        assertTrue(palettes.addCopy(copy));
        assertEquals(standard.getName() + " (1)", copy.getName());
        assertFalse(palettes.getStyleByName(copy.getName()).isDefault());

        assertFalse(palettes.updateCustomStyle("Mine", new ColorStyle(copy.getName(), List.of("#000000"), false)));
        assertTrue(palettes.updateCustomStyle("Mine", new ColorStyle("Mine renamed", List.of("#000000"), false)));
        assertEquals(List.of("#000000"), palettes.getStyleByName("Mine renamed").getColors());

        assertTrue(palettes.reorderCustomStyles(List.of(copy.getId(), mine.getId())));
        assertEquals(List.of(copy.getName(), "Mine renamed"),
                palettes.getCustomStyles().stream().map(ColorStyle::getName).toList());

        /*  A palette in the trash keeps its name; it comes back as it was  */
        assertTrue(palettes.deleteCustomStyle("Mine renamed"));
        assertEquals(List.of(copy.getName()), palettes.getCustomStyles().stream().map(ColorStyle::getName).toList());
        assertTrue(palettes.styleExists("Mine renamed"));
        assertTrue(palettes.restoreStyle(mine.getId()));
        assertTrue(palettes.getDeletedStyles().isEmpty());
        assertTrue(palettes.deleteCustomStyle("Mine renamed"));
        assertTrue(palettes.deleteStylePermanently(mine.getId()));
        assertFalse(palettes.styleExists("Mine renamed"));
    }

    @Test
    void exportsAndImportsPalettesAsJson() throws Exception {
        ColorStyle mine = new ColorStyle("Mine", List.of("#112233"), false);
        assertTrue(palettes.addCustomStyle(mine));
        File file = tempDir.resolve("palettes.json").toFile();
        assertTrue(palettes.exportStyles(file));

        assertEquals(1, palettes.importStyles(file, true));
        List<ColorStyle> custom = palettes.getCustomStyles();
        assertEquals(List.of("Mine", "Mine (1)"), custom.stream().map(ColorStyle::getName).toList());
        assertNotEquals(mine.getId(), custom.get(1).getId());
        assertEquals(List.of("#112233"), custom.get(1).getColors());

        palettes.resetToDefaults();
        assertEquals(1, palettes.importStyles(file, true));
        assertEquals(mine.getId(), palettes.getStyleByName("Mine").getId());
    }

    @Test
    void listsDeletedBlocksAndPalettesInTheTrashWithTheMoodsUsingThem() {
        PromptBlock light = add(PromptBlock.Type.LIGHT, "My Light", "light fragment");
        ColorStyle palette = new ColorStyle("My Palette", List.of("#112233"), false);
        assertTrue(palettes.addCustomStyle(palette));
        assertTrue(blocks.deleteCustom(light.getId()));
        assertTrue(palettes.deleteCustomStyle("My Palette"));

        BlockTrashGroup group = new BlockTrashGroup(blocks, palettes,
                (id) -> id.equals(light.getId()) ? List.of("Noir Rain", "Quiet Morning") : List.of());
        assertEquals(TrashGroup.Kind.BLOCKS, group.kind());
        List<TrashEntry> entries = group.list();
        assertEquals(Set.of("My Light", "My Palette"), Set.copyOf(entries.stream().map(TrashEntry::name).toList()));
        TrashEntry lightEntry = entries.stream().filter((entry) -> entry.id().equals(light.getId())).findFirst()
                .orElseThrow();
        assertTrue(lightEntry.context().contains("2"), lightEntry.context());

        assertTrue(group.restore(light.getId()));
        assertTrue(group.deletePermanently(palette.getId()));
        assertTrue(group.list().isEmpty());
        assertEquals(List.of("My Light"), names(blocks.getCustom(PromptBlock.Type.LIGHT)));
        assertFalse(palettes.styleExists("My Palette"));
    }

    @Test
    void theBackupCarriesAllBlocksAndPalettesAndGivesATakenIdANewOne() throws Exception {
        PromptBlock style = add(PromptBlock.Type.STYLE, "My Style", "style fragment");
        PromptBlock atmosphere = add(PromptBlock.Type.ATMOSPHERE, "My Atmosphere", "atmosphere fragment");
        PromptBlock light = add(PromptBlock.Type.LIGHT, "My Light", "light fragment");
        ColorStyle palette = new ColorStyle("My Palette", List.of("#112233", "#445566"), false);
        assertTrue(palettes.addCustomStyle(palette));

        ApplicationConfig config = mock(ApplicationConfig.class);
        File file = tempDir.resolve("backup.json").toFile();
        new ConfigurationExporter().exportConfiguration(config, file,
                Set.of(ConfigurationExporter.ConfigCategory.CUSTOM_STYLES));
        assertEquals(3, new ObjectMapper().readTree(file).at("/configuration/customBlocks").size());

        /*  Into the same library: everything is added next to the existing entries, with new ids  */
        ConfigurationExporter.ImportResult result = new ConfigurationExporter().importConfiguration(file, config);
        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(List.of("My Style", "My Style (1)"), names(blocks.getCustom(PromptBlock.Type.STYLE)));
        assertNotEquals(style.getId(), blocks.find(PromptBlock.Type.STYLE, "My Style (1)").getId());
        assertEquals(List.of("My Palette", "My Palette (1)"),
                palettes.getCustomStyles().stream().map(ColorStyle::getName).toList());

        /*  Into an empty library: the ids stay  */
        clear();
        assertTrue(new ConfigurationExporter().importConfiguration(file, config).isSuccess());
        assertEquals(style.getId(), blocks.find(PromptBlock.Type.STYLE, "My Style").getId());
        assertEquals(atmosphere.getId(), blocks.find(PromptBlock.Type.ATMOSPHERE, "My Atmosphere").getId());
        assertEquals(light.getId(), blocks.find(PromptBlock.Type.LIGHT, "My Light").getId());
        assertEquals("light fragment", blocks.find(PromptBlock.Type.LIGHT, "My Light").getText());
        assertEquals(palette.getId(), palettes.getStyleByName("My Palette").getId());
        assertEquals(List.of("#112233", "#445566"), palettes.getStyleByName("My Palette").getColors());
    }
}
