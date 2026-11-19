package art.moodframe.mcp;

import art.moodframe.service.chat.TestChatServices;
import art.moodframe.service.library.TestLibraryServices;
import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ConfigMocks;
import art.moodframe.model.ApiProvider;
import art.moodframe.database.DatabaseConfig;
import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryFrame;
import art.moodframe.model.EntryMood;
import art.moodframe.model.Frame;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.Mood;
import art.moodframe.repository.ChatRepository;
import art.moodframe.service.library.BlockTrashGroup;
import art.moodframe.service.chat.ChatHistoryService;
import art.moodframe.service.chat.ChatLibrary;
import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.FavoriteService;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.library.FrameTrashGroup;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.MoodTrashGroup;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.trash.TrashCenter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;

/**
 * The MCP server against a library in a throwaway {@code user.home}: the checks of the transport, the protocol and
 * every reading tool, on the JavaFX thread of the Monocle headless platform.
 */
class McpServerTest {

    private static final String TOKEN = "test-token-123";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @TempDir
    static Path tempDir;

    private static String home;
    private static int port;
    private static McpServer server;
    private static Chat chat;
    private static Path imageFile;
    private static Mood ownMood;
    private static Frame trashedFrame;
    private static final List<String> reported = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void startServerOnALibrary() throws Exception {
        home = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        }
        catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await(10, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);

        Path database = tempDir.resolve("db").resolve("test.db");
        DatabaseConfig.initialize(database.toString());
        LibraryEvents events = new LibraryEvents();
        events.addListener((LibraryEvents.Kind kind, String id) -> reported.add(kind + ":" + id));
        ChatLibrary chats = new ChatLibrary(TestChatServices.history(new DatabaseManager(database)), events);
        PromptBlockService blocks = TestLibraryServices.blocks(events);
        ColorStyleService colors = TestLibraryServices.palettes(events);
        MoodService moods = TestLibraryServices.moods(blocks, colors, events);
        FrameService frames = TestLibraryServices.frames(events);
        FavoriteService favorites = TestLibraryServices.favorites(events);
        TrashCenter trash = new TrashCenter();
        trash.register(new BlockTrashGroup(blocks, colors, moods));
        trash.register(new MoodTrashGroup(moods));
        trash.register(new FrameTrashGroup(frames));

        /*  Only BFL is enabled with a key  */
        ApplicationConfig config = ConfigMocks.config();
        when(config.paths()).thenReturn(art.moodframe.config.LibraryPaths.under(tempDir));
        when(config.accounts().isEnabled(ApiProvider.BFL)).thenReturn(true);
        when(config.accounts().hasApiKey(ApiProvider.BFL)).thenReturn(true);
        when(config.accounts().getOpenAiModels()).thenReturn(List.of());
        when(config.generation().getFlux2SafetyTolerance()).thenReturn(2);
        when(config.generation().getFlux2FlexGuidance()).thenReturn(5.0);
        when(config.generation().getFlux2FlexSteps()).thenReturn(50);

