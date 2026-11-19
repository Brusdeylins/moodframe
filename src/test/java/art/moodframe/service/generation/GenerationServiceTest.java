package art.moodframe.service.generation;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ConfigMocks;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.CameraSettings;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryFrame;
import art.moodframe.model.EntryMood;
import art.moodframe.model.Frame;
import art.moodframe.model.GenerationCost;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.chat.ChatHistoryService;
import art.moodframe.service.chat.ChatLibrary;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.prompt.PromptComposer;
import art.moodframe.util.I18nManager;
import art.moodframe.service.provider.MockProviders;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Carries out generation orders without the JavaFX interface, the provider replaced by a mock; the chats change on the
 * JavaFX thread of the Monocle headless platform.
 */
class GenerationServiceTest {

    private static final GenerationOrder.Target WIDE_2K = new GenerationOrder.Target(null, false, "16:9", "2K");

    private final ApplicationConfig config = ConfigMocks.config();
    private final ChatHistoryService chatHistory = mock(ChatHistoryService.class);
    private final ChatLibrary chats = new ChatLibrary(chatHistory, new LibraryEvents());
    private final RequestResponseService requests = mock(RequestResponseService.class);
    private final GenerationService service = new GenerationService(config, chats, null, requests,
            MockProviders.preparing(), Runnable::run);
    private final Chat chat = new Chat.Builder().id("chat-1").name("Test").build();

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await(10, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);
    }

    @BeforeEach
    void enableEveryProvider() {
        when(config.accounts().isEnabled(ApiProvider.BFL)).thenReturn(true);
        when(config.accounts().isEnabled(ApiProvider.GOOGLE_GEMINI)).thenReturn(true);
        when(config.accounts().isEnabled(ApiProvider.OPENAI)).thenReturn(true);
        when(config.accounts().hasApiKey(ApiProvider.BFL)).thenReturn(true);
        when(config.accounts().hasApiKey(ApiProvider.GOOGLE_GEMINI)).thenReturn(true);
        when(config.accounts().hasApiKey(ApiProvider.OPENAI)).thenReturn(true);
        when(config.getOpenAiModels()).thenReturn(List.of("gpt-image-1", "qwen-image-2.1"));
        when(config.getFlux2SafetyTolerance()).thenReturn(2);
        when(requests.submitImageGeneration(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    private static GenerationOrder.Parameters parameters(Integer videoSeconds) {
        return new GenerationOrder.Parameters(null, 2, null, false, null, null, null, null, videoSeconds);
    }

    private GenerationOrder order(String prompt, PromptComposer.Selection blocks, String modelId, int imageCount,
            GenerationOrder.Target target) {
        return new GenerationOrder(chat, prompt, blocks, null, false, target, modelId, imageCount, parameters(null),
                null, List.of(), ChatEntry.Source.UI);
    }

    private GenerationOrder order(String modelId, GenerationOrder.Target target) {
        return order("an apple", null, modelId, 1, target);
    }

    /*  the requests handed to the provider, in their order  */
    private List<ImageGenerationRequest> sent() {
        ArgumentCaptor<ImageGenerationRequest> request = ArgumentCaptor.forClass(ImageGenerationRequest.class);
        verify(requests, org.mockito.Mockito.atLeastOnce()).submitImageGeneration(request.capture(), any(), any());
        return request.getAllValues();
    }

    private static GenerationService.Failure failure(org.junit.jupiter.api.function.Executable submit) {
        return assertThrows(GenerationService.GenerationException.class, submit).getFailure();
    }

    @Test
    void sendsThePromptOfTheUserAloneOrWithTheBuildingBlocks() throws Exception {
        ChatEntry plain = service.submit(order("FLUX2_PRO", WIDE_2K)).entry();
        assertEquals("an apple", plain.getEnhancedPrompt());
        assertEquals("None", plain.getStyle());

        PromptBlock style = new PromptBlock("style-1", PromptBlock.Type.STYLE, "Watercolor", "soft watercolor", null,
                false);
        PromptComposer.Selection blocks = new PromptComposer.Selection(style, null, null, null, null);
        ChatEntry styled = service.submit(order("an apple", blocks, "FLUX2_PRO", 1, WIDE_2K)).entry();
        String composed = new PromptComposer(2000).compose("an apple", blocks);
        assertTrue(composed.startsWith("Image style: soft watercolor."), composed);
        assertEquals("an apple", styled.getUserPrompt(), "the entry keeps the prompt of the user for copying");
        assertEquals(composed, styled.getEnhancedPrompt());
        assertEquals("Watercolor", styled.getStyle());

        List<ImageGenerationRequest> requests = sent();
        assertEquals("an apple", requests.get(0).getPrompt());
        assertNull(requests.get(0).getStylePreset());
        assertEquals(composed, requests.get(1).getPrompt());
        assertEquals("Watercolor", requests.get(1).getStylePreset());
        assertEquals(List.of(plain, styled), chat.getEntries());
    }

    @Test
    void asksToChangeOnlyTheCameraOfAReferenceImage() throws Exception {
        PromptBlock camera = PromptBlockService.camera("camera-1", "Slow", new CameraSettings(null, null, null,
                CameraSettings.Shutter.MOTION_BLUR, null, null, null));
        PromptComposer.Selection blocks = new PromptComposer.Selection(null, null, null, camera, null);
        GenerationOrder edit = new GenerationOrder(chat, "a woman on a ledge", blocks, null, false, WIDE_2K,
                "NANO_BANANA2_1K", 1, parameters(null), null, List.of("aW1hZ2U="), ChatEntry.Source.UI);

        assertEquals(new PromptComposer(2000).compose("a woman on a ledge", blocks, true),
                service.preview(edit).prompt());
        assertTrue(service.preview(edit).prompt().contains("\n" + PromptComposer.EDIT_CAMERA_INTRO + " Camera: "));
        assertFalse(service.preview(order("a woman on a ledge", blocks, "NANO_BANANA2_1K", 1, WIDE_2K)).prompt()
                .contains(PromptComposer.EDIT_CAMERA_INTRO), "no intro without reference image");
        service.submit(edit);
        assertTrue(sent().get(0).getPrompt().contains(PromptComposer.EDIT_CAMERA_INTRO));
    }

    @Test
    void refusesAPromptTooLongForTheModelBeforeAnythingIsStored() throws Exception {
        String prompt = "a".repeat(1990);
        PromptBlock style = new PromptBlock("style-1", PromptBlock.Type.STYLE, "Watercolor", "soft watercolor", null,
                false);
        GenerationService.GenerationException tooLong = assertThrows(GenerationService.GenerationException.class,
                () -> service.submit(order(prompt, new PromptComposer.Selection(style, null, null, null, null),
                        "FLUX2_PRO", 1, WIDE_2K)));
        assertEquals(GenerationService.Failure.PROMPT_TOO_LONG, tooLong.getFailure());
        assertEquals(2000, tooLong.getMaxPromptLength());
        assertTrue(tooLong.getPromptLength() > 2000, "the building blocks count");
        verify(requests, never()).submitImageGeneration(any(), any(), any());
        verifyNoInteractions(chatHistory);
        assertTrue(chat.getEntries().isEmpty());

        /*  Google takes long prompts  */
        assertDoesNotThrow(() -> service.submit(order("a".repeat(2001), null, "NANO_BANANA2_1K", 1, WIDE_2K)));
        assertEquals(GenerationService.Failure.EMPTY_PROMPT,
                failure(() -> service.submit(order(" ", null, "FLUX2_PRO", 1, WIDE_2K))));
    }

    @Test
    void refusesEveryProviderWithoutItsKey() {
        when(config.accounts().hasApiKey(ApiProvider.BFL)).thenReturn(false);
        when(config.accounts().hasApiKey(ApiProvider.GOOGLE_GEMINI)).thenReturn(false);
        when(config.accounts().hasApiKey(ApiProvider.OPENAI)).thenReturn(false);
        for (String modelId : List.of("FLUX2_PRO", "FLUX3_IMAGE_1K", "NANO_BANANA2_1K", "OMNI_720P", "gpt-image-1"))
            assertEquals(GenerationService.Failure.MISSING_KEY,
                    failure(() -> service.submit(order(modelId, WIDE_2K))), modelId);
        verify(requests, never()).submitImageGeneration(any(), any(), any());

        /*  A key opens its own provider only  */
        when(config.accounts().hasApiKey(ApiProvider.GOOGLE_GEMINI)).thenReturn(true);
        assertDoesNotThrow(() -> service.submit(order("NANO_BANANA2_1K", WIDE_2K)));
        assertEquals(GenerationService.Failure.MISSING_KEY, failure(() -> service.submit(order("FLUX2_PRO", WIDE_2K))));
    }

    @Test
    void refusesAnUnknownModelAndAnOrderWithoutChat() {
        assertEquals(GenerationService.Failure.UNKNOWN_MODEL,
                failure(() -> service.submit(order("dall-e-9", WIDE_2K))), "not listed by the gateway");
        assertEquals(GenerationService.Failure.UNKNOWN_MODEL,
                failure(() -> service.submit(order("OPENAI_IMAGE", WIDE_2K))), "the placeholder is no model");
        assertEquals(GenerationService.Failure.NO_CHAT, failure(() -> service.submit(new GenerationOrder(null,
                "an apple", null, null, false, WIDE_2K, "FLUX2_PRO", 1, parameters(null), null, List.of(),
                ChatEntry.Source.UI))));
        verify(requests, never()).submitImageGeneration(any(), any(), any());
    }

    @Test
    void mapsTheFrameToTheFormatAndResolutionOfEachModel() throws Exception {
        Frame frame = new Frame("frame-1", "Wide");
        GenerationOrder.Target target = new GenerationOrder.Target(frame, false, "16:9", "2K");
        ChatEntry flux = service.submit(order("FLUX2_PRO", target)).entry();
        ChatEntry gptImage = service.submit(order("gpt-image-1", target)).entry();
        ChatEntry nanoBanana = service.submit(order("NANO_BANANA2_2K", target)).entry();
        ChatEntry qwen = service.submit(order("qwen-image-2.1", new GenerationOrder.Target(null, false, "16:9", "1K")))
                .entry();
        ChatEntry square = service.submit(order("FLUX2_PRO", new GenerationOrder.Target(null, false, null, null)))
                .entry();

        assertEquals("16:9", flux.getSize());
        assertEquals("3:2", gptImage.getSize(), "gpt-image has no 16:9, 3:2 comes closest");
        assertEquals(GenerationModel.OPENAI_PREFIX + "gpt-image-1", gptImage.getModel());
        assertEquals(new EntryFrame("frame-1", "Wide", "16:9", "2K"), flux.getFrame());
        assertEquals("16:9", square.getSize(), "the entry shows 16:9 without target");

        List<ImageGenerationRequest> requests = sent();
        assertEquals("flux.2-pro", requests.get(0).getModel());
        assertEquals("16:9", requests.get(0).getAspectRatio());
        assertEquals(16.0 / 9, (double) requests.get(0).getWidth() / requests.get(0).getHeight(), 0.02);
        assertEquals("gpt-image-1", requests.get(1).getModel());
        assertEquals("3:2", requests.get(1).getAspectRatio());
        assertEquals("2K", requests.get(2).getImageSize());
        assertEquals("16:9", requests.get(2).getAspectRatio());
        assertEquals("2K", requests.get(3).getImageSize(), "Qwen-Image is too weak at 1K");
        assertEquals("1:1", requests.get(4).getAspectRatio());
        assertEquals(requests.get(4).getWidth(), requests.get(4).getHeight());

        /*  The family takes the variant closest to the target  */
        assertEquals("NANO_BANANA2_2K", service.modelFor("Nano Banana 2", "2K"));
        assertEquals("NANO_BANANA2_4K", service.modelFor("Nano Banana 2", "4K"));
        assertNull(service.modelFor("Unknown", "2K"));
    }

    @Test
    void estimatesTheCostOfAllImages() throws Exception {
        ChatEntry images = service.submit(order("an apple", null, "FLUX3_IMAGE_1K", 3, WIDE_2K)).entry();
        assertEquals(3 * GenerationModel.FLUX3_IMAGE_1K.getPricePerImage(), images.getCost(), 1e-9);

        ChatEntry video = service.submit(new GenerationOrder(chat, "a wave", null, null, false, WIDE_2K, "OMNI_720P",
                1, parameters(5), null, List.of(), ChatEntry.Source.UI)).entry();
        assertEquals(GenerationModel.OMNI_720P.videoPrice(5), video.getCost(), 1e-9);

        List<ImageGenerationRequest> requests = sent();
        assertEquals(3, requests.get(0).getImageCount());
        assertEquals(GenerationModel.FLUX3_IMAGE_1K.getPricePerImage(), requests.get(0).getBaseCostPerImage(), 1e-9);
        assertEquals(1, requests.get(1).getImageCount());
        assertEquals(5, requests.get(1).getVideoDuration());
    }

    @Test
    void previewAndEntryCountGoogleSearchLikeTheStoredImages() throws Exception {
        GenerationOrder.Parameters search = new GenerationOrder.Parameters(null, 2, null, false, null, true, null,
                null, null);
        GenerationOrder order = new GenerationOrder(chat, "a harbor", null, null, false, WIDE_2K, "NANO_BANANA2_1K",
                2, search, null, List.of(), ChatEntry.Source.UI);
        double perImage = GenerationModel.NANO_BANANA2_1K.getPricePerImage() + GenerationCost.SEARCH_PER_IMAGE;

        GenerationService.Preview preview = service.preview(order);
        assertEquals(perImage, preview.costPerImage(), 1e-9);
        assertEquals(2 * perImage, preview.cost(), 1e-9);
        assertEquals(2 * perImage, service.submit(order).entry().getCost(), 1e-9);

        /*  OpenAI has no known price  */
        assertEquals(0.0, service.preview(order("gpt-image-1", WIDE_2K)).cost(), 1e-9);
    }

    @Test
    void namesTheModelByAnIdThatDoesNotDependOnTheLanguage() throws Exception {
        Locale before = I18nManager.getCurrentLocale();
        try {
            I18nManager.setLocale(Locale.ENGLISH);
            String english = ModelLabels.displayName(GenerationModel.FLUX2_PRO);
            assertEquals("FLUX2_PRO", GenerationModel.idOf(english));

            I18nManager.setLocale(Locale.GERMAN);
            String german = ModelLabels.displayName(GenerationModel.FLUX2_PRO);
            assertNotEquals(english, german);
            assertEquals("FLUX2_PRO", GenerationModel.idOf(german));
            assertEquals(german, ModelLabels.itemOf("FLUX2_PRO"));
            assertEquals("gpt-image-1", GenerationModel.idOf(ModelLabels.itemOf("gpt-image-1")));

            ChatEntry entry = service.submit(order("FLUX2_PRO", WIDE_2K)).entry();
            assertEquals(GenerationModel.FLUX2_PRO.getDisplayName(), entry.getModel(), "stored in English");
            assertEquals("flux.2-pro", sent().get(0).getModel());
        } finally {
            I18nManager.setLocale(before);
        }
    }

    @Test
    void handsTheMoodTheAttachmentAndTheReferenceImagesOn() throws Exception {
        MoodService moods = mock(MoodService.class);
        Mood mood = new Mood("mood-1", "Calm");
        EntryMood copy = new EntryMood("mood-1", "Calm", Map.of());
        when(moods.snapshot(eq(mood), eq(true), any())).thenReturn(copy);
        GenerationService withMoods = new GenerationService(config, chats, moods, requests,
                MockProviders.preparing(), Runnable::run);
        File attachment = new File("attached.png");

        ChatEntry entry = withMoods.submit(new GenerationOrder(chat, "an apple", null, mood, true, WIDE_2K,
                "FLUX3_IMAGE_1K", 1, parameters(null), attachment, List.of("first", "second"),
                ChatEntry.Source.UI)).entry();
        assertSame(copy, entry.getMood());

        ArgumentCaptor<ImageGenerationRequest> request = ArgumentCaptor.forClass(ImageGenerationRequest.class);
        verify(requests).submitImageGeneration(request.capture(), eq(entry), eq(attachment));
        assertEquals(List.of("first", "second"), request.getValue().getInputImages());
    }

    @Test
    void reportsTheStateTheEntryEndsIn() throws Exception {
        GenerationService.Submission submission = service.submit(order("FLUX2_PRO", WIDE_2K));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ChatEntry>> listener = ArgumentCaptor.forClass(Consumer.class);
        verify(requests).addEntryListener(listener.capture());
        assertTrue(submission.submitted().isDone());

        ChatEntry entry = submission.entry();
        entry.setStatus(ChatEntry.Status.PROCESSING);
        listener.getValue().accept(entry);
        assertFalse(submission.finished().isDone());

        entry.setStatus(ChatEntry.Status.COMPLETED);
        listener.getValue().accept(entry);
        assertEquals(ChatEntry.Status.COMPLETED, submission.finished().getNow(null));
        verify(requests).removeEntryListener(listener.getValue());
    }

    @Test
    void aFailedSubmissionFailsItsFutureAndIsMarkedFailedOnceByTheRequests() throws Exception {
        IllegalArgumentException refused = new IllegalArgumentException("refused");
        when(requests.submitImageGeneration(any(), any(), any())).thenReturn(CompletableFuture.failedFuture(refused));

        GenerationService.Submission submission = service.submit(order("FLUX2_PRO", WIDE_2K));
        java.util.concurrent.ExecutionException failure = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> submission.submitted().get(10, TimeUnit.SECONDS));
        assertSame(refused, failure.getCause());
        verify(requests, never()).markFailed(any(), any());
    }

    @Test
    void storesTheEntryOffTheCallingThreadAndDispatchesItOnlyOnceItIsStored() throws Exception {
        List<Runnable> storing = new java.util.ArrayList<>();
        GenerationService queued = new GenerationService(config, chats, null, requests, MockProviders.preparing(),
                storing::add);

        GenerationService.Submission submission = queued.submit(order("FLUX2_PRO", WIDE_2K));
        assertEquals(ChatEntry.Status.PENDING, submission.entry().getStatus(), "the entry to show at once");
        verifyNoInteractions(chatHistory);
        verify(requests, never()).submitImageGeneration(any(), any(), any());
        assertFalse(chat.getEntries().contains(submission.entry()));

        storing.forEach(Runnable::run);
        verify(chatHistory).saveChatEntry(submission.entry());
        assertTrue(chat.getEntries().contains(submission.entry()));
        assertTrue(submission.stored().isDone());
        verify(requests).submitImageGeneration(any(), eq(submission.entry()), any());
        assertTrue(submission.submitted().isDone());
    }

    @Test
    void anEntryThatCannotBeAddedFailsWithoutDispatch() {
        GenerationService rejecting = new GenerationService(config, chats, null, requests, MockProviders.preparing(),
                (Runnable task) -> {
                    throw new java.util.concurrent.RejectedExecutionException("closed");
                });

        GenerationService.Submission submission = assertDoesNotThrow(() -> rejecting.submit(order("FLUX2_PRO",
                WIDE_2K)));
        assertTrue(submission.stored().isCompletedExceptionally());
        assertTrue(submission.submitted().isCompletedExceptionally());
        verify(requests).markFailed(eq(submission.entry()), any());
        verify(requests, never()).submitImageGeneration(any(), any(), any());
    }
}
