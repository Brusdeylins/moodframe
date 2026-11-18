package art.moodframe.service.generation;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ConfigMocks;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * The one rule which models the dropdown offers for a number of attached images: only those of usable providers
 * that take that many images.
 */
class AvailableItemsTest {

    private final ApplicationConfig config = ConfigMocks.config();

    private void usable(ApiProvider provider) {
        when(config.accounts().isEnabled(provider)).thenReturn(true);
        when(config.accounts().hasApiKey(provider)).thenReturn(true);
    }

    @Test
    void withoutImagesEveryModelOfTheUsableProvidersIsOffered() {
        usable(ApiProvider.BFL);
        assertEquals(GenerationService.availableItems(config), GenerationService.availableItems(config, 0));
    }

    @Test
    void aModelTakingFewerImagesIsLeftOut() {
        usable(ApiProvider.BFL);
        usable(ApiProvider.GOOGLE_GEMINI);
        List<String> items = GenerationService.availableItems(config, 9);
        assertFalse(items.isEmpty());
        for (String item : items)
            assertTrue(GenerationModel.fromLocalizedDisplayName(item).getMaxInputImages() >= 9, item);
        assertTrue(GenerationService.availableItems(config).stream().anyMatch((String item) -> !items.contains(item)));
    }

    @Test
    void aDisabledProviderOffersNothingEvenWithImages() {
        usable(ApiProvider.GOOGLE_GEMINI);
        when(config.accounts().hasApiKey(ApiProvider.BFL)).thenReturn(true);
        List<String> items = GenerationService.availableItems(config, 1);
        assertFalse(items.isEmpty());
        for (String item : items)
            assertEquals(ApiProvider.GOOGLE_GEMINI, GenerationModel.fromLocalizedDisplayName(item).getProvider(), item);
    }

    @Test
    void theOpenAiModelsFollowTheLimitOfTheGateway() {
        usable(ApiProvider.OPENAI);
        when(config.getOpenAiModels()).thenReturn(List.of("gpt-image-1"));
        int limit = GenerationModel.OPENAI_IMAGE.getMaxInputImages();
        assertEquals(List.of(GenerationModel.OPENAI_PREFIX + "gpt-image-1"), GenerationService.availableItems(config,
                limit));
        assertEquals(List.of(), GenerationService.availableItems(config, limit + 1));
    }
}
