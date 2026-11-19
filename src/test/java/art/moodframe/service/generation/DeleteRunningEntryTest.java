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
import art.moodframe.service.provider.BflApiService;
import art.moodframe.service.provider.GeminiApiService;
import art.moodframe.service.provider.GenerationJobInfo;
import art.moodframe.service.provider.OpenAiApiService;
import art.moodframe.service.provider.ProviderRegistry;
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
 * the entry in the trash or brings it back once it is removed for good. The window and the MCP server share this path.
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
        requests = new RequestResponseManager(config, providers,
                new ChatEntryTracker(repository, trash, workers.store()),
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
        GenerationService.Submission submission = generation.submit(new GenerationOrder(chat, "a lighthouse", null,
                null, false, new GenerationOrder.Target(null, false, "16:9", "1K"),
                GenerationModel.NANO_BANANA2_1K.name(), imageCount,
                new GenerationOrder.Parameters(null, 2, null, false, null, null, null, null, null), null, null,
                ChatEntry.Source.UI));
        submission.stored().get(10, TimeUnit.SECONDS);
        return submission.entry();
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
}
