package art.moodframe.ui.support;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The size of the cached images, the limit of the cache and loads of one file and of several files at once. */
class ImageCacheTest {

    @TempDir
    static Path tempDir;

    /*  the cached images are JavaFX images, which need the toolkit  */
    @BeforeAll
    static void startTheToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        }
        catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    /** Writes an image file of a size. */
    private static String file(String name, int width, int height) throws Exception {
        File file = tempDir.resolve(name + ".png").toFile();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", file);
        return file.getAbsolutePath();
    }

    @Test
    void aFileIsLoadedAtTheSizeOfTheThumbnails() throws Exception {
        ImageCache cache = new ImageCache(32_000_000.0);
        String path = file("large", 1600, 900);

        assertNull(cache.getIfCached(path));
        Image image = cache.get(path);
        assertEquals(800, image.getWidth());
        assertEquals(450, image.getHeight());
        assertSame(image, cache.getIfCached(path));
        assertNull(cache.get(tempDir.resolve("missing.png").toString()));

        cache.remove(path);
        assertNull(cache.getIfCached(path));
    }

    @Test
    void theCacheHoldsAboutFiftyThumbnailsOfFullSize() throws Exception {
        ImageCache cache = new ImageCache(32_000_000.0);
        String[] paths = new String[60];
        for (int i = 0; i < paths.length; i++)
            paths[i] = file("full-" + i, 800, 800);

        for (String path : paths)
            assertNotNull(cache.get(path));
        assertNull(cache.getIfCached(paths[0]));
        assertNull(cache.getIfCached(paths[9]));
        assertNotNull(cache.getIfCached(paths[10]));
        assertNotNull(cache.getIfCached(paths[59]));
        assertEquals(50, cache.size(), "the memory display counts the images held, not the ones loaded once");

        cache.remove(paths[59]);
        assertEquals(49, cache.size());
    }

    @Test
    void aFileIsDecodedOnceWhileOtherFilesLoadMeanwhile() throws Exception {
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        Map<String, AtomicInteger> decodes = new ConcurrentHashMap<>();
        ImageCache cache = new ImageCache(32_000_000.0, (String path) -> {
            decodes.computeIfAbsent(path, (String key) -> new AtomicInteger()).incrementAndGet();
            if (path.equals("slow")) {
                slowStarted.countDown();
                try {
                    assertTrue(releaseSlow.await(10, TimeUnit.SECONDS));
                }
                catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
            return new WritableImage(4, 4);
        });

        /*  a slow file is being read: another file loads at once, a second caller of the slow one waits for it  */
        ExecutorService callers = Executors.newFixedThreadPool(3);
        CompletableFuture<Image> first = CompletableFuture.supplyAsync(() -> cache.get("slow"), callers);
        assertTrue(slowStarted.await(10, TimeUnit.SECONDS));
        CompletableFuture<Image> second = CompletableFuture.supplyAsync(() -> cache.get("slow"), callers);
        Image fast = CompletableFuture.supplyAsync(() -> cache.get("fast"), callers).get(5, TimeUnit.SECONDS);
        assertNotNull(fast);
        assertSame(fast, cache.getIfCached("fast"), "the cache answers while a file is read");
        Thread.sleep(100);
        assertFalse(second.isDone(), "the second caller waits for the load of the first");

        releaseSlow.countDown();
        Image slow = first.get(5, TimeUnit.SECONDS);
        assertSame(slow, second.get(5, TimeUnit.SECONDS));
        assertEquals(1, decodes.get("slow").get(), "the slow file is decoded once");
        assertEquals(1, decodes.get("fast").get());
        callers.shutdown();
    }
}
