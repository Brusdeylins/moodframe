package art.moodframe.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageGenerationRequestTest {

    private static ImageGenerationRequest request(String model, int width, int height) {
        ImageGenerationRequest request = new ImageGenerationRequest("a prompt", model);
        request.setWidth(width);
        request.setHeight(height);
        return request;
    }

    @Test
    void flux2AcceptsMultiplesOf16SuchAsThe3To2Ratio() {
        assertDoesNotThrow(() -> request("flux.2-pro", 2048, 1360).validate());
        assertDoesNotThrow(() -> request("flux.2-klein-4b", 1360, 2048).validate());
        assertDoesNotThrow(() -> request("flux.2-flex", 64, 64).validate());
    }

    @Test
    void flux2StillRejectsInvalidSizes() {
        assertThrows(IllegalArgumentException.class, () -> request("flux.2-pro", 2048, 1365).validate());
        assertThrows(IllegalArgumentException.class, () -> request("flux.2-pro", 32, 1024).validate());
        assertThrows(IllegalArgumentException.class, () -> request("flux.2-pro", 2064, 1024).validate());
    }

    @Test
    void omniAcceptsDurationsFrom3To10SecondsOnly() {
        for (int seconds : new int[] {3, 7, 10}) {
            ImageGenerationRequest request = new ImageGenerationRequest("a prompt", "gemini-omni-1.1-flash");
            request.setVideoDuration(seconds);
            assertDoesNotThrow(request::validate, seconds + " s");
        }
        for (int seconds : new int[] {2, 11}) {
            ImageGenerationRequest request = new ImageGenerationRequest("a prompt", "gemini-omni-1.1-flash");
            request.setVideoDuration(seconds);
            assertThrows(IllegalArgumentException.class, request::validate, seconds + " s");
        }
    }

    @Test
    void aGatewayModelTakesAttachedImagesAndLongPrompts() {
        ImageGenerationRequest request = request("qwen-image-2.1-noct", 4096, 2304);
        request.setPrompt("x".repeat(3000));
        request.setInputImages(java.util.List.of("aW1hZ2Ux"));
        assertDoesNotThrow(request::validate);
    }

    @Test
    void flux3IsSizedByItsResolutionTierNotByWidthAndHeight() {
        assertDoesNotThrow(() -> request("flux.3-image", 4096, 1755).validate());
        assertDoesNotThrow(() -> request("flux.3-image-klein", 768, 768).validate());
    }
}
