package tools.moodframe.util;

import tools.moodframe.model.AspectRatios;

import tools.moodframe.model.GenerationModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class AspectRatiosTest {

    @Test
    void nanoBanana2AndLiteGetTheExtendedRatios() {
        assertArrayEquals(AspectRatios.NANO_BANANA_2, AspectRatios.forModel(GenerationModel.NANO_BANANA2_1K));
        assertArrayEquals(AspectRatios.NANO_BANANA_2, AspectRatios.forModel(GenerationModel.NANO_BANANA2_LITE));
    }

    @Test
    void otherModelsKeepTheirOwnLists() {
        assertArrayEquals(AspectRatios.GEMINI, AspectRatios.forModel(GenerationModel.GEMINI_PRO_2K));
        assertArrayEquals(AspectRatios.BFL, AspectRatios.forModel(GenerationModel.FLUX2_PRO));
        assertArrayEquals(AspectRatios.FLUX_3, AspectRatios.forModel(GenerationModel.FLUX3_IMAGE_2K));
        assertArrayEquals(AspectRatios.FLUX_3, AspectRatios.forModel(GenerationModel.FLUX3_KLEIN_768));
        assertArrayEquals(AspectRatios.VIDEO, AspectRatios.forModel(GenerationModel.OMNI_1080P));
        assertArrayEquals(AspectRatios.VIDEO, AspectRatios.forModel(GenerationModel.OMNI_720P));
    }
}
