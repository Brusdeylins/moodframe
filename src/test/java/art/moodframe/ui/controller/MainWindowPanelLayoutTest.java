package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.database.DatabaseConfig;
import art.moodframe.model.Chat;
import art.moodframe.model.PromptBlock;
import art.moodframe.model.StylePresetItem;
import art.moodframe.service.ColorStyleService;
import art.moodframe.service.FavoriteService;
import art.moodframe.service.FrameService;
import art.moodframe.service.LibraryEvents;
import art.moodframe.service.MoodService;
import art.moodframe.service.PromptBlockService;
import art.moodframe.ui.component.MultiImageAttachmentPanel;
import art.moodframe.util.I18nManager;
import art.moodframe.util.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.text.Text;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * The cards of the groups of the right panel in the shown main window, on a throwaway {@code user.home} with the
 * standard library: each card encloses its shown fields and its line with its inner padding below, unfolded or
 * folded, after unfolding another group, after choosing another model, with an attached image, with and without the
 * scroll bar of the panel and after a change of language and theme. The fold arrow shows a surface only while
 * hovered, like the other icon buttons. The line of Generation runs straight, those of Mood and Frame bend inwards.
 * Every list of the panel opens flush with the left edge of its field, inside the panel.
 */
class MainWindowPanelLayoutTest {

    /*  the inner padding of a card below its fields (see .panel-group in main-window.css)  */
    private static final double CARD_PADDING = 12.0;

    /*  how much further in the detail fields of Mood and Frame stand than their summary field (see main-window.css)  */
    private static final double DETAILS_INDENT = 16.0;

    private static final List<String> GROUPS = List.of("moodGroupPane", "frameGroupPane", "generationGroupPane");

    /*  models with different fields: the reasoning of Nano Banana 2, the fields of FLUX, the duration of a video  */
    private static final List<String> MODELS = List.of("Nano Banana Pro", "Nano Banana 2", "FLUX 3 Image",
            "Gemini Omni");

    @TempDir
    static java.nio.file.Path home;

    private static String realHome;
    private static String userAgentStylesheet;
    private static Locale locale;
    private static ApplicationConfig realConfig;
    private static final Map<Class<?>, Object> realServices = new HashMap<>();
    private static PromptBlockService blocks;
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

        /*  the standard library in the throwaway home, with the services of the panel as the application has them  */
        DatabaseConfig.initialize(home.resolve("db").resolve("moodframe.db").toString());
        LibraryEvents events = new LibraryEvents();
        blocks = new PromptBlockService(events);
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
        onFxThread(() -> Application.setUserAgentStylesheet(userAgentStylesheet));
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

    /*  the left edge of a node on the screen  */
    private static double left(Node node) {
        return node.localToScreen(node.getLayoutBounds()).getMinX();
    }

    @Test
    void theLineOfGenerationRunsStraightBesideItsFieldsWhileMoodAndFrameBendInwards() throws Exception {
        show("mood");
        for (String id : GROUPS) {
            unfold(id);
            VBox card = group(id);
            Path line = (Path) card.lookup(".panel-group-line");
            Set<Double> xs = new HashSet<>();
            for (PathElement element : line.getElements())
                xs.add(element instanceof MoveTo move ? move.getX() : ((LineTo) element).getX());
            Node summary = ((VBox) card.lookup(".panel-group-summary")).getChildren().stream().filter(Node::isVisible)
                    .findFirst().orElseThrow();
            List<Node> details = ((VBox) card.lookup(".panel-group-details")).getChildren().stream()
                    .filter(Node::isManaged).toList();
            boolean straight = id.equals("generationGroupPane");
            assertEquals(straight ? 1 : 2, xs.size(), id + ": the line runs on " + xs);
            for (Node field : details)
                assertEquals(left(summary) + (straight ? 0 : DETAILS_INDENT), left(field), 0.5,
                        id + ": the detail field " + field + " stands on " + (straight ? "" : "the indent of ")
                                + "the summary field");
        }
    }

