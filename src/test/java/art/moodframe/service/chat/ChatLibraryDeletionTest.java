package art.moodframe.service.chat;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatCost;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.repository.ChatRepository;
import art.moodframe.service.library.LibraryEvents;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The library deletes entries and images for the main window and the MCP tools alike: it books the costs in the
 * database and in the shown chat once, and reports the change once, on the Monocle headless platform.
 */
class ChatLibraryDeletionTest {

    @TempDir
    static Path tempDir;

    private static ChatRepository repository;
    private static ChatHistoryService history;

    @BeforeAll
    static void startToolkitAndCreateDatabase() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await(10, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);

        DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve("db").resolve("test.db"));
        databaseManager.initialize();
        repository = new ChatRepository(databaseManager);
        history = TestChatServices.history(repository, databaseManager);
    }

    @Test
    void anImageAndThenItsEntryAreBookedOnceInTheDatabaseAndInTheChat() throws Exception {
        List<String> reported = Collections.synchronizedList(new ArrayList<>());
        LibraryEvents events = new LibraryEvents();
        events.addListener((kind, id) -> reported.add(kind + " " + id));
        ChatLibrary library = new ChatLibrary(history, events);
        Chat chat = library.createChat("Kosten");
        ChatEntry entry = entry(library, chat, true, 0.09, 0.03, 0.06);
        ChatEntry other = entry(library, chat, true, 0.04, 0.04);
        handled();
        reported.clear();

        assertFalse(library.deleteImage(entry, entry.getImages().get(1)));
        assertEquals(0.06, entry.getDeletedCost(), 1e-9, "the FLUX.2 image costs what it was billed");
        assertEquals(0.06, repository.findChatEntryById(entry.getId()).orElseThrow().getDeletedCost(), 1e-9);
        assertEquals(0.13, ChatCost.total(chat), 1e-9);

        library.deleteEntry(entry);
        assertEquals(List.of(other), chat.getEntries());
        assertEquals(0.09, chat.getDeletedCost(), 1e-9, "the whole entry, its image deleted before included");
        assertEquals(0.09, repository.findChatById(chat.getId()).orElseThrow().getDeletedCost(), 1e-9);
        assertEquals(0.13, ChatCost.total(chat), 1e-9);

        assertTrue(library.deleteImage(other, other.getImages().get(0)), "the last image takes its entry");
        assertTrue(chat.getEntries().isEmpty());
        assertEquals(0.13, chat.getDeletedCost(), 1e-9);
        assertEquals(0.13, repository.findChatById(chat.getId()).orElseThrow().getDeletedCost(), 1e-9);

        handled();
        assertEquals(List.of("ENTRY " + entry.getId(), "ENTRY " + entry.getId(), "ENTRY " + other.getId()),
                reported);
    }

    @Test
    void withoutDatabaseTheChatBooksTheSameCosts() throws Exception {
        ChatLibrary library = new ChatLibrary(null, new LibraryEvents());
        Chat chat = library.newChat("Ohne Datenbank");
        ChatEntry entry = entry(library, chat, false, 0.152, 0.134, 0.018);
        ChatEntry other = entry(library, chat, false, 0.08, 0.04, 0.04);

        library.deleteImage(entry, entry.getImages().get(1));
        assertEquals(0.018, entry.getDeletedCost(), 1e-9);

        library.deleteEntry(entry);
        assertEquals(0.152, chat.getDeletedCost(), 1e-9);

        library.deleteImage(other, other.getImages().get(0));
        library.deleteImage(other, other.getImages().get(0));
        assertTrue(chat.getEntries().isEmpty());
        assertEquals(0.232, chat.getDeletedCost(), 1e-9);
        assertEquals(0.232, ChatCost.total(chat), 1e-9);
    }

    /*  an entry of the library with an image for each stored cost, the images stored with a database  */
    private static ChatEntry entry(ChatLibrary library, Chat chat, boolean stored, double cost, double... imageCosts)
            throws Exception {
        ChatEntry entry = new ChatEntry.Builder().chatId(chat.getId()).userPrompt("prompt").cost(cost).build();
        library.addEntry(chat, entry);
        List<GeneratedImage> images = new ArrayList<>();
        for (double imageCost : imageCosts) {
            GeneratedImage image = new GeneratedImage.Builder().chatEntryId(entry.getId()).model("flux-2-pro")
                    .baseCost(imageCost).sortOrder(images.size()).build();
            if (stored)
                repository.saveGeneratedImage(image);
            images.add(image);
        }
        CountDownLatch added = new CountDownLatch(1);
        Platform.runLater(() -> {
            entry.getImages().setAll(images);
            added.countDown();
        });
        assertTrue(added.await(10, TimeUnit.SECONDS));
        return entry;
    }

    /*  waits until the JavaFX thread handled the reported changes  */
    private static void handled() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(done::countDown);
        assertTrue(done.await(10, TimeUnit.SECONDS));
    }
}
