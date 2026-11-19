package art.moodframe;

import java.io.IOException;
import java.net.URL;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.DetailedLogging;
import art.moodframe.config.LibraryPaths;
import art.moodframe.config.ServiceFactory;
import art.moodframe.database.DatabaseManager;
import art.moodframe.mcp.McpService;
import art.moodframe.mcp.McpTools;
import art.moodframe.net.CertificateStore;
import art.moodframe.net.HttpClients;
import art.moodframe.net.IoExecutor;
import art.moodframe.repository.BrainstormRepository;
import art.moodframe.repository.ChatGroupQueries;
import art.moodframe.repository.ChatRepository;
import art.moodframe.repository.ChatTrashRepository;
import art.moodframe.repository.ColorStyleRepository;
import art.moodframe.repository.FavoriteRepository;
import art.moodframe.repository.FrameRepository;
import art.moodframe.repository.MoodRepository;
import art.moodframe.repository.PromptBlockRepository;
import art.moodframe.service.ConfigurationExporter;
import art.moodframe.service.UpdateCheckService;
import art.moodframe.service.brainstorm.BrainstormModels;
import art.moodframe.service.brainstorm.BrainstormService;
import art.moodframe.service.brainstorm.BrainstormSessions;
import art.moodframe.service.brainstorm.BrainstormTools;
import art.moodframe.service.brainstorm.MoodAnalysisService;
import art.moodframe.service.brainstorm.YouTubeInfoService;
import art.moodframe.service.chat.ChatFileService;
import art.moodframe.service.chat.ChatHistoryService;
import art.moodframe.service.chat.ChatLibrary;
import art.moodframe.service.chat.ChatTrashGroup;
import art.moodframe.service.chat.FileManagerService;
import art.moodframe.service.chat.ImageAttachmentService;
import art.moodframe.service.chat.ImageSizeCheck;
import art.moodframe.service.chat.ImageTrashGroup;
import art.moodframe.service.chat.ThumbnailManager;
import art.moodframe.service.generation.ChatEntryTracker;
import art.moodframe.service.generation.GenerationService;
import art.moodframe.service.generation.GenerationWorkers;
import art.moodframe.service.generation.ImageFileStore;
import art.moodframe.service.generation.RequestResponseManager;
import art.moodframe.service.library.BlockTrashGroup;
import art.moodframe.service.library.BlockUsage;
import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.FavoriteService;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.library.FrameTrashGroup;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.MoodTrashGroup;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.provider.ApiProviderValidator;
import art.moodframe.service.provider.BflApiService;
import art.moodframe.service.provider.BflApiServiceImpl;
import art.moodframe.service.provider.GeminiApiService;
import art.moodframe.service.provider.GeminiApiServiceImpl;
import art.moodframe.service.provider.OpenAiApiService;
import art.moodframe.service.provider.OpenAiApiServiceImpl;
import art.moodframe.service.provider.ProviderRegistry;
import art.moodframe.service.trash.TrashCenter;
import art.moodframe.service.trash.TrashCleanupService;
import art.moodframe.service.trash.TrashManager;
import art.moodframe.ui.SplashScreen;
import art.moodframe.ui.component.DialogButtons;
import art.moodframe.ui.support.ThemeManager;
import art.moodframe.util.I18nManager;
import art.moodframe.util.Secrets;

/**
 * Main JavaFX Application class for MoodFrame.
 *
 * <p>
 * This class initializes the application, loads the main FXML layout,
 * and sets up the primary stage. It also initializes the application
 * configuration and database systems.
 * </p>
 *
 * <p>
 * <strong>Application Flow:</strong>
 * </p>
 * <ol>
 * <li>Show the splash screen (JavaFX thread)</li>
 * <li>On the startup thread meanwhile: configuration, database schema, services, trash cleanup, then the chats</li>
 * <li>Once both are done, at least one second after the splash showed: load MainWindow FXML with controller, apply
 * the theme and display the primary stage (JavaFX thread)</li>
 * </ol>
 *
 * @since 1.0.0
 */
public class MoodframeApplication extends Application {

    private static final Logger logger = LoggerFactory.getLogger(MoodframeApplication.class);

    /*  The folders of the library, the only reading of user.home; the configuration may move the images folder  */
    private final LibraryPaths homePaths = LibraryPaths.fromUserHome();