    /*  a chat as if chosen in the list, without which the fields of the panel are off  */
    private static void selectAChat() throws Exception {
        onFxThread(() -> {
            Field chat = MainWindowController.class.getDeclaredField("currentChat");
            chat.setAccessible(true);
            chat.set(controller, new Chat("chat"));
            Method update = MainWindowController.class.getDeclaredMethod("updateUIState");
            update.setAccessible(true);
            update.invoke(controller);
        });
    }

    /*  whether a node shows, with all its parents  */
    private static boolean shown(Node node) {
        for (Node n = node; n != null; n = n.getParent())
            if (!n.isVisible())
                return false;
        return true;
    }

    /*  the fields of a group that show with a list: combo boxes and choice boxes  */
    private static List<Control> listFields(String id) {
        List<Control> fields = new ArrayList<>();
        for (String kind : List.of(".combo-box", ".choice-box"))
            group(id).lookupAll(kind).stream().filter(MainWindowPanelLayoutTest::shown)
                    .forEach((field) -> fields.add((Control) field));
        return fields;
    }

    /** The open list of a field: where it shows on the screen and the names of its entries that end in "…". */
    private record OpenList(Bounds bounds, Set<String> cut) {
    }

    /** Opens the list of a field, notes where it shows and which names it cuts, and closes it again. */
    private static OpenList openList(Control field) throws Exception {
        assertFalse(field.isDisabled(), field.getId() + " can be opened");
        onFxThread(() -> {
            if (field instanceof ComboBox<?> comboBox)
                comboBox.show();
            else
                ((ChoiceBox<?>) field).show();
        });
        AtomicReference<OpenList> shown = new AtomicReference<>();
        onFxThread(() -> {
            PopupWindow popup = List.copyOf(Window.getWindows()).stream()
                    .filter((window) -> window instanceof PopupWindow candidate && candidate.isShowing()
                            && candidate.getOwnerWindow() == stage && !(candidate instanceof Tooltip))
                    .map((window) -> (PopupWindow) window).findFirst().orElseThrow();
            Node list = popup instanceof ContextMenu menu ? menu.getSkin().getNode()
                    : popup.getScene().getRoot().lookup(".list-view");
            Bounds bounds = list.localToScreen(list.getLayoutBounds());

            /*  the rows of a combo box show some at a time: scrolled through, each laid out at once  */
            Set<String> cut = new TreeSet<>();
            if (list instanceof ListView<?> rows)
                for (int index = 0; index < rows.getItems().size(); index += 5) {
                    rows.scrollTo(index);
                    rows.layout();
                    for (Node row : rows.lookupAll(".list-cell"))
                        for (Node name : row.lookupAll(".label"))
                            if (name.lookup(".text") instanceof Text text && text.getText().endsWith("…"))
                                cut.add(((Label) name).getText());
                }
            shown.set(new OpenList(bounds, cut));
            if (field instanceof ComboBox<?> comboBox)
                comboBox.hide();
            else
                ((ChoiceBox<?>) field).hide();
        });
        return shown.get();
    }

