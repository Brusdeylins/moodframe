package art.moodframe.config;

import art.moodframe.model.ApiProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A settings file that cannot be read is kept as a copy before anything overwrites it, the defaults apply and the
 * start tells about it; a reset never runs beside a save.
 */
class ConfigStoreUnreadableTest {

    @TempDir
    Path home;

    /*  the copies of unreadable files beside the settings file  */
    private List<Path> copies(LibraryPaths paths) throws Exception {
        try (Stream<Path> files = Files.list(paths.root())) {
            return files.filter((Path file) -> file.getFileName().toString()
                    .startsWith("config.properties.unreadable-")).toList();
        }
    }

    @Test
    void anUnreadableFileIsCopiedBeforeTheFirstSave() throws Exception {
        LibraryPaths paths = LibraryPaths.under(home);
        Files.createDirectories(paths.root());
        String unreadable = "gemini.api.key=ENCRYPTED\nui.theme=nord-dark\nbroken=\\uZZZZ\n";
        Files.writeString(paths.configFile(), unreadable);

        ApplicationConfig config = new ApplicationConfig(paths);
        assertTrue(config.wasConfigUnreadable());
        assertEquals("primer-dark", config.ui().getUiTheme(), "the defaults apply");
        config.accounts().setApiKey(ApiProvider.BFL, "bfl-key");

        List<Path> copies = copies(paths);
        assertEquals(1, copies.size());
        assertEquals(copies.get(0), config.unreadableConfigCopy());
        assertEquals(unreadable, Files.readString(copies.get(0)), "the copy keeps the stored keys");
        assertEquals("bfl-key", new ApplicationConfig(paths).accounts().getApiKey(ApiProvider.BFL),
                "the settings are written again");
    }

    @Test
    void aReadableFileLeavesNoCopy() throws Exception {
        LibraryPaths paths = LibraryPaths.under(home);
        new ApplicationConfig(paths).ui().setUiTheme("nord-dark");

        ApplicationConfig config = new ApplicationConfig(paths);

        assertFalse(config.wasConfigUnreadable());
        assertNull(config.unreadableConfigCopy());
        assertEquals("nord-dark", config.ui().getUiTheme());
        assertEquals(List.of(), copies(paths));
    }

    @Test
    void aResetWaitsForASaveInProgress() throws Exception {
        ConfigStore store = new ConfigStore(LibraryPaths.under(home));
        Thread reset = new Thread(store::resetToDefaults, "reset");
        synchronized (store) {
            reset.start();
            long end = System.nanoTime() + 5_000_000_000L;
            while (reset.getState() != Thread.State.BLOCKED && reset.isAlive() && System.nanoTime() < end)
                Thread.onSpinWait();
            assertEquals(Thread.State.BLOCKED, reset.getState(), "the reset waits for the lock of save()");
        }
        reset.join(5000);
        assertFalse(reset.isAlive());
        assertNotNull(store.get("ui.theme"));
    }
}
