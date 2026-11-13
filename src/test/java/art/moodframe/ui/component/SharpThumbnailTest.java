package art.moodframe.ui.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When a view shows its thumbnail larger than its pixels on the screen. */
class SharpThumbnailTest {

    @Test
    void anOldThumbnailOf400PxIsTooSmallOnlyOnARetinaScreen() {
        assertFalse(SharpThumbnail.isTooSmall(400, 225, 300, 300, 1.0));
        assertTrue(SharpThumbnail.isTooSmall(400, 225, 300, 300, 2.0));
        assertFalse(SharpThumbnail.isTooSmall(400, 225, 200, 200, 2.0));
    }

    @Test
    void aThumbnailIsTooSmallOnceItsTileGetsLargerThanItsPixels() {
        assertFalse(SharpThumbnail.isTooSmall(400, 400, 400, 400, 1.0));
        assertTrue(SharpThumbnail.isTooSmall(400, 400, 420, 420, 1.0));

        /*  An image of the chat fits into the width of its tile and the chosen size as height  */
        assertFalse(SharpThumbnail.isTooSmall(400, 300, 520, 300, 1.0));
        assertTrue(SharpThumbnail.isTooSmall(400, 300, 520, 320, 1.0));
    }

    @Test
    void aThumbnailOfFullSizeIsNeverTooSmall() {
        assertFalse(SharpThumbnail.isTooSmall(800, 450, 480, 480, 2.0));
        assertFalse(SharpThumbnail.isTooSmall(450, 800, 800, 800, 2.0));
        assertFalse(SharpThumbnail.isTooSmall(0, 0, 400, 400, 2.0));
    }
}