        /*  A chat with one entry and its image of 2000 x 1000 pixels  */
        imageFile = tempDir.resolve("image-1.png");
        ImageIO.write(new BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB), "png", imageFile.toFile());
        chat = chats.createChat("Lighthouses");
        ChatEntry entry = new ChatEntry.Builder().id("entry-1").chatId(chat.getId()).userPrompt("a lighthouse")
                .enhancedPrompt("Image style: Film.\na lighthouse").status(ChatEntry.Status.COMPLETED)
                .model(GenerationModel.FLUX2_PRO.getDisplayName()).cost(0.03)
                .mood(new EntryMood("std:mood:film-noir", "Film Noir", Map.of()))
                .frame(new EntryFrame("std:frame:social-post", "Social Post", "4:5", "2K")).build();
        entry.getImages().add(new GeneratedImage.Builder().id("image-1").chatEntryId("entry-1")
                .localFilePath(imageFile.toString()).filename("image-1.png")
                .downloadStatus(GeneratedImage.DownloadStatus.CACHED).imageWidth(2000).imageHeight(1000)
                .fileSizeBytes(Files.size(imageFile)).mimeType("image/png")
                .createdAt(LocalDateTime.of(2026, 11, 16, 17, 36, 17)).build());
        chats.addEntry(chat, entry);

        /*  A chat with umlauts: an image, a video and an image in the trash  */
        Path umlautFile = tempDir.resolve("umlaut.png");
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", umlautFile.toFile());
        Path videoFile = Files.write(tempDir.resolve("clip.mp4"), new byte[] { 0, 0, 0, 0x18 });
        Chat umlautChat = chats.createChat("Größe Übung");
        ChatEntry umlautEntry = new ChatEntry.Builder().id("entry-2").chatId(umlautChat.getId()).userPrompt("a sauna")
                .status(ChatEntry.Status.COMPLETED).model(GenerationModel.FLUX2_PRO.getDisplayName()).build();
        umlautEntry.getImages().add(new GeneratedImage.Builder().id("umlaut-image").chatEntryId("entry-2")
                .localFilePath(umlautFile.toString()).downloadStatus(GeneratedImage.DownloadStatus.CACHED)
                .createdAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5)).build());
        GeneratedImage video = new GeneratedImage.Builder().id("umlaut-video").chatEntryId("entry-2")
                .localFilePath(videoFile.toString()).downloadStatus(GeneratedImage.DownloadStatus.CACHED)
                .createdAt(LocalDateTime.of(2026, 1, 2, 3, 4, 6)).build();
        video.setIsVideo(true);
        umlautEntry.getImages().add(video);
        umlautEntry.getImages().add(new GeneratedImage.Builder().id("deleted-image").chatEntryId("entry-2")
                .localFilePath(umlautFile.toString()).downloadStatus(GeneratedImage.DownloadStatus.CACHED)
                .deletedAt(LocalDateTime.now()).build());
        chats.addEntry(umlautChat, umlautEntry);

        /*  An own favorite mood and a frame in the trash  */
        ownMood = new Mood(null, "Own Mood");
        ownMood.setReference(Mood.Field.STYLE, "std:style:watercolor");
        assertTrue(moods.add(ownMood));
        favorites.toggle(FavoriteService.Kind.MOOD, ownMood.getId());
        trashedFrame = new Frame(null, "Old Frame");
        trashedFrame.setAspectRatio("3:2");
        assertTrue(frames.add(trashedFrame));
        assertTrue(frames.moveToTrash(trashedFrame.getId()));

        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }
        server = new McpServer(port, TOKEN, McpTools.reading(config, chats, moods, blocks, colors, frames, favorites,
                trash, events));
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
        System.setProperty("user.home", home);
    }

    /*  ===== HTTP and JSON-RPC =====  */

    private static HttpResponse<String> post(String body, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (headers.length > 0)
            request.headers(headers);
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode rpc(String method, String params) throws Exception {
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"" + method
                + "\",\"params\":" + params + "}", "Authorization", "Bearer " + TOKEN);
        assertEquals(200, response.statusCode());
        JsonNode message = JSON.readTree(response.body());
        assertEquals(7, message.path("id").asInt());
        return message;
    }

    /*  the result of a tool call, which must succeed  */
    private static JsonNode call(String tool, String arguments) throws Exception {
        JsonNode result = rpc("tools/call", "{\"name\":\"" + tool + "\",\"arguments\":" + arguments + "}")
                .path("result");
        assertFalse(result.path("isError").asBoolean(), () -> "tool error: " + result);
        JsonNode structured = result.path("structuredContent");
        JsonNode text = result.path("content").get(result.path("content").size() - 1);
        assertEquals(structured, JSON.readTree(text.path("text").asText()), "the text carries the same JSON");
        return structured;
    }

    /*  the message of a tool error  */
    private static String toolError(String tool, String arguments) throws Exception {
        JsonNode result = rpc("tools/call", "{\"name\":\"" + tool + "\",\"arguments\":" + arguments + "}")
                .path("result");
        assertTrue(result.path("isError").asBoolean(), () -> "no tool error: " + result);
        return result.path("content").get(0).path("text").asText();
    }

    /*  a raw request, as HttpClient sets the Host header itself  */
    private static String rawStatusLine(String host) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream out = socket.getOutputStream();
            String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
            out.write(("POST /mcp HTTP/1.1\r\nHost: " + host + "\r\nAuthorization: Bearer " + TOKEN
                    + "\r\nContent-Type: application/json\r\nContent-Length: " + body.length()
                    + "\r\nConnection: close\r\n\r\n" + body).getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            return new String(in.readAllBytes(), StandardCharsets.US_ASCII).lines().findFirst().orElse("");
        }
    }

    private static List<String> texts(JsonNode array, String field) {
        return StreamSupport.stream(array.spliterator(), false).map((JsonNode node) -> node.path(field).asText())
                .toList();
    }

    private static JsonNode find(JsonNode array, String field, String value) {
        return StreamSupport.stream(array.spliterator(), false)
                .filter((JsonNode node) -> value.equals(node.path(field).asText())).findFirst()
                .orElseThrow(() -> new AssertionError("no " + field + " " + value + " in " + array));
    }

    @Test
    void refusesARequestWithoutOrWithAWrongToken() throws Exception {
        String ping = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        assertEquals(401, post(ping).statusCode());
        assertEquals(401, post(ping, "Authorization", "Bearer wrong").statusCode());
        assertEquals(401, post(ping, "Authorization", "Basic " + TOKEN).statusCode());
        assertEquals(200, post(ping, "Authorization", "Bearer " + TOKEN).statusCode());
    }

    @Test
    void refusesAForeignOriginOrHost() throws Exception {
        String ping = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        assertEquals(403, post(ping, "Authorization", "Bearer " + TOKEN, "Origin", "http://evil.example")
                .statusCode());
        assertEquals(403, post(ping, "Authorization", "Bearer " + TOKEN, "Origin", "null").statusCode());
        assertEquals(200, post(ping, "Authorization", "Bearer " + TOKEN, "Origin", "http://localhost:3000")
                .statusCode());

        assertTrue(rawStatusLine("evil.example:" + port).contains(" 403"), "a rebound host name");
        assertTrue(rawStatusLine("127.0.0.1:" + (port + 1)).contains(" 403"), "another port");
        assertTrue(rawStatusLine("localhost:" + port).contains(" 200"));
    }

    @Test
    void listensOn127001Only() throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 2000);
        }

        /*  Another address of this computer finds no server: refused, or dropped by a tunnel interface  */
        List<InetAddress> others = new ArrayList<>();
        for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces()))
            if (network.isUp() && !network.isLoopback())
                for (InetAddress address : Collections.list(network.getInetAddresses()))
                    if (address instanceof Inet4Address)
                        others.add(address);
        assumeTrue(!others.isEmpty(), "no network address besides the loopback");
        try (Socket socket = new Socket()) {
            assertThrows(IOException.class, () -> socket.connect(new InetSocketAddress(others.get(0), port), 2000));
        }
    }

    @Test
    void answersPostOnTheEndpointOnly() throws Exception {
        HttpResponse<String> get = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Authorization", "Bearer " + TOKEN).header("Accept", "text/event-stream").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(405, get.statusCode(), "no event stream");
        HttpResponse<String> other = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/other")).header("Authorization", "Bearer " + TOKEN).POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(404, other.statusCode());

        /*  Notifications get no answer, a broken message a parse error  */
        assertEquals(202, post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", "Authorization",
                "Bearer " + TOKEN).statusCode());
        HttpResponse<String> broken = post("{oops", "Authorization", "Bearer " + TOKEN);
        assertEquals(400, broken.statusCode());
        assertEquals(-32700, JSON.readTree(broken.body()).path("error").path("code").asInt());
        assertEquals(-32601, rpc("resources/list", "{}").path("error").path("code").asInt());
    }

    @Test
    void initializeNegotiatesTheProtocolVersion() throws Exception {
        JsonNode result = rpc("initialize", "{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}").path("result");
        assertEquals("2025-06-18", result.path("protocolVersion").asText());
        assertEquals("moodframe", result.path("serverInfo").path("name").asText());
        assertTrue(result.path("capabilities").has("tools"));

        /*  A version the server does not speak gets its latest one  */
        assertEquals("2025-11-25", rpc("initialize", "{\"protocolVersion\":\"2099-01-01\"}").path("result")
                .path("protocolVersion").asText());
        assertEquals(400, post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}", "Authorization",
                "Bearer " + TOKEN, "MCP-Protocol-Version", "2099-01-01").statusCode());
    }

    @Test
    void listsEveryReadingToolWithItsSchema() throws Exception {
        JsonNode tools = rpc("tools/list", "{}").path("result").path("tools");
        assertEquals(List.of("list_chats", "get_chat", "get_entry", "get_image", "download_image", "get_capabilities",
                "list_moods", "get_mood", "list_blocks", "get_block", "list_frames", "list_trash"),
                texts(tools, "name"));
        for (JsonNode tool : tools) {
            assertFalse(tool.path("description").asText().isBlank(), tool.path("name").asText());
            assertEquals("object", tool.path("inputSchema").path("type").asText(), tool.path("name").asText());
        }
        assertEquals("chatId", find(tools, "name", "get_chat").path("inputSchema").path("required").get(0).asText());
        assertEquals(-32602, rpc("tools/call", "{\"name\":\"delete_everything\"}").path("error").path("code")
                .asInt());
    }

    @Test
    void getCapabilitiesListsTheModelsOfTheEnabledProvidersAndTheCameraValues() throws Exception {
        JsonNode capabilities = call("get_capabilities", "{}");
        JsonNode models = capabilities.path("models");
        assertTrue(models.size() > 0);
        assertEquals(Set.of(ApiProvider.BFL.getDisplayName()), Set.copyOf(texts(models, "provider")),
                "only the enabled provider");
        JsonNode pro = find(models, "id", "FLUX2_PRO");
        assertEquals(8, pro.path("maxReferenceImages").asInt());
        assertEquals(2000, pro.path("maxPromptLength").asInt());
        assertTrue(pro.path("parameters").has("promptUpsampling"));
        assertEquals(5, pro.path("parameters").path("safetyTolerance").path("maximum").asInt());
        assertTrue(find(models, "id", "FLUX2_FLEX").path("parameters").has("steps"));
        assertEquals("[\"2K\"]", find(models, "id", "FLUX3_IMAGE_2K").path("resolutions").toString());
        assertTrue(capabilities.path("camera").path("body").toString().contains("DRONE"));
        assertTrue(capabilities.path("camera").path("lens").toString().contains("\"SHIFT\""));
        assertFalse(capabilities.path("camera").has("models"), "the camera type carries the look, no model");
    }

    @Test
    void listChatsAndGetChatShowTheChatPageByPage() throws Exception {
        JsonNode listed = find(call("list_chats", "{}").path("chats"), "id", chat.getId());
        assertEquals("Lighthouses", listed.path("name").asText());
        assertEquals(1, listed.path("entries").asInt());
        assertEquals(0.03, listed.path("cost").asDouble(), 1e-9);

        JsonNode page = call("get_chat", "{\"chatId\":\"" + chat.getId() + "\"}");
        assertEquals(1, page.path("total").asInt());
        JsonNode entry = page.path("entries").get(0);
        assertEquals("entry-1", entry.path("id").asText());
        assertEquals("a lighthouse", entry.path("prompt").asText());
        assertEquals("FLUX2_PRO", entry.path("modelId").asText());
        assertEquals("COMPLETED", entry.path("status").asText());
        assertEquals("Film Noir", entry.path("mood").path("name").asText());
        assertEquals("4:5", entry.path("frame").path("aspectRatio").asText());
        assertEquals("image-1", entry.path("imageIds").get(0).asText());
        assertEquals("UI", entry.path("source").asText(), "an entry of the send button");

        assertEquals(0, call("get_chat", "{\"chatId\":\"" + chat.getId() + "\",\"offset\":1}").path("entries")
                .size());
        assertTrue(toolError("get_chat", "{\"chatId\":\"no-such-chat\"}").contains("no-such-chat"));
        assertTrue(toolError("get_chat", "{\"chatId\":\"" + chat.getId() + "\",\"limit\":0}").contains("limit"));
        assertTrue(toolError("get_chat", "{}").contains("chatId"));
    }

    @Test
    void getEntryTellsStatusAndTheFilesOfTheImages() throws Exception {
        JsonNode entry = call("get_entry", "{\"entryId\":\"entry-1\"}");
        assertEquals("COMPLETED", entry.path("status").asText());
        assertEquals("Image style: Film.\na lighthouse", entry.path("finalPrompt").asText());
        assertEquals("std:frame:social-post", entry.path("frame").path("frame").path("id").asText());
        assertEquals("UI", entry.path("source").asText());
        JsonNode image = entry.path("images").get(0);
        assertEquals(imageFile.toString(), image.path("path").asText());
        assertEquals(2000, image.path("width").asInt());
        assertEquals(Files.size(imageFile), image.path("fileSize").asLong());
        assertEquals("Lighthouses 2026-11-16 17.36.17.png", image.path("downloadName").asText());
        assertTrue(toolError("get_entry", "{\"entryId\":\"nope\"}").contains("nope"));

        /*  Umlauts stay, a video is named .mp4, an image in the trash is not shown  */
        JsonNode umlauts = call("get_entry", "{\"entryId\":\"entry-2\"}").path("images");
        assertEquals(List.of("umlaut-image", "umlaut-video"), texts(umlauts, "id"));
        assertEquals(List.of("Größe Übung 2026-01-02 03.04.05.png", "Größe Übung 2026-01-02 03.04.06.mp4"),
                texts(umlauts, "downloadName"));
    }

    @Test
    void getImageReturnsAPreviewOrTheOriginal() throws Exception {
        JsonNode result = rpc("tools/call", "{\"name\":\"get_image\",\"arguments\":{\"imageId\":\"image-1\"}}")
                .path("result");
        JsonNode image = result.path("content").get(0);
        assertEquals("image", image.path("type").asText());
        assertEquals("image/jpeg", image.path("mimeType").asText());
        BufferedImage preview = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder()
                .decode(image.path("data").asText())));
        assertEquals(1024, preview.getWidth());
        assertEquals(512, preview.getHeight());
        assertEquals(imageFile.toString(), result.path("structuredContent").path("path").asText());
        assertEquals("Lighthouses 2026-11-16 17.36.17.png", result.path("structuredContent").path("downloadName")
                .asText());

        JsonNode original = call("get_image", "{\"imageId\":\"image-1\",\"size\":\"original\"}");
        assertEquals("image/png", original.path("mimeType").asText());
        assertEquals(2000, original.path("width").asInt());
        assertEquals(Files.size(imageFile), original.path("bytes").asLong());

        assertTrue(toolError("get_image", "{\"imageId\":\"image-1\",\"size\":\"huge\"}").contains("size"));
        assertTrue(toolError("get_image", "{\"imageId\":\"image-2\"}").contains("image-2"));
    }

    @Test
    void downloadImageCopiesLikeTheDownloadButton() throws Exception {
        Path folder = Files.createDirectory(tempDir.resolve("agent-downloads"));
        String arguments = "{\"imageId\":\"image-1\",\"directory\":" + JSON.writeValueAsString(folder.toString())
                + "}";
        JsonNode first = call("download_image", arguments);
        assertEquals("Lighthouses 2026-11-16 17.36.17.png", first.path("fileName").asText());
        assertEquals(folder.resolve("Lighthouses 2026-11-16 17.36.17.png").toString(), first.path("path").asText());
        assertEquals(Files.size(imageFile), Files.size(Path.of(first.path("path").asText())));
        assertEquals("Lighthouses 2026-11-16 17.36.17 2.png", call("download_image", arguments).path("fileName")
                .asText(), "an existing file is kept");

        /*  Umlauts stay; without folder a video goes to ~/Downloads of the throwaway home  */
        assertEquals("Größe Übung 2026-01-02 03.04.05.png", call("download_image", "{\"imageId\":\"umlaut-image\","
                + "\"directory\":" + JSON.writeValueAsString(folder.toString()) + "}").path("fileName").asText());
        JsonNode video = call("download_image", "{\"imageId\":\"umlaut-video\"}");
        assertEquals("Größe Übung 2026-01-02 03.04.06.mp4", video.path("fileName").asText());
        assertEquals(tempDir.resolve("Downloads").resolve("Größe Übung 2026-01-02 03.04.06.mp4").toString(),
                video.path("path").asText());
        assertTrue(Files.isRegularFile(Path.of(video.path("path").asText())));

        /*  The status bar learns of every download, on the JavaFX thread  */
        CountDownLatch shown = new CountDownLatch(1);
        Platform.runLater(shown::countDown);
        assertTrue(shown.await(10, TimeUnit.SECONDS));
        assertTrue(reported.contains("DOWNLOAD:Lighthouses 2026-11-16 17.36.17 2.png"), reported::toString);

        assertTrue(toolError("download_image", "{\"imageId\":\"image-1\",\"directory\":\"relative/folder\"}")
                .contains("directory"));
        assertTrue(toolError("download_image", "{\"imageId\":\"image-1\",\"directory\":"
                + JSON.writeValueAsString(tempDir.resolve("missing").toString()) + "}").contains("directory"));
        assertTrue(toolError("download_image", "{\"imageId\":\"no-such-image\"}").contains("no-such-image"));
        assertTrue(toolError("download_image", "{\"imageId\":\"deleted-image\"}").contains("deleted-image"));
        assertTrue(toolError("download_image", "{}").contains("imageId"));
        assertFalse(Files.exists(tempDir.resolve("missing")), "a missing folder is not created");
    }

    @Test
    void moodsShowTheirBlocksAndThePromptTheyMake() throws Exception {
        JsonNode moods = call("list_moods", "{}").path("moods");
        JsonNode noir = find(moods, "id", "std:mood:film-noir");
        assertTrue(noir.path("standard").asBoolean());
        assertEquals("Black-and-White Film", noir.path("blocks").path("style").path("name").asText());
        assertTrue(noir.path("blocks").path("camera").isNull());
        JsonNode own = find(moods, "id", ownMood.getId());
        assertFalse(own.path("standard").asBoolean());
        assertTrue(own.path("favorite").asBoolean());

        JsonNode mood = call("get_mood", "{\"moodId\":\"std:mood:film-noir\"}");
        assertFalse(mood.path("blocks").path("style").path("text").asText().isBlank());
        assertFalse(mood.path("blocks").path("color").path("colors").isEmpty());
        assertTrue(mood.path("prompt").asText().startsWith("Image style: "));
        assertTrue(mood.path("prompt").asText().contains("\n[subject]\n"));
        assertTrue(toolError("get_mood", "{\"moodId\":\"std:mood:none\"}").contains("std:mood:none"));
    }

    @Test
    void blocksAreListedByTypeAndResolved() throws Exception {
        JsonNode cameras = call("list_blocks", "{\"type\":\"camera\"}").path("blocks");
        JsonNode portrait = find(cameras, "name", "Classic Portrait");
        assertTrue(portrait.path("standard").asBoolean());

        JsonNode camera = call("get_block", "{\"type\":\"camera\",\"blockId\":\"" + portrait.path("id").asText()
                + "\"}");
        assertEquals("FULL_FRAME", camera.path("settings").path("body").asText());
        assertTrue(camera.path("text").asText().contains("85"));

        JsonNode palettes = call("list_blocks", "{\"type\":\"color\"}").path("blocks");
        JsonNode palette = call("get_block", "{\"type\":\"color\",\"blockId\":\"" + palettes.get(0).path("id")
                .asText() + "\"}");
        assertFalse(palette.path("colors").isEmpty());
        assertFalse(palette.path("text").asText().isBlank());

        assertTrue(toolError("list_blocks", "{\"type\":\"sound\"}").contains("type"));
        assertTrue(toolError("get_block", "{\"type\":\"style\",\"blockId\":\"std:light:candlelight\"}")
                .contains("std:light:candlelight"), "a block of another type is unknown");
    }

    @Test
    void framesAndTrashAreListed() throws Exception {
        JsonNode frames = call("list_frames", "{}").path("frames");
        JsonNode post = find(frames, "id", "std:frame:social-post");
        assertEquals("4:5", post.path("aspectRatio").asText());
        assertTrue(post.path("standard").asBoolean());
        assertFalse(texts(frames, "id").contains(trashedFrame.getId()), "a frame in the trash is not in use");

        JsonNode trash = call("list_trash", "{}");
        assertEquals("Old Frame", find(trash.path("frames"), "id", trashedFrame.getId()).path("name").asText());
        for (String kind : List.of("chats", "images", "blocks", "moods", "frames"))
            assertTrue(trash.path(kind).isArray(), kind);
    }
}
