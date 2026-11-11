package art.moodframe.ui;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.util.VersionManager;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Professional splash screen displayed during application startup.
 * Shows the application logo with version information for 3 seconds.
 * 
 * @author Matthias Brusdeylins
 * @since 1.0.0
 */
public class SplashScreen {

    private static final Logger logger = LoggerFactory.getLogger(SplashScreen.class);

    private static final int SPLASH_WIDTH = 480;
    private static final int SPLASH_HEIGHT = 720;
    private static final int DISPLAY_DURATION_MS = 1000;

    private final Stage splashStage;
    private final Runnable onComplete;

    /**
     * Creates a new splash screen.
     * 
     * @param onComplete callback to execute when splash screen closes
     */
    public SplashScreen(Runnable onComplete) {
        this.onComplete = onComplete;
        this.splashStage = new Stage();
        initializeSplashStage();
    }

    /**
     * Initializes the splash screen stage with image and version overlay.
     */
    private void initializeSplashStage() {
        try {
            // Configure stage style - undecorated for borderless window
            splashStage.initStyle(StageStyle.UNDECORATED);
            splashStage.setResizable(false);
            splashStage.setAlwaysOnTop(true);

            // Load splash image
            Image splashImage = new Image(
                    getClass().getResourceAsStream("/images/splash.png"),
                    SPLASH_WIDTH,
                    SPLASH_HEIGHT,
                    true, // preserveRatio
                    true // smooth
            );

            ImageView imageView = new ImageView(splashImage);
            imageView.setFitWidth(SPLASH_WIDTH);
            imageView.setFitHeight(SPLASH_HEIGHT);
            imageView.setPreserveRatio(false);

            // Create version overlay
            String version = VersionManager.getVersion();
            Label versionLabel = new Label("MoodFrame - Version " + version + " - © Matthias Brusdeylins 2025-2026");
            versionLabel.setStyle(
                    "-fx-text-fill: #FFFFFF;" +
                            "-fx-font-size: 12px;" +
                            "-fx-font-weight: 300;");

            // Create AI development credits
            Label emptyLine = new Label("");
            Label aiCreditLine1 = new Label("This application was built with Agentic Coding using Claude Code AI.");
            aiCreditLine1.setStyle(
                    "-fx-text-fill: #FFFFFF;" +
                            "-fx-font-size: 11px;" +
                            "-fx-font-weight: 200;");

            Label aiCreditLine2 = new Label("Concept, planning, review and direction by human – implementation by AI.");
            aiCreditLine2.setStyle(
                    "-fx-text-fill: #FFFFFF;" +
                            "-fx-font-size: 11px;" +
                            "-fx-font-weight: 200;");

            // Create semi-transparent black bar for version text
            VBox versionOverlay = new VBox(2);
            versionOverlay.setAlignment(Pos.CENTER);
            versionOverlay.setPadding(new Insets(10));
            versionOverlay.setMaxHeight(80); // Increased height for multiple lines
            versionOverlay.setPrefHeight(80);
            versionOverlay.setStyle(
                    "-fx-background-color: rgba(0, 0, 0, 0.7);" +
                            "-fx-background-radius: 0;");
            versionOverlay.getChildren().addAll(versionLabel, emptyLine, aiCreditLine1, aiCreditLine2);

            // Stack image and overlay
            StackPane root = new StackPane();
            root.getChildren().addAll(imageView, versionOverlay);
            StackPane.setAlignment(versionOverlay, Pos.BOTTOM_CENTER);

            // Create scene
            Scene scene = new Scene(root, SPLASH_WIDTH, SPLASH_HEIGHT);
            scene.setFill(Color.TRANSPARENT);

            splashStage.setScene(scene);

            // Center on screen
            centerOnScreen();

            logger.info("Splash screen initialized with version: {}", version);

        } catch (Exception e) {
            logger.error("Failed to initialize splash screen", e);
            // If splash fails, immediately continue with application startup
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    /**
     * Centers the splash screen on the primary monitor.
     */
    private void centerOnScreen() {
        Screen screen = Screen.getPrimary();
        double screenWidth = screen.getBounds().getWidth();
        double screenHeight = screen.getBounds().getHeight();

        double x = (screenWidth - SPLASH_WIDTH) / 2;
        double y = (screenHeight - SPLASH_HEIGHT) / 2;

        splashStage.setX(x);
        splashStage.setY(y);
    }

    /**
     * Shows the splash screen and automatically closes it after the display
     * duration.
     */
    public void show() {
        try {
            splashStage.show();

            // Create pause transition for auto-close
            PauseTransition pause = new PauseTransition(Duration.millis(DISPLAY_DURATION_MS));
            pause.setOnFinished(event -> close());
            pause.play();

            ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
            if (config != null && config.getLoggingEnabled()) {
                logger.info("Splash screen displayed for {} ms", DISPLAY_DURATION_MS);
            }

        } catch (Exception e) {
            logger.error("Failed to show splash screen", e);
            // If showing fails, immediately continue
            close();
        }
    }

    /**
     * Closes the splash screen and triggers the completion callback.
     */
    private void close() {
        try {
            splashStage.close();
            ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
            if (config != null && config.getLoggingEnabled()) {
                logger.info("Splash screen closed");
            }
        } catch (Exception e) {
            logger.error("Error closing splash screen", e);
        } finally {
            // Always execute completion callback
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }
}