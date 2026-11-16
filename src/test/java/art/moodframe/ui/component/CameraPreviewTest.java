package art.moodframe.ui.component;

import art.moodframe.model.CameraSettings;
import art.moodframe.model.CameraSettings.Aperture;
import art.moodframe.model.CameraSettings.Lens;
import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Draws the camera preview on the JavaFX thread and reads the band of focus from its view from above.
 */
class CameraPreviewTest {

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        Platform.setImplicitExit(false);
        started.await(10, TimeUnit.SECONDS);
    }

    @Test
    void widensTheObliqueBandOfATiltShiftLensWithTheAperture() throws Exception {
        boolean[][] open = focus(Lens.TILT_SHIFT, Aperture.F_1_4);
        boolean[][] wider = focus(Lens.TILT_SHIFT, Aperture.F_4);

        assertTrue(count(open) > 0, "a band at f/1.4");
        assertTrue(count(wider) > 1.5 * count(open), "f/4 wider than f/1.4: " + count(wider) + " " + count(open));
        assertTrue(Arrays.deepEquals(open, focus(Lens.TILT_SHIFT, null)), "\"Any\" as f/1.4");
        for (boolean[][] band : new boolean[][][] { open, wider }) {
            int[] columns = columns(band);
            assertTrue(top(band, columns[0]) - top(band, columns[1]) > 10, "the band rises to the right");
        }
    }

    @Test
    void showsTheDepthOfFieldOfAShiftLensAsWithoutASpecialLens() throws Exception {
        for (Aperture aperture : new Aperture[] { Aperture.F_1_4, Aperture.F_11 }) {
            boolean[][] shift = focus(Lens.SHIFT, aperture);
            assertTrue(count(shift) > 0, "a band at " + aperture);
            assertArrayEquals(rows(focus(null, aperture)), rows(shift), 1, "the band at " + aperture);
            int[] columns = columns(shift);
            assertEquals(top(shift, columns[0]), top(shift, columns[1]), 1, "level at " + aperture);
        }
        assertEquals(0, count(focus(Lens.SHIFT, null)), "no band at \"Any\"");
    }

    /** The pixels of the band of focus in the view from above: orange over the blue cone. */
    private static boolean[][] focus(Lens lens, Aperture aperture) throws Exception {
        CameraSettings settings = new CameraSettings(null, lens, aperture, null, null, null, null);
        AtomicReference<boolean[][]> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                CameraPreview preview = new CameraPreview();
                preview.show(settings);
                Canvas top = preview.getChildren().stream().filter(Canvas.class::isInstance).map(Canvas.class::cast)
                        .findFirst().orElseThrow();
                SnapshotParameters transparent = new SnapshotParameters();
                transparent.setFill(Color.TRANSPARENT);
                PixelReader pixels = top.snapshot(transparent, null).getPixelReader();
                boolean[][] band = new boolean[(int) top.getHeight()][(int) top.getWidth()];
                for (int y = 0; y < band.length; y++)
                    for (int x = 0; x < band[y].length; x++) {
                        Color color = pixels.getColor(x, y);
                        band[y][x] = color.getOpacity() > 0 && color.getRed() > color.getBlue();
                    }
                result.set(band);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        return result.get();
    }

    private static long count(boolean[][] band) {
        long count = 0;
        for (boolean[] row : band)
            for (boolean pixel : row)
                count += pixel ? 1 : 0;
        return count;
    }

    /** The first and the last row of the band. */
    private static double[] rows(boolean[][] band) {
        int first = -1;
        int last = -1;
        for (int y = 0; y < band.length; y++)
            for (boolean pixel : band[y])
                if (pixel) {
                    first = first < 0 ? y : first;
                    last = y;
                }
        return new double[] { first, last };
    }

    /** The columns a quarter of the band width inside its left and its right end. */
    private static int[] columns(boolean[][] band) {
        int left = Integer.MAX_VALUE;
        int right = -1;
        for (boolean[] row : band)
            for (int x = 0; x < row.length; x++)
                if (row[x]) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
        int quarter = (right - left) / 4;
        return new int[] { left + quarter, right - quarter };
    }

    /** The topmost row of the band in a column. */
    private static int top(boolean[][] band, int column) {
        for (int y = 0; y < band.length; y++)
            if (band[y][column])
                return y;
        return -1;
    }
}
