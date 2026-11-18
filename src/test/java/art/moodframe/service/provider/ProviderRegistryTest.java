package art.moodframe.service.provider;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Checks that the registry finds the provider of an id and of a model id as the request manager did before. */
class ProviderRegistryTest {

    private static final List<String> GATEWAY_MODELS = List.of("gpt-image-1", "qwen-image-2.1");

    private BflApiService bfl;
    private GeminiApiService gemini;
    private OpenAiApiService openAi;
    private ProviderRegistry registry;

    @BeforeEach
    void setUp() {
        bfl = mock(BflApiService.class);
        gemini = mock(GeminiApiService.class);
        openAi = mock(OpenAiApiService.class);
        when(openAi.getImageModels()).thenReturn(GATEWAY_MODELS);
        doCallRealMethod().when(openAi).servesModel(anyString());
        registry = MockProviders.registry(bfl, gemini, openAi);
    }

    /*  the provider of a model id as RequestResponseManager.detectProvider guessed it before the registry  */
    private static ApiProvider formerProvider(String model) {
        if (model == null)
            return ApiProvider.BFL;
        if (model.toLowerCase(Locale.ROOT).contains("gemini"))
            return ApiProvider.GOOGLE_GEMINI;
        if (GenerationModel.isOpenAiImageModelId(model) || GATEWAY_MODELS.contains(model))
            return ApiProvider.OPENAI;
        return ApiProvider.BFL;
    }

    @Test
    void findsEveryProviderByItsId() {
        assertSame(bfl, registry.get(ApiProvider.BFL));
        assertSame(gemini, registry.get(ApiProvider.GOOGLE_GEMINI));
        assertSame(openAi, registry.get(ApiProvider.OPENAI));
        assertSame(gemini, registry.get(ApiProvider.GOOGLE_GEMINI, GeminiApiService.class));
        assertEquals(3, registry.all().size());
    }

    @Test
    void refusesAMissingProviderAProviderOfAnotherTypeAndASecondProviderOfAnId() {
        ProviderRegistry onlyBfl = MockProviders.registry(mock(BflApiService.class));
        assertThrows(IllegalStateException.class, () -> onlyBfl.get(ApiProvider.OPENAI));
        assertThrows(IllegalStateException.class, () -> registry.get(ApiProvider.BFL, GeminiApiService.class));
        assertThrows(IllegalArgumentException.class,
                () -> MockProviders.registry(mock(BflApiService.class), mock(BflApiService.class)));
    }

    @Test
    void everyModelOfARequestGoesToTheSameProviderAsBefore() {
        for (GenerationModel model : GenerationModel.values()) {
            if (model == GenerationModel.OPENAI_IMAGE)
                continue; // a placeholder, a request carries the id of the OpenAI model
            assertEquals(formerProvider(model.getApiModelId()), registry.providerOf(model.getApiModelId()),
                    model.name());
        }
        for (String id : List.of("gpt-image-1", "dall-e-3", "chatgpt-image-latest", "qwen-image-2.1", "flux-pro-1.1",
                "unknown"))
            assertEquals(formerProvider(id), registry.providerOf(id), id);
        assertEquals(ApiProvider.BFL, registry.providerOf(null));
    }

    @Test
    void forModelDispatchesToTheProviderOfTheModel() {
        assertSame(bfl, registry.forModel("flux.2-pro"));
        assertSame(gemini, registry.forModel("gemini-3.1-flash-image"));
        assertSame(gemini, registry.forModel("gemini-omni-1.1-flash"));
        assertSame(openAi, registry.forModel("qwen-image-2.1"));
    }

    @Test
    void onlyBflHasSeedsAndPollsItsJobs() {
        BflApiService realBfl = mock(BflApiService.class);
        doCallRealMethod().when(realBfl).usesSeeds();
        doCallRealMethod().when(realBfl).answersWithImage();
        assertTrue(realBfl.usesSeeds());
        assertFalse(realBfl.answersWithImage());

        ImageProvider other = mock(ImageProvider.class);
        doCallRealMethod().when(other).usesSeeds();
        doCallRealMethod().when(other).answersWithImage();
        assertFalse(other.usesSeeds());
        assertTrue(other.answersWithImage());
    }

    @Test
    void onlyFlux3AndFlux2TakeAnAttachedImageAtBfl() {
        BflApiService realBfl = mock(BflApiService.class);
        doCallRealMethod().when(realBfl).checkImageInput(any());
        assertDoesNotThrow(() -> realBfl.checkImageInput("flux.3-image"));
        assertDoesNotThrow(() -> realBfl.checkImageInput("flux.2-klein-4b"));
        assertThrows(IllegalArgumentException.class, () -> realBfl.checkImageInput("flux-pro-1.1"));
        assertThrows(IllegalArgumentException.class, () -> realBfl.checkImageInput(null));
    }
}
