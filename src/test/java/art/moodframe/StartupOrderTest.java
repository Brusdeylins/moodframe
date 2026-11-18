package art.moodframe;

import art.moodframe.model.Chat;
import art.moodframe.service.chat.ChatLibrary;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.trash.TrashCenter;
import art.moodframe.service.trash.TrashCleanupService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The start cleans up the trash before it loads the chats, on its own thread, and the library holds the loaded chats
 * for the main window, which loads them no more.
 */
class StartupOrderTest {

    /*  a cleanup that notes when it runs, and may fail  */
    private static TrashCleanupService cleanup(List<String> steps, boolean failing) {
        return new TrashCleanupService(new TrashCenter()) {
            @Override
            public int performAutoCleanup() {
                steps.add("cleanup on " + Thread.currentThread().getName());
                if (failing)
                    throw new IllegalStateException("the trash folder is locked");
                return 0;
            }
        };
    }

    /*  chats without database that note when they load  */
    private static ChatLibrary chats(List<String> steps) {
        return new ChatLibrary(null, new LibraryEvents()) {
            @Override
            public void load() {
                steps.add("load on " + Thread.currentThread().getName());
                super.load();
            }
        };
    }

    /*  runs the step of the start on a thread like that of the application  */
    private static void onStartupThread(Runnable step) throws Exception {
        Thread thread = new Thread(step, "MoodFrame-startup");
        thread.start();
        thread.join(10_000);
    }

    @Test
    void theTrashIsCleanedUpBeforeTheChatsLoad() throws Exception {
        List<String> steps = new CopyOnWriteArrayList<>();
        TrashCleanupService cleanup = cleanup(steps, false);
        ChatLibrary chats = chats(steps);

        onStartupThread(() -> MoodframeApplication.cleanUpAndLoad(cleanup, chats));
        cleanup.shutdown();

        assertEquals(List.of("cleanup on MoodFrame-startup", "load on MoodFrame-startup"), steps);
        List<Chat> loaded = chats.chats();
        assertEquals(1, loaded.size(), "the library holds the chats for the window, here the welcome chat");
        assertEquals("Welcome to MoodFrame", loaded.get(0).getName());
    }

    @Test
    void aFailedCleanupDoesNotKeepTheChatsFromLoading() throws Exception {
        List<String> steps = new CopyOnWriteArrayList<>();
        TrashCleanupService cleanup = cleanup(steps, true);
        ChatLibrary chats = chats(steps);

        onStartupThread(() -> MoodframeApplication.cleanUpAndLoad(cleanup, chats));
        cleanup.shutdown();

        assertEquals(List.of("cleanup on MoodFrame-startup", "load on MoodFrame-startup"), steps);
        assertNotEquals(List.of(), chats.chats());
    }
}
