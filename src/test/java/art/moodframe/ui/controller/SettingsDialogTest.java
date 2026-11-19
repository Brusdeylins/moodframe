package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ConfigMocks;
import art.moodframe.config.LibraryPaths;
import art.moodframe.database.DatabaseConfig;
import art.moodframe.database.DatabaseManager;
import art.moodframe.mcp.McpService;
import art.moodframe.mcp.McpTools;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.Mood;
import art.moodframe.service.ConfigurationExporter;
import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.provider.BflApiService;
import art.moodframe.service.provider.BflApiServiceImpl;
import art.moodframe.service.provider.GeminiApiService;
import art.moodframe.service.provider.GeminiApiServiceImpl;
import art.moodframe.service.provider.OpenAiApiService;
import art.moodframe.service.provider.OpenAiApiServiceImpl;
import art.moodframe.service.provider.ProviderRegistry;
import art.moodframe.ui.controller.settings.SettingsPrompts;
import art.moodframe.ui.controller.settings.SettingsServices;
import art.moodframe.util.I18nManager;
import atlantafx.base.theme.NordLight;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Opens the settings dialog off-screen on a fresh library and clicks through its sections: the providers are the
 * real clients with their connection tests answered, so no request leaves the machine.
 */
class SettingsDialogTest {

    private static final String DEFAULT_OPENAI_URL = ApplicationConfig.OPENAI_DEFAULT_BASE_URL;

    @TempDir
    Path folder;

    private ApplicationConfig config;
    private BflApiService bfl;
    private GeminiApiService gemini;
    private OpenAiApiService openAi;
    private McpService mcp;
    private ConfigurationExporter exporter;
    private final Prompts prompts = new Prompts();

