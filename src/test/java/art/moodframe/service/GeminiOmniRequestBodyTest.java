package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ImageGenerationRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Checks the Interactions API request and response handling of Gemini Omni, as confirmed with live requests. */
class GeminiOmniRequestBodyTest {

    private static GeminiApiServiceImpl service;
    private static Method build;
    private static Method extractUri;
    private static Method extractError;

    @BeforeAll
    static void setUp() throws Exception {
        ApplicationConfig config = mock(ApplicationConfig.class, invocation ->
                invocation.getMethod().getReturnType() == String.class ? "" : org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation));
        service = new GeminiApiServiceImpl(config);
        build = GeminiApiServiceImpl.class.getDeclaredMethod("buildOmniRequestBody", ImageGenerationRequest.class);
        extractUri = GeminiApiServiceImpl.class.getDeclaredMethod("extractOmniVideoUri", JSONObject.class);
        extractError = GeminiApiServiceImpl.class.getDeclaredMethod("extractOmniError", JSONObject.class);
        build.setAccessible(true);
        extractUri.setAccessible(true);
        extractError.setAccessible(true);
    }

    private static ImageGenerationRequest request(Integer seconds, String ratio, String resolution) {
        ImageGenerationRequest request = new ImageGenerationRequest("a cat on a roof", "gemini-omni-1.1-flash");
        request.setVideoDuration(seconds);
        request.setAspectRatio(ratio);
        request.setVideoResolution(resolution);
        return request;
    }

    @Test
    void textToVideoSendsAStringInputWithTheDurationInThePrompt() throws Exception {
        JSONObject body = (JSONObject) build.invoke(service, request(5, "9:16", "1080p"));

        assertEquals("gemini-omni-1.1-flash", body.getString("model"));
        assertEquals("A 5-second video: a cat on a roof", body.getString("input"));
        assertTrue(body.getBoolean("store"), "delivery by URI needs the stored interaction");

        JSONObject format = body.getJSONObject("response_format");
        assertEquals("video", format.getString("type"));
        assertEquals("9:16", format.getString("aspect_ratio"));
        assertEquals("1080p", format.getString("resolution"));
        assertEquals("uri", format.getString("delivery"));
    }

    @Test
    void defaultsAreLandscapeAnd720pAndNoDurationLeavesThePromptAlone() throws Exception {
        JSONObject body = (JSONObject) build.invoke(service, request(null, "1:1", null));

        assertEquals("a cat on a roof", body.getString("input"));
        assertEquals("16:9", body.getJSONObject("response_format").getString("aspect_ratio"));
        assertEquals("720p", body.getJSONObject("response_format").getString("resolution"));
    }

    @Test
    void imageToVideoSendsTheImageBeforeTheTextWithItsMimeType() throws Exception {
        ImageGenerationRequest request = request(4, "16:9", "720p");
        request.setInputImage("/9j/4AAQSkZJRg==");

        JSONArray input = ((JSONObject) build.invoke(service, request)).getJSONArray("input");

        assertEquals(2, input.length());
        assertEquals("image", input.getJSONObject(0).getString("type"));
        assertEquals("image/jpeg", input.getJSONObject(0).getString("mime_type"));
        assertEquals("/9j/4AAQSkZJRg==", input.getJSONObject(0).getString("data"));
        assertEquals("text", input.getJSONObject(1).getString("type"));
        assertTrue(input.getJSONObject(1).getString("text").startsWith("A 4-second video: The attached image <IMAGE_REF_0>"));
        assertTrue(input.getJSONObject(1).getString("text").endsWith("a cat on a roof"));

        request.setInputImage("iVBORw0KGgo=");
        assertEquals("image/png", ((JSONObject) build.invoke(service, request)).getJSONArray("input")
                .getJSONObject(0).getString("mime_type"));
    }

    @Test
    void anAttachedImageIsAReferenceUnlessThePromptNamesTheFirstFrame() throws Exception {
        ImageGenerationRequest reference = request(null, "16:9", "720p");
        reference.setInputImage("iVBORw0KGgo=");
        JSONObject body = (JSONObject) build.invoke(service, reference);
        assertEquals("reference_to_video", body.getJSONObject("generation_config").getJSONObject("video_config").getString("task"));

        reference.setPrompt("Put the logo from <IMAGE_REF_0> on the cup");
        JSONArray input = ((JSONObject) build.invoke(service, reference)).getJSONArray("input");
        assertEquals("Put the logo from <IMAGE_REF_0> on the cup", input.getJSONObject(1).getString("text"), "an own tag is kept");

        ImageGenerationRequest first = request(null, "16:9", "720p");
        first.setInputImage("iVBORw0KGgo=");
        first.setPrompt("<FIRST_FRAME> then the camera pulls back");
        JSONObject firstBody = (JSONObject) build.invoke(service, first);
        assertEquals("image_to_video", firstBody.getJSONObject("generation_config").getJSONObject("video_config").getString("task"));
        assertEquals("<FIRST_FRAME> then the camera pulls back", firstBody.getJSONArray("input").getJSONObject(1).getString("text"));
    }

    @Test
    void withoutAnImageThereIsNoTask() throws Exception {
        JSONObject body = (JSONObject) build.invoke(service, request(5, "16:9", "720p"));

        assertEquals(false, body.has("generation_config"));
    }

    @Test
    void theVideoUriIsTakenFromTheModelOutputStep() throws Exception {
        JSONObject interaction = new JSONObject("""
                {"id":"i1","status":"completed","steps":[
                  {"type":"user_input","content":[{"type":"text","text":"x"}]},
                  {"type":"model_output","content":[{"type":"video","uri":"https://generativelanguage.googleapis.com/v1beta/files/abc123:download?alt=media"}]}]}""");

        assertEquals("https://generativelanguage.googleapis.com/v1beta/files/abc123:download?alt=media",
                extractUri.invoke(service, interaction));
        assertNull(extractUri.invoke(service, new JSONObject("{\"status\":\"failed\"}")));
    }

    @Test
    void noVideoIsExplainedByTheErrorsThenTheModelTextThenTheStatus() throws Exception {
        assertEquals("blocked by safety", extractError.invoke(service, new JSONObject(
                "{\"status\":\"failed\",\"errors\":[{\"message\":\"blocked by safety\"}]}")));
        assertEquals("I cannot make that.", extractError.invoke(service, new JSONObject(
                "{\"steps\":[{\"type\":\"model_output\",\"content\":[{\"type\":\"text\",\"text\":\"I cannot make that.\"}]}]}")));
        assertEquals("No video in response (status: incomplete)", extractError.invoke(service, new JSONObject(
                "{\"status\":\"incomplete\"}")));
    }
}
