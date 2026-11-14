package art.moodframe.mcp;

import art.moodframe.config.ApplicationConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The server follows the settings: a port in use becomes the status without blocking, a new token or port starts it
 * anew, disabling and closing the app stop it.
 */
class McpServiceTest {

    private final ApplicationConfig config = mock(ApplicationConfig.class);
    private final McpService service = new McpService(config, new McpTools());

    @AfterEach
    void stop() {
        service.stop();
    }

    private static int freePort() throws Exception {
        try (ServerSocket free = new ServerSocket(0)) {
            return free.getLocalPort();
        }
    }

    private static int ping(int port, String token) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
                .build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    void reportsAPortInUseAndStartsOnceItIsFree() throws Exception {
        int port = freePort();
        when(config.isMcpEnabled()).thenReturn(true);
        when(config.getMcpPort()).thenReturn(port);
        when(config.getMcpToken()).thenReturn("token-1");

        try (ServerSocket other = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))) {
            assertEquals(new McpService.Status(McpService.State.PORT_IN_USE, port), service.applyAsync().get());
        }
        assertEquals(new McpService.Status(McpService.State.RUNNING, port), service.apply());
        assertEquals(200, ping(port, "token-1"));
    }

    @Test
    void startsAnewOnANewTokenOrPortAndStopsWhenDisabled() throws Exception {
        int port = freePort();
        when(config.isMcpEnabled()).thenReturn(true);
        when(config.getMcpPort()).thenReturn(port);
        when(config.getMcpToken()).thenReturn("token-1");
        assertEquals(McpService.State.RUNNING, service.apply().state());

        /*  The same port at once, with the new token only  */
        when(config.getMcpToken()).thenReturn("token-2");
        assertEquals(McpService.State.RUNNING, service.apply().state());
        assertEquals(401, ping(port, "token-1"));
        assertEquals(200, ping(port, "token-2"));

        int otherPort = freePort();
        when(config.getMcpPort()).thenReturn(otherPort);
        assertEquals(new McpService.Status(McpService.State.RUNNING, otherPort), service.apply());
        assertEquals(200, ping(otherPort, "token-2"));

        when(config.isMcpEnabled()).thenReturn(false);
        assertEquals(McpService.State.OFF, service.apply().state());
        assertThrows(IOException.class, () -> ping(otherPort, "token-2"), "nobody listens anymore");
    }

    @Test
    void createsATokenOnTheFirstStart() throws Exception {
        when(config.isMcpEnabled()).thenReturn(true);
        when(config.getMcpPort()).thenReturn(freePort());
        assertEquals(McpService.State.RUNNING, service.apply().state());
        verify(config).setMcpToken(anyString());
        assertNotEquals(McpService.newToken(), McpService.newToken());
        assertEquals(43, McpService.newToken().length(), "32 random bytes");
    }

    @Test
    void theCommandAddsTheServerToClaudeCode() {
        assertEquals("claude mcp add --transport http moodframe http://127.0.0.1:8765/mcp --header "
                + "\"Authorization: Bearer abc\"", McpService.command(8765, "abc"));
    }
}
