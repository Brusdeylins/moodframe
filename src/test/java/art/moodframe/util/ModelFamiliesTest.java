package art.moodframe.util;

import art.moodframe.model.ModelFamilies;

import art.moodframe.model.GenerationModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModelFamiliesTest {

    private final List<String> items = List.of(GenerationModel.NANO_BANANA2_1K.getLocalizedDisplayName(),
            GenerationModel.NANO_BANANA2_4K.getLocalizedDisplayName(),
            GenerationModel.OPENAI_PREFIX + "qwen-image-2.1");

    @Test
    void picksTheVariantByResolutionWithoutFailingOnNulls() {
        assertEquals(items.get(1), ModelFamilies.variantFor(items, "Nano Banana 2", "4K", null));
        assertEquals(items.get(0), ModelFamilies.variantFor(items, "Nano Banana 2", null, null));
        assertEquals(items.get(0), ModelFamilies.variantFor(items, "Nano Banana 2", "8K", "1K"));
        assertNull(ModelFamilies.variantFor(items, "Unknown", "1K", null));
    }

    @Test
    void offersOnlyTwoAndFourKForQwen() {
        assertEquals(List.of("2K", "4K"), ModelFamilies.resolutionsOf(items, items.get(2)));
        assertEquals(List.of("1K", "4K"), ModelFamilies.resolutionsOf(items, items.get(0)));
    }
}