    /*  the theme the system prefers, detected by the startup while no theme is chosen (null: one is chosen)  */
    private volatile String systemTheme;

    @Override
    public void start(Stage primaryStage) {

        /*  The icon buttons look the same in every window, the dialogs included  */
        String iconButtonCss = MoodframeApplication.class.getResource("/css/icon-button.css").toExternalForm();
        Window.getWindows().addListener((ListChangeListener<Window>) (change) -> {
            while (change.next())
                for (Window window : change.getAddedSubList())
                    if (window instanceof Stage && window.getScene() != null
                            && !window.getScene().getStylesheets().contains(iconButtonCss))
                        window.getScene().getStylesheets().add(iconButtonCss);
        });

        /*  The one configuration, read before the splash, which places itself by the saved bounds of the main window  */
        ApplicationConfig config;
        try {
            config = new ApplicationConfig(homePaths);
        }
        catch (RuntimeException e) {
            logger.error("Failed to read the configuration", e);
            showErrorAndExit("Application Startup Failed", e.getMessage());
            return;
        }

        /*  The work that needs no JavaFX thread runs on a thread of its own while the splash shows: the rest of the
            configuration, database and migrations, services, the trash cleanup and the chats. The splash stays until
            it is done, at least one second  */
        CompletableFuture<Void> prepared = new CompletableFuture<>();
        SplashScreen splash = new SplashScreen(prepared, () -> prepared.whenComplete((Void done, Throwable failure) ->
                Platform.runLater(() -> showMainWindow(primaryStage, failure))), config);
        splash.show();
        Thread startup = new Thread(() -> {
            try {
                prepare(config);
                prepared.complete(null);
            }
            catch (Throwable e) {
                prepared.completeExceptionally(e);
            }
        }, "MoodFrame-startup");
        startup.setDaemon(true);
        startup.start();
    }

    /**
     * Prepares the application off the JavaFX thread, while the splash shows: configuration, the theme the system
     * prefers, database, services, the trash cleanup, then the chats, the thumbnails of older images and the check of
     * the accounts.
     */
    private void prepare(ApplicationConfig config) {
        initializeApplication(config);
        ServiceFactory services = ServiceFactory.getInstance();
        detectSystemTheme(services.getService(ApplicationConfig.class));
        startLibrary(services);
        cleanUpAndLoad(createTrashCleanup(), services.getService(ChatLibrary.class));
        performRetroactiveThumbnailGeneration();
        validateApiProviders();
        createWindowServices();
    }

    /**
     * Asks the system for its preferred theme while no theme is chosen, which runs programs ({@code defaults},
     * {@code reg}, {@code gdbus}) for up to 2 s, so not on the JavaFX thread; the theme is applied with the window.
     *
     * @param config the configuration, which remembers the chosen theme
     */
    private void detectSystemTheme(ApplicationConfig config) {
        ThemeManager.useConfig(config);
        if (!config.ui().hasUserSetTheme())
            systemTheme = ThemeManager.getSystemPreferredTheme();
    }

    /**
     * Starts the services of the library once, before anything uses them: the blocks, palettes and moods of the
     * user named like a standard one get a free name; the moods last, as they refer to blocks and palettes.
     *
     * @param services the registered services
     */
    private static void startLibrary(ServiceFactory services) {
        services.getService(PromptBlockService.class).start();
        services.getService(ColorStyleService.class).start();
        services.getService(MoodService.class).start();
    }

    /**
     * Cleans up the trash and loads the chats, in this order, so the chats hold nothing the cleanup removes and the
     * main window gets them loaded. A failed cleanup does not stop the start.
     *
     * @param cleanup the trash cleanup, null if it could not be created
     * @param chats   the chats, null if they are not available
     */
    static void cleanUpAndLoad(TrashCleanupService cleanup,
                               ChatLibrary chats) {
        if (cleanup != null) {
            try {
                cleanup.performAutoCleanup();
            } catch (Exception e) {
                logger.error("Failed to perform trash cleanup", e);
            }
        }
        if (chats != null)
            chats.load();
    }

    /**
     * Creates the services the main window looks up, so their construction (database, folders, threads) does not
     * run on the JavaFX thread.
     */
    private void createWindowServices() {
        ServiceFactory services = ServiceFactory.getInstance();
        for (Class<?> type : List.of(PromptBlockService.class,
                ColorStyleService.class, MoodService.class,
                FrameService.class, FavoriteService.class,
                ChatHistoryService.class,
                GenerationService.class,
                UpdateCheckService.class, ImageSizeCheck.class,
                McpService.class))
            services.findService(type);
    }

