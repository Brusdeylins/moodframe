package art.moodframe.mcp;

import art.moodframe.util.VersionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The MCP server of MoodFrame: Streamable HTTP on {@code http://127.0.0.1:<port>/mcp}, stateless, JSON-RPC 2.0 by
 * POST only. It answers {@code initialize}, {@code ping}, {@code tools/list} and {@code tools/call} and takes the
 * notifications of the client; GET and every other method get 405, as the server sends no events.
 *
 * <p>An own implementation on {@code jdk.httpserver} and the Jackson 2 of the app: the MCP Java SDK 2.2.0 serves
 * HTTP through a servlet container only, needs Reactor and Jackson 3 and comes as automatic modules only.</p>
 *
 * <p>Every request needs {@code Authorization: Bearer <token>}, else 401. A {@code Host} other than 127.0.0.1 or
 * localhost with the port and an {@code Origin} of another host get 403 (protection against DNS rebinding). The
 * token is never logged. The tools run on the threads of the server, never on the JavaFX thread.</p>
 *
 * @since 2.1.0
 */
public class McpServer {

    private static final Logger logger = LoggerFactory.getLogger(McpServer.class);

    /** The protocol versions the server speaks, the latest first. */
    public static final List<String> PROTOCOL_VERSIONS = List.of("2025-11-25", "2025-06-18", "2025-03-26");

    /** The path of the endpoint. */
    public static final String PATH = "/mcp";

    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final byte[] LOOPBACK = { 127, 0, 0, 1 };

    private final int port;
    private final byte[] token;
    private final McpTools tools;
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private ExecutorService executor;

    /**
     * Creates the server; it does not run until {@link #start()}.
     *
     * @param port  the port on 127.0.0.1
     * @param token the bearer token every request has to carry
     * @param tools the tools the server offers
     */
    public McpServer(int port, String token, McpTools tools) {
        this.port = port;
        this.token = token.getBytes(StandardCharsets.UTF_8);
        this.tools = tools;
    }

    /**
     * Binds to 127.0.0.1 and starts answering.
     *
     * @throws java.net.BindException if the port is in use
     * @throws IOException            if the server cannot be started otherwise
     */
    public synchronized void start() throws IOException {
        HttpServer created = HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(LOOPBACK), port), 0);
        AtomicInteger count = new AtomicInteger();
        ExecutorService threads = Executors.newFixedThreadPool(4, (Runnable runnable) -> {
            Thread thread = new Thread(runnable, "mcp-server-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        created.createContext("/", this::handle);
        created.setExecutor(threads);
        created.start();
        server = created;
        executor = threads;
    }

    /**
     * Stops the server at once; running tool calls are cut off.
     */
    public synchronized void stop() {
        if (server == null)
            return;
        server.stop(0);
        executor.shutdownNow();
        server = null;
        executor = null;
    }

    /**
     * Tells the port the server listens on.
     *
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /*  One HTTP request: the checks of the transport, then one JSON-RPC message  */
    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!hostAllowed(exchange.getRequestHeaders().getFirst("Host"))
                    || !originAllowed(exchange.getRequestHeaders().getFirst("Origin"))) {
                send(exchange, 403, null);
                return;
            }
            if (!PATH.equals(exchange.getRequestURI().getPath())) {
                send(exchange, 404, null);
                return;
            }
            if (!authorized(exchange.getRequestHeaders().getFirst("Authorization"))) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                send(exchange, 401, null);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST");
                send(exchange, 405, null);
                return;
            }

            byte[] body = read(exchange.getRequestBody());
            if (body == null) {
                send(exchange, 413, null);
                return;
            }
            JsonNode message;
            try {
                message = json.readTree(body);
            }
            catch (IOException e) {
                send(exchange, 400, error(null, -32700, "Parse error"));
                return;
            }
            if (message == null || !message.isObject()) {
                send(exchange, 400, error(null, -32600, "Invalid request: one JSON-RPC message per request"));
                return;
            }

            /*  Notifications and responses of the client are taken without an answer  */
            JsonNode id = message.get("id");
            String method = message.path("method").asText(null);
            if (method == null || id == null || id.isNull()) {
                send(exchange, 202, null);
                return;
            }

            /*  After the initialization the client names the negotiated version  */
            String version = exchange.getRequestHeaders().getFirst("MCP-Protocol-Version");
            if (!"initialize".equals(method) && version != null && !PROTOCOL_VERSIONS.contains(version)) {
                send(exchange, 400, error(id, -32600, "Unsupported protocol version: " + version));
                return;
            }
            send(exchange, 200, answer(id, method, message.path("params")));
        }
        catch (Exception e) {
            logger.error("MCP request failed", e);
            if (exchange.getResponseCode() == -1)
                send(exchange, 500, null);
        }
        finally {
            exchange.close();
        }
    }

    /*  The response to a request of the client  */
    private ObjectNode answer(JsonNode id, String method, JsonNode params) {
        switch (method) {
            case "initialize" -> {
                String requested = params.path("protocolVersion").asText("");
                ObjectNode result = json.createObjectNode();
                result.put("protocolVersion", PROTOCOL_VERSIONS.contains(requested) ? requested
                        : PROTOCOL_VERSIONS.get(0));
                result.putObject("capabilities").putObject("tools").put("listChanged", false);
                result.putObject("serverInfo").put("name", "moodframe").put("title", "MoodFrame")
                        .put("version", VersionManager.getVersion());
                result.put("instructions", "Reads the library of MoodFrame: chats with their entries and images, "
                        + "moods, building blocks, frames, the trash and the models to generate with.");
                return success(id, result);
            }
            case "ping" -> {
                return success(id, json.createObjectNode());
            }
            case "tools/list" -> {
                ObjectNode result = json.createObjectNode();
                result.set("tools", tools.list());
                return success(id, result);
            }
            case "tools/call" -> {
                String name = params.path("name").asText("");
                if (!tools.has(name))
                    return error(id, -32602, "Unknown tool: " + name);
                logger.debug("MCP tool call: {}", name);
                return success(id, tools.call(name, params.path("arguments")));
            }
            default -> {
                return error(id, -32601, "Method not found: " + method);
            }
        }
    }

    private ObjectNode success(JsonNode id, ObjectNode result) {
        ObjectNode response = json.createObjectNode().put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        return response;
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = json.createObjectNode().put("jsonrpc", "2.0");
        response.set("id", id);
        response.putObject("error").put("code", code).put("message", message);
        return response;
    }

    /*  127.0.0.1 or localhost with the port of the server  */
    private boolean hostAllowed(String host) {
        return host != null && (host.equalsIgnoreCase("127.0.0.1:" + port)
                || host.equalsIgnoreCase("localhost:" + port));
    }

    /*  no Origin (a client outside a browser), or one of localhost  */
    private static boolean originAllowed(String origin) {
        if (origin == null)
            return true;
        try {
            String host = URI.create(origin).getHost();
            return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "[::1]".equals(host);
        }
        catch (IllegalArgumentException e) {
            return false;
        }
    }

    /*  compares in constant time, so the answer time tells nothing about the token  */
    private boolean authorized(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7))
            return false;
        byte[] given = authorization.substring(7).trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(given, token);
    }

    /*  the body of the request, null if it exceeds the limit  */
    private static byte[] read(InputStream in) throws IOException {
        byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
        return body.length > MAX_BODY_BYTES ? null : body;
    }

    private void send(HttpExchange exchange, int status, ObjectNode response) throws IOException {
        if (response == null) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        byte[] body = json.writeValueAsBytes(response);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
