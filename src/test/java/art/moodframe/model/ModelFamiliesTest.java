package art.moodframe.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModelFamiliesTest {

    private final List<String> items = List.of(GenerationModel.NANO_BANANA2_1K.getDisplayName(),
            GenerationModel.NANO_BANANA2_4K.getDisplayName(),
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

    @Test
    void offersEveryImageResolutionOnceFromSmallToLargeAsTarget() {
        assertEquals(List.of("512", "768", "1K", "1.5K", "2K", "4K"), ModelFamilies.RESOLUTIONS);
    }

    @Test
    void mapsATargetResolutionToTheClosestOneOfEachModel() {
        List<String> models = Stream.of(GenerationModel.NANO_BANANA2_LITE, GenerationModel.GEMINI_PRO_2K,
                GenerationModel.GEMINI_PRO_4K, GenerationModel.FLUX2_PRO, GenerationModel.OMNI_360P,
                GenerationModel.OMNI_720P, GenerationModel.OMNI_1080P, GenerationModel.OMNI_4K)
                .map(GenerationModel::getDisplayName).collect(Collectors.toCollection(ArrayList::new));
        models.add(items.get(2));

        assertEquals("1K", ModelFamilies.nearestForItem("4K", models, models.get(0)), "Nano Banana 2 Lite");
        assertEquals("2K", ModelFamilies.nearestForItem("2K", models, models.get(2)), "Nano Banana Pro");
        assertEquals("2K", ModelFamilies.nearestForItem("1K", models, models.get(2)), "Nano Banana Pro");
        assertEquals("720p", ModelFamilies.nearestForItem("1K", models, models.get(4)), "Gemini Omni");
        assertEquals("1080p", ModelFamilies.nearestForItem("2K", models, models.get(4)), "Gemini Omni");
        assertEquals("2K", ModelFamilies.nearestForItem("1K", models, items.get(2)), "Qwen-Image");

        /*  A model without resolution choice keeps its fixed size  */
        assertNull(ModelFamilies.nearestForItem("4K", models, models.get(3)));
        assertNull(ModelFamilies.nearestForItem("4K", models, null));
    }

    @Test
    void mapsAResolutionThatIsNoTargetToTheTargetSelectingTheClosestVariant() {
        List<String> models = Stream.of(GenerationModel.NANO_BANANA2_1K, GenerationModel.NANO_BANANA2_4K,
                GenerationModel.FLUX2_PRO, GenerationModel.OMNI_360P, GenerationModel.OMNI_720P,
                GenerationModel.OMNI_1080P, GenerationModel.OMNI_4K).map(GenerationModel::getDisplayName).toList();

        assertEquals("2K", ModelFamilies.targetFor(models, "Gemini Omni", "1080p"));
        assertEquals("1.5K", ModelFamilies.targetFor(models, "Gemini Omni", "720p"));
        assertEquals("768", ModelFamilies.targetFor(models, "Gemini Omni", "360p"));
        for (String variant : models.subList(3, 7))
            assertEquals(variant, ModelFamilies.variantForTarget(models, "Gemini Omni",
                    ModelFamilies.targetFor(models, "Gemini Omni", ModelFamilies.resolutionOf(variant))),
                    "the target selects the variant again");

        /*  A resolution the family does not have takes its closest variant, a target stays as it is  */
        assertEquals("2K", ModelFamilies.targetFor(models, "Gemini Omni", "1440p"), "1080p");
        assertEquals("4K", ModelFamilies.targetFor(models, "Nano Banana 2", "8K"));
        assertEquals("2K", ModelFamilies.targetFor(models, "Nano Banana 2", "2K"));
        assertEquals("2K", ModelFamilies.targetFor(models, GenerationModel.FLUX2_PRO.getFamilyName(), "1080p"),
                "a fixed size: the closest");
        assertEquals("2K", ModelFamilies.targetFor(models, null, "1080p"));
        assertNull(ModelFamilies.targetFor(models, "Gemini Omni", null));
    }

    @Test
    void takesTheSmallerResolutionOnATie() {
        assertEquals("1K", ModelFamilies.nearest("2K", List.of("4K", "1K")));
        assertEquals("2K", ModelFamilies.nearest("2K", List.of("1K", "2K", "4K")));
        assertEquals("1K", ModelFamilies.nearest(null, List.of("512", "1K", "2K")), "no target counts as 1K");
        assertNull(ModelFamilies.nearest("2K", List.of()));
    }
}
