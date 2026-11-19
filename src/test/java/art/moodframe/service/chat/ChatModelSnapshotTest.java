package art.moodframe.service.chat;

import art.moodframe.model.Chat;
import art.moodframe.model.ChatCost;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.service.library.LibraryEvents;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule of the shared chat models: the library changes the lists of a chat on the calling thread (as for the MCP
 * server), while another thread (as the JavaFX thread) reads them only as snapshots under the {@link ModelLock}, which
 * never fail with a {@link java.util.ConcurrentModificationException} and do not follow later changes.
 */
class ChatModelSnapshotTest {

    private static final int ROUNDS = 400;

    @Test
    void snapshotsReadSafelyWhileAnotherThreadChangesTheChat() throws Exception {
        ChatLibrary library = new ChatLibrary(null, new LibraryEvents());
        Chat chat = library.newChat("Shared");
        AtomicBoolean changing = new AtomicBoolean(true);
        AtomicInteger reads = new AtomicInteger();
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> changes = CompletableFuture.runAsync(() -> {
                try {
                    for (int round = 0; round < ROUNDS; round++) {
                        ChatEntry entry = entry(chat, round);
                        library.addEntry(chat, entry);
                        library.deleteImage(entry, ChatLibrary.imagesOf(entry).get(0));
                        if (round % 3 == 0)
                            library.deleteEntry(entry);
                    }
                }
                catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                finally {
                    changing.set(false);
                }
            }, threads);
            CompletableFuture<Void> readings = CompletableFuture.runAsync(() -> {
                while (changing.get()) {
                    double sum = 0;
                    for (ChatEntry entry : ChatLibrary.entriesOf(chat))
                        for (GeneratedImage image : ChatLibrary.imagesOf(entry))
                            sum += image.getBaseCost();
                    sum += ModelLock.read(() -> ChatCost.total(chat));
                    assertTrue(sum >= 0);
                    reads.incrementAndGet();
                }
            }, threads);
            CompletableFuture.allOf(changes, readings).get(60, TimeUnit.SECONDS);
        }
        finally {
            threads.shutdownNow();
        }
        assertTrue(reads.get() > 0, "the reader ran meanwhile");
        assertEquals(ROUNDS - (ROUNDS + 2) / 3, ChatLibrary.entriesOf(chat).size());
    }

    @Test
    void aSnapshotKeepsItsStateAndCannotBeChanged() {
        ChatLibrary library = new ChatLibrary(null, new LibraryEvents());
        Chat chat = library.newChat("Snapshot");
        ChatEntry first = entry(chat, 0);
        library.addEntry(chat, first);

        List<ChatEntry> entries = ChatLibrary.entriesOf(chat);
        List<GeneratedImage> images = ChatLibrary.imagesOf(first);
        library.addEntry(chat, entry(chat, 1));
        ModelLock.change(() -> first.getImages().clear());

        assertEquals(List.of(first), entries);
        assertEquals(3, images.size());
        assertThrows(UnsupportedOperationException.class, () -> entries.add(first));
        assertEquals(List.of(), ChatLibrary.entriesOf(null));
        assertEquals(List.of(), ChatLibrary.imagesOf(null));
    }

    /*  an entry of three images  */
    private static ChatEntry entry(Chat chat, int number) {
        ChatEntry entry = new ChatEntry.Builder().chatId(chat.getId()).userPrompt("prompt " + number).build();
        for (int index = 0; index < 3; index++) {
            GeneratedImage image = new GeneratedImage();
            image.setBaseCost(0.01);
            entry.getImages().add(image);
        }
        entry.setCost(0.03);
        return entry;
    }
}
