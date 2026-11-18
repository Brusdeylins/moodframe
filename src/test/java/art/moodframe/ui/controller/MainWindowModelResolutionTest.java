package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.database.DatabaseConfig;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryFrame;
import art.moodframe.model.GenerationModel;
import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.FavoriteService;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.ui.component.ChatEntryCard;
import art.moodframe.ui.component.MultiImageAttachmentPanel;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * The model of the main window follows the target resolution of the frame, on a throwaway {@code user.home} with the
 * standard library and without showing the window: choosing the family, reusing an entry and the hint below the
 * resolution, which appears only while the family cannot make the target.
 */
class MainWindowModelResolutionTest {

    private static final String NANO_BANANA_PRO_2K = GenerationModel.GEMINI_PRO_2K.getLocalizedDisplayName();
    private static final String NANO_BANANA_PRO_4K = GenerationModel.GEMINI_PRO_4K.getLocalizedDisplayName();

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
        realConfig = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        locale = I18nManager.getCurrentLocale();

        /*  the standard library in the throwaway home, with the services of the panel as the application has them  */
        DatabaseConfig.initialize(home.resolve("db").resolve("moodframe.db").toString());
        LibraryEvents events = new LibraryEvents();
        PromptBlockService blocks = new PromptBlockService(events);
        ColorStyleService colors = new ColorStyleService(events);
        register(LibraryEvents.class, events);
        register(PromptBlockService.class, blocks);
        register(ColorStyleService.class, colors);
        register(MoodService.class, new MoodService(blocks, colors, events));
        register(FrameService.class, new FrameService(events));
        register(FavoriteService.class, new FavoriteService(events));
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

        /*  a configuration without file and with the keys of BFL and Gemini, so that every model can be chosen  */
        ApplicationConfig config = mock(ApplicationConfig.class, CALLS_REAL_METHODS);
        Field properties = ApplicationConfig.class.getDeclaredField("properties");
        properties.setAccessible(true);
        properties.set(config, new Properties());
        doNothing().when(config).saveConfiguration();
        doReturn(true).when(config).hasApiKey();
        doReturn(true).when(config).hasGeminiApiKey();
        doReturn(true).when(config).isBflProviderEnabled();
        doReturn(true).when(config).isGeminiProviderEnabled();
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
        onFxThread(() -> ((MultiImageAttachmentPanel) ((VBox) field("multiImageAttachmentContainer"))
                .getChildren().get(0)).dispose());
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

    /*  a field of the window, which a lookup does not reach in the panel before it is shown  */
    private static Object field(String id) throws Exception {
        Field field = MainWindowController.class.getDeclaredField(id);
        field.setAccessible(true);
        return field.get(controller);
    }

    @SuppressWarnings("unchecked")
    private static ChoiceBox<String> choice(String id) throws Exception {
        return (ChoiceBox<String>) field(id);
    }

    private static Label hint() throws Exception {
        return (Label) field("resolutionHintLabel");
    }

    private static void reuse(ChatEntry entry) throws Exception {
        onFxThread(() -> {
            Method reuse = MainWindowController.class.getDeclaredMethod("reuseEntry", ChatEntry.class,
                    ChatEntryCard.Reuse.class);
            reuse.setAccessible(true);
            reuse.invoke(controller, entry, ChatEntryCard.Reuse.ALL);
        });
    }

    @Test
    void theFamilyMakesTheTargetOfTheFrame() throws Exception {
        onFxThread(() -> {
            choice("resolutionChoiceBox").setValue("4K");
            choice("modelFamilyChoiceBox").setValue("Nano Banana Pro");
        });
        assertEquals(NANO_BANANA_PRO_4K, choice("modelComboBox").getValue());
        assertFalse(hint().isVisible(), hint().getText());

        String lite = GenerationModel.NANO_BANANA2_LITE.getFamilyName();
        onFxThread(() -> choice("modelFamilyChoiceBox").setValue(lite));
        assertTrue(hint().isVisible(), "a family that cannot make the target says so");
        assertEquals(lite + " → 1K", hint().getText());
    }

    @Test
    void aReusedEntryTakesTheModelForTheTargetItWasMadeFor() throws Exception {
        onFxThread(() -> choice("resolutionChoiceBox").setValue("4K"));

        /*  made for 4K with the 2K variant, as before this fix: the target stays, the model follows it  */
        reuse(new ChatEntry.Builder().chatId("chat").userPrompt("a lighthouse")
                .model(GenerationModel.GEMINI_PRO_2K.getDisplayName())
                .frame(new EntryFrame(null, null, "16:9", "4K")).build());
        assertEquals("4K", choice("resolutionChoiceBox").getValue());
        assertEquals(NANO_BANANA_PRO_4K, choice("modelComboBox").getValue());
        assertFalse(hint().isVisible(), hint().getText());

        /*  from before the frames were copied: the target becomes the resolution of its model  */
        reuse(new ChatEntry.Builder().chatId("chat").userPrompt("a lighthouse")
                .model(GenerationModel.GEMINI_PRO_2K.getDisplayName()).size("16:9").build());
        assertEquals("2K", choice("resolutionChoiceBox").getValue());
        assertEquals(NANO_BANANA_PRO_2K, choice("modelComboBox").getValue());
        assertFalse(hint().isVisible(), hint().getText());
    }
}
