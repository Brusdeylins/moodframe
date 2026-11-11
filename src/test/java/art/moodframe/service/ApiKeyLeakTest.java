package art.moodframe.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.model.ImageGenerationRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sends the requests of every provider to a local server and checks that the API key travels as a header only:
 * it shows up in no request URL and in no log output.
 */
class ApiKeyLeakTest {

    private static final String KEY = "test-key-4f9c2e7a1b";

    private record Call(String url, Headers headers) {
    }

    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger appLogger = (Logger) LoggerFactory.getLogger("art.moodframe");
    private final Level appLevel = appLogger.getLevel();
    private HttpServer server;
    private String baseUrl;
    private ApplicationConfig config;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", (exchange) -> {
            calls.add(new Call(exchange.getRequestURI().toString(), exchange.getRequestHeaders()));
            exchange.getRequestBody().readAllBytes();
            byte[] answer = "{\"error\":{\"code\":400,\"message\":\"simulated\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        /*  Every level is captured, and nothing of it reaches the log files of the user  */
        logs.start();
        appLogger.addAppender(logs);
        appLogger.setLevel(Level.TRACE);
        appLogger.setAdditive(false);

        config = mock(ApplicationConfig.class, (invocation) ->
                invocation.getMethod().getReturnType() == String.class ? "" : org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation));
    }

    @AfterEach
    void tearDown() {
        appLogger.setAdditive(true);
        appLogger.setLevel(appLevel);
        appLogger.detachAppender(logs);
        server.stop(0);
    }

    /** Waits for the end of a call, which is a failure: the server answers every request with an error. */
    private static void await(CompletableFuture<?> call) throws Exception {
        call.handle((result, error) -> null).get(10, TimeUnit.SECONDS);
    }

    private void assertKeyInHeaderOnly(int requests, String header, String value) {
        assertEquals(requests, calls.size());
        for (Call call : calls) {
            assertFalse(call.url().contains(KEY), call.url());
            assertEquals(value, call.headers().getFirst(header));
        }

        assertFalse(logs.list.isEmpty());
        for (ILoggingEvent event : List.copyOf(logs.list)) {
            String output = event.getFormattedMessage() + ThrowableProxyUtil.asString(event.getThrowableProxy());
            assertFalse(output.contains(KEY), output);
        }
    }

    @Test
    void geminiSendsTheKeyAsHeaderOnly() throws Exception {
        when(config.getGeminiApiKey()).thenReturn(KEY);
        GeminiApiServiceImpl service = new GeminiApiServiceImpl(config) {
            @Override
            String getApiBaseUrl() {
                return baseUrl;
            }
        };
        ImageGenerationRequest request = new ImageGenerationRequest("an apple", "gemini-3.1-flash-image");
        request.setImageSize("1K");
        request.setAspectRatio("1:1");

        await(service.testConnection());
        await(service.submitGenerationRequest(request));
        await(service.analyzeYouTubeVideo("https://www.youtube.com/watch?v=jNQXAC9IVRw", 0, 0, "What is shown?"));

        assertKeyInHeaderOnly(3, "x-goog-api-key", KEY);
    }

    @Test
    void bflSendsTheKeyAsHeaderOnly() throws Exception {
        when(config.getApiKey()).thenReturn(KEY);
        when(config.getApiTimeoutSeconds()).thenReturn(30);
        when(config.getLoggingEnabled()).thenReturn(true);
        ServiceFactory.getInstance().registerService(ApplicationConfig.class, config);
        BflApiServiceImpl service = new BflApiServiceImpl() {
            @Override
            public String getApiBaseUrl() {
                return baseUrl;
            }
        };
        ImageGenerationRequest request = new ImageGenerationRequest("an apple", "flux.2-pro");
        request.setWidth(1024);
        request.setHeight(1024);

        await(service.submitGenerationRequest(request));

        assertKeyInHeaderOnly(1, "X-Key", KEY);
    }

    @Test
    void openAiSendsTheKeyAsHeaderOnly() throws Exception {
        when(config.getOpenAiApiKey()).thenReturn(KEY);
        when(config.getOpenAiBaseUrl()).thenReturn(baseUrl + "/v1");
        OpenAiApiServiceImpl service = new OpenAiApiServiceImpl(config);

        await(service.submitGenerationRequest(new ImageGenerationRequest("an apple", "gpt-image-1")));

        assertKeyInHeaderOnly(1, "Authorization", "Bearer " + KEY);
    }
}
