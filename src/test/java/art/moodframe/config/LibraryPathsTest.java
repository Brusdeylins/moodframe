package art.moodframe.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void aFreshConfigurationWritesTheDefaultFoldersAsBefore() {
        LibraryPaths paths = LibraryPaths.under(home);
        ApplicationConfig config = new ApplicationConfig(paths);

        /*  the same strings as the former ~/.moodframe constants, so no stored path moves  */
        String root = home + "/.moodframe";
        assertEquals(root + "/images", config.getProperty("path.images"));
        assertEquals(root + "/db/moodframe.db", config.getProperty("path.database"));
        assertEquals(root + "/logs", config.getProperty("path.logs"));
        assertEquals(paths, config.paths());
    }

    @Test
    void theConfiguredImagesFolderCountsEverywhere() {
        ApplicationConfig config = new ApplicationConfig(LibraryPaths.under(home));
        Path elsewhere = home.resolve("pictures");
        config.setProperty("path.images", elsewhere.toString());

        assertEquals(elsewhere, config.paths().images());
        assertEquals(home.resolve(".moodframe").resolve("thumbnails"), config.paths().thumbnails());
    }
}
