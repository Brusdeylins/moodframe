package art.moodframe.service.generation;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ConfigMocks;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationCost;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.repository.ChatRepository;
import art.moodframe.service.provider.BflApiService;
import art.moodframe.service.provider.GenerationJobInfo;
import art.moodframe.service.provider.GenerationJobResult;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Gives every finished FLUX image its price, as estimated or as BFL reports it, and the entry the sum of its images. */
class BflGenerationCostTest {

    private static final AtomicInteger jobs = new AtomicInteger();

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

    /*  runs an entry with two images of the model through the flow, BFL answers every job with the status and cost  */
    private static ChatEntry generate(GenerationModel model, int width, int height, String status, Double credits)
            throws Exception {
        GenerationJobResult.GenerationResult sample = new GenerationJobResult.GenerationResult();
        sample.setSample("https://bfl.example/sample.png");
        GenerationJobResult result = new GenerationJobResult();
        result.setStatus(status);
        result.setResult(sample);
        result.setCost(credits);
        BflApiService api = mock(BflApiService.class, CALLS_REAL_METHODS);
        doAnswer((invocation) -> CompletableFuture.completedFuture(
                new GenerationJobInfo("job-" + jobs.incrementAndGet(), "Pending")))
                .when(api).submitGenerationRequest(any());
        doReturn(CompletableFuture.completedFuture(result)).when(api).pollJob(any());

        /*  the status is polled once at once, the database writes on the scheduler are left out  */
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        when(scheduler.schedule(any(Runnable.class), anyLong(), any())).thenAnswer((invocation) -> {
            invocation.<Runnable>getArgument(0).run();
            return mock(ScheduledFuture.class);
        });
        ImageFileStore files = mock(ImageFileStore.class);
        when(files.download(any(), any())).thenReturn(new ImageFileStore.StoredImage(Path.of("sample.png"), "sample.png",
                "png", "image/png", width, height, 1000));
        ApplicationConfig config = ConfigMocks.config();
        when(config.getApiTimeoutSeconds()).thenReturn(600);

        ChatEntryTracker tracker = new ChatEntryTracker(mock(ChatRepository.class), Runnable::run);
        JobPollingFlow flow = new JobPollingFlow(config, tracker, files, scheduler, Runnable::run);

        /*  as the request manager sends it: placeholders first, the price per image set by the generation service  */
        ChatEntry entry = new ChatEntry.Builder().status(ChatEntry.Status.PROCESSING).build();
        for (int i = 0; i < 2; i++)
            entry.getImages().add(new GeneratedImage.Builder().chatEntryId(entry.getId())
                    .downloadStatus(GeneratedImage.DownloadStatus.PENDING).sortOrder(i).build());
        CompletableFuture<ChatEntry.Status> finished = new CompletableFuture<>();
        tracker.addEntryListener((ChatEntry updated) -> {
            if (updated.getStatus() == ChatEntry.Status.COMPLETED || updated.getStatus() == ChatEntry.Status.FAILED)
                finished.complete(updated.getStatus());
        });
        ImageGenerationRequest request = new ImageGenerationRequest("a lighthouse", model.getApiModelId());
        request.setWidth(width);
        request.setHeight(height);
        request.setSeed(42L);
        request.setBaseCostPerImage(GenerationCost.perImage(model, width, height, null));
        flow.submit(api, request, entry, 0);
        flow.submit(api, request, entry, 1);

        finished.get(10, TimeUnit.SECONDS);
        drainFxThread();
        return entry;
    }

    @Test
    void takesTheEstimatedPriceOfFluxThreeAndFluxTwo() throws Exception {
        for (GenerationModel model : new GenerationModel[] {
                GenerationModel.FLUX3_IMAGE_1K, GenerationModel.FLUX3_KLEIN_1K, GenerationModel.FLUX2_PRO }) {
            double estimate = GenerationCost.perImage(model, 1440, 1440, null);
            assertTrue(estimate > 0, model + " has a price");
            ChatEntry entry = generate(model, 1440, 1440, "Ready", null);
            assertEquals(ChatEntry.Status.COMPLETED, entry.getStatus());
            for (GeneratedImage image : entry.getImages()) {
                assertEquals(GeneratedImage.DownloadStatus.CACHED, image.getDownloadStatus());
                assertEquals(estimate, image.getBaseCost(), 1e-9, model + ": the image has the estimate");
            }
            assertEquals(2 * estimate, entry.getCost(), 1e-9, model + ": the entry has the sum of its images");
        }
    }

    @Test
    void takesThePriceBflReportsInCredits() throws Exception {
        ChatEntry entry = generate(GenerationModel.FLUX3_KLEIN_1K, 1024, 1024, "Ready", 1.2);
        for (GeneratedImage image : entry.getImages())
            assertEquals(0.012, image.getBaseCost(), 1e-9, "1.2 credits of one cent");
        assertEquals(0.024, entry.getCost(), 1e-9);
    }

    @Test
    void aModeratedJobCostsNothing() throws Exception {
        ChatEntry entry = generate(GenerationModel.FLUX2_PRO, 1024, 1024, "Content Moderated", null);
        assertEquals(ChatEntry.Status.FAILED, entry.getStatus());
        for (GeneratedImage image : entry.getImages())
            assertEquals(0.0, image.getBaseCost(), 1e-9);
        assertEquals(0.0, entry.getCost(), 1e-9);
    }
}
