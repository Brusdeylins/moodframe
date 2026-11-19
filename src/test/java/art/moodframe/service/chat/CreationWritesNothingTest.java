package art.moodframe.service.chat;

import art.moodframe.config.LibraryPaths;
import art.moodframe.service.trash.TrashCenter;
import art.moodframe.service.trash.TrashCleanupService;
import art.moodframe.service.trash.TrashManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Creating a service reads and writes nothing (docs/ARCHITECTURE.md): what it does once per start is its start(). */
class CreationWritesNothingTest {

    @TempDir
    Path home;

    private static long cleanupThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter((Thread thread) -> thread.getName().equals("TrashCleanupService")).count();
    }

    @Test
    void theThumbnailsAndTheTrashCreateNoFolder() throws Exception {
        LibraryPaths paths = LibraryPaths.under(home);
        ThumbnailManager thumbnails = new ThumbnailManager(paths);
        new TrashManager(paths, 60);
        thumbnails.shutdown();
        try (Stream<Path> files = Files.walk(home)) {
            assertEquals(java.util.List.of(home), files.toList(), "nothing is created in the library");
        }
    }

    @Test
    void theTrashCleanupRunsOnlyOnceStarted() {
        long before = cleanupThreads();
        TrashCleanupService cleanup = new TrashCleanupService(new TrashCenter());
        assertEquals(before, cleanupThreads(), "no thread before the start");
        cleanup.start();
        cleanup.start();
        assertEquals(before + 1, cleanupThreads(), "one thread, however often it is started");
        cleanup.shutdown();
    }
}
