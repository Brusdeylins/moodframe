package art.moodframe.service.library;

import art.moodframe.database.TestDatabase;
import art.moodframe.model.SavedImage;
import art.moodframe.service.trash.TrashCenter;
import art.moodframe.service.trash.TrashEntry;
import art.moodframe.service.trash.TrashGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The saved images in a fresh SQLite database: saving keeps one copy of a file, the same file is found again instead of
 * a second copy, a name is taken only once, and the trash takes an image and gives it back.
 */
class SavedImageServiceTest {

    @TempDir
    static Path tempDir;

    private static Path folder;
    private static SavedImageService savedImages;
    private static final List<String> reported = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void createDatabase() {
        TestDatabase.open(tempDir.resolve("db").resolve("saved.db").toString());
        LibraryEvents events = new LibraryEvents();
        events.addListener((LibraryEvents.Kind kind, String id) -> reported.add(kind + ":" + id), Runnable::run);
        folder = tempDir.resolve("library-images");
        savedImages = TestLibraryServices.savedImages(folder, events);
    }

    /*  an image file of a color, so each color is a file of its own content  */
    private static Path image(String name, Color color, int width, int height) throws IOException {
        Path file = tempDir.resolve(name);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                image.setRGB(x, y, color.getRGB());
        ImageIO.write(image, "png", file.toFile());
        return file;
    }

    private static long filesInFolder() throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.count();
        }
    }

    @Test
    void savesAFileOnceAndFindsItAgain() throws IOException {
        Path logo = image("acme-red.png", Color.RED, 64, 32);
        SavedImageService.Saved saved = savedImages.save(logo, "  ACME Logo (red) ", "red logo, lower right");
        assertEquals(SavedImageService.Outcome.SAVED, saved.outcome());
        SavedImage image = saved.image();
        assertEquals("ACME Logo (red)", image.name());
        assertEquals(64, image.width());
        assertEquals(32, image.height());
        assertEquals("PNG", image.format());
        assertEquals("red logo, lower right", image.description());
        assertTrue(Files.isRegularFile(savedImages.fileOf(image)), "the library keeps a copy");
        assertTrue(reported.contains("SAVED_IMAGE:" + image.id()), "the main window learns of it");

        /*  The same content under another file name is no second copy; the image saved is answered  */
        Path again = Files.copy(logo, tempDir.resolve("copy-of-logo.png"));
        long files = filesInFolder();
        SavedImageService.Saved same = savedImages.save(again, "Another Name", null);
        assertEquals(SavedImageService.Outcome.EXISTS, same.outcome());
        assertEquals(image.id(), same.image().id());
        assertEquals(files, filesInFolder());
        assertEquals(image.id(), savedImages.sameFile(again).id());
        assertTrue(savedImages.rename(image.id(), "ACME Logo"), "an image saved already is renamed");

        /*  Found by id and by name; the copy stays when the file it came from is gone  */
        Files.delete(logo);
        assertEquals("ACME Logo", savedImages.find(image.id()).name());
        assertEquals(image.id(), savedImages.find("ACME Logo").id());
        assertTrue(Files.isRegularFile(savedImages.fileOf(savedImages.find("ACME Logo"))));
    }

    @Test
    void refusesANameTakenTwice() throws IOException {
        SavedImage word = savedImages.save(image("word-mark.png", Color.GREEN, 120, 40), "Word Mark", null).image();
        assertNotNull(word);
        assertEquals(SavedImageService.Outcome.NAME_TAKEN,
                savedImages.save(image("other.png", Color.BLUE, 20, 20), "Word Mark", null).outcome());
        assertNull(savedImages.sameFile(tempDir.resolve("other.png")), "nothing was saved");
        assertEquals(SavedImageService.Outcome.FAILED,
                savedImages.save(tempDir.resolve("other.png"), "  ", null).outcome(), "a name is required");

        SavedImage photo = savedImages.save(tempDir.resolve("other.png"), "Product Photo", null).image();
        assertFalse(savedImages.rename(photo.id(), "Word Mark"), "the name of another image is refused");
        assertTrue(savedImages.rename(photo.id(), "Product Photo"), "its own name is free");
        assertTrue(savedImages.describe(photo.id(), "  a bottle  "));
        assertEquals("a bottle", savedImages.find(photo.id()).description());
    }

    @Test
    void theTrashTakesAnImageAndGivesItBack() throws IOException {
        TrashCenter trash = new TrashCenter();
        trash.register(new SavedImageTrashGroup(savedImages));
        SavedImage bottle = savedImages.save(image("bottle.png", Color.ORANGE, 30, 60), "Bottle", "a bottle").image();
        Path file = savedImages.fileOf(bottle);

        assertTrue(savedImages.moveToTrash(bottle.id()));
        assertNull(savedImages.find(bottle.id()), "an image in the trash is not in use");
        assertFalse(savedImages.exists("Bottle"));
        List<TrashEntry> listed = trash.list(TrashGroup.Kind.SAVED_IMAGES);
        assertEquals(List.of("Bottle"), listed.stream().map(TrashEntry::name).toList());
        assertEquals("30 × 60 · PNG · a bottle", listed.get(0).context());
        assertTrue(Files.isRegularFile(file), "its file waits in the library");

        /*  Its name is free meanwhile; restored, it takes the next free one  */
        SavedImage taker = savedImages.save(image("taker.png", Color.CYAN, 10, 10), "Bottle", null).image();
        assertTrue(trash.restore(TrashGroup.Kind.SAVED_IMAGES, bottle.id()));
        assertEquals("Bottle (1)", savedImages.find(bottle.id()).name());

        /*  Saving its file again while it is in the trash brings it back under the new name  */
        assertTrue(savedImages.moveToTrash(bottle.id()));
        SavedImageService.Saved again = savedImages.save(tempDir.resolve("bottle.png"), "Bottle Again", null);
        assertEquals(SavedImageService.Outcome.SAVED, again.outcome());
        assertEquals(bottle.id(), again.image().id());
        assertEquals("Bottle Again", again.image().name());

        /*  Removed for good with its file; an image in use stays  */
        assertFalse(savedImages.deletePermanently(taker.id()), "an image in use is not removed");
        assertTrue(savedImages.moveToTrash(bottle.id()));
        assertEquals(List.of(bottle.id()), trash.deletePermanently(TrashGroup.Kind.SAVED_IMAGES,
                List.of(bottle.id())));
        assertFalse(Files.exists(file), "its file is gone");
        assertTrue(trash.list(TrashGroup.Kind.SAVED_IMAGES).isEmpty());
    }
}
