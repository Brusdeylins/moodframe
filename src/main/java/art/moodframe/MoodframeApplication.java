package art.moodframe;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.database.DatabaseConfig;
import art.moodframe.config.DetailedLogging;
import art.moodframe.config.ServiceFactory;
import art.moodframe.ui.SplashScreen;
import art.moodframe.config.LibraryPaths;
import art.moodframe.util.Secrets;
import art.moodframe.ui.support.ThemeManager;

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

        /*  The work that needs no JavaFX thread runs on a thread of its own while the splash shows: configuration,
            database and migrations, services, the trash cleanup and the chats. The splash stays until it is done,
            at least one second; the splash reads the saved window bounds before the work begins  */
        CompletableFuture<Void> prepared = new CompletableFuture<>();
        SplashScreen splash = new SplashScreen(prepared, () -> prepared.whenComplete((Void done, Throwable failure) ->
                Platform.runLater(() -> showMainWindow(primaryStage, failure))), homePaths);
        splash.show();
        Thread startup = new Thread(() -> {
            try {
                prepare();
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
     * Prepares the application off the JavaFX thread, while the splash shows: configuration, database, services,
     * the trash cleanup, then the chats, the thumbnails of older images and the check of the accounts.
     */
    private void prepare() {
        initializeApplication();
        ServiceFactory services = ServiceFactory.getInstance();
        cleanUpAndLoad(createTrashCleanup(), services.getService(art.moodframe.service.chat.ChatLibrary.class));
        performRetroactiveThumbnailGeneration();
        validateApiProviders();
        createWindowServices();
    }

    /**
     * Cleans up the trash and loads the chats, in this order, so the chats hold nothing the cleanup removes and the
     * main window gets them loaded. A failed cleanup does not stop the start.
     *
     * @param cleanup the trash cleanup, null if it could not be created
     * @param chats   the chats, null if they are not available
     */
    static void cleanUpAndLoad(art.moodframe.service.trash.TrashCleanupService cleanup,
                               art.moodframe.service.chat.ChatLibrary chats) {
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
        for (Class<?> type : java.util.List.of(art.moodframe.service.library.PromptBlockService.class,
                art.moodframe.service.library.ColorStyleService.class, art.moodframe.service.library.MoodService.class,
                art.moodframe.service.library.FrameService.class, art.moodframe.service.library.FavoriteService.class,
                art.moodframe.service.chat.ChatHistoryService.class,
                art.moodframe.service.generation.GenerationService.class,
                art.moodframe.service.UpdateCheckService.class, art.moodframe.service.chat.ImageSizeCheck.class,
                art.moodframe.mcp.McpService.class))
            services.getService(type);
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

        } catch (Throwable e) {
            logger.error("Failed to start MoodFrame Application", e);
            // Show error dialog or fallback UI
            showErrorAndExit(primaryStage, "Application Startup Failed", e.getMessage());
        }
    }

    /**
     * Initializes application systems (configuration, database, services).
     */
    private void initializeApplication() {

        try {
            // Initialize configuration
            ApplicationConfig config = new ApplicationConfig(homePaths);
            ServiceFactory.getInstance().registerService(ApplicationConfig.class, config);
            LibraryPaths paths = config.paths();
            ServiceFactory.getInstance().registerService(LibraryPaths.class, paths);
            DetailedLogging.apply(config.getLoggingEnabled());

            /*  Earlier versions wrote the Gemini key into the log files  */
            Secrets.redactLogs(java.nio.file.Paths.get(config.getLogsPath()));

            // Create the directory of the corporate certificates at the start
            new art.moodframe.net.CorporateProxyConfig(config);

            // Initialize I18n with user's preferred language
            String userLanguage = config.ui().getUiLanguage();
            java.util.Locale appLocale = "de".equals(userLanguage) ? java.util.Locale.GERMAN : java.util.Locale.ENGLISH;
            art.moodframe.util.I18nManager.setLocale(appLocale);

            /*  The one database of the library, every repository gets its connections here  */
            art.moodframe.database.DatabaseManager database = DatabaseConfig.initialize(config.getDatabasePath());

            /*  The services report every change of the library here, the main window shows what others changed  */
            art.moodframe.service.library.LibraryEvents events = new art.moodframe.service.library.LibraryEvents();
            ServiceFactory.getInstance().registerService(art.moodframe.service.library.LibraryEvents.class, events);

            // Initialize PromptBlockService for the prompt building blocks (style, atmosphere, light)
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.library.PromptBlockService.class,
                    () -> new art.moodframe.service.library.PromptBlockService(database, events));

            // Initialize ColorStyleService for color palette management
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.library.ColorStyleService.class,
                    () -> new art.moodframe.service.library.ColorStyleService(database, events));

            // Initialize MoodService for the moods, the saved combinations of building blocks
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.library.MoodService.class,
                    () -> new art.moodframe.service.library.MoodService(database,
                            ServiceFactory.getInstance().getService(
                                    art.moodframe.service.library.PromptBlockService.class),
                            ServiceFactory.getInstance().getService(
                                    art.moodframe.service.library.ColorStyleService.class),
                            events));

            /*  The frames, the saved settings of model, aspect ratio and image count  */
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.library.FrameService.class,
                    () -> new art.moodframe.service.library.FrameService(database, events));

            /*  The favorites among moods, frames and building blocks, shown first in their lists  */
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.library.FavoriteService.class,
                    () -> new art.moodframe.service.library.FavoriteService(database, events));

            /*  Backup of the settings and the library; an import reaches the main window through the events  */
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.ConfigurationExporter.class,
                    () -> {
                        ServiceFactory services = ServiceFactory.getInstance();
                        return new art.moodframe.service.ConfigurationExporter(
                                services.getService(art.moodframe.service.library.PromptBlockService.class),
                                services.getService(art.moodframe.service.library.ColorStyleService.class),
                                services.getService(art.moodframe.service.library.MoodService.class),
                                services.getService(art.moodframe.service.library.FrameService.class));
                    });

            /*  The moods tell which of them use a building block (question before deleting, count in the trash)  */
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.library.BlockUsage.class,
                    () -> ServiceFactory.getInstance().getService(art.moodframe.service.library.MoodService.class));

            // Initialize TrashManager for trash file operations (60 days retention)
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.trash.TrashManager.class,
                    () -> new art.moodframe.service.trash.TrashManager(paths, 60));

            /*  The blocking network work of the providers runs on its own bounded pool, never on the common pool  */
            java.util.concurrent.ExecutorService io = art.moodframe.net.IoExecutor.get();

            // Initialize BFL API Service
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.provider.BflApiService.class,
                    () -> new art.moodframe.service.provider.BflApiServiceImpl(config, io));

            // Initialize Google Gemini API Service
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.provider.GeminiApiService.class,
                    () -> new art.moodframe.service.provider.GeminiApiServiceImpl(config, io));

            // Initialize OpenAI API Service
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.provider.OpenAiApiService.class,
                    () -> new art.moodframe.service.provider.OpenAiApiServiceImpl(config, io));

            /*  The providers by their id, for the services that dispatch by provider  */
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.provider.ProviderRegistry.class,
                    () -> {
                        ServiceFactory services = ServiceFactory.getInstance();
                        return new art.moodframe.service.provider.ProviderRegistry(
                                services.getService(art.moodframe.service.provider.BflApiService.class),
                                services.getService(art.moodframe.service.provider.GeminiApiService.class),
                                services.getService(art.moodframe.service.provider.OpenAiApiService.class));
                    });

            // Moves the files of chats, entries and images to the trash and back
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.chat.ChatFileService.class,
                    () -> {
                        art.moodframe.service.chat.ThumbnailManager thumbnails = ServiceFactory.getInstance()
                                .getService(art.moodframe.service.chat.ThumbnailManager.class);
                        return new art.moodframe.service.chat.ChatFileService(
                                ServiceFactory.getInstance().getService(art.moodframe.service.trash.TrashManager.class),
                                thumbnails, new art.moodframe.service.chat.FileManagerService(paths.images(),
                                        thumbnails));
                    });

            // The one ChatRepository: the request manager, the trash and the main window share it
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.repository.ChatRepository.class,
                    () -> new art.moodframe.repository.ChatRepository(database,
                            ServiceFactory.getInstance().getService(art.moodframe.service.chat.ChatFileService.class)));

            // The central trash: chats first, the other kinds of items dock in with their own group
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.trash.TrashCenter.class,
                    () -> {
                        art.moodframe.service.trash.TrashCenter trashCenter =
                                new art.moodframe.service.trash.TrashCenter();
                        ServiceFactory services = ServiceFactory.getInstance();
                        art.moodframe.repository.ChatRepository chatRepo = ServiceFactory.getInstance()
                                .getService(art.moodframe.repository.ChatRepository.class);
                        if (chatRepo != null) {
                            trashCenter.register(new art.moodframe.service.chat.ChatTrashGroup(chatRepo));
                            trashCenter.register(new art.moodframe.service.chat.ImageTrashGroup(chatRepo,
                                    services.getService(art.moodframe.service.chat.ChatFileService.class),
                                    services.getService(art.moodframe.service.trash.TrashManager.class),
                                    services.getService(art.moodframe.service.chat.ThumbnailManager.class)));
                        }
                        trashCenter.register(new art.moodframe.service.library.BlockTrashGroup(
                                services.getService(art.moodframe.service.library.PromptBlockService.class),
                                services.getService(art.moodframe.service.library.ColorStyleService.class),
                                services.getService(art.moodframe.service.library.BlockUsage.class)));

                        /*  Moods lose the references to blocks deleted for good, also by the automatic cleanup  */
                        art.moodframe.service.library.MoodService moodService = ServiceFactory.getInstance()
                                .getService(art.moodframe.service.library.MoodService.class);
                        trashCenter.register(new art.moodframe.service.library.MoodTrashGroup(moodService));
                        trashCenter.register(new art.moodframe.service.library.FrameTrashGroup(
                                services.getService(art.moodframe.service.library.FrameService.class)));
                        trashCenter.addListener(new art.moodframe.service.trash.TrashCenter.Listener() {
                            @Override
                            public void restored(art.moodframe.service.trash.TrashGroup.Kind kind, String id) {
                            }

                            @Override
                            public void deleted(art.moodframe.service.trash.TrashGroup.Kind kind,
                                                java.util.List<String> ids) {
                                if (kind == art.moodframe.service.trash.TrashGroup.Kind.BLOCKS)
                                    moodService.blocksDeleted(ids);
                            }
                        });
                        return trashCenter;
                    });

            // Loads the chats and settles what a restart left behind
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.chat.ChatHistoryService.class,
                    () -> {
                        art.moodframe.repository.ChatRepository chatRepo = ServiceFactory.getInstance()
                                .getService(art.moodframe.repository.ChatRepository.class);
                        ServiceFactory services = ServiceFactory.getInstance();
                        return chatRepo != null ? new art.moodframe.service.chat.ChatHistoryService(chatRepo,
                                services.getService(art.moodframe.service.trash.TrashManager.class),
                                services.getService(art.moodframe.service.chat.ChatFileService.class))
                                : null;
                    });

            /*  The list of the chats, shared by the main window and later callers; it follows the trash as well  */
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.chat.ChatLibrary.class,
                    () -> {
                        art.moodframe.service.chat.ChatLibrary chats = new art.moodframe.service.chat.ChatLibrary(
                                ServiceFactory.getInstance().getService(
                                        art.moodframe.service.chat.ChatHistoryService.class),
                                events);
                        art.moodframe.service.trash.TrashCenter trashCenter = ServiceFactory.getInstance()
                                .getService(art.moodframe.service.trash.TrashCenter.class);
                        if (trashCenter != null)
                            trashCenter.addListener(chats);
                        return chats;
                    });

            /*  Reads, shrinks and encodes the images attached to an order, for the drop square and the attachment panel  */
            ServiceFactory.getInstance().registerService(art.moodframe.service.chat.ImageAttachmentService.class,
                    new art.moodframe.service.chat.ImageAttachmentService());

            /*  Corrects a missing or wrong pixel size of an image from its file when the image is first shown  */
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.chat.ImageSizeCheck.class,
                    () -> {
                        art.moodframe.repository.ChatRepository chatRepo = ServiceFactory.getInstance()
                                .getService(art.moodframe.repository.ChatRepository.class);
                        return chatRepo != null ? new art.moodframe.service.chat.ImageSizeCheck(chatRepo,
                                javafx.application.Platform::runLater) : null;
                    });

            // Initialize RequestResponseManager for real API integration
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.generation.RequestResponseManager.class,
                    () -> {
                        try {
                            art.moodframe.repository.ChatRepository chatRepo = ServiceFactory.getInstance()
                                    .getService(art.moodframe.repository.ChatRepository.class);
                            return new art.moodframe.service.generation.RequestResponseManager(config, chatRepo,
                                    ServiceFactory.getInstance().getService(
                                            art.moodframe.service.provider.ProviderRegistry.class), io);
                        } catch (Exception e) {
                            logger.error("Failed to create RequestResponseManager with database integration", e);
                            return null;
                        }
                    });

            /*  Carries out a generation order without the user interface: entry, request and submission  */
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.generation.GenerationService.class,
                    () -> {
                        art.moodframe.service.generation.RequestResponseManager requests = ServiceFactory.getInstance()
                                .getService(art.moodframe.service.generation.RequestResponseManager.class);
                        ServiceFactory services = ServiceFactory.getInstance();
                        return requests != null ? new art.moodframe.service.generation.GenerationService(config,
                                services.getService(art.moodframe.service.chat.ChatLibrary.class),
                                services.getService(art.moodframe.service.library.MoodService.class),
                                requests, services.getService(art.moodframe.service.provider.ProviderRegistry.class))
                                : null;
                    });

            /*  The MCP server for clients on this computer, like Claude Code; it runs only if enabled. Without the
                generation service, it only reads  */
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.mcp.McpService.class,
                    () -> {
                        ServiceFactory services = ServiceFactory.getInstance();
                        art.moodframe.service.chat.ChatLibrary chats = services.getService(
                                art.moodframe.service.chat.ChatLibrary.class);
                        art.moodframe.service.library.MoodService moods = services.getService(
                                art.moodframe.service.library.MoodService.class);
                        art.moodframe.service.library.PromptBlockService blocks = services.getService(
                                art.moodframe.service.library.PromptBlockService.class);
                        art.moodframe.service.library.ColorStyleService colors = services.getService(
                                art.moodframe.service.library.ColorStyleService.class);
                        art.moodframe.service.library.FrameService frames = services.getService(
                                art.moodframe.service.library.FrameService.class);
                        art.moodframe.service.library.FavoriteService favorites = services.getService(
                                art.moodframe.service.library.FavoriteService.class);
                        art.moodframe.service.trash.TrashCenter trash = services.getService(
                                art.moodframe.service.trash.TrashCenter.class);
                        art.moodframe.service.generation.GenerationService generation = services.getService(
                                art.moodframe.service.generation.GenerationService.class);
                        return new art.moodframe.mcp.McpService(config, generation != null
                                ? art.moodframe.mcp.McpTools.all(config, chats, moods, blocks, colors, frames,
                                        favorites, trash, events, generation, services.getService(
                                                art.moodframe.service.brainstorm.MoodAnalysisService.class))
                                : art.moodframe.mcp.McpTools.reading(config, chats, moods, blocks, colors, frames,
                                        favorites, trash, events));
                    });

            /*  The analysis of an image into a mood, for the panel of the main window and the MCP tools  */
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.brainstorm.MoodAnalysisService.class,
                    () -> new art.moodframe.service.brainstorm.MoodAnalysisService(
                            ServiceFactory.getInstance().getService(
                                    art.moodframe.service.provider.GeminiApiService.class),
                            ServiceFactory.getInstance().getService(
                                    art.moodframe.service.provider.OpenAiApiService.class),
                            config));

            /*  A brainstorm service for each brainstorm window, on the stored sessions and the language models  */
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.brainstorm.BrainstormService.Factory.class,
                    () -> {
                        ServiceFactory services = ServiceFactory.getInstance();
                        art.moodframe.service.provider.GeminiApiService gemini = services.getService(
                                art.moodframe.service.provider.GeminiApiService.class);
                        art.moodframe.service.provider.OpenAiApiService openAi = services.getService(
                                art.moodframe.service.provider.OpenAiApiService.class);
                        return () -> new art.moodframe.service.brainstorm.BrainstormService(
                                new art.moodframe.repository.BrainstormRepository(database), gemini, openAi, config);
                    });

            /*  A newer version on the update site: hint behind the version in the header, checked at the start  */
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.UpdateCheckService.class,
                    () -> new art.moodframe.service.UpdateCheckService(config));

            // Initialize ThumbnailManager for image optimization
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.chat.ThumbnailManager.class,
                    () -> {
                        try {
                            return new art.moodframe.service.chat.ThumbnailManager(paths);
                        } catch (Exception e) {
                            logger.error("Failed to create ThumbnailManager", e);
                            return null;
                        }
                    });

        } catch (Exception e) {
            logger.error("Failed to initialize application systems", e);
            throw new RuntimeException("Application initialization failed", e);
        }
    }

    /**
     * Creates the trash cleanup, which also cleans up every 6 hours while the application runs.
     *
     * @return the cleanup, null if it cannot be created
     */
    private art.moodframe.service.trash.TrashCleanupService createTrashCleanup() {
        try {
            art.moodframe.service.trash.TrashCleanupService cleanupService =
                    new art.moodframe.service.trash.TrashCleanupService(
                            ServiceFactory.getInstance().getService(art.moodframe.service.trash.TrashCenter.class));
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.trash.TrashCleanupService.class,
                    () -> cleanupService);
            return cleanupService;
        } catch (Exception e) {
            // Don't let cleanup failure prevent app startup
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
            art.moodframe.service.chat.ThumbnailManager thumbnailManager = ServiceFactory.getInstance()
                    .getService(art.moodframe.service.chat.ThumbnailManager.class);

            if (thumbnailManager != null) {
                // Start thumbnail generation in background (non-blocking)
                thumbnailManager.generateThumbnailsForExistingImages()
                        .whenCompleteAsync((generatedCount, throwable) -> {
                            if (throwable != null) {
                                logger.error("Retroactive thumbnail generation failed", throwable);
                            } else {
                                // Thumbnail generation completed
                            }
                        });

                // Also clean up orphaned thumbnails from old deletions
                thumbnailManager.cleanupOrphanedThumbnails()
                        .whenCompleteAsync((deletedCount, throwable) -> {
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
     * Starts the MCP server in the background if it is enabled. Neither a port in use nor an error blocks the app.
     */
    private void startMcpServer() {
        try {
            art.moodframe.mcp.McpService mcp = ServiceFactory.getInstance()
                    .getService(art.moodframe.mcp.McpService.class);
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
            art.moodframe.service.provider.ProviderRegistry providers = ServiceFactory.getInstance()
                    .getService(art.moodframe.service.provider.ProviderRegistry.class);

            if (config == null || providers == null) {
                logger.warn("Cannot validate API providers: required services not available");
                return;
            }

            // Create and register ApiProviderValidator
            art.moodframe.service.provider.ApiProviderValidator validator =
                    new art.moodframe.service.provider.ApiProviderValidator(config, providers);
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.provider.ApiProviderValidator.class,
                    () -> validator);

            // Start validation in background (non-blocking)
            validator.validateAllProviders()
                    .whenCompleteAsync((results, throwable) -> {
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
     * If the user hasn't explicitly set a theme preference, it will attempt
     * to detect and use the system theme preference.
     * AtlantaFX provides modern, consistent styling across all JavaFX controls.
     */
    private void applyAtlantaFXTheme() {

        ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        String configuredTheme = config.ui().getUiTheme();

        // Check if user has explicitly set a theme preference
        boolean hasUserPreference = config.ui().hasUserSetTheme();

        String themeToApply;
        if (!hasUserPreference) {
            // No user preference set, try to apply system-preferred theme
            themeToApply = ThemeManager.getSystemPreferredTheme();

            // If system theme differs from config, update the config
            if (!themeToApply.equals(configuredTheme)) {
                config.ui().setUiTheme(themeToApply);
            }
        } else {
            // User has set a preference, use it
            themeToApply = configuredTheme;
        }

        if (!ThemeManager.applyTheme(themeToApply)) {
            logger.error("Failed to apply theme '{}', using default", themeToApply);
            ThemeManager.applyDefaultTheme();
        }
    }

    /**
     * Changes the application theme at runtime.
     * 
     * @param themeName the name of the theme to apply
     */
    public void changeTheme(String themeName) {
        ThemeManager.changeTheme(themeName);
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
     * Shows an error dialog and exits the application.
     * 
     * @param stage   the primary stage
     * @param title   error title
     * @param message error message
     */
    private void showErrorAndExit(Stage stage, String title, String message) {
        logger.error("Showing error and exiting: {} - {}", title, message);

        // For now, just log and exit
        // In future, could show a proper error dialog
        System.err.println("ERROR: " + title + ": " + message);
        System.exit(1);
    }

    @Override
    public void stop() {
        ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        logger.debug("Shutting down MoodFrame Application...");

        try {
            // Shutdown TrashCleanupService background tasks
            art.moodframe.service.trash.TrashCleanupService cleanupService = ServiceFactory.getInstance()
                    .getService(art.moodframe.service.trash.TrashCleanupService.class);
            if (cleanupService != null) {
                cleanupService.shutdown();
            }

            // Shutdown RequestResponseManager to stop all threads
            art.moodframe.service.generation.RequestResponseManager requestManager = ServiceFactory.getInstance()
                    .getService(art.moodframe.service.generation.RequestResponseManager.class);
            if (requestManager != null) {
                requestManager.shutdown();
            }

            // Shutdown ThumbnailManager to stop thumbnail generation threads
            art.moodframe.service.chat.ThumbnailManager thumbnailManager = ServiceFactory.getInstance()
                    .getService(art.moodframe.service.chat.ThumbnailManager.class);
            if (thumbnailManager != null) {
                thumbnailManager.shutdown();
            }

            /*  Stop the workers of the size check, if they were started at all  */
            art.moodframe.service.chat.ImageSizeCheck sizeCheck = ServiceFactory.getInstance()
                    .getCreatedService(art.moodframe.service.chat.ImageSizeCheck.class);
            if (sizeCheck != null)
                sizeCheck.shutdown();

            /*  The MCP server stops before the database closes  */
            art.moodframe.mcp.McpService mcp = ServiceFactory.getInstance()
                    .getService(art.moodframe.mcp.McpService.class);
            if (mcp != null)
                mcp.stop();

            /*  Pending provider calls and downloads end with the application  */
            art.moodframe.net.IoExecutor.shutdown();

            /*  Write the window bounds and divider positions of the last moments, which wait for a quiet time  */
            if (config != null)
                config.flushConfiguration();

            // Clear service factory
            ServiceFactory.getInstance().clearServices();

        } catch (Exception e) {
            logger.error("Error during application shutdown", e);
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