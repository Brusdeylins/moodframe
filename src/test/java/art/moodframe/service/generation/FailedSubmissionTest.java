package art.moodframe.service.generation;

import art.moodframe.config.ConfigMocks;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.repository.ChatRepository;
import art.moodframe.service.provider.BflApiService;
import art.moodframe.service.provider.GeminiApiService;
import art.moodframe.service.provider.OpenAiApiService;
import art.moodframe.service.provider.ProviderException;
import art.moodframe.service.provider.ProviderRegistry;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * A submission that fails is stored as failed with the reason past its wrappers, before the listeners learn of it, so
 * a restart keeps the reason; an entry of the MCP server gets it in English.
 */
class FailedSubmissionTest {

    private final ChatRepository repository = mock(ChatRepository.class);
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

    /*  submits a Gemini image whose submission fails with a missing key, wrapped as a future wraps it  */
    private ChatEntry submitFailing(ChatEntry.Source source) throws Exception {
        GeminiApiService gemini = mock(GeminiApiService.class, CALLS_REAL_METHODS);
        doThrow(new CompletionException(ProviderException.noKey("Gemini"))).when(gemini)
                .submitGenerationRequest(any());
        ProviderRegistry providers = new ProviderRegistry(mock(BflApiService.class, CALLS_REAL_METHODS), gemini,
                mock(OpenAiApiService.class, CALLS_REAL_METHODS));
        requests = TestRequests.create(ConfigMocks.config(), repository, providers, Runnable::run);

        ChatEntry entry = new ChatEntry.Builder().status(ChatEntry.Status.PENDING).source(source).build();
        CompletableFuture<ChatEntry.Status> told = new CompletableFuture<>();
        requests.addEntryListener((ChatEntry updated) -> {
            if (updated.getStatus() == ChatEntry.Status.FAILED)
                told.complete(updated.getStatus());
        });
        ImageGenerationRequest request = new ImageGenerationRequest("a lighthouse",
                GenerationModel.NANO_BANANA2_1K.getApiModelId());
        request.setImageCount(1);
        requests.submitImageGeneration(request, entry, null).get(10, TimeUnit.SECONDS);

        verify(repository, timeout(10_000)).saveChatEntry(entry);
        assertEquals(ChatEntry.Status.FAILED, told.get(10, TimeUnit.SECONDS));
        assertEquals(ChatEntry.Status.FAILED, entry.getStatus());
        return entry;
    }

    @Test
    void aFailedSubmissionIsStoredWithItsUnwrappedReason() throws Exception {
        ChatEntry entry = submitFailing(ChatEntry.Source.UI);
        assertEquals(ProviderException.noKey("Gemini").getMessage(), entry.getErrorMessage());
        assertTrue(!entry.getErrorMessage().contains("Exception"), entry.getErrorMessage());
    }

    @Test
    void anEntryOfTheMcpServerGetsTheReasonInEnglish() throws Exception {
        ChatEntry entry = submitFailing(ChatEntry.Source.MCP);
        assertEquals(ProviderException.noKey("Gemini").getMessage(Locale.ENGLISH), entry.getErrorMessage());
    }
}