    /**
     * Shows the main window once the preparation is done, on the JavaFX thread: the FXML layout, the theme and the
     * stage; then the MCP server starts if it is enabled. A failed preparation ends the application.
     *
     * @param primaryStage the primary stage to display
     * @param failure      why the preparation failed, null if it succeeded
     */
    private void showMainWindow(Stage primaryStage, Throwable failure) {
        try {
            if (failure != null)
                throw failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause() : failure;

            // Load the main FXML layout
            Parent root = loadMainWindow();

            // Create the scene with default size from requirements (1200x800)
            Scene scene = new Scene(root, 1200, 800);

            // Apply AtlantaFX theme
            applyAtlantaFXTheme();

            // Configure the primary stage
            configureStage(primaryStage, scene);

            // Show the application
            primaryStage.show();

            /*  Start the MCP server if it is enabled  */
            startMcpServer();

            /*  Tell once that the settings file could not be read and where its copy is  */
            warnOfUnreadableConfig(primaryStage);

        } catch (Throwable e) {
            logger.error("Failed to start MoodFrame Application", e);
            // Show error dialog or fallback UI
            showErrorAndExit("Application Startup Failed", e.getMessage());
        }
    }

    /**
     * Initializes application systems (configuration, database, services): registers the services in the locator,
     * most of them as providers created on their first use.
     */
    private void initializeApplication(ApplicationConfig config) {

        try {
            setUpConfiguration(config);
            LibraryPaths paths = config.paths();

            /*  The one database of the library with its schema up to date, every repository gets its connections
                here  */
            DatabaseManager database = new DatabaseManager(paths.database());
            database.initialize();

            /*  The services report every change of the library here, the main window shows what others changed  */
            LibraryEvents events = new LibraryEvents();
            ServiceFactory.getInstance().registerService(LibraryEvents.class, events);

            /*  The blocking network work of the providers runs on its own bounded pool, never on the common pool  */
            ExecutorService io = IoExecutor.get();

            registerLibrary(database, events);
            registerProviders(config, io);
            registerChats(paths, database, events);
            registerGeneration(config, io);
            registerMcp(config, events);
            registerBrainstorm(config, database);

            /*  A newer version on the update site: hint behind the version in the header, checked at the start  */
            ServiceFactory.getInstance().registerProvider(UpdateCheckService.class, () -> new UpdateCheckService(config));

        } catch (Exception e) {
            logger.error("Failed to initialize application systems", e);
            throw new RuntimeException("Application initialization failed", e);
        }
    }

    /*  Registers the configuration with the folders of the library and sets up the logging, the certificates and
        the language  */
    private void setUpConfiguration(ApplicationConfig config) {
        ServiceFactory.getInstance().registerService(ApplicationConfig.class, config);
        LibraryPaths paths = config.paths();
        ServiceFactory.getInstance().registerService(LibraryPaths.class, paths);
        DetailedLogging.apply(config.getLoggingEnabled());

        /*  Earlier versions wrote the Gemini key into the log files  */
        Secrets.redactLogs(paths.logs());

        /*  The folder of the corporate certificates exists from the start, so the user finds it  */
        try {
            new CertificateStore(Path.of(config.proxy().getCertificatePath().trim())).createDirectory();
        }
        catch (IOException | RuntimeException e) {
            logger.warn("Failed to create the folder of the certificates", e);
        }

        /*  The HTTP clients with proxy and certificates of the services built here, which read them on first use  */
        ServiceFactory.getInstance().registerService(HttpClients.class, new HttpClients(config));

        // Initialize I18n with user's preferred language
        String userLanguage = config.ui().getUiLanguage();
        I18nManager.setLocale("de".equals(userLanguage) ? Locale.GERMAN : Locale.ENGLISH);
    }

