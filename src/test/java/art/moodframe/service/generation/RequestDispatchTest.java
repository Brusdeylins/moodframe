package art.moodframe.service.generation;

import art.moodframe.config.ConfigMocks;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.repository.ChatRepository;
import art.moodframe.service.provider.BflApiService;
import art.moodframe.service.provider.GeminiApiService;
import art.moodframe.service.provider.GenerationJobInfo;
import art.moodframe.service.provider.OpenAiApiService;
import art.moodframe.service.provider.ProviderRegistry;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/** The request manager finds the flow of a provider by what the provider tells (D4), not by its name. */
class RequestDispatchTest {

    private RequestResponseManager requests;

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

    @AfterEach
    void shutdown() {
        if (requests != null)
            requests.shutdown();
    }

    @Test
    void aProviderThatAnswersWithAJobIsPolledWhateverItsName() throws Exception {
        GeminiApiService jobs = mock(GeminiApiService.class, CALLS_REAL_METHODS);
        doReturn(false).when(jobs).answersWithImage();
        doReturn(true).when(jobs).usesSeeds();
        doReturn(CompletableFuture.completedFuture(new GenerationJobInfo("job-7", "Pending"))).when(jobs)
                .submitGenerationRequest(any());
        ProviderRegistry providers = new ProviderRegistry(mock(BflApiService.class, CALLS_REAL_METHODS), jobs,
                mock(OpenAiApiService.class, CALLS_REAL_METHODS));
        ChatRepository repository = mock(ChatRepository.class);
        requests = TestRequests.create(ConfigMocks.config(), repository, providers, Runnable::run);

        ChatEntry entry = new ChatEntry.Builder().status(ChatEntry.Status.PENDING).build();
        ImageGenerationRequest request = new ImageGenerationRequest("a lighthouse",
                GenerationModel.NANO_BANANA2_1K.getApiModelId());
        request.setImageCount(1);
        requests.submitImageGeneration(request, entry, null).get(10, TimeUnit.SECONDS);

        /*  the job replaced the placeholder, with a random seed as the provider uses seeds  */
        verify(repository, timeout(10_000)).saveGeneratedImage(any());
        CompletableFuture<GeneratedImage> image = new CompletableFuture<>();
        Platform.runLater(() -> image.complete(entry.getImages().get(0)));
        assertEquals("job-7", image.get(10, TimeUnit.SECONDS).getGenerationJobId());
        assertTrue(image.get().getSeed() != 0);
        assertEquals(1, entry.getImages().size());
    }

    @Test
    void anEntryCancelledBeforeItsDispatchSendsNothingAndCostsNothing() throws Exception {
        GeminiApiService gemini = mock(GeminiApiService.class, CALLS_REAL_METHODS);
        ProviderRegistry providers = new ProviderRegistry(mock(BflApiService.class, CALLS_REAL_METHODS), gemini,
                mock(OpenAiApiService.class, CALLS_REAL_METHODS));
        java.util.Queue<Runnable> io = new java.util.concurrent.ConcurrentLinkedQueue<>();
        requests = TestRequests.create(ConfigMocks.config(), mock(ChatRepository.class), providers, io::add);

        /*  cancel_generation right after generate: the entry is cancelled while its dispatch still waits  */
        ChatEntry entry = new ChatEntry.Builder().status(ChatEntry.Status.PENDING).cost(0.04).build();
        ImageGenerationRequest request = new ImageGenerationRequest("a lighthouse",
                GenerationModel.NANO_BANANA2_1K.getApiModelId());
        request.setImageCount(1);
        CompletableFuture<Void> submitted = requests.submitImageGeneration(request, entry, null);
        assertTrue(requests.cancelEntry(entry).get(10, TimeUnit.SECONDS));
        for (int i = 0; i < 100 && !submitted.isDone(); i++) {
            Runnable task = io.poll();
            if (task != null)
                task.run();
            else
                Thread.sleep(20);
        }

        submitted.get(10, TimeUnit.SECONDS);
        verify(gemini, never()).submitGenerationRequest(any());
        CompletableFuture<Integer> images = new CompletableFuture<>();
        Platform.runLater(() -> images.complete(entry.getImages().size()));
        assertEquals(0, images.get(10, TimeUnit.SECONDS), "no placeholder for a request never sent");
        assertEquals(ChatEntry.Status.CANCELLED, entry.getStatus());
        assertEquals(0.0, entry.getCost());
    }
}
