package art.moodframe.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void theQuoteAddsGoogleSearchOfGeminiAndKnowsNoOpenAiPrice() {
        GenerationCost.Quote gemini = GenerationCost.quote(GenerationModel.NANO_BANANA2_1K, null, true, 3);
        double perImage = GenerationModel.NANO_BANANA2_1K.getPricePerImage() + 0.03;
        assertEquals(perImage, gemini.perImage(), 1e-9);
        assertEquals(3 * perImage, gemini.total(), 1e-9);
        assertTrue(gemini.known());

        /*  no grounding: Nano Banana 2 Lite, FLUX, switched off  */
        assertEquals(0.0, GenerationCost.search(GenerationModel.NANO_BANANA2_LITE, true), 1e-9);
        assertEquals(0.0, GenerationCost.search(GenerationModel.FLUX2_MAX, true), 1e-9);
        assertEquals(0.0, GenerationCost.search(GenerationModel.NANO_BANANA2_1K, null), 1e-9);

        GenerationCost.Quote openAi = GenerationCost.quote(GenerationModel.OPENAI_IMAGE, null, null, 2);
        assertFalse(openAi.known());
        assertEquals(0.0, openAi.total(), 1e-9);
    }

    @Test
    void thinkingTokensCostTwelveDollarsPerMillion() {
        assertEquals(0.012, GenerationCost.reasoning(1000), 1e-12);
        assertEquals(0.0, GenerationCost.reasoning(null), 1e-12);
        assertEquals(0.0, GenerationCost.reasoning(0), 1e-12);
    }
}
