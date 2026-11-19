package art.moodframe.ui.entry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The zoom of the full view between the fit zoom and the pixels of the image, and the limits of the pan. */
class ZoomLevelTest {

    @Test
    void aLargeImageZoomsBetweenFittedAndItsOwnPixels() {
        ZoomLevel zoom = new ZoomLevel();
        zoom.limit(4000, 2000, 1000, 1000);
        zoom.toFit();
        assertEquals(0.25, zoom.fit(), 1e-9);
        assertTrue(zoom.isFit());
        assertFalse(zoom.zoomOut(), "never below the fit zoom");
        assertTrue(zoom.canZoomToOriginal());
        assertFalse(zoom.canZoomToFit());

        while (zoom.zoomIn())
            assertTrue(zoom.zoom() <= 1.0);
        assertEquals(1.0, zoom.zoom(), 1e-9, "at most the pixels of the image");
        assertTrue(zoom.isOriginal());
        assertTrue(zoom.canZoomToFit());
    }

    @Test
    void aSmallImageIsNotEnlarged() {
        ZoomLevel zoom = new ZoomLevel();
        zoom.limit(200, 100, 1000, 1000);
        zoom.toFit();
        assertEquals(1.0, zoom.fit());
        assertFalse(zoom.zoomIn());
        assertFalse(zoom.canZoomToOriginal());
        assertFalse(zoom.canZoomToFit());
    }

    @Test
    void aNewSizeOfTheWindowFitsAFittedImageAnewAndKeepsAZoomedOneWithinTheLimits() {
        ZoomLevel zoom = new ZoomLevel();
        zoom.limit(4000, 2000, 1000, 1000);
        zoom.toFit();
        zoom.refit(4000, 2000, 2000, 2000);
        assertEquals(0.5, zoom.zoom(), 1e-9, "fitted anew");

        zoom.toOriginal();
        zoom.refit(4000, 2000, 500, 500);
        assertEquals(1.0, zoom.zoom(), 1e-9, "the zoom stays");
    }

    @Test
    void aLargerImagePansUntilItsEdgeReachesTheSafeAreaOneThatFitsStaysCentered() {
        assertEquals(100.0, ZoomLevel.clampPan(300, 1200, 1000));
        assertEquals(-100.0, ZoomLevel.clampPan(-300, 1200, 1000));
        assertEquals(50.0, ZoomLevel.clampPan(50, 1200, 1000));
        assertEquals(0.0, ZoomLevel.clampPan(80, 800, 1000));
    }
}
