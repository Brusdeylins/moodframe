package art.moodframe.service.chat;

import art.moodframe.config.LibraryPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The thumbnails of the existing images are made without a thread of the pool waiting for another one: more batches
 * at once than the pool has threads still end, and every thread of a manager has a name of its own.
 */
class ThumbnailBatchTest {

    @TempDir
    Path tempDir;

    @Test
    void moreBatchesThanThreadsEndAndEveryThreadHasItsOwnName() throws Exception {
        ThumbnailManager thumbnails = new ThumbnailManager(LibraryPaths.under(tempDir));
        try {
            int images = ThumbnailManager.THUMBNAIL_THREAD_POOL_SIZE + 2;
            List<String> originals = new ArrayList<>();
            for (int index = 0; index < images; index++) {
                File file = thumbnails.getOriginalImagesDirectory().resolve("image-" + index + ".png").toFile();
                ImageIO.write(new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB), "png", file);
                originals.add(file.getAbsolutePath());
            }

            List<CompletableFuture<Integer>> batches = new ArrayList<>();
            for (int batch = 0; batch <= ThumbnailManager.THUMBNAIL_THREAD_POOL_SIZE; batch++)
                batches.add(thumbnails.generateThumbnailsForExistingImages());
            CompletableFuture.allOf(batches.toArray(CompletableFuture[]::new)).get(60, TimeUnit.SECONDS);

            for (String original : originals)
                assertTrue(thumbnails.thumbnailExists(original), original);
            assertEquals(0, (int) thumbnails.generateThumbnailsForExistingImages().get(30, TimeUnit.SECONDS),
                    "nothing is left to do");

            List<String> names = Thread.getAllStackTraces().keySet().stream().map(Thread::getName)
                    .filter((String name) -> name.startsWith("ThumbnailGenerator-")).toList();
            assertEquals(names.size(), names.stream().distinct().count(), "unique names: " + names);
        }
        finally {
            thumbnails.shutdown();
        }
    }
}