    /*  The moods, frames, building blocks, palettes and favorites with the backup of the settings and the library  */
    private static void registerLibrary(DatabaseManager database, LibraryEvents events) {
        ServiceFactory services = ServiceFactory.getInstance();

        // Initialize PromptBlockService for the prompt building blocks (style, atmosphere, light)
        services.registerProvider(PromptBlockService.class,
                () -> new PromptBlockService(new PromptBlockRepository(database), events));

        // Initialize ColorStyleService for color palette management
        services.registerProvider(ColorStyleService.class,
                () -> new ColorStyleService(new ColorStyleRepository(database), events));

        // Initialize MoodService for the moods, the saved combinations of building blocks
        services.registerProvider(MoodService.class,
                () -> new MoodService(new MoodRepository(database), services.getService(PromptBlockService.class),
                        services.getService(ColorStyleService.class), events));

        /*  The frames, the saved settings of model, aspect ratio and image count  */
        services.registerProvider(FrameService.class, () -> new FrameService(new FrameRepository(database), events));

        /*  The favorites among moods, frames and building blocks, shown first in their lists  */
        services.registerProvider(FavoriteService.class,
                () -> new FavoriteService(new FavoriteRepository(database), events));

        /*  Backup of the settings and the library; an import reaches the main window through the events  */
        services.registerProvider(ConfigurationExporter.class,
                () -> new ConfigurationExporter(services.getService(PromptBlockService.class),
                        services.getService(ColorStyleService.class), services.getService(MoodService.class),
                        services.getService(FrameService.class)));

        /*  The moods tell which of them use a building block (question before deleting, count in the trash)  */
        services.registerProvider(BlockUsage.class, () -> services.getService(MoodService.class));
    }

    /*  The clients of BFL, Gemini and OpenAI and their registry  */
    private static void registerProviders(ApplicationConfig config, ExecutorService io) {
        ServiceFactory services = ServiceFactory.getInstance();

        // Initialize BFL API Service
        services.registerProvider(BflApiService.class, () -> new BflApiServiceImpl(config, io));

        // Initialize Google Gemini API Service
        services.registerProvider(GeminiApiService.class, () -> new GeminiApiServiceImpl(config, io));

        // Initialize OpenAI API Service
        services.registerProvider(OpenAiApiService.class, () -> new OpenAiApiServiceImpl(config, io));

        /*  The providers by their id, for the services that dispatch by provider  */
        services.registerProvider(ProviderRegistry.class,
                () -> new ProviderRegistry(services.getService(BflApiService.class),
                        services.getService(GeminiApiService.class), services.getService(OpenAiApiService.class)));
    }

    /*  The chats with their files, thumbnails and attachments, and the central trash with its groups  */
    private static void registerChats(LibraryPaths paths, DatabaseManager database, LibraryEvents events) {
        ServiceFactory services = ServiceFactory.getInstance();

        // Initialize TrashManager for trash file operations (60 days retention)
        services.registerProvider(TrashManager.class, () -> new TrashManager(paths, 60));

        /*  The thumbnails of the images  */
        services.registerProvider(ThumbnailManager.class, () -> new ThumbnailManager(paths));

        // Moves the files of chats, entries and images to the trash and back
        services.registerProvider(ChatFileService.class, () -> {
            ThumbnailManager thumbnails = services.getService(ThumbnailManager.class);
            return new ChatFileService(services.getService(TrashManager.class), thumbnails,
                    new FileManagerService(paths.images(), thumbnails));
        });

        // The one ChatRepository: the request manager, the trash and the main window share it
        services.registerProvider(ChatRepository.class, () -> new ChatRepository(database));

        /*  The trash of the chats, entries and images; it moves their files after each commit  */
        services.registerProvider(ChatTrashRepository.class,
                () -> new ChatTrashRepository(database, services.getService(ChatFileService.class)));

        // The central trash: chats first, the other kinds of items dock in with their own group
        services.registerProvider(TrashCenter.class, () -> {
            TrashCenter trashCenter = new TrashCenter();
            ChatRepository chatRepo = services.getService(ChatRepository.class);
            ChatTrashRepository chatTrash = services.getService(ChatTrashRepository.class);
            if (chatRepo != null) {
                trashCenter.register(new ChatTrashGroup(chatRepo, chatTrash));
                trashCenter.register(new ImageTrashGroup(chatRepo, chatTrash, services.getService(TrashManager.class),
                        services.getService(ThumbnailManager.class)));
            }
            trashCenter.register(new BlockTrashGroup(services.getService(PromptBlockService.class),
                    services.getService(ColorStyleService.class), services.getService(BlockUsage.class)));

            /*  Moods lose the references to blocks deleted for good, also by the automatic cleanup  */
            MoodService moodService = services.getService(MoodService.class);
            trashCenter.register(new MoodTrashGroup(moodService));
            trashCenter.register(new FrameTrashGroup(services.getService(FrameService.class)));
            moodService.followTrash(trashCenter);
            return trashCenter;
        });

        // Loads the chats and settles what a restart left behind
        services.registerProvider(ChatHistoryService.class, () -> new ChatHistoryService(
                services.getService(ChatRepository.class), services.getService(ChatTrashRepository.class),
                new ChatGroupQueries(database), services.getService(TrashManager.class)));

        /*  The list of the chats, shared by the main window and later callers; it follows the trash as well  */
        services.registerProvider(ChatLibrary.class, () -> {
            ChatLibrary chats = new ChatLibrary(services.getService(ChatHistoryService.class), events);
            services.getService(TrashCenter.class).addListener(chats);
            return chats;
        });

        /*  Reads, shrinks and encodes the images attached to an order, for the drop square and the attachment panel  */
        services.registerService(ImageAttachmentService.class, new ImageAttachmentService());

        /*  Corrects a missing or wrong pixel size of an image from its file when the image is first shown  */
        services.registerProvider(ImageSizeCheck.class,
                () -> new ImageSizeCheck(services.getService(ChatRepository.class), Platform::runLater));
    }

