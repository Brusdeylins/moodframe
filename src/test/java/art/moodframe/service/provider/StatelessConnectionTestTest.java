package art.moodframe.service.provider;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ConfigMocks;
import art.moodframe.config.ProviderAccounts;
import art.moodframe.model.ApiProvider;
import art.moodframe.service.provider.bfl.BflApiServiceImpl;
import art.moodframe.service.provider.gemini.GeminiApiServiceImpl;
import art.moodframe.service.provider.openai.OpenAiApiService;
import art.moodframe.service.provider.openai.OpenAiApiServiceImpl;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A key typed in the settings is tested with a copy of the client: the live client keeps the saved key, region and
 * base URL, and the configuration is not written.
 */
class StatelessConnectionTestTest {

    private final List<String> keys = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private String baseUrl;
    private ApplicationConfig config;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", (exchange) -> {
            String key = exchange.getRequestHeaders().getFirst("x-goog-api-key");
            keys.add(key != null ? key : String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            byte[] body = "{\"data\": [{\"id\": \"gpt-image-1\"}, {\"id\": \"gpt-5\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        config = ConfigMocks.withEmptyTexts();
        ProviderAccounts accounts = config.accounts();
        when(accounts.getApiKey(ApiProvider.GOOGLE_GEMINI)).thenReturn("saved");
        when(accounts.getApiKey(ApiProvider.OPENAI)).thenReturn("saved");
        when(accounts.getOpenAiBaseUrl()).thenReturn("https://saved.example/v1");
        when(accounts.getApiKey(ApiProvider.BFL)).thenReturn("saved");
        when(accounts.getApiRegion()).thenReturn("Global");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void geminiTestsTheTypedKeyAndKeepsTheSavedOne() {
        GeminiApiServiceImpl gemini = new GeminiApiServiceImpl(config) {
            @Override
            public String getApiBaseUrl() {
                return baseUrl;
            }
        };

        assertTrue(gemini.testConnection("typed", null).join());
        assertEquals(List.of("typed"), keys);
        assertEquals("saved", gemini.getApiKey());

        /*  A key saved later is sent at once, without a test setting it  */
        when(config.accounts().getApiKey(ApiProvider.GOOGLE_GEMINI)).thenReturn("new");
        assertEquals("new", gemini.getApiKey());
    }

    @Test
    void openAiFindsTheModelsOfTheTypedTokenWithoutStoringAnything() {
        OpenAiApiServiceImpl openAi = new OpenAiApiServiceImpl(config);

        OpenAiApiService.Account account = openAi.testAccount("typed", baseUrl).join();
        assertTrue(account.works());
        assertEquals(baseUrl, account.baseUrl());
        assertEquals(List.of("gpt-5", "gpt-image-1"), account.models());
        assertEquals(List.of("gpt-image-1"), account.selected());
        assertEquals(List.of("Bearer typed"), keys);

        assertEquals("saved", openAi.getApiKey());
        assertEquals("https://saved.example/v1", openAi.getBaseUrl());
        verify(config.accounts(), never()).setOpenAiAvailableModels(anyList());
        verify(config.accounts(), never()).setOpenAiModels(anyList());
        verify(config.accounts(), never()).setOpenAiBaseUrl(any());
    }

    @Test
    void bflTestsTheTypedKeyInTheChosenRegion() throws Exception {
        BflApiServiceImpl bfl = new BflApiServiceImpl(config);
        BflApiServiceImpl typed = (BflApiServiceImpl) bfl.withCredentials("typed", "EU");

        assertEquals("typed", typed.getApiKey());
        assertEquals("https://api.eu.bfl.ai", typed.getApiBaseUrl());
        assertEquals("saved", bfl.getApiKey());
        assertEquals("https://api.bfl.ai", bfl.getApiBaseUrl());
        assertFalse(typed == bfl);
        verify(config.accounts(), never()).setApiRegion(any());
    }
}
