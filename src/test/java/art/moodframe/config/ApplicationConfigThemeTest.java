package art.moodframe.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The theme of a fresh configuration and of a stored own choice. */
class ApplicationConfigThemeTest {

    @TempDir
    Path home;

    @Test
    void freshConfigurationDefaultsToPrimerDark() {
        /*  the defaults a first start writes  */
        ApplicationConfig config = new ApplicationConfig(LibraryPaths.under(home));
        assertEquals("primer-dark", config.ui().getUiTheme());
        assertEquals("primer-dark", config.getProperty("ui.theme"));
        assertFalse(config.ui().hasUserSetTheme());
    }

    @Test
    void keepsTheThemeTheUserChose() {
        new ApplicationConfig(LibraryPaths.under(home)).ui().setUiTheme("primer-light");

        ApplicationConfig config = new ApplicationConfig(LibraryPaths.under(home));
        assertEquals("primer-light", config.ui().getUiTheme());
        assertTrue(config.ui().hasUserSetTheme());
    }
}
