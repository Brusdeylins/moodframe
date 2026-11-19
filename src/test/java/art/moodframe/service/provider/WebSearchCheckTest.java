package art.moodframe.service.provider;

import art.moodframe.model.ApiProvider;
import art.moodframe.config.ApplicationConfig;
import art.moodframe.service.provider.bfl.BflApiServiceImpl;
import art.moodframe.service.provider.gemini.GeminiApiServiceImpl;
import art.moodframe.service.provider.openai.OpenAiApiServiceImpl;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Sends the checks of the language models and their web search to a local server: the question, the retry of Gemini,
 * the definite and the transient outcomes.
 */
class WebSearchCheckTest {

    private record Answer(int status, String body) {
    }

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final Queue<Answer> answers = new ConcurrentLinkedQueue<>();
    private HttpServer server;
    private String baseUrl;
    private ApplicationConfig config;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", (exchange) -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Answer answer = answers.isEmpty() ? new Answer(500, "{}") : answers.poll();
            byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(answer.status(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        /*  With a path, the OpenAI service does not try ".../v1" after a 404  */
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        config = art.moodframe.config.ConfigMocks.withEmptyTexts();
        when(config.accounts().getApiKey(ApiProvider.GOOGLE_GEMINI)).thenReturn("key");
        when(config.accounts().getApiKey(ApiProvider.OPENAI)).thenReturn("key");
        when(config.getOpenAiBaseUrl()).thenReturn(baseUrl);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private GeminiApiServiceImpl gemini() {
        return new GeminiApiServiceImpl(config) {
            @Override
            public String getApiBaseUrl() {
                return baseUrl;
            }
        };
    }

    /*  an answer of generateContent with a text, and with one source of the Google search if the model searched  */
    private static String geminiAnswer(String text, boolean searched) {
        JSONObject candidate = new JSONObject().put("content", new JSONObject()
                .put("parts", new JSONArray().put(new JSONObject().put("text", text))));
        if (searched)
            candidate.put("groundingMetadata", new JSONObject().put("groundingChunks", new JSONArray()
                    .put(new JSONObject().put("web", new JSONObject()
                            .put("uri", "https://vertexaisearch.cloud.google.com/grounding-api-redirect/A1")
                            .put("title", "timeanddate.com")))));
        return new JSONObject().put("candidates", new JSONArray().put(candidate)).toString();
    }

    @Test
    void geminiAsksForTheCurrentTimeAndAsksAgainOnceIfTheModelDidNotSearch() {
        answers.add(new Answer(200, geminiAnswer("14:32", false)));
        answers.add(new Answer(200, geminiAnswer("14:32", true)));

        assertNull(gemini().checkWebSearch().join());
        assertEquals(2, requests.size());
        assertTrue(requests.get(0).contains("what time is it right now in Berlin"), requests.get(0));
        assertTrue(requests.get(1).contains("Use the Google Search tool now"), requests.get(1));
    }

    @Test
    void geminiCountsATimeWithoutSourcesAsNoSearchAfterExactlyOneRetry() {
        answers.add(new Answer(200, geminiAnswer("14:32", false)));
        answers.add(new Answer(200, geminiAnswer("14:32", false)));

        assertEquals("The model did not search the web.", gemini().checkWebSearch().join());
        assertEquals(2, requests.size());
    }

    @Test
    void openAiAsksForTheCurrentTimeWithTheBuiltInWebSearch() {
        answers.add(new Answer(200, new JSONObject().put("output", new JSONArray()
                .put(new JSONObject().put("type", "web_search_call").put("status", "completed"))).toString()));

        assertNull(new OpenAiApiServiceImpl(config).checkWebSearch("m").join());
        JSONObject request = new JSONObject(requests.get(0));
        assertTrue(request.getString("input").contains("what time is it right now in Berlin"), request.toString());
        assertEquals("web_search", request.getJSONArray("tools").getJSONObject(0).getString("type"));
    }

    @Test
    void aTransientErrorDecidesNothingWhileADefiniteOneNamesTheReason() {
        answers.add(new Answer(503, "{}"));
        assertThrows(CompletionException.class, () -> gemini().checkWebSearch().join());
        answers.add(new Answer(429, "{}"));
        assertThrows(CompletionException.class, () -> gemini().checkChatModel().join());
        answers.add(new Answer(404, "{}"));
        assertNotNull(gemini().checkChatModel().join());

        OpenAiApiServiceImpl openAi = new OpenAiApiServiceImpl(config);
        answers.add(new Answer(429, "{}"));
        assertThrows(CompletionException.class, () -> openAi.checkWebSearch("m").join());
        answers.add(new Answer(503, "{}"));
        assertThrows(CompletionException.class, () -> openAi.checkChatModel("m").join());
        answers.add(new Answer(404, "{}"));
        assertEquals("OpenAI error 404", openAi.checkWebSearch("m").join());
        answers.add(new Answer(401, "{}"));
        assertEquals("OpenAI: invalid bearer token (401)", openAi.checkChatModel("m").join());
    }

    @Test
    void aTransientConnectionTestDecidesNothingWhileADefiniteOneRefuses() {
        answers.add(new Answer(503, "{}"));
        assertThrows(CompletionException.class, () -> gemini().testConnection().join());
        answers.add(new Answer(401, "{}"));
        assertEquals(false, gemini().testConnection().join());

        OpenAiApiServiceImpl openAi = new OpenAiApiServiceImpl(config);
        answers.add(new Answer(429, "{}"));
        assertThrows(CompletionException.class, () -> openAi.testConnection().join());
        answers.add(new Answer(401, "{}"));
        assertEquals(false, openAi.testConnection().join());
    }

    @Test
    void aTransientBflConnectionTestDecidesNothingWhileADefiniteOneRefuses() throws Exception {
        when(config.getApiTimeoutSeconds()).thenReturn(5);
        BflApiServiceImpl bfl = new BflApiServiceImpl(config) {
            @Override
            public String getApiBaseUrl() {
                return baseUrl;
            }

            @Override
            public String getApiKey() {
                return "key";
            }
        };
        answers.add(new Answer(503, "{}"));
        assertThrows(CompletionException.class, () -> bfl.testConnection().join());
        answers.add(new Answer(401, "{}"));
        assertEquals(false, bfl.testConnection().join());
    }
}
