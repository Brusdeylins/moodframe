package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.model.ImageGenerationRequest;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Checks the FLUX.2 and FLUX 3 request bodies against the BFL OpenAPI schemas (unknown fields give HTTP 422). */
class BflRequestBodyTest {

    private static BflApiServiceImpl service;
    private static Method build;

    @BeforeAll
    static void setUp() throws Exception {
        ApplicationConfig config = mock(ApplicationConfig.class, invocation ->
                invocation.getMethod().getReturnType() == String.class ? "" : org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation));
        ServiceFactory.getInstance().registerService(ApplicationConfig.class, config);
        service = new BflApiServiceImpl();
        build = BflApiServiceImpl.class.getDeclaredMethod("buildModelSpecificRequestBody", ImageGenerationRequest.class);
        build.setAccessible(true);
    }

    private static JSONObject body(String model, Boolean upsampling) throws Exception {
        ImageGenerationRequest request = new ImageGenerationRequest("an apple", model);
        request.setWidth(1024);
        request.setHeight(1024);
        request.setPromptUpsampling(upsampling);
        return new JSONObject((String) build.invoke(service, request));
    }

    @Test
    void proAndMaxUseDisablePupNeverPromptUpsampling() throws Exception {
        for (String model : new String[] {"flux.2-pro", "flux.2-max"}) {
            assertTrue(body(model, false).getBoolean("disable_pup"), model + " with upsampling off");
            assertFalse(body(model, true).getBoolean("disable_pup"), model + " with upsampling on");
            assertFalse(body(model, true).has("prompt_upsampling"), model);
        }
    }

    @Test
    void flexUsesPromptUpsamplingNeverDisablePup() throws Exception {
        assertFalse(body("flux.2-flex", false).getBoolean("prompt_upsampling"));
        assertTrue(body("flux.2-flex", true).getBoolean("prompt_upsampling"));
        assertFalse(body("flux.2-flex", false).has("disable_pup"));
    }

    @Test
    void kleinGetsNeitherUpsamplingField() throws Exception {
        for (String model : new String[] {"flux.2-klein-4b", "flux.2-klein-9b"}) {
            JSONObject json = body(model, false);
            assertFalse(json.has("disable_pup"), model);
            assertFalse(json.has("prompt_upsampling"), model);
            assertEquals(1024, json.getInt("width"));
        }
    }

    @Test
    void nothingIsSentWithoutAnUpsamplingChoice() throws Exception {
        JSONObject json = body("flux.2-pro", null);
        assertFalse(json.has("disable_pup"));
        assertFalse(json.has("prompt_upsampling"));
    }

    private static JSONObject flux3Body(String model) throws Exception {
        ImageGenerationRequest request = new ImageGenerationRequest("an apple", model);
        request.setWidth(4096);
        request.setHeight(2304);
        request.setAspectRatio("16:9");
        request.setImageSize("4k");
        request.setSeed(7L);
        request.setSafetyTolerance(2);
        request.setPromptUpsampling(true);
        request.setInputImages(java.util.List.of("aW1hZ2Ux", "aW1hZ2Uy"));
        return new JSONObject((String) build.invoke(service, request));
    }

    @Test
    void flux3ImageSendsResolutionAspectRatioAndImagesOnly() throws Exception {
        JSONObject json = flux3Body("flux.3-image");
        assertEquals(java.util.Set.of("prompt", "aspect_ratio", "resolution", "safety_tolerance", "images"), json.keySet());
        assertEquals("4k", json.getString("resolution"));
        assertEquals(2, json.getJSONArray("images").length());
    }

    @Test
    void flux3KleinAlsoSendsSeedAndOutputFormat() throws Exception {
        JSONObject json = flux3Body("flux.3-image-klein");
        assertEquals(7, json.getInt("seed"));
        assertEquals("png", json.getString("output_format"));
        assertFalse(json.has("width"));
        assertFalse(json.has("prompt_upsampling"));
    }
}
