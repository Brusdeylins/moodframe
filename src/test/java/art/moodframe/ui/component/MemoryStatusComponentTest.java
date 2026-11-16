package art.moodframe.ui.component;

import art.moodframe.util.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tooltips of the displays in the status bar, shown on the Monocle headless platform. */
class MemoryStatusComponentTest {

    /*  the key under which a node keeps its installed tooltip  */
    private static final String TOOLTIP_KEY = "javafx.scene.control.Tooltip";

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

    private static <T> T onFxThread(Supplier<T> step) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(step.get());
            }
            catch (Throwable e) {
                failure.set(e);
            }
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() != null)
            throw new AssertionError(failure.get());
        return result.get();
    }

    /*  waits until a condition holds on the JavaFX thread, at most for a time  */
    private static boolean await(Supplier<Boolean> condition, long millis) throws Exception {
        long until = System.currentTimeMillis() + millis;
        while (!onFxThread(condition)) {
            if (System.currentTimeMillis() > until)
                return false;
            Thread.sleep(20);
        }
        return true;
    }

    /*  the pointer moving over, entering, leaving or pressing on the middle of a node  */
    private static void mouse(Node node, EventType<MouseEvent> type) {
        Bounds scene = node.localToScene(node.getLayoutBounds());
        Bounds screen = node.localToScreen(node.getLayoutBounds());
        Event.fireEvent(node, new MouseEvent(type, scene.getCenterX(), scene.getCenterY(), screen.getCenterX(),
                screen.getCenterY(), MouseButton.PRIMARY, 1, false, false, false, false, false, false, false, false,
                false, false, null));
    }

    /*  the label which draws a shown tooltip  */
    private static Label skin(Tooltip tooltip) {
        return (Label) tooltip.getSkin().getNode();
    }

    @Test
    void theMemoryTooltipShowsWithinHalfASecondAndStaysAcrossAnUpdate() throws Exception {
        AtomicReference<MemoryStatusComponent> status = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        AtomicReference<HBox> group = new AtomicReference<>();
        AtomicReference<Label> label = new AtomicReference<>();
        AtomicLong shownAfter = new AtomicLong(-1);
        AtomicInteger hidden = new AtomicInteger();
        Tooltip tooltip = onFxThread(() -> {
            status.set(new MemoryStatusComponent());
            stage.set(new Stage());
            stage.get().setScene(new Scene(new StackPane(status.get()), 400, 60));
            stage.get().show();
            group.set((HBox) status.get().getChildren().get(0));
            label.set((Label) group.get().getChildren().get(1));
            Tooltip installed = (Tooltip) group.get().getProperties().get(TOOLTIP_KEY);
            long moved = System.nanoTime();

            /*  the first tooltip ever shows and hides once invisibly (opacity 0) to apply its CSS  */
            installed.showingProperty().addListener((observable, was, showing) -> {
                if (installed.getOpacity() == 0)
                    return;
                if (showing && shownAfter.get() < 0)
                    shownAfter.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - moved));
                else if (!showing)
                    hidden.incrementAndGet();
            });
            mouse(label.get(), MouseEvent.MOUSE_MOVED);
            return installed;
        });
        try {
            assertTrue(await(tooltip::isShowing, 2000), "the tooltip shows");
            assertTrue(shownAfter.get() <= 700, "the tooltip shows after " + shownAfter.get() + " ms");

            /*  an outdated text, which the next update (every 2 s) replaces in the tooltip already shown  */
            onFxThread(() -> {
                tooltip.setText("outdated");
                return null;
            });
            assertTrue(await(() -> !"outdated".equals(tooltip.getText()), 3000), "the text follows the update");
            onFxThread(() -> {
                assertTrue(tooltip.getText().startsWith("Memory: "), tooltip.getText());
                assertSame(tooltip, group.get().getProperties().get(TOOLTIP_KEY));
                assertTrue(tooltip.isShowing(), "the tooltip stays shown across the update");
                assertEquals(0, hidden.get(), "the tooltip never flickered");
                return null;
            });
        }
        finally {
            /*  a press closes the tooltip and resets the timers, which all tooltips share  */
            onFxThread(() -> {
                mouse(label.get(), MouseEvent.MOUSE_PRESSED);
                status.get().shutdown();
                stage.get().close();
                return null;
            });
        }
    }

    @Test
    void symbolAndTextShareOneTooltipInTheFontSizeOfTheSidebarTooltips() throws Exception {
        String before = onFxThread(Application::getUserAgentStylesheet);
        AtomicReference<MemoryStatusComponent> status = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        AtomicReference<Button> sidebar = new AtomicReference<>();
        onFxThread(() -> {
            Application.setUserAgentStylesheet(ThemeManager.getAtlantaFXTheme("primer-light").getUserAgentStylesheet());
            status.set(new MemoryStatusComponent());
            sidebar.set(new Button("Trash"));
            sidebar.get().setTooltip(new Tooltip("Trash"));

            /*  the stylesheet of the status bar sits on the root of the main window, as in its FXML  */
            VBox root = new VBox(sidebar.get(), status.get());
            root.getStylesheets().add(MemoryStatusComponentTest.class.getResource("/css/memory-status.css")
                    .toExternalForm());
            stage.set(new Stage());
            stage.get().setScene(new Scene(root, 400, 120));
            stage.get().show();
            return null;
        });
        try {
            double[] fontSizes = new double[2];
            for (int display = 0; display < 2; display++) {
                int index = display;
                HBox shown = onFxThread(() -> (HBox) status.get().getChildren().get(index));
                Node icon = onFxThread(() -> shown.getChildren().get(0));
                Node text = onFxThread(() -> shown.getChildren().get(1));
                Tooltip tooltip = onFxThread(() -> (Tooltip) shown.getProperties().get(TOOLTIP_KEY));
                AtomicInteger hidden = new AtomicInteger();
                onFxThread(() -> {
                    assertNull(icon.getProperties().get(TOOLTIP_KEY), "the symbol has no tooltip of its own");
                    assertNull(text.getProperties().get(TOOLTIP_KEY), "the text has no tooltip of its own");
                    tooltip.showingProperty().addListener((observable, was, showing) -> {
                        if (!showing && tooltip.getOpacity() != 0)
                            hidden.incrementAndGet();
                    });
                    mouse(icon, MouseEvent.MOUSE_MOVED);
                    return null;
                });
                assertTrue(await(tooltip::isShowing, 2000), "the tooltip shows over the symbol");

                /*  the pointer moves from the symbol over the gap onto the text of the same display; the scene fires
                    the *_TARGET events, which reach the target itself as MOUSE_EXITED or MOUSE_ENTERED  */
                onFxThread(() -> {
                    mouse(icon, MouseEvent.MOUSE_EXITED_TARGET);
                    mouse(shown, MouseEvent.MOUSE_MOVED);
                    mouse(text, MouseEvent.MOUSE_ENTERED_TARGET);
                    mouse(text, MouseEvent.MOUSE_MOVED);
                    return null;
                });
                Thread.sleep(800);
                onFxThread(() -> {
                    assertTrue(tooltip.isShowing(), "the tooltip stays over the text");
                    assertEquals(0, hidden.get(), "the tooltip neither hid nor jumped");
                    assertTrue(skin(tooltip).isWrapText(), "long lines wrap");
                    fontSizes[index] = skin(tooltip).getFont().getSize();
                    mouse(text, MouseEvent.MOUSE_PRESSED);
                    return null;
                });
            }

            onFxThread(() -> {
                mouse(sidebar.get(), MouseEvent.MOUSE_MOVED);
                return null;
            });
            assertTrue(await(() -> sidebar.get().getTooltip().isShowing(), 2000), "the sidebar tooltip shows");
            double sidebarSize = onFxThread(() -> skin(sidebar.get().getTooltip()).getFont().getSize());
            assertEquals(sidebarSize, fontSizes[0], "memory tooltip font size");
            assertEquals(sidebarSize, fontSizes[1], "images tooltip font size");
        }
        finally {
            onFxThread(() -> {
                mouse(sidebar.get(), MouseEvent.MOUSE_PRESSED);
                status.get().shutdown();
                stage.get().close();
                Application.setUserAgentStylesheet(before);
                return null;
            });
        }
    }
}
