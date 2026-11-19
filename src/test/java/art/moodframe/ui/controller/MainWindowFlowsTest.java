package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ConfigMocks;
import art.moodframe.config.LibraryPaths;
import art.moodframe.config.ServiceFactory;
import art.moodframe.database.DatabaseManager;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.Frame;
import art.moodframe.model.FrameGroup;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.Mood;
import art.moodframe.model.MoodGroup;
import art.moodframe.repository.BrainstormRepository;
import art.moodframe.repository.ChatGroupQueries;
import art.moodframe.repository.ChatRepository;
import art.moodframe.repository.ChatTrashRepository;
import art.moodframe.repository.ColorStyleRepository;
import art.moodframe.repository.FavoriteRepository;
import art.moodframe.repository.FrameRepository;
import art.moodframe.repository.MoodRepository;
import art.moodframe.repository.PromptBlockRepository;
import art.moodframe.service.brainstorm.BrainstormService;
import art.moodframe.service.brainstorm.MoodAnalysisService;
import art.moodframe.service.chat.ChatFileService;
import art.moodframe.service.chat.ChatHistoryService;
import art.moodframe.service.chat.ChatLibrary;
import art.moodframe.service.chat.ChatTrashGroup;
import art.moodframe.service.chat.FileManagerService;
import art.moodframe.service.chat.ImageAttachmentService;
import art.moodframe.service.chat.ImageSizeCheck;
import art.moodframe.service.chat.ImageTrashGroup;
import art.moodframe.service.chat.ModelLock;
import art.moodframe.service.chat.ThumbnailManager;
import art.moodframe.service.generation.GenerationService;
import art.moodframe.service.generation.ModelLabels;
import art.moodframe.service.generation.RequestResponseManager;
import art.moodframe.service.generation.TestRequests;
import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.FavoriteService;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.provider.BflApiService;
import art.moodframe.service.provider.GeminiApiService;
import art.moodframe.service.provider.GenerationJobInfo;
import art.moodframe.service.provider.OpenAiApiService;
import art.moodframe.service.provider.ProviderRegistry;
import art.moodframe.service.trash.TrashCenter;
import art.moodframe.service.trash.TrashEntry;
import art.moodframe.service.trash.TrashGroup;
import art.moodframe.service.trash.TrashManager;
import art.moodframe.ui.brainstorm.BrainstormDialog;
import art.moodframe.ui.cell.ChatListCell;
import art.moodframe.ui.cell.MoodGroupListCell;
import art.moodframe.ui.entry.ChatEntryCard;
import art.moodframe.ui.entry.ImagePreviewOverlay;
import art.moodframe.ui.component.InPlaceEditableLabel;
import art.moodframe.ui.component.MultiImageAttachmentPanel;
import art.moodframe.ui.support.ThemeManager;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kordamp.ikonli.javafx.FontIcon;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * The flows of the main window across its parts, in a shown window on a throwaway {@code user.home} with a library of
 * its own (database, images, trash) and the services wired as the application does; the providers are mocks, a Gemini
 * generation answers at once with a small test image, so nothing reaches the network. Each test starts with a fresh
 * library and window and leaves no window, dialog, listener or thread behind that would keep the window alive.
 */
class MainWindowFlowsTest {

    /*  the Gemini model the generations go to, and a model of BFL for the seeds  */
    private static final String GEMINI_FAMILY = "Nano Banana 2";
    private static final GenerationModel FLUX = GenerationModel.FLUX2_PRO;

    private static final String TRASH_ICON = "mdi2t-trash-can-outline";

    @TempDir
    static Path home;

    private static String realHome;
    private static Locale locale;
    private static ApplicationConfig realConfig;
    private static final Map<Class<?>, Object> realServices = new HashMap<>();
    private static String testImage;

    /*  the library of a test  */
    private ApplicationConfig config;
    private ChatRepository repository;
    private ChatLibrary chats;
    private TrashCenter trash;
    private MoodService moods;
    private FrameService frames;
    private ThumbnailManager thumbnails;
    private ImageSizeCheck sizeCheck;
    private RequestResponseManager requests;
    private ExecutorService io;
    private final AtomicInteger generations = new AtomicInteger();
    private final AtomicInteger loads = new AtomicInteger();
    private final List<String> deletions = new CopyOnWriteArrayList<>();
    private final List<String> chatWrites = new CopyOnWriteArrayList<>();

    /*  the window of a test  */
    private int languageListeners;
    private int themeListeners;
    private Stage stage;
    private Parent root;
    private MainWindowController controller;

    @BeforeAll
    static void startTheToolkit() throws Exception {
        realHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        }
        catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
        realConfig = ServiceFactory.getInstance().findService(ApplicationConfig.class).orElse(null);
        locale = I18nManager.getCurrentLocale();

        /*  what the mocked Gemini answers: a small PNG  */
        BufferedImage image = new BufferedImage(64, 36, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        testImage = Base64.getEncoder().encodeToString(png.toByteArray());
    }

    @AfterAll
    static void restoreTheEnvironment() throws Exception {
        I18nManager.setLocale(locale);
        if (realConfig != null)
            ServiceFactory.getInstance().registerService(ApplicationConfig.class, realConfig);
        Map<Class<?>, Object> services = services();
        realServices.forEach((type, service) -> {
            if (service != null)
                services.put(type, service);
            else
                services.remove(type);
        });
        System.setProperty("user.home", realHome);
    }

    /*  the services the factory holds, of which this test takes out again what it put in  */
    @SuppressWarnings("unchecked")
    private static Map<Class<?>, Object> services() throws Exception {
        Field services = ServiceFactory.class.getDeclaredField("services");
        services.setAccessible(true);
        return (Map<Class<?>, Object>) services.get(ServiceFactory.getInstance());
    }

    /*  the chats of the library, which note the thread every deletion and every write of a chat (trash, order, name)
        runs on and count their loads  */
    private static ChatLibrary recording(ChatHistoryService history, LibraryEvents events, List<String> deletions,
                                         List<String> chatWrites, AtomicInteger loads) {
        return new ChatLibrary(history, events) {
            @Override
            public boolean moveToTrash(Chat chat) throws SQLException {
                chatWrites.add(Thread.currentThread().getName());
                return super.moveToTrash(chat);
            }

            @Override
            public void moveChat(Chat chat, Chat before) throws SQLException {
                chatWrites.add(Thread.currentThread().getName());
                super.moveChat(chat, before);
            }

            @Override
            public void renameChat(Chat chat, String name) throws SQLException {
                chatWrites.add(Thread.currentThread().getName());
                super.renameChat(chat, name);
            }

            @Override
            public void load() {
                loads.incrementAndGet();
                super.load();
            }

            @Override
            public void deleteEntry(ChatEntry entry) throws SQLException {
                deletions.add(Thread.currentThread().getName());
                super.deleteEntry(entry);
            }

            @Override
            public boolean deleteImage(ChatEntry entry, GeneratedImage image) throws SQLException {
                deletions.add(Thread.currentThread().getName());
                return super.deleteImage(entry, image);
            }
        };
    }

