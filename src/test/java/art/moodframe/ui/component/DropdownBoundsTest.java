package art.moodframe.ui.component;

import art.moodframe.model.PromptBlock;
import art.moodframe.model.StylePresetItem;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The open list of a field at the right edge of a window, shown on the Monocle headless platform. */
class DropdownBoundsTest {

    /*  the window: 400 wide at x 500, the field 150 wide at its right edge with 10 to spare  */
    private static final double WINDOW_X = 500;
    private static final double WINDOW_WIDTH = 400;
    private static final double FIELD_WIDTH = 150;

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await(10, TimeUnit.SECONDS);

        /*  closing the window of the test must not end the toolkit, which the following tests share  */
        Platform.setImplicitExit(false);
    }

    /** Runs a step on the JavaFX thread and waits for it and for what it left for later. */
    private static void onFxThread(Runnable step) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(step);
        Platform.runLater(() -> Platform.runLater(done::countDown));
        assertTrue(done.await(10, TimeUnit.SECONDS));
    }

    private static StylePresetItem entry(String name) {
        return new StylePresetItem(new PromptBlock(PromptBlock.STANDARD_PREFIX + name, PromptBlock.Type.ATMOSPHERE,
                name, name + " fragment", null, true));
    }

    /*  a window at the right of which the field fills a column, as in the right panel  */
    private static Stage window(Region field) {
        field.setMinWidth(0);
        field.setMaxWidth(Double.MAX_VALUE);
        VBox column = new VBox(field);
        column.setMinWidth(FIELD_WIDTH);
        column.setMaxWidth(FIELD_WIDTH);
        StackPane root = new StackPane(column);
        StackPane.setAlignment(column, Pos.TOP_RIGHT);
        root.setStyle("-fx-padding: 10;");
        Stage stage = new Stage();
        stage.setScene(new Scene(root, WINDOW_WIDTH, 300));
        stage.setX(WINDOW_X);
        stage.setY(100);
        stage.show();
        return stage;
    }

    /*  the popup of a window shown at the moment, laid out  */
    private static PopupWindow shownPopup(Window owner) {
        PopupWindow popup = List.copyOf(Window.getWindows()).stream()
                .filter((candidate) -> candidate instanceof PopupWindow shown && shown.isShowing()
                        && shown.getOwnerWindow() == owner)
                .map((candidate) -> (PopupWindow) candidate).findFirst().orElseThrow();
        popup.getScene().getRoot().applyCss();
        popup.getScene().getRoot().layout();
        return popup;
    }

    private static Bounds onScreen(Node node) {
        return node.localToScreen(node.getLayoutBounds());
    }

    /*  opens a field with star rows of the given names, at the right of a window  */
    private static ComboBox<StylePresetItem> openField(AtomicReference<Stage> stage, AtomicReference<Parent> list,
            String... names) throws Exception {
        ComboBox<StylePresetItem> comboBox = new ComboBox<>();
        FavoriteStar.Favorites<StylePresetItem> starred = new FavoriteStar.Favorites<>(comboBox,
                (item) -> Set.of(names).contains(item.getName()), (item) -> {
                });
        onFxThread(() -> {
            comboBox.setCellFactory((listView) -> new StylePresetListCell(null, starred));
            comboBox.setButtonCell(new StylePresetListCell());
            comboBox.getItems().setAll(List.of(names).stream().map(DropdownBoundsTest::entry).toList());
            comboBox.setValue(comboBox.getItems().get(0));
            DropdownBounds.keepInWindow(comboBox);
            stage.set(window(comboBox));
            comboBox.show();
            list.set(shownPopup(stage.get()).getScene().getRoot());
        });

        /*  the list takes its new width in the next layout  */
        onFxThread(() -> {
            list.get().applyCss();
            list.get().layout();
        });
        return comboBox;
    }

    @Test
    void theListIsAsWideAsItsRowsNeedButAtLeastTheFieldAndAtMostTheRoom() {
        assertEquals(300, DropdownBounds.width(150, 300, 400));
        assertEquals(400, DropdownBounds.width(150, 900, 400), "at most the room left of the field");
        assertEquals(150, DropdownBounds.width(150, 80, 400), "at least the field");
        assertEquals(150, DropdownBounds.width(150, 900, 100), "the field even without room");
    }

    @Test
    void aWiderListEndsFlushWithTheFieldAndGrowsToTheLeft() throws Exception {
        AtomicReference<Stage> stage = new AtomicReference<>();
        AtomicReference<Parent> popup = new AtomicReference<>();
        ComboBox<StylePresetItem> comboBox = openField(stage, popup, "Calm",
                "A rather long atmosphere name that needs room");
        Node list = popup.get().lookup(".list-view");
        Bounds field = onScreen(comboBox);
        Bounds shown = onScreen(list);

        assertTrue(shown.getWidth() > field.getWidth() + 1, "the list is wider than the field: " + shown.getWidth());
        assertEquals(field.getMaxX(), shown.getMaxX(), 1, "the list ends flush with the field");
        assertTrue(shown.getMinX() >= WINDOW_X + DropdownBounds.MARGIN - 1, "the list stays inside the window");
        assertTrue(shown.getMinY() >= field.getMaxY() - 1, "the list opens below the field");
        close(stage.get());
    }

    @Test
    void aListOfShortNamesIsAsWideAsTheField() throws Exception {
        AtomicReference<Stage> stage = new AtomicReference<>();
        AtomicReference<Parent> popup = new AtomicReference<>();
        ComboBox<StylePresetItem> comboBox = openField(stage, popup, "Calm", "Dark");
        Bounds field = onScreen(comboBox);
        Bounds shown = onScreen(popup.get().lookup(".list-view"));

        assertEquals(field.getWidth(), shown.getWidth(), 1);
        assertEquals(field.getMinX(), shown.getMinX(), 1);
        close(stage.get());
    }

    @Test
    void aNameBeyondTheRoomIsCutAndShowsInFullInItsTooltip() throws Exception {
        String longName = "An atmosphere name so long that it cannot fit into the window at all, not even close to it";
        AtomicReference<Stage> stage = new AtomicReference<>();
        AtomicReference<Parent> popup = new AtomicReference<>();
        ComboBox<StylePresetItem> comboBox = openField(stage, popup, "Calm", longName);
        Bounds field = onScreen(comboBox);
        Bounds shown = onScreen(popup.get().lookup(".list-view"));

        assertEquals(field.getMaxX() - WINDOW_X - DropdownBounds.MARGIN, shown.getWidth(), 1,
                "the list fills the room left of the field");
        assertEquals(field.getMaxX(), shown.getMaxX(), 1);
        ListCell<?> row = popup.get().lookupAll(".list-cell").stream().map((node) -> (ListCell<?>) node)
                .filter((cell) -> cell.getItem() != null && longName.equals(cell.getItem().toString()))
                .findFirst().orElse(null);
        assertNotNull(row);
        Label name = (Label) row.lookup(".label");
        Node star = row.lookup(".favorite-star");
        assertTrue(onScreen(star).getMaxX() <= shown.getMaxX(), "the star shows in the list");

        onFxThread(() -> enter(name));
        assertNotNull(name.getTooltip(), "the cut name has a tooltip");
        assertEquals(longName, name.getTooltip().getText());

        /*  a name that fits needs none  */
        Label calm = (Label) popup.get().lookupAll(".list-cell").stream().map((node) -> (ListCell<?>) node)
                .filter((cell) -> cell.getItem() != null && "Calm".equals(cell.getItem().toString()))
                .findFirst().orElseThrow().lookup(".label");
        onFxThread(() -> enter(calm));
        assertNull(calm.getTooltip());
        close(stage.get());
    }

    /*  opens a choice of the given entries at the right of a window, laid out as after the next pulse  */
    private static ContextMenu openChoice(AtomicReference<Stage> stage, ChoiceBox<String> choiceBox,
            String... entries) throws Exception {
        AtomicReference<ContextMenu> menu = new AtomicReference<>();
        onFxThread(() -> {
            choiceBox.getItems().setAll(entries);
            choiceBox.setValue(entries[0]);
            DropdownBounds.keepInWindow(choiceBox);
            stage.set(window(choiceBox));
            choiceBox.show();
            menu.set((ContextMenu) shownPopup(stage.get()));
        });

        /*  the skin sized the field to its entries while it opened the menu, the layout gives its width back  */
        onFxThread(() -> {
            stage.get().getScene().getRoot().layout();
            menu.get().getScene().getRoot().applyCss();
            menu.get().getScene().getRoot().layout();
        });
        return menu.get();
    }

    @Test
    void theMenuOfAChoiceEndsFlushWithTheField() throws Exception {
        ChoiceBox<String> choiceBox = new ChoiceBox<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        ContextMenu menu = openChoice(stage, choiceBox, "16:9", "A choice somewhat wider than its field");
        Bounds field = onScreen(choiceBox);
        Bounds shown = onScreen(menu.getSkin().getNode());

        assertEquals(FIELD_WIDTH, field.getWidth(), 1);
        assertTrue(shown.getWidth() > field.getWidth() + 1, "the menu is wider than the field: " + shown.getWidth());
        assertEquals(field.getMaxX(), shown.getMaxX(), 1, "the menu ends flush with the field");
        close(stage.get());
    }

    @Test
    void theMenuOfShortChoicesIsAsWideAsTheField() throws Exception {
        ChoiceBox<String> choiceBox = new ChoiceBox<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        ContextMenu menu = openChoice(stage, choiceBox, "1K", "2K", "4K");
        Bounds field = onScreen(choiceBox);
        Bounds shown = onScreen(menu.getSkin().getNode());

        assertEquals(FIELD_WIDTH, field.getWidth(), 1);
        assertEquals(field.getMinX(), shown.getMinX(), 1);
        assertEquals(field.getMaxX(), shown.getMaxX(), 1);
        close(stage.get());
    }

    /*  closes the window of a test with its open list, which the next test must not find  */
    private static void close(Stage stage) throws Exception {
        onFxThread(() -> {
            List.copyOf(Window.getWindows()).stream()
                    .filter((window) -> window instanceof PopupWindow popup && popup.getOwnerWindow() == stage)
                    .forEach(Window::hide);
            stage.close();
        });
    }

    /*  the pointer entering a node  */
    private static void enter(Node node) {
        Bounds bounds = node.localToScene(node.getLayoutBounds());
        Event.fireEvent(node, new MouseEvent(MouseEvent.MOUSE_ENTERED, bounds.getCenterX(), bounds.getCenterY(),
                bounds.getCenterX(), bounds.getCenterY(), MouseButton.NONE, 0, false, false, false, false, false,
                false, false, false, false, false, null));
    }
}
