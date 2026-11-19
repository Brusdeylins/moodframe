package art.moodframe.service.generation;

import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.repository.ChatRepository;
import art.moodframe.service.provider.GenerationJobInfo;
import art.moodframe.service.provider.gemini.GeminiApiService;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A video whose download fails fails like an image of the other flows: it costs nothing and so does its entry. */
class VideoFlowTest {

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
    }

    /*  waits until the JavaFX thread ran what was queued before  */
    private static void drainFxThread() throws Exception {
        for (int i = 0; i < 4; i++) {
            CountDownLatch done = new CountDownLatch(1);
            Platform.runLater(done::countDown);
            assertTrue(done.await(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void aVideoWhoseDownloadFailsCostsNothing() throws Exception {
        ChatRepository repository = mock(ChatRepository.class);
        ChatEntryTracker tracker = new ChatEntryTracker(repository, Runnable::run);
        ImageFileStore files = mock(ImageFileStore.class);
        when(files.downloadVideo(any(), any(), any())).thenThrow(new java.io.IOException("disk full"));
        GenerationJobInfo job = new GenerationJobInfo("omni-1", "Ready");
        job.setVideoDownloadUrl("https://example.invalid/video.mp4");
        GeminiApiService gemini = mock(GeminiApiService.class, CALLS_REAL_METHODS);
        doReturn(CompletableFuture.completedFuture(job)).when(gemini).submitGenerationRequest(any());
        VideoFlow flow = new VideoFlow(tracker, files, Runnable::run);

        ChatEntry entry = new ChatEntry.Builder().status(ChatEntry.Status.PROCESSING).cost(0.80).build();
        entry.getImages().add(new GeneratedImage.Builder().chatEntryId(entry.getId())
                .downloadStatus(GeneratedImage.DownloadStatus.PENDING).sortOrder(0).build());
        ImageGenerationRequest request = new ImageGenerationRequest("a lighthouse at dusk",
                GenerationModel.OMNI_720P.getApiModelId());
        request.setBaseCostPerImage(0.80);
        flow.submit(gemini, request, entry, 0);
        drainFxThread();

        GeneratedImage video = entry.getImages().get(0);
        assertEquals(GeneratedImage.DownloadStatus.FAILED, video.getDownloadStatus());
        assertEquals(I18nManager.getText("generation.error.video.download", "disk full"), video.getErrorMessage());
        assertEquals(0.0, video.getBaseCost());
        assertEquals(ChatEntry.Status.FAILED, entry.getStatus());
        assertEquals(0.0, entry.getCost(), 1e-9);
        verify(repository, atLeastOnce()).saveGeneratedImage(argThat((GeneratedImage saved) -> saved == video
                && saved.getDownloadStatus() == GeneratedImage.DownloadStatus.FAILED));
    }
}