    /*  the chats in the database, whose writes of finished images by a generation take a while, as on a slow disk:
        what a generation stores must be done before the window queries it  */
    private static ChatRepository slowImageWrites(DatabaseManager database) {
        return new ChatRepository(database) {
            @Override
            public boolean saveGeneratedImage(GeneratedImage image) throws SQLException {
                if (Thread.currentThread().getName().startsWith("generation-store")
                        && image.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED) {
                    try {
                        Thread.sleep(150);
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return super.saveGeneratedImage(image);
            }
        };
    }

    private static <T> void register(Class<T> type, T service) throws Exception {
        if (!realServices.containsKey(type))
            realServices.put(type, services().get(type));
        ServiceFactory.getInstance().registerService(type, service);
    }

    /**
     * Wires a fresh library as {@code MoodframeApplication} does, with mocked providers, and shows a new main window
     * in English with every model of BFL and Gemini to choose.
     */
    @BeforeEach
    void openTheWindow() throws Exception {
        I18nManager.setLocale(Locale.ENGLISH);
        config = ConfigMocks.withBflAndGemini();
        ServiceFactory.getInstance().registerService(ApplicationConfig.class, config);
        LibraryPaths paths = config.paths();
        register(LibraryPaths.class, paths);
        if (!realServices.containsKey(DatabaseManager.class))
            realServices.put(DatabaseManager.class, services().get(DatabaseManager.class));
        DatabaseManager database = new DatabaseManager(paths.database());
        database.initialize();

        LibraryEvents events = new LibraryEvents();
        PromptBlockService blocks = new PromptBlockService(new PromptBlockRepository(database), events);
        ColorStyleService colors = new ColorStyleService(new ColorStyleRepository(database), events);
        moods = new MoodService(new MoodRepository(database), blocks, colors, events);
        frames = new FrameService(new FrameRepository(database), events);
        register(LibraryEvents.class, events);
        register(PromptBlockService.class, blocks);
        register(ColorStyleService.class, colors);
        register(MoodService.class, moods);
        register(FrameService.class, frames);
        register(FavoriteService.class, new FavoriteService(new FavoriteRepository(database), events));

        /*  chats, their files and the trash  */
        TrashManager trashManager = new TrashManager(paths, 60);
        thumbnails = new ThumbnailManager(paths);
        ChatFileService files = new ChatFileService(trashManager, thumbnails,
                new FileManagerService(paths.images(), thumbnails));
        repository = slowImageWrites(database);
        ChatTrashRepository chatTrash = new ChatTrashRepository(database, files);
        trash = new TrashCenter();
        trash.register(new ChatTrashGroup(repository, chatTrash));
        trash.register(new ImageTrashGroup(repository, chatTrash, trashManager, thumbnails));
        ChatHistoryService history = new ChatHistoryService(repository, chatTrash, new ChatGroupQueries(database),
                trashManager);
        chats = recording(history, events, deletions, chatWrites, loads);
        trash.addListener(chats);

        /*  the start loads the chats before the window opens  */
        chats.load();
        register(TrashManager.class, trashManager);
        register(ThumbnailManager.class, thumbnails);
        register(ChatFileService.class, files);
        register(ChatRepository.class, repository);
        register(TrashCenter.class, trash);
        register(ChatHistoryService.class, history);
        register(ChatLibrary.class, chats);
        register(ImageAttachmentService.class, new ImageAttachmentService());
        sizeCheck = new ImageSizeCheck(repository, Platform::runLater);
        register(ImageSizeCheck.class, sizeCheck);

        /*  the providers: Gemini answers every request at once with the test image; the answer holds nothing of the
            test, as Mockito keeps it as long as the mock  */
        BflApiService bfl = mock(BflApiService.class, CALLS_REAL_METHODS);
        GeminiApiService gemini = mock(GeminiApiService.class, CALLS_REAL_METHODS);
        OpenAiApiService openAi = mock(OpenAiApiService.class, CALLS_REAL_METHODS);
        AtomicInteger count = generations;
        doAnswer((invocation) -> {
            GenerationJobInfo job = new GenerationJobInfo("job-" + count.incrementAndGet(), "Ready");
            job.setSynchronous(true);
            job.setBase64Image(testImage);
            return CompletableFuture.completedFuture(job);
        }).when(gemini).submitGenerationRequest(any());
        ProviderRegistry providers = new ProviderRegistry(bfl, gemini, openAi);
        register(BflApiService.class, bfl);
        register(GeminiApiService.class, gemini);
        register(OpenAiApiService.class, openAi);
        register(ProviderRegistry.class, providers);
        io = Executors.newCachedThreadPool((runnable) -> {
            Thread thread = new Thread(runnable, "MainWindowFlowsTest-io");
            thread.setDaemon(true);
            return thread;
        });
        requests = TestRequests.create(config, repository, providers, io);
        register(RequestResponseManager.class, requests);
        register(GenerationService.class, new GenerationService(config, chats, moods, requests, providers,
                Runnable::run));

        /*  the brainstorm and the analysis of an image, on the mocked language models  */
        ApplicationConfig brainstormConfig = config;
        register(BrainstormService.Factory.class, () -> new BrainstormService(new BrainstormRepository(database),
                gemini, openAi, brainstormConfig));
        register(MoodAnalysisService.class, new MoodAnalysisService(gemini, openAi, config));

        languageListeners = I18nManager.getChangeListenerCount();
        themeListeners = ThemeManager.getThemeListenerCount();
        onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(MainWindowFlowsTest.class.getResource("/fxml/MainWindow.fxml"));
            root = loader.load();
            controller = loader.getController();
            stage = new Stage();
            stage.setScene(new Scene(root, 1200, 800));
            stage.getScene().getStylesheets().add(MainWindowFlowsTest.class.getResource("/css/icon-button.css")
                    .toExternalForm());
            stage.show();
        });
    }

    /**
     * Closes the dialogs and the window, which lets go of what would keep it alive: the listeners of the language and
     * the theme, the clipboard watch, the timeline of the memory display; then the threads of the work end.
     */
    @AfterEach
    void closeTheWindow() throws Exception {
        if (stage == null)
            return;
        onFxThread(() -> {
            for (Window window : List.copyOf(Window.getWindows()))
                if (window != stage && window.isShowing())
                    window.hide();
        });
        onFxThread(stage::hide);
        I18nManager.setLocale(Locale.ENGLISH);
        requests.shutdown();
        io.shutdownNow();
        thumbnails.shutdown();
        sizeCheck.shutdown();
        stage = null;
        assertEquals(languageListeners, I18nManager.getChangeListenerCount(), "the window leaves no listener behind");
        assertEquals(themeListeners, ThemeManager.getThemeListenerCount(), "nor a listener of the theme");
    }

    // ===== Steps on the JavaFX thread =====

    /*  a step on the JavaFX thread  */
    private interface Step {
        void run() throws Exception;
    }

    /** Runs a step on the JavaFX thread and waits for it and for the layout of what it changed. */
    private void onFxThread(Step step) throws Exception {
        onFx(() -> {
            step.run();
            return null;
        });
        if (stage != null && stage.isShowing())
            waitForLayout();
    }

