package art.moodframe.ui.dialog;

import art.moodframe.service.MemoryTrashGroup;
import art.moodframe.service.TrashCenter;
import art.moodframe.service.TrashCleanupService;
import art.moodframe.service.TrashEntry;
import art.moodframe.service.TrashGroup;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Builds the window on the JavaFX thread without showing it, to check its tabs and what a restore does. */
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

    @Test
    void showsOneTabPerKindWithItsCountAndRestoresAnItem() throws Exception {
        TrashCenter center = new TrashCenter();
        center.register(new MemoryTrashGroup(TrashGroup.Kind.CHATS).put("c1", 3).put("c2", 59));
        center.register(new MemoryTrashGroup(TrashGroup.Kind.BLOCKS).put("b1", 10));
        List<String> restored = new ArrayList<>();
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

        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                TrashWindow window = new TrashWindow(null, center, cleanup);
                TabPane tabs = (TabPane) window.getScene().getRoot();
                assertEquals(List.of(
                        I18nManager.getText("trash.tab.chats", 2), I18nManager.getText("trash.tab.blocks", 1),
                        I18nManager.getText("trash.tab.moods", 0), I18nManager.getText("trash.tab.frames", 0)),
                        tabs.getTabs().stream().map((tab) -> tab.getText()).toList());

                /*  The jump from an incomplete mood: the tab of the building blocks with the block marked  */
                window.select(TrashGroup.Kind.BLOCKS, "b1");
                assertEquals(TrashGroup.Kind.BLOCKS.ordinal(), tabs.getSelectionModel().getSelectedIndex());
                @SuppressWarnings("unchecked")
                ListView<TrashEntry> blocks = (ListView<TrashEntry>) tabs.getSelectionModel().getSelectedItem()
                        .getContent().lookup(".list-view");
                assertEquals("b1", blocks.getSelectionModel().getSelectedItem().id());

                window.restore(TrashGroup.Kind.CHATS, center.list(TrashGroup.Kind.CHATS).get(0));
                assertEquals(I18nManager.getText("trash.tab.chats", 1), tabs.getTabs().get(0).getText());
                assertEquals(List.of("CHATS c1"), restored);
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(20, TimeUnit.SECONDS), "the window was not built in time");
        cleanup.shutdown();
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }
}
