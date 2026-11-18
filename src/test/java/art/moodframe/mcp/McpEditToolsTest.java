package art.moodframe.mcp;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.database.DatabaseConfig;
import art.moodframe.database.DatabaseManager;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.AspectRatios;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationCost;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.model.ModelFamilies;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.repository.ChatFiles;
import art.moodframe.repository.ChatRepository;
import art.moodframe.service.brainstorm.MoodAnalysisService;
import art.moodframe.service.chat.ChatHistoryService;
import art.moodframe.service.chat.ChatLibrary;
import art.moodframe.service.chat.ChatTrashGroup;
import art.moodframe.service.chat.ImageTrashGroup;
import art.moodframe.service.generation.GenerationService;
import art.moodframe.service.generation.RequestResponseService;
import art.moodframe.service.library.BlockTrashGroup;
import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.FavoriteService;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.library.FrameTrashGroup;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.MoodTrashGroup;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.prompt.PromptComposer;
import art.moodframe.service.trash.TrashCenter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The tools that change the library and generate, against a library in a throwaway {@code user.home} and a provider
 * that answers at once: what they store, what the main window learns of, the read-only standard entries, the trash
 * and the generation through the {@link GenerationService}.
 */
class McpEditToolsTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MODEL = GenerationModel.FLUX2_PRO.name();

    @TempDir
    static Path tempDir;

    private static String home;
    private static ChatLibrary chats;
    private static MoodService moods;
    private static PromptBlockService blocks;
    private static ColorStyleService colors;
    private static FrameService frames;
    private static FavoriteService favorites;
    private static Provider provider;
    private static McpTools tools;
    private static final List<String> reported = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void createTheLibrary() throws Exception {
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

        Path database = tempDir.resolve("db").resolve("edit.db");
        DatabaseConfig.initialize(database.toString());
        LibraryEvents events = new LibraryEvents();
        events.addListener((LibraryEvents.Kind kind, String id) -> reported.add(kind + ":" + id));
        ChatRepository repository = new ChatRepository(new DatabaseManager(database));
        chats = new ChatLibrary(new ChatHistoryService(repository, null, null), events);
        blocks = new PromptBlockService(events);
        colors = new ColorStyleService(events);
        moods = new MoodService(blocks, colors, events);
        frames = new FrameService(events);
        favorites = new FavoriteService(events);
        TrashCenter trash = new TrashCenter();
        trash.register(new ChatTrashGroup(repository));
        trash.register(new ImageTrashGroup(repository, ChatFiles.NONE, null, null));
        trash.register(new BlockTrashGroup(blocks, colors, moods));
        trash.register(new MoodTrashGroup(moods));
        trash.register(new FrameTrashGroup(frames));
        trash.addListener(chats);

        /*  Only BFL is enabled with a key; no language model for the analysis of images  */
        ApplicationConfig config = mock(ApplicationConfig.class);
        when(config.isBflProviderEnabled()).thenReturn(true);
        when(config.hasApiKey()).thenReturn(true);
        when(config.hasApiKey(any(ApiProvider.class))).thenCallRealMethod();
        when(config.getOpenAiModels()).thenReturn(List.of());
        when(config.getFlux2SafetyTolerance()).thenReturn(2);
        when(config.getFlux2FlexGuidance()).thenReturn(5.0);
        when(config.getFlux2FlexSteps()).thenReturn(50);

        provider = new Provider(repository);
        GenerationService generation = new GenerationService(config, chats, moods, provider);
        tools = McpTools.all(config, chats, moods, blocks, colors, frames, favorites, trash, events, generation,
                new MoodAnalysisService(null, null, config));
    }

    @AfterAll
    static void restoreHome() {
        System.setProperty("user.home", home);
    }

    /*  ===== Calls =====  */

    /*  the result of a tool call, which must succeed  */
    private static JsonNode call(String tool, String arguments) throws Exception {
        ObjectNode response = tools.call(tool, JSON.readTree(arguments));
        assertFalse(response.path("isError").asBoolean(), () -> tool + " failed: " + response);
        return response.path("structuredContent");
    }

    /*  the message of a tool call, which must fail  */
    private static String error(String tool, String arguments) throws Exception {
        ObjectNode response = tools.call(tool, JSON.readTree(arguments));
        assertTrue(response.path("isError").asBoolean(), () -> tool + " should fail: " + response);
        return response.path("content").get(0).path("text").asText();
    }

    /*  waits until the JavaFX thread ran what was queued before, the reports of the changes included  */
    private static void drainFxThread() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(done::countDown);
        assertTrue(done.await(10, TimeUnit.SECONDS));
    }

    private static List<String> ids(JsonNode list) {
        return StreamSupport.stream(list.spliterator(), false).map((JsonNode item) -> item.path("id").asText())
                .toList();
    }

    /*  ===== Chats =====  */

    @Test
    void createsRenamesAndDeletesAChat() throws Exception {
        String chatId = call("create_chat", "{\"name\":\"Harbors\"}").path("id").asText();
        assertEquals("Harbors", chats.find(chatId).getName());
        call("rename_chat", "{\"chatId\":\"" + chatId + "\",\"name\":\"Harbors at Dawn\"}");
        assertEquals("Harbors at Dawn", chats.find(chatId).getName());
        drainFxThread();
        assertTrue(reported.contains("CHAT:" + chatId), "the main window learns of the chat");

        assertTrue(call("delete_chat", "{\"chatId\":\"" + chatId + "\"}").path("inTrash").asBoolean());
        assertFalse(ids(call("list_chats", "{}").path("chats")).contains(chatId));
        assertTrue(ids(call("list_trash", "{}").path("chats")).contains(chatId));
        assertTrue(error("rename_chat", "{\"chatId\":\"" + chatId + "\",\"name\":\"x\"}").contains("No chat"));

        call("restore", "{\"kind\":\"chats\",\"id\":\"" + chatId + "\"}");
        assertTrue(ids(call("list_chats", "{}").path("chats")).contains(chatId), "the chat is back");
    }

    /*  ===== Moods =====  */

    @Test
    void createsAndChangesOwnMoodsOnly() throws Exception {
        JsonNode created = call("create_mood", "{\"name\":\"Harbor Mood\",\"blocks\":{\"style\":"
                + "\"Black-and-White Film\",\"light\":\"std:light:chiaroscuro\",\"color\":\"Silver Screen\"}}");
        String moodId = created.path("id").asText();
        Mood stored = moods.find(moodId);
        assertEquals("Harbor Mood", stored.getName());
        assertEquals(blocks.find(PromptBlock.Type.STYLE, "Black-and-White Film").getId(),
                stored.getReference(Mood.Field.STYLE), "a block by its name");
        assertEquals(colors.getStyleByName("Silver Screen").getId(), stored.getReference(Mood.Field.COLOR));
        assertNull(stored.getReference(Mood.Field.ATMOSPHERE), "a missing field stays empty");

        call("update_mood", "{\"moodId\":\"" + moodId + "\",\"name\":\"Harbor Mood 2\",\"blocks\":{\"light\":null}}");
        stored = moods.find(moodId);
        assertEquals("Harbor Mood 2", stored.getName());
        assertNull(stored.getReference(Mood.Field.LIGHT), "null empties the field");
        assertNotNull(stored.getReference(Mood.Field.STYLE), "a missing field stays as it is");
        drainFxThread();
        assertTrue(reported.contains("MOOD:" + moodId));

        /*  Standard moods are read-only, a taken name and an unknown block are refused  */
        assertTrue(error("update_mood", "{\"moodId\":\"std:mood:film-noir\",\"name\":\"Mine\"}")
                .contains("standard mood"));
        assertTrue(error("delete_mood", "{\"moodId\":\"std:mood:film-noir\"}").contains("create_mood"));
        assertTrue(error("create_mood", "{\"name\":\"Film Noir\"}").contains("Film Noir (1)"),
                "the next free name is proposed");
        assertTrue(error("create_mood", "{\"name\":\"Other\",\"blocks\":{\"style\":\"No Such Style\"}}")
                .contains("No style block"));

        call("delete_mood", "{\"moodId\":\"" + moodId + "\"}");
        assertNull(moods.find(moodId));
        assertTrue(ids(call("list_trash", "{}").path("moods")).contains(moodId));
        call("restore", "{\"kind\":\"moods\",\"id\":\"" + moodId + "\"}");
        assertNotNull(moods.find(moodId));
    }

    @Test
    void proposesTheBlocksOfAnImageWithoutStoringThem() throws Exception {
        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0x1E, 0x50, 0xA0));
        graphics.fillRect(0, 0, 100, 100);
        graphics.setColor(new Color(0xF0, 0xB4, 0x28));
        graphics.fillRect(100, 0, 100, 100);
        graphics.dispose();
        Path file = tempDir.resolve("harbor.png");
        ImageIO.write(image, "png", file.toFile());
        int palettes = colors.getAllStyles().size();

        JsonNode found = call("mood_from_image", "{\"image\":" + JSON.writeValueAsString(file.toString()) + "}");
        JsonNode palette = found.path("blocks").path("color");
        assertTrue(palette.path("new").asBoolean());
        assertFalse(palette.path("colors").isEmpty(), "the palette comes from the pixels");
        assertFalse(found.path("notice").isNull(), "without a language model, the notice says why");
        assertEquals(palettes, colors.getAllStyles().size(), "nothing is stored");
        assertTrue(error("mood_from_image", "{\"image\":\"no-such-image\"}").contains("No image"));
    }

    /*  ===== Building blocks =====  */

    @Test
    void createsChangesAndDeletesBlocksOfEveryType() throws Exception {
        String atmosphere = call("create_block", "{\"type\":\"atmosphere\",\"name\":\"Harbor Fog\","
                + "\"text\":\"thick fog over the harbor\"}").path("id").asText();
        assertEquals("thick fog over the harbor", blocks.find(PromptBlock.Type.ATMOSPHERE, atmosphere).getText());
        String style = call("create_block", "{\"type\":\"style\",\"name\":\"Ink\",\"text\":\"ink drawing\","
                + "\"photographic\":false}").path("id").asText();
        assertFalse(blocks.find(PromptBlock.Type.STYLE, style).isPhotographic());
        String palette = call("create_block", "{\"type\":\"color\",\"name\":\"Harbor Blues\","
                + "\"colors\":[\"#1e50a0\",\"#F0B428\"]}").path("id").asText();
        assertEquals(List.of("#1E50A0", "#F0B428"), colors.getStyleById(palette).getColors().stream()
                .filter((String color) -> color != null && !color.isEmpty()).toList());
        String camera = call("create_block", "{\"type\":\"camera\",\"name\":\"Harbor Cam\","
                + "\"settings\":{\"body\":\"FULL_FRAME\",\"lens\":\"MM_35\",\"shot\":\"WIDE\"}}").path("id").asText();

        /*  A camera changes setting by setting  */
        call("update_block", "{\"type\":\"camera\",\"blockId\":\"" + camera + "\",\"settings\":{\"lens\":\"MM_85\"}}");
        JsonNode settings = call("get_block", "{\"type\":\"camera\",\"blockId\":\"" + camera + "\"}")
                .path("settings");
        assertEquals("MM_85", settings.path("lens").asText());
        assertEquals("FULL_FRAME", settings.path("body").asText(), "a missing setting stays");
        call("update_block", "{\"type\":\"color\",\"blockId\":\"" + palette + "\",\"name\":\"Harbor Blues 2\"}");
        assertEquals("Harbor Blues 2", colors.getStyleById(palette).getName());
        assertEquals(2, colors.getStyleById(palette).getColors().stream()
                .filter((String color) -> color != null && !color.isEmpty()).count(), "the colors stay");
        drainFxThread();
        assertTrue(reported.contains("CAMERA:" + camera));

        /*  Content of another type, wrong colors, standard blocks and taken names are refused  */
        assertTrue(error("create_block", "{\"type\":\"camera\",\"name\":\"X\",\"text\":\"t\"}")
                .contains("does not apply"));
        assertTrue(error("create_block", "{\"type\":\"color\",\"name\":\"X\",\"colors\":[\"blue\"]}")
                .contains("#RRGGBB"));
        assertTrue(error("create_block", "{\"type\":\"camera\",\"name\":\"X\",\"settings\":{\"lens\":\"MM_1\"}}")
                .contains("MM_35"));
        assertTrue(error("update_block", "{\"type\":\"style\",\"blockId\":\"std:style:watercolor\","
                + "\"text\":\"t\"}").contains("standard style block"));
        assertTrue(error("create_block", "{\"type\":\"atmosphere\",\"name\":\"Harbor Fog\",\"text\":\"t\"}")
                .contains("Harbor Fog (1)"));

        /*  Deleting names the moods that use the block  */
        call("create_mood", "{\"name\":\"Blue Harbor\",\"blocks\":{\"color\":\"" + palette + "\"}}");
        JsonNode deleted = call("delete_block", "{\"type\":\"color\",\"blockId\":\"" + palette + "\"}");
        assertEquals(List.of("Blue Harbor"), JSON.convertValue(deleted.path("moods"), List.class));
        assertNull(colors.getStyleById(palette));
        call("delete_block", "{\"type\":\"atmosphere\",\"blockId\":\"" + atmosphere + "\"}");
        call("restore", "{\"kind\":\"blocks\",\"id\":\"" + atmosphere + "\"}");
        assertNotNull(blocks.find(PromptBlock.Type.ATMOSPHERE, atmosphere));
    }

    /*  ===== Frames and favorites =====  */

    @Test
    void createsChangesAndDeletesFramesAndSetsFavorites() throws Exception {
        String frameId = call("create_frame", "{\"name\":\"Harbor Poster\",\"aspectRatio\":\"2:3\","
                + "\"resolution\":\"4K\"}").path("id").asText();
        assertEquals("4K", frames.find(frameId).getResolution());
        call("update_frame", "{\"frameId\":\"" + frameId + "\",\"aspectRatio\":\"3:4\",\"resolution\":null}");
        assertEquals("3:4", frames.find(frameId).getAspectRatio());
        assertNull(frames.find(frameId).getResolution());
        assertTrue(error("update_frame", "{\"frameId\":\"std:frame:social-post\",\"name\":\"x\"}")
                .contains("standard frame"));
        assertTrue(error("create_frame", "{\"name\":\"X\",\"aspectRatio\":\"11:7\"}").contains("aspectRatio"));

        call("set_favorite", "{\"kind\":\"frame\",\"id\":\"" + frameId + "\",\"favorite\":true}");
        assertTrue(favorites.isFavorite(FavoriteService.Kind.FRAME, frameId));
        call("set_favorite", "{\"kind\":\"frame\",\"id\":\"" + frameId + "\",\"favorite\":true}");
        assertTrue(favorites.isFavorite(FavoriteService.Kind.FRAME, frameId), "setting it twice keeps it");
        call("set_favorite", "{\"kind\":\"style\",\"id\":\"std:style:watercolor\",\"favorite\":true}");
        assertTrue(favorites.isFavorite(FavoriteService.Kind.STYLE, "std:style:watercolor"),
                "standard entries can be favorites");
        call("set_favorite", "{\"kind\":\"frame\",\"id\":\"" + frameId + "\",\"favorite\":false}");
        assertFalse(favorites.isFavorite(FavoriteService.Kind.FRAME, frameId));
        assertTrue(error("set_favorite", "{\"kind\":\"mood\",\"id\":\"nope\",\"favorite\":true}").contains("No mood"));

        call("delete_frame", "{\"frameId\":\"" + frameId + "\"}");
        assertNull(frames.find(frameId));
        call("restore", "{\"kind\":\"frames\",\"id\":\"" + frameId + "\"}");
        assertNotNull(frames.find(frameId));
        assertTrue(error("restore", "{\"kind\":\"frames\",\"id\":\"" + frameId + "\"}").contains("trash"));
    }

    /*  ===== Generation =====  */

    private static final String ORDER = "\"prompt\":\"a harbor at dawn\",\"model\":\"" + MODEL + "\","
            + "\"moodId\":\"std:mood:film-noir\",\"blocks\":{\"atmosphere\":null},"
            + "\"frameId\":\"Social Post\",\"aspectRatio\":\"16:9\",\"count\":2";

    @Test
    void previewTellsWhatTheMainWindowSends() throws Exception {
        int sent = provider.sent.size();
        JsonNode preview = call("preview_generation", "{" + ORDER + "}");

        /*  As the main window composes and maps it: the blocks of the mood without the emptied atmosphere  */
        Mood filmNoir = moods.find("std:mood:film-noir");
        PromptComposer.Selection selection = moods.selection(filmNoir);
        String expected = new PromptComposer(GenerationService.maxPromptLength(MODEL)).compose("a harbor at dawn",
                new PromptComposer.Selection(selection.style(), null, selection.light(), selection.camera(),
                        selection.palette()));
        String item = GenerationModel.itemOf(MODEL);
        assertEquals(expected, preview.path("prompt").asText());
        assertTrue(preview.path("fits").asBoolean());
        assertEquals(AspectRatios.nearestForItem("16:9", item), preview.path("aspectRatio").asText());
        assertEquals(ModelFamilies.resolutionOf(item), preview.path("resolution").textValue());
        assertEquals(McpTools.usd(GenerationCost.estimate(GenerationModel.FLUX2_PRO, null) * 2),
                preview.path("cost").asDouble());
        assertTrue(preview.path("moodChanged").asBoolean(), "the emptied atmosphere changes the mood");
        assertTrue(preview.path("frameChanged").asBoolean(), "the aspect ratio changes the frame");
        assertEquals(sent, provider.sent.size(), "nothing is sent");

        /*  The order sent makes the same prompt, format and cost  */
        String chatId = call("create_chat", "{\"name\":\"Preview\"}").path("id").asText();
        JsonNode generated = call("generate", "{\"chatId\":\"" + chatId + "\"," + ORDER + "}");
        ChatEntry entry = chats.findEntry(generated.path("entryId").asText());
        assertEquals(preview.path("prompt").asText(), entry.getEnhancedPrompt());
        assertEquals(preview.path("aspectRatio").asText(), entry.getSize());
        assertEquals(preview.path("cost").asDouble(), McpTools.usd(provider.sentCosts.get(
                provider.sentCosts.size() - 1)), "the estimate of the entry as sent");
        ImageGenerationRequest request = provider.sent.get(provider.sent.size() - 1);
        assertEquals(preview.path("prompt").asText(), request.getPrompt());
        assertEquals(preview.path("aspectRatio").asText(), request.getAspectRatio());
        assertEquals("std:mood:film-noir", entry.getMood().baseId(), "the changed mood is the base of the copy");
    }

    @Test
    void previewWithAReferenceImageTellsTheEditIntroOfTheCamera() throws Exception {
        File photo = tempDir.resolve("reference.png").toFile();
        ImageIO.write(new BufferedImage(64, 36, BufferedImage.TYPE_INT_RGB), "png", photo);
        String order = "\"prompt\":\"a harbor at dawn\",\"model\":\"" + MODEL + "\",\"blocks\":{\"camera\":"
                + "\"Classic Portrait\"},\"referenceImages\":[" + JSON.writeValueAsString(photo.getPath()) + "]";

        String prompt = call("preview_generation", "{" + order + "}").path("prompt").asText();
        assertTrue(prompt.contains(PromptComposer.EDIT_CAMERA_INTRO + " Camera: "), prompt);

        /*  The order sent makes the same prompt  */
        String chatId = call("create_chat", "{\"name\":\"Edit\"}").path("id").asText();
        JsonNode generated = call("generate", "{\"chatId\":\"" + chatId + "\"," + order + "}");
        assertEquals(prompt, chats.findEntry(generated.path("entryId").asText()).getEnhancedPrompt());
        assertEquals(prompt, provider.sent.get(provider.sent.size() - 1).getPrompt());
    }

    @Test
    void generatesIntoTheChatAndDeletesToTheTrash() throws Exception {
        String chatId = call("create_chat", "{\"name\":\"Generated\"}").path("id").asText();
        JsonNode result = call("generate", "{\"chatId\":\"" + chatId + "\",\"prompt\":\"a lighthouse\","
                + "\"model\":\"" + MODEL + "\",\"count\":2,\"wait\":10}");
        assertEquals("COMPLETED", result.path("status").asText());
        assertEquals(2, result.path("images").size());
        ImageGenerationRequest request = provider.sent.get(provider.sent.size() - 1);
        double price = GenerationCost.perImage(GenerationModel.FLUX2_PRO, request.getWidth(), request.getHeight(),
                null);
        assertTrue(price > 0);
        assertEquals(McpTools.usd(2 * price), result.path("cost").asDouble(), "a FLUX entry costs its images");
        for (JsonNode image : result.path("images")) {
            assertTrue(Files.isRegularFile(Path.of(image.path("path").asText())));
            assertTrue(image.path("downloadName").asText().matches("Generated \\d{4}-\\d\\d-\\d\\d "
                    + "\\d\\d\\.\\d\\d\\.\\d\\d\\.(png|jpeg|webp)"), image.path("downloadName").asText());
        }
        assertFalse(result.has("hint"));
        String entryId = result.path("entryId").asText();
        assertTrue(ids(call("get_chat", "{\"chatId\":\"" + chatId + "\"}").path("entries")).contains(entryId));

        /*  The entry is one of an AI agent, as the chat and the entry tell  */
        assertEquals(ChatEntry.Source.MCP, chats.findEntry(entryId).getSource());
        assertEquals("MCP", call("get_entry", "{\"entryId\":\"" + entryId + "\"}").path("source").asText());
        for (JsonNode listed : call("get_chat", "{\"chatId\":\"" + chatId + "\"}").path("entries"))
            assertEquals("MCP", listed.path("source").asText());
        drainFxThread();
        assertTrue(reported.contains("ENTRY:" + entryId), "the main window learns of the entry");

        /*  An image to the trash, then the entry; restore brings the entry back with its images  */
        String imageId = result.path("images").get(0).path("id").asText();
        JsonNode deleted = call("delete_image", "{\"imageId\":\"" + imageId + "\"}");
        assertFalse(deleted.path("entryInTrash").asBoolean());
        assertEquals(1, call("get_entry", "{\"entryId\":\"" + entryId + "\"}").path("images").size());
        call("delete_entry", "{\"entryId\":\"" + entryId + "\"}");
        assertTrue(error("get_entry", "{\"entryId\":\"" + entryId + "\"}").contains("No chat entry"));
        assertTrue(ids(call("list_trash", "{}").path("images")).contains(entryId));
        call("restore", "{\"kind\":\"images\",\"id\":\"" + entryId + "\"}");
        assertEquals(2, call("get_entry", "{\"entryId\":\"" + entryId + "\"}").path("images").size());

        /*  Unknown models, parameters the model does not take and too many references are refused  */
        assertTrue(error("generate", "{\"chatId\":\"" + chatId + "\",\"prompt\":\"x\",\"model\":\"NO_MODEL\"}")
                .contains("get_capabilities"));
        assertTrue(error("generate", "{\"chatId\":\"" + chatId + "\",\"prompt\":\"x\",\"model\":\"" + MODEL
                + "\",\"params\":{\"thinkingLevel\":\"high\"}}").contains("thinkingLevel"));
        String reference = JSON.writeValueAsString(result.path("images").get(1).path("path").asText());
        assertTrue(error("preview_generation", "{\"prompt\":\"x\",\"model\":\"" + MODEL + "\",\"referenceImages\":["
                + String.join(",", Collections.nCopies(9, reference)) + "]}").contains("at most 8"));
    }

    @Test
    void cancelsARunningEntry() throws Exception {
        String chatId = call("create_chat", "{\"name\":\"Cancelled\"}").path("id").asText();
        provider.hold = true;
        try {
            JsonNode result = call("generate", "{\"chatId\":\"" + chatId + "\",\"prompt\":\"a storm\","
                    + "\"model\":\"" + MODEL + "\",\"wait\":0}");
            String entryId = result.path("entryId").asText();
            assertTrue(result.has("hint"), "a running entry points to get_entry");
            assertTrue(error("delete_entry", "{\"entryId\":\"" + entryId + "\"}").contains("cancel_generation"));

            assertEquals("CANCELLED", call("cancel_generation", "{\"entryId\":\"" + entryId + "\"}")
                    .path("status").asText());
            assertEquals("CANCELLED", call("get_entry", "{\"entryId\":\"" + entryId + "\"}").path("status").asText());
            assertTrue(error("cancel_generation", "{\"entryId\":\"" + entryId + "\"}").contains("not running"));
            call("delete_entry", "{\"entryId\":\"" + entryId + "\"}");
        }
        finally {
            provider.hold = false;
        }
    }

    /*  ===== The provider =====  */

    /**
     * Answers every request at once on the JavaFX thread with one stored image file per image, as the providers do in
     * the end; while held, a request keeps running until it is cancelled.
     */
    private static class Provider implements RequestResponseService {

        private final ChatRepository repository;
        private final List<Consumer<ChatEntry>> listeners = new CopyOnWriteArrayList<>();
        final List<ImageGenerationRequest> sent = new CopyOnWriteArrayList<>();
        final List<Double> sentCosts = new CopyOnWriteArrayList<>();
        volatile boolean hold;

        Provider(ChatRepository repository) {
            this.repository = repository;
        }

        @Override
        public void addEntryListener(Consumer<ChatEntry> listener) {
            listeners.add(listener);
        }

        @Override
        public void removeEntryListener(Consumer<ChatEntry> listener) {
            listeners.remove(listener);
        }

        @Override
        public void addImageListener(Consumer<GeneratedImage> listener) {
        }

        @Override
        public void removeImageListener(Consumer<GeneratedImage> listener) {
        }

        @Override
        public void setOnSafetyToleranceLowered(IntConsumer callback) {
        }

        @Override
        public CompletableFuture<Void> submitImageGeneration(ImageGenerationRequest request, ChatEntry chatEntry,
                File attachedImageFile) {
            sent.add(request);
            sentCosts.add(chatEntry.getCost());
            boolean held = hold;
            Platform.runLater(() -> {
                chatEntry.setStatus(ChatEntry.Status.PROCESSING);
                try {
                    if (!held) {
                        for (int i = 0; i < request.getImageCount(); i++) {
                            File file = tempDir.resolve(chatEntry.getId() + "-" + i + ".png").toFile();
                            ImageIO.write(new BufferedImage(64, 36, BufferedImage.TYPE_INT_RGB), "png", file);
                            GeneratedImage image = new GeneratedImage.Builder().chatEntryId(chatEntry.getId())
                                    .localFilePath(file.getPath()).filename(file.getName()).sortOrder(i)
                                    .downloadStatus(GeneratedImage.DownloadStatus.CACHED).imageWidth(64)
                                    .imageHeight(36).fileSizeBytes(file.length()).mimeType("image/png")
                                    .baseCost(request.getBaseCostPerImage()).build();
                            repository.saveGeneratedImage(image);
                            chatEntry.getImages().add(image);
                        }

                        /*  as the providers settle it: the entry costs the sum of its images  */
                        chatEntry.setCost(chatEntry.getImages().stream()
                                .mapToDouble((GeneratedImage image) -> image.getBaseCost()).sum());
                        chatEntry.setStatus(ChatEntry.Status.COMPLETED);
                    }
                    repository.saveChatEntry(chatEntry);
                }
                catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                listeners.forEach((Consumer<ChatEntry> listener) -> listener.accept(chatEntry));
            });
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> cancelEntry(ChatEntry chatEntry) {
            CompletableFuture<Boolean> cancelled = new CompletableFuture<>();
            Platform.runLater(() -> {
                boolean running = chatEntry.getStatus() == ChatEntry.Status.PENDING
                        || chatEntry.getStatus() == ChatEntry.Status.PROCESSING;
                if (running) {
                    chatEntry.setStatus(ChatEntry.Status.CANCELLED);
                    listeners.forEach((Consumer<ChatEntry> listener) -> listener.accept(chatEntry));
                }
                cancelled.complete(running);
            });
            return cancelled;
        }

        @Override
        public void shutdown() {
        }
    }
}
