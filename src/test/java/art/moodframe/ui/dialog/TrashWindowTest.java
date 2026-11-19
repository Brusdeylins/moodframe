package art.moodframe.ui.dialog;

import art.moodframe.service.trash.MemoryTrashGroup;
import art.moodframe.service.trash.TrashCenter;
import art.moodframe.service.trash.TrashCleanupService;
import art.moodframe.service.trash.TrashEntry;
import art.moodframe.service.trash.TrashGroup;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds the window on the JavaFX thread without showing it, to check its tabs and what a restore does; the trash is
 * asked on a worker the test runs by hand, never on the JavaFX thread.
 */
class TrashWindowTest {

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await(10, TimeUnit.SECONDS);
    }

    /*  A step on the FX thread, after the steps queued before  */
    private static <T> T onFxThread(Supplier<T> step) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(step.get());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(20, TimeUnit.SECONDS), "the FX thread did not answer in time");
        if (failure.get() != null)
            throw new AssertionError(failure.get());
        return result.get();
    }

    /*  A worker the test runs by hand: the tasks wait until worked off on the thread of the test  */
    private static final class ManualWorker implements Executor {

        private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        void workOff() {
            for (Runnable task = tasks.poll(); task != null; task = tasks.poll())
                task.run();
        }
    }

    /*  A group in memory that notes the threads it is asked on  */
    private static MemoryTrashGroup group(TrashGroup.Kind kind, List<String> threads) {
        return new MemoryTrashGroup(kind) {
            @Override
            public List<TrashEntry> list() {
                threads.add(Thread.currentThread().getName());
                return super.list();
            }

            @Override
            public boolean restore(String id) {
                threads.add(Thread.currentThread().getName());
                return super.restore(id);
            }
        };
    }

    private static List<String> tabTexts(TabPane tabs) {
        return tabs.getTabs().stream().map((tab) -> tab.getText()).toList();
    }

    @SuppressWarnings("unchecked")
    private static ListView<TrashEntry> list(TabPane tabs, TrashGroup.Kind kind) {
        return (ListView<TrashEntry>) tabs.getTabs().get(kind.ordinal()).getContent().lookup(".list-view");
    }

    /*  "Empty trash" of a tab  */
    private static Button clearButton(TabPane tabs, TrashGroup.Kind kind) {
        return (Button) tabs.getTabs().get(kind.ordinal()).getContent().lookup(".button");
    }

    @Test
    void showsOneTabPerKindWithItsCountAndRestoresAnItemOnTheWorker() throws Exception {
        List<String> threads = new CopyOnWriteArrayList<>();
        TrashCenter center = new TrashCenter();
        center.register(group(TrashGroup.Kind.CHATS, threads).put("c1", 3).put("c2", 59));
        center.register(group(TrashGroup.Kind.BLOCKS, threads).put("b1", 10));
        List<String> restored = new CopyOnWriteArrayList<>();
        center.addListener(new TrashCenter.Listener() {
            @Override
            public void restored(TrashGroup.Kind kind, String id) {
                restored.add(kind + " " + id);
            }

            @Override
            public void deleted(TrashGroup.Kind kind, List<String> ids) {
            }
        });
        TrashCleanupService cleanup = new TrashCleanupService(center);
        ManualWorker worker = new ManualWorker();
        try {
            /*  The jump from an incomplete mood: the tab of the building blocks, the block marked once loaded  */
            TrashWindow window = onFxThread(() -> new TrashWindow.Opener(center, cleanup, worker)
                    .window(null, TrashGroup.Kind.BLOCKS, "b1"));
            TabPane tabs = (TabPane) window.getScene().getRoot();
            assertTrue(onFxThread(() -> clearButton(tabs, TrashGroup.Kind.CHATS).isDisabled()),
                    "the buttons wait for the trash");
            assertEquals(List.of(), threads, "nothing asked on the FX thread");

            worker.workOff();
            assertEquals(List.of(
                    I18nManager.getText("trash.tab.chats", 2), I18nManager.getText("trash.tab.images", 0),
                    I18nManager.getText("trash.tab.blocks", 1), I18nManager.getText("trash.tab.moods", 0),
                    I18nManager.getText("trash.tab.frames", 0), I18nManager.getText("trash.tab.saved_images", 0)),
                    onFxThread(() -> tabTexts(tabs)));
            assertEquals(TrashGroup.Kind.BLOCKS.ordinal(),
                    (int) onFxThread(() -> tabs.getSelectionModel().getSelectedIndex()));
            assertEquals("b1", onFxThread(() -> list(tabs, TrashGroup.Kind.BLOCKS).getSelectionModel()
                    .getSelectedItem().id()));
            assertFalse(onFxThread(() -> clearButton(tabs, TrashGroup.Kind.CHATS).isDisabled()));

            onFxThread(() -> {
                window.restore(TrashGroup.Kind.CHATS, list(tabs, TrashGroup.Kind.CHATS).getItems().get(0), null);
                return null;
            });
            assertTrue(onFxThread(() -> clearButton(tabs, TrashGroup.Kind.CHATS).isDisabled()),
                    "the buttons wait for the restore");
            assertEquals(List.of(), restored, "the restore waits for the worker");

            worker.workOff();
            assertEquals(I18nManager.getText("trash.tab.chats", 1), onFxThread(() -> tabTexts(tabs).get(0)));
            assertFalse(onFxThread(() -> clearButton(tabs, TrashGroup.Kind.CHATS).isDisabled()));
            assertEquals(List.of("CHATS c1"), new ArrayList<>(restored));
            String test = Thread.currentThread().getName();
            assertTrue(threads.stream().allMatch(test::equals), "the trash was asked on the worker: " + threads);
        } finally {
            cleanup.shutdown();
        }
    }
}
