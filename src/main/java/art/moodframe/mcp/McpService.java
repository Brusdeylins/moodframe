package art.moodframe.mcp;

import art.moodframe.config.ApplicationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.BindException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs the MCP server as the settings say: it starts when enabled, starts anew on a changed port or token and stops
 * when disabled or when the app closes. A port in use does not block the app, it becomes the status and is logged.
 *
 * @since 2.1.0
 */
public class McpService {

    private static final Logger logger = LoggerFactory.getLogger(McpService.class);

    /** What the server does. */
    public enum State {
        /** Disabled in the settings. */
        OFF,
        /** Answers on the port. */
        RUNNING,
        /** Enabled, but another program holds the port. */
        PORT_IN_USE,
        /** Enabled, but it could not be started otherwise. */
        FAILED
    }

    /**
     * The state of the server and its port.
     *
     * @param state the state
     * @param port  the port of the settings, 0 while off
     */
    public record Status(State state, int port) {
    }

    private final ApplicationConfig config;
    private final McpTools tools;
    private final ExecutorService worker = Executors.newSingleThreadExecutor((Runnable runnable) -> {
        Thread thread = new Thread(runnable, "mcp-lifecycle");
        thread.setDaemon(true);
        return thread;
    });
    private McpServer server;
    private String serverToken;
    private volatile Status status = new Status(State.OFF, 0);

    /**
     * Creates the service; the server does not run until {@link #apply()}.
     *
     * @param config the settings {@code mcp.enabled}, {@code mcp.port} and the token
     * @param tools  the tools the server offers
     */
    public McpService(ApplicationConfig config, McpTools tools) {
        this.config = config;
        this.tools = tools;
    }

    /**
     * Tells what the server does.
     *
     * @return the status after the last {@link #apply()}
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Brings the server in line with the settings: starts it, starts it anew on a changed port or token, or stops it.
     * An enabled server without token gets a new one first.
     *
     * @return the status afterwards
     */
    public synchronized Status apply() {
        boolean enabled = config.isMcpEnabled();
        int port = config.getMcpPort();
        String token = config.getMcpToken();
        if (enabled && token == null) {
            token = newToken();
            config.setMcpToken(token);
        }

        if (server != null && (!enabled || server.getPort() != port || !token.equals(serverToken))) {
            server.stop();
            server = null;
            logger.info("MCP server stopped");
        }
        if (!enabled)
            return status = new Status(State.OFF, 0);
        if (server != null)
            return status;

        McpServer candidate = new McpServer(port, token, tools);
        try {
            candidate.start();
            server = candidate;
            serverToken = token;
            status = new Status(State.RUNNING, port);
            logger.info("MCP server running on http://127.0.0.1:{}{}", port, McpServer.PATH);
        }
        catch (BindException e) {
            status = new Status(State.PORT_IN_USE, port);
            logger.warn("MCP server not started: port {} is in use", port);
        }
        catch (IOException e) {
            status = new Status(State.FAILED, port);
            logger.error("MCP server not started on port {}", port, e);
        }
        return status;
    }

    /**
     * Applies the settings on a thread of its own, never on the JavaFX thread; calls are carried out in order.
     *
     * @return completes with the status afterwards
     */
    public CompletableFuture<Status> applyAsync() {
        return CompletableFuture.supplyAsync(this::apply, worker);
    }

    /**
     * Stops the server, when the app closes.
     */
    public synchronized void stop() {
        if (server != null) {
            server.stop();
            server = null;
            logger.info("MCP server stopped");
        }
        status = new Status(State.OFF, 0);
        worker.shutdownNow();
    }

    /**
     * Creates a random token: 32 bytes, Base64 for URLs without padding.
     *
     * @return the token
     */
    public static String newToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * The command that adds the server to Claude Code.
     *
     * @param port  the port
     * @param token the token
     * @return the command line
     */
    public static String command(int port, String token) {
        return "claude mcp add --transport http moodframe http://127.0.0.1:" + port + McpServer.PATH
                + " --header \"Authorization: Bearer " + token + "\"";
    }
}