    /** Gets a value on the JavaFX thread. */
    private static <T> T onFx(Callable<T> value) throws Exception {
        FutureTask<T> task = new FutureTask<>(value);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }

    /** Waits for three layout passes of the window, so that also what a step left for later is laid out. */
    private void waitForLayout() throws Exception {
        CountDownLatch passes = new CountDownLatch(3);
        Scene scene = stage.getScene();
        Runnable pass = () -> {
            passes.countDown();
            Platform.requestNextPulse();
        };
        Platform.runLater(() -> {
            scene.addPostLayoutPulseListener(pass);
            Platform.requestNextPulse();
        });
        assertTrue(passes.await(10, TimeUnit.SECONDS), "the window lays out its changes");
        Platform.runLater(() -> scene.removePostLayoutPulseListener(pass));
    }

    /** Waits until a condition, checked on the JavaFX thread, holds; the window shows what it waited for. */
    private void waitUntil(String what, Callable<Boolean> condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!onFx(condition)) {
            if (System.nanoTime() > end)
                fail("Timed out waiting until " + what);
            Thread.sleep(20);
        }
        waitForLayout();
    }

    /** Runs work as another caller would, on a thread of its own, and waits for it. */
    private static void fromAnotherThread(Step work) throws Exception {
        CompletableFuture<Void> done = CompletableFuture.runAsync(() -> {
            try {
                work.run();
            }
            catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        done.get(20, TimeUnit.SECONDS);
    }

    /**
     * Answers the next confirmation with OK; call it on the JavaFX thread right before the action that asks, it runs
     * once the dialog waits for the answer.
     */
    private static void confirmTheNextDialog() {
        Platform.runLater(() -> {
            for (Window window : Window.getWindows())
                if (window.isShowing() && window.getScene() != null
                        && window.getScene().getRoot() instanceof DialogPane pane) {
                    ButtonType ok = pane.getButtonTypes().stream()
                            .filter((type) -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst()
                            .orElseThrow();
                    ((Button) pane.lookupButton(ok)).fire();
                    return;
                }
        });
    }

    // ===== The parts of the window =====

    @SuppressWarnings("unchecked")
    private ListView<Chat> chatList() {
        return (ListView<Chat>) root.lookup("#chatListView");
    }

    @SuppressWarnings("unchecked")
    private ListView<ChatEntry> entryList() {
        return (ListView<ChatEntry>) root.lookup("#chatEntriesListView");
    }

    @SuppressWarnings("unchecked")
    private ListView<MoodGroup> moodGroups() {
        return (ListView<MoodGroup>) root.lookup("#moodGroupListView");
    }

    @SuppressWarnings("unchecked")
    private ListView<FrameGroup> frameGroups() {
        return (ListView<FrameGroup>) root.lookup("#frameGroupListView");
    }

    private TextArea prompt() {
        return (TextArea) root.lookup("#promptTextArea");
    }

    private Button sendButton() {
        return (Button) root.lookup("#sendButton");
    }

    private Button brainstormButton() {
        return (Button) root.lookup("#brainstormButton");
    }

    private Label header() {
        return (Label) root.lookup("#chatTitleLabel");
    }

    private Label status() {
        return (Label) root.lookup("#statusLabel");
    }

    private Label cost() {
        return (Label) root.lookup("#chatTotalCostLabel");
    }

    private ImagePreviewOverlay overlay() {
        return (ImagePreviewOverlay) root.lookup(".image-preview-overlay");
    }

    private RadioMenuItem sidebarMode(String id) {
        return (RadioMenuItem) ((MenuButton) root.lookup("#sidebarModeButton")).getItems().stream()
                .filter((item) -> id.equals(item.getId())).findFirst().orElseThrow();
    }

    /** The card of an entry the chat view shows, null while it is not shown. */
    private ChatEntryCard card(ChatEntry entry) {
        return entryList().lookupAll(".list-cell").stream()
                .filter((node) -> node instanceof ListCell<?> cell && cell.getItem() == entry && node.isVisible()
                        && cell.getGraphic() instanceof ChatEntryCard)
                .map((node) -> (ChatEntryCard) ((ListCell<?>) node).getGraphic()).findFirst().orElse(null);
    }

    /** The cell of a chat in the sidebar. */
    private ChatListCell chatCell(Chat chat) {
        return chatList().lookupAll(".list-cell").stream()
                .filter((node) -> node instanceof ChatListCell cell && cell.getItem() == chat && node.isVisible())
                .map((node) -> (ChatListCell) node).findFirst().orElseThrow();
    }

    /** The buttons below a node with an icon, in the order of the scene. */
    private static List<Button> buttons(Node parent, String icon) {
        return parent.lookupAll(".button").stream()
                .filter((node) -> node instanceof Button button && button.getGraphic() instanceof FontIcon graphic
                        && icon.equals(graphic.getIconLiteral()))
                .map((node) -> (Button) node).toList();
    }

    /** The first node of a type below a node, depth first. */
    private static <T> T find(Node node, Class<T> type) {
        if (type.isInstance(node))
            return type.cast(node);
        if (node instanceof Parent parent)
            for (Node child : parent.getChildrenUnmodifiable()) {
                T found = find(child, type);
                if (found != null)
                    return found;
            }
        return null;
    }

    /** Whether a card shows all images of its entry. */
    private static boolean showsItsImages(ChatEntryCard card, ChatEntry entry) {
        return card != null && card.lookupAll(".image-view").stream()
                .filter((node) -> ((ImageView) node).getImage() != null
                        && ((ImageView) node).getOnContextMenuRequested() != null)
                .count() == entry.getImages().size();
    }

    // ===== The library =====

    /** Creates a stored chat on top of the list, as an MCP client would. */
    private Chat createChat(String name) throws Exception {
        Chat[] created = new Chat[1];
        fromAnotherThread(() -> created[0] = chats.createChat(name));
        waitUntil("the chat " + name + " is listed", () -> chatList().getItems().contains(created[0]));
        return created[0];
    }

    /** Opens a chat as a click in the sidebar does. */
    private void open(Chat chat) throws Exception {
        onFxThread(() -> chatList().getSelectionModel().select(chat));
        assertEquals(chat.getName(), header().getText());
    }

    /**
     * Adds a finished entry with images to a chat as another caller would, the images stored in the images folder:
     * each costs 0.10 and has the given seed plus its index.
     */
    private ChatEntry addEntry(Chat chat, String prompt, GenerationModel model, long seed, int images)
            throws Exception {
        ChatEntry entry = new ChatEntry.Builder().chatId(chat.getId()).userPrompt(prompt).enhancedPrompt(prompt)
                .status(ChatEntry.Status.COMPLETED).model(model.getDisplayName()).size("16:9").seed(seed)
                .cost(0.10 * images).build();
        Path folder = Files.createDirectories(config.paths().images());
        for (int index = 0; index < images; index++) {
            GeneratedImage image = new GeneratedImage.Builder().chatEntryId(entry.getId())
                    .downloadStatus(GeneratedImage.DownloadStatus.CACHED).model(model.getApiModelId())
                    .seed(seed + index).sortOrder(index).baseCost(0.10).width(64).height(36).format("PNG")
                    .mimeType("image/png").aspectRatio("16:9").build();
            Path file = folder.resolve(image.getId() + ".png");
            Files.write(file, Base64.getDecoder().decode(testImage));
            image.setLocalFilePath(file.toString());
            image.setFileName(file.getFileName().toString());
            entry.getImages().add(image);
        }
        fromAnotherThread(() -> {
            chats.addEntry(chat, entry);
            for (GeneratedImage image : entry.getImages())
                repository.saveGeneratedImage(image);
        });
        return entry;
    }

    /** Chooses the family of the Gemini models, whose generations the mock answers. */
    private void chooseGemini() throws Exception {
        onFxThread(() -> controller.generationPanel().modelFamilyChoice().setValue(GEMINI_FAMILY));
        assertEquals(ApiProvider.GOOGLE_GEMINI, controller.generationPanel().selectedModel().getProvider());
    }

    /**
     * Types a prompt and sends it with the button, waits for the images in the card and in the database (written on
     * a thread of their own after the entry finished) and returns the new entry.
     */
    private ChatEntry send(Chat chat, String text) throws Exception {
        int before = onFx(() -> chat.getEntries().size());
        onFxThread(() -> prompt().setText(text));
        assertFalse(sendButton().isDisabled(), "the prompt can be sent");
        onFxThread(() -> sendButton().fire());
        waitUntil("the entry of '" + text + "' is complete", () -> chat.getEntries().size() == before + 1
                && chat.getEntries().get(before).getStatus() == ChatEntry.Status.COMPLETED);
        ChatEntry entry = onFx(() -> chat.getEntries().get(before));
        waitUntil("the card of '" + text + "' shows its images", () -> showsItsImages(card(entry), entry));
        waitUntil("the images of '" + text + "' are stored", () -> repository.findGeneratedImagesByEntry(entry.getId())
                .size() == entry.getImages().size());
        return entry;
    }

    private static String dollars(double cost) {
        return I18nManager.formatCost(cost);
    }

    // ===== 1. Sending =====

    /**
     * A prompt typed and sent with the button goes to the provider of the chosen model and shows as a new entry with
     * the generated image, stored in the database and the images folder; the header shows its cost, which the send
     * button named before, the status bar the provider, and the prompt is cleared for the next one.
     */
    @Test
    void aSentPromptShowsItsGeneratedImageAndCostAndClearsThePrompt() throws Exception {
        Chat chat = createChat("Lighthouses");
        open(chat);
        chooseGemini();
        assertTrue(sendButton().isDisabled(), "nothing to send without a prompt");
        onFxThread(() -> prompt().setText("a lighthouse at dawn"));
        String price = sendButton().getText();

        ChatEntry entry = send(chat, "a lighthouse at dawn");
        assertEquals(1, generations.get(), "one request for one image");
        assertEquals("a lighthouse at dawn", entry.getUserPrompt());
        assertEquals(List.of(entry), List.copyOf(entryList().getItems()));
        assertEquals("$" + dollars(entry.getCost()), price, "the button named the price of the order");
        assertEquals(I18nManager.getText("chat.total.cost", dollars(entry.getCost())), cost().getText());
        assertEquals("", prompt().getText(), "the prompt is cleared after sending");
        assertTrue(sendButton().isDisabled());
        waitUntil("the status bar names the provider", () -> I18nManager.getText("status.request.submitted.gemini")
                .equals(status().getText()));

        /*  stored: the entry with its image, the file in the images folder  */
        GeneratedImage image = entry.getImages().get(0);
        assertEquals(GeneratedImage.DownloadStatus.CACHED, image.getDownloadStatus());
        assertTrue(new File(image.getLocalFilePath()).exists(), image.getLocalFilePath());
        assertTrue(image.getLocalFilePath().startsWith(config.paths().images().toString()));
        assertEquals("a lighthouse at dawn", repository.findChatEntryById(entry.getId()).orElseThrow()
                .getUserPrompt());
    }

    // ===== 2. The list of the chats =====

    /** The start loaded the chats before the window opened, the window shows them without loading them again. */
    @Test
    void theListShowsTheChatsLoadedAtTheStart() throws Exception {
        assertEquals(1, loads.get(), "the window loads the chats no more");
        assertEquals(chats.chats(), chatList().getItems());
        assertEquals("Welcome to MoodFrame", chatList().getItems().get(0).getName());
    }

    /**
     * Closing the window lets go of what it registered beyond itself: the listeners of the language (its own and those
     * of the cards) and of the theme, and the listener of the library changes.
     */
    @Test
    void aClosedWindowLetsGoOfWhatItRegistered() throws Exception {
        Chat chat = createChat("Before closing");
        open(chat);
        chooseGemini();
        send(chat, "a lighthouse");
        assertTrue(I18nManager.getChangeListenerCount() > languageListeners, "the window and its cards listen");
        assertEquals(themeListeners + 1, ThemeManager.getThemeListenerCount(), "the cloud follows the theme");

        onFxThread(stage::hide);
        assertEquals(languageListeners, I18nManager.getChangeListenerCount());
        assertEquals(themeListeners, ThemeManager.getThemeListenerCount());

        /*  the changes of the library reach the closed window no more  */
        int listed = onFx(() -> chatList().getItems().size());
        fromAnotherThread(() -> chats.createChat("After closing"));
        onFx(() -> null);
        assertEquals(listed, onFx(() -> chatList().getItems().size()));

        /*  work asked of the closed window, whose worker is drained, is dropped instead of refused with an error  */
        onFxThread(() -> {
            InPlaceEditableLabel name = find(chatCell(chat), InPlaceEditableLabel.class);
            name.startEdit();
            find(name, TextField.class).setText("After the window closed");
            name.confirmEdit();
        });
        assertEquals("Before closing", chat.getName());
    }

    /**
     * A new chat comes on top of the list and opens; a chat opens scrolled towards its latest entry; a chat is renamed
     * in place and the header follows, moved with Alt+Down, moved to the trash, where it leaves the list and closes,
     * and comes back from the trash at its place.
     */
    @Test
    void chatsAreCreatedOpenedRenamedMovedTrashedAndRestored() throws Exception {
        Chat full = createChat("Full chat");
        for (int index = 0; index < 6; index++)
            addEntry(full, "entry " + index, FLUX, 100 + index, 1);

        /*  "New chat": on top, opened, stored only with its first entry  */
        Button newChat = (Button) root.lookup("#newChatButton");
        assertFalse(newChat.isDisabled());
        onFxThread(newChat::fire);
        Chat created = chatList().getItems().get(0);
        assertEquals(created, chatList().getSelectionModel().getSelectedItem());
        assertEquals(created.getName(), header().getText());
        assertTrue(entryList().getItems().isEmpty());

        /*  a chat with more entries than fit opens scrolled towards its latest entry (scrollTo of the last one,
            which misjudges cards whose images are not measured yet, so the end itself is not checked)  */
        open(full);
        assertEquals(full.getEntries(), entryList().getItems());
        waitUntil("the chat is scrolled from its start", () -> entryList().lookup(".virtual-flow")
                instanceof VirtualFlow<?> flow && flow.getFirstVisibleCell() != null
                && flow.getFirstVisibleCell().getIndex() > 0);

        /*  renamed in place: list, header and database  */
        onFxThread(() -> {
            InPlaceEditableLabel name = find(chatCell(full), InPlaceEditableLabel.class);
            name.startEdit();
            find(name, TextField.class).setText("Renamed chat");
            name.confirmEdit();
        });
        waitUntil("the rename is stored", () -> java.text.MessageFormat.format(
                I18nManager.getText("status.chat.renamed"), "Renamed chat").equals(status().getText()));
        assertEquals("Renamed chat", full.getName());
        assertEquals("Renamed chat", onFx(() -> find(chatCell(full), InPlaceEditableLabel.class).getText()));
        assertEquals("Renamed chat", header().getText());
        assertEquals("Renamed chat", repository.findChatById(full.getId()).orElseThrow().getName());

        /*  Alt+Down moves the open chat one place down, stored  */
        List<Chat> before = List.copyOf(chatList().getItems());
        int index = before.indexOf(full);
        onFxThread(() -> Event.fireEvent(chatList(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN, false,
                false, true, false)));
        waitUntil("the chat moved one place down", () -> chatList().getItems().indexOf(full) == index + 1);
        assertEquals(full, chatList().getSelectionModel().getSelectedItem(), "and still open");
        Chat below = before.get(index + 1);
        assertTrue(repository.findChatById(full.getId()).orElseThrow().getSortOrder()
                > repository.findChatById(below.getId()).orElseThrow().getSortOrder(), "the order is stored");

        /*  to the trash with the button of its row: it leaves the list, the trash lists it  */
        assertFalse(prompt().isDisabled());
        onFxThread(() -> buttons(chatCell(full), TRASH_ICON).get(0).fire());
        waitUntil("the chat is in the trash", () -> I18nManager.getText("status.chat.moved.trash")
                .equals(status().getText()));
        assertTrue(full.isDeleted());
        assertFalse(chatList().getItems().contains(full));
        assertEquals(List.of("MainWindowWorker", "MainWindowWorker", "MainWindowWorker"), chatWrites,
                "rename, move and trash ran on the worker");
        assertNull(chatList().getSelectionModel().getSelectedItem(), "the open chat closed");
        assertEquals(I18nManager.getText("chat.select"), header().getText());
        Locale language = I18nManager.getCurrentLocale();
        Locale other = "de".equals(language.getLanguage()) ? Locale.ENGLISH : Locale.GERMAN;
        onFxThread(() -> I18nManager.setLocale(other));
        assertEquals(I18nManager.getTextIn(other, "chat.select"), header().getText(), "it asks in the new language");
        onFxThread(() -> I18nManager.setLocale(language));
        assertTrue(prompt().isDisabled(), "no chat takes the prompt");
        assertTrue(trash.list(TrashGroup.Kind.CHATS).stream().map(TrashEntry::id).toList().contains(full.getId()));

        /*  back from the trash, as the trash window does it  */
        fromAnotherThread(() -> assertTrue(trash.restore(TrashGroup.Kind.CHATS, full.getId())));
        waitUntil("the chat is listed again", () -> chatList().getItems().contains(full));
        assertFalse(full.isDeleted());
        assertEquals(6, full.getEntries().size());
        assertEquals(I18nManager.getText("status.chat.restored"), status().getText());
        open(full);
        assertEquals(6, entryList().getItems().size());
        assertFalse(prompt().isDisabled(), "the restored chat takes prompts again");
    }

    // ===== 3. The list by mood and by frame =====

    /**
     * The sidebar lists the moods and the frames images were generated with; a new entry counts in its group once the
     * library reports it, an open group shows its entries from all chats and takes a new entry of its own at once;
     * the link of an entry of a group leads back to its chat.
     */
    @Test
    void theSidebarGroupsByMoodAndFrameAndCountsANewEntry() throws Exception {
        Chat chat = createChat("Grouped");
        open(chat);
        chooseGemini();
        Mood mood = moods.getDefaults().get(0);
        Frame frame = frames.getDefaults().get(0);
        onFxThread(() -> {
            controller.moodFramePanel().chooseMood(mood);
            controller.moodFramePanel().chooseFrame(frame);
        });
        send(chat, "a quiet harbour");

        /*  by mood: the group of the mood with its image  */
        onFxThread(() -> sidebarMode("byMoodMenuItem").setSelected(true));
        assertTrue(moodGroups().isVisible());
        assertFalse(chatList().isVisible());
        assertEquals(I18nManager.getText("sidebar.moods"), ((Label) root.lookup("#sidebarTitleLabel")).getText());
        waitUntil("the mood is listed with one image", () -> moodGroup(mood) != null
                && moodGroup(mood).imageCount() == 1);

        /*  open: its entries in front of the chat, a new one counts and shows at once  */
        String name = MoodGroupListCell.nameOf(moodGroup(mood));
        List<ChatEntry> ofTheChat = onFx(() -> entryList().getItems());
        onFxThread(() -> moodGroups().getSelectionModel().select(moodGroup(mood)));
        assertEquals(name, header().getText());
        waitUntil("the group shows its entries", () -> entryList().getItems() != ofTheChat);
        assertEquals(1, entryList().getItems().size());
        assertEquals(I18nManager.getText("sidebar.group.target", chat.getName()), cost().getText());
        ChatEntry second = send(chat, "a quiet harbour at night");
        assertTrue(entryList().getItems().contains(second), "the open group shows the new entry");

        /*  the end of the generation refreshes the count once its image is stored, without another change  */
        waitUntil("the mood counts two images", () -> moodGroup(mood).imageCount() == 2);
        assertEquals(name, header().getText(), "the group stays open");

        /*  another caller renames the chat that takes the new prompts: the open group names it anew  */
        fromAnotherThread(() -> chats.renameChat(chat, "Grouped harbour"));
        waitUntil("the group names the renamed chat", () -> I18nManager.getText("sidebar.group.target",
                "Grouped harbour").equals(cost().getText()));
        assertEquals(name, header().getText(), "the group stays open");

        /*  by frame: the group of the frame  */
        onFxThread(() -> sidebarMode("byFrameMenuItem").setSelected(true));
        assertTrue(frameGroups().isVisible());
        assertEquals(chat.getName(), header().getText(), "the chat shows again");
        waitUntil("the frame is listed with two images", () -> frameGroups().getItems().stream()
                .anyMatch((group) -> frame.getId().equals(group.frameId()) && group.imageCount() == 2));

        /*  the link of an entry of an open group leads to its chat  */
        List<ChatEntry> shownBefore = onFx(() -> entryList().getItems());
        onFxThread(() -> frameGroups().getSelectionModel().select(frameGroups().getItems().stream()
                .filter((group) -> frame.getId().equals(group.frameId())).findFirst().orElseThrow()));
        waitUntil("the group shows its entries", () -> entryList().getItems() != shownBefore);
        assertEquals(2, entryList().getItems().size());
        onFxThread(() -> find(card(second), Hyperlink.class).fire());
        assertTrue(chatList().isVisible(), "the list of the chats shows again");
        assertEquals(chat, chatList().getSelectionModel().getSelectedItem());
        assertEquals(chat.getName(), header().getText());
        assertEquals(List.of(chat.getEntries().get(0), second), List.copyOf(entryList().getItems()));
    }

    private MoodGroup moodGroup(Mood mood) {
        return moodGroups().getItems().stream().filter((group) -> mood.getId().equals(group.moodId())).findFirst()
                .orElse(null);
    }

    // ===== 4. The actions of an entry =====

    /**
     * Deleting an image and then an entry, each confirmed, runs on the worker of the window: the card of the image
     * shows one image less, the deleted entry leaves the list, and the header books each deleted cost exactly once,
     * as the database does. Back from the trash, image and entry show again with their status and the cost.
     */
    @Test
    void deletingAnImageAndAnEntryBooksTheirCostOnce() throws Exception {
        Chat chat = createChat("Deletions");
        ChatEntry two = addEntry(chat, "two images", FLUX, 7, 2);
        ChatEntry one = addEntry(chat, "one image", FLUX, 9, 1);
        open(chat);
        waitUntil("both cards show their images", () -> showsItsImages(card(two), two)
                && showsItsImages(card(one), one));
        assertEquals(I18nManager.getText("chat.total.cost", dollars(0.30)), cost().getText());

        /*  the second image of the first entry  */
        GeneratedImage image = two.getImages().get(1);
        onFxThread(() -> {
            confirmTheNextDialog();
            buttons(card(two), TRASH_ICON).get(1).fire();
        });
        waitUntil("the image is gone", () -> two.getImages().size() == 1 && showsItsImages(card(two), two));
        assertFalse(two.getImages().contains(image));
        assertEquals(List.of(two, one), List.copyOf(entryList().getItems()));
        assertEquals(I18nManager.getText("chat.total.cost.with.deleted", dollars(0.30), dollars(0.10)),
                cost().getText());

        /*  the second entry, from the menu of its card  */
        onFxThread(() -> {
            MenuButton more = (MenuButton) card(one).lookupAll(".menu-button").iterator().next();
            MenuItem delete = more.getItems().stream()
                    .filter((item) -> I18nManager.getText("entry.delete").equals(item.getText())).findFirst()
                    .orElseThrow();
            confirmTheNextDialog();
            delete.fire();
        });
        waitUntil("the entry leaves the list", () -> !entryList().getItems().contains(one));
        assertEquals(List.of(two), List.copyOf(entryList().getItems()));
        assertEquals(List.of(two), List.copyOf(chat.getEntries()));
        assertEquals(I18nManager.getText("chat.total.cost.with.deleted", dollars(0.30), dollars(0.20)),
                cost().getText());

        /*  the database books the same  */
        assertEquals(0.10, repository.findChatById(chat.getId()).orElseThrow().getDeletedCost(), 1e-9);
        assertEquals(0.10, two.getDeletedCost(), 1e-9);
        assertEquals(List.of(two.getId()), repository.findChatEntriesByChatId(chat.getId()).stream()
                .map(ChatEntry::getId).toList());
        assertEquals(List.of("MainWindowWorker", "MainWindowWorker"), deletions, "both ran on the worker");

        /*  back from the trash: the image, then the entry, each with its status and cost  */
        fromAnotherThread(() -> assertTrue(trash.restore(TrashGroup.Kind.IMAGES, image.getId())));
        waitUntil("the image is back", () -> showsItsImages(card(two), two) && two.getImages().size() == 2);
        waitUntil("the status tells the image is back",
                () -> I18nManager.getText("status.image.restored").equals(status().getText()));
        assertEquals(I18nManager.getText("chat.total.cost.with.deleted", dollars(0.30), dollars(0.10)),
                cost().getText());
        fromAnotherThread(() -> assertTrue(trash.restore(TrashGroup.Kind.IMAGES, one.getId())));
        waitUntil("the entry is back", () -> entryList().getItems().size() == 2);
        assertEquals(List.of(two.getId(), one.getId()), entryList().getItems().stream().map(ChatEntry::getId)
                .toList());
        waitUntil("the status tells the entry is back",
                () -> I18nManager.getText("status.entry.restored").equals(status().getText()));
        assertEquals(I18nManager.getText("chat.total.cost", dollars(0.30)), cost().getText());
    }

    /**
     * "Reuse" of a card takes over prompt, model, resolution, frame and mood of its entry after they were changed.
     */
    @Test
    void reusingAnEntryTakesOverPromptModelResolutionFrameAndMood() throws Exception {
        Chat chat = createChat("Reuse");
        open(chat);
        chooseGemini();
        Mood mood = moods.getDefaults().get(0);
        Frame frame = frames.getDefaults().stream().filter((candidate) -> candidate.getResolution() != null)
                .findFirst().orElseThrow();
        onFxThread(() -> {
            controller.moodFramePanel().chooseMood(mood);
            controller.moodFramePanel().chooseFrame(frame);
        });
        String model = controller.generationPanel().modelItem();
        String resolution = controller.moodFramePanel().resolution();
        String aspectRatio = controller.moodFramePanel().aspectRatio();
        ChatEntry entry = send(chat, "a red kite");

        /*  everything else chosen meanwhile  */
        Mood otherMood = moods.getDefaults().get(1);
        Frame otherFrame = frames.getDefaults().stream().filter((candidate) -> !candidate.getAspectRatio()
                .equals(frame.getAspectRatio())).findFirst().orElseThrow();
        onFxThread(() -> {
            controller.moodFramePanel().chooseMood(otherMood);
            controller.moodFramePanel().chooseFrame(otherFrame);
            controller.generationPanel().modelFamilyChoice().setValue(FLUX.getFamilyName());
        });
        assertNotEquals(model, controller.generationPanel().modelItem());
        assertNotEquals(mood.getId(), controller.moodFramePanel().chosenMood().getId());
        assertNotEquals(aspectRatio, controller.moodFramePanel().aspectRatio());

        onFxThread(() -> buttons(card(entry), "mdi2c-content-copy").get(0).fire());
        assertEquals("a red kite", prompt().getText());
        assertEquals(model, controller.generationPanel().modelItem());
        assertEquals(resolution, controller.moodFramePanel().resolution());
        assertEquals(aspectRatio, controller.moodFramePanel().aspectRatio());
        assertEquals(frame.getId(), controller.moodFramePanel().chosenFrame().getId());
        assertEquals(mood.getId(), controller.moodFramePanel().chosenMood().getId());
    }

    /**
     * The menu of an image of BFL offers "Copy seed", which applies only while a model of BFL is chosen and then sets
     * the seed; an image of Gemini offers none.
     */
    @Test
    void copySeedIsOfferedForImagesOfBflAndAppliesWithAModelOfBfl() throws Exception {
        Chat chat = createChat("Seeds");
        open(chat);
        chooseGemini();
        ChatEntry flux = addEntry(chat, "a fox", FLUX, 4242, 1);
        ChatEntry nano = send(chat, "a hare");
        waitUntil("the FLUX card shows its image", () -> showsItsImages(card(flux), flux));

        String copySeed = I18nManager.getText("tooltip.copy.seed");
        MenuItem item = imageMenuItem(flux, copySeed);
        assertNotNull(item, "a FLUX image offers its seed");
        assertTrue(item.isDisable(), "but not while a Gemini model is chosen");
        assertNull(imageMenuItem(nano, copySeed), "a Gemini image has no seed");

        onFxThread(() -> controller.generationPanel().modelFamilyChoice().setValue(FLUX.getFamilyName()));
        MenuItem enabled = imageMenuItem(flux, copySeed);
        assertFalse(enabled.isDisable(), "a model of BFL takes the seed");
        onFxThread(enabled::fire);
        assertEquals("4242", ((TextField) root.lookup("#seedTextField")).getText());
    }

    /**
     * A generation changes only the card of its entry, and that one in place: the card of another entry stays, as
     * does the card of the entry when its generation reports a change, which shows the cost it does not follow itself.
     */
    @Test
    void aGenerationBuildsNoCardAnew() throws Exception {
        Chat chat = createChat("Cards");
        open(chat);
        chooseGemini();
        ChatEntry other = addEntry(chat, "a fox", FLUX, 1, 1);
        waitUntil("the card shows its image", () -> showsItsImages(card(other), other));
        ChatEntryCard otherCard = card(other);

        ChatEntry sent = send(chat, "a lynx");
        assertSame(otherCard, card(other), "the generation leaves the card of another entry alone");

        ChatEntryCard sentCard = card(sent);
        GeneratedImage image = sent.getImages().get(0);
        onFxThread(() -> {
            ModelLock.change(() -> sent.setCost(1.23));
            controller.chatView().entryUpdated(sent);
            controller.chatView().imageUpdated(image);
        });
        assertSame(sentCard, card(sent), "the card of the entry stays");
        assertSame(otherCard, card(other));
        assertTrue(sentCard.lookupAll(".label").stream().anyMatch((node) -> ((Label) node).getText() != null
                && ((Label) node).getText().contains("$" + dollars(1.23))), "the card shows the new cost");
        waitUntil("the tile of the changed image is built anew", () -> showsItsImages(sentCard, sent));
    }

    /** Opens the menu of the first image of an entry and finds an item of it; the menu closes again. */
    private MenuItem imageMenuItem(ChatEntry entry, String text) throws Exception {
        MenuItem[] found = new MenuItem[1];
        onFxThread(() -> {
            ImageView view = card(entry).lookupAll(".image-view").stream().map(ImageView.class::cast)
                    .filter((candidate) -> candidate.getOnContextMenuRequested() != null).findFirst().orElseThrow();
            Event.fireEvent(view, new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 1, 1, 100, 100,
                    false, null));
            for (Window window : List.copyOf(Window.getWindows()))
                if (window instanceof ContextMenu menu && menu.isShowing()) {
                    found[0] = menu.getItems().stream().filter((item) -> text.equals(item.getText())).findFirst()
                            .orElse(null);
                    menu.hide();
                }
        });
        return found[0];
    }

    // ===== 5. The full view =====

    /**
     * The eye of an image opens the full view on it; the arrows page through the images of the entry and up and down
     * through the entries with images; Escape closes it.
     */
    @Test
    void theFullViewPagesThroughImagesAndEntriesAndCloses() throws Exception {
        Chat chat = createChat("Gallery");
        ChatEntry first = addEntry(chat, "two images", FLUX, 1, 2);
        ChatEntry second = addEntry(chat, "one image", FLUX, 5, 1);
        open(chat);
        waitUntil("the cards show their images", () -> showsItsImages(card(first), first)
                && showsItsImages(card(second), second));

        onFxThread(() -> buttons(card(first), "mdi2e-eye").get(0).fire());
        assertTrue(overlay().isVisible());
        assertEquals(first.getImages().get(0).getLocalFilePath(), shownImage());

        press(KeyCode.RIGHT);
        assertEquals(first.getImages().get(1).getLocalFilePath(), shownImage());
        press(KeyCode.LEFT);
        assertEquals(first.getImages().get(0).getLocalFilePath(), shownImage());
        press(KeyCode.DOWN);
        assertEquals(second.getImages().get(0).getLocalFilePath(), shownImage(), "the next entry with images");
        press(KeyCode.UP);
        assertEquals(first.getImages().get(0).getLocalFilePath(), shownImage(), "the previous entry with images");

        press(KeyCode.ESCAPE);
        assertFalse(overlay().isVisible());
    }

    private void press(KeyCode key) throws Exception {
        onFxThread(() -> overlay().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, false, false, false,
                false)));
    }

    /** The file of the image the full view shows. */
    private String shownImage() {
        return overlay().lookupAll(".image-view").stream().map(ImageView.class::cast)
                .filter((view) -> view.isVisible() && view.getImage() != null && view.getImage().getUrl() != null)
                .map((view) -> new File(URI.create(view.getImage().getUrl())).getPath())
                .filter((path) -> path.startsWith(config.paths().images().toString())).findFirst().orElse(null);
    }

    // ===== 6. Attachments =====

    /**
     * An image dropped on the square of the attachments (the call of its drop handler) shows in the group Generation,
     * which then offers only the models that take it; a second image pasted from the clipboard goes into a new slot
     * and narrows the models to those taking two images; removing all brings every model back.
     */
    @Test
    void attachedImagesShowAndFilterTheModelsByTheirCount() throws Exception {
        Chat chat = createChat("Attachments");
        open(chat);
        List<String> all = List.copyOf(controller.generationPanel().modelChoice().getItems());
        MultiImageAttachmentPanel panel = controller.generationPanel().attachmentPanel();
        File first = image("first.png");
        File second = image("second.png");

        onFxThread(() -> panel.attachImageToSlot(0, first));
        waitUntil("the image is attached", () -> panel.getAttachmentCount() == 1
                && controller.generationPanel().hasAttachment());
        assertEquals(first, controller.generationPanel().attachedImage());
        waitUntil("the models take an image", () -> List.copyOf(controller.generationPanel().modelChoice()
                .getItems()).equals(models((model) -> model.getMaxInputImages() >= 1)));

        /*  a second one pasted: the paste button follows the clipboard, the image goes into a new slot  */
        onFxThread(() -> {
            ClipboardContent content = new ClipboardContent();
            content.putImage(new Image(second.toURI().toString()));
            Clipboard.getSystemClipboard().setContent(content);
        });
        Button paste = buttons(panel, "mdi2c-clipboard-arrow-down-outline").get(0);
        waitUntil("the clipboard can be pasted", () -> !paste.isDisabled());
        onFxThread(paste::fire);
        waitUntil("two images are attached", () -> panel.getAttachmentCount() == 2);
        onFxThread(() -> Clipboard.getSystemClipboard().clear());
        waitUntil("the models take two images", () -> List.copyOf(controller.generationPanel().modelChoice()
                .getItems()).equals(models((model) -> model.getMaxInputImages() >= 2)));
        assertTrue(GenerationModel.fromLocalizedDisplayName(controller.generationPanel().modelItem())
                .getMaxInputImages() >= 2);

        onFxThread(panel::clearAllAttachments);
        waitUntil("every model is back", () -> !controller.generationPanel().hasAttachment()
                && List.copyOf(controller.generationPanel().modelChoice().getItems()).equals(all));
    }

    /*  the selectable models of BFL and Gemini that pass a test, as listed  */
    private static List<String> models(Predicate<GenerationModel> test) {
        return GenerationModel.selectable().stream()
                .filter((model) -> model.getProvider() == ApiProvider.BFL
                        || model.getProvider() == ApiProvider.GOOGLE_GEMINI)
                .filter(test).map(ModelLabels::displayName).toList();
    }

    /**
     * The edit pencil attaches through the panel of the attachments: a file the panel cannot read leaves nothing
     * attached, a readable one is the attached image once the panel holds it.
     */
    @Test
    void anEditedImageIsAttachedOnlyOnceThePanelHoldsIt() throws Exception {
        Chat chat = createChat("Edit");
        open(chat);
        MultiImageAttachmentPanel panel = controller.generationPanel().attachmentPanel();
        Path broken = home.resolve("broken.png");
        Files.writeString(broken, "no image");
        String editing = I18nManager.getText("status.image.edit.attached");

        onFxThread(() -> controller.editImage(broken.toString()));
        assertNull(controller.generationPanel().attachedImage(), "nothing is attached before the panel holds it");
        waitUntil("the failure shows", () -> !editing.equals(status().getText()));
        assertEquals(0, panel.getAttachmentCount());
        assertFalse(controller.generationPanel().hasAttachment(), "the unreadable file is not attached");

        File image = image("edited.png");
        onFxThread(() -> controller.editImage(image.getPath()));
        waitUntil("the image is attached", () -> panel.getAttachmentCount() == 1
                && controller.generationPanel().hasAttachment());
        assertEquals(image, controller.generationPanel().attachedImage());
        onFxThread(panel::clearAllAttachments);
    }

    private File image(String name) throws Exception {
        Path file = home.resolve(name);
        Files.write(file, Base64.getDecoder().decode(testImage));
        return file.toFile();
    }

    // ===== 7. Changes of other callers =====

    /**
     * What another caller (the MCP server) changes in the library on its own thread shows in the window: a new chat
     * in the list, a new entry in the open chat with its cost, a new name in the header, a deleted entry leaves.
     */
    @Test
    void changesOfAnotherCallerShowInTheWindow() throws Exception {
        Chat chat = createChat("Shared");
        open(chat);

        ChatEntry entry = addEntry(chat, "made elsewhere", FLUX, 3, 1);
        waitUntil("the entry shows", () -> showsItsImages(card(entry), entry));
        assertEquals(I18nManager.getText("chat.total.cost", dollars(0.10)), cost().getText());

        fromAnotherThread(() -> chats.renameChat(chat, "Renamed elsewhere"));
        waitUntil("the header shows the new name", () -> "Renamed elsewhere".equals(header().getText()));

        Chat other = createChat("Made elsewhere");
        assertEquals(other, chatList().getItems().get(0), "on top of the list");
        assertEquals(chat, chatList().getSelectionModel().getSelectedItem(), "the open chat stays open");

        fromAnotherThread(() -> chats.deleteEntry(entry));
        waitUntil("the entry leaves", () -> entryList().getItems().isEmpty());
        assertEquals(I18nManager.getText("chat.total.cost.with.deleted", dollars(0.10), dollars(0.10)),
                cost().getText());
    }

    // ===== 8. The language =====

    /** A change of the language relabels every part of the window. */
    @Test
    void everyPartFollowsTheLanguage() throws Exception {
        Map<String, Callable<String>> samples = new LinkedHashMap<>();
        samples.put("main.info.tooltip", () -> ((Button) root.lookup("#infoButton")).getTooltip().getText());
        samples.put("sidebar.by.mood", () -> sidebarMode("byMoodMenuItem").getText());
        samples.put("app.welcome", () -> ((Label) root.lookup("#welcomeLabel")).getText());
        samples.put("input.send.tooltip", () -> sendButton().getTooltip().getText());
        samples.put("params.aspect.ratio", () -> ((Label) root.lookup("#aspectRatioLabel")).getText());
        samples.put("params.generation.title", () -> ((Label) root.lookup("#generationTitleLabel")).getText());
        samples.put("status.ready", () -> ((Label) root.lookup("#statusLabel")).getText());
        for (Map.Entry<String, Callable<String>> sample : samples.entrySet())
            assertEquals(I18nManager.getText(sample.getKey()), onFx(sample.getValue()), sample.getKey());

        Map<String, String> english = new HashMap<>();
        for (String key : samples.keySet())
            english.put(key, I18nManager.getText(key));
        onFxThread(() -> I18nManager.setLocale(Locale.GERMAN));
        for (Map.Entry<String, Callable<String>> sample : samples.entrySet()) {
            String german = onFx(sample.getValue());
            assertEquals(I18nManager.getText(sample.getKey()), german, sample.getKey());
            assertNotEquals(english.get(sample.getKey()), german, sample.getKey() + " is German now");
        }
    }

    // ===== 9. The brainstorm =====

    /**
     * The brainstorm button needs an account with a language model and web search; it opens the brainstorm, whose
     * "Adopt" hands the prompt and the model to the window and closes it; nothing is sent to the AI.
     */
    @Test
    void theBrainstormOpensAndAdoptsItsResult() throws Exception {
        Chat chat = createChat("Ideas");
        open(chat);
        assertTrue(brainstormButton().isDisabled(), "no account with a language model and web search");

        config.accounts().setValidated(ApiProvider.GOOGLE_GEMINI, true);
        config.accounts().setChatOk(ApiProvider.GOOGLE_GEMINI, true);
        config.accounts().setWebSearch(ApiProvider.GOOGLE_GEMINI, true);
        onFxThread(controller::refreshApiKeyStatus);
        assertFalse(brainstormButton().isDisabled());
        String model = controller.generationPanel().modelItem();

        onFxThread(() -> brainstormButton().fire());
        BrainstormDialog dialog = onFx(() -> Window.getWindows().stream()
                .filter((window) -> window instanceof BrainstormDialog && window.isShowing())
                .map(BrainstormDialog.class::cast).findFirst().orElse(null));
        assertNotNull(dialog, "the brainstorm is open");

        onFxThread(() -> {
            Button adopt = dialog.getScene().getRoot().lookupAll(".button").stream().map(Button.class::cast)
                    .filter((button) -> I18nManager.getText("brainstorm.adopt").equals(button.getText()))
                    .findFirst().orElseThrow();
            TextArea result = adopt.getParent().lookupAll(".text-area").stream().map(TextArea.class::cast)
                    .findFirst().orElseThrow();
            result.setText("a lighthouse in a storm, wide angle");
            adopt.fire();
        });
        assertFalse(dialog.isShowing(), "adopting closes the brainstorm");
        assertEquals("a lighthouse in a storm, wide angle", prompt().getText());
        assertEquals(model, controller.generationPanel().modelItem());
        assertEquals(0, generations.get(), "nothing went to a provider");
    }
}