    /*  The generation: the requests to the providers and the orders without the user interface  */
    private static void registerGeneration(ApplicationConfig config, ExecutorService io) {
        ServiceFactory services = ServiceFactory.getInstance();

        /*  The threads of the polling and of the database writes, shared by the requests and the orders  */
        services.registerProvider(GenerationWorkers.class, GenerationWorkers::new);

        /*  The requests to the providers: the tracker writes on the store thread, the files come from the downloads
            of the shared HTTP clients  */
        services.registerProvider(RequestResponseManager.class, () -> {
            GenerationWorkers workers = services.getService(GenerationWorkers.class);
            ImageFileStore files = new ImageFileStore(config, services.getService(HttpClients.class)
                    .rebuilding((HttpClients clients) -> clients.javaHttp().connectTimeout(Duration.ofSeconds(30))
                            .followRedirects(HttpClient.Redirect.NORMAL).build()));
            return new RequestResponseManager(config, services.getService(ProviderRegistry.class),
                    new ChatEntryTracker(services.getService(ChatRepository.class), workers.store()), files,
                    workers, io);
        });

        /*  Carries out a generation order without the user interface: entry, request and submission  */
        services.registerProvider(GenerationService.class, () -> new GenerationService(config,
                services.getService(ChatLibrary.class), services.getService(MoodService.class),
                services.getService(RequestResponseManager.class), services.getService(ProviderRegistry.class),
                services.getService(GenerationWorkers.class).store()));
    }

    /*  The MCP server for clients on this computer, like Claude Code; it runs only if enabled  */
    private static void registerMcp(ApplicationConfig config, LibraryEvents events) {
        ServiceFactory services = ServiceFactory.getInstance();
        services.registerProvider(McpService.class, () -> {
            ChatLibrary chats = services.getService(ChatLibrary.class);
            MoodService moods = services.getService(MoodService.class);
            PromptBlockService blocks = services.getService(PromptBlockService.class);
            ColorStyleService colors = services.getService(ColorStyleService.class);
            FrameService frames = services.getService(FrameService.class);
            FavoriteService favorites = services.getService(FavoriteService.class);
            TrashCenter trash = services.getService(TrashCenter.class);
            return new McpService(config, McpTools.all(config, chats, moods, blocks, colors, frames, favorites, trash,
                    events, services.getService(GenerationService.class),
                    services.getService(MoodAnalysisService.class)));
        });
    }

    /*  The analysis of an image into a mood and the brainstorm windows  */
    private static void registerBrainstorm(ApplicationConfig config, DatabaseManager database) {
        ServiceFactory services = ServiceFactory.getInstance();

        /*  The analysis of an image into a mood, for the panel of the main window and the MCP tools  */
        services.registerProvider(MoodAnalysisService.class,
                () -> new MoodAnalysisService(services.getService(GeminiApiService.class),
                        services.getService(OpenAiApiService.class), config));

        /*  A brainstorm service for each brainstorm window, on the stored sessions, the language models and the tools,
            which share one lookup of the YouTube videos  */
        services.registerProvider(BrainstormService.Factory.class, () -> {
            GeminiApiService gemini = services.getService(GeminiApiService.class);
            BrainstormSessions sessions = new BrainstormSessions(new BrainstormRepository(database));
            BrainstormModels models = new BrainstormModels(gemini, services.getService(OpenAiApiService.class), config);
            BrainstormTools tools = new BrainstormTools(gemini, new YouTubeInfoService(config));
            return () -> new BrainstormService(sessions, models, tools);
        });
    }

