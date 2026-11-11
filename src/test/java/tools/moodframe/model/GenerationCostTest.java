package tools.moodframe.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenerationCostTest {

    @Test
    void fluxTwoIsBilledPerMegapixelOnceTheSizeIsKnown() {
        assertEquals(GenerationModel.FLUX2_PRO.costForMegapixels(4.0),
                GenerationCost.perImage(GenerationModel.FLUX2_PRO, 2000, 2000, null), 1e-9);
        assertEquals(0.03, GenerationCost.perImage(GenerationModel.FLUX2_PRO, null, null, null), 1e-9);
        assertEquals(0.607, GenerationCost.perImage(GenerationModel.FLUX3_IMAGE_4K, 4096, 4096, null), 1e-9);
    }

    @Test
    void videoIsBilledPerSecondAndTheOtherModelsPerImage() {
        assertEquals(GenerationModel.OMNI_720P.videoPrice(5),
                GenerationCost.perImage(GenerationModel.OMNI_720P, null, null, 5), 1e-9);
        assertEquals(GenerationModel.OMNI_720P.videoPrice(5), GenerationCost.estimate(GenerationModel.OMNI_720P, 5), 1e-9);
        assertEquals(GenerationModel.NANO_BANANA2_1K.getPricePerImage(),
                GenerationCost.perImage(GenerationModel.NANO_BANANA2_1K, 1024, 1024, null), 1e-9);
    }

    @Test
    void theEstimateShowsKleinWithItsFirstMegapixelAndFallsBackToTheDefault() {
        assertEquals(GenerationModel.FLUX2_KLEIN_4B.costForMegapixels(1.0),
                GenerationCost.estimate(GenerationModel.FLUX2_KLEIN_4B, null), 1e-9);
        assertEquals(GenerationModel.getDefault().getPricePerImage(), GenerationCost.estimate(null, null), 1e-9);
    }
}
