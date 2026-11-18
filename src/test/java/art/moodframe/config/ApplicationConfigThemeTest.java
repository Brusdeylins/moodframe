package art.moodframe.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/** The theme of a fresh configuration and of a stored own choice. */
class ApplicationConfigThemeTest {

    /*  a configuration with the given stored values, without reading or writing ~/.moodframe  */
    private static ApplicationConfig config(Properties stored) throws Exception {
        ApplicationConfig config = mock(ApplicationConfig.class, CALLS_REAL_METHODS);
        Field properties = ApplicationConfig.class.getDeclaredField("properties");
        properties.setAccessible(true);
        properties.set(config, stored);
        Field paths = ApplicationConfig.class.getDeclaredField("paths");
        paths.setAccessible(true);
        paths.set(config, LibraryPaths.under(Path.of("home")));
        return config;
    }

    @Test
    void freshConfigurationDefaultsToPrimerDark() throws Exception {
        ApplicationConfig config = config(new Properties());
        assertEquals("primer-dark", config.getUiTheme());

        /*  the defaults a first start writes  */
        Method loadDefaults = ApplicationConfig.class.getDeclaredMethod("loadDefaults");
        loadDefaults.setAccessible(true);
        loadDefaults.invoke(config);
        assertEquals("primer-dark", config.getUiTheme());
        assertFalse(config.hasUserSetTheme());
    }

    @Test
    void keepsTheThemeTheUserChose() throws Exception {
        Properties stored = new Properties();
        stored.setProperty("ui.theme", "primer-light");
        stored.setProperty("ui.theme.user_set", "true");
        ApplicationConfig config = config(stored);
        assertEquals("primer-light", config.getUiTheme());
        assertTrue(config.hasUserSetTheme());
    }
}
