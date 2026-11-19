package art.moodframe.service.generation;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ConfigMocks;
import art.moodframe.config.LibraryPaths;
import art.moodframe.database.DatabaseManager;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationModel;
import art.moodframe.repository.ChatFiles;
import art.moodframe.repository.ChatGroupQueries;
import art.moodframe.repository.ChatRepository;
import art.moodframe.repository.ChatTrashRepository;
import art.moodframe.service.chat.ChatHistoryService;
import art.moodframe.service.chat.ChatLibrary;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.provider.GenerationJobInfo;
import art.moodframe.service.provider.ProviderRegistry;
import art.moodframe.service.provider.bfl.BflApiService;
import art.moodframe.service.provider.gemini.GeminiApiService;
import art.moodframe.service.provider.openai.OpenAiApiService;
import art.moodframe.service.trash.TrashGroup;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deleting an entry, or its chat, while its generation runs: the library cancels the generation first and waits for
 * its writes, so the trash books the cost the cancellation settled, and nothing the provider still delivers changes
 * the entry in the trash or brings it back once it is removed for good. Deleting an image the generation still makes,
 * or the last one it made, does the same; a finished image deleted beside running ones keeps its share in the cost of
 * the entry. The window and the MCP server share this path.
 */
class DeleteRunningEntryTest {

    @TempDir
    Path tempDir;

    private ChatRepository repository;
    private ChatTrashRepository trash;
    private ChatLibrary chats;
    private GeminiApiService gemini;
    private GenerationWorkers workers;
    private RequestResponseManager requests;
    private GenerationService generation;
    private ExecutorService pool;

    /*  the answers of Gemini, one per request in their order; an answer not given yet keeps its image running  */
    private final Queue<CompletableFuture<GenerationJobInfo>> answers = new ConcurrentLinkedQueue<>();
    /*  the dispatch of the requests, held back while a test wants its entry pending  */
    private final Queue<Runnable> heldBack = new ConcurrentLinkedQueue<>();
    private volatile boolean holdBack;
    private final List<String> dropped = new CopyOnWriteArrayList<>();

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

