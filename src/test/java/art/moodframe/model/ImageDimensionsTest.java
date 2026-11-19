package art.moodframe.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageDimensionsTest {

    @Test
    void takesTheLargestSizeOfTheRatioInTheAlignment() {
        assertArrayEquals(new int[] {2048, 1152}, ImageDimensions.forAspectRatio("16:9", GenerationModel.FLUX2_PRO));
        assertArrayEquals(new int[] {1152, 2048}, ImageDimensions.forAspectRatio("9:16", GenerationModel.FLUX2_PRO));
        assertArrayEquals(new int[] {1440, 960}, ImageDimensions.forAspectRatio("3:2",
                GenerationModel.FLUX3_IMAGE_1_5K));
    }

    @Test
    void readsAnUnknownRatioAsSquareAndKeepsTheMinimum() {
        assertArrayEquals(new int[] {1024, 1024}, ImageDimensions.forAspectRatio("wide",
                GenerationModel.FLUX3_IMAGE_1K));
        assertArrayEquals(new int[] {2048, 256}, ImageDimensions.forAspectRatio("21:1", null));
    }

    @Test
    void keepsTheSmallestSideOfTheModel() {
        assertArrayEquals(new int[] {2048, 64}, ImageDimensions.forAspectRatio("64:1", GenerationModel.FLUX2_PRO));
        assertArrayEquals(new int[] {1024, 256}, ImageDimensions.forAspectRatio("64:1",
                GenerationModel.FLUX3_IMAGE_1K));
        assertEquals(64, ImageDimensions.minimumFor(GenerationModel.FLUX2_PRO));
        assertEquals(256, ImageDimensions.minimumFor(null));
    }

    @Test
    void alignsFluxTwoTo16AndTheOthersTo32() {
        assertEquals(16, ImageDimensions.multipleFor(GenerationModel.FLUX2_PRO));
        assertEquals(32, ImageDimensions.multipleFor(null));
    }
}
