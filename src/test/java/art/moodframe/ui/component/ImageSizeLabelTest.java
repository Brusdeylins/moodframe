package art.moodframe.ui.component;

import art.moodframe.model.GeneratedImage;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The size line over an image while the pointer is over it, and the tooltip of the image beside it. */
class ImageSizeLabelTest {

    /*  The line is a control, which needs the toolkit  */
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
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(step.get());
            }
            catch (Throwable e) {
                failure.set(e);
            }
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() != null)
            throw new AssertionError(failure.get());
        return result.get();
    }

    /*  A finished image of a size, with a seed for FLUX or without for Gemini  */
    private static GeneratedImage image(int width, int height, long seed) {
        GeneratedImage image = new GeneratedImage.Builder().downloadStatus(GeneratedImage.DownloadStatus.CACHED)
                .localFilePath("/tmp/image.png").build();
        image.setWidth(width);
        image.setHeight(height);
        image.setSeed(seed);
        return image;
    }

    /*  The pointer over a node, as the scene sets it  */
    private static void hover(Node node, boolean over) {
        try {
            Method setHover = Node.class.getDeclaredMethod("setHover", boolean.class);
            setHover.setAccessible(true);
            setHover.invoke(node, over);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /*  A tile of an image as the views build it, with the line of mood and frame 17 px high if asked for  */
    private static StackPane tile(double width, double height, boolean dots) {
        StackPane tile = new StackPane(new Rectangle(width, height));
        tile.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        if (dots) {
            Label line = new Label("Golden Coast");
            line.setManaged(false);
            line.resize(80, 17);
            line.visibleProperty().bind(tile.hoverProperty());
            tile.getChildren().add(line);
        }
        new Scene(new StackPane(tile), 800, 800);
        tile.getParent().applyCss();
        tile.getParent().layout();
        return tile;
    }

    /*  The line of an image in a tile while the pointer is over it, null if it gives way  */
    private static Bounds shown(GeneratedImage image, double width, double height, boolean dots) throws Exception {
        return onFxThread(() -> {
            StackPane tile = tile(width, height, dots);
            ImageSizeLabel size = new ImageSizeLabel(image);
            size.attachTo(tile, dots ? (Region) tile.getChildren().get(1) : null);
            hover(tile, true);
            return size.isVisible() ? size.getBoundsInParent() : null;
        });
    }

    @Test
    void theLineNamesSizeAndFormat() {
        GeneratedImage image = image(2752, 1536, 0);
        assertEquals("2752 × 1536 · PNG", ImageSizeLabel.text(image));
        image.setFormat("jpeg");
        assertEquals("2752 × 1536 · JPEG", ImageSizeLabel.text(image));
    }

    @Test
    void theLineGivesWayForAVideoOrAnUnknownSize() {
        assertNull(ImageSizeLabel.text(image(0, 0, 0)));
        GeneratedImage video = image(1280, 720, 0);
        video.setIsVideo(true);
        assertNull(ImageSizeLabel.text(video));
    }

    @Test
    void theLineShowsAtTheBottomCenterAndInTheImagesViewAboveMoodAndFrame() throws Exception {
        Bounds alone = shown(image(2752, 1536, 0), 400, 225, false);
        Bounds above = shown(image(2752, 1536, 0), 400, 225, true);
        assertEquals(200.0, alone.getCenterX(), 1.0);
        assertEquals(225.0 - 5.0, alone.getMaxY(), 1.0);
        assertEquals(200.0, above.getCenterX(), 1.0);
        assertEquals(225.0 - 5.0 - 17.0 - 3.0, above.getMaxY(), 1.0);
    }

    @Test
    void theLineGivesWayOnATileTooSmallForIt() throws Exception {
        assertNull(shown(image(2752, 1536, 0), 160, 60, true));
        assertNull(shown(image(1536, 2752, 0), 60, 200, false));
        assertNotNull(shown(image(2752, 1536, 0), 160, 60, false));
    }

    @Test
    void theLineShowsOnlyForAFinishedImageWhileThePointerIsOverIt() throws Exception {
        GeneratedImage pending = image(2752, 1536, 0);
        pending.setDownloadStatus(GeneratedImage.DownloadStatus.PENDING);
        assertNull(shown(pending, 400, 225, false));
        boolean left = onFxThread(() -> {
            StackPane tile = tile(400, 225, false);
            ImageSizeLabel size = new ImageSizeLabel(image(2752, 1536, 0));
            size.attachTo(tile, null);
            hover(tile, true);
            hover(tile, false);
            return size.isVisible();
        });
        assertFalse(left);
    }

    @Test
    void theTooltipNamesTheSizeOnlyIfTheLineGivesWay() {
        GeneratedImage image = image(2752, 1536, 42);
        assertEquals("Seed: 42", ChatEntryCard.imageDetails(image, false));
        assertEquals("2752 × 1536 · PNG\nSeed: 42", ChatEntryCard.imageDetails(image, true));
        image.setBaseCost(0.13);
        image.setSearchCost(0.01);
        assertEquals("Seed: 42\nBase Generation: $0.13\nGoogle Search: $0.01\nTotal: $0.14",
                ChatEntryCard.imageDetails(image, false).replace(',', '.'));
    }

    @Test
    void anImageWithoutDetailsBesideTheLineHasNoTooltip() throws Exception {
        Object[] tooltips = onFxThread(() -> {
            Object[] found = new Object[2];
            for (int i = 0; i < 2; i++) {
                StackPane tile = tile(i == 0 ? 400 : 60, 225, false);
                Node view = tile.getChildren().get(0);
                GeneratedImage image = image(2752, 1536, 0);
                ImageSizeLabel size = new ImageSizeLabel(image);
                size.attachTo(tile, null);
                ChatEntryCard.installDetails(tile, view, image, size);
                hover(tile, true);
                found[i] = view.getProperties().get("javafx.scene.control.Tooltip");
            }
            return found;
        });
        assertNull(tooltips[0]);
        assertEquals("2752 × 1536 · PNG", ((Tooltip) tooltips[1]).getText());
    }
}
