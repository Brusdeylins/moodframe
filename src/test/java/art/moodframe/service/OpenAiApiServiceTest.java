package art.moodframe.service;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiApiServiceTest {

    @Test
    void modelListKeepsAllIdsSortedAndUnique() {
        String body = "{\"object\":\"list\",\"data\":[{\"id\":\"gpt-4o\"},{\"id\":\"gpt-image-1\"},"
                + "{\"id\":\"claude\"},{\"id\":\"gpt-image-1\"},{\"id\":\"a,b\"},{\"id\":\"\"}]}";

        assertEquals(List.of("claude", "gpt-4o", "gpt-image-1"), OpenAiApiServiceImpl.parseModelIds(body));
    }

    @Test
    void knownImageNamesArePreselected() {
        assertEquals(List.of("dall-e-3", "gpt-image-1"),
                OpenAiApiServiceImpl.imageModelsOf(List.of("claude", "dall-e-3", "gpt-4o", "gpt-image-1")));
        assertTrue(OpenAiApiServiceImpl.imageModelsOf(List.of("claude", "codestral")).isEmpty());
    }

    @Test
    void modelListToleratesGarbage() {
        assertTrue(OpenAiApiServiceImpl.parseModelIds("not json").isEmpty());
        assertTrue(OpenAiApiServiceImpl.parseModelIds("{\"error\":{}}").isEmpty());
    }

    @Test
    void sizeFollowsTheAspectRatioAndTheModel() {
        assertEquals("1536x1024", OpenAiApiServiceImpl.sizeFor("gpt-image-1", "16:9", null));
        assertEquals("1024x1536", OpenAiApiServiceImpl.sizeFor("gpt-image-1", "2:3", null));
        assertEquals("1024x1024", OpenAiApiServiceImpl.sizeFor("gpt-image-1", "1:1", null));
        assertEquals("1024x1024", OpenAiApiServiceImpl.sizeFor("gpt-image-1", null, null));
        assertEquals("1792x1024", OpenAiApiServiceImpl.sizeFor("dall-e-3", "3:2", null));
        assertEquals("1024x1792", OpenAiApiServiceImpl.sizeFor("dall-e-3", "9:16", null));
        assertEquals("1024x1024", OpenAiApiServiceImpl.sizeFor("dall-e-2", "3:2", null));
        assertEquals("1360x768", OpenAiApiServiceImpl.sizeFor("qwen-image-2.1", "16:9", null));
        assertEquals("768x1360", OpenAiApiServiceImpl.sizeFor("qwen-image-2.1", "9:16", null));
        assertEquals("1184x896", OpenAiApiServiceImpl.sizeFor("qwen-image-2.1", "4:3", null));
        assertEquals("1024x1024", OpenAiApiServiceImpl.sizeFor("qwen-image-2.1", "1:1", null));
        assertEquals("2736x1536", OpenAiApiServiceImpl.sizeFor("qwen-image-2.1", "16:9", "2K"));
        assertEquals("4096x2304", OpenAiApiServiceImpl.sizeFor("qwen-image-2.1", "16:9", "4K"));
        assertEquals("2304x4096", OpenAiApiServiceImpl.sizeFor("qwen-image-2.1", "9:16", "4K"));
        assertEquals("4096x4096", OpenAiApiServiceImpl.sizeFor("qwen-image-2.1", "1:1", "4K"));
        assertEquals("1536x1024", OpenAiApiServiceImpl.sizeFor("gpt-image-1", "16:9", "4K"));
    }

    @Test
    void generationBodyAsksDalleForBase64ButNotGptImage() {
        ImageGenerationRequest gptImage = new ImageGenerationRequest("a cat", "gpt-image-1");
        gptImage.setAspectRatio("3:2");
        JSONObject body = OpenAiApiServiceImpl.buildGenerationBody(gptImage);
        assertEquals("gpt-image-1", body.getString("model"));
        assertEquals("a cat", body.getString("prompt"));
        assertEquals(1, body.getInt("n"));
        assertEquals("1536x1024", body.getString("size"));
        assertFalse(body.has("response_format"));

        JSONObject dalle = OpenAiApiServiceImpl.buildGenerationBody(new ImageGenerationRequest("a cat", "dall-e-3"));
        assertEquals("b64_json", dalle.getString("response_format"));
    }

    @Test
    void imageTypeComesFromTheFirstBytes() {
        assertEquals("image/png", OpenAiApiServiceImpl.mimeType(
                new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0}));
        assertEquals("image/jpeg", OpenAiApiServiceImpl.mimeType(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
        assertEquals("image/webp", OpenAiApiServiceImpl.mimeType(
                new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0}));
    }

    @Test
    void dropdownItemsCarryTheOpenAiModelId() {
        String item = GenerationModel.OPENAI_PREFIX + "gpt-image-1";

        assertEquals("gpt-image-1", GenerationModel.openAiModelId(item));
        assertNull(GenerationModel.openAiModelId("FLUX.2 [pro] - Production Grade ($0.03/MP)"));
        assertNull(GenerationModel.openAiModelId(GenerationModel.OPENAI_PREFIX));
        assertNull(GenerationModel.openAiModelId(null));
        assertEquals(GenerationModel.OPENAI_IMAGE, GenerationModel.fromLocalizedDisplayName(item));
        assertEquals(ApiProvider.OPENAI, GenerationModel.OPENAI_IMAGE.getProvider());
        assertFalse(GenerationModel.selectable().contains(GenerationModel.OPENAI_IMAGE));
        assertTrue(GenerationModel.OPENAI_IMAGE.supportsImageAttachment());
    }

    @Test
    void openAiRequestsAreValidatedWithoutBflLimits() {
        ImageGenerationRequest request = new ImageGenerationRequest("x".repeat(5000), "gpt-image-1");
        request.setWidth(1536);
        request.setHeight(1024);
        assertDoesNotThrow(request::validate);

        ImageGenerationRequest dalle = new ImageGenerationRequest("x".repeat(5000), "dall-e-3");
        assertThrows(IllegalArgumentException.class, dalle::validate);
        assertEquals(32000, GenerationModel.openAiMaxPromptLength("gpt-image-1"));
        assertEquals(4000, GenerationModel.openAiMaxPromptLength("dall-e-3"));
    }

    @Test
    void chatBodyMapsToolRoundsToToolMessages() {
        JSONArray contents = new JSONArray()
                .put(new JSONObject().put("role", "user").put("parts", new JSONArray().put(new JSONObject().put("text", "hi"))))
                .put(new JSONObject().put("role", "model").put("parts", new JSONArray().put(new JSONObject()
                        .put("functionCall", new JSONObject().put("name", "research").put("args", new JSONObject().put("query", "q"))))))
                .put(new JSONObject().put("role", "user").put("parts", new JSONArray().put(new JSONObject()
                        .put("functionResponse", new JSONObject().put("name", "research").put("response", new JSONObject().put("result", "r"))))));

        JSONObject body = OpenAiApiServiceImpl.buildChatBody("m", "sys", contents, true, false, true);
        JSONArray messages = body.getJSONArray("messages");

        assertEquals(4, messages.length());
        assertEquals("system", messages.getJSONObject(0).getString("role"));
        assertEquals("user", messages.getJSONObject(1).getString("role"));
        JSONObject assistant = messages.getJSONObject(2);
        assertEquals("assistant", assistant.getString("role"));
        String id = assistant.getJSONArray("tool_calls").getJSONObject(0).getString("id");
        assertEquals("research", assistant.getJSONArray("tool_calls").getJSONObject(0).getJSONObject("function").getString("name"));
        assertEquals("tool", messages.getJSONObject(3).getString("role"));
        assertEquals(id, messages.getJSONObject(3).getString("tool_call_id"));
        assertEquals("auto", body.getString("tool_choice"));
        assertEquals(2, body.getJSONArray("tools").length());
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"));
    }

    @Test
    void chatBodyTurnsAnImagePartIntoAnImageUrl() {
        JSONArray contents = new JSONArray().put(new JSONObject().put("role", "user").put("parts", new JSONArray()
                .put(new JSONObject().put("inlineData", new JSONObject().put("mimeType", "image/jpeg").put("data", "QUJD")))
                .put(new JSONObject().put("text", "Find the blocks."))));

        JSONArray content = OpenAiApiServiceImpl.buildChatBody("m", "sys", contents, false, false, false)
                .getJSONArray("messages").getJSONObject(1).getJSONArray("content");

        assertEquals(2, content.length());
        assertEquals("text", content.getJSONObject(0).getString("type"));
        assertEquals("Find the blocks.", content.getJSONObject(0).getString("text"));
        assertEquals("image_url", content.getJSONObject(1).getString("type"));
        assertEquals("data:image/jpeg;base64,QUJD", content.getJSONObject(1).getJSONObject("image_url").getString("url"));
    }

    @Test
    void chatBodyWithoutToolsOffersNone() {
        JSONArray contents = new JSONArray().put(new JSONObject().put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text", "hi"))));

        assertTrue(!OpenAiApiServiceImpl.buildChatBody("m", "sys", contents, false, false, true).has("tools"));
        assertEquals("none", OpenAiApiServiceImpl.buildChatBody("m", "sys", contents, true, true, true).getString("tool_choice"));
    }

    @Test
    void chatBodyOffersTheVideoToolOnlyOnRequest() {
        JSONArray contents = new JSONArray().put(new JSONObject().put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text", "hi"))));

        JSONArray withoutVideo = OpenAiApiServiceImpl.buildChatBody("m", "sys", contents, true, false, false)
                .getJSONArray("tools");
        assertEquals(1, withoutVideo.length());
        assertEquals("research", withoutVideo.getJSONObject(0).getJSONObject("function").getString("name"));
    }

    @Test
    void webResearchOnlyReadsPublicAddresses() {
        assertTrue(!WebResearchService.isPublicHttpUrl("http://localhost:8080/x"));
        assertTrue(!WebResearchService.isPublicHttpUrl("http://127.0.0.1/"));
        assertTrue(!WebResearchService.isPublicHttpUrl("http://192.168.1.1/"));
        assertTrue(!WebResearchService.isPublicHttpUrl("http://169.254.169.254/latest"));
        assertTrue(!WebResearchService.isPublicHttpUrl("file:///etc/passwd"));
    }

    @Test
    void webResearchResolvesSearchLinksAndStripsTags() {
        assertEquals("https://example.org/a b",
                WebResearchService.resolveResultUrl("//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.org%2Fa%20b&amp;rut=x"));
        assertEquals("Hi & bye", WebResearchService.plainText("<p>Hi&nbsp;&amp;\n <b>bye</b></p>"));
    }
}
