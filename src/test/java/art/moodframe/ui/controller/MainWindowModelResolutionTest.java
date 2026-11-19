package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ConfigMocks;
import art.moodframe.config.ServiceFactory;
import art.moodframe.database.TestDatabase;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryFrame;
import art.moodframe.model.GenerationModel;
import art.moodframe.service.brainstorm.BrainstormService;
import art.moodframe.service.brainstorm.MoodAnalysisService;
import art.moodframe.service.chat.ChatHistoryService;
import art.moodframe.service.chat.ChatLibrary;
import art.moodframe.service.chat.ImageAttachmentService;
import art.moodframe.service.chat.ImageSizeCheck;
import art.moodframe.service.chat.ThumbnailManager;
import art.moodframe.service.generation.GenerationService;
import art.moodframe.service.generation.ModelLabels;
import art.moodframe.service.generation.RequestResponseManager;
import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.FavoriteService;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.library.TestLibraryServices;
import art.moodframe.service.provider.MockProviders;
import art.moodframe.service.trash.TrashCenter;
import art.moodframe.ui.component.ChatEntryCard;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The model of the main window follows the target resolution of the frame, on a throwaway {@code user.home} with the
 * standard library and without showing the window: choosing the family, reusing an entry and the hint below the
 * resolution, which appears only while the family cannot make the target.
 */
class MainWindowModelResolutionTest {

    private static final String NANO_BANANA_PRO_2K = ModelLabels.displayName(GenerationModel.GEMINI_PRO_2K);
    private static final String NANO_BANANA_PRO_4K = ModelLabels.displayName(GenerationModel.GEMINI_PRO_4K);

    @TempDir
    static java.nio.file.Path home;

    private static String realHome;
    private static Locale locale;
    private static ApplicationConfig realConfig;
    private static final Map<Class<?>, Object> realServices = new HashMap<>();
    private static MainWindowController controller;

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

        /*  the standard library in the throwaway home, with the services of the panel as the application has them  */
        TestDatabase.open(home.resolve("db").resolve("moodframe.db").toString());
        LibraryEvents events = new LibraryEvents();
        PromptBlockService blocks = TestLibraryServices.blocks(events);
        ColorStyleService colors = TestLibraryServices.palettes(events);
        register(LibraryEvents.class, events);
        register(PromptBlockService.class, blocks);
        register(ColorStyleService.class, colors);
        register(MoodService.class, TestLibraryServices.moods(blocks, colors, events));
        register(FrameService.class, TestLibraryServices.frames(events));
        register(FavoriteService.class, TestLibraryServices.favorites(events));
        ChatLibrary chats = new ChatLibrary(null, events);
        chats.load();
        register(ChatLibrary.class, chats);

        /*  tells the prompt limits of the models, as the providers know them  */
        register(GenerationService.class, new GenerationService(null, chats, null, null, MockProviders.preparing()));
        register(ImageAttachmentService.class, new ImageAttachmentService());

        /*  the rest the window looks up, which this test does not use  */
        register(ChatHistoryService.class, mock(ChatHistoryService.class));
        register(TrashCenter.class, new TrashCenter());
        register(ThumbnailManager.class, mock(ThumbnailManager.class));
        register(ImageSizeCheck.class, mock(ImageSizeCheck.class));
        register(BrainstormService.Factory.class, mock(BrainstormService.Factory.class));
        register(MoodAnalysisService.class, mock(MoodAnalysisService.class));
        register(RequestResponseManager.class, mock(RequestResponseManager.class));
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

    private static <T> void register(Class<T> type, T service) throws Exception {
        realServices.put(type, services().get(type));
        ServiceFactory.getInstance().registerService(type, service);
    }

    /** Loads a new main window as the application does on its start, in English, with every model to choose. */
    @BeforeEach
    void load() throws Exception {

        /*  a fresh configuration with the keys of BFL and Gemini, so that every model can be chosen  */
        ApplicationConfig config = ConfigMocks.withBflAndGemini();
        ServiceFactory.getInstance().registerService(ApplicationConfig.class, config);

        I18nManager.setLocale(Locale.ENGLISH);
        onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(MainWindowModelResolutionTest.class.getResource(
                    "/fxml/MainWindow.fxml"));
            loader.load();
            controller = loader.getController();
        });
    }

    @AfterEach
    void dispose() throws Exception {
        onFxThread(() -> controller.generationPanel().dispose());
        I18nManager.removeChangeListener(controller);
    }

    /*  a step on the JavaFX thread  */
    private interface Step {
        void run() throws Exception;
    }

    /** Runs a step on the JavaFX thread and waits for it and for what it left for later. */
    private static void onFxThread(Step step) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                step.run();
                done.complete(null);
            }
            catch (Throwable failure) {
                done.completeExceptionally(failure);
            }
        });
        done.get(20, TimeUnit.SECONDS);
        CountDownLatch later = new CountDownLatch(1);
        Platform.runLater(() -> Platform.runLater(later::countDown));
        assertTrue(later.await(10, TimeUnit.SECONDS));
    }

    /*  the fields of the window, which a lookup does not reach in the panel before it is shown  */
    private static ChoiceBox<String> resolution() {
        return controller.moodFramePanel().resolutionChoice();
    }

    private static ChoiceBox<String> family() {
        return controller.generationPanel().modelFamilyChoice();
    }

    private static ChoiceBox<String> model() {
        return controller.generationPanel().modelChoice();
    }

    private static Label hint() {
        return controller.moodFramePanel().resolutionHint();
    }

    private static void reuse(ChatEntry entry) throws Exception {
        onFxThread(() -> controller.reuseEntry(entry, ChatEntryCard.Reuse.ALL));
    }

    @Test
    void theFamilyMakesTheTargetOfTheFrame() throws Exception {
        onFxThread(() -> {
            resolution().setValue("4K");
            family().setValue("Nano Banana Pro");
        });
        assertEquals(NANO_BANANA_PRO_4K, model().getValue());
        assertFalse(hint().isVisible(), hint().getText());

        String lite = GenerationModel.NANO_BANANA2_LITE.getFamilyName();
        onFxThread(() -> family().setValue(lite));
        assertTrue(hint().isVisible(), "a family that cannot make the target says so");
        assertEquals(lite + " → 1K", hint().getText());
    }

    @Test
    void aReusedEntryTakesTheModelForTheTargetItWasMadeFor() throws Exception {
        onFxThread(() -> resolution().setValue("4K"));

        /*  made for 4K with the 2K variant, as before this fix: the target stays, the model follows it  */
        reuse(new ChatEntry.Builder().chatId("chat").userPrompt("a lighthouse")
                .model(GenerationModel.GEMINI_PRO_2K.getDisplayName())
                .frame(new EntryFrame(null, null, "16:9", "4K")).build());
        assertEquals("4K", resolution().getValue());
        assertEquals(NANO_BANANA_PRO_4K, model().getValue());
        assertFalse(hint().isVisible(), hint().getText());

        /*  from before the frames were copied: the target becomes the resolution of its model  */
        reuse(new ChatEntry.Builder().chatId("chat").userPrompt("a lighthouse")
                .model(GenerationModel.GEMINI_PRO_2K.getDisplayName()).size("16:9").build());
        assertEquals("2K", resolution().getValue());
        assertEquals(NANO_BANANA_PRO_2K, model().getValue());
        assertFalse(hint().isVisible(), hint().getText());
    }
}
