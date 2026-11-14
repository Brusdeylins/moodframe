package art.moodframe.ui.component;

import art.moodframe.model.PromptBlock;
import art.moodframe.model.StylePresetItem;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Clicks the favorite star in the open list of a field, shown on the Monocle headless platform. */
class FavoriteStarTest {

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

    private static StylePresetItem entry(String name, boolean standard) {
        return new StylePresetItem(new PromptBlock((standard ? PromptBlock.STANDARD_PREFIX : "id-") + name,
                PromptBlock.Type.ATMOSPHERE, name, name + " fragment", null, true));
    }

    /*  a mouse event of the primary button, fired at the center of a node  */
    private static void click(Node node, EventType<MouseEvent> type) {
        Bounds bounds = node.localToScene(node.getLayoutBounds());
        Event.fireEvent(node, new MouseEvent(type, bounds.getCenterX(), bounds.getCenterY(), bounds.getCenterX(),
                bounds.getCenterY(), MouseButton.PRIMARY, 1, false, false, false, false,
                type == MouseEvent.MOUSE_PRESSED, false, false, false, false, true, null));
    }

    @Test
    void theStarSwitchesTheFavoriteWithoutChoosingTheRowOrClosingTheList() throws Exception {
        StylePresetItem none = new StylePresetItem("None", "", false);
        StylePresetItem calm = entry("Calm", true);
        StylePresetItem mine = entry("Mine", false);
        Set<String> favorites = new CopyOnWriteArraySet<>(Set.of("Calm"));
        List<String> toggled = new CopyOnWriteArrayList<>();
        ComboBox<StylePresetItem> comboBox = new ComboBox<>();
        FavoriteStar.Favorites<StylePresetItem> starred = new FavoriteStar.Favorites<>(comboBox,
                (item) -> favorites.contains(item.getName()), (item) -> toggled.add(item.getName()));
        AtomicReference<Stage> stage = new AtomicReference<>();
        AtomicReference<Parent> popup = new AtomicReference<>();

        onFxThread(() -> {
            comboBox.setCellFactory((listView) -> new StylePresetListCell(null, starred));
            comboBox.setButtonCell(new StylePresetListCell());
            comboBox.getItems().setAll(none, calm, mine);
            comboBox.setValue(none);
            stage.set(new Stage());
            stage.get().setScene(new Scene(new StackPane(comboBox), 300, 200));
            stage.get().show();
            comboBox.show();
            Window window = List.copyOf(Window.getWindows()).stream()
                    .filter((candidate) -> candidate instanceof PopupWindow && candidate.isShowing())
                    .findFirst().orElseThrow();
            popup.set(window.getScene().getRoot());
            popup.get().applyCss();
            popup.get().layout();
        });
        ListCell<?> noneRow = row(popup.get(), none);
        ListCell<?> calmRow = row(popup.get(), calm);
        ListCell<?> mineRow = row(popup.get(), mine);
        FavoriteStar<?> calmStar = (FavoriteStar<?>) calmRow.lookup(".favorite-star");
        FavoriteStar<?> mineStar = (FavoriteStar<?>) mineRow.lookup(".favorite-star");

        /*  a filled star always, a hollow one only on hover, none for "None"; the icon column keeps its space  */
        assertTrue(calmStar.isVisible());
        assertFalse(mineStar.isVisible());
        assertFalse(noneRow.lookup(".favorite-star").isVisible());
        assertEquals(nameX(calmRow), nameX(mineRow), 0.5, "the names of a standard and an own entry are flush");
        assertEquals(nameX(calmRow), nameX(noneRow), 0.5, "\"None\" is flush with the entries");

        onFxThread(() -> {
            click(mineStar, MouseEvent.MOUSE_PRESSED);
            click(mineStar, MouseEvent.MOUSE_RELEASED);
            click(mineStar, MouseEvent.MOUSE_CLICKED);
        });
        assertEquals(List.of("Mine"), toggled);
        assertEquals(none, comboBox.getValue(), "the row of the star is not chosen");
        assertTrue(comboBox.isShowing(), "the list stays open");

        /*  a click on the row itself chooses and closes as before  */
        onFxThread(() -> {
            click(mineRow, MouseEvent.MOUSE_PRESSED);
            click(mineRow, MouseEvent.MOUSE_RELEASED);
        });
        assertEquals(mine, comboBox.getValue());
        assertFalse(comboBox.isShowing());
        onFxThread(() -> stage.get().close());
    }

    private static ListCell<?> row(Parent popup, StylePresetItem item) {
        ListCell<?> row = popup.lookupAll(".list-cell").stream().map((node) -> (ListCell<?>) node)
                .filter((cell) -> cell.getItem() == item).findFirst().orElse(null);
        assertNotNull(row, item.getName());
        return row;
    }

    /*  where the name of a row starts, in the coordinates of the list  */
    private static double nameX(ListCell<?> row) {
        Node name = row.lookup(".label");
        return name.localToScene(name.getLayoutBounds()).getMinX();
    }
}
