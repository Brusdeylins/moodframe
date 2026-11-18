package art.moodframe.ui.component;

import art.moodframe.util.I18nManager;
import art.moodframe.ui.support.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tooltips of the white icon buttons on the dark bar over an image and in the full view: text and background in
 * the colors of the theme, readable in all themes, while the icons stay white. Shown on the Monocle headless platform,
 * against a throwaway {@code user.home}.
 */
class IconButtonTooltipTest {

    private static final List<String> THEMES = List.of("primer-light", "primer-dark", "nord-light", "nord-dark",
            "cupertino-light", "cupertino-dark", "dracula");

    private String home;

    @TempDir
    Path dir;

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        }
        catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await(10, TimeUnit.SECONDS);

        /*  closing the window of the test must not end the toolkit, which the following tests share  */
        Platform.setImplicitExit(false);
    }

    @BeforeEach
    void setUp() throws Exception {
        home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectory(dir.resolve("home")).toString());
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.home", home);
    }

    @Test
    void theTooltipsOfTheWhiteIconButtonsAreReadableInAllThemes() throws Exception {
        String before = onFxThread(Application::getUserAgentStylesheet);
        String iconButtonCss = IconButtonTooltipTest.class.getResource("/css/icon-button.css").toExternalForm();
        try {
            for (String theme : THEMES) {
                onFxThread(() -> {
                    Application.setUserAgentStylesheet(ThemeManager.getAtlantaFXTheme(theme).getUserAgentStylesheet());
                    ImagePreviewOverlay overlay = new ImagePreviewOverlay();
                    overlay.setVisible(true);
                    ImageActionBar bar = new ImageActionBar((action) -> {
                    });
                    bar.setVisible(true);
                    Stage stage = new Stage();
                    stage.setScene(new Scene(new StackPane(overlay, bar), 800, 600));
                    stage.getScene().getStylesheets().add(iconButtonCss);
                    stage.show();
                    try {
                        Button close = overlay.lookupAll(".icon-button").stream()
                                .map(Button.class::cast)
                                .filter((button) -> button.getTooltip() != null && I18nManager
                                        .getText("tooltip.preview.close").equals(button.getTooltip().getText()))
                                .findFirst().orElseThrow();
                        Button view = (Button) bar.getChildren().get(0);
                        Button delete = (Button) bar.getChildren().get(bar.getChildren().size() - 1);
                        for (Button button : List.of(close, view, delete)) {
                            Label tip = shownTooltip(button);
                            Color text = (Color) tip.getTextFill();
                            Color background = (Color) tip.getBackground().getFills().get(1).getFill();
                            double contrast = contrast(text, background);
                            assertTrue(contrast >= 4.5, theme + ", " + button.getTooltip().getText() + ": " + text
                                    + " on " + background + " = " + contrast);
                            button.getTooltip().hide();
                        }

                        /*  The icons on the dark bar stay white, the delete icon turns red while hovered  */
                        assertEquals(Color.WHITE, ((FontIcon) view.getGraphic()).getIconColor(), theme);
                        assertEquals(Color.WHITE, ((FontIcon) delete.getGraphic()).getIconColor(), theme);
                        delete.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), true);
                        delete.applyCss();
                        assertEquals(Color.web("#ff6b6b"), ((FontIcon) delete.getGraphic()).getIconColor(), theme);
                    }
                    finally {
                        overlay.closeOverlay();
                        stage.close();
                    }
                    return null;
                });
            }
        }
        finally {
            onFxThread(() -> {
                Application.setUserAgentStylesheet(before);
                return null;
            });
        }
    }

    /*  Shows the tooltip of a button below it and returns the label which draws it  */
    private static Label shownTooltip(Button button) {
        Tooltip tooltip = button.getTooltip();
        Bounds screen = button.localToScreen(button.getLayoutBounds());
        tooltip.show(button, screen.getMinX(), screen.getMaxY());
        tooltip.getScene().getRoot().applyCss();
        Node skin = tooltip.getSkin().getNode();
        return (Label) skin;
    }

    /*  The contrast ratio of two colors after WCAG 2  */
    private static double contrast(Color a, Color b) {
        double lighter = Math.max(luminance(a), luminance(b));
        double darker  = Math.min(luminance(a), luminance(b));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen()) + 0.0722 * linear(color.getBlue());
    }

    private static double linear(double channel) {
        return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    /*  Runs a step on the FX thread, and hands a failed assertion of it to the test  */
    private static <T> T onFxThread(Supplier<T> step) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(step.get());
            }
            catch (Throwable error) {
                failure.set(error);
            }
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() != null)
            throw new AssertionError(failure.get());
        return result.get();
    }
}
