package art.moodframe.service;

import art.moodframe.util.ImageSize;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The size of new and old thumbnails, their enlargement on demand and the limit of the image cache. */
class ThumbnailManagerTest {

    @TempDir
    static Path tempDir;

    private static ThumbnailManager thumbnails;
    private static Path images;

    /*  The thumbnail manager takes its directories from the home directory once, when it is created; the cached
        images are JavaFX images, which need the toolkit  */
    @BeforeAll
    static void createManager() throws Exception {
        String home = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        try {
            thumbnails = new ThumbnailManager();
        }
        finally {
            System.setProperty("user.home", home);
        }
        images = thumbnails.getOriginalImagesDirectory();

        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        }
        catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await(10, TimeUnit.SECONDS);
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
    void theCacheLoadsAThumbnailAtItsFullSize() throws Exception {
        String original = original("cached", 1600, 900);
        File thumbnail = thumbnails.getThumbnail(original).get(30, TimeUnit.SECONDS);

        javafx.scene.image.Image image = thumbnails.getCachedImage(thumbnail.getAbsolutePath());
        assertEquals(800, image.getWidth());
        assertEquals(450, image.getHeight());
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

    @Test
    void theCacheHoldsAboutFiftyThumbnailsOfFullSize() throws Exception {
        String[] paths = new String[60];
        for (int i = 0; i < paths.length; i++) {
            paths[i] = tempDir.resolve("full-" + i + ".png").toString();
            ImageIO.write(new BufferedImage(800, 800, BufferedImage.TYPE_INT_RGB), "png", new File(paths[i]));
        }

        for (String path : paths)
            assertNotNull(thumbnails.getCachedImage(path));
        assertNull(thumbnails.getFromCacheOnly(paths[0]));
        assertNull(thumbnails.getFromCacheOnly(paths[9]));
        assertNotNull(thumbnails.getFromCacheOnly(paths[10]));
        assertNotNull(thumbnails.getFromCacheOnly(paths[59]));
    }
}
