package art.moodframe.service.provider;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * The failures of the BFL client keep their type through the futures: a poll answered with HTTP 404 (normal until
 * the job is registered) arrives as the {@link BflApiException} with its status, logged at DEBUG only.
 */
class BflPollingErrorsTest {

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
            exchange.getRequestBody().readAllBytes();
            byte[] answer = "{\"detail\":\"Not found\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        logs.start();
        appLogger.addAppender(logs);
        appLogger.setLevel(Level.TRACE);
        appLogger.setAdditive(false);

        config = art.moodframe.config.ConfigMocks.withEmptyTexts();
        when(config.accounts().getApiKey(ApiProvider.BFL)).thenReturn("test-key");
        when(config.getApiTimeoutSeconds()).thenReturn(30);
    }

    @AfterEach
    void tearDown() {
        appLogger.setAdditive(true);
        appLogger.setLevel(appLevel);
        appLogger.detachAppender(logs);
        server.stop(0);
    }

    @Test
    void aPollAnsweredWith404ArrivesAsTheTypedFailureAndLogsAtDebugOnly() {
        BflApiServiceImpl service = new BflApiServiceImpl(config);

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> service.getJobResultWithPollingUrl(baseUrl + "/v1/get_result?id=job").get(10,
                        TimeUnit.SECONDS));
        BflApiException cause = assertInstanceOf(BflApiException.class, ProviderException.unwrap(failure));
        assertEquals(404, cause.getStatusCode());

        List<ILoggingEvent> loud = logs.list.stream()
                .filter((ILoggingEvent event) -> event.getLevel().isGreaterOrEqual(Level.WARN)).toList();
        assertTrue(loud.isEmpty(), () -> loud.get(0).getFormattedMessage());
    }

    @Test
    void aMissingKeyIsALocalizedProviderFailure() {
        when(config.accounts().getApiKey(ApiProvider.BFL)).thenReturn(null);
        BflApiServiceImpl service = new BflApiServiceImpl(config);

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> service.testConnection().get(10, TimeUnit.SECONDS));
        ProviderException cause = assertInstanceOf(ProviderException.class, ProviderException.unwrap(failure));
        assertEquals("BFL: no API key configured. Please enter it in the settings.",
                cause.getMessage(Locale.ENGLISH));
        assertEquals("BFL: Kein API-Schlüssel konfiguriert. Bitte in den Einstellungen eintragen.",
                cause.getMessage(Locale.GERMAN));
    }

    @Test
    void aFailedSslTestIsATypedFailureThatNamesSslInEveryLanguage() throws Exception {
        /*  a server that answers the handshake with plain text  */
        java.net.ServerSocket plain = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
        Thread.ofVirtual().start(() -> {
            try (plain; java.net.Socket socket = plain.accept()) {
                socket.getOutputStream().write("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
            } catch (java.io.IOException ignored) {
            }
        });
        String plainServer = "https://127.0.0.1:" + plain.getLocalPort();
        BflApiServiceImpl service = new BflApiServiceImpl(config) {
            @Override
            public String getApiBaseUrl() {
                return plainServer;
            }
        };

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> service.testSslConnection().get(20, TimeUnit.SECONDS));
        ProviderException cause = assertInstanceOf(ProviderException.class, ProviderException.unwrap(failure));
        assertInstanceOf(javax.net.ssl.SSLException.class, cause.getCause());

        /*  the settings dialog tells an SSL failure by its text  */
        assertTrue(cause.getMessage(Locale.ENGLISH).contains("SSL"), cause.getMessage(Locale.ENGLISH));
        assertTrue(cause.getMessage(Locale.GERMAN).contains("SSL"), cause.getMessage(Locale.GERMAN));
        assertTrue(failure.getMessage().contains("SSL"), failure.getMessage());
    }
}
