package art.moodframe.service.generation;

import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.repository.ChatRepository;
import art.moodframe.service.provider.GenerationJobInfo;
import art.moodframe.service.provider.ImageProvider;
import art.moodframe.service.provider.gemini.GeminiApiService;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** An image of a provider that answers with the image, whose file cannot be written, fails alone and costs nothing. */
class SynchronousImageFlowTest {

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

    private static GenerationJobInfo answer() {
        GenerationJobInfo job = new GenerationJobInfo("gemini-1", "Ready");
        job.setSynchronous(true);
        job.setBase64Image("aW1hZ2U=");
        return job;
    }

    @Test
    void anImageWhoseFileCannotBeWrittenFailsIsStoredAndCostsNothing() throws Exception {
        ChatRepository repository = mock(ChatRepository.class);
        ChatEntryTracker tracker = new ChatEntryTracker(repository, Runnable::run);
        ImageFileStore files = mock(ImageFileStore.class);
        when(files.writeBase64(any(), any())).thenAnswer((invocation) -> {
            GeneratedImage image = invocation.getArgument(1);
            if (image.getSortOrder() == 0)
                throw new java.io.IOException("disk full");
            return new ImageFileStore.StoredImage(Path.of("b.png"), "b.png", "png", "image/png", 1024, 1024, 1000);
        });
        GeminiApiService gemini = mock(GeminiApiService.class, CALLS_REAL_METHODS);
        doReturn(CompletableFuture.completedFuture(answer())).when(gemini).submitGenerationRequest(any());
        doReturn(new ImageProvider.ExtraCosts(0.0, 0.0)).when(gemini).extraCosts(any(), any());
        SynchronousImageFlow flow = new SynchronousImageFlow(tracker, files, Runnable::run);

        ChatEntry entry = new ChatEntry.Builder().status(ChatEntry.Status.PROCESSING).cost(0.08).build();
        for (int i = 0; i < 2; i++)
            entry.getImages().add(new GeneratedImage.Builder().chatEntryId(entry.getId())
                    .downloadStatus(GeneratedImage.DownloadStatus.PENDING).sortOrder(i).build());
        ImageGenerationRequest request = new ImageGenerationRequest("a lighthouse",
                GenerationModel.NANO_BANANA2_1K.getApiModelId());
        request.setBaseCostPerImage(0.04);
        flow.submit(gemini, request, entry, 0);
        flow.submit(gemini, request, entry, 1);
        drainFxThread();

        GeneratedImage failed = entry.getImages().get(0);
        assertEquals(GeneratedImage.DownloadStatus.FAILED, failed.getDownloadStatus());
        assertEquals(I18nManager.getText("generation.error.save", "disk full"), failed.getErrorMessage());
        assertEquals(0.0, failed.getBaseCost());
        verify(repository, atLeastOnce()).saveGeneratedImage(argThat((GeneratedImage saved) -> saved == failed
                && saved.getDownloadStatus() == GeneratedImage.DownloadStatus.FAILED));
        assertEquals(ChatEntry.Status.COMPLETED, entry.getStatus(), "the other image was made");
        assertEquals(0.04, entry.getCost(), 1e-9);
        verify(repository, atLeastOnce()).updateChatEntry(eq(entry));
    }
}
