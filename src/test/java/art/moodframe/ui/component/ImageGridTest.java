package art.moodframe.ui.component;

import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryFrame;
import art.moodframe.model.EntryMood;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationModel;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The columns of the images view, the images it shows in their order and the line of an image in its full view. */
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

    /*  Whether the last row of the grid is in view, once laid out  */
    private static boolean showsLastRow(ImageGrid grid) {
        grid.getParent().applyCss();
        grid.getParent().layout();
        VirtualFlow<?> flow = (VirtualFlow<?>) grid.lookup(".virtual-flow");
        IndexedCell<?> last = flow.getLastVisibleCell();
        return last != null && last.getIndex() == flow.getCellCount() - 1
                && last.getLayoutY() + last.getHeight() <= flow.getHeight() + 1.0;
    }

    /*  A turn of the mouse wheel over the grid, upwards  */
    private static void scrollUp(ImageGrid grid) {
        Event.fireEvent(grid.lookup(".virtual-flow"), new ScrollEvent(ScrollEvent.SCROLL, 100, 100, 100, 100,
                false, false, false, false, false, false, 0, 400, 0, 400,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0, ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null));
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
    void theTilesFollowTheOrderOfTheChatAndANewImageShowsAtTheEnd() throws Exception {
        ChatEntry first = entry("a lighthouse at dusk");
        first.addImage(new GeneratedImage.Builder().downloadStatus(GeneratedImage.DownloadStatus.CACHED).build());
        ChatEntry second = entry("a lighthouse in fog");
        ChatEntry third = entry("a lighthouse in a storm");
        ObservableList<ChatEntry> chat = FXCollections.observableArrayList(first, second);
        List<List<GeneratedImage>> shown = onFxThread(() -> {
            ImageGrid grid = new ImageGrid((image) -> null);
            new Scene(new StackPane(grid), 900, 600);
            grid.entriesProperty().set(chat);
            chat.add(third);
            return List.of(grid.getItems().stream().flatMap(List::stream).map(ImageGrid.Item::image).toList(),
                    grid.getImages().stream().map(ImageGrid.Item::image).toList());
        });
        List<GeneratedImage> expected = List.of(first.getImages().get(0), first.getImages().get(1),
                second.getImages().get(0), third.getImages().get(0));
        for (List<GeneratedImage> images : shown) {
            assertEquals(expected.size(), images.size());
            for (int i = 0; i < expected.size(); i++)
                assertSame(expected.get(i), images.get(i));
        }
    }

    @Test
    void theGridOpensAtItsEndAndStaysThereUnlessScrolledAway() throws Exception {
        ObservableList<ChatEntry> chat = FXCollections.observableArrayList();
        for (int i = 0; i < 40; i++)
            chat.add(entry("a lighthouse, take " + i));
        ImageGrid grid = onFxThread(() -> {
            ImageGrid shown = new ImageGrid((image) -> null);
            new Scene(new StackPane(shown), 900, 600);
            shown.entriesProperty().set(chat);
            return shown;
        });
        boolean opened = onFxThread(() -> showsLastRow(grid));
        int first = onFxThread(() -> ((VirtualFlow<?>) grid.lookup(".virtual-flow")).getFirstVisibleCell()
                .getIndex());
        boolean followed = onFxThread(() -> {
            for (int i = 0; i < 10; i++)
                chat.add(entry("a lighthouse, take " + (40 + i)));
            return showsLastRow(grid);
        });
        onFxThread(() -> {
            scrollUp(grid);
            return null;
        });
        boolean left = onFxThread(() -> {
            chat.add(entry("a lighthouse, take 50"));
            return showsLastRow(grid);
        });
        assertTrue(first > 0);
        assertTrue(opened);
        assertTrue(followed);
        assertFalse(left);
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
