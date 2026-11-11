package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.database.DatabaseConfig;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** The full backup of the moods of the user in a fresh SQLite database. */
class ConfigurationExporterTest {

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void createDatabase() {
        DatabaseConfig.initialize(tempDir.resolve("db").resolve("test.db").toString());
    }

    @Test
    void exportsAndImportsTheMoodsOfTheUser() throws Exception {
        MoodService moods = new MoodService(new PromptBlockService(), new ColorStyleService());
        Mood mood = new Mood(null, "Backup Me");
        mood.setReference(Mood.Field.STYLE, "std:style:analog-film");
        mood.setReference(Mood.Field.COLOR, "std:color:faded-cream");
        mood.setReference(Mood.Field.CAMERA, "std:camera:street-35mm");
        assertTrue(moods.add(mood));

        ConfigurationExporter exporter = new ConfigurationExporter();
        File file = tempDir.resolve("backup.json").toFile();
        exporter.exportConfiguration(mock(ApplicationConfig.class), file,
                Set.of(ConfigurationExporter.ConfigCategory.MOODS));

        /*  A restore after the mood was lost brings it back under its id and name  */
        assertTrue(moods.deletePermanently(mood.getId()));
        ConfigurationExporter.ImportResult result = exporter.importConfiguration(file, mock(ApplicationConfig.class));
        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals("1 moods imported", result.getChanges().get("Moods"));
        Mood restored = moods.find(mood.getId());
        assertNotNull(restored);
        assertEquals("Backup Me", restored.getName());
        assertEquals(mood.getReferences(), restored.getReferences());

        /*  Importing again keeps the mood and adds a copy under a new id and the next free name  */
        result = exporter.importConfiguration(file, mock(ApplicationConfig.class));
        assertEquals("1 moods imported", result.getChanges().get("Moods"));
        Mood copy = moods.getCustom().stream().filter((Mood custom) -> custom.getName().equals("Backup Me (1)"))
                .findFirst().orElseThrow();
        assertNotEquals(mood.getId(), copy.getId());
        assertEquals(mood.getReferences(), copy.getReferences());
    }

    @Test
    void aRestoredMoodFindsItsRestoredBlocks() throws Exception {
        PromptBlockService blocks = new PromptBlockService();
        MoodService moods = new MoodService(blocks, new ColorStyleService());
        PromptBlock style = new PromptBlock(null, PromptBlock.Type.STYLE, "Backup Style", "grainy test look", null, true);
        assertTrue(blocks.addCustom(style));
        Mood mood = new Mood(null, "Backup With Block");
        mood.setReference(Mood.Field.STYLE, style.getId());
        assertTrue(moods.add(mood));

        ConfigurationExporter exporter = new ConfigurationExporter();
        File file = tempDir.resolve("backup-blocks.json").toFile();
        exporter.exportConfiguration(mock(ApplicationConfig.class), file,
                Set.of(ConfigurationExporter.ConfigCategory.CUSTOM_STYLES, ConfigurationExporter.ConfigCategory.MOODS));

        /*  Block and mood are lost, the backup brings both back and the mood points at the block again  */
        assertTrue(blocks.deleteCustom(style.getId()));
        assertTrue(blocks.deletePermanently(style.getId()));
        assertTrue(moods.deletePermanently(mood.getId()));
        ConfigurationExporter.ImportResult result = exporter.importConfiguration(file, mock(ApplicationConfig.class));
        assertTrue(result.isSuccess(), result.getMessage());
        Mood restored = moods.find(mood.getId());
        assertNotNull(restored);
        assertEquals(style.getId(), restored.getReferences().get(Mood.Field.STYLE));
        assertNotNull(blocks.find(PromptBlock.Type.STYLE, style.getId()));
    }

    @Test
    void aStyleOfTheUserNamedLikeAFormerLookStaysAStyle() {
        PromptBlockService blocks = new PromptBlockService();
        MoodService moods = new MoodService(blocks, new ColorStyleService());
        assertNotNull(moods.findForLegacyStyle("Film Noir"));
        assertTrue(blocks.addCustom(new PromptBlock(null, PromptBlock.Type.STYLE, "Film Noir", "my own noir", null, true)));
        assertEquals(null, moods.findForLegacyStyle("Film Noir"));
    }
}
