package art.moodframe.service;

import art.moodframe.service.library.TestLibraryServices;
import art.moodframe.config.ConfigMocks;
import art.moodframe.database.DatabaseManager;
import art.moodframe.database.TestDatabase;
import art.moodframe.model.Frame;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.model.SavedImage;
import art.moodframe.repository.SavedImageRepository;
import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.library.SavedImageService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The full backup of the moods, frames and saved images of the user in a fresh SQLite database. */
class ConfigurationExporterTest {

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void createDatabase() {
        TestDatabase.open(tempDir.resolve("db").resolve("test.db").toString());
    }

    @Test
    void exportsAndImportsTheMoodsOfTheUser() throws Exception {
        MoodService moods = TestLibraryServices.moods(TestLibraryServices.blocks(), TestLibraryServices.palettes());
        Mood mood = new Mood(null, "Backup Me");
        mood.setReference(Mood.Field.STYLE, "std:style:analog-film");
        mood.setReference(Mood.Field.COLOR, "std:color:faded-cream");
        mood.setReference(Mood.Field.CAMERA, "std:camera:street-35mm");
        assertTrue(moods.add(mood));

        ConfigurationExporter exporter = exporter();
        File file = tempDir.resolve("backup.json").toFile();
        exporter.exportConfiguration(ConfigMocks.config(), file,
                Set.of(ConfigurationExporter.ConfigCategory.MOODS));

        /*  A restore after the mood was lost brings it back under its id and name  */
        assertTrue(moods.moveToTrash(mood.getId()));
        assertTrue(moods.deletePermanently(mood.getId()));
        ConfigurationExporter.ImportResult result = exporter.importConfiguration(file, ConfigMocks.config());
        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals("1 moods imported", result.getChanges().get("Moods"));
        Mood restored = moods.find(mood.getId());
        assertNotNull(restored);
        assertEquals("Backup Me", restored.getName());
        assertEquals(mood.getReferences(), restored.getReferences());

        /*  Importing again keeps the mood and adds a copy under a new id and the next free name  */
        result = exporter.importConfiguration(file, ConfigMocks.config());
        assertEquals("1 moods imported", result.getChanges().get("Moods"));
        Mood copy = moods.getCustom().stream().filter((Mood custom) -> custom.getName().equals("Backup Me (1)"))
                .findFirst().orElseThrow();
        assertNotEquals(mood.getId(), copy.getId());
        assertEquals(mood.getReferences(), copy.getReferences());
    }

    @Test
    void exportsAndImportsTheFramesOfTheUser() throws Exception {
        FrameService frames = TestLibraryServices.frames();
        Frame frame = new Frame(null, "Druck A3");
        frame.setAspectRatio("2:3");
        frame.setResolution("4K");
        assertTrue(frames.add(frame));

        ConfigurationExporter exporter = exporter();
        File file = tempDir.resolve("frames-backup.json").toFile();
        exporter.exportConfiguration(ConfigMocks.config(), file,
                Set.of(ConfigurationExporter.ConfigCategory.FRAMES));

        /*  A restore after the frame was lost brings it back under its id and name  */
        assertTrue(frames.moveToTrash(frame.getId()));
        assertTrue(frames.deletePermanently(frame.getId()));
        ConfigurationExporter.ImportResult result = exporter.importConfiguration(file, ConfigMocks.config());
        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals("1 frames imported", result.getChanges().get("Frames"));
        Frame restored = frames.find(frame.getId());
        assertNotNull(restored);
        assertEquals("Druck A3", restored.getName());
        assertEquals("2:3", restored.getAspectRatio());
        assertEquals("4K", restored.getResolution());
    }

