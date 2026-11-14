package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GenerationJobInfo;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.repository.ChatRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Checks the retry with the approved maximum when BFL rejects the safety tolerance of a new account. */
class BflSafetyToleranceTest {

    private static final String NOT_APPROVED = "{\"detail\":{\"code\":\"safety_tolerance_not_approved\","
            + "\"requested_safety_tolerance\":5,\"maximum_approved_safety_tolerance\":4}}";

    /*  as from the HTTP client: the error arrives wrapped in a CompletionException  */
    private static CompletableFuture<GenerationJobInfo> rejected(String body) {
        return CompletableFuture.<GenerationJobInfo>failedFuture(
                new BflApiServiceImpl.BflApiException(422, "HTTP 422", body)).thenApply((info) -> info);
    }

    /*  submits one FLUX.2 image with safety tolerance 5, every submission is answered with the given body  */
    private static void submit(String body, ApplicationConfig config, ChatEntryTracker tracker,
            List<Integer> sent, List<Integer> lowered) {
        BflApiService api = mock(BflApiService.class);
        when(api.submitGenerationRequest(any())).thenAnswer((invocation) -> {
            sent.add(invocation.<ImageGenerationRequest>getArgument(0).getSafetyTolerance());
            return rejected(body);
        });
        BflGenerationFlow flow = new BflGenerationFlow(config, api, mock(ChatRepository.class), tracker,
                mock(ImageFileStore.class), mock(ScheduledExecutorService.class));
        flow.setOnSafetyToleranceLowered(lowered::add);
        ImageGenerationRequest request = new ImageGenerationRequest("an apple", "flux.2-pro");
        request.setSafetyTolerance(5);
        flow.submit(request, mock(ChatEntry.class), 0);
    }

    @Test
    void retriesExactlyOnceWithTheApprovedMaximum() {
        ApplicationConfig config = mock(ApplicationConfig.class);
        ChatEntryTracker tracker = mock(ChatEntryTracker.class);
        List<Integer> sent = new CopyOnWriteArrayList<>();
        List<Integer> lowered = new CopyOnWriteArrayList<>();
        submit(NOT_APPROVED, config, tracker, sent, lowered);

        /*  the retry is rejected, too: no second retry, the entry fails  */
        verify(tracker, timeout(5000)).markFailed(any(), anyString());
        assertEquals(List.of(5, 4), sent);
        assertEquals(List.of(4), lowered);
        verify(config).setFlux2SafetyTolerance(4);
    }

    @Test
    void keepsTheErrorWithoutAnApprovedMaximum() {
        ApplicationConfig config = mock(ApplicationConfig.class);
        ChatEntryTracker tracker = mock(ChatEntryTracker.class);
        List<Integer> sent = new CopyOnWriteArrayList<>();
        List<Integer> lowered = new CopyOnWriteArrayList<>();
        submit("{\"detail\":{\"code\":\"safety_tolerance_not_approved\",\"requested_safety_tolerance\":5}}",
                config, tracker, sent, lowered);

        verify(tracker, timeout(5000)).markFailed(any(), anyString());
        assertEquals(List.of(5), sent);
        assertEquals(List.of(), lowered);
        verify(config, never()).setFlux2SafetyTolerance(anyInt());
    }

    @Test
    void readsTheApprovedMaximumOnlyFromTheMatchingError() {
        assertEquals(4, new BflApiServiceImpl.BflApiException(422, "", NOT_APPROVED)
                .getMaximumApprovedSafetyTolerance());
        assertNull(new BflApiServiceImpl.BflApiException(400, "", NOT_APPROVED)
                .getMaximumApprovedSafetyTolerance());
        assertNull(new BflApiServiceImpl.BflApiException(422, "", NOT_APPROVED.replace("not_approved", "other"))
                .getMaximumApprovedSafetyTolerance());
        assertNull(new BflApiServiceImpl.BflApiException(422, "", "Unprocessable Entity")
                .getMaximumApprovedSafetyTolerance());
        assertNull(new BflApiServiceImpl.BflApiException(422, "", null)
                .getMaximumApprovedSafetyTolerance());
    }

    @Test
    void defaultsToSafetyToleranceTwoLikeBfl() throws Exception {
        /*  a configuration without any stored value, without reading or writing ~/.moodframe  */
        ApplicationConfig config = mock(ApplicationConfig.class, CALLS_REAL_METHODS);
        Field properties = ApplicationConfig.class.getDeclaredField("properties");
        properties.setAccessible(true);
        properties.set(config, new Properties());
        assertEquals(2, config.getFlux2SafetyTolerance());
    }
}