    private Stage stage;
    private Scene scene;
    private SettingsController controller;
    private Locale locale;
    private String userAgentStylesheet;

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        }
        catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));

        /*  The toolkit stays when the dialog, the last window, closes  */
        Platform.setImplicitExit(false);
    }

    @BeforeEach
    void setUp() throws Exception {
        locale = I18nManager.getCurrentLocale();
        userAgentStylesheet = onFx(Application::getUserAgentStylesheet);
        config = ConfigMocks.fresh();
        config.ui().setUiLanguage("en");

        /*  The live clients read their keys from the configuration; only the tests of a typed key are answered  */
        bfl = spy(new BflApiServiceImpl(config));
        doReturn(CompletableFuture.completedFuture(true)).when(bfl).testConnection(anyString(), anyString());
        gemini = spy(new GeminiApiServiceImpl(config));
        doReturn(CompletableFuture.completedFuture(true)).when(gemini).testConnection(anyString(), any());
        openAi = spy(new OpenAiApiServiceImpl(config));
        doReturn(CompletableFuture.completedFuture(new OpenAiApiService.Account(true, DEFAULT_OPENAI_URL,
                List.of("gpt-5", "gpt-image-1"), List.of("gpt-image-1"))))
                .when(openAi).testAccount(anyString(), anyString());
        mcp = new McpService(config, new McpTools());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (stage != null)
            onFx(stage::close);
        if (controller != null)
            I18nManager.removeChangeListener(controller);
        mcp.stop();
        I18nManager.setLocale(locale);
        onFx(() -> Application.setUserAgentStylesheet(userAgentStylesheet));
    }

    /** Opens the dialog with the services of the test, as the main window does. */
    private void open() throws Exception {
        SettingsServices services = new SettingsServices(config, new ProviderRegistry(bfl, gemini, openAi), null,
                mcp, exporter, null, prompts);
        onFx(() -> {
            FXMLLoader loader = new FXMLLoader(SettingsController.class.getResource("/fxml/SettingsDialog.fxml"),
                    I18nManager.getCurrentResourceBundle());
            loader.setControllerFactory((type) -> {
                try {
                    return type == SettingsController.class ? new SettingsController(services)
                            : type.getDeclaredConstructor().newInstance();
                }
                catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
            DialogPane pane = loader.load();
            controller = loader.getController();
            stage = new Stage();
            scene = new Scene(pane, 900, 700);
            stage.setScene(scene);
            stage.show();
            return null;
        });
        assertEquals(List.of(), prompts.errors, "the dialog opened without an error");
    }

    @Test
    void theListSwitchesTheSections() throws Exception {
        open();
        List<String> panes = List.of("apiConfigurationPane", "corporateProxyPane", "appearancePane", "languagePane",
                "advancedPane", "mcpPane");
        ListView<?> list = node("settingsCategoryList");
        assertEquals(6, onFx(() -> list.getItems().size()));
        for (int index = panes.size() - 1; index >= 0; index--) {
            select(index);
            for (int pane = 0; pane < panes.size(); pane++) {
                Node node = node(panes.get(pane));
                assertEquals(pane == index, node.isVisible(), panes.get(pane) + " visible with section " + index);
                assertEquals(pane == index, node.isManaged(), panes.get(pane) + " managed with section " + index);
            }
        }

        /*  every section came with its controls and texts  */
        for (String id : List.of("apiKeyField", "geminiApiKeyField", "openaiApiKeyField", "proxyHostField",
                "installedCertificatesListView", "themeSelectionContainer", "germanRadio", "exportSettingsButton",
                "mcpPortSpinner"))
            assertNotNull(node(id), id);
        assertEquals(I18nManager.getText("settings.api.test"), onFx(() -> this.<Button>node("testConnectionButton")
                .getText()));
        assertEquals(I18nManager.getText("settings.mcp.status.off"), onFx(() -> this.<Label>node("mcpStatusLabel")
                .getText()));
    }

    @Test
    void aTestedKeyChangesNeitherTheLiveProviderNorTheSavedKey() throws Exception {
        open();
        testKey("apiKeyField", "testConnectionButton", "connectionStatusLabel", "bfl-typed",
                I18nManager.getText("settings.test.api.connection.success", "Global"));
        testKey("geminiApiKeyField", "testGeminiConnectionButton", "geminiConnectionStatusLabel", "gemini-typed",
                I18nManager.getText("settings.gemini.test.success"));
        testKey("openaiApiKeyField", "testOpenaiConnectionButton", "openaiConnectionStatusLabel", "openai-typed",
                java.text.MessageFormat.format(I18nManager.getText("settings.openai.test.success"), 2));

        verify(bfl).testConnection("bfl-typed", "Global");
        verify(gemini).testConnection("gemini-typed", null);
        verify(openAi).testAccount("openai-typed", DEFAULT_OPENAI_URL);
        for (ApiProvider provider : ApiProvider.values())
            assertNull(config.accounts().getApiKey(provider), provider + " saved by the test");
        assertNull(bfl.getApiKey());
        assertNull(gemini.getApiKey());
        assertNull(openAi.getApiKey());

        /*  The OpenAI test keeps the models of the API for the next opening, not the selection  */
        assertEquals(List.of("gpt-5", "gpt-image-1"), config.accounts().getOpenAiAvailableModels());
        assertEquals(List.of(), config.accounts().getOpenAiModels());
        assertTrue(onFx(() -> node("openaiModelsContainer").isVisible()));

        /*  Closed without saving: nothing of it reaches the file  */
        ApplicationConfig stored = new ApplicationConfig(LibraryPaths.under(config.paths().home()));
        for (ApiProvider provider : ApiProvider.values())
            assertNull(stored.accounts().getApiKey(provider), provider + " in the file");
    }

    @Test
    void saveStoresTheTestedKeysAndTheLiveProvidersSendThem() throws Exception {
        open();
        testKey("apiKeyField", "testConnectionButton", "connectionStatusLabel", "bfl-typed",
                I18nManager.getText("settings.test.api.connection.success", "Global"));
        testKey("geminiApiKeyField", "testGeminiConnectionButton", "geminiConnectionStatusLabel", "gemini-typed",
                I18nManager.getText("settings.gemini.test.success"));
        testKey("openaiApiKeyField", "testOpenaiConnectionButton", "openaiConnectionStatusLabel", "openai-typed",
                java.text.MessageFormat.format(I18nManager.getText("settings.openai.test.success"), 2));
        onFx(controller::applySettings);

        assertEquals(List.of(), prompts.errors);
        assertEquals("bfl-typed", config.accounts().getApiKey(ApiProvider.BFL));
        assertEquals("gemini-typed", config.accounts().getApiKey(ApiProvider.GOOGLE_GEMINI));
        assertEquals("openai-typed", config.accounts().getApiKey(ApiProvider.OPENAI));
        assertEquals(List.of("gpt-image-1"), config.accounts().getOpenAiModels());
        for (ApiProvider provider : ApiProvider.values())
            assertTrue(config.accounts().isUsable(provider), provider + " enabled and validated");

        /*  The live clients take the saved keys with their next request  */
        assertEquals("bfl-typed", bfl.getApiKey());
        assertEquals("gemini-typed", gemini.getApiKey());
        assertEquals("openai-typed", openAi.getApiKey());
        ApplicationConfig stored = new ApplicationConfig(LibraryPaths.under(config.paths().home()));
        assertEquals("gemini-typed", stored.accounts().getApiKey(ApiProvider.GOOGLE_GEMINI));
    }

    @Test
    void theResultOfATestFollowsTheLanguage() throws Exception {
        open();
        testKey("apiKeyField", "testConnectionButton", "connectionStatusLabel", "bfl-typed",
                I18nManager.getText("settings.test.api.connection.success", "Global"));
        onFx(() -> I18nManager.setLocale(Locale.GERMAN));
        assertEquals(I18nManager.getText("settings.test.api.connection.success", "Global"),
                onFx(() -> this.<Label>node("connectionStatusLabel").getText()));
        assertTrue(onFx(() -> this.<Label>node("connectionStatusLabel").getText()).startsWith("Verbindung"));
    }

    @Test
    void aClosedDialogNoLongerFollowsTheLanguage() throws Exception {
        open();
        onFx(stage::close);
        int listeners = I18nManager.getChangeListenerCount();
        for (int i = 0; i < 3; i++) {
            open();
            assertTrue(I18nManager.getChangeListenerCount() > listeners, "the open dialog follows the language");
            onFx(stage::close);
            assertEquals(listeners, I18nManager.getChangeListenerCount(), "listeners after closing " + (i + 1));
        }
    }

    @Test
    void anUntestedKeyIsNotSaved() throws Exception {
        open();
        onFx(() -> this.<TextInputControl>node("apiKeyField").setText("bfl-untested"));
        onFx(() -> this.<TextInputControl>node("geminiApiKeyField").setText("gemini-untested"));
        onFx(controller::applySettings);

        assertEquals(List.of("API Key Validation Required"), prompts.errors);
        assertNull(config.accounts().getApiKey(ApiProvider.BFL));
        assertNull(config.accounts().getApiKey(ApiProvider.GOOGLE_GEMINI));
        verify(bfl, never()).testConnection(anyString(), anyString());
    }

    @Test
    void theProxyIsSavedAndCertificatesAreImportedAndRemoved() throws Exception {
        open();
        select(1);
        CheckBox enabled = node("enableProxyCheckBox");
        TextInputControl host = node("proxyHostField");
        Spinner<Integer> port = node("proxyPortSpinner");
        assertTrue(onFx(host::isDisabled));
        onFx(() -> enabled.setSelected(true));
        assertFalse(onFx(host::isDisabled));
        assertFalse(onFx(() -> this.<Button>node("testProxyConnectionButton").isDisabled()));
        onFx(() -> {
            host.setText(" proxy.example ");
            port.getValueFactory().setValue(3128);
        });
        onFx(controller::applySettings);
        assertTrue(config.proxy().isProxyEnabled());
        assertEquals("proxy.example", config.proxy().getProxyHost());
        assertEquals(3128, config.proxy().getProxyPort());

        onFx(() -> enabled.setSelected(false));
        assertTrue(onFx(host::isDisabled));
        onFx(controller::applySettings);
        assertFalse(config.proxy().isProxyEnabled());

        /*  A certificate goes into the folder of the library and out again  */
        Path certificate = Files.writeString(folder.resolve("corporate.pem"), "-----BEGIN CERTIFICATE-----\n");
        prompts.files = List.of(certificate.toFile());
        fire("uploadCertificateButton");
        Path stored = Path.of(config.proxy().getCertificatePath()).resolve("corporate.pem");
        assertTrue(Files.exists(stored));
        ListView<String> certificates = node("installedCertificatesListView");
        assertEquals(List.of("corporate.pem"), onFx(() -> List.copyOf(certificates.getItems())));
        assertEquals("1 certificate(s) uploaded successfully",
                onFx(() -> this.<Label>node("certificateUploadStatus").getText()));

        /*  Importing it again asks before it is replaced  */
        prompts.answers.add(false);
        fire("uploadCertificateButton");
        assertEquals(List.of("Upload Failed"), prompts.errors);

        onFx(() -> certificates.getSelectionModel().select("corporate.pem"));
        assertFalse(onFx(() -> this.<Button>node("removeCertificateButton").isDisabled()));
        fire("removeCertificateButton");
        assertFalse(Files.exists(stored));
        assertEquals(List.of(), onFx(() -> List.copyOf(certificates.getItems())));
        assertTrue(onFx(() -> this.<Button>node("removeCertificateButton").isDisabled()));
    }

    @Test
    void theMcpServerFollowsActivationAndPortAtOnce() throws Exception {
        int free;
        try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            free = socket.getLocalPort();
        }
        open();
        select(5);
        Spinner<Integer> port = node("mcpPortSpinner");
        Label status = node("mcpStatusLabel");
        onFx(() -> port.getValueFactory().setValue(free));
        assertEquals(free, config.mcp().getMcpPort());
        assertNull(config.mcp().getMcpToken());

        /*  The first activation creates the token and starts the server  */
        onFx(() -> this.<CheckBox>node("mcpEnabledCheckBox").setSelected(true));
        assertTrue(config.mcp().isMcpEnabled());
        assertNotNull(config.mcp().getMcpToken());
        assertEquals(config.mcp().getMcpToken(), onFx(() -> this.<TextInputControl>node("mcpTokenField").getText()));
        waitFor(() -> status.getText().equals(I18nManager.getText("settings.mcp.status.running",
                String.valueOf(free))), "the server runs");
        assertEquals(new McpService.Status(McpService.State.RUNNING, free), mcp.getStatus());

        onFx(() -> this.<CheckBox>node("mcpEnabledCheckBox").setSelected(false));
        waitFor(() -> status.getText().equals(I18nManager.getText("settings.mcp.status.off")), "the server stops");
        assertFalse(config.mcp().isMcpEnabled());
        assertEquals(McpService.State.OFF, mcp.getStatus().state());
    }

    @Test
    void aChosenThemeShowsAtOnceAndIsSaved() throws Exception {
        open();
        select(2);
        VBox themes = node("themeSelectionContainer");
        RadioButton nordLight = onFx(() -> themes.getChildren().stream().map((Node node) -> (RadioButton) node)
                .filter((RadioButton radio) -> "nord-light".equals(radio.getUserData())).findFirst().orElseThrow());
        onFx(() -> nordLight.setSelected(true));
        assertEquals(new NordLight().getUserAgentStylesheet(), onFx(Application::getUserAgentStylesheet));
        assertEquals("primer-dark", config.ui().getUiTheme());

        onFx(controller::applySettings);
        assertEquals("nord-light", config.ui().getUiTheme());
    }

    @Test
    void anExportedBackupComesBackAndTheDialogAndTheLibraryShowIt() throws Exception {
        DatabaseManager database = DatabaseConfig.initialize(folder.resolve("db").resolve("test.db").toString());
        LibraryEvents events = new LibraryEvents();
        PromptBlockService blocks = new PromptBlockService(database, events);
        ColorStyleService colors = new ColorStyleService(database, events);
        MoodService moods = new MoodService(database, blocks, colors, events);
        exporter = new ConfigurationExporter(blocks, colors, moods, new FrameService(database, events));
        Mood mood = new Mood(null, "Backup Me");
        mood.setReference(Mood.Field.STYLE, "std:style:analog-film");
        assertTrue(moods.add(mood));
        config.accounts().setApiRegion("US");
        open();
        select(4);

        File backup = folder.resolve("backup.json").toFile();
        prompts.saveTo = backup;
        fire("exportSettingsButton");
        assertTrue(backup.length() > 0);
        assertEquals(List.of("Export Successful"), prompts.infos);

        /*  Region and mood changed after the backup  */
        ChoiceBox<String> region = node("regionComboBox");
        onFx(() -> region.setValue("EU"));
        config.accounts().setApiRegion("EU");
        assertTrue(moods.deletePermanently(mood.getId()));
        List<LibraryEvents.Kind> changed = new CopyOnWriteArrayList<>();
        events.addListener((kind, id) -> changed.add(kind), Platform::runLater);

        prompts.openFrom = backup;
        fire("importSettingsButton");
        assertEquals(List.of("Export Successful", "Import Successful"), prompts.infos);
        assertEquals("US", config.accounts().getApiRegion());
        assertEquals("US", onFx(region::getValue));
        assertNotNull(moods.find(mood.getId()));
        waitFor(() -> changed.contains(LibraryEvents.Kind.MOOD), "the main window learns of the restored mood");
    }

    @Test
    void aResetKeepsTheSecretsAndShowsTheDefaults() throws Exception {
        config.accounts().setApiKey(ApiProvider.BFL, "bfl-saved");
        config.accounts().setApiKey(ApiProvider.GOOGLE_GEMINI, "gemini-saved");
        config.accounts().setApiKey(ApiProvider.OPENAI, "openai-saved");
        config.proxy().setProxyPassword("proxy-secret");
        config.accounts().setApiRegion("EU");
        open();
        assertEquals("EU", onFx(() -> this.<ChoiceBox<String>>node("regionComboBox").getValue()));
        select(4);

        fire("resetSettingsButton");
        assertEquals(List.of("Settings Reset"), prompts.infos);
        assertEquals("bfl-saved", config.accounts().getApiKey(ApiProvider.BFL));
        assertEquals("gemini-saved", config.accounts().getApiKey(ApiProvider.GOOGLE_GEMINI));
        assertEquals("openai-saved", config.accounts().getApiKey(ApiProvider.OPENAI));
        assertEquals("proxy-secret", config.proxy().getProxyPassword());
        assertEquals("Global", config.accounts().getApiRegion());
        assertEquals("Global", onFx(() -> this.<ChoiceBox<String>>node("regionComboBox").getValue()));

        /*  Save afterwards keeps the saved keys, which the dialog showed as validated  */
        onFx(controller::applySettings);
        assertEquals(List.of(), prompts.errors);
        assertEquals("bfl-saved", config.accounts().getApiKey(ApiProvider.BFL));
    }

    /*  types a key, starts its test and waits for the status line  */
    private void testKey(String field, String button, String statusLabel, String key, String success)
            throws Exception {
        onFx(() -> this.<TextInputControl>node(field).setText(key));
        fire(button);
        Label status = node(statusLabel);
        waitFor(() -> status.getText().equals(success), button + " passes");
    }

    private void select(int section) throws Exception {
        onFx(() -> this.<ListView<?>>node("settingsCategoryList").getSelectionModel().select(section));
    }

    private void fire(String button) throws Exception {
        onFx(() -> this.<Button>node(button).fire());
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T node(String id) {
        return (T) scene.lookup("#" + id);
    }

    private static void waitFor(Callable<Boolean> condition, String what) throws Exception {
        for (int i = 0; i < 200; i++) {
            if (onFx(condition))
                return;
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out: " + what);
    }

    private static void onFx(Runnable action) throws Exception {
        onFx(() -> {
            action.run();
            return null;
        });
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(action.call());
            }
            catch (Exception e) {
                failure.set(e);
            }
            finally {
                done.countDown();
            }
        });
        assertTrue(done.await(20, TimeUnit.SECONDS), "the JavaFX thread did not answer in time");
        if (failure.get() != null)
            throw failure.get();
        return result.get();
    }

    /** Answers the message boxes and file choosers of the dialog and records what they said. */
    private static final class Prompts implements SettingsPrompts {

        final List<String> errors = new CopyOnWriteArrayList<>();
        final List<String> infos = new CopyOnWriteArrayList<>();
        final Deque<Boolean> answers = new ArrayDeque<>();
        File saveTo;
        File openFrom;
        List<File> files = new ArrayList<>();

        @Override
        public void error(String title, String message) {
            errors.add(title);
        }

        @Override
        public void info(String title, String message) {
            infos.add(title);
        }

        @Override
        public boolean confirm(String title, String header, String content, boolean yesNo) {
            return answers.isEmpty() || answers.poll();
        }

        @Override
        public File saveFile(FileChooser chooser, Window owner) {
            return saveTo;
        }

        @Override
        public File openFile(FileChooser chooser, Window owner) {
            return openFrom;
        }

        @Override
        public List<File> openFiles(FileChooser chooser, Window owner) {
            return files;
        }
    }
}
