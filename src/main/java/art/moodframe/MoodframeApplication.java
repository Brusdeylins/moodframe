package art.moodframe;

import java.io.IOException;
import java.net.URL;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.CorporateProxyConfig;
import art.moodframe.database.DatabaseConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.ui.SplashScreen;
import art.moodframe.util.Secrets;
import art.moodframe.util.ThemeManager;

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
 * <li>Initialize application configuration</li>
 * <li>Initialize database schema</li>
 * <li>Load MainWindow FXML with controller</li>
 * <li>Apply CSS styling</li>
 * <li>Display the primary stage</li>
 * </ol>
 * 
 * @since 1.0.0
 */
public class MoodframeApplication extends Application {

    private static final Logger logger = LoggerFactory.getLogger(MoodframeApplication.class);

    @Override
    public void start(Stage primaryStage) {

        // Show splash screen first
        SplashScreen splash = new SplashScreen(() -> {
            // This callback runs after splash screen closes
            initializeAndShowMainWindow(primaryStage);
        });

        splash.show();
    }

    /**
     * Initializes the application and shows the main window.
     * This is called after the splash screen closes.
     * 
     * @param primaryStage the primary stage to display
     */
    private void initializeAndShowMainWindow(Stage primaryStage) {
        try {
            // Initialize application systems
            initializeApplication();

            // Perform trash cleanup on startup
            performTrashCleanup();

            // Perform retroactive thumbnail generation
            performRetroactiveThumbnailGeneration();

            // Validate API providers (BFL, Gemini and OpenAI)
            validateApiProviders();

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

        } catch (Exception e) {
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
            ApplicationConfig config = new ApplicationConfig();
            ServiceFactory.getInstance().registerService(ApplicationConfig.class, config);

            /*  Earlier versions wrote the Gemini key into the log files  */
            Secrets.redactLogs(java.nio.file.Paths.get(config.getLogsPath()));

            // Initialize CorporateProxyConfig after ApplicationConfig
            CorporateProxyConfig proxyConfig = new CorporateProxyConfig(config);
            ServiceFactory.getInstance().registerService(CorporateProxyConfig.class, proxyConfig);

            // Initialize I18n with user's preferred language
            String userLanguage = config.getUiLanguage();
            java.util.Locale appLocale = "de".equals(userLanguage) ? java.util.Locale.GERMAN : java.util.Locale.ENGLISH;
            art.moodframe.util.I18nManager.setLocale(appLocale);

            // Initialize database
            DatabaseConfig.initialize(config.getDatabasePath());

            // Register DatabaseManager in service factory for dependency injection
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.database.DatabaseManager.class,
                    () -> {
                        try {
                            return new art.moodframe.database.DatabaseManager(
                                    java.nio.file.Paths.get(config.getDatabasePath()));
                        } catch (Exception e) {
                            logger.error("Failed to create DatabaseManager", e);
                            return null;
                        }
                    });

            // Initialize PromptBlockService for the prompt building blocks (style, atmosphere, light)
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.PromptBlockService.class,
                    () -> new art.moodframe.service.PromptBlockService());

