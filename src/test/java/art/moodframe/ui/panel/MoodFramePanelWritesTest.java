package art.moodframe.ui.panel;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ConfigMocks;
import art.moodframe.database.TestDatabase;
import art.moodframe.model.Frame;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.repository.FrameRepository;
import art.moodframe.repository.MoodRepository;
import art.moodframe.service.generation.ModelLabels;
import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.library.TestLibraryServices;
import art.moodframe.ui.component.StylePresetItem;
import art.moodframe.ui.support.StatusSink;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The panel writes the library on the worker of its window, never on the JavaFX thread, and shows the outcome
 * afterwards: a saved mood or frame is chosen and unchanged. The worker waits on a gate until the JavaFX thread has
 * returned from the click, so a write on the JavaFX thread would not wait for the gate. The worker also finds the
 * moods with a block in the trash, once per list load.
 */
class MoodFramePanelWritesTest {

    private static final String WORKER = "panel-test-worker";

    @TempDir
    static Path home;

    /*  the threads the writes of moods and frames ran on, and those that asked the trash for the blocks of a mood  */
    private final List<String> writes = new CopyOnWriteArrayList<>();
    private final List<String> trashQueries = new CopyOnWriteArrayList<>();

    /*  the worker opens once the JavaFX thread returned from a click  */
    private final CountDownLatch gate = new CountDownLatch(1);
    private final ExecutorService worker = Executors.newSingleThreadExecutor((runnable) -> new Thread(() -> {
        try {
            gate.await(10, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        runnable.run();
    }, WORKER));

    private final List<String> messages = new CopyOnWriteArrayList<>();
    private PromptBlockService blocks;
    private MoodService moods;
    private FrameService frames;
    private Parent root;
    private MoodFramePanelController panel;

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
        TestDatabase.open(home.resolve("db").resolve("panel.db").toString());
    }

    /** Loads the panel on the standard library with services that record the thread of every write. */
    @BeforeEach
    void load() throws Exception {
        LibraryEvents events = new LibraryEvents();
        blocks = TestLibraryServices.blocks(events);
        ColorStyleService colors = TestLibraryServices.palettes(events);
        moods = new MoodService(new MoodRepository(TestLibraryServices.database()), blocks, colors, events) {
            @Override
            public synchronized boolean add(Mood mood) {
                writes.add(Thread.currentThread().getName());
                return super.add(mood);
            }

            @Override
            public synchronized boolean update(Mood mood) {
                writes.add(Thread.currentThread().getName());
                return super.update(mood);
            }

            @Override
            public List<String> trashedBlocks(Mood mood) {
                trashQueries.add(Thread.currentThread().getName());
                return super.trashedBlocks(mood);
            }
        };
        frames = new FrameService(new FrameRepository(TestLibraryServices.database()), events) {
            @Override
            public synchronized boolean update(Frame frame) {
                writes.add(Thread.currentThread().getName());
                return super.update(frame);
            }
        };
        ApplicationConfig config = ConfigMocks.fresh();
        StatusSink status = new StatusSink() {
            @Override
            public void status(String message) {
                messages.add(message);
            }

            @Override
            public void error(String message) {
                messages.add(message);
            }
        };
        onFx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/panel/MoodFramePanel.fxml"));
            root = loader.load();
            panel = loader.getController();
            panel.followModel(new SimpleStringProperty(ModelLabels.displayName(GenerationModel.GEMINI_PRO_2K)));
            panel.init(new MoodFramePanelController.Services(config, blocks, colors, moods, frames,
                    TestLibraryServices.favorites(events), null), worker, status, () -> { });
            return null;
        });
    }

    @AfterEach
    void stopTheWorker() {
        gate.countDown();
        worker.shutdownNow();
    }

    @Test
    void aChangedMoodIsSavedOnTheWorkerAndStaysChosen() throws Exception {
        Mood mood = new Mood(null, "Mine " + System.nanoTime());
        mood.setReference(Mood.Field.STYLE, "std:style:analog-film");
        assertTrue(moods.add(mood));
        writes.clear();

        boolean changed = onFx(() -> {
            panel.onLibraryChanged(LibraryEvents.Kind.MOOD);
            panel.chooseMood(mood);
            ComboBox<StylePresetItem> atmosphere = node("atmosphereComboBox");
            atmosphere.setValue(atmosphere.getItems().stream().filter((item) -> item.getBlock() != null)
                    .findFirst().orElseThrow());
            boolean before = panel.isMoodChanged();
            this.<Button>node("saveMoodButton").fire();
            return before && writes.isEmpty();
        });
        assertTrue(changed, "the click returned before the mood was written");

        gate.countDown();
        waitFor(() -> messages.stream().anyMatch((message) -> message.contains(mood.getName())), "the status");
        assertEquals(List.of(WORKER), writes);
        assertEquals(mood.getId(), onFx(() -> panel.chosenMood().getId()));
        assertFalse(onFx(panel::isMoodChanged), "the saved mood shows unchanged");
        assertTrue(moods.find(mood.getId()).getReferences().containsKey(Mood.Field.ATMOSPHERE));
    }

    @Test
    void aChangedFrameIsSavedOnTheWorkerAndStaysChosen() throws Exception {
        Frame frame = new Frame(null, "Wide " + System.nanoTime());
        frame.setAspectRatio("16:9");
        frame.setResolution("2K");
        assertTrue(frames.add(frame));

        boolean clicked = onFx(() -> {
            panel.onLibraryChanged(LibraryEvents.Kind.FRAME);
            panel.chooseFrame(frame);
            this.<ChoiceBox<String>>node("aspectRatioComboBox").setValue("1:1");
            this.<Button>node("saveFrameButton").fire();
            return writes.isEmpty();
        });
        assertTrue(clicked, "the click returned before the frame was written");

        gate.countDown();
        waitFor(() -> "1:1".equals(frames.find(frame.getId()).getAspectRatio()), "the frame is stored");
        waitFor(() -> messages.stream().anyMatch((message) -> message.contains(frame.getName())), "the status");
        assertEquals(List.of(WORKER), writes);
        assertEquals(frame.getId(), onFx(() -> panel.chosenFrame().getId()));
        assertFalse(onFx(() -> panel.selection().frameChanged()));
    }

    @Test
    void theWorkerFindsTheMoodsWithABlockInTheTrashOncePerListLoad() throws Exception {
        PromptBlock block = new PromptBlock(null, PromptBlock.Type.ATMOSPHERE, "Gone " + System.nanoTime(), "fog",
                null, false);
        assertTrue(blocks.addCustom(block));
        Mood mood = new Mood(null, "Incomplete " + System.nanoTime());
        mood.setReference(Mood.Field.ATMOSPHERE, block.getId());
        assertTrue(moods.add(mood));
        assertTrue(blocks.deleteCustom(block.getId()));

        assertFalse(onFx(() -> {
            panel.onLibraryChanged(LibraryEvents.Kind.MOOD);
            panel.chooseMood(mood);
            return node("moodIncompleteButton").isVisible();
        }), "the worker has not looked yet");
        gate.countDown();
        waitFor(() -> node("moodIncompleteButton").isVisible(), "the warning of the chosen mood");
        assertFalse(trashQueries.isEmpty());
        assertEquals(List.of(WORKER), trashQueries.stream().distinct().toList(), "no query on the JavaFX thread");
        int queries = trashQueries.size();
        int moodCount = moods.getAll().size();
        assertTrue(queries <= 2 * moodCount, queries + " queries for two loads of " + moodCount + " moods");
    }

    @Test
    void thePromptBlocksFollowTheReferenceTheCallerPasses() throws Exception {
        assertTrue(onFx(() -> {
            ComboBox<StylePresetItem> atmosphere = node("atmosphereComboBox");
            atmosphere.setValue(atmosphere.getItems().stream().filter((item) -> item.getBlock() != null)
                    .findFirst().orElseThrow());
            panel.setReference(false);
            return panel.promptBlocks(true).atmosphere() == null && panel.promptBlocks(false).atmosphere() != null;
        }), "with a reference image the blocks apply only with \"Apply mood\", whatever the panel was told before");
    }

    /*  the node of an id in the panel  */
    @SuppressWarnings("unchecked")
    private <T extends javafx.scene.Node> T node(String id) {
        return (T) root.lookup("#" + id);
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