    @Test
    void everyListOfThePanelOpensFlushWithItsFieldAndInsideThePanelInEnglishAndGerman() throws Exception {
        show("mood");
        selectAChat();
        Bounds panel = root.lookup("#panelScrollPane").localToScreen(root.lookup("#panelScrollPane")
                .getLayoutBounds());
        Set<String> opened = new TreeSet<>();
        Set<String> cut = new TreeSet<>();
        for (Locale language : List.of(Locale.ENGLISH, Locale.GERMAN)) {
            onFxThread(() -> I18nManager.setLocale(language));
            for (String id : GROUPS) {
                unfold(id);
                for (String model : id.equals("generationGroupPane") ? MODELS : List.of("")) {
                    if (!model.isEmpty())
                        chooseModel(model);
                    for (Control field : listFields(id)) {
                        String where = language + ", " + model + " " + field.getId();
                        Bounds bounds = field.localToScreen(field.getLayoutBounds());
                        OpenList list = openList(field);
                        assertEquals(bounds.getMinX(), list.bounds().getMinX(), 1, where + ": flush with the field");
                        assertTrue(list.bounds().getMaxX() <= bounds.getMaxX() + 1,
                                where + ": no wider than the field: " + list.bounds().getWidth());
                        assertTrue(list.bounds().getMinX() >= panel.getMinX() - 0.5
                                && list.bounds().getMaxX() <= panel.getMaxX() + 0.5, where + ": inside the panel");
                        opened.add(field.getId());
                        list.cut().forEach((name) -> cut.add(where + ": " + name));
                    }
                }
            }
        }
        assertEquals(Set.of("moodComboBox", "atmosphereComboBox", "stylePresetsComboBox", "colorStyleComboBox",
                "lightComboBox", "cameraComboBox", "frameComboBox", "aspectRatioComboBox", "resolutionChoiceBox",
                "modelFamilyChoiceBox", "videoDurationChoiceBox", "geminiThinkingLevelChoiceBox"), opened);
        assertEquals(Set.of(), cut, "every standard entry fits into its field");
    }

    @Test
    void aBuildingBlockOfTheUserWithALongNameIsCutToItsFieldAndShowsInFullInItsTooltip() throws Exception {
        String name = "Soft morning haze over a quiet harbour with fishing boats, gulls and wet pebbles";
        assertEquals(80, name.length());
        PromptBlock block = new PromptBlock(null, PromptBlock.Type.STYLE, name, "a style of the user", null, true);
        assertTrue(blocks.addCustom(block));
        try {
            show("mood");
            selectAChat();
            @SuppressWarnings("unchecked")
            ComboBox<StylePresetItem> field = (ComboBox<StylePresetItem>) root.lookup("#stylePresetsComboBox");
            StylePresetItem item = field.getItems().stream().filter((entry) -> name.equals(entry.getName()))
                    .findFirst().orElseThrow();
            onFxThread(() -> field.setValue(item));
            Bounds bounds = field.localToScreen(field.getLayoutBounds());
            onFxThread(field::show);
            AtomicReference<Parent> popup = new AtomicReference<>();
            onFxThread(() -> {
                popup.set(((PopupWindow) List.copyOf(Window.getWindows()).stream()
                        .filter((window) -> window instanceof PopupWindow candidate && candidate.isShowing()
                                && candidate.getOwnerWindow() == stage).findFirst().orElseThrow())
                        .getScene().getRoot());
                ((ListView<?>) popup.get().lookup(".list-view")).scrollTo(field.getItems().indexOf(item));
            });
            AtomicReference<Label> label = new AtomicReference<>();
            onFxThread(() -> {
                Node list = popup.get().lookup(".list-view");
                Bounds shown = list.localToScreen(list.getLayoutBounds());
                assertEquals(bounds.getWidth(), shown.getWidth(), 1, "the list is as wide as the field");
                assertEquals(bounds.getMinX(), shown.getMinX(), 1, "the list starts flush with the field");
                ListCell<?> row = popup.get().lookupAll(".list-cell").stream().map((node) -> (ListCell<?>) node)
                        .filter((cell) -> cell.getItem() == item).findFirst().orElseThrow();
                label.set((Label) row.lookup(".label"));
            });
            assertTrue(((Text) label.get().lookup(".text")).getText().endsWith("…"), "the name ends in \"…\"");
            onFxThread(() -> {
                Bounds center = label.get().localToScene(label.get().getLayoutBounds());
                Event.fireEvent(label.get(), new MouseEvent(MouseEvent.MOUSE_ENTERED, center.getCenterX(),
                        center.getCenterY(), center.getCenterX(), center.getCenterY(), MouseButton.NONE, 0, false,
                        false, false, false, false, false, false, false, false, false, null));
            });
            assertNotNull(label.get().getTooltip(), "the cut name has a tooltip");
            assertEquals(name, label.get().getTooltip().getText());
            onFxThread(field::hide);
        }
        finally {
            blocks.deleteCustom(block.getId());
            blocks.deletePermanently(block.getId());
        }
    }
}
