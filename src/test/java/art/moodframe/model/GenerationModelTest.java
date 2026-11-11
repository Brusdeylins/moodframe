package art.moodframe.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationModelTest {

    @Test
    void kleinCostIsBasePriceForTheFirstMegapixelPlusPriceForEachAdditional() {
        assertEquals(0.014, GenerationModel.FLUX2_KLEIN_4B.costForMegapixels(0.5), 1e-9);
        assertEquals(0.014, GenerationModel.FLUX2_KLEIN_4B.costForMegapixels(1.0), 1e-9);
        assertEquals(0.015, GenerationModel.FLUX2_KLEIN_4B.costForMegapixels(2.0), 1e-9, "BFL example: 2 MP = $0.015");
        assertEquals(0.017, GenerationModel.FLUX2_KLEIN_4B.costForMegapixels(4.0), 1e-9);
        assertEquals(0.015, GenerationModel.FLUX2_KLEIN_9B.costForMegapixels(1.0), 1e-9);
        assertEquals(0.021, GenerationModel.FLUX2_KLEIN_9B.costForMegapixels(4.0), 1e-9);
    }

    @Test
    void proAndMaxCostTheirFirstMegapixelPlusEachAdditionalOneFlexPerMegapixel() {
        assertEquals(0.03, GenerationModel.FLUX2_PRO.costForMegapixels(1.0), 1e-9);
        assertEquals(0.045, GenerationModel.FLUX2_PRO.costForMegapixels(2.0), 1e-9);
        assertEquals(0.07, GenerationModel.FLUX2_MAX.costForMegapixels(1.0), 1e-9);
        assertEquals(0.16, GenerationModel.FLUX2_MAX.costForMegapixels(4.0), 1e-9);
        assertEquals(0.10, GenerationModel.FLUX2_FLEX.costForMegapixels(2.0), 1e-9);
    }

    @Test
    void flux3VariantsFormTwoFamiliesWithTheirTierPricesAndTenReferences() {
        assertEquals(List.of(0.041, 0.048, 0.07, 0.10, 0.607), List.of(GenerationModel.FLUX3_IMAGE_768,
                GenerationModel.FLUX3_IMAGE_1K, GenerationModel.FLUX3_IMAGE_1_5K, GenerationModel.FLUX3_IMAGE_2K,
                GenerationModel.FLUX3_IMAGE_4K).stream().map(GenerationModel::getPricePerImage).toList());
        assertEquals(List.of(0.008, 0.009, 0.011, 0.014, 0.036), List.of(GenerationModel.FLUX3_KLEIN_768,
                GenerationModel.FLUX3_KLEIN_1K, GenerationModel.FLUX3_KLEIN_1_5K, GenerationModel.FLUX3_KLEIN_2K,
                GenerationModel.FLUX3_KLEIN_4K).stream().map(GenerationModel::getPricePerImage).toList());
        assertEquals("FLUX 3 Image", GenerationModel.FLUX3_IMAGE_4K.getFamilyName());
        assertEquals("FLUX 3 Image Klein", GenerationModel.FLUX3_KLEIN_768.getFamilyName());
        assertEquals("1.5K", GenerationModel.FLUX3_KLEIN_1_5K.getResolutionLabel());
        assertEquals("768sq", GenerationModel.FLUX3_IMAGE_768.getFlux3Resolution());
        assertEquals("1.5k", GenerationModel.FLUX3_IMAGE_1_5K.getFlux3Resolution());
        assertEquals("4k", GenerationModel.FLUX3_KLEIN_4K.getFlux3Resolution());
        assertEquals(10, GenerationModel.FLUX3_IMAGE_1K.getMaxInputImages());
        assertTrue(GenerationModel.FLUX3_KLEIN_2K.isFlux3Klein());
        assertFalse(GenerationModel.FLUX3_IMAGE_2K.isFlux3Klein());
        assertEquals(art.moodframe.model.ApiProvider.BFL, GenerationModel.FLUX3_IMAGE_1K.getProvider());
        assertEquals(GenerationModel.FLUX3_IMAGE_1K, GenerationModel.getDefault());
    }

    @Test
    void anOlderFlux2NameWithAnotherPriceStillFindsItsModel() {
        assertEquals(GenerationModel.FLUX2_PRO,
                GenerationModel.fromLocalizedDisplayName("FLUX.2 [pro] - Production Grade ($0.03/MP)"));
        assertEquals(null, GenerationModel.fromLocalizedDisplayName("FLUX 1.1 [pro] - Professional Quality ($0.04)"));
    }

    @Test
    void kleinHasFourReferenceImagesAndIsAFlux2Model() {
        assertTrue(GenerationModel.FLUX2_KLEIN_9B.isFlux2Model());
        assertTrue(GenerationModel.FLUX2_KLEIN_9B.isFlux2Klein());
        assertEquals(4, GenerationModel.FLUX2_KLEIN_9B.getMaxInputImages());
        assertEquals(8, GenerationModel.FLUX2_PRO.getMaxInputImages());
        assertFalse(GenerationModel.FLUX2_PRO.isFlux2Klein());
    }

    @Test
    void deprecatedModelsAreNotSelectableButStillKnown() {
        assertTrue(GenerationModel.GEMINI_FLASH_1K.isDeprecated());
        assertFalse(GenerationModel.selectable().contains(GenerationModel.GEMINI_FLASH_1K));
        assertEquals(GenerationModel.GEMINI_FLASH_1K, GenerationModel.fromApiModelId("gemini-2.5-flash-image"));
        assertTrue(GenerationModel.selectable().contains(GenerationModel.FLUX2_KLEIN_4B));
    }

    @Test
    void nanoBanana2AndLiteShareTheExtendedFeatures() {
        for (GenerationModel model : new GenerationModel[] {GenerationModel.NANO_BANANA2_1K, GenerationModel.NANO_BANANA2_LITE}) {
            assertTrue(model.supportsExtendedAspectRatios(), model.name());
            assertTrue(model.supportsThinkingLevel(), model.name());
            assertTrue(model.supportsVideoSource(), model.name());
        }
        assertFalse(GenerationModel.GEMINI_PRO_2K.supportsThinkingLevel());
        assertFalse(GenerationModel.GEMINI_PRO_2K.supportsVideoSource());
        assertFalse(GenerationModel.NANO_BANANA2_LITE.supportsGroundingSearch());
        assertTrue(GenerationModel.NANO_BANANA2_1K.supportsGroundingSearch());
    }

    @Test
    void omniModelsAreSelectableVideoModels() {
        for (GenerationModel model : new GenerationModel[] {GenerationModel.OMNI_360P, GenerationModel.OMNI_720P,
                GenerationModel.OMNI_1080P, GenerationModel.OMNI_4K}) {
            assertTrue(model.isOmniModel(), model.name());
            assertTrue(model.isVideoModel(), model.name());
            assertFalse(model.isDeprecated(), model.name());
            assertTrue(GenerationModel.selectable().contains(model), model.name());
            assertEquals(art.moodframe.model.ApiProvider.GOOGLE_GEMINI, model.getProvider(), model.name());
            assertEquals(1, model.getMaxInputImages(), model.name());
            assertFalse(model.supportsMultiImage(), model.name());
            assertFalse(model.supportsGroundingSearch(), model.name());
        }
        assertEquals("360p", GenerationModel.OMNI_360P.getVideoResolution());
        assertEquals("720p", GenerationModel.OMNI_720P.getVideoResolution());
        assertEquals("1080p", GenerationModel.OMNI_1080P.getVideoResolution());
        assertEquals("4k", GenerationModel.OMNI_4K.getVideoResolution());
        assertEquals(null, GenerationModel.FLUX2_PRO.getVideoResolution());
    }

    @Test
    void omniIsBilledPerSecondWithTheCalibratedRates() {
        /* Live calibration: 1,931 / 5,792 / 8,688 / 17,376 video tokens per second at $17.50 per 1M tokens */
        assertEquals(0.34, GenerationModel.OMNI_360P.videoPrice(10), 0.01);
        assertEquals(0.30, GenerationModel.OMNI_720P.videoPrice(3), 0.01);
        assertEquals(1.52, GenerationModel.OMNI_1080P.videoPrice(10), 0.01);
        assertEquals(3.04, GenerationModel.OMNI_4K.videoPrice(10), 0.01);
        assertEquals(GenerationModel.OMNI_720P.videoPrice(4) * 2, GenerationModel.OMNI_720P.videoPrice(8), 1e-9);
    }

    @Test
    void videoDurationChoicesFollowTheModel() {
        assertEquals(List.of(3, 4, 5, 6, 7, 8, 9, 10), GenerationModel.OMNI_720P.getVideoDurationChoices());
        assertEquals(List.of(), GenerationModel.FLUX2_PRO.getVideoDurationChoices());
    }
}
