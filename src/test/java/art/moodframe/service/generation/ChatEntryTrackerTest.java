package art.moodframe.service.generation;

import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.repository.ChatRepository;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
        ChatEntryTracker tracker = new ChatEntryTracker(mock(ChatRepository.class), Runnable::run);
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
        ChatEntryTracker tracker = new ChatEntryTracker(mock(ChatRepository.class), Runnable::run);
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

    @Test
    void anEntryThatEndedIsToldOnceItIsStoredWithItsImages() throws Exception {
        List<String> stored = new CopyOnWriteArrayList<>();
        ChatRepository repository = mock(ChatRepository.class);
        doAnswer((invocation) -> stored.add("image")).when(repository).saveGeneratedImage(any());
        doAnswer((invocation) -> stored.add("entry")).when(repository).saveChatEntry(any());
        Queue<Runnable> writes = new ConcurrentLinkedQueue<>();
        ChatEntryTracker tracker = new ChatEntryTracker(repository, writes::add);
        List<String> told = new CopyOnWriteArrayList<>();
        tracker.addEntryListener((ChatEntry updated) -> told.add(updated.getStatus().name() + " after " + stored));

        GeneratedImage image = new GeneratedImage.Builder().downloadStatus(GeneratedImage.DownloadStatus.CACHED)
                .baseCost(0.04).build();
        ChatEntry entry = new ChatEntry.Builder().status(ChatEntry.Status.PROCESSING).build();
        entry.getImages().add(image);
        onFxThread(() -> {
            tracker.saveImage(image);
            tracker.refresh(entry);
            return null;
        });
        drainFxThread();
        drainFxThread();
        assertEquals(ChatEntry.Status.COMPLETED, entry.getStatus());
        assertEquals(List.of(), told, "nobody learns of the end while its writes wait");

        /*  the worker of the writes runs them in order, the listeners learn of the end after the last one  */
        while (!writes.isEmpty())
            writes.poll().run();
        drainFxThread();
        assertEquals(List.of("COMPLETED after [image, entry]"), told);
    }

    @Test
    void aFailedEntryTellsWhyItsImagesFailed() throws Exception {
        ChatEntryTracker tracker = new ChatEntryTracker(mock(ChatRepository.class), Runnable::run);
        String reason = "Gemini generation failed: Request contains an invalid argument";
        ChatEntry entry = new ChatEntry.Builder().status(ChatEntry.Status.PROCESSING).build();
        for (String message : new String[] { reason, null, reason })
            entry.getImages().add(new GeneratedImage.Builder().downloadStatus(GeneratedImage.DownloadStatus.FAILED)
                    .errorMessage(message).build());

        tracker.refresh(entry);
        drainFxThread();
        drainFxThread();
        assertEquals(ChatEntry.Status.FAILED, entry.getStatus());
        assertEquals(reason, entry.getErrorMessage(), "each reason once, an image without one adds nothing");
    }

    @Test
    void anEntryThatFailedAsAWholeCostsOnlyWhatItsImagesCost() throws Exception {
        ChatEntryTracker tracker = new ChatEntryTracker(mock(ChatRepository.class), Runnable::run);
        ChatEntry empty = new ChatEntry.Builder().status(ChatEntry.Status.PENDING).cost(0.12).build();
        GeneratedImage made = new GeneratedImage.Builder().downloadStatus(GeneratedImage.DownloadStatus.CACHED)
                .baseCost(0.04).build();
        GeneratedImage running = new GeneratedImage.Builder().downloadStatus(GeneratedImage.DownloadStatus.PENDING)
                .baseCost(0.04).build();
        ChatEntry partial = new ChatEntry.Builder().status(ChatEntry.Status.PROCESSING).cost(0.08).build();
        partial.getImages().addAll(made, running);

        tracker.markFailed(empty, "not stored");
        tracker.markFailed(partial, "no answer");
        drainFxThread();
        assertEquals(ChatEntry.Status.FAILED, empty.getStatus());
        assertEquals(0.0, empty.getCost(), "an order that sent nothing costs nothing");
        assertEquals(0.04, partial.getCost(), 1e-9);
        assertEquals(GeneratedImage.DownloadStatus.FAILED, running.getDownloadStatus());
        assertEquals("no answer", running.getErrorMessage());
    }

    @Test
    void storesOnAWorkerAfterTheChangeOnTheFxThread() throws Exception {
        ChatRepository repository = mock(ChatRepository.class);
        List<String> saved = new CopyOnWriteArrayList<>();
        org.mockito.Mockito.doAnswer((invocation) -> {
            ChatEntry stored = invocation.getArgument(0);
            saved.add(stored.getStatus().name() + ":" + Platform.isFxApplicationThread());
            return null;
        }).when(repository).saveChatEntry(org.mockito.ArgumentMatchers.any());
        java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            ChatEntryTracker tracker = new ChatEntryTracker(repository, worker);
            ChatEntry entry = new ChatEntry.Builder().status(ChatEntry.Status.PROCESSING).build();

            tracker.markFailed(entry, "no answer");
            drainFxThread();
            tracker.saveEntry(entry).get(10, TimeUnit.SECONDS);
            assertEquals(List.of("FAILED:false", "FAILED:false"), saved,
                    "the write sees the change and runs off the JavaFX thread");
        } finally {
            worker.shutdownNow();
        }
    }
}
