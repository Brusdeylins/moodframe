package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ChatEntry;
import art.moodframe.repository.ChatRepository;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Tells several listeners about an entry on the JavaFX thread, as the main window and later callers listen at once. */
class ChatEntryTrackerTest {

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

    /*  waits until the JavaFX thread ran what was queued before  */
    private static void drainFxThread() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(done::countDown);
        assertTrue(done.await(10, TimeUnit.SECONDS));
    }

    @Test
    void tellsEveryListenerUntilItIsRemoved() throws Exception {
        ChatEntryTracker tracker = new ChatEntryTracker(mock(ApplicationConfig.class), mock(ChatRepository.class),
                Runnable::run);
        List<String> calls = new CopyOnWriteArrayList<>();
        Consumer<ChatEntry> first = (ChatEntry entry) -> calls.add("first:" + Platform.isFxApplicationThread());
        Consumer<ChatEntry> broken = (ChatEntry entry) -> {
            throw new IllegalStateException("broken listener");
        };
        Consumer<ChatEntry> second = (ChatEntry entry) -> calls.add("second");
        tracker.addEntryListener(first);
        tracker.addEntryListener(broken);
        tracker.addEntryListener(second);

        ChatEntry entry = new ChatEntry.Builder().build();
        tracker.notifyEntryUpdated(entry);
        drainFxThread();
        assertEquals(List.of("first:true", "second"), calls, "a broken listener keeps no other from its call");

        tracker.removeEntryListener(first);
        tracker.notifyEntryUpdated(entry);
        drainFxThread();
        assertEquals(List.of("first:true", "second", "second"), calls);
    }
}
