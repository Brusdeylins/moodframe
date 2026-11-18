package art.moodframe.ui.support;

import art.moodframe.service.chat.ThumbnailManager;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The decoded JavaFX images of the thumbnails (and of originals shown in their place) by the path of their file, so
 * scrolling shows them again without reading the disk. The least recently used give way once the cache holds more
 * pixels than its limit; the newest one always stays.
 *
 * <p>A file is decoded on the calling thread, never on the JavaFX thread, and only one caller decodes it at a time:
 * others asking for the same file wait for that load, loads of other files run in parallel. No lock is held while a
 * file is read.</p>
 *
 * @since 2.1.3
 */
public final class ImageCache {

    private static final Logger logger = LoggerFactory.getLogger(ImageCache.class);

    /*  as many as 200 thumbnails of 400 by 400 px or 50 of 800 by 800 px, about 128 MB  */
    private static final double DEFAULT_PIXELS = 32_000_000.0;

    private static final ImageCache SHARED = new ImageCache(DEFAULT_PIXELS);

    private final double maxPixels;
    private final Function<String, Image> decoder;

    /*  the images in the order of their last use, guarded by itself; only map operations run under its lock  */
    private final LinkedHashMap<String, Image> images = new LinkedHashMap<>(128, 0.75f, true);

    /*  the loads in progress by path, so a file is decoded once  */
    private final Map<String, CompletableFuture<Image>> loading = new ConcurrentHashMap<>();

    /**
     * Creates a cache.
     *
     * @param maxPixels the pixels the cache holds at most, besides its newest image
     */
    public ImageCache(double maxPixels) {
        this(maxPixels, ImageCache::decode);
    }

    /*  a cache with another decoder, for the tests  */
    ImageCache(double maxPixels, Function<String, Image> decoder) {
        this.maxPixels = maxPixels;
        this.decoder = decoder;
    }

    /**
     * The cache of the application, shared by the chat entries, the images view and the full view.
     *
     * @return the cache
     */
    public static ImageCache shared() {
        return SHARED;
    }

    /**
     * Gets an image from the cache only, without reading the disk; fast enough for the JavaFX thread.
     *
     * @param path the path of the file
     * @return the image, null if it is not in the cache
     */
    public Image getIfCached(String path) {
        if (path == null || path.isEmpty())
            return null;
        synchronized (images) {
            return images.get(path);
        }
    }

    /**
     * Gets the image of a file, decoding it if it is not in the cache. It is decoded completely at the size of the
     * thumbnails, so an original is scaled down on loading. Call it off the JavaFX thread.
     *
     * @param path the path of the file
     * @return the image, null if the file is missing or cannot be decoded
     */
    public Image get(String path) {
        Image cached = getIfCached(path);
        if (cached != null || path == null || path.isEmpty())
            return cached;
        CompletableFuture<Image> load = new CompletableFuture<>();
        CompletableFuture<Image> running = loading.putIfAbsent(path, load);
        if (running != null)
            return running.join();
        try {
            /*  a load that ended right before this one began  */
            Image image = getIfCached(path);
            if (image == null)
                image = decoder.apply(path);
            if (image != null)
                put(path, image);
            load.complete(image);
            return image;
        }
        catch (RuntimeException e) {
            load.complete(null);
            throw e;
        }
        finally {
            loading.remove(path, load);
        }
    }

    /**
     * Forgets the image of a file, for example a thumbnail that was created anew.
     *
     * @param path the path of the file
     */
    public void remove(String path) {
        synchronized (images) {
            images.remove(path);
        }
    }

    /*  Decodes a file completely (no background loading, so no incomplete image gets cached and flickers)  */
    private static Image decode(String path) {
        try {
            File file = new File(path);
            if (!file.exists())
                return null;
            return new Image(file.toURI().toString(), ThumbnailManager.MAX_THUMBNAIL_SIZE,
                    ThumbnailManager.MAX_THUMBNAIL_SIZE, true, true, false);
        }
        catch (Exception e) {
            logger.warn("Failed to load cached image: {}", path, e);
            return null;
        }
    }

    /*  Adds an image; the least recently used give way while the cache holds more pixels than its limit  */
    private void put(String path, Image image) {
        synchronized (images) {
            images.put(path, image);
            double pixels = images.values().stream().mapToDouble((Image held) -> held.getWidth() * held.getHeight())
                    .sum();
            Iterator<Image> eldest = images.values().iterator();
            while (pixels > maxPixels && images.size() > 1) {
                Image held = eldest.next();
                pixels -= held.getWidth() * held.getHeight();
                eldest.remove();
            }
        }
    }
}
