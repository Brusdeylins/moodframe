package art.moodframe.ui.component;

import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryFrame;
import art.moodframe.model.EntryMood;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationModel;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The columns of the images view, the images it shows and the line of an image in its full view. */
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

    private static <T> T onFxThread(Supplier<T> step) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            result.set(step.get());
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        return result.get();
    }

    /*  An entry with one finished image, whose file the grid does not look for  */
    private static ChatEntry entry(String prompt) {
        ChatEntry entry = new ChatEntry.Builder().userPrompt(prompt).model("Nano Banana Pro").build();
        entry.addImage(new GeneratedImage.Builder().downloadStatus(GeneratedImage.DownloadStatus.CACHED).build());
        return entry;
    }

    /*  The tiles in the rows the grid shows, once laid out  */
    private static int tiles(ImageGrid grid) {
        grid.getParent().applyCss();
        grid.getParent().layout();
        return grid.lookupAll(".list-cell").stream().map((cell) -> ((ListCell<?>) cell).getGraphic())
                .mapToInt((row) -> row instanceof HBox tiles ? tiles.getChildren().size() : 0).sum();
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
    void theImagesOfANewChatShowAfterAnotherChatWithoutEntries() throws Exception {
        int tiles = onFxThread(() -> {
            ListView<ChatEntry> chat = new ListView<>();
            ImageGrid grid = new ImageGrid((image) -> null);
            grid.entriesProperty().bind(chat.itemsProperty());
            new Scene(new StackPane(chat, grid), 900, 600);

            /*  Both chats are without entries, so their lists are equal; then the new one gets two images  */
            chat.setItems(FXCollections.observableArrayList());
            chat.setItems(FXCollections.observableArrayList());
            chat.getItems().add(entry("a lighthouse at dusk"));
            chat.getItems().add(entry("a lighthouse in fog"));
            return tiles(grid);
        });
        assertEquals(2, tiles);
    }

    @Test
    void aFailedImageShowsAsErrorTileInThePlaceOfItsPlaceholder() throws Exception {
        ChatEntry entry = new ChatEntry.Builder().userPrompt("a lighthouse at dusk").model("Nano Banana Pro").build();
        GeneratedImage failing = new GeneratedImage.Builder().downloadStatus(GeneratedImage.DownloadStatus.PENDING)
                .build();
        entry.addImage(failing);
        entry.addImage(new GeneratedImage.Builder().downloadStatus(GeneratedImage.DownloadStatus.PENDING).build());
        ImageGrid grid = onFxThread(() -> {
            ImageGrid shown = new ImageGrid((image) -> null);
            new Scene(new StackPane(shown), 900, 600);
            shown.entriesProperty().set(FXCollections.observableArrayList(entry));
            return shown;
        });
        int generating = onFxThread(() -> tiles(grid));

        onFxThread(() -> {
            failing.setErrorMessage("Blocked by the safety filter");
            failing.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
            return null;
        });
        int[] failed = onFxThread(() -> new int[] { tiles(grid), grid.lookupAll(".image-grid-failed").size() });
        assertEquals(2, generating);
        assertEquals(2, failed[0]);
        assertEquals(1, failed[1]);
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
