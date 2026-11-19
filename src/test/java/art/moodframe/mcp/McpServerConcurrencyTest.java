package art.moodframe.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Calls that wait long (as {@code generate} waits for its images) do not starve the server: with more of them running
 * than a small pool has threads, {@code list_chats} and {@code cancel_generation} still answer at once.
 */
class McpServerConcurrencyTest {

    private static final String TOKEN = "concurrency-token";
    private static final int WAITING_CALLS = 8;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newHttpClient();
    private McpServer server;
    private int port;

    @AfterEach
    void stopTheServer() {
        if (server != null)
            server.stop();
    }

    @Test
    void waitingGenerationsDoNotBlockOtherCalls() throws Exception {
        CountDownLatch cancelled = new CountDownLatch(1);
        AtomicInteger waiting = new AtomicInteger();
        AtomicInteger onVirtualThreads = new AtomicInteger();
        McpTools tools = new McpTools();
        tools.add("generate", "waits until cancelled", JSON.createObjectNode(), (JsonNode arguments) -> {
            waiting.incrementAndGet();
            if (Thread.currentThread().isVirtual())
                onVirtualThreads.incrementAndGet();
            try {
                assertTrue(cancelled.await(30, TimeUnit.SECONDS));
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new McpTools.Result(JSON.createObjectNode().put("status", "CANCELLED"));
        });
        tools.add("list_chats", "answers at once", JSON.createObjectNode(),
                (JsonNode arguments) -> new McpTools.Result(JSON.createObjectNode().put("chats", 0)));
        tools.add("cancel_generation", "ends the waiting calls", JSON.createObjectNode(), (JsonNode arguments) -> {
            cancelled.countDown();
            return new McpTools.Result(JSON.createObjectNode().put("status", "CANCELLED"));
        });
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }
        server = new McpServer(port, TOKEN, tools);
        server.start();

        List<CompletableFuture<HttpResponse<String>>> generations = new ArrayList<>();
        for (int i = 0; i < WAITING_CALLS; i++)
            generations.add(http.sendAsync(request("generate"), HttpResponse.BodyHandlers.ofString()));
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (waiting.get() < WAITING_CALLS && System.nanoTime() < end)
            Thread.sleep(10);
        assertEquals(WAITING_CALLS, waiting.get(), "every generation runs at the same time");

        HttpResponse<String> listed = http.sendAsync(request("list_chats"), HttpResponse.BodyHandlers.ofString())
                .get(5, TimeUnit.SECONDS);
        assertEquals(0, JSON.readTree(listed.body()).path("result").path("structuredContent").path("chats").asInt(-1));
        assertFalse(generations.stream().anyMatch(CompletableFuture::isDone), "the generations still wait");

        http.sendAsync(request("cancel_generation"), HttpResponse.BodyHandlers.ofString()).get(5, TimeUnit.SECONDS);
        for (CompletableFuture<HttpResponse<String>> generation : generations)
            assertEquals(200, generation.get(10, TimeUnit.SECONDS).statusCode());
        assertEquals(WAITING_CALLS, onVirtualThreads.get(), "each request runs on a virtual thread of its own");
    }

    private HttpRequest request(String tool) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
                .header("Authorization", "Bearer " + TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"" + tool + "\",\"arguments\":{}}}"))
                .build();
    }
}
