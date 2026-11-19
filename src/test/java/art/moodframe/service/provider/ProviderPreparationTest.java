package art.moodframe.service.provider;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ConfigMocks;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Each provider knows the prompt limit, the parameters and the download headers of its models. */
class ProviderPreparationTest {

    private final ProviderRegistry providers = MockProviders.preparing();

    private static RequestOptions options(Boolean search, String thinking, List<String> images) {
        return new RequestOptions("item", "2K", 4, search, thinking, 3.5, 40, null, images);
    }

    @Test
    void eachProviderTellsItsPromptLimit() {
        assertEquals(2000, providers.get(GenerationModel.FLUX2_PRO.getProvider()).maxPromptLength("flux.2-pro"));
        assertEquals(250_000, providers.forModel("gemini-3-pro-image").maxPromptLength("gemini-3-pro-image"));
        assertEquals(4000, providers.get(GenerationModel.OPENAI_IMAGE.getProvider()).maxPromptLength("dall-e-3"));
    }

    @Test
    void theProviderChecksThePromptAgainstItsLimit() {
        ImageGenerationRequest flux = new ImageGenerationRequest("x".repeat(2001), "flux.2-pro");
        IllegalArgumentException tooLong = assertThrows(IllegalArgumentException.class,
                () -> providers.forModel("flux.2-pro").checkPromptLength(flux));
        assertEquals("Prompt cannot exceed 2000 characters (BFL (Blackforest Labs) limit)", tooLong.getMessage());
        ImageGenerationRequest gemini = new ImageGenerationRequest("x".repeat(2001), "gemini-3-pro-image");
        providers.forModel("gemini-3-pro-image").checkPromptLength(gemini);
    }

    @Test
    void geminiSetsTheSizeOfTheVariantSearchAndThinking() {
        ImageGenerationRequest request = new ImageGenerationRequest("an apple", "gemini-3.1-flash-image");
        GenerationModel model = GenerationModel.NANO_BANANA2_4K;
        providers.get(model.getProvider()).prepare(request, model, options(true, "HIGH", List.of("a", "b")));

        assertEquals("4K", request.getImageSize());
        assertEquals(Boolean.TRUE, request.getEnableGoogleSearch());
        assertEquals("high", request.getThinkingMode());
        assertEquals(2, request.getInputImageCount());
        assertNull(request.getVideoDuration());
    }

    @Test
    void bflSetsTheSafetyToleranceAndTheFlexParametersOfFluxTwo() {
        ImageGenerationRequest request = new ImageGenerationRequest("an apple", "flux.2-flex");
        request.setSafetyTolerance(2);
        GenerationModel model = GenerationModel.fromApiModelId("flux.2-flex");
        providers.get(model.getProvider()).prepare(request, model, options(true, null, List.of()));

        assertEquals(4, request.getSafetyTolerance());
        assertEquals(3.5, request.getGuidance());
        assertEquals(40, request.getSteps());
        assertNull(request.getEnableGoogleSearch());
    }

    @Test
    void onlyAFileOfTheGeminiApiGetsTheKey() {
        ApplicationConfig config = ConfigMocks.config();
        when(config.accounts().getApiKey(ApiProvider.GOOGLE_GEMINI)).thenReturn("key");
        GeminiApiServiceImpl gemini = new GeminiApiServiceImpl(config);

        assertEquals(Map.of("x-goog-api-key", "key"),
                gemini.downloadHeaders("https://generativelanguage.googleapis.com/v1beta/files/abc:download"));
        assertTrue(gemini.downloadHeaders("https://cdn.example.com/video.mp4").isEmpty());
    }

    @Test
    void bflLimitsTheJobOfAnImageAndReportsItsPrice() {
        ImageProvider bfl = providers.get(ApiProvider.BFL);
        ImageGenerationRequest flux2 = new ImageGenerationRequest("an apple", "flux.2-pro");
        flux2.setSafetyTolerance(6);
        flux2.setSeed(-1L);
        bfl.prepareJob(flux2);
        assertEquals(5, flux2.getSafetyTolerance(), "FLUX.2 takes up to 5");
        assertEquals(-1L, flux2.getSeed());

        ImageGenerationRequest flux3 = new ImageGenerationRequest("an apple", "flux.3-klein");
        flux3.setSafetyTolerance(6);
        flux3.setSeed(-1L);
        bfl.prepareJob(flux3);
        assertEquals(6, flux3.getSafetyTolerance());
        assertEquals(4294967295L, flux3.getSeed(), "FLUX 3 takes seeds of 32 bits");

        art.moodframe.model.GenerationJobResult result = new art.moodframe.model.GenerationJobResult();
        result.setCost(1.2);
        assertEquals(0.012, bfl.reportedCost(result), 1e-9);
        result.setCost(0.0);
        assertNull(bfl.reportedCost(result));
        assertNull(providers.get(ApiProvider.GOOGLE_GEMINI).reportedCost(result));
        assertEquals(4, bfl.approvedSafetyTolerance(new java.util.concurrent.CompletionException(new BflApiException(
                422, "", "{\"detail\":{\"code\":\"safety_tolerance_not_approved\","
                        + "\"maximum_approved_safety_tolerance\":4}}"))));
        assertNull(providers.get(ApiProvider.OPENAI).approvedSafetyTolerance(new IllegalStateException()));
    }
}
