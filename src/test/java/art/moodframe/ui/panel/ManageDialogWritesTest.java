package art.moodframe.ui.panel;

import art.moodframe.database.TestDatabase;
import art.moodframe.model.Frame;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.repository.FrameRepository;
import art.moodframe.repository.MoodRepository;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.library.TestLibraryServices;
import art.moodframe.ui.component.FavoriteStar;
import art.moodframe.ui.dialog.FrameManageDialog;
import art.moodframe.ui.dialog.MoodManageDialog;
import art.moodframe.ui.support.StatusSink;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "Manage…" windows of moods and frames store their changes on the worker of the window, never on the JavaFX
 * thread, and wait meanwhile; the moods with a block in the trash are found there once per load of the list. A work
 * waits on the gate open when it was handed to the worker, so a write on the JavaFX thread would not wait for it.
 */
class ManageDialogWritesTest {

    private static final String WORKER = "manage-test-worker";

    @TempDir
    static Path home;

    /*  the threads the writes ran on, and those that asked the trash for the blocks of a mood  */
    private final List<String> writes = new CopyOnWriteArrayList<>();
    private final List<String> trashQueries = new CopyOnWriteArrayList<>();

    private volatile CountDownLatch gate = new CountDownLatch(1);
    private final ExecutorService thread = Executors.newSingleThreadExecutor((task) -> new Thread(task, WORKER));
    private final Executor worker = (task) -> {
        CountDownLatch waiting = gate;
        thread.execute(() -> {
            try {
                waiting.await(10, TimeUnit.SECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            task.run();
        });
    };
    private final PanelWorker panelWorker = new PanelWorker(worker, new StatusSink() {
        @Override
        public void status(String message) {
        }

        @Override
        public void error(String message) {
        }
    });

    @BeforeAll
    static void startTheToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        }
        catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
        TestDatabase.open(home.resolve("db").resolve("manage.db").toString());
    }

    @AfterEach
    void stopTheWorker() {
        gate.countDown();
        thread.shutdownNow();
    }

    @Test
    void theMoodWindowDeletesOnTheWorkerAndAsksTheTrashOncePerLoad() throws Exception {
        LibraryEvents events = new LibraryEvents();
        PromptBlockService blocks = TestLibraryServices.blocks(events);
        MoodService moods = new MoodService(new MoodRepository(TestLibraryServices.database()), blocks,
                TestLibraryServices.palettes(events), events) {
            @Override
            public synchronized boolean moveToTrash(String id) {
                writes.add(Thread.currentThread().getName());
                return super.moveToTrash(id);
            }

            @Override
            public List<String> trashedBlocks(Mood mood) {
                trashQueries.add(Thread.currentThread().getName());
                return super.trashedBlocks(mood);
            }
        };
        PromptBlock block = new PromptBlock(null, PromptBlock.Type.LIGHT, "Gone " + System.nanoTime(), "dusk", null,
                false);
        assertTrue(blocks.addCustom(block));
        Mood incomplete = new Mood(null, "Incomplete " + System.nanoTime());
        incomplete.setReference(Mood.Field.LIGHT, block.getId());
        Mood doomed = new Mood(null, "Doomed " + System.nanoTime());
        assertTrue(moods.add(incomplete));
        assertTrue(moods.add(doomed));
        assertTrue(blocks.deleteCustom(block.getId()));
        int listed = moods.getCustom().size();

        MoodManageDialog dialog = onFx(() -> new MoodManageDialog(null, ManagedLibraries.moods(moods, panelWorker),
                new FavoriteStar.Favorites<>(null, (mood) -> false, (mood) -> { }), null));
        assertTrue(trashQueries.isEmpty(), "the trash is asked on the worker");
        openGate();
        waitFor(() -> trashQueries.size() == listed, "the trash queries of the first load");
        assertEquals(List.of(WORKER), trashQueries.stream().distinct().toList());

        boolean waiting = onFx(() -> {
            ListView<Mood> list = list(dialog);
            list.getSelectionModel().select(list.getItems().stream()
                    .filter((mood) -> mood.getId().equals(doomed.getId())).findFirst().orElseThrow());
            button(dialog, I18nManager.getText("mood.manage.delete")).fire();
            return dialog.getScene().getRoot().isDisabled() && writes.isEmpty();
        });
        assertTrue(waiting, "the window waits for the worker");
        openGate();
        waitFor(() -> !dialog.getScene().getRoot().isDisabled(), "the window after the delete");
        assertEquals(List.of(WORKER), writes);
        assertNull(moods.find(doomed.getId()));
        assertFalse(onFx(() -> ManageDialogWritesTest.<Mood>list(dialog).getItems().stream()
                .anyMatch((mood) -> mood.getId().equals(doomed.getId()))));
        openGate();
        waitFor(() -> trashQueries.size() == 2 * listed - 1, "one query per mood of the second load");
        assertEquals(List.of(WORKER), trashQueries.stream().distinct().toList());
    }