    @BeforeEach
    void wire() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("db").resolve("test.db"));
        database.initialize();
        repository = new ChatRepository(database);
        trash = new ChatTrashRepository(database, new ChatFiles() {
            @Override
            public void moveToTrash(String chatId, List<GeneratedImage> images) {
            }

            @Override
            public void restoreFromTrash(String chatId, List<GeneratedImage> images) {
            }

            @Override
            public boolean deletePermanently(String chatId, List<GeneratedImage> images) {
                return true;
            }

            @Override
            public void removeDropped(List<GeneratedImage> images) {
                images.forEach((GeneratedImage image) -> dropped.add(image.getId()));
            }
        });
        chats = new ChatLibrary(new ChatHistoryService(repository, trash, new ChatGroupQueries(database), null),
                new LibraryEvents());

        ApplicationConfig config = ConfigMocks.config();
        when(config.paths()).thenReturn(LibraryPaths.under(tempDir));
        when(config.accounts().isEnabled(ApiProvider.GOOGLE_GEMINI)).thenReturn(true);
        when(config.accounts().hasApiKey(ApiProvider.GOOGLE_GEMINI)).thenReturn(true);
        gemini = mock(GeminiApiService.class, CALLS_REAL_METHODS);
        doAnswer((invocation) -> {
            CompletableFuture<GenerationJobInfo> answer = new CompletableFuture<>();
            answers.add(answer);
            return answer;
        }).when(gemini).submitGenerationRequest(any());
        ProviderRegistry providers = new ProviderRegistry(mock(BflApiService.class, CALLS_REAL_METHODS), gemini,
                mock(OpenAiApiService.class, CALLS_REAL_METHODS));

        pool = Executors.newCachedThreadPool();
        workers = new GenerationWorkers();
        ChatEntryTracker tracker = new ChatEntryTracker(repository, trash, workers.store());
        tracker.setOnEntryStored(chats::entryStored);
        requests = new RequestResponseManager(config, providers, tracker,
                new ImageFileStore(config, HttpClient::newHttpClient), workers, (Runnable task) -> {
                    if (holdBack)
                        heldBack.add(task);
                    else
                        pool.execute(task);
                });
        generation = new GenerationService(config, chats, null, requests, providers, workers.store());
        chats.setGenerations(generation::settle);
    }

    @AfterEach
    void shutdown() {
        requests.shutdown();
        pool.shutdownNow();
    }

    /*  orders images of Nano Banana 2 in a new chat and waits until the entry is stored  */
    private ChatEntry order(Chat chat, int imageCount) throws Exception {
        return submit(chat, imageCount).entry();
    }

    private GenerationService.Submission submit(Chat chat, int imageCount) throws Exception {
        GenerationService.Submission submission = generation.submit(new GenerationOrder(chat, "a lighthouse", null,
                null, false, new GenerationOrder.Target(null, false, "16:9", "1K"),
                GenerationModel.NANO_BANANA2_1K.name(), imageCount,
                new GenerationOrder.Parameters(null, 2, null, false, null, null, null, null, null), null, null,
                ChatEntry.Source.UI));
        submission.stored().get(10, TimeUnit.SECONDS);
        return submission;
    }

    /*  an answer of Gemini with a real picture  */
    private static GenerationJobInfo picture() throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(4, 4,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", png);
        GenerationJobInfo job = new GenerationJobInfo("gemini-late", "Ready");
        job.setSynchronous(true);
        job.setBase64Image(Base64.getEncoder().encodeToString(png.toByteArray()));
        return job;
    }

    /*  waits until a condition, read on the JavaFX thread, holds  */
    private static void await(Supplier<Boolean> condition) throws Exception {
        for (int i = 0; i < 200; i++) {
            CompletableFuture<Boolean> holds = new CompletableFuture<>();
            Platform.runLater(() -> holds.complete(condition.get()));
            if (holds.get(10, TimeUnit.SECONDS))
                return;
            Thread.sleep(25);
        }
        throw new AssertionError("the condition never held");
    }

    /*  waits until the JavaFX thread and the store thread ran what the generation asked for so far  */
    private void drain(ChatEntry entry) throws Exception {
        for (int i = 0; i < 3; i++)
            await(() -> true);
        generation.settle(entry).get(10, TimeUnit.SECONDS);
    }

    /*  waits until the JavaFX thread and the store thread ran what was asked for so far, cancelling nothing  */
    private void flush() throws Exception {
        for (int i = 0; i < 3; i++)
            await(() -> true);
        CompletableFuture.runAsync(() -> { }, workers.store()).get(10, TimeUnit.SECONDS);
    }

    private Chat chat() throws Exception {
        return chats.createChat("Leuchtturm");
    }

    private double deletedCostOf(Chat chat) throws Exception {
        return repository.findChatById(chat.getId()).orElseThrow().getDeletedCost();
    }

    @Test
    void aPendingEntryDeletedIsCancelledFirstAndBooksNothing() throws Exception {
        holdBack = true;
        Chat chat = chat();
        ChatEntry entry = order(chat, 2);
        assertTrue(entry.getCost() > 0, "the order is estimated");

        chats.deleteEntry(entry);

        ChatEntry stored = repository.findChatEntryById(entry.getId()).orElseThrow();
        assertNotNull(stored.getDeletedAt());
        assertEquals(ChatEntry.Status.CANCELLED, stored.getStatus(), "the cancellation is stored before the deletion");
        assertEquals(0.0, stored.getCost());
        assertEquals(0.0, deletedCostOf(chat), "the chat books what the cancellation settled, not the estimate");

        /*  the dispatch held back finds the entry cancelled and sends nothing  */
        holdBack = false;
        while (!heldBack.isEmpty())
            heldBack.poll().run();
        verify(gemini, after(300).never()).submitGenerationRequest(any());
    }

    @Test
    void anImageDeliveredAfterTheDeletionChangesNothingAndBringsNothingBack() throws Exception {
        Chat chat = chat();
        ChatEntry entry = order(chat, 2);
        verify(gemini, timeout(10_000).times(2)).submitGenerationRequest(any());

        chats.deleteEntry(entry);

        ChatEntry stored = repository.findChatEntryById(entry.getId()).orElseThrow();
        assertEquals(ChatEntry.Status.CANCELLED, stored.getStatus());
        double booked = deletedCostOf(chat);
        assertEquals(stored.getCost(), booked, 1e-9, "the chat books the cost the cancellation stored");
        assertEquals(stored.getCost(), stored.getDeletedCost(), 1e-9);

        /*  Gemini still answers the first request: the image is dropped with its file, the entry keeps its cost  */
        answers.poll().complete(picture());
        await(() -> entry.getImages().get(0).getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED);
        drain(entry);
        assertEquals(1, dropped.size(), "the trash removes the file of the late image");
        assertTrue(repository.findGeneratedImagesByEntry(entry.getId()).isEmpty());
        ChatEntry inTrash = repository.findChatEntryById(entry.getId()).orElseThrow();
        assertEquals(ChatEntry.Status.CANCELLED, inTrash.getStatus());
        assertEquals(stored.getCost(), inTrash.getCost(), 1e-9, "the cost of an entry in the trash stays as booked");
        assertEquals(booked, deletedCostOf(chat), 1e-9);

        /*  removed for good, the entry does not come back with the second answer  */
        assertTrue(trash.deleteChatEntryPermanently(entry.getId()));
        GeneratedImage second = entry.getImages().get(1);
        String cancelled = second.getErrorMessage();
        answers.poll().complete(picture());
        await(() -> second.getDownloadStatus() == GeneratedImage.DownloadStatus.FAILED
                && !cancelled.equals(second.getErrorMessage()));
        drain(entry);
        assertTrue(repository.findChatEntryById(entry.getId()).isEmpty(), "no entry rises from the trash");
        assertTrue(repository.findChatEntriesByChatId(chat.getId()).isEmpty());
    }

    @Test
    void anEntryRestoredWhileItsGenerationStillDeliversShowsTheLateImage() throws Exception {
        Chat chat = chat();
        ChatEntry entry = order(chat, 2);
        verify(gemini, timeout(10_000).times(2)).submitGenerationRequest(any());
        chats.deleteEntry(entry);

        /*  the trash gives the entry back as a new object, while the generation still holds the one that left  */
        assertTrue(trash.restoreChatEntry(entry.getId()));
        chats.restored(TrashGroup.Kind.IMAGES, entry.getId());
        ChatEntry restored = chats.findEntry(entry.getId());
        assertNotNull(restored);
        assertNotSame(entry, restored);

        /*  Gemini still answers: the image is stored, and the chat shows it with the cost of the entry as stored  */
        answers.poll().complete(picture());
        await(() -> entry.getImages().get(0).getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED);
        flush();
        assertTrue(dropped.isEmpty(), "the image of an entry in use keeps its file");
        ChatEntry stored = repository.findChatEntryById(entry.getId()).orElseThrow();
        assertEquals(1, repository.findGeneratedImagesByEntry(entry.getId()).stream().filter((GeneratedImage image) ->
                image.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED).count());
        ChatEntry shown = chats.findEntry(entry.getId());
        assertEquals(stored.getCost(), shown.getCost(), 1e-9, "the window and the MCP server show the stored cost");
        assertEquals(1, ChatLibrary.imagesOf(shown).stream().filter((GeneratedImage image) ->
                image.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED).count(), "the late image shows");
        assertEquals(List.of(shown), ChatLibrary.entriesOf(chat), "the chat shows the entry once");
    }

    @Test
    void aChatMovedToTheTrashCancelsItsGenerationsFirst() throws Exception {
        holdBack = true;
        Chat chat = chat();
        ChatEntry entry = order(chat, 1);

        assertTrue(chats.moveToTrash(chat));

        ChatEntry stored = repository.findChatEntryById(entry.getId()).orElseThrow();
        assertEquals(ChatEntry.Status.CANCELLED, stored.getStatus());
        assertEquals(0.0, stored.getCost());
        assertNotNull(repository.findChatById(chat.getId()).orElseThrow().getDeletedAt());
        holdBack = false;
        while (!heldBack.isEmpty())
            heldBack.poll().run();
        verify(gemini, after(300).never()).submitGenerationRequest(any());
    }

    @Test
    void aCancellationIsStoredWhenItsFutureCompletes() throws Exception {
        holdBack = true;
        ChatEntry entry = order(chat(), 1);

        assertTrue(generation.cancel(entry).get(10, TimeUnit.SECONDS));

        ChatEntry stored = repository.findChatEntryById(entry.getId()).orElseThrow();
        assertEquals(ChatEntry.Status.CANCELLED, stored.getStatus(), "cancel_generation answers after the write");
        assertEquals(0.0, stored.getCost());
        assertFalse(generation.cancel(entry).get(10, TimeUnit.SECONDS), "an entry cancelled before is not running");
        verify(gemini, never()).submitGenerationRequest(any());
    }

    @Test
    void theRunningPlaceholderOfASingleImageDeletedCancelsTheGenerationFirst() throws Exception {
        Chat chat = chat();
        GenerationService.Submission submission = submit(chat, 1);
        ChatEntry entry = submission.entry();
        verify(gemini, timeout(10_000).times(1)).submitGenerationRequest(any());
        GeneratedImage placeholder = ChatLibrary.imagesOf(entry).get(0);

        assertTrue(chats.deleteImage(entry, placeholder), "the entry goes to the trash with its only image");

        assertEquals(ChatEntry.Status.CANCELLED, submission.finished().get(10, TimeUnit.SECONDS),
                "the order of the window and of the MCP server ends");
        ChatEntry stored = repository.findChatEntryById(entry.getId()).orElseThrow();
        assertNotNull(stored.getDeletedAt());
        assertEquals(ChatEntry.Status.CANCELLED, stored.getStatus());
        assertEquals(stored.getCost(), deletedCostOf(chat), 1e-9, "the chat books what the cancellation settled");

        /*  Gemini still answers: no placeholder takes the image, nothing is written or stored  */
        answers.poll().complete(picture());
        flush();
        assertTrue(dropped.isEmpty());
        assertTrue(repository.findGeneratedImagesByEntry(entry.getId()).isEmpty());
        assertEquals(stored.getCost(), repository.findChatEntryById(entry.getId()).orElseThrow().getCost(), 1e-9);
    }

    @Test
    void aFinishedImageDeletedBesideRunningOnesKeepsItsShareInTheEntryCost() throws Exception {
        Chat chat = chat();
        ChatEntry entry = order(chat, 3);
        verify(gemini, timeout(10_000).times(3)).submitGenerationRequest(any());
        answers.poll().complete(picture());
        answers.poll().complete(picture());
        await(() -> entry.getImages().stream().filter((GeneratedImage image) ->
                image.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED).count() == 2);
        flush();
        GeneratedImage made = ChatLibrary.imagesOf(entry).stream().filter((GeneratedImage image) ->
                image.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED).findFirst().orElseThrow();
        double price = made.getBaseCost();
        assertEquals(3 * price, entry.getCost(), 1e-9, "the estimate of the order");

        assertFalse(chats.deleteImage(entry, made));

        assertEquals(ChatEntry.Status.PROCESSING, entry.getStatus(), "the other images still run");
        assertEquals(price, entry.getDeletedCost(), 1e-9, "the deleted image books what it cost itself");
        answers.poll().complete(picture());
        await(() -> entry.getStatus() == ChatEntry.Status.COMPLETED);
        flush();
        ChatEntry stored = repository.findChatEntryById(entry.getId()).orElseThrow();
        assertEquals(3 * price, stored.getCost(), 1e-9, "the entry costs its three images, the deleted one included");
        assertEquals(price, stored.getDeletedCost(), 1e-9);
        assertEquals(3 * price, entry.getCost(), 1e-9);
        assertEquals(3 * price, chat.getDeletedCost() + entry.getCost(), 1e-9, "the chat total stays");
    }

    @Test
    void aFinishedImageRestoredWhileOthersRunLeavesTheGenerationItsPlaceholders() throws Exception {
        Chat chat = chat();
        ChatEntry entry = order(chat, 3);
        verify(gemini, timeout(10_000).times(3)).submitGenerationRequest(any());
        answers.poll().complete(picture());
        answers.poll().complete(picture());
        await(() -> entry.getImages().stream().filter((GeneratedImage image) ->
                image.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED).count() == 2);
        flush();
        GeneratedImage made = ChatLibrary.imagesOf(entry).stream().filter((GeneratedImage image) ->
                image.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED).findFirst().orElseThrow();
        double price = made.getBaseCost();
        GeneratedImage running = ChatLibrary.imagesOf(entry).stream().filter((GeneratedImage image) ->
                image.getDownloadStatus() != GeneratedImage.DownloadStatus.CACHED).findFirst().orElseThrow();
        assertFalse(chats.deleteImage(entry, made));

        /*  The trash gives the image back while the third image still runs: its placeholder stays in the entry  */
        assertTrue(trash.restoreImage(made.getId()));
        chats.restored(TrashGroup.Kind.IMAGES, made.getId());

        List<GeneratedImage> shown = ChatLibrary.imagesOf(entry);
        assertEquals(3, shown.size());
        assertTrue(shown.contains(running), "the placeholder of the running request keeps its object");
        assertEquals(List.of(0, 1, 2), shown.stream().map(GeneratedImage::getSortOrder).toList());
        assertEquals(0.0, entry.getDeletedCost(), 1e-9);
        assertEquals(ChatEntry.Status.PROCESSING, entry.getStatus());

        answers.poll().complete(picture());
        await(() -> entry.getStatus() == ChatEntry.Status.COMPLETED);
        flush();
        assertEquals(GeneratedImage.DownloadStatus.CACHED, running.getDownloadStatus(), "the third image arrives");
        ChatEntry stored = repository.findChatEntryById(entry.getId()).orElseThrow();
        assertEquals(ChatEntry.Status.COMPLETED, stored.getStatus());
        assertEquals(3, repository.findGeneratedImagesByEntry(entry.getId()).size());
        assertEquals(3 * price, stored.getCost(), 1e-9, "the entry costs its three images");
        assertEquals(0.0, stored.getDeletedCost(), 1e-9);
        assertEquals(3 * price, entry.getCost(), 1e-9);
        assertEquals(0.0, deletedCostOf(chat), 1e-9);
    }

    @Test
    void theLastImageMadeDeletedWhileOthersRunCancelsTheGenerationFirst() throws Exception {
        Chat chat = chat();
        GenerationService.Submission submission = submit(chat, 2);
        ChatEntry entry = submission.entry();
        verify(gemini, timeout(10_000).times(2)).submitGenerationRequest(any());
        answers.poll().complete(picture());
        await(() -> entry.getImages().stream().anyMatch((GeneratedImage image) ->
                image.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED));
        flush();
        GeneratedImage made = ChatLibrary.imagesOf(entry).stream().filter((GeneratedImage image) ->
                image.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED).findFirst().orElseThrow();
        double price = made.getBaseCost();

        assertFalse(chats.deleteImage(entry, made), "the entry keeps its cancelled image");

        assertEquals(ChatEntry.Status.CANCELLED, submission.finished().get(10, TimeUnit.SECONDS));
        ChatEntry stored = repository.findChatEntryById(entry.getId()).orElseThrow();
        assertNull(stored.getDeletedAt(), "the entry stays in its chat");
        assertEquals(ChatEntry.Status.CANCELLED, stored.getStatus());
        assertEquals(2 * price, stored.getCost(), 1e-9, "a cancelled entry keeps its estimate");
        assertEquals(price, stored.getDeletedCost(), 1e-9, "the deleted image books what it cost itself");
        assertEquals(1, repository.findGeneratedImagesByEntry(entry.getId()).size());

        /*  the second image still arrives: the entry costs both images, the deleted one booked once  */
        answers.poll().complete(picture());
        await(() -> ChatLibrary.imagesOf(entry).get(0).getDownloadStatus()
                == GeneratedImage.DownloadStatus.CACHED);
        flush();
        assertEquals(2 * price, repository.findChatEntryById(entry.getId()).orElseThrow().getCost(), 1e-9);
        assertEquals(0.0, deletedCostOf(chat), 1e-9);
    }
}
