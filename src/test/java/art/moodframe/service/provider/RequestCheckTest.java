package art.moodframe.service.provider;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.ImageGenerationRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The provider of a model checks a request before it is sent, by the one table of the models and without guessing the
 * provider from the model id: the limits every provider shares, and those only its models have (BFL: width and height).
 */
class RequestCheckTest {

    private final ProviderRegistry providers = MockProviders.preparing();

    private static ImageGenerationRequest request(String model, int width, int height) {
        ImageGenerationRequest request = new ImageGenerationRequest("a prompt", model);
        request.setWidth(width);
        request.setHeight(height);
        return request;
    }

    /*  the English message of the refusal of a request by the provider of its model  */
    private String refusal(ImageGenerationRequest request) {
        return refusal(providers.forModel(request.getModel()), request);
    }

    private static String refusal(ImageProvider provider, ImageGenerationRequest request) {
        return assertThrows(ProviderException.class, () -> provider.checkRequest(request))
                .getMessage(Locale.ENGLISH);
    }

    private void accepts(ImageGenerationRequest request) {
        assertDoesNotThrow(() -> providers.forModel(request.getModel()).checkRequest(request));
    }

    @Test
    void everyRequestNeedsAPromptAModelAndOneToFourImages() {
        assertEquals("Prompt cannot be empty", refusal(new ImageGenerationRequest(" ", "flux.2-pro")));
        assertEquals("Model must be specified", refusal(new ImageGenerationRequest("a prompt", "")));
        ImageGenerationRequest five = new ImageGenerationRequest("a prompt", "gemini-3-pro-image");
        five.setImageCount(5);
        assertEquals("Image count must be between 1 and 4", refusal(five));
        five.setImageCount(4);
        accepts(five);
    }

    @Test
    void guidanceAndStepsStayWithinTheirParameters() {
        ImageGenerationRequest request = request("flux.2-flex", 1024, 1024);
        request.setGuidance(10.5);
        assertEquals("Guidance must be between 1.5 and 10.0", refusal(request));
        request.setGuidance(5.0);
        request.setSteps(51);
        assertEquals("Steps must be between 1 and 50", refusal(request));
        request.setSteps(50);
        accepts(request);
    }

    @Test
    void flux2AcceptsMultiplesOf16SuchAsThe3To2Ratio() {
        accepts(request("flux.2-pro", 2048, 1360));
        accepts(request("flux.2-klein-4b", 1360, 2048));
        accepts(request("flux.2-flex", 64, 64));
    }

    @Test
    void flux2StillRejectsInvalidSizes() {
        assertEquals("Height must be between 64-2048 and multiple of 16", refusal(request("flux.2-pro", 2048, 1365)));
        assertEquals("Width must be between 64-2048 and multiple of 16", refusal(request("flux.2-pro", 32, 1024)));
        assertEquals("Width must be between 64-2048 and multiple of 16", refusal(request("flux.2-pro", 2064, 1024)));
    }

    @Test
    void aFormerFluxModelGoesToBflWithItsOwnSizes() {
        assertEquals("Width must be between 256-2048 and multiple of 32", refusal(request("flux-dev", 1040, 1024)));
        accepts(request("flux-dev", 1024, 768));
        ImageGenerationRequest attached = request("flux-dev", 1024, 768);
        attached.setInputImages(List.of("aW1hZ2Ux"));
        assertEquals("Input image is not supported with the model flux-dev", refusal(attached));
    }

    @Test
    void flux3IsSizedByItsResolutionTierNotByWidthAndHeight() {
        accepts(request("flux.3-image", 4096, 1755));
        accepts(request("flux.3-image-klein", 768, 768));
    }

    @Test
    void aVideoTakesTheLengthsItsModelOffers() {
        for (int seconds : new int[] {3, 7, 10}) {
            ImageGenerationRequest request = new ImageGenerationRequest("a prompt", "gemini-omni-1.1-flash");
            request.setVideoDuration(seconds);
            accepts(request);
        }
        for (int seconds : new int[] {2, 11}) {
            ImageGenerationRequest request = new ImageGenerationRequest("a prompt", "gemini-omni-1.1-flash");
            request.setVideoDuration(seconds);
            assertEquals("Video duration must be between 3 and 10 seconds", refusal(request), seconds + " s");
        }
    }

    @Test
    void aGatewayModelTakesAttachedImagesAndAnySizeWhateverItsName() {
        ImageProvider openAi = providers.get(ApiProvider.OPENAI);
        for (String model : List.of("qwen-image-2.1-noct", "flux.1-schnell", "gemini-like-image")) {
            ImageGenerationRequest request = request(model, 4096, 2304);
            request.setPrompt("x".repeat(3000));
            request.setInputImages(List.of("aW1hZ2Ux"));
            assertDoesNotThrow(() -> openAi.checkRequest(request), model);
        }
    }

    @Test
    void thePromptLimitOfTheProviderIsPartOfTheCheck() {
        assertEquals("The prompt cannot exceed 2000 characters (BFL (Blackforest Labs) limit).",
                refusal(new ImageGenerationRequest("x".repeat(2001), "flux.2-pro")));
    }
}
