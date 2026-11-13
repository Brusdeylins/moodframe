package art.moodframe.ui;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.util.VersionManager;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
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
            String versionStyle = titleStyle + " -fx-font-weight: 300;";
            Label versionText = new Label("Frame · Version " + version + " · © ");
            versionText.setStyle(versionStyle);
            Labeled authorLink = creditLink("Matthias Brusdeylins", "https://brusdeylins.info", versionStyle);
            Label versionYears = new Label(" 2025-2026");
            versionYears.setStyle(versionStyle);
            HBox versionLabel = new HBox(moodLabel, versionText, authorLink, versionYears);
            versionLabel.setAlignment(Pos.BASELINE_CENTER);

            /*  credits: built with Claude Code and ASE  */
            String creditStyle = "-fx-text-fill: #FFFFFF; -fx-font-size: 12px; -fx-font-weight: 200;";
            Label creditBefore = new Label("This application was built with Agentic Coding using ");
            creditBefore.setStyle(creditStyle);
            Labeled claudeLink = creditLink("Claude Code", "https://claude.ai", creditStyle);
            Label creditBetween = new Label(" and ");
            creditBetween.setStyle(creditStyle);
            Labeled aseLink = creditLink("ASE", "https://ase.tools", creditStyle);
            Label creditAfter = new Label(".");
            creditAfter.setStyle(creditStyle);
            HBox aiCreditLine1 = new HBox(creditBefore, claudeLink, creditBetween, aseLink, creditAfter);
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

    /*  a link in the info window, plain text in the same font during startup, so the lines keep their length  */
    private Labeled creditLink(String text, String url, String style) {
        if (info) {
            Hyperlink link = new Hyperlink(text);
            link.setStyle(style + " -fx-underline: true; -fx-padding: 0; -fx-border-width: 0;");
            link.setFocusTraversable(false);
            link.setOnAction((event) -> openInBrowser(url));
            return link;
        }
        else {
            Label label = new Label(text);
            label.setStyle(style);
            return label;
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
     * Centers the splash screen over the main window -- the owner, or at startup its saved bounds -- completely
     * on the screen the main window lies on, and without either on the primary monitor.
     */
    private void centerOnScreen() {
        Window owner = splashStage.getOwner();
        Rectangle2D anchor = owner != null
                ? new Rectangle2D(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight())
                : savedWindowBounds();
        Screen screen = anchor != null
                ? Screen.getScreensForRectangle(anchor).stream().findFirst().orElse(null)
                : null;
        if (screen == null) {
            screen = Screen.getPrimary();
            anchor = screen.getBounds();
        }
        Point2D position = position(anchor, screen.getVisualBounds(), SPLASH_WIDTH, SPLASH_HEIGHT);
        splashStage.setX(position.getX());
        splashStage.setY(position.getY());
    }

    /**
     * Computes the position of a window of the given size centered over the anchor, shifted into the visible
     * bounds of the screen where it would stick out.
     */
    static Point2D position(Rectangle2D anchor, Rectangle2D screen, double width, double height) {
        double x = anchor.getMinX() + (anchor.getWidth()  - width)  / 2;
        double y = anchor.getMinY() + (anchor.getHeight() - height) / 2;
        return new Point2D(
                Math.max(screen.getMinX(), Math.min(x, screen.getMaxX() - width)),
                Math.max(screen.getMinY(), Math.min(y, screen.getMaxY() - height)));
    }

    /*  the main window bounds saved at the last exit, or null where the main window does not restore them either;
        the configuration is registered only after the splash, so it is read here  */
    private static Rectangle2D savedWindowBounds() {
        try {
            ApplicationConfig config = new ApplicationConfig();
            return config.getWindowX() >= 0 && config.getWindowY() >= 0
                    ? new Rectangle2D(config.getWindowX(), config.getWindowY(),
                            config.getWindowWidth(), config.getWindowHeight())
                    : null;
        }
        catch (Exception e) {
            logger.warn("Could not read the saved window position: {}", e.getMessage());
            return null;
        }
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