    @Test
    void theFrameWindowReordersOnTheWorker() throws Exception {
        FrameService frames = new FrameService(new FrameRepository(TestLibraryServices.database()),
                new LibraryEvents()) {
            @Override
            public synchronized void reorder(List<String> ids) {
                writes.add(Thread.currentThread().getName());
                super.reorder(ids);
            }
        };
        for (String name : List.of("First ", "Second ")) {
            Frame frame = new Frame(null, name + System.nanoTime());
            frame.setAspectRatio("1:1");
            assertTrue(frames.add(frame));
        }
        List<String> before = frames.getCustom().stream().map(Frame::getId).toList();

        FrameManageDialog dialog = onFx(() -> new FrameManageDialog(null,
                ManagedLibraries.frames(frames, panelWorker),
                new FavoriteStar.Favorites<>(null, (frame) -> false, (frame) -> { }), null));
        boolean waiting = onFx(() -> {
            list(dialog).getSelectionModel().select(0);
            button(dialog, I18nManager.getText("frame.manage.down")).fire();
            return dialog.getScene().getRoot().isDisabled() && writes.isEmpty();
        });
        assertTrue(waiting, "the window waits for the worker");
        openGate();
        waitFor(() -> !dialog.getScene().getRoot().isDisabled(), "the window after the move");
        assertEquals(List.of(WORKER), writes);
        List<String> after = frames.getCustom().stream().map(Frame::getId).toList();
        assertEquals(before.get(0), after.get(1));
        Frame marked = onFx(() -> ManageDialogWritesTest.<Frame>list(dialog).getSelectionModel().getSelectedItem());
        assertEquals(before.get(0), marked.getId(), "the moved frame stays marked");
    }

    /*  lets the works handed to the worker so far run, and holds the next ones  */
    private void openGate() {
        CountDownLatch open = gate;
        gate = new CountDownLatch(1);
        open.countDown();
    }

    @SuppressWarnings("unchecked")
    private static <T> ListView<T> list(Stage dialog) {
        return (ListView<T>) find(dialog.getScene().getRoot(), ListView.class, null);
    }

    /*  the button of a window by its text or the text of its tooltip  */
    private static Button button(Stage dialog, String text) {
        return find(dialog.getScene().getRoot(), Button.class, text);
    }

    private static <T extends Node> T find(Node node, Class<T> type, String text) {
        if (type.isInstance(node) && (text == null || node instanceof Button button
                && (text.equals(button.getText())
                || button.getTooltip() != null && text.equals(button.getTooltip().getText()))))
            return type.cast(node);
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                T found = find(child, type, text);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    private static void waitFor(Callable<Boolean> condition, String what) throws Exception {
        for (int i = 0; i < 200; i++) {
            if (onFx(condition))
                return;
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out: " + what);
    }

    /** Runs a step on the JavaFX thread and returns its result. */
    private static <T> T onFx(Callable<T> step) throws Exception {
        CompletableFuture<T> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                done.complete(step.call());
            }
            catch (Throwable failure) {
                done.completeExceptionally(failure);
            }
        });
        return done.get(20, TimeUnit.SECONDS);
    }
}
