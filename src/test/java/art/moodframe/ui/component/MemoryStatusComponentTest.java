package art.moodframe.ui.component;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tooltip of the memory display in the status bar, shown on the Monocle headless platform. */
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

    /*  the pointer moving over or pressing on the middle of a node  */
    private static void mouse(Node node, EventType<MouseEvent> type) {
        Bounds scene = node.localToScene(node.getLayoutBounds());
        Bounds screen = node.localToScreen(node.getLayoutBounds());
        Event.fireEvent(node, new MouseEvent(type, scene.getCenterX(), scene.getCenterY(), screen.getCenterX(),
                screen.getCenterY(), MouseButton.PRIMARY, 1, false, false, false, false, false, false, false, false,
                false, false, null));
    }

    @Test
    void theMemoryTooltipShowsWithinHalfASecondAndStaysAcrossAnUpdate() throws Exception {
        AtomicReference<MemoryStatusComponent> status = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        AtomicReference<Label> label = new AtomicReference<>();
        AtomicLong shownAfter = new AtomicLong(-1);
        AtomicInteger hidden = new AtomicInteger();
        Tooltip tooltip = onFxThread(() -> {
            status.set(new MemoryStatusComponent());
            stage.set(new Stage());
            stage.get().setScene(new Scene(new StackPane(status.get()), 400, 60));
            stage.get().show();
            label.set((Label) status.get().getChildren().get(1));
            Tooltip installed = (Tooltip) label.get().getProperties().get(TOOLTIP_KEY);
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
                assertSame(tooltip, label.get().getProperties().get(TOOLTIP_KEY));
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
}
