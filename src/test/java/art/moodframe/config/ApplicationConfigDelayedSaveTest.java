package art.moodframe.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Values dragged with the mouse are written once after a quiet time, and at the latest on shutdown. The configuration
 * lives in a temporary home.
 */
class ApplicationConfigDelayedSaveTest {

    @TempDir
    Path home;

    /*  a store that counts its saves after it was read  */
    private static final class CountingStore extends ConfigStore {
        final AtomicInteger saves = new AtomicInteger();

        CountingStore(LibraryPaths paths) {
            super(paths);
        }

        @Override
        public synchronized void save() {
            super.save();
            if (saves != null)
                saves.incrementAndGet();
        }
    }

    /*  a configuration on a counting store  */
    private static final class CountingConfig extends ApplicationConfig {
        final AtomicInteger saves;

        CountingConfig(LibraryPaths paths) {
            this(new CountingStore(paths));
        }

        private CountingConfig(CountingStore store) {
            super(store);
            saves = store.saves;
        }
    }

    /*  every value a drag changes pixel by pixel  */
    private static void drag(ApplicationConfig config) {
        for (int i = 0; i < 50; i++) {
            config.ui().setSplitterPosition(0.2 + i / 1000.0);
            config.ui().setWindowBounds(10 + i, 20, 1200 + i, 800, false);
            config.ui().setImageTileSize(180 + i);
            config.ui().setChatInputHeight(100 + i);
        }
    }

    /*  waits until the count is reached or a few quiet times passed  */
    private static void awaitSaves(CountingConfig config, int count) throws InterruptedException {
        long end = System.currentTimeMillis() + ApplicationConfig.SAVE_DELAY_MILLIS * 10;
        while (config.saves.get() < count && System.currentTimeMillis() < end)
            Thread.sleep(20);
    }

    @Test
    void writesADragOnceAfterTheQuietTime() throws Exception {
        CountingConfig config = new CountingConfig(LibraryPaths.under(home));

        drag(config);
        assertEquals(0, config.saves.get());
        awaitSaves(config, 1);
        Thread.sleep(ApplicationConfig.SAVE_DELAY_MILLIS * 2);
        assertEquals(1, config.saves.get());

        /*  what is stored stays the same, only the moment of writing changes  */
        ApplicationConfig stored = new ApplicationConfig(LibraryPaths.under(home));
        assertEquals(0.249, stored.ui().getSplitterPosition());
        assertEquals(59.0, stored.ui().getWindowX());
        assertEquals(1249, stored.ui().getWindowWidth());
        assertEquals(229.0, stored.ui().getImageTileSize());
        assertEquals(149.0, stored.ui().getChatInputHeight());
    }

    @Test
    void writesAWaitingSaveAtOnceOnShutdown() throws Exception {
        CountingConfig config = new CountingConfig(LibraryPaths.under(home));

        drag(config);
        config.ui().setSplitterPosition(0.3);
        config.flushConfiguration();
        assertEquals(1, config.saves.get());
        assertEquals(0.3, new ApplicationConfig(LibraryPaths.under(home)).ui().getSplitterPosition());

        /*  the delayed save is gone, nothing is written twice  */
        Thread.sleep(ApplicationConfig.SAVE_DELAY_MILLIS * 2);
        assertEquals(1, config.saves.get());
    }

    @Test
    void shutdownWritesNothingWithoutAWaitingSave() {
        CountingConfig config = new CountingConfig(LibraryPaths.under(home));

        config.flushConfiguration();
        assertEquals(0, config.saves.get());
    }

    @Test
    void otherSettingsAreWrittenAtOnce() {
        CountingConfig config = new CountingConfig(LibraryPaths.under(home));

        config.ui().setOpenPanelGroup("frame");
        assertEquals(1, config.saves.get());
    }
}
