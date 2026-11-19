package art.moodframe.service.brainstorm;

import art.moodframe.model.BrainstormMessage;
import art.moodframe.service.provider.ChatTool;
import art.moodframe.service.provider.ChatTurn;
import art.moodframe.service.provider.gemini.GeminiApiService;
import art.moodframe.service.provider.openai.OpenAiApiService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class BrainstormToolsTest {

    private final GeminiApiService gemini = mock(GeminiApiService.class);
    private final BrainstormTools tools = new BrainstormTools(gemini, null, Runnable::run);

    private static BrainstormTools.Request request(ChatModel model, List<String> status) {
        return new BrainstormTools.Request("s1", model, status::add, () -> { });
    }

    @Test
    void geminiIsOfferedTheVideoToolAndOpenAiOnlyTheResearch() {
        ChatModel openAi = ChatModel.openAi(mock(OpenAiApiService.class), "m");

        assertEquals(List.of("research", "analyze_video"),
                tools.offeredTo(ChatModel.gemini(gemini)).stream().map(ChatTool::name).toList());
        assertEquals(List.of("research"), tools.offeredTo(openAi).stream().map(ChatTool::name).toList());
    }

    @Test
    void anUnknownToolIsReportedToTheModelAsError() {
        ChatTurn.ToolCall call = new ChatTurn.ToolCall("call_0", "list_moods", new JSONObject());

        BrainstormTools.Run run = tools.run(request(ChatModel.gemini(gemini), new ArrayList<>()), call);

        assertSame(call, run.result().call());
        assertTrue(run.result().response().getString("error").contains("Unknown tool list_moods"));
        assertNull(run.note());
    }

    @Test
    void openAiWatchesNoVideoOnAToolCall() {
        ChatModel openAi = ChatModel.openAi(mock(OpenAiApiService.class), "m");
        ChatTurn.ToolCall call = new ChatTurn.ToolCall("call_0", "analyze_video",
                new JSONObject().put("url", "https://youtu.be/dQw4w9WgXcQ").put("question", "q"));

        BrainstormTools.Run run = tools.run(request(openAi, new ArrayList<>()), call);

        assertTrue(run.result().response().has("error"));
        verify(gemini, never()).analyzeYouTubeVideo(anyString(), anyInt(), anyInt(), anyString());
    }

    @Test
    void aMessageWithoutVideoWatchesNothing() {
        List<String> status = new ArrayList<>();

        List<String> turns = tools.watchLinked(request(ChatModel.gemini(gemini), status),
                new BrainstormMessage(BrainstormMessage.ROLE_USER, "Ein Logo"),
                new ArrayList<>());

        assertEquals(List.of(), turns);
        assertEquals(List.of(), status);
        verifyNoInteractions(gemini);
    }
}
