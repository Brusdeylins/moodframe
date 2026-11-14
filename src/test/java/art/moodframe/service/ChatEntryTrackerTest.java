package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.repository.ChatRepository;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /*  runs on the JavaFX thread and waits for the result  */
    private static <T> T onFxThread(Callable<T> call) throws Exception {
        FutureTask<T> task = new FutureTask<>(call);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    @Test
    void cancelEndsARunningEntryForGood() throws Exception {
        ChatEntryTracker tracker = new ChatEntryTracker(mock(ApplicationConfig.class), mock(ChatRepository.class),
                Runnable::run);
        GeneratedImage done = new GeneratedImage.Builder().downloadStatus(GeneratedImage.DownloadStatus.CACHED)
                .baseCost(0.03).build();
        GeneratedImage pending = new GeneratedImage.Builder().downloadStatus(GeneratedImage.DownloadStatus.PENDING)
                .baseCost(0.03).build();
        ChatEntry entry = new ChatEntry.Builder().status(ChatEntry.Status.PROCESSING).build();
        entry.getImages().addAll(done, pending);
        List<ChatEntry.Status> told = new CopyOnWriteArrayList<>();
        tracker.addEntryListener((ChatEntry updated) -> told.add(updated.getStatus()));

        assertTrue(onFxThread(() -> tracker.cancel(entry)));
        drainFxThread();
        assertEquals(ChatEntry.Status.CANCELLED, entry.getStatus());
        assertEquals(GeneratedImage.DownloadStatus.CACHED, done.getDownloadStatus(), "a finished image stays");
        assertEquals(GeneratedImage.DownloadStatus.FAILED, pending.getDownloadStatus());
        assertEquals(List.of(ChatEntry.Status.CANCELLED), told, "the listeners learn of the end");

        /*  What arrives later changes the state no more; a finished entry cannot be cancelled  */
        onFxThread(() -> {
            tracker.refresh(entry);
            tracker.markFailed(entry, "late failure");
            return null;
        });
        drainFxThread();
        drainFxThread();
        assertEquals(ChatEntry.Status.CANCELLED, entry.getStatus());
        assertFalse(onFxThread(() -> tracker.cancel(entry)));
    }
}
