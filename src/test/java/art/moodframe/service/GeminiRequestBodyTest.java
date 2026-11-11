package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ImageGenerationRequest;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Checks the Gemini image request body per model, based on parameters confirmed with live requests. */
class GeminiRequestBodyTest {

    private static GeminiApiServiceImpl service;
    private static Method build;

    @BeforeAll
    static void setUp() throws Exception {
        ApplicationConfig config = mock(ApplicationConfig.class, invocation ->
                invocation.getMethod().getReturnType() == String.class ? "" : org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation));
        service = new GeminiApiServiceImpl(config);
        build = GeminiApiServiceImpl.class.getDeclaredMethod("buildGeminiRequestBody", ImageGenerationRequest.class);
        build.setAccessible(true);
    }

    private static JSONObject body(String model, String thinking, boolean search, String videoUrl) throws Exception {
        ImageGenerationRequest request = new ImageGenerationRequest("an apple", model);
        request.setImageSize("1K");
        request.setAspectRatio("1:4");
        request.setThinkingMode(thinking);
        request.setEnableGoogleSearch(search);
        if (videoUrl != null) {
            request.setVideoSourceUrl(videoUrl);
            request.setVideoStartOffsetSeconds(0);
            request.setVideoEndOffsetSeconds(0);
        }
        return (JSONObject) build.invoke(service, request);
    }

    @Test
    void liteDoesNotGetTheSearchToolButTheOtherModelsDo() throws Exception {
        assertFalse(body("gemini-3.1-flash-lite-image", null, true, null).has("tools"));
        assertTrue(body("gemini-3.1-flash-image", null, true, null).has("tools"));
        assertTrue(body("gemini-3-pro-image", null, true, null).has("tools"));
        assertFalse(body("gemini-3.1-flash-image", null, false, null).has("tools"));
    }

    @Test
    void thinkingLevelIsSentOnlyForNanoBanana2AndLiteAndOnlyMinimalOrHigh() throws Exception {
        JSONObject nb2 = body("gemini-3.1-flash-image", "minimal", false, null);
        assertEquals("minimal", nb2.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"));
        JSONObject lite = body("gemini-3.1-flash-lite-image", "high", false, null);
        assertEquals("high", lite.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"));

        assertFalse(body("gemini-3.1-flash-image", "low", false, null).getJSONObject("generationConfig").has("thinkingConfig"));
        assertFalse(body("gemini-3-pro-image", "high", false, null).getJSONObject("generationConfig").has("thinkingConfig"));
        assertFalse(body("gemini-3.1-flash-image", null, false, null).getJSONObject("generationConfig").has("thinkingConfig"));
    }

    @Test
    void videoSourceIsPassedForNanoBanana2AndLiteButNotForPro() throws Exception {
        String url = "https://www.youtube.com/watch?v=jNQXAC9IVRw";
        assertTrue(body("gemini-3.1-flash-lite-image", null, false, url).toString().contains("jNQXAC9IVRw"));
        assertTrue(body("gemini-3.1-flash-image", null, false, url).toString().contains("jNQXAC9IVRw"));
        assertFalse(body("gemini-3-pro-image", null, false, url).toString().contains("jNQXAC9IVRw"));
    }

    @Test
    void imageSizeAndAspectRatioArePassedThrough() throws Exception {
        JSONObject imageConfig = body("gemini-3.1-flash-lite-image", null, false, null)
                .getJSONObject("generationConfig").getJSONObject("imageConfig");
        assertEquals("1K", imageConfig.getString("imageSize"));
        assertEquals("1:4", imageConfig.getString("aspectRatio"));
    }
}