            // Initialize ColorStyleService for color palette management
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.ColorStyleService.class,
                    () -> new art.moodframe.service.ColorStyleService());

            // Initialize TrashManager for trash file operations (60 days retention)
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.TrashManager.class,
                    () -> new art.moodframe.service.TrashManager(60));

            // Initialize BFL API Service
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.BflApiService.class,
                    () -> new art.moodframe.service.BflApiServiceImpl());

            // Initialize Google Gemini API Service
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.GeminiApiService.class,
                    () -> new art.moodframe.service.GeminiApiServiceImpl(config));

            // Initialize OpenAI API Service
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.OpenAiApiService.class,
                    () -> new art.moodframe.service.OpenAiApiServiceImpl(config));

            // Moves the files of chats, entries and images to the trash and back
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.ChatFileService.class,
                    () -> new art.moodframe.service.ChatFileService(
                            ServiceFactory.getInstance().getService(art.moodframe.service.TrashManager.class),
                            ServiceFactory.getInstance().getService(art.moodframe.service.ThumbnailManager.class),
                            new art.moodframe.service.FileManagerService()));

            // The one ChatRepository: the request manager, the trash and the main window share it
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.repository.ChatRepository.class,
                    () -> {
                        art.moodframe.database.DatabaseManager dbManager = ServiceFactory.getInstance()
                                .getService(art.moodframe.database.DatabaseManager.class);
                        if (dbManager == null)
                            return null;

                        return new art.moodframe.repository.ChatRepository(dbManager,
                                ServiceFactory.getInstance().getService(art.moodframe.service.ChatFileService.class));
                    });

            // The central trash: chats first, the other kinds of items dock in with their own group
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.TrashCenter.class,
                    () -> {
                        art.moodframe.service.TrashCenter trashCenter = new art.moodframe.service.TrashCenter();
                        art.moodframe.repository.ChatRepository chatRepo = ServiceFactory.getInstance()
                                .getService(art.moodframe.repository.ChatRepository.class);
                        if (chatRepo != null)
                            trashCenter.register(new art.moodframe.service.ChatTrashGroup(chatRepo));
                        return trashCenter;
                    });

            // Loads the chats and settles what a restart left behind
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.ChatHistoryService.class,
                    () -> {
                        art.moodframe.repository.ChatRepository chatRepo = ServiceFactory.getInstance()
                                .getService(art.moodframe.repository.ChatRepository.class);
                        return chatRepo != null ? new art.moodframe.service.ChatHistoryService(chatRepo,
                                ServiceFactory.getInstance().getService(art.moodframe.service.TrashManager.class),
                                ServiceFactory.getInstance().getService(art.moodframe.service.ChatFileService.class))
                                : null;
                    });

            // Initialize RequestResponseManager for real API integration
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.RequestResponseManager.class,
                    () -> {
                        try {
                            art.moodframe.repository.ChatRepository chatRepo = ServiceFactory.getInstance()
                                    .getService(art.moodframe.repository.ChatRepository.class);
                            return new art.moodframe.service.RequestResponseManager(config, chatRepo);
                        } catch (Exception e) {
                            logger.error("Failed to create RequestResponseManager with database integration", e);
                            return null;
                        }
                    });

            // Initialize ThumbnailManager for image optimization
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.ThumbnailManager.class,
                    () -> {
                        try {
                            return new art.moodframe.service.ThumbnailManager();
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
     * Initializes and starts the background trash cleanup service.
     * This runs initial cleanup on application startup and then continues
     * to run automatic cleanup every 6 hours during application runtime.
     */
    private void performTrashCleanup() {
        try {

            // Create and register TrashCleanupService in ServiceFactory
            art.moodframe.service.TrashCleanupService cleanupService = new art.moodframe.service.TrashCleanupService(
                    ServiceFactory.getInstance().getService(art.moodframe.service.TrashCenter.class));
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.TrashCleanupService.class,
                    () -> cleanupService);

            // Perform initial cleanup
            cleanupService.performAutoCleanup();

        } catch (Exception e) {
            // Don't let cleanup failure prevent app startup
            logger.error("Failed to perform trash cleanup", e);
        }
    }

    /**
     * Performs retroactive thumbnail generation for existing images.
     * This runs in the background to avoid blocking application startup.
     */
    private void performRetroactiveThumbnailGeneration() {
        try {

            // Get ThumbnailManager from ServiceFactory
            art.moodframe.service.ThumbnailManager thumbnailManager = ServiceFactory.getInstance()
                    .getService(art.moodframe.service.ThumbnailManager.class);

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
     * Validates all configured API providers (BFL, Gemini and OpenAI).
     * This runs in the background to avoid blocking application startup.
     * Validation results are persisted to configuration for UI consumption.
     */
    private void validateApiProviders() {
        try {
            // Get required services from ServiceFactory
            ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
            art.moodframe.service.BflApiService bflService = ServiceFactory.getInstance()
                    .getService(art.moodframe.service.BflApiService.class);
            art.moodframe.service.GeminiApiService geminiService = ServiceFactory.getInstance()
                    .getService(art.moodframe.service.GeminiApiService.class);
            art.moodframe.service.OpenAiApiService openAiService = ServiceFactory.getInstance()
                    .getService(art.moodframe.service.OpenAiApiService.class);

            if (config == null || bflService == null || geminiService == null || openAiService == null) {
                logger.warn("Cannot validate API providers: required services not available");
                return;
            }

            // Create and register ApiProviderValidator
            art.moodframe.service.ApiProviderValidator validator = new art.moodframe.service.ApiProviderValidator(
                    config, bflService, geminiService, openAiService);
            ServiceFactory.getInstance().registerProvider(
                    art.moodframe.service.ApiProviderValidator.class,
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
        String configuredTheme = config.getUiTheme();

        // Check if user has explicitly set a theme preference
        boolean hasUserPreference = config.hasUserSetTheme();

        String themeToApply;
        if (!hasUserPreference) {
            // No user preference set, try to apply system-preferred theme
            themeToApply = ThemeManager.getSystemPreferredTheme();

            // If system theme differs from config, update the config
            if (!themeToApply.equals(configuredTheme)) {
                config.setUiTheme(themeToApply);
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
                double x = config.getWindowX();
                double y = config.getWindowY();
                int width = config.getWindowWidth();
                int height = config.getWindowHeight();
                boolean maximized = config.getWindowMaximized();

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
                config.setWindowBounds(
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
        if (config != null && config.getLoggingEnabled()) {
            logger.info("Shutting down MoodFrame Application...");
        }

        try {
            // Shutdown TrashCleanupService background tasks
            art.moodframe.service.TrashCleanupService cleanupService = ServiceFactory.getInstance()
                    .getService(art.moodframe.service.TrashCleanupService.class);
            if (cleanupService != null) {
                cleanupService.shutdown();
            }

            // Shutdown RequestResponseManager to stop all threads
            art.moodframe.service.RequestResponseManager requestManager = ServiceFactory.getInstance()
                    .getService(art.moodframe.service.RequestResponseManager.class);
            if (requestManager != null) {
                requestManager.shutdown();
            }

            // Shutdown ThumbnailManager to stop thumbnail generation threads
            art.moodframe.service.ThumbnailManager thumbnailManager = ServiceFactory.getInstance()
                    .getService(art.moodframe.service.ThumbnailManager.class);
            if (thumbnailManager != null) {
                thumbnailManager.shutdown();
            }

            // Close database connections
            DatabaseConfig.close();

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