    /**
     * Creates the trash cleanup, which also cleans up every 6 hours while the application runs.
     *
     * @return the cleanup, null if it cannot be created
     */
    private TrashCleanupService createTrashCleanup() {
        try {
            TrashCleanupService cleanupService =
                    new TrashCleanupService(
                            ServiceFactory.getInstance().getService(TrashCenter.class));
            cleanupService.start();

            /*  Registered as created, so the shutdown, which creates nothing, finds it  */
            ServiceFactory.getInstance().registerService(TrashCleanupService.class, cleanupService);
            return cleanupService;
        }
        catch (Exception e) {
            /*  A failed cleanup does not stop the start  */
            logger.error("Failed to perform trash cleanup", e);
            return null;
        }
    }

    /**
     * Performs retroactive thumbnail generation for existing images.
     * This runs in the background to avoid blocking application startup.
     */
    private void performRetroactiveThumbnailGeneration() {
        try {

            // Get ThumbnailManager from ServiceFactory
            ThumbnailManager thumbnailManager = ServiceFactory.getInstance()
                    .getService(ThumbnailManager.class);

            if (thumbnailManager != null) {
                // Start thumbnail generation in background (non-blocking)
                thumbnailManager.generateThumbnailsForExistingImages()
                        .whenComplete((generatedCount, throwable) -> {
                            if (throwable != null)
                                logger.error("Retroactive thumbnail generation failed", throwable);
                        });

                // Also clean up orphaned thumbnails from old deletions
                thumbnailManager.cleanupOrphanedThumbnails()
                        .whenComplete((deletedCount, throwable) -> {
                            if (throwable != null) {
                                logger.error("Orphaned thumbnail cleanup failed", throwable);
                            } else if (deletedCount > 0) {
                                logger.info("Cleaned up {} orphaned thumbnails from old deletions", deletedCount);
                            }
                        });

            } else {
                logger.warn("ThumbnailManager not available for retroactive generation");
            }

        } catch (Exception e) {
            // Don't let thumbnail generation failure prevent app startup
            logger.error("Failed to start retroactive thumbnail generation", e);
        }
    }

