package art.moodframe.service.provider.openai;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.model.ResearchNote;
import art.moodframe.service.provider.ApiHttpException;
import art.moodframe.service.provider.ChatTool;
import art.moodframe.service.provider.ChatTurn;
import art.moodframe.service.provider.MockProviders;
import art.moodframe.service.provider.ProviderException;
import okhttp3.OkHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Supplier;

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

        assertEquals(List.of("claude", "gpt-4o", "gpt-image-1"), OpenAiAccounts.parseModelIds(body));
    }

    @Test
    void knownImageNamesArePreselected() {
        assertEquals(List.of("dall-e-3", "gpt-image-1"),
                OpenAiAccounts.imageModelsOf(List.of("claude", "dall-e-3", "gpt-4o", "gpt-image-1")));
        assertTrue(OpenAiAccounts.imageModelsOf(List.of("claude", "codestral")).isEmpty());
    }

    @Test
    void modelListToleratesGarbage() {
        assertTrue(OpenAiAccounts.parseModelIds("not json").isEmpty());
        assertTrue(OpenAiAccounts.parseModelIds("{\"error\":{}}").isEmpty());
    }

    @Test
    void sizeFollowsTheAspectRatioAndTheModel() {
        assertEquals("1536x1024", OpenAiImageClient.sizeFor("gpt-image-1", "16:9", null));
        assertEquals("1024x1536", OpenAiImageClient.sizeFor("gpt-image-1", "2:3", null));
        assertEquals("1024x1024", OpenAiImageClient.sizeFor("gpt-image-1", "1:1", null));
        assertEquals("1024x1024", OpenAiImageClient.sizeFor("gpt-image-1", null, null));
        assertEquals("1792x1024", OpenAiImageClient.sizeFor("dall-e-3", "3:2", null));
        assertEquals("1024x1792", OpenAiImageClient.sizeFor("dall-e-3", "9:16", null));
        assertEquals("1024x1024", OpenAiImageClient.sizeFor("dall-e-2", "3:2", null));
        assertEquals("1360x768", OpenAiImageClient.sizeFor("qwen-image-2.1", "16:9", null));
        assertEquals("768x1360", OpenAiImageClient.sizeFor("qwen-image-2.1", "9:16", null));
        assertEquals("1184x896", OpenAiImageClient.sizeFor("qwen-image-2.1", "4:3", null));
        assertEquals("1024x1024", OpenAiImageClient.sizeFor("qwen-image-2.1", "1:1", null));
        assertEquals("2736x1536", OpenAiImageClient.sizeFor("qwen-image-2.1", "16:9", "2K"));
        assertEquals("4096x2304", OpenAiImageClient.sizeFor("qwen-image-2.1", "16:9", "4K"));
        assertEquals("2304x4096", OpenAiImageClient.sizeFor("qwen-image-2.1", "9:16", "4K"));
        assertEquals("4096x4096", OpenAiImageClient.sizeFor("qwen-image-2.1", "1:1", "4K"));
        assertEquals("1536x1024", OpenAiImageClient.sizeFor("gpt-image-1", "16:9", "4K"));
    }

    @Test
    void generationBodyAsksDalleForBase64ButNotGptImage() {
        ImageGenerationRequest gptImage = new ImageGenerationRequest("a cat", "gpt-image-1");
        gptImage.setAspectRatio("3:2");
        JSONObject body = OpenAiImageClient.buildGenerationBody(gptImage);
        assertEquals("gpt-image-1", body.getString("model"));
        assertEquals("a cat", body.getString("prompt"));
        assertEquals(1, body.getInt("n"));
        assertEquals("1536x1024", body.getString("size"));
        assertFalse(body.has("response_format"));

        JSONObject dalle = OpenAiImageClient.buildGenerationBody(new ImageGenerationRequest("a cat", "dall-e-3"));
        assertEquals("b64_json", dalle.getString("response_format"));
    }

    @Test
    void imageTypeComesFromTheFirstBytes() {
        assertEquals("image/png", OpenAiImageClient.mimeType(
                new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0}));
        assertEquals("image/jpeg", OpenAiImageClient.mimeType(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
        assertEquals("image/webp", OpenAiImageClient.mimeType(
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
        OpenAiApiService openAi = MockProviders.preparing().get(ApiProvider.OPENAI, OpenAiApiService.class);
        assertThrows(ProviderException.class, () -> openAi.checkPromptLength(dalle));
        assertDoesNotThrow(() -> openAi.checkPromptLength(request));
        assertEquals(32000, OpenAiApiService.promptLimitOf("gpt-image-1"));
        assertEquals(4000, OpenAiApiService.promptLimitOf("dall-e-3"));
        assertEquals(1000, OpenAiApiService.promptLimitOf("dall-e-2"));
    }

    private static final ChatTool RESEARCH = new ChatTool("research", "Searches the web.",
            new JSONObject().put("type", "object"));

    @Test
    void chatBodyMapsToolRoundsToToolMessages() {
        ChatTurn.Model answer = OpenAiChatClient.parseAnswer(new JSONObject().put("content", JSONObject.NULL)
                .put("tool_calls", new JSONArray().put(new JSONObject().put("id", "call_abc").put("type", "function")
                        .put("function", new JSONObject().put("name", "research")
                                .put("arguments", "{\"query\":\"q\"}")))));
        List<ChatTurn> turns = List.of(new ChatTurn.User("hi"), answer, new ChatTurn.ToolResults(
                List.of(new ChatTurn.ToolResult(answer.calls().get(0), new JSONObject().put("result", "r")))));

        JSONObject body = OpenAiChatClient.buildChatBody("m", "sys", turns, List.of(RESEARCH), false);
        JSONArray messages = body.getJSONArray("messages");

        assertEquals(4, messages.length());
        assertEquals("system", messages.getJSONObject(0).getString("role"));
        assertEquals("user", messages.getJSONObject(1).getString("role"));
        JSONObject assistant = messages.getJSONObject(2);
        assertEquals("assistant", assistant.getString("role"));
        assertEquals("call_abc", assistant.getJSONArray("tool_calls").getJSONObject(0).getString("id"),
                "the id of OpenAI is kept");
        assertEquals("research", assistant.getJSONArray("tool_calls").getJSONObject(0).getJSONObject("function")
                .getString("name"));
        assertEquals("q", answer.calls().get(0).args().getString("query"));
        assertEquals("tool", messages.getJSONObject(3).getString("role"));
        assertEquals("call_abc", messages.getJSONObject(3).getString("tool_call_id"));
        assertEquals("r", new JSONObject(messages.getJSONObject(3).getString("content")).getString("result"));
        assertEquals("auto", body.getString("tool_choice"));
        assertEquals(1, body.getJSONArray("tools").length());
        assertEquals("research", body.getJSONArray("tools").getJSONObject(0).getJSONObject("function").getString("name"));
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"));
    }

    @Test
    void chatBodyTurnsAnImageIntoAnImageUrl() {
        List<ChatTurn> turns = List.of(new ChatTurn.User("Find the blocks.",
                List.of(new ChatTurn.Image("image/jpeg", "QUJD"))));

        JSONArray content = OpenAiChatClient.buildChatBody("m", "sys", turns, List.of(), false)
                .getJSONArray("messages").getJSONObject(1).getJSONArray("content");

        assertEquals(2, content.length());
        assertEquals("text", content.getJSONObject(0).getString("type"));
        assertEquals("Find the blocks.", content.getJSONObject(0).getString("text"));
        assertEquals("image_url", content.getJSONObject(1).getString("type"));
        assertEquals("data:image/jpeg;base64,QUJD", content.getJSONObject(1).getJSONObject("image_url").getString("url"));
    }

    @Test
    void chatBodyWithoutToolsOffersNone() {
        List<ChatTurn> turns = List.of(new ChatTurn.User("hi"));

        assertFalse(OpenAiChatClient.buildChatBody("m", "sys", turns, List.of(), false).has("tools"));
        assertEquals("none", OpenAiChatClient.buildChatBody("m", "sys", turns, List.of(RESEARCH), true)
                .getString("tool_choice"));
    }

    @Test
    void anAnswerWithoutIdsGetsIdsOfItsOwnAndBrokenArgumentsCountAsNone() {
        ChatTurn.Model answer = OpenAiChatClient.parseAnswer(new JSONObject().put("content", "")
                .put("tool_calls", new JSONArray().put(new JSONObject().put("function", new JSONObject()
                        .put("name", "research").put("arguments", "{not json")))));

        assertEquals("call_0", answer.calls().get(0).id());
        assertTrue(answer.calls().get(0).args().isEmpty());
        assertNull(answer.replay());
    }

    @Test
    void webSearchBodyForcesTheBuiltInSearch() {
        JSONObject body = OpenAiChatClient.webSearchBody("m", "question");

        assertEquals("m", body.getString("model"));
        assertEquals("question", body.getString("input"));
        assertEquals("web_search", body.getJSONArray("tools").getJSONObject(0).getString("type"));
        assertEquals("required", body.getString("tool_choice"));
    }

    @Test
    void researchAnswerYieldsTheTextAndEachCitedSourceOnce() {
        String body = new JSONObject().put("output", new JSONArray()
                .put(new JSONObject().put("type", "web_search_call").put("id", "ws_1").put("status", "completed"))
                .put(new JSONObject().put("type", "message").put("role", "assistant").put("content", new JSONArray()
                        .put(new JSONObject().put("type", "output_text").put("text", " The nautilus has 30 chambers. ")
                                .put("annotations", new JSONArray()
                                        .put(citation("https://www.nautilus.example/shell?utm_source=openai"))
                                        .put(citation("https://facts.example.org/n"))
                                        .put(citation("https://www.nautilus.example/shell?utm_source=openai"))))))).toString();

        ResearchNote note = OpenAiChatClient.parseResearch("nautilus", body);

        assertEquals("nautilus", note.query());
        assertEquals("The nautilus has 30 chambers.", note.result());
        assertEquals(List.of(
                new ResearchNote.Source("nautilus.example", "https://www.nautilus.example/shell?utm_source=openai"),
                new ResearchNote.Source("facts.example.org", "https://facts.example.org/n")), note.sources());
    }

    @Test
    void researchAnswerWithoutTextFails() {
        String body = new JSONObject().put("output", new JSONArray()
                .put(new JSONObject().put("type", "web_search_call").put("status", "completed"))).toString();

        assertThrows(RuntimeException.class, () -> OpenAiChatClient.parseResearch("q", body));
    }

    @Test
    void researchKeepsTheTextAndTheOtherSourcesWhenACitedUrlIsBroken() {
        String body = new JSONObject().put("output", new JSONArray()
                .put(new JSONObject().put("type", "message").put("role", "assistant").put("content", new JSONArray()
                        .put(new JSONObject().put("type", "output_text").put("text", "The nautilus floats.")
                                .put("annotations", new JSONArray()
                                        .put(citation("https://bad.example/a b{c}"))
                                        .put(citation("https://facts.example.org/n"))))))).toString();

        ResearchNote note = OpenAiChatClient.parseResearch("nautilus", body);

        assertEquals("The nautilus floats.", note.result());
        assertEquals(List.of(
                new ResearchNote.Source("https://bad.example/a b{c}", "https://bad.example/a b{c}"),
                new ResearchNote.Source("facts.example.org", "https://facts.example.org/n")), note.sources());
    }

    @Test
    void webSearchCheckWorksIfTheModelSearched() {
        String body = new JSONObject().put("output", new JSONArray()
                .put(new JSONObject().put("type", "web_search_call").put("status", "completed"))
                .put(new JSONObject().put("type", "message").put("role", "assistant"))).toString();

        assertNull(OpenAiChatClient.webSearchProblem(200, body));
    }

    @Test
    void webSearchCheckNamesTheReasonIfTheModelDidNotSearch() {
        String body = new JSONObject().put("output", new JSONArray()
                .put(new JSONObject().put("type", "message").put("role", "assistant"))).toString();

        assertEquals("The model did not search the web.", OpenAiChatClient.webSearchProblem(200, body));
    }

    @Test
    void aFailedSearchCallIsNoWorkingSearch() {
        String body = new JSONObject().put("output", new JSONArray()
                .put(new JSONObject().put("type", "web_search_call").put("status", "failed"))
                .put(new JSONObject().put("type", "message").put("role", "assistant"))).toString();

        assertEquals("The model did not search the web.", OpenAiChatClient.webSearchProblem(200, body));
    }

    @Test
    void webSearchCheckNamesTheErrorOfTheApi() {
        String body = new JSONObject().put("error", new JSONObject()
                .put("message", "Tool 'web_search' is not supported with this model.")).toString();

        assertEquals("OpenAI error 400: Tool 'web_search' is not supported with this model.",
                OpenAiChatClient.webSearchProblem(400, body));
        assertEquals("OpenAI error 404", OpenAiChatClient.webSearchProblem(404, "Not Found"));
    }

    @Test
    void webSearchCheckDecidesNothingOnATransientError() {
        assertEquals(429, assertThrows(ApiHttpException.class,
                () -> OpenAiChatClient.webSearchProblem(429, "{}")).getStatusCode());
        assertEquals(503, assertThrows(ApiHttpException.class,
                () -> OpenAiChatClient.webSearchProblem(503, "Service Unavailable")).getStatusCode());
    }

    @Test
    void webSearchCheckAsksForTheCurrentTimeAndCountsATimeWithoutSearchAsNoSearch() {
        assertTrue(ChatTurn.SEARCH_CHECK_QUESTION.contains("what time is it right now"));
        String body = new JSONObject().put("output", new JSONArray()
                .put(new JSONObject().put("type", "message").put("role", "assistant").put("content", new JSONArray()
                        .put(new JSONObject().put("type", "output_text").put("text", "14:32"))))).toString();

        assertEquals("The model did not search the web.", OpenAiChatClient.webSearchProblem(200, body));
    }

    @Test
    void openAiResearchWaitsAtMost60Seconds() throws Exception {
        Field transport = OpenAiApiServiceImpl.class.getDeclaredField("transport");
        transport.setAccessible(true);

        OpenAiApiServiceImpl service = new OpenAiApiServiceImpl(art.moodframe.config.ConfigMocks.withEmptyTexts());
        Supplier<OkHttpClient> research = ((OpenAiTransport) transport.get(service)).researchClient();
        assertEquals(60_000, research.get().readTimeoutMillis());
    }

    private static JSONObject citation(String url) {
        return new JSONObject().put("type", "url_citation").put("url", url).put("title", "t")
                .put("start_index", 0).put("end_index", 5);
    }
}
