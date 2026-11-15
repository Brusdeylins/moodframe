package art.moodframe.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

/** The one unfolded group of the right panel: Mood on the first start, then as the user left it. */
class ApplicationConfigPanelGroupTest {

    /*  a configuration with the given stored values, without reading or writing ~/.moodframe  */
    private static ApplicationConfig config(Properties stored) throws Exception {
        ApplicationConfig config = mock(ApplicationConfig.class, CALLS_REAL_METHODS);
        Field properties = ApplicationConfig.class.getDeclaredField("properties");
        properties.setAccessible(true);
        properties.set(config, stored);
        doNothing().when(config).saveConfiguration();
        return config;
    }

    @Test
    void moodIsUnfoldedOnTheFirstStart() throws Exception {
        assertEquals("mood", config(new Properties()).getOpenPanelGroup());
    }

    @Test
    void remembersTheUnfoldedGroupAndThatAllAreFolded() throws Exception {
        Properties stored = new Properties();
        ApplicationConfig config = config(stored);

        config.setOpenPanelGroup("frame");
        assertEquals("frame", config(stored).getOpenPanelGroup());

        config.setOpenPanelGroup("");
        assertEquals("", config(stored).getOpenPanelGroup());
    }

    @Test
    void ignoresTheFormerFoldStateOfEachGroup() throws Exception {
        Properties stored = new Properties();
        stored.setProperty("ui.panel.mood.expanded", "false");
        stored.setProperty("ui.panel.frame.expanded", "true");
        assertEquals("mood", config(stored).getOpenPanelGroup());
    }
}