    @Test
    void importsAFrameOfAnOlderBackupWithModel() throws Exception {
        File file = tempDir.resolve("older-backup.json").toFile();
        Files.writeString(file.toPath(), """
                {
                  "metadata": { "exportVersion": "1.0", "applicationVersion": "2.0.0" },
                  "configuration": {
                    "frames": [ { "id": "older-backup", "name": "Older Backup", "model": "flux.3-image",
                      "resolution": "4K", "aspectRatio": "21:9", "imageCount": 2, "guidance": 3.5, "steps": 28 } ]
                  }
                }
                """);

        ConfigurationExporter.ImportResult result = exporter().importConfiguration(file,
                ConfigMocks.config());
        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals("1 frames imported", result.getChanges().get("Frames"));
        Frame imported = TestLibraryServices.frames().find("older-backup");
        assertNotNull(imported);
        assertEquals("Older Backup", imported.getName());
        assertEquals("21:9", imported.getAspectRatio());
        assertEquals("4K", imported.getResolution(), "the resolution of an older backup becomes the target");
    }

    @Test
    void exportsAndImportsTheSavedImagesIntoAnotherLibrary() throws Exception {
        SavedImageService source = TestLibraryServices.savedImages(tempDir.resolve("source-images"),
                new LibraryEvents());
        SavedImage logo = source.save(image("backup-logo.png", Color.RED), "Backup Logo", "red logo, lower right")
                .image();
        assertTrue(source.setFavorite(logo.id(), true));
        SavedImage word = source.save(image("backup-word.png", Color.BLUE), "Backup Word Mark", null).image();
        SavedImage old = source.save(image("backup-old.png", Color.GRAY), "Backup Old Logo", null).image();
        assertTrue(source.moveToTrash(old.id()));

        File file = tempDir.resolve("saved-images-backup.json").toFile();
        exporter(source).exportConfiguration(ConfigMocks.config(), file,
                Set.of(ConfigurationExporter.ConfigCategory.SAVED_IMAGES));
        assertFalse(Files.readString(file.toPath()).contains("Backup Old Logo"), "the trash stays out");

        /*  An empty library on another machine gets them with name, description, favorite and the same file  */
        DatabaseManager database = new DatabaseManager(tempDir.resolve("other-db").resolve("other.db"));
        database.initialize();
        SavedImageService target = new SavedImageService(new SavedImageRepository(database),
                tempDir.resolve("other-images"), new LibraryEvents());
        ConfigurationExporter importer = exporter(target);
        ConfigurationExporter.ImportResult result = importer.importConfiguration(file, ConfigMocks.config());
        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals("2 saved images imported", result.getChanges().get("Saved Images"));
        List<SavedImage> imported = target.getAll();
        assertEquals(List.of("Backup Logo", "Backup Word Mark"), imported.stream().map(SavedImage::name).toList());
        assertEquals("red logo, lower right", imported.get(0).description());
        assertTrue(imported.get(0).favorite());
        assertNull(imported.get(1).description());
        assertFalse(imported.get(1).favorite());
        assertEquals(logo.sha256(), imported.get(0).sha256());
        assertEquals(word.sha256(), imported.get(1).sha256());
        assertTrue(Files.isRegularFile(target.fileOf(imported.get(0))), "the library keeps the file");

        /*  A second import stores no copies  */
        result = importer.importConfiguration(file, ConfigMocks.config());
        assertNull(result.getChanges().get("Saved Images"));
        assertEquals(2, target.getAll().size());

        /*  A name taken by another file meanwhile gets a new one  */
        String wordId = imported.get(1).id();
        assertTrue(target.moveToTrash(wordId));
        assertTrue(target.deletePermanently(wordId));
        assertNotNull(target.save(image("other-word.png", Color.GREEN), "Backup Word Mark", null).image());
        result = importer.importConfiguration(file, ConfigMocks.config());
        assertEquals("1 saved images imported", result.getChanges().get("Saved Images"));
        assertEquals(word.sha256(), target.find("Backup Word Mark (1)").sha256());
    }

