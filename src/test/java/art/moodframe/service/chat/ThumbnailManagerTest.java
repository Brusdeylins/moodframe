package art.moodframe.service.chat;

import art.moodframe.config.LibraryPaths;
import art.moodframe.util.ImageSize;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The size of new and old thumbnails and their enlargement on demand. */
class ThumbnailManagerTest {

    @TempDir
    static Path tempDir;

    private static ThumbnailManager thumbnails;
    private static Path images;

    /*  The thumbnail manager works in the library of a temporary home  */
    @BeforeAll
    static void createManager() throws Exception {
        thumbnails = new ThumbnailManager(LibraryPaths.under(tempDir));
        images = thumbnails.getOriginalImagesDirectory();
    }

    @AfterAll
    static void shutdown() {
        thumbnails.shutdown();
    }

    /** Writes an image file of a size into the images directory. */
    private static String original(String name, int width, int height) throws Exception {
        File file = images.resolve(name + ".png").toFile();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", file);
        return file.getAbsolutePath();
    }

    private static ImageSize thumbnailSize(String original) {
        return ImageSize.read(Path.of(thumbnails.getThumbnailPath(original)));
    }

    @Test
    void aNewThumbnailHas800PxOnItsLongerSide() throws Exception {
        String landscape = original("landscape", 2000, 1000);
        String portrait = original("portrait", 900, 1800);

        thumbnails.getThumbnail(landscape).get(30, TimeUnit.SECONDS);
        thumbnails.getThumbnail(portrait).get(30, TimeUnit.SECONDS);
        assertEquals(new ImageSize(800, 400), thumbnailSize(landscape));
        assertEquals(new ImageSize(400, 800), thumbnailSize(portrait));
    }

    @Test
    void anOldThumbnailOf400PxStaysUntilItIsEnlargedOnDemand() throws Exception {
        String original = original("old", 1600, 1200);
        File thumbnail = new File(thumbnails.getThumbnailPath(original));
        ImageIO.write(new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB), "jpg", thumbnail);
        assertTrue(thumbnail.setLastModified(new File(original).lastModified() + 1000));

        /*  Neither the start nor showing it replaces it, only the request of a view that shows it too small  */
        assertTrue(thumbnails.thumbnailExists(original));
        assertEquals(thumbnail, thumbnails.getThumbnail(original).get(30, TimeUnit.SECONDS));
        assertEquals(new ImageSize(400, 300), thumbnailSize(original));

        CompletableFuture<Boolean> enlarged = thumbnails.enlargeThumbnail(original);
        assertTrue(enlarged.get(30, TimeUnit.SECONDS));
        assertEquals(new ImageSize(800, 600), thumbnailSize(original));
        assertSame(enlarged, thumbnails.enlargeThumbnail(original));
    }

    @Test
    void aThumbnailOfFullSizeIsNotCreatedAnew() throws Exception {
        String small = original("small", 600, 300);
        String large = original("large", 1200, 1200);
        thumbnails.getThumbnail(small).get(30, TimeUnit.SECONDS);
        thumbnails.getThumbnail(large).get(30, TimeUnit.SECONDS);

        assertFalse(thumbnails.enlargeThumbnail(small).get(30, TimeUnit.SECONDS));
        assertFalse(thumbnails.enlargeThumbnail(large).get(30, TimeUnit.SECONDS));
        assertFalse(thumbnails.enlargeThumbnail(images.resolve("missing.png").toString()).get(30, TimeUnit.SECONDS));
        assertEquals(new ImageSize(600, 300), thumbnailSize(small));
        assertEquals(new ImageSize(800, 800), thumbnailSize(large));
    }
}
