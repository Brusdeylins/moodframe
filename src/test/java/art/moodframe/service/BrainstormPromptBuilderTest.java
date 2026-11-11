package art.moodframe.service;

import art.moodframe.model.ColorStyle;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.PromptBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrainstormPromptBuilderTest {

    private final BrainstormContext context = new BrainstormContext(
            List.of(new PromptBlock("std:style:pixel-art", PromptBlock.Type.STYLE, "Pixel Art", "retro pixel art",
                    PromptBlock.GROUP_ART, false)),
            List.of(new ColorStyle("Ocean Breeze", List.of("#48BDFF", "#0096C7"), true)),
            List.of(GenerationModel.FLUX2_PRO, GenerationModel.OMNI_360P),
            "Target model: FLUX.2 [pro]");

    @Test
    void fillsEveryPlaceholderOfTheShippedTemplate() throws Exception {
        String template = new String(getClass().getResourceAsStream("/brainstorm/system-prompt.txt").readAllBytes());

        String prompt = BrainstormPromptBuilder.build(template, "German", context, "");

        assertFalse(prompt.contains("{{"), "no placeholder may stay in the prompt");
        assertTrue(prompt.contains("Chat with the user in German"));
    }

    @Test
    void listsStylesColorStylesModelsAndRatios() {
        String prompt = BrainstormPromptBuilder.build(
                "{{STYLES}}|{{COLOR_STYLES}}|{{MODELS}}|{{ASPECT_RATIOS}}|{{CURRENT_STATE}}|{{CURRENT_DRAFT}}",
                "English", context, "{\"prompt\":\"p\"}");

        assertTrue(prompt.contains("- Pixel Art: retro pixel art"));
        assertTrue(prompt.contains("- Ocean Breeze: #48BDFF, #0096C7"));
        assertTrue(prompt.contains("- FLUX.2 [pro] | image |"));
        assertTrue(prompt.contains("- Gemini Omni | video | resolutions: 360p"));
        assertTrue(prompt.contains("Video models (Gemini Omni): 16:9, 9:16"));
        assertTrue(prompt.contains("Target model: FLUX.2 [pro]"));
        assertTrue(prompt.endsWith("{\"prompt\":\"p\"}"));
    }

    @Test
    void offersFlux3ImageAndKleinWithTheirTiersPricesAndRatios() {
        List<GenerationModel> models = List.of(GenerationModel.FLUX3_IMAGE_768, GenerationModel.FLUX3_IMAGE_1K,
                GenerationModel.FLUX3_IMAGE_1_5K, GenerationModel.FLUX3_IMAGE_2K, GenerationModel.FLUX3_IMAGE_4K,
                GenerationModel.FLUX3_KLEIN_1K, GenerationModel.FLUX3_KLEIN_4K);
        BrainstormContext flux3 = new BrainstormContext(List.of(), List.of(), models, "",
                models.stream().map(GenerationModel::getLocalizedDisplayName).toList());

        String prompt = BrainstormPromptBuilder.build("{{MODELS}}|{{ASPECT_RATIOS}}", "English", flux3, "");

        assertTrue(prompt.contains("- FLUX 3 Image | image | resolutions: 768 ($0.041), 1K ($0.048), 1.5K ($0.07), "
                + "2K ($0.10), 4K ($0.607) |"), prompt);
        assertTrue(prompt.contains("- FLUX 3 Image Klein | image | resolutions: 1K ($0.009), 4K ($0.036) |"), prompt);
        assertTrue(prompt.contains("- FLUX 3 Image and FLUX 3 Image Klein: 21:9, 2:1, 16:9, 3:2, 7:5, 4:3, 5:4, 1:1, "
                + "4:5, 3:4, 5:7, 2:3, 9:16, 1:2, 9:21"), prompt);
    }

    @Test
    void listsOneLinePerFamilyWithItsResolutionsAndPrices() {
        BrainstormContext variants = new BrainstormContext(List.of(), List.of(),
                List.of(GenerationModel.NANO_BANANA2_1K, GenerationModel.NANO_BANANA2_2K,
                        GenerationModel.NANO_BANANA2_4K, GenerationModel.OPENAI_IMAGE),
                "", List.of(GenerationModel.NANO_BANANA2_1K.getLocalizedDisplayName(),
                        GenerationModel.NANO_BANANA2_2K.getLocalizedDisplayName(),
                        GenerationModel.NANO_BANANA2_4K.getLocalizedDisplayName(),
                        GenerationModel.OPENAI_PREFIX + "qwen-image-2.1"));

        String models = BrainstormPromptBuilder.build("{{MODELS}}", "English", variants, "");

        assertTrue(models.contains("- Nano Banana 2 | image | resolutions: 1K ($0.067), 2K ($0.101), 4K ($0.151) |"), models);
        assertTrue(models.contains("- OpenAI · qwen-image-2.1 | image | resolutions: 2K, 4K |"), models);
        assertFalse(models.contains("Nano Banana 2 4K"), models);
    }

    @Test
    void marksAMissingDraft() {
        String prompt = BrainstormPromptBuilder.build("{{CURRENT_DRAFT}}", "English", context, "");

        assertTrue(prompt.contains("none yet"));
    }
}
