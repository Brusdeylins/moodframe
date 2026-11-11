package tools.moodframe.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageDimensionsTest {

    @Test
    void takesTheLargestSizeOfTheRatioInTheAlignment() {
        assertArrayEquals(new int[] {2048, 1152}, ImageDimensions.forAspectRatio("16:9", 2048, 16));
        assertArrayEquals(new int[] {1152, 2048}, ImageDimensions.forAspectRatio("9:16", 2048, 16));
        assertArrayEquals(new int[] {1440, 960}, ImageDimensions.forAspectRatio("3:2", 1440, 32));
    }

    @Test
    void readsAnUnknownRatioAsSquareAndKeepsTheMinimum() {
        assertArrayEquals(new int[] {1024, 1024}, ImageDimensions.forAspectRatio("wide", 1024, 32));
        assertArrayEquals(new int[] {2048, 256}, ImageDimensions.forAspectRatio("21:1", 2048, 32));
    }

    @Test
    void alignsFluxTwoTo16AndTheOthersTo32() {
        assertEquals(16, ImageDimensions.multipleFor(GenerationModel.FLUX2_PRO));
        assertEquals(32, ImageDimensions.multipleFor(null));
    }
}