    @Test
    void aFileThatCannotBeReadToTheEndChangesNothing() throws Exception {
        File file = tempDir.resolve("broken-backup.json").toFile();
        Files.writeString(file.toPath(), """
                {
                  "metadata": { "exportVersion": "1.0", "applicationVersion": "2.0.0" },
                  "configuration": {
                    "apiSettings": { "apiRegion": "EU" },
                    "uiPreferences": { "uiTheme": "dark" },
                    "frames": [ { "id": "broken-backup", "name": "Broken Backup", "aspectRatio": "1:1" } ],
                    "customPalettes": "no list"
                  }
                }
                """);

        art.moodframe.config.ApplicationConfig config = ConfigMocks.config();
        org.mockito.Mockito.clearInvocations(config.accounts(), config.ui());
        ConfigurationExporter.ImportResult result = exporter().importConfiguration(file, config);
        assertFalse(result.isSuccess());
        org.mockito.Mockito.verify(config.accounts(), org.mockito.Mockito.never()).setApiRegion(
                org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(config.ui(), org.mockito.Mockito.never()).setUiTheme(
                org.mockito.ArgumentMatchers.any());
        assertNull(TestLibraryServices.frames().find("broken-backup"), "no frame of a broken file");
    }

    @Test
    void aRestoredMoodFindsItsRestoredBlocks() throws Exception {
        PromptBlockService blocks = TestLibraryServices.blocks();
        MoodService moods = TestLibraryServices.moods(blocks, TestLibraryServices.palettes());
        PromptBlock style = new PromptBlock(null, PromptBlock.Type.STYLE, "Backup Style", "grainy test look", null, true);
        assertTrue(blocks.addCustom(style));
        Mood mood = new Mood(null, "Backup With Block");
        mood.setReference(Mood.Field.STYLE, style.getId());
        assertTrue(moods.add(mood));

        ConfigurationExporter exporter = exporter();
        File file = tempDir.resolve("backup-blocks.json").toFile();
        exporter.exportConfiguration(ConfigMocks.config(), file,
                Set.of(ConfigurationExporter.ConfigCategory.CUSTOM_STYLES, ConfigurationExporter.ConfigCategory.MOODS));

        /*  Block and mood are lost, the backup brings both back and the mood points at the block again  */
        assertTrue(blocks.deleteCustom(style.getId()));
        assertTrue(blocks.deletePermanently(style.getId()));
        assertTrue(moods.moveToTrash(mood.getId()));
        assertTrue(moods.deletePermanently(mood.getId()));
        ConfigurationExporter.ImportResult result = exporter.importConfiguration(file, ConfigMocks.config());
        assertTrue(result.isSuccess(), result.getMessage());
        Mood restored = moods.find(mood.getId());
        assertNotNull(restored);
        assertEquals(style.getId(), restored.getReferences().get(Mood.Field.STYLE));
        assertNotNull(blocks.find(PromptBlock.Type.STYLE, style.getId()));
    }

    @Test
    void aStyleOfTheUserNamedLikeAFormerLookStaysAStyle() {
        PromptBlockService blocks = TestLibraryServices.blocks();
        MoodService moods = TestLibraryServices.moods(blocks, TestLibraryServices.palettes());
        assertNotNull(moods.findForLegacyStyle("Film Noir"));
        assertTrue(blocks.addCustom(new PromptBlock(null, PromptBlock.Type.STYLE, "Film Noir", "my own noir", null, true)));
        assertEquals(null, moods.findForLegacyStyle("Film Noir"));
    }

    /*  an exporter on library services of its own, as the application builds one on its registered services  */
    private static ConfigurationExporter exporter() {
        return exporter(TestLibraryServices.savedImages(tempDir.resolve("library-images"), new LibraryEvents()));
    }

    /*  an exporter on the saved images of a library  */
    private static ConfigurationExporter exporter(SavedImageService savedImages) {
        PromptBlockService blocks = TestLibraryServices.blocks();
        ColorStyleService palettes = TestLibraryServices.palettes();
        return new ConfigurationExporter(blocks, palettes, TestLibraryServices.moods(blocks, palettes),
                TestLibraryServices.frames(), savedImages);
    }

    /*  an image file of a color, so each color is a file of its own content  */
    private static Path image(String name, Color color) throws Exception {
        Path file = tempDir.resolve(name);
        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 40; x++)
            for (int y = 0; y < 20; y++)
                image.setRGB(x, y, color.getRGB());
        ImageIO.write(image, "png", file.toFile());
        return file;
    }
}
