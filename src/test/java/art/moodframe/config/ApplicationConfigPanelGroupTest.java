package art.moodframe.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The one unfolded group of the right panel: Mood on the first start, then as the user left it. */
class ApplicationConfigPanelGroupTest {

    @TempDir
    Path home;

    /*  the configuration as stored in the temporary home, read anew  */
    private UiState stored() {
        return new ApplicationConfig(LibraryPaths.under(home)).ui();
    }

    @Test
    void moodIsUnfoldedOnTheFirstStart() {
        assertEquals("mood", stored().getOpenPanelGroup());
    }

    @Test
    void remembersTheUnfoldedGroupAndThatAllAreFolded() {
        UiState ui = stored();

        ui.setOpenPanelGroup("frame");
        assertEquals("frame", stored().getOpenPanelGroup());

        ui.setOpenPanelGroup("");
        assertEquals("", stored().getOpenPanelGroup());
    }

    @Test
    void ignoresTheFormerFoldStateOfEachGroup() {
        ConfigStore store = new ConfigStore(LibraryPaths.under(home));
        store.set("ui.panel.mood.expanded", "false");
        store.set("ui.panel.frame.expanded", "true");
        assertEquals("mood", stored().getOpenPanelGroup());
    }
}
