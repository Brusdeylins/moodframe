package art.moodframe.service.generation;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ConfigMocks;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationJobInfo;
import art.moodframe.model.GenerationJobResult;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.repository.ChatRepository;
import art.moodframe.service.provider.ApiHttpException;
import art.moodframe.service.provider.BflApiService;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Polls a job of a provider that answers with a job one poll at a time, and handles its end once: a slow answer starts
 * no second poll and no second download, an answer after a cancellation changes nothing.
 */
class JobPollingTest {

    /*  the polls the flow scheduled, run by the test  */
    private final Queue<Runnable> scheduled = new ConcurrentLinkedQueue<>();

    /*  the answers to the polls, completed by the test  */
    private final Queue<CompletableFuture<GenerationJobResult>> polls = new ConcurrentLinkedQueue<>();

    private BflApiService api;
    private ImageFileStore files;
    private ApplicationConfig config;
    private ChatEntryTracker tracker;
    private JobPollingFlow flow;
    private ChatEntry entry;

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
        for (int i = 0; i < 3; i++) {
            CountDownLatch done = new CountDownLatch(1);
            Platform.runLater(done::countDown);
            assertTrue(done.await(10, TimeUnit.SECONDS));
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        api = mock(BflApiService.class, CALLS_REAL_METHODS);
        doAnswer((invocation) -> {
            CompletableFuture<GenerationJobResult> poll = new CompletableFuture<>();
            polls.add(poll);
            return poll;
        }).when(api).pollJob(any());
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        when(scheduler.schedule(any(Runnable.class), anyLong(), any())).thenAnswer((invocation) -> {
            scheduled.add(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        files = mock(ImageFileStore.class);
        when(files.download(any())).thenReturn(new ImageFileStore.StoredImage(Path.of("sample.png"), "sample.png",
                "png", "image/png", 1024, 1024, 1000));
        config = ConfigMocks.config();
        when(config.getApiTimeoutSeconds()).thenReturn(600);
        tracker = new ChatEntryTracker(mock(ChatRepository.class), Runnable::run);
        flow = new JobPollingFlow(config, tracker, files, scheduler, Runnable::run);

        entry = new ChatEntry.Builder().status(ChatEntry.Status.PROCESSING).build();
        entry.getImages().add(new GeneratedImage.Builder().chatEntryId(entry.getId())
                .downloadStatus(GeneratedImage.DownloadStatus.PENDING).sortOrder(0).build());
    }

    /*  submits one image, the job is answered at once  */
    private void submit(CompletableFuture<GenerationJobInfo> job) throws Exception {
        doReturn(job).when(api).submitGenerationRequest(any());
        ImageGenerationRequest request = new ImageGenerationRequest("a lighthouse",
                GenerationModel.FLUX2_PRO.getApiModelId());
        request.setSeed(42L);
        flow.submit(api, request, entry, 0);
        drainFxThread();
    }

    /*  runs the one poll scheduled now  */
    private void runPoll() {
        assertEquals(1, scheduled.size(), "one poll is scheduled");
        scheduled.poll().run();
    }

    private static GenerationJobResult answer(String status) {
        GenerationJobResult.GenerationResult sample = new GenerationJobResult.GenerationResult();
        sample.setSample("https://bfl.example/sample.png");
        GenerationJobResult result = new GenerationJobResult();
        result.setStatus(status);
        result.setResult(sample);
        return result;
    }

    private GeneratedImage image() {
        return entry.getImages().get(0);
    }

    @Test
    void aSlowPollStartsNoSecondPollAndTheImageIsDownloadedOnce() throws Exception {
        submit(CompletableFuture.completedFuture(new GenerationJobInfo("job-1", "Pending")));
        runPoll();
        assertEquals(1, polls.size());
        assertEquals(0, scheduled.size(), "no poll while one is in flight");

        polls.poll().complete(answer("Running"));
        drainFxThread();
        runPoll();
        polls.poll().complete(answer("Ready"));
        drainFxThread();
        drainFxThread();

        verify(files, times(1)).download(any());
        assertEquals(0, scheduled.size(), "a finished job is not polled again");
        assertEquals(GeneratedImage.DownloadStatus.CACHED, image().getDownloadStatus());
        assertEquals(ChatEntry.Status.COMPLETED, entry.getStatus());
    }

    @Test
    void aReadyAnswerAfterTheCancellationChangesNothing() throws Exception {
        submit(CompletableFuture.completedFuture(new GenerationJobInfo("job-2", "Pending")));
        runPoll();
        FxModel.changeAndGet(() -> tracker.cancel(entry)).get(10, TimeUnit.SECONDS);
        flow.cancelEntry(entry.getId());

        polls.poll().complete(answer("Ready"));
        drainFxThread();

        verify(files, never()).download(any());
        assertEquals(GeneratedImage.DownloadStatus.FAILED, image().getDownloadStatus());
        assertEquals(ChatEntry.Status.CANCELLED, entry.getStatus());
        assertEquals(0, scheduled.size());
    }

    @Test
    void aJobThatTimedOutIsNotPolledAgain() throws Exception {
        when(config.getApiTimeoutSeconds()).thenReturn(3);
        submit(CompletableFuture.completedFuture(new GenerationJobInfo("job-3", "Pending")));
        runPoll();
        polls.poll().completeExceptionally(new ApiHttpException(404, "Not found"));
        runPoll();
        drainFxThread();

        assertEquals(0, polls.size(), "the second attempt is past the timeout and asks nothing");
        assertEquals(0, scheduled.size());
        assertEquals(GeneratedImage.DownloadStatus.FAILED, image().getDownloadStatus());
        verify(files, never()).download(any());
    }

    @Test
    void aJobAnsweredAfterTheCancellationIsNotPolled() throws Exception {
        CompletableFuture<GenerationJobInfo> job = new CompletableFuture<>();
        submit(job);
        FxModel.changeAndGet(() -> tracker.cancel(entry)).get(10, TimeUnit.SECONDS);
        flow.cancelEntry(entry.getId());

        job.complete(new GenerationJobInfo("job-4", "Pending"));
        drainFxThread();

        assertEquals(0, scheduled.size());
        assertEquals(1, entry.getImages().size(), "no image is added to a cancelled entry");
        assertEquals(ChatEntry.Status.CANCELLED, entry.getStatus());
    }
}
