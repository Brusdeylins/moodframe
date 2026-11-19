package art.moodframe.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The folders of the library: where they are by default and that the configured images folder counts. */
class LibraryPathsTest {

    @TempDir
    Path home;

    @Test
    void theLibraryLivesInDotMoodframeOfTheHome() {
        LibraryPaths paths = LibraryPaths.under(home);
        Path root = home.resolve(".moodframe");
        assertEquals(root, paths.root());
        assertEquals(root.resolve("config.properties"), paths.configFile());
        assertEquals(root.resolve("secret.key"), paths.secretKey());
        assertEquals(root.resolve("db").resolve("moodframe.db"), paths.database());
        assertEquals(root.resolve("images"), paths.images());
        assertEquals(root.resolve("thumbnails"), paths.thumbnails());
        assertEquals(root.resolve("trash"), paths.trash());
        assertEquals(root.resolve("logs"), paths.logs());
        assertEquals(root.resolve("certificates"), paths.certificates());
        assertEquals(home.resolve("Downloads"), paths.downloads());
    }

    @Test
    void aFreshConfigurationWritesTheDefaultImagesFolderAsBefore() {
        LibraryPaths paths = LibraryPaths.under(home);
        ConfigStore store = new ConfigStore(paths);
        ApplicationConfig config = new ApplicationConfig(store);

        /*  the same string as the former ~/.moodframe constant, so no stored path moves; the database and the logs
            are no settings  */
        assertEquals(home + "/.moodframe/images", store.get("path.images"));
        assertNull(store.get("path.database"));
        assertNull(store.get("path.logs"));
        assertEquals(paths, config.paths());
    }

    @Test
    void theDatabaseAndTheLogsStayInTheLibraryWhateverAFormerFileSays() throws Exception {
        LibraryPaths paths = LibraryPaths.under(home);
        Files.createDirectories(paths.root());
        Files.writeString(paths.configFile(), "path.database=/elsewhere/other.db\npath.logs=/elsewhere/logs\n");

        ApplicationConfig config = new ApplicationConfig(paths);

        assertEquals(paths.database(), config.paths().database());
        assertEquals(paths.logs(), config.paths().logs());
    }

    @Test
    void theConfiguredImagesFolderCountsEverywhere() {
        ConfigStore store = new ConfigStore(LibraryPaths.under(home));
        ApplicationConfig config = new ApplicationConfig(store);
        Path elsewhere = home.resolve("pictures");
        store.set("path.images", elsewhere.toString());

        assertEquals(elsewhere, config.paths().images());
        assertEquals(home.resolve(".moodframe").resolve("thumbnails"), config.paths().thumbnails());
    }
}
