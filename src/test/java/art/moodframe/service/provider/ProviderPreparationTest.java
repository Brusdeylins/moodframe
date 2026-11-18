package art.moodframe.service.provider;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
        assertEquals(GenerationModel.openAiMaxPromptLength("dall-e-3"),
                providers.get(GenerationModel.OPENAI_IMAGE.getProvider()).maxPromptLength("dall-e-3"));
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
        ApplicationConfig config = mock(ApplicationConfig.class);
        when(config.getGeminiApiKey()).thenReturn("key");
        GeminiApiServiceImpl gemini = new GeminiApiServiceImpl(config);

        assertEquals(Map.of("x-goog-api-key", "key"),
                gemini.downloadHeaders("https://generativelanguage.googleapis.com/v1beta/files/abc:download"));
        assertTrue(gemini.downloadHeaders("https://cdn.example.com/video.mp4").isEmpty());
    }
}
