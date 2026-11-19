package art.moodframe.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The theme of a fresh configuration, of a stored own choice and of the followed theme of the system. */
class ApplicationConfigThemeTest {

    @TempDir
    Path home;

    @Test
    void freshConfigurationDefaultsToPrimerDark() {
        /*  the defaults a first start writes  */
        ConfigStore store = new ConfigStore(LibraryPaths.under(home));
        ApplicationConfig config = new ApplicationConfig(store);
        assertEquals("primer-dark", config.ui().getUiTheme());
        assertEquals("primer-dark", store.get("ui.theme"));
        assertFalse(config.ui().hasUserSetTheme());
    }

    @Test
    void keepsTheThemeTheUserChose() {
        new ApplicationConfig(LibraryPaths.under(home)).ui().setUiTheme("primer-light");

        ApplicationConfig config = new ApplicationConfig(LibraryPaths.under(home));
        assertEquals("primer-light", config.ui().getUiTheme());
        assertTrue(config.ui().hasUserSetTheme());
    }

    @Test
    void theThemeOfTheSystemIsStoredAsNotChosen() {
        ApplicationConfig chosen = new ApplicationConfig(LibraryPaths.under(home));
        chosen.ui().setUiTheme("nord-dark");
        chosen.ui().setSystemTheme("primer-light");

        /*  the next start asks the system again  */
        ApplicationConfig config = new ApplicationConfig(LibraryPaths.under(home));
        assertEquals("primer-light", config.ui().getUiTheme());
        assertFalse(config.ui().hasUserSetTheme());
    }
}
