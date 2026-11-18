package art.moodframe.service.library;

import art.moodframe.service.ConfigurationExporter;
import art.moodframe.database.DatabaseConfig;
import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.Frame;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.repository.ChatRepository;
import art.moodframe.service.chat.ChatHistoryService;
import art.moodframe.service.chat.ChatLibrary;
import art.moodframe.service.chat.ChatTrashGroup;
import art.moodframe.service.trash.TrashGroup;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every successful write of the library is reported exactly once, on the JavaFX thread of the Monocle headless
 * platform; a refused write is not reported.
 */
class LibraryEventsTest {

    /** A reported change. */
    private record Change(LibraryEvents.Kind kind, String id) {
    }

    @TempDir
    static Path tempDir;

    private static ChatHistoryService history;

    private final LibraryEvents events = new LibraryEvents();
    private final List<Change> changes = Collections.synchronizedList(new ArrayList<>());

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

        Path database = tempDir.resolve("db").resolve("test.db");
        DatabaseConfig.initialize(database.toString());
        history = new ChatHistoryService(new ChatRepository(new DatabaseManager(database)), null, null);
    }

    @BeforeEach
    void listen() {
        events.addListener((kind, id) -> {
            assertTrue(Platform.isFxApplicationThread(), "the listeners learn of a change on the JavaFX thread");
            changes.add(new Change(kind, id));
        });
    }

    /*  the changes reported since the last call, once the JavaFX thread handled all of them  */
    private List<Change> reported() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(done::countDown);
        assertTrue(done.await(10, TimeUnit.SECONDS));
        List<Change> reported = List.copyOf(changes);
        changes.clear();
        return reported;
    }

    private List<Change> once(LibraryEvents.Kind kind, String id) {
        return List.of(new Change(kind, id));
    }

    @Test
    void reportsEveryWriteOfAMoodOnce() throws Exception {
        MoodService moods = new MoodService(TestLibraryServices.database(), TestLibraryServices.blocks(),
                TestLibraryServices.palettes(), events);
        Mood mood = new Mood(null, "Event Mood");
        assertTrue(moods.add(mood));
        assertEquals(once(LibraryEvents.Kind.MOOD, mood.getId()), reported());

        mood.setName("Event Mood 2");
        assertTrue(moods.update(mood));
        assertEquals(once(LibraryEvents.Kind.MOOD, mood.getId()), reported());

        moods.reorder(List.of(mood.getId()));
        assertEquals(once(LibraryEvents.Kind.MOOD, null), reported());

        assertTrue(moods.moveToTrash(mood.getId()));
        assertEquals(once(LibraryEvents.Kind.MOOD, mood.getId()), reported());
        assertTrue(moods.restore(mood.getId()));
        assertEquals(once(LibraryEvents.Kind.MOOD, mood.getId()), reported());
        assertTrue(moods.deletePermanently(mood.getId()));
        assertEquals(once(LibraryEvents.Kind.MOOD, mood.getId()), reported());

        /*  a refused write changes nothing  */
        assertFalse(moods.add(new Mood(null, "Film Noir")));
        assertFalse(moods.update(moods.getDefaults().get(0)), "a standard mood stays as it is");
        assertEquals(List.of(), reported());
    }

    @Test
    void reportsEveryWriteOfAFrameOnce() throws Exception {
        FrameService frames = new FrameService(TestLibraryServices.database(), events);
        Frame frame = new Frame(null, "Event Frame");
        frame.setAspectRatio("3:2");
        assertTrue(frames.add(frame));
        assertEquals(once(LibraryEvents.Kind.FRAME, frame.getId()), reported());

        frame.setResolution("4K");
        assertTrue(frames.update(frame));
        assertEquals(once(LibraryEvents.Kind.FRAME, frame.getId()), reported());

        assertTrue(frames.moveToTrash(frame.getId()));
        assertTrue(frames.restore(frame.getId()));
        assertTrue(frames.deletePermanently(frame.getId()));
        assertEquals(List.of(new Change(LibraryEvents.Kind.FRAME, frame.getId()),
                new Change(LibraryEvents.Kind.FRAME, frame.getId()), new Change(LibraryEvents.Kind.FRAME,
                        frame.getId())), reported());

        assertFalse(frames.moveToTrash("std:frame:social-post"));
        assertEquals(List.of(), reported());
    }

    @Test
    void reportsEveryWriteOfABuildingBlockOnceWithItsType() throws Exception {
        PromptBlockService blocks = new PromptBlockService(TestLibraryServices.database(), events);
        PromptBlock light = new PromptBlock(null, PromptBlock.Type.LIGHT, "Event Light", "event light", null, false);
        assertTrue(blocks.addCustom(light));
        assertEquals(once(LibraryEvents.Kind.LIGHT, light.getId()), reported());

        light.setText("another light");
        assertTrue(blocks.updateCustom(light));
        assertEquals(once(LibraryEvents.Kind.LIGHT, light.getId()), reported());

        PromptBlock copy = new PromptBlock(null, PromptBlock.Type.CAMERA, "Event Camera", "", null, true);
        assertTrue(blocks.addCopy(copy));
        assertEquals(once(LibraryEvents.Kind.CAMERA, copy.getId()), reported());

        assertTrue(blocks.reorderCustom(PromptBlock.Type.LIGHT, List.of(light.getId())));
        assertEquals(once(LibraryEvents.Kind.LIGHT, null), reported());

        assertTrue(blocks.deleteCustom(light.getId()));
        assertEquals(once(LibraryEvents.Kind.LIGHT, light.getId()), reported());
        assertTrue(blocks.restore(light.getId()));
        assertEquals(once(LibraryEvents.Kind.LIGHT, light.getId()), reported());
        assertTrue(blocks.deleteCustom(light.getId()));
        assertTrue(blocks.deletePermanently(light.getId()));
        assertEquals(List.of(new Change(LibraryEvents.Kind.LIGHT, light.getId()),
                new Change(LibraryEvents.Kind.LIGHT, light.getId())), reported());

        assertFalse(blocks.deleteCustom(light.getId()), "gone for good");
        assertFalse(blocks.addCustom(new PromptBlock(null, PromptBlock.Type.CAMERA, "Event Camera", "", null, true)));
        assertEquals(List.of(), reported());
    }

    @Test
    void reportsEveryWriteOfAPaletteOnce() throws Exception {
        ColorStyleService palettes = new ColorStyleService(TestLibraryServices.database(), events);
        ColorStyle palette = new ColorStyle("Event Palette", List.of("#112233", "#445566"));
        assertTrue(palettes.addCustomStyle(palette));
        assertNotNull(palette.getId());
        assertEquals(once(LibraryEvents.Kind.COLOR, palette.getId()), reported());

        assertTrue(palettes.updateCustomStyle("Event Palette", new ColorStyle("Event Palette 2",
                List.of("#778899"))));
        assertEquals(once(LibraryEvents.Kind.COLOR, palette.getId()), reported());

        assertTrue(palettes.deleteCustomStyle("Event Palette 2"));
        assertTrue(palettes.restoreStyle(palette.getId()));
        assertTrue(palettes.deleteStylePermanently(palette.getId()));
        assertEquals(List.of(new Change(LibraryEvents.Kind.COLOR, palette.getId()),
                new Change(LibraryEvents.Kind.COLOR, palette.getId()), new Change(LibraryEvents.Kind.COLOR,
                        palette.getId())), reported());

        assertFalse(palettes.deleteCustomStyle("Event Palette 2"));
        assertEquals(List.of(), reported());
    }

    @Test
    void reportsEverySwitchedFavoriteOnce() throws Exception {
        FavoriteService favorites = new FavoriteService(TestLibraryServices.database(), events);
        assertTrue(favorites.toggle(FavoriteService.Kind.STYLE, "event-style"));
        assertFalse(favorites.toggle(FavoriteService.Kind.STYLE, "event-style"));
        assertEquals(List.of(new Change(LibraryEvents.Kind.FAVORITE, "event-style"),
                new Change(LibraryEvents.Kind.FAVORITE, "event-style")), reported());
    }

    @Test
    void reportsEveryChangeOfTheChatsOnce() throws Exception {
        ChatLibrary library = new ChatLibrary(history, events);
        Chat chat = library.createChat("Event Chat");
        assertEquals(once(LibraryEvents.Kind.CHAT, chat.getId()), reported());
        assertTrue(history.findChat(chat.getId()).isPresent(), "a chat of another caller is stored at once");

        library.renameChat(chat, "Event Chat 2");
        assertEquals(once(LibraryEvents.Kind.CHAT, chat.getId()), reported());
        assertEquals("Event Chat 2", history.findChat(chat.getId()).orElseThrow().getName());

        ChatEntry entry = new ChatEntry.Builder().chatId(chat.getId()).userPrompt("an apple").build();
        library.addEntry(chat, entry);
        assertEquals(once(LibraryEvents.Kind.ENTRY, entry.getId()), reported());
        assertEquals(List.of(entry), chat.getEntries());

        assertTrue(library.moveToTrash(chat));
        assertEquals(once(LibraryEvents.Kind.CHAT, chat.getId()), reported());
        assertTrue(chat.isDeleted());

        /*  The trash reports what left it, the library shows it again or drops it  */
        assertTrue(new ChatTrashGroup(new ChatRepository(new DatabaseManager(tempDir.resolve("db")
                .resolve("test.db")))).restore(chat.getId()));
        library.restored(TrashGroup.Kind.CHATS, chat.getId());
        assertEquals(once(LibraryEvents.Kind.CHAT, chat.getId()), reported());
        assertFalse(chat.isDeleted());
        library.deleted(TrashGroup.Kind.CHATS, List.of(chat.getId()));
        assertEquals(once(LibraryEvents.Kind.CHAT, chat.getId()), reported());
        assertNull(library.find(chat.getId()));

        /*  The main window stores a new chat with its first entry only  */
        Chat unsaved = library.newChat("Unsaved");
        assertEquals(once(LibraryEvents.Kind.CHAT, unsaved.getId()), reported());
        assertFalse(history.findChat(unsaved.getId()).isPresent());
        assertEquals(unsaved, library.getChats().get(0));
    }

    @Test
    void anImportOfABackupIsReportedByTheServicesOfTheApplication() throws Exception {
        PromptBlockService blocks = new PromptBlockService(TestLibraryServices.database(), events);
        ColorStyleService palettes = new ColorStyleService(TestLibraryServices.database(), events);
        MoodService moods = new MoodService(TestLibraryServices.database(), blocks, palettes, events);
        ConfigurationExporter exporter = new ConfigurationExporter(blocks, palettes, moods,
                new FrameService(TestLibraryServices.database(), events));
        Mood mood = new Mood(null, "Imported Mood");
        assertTrue(moods.add(mood));
        java.io.File file = tempDir.resolve("events-backup.json").toFile();
        exporter.exportConfiguration(art.moodframe.config.ConfigMocks.config(), file,
                java.util.Set.of(ConfigurationExporter.ConfigCategory.MOODS));
        assertTrue(moods.deletePermanently(mood.getId()));
        reported();

        /*  the main window listens to these events: an import must not go to services of its own  */
        assertTrue(exporter.importConfiguration(file, art.moodframe.config.ConfigMocks.config()).isSuccess());
        assertTrue(reported().contains(new Change(LibraryEvents.Kind.MOOD, mood.getId())));
    }
}
