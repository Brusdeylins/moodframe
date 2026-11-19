package art.moodframe.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks that the explicit provider of every model and what depends on it are the same as when they were derived from
 * the prefixes of the API model ids.
 */
class GenerationModelProviderTest {

    /*  the provider as getProvider() derived it from the API model id before each constant named it  */
    private static ApiProvider formerProvider(GenerationModel model) {
        String id = model.getApiModelId();
        if (id.startsWith("gemini"))
            return ApiProvider.GOOGLE_GEMINI;
        if (id.startsWith("openai-"))
            return ApiProvider.OPENAI;
        return ApiProvider.BFL;
    }

    @Test
    void everyModelHasTheProviderOfItsFormerPrefix() {
        for (GenerationModel model : GenerationModel.values())
            assertEquals(formerProvider(model), model.getProvider(), model.name());
    }

    @Test
    void theModelIdsStayAsTheyAreStored() {
        assertEquals(Set.of("flux.3-image", "flux.3-image-klein", "flux.2-pro", "flux.2-max", "flux.2-flex",
                "flux.2-klein-4b", "flux.2-klein-9b", "gemini-2.5-flash-image", "gemini-3-pro-image",
                "gemini-3.1-flash-image", "gemini-3.1-flash-lite-image", "gemini-omni-1.1-flash", "openai-image"),
                Arrays.stream(GenerationModel.values()).map(GenerationModel::getApiModelId)
                        .collect(Collectors.toSet()));
    }

    @Test
    void theCapabilitiesOfTheProviderStayAsTheyWere() {
        for (GenerationModel model : GenerationModel.values()) {
            String id = model.getApiModelId();
            boolean gemini = id.startsWith("gemini");
            boolean openAi = id.startsWith("openai-");
            boolean flux = id.startsWith("flux.3-") || id.startsWith("flux.2-");
            boolean omni = id.startsWith("gemini-omni");

            assertEquals(flux || gemini || openAi, model.supportsImageAttachment(), model.name());
            assertEquals(flux || openAi || gemini && !omni, model.supportsMultiImage(), model.name());
            assertEquals(!id.equals("gemini-3.1-flash-lite-image") && !omni
                    && (model == GenerationModel.FLUX2_MAX || gemini), model.supportsGroundingSearch(), model.name());
            int maxInputImages = model.isFlux3Model() ? 10 : model.isFlux2Klein() ? 4 : id.startsWith("flux.2-") ? 8
                    : openAi ? 14 : omni ? 1 : gemini ? 14 : 0;
            assertEquals(maxInputImages, model.getMaxInputImages(), model.name());
            assertEquals(formerSize(model)[0], model.getMaxMegapixels(), model.name());
            assertEquals(formerSize(model)[1], model.getMaxResolution(), model.name());
        }
    }

    /*  megapixels and resolution as the constructor derived them from the prefixes  */
    private static int[] formerSize(GenerationModel model) {
        String id = model.getApiModelId();
        String name = model.name();
        if (id.startsWith("gemini-omni"))
            return new int[] {0, name.contains("4K") ? 3840 : name.contains("1080P") ? 1920
                    : name.contains("360P") ? 640 : 1280};
        if (id.startsWith("gemini"))
            return name.contains("4K") ? new int[] {16, 4096} : name.contains("2K") ? new int[] {4, 2048}
                    : name.contains("512") ? new int[] {1, 512} : new int[] {1, 1024};
        if (id.startsWith("openai-"))
            return new int[] {4, 1792};
        if (id.startsWith("flux.3-"))
            return new int[] {name.endsWith("4K") ? 16 : name.endsWith("_2K") ? 4 : name.endsWith("1_5K") ? 2 : 1,
                    name.endsWith("4K") ? 4096 : name.endsWith("_2K") ? 2048 : name.endsWith("1_5K") ? 1440
                            : name.endsWith("768") ? 768 : 1024};
        return new int[] {4, 2048};
    }

    @Test
    void theAttachmentLimitOfEveryProviderStaysAsItWas() {
        assertEquals(10L * 1024 * 1024, GenerationModel.getMaxFileSize(ApiProvider.BFL));
        assertEquals(20L * 1024 * 1024, GenerationModel.getMaxFileSize(ApiProvider.GOOGLE_GEMINI));
        assertEquals(50L * 1024 * 1024, GenerationModel.getMaxFileSize(ApiProvider.OPENAI));
    }

    @Test
    void googleSearchAndSafetyToleranceAreParametersOfTheirProviders() {
        for (GenerationModel model : GenerationModel.values()) {
            assertEquals(model.getProvider() == ApiProvider.BFL, model.supportsSafetyTolerance(), model.name());
            assertEquals(model.getProvider() == ApiProvider.GOOGLE_GEMINI && !model.isVideoModel()
                    && model.supportsGroundingSearch(), model.supportsGoogleSearch(), model.name());
        }
    }
}
