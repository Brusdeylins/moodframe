package art.moodframe.service.provider;

import art.moodframe.model.GenerationCost;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/** The costs beyond the price per image come from the provider: Gemini adds its search and thinking, others none. */
class ExtraCostsTest {

    private static GenerationJobInfo answer(Integer thinkingTokens) {
        return new GenerationJobInfo.Builder().status("completed").isSynchronous(true).thinkingTokens(thinkingTokens)
                .build();
    }

    @Test
    void geminiAddsTheSearchOfTheRequestAndTheThinkingOfTheAnswer() {
        GeminiApiService gemini = mock(GeminiApiService.class, CALLS_REAL_METHODS);
        ImageGenerationRequest request = new ImageGenerationRequest("a lighthouse",
                GenerationModel.NANO_BANANA2_1K.getApiModelId());
        request.setEnableGoogleSearch(true);

        ImageProvider.ExtraCosts costs = gemini.extraCosts(request, answer(2_000));
        assertEquals(GenerationCost.search(GenerationModel.NANO_BANANA2_1K, true), costs.search(), 1e-9);
        assertEquals(GenerationCost.reasoning(2_000), costs.reasoning(), 1e-9);
    }

    @Test
    void aProviderWithoutExtrasAddsNothing() {
        OpenAiApiService openAi = mock(OpenAiApiService.class, CALLS_REAL_METHODS);
        ImageGenerationRequest request = new ImageGenerationRequest("a lighthouse", "gpt-image-1");
        request.setEnableGoogleSearch(true);
        assertEquals(ImageProvider.ExtraCosts.NONE, openAi.extraCosts(request, answer(2_000)));
    }
}