    /**
     * Tells the user that the settings file could not be read, so the defaults apply, and where the copy of the
     * unreadable file lies, with its stored keys.
     *
     * @param owner the main window
     */
    private void warnOfUnreadableConfig(Stage owner) {
        ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        if (config == null || !config.wasConfigUnreadable())
            return;
        java.nio.file.Path copy = config.unreadableConfigCopy();
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING,
                copy != null ? I18nManager.getText("config.unreadable.copied", copy)
                        : I18nManager.getText("config.unreadable.kept"));
        alert.initOwner(owner);
        alert.setTitle("MoodFrame");
        alert.setHeaderText(I18nManager.getText("config.unreadable.title"));
        alert.show();
    }

    /**
     * Starts the MCP server in the background if it is enabled. Neither a port in use nor an error blocks the app.
     */
    private void startMcpServer() {
        try {
            McpService mcp = ServiceFactory.getInstance()
                    .getService(McpService.class);
            if (mcp != null)
                mcp.applyAsync();
        }
        catch (Exception e) {
            logger.error("Failed to start the MCP server", e);
        }
    }

    /**
     * Validates all configured API providers (BFL, Gemini and OpenAI).
     * This runs in the background to avoid blocking application startup.
     * Validation results are persisted to configuration for UI consumption.
     */
    private void validateApiProviders() {
        try {
            // Get required services from ServiceFactory
            ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
            ProviderRegistry providers = ServiceFactory.getInstance()
                    .getService(ProviderRegistry.class);

            if (config == null || providers == null) {
                logger.warn("Cannot validate API providers: required services not available");
                return;
            }

            // Create and register ApiProviderValidator
            ApiProviderValidator validator =
                    new ApiProviderValidator(config, providers);
            ServiceFactory.getInstance().registerProvider(
                    ApiProviderValidator.class,
                    () -> validator);

            // Start validation in background (non-blocking)
            validator.validateAllProviders()
                    .whenComplete((results, throwable) -> {
                        if (throwable != null) {
                            logger.error("API provider validation failed", throwable);
                        } else {
                            logger.info("API provider validation completed: {}", results);
                        }
                    });

        } catch (Exception e) {
            // Don't let validation failure prevent app startup
            logger.error("Failed to start API provider validation", e);
        }
    }

    /**
     * Loads the main window FXML layout.
     *
     * @return the loaded Parent node
     * @throws IOException if FXML loading fails
     */
    private Parent loadMainWindow() throws IOException {

        URL fxmlUrl = getClass().getResource("/fxml/MainWindow.fxml");
        if (fxmlUrl == null) {
            throw new IOException("Could not find MainWindow.fxml resource");
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        return root;
    }

    /**
     * Applies AtlantaFX theme to the application.
     *
     * This method sets the application theme based on the user's configuration.
     * If the user hasn't explicitly set a theme preference, it uses the theme the system prefers, which the startup
     * detected.
     * AtlantaFX provides modern, consistent styling across all JavaFX controls.
     */
    private void applyAtlantaFXTheme() {

        ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        String configuredTheme = config.ui().getUiTheme();

        String themeToApply;
        if (systemTheme != null) {
            /*  No theme chosen: the system decides; it is stored, not as a choice, so the next start asks again  */
            themeToApply = systemTheme;
            if (!themeToApply.equals(configuredTheme))
                config.ui().setSystemTheme(themeToApply);
        }
        else {
            /*  The theme the user chose  */
            themeToApply = configuredTheme;
        }

        if (!ThemeManager.applyTheme(themeToApply)) {
            logger.error("Failed to apply theme '{}', using default", themeToApply);
            ThemeManager.applyDefaultTheme();
        }
    }

    /**
     * Configures the primary stage properties.
     *
     * @param stage the primary stage
     * @param scene the scene to display
     */
    private void configureStage(Stage stage, Scene scene) {
        stage.setTitle("MoodFrame");
        stage.setScene(scene);

        // Set minimum size constraints
        stage.setMinWidth(800);
        stage.setMinHeight(600);

        // Restore window bounds from configuration
        restoreWindowBounds(stage);

        // Create bound listeners for proper cleanup
        javafx.beans.value.ChangeListener<Number> boundsListener = (obs, oldVal, newVal) -> saveWindowBounds(stage);
        javafx.beans.value.ChangeListener<Boolean> maximizedListener = (obs, oldVal, newVal) -> saveWindowBounds(stage);

        // Save window bounds when window is moved or resized
        stage.widthProperty().addListener(boundsListener);
        stage.heightProperty().addListener(boundsListener);
        stage.xProperty().addListener(boundsListener);
        stage.yProperty().addListener(boundsListener);
        stage.maximizedProperty().addListener(maximizedListener);

        // Handle close request - save window bounds and cleanup listeners
        stage.setOnCloseRequest(event -> {
            saveWindowBounds(stage);
            stage.widthProperty().removeListener(boundsListener);
            stage.heightProperty().removeListener(boundsListener);
            stage.xProperty().removeListener(boundsListener);
            stage.yProperty().removeListener(boundsListener);
            stage.maximizedProperty().removeListener(maximizedListener);
        });
    }

    /**
     * Restores window bounds from configuration.
     *
     * @param stage the primary stage
     */
    private void restoreWindowBounds(Stage stage) {
        try {
            ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
            if (config != null) {
                double x = config.ui().getWindowX();
                double y = config.ui().getWindowY();
                int width = config.ui().getWindowWidth();
                int height = config.ui().getWindowHeight();
                boolean maximized = config.ui().getWindowMaximized();

                stage.setWidth(width);
                stage.setHeight(height);

                if (x >= 0 && y >= 0) {
                    stage.setX(x);
                    stage.setY(y);
                } else {
                    stage.centerOnScreen();
                }

                stage.setMaximized(maximized);
            }
        } catch (Exception e) {
            logger.warn("Failed to restore window bounds, using defaults", e);
            stage.centerOnScreen();
        }
    }

    /**
     * Saves current window bounds to configuration.
     *
     * @param stage the primary stage
     */
    private void saveWindowBounds(Stage stage) {
        try {
            ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
            if (config != null && stage.isShowing()) {
                config.ui().setWindowBounds(
                        stage.getX(),
                        stage.getY(),
                        stage.getWidth(),
                        stage.getHeight(),
                        stage.isMaximized());
            }
        } catch (Exception e) {
            logger.warn("Failed to save window bounds", e);
        }
    }

    /**
     * Logs the error, shows it in a message box (on the JavaFX thread, where the start fails) and exits the
     * application.
     *
     * @param title   error title
     * @param message error message
     */
    private static void showErrorAndExit(String title, String message) {
        logger.error("Showing error and exiting: {} - {}", title, message);
        System.err.println("ERROR: " + title + ": " + message);
        if (Platform.isFxApplicationThread()) {
            try {
                Alert alert = new Alert(Alert.AlertType.ERROR, message != null ? message : title, DialogButtons.ok());
                alert.setTitle(title);
                alert.setHeaderText(title);
                alert.showAndWait();
            }
            catch (RuntimeException e) {
                logger.error("Failed to show the error", e);
            }
        }
        System.exit(1);
    }

    @Override
    public void stop() {
        logger.debug("Shutting down MoodFrame Application...");
        shutDown(ServiceFactory.getInstance());
    }

    /**
     * Stops what the application started, creates nothing (only services created already are stopped) and runs every
     * step even if one before it failed, so the settings are written in any case.
     *
     * @param services the registered services, cleared at the end
     */
    static void shutDown(ServiceFactory services) {
        stopService(services, TrashCleanupService.class, TrashCleanupService::shutdown);
        stopService(services, RequestResponseManager.class, RequestResponseManager::shutdown);
        stopService(services, ThumbnailManager.class, ThumbnailManager::shutdown);
        stopService(services, ImageSizeCheck.class, ImageSizeCheck::shutdown);

        /*  The MCP server stops before the database closes  */
        stopService(services, McpService.class, McpService::stop);

        /*  Pending provider calls and downloads end with the application  */
        runStep("the I/O executor", IoExecutor::shutdown);

        /*  Write the window bounds and divider positions of the last moments, which wait for a quiet time  */
        stopService(services, ApplicationConfig.class, ApplicationConfig::flushConfiguration);
        runStep("the services", services::clearServices);
    }

    /*  Stops a service if it was created; a failure is logged and the shutdown goes on  */
    private static <T> void stopService(ServiceFactory services, Class<T> type, Consumer<T> stop) {
        T service = services.getCreatedService(type);
        if (service != null)
            runStep(type.getSimpleName(), () -> stop.accept(service));
    }

    /*  Runs a step of the shutdown; a failure is logged and the shutdown goes on  */
    private static void runStep(String name, Runnable step) {
        try {
            step.run();
        }
        catch (RuntimeException e) {
            logger.error("Failed to stop {}", name, e);
        }
    }

    public static void main(String[] args) {
        // Set system properties for better JavaFX performance and GPU acceleration
        // These properties must be set BEFORE JavaFX Application.launch()

        // Disable Prism verbose logging for production (set to "true" for debugging GPU issues)
        System.setProperty("prism.verbose", "false");

        // Force hardware acceleration: es2 (OpenGL), d3d (DirectX on Windows), sw
        // (software fallback)
        System.setProperty("prism.order", "es2,d3d,sw");

        // Enable GPU-based text rendering for better performance
        System.setProperty("prism.text", "t2k");

        // Disable LCD text for better scrolling performance (existing setting)
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.subpixeltext", "false");

        // Enable threaded optimization for multi-core systems
        System.setProperty("prism.threaded", "true");

        // Enable vsync for smooth animations (prevent screen tearing)
        System.setProperty("prism.vsync", "true");

        // Optimize for scrolling and animation performance
        System.setProperty("javafx.animation.fullspeed", "true");

        // Enable aggressive GPU caching for better performance
        System.setProperty("prism.allowhidpi", "true");

        // Allocate VRAM for texture caching (improves image rendering performance)
        System.setProperty("prism.maxvram", "512M");

        // Enable multi-threaded rendering pipeline (Quantum)
        System.setProperty("quantum.multithreaded", "true");

        // Launch JavaFX application
        launch(args);
    }
}