package art.moodframe.ui.component;

import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryFrame;
import art.moodframe.model.EntryMood;
import art.moodframe.model.GenerationModel;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The columns of the images view and the line of an image in its full view. */
class ImageGridTest {

    /*  The grid is a control, which needs the toolkit before its class is loaded  */
    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await(10, TimeUnit.SECONDS);
    }

    @Test
    void tilesAtLeastAsWideAsTheChosenSizeFillTheWidth() {
        assertEquals(5, ImageGrid.columns(1000, 180, 400));
        assertEquals(8, ImageGrid.columns(1600, 180, 400));
        assertEquals(9, ImageGrid.columns(1000, 100, 400));
    }

    @Test
    void thereAreAlwaysTwoColumnsAtLeast() {
        assertEquals(2, ImageGrid.columns(300, 180, 400));
        assertEquals(2, ImageGrid.columns(0, 180, 400));
    }

    @Test
    void tilesNeverGetWiderThanTheThumbnailsAllow() {
        int columns = ImageGrid.columns(1000, 200, 200);
        assertEquals(5, columns);
        assertTrue((1000 - 10.0 * (columns - 1)) / columns <= 200);
    }

    @Test
    void theLineOfAnImageNamesMoodFrameAndModel() {
        GenerationModel model = GenerationModel.getDefault();
        ChatEntry entry = new ChatEntry.Builder().userPrompt("a harbor at dawn")
                .mood(new EntryMood("std:mood:golden-coast", "Golden Coast", Map.of()))
                .frame(new EntryFrame("std:frame:landscape-print", "Landscape print", "3:2", "2K"))
                .model(model.getDisplayName()).build();
        assertEquals("Golden Coast · Landscape print · " + model.getFamilyName() + " · " + model.getResolutionLabel(),
                ImageGrid.caption(entry));
    }

    @Test
    void theLineLeavesOutWhatTheEntryLacks() {
        ChatEntry entry = new ChatEntry.Builder().userPrompt("a wide street").size("16:9").model("gpt-image-1")
                .build();
        assertEquals("16:9 · gpt-image-1", ImageGrid.caption(entry));
    }
}
