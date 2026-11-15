package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.ui.component.MultiImageAttachmentPanel;
import art.moodframe.util.I18nManager;
import art.moodframe.util.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.Background;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.Path;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * The cards of the groups of the right panel in the shown main window, on a throwaway {@code user.home}: each card
 * encloses its shown fields and its line with its inner padding below, unfolded or folded, after unfolding another
 * group, after choosing another model, with an attached image, with and without the scroll bar of the panel and after
 * a change of language and theme. The fold arrow shows a surface only while hovered, like the other icon buttons.
 */
class MainWindowPanelLayoutTest {

    /*  the inner padding of a card below its fields (see .panel-group in main-window.css)  */
    private static final double CARD_PADDING = 12.0;

    private static final List<String> GROUPS = List.of("moodGroupPane", "frameGroupPane", "generationGroupPane");

    @TempDir
    static java.nio.file.Path home;

    private static String realHome;
    private static String userAgentStylesheet;
    private static Locale locale;
    private static ApplicationConfig realConfig;
    private static Stage stage;
    private static Parent root;
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
        userAgentStylesheet = Application.getUserAgentStylesheet();
        locale = I18nManager.getCurrentLocale();
    }

    @AfterAll
    static void restoreTheEnvironment() throws Exception {
        onFxThread(() -> Application.setUserAgentStylesheet(userAgentStylesheet));
        I18nManager.setLocale(locale);
        if (realConfig != null)
            ServiceFactory.getInstance().registerService(ApplicationConfig.class, realConfig);
        System.setProperty("user.home", realHome);
    }

    /**
     * Shows a new main window as the application does on its start, in English and the dark theme, with the given
     * group unfolded as remembered from the last time; closes the window shown before.
     */
    private static void show(String open) throws Exception {
        close();

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
        config.setOpenPanelGroup(open);
        ServiceFactory.getInstance().registerService(ApplicationConfig.class, config);

        I18nManager.setLocale(Locale.ENGLISH);
        onFxThread(() -> {
            ThemeManager.applyTheme("primer-dark");
            FXMLLoader loader = new FXMLLoader(MainWindowPanelLayoutTest.class.getResource("/fxml/MainWindow.fxml"));
            root = loader.load();
            controller = loader.getController();
            stage = new Stage();
            stage.setScene(new Scene(root, 1200, 800));

            /*  as the application does for every window  */
            stage.getScene().getStylesheets().add(MainWindowPanelLayoutTest.class.getResource("/css/icon-button.css")
                    .toExternalForm());
            stage.show();
        });
    }

    @AfterEach
    void closeTheWindow() throws Exception {
        close();
    }

    private static void close() throws Exception {
        if (stage == null)
            return;
        Stage shown = stage;
        stage = null;
        onFxThread(() -> {
            shown.hide();
            attachments().dispose();
        });
        I18nManager.removeChangeListener(controller);
    }

    /*  a step on the JavaFX thread  */
    private interface Step {
        void run() throws Exception;
    }

    /** Runs a step on the JavaFX thread and waits for it and for the layout of what it changed. */
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
        if (stage != null && stage.isShowing())
            waitForLayout();
    }

    /** Waits for three layout passes of the window, so that also what the step left for later is laid out. */
    private static void waitForLayout() throws Exception {
        CountDownLatch passes = new CountDownLatch(3);
        Runnable pass = () -> {
            passes.countDown();
            Platform.requestNextPulse();
        };
        Platform.runLater(() -> {
            stage.getScene().addPostLayoutPulseListener(pass);
            Platform.requestNextPulse();
        });
        assertTrue(passes.await(10, TimeUnit.SECONDS), "the window lays out its changes");
        Platform.runLater(() -> stage.getScene().removePostLayoutPulseListener(pass));
    }

    private static VBox group(String id) {
        return (VBox) root.lookup("#" + id);
    }

    /** Unfolds a group with its arrow, unless it is unfolded already. */
    private static void unfold(String id) throws Exception {
        onFxThread(() -> {
            if (!group(id).lookup(".panel-group-details").isVisible())
                ((Button) group(id).lookup(".panel-group-arrow")).fire();
        });
        assertTrue(group(id).lookup(".panel-group-details").isVisible(), id);
    }

    private static MultiImageAttachmentPanel attachments() {
        return (MultiImageAttachmentPanel) ((VBox) root.lookup("#multiImageAttachmentContainer")).getChildren().get(0);
    }

    @SuppressWarnings("unchecked")
    private static void chooseModel(String family) throws Exception {
        ChoiceBox<String> families = (ChoiceBox<String>) root.lookup("#modelFamilyChoiceBox");
        assertTrue(families.getItems().contains(family), family + " in " + families.getItems());
        onFxThread(() -> families.setValue(family));
    }

    private static boolean scrollBarShown() {
        return root.lookup("#panelScrollPane").lookupAll(".scroll-bar").stream()
                .anyMatch((bar) -> ((ScrollBar) bar).getOrientation() == Orientation.VERTICAL && bar.isVisible());
    }

    /**
     * Checks that every card encloses its shown fields with its inner padding below, the detail fields while
     * unfolded, else the summary field, and that its line ends where these fields end.
     */
    private static void assertTheCardsEncloseTheirFields(String state) {
        for (String id : GROUPS) {
            VBox card = group(id);
            Node details = card.lookup(".panel-group-details");
            Node fields = details.isVisible() ? details : card.lookup(".panel-group-summary");

            /*  the drawn extent of the fields, which includes fields reaching out of them  */
            double fieldsBottom = fields.localToScene(fields.getBoundsInLocal()).getMaxY();
            Bounds cardBounds = card.localToScene(card.getLayoutBounds());
            assertTrue(cardBounds.getMaxY() - fieldsBottom >= CARD_PADDING - 0.5, String.format(
                    "%s, %s: the fields end at %.1f, the card at %.1f", state, id, fieldsBottom, cardBounds.getMaxY()));

            Path line = (Path) card.lookup(".panel-group-line");
            LineTo end = (LineTo) line.getElements().get(line.getElements().size() - 1);
            double lineBottom = line.localToScene(0, end.getY()).getY();
            assertEquals(fieldsBottom, lineBottom, 0.5, state + ", " + id + ": the line ends with the fields");
        }
    }

    @Test
    void everyCardEnclosesItsFieldsFromTheStartAndAfterUnfoldingAnyOtherGroup() throws Exception {
        Set<Boolean> scrollBar = new HashSet<>();
        for (String first : GROUPS) {
            show(first);
            assertTheCardsEncloseTheirFields(first + " unfolded at the start");
            scrollBar.add(scrollBarShown());
            for (String then : GROUPS) {
                if (first.equals(then))
                    continue;
                unfold(then);
                assertTheCardsEncloseTheirFields(then + " unfolded after " + first);
                scrollBar.add(scrollBarShown());
                unfold(first);
                assertTheCardsEncloseTheirFields(first + " unfolded again after " + then);
            }
        }
        assertEquals(Set.of(true, false), scrollBar, "checked with and without the scroll bar of the panel");
    }

    @Test
    void theGenerationCardGrowsWithTheFieldsOfEachModelAndAnAttachedImage() throws Exception {
        show("mood");
        unfold("generationGroupPane");
        List<String> models = List.of("Nano Banana Pro", "FLUX 3 Image", "Gemini Omni");
        for (String model : models) {
            chooseModel(model);
            assertTheCardsEncloseTheirFields(model);
        }

        /*  an image in the square of the attachment, which then offers to add a second one  */
        File image = home.resolve("attachment.png").toFile();
        ImageIO.write(new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB), "png", image);
        chooseModel("Nano Banana Pro");
        MultiImageAttachmentPanel attachments = attachments();
        onFxThread(() -> attachments.attachImageToSlot(0, image));
        for (int i = 0; i < 100 && attachments.getAttachedImages().isEmpty(); i++)
            Thread.sleep(100);
        assertEquals(1, attachments.getAttachedImages().size(), "the image is attached");
        waitForLayout();
        assertTheCardsEncloseTheirFields("Nano Banana Pro with an image");
        for (String model : models) {
            chooseModel(model);
            assertTheCardsEncloseTheirFields(model + " after an image");
        }
    }

    @Test
    void everyCardEnclosesItsFieldsAfterAChangeOfLanguageAndTheme() throws Exception {
        for (String id : GROUPS) {
            show(id);
            onFxThread(() -> I18nManager.setLocale(Locale.GERMAN));
            assertTheCardsEncloseTheirFields(id + " in German");
            onFxThread(() -> ThemeManager.applyTheme("primer-light"));
            assertTheCardsEncloseTheirFields(id + " in the light theme");
            onFxThread(() -> {
                I18nManager.setLocale(Locale.ENGLISH);
                ThemeManager.applyTheme("primer-dark");
            });
            assertTheCardsEncloseTheirFields(id + " back in English and the dark theme");
        }
    }

    /** Whether a button shows a surface behind its icon in the given states, e.g. "hover" or "focused". */
    private static boolean surface(Button button, String... states) throws Exception {
        boolean[] shown = new boolean[1];
        onFxThread(() -> {
            for (String state : states)
                button.pseudoClassStateChanged(PseudoClass.getPseudoClass(state), true);
            button.applyCss();
            Background background = button.getBackground();
            shown[0] = background != null && background.getFills().stream()
                    .anyMatch((fill) -> !(fill.getFill() instanceof Color color) || color.getOpacity() > 0);
            for (String state : states)
                button.pseudoClassStateChanged(PseudoClass.getPseudoClass(state), false);
            button.applyCss();
        });
        return shown[0];
    }

    @Test
    void theFoldArrowShowsASurfaceOnlyWhileHoveredLikeTheActionsOfTheGroups() throws Exception {
        show("mood");
        Button action = (Button) group("moodGroupPane").lookup(".panel-group-action");
        assertNotNull(action, "the mood group has actions in its header");
        for (String id : GROUPS) {
            Button arrow = (Button) group(id).lookup(".panel-group-arrow");
            assertFalse(surface(arrow), id + ": no surface at rest");
            assertFalse(surface(arrow, "focused"), id + ": no surface while focused");
            assertTrue(surface(arrow, "hover"), id + ": the surface of the screenshot is the one while hovered");
            assertEquals(action.getBackground(), arrow.getBackground(), id + ": like the actions");
        }
    }
}
