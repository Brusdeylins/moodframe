package art.moodframe.service.provider;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.service.provider.gemini.GeminiApiServiceImpl;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * A video model is generated through {@link ImageProvider#submitGenerationRequest}, like an image model: the
 * generation needs no client interface of a provider by name.
 */
class VideoThroughSpiTest {

    private final List<String> paths = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private ApplicationConfig config;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", (exchange) -> {
            paths.add(exchange.getRequestURI().getPath());
            exchange.getRequestBody().readAllBytes();
            byte[] answer = "{\"error\":{\"code\":400,\"message\":\"simulated\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
        config = art.moodframe.config.ConfigMocks.withEmptyTexts();
        when(config.accounts().getApiKey(ApiProvider.GOOGLE_GEMINI)).thenReturn("test-key");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private ImageProvider gemini() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ImageProvider gemini = new GeminiApiServiceImpl(config) {
            @Override
            public String getApiBaseUrl() {
                return baseUrl;
            }
        };
        return new ProviderRegistry(gemini).forModel(GenerationModel.OMNI_720P.getApiModelId());
    }

    @Test
    void aVideoModelGoesToTheVideoApiOfItsProvider() throws Exception {
        ImageGenerationRequest request = new ImageGenerationRequest("a harbor at dawn",
                GenerationModel.OMNI_720P.getApiModelId());
        request.setVideoDuration(5);

        GenerationJobInfo job = gemini().submitGenerationRequest(request).get(10, TimeUnit.SECONDS);

        assertEquals(List.of("/interactions"), paths);
        assertTrue(job.isFailed());
        assertTrue(job.isVideoGeneration());
    }

    @Test
    void anImageModelOfTheSameProviderGoesToTheImageApi() {
        ImageGenerationRequest request = new ImageGenerationRequest("a harbor at dawn",
                GenerationModel.NANO_BANANA2_1K.getApiModelId());
        request.setImageSize("1K");
        request.setAspectRatio("1:1");

        gemini().submitGenerationRequest(request).handle((job, failure) -> null).join();

        assertEquals(1, paths.size());
        assertTrue(paths.get(0).contains("generateContent"), paths.get(0));
    }
}
