package art.moodframe.service.provider;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ImageGenerationRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the Gemini image request body per model, based on parameters confirmed with live requests. */
class GeminiRequestBodyTest {

    private static GeminiApiServiceImpl service;
    private static Method build;

    @BeforeAll
    static void setUp() throws Exception {
        ApplicationConfig config = art.moodframe.config.ConfigMocks.withEmptyTexts();
        service = new GeminiApiServiceImpl(config);
        build = GeminiImageClient.class.getDeclaredMethod("buildRequestBody", ImageGenerationRequest.class);
        build.setAccessible(true);
    }

    private static JSONObject body(String model, String thinking, boolean search) throws Exception {
        ImageGenerationRequest request = new ImageGenerationRequest("an apple", model);
        request.setImageSize("1K");
        request.setAspectRatio("1:4");
        request.setThinkingMode(thinking);
        request.setEnableGoogleSearch(search);
        return (JSONObject) build.invoke(service, request);
    }

    @Test
    void liteDoesNotGetTheSearchToolButTheOtherModelsDo() throws Exception {
        assertFalse(body("gemini-3.1-flash-lite-image", null, true).has("tools"));
        assertTrue(body("gemini-3.1-flash-image", null, true).has("tools"));
        assertTrue(body("gemini-3-pro-image", null, true).has("tools"));
        assertFalse(body("gemini-3.1-flash-image", null, false).has("tools"));
    }

    @Test
    void thinkingLevelIsSentOnlyForNanoBanana2AndLiteAndOnlyMinimalOrHigh() throws Exception {
        JSONObject nb2 = body("gemini-3.1-flash-image", "minimal", false);
        assertEquals("minimal", nb2.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"));
        JSONObject lite = body("gemini-3.1-flash-lite-image", "high", false);
        assertEquals("high", lite.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"));

        assertFalse(body("gemini-3.1-flash-image", "low", false).getJSONObject("generationConfig").has("thinkingConfig"));
        assertFalse(body("gemini-3-pro-image", "high", false).getJSONObject("generationConfig").has("thinkingConfig"));
        assertFalse(body("gemini-3.1-flash-image", null, false).getJSONObject("generationConfig").has("thinkingConfig"));
    }

    @Test
    void chatBodyPassesTheImageOnAsInlineDataAndAsksForJson() {
        List<ChatTurn> turns = List.of(new ChatTurn.User("Find the blocks.",
                List.of(new ChatTurn.Image("image/jpeg", "QUJD"))));

        JSONObject body = GeminiChatClient.buildChatBody("sys", turns, List.of(), false);

        JSONArray parts = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts");
        assertEquals("image/jpeg", parts.getJSONObject(0).getJSONObject("inlineData").getString("mimeType"));
        assertEquals("QUJD", parts.getJSONObject(0).getJSONObject("inlineData").getString("data"));
        assertEquals("Find the blocks.", parts.getJSONObject(1).getString("text"));
        assertEquals("application/json", body.getJSONObject("generationConfig").getString("responseMimeType"));
        assertFalse(body.has("tools"));
    }

    @Test
    void chatBodySendsAGeminiAnswerBackWithItsSignatureAndTheToolResultsAsFunctionResponses() {
        JSONArray answerParts = new JSONArray().put(new JSONObject().put("thoughtSignature", "sig")
                .put("functionCall", new JSONObject().put("name", "research").put("args",
                        new JSONObject().put("query", "q"))));
        ChatTurn.Model answer = GeminiChatClient.parseAnswer(answerParts);
        ChatTool research = new ChatTool("research", "Searches the web.", new JSONObject().put("type", "object"));
        List<ChatTurn> turns = List.of(new ChatTurn.User("hi"), answer, new ChatTurn.ToolResults(
                List.of(new ChatTurn.ToolResult(answer.calls().get(0), new JSONObject().put("result", "r")))));

        JSONObject body = GeminiChatClient.buildChatBody("sys", turns, List.of(research), true);

        JSONArray contents = body.getJSONArray("contents");
        assertEquals("model", contents.getJSONObject(1).getString("role"));
        assertEquals("sig", contents.getJSONObject(1).getJSONArray("parts").getJSONObject(0)
                .getString("thoughtSignature"));
        JSONObject response = contents.getJSONObject(2).getJSONArray("parts").getJSONObject(0)
                .getJSONObject("functionResponse");
        assertEquals("user", contents.getJSONObject(2).getString("role"));
        assertEquals("research", response.getString("name"));
        assertEquals("r", response.getJSONObject("response").getString("result"));
        assertEquals("research", body.getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations")
                .getJSONObject(0).getString("name"));
        assertEquals("NONE", body.getJSONObject("toolConfig").getJSONObject("functionCallingConfig")
                .getString("mode"));
    }

    @Test
    void chatBodyBuildsTheAnswerOfAnotherModelFromItsTextAndCalls() {
        ChatTurn.Model answer = new ChatTurn.Model("", List.of(new ChatTurn.ToolCall("call_x", "research",
                new JSONObject().put("query", "q"))), null);

        JSONObject body = GeminiChatClient.buildChatBody("sys", List.of(new ChatTurn.User("hi"), answer), List.of(),
                false);

        JSONObject call = body.getJSONArray("contents").getJSONObject(1).getJSONArray("parts").getJSONObject(0)
                .getJSONObject("functionCall");
        assertEquals("research", call.getString("name"));
        assertEquals("q", call.getJSONObject("args").getString("query"));
    }

    @Test
    void imageSizeAndAspectRatioArePassedThrough() throws Exception {
        JSONObject imageConfig = body("gemini-3.1-flash-lite-image", null, false)
                .getJSONObject("generationConfig").getJSONObject("imageConfig");
        assertEquals("1K", imageConfig.getString("imageSize"));
        assertEquals("1:4", imageConfig.getString("aspectRatio"));
    }
}
