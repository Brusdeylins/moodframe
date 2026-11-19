package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ConfigMocks;
import art.moodframe.config.ServiceFactory;
import art.moodframe.database.TestDatabase;
import art.moodframe.model.Chat;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.chat.ChatLibrary;
import art.moodframe.service.chat.ImageAttachmentService;
import art.moodframe.service.generation.GenerationService;
import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.FavoriteService;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.library.TestLibraryServices;
import art.moodframe.service.provider.MockProviders;
import art.moodframe.ui.component.MultiImageAttachmentPanel;
import art.moodframe.ui.component.StylePresetItem;
import art.moodframe.ui.support.ThemeManager;
import art.moodframe.util.I18nManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Slider;
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

/**
 * The cards of the groups of the right panel in the shown main window, on a throwaway {@code user.home} with the
 * standard library: each card encloses its shown fields and its line with its inner padding below, unfolded or
 * folded, after unfolding another group, after choosing another model, with an attached image, with and without the
 * scroll bar of the panel and after a change of language and theme. The fold arrow shows a surface only while
 * hovered, like the other icon buttons. The line of Generation runs straight, those of Mood and Frame bend inwards.
 * Every list of the panel opens flush with the left edge of its field, inside the panel. Folded, Generation sums up
 * count (or duration) and model and the attachments in two lines, no higher than Mood and Frame; unfolded, it shows
 * the model first, right below it the fields of the model, then count or duration and the attachment, 15 px apart.
 * The edit pencil of an image unfolds Generation like a click on its title; the paperclip and a failed edit do not.
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
        TestDatabase.open(home.resolve("db").resolve("moodframe.db").toString());
        LibraryEvents events = new LibraryEvents();
        blocks = TestLibraryServices.blocks(events);
        ColorStyleService colors = TestLibraryServices.palettes(events);
        register(LibraryEvents.class, events);
        register(PromptBlockService.class, blocks);
        register(ColorStyleService.class, colors);
        register(MoodService.class, TestLibraryServices.moods(blocks, colors, events));
        register(FrameService.class, TestLibraryServices.frames(events));
        register(FavoriteService.class, TestLibraryServices.favorites(events));
        ChatLibrary chats = new ChatLibrary(null, events);
        chats.load();
        register(ChatLibrary.class, chats);

        /*  tells the prompt limits of the models, as the providers know them  */
        register(GenerationService.class, new GenerationService(null, chats, null, null, MockProviders.preparing(),
                Runnable::run));
        register(ImageAttachmentService.class, new ImageAttachmentService());
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

        /*  a fresh configuration with the keys of BFL and Gemini, so that every model can be chosen  */
        ApplicationConfig config = ConfigMocks.withBflAndGemini();
        config.ui().setOpenPanelGroup(open);
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
        onFxThread(() -> controller.chooseChat(new Chat("chat")));
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

    private static String text(String id) {
        return ((Label) root.lookup("#" + id)).getText();
    }

    /*  the count (for a video the duration) and the model as the first line of the summary shows them  */
    @SuppressWarnings("unchecked")
    private static String countAndModel(String family) {
        ChoiceBox<String> duration = (ChoiceBox<String>) root.lookup("#videoDurationChoiceBox");
        if (family.equals("Gemini Omni"))
            return duration.getValue().replace("s", "") + " s · " + family;
        return Math.round(((Slider) root.lookup("#imageCountSlider")).getValue()) + " × " + family;
    }

    @Test
    void theFoldedGenerationCardSummarizesCountModelAndAttachmentsInTwoLinesNoHigherThanMoodAndFrame()
            throws Exception {
        show("");
        selectAChat();
        assertFalse(group("generationGroupPane").lookup(".panel-group-details").isVisible(), "folded");
        assertFalse(shown(root.lookup("#modelFamilyChoiceBox")), "the model is chosen in the folded fields");
        chooseModel("Nano Banana Pro");
        assertEquals(countAndModel("Nano Banana Pro"), text("generationSummaryModel"));
        assertEquals("No attachment", text("generationSummaryAttachments"));

        /*  no higher than the folded cards of Mood and Frame, whose summary is a field  */
        double generation = group("generationGroupPane").getHeight();
        for (String id : List.of("moodGroupPane", "frameGroupPane"))
            assertTrue(generation <= group(id).getHeight() + 0.5,
                    String.format("Generation %.1f, %s %.1f", generation, id, group(id).getHeight()));

        /*  the lines follow the count, the model and the duration of a video  */
        onFxThread(() -> ((Slider) root.lookup("#imageCountSlider")).setValue(3));
        assertEquals("3 × Nano Banana Pro", text("generationSummaryModel"));
        for (String model : MODELS) {
            chooseModel(model);
            assertEquals(countAndModel(model), text("generationSummaryModel"), model);
        }
        @SuppressWarnings("unchecked")
        ChoiceBox<String> duration = (ChoiceBox<String>) root.lookup("#videoDurationChoiceBox");
        String other = duration.getItems().stream().filter((item) -> !item.equals(duration.getValue())).findFirst()
                .orElseThrow();
        onFxThread(() -> duration.setValue(other));
        assertEquals(other.replace("s", "") + " s · Gemini Omni", text("generationSummaryModel"));
        assertEquals(other, ServiceFactory.getInstance().getService(ApplicationConfig.class).generation()
                .getDefaultVideoDuration(), "the next start takes the chosen duration");

        /*  and the attached images, also in German  */
        chooseModel("Nano Banana Pro");
        MultiImageAttachmentPanel attachments = attachments();
        for (int count = 1; count <= 2; count++) {
            File image = home.resolve("summary-" + count + ".png").toFile();
            ImageIO.write(new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB), "png", image);
            int slot = count - 1;
            onFxThread(() -> {

                /*  a second square, as the button "Add Image" adds it  */
                if (slot > 0) {
                    Method add = MultiImageAttachmentPanel.class.getDeclaredMethod("addNewSlot");
                    add.setAccessible(true);
                    add.invoke(attachments);
                }
                attachments.attachImageToSlot(slot, image);
            });
            for (int i = 0; i < 100 && attachments.getAttachmentCount() < count; i++)
                Thread.sleep(100);
            assertEquals(count, attachments.getAttachmentCount(), "the image is attached");
            waitForLayout();
        }
        assertEquals("2 attachments", text("generationSummaryAttachments"));
        onFxThread(() -> I18nManager.setLocale(Locale.GERMAN));
        assertEquals("2 Anhänge", text("generationSummaryAttachments"));
        assertEquals("1 × Nano Banana Pro", text("generationSummaryModel"), "an attachment sets the count to 1");
        onFxThread(() -> {
            Method remove = MultiImageAttachmentPanel.class.getDeclaredMethod("removeImageAt", int.class);
            remove.setAccessible(true);
            remove.invoke(attachments, 1);
        });
        waitForLayout();
        assertEquals("1 Anhang", text("generationSummaryAttachments"));
        onFxThread(attachments::clearAllAttachments);
        waitForLayout();
        assertEquals("Kein Anhang", text("generationSummaryAttachments"));
        assertTrue(group("generationGroupPane").getHeight() <= group("moodGroupPane").getHeight() + 0.5,
                "no higher in German either");
    }

    /*  the detail fields of Generation that show, top down, with the fields of BFL and FLUX.2 taken out of their
        containers  */
    private static List<Node> shownGenerationFields(Parent fields) {
        List<Node> shown = new ArrayList<>();
        for (Node field : fields.getChildrenUnmodifiable()) {
            if (!field.isVisible() || !field.isManaged())
                continue;
            if ("bflParametersContainer".equals(field.getId()) || "flux2ParametersContainer".equals(field.getId()))
                shown.addAll(shownGenerationFields((Parent) field));
            else
                shown.add(field);
        }
        return shown;
    }

    /*  a detail field of Generation by its id; the one holding the model as "model", the one of the seed as "seed"  */
    private static String fieldName(Node field) {
        if (field.lookup("#modelFamilyChoiceBox") != null)
            return "model";
        return field.lookup("#seedTextField") != null ? "seed" : field.getId();
    }

    @Test
    void theUnfoldedGenerationCardShowsTheModelItsFieldsCountOrDurationAndTheAttachmentInThisOrder()
            throws Exception {
        show("generation");
        String attachment = "multiImageAttachmentContainer";
        Map<String, List<String>> fields = Map.of(
                "Nano Banana Pro", List.of("model", "geminiAdvancedFeaturesContainer", "imageCountContainer",
                        attachment),
                "Nano Banana 2", List.of("model", "geminiAdvancedFeaturesContainer", "geminiThinkingLevelContainer",
                        "imageCountContainer", attachment),
                "FLUX 3 Image", List.of("model", "flux2SafetyContainer", "seed", "imageCountContainer", attachment),
                "FLUX.2 [pro]", List.of("model", "bflPromptUpsamplingCheckBox", "flux2SafetyContainer", "seed",
                        "imageCountContainer", attachment),
                "FLUX.2 [flex]", List.of("model", "bflPromptUpsamplingCheckBox", "flux2SafetyContainer",
                        "flux2GuidanceContainer", "flux2StepsContainer", "seed", "imageCountContainer", attachment),
                "FLUX.2 [max]", List.of("model", "flux2GroundingSearchContainer", "bflPromptUpsamplingCheckBox",
                        "flux2SafetyContainer", "seed", "imageCountContainer", attachment),
                "Gemini Omni", List.of("model", "videoParametersContainer", attachment));
        Parent details = (Parent) group("generationGroupPane").lookup(".panel-group-details");
        for (Map.Entry<String, List<String>> expected : fields.entrySet()) {
            String model = expected.getKey();
            chooseModel(model);
            List<Node> shown = shownGenerationFields(details);
            assertEquals(expected.getValue(), shown.stream().map(MainWindowPanelLayoutTest::fieldName).toList(), model);

            /*  one below the other, 15 px apart like the rest of the folded fields  */
            for (int i = 1; i < shown.size(); i++) {
                Node above = shown.get(i - 1);
                Node below = shown.get(i);
                double gap = below.localToScene(below.getLayoutBounds()).getMinY()
                        - above.localToScene(above.getLayoutBounds()).getMaxY();
                assertEquals(15.0, gap, 0.5, model + ": " + fieldName(above) + " to " + fieldName(below));
            }
            assertTrue(shown(root.lookup("#generationSummaryModel")), model + ": the summary stays");
            assertEquals(countAndModel(model), text("generationSummaryModel"), model);
        }

        /*  the model under its heading, Google Search grounding as its first field  */
        assertEquals("Model", text("modelLabel"));
        chooseModel("Nano Banana Pro");
        Node grounding = root.lookup("#geminiAdvancedFeaturesContainer").lookup(".check-box");
        assertEquals("Google Search Grounding", ((CheckBox) grounding).getText());
        onFxThread(() -> I18nManager.setLocale(Locale.GERMAN));
        assertEquals("Modell", text("modelLabel"));
    }

    private static boolean unfolded(String id) {
        return group(id).lookup(".panel-group-details").isVisible();
    }

    /*  the edit pencil of an image, as the cards, the grid and the large preview call it  */
    private static void edit(File image) throws Exception {
        onFxThread(() -> controller.editImage(image.getPath()));
    }

    /*  waits until the attachment holds the given number of images and the window is laid out  */
    private static void awaitAttachments(MultiImageAttachmentPanel attachments, int count) throws Exception {
        for (int i = 0; i < 100 && attachments.getAttachmentCount() < count; i++)
            Thread.sleep(100);
        assertEquals(count, attachments.getAttachmentCount(), "the images are attached");
        waitForLayout();
    }

    @Test
    void theEditPencilUnfoldsGenerationLikeAClickOnItsTitleWhileThePaperclipAndAFailedEditLeaveTheGroups()
            throws Exception {
        show("mood");
        chooseModel("Nano Banana Pro");
        ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        MultiImageAttachmentPanel attachments = attachments();
        File image = home.resolve("edit.png").toFile();
        ImageIO.write(new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB), "png", image);

        /*  the paperclip attaches the image chosen in its file dialog to the square  */
        onFxThread(() -> attachments.attachImageToSlot(0, image));
        awaitAttachments(attachments, 1);
        assertTrue(unfolded("moodGroupPane"), "the paperclip leaves Mood unfolded");
        assertFalse(unfolded("generationGroupPane"), "the paperclip leaves Generation folded");
        assertEquals("mood", config.ui().getOpenPanelGroup(), "the paperclip leaves the remembered group");

        /*  the edit pencil attaches the image and unfolds Generation  */
        onFxThread(attachments::clearAllAttachments);
        edit(image);
        awaitAttachments(attachments, 1);
        assertTrue(unfolded("generationGroupPane"), "the edit pencil unfolds Generation");
        assertFalse(unfolded("moodGroupPane"), "the edit pencil folds Mood");
        assertFalse(unfolded("frameGroupPane"), "the edit pencil folds Frame");
        assertEquals("generation", config.ui().getOpenPanelGroup(), "remembered like a click on the title");
        assertTheCardsEncloseTheirFields("Generation unfolded by the edit pencil");

        /*  an edit that attaches nothing changes no group: a missing image, or the attachment is full  */
        unfold("moodGroupPane");
        edit(home.resolve("missing.png").toFile());
        assertTrue(unfolded("moodGroupPane"), "an edit of a missing image leaves Mood unfolded");
        onFxThread(() -> {
            Method add = MultiImageAttachmentPanel.class.getDeclaredMethod("addNewSlot");
            add.setAccessible(true);
            add.invoke(attachments);
            attachments.attachImageToSlot(1, image);
        });
        awaitAttachments(attachments, 2);
        Field max = MultiImageAttachmentPanel.class.getDeclaredField("maxImages");
        max.setAccessible(true);
        onFxThread(() -> ((IntegerProperty) max.get(attachments)).set(2));
        edit(image);
        assertEquals(2, attachments.getAttachmentCount(), "the full attachment takes no further image");
        assertTrue(unfolded("moodGroupPane"), "an edit into the full attachment leaves Mood unfolded");
        assertFalse(unfolded("generationGroupPane"), "an edit into the full attachment leaves Generation folded");
        assertEquals("mood", config.ui().getOpenPanelGroup(), "a failed edit leaves the remembered group");
    }
}
