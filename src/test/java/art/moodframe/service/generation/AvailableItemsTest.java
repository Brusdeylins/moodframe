package art.moodframe.service.generation;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ConfigMocks;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationModel;
import art.moodframe.service.provider.BflApiService;
import art.moodframe.service.provider.GeminiApiService;
import art.moodframe.service.provider.MockProviders;
import art.moodframe.service.provider.OpenAiApiService;
import art.moodframe.service.provider.ProviderRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The one rule which models the dropdown offers for a number of attached images: only those of usable providers
 * that take that many images.
 */
class AvailableItemsTest {

    private final ApplicationConfig config = ConfigMocks.config();
    private final ProviderRegistry providers = MockProviders.preparing(config);

    private List<String> items(int imageCount) {
        return GenerationService.availableItems(config, providers, imageCount);
    }

    private void usable(ApiProvider provider) {
        when(config.accounts().isEnabled(provider)).thenReturn(true);
        when(config.accounts().hasApiKey(provider)).thenReturn(true);
    }

    @Test
    void withoutImagesEveryModelOfTheUsableProvidersIsOffered() {
        usable(ApiProvider.BFL);
        assertEquals(GenerationModel.selectable().stream()
                .filter((GenerationModel model) -> model.getProvider() == ApiProvider.BFL)
                .map(ModelLabels::displayName).toList(), items(0));
    }

    @Test
    void aModelTakingFewerImagesIsLeftOut() {
        usable(ApiProvider.BFL);
        usable(ApiProvider.GOOGLE_GEMINI);
        List<String> items = items(9);
        assertFalse(items.isEmpty());
        for (String item : items)
            assertTrue(GenerationModel.fromLocalizedDisplayName(item).getMaxInputImages() >= 9, item);
        assertTrue(items(0).stream().anyMatch((String item) -> !items.contains(item)));
    }

    @Test
    void aDisabledProviderOffersNothingEvenWithImages() {
        usable(ApiProvider.GOOGLE_GEMINI);
        when(config.accounts().hasApiKey(ApiProvider.BFL)).thenReturn(true);
        List<String> items = items(1);
        assertFalse(items.isEmpty());
        for (String item : items)
            assertEquals(ApiProvider.GOOGLE_GEMINI, GenerationModel.fromLocalizedDisplayName(item).getProvider(), item);
    }

    @Test
    void theOpenAiModelsFollowTheLimitOfTheGateway() {
        usable(ApiProvider.OPENAI);
        when(config.getOpenAiModels()).thenReturn(List.of("gpt-image-1"));
        int limit = GenerationModel.OPENAI_IMAGE.getMaxInputImages();
        assertEquals(List.of(GenerationModel.OPENAI_PREFIX + "gpt-image-1"), items(limit));
        assertEquals(List.of(), items(limit + 1));
    }

    @Test
    void theListedModelsComeFromTheProviderNotFromTheSettings() {
        usable(ApiProvider.OPENAI);
        OpenAiApiService openAi = mock(OpenAiApiService.class, CALLS_REAL_METHODS);
        doReturn(List.of("qwen-image")).when(openAi).getImageModels();
        ProviderRegistry listing = MockProviders.registry(mock(BflApiService.class, CALLS_REAL_METHODS),
                mock(GeminiApiService.class, CALLS_REAL_METHODS), openAi);

        assertEquals(List.of(GenerationModel.OPENAI_PREFIX + "qwen-image"),
                GenerationService.availableItems(config, listing, 0));
        assertTrue(listing.isOrderable("qwen-image"), "what the dropdown offers can be ordered");
    }
}
