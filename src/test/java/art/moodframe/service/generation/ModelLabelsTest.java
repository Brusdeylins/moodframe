package art.moodframe.service.generation;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ModelFamilies;
import art.moodframe.util.I18nManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelLabelsTest {

    @Test
    void findsTheModelOfEveryLocalizedItemInEveryLanguage() {
        Locale before = I18nManager.getCurrentLocale();
        try {
            for (Locale locale : I18nManager.getAvailableLocales()) {
                I18nManager.setLocale(locale);
                for (GenerationModel model : GenerationModel.values()) {
                    String item = ModelLabels.displayName(model);
                    assertEquals(model, GenerationModel.fromLocalizedDisplayName(item), locale + " " + item);
                    assertEquals(model.getFamilyName(), ModelFamilies.familyOf(item), locale + " " + item);
                    assertEquals(model.getResolutionLabel(), ModelFamilies.resolutionOf(item), locale + " " + item);
                    if (model != GenerationModel.OPENAI_IMAGE) {
                        assertEquals(model.name(), GenerationModel.idOf(item));
                        assertEquals(item, ModelLabels.itemOf(model.name()));
                    }
                }
            }
        } finally {
            I18nManager.setLocale(before);
        }
    }

    @Test
    void localizesOnlyTheModelsWithAKey() {
        Locale before = I18nManager.getCurrentLocale();
        try {
            I18nManager.setLocale(Locale.GERMAN);
            String german = ModelLabels.displayName(GenerationModel.FLUX2_PRO);
            assertEquals("FLUX.2 [pro] - Produktionsqualität ($0.03+)", german);
            assertNotEquals(GenerationModel.FLUX2_PRO.getDisplayName(), german);
            assertEquals(GenerationModel.NANO_BANANA2_2K.getDisplayName(),
                    ModelLabels.displayName(GenerationModel.NANO_BANANA2_2K));
            assertNull(GenerationModel.NANO_BANANA2_2K.displayNameKey());

            I18nManager.setLocale(Locale.ENGLISH);
            assertEquals(GenerationModel.FLUX2_PRO.getDisplayName(),
                    ModelLabels.displayName(GenerationModel.FLUX2_PRO));
        } finally {
            I18nManager.setLocale(before);
        }
    }

    @Test
    void namesAnOpenAiModelByItsPrefixAndNothingWithoutId() {
        assertEquals(GenerationModel.OPENAI_PREFIX + "gpt-image-1", ModelLabels.itemOf("gpt-image-1"));
        assertNull(ModelLabels.itemOf(null));
    }

    @Test
    void listsTheModelsOfTheUsableProvidersThenTheGateway() {
        List<String> items = ModelLabels.availableItems((ApiProvider provider) -> provider == ApiProvider.BFL,
                List.of("qwen-image"));

        assertTrue(items.contains(ModelLabels.displayName(GenerationModel.FLUX2_PRO)));
        assertFalse(items.contains(ModelLabels.displayName(GenerationModel.NANO_BANANA2_1K)));
        assertFalse(items.contains(ModelLabels.displayName(GenerationModel.GEMINI_FLASH_1K)), "deprecated");
        assertEquals(GenerationModel.OPENAI_PREFIX + "qwen-image", items.get(items.size() - 1));
        assertTrue(items.stream().limit(items.size() - 1).map(GenerationModel::fromLocalizedDisplayName)
                .allMatch((GenerationModel model) -> model.getProvider() == ApiProvider.BFL));
    }
}
