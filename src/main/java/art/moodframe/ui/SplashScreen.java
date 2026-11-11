package art.moodframe.ui;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.util.VersionManager;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
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

    /*  height of the dark floor at the bottom of the splash image, in window pixels  */
    private static final double FLOOR_HEIGHT = 150.0;

    private final Stage splashStage;
    private final Runnable onComplete;

    /*  opened from the Info button: stays until Esc or a click, belongs to the main window  */
    private final boolean info;

    /**
     * Creates a new splash screen.
     * 
     * @param onComplete callback to execute when splash screen closes
     */
    public SplashScreen(Runnable onComplete) {
        this(onComplete, null);
    }

    private SplashScreen(Runnable onComplete, Window owner) {
        this.onComplete = onComplete;
        this.info = owner != null;
        this.splashStage = new Stage();
        if (info) {
            splashStage.initOwner(owner);
            splashStage.initModality(Modality.WINDOW_MODAL);
        }
        initializeSplashStage();
    }

    /**
     * Shows the splash screen again as an info window over the given window. It stays until the user presses
     * Esc or clicks it.
     *
     * @param owner the window the info belongs to
     */
    public static void showInfo(Window owner) {
        new SplashScreen(null, owner).show();
    }

    /**
     * Initializes the splash screen stage with image and version overlay.
     */
    private void initializeSplashStage() {
        try {
            // Configure stage style - undecorated for borderless window
            splashStage.initStyle(StageStyle.UNDECORATED);
            splashStage.setResizable(false);
            splashStage.setAlwaysOnTop(!info);

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
            String titleStyle = "-fx-text-fill: #FFFFFF; -fx-font-size: 14px;";
            Label moodLabel = new Label("Mood");
            moodLabel.setStyle(titleStyle + " -fx-font-weight: bold;");
            Label versionText = new Label("Frame · Version " + version + " · © Matthias Brusdeylins 2025-2026");
            versionText.setStyle(titleStyle + " -fx-font-weight: 300;");
            HBox versionLabel = new HBox(moodLabel, versionText);
            versionLabel.setAlignment(Pos.BASELINE_CENTER);

            /*  credits: built with ASE (a link in the info window, plain text during startup)  */
            String creditStyle = "-fx-text-fill: #FFFFFF; -fx-font-size: 12px; -fx-font-weight: 200;";
            Label creditBefore = new Label("This application was built with Agentic Coding using Claude Code and ");
            creditBefore.setStyle(creditStyle);
            Hyperlink aseLink = new Hyperlink("ase.tools");
            aseLink.setStyle(creditStyle + " -fx-underline: true; -fx-padding: 0; -fx-border-width: 0;");
            aseLink.setFocusTraversable(false);
            aseLink.setOnAction((event) -> openInBrowser("https://ase.tools"));
            Label creditAfter = new Label(".");
            creditAfter.setStyle(creditStyle);
            HBox aiCreditLine1 = new HBox(creditBefore, aseLink, creditAfter);
            aiCreditLine1.setAlignment(Pos.BASELINE_CENTER);

            Label aiCreditLine2 = new Label("Concept, planning, review and direction by human.");
            aiCreditLine2.setStyle(creditStyle);

            /*  the text stands directly on the image, centered in the dark floor below the glass pane  */
            VBox versionOverlay = new VBox(2);
            versionOverlay.setAlignment(Pos.CENTER);
            versionOverlay.setPadding(new Insets(0, 10, 17, 10));
            versionOverlay.setMaxHeight(FLOOR_HEIGHT);
            versionOverlay.setPrefHeight(FLOOR_HEIGHT);
            versionOverlay.setEffect(new DropShadow(6.0, Color.rgb(0, 0, 0, 0.9)));
            Region gap = new Region();
            gap.setMinHeight(6.0);
            versionOverlay.getChildren().addAll(versionLabel, gap, aiCreditLine1, aiCreditLine2);

            // Stack image and overlay
            StackPane root = new StackPane();
            root.getChildren().addAll(imageView, versionOverlay);
            StackPane.setAlignment(versionOverlay, Pos.BOTTOM_CENTER);

            // Create scene
            Scene scene = new Scene(root, SPLASH_WIDTH, SPLASH_HEIGHT);
            scene.setFill(Color.TRANSPARENT);

            if (info) {
                scene.setOnKeyPressed((event) -> {
                    if (event.getCode() == KeyCode.ESCAPE)
                        close();
                });
                scene.setOnMouseClicked((event) -> close());
            }

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

    private static void openInBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE))
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        }
        catch (Exception e) {
            logger.warn("Could not open {}: {}", url, e.getMessage());
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
            if (info) {
                splashStage.requestFocus();
                return;
            }

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