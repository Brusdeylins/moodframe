package art.moodframe.service;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.repository.ChatRepository;
import art.moodframe.util.I18nManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against a fresh SQLite database and a file trash in a throwaway home directory, so the images are deleted
 * the way the main window does it and their files really move.
 */
class ImageTrashGroupTest {

    @TempDir
    static Path tempDir;

    private static DatabaseManager databaseManager;
    private static ChatRepository repository;
    private static TrashManager trashManager;
    private static ChatHistoryService history;
    private static ImageTrashGroup group;

    @BeforeAll
    static void createDatabaseAndTrash() throws Exception {
        databaseManager = new DatabaseManager(tempDir.resolve("db").resolve("test.db"));
        databaseManager.initialize();

        /*  The trash manager takes its directories from the home directory once, when it is created  */
        String home = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        try {
            trashManager = new TrashManager(60);
        }
        finally {
            System.setProperty("user.home", home);
        }

        ChatFileService chatFiles = new ChatFileService(trashManager, null,
                new FileManagerService(tempDir.resolve(".moodframe").resolve("images")));
        repository = new ChatRepository(databaseManager, chatFiles);
        history = new ChatHistoryService(repository, trashManager, chatFiles);
        group = new ImageTrashGroup(repository, chatFiles, trashManager, null);
    }

    @AfterAll
    static void stopTrash() {
        trashManager.shutdown();
    }

    @Test
    void restoresASingleImageWithItsFileAndTheCostOfItsEntry() throws Exception {
        Chat chat = chat("Hafen");
        ChatEntry entry = entry(chat, "a harbor at dawn", 0.08, 2);
        GeneratedImage image = images(entry).get(0);
        history.deleteImage(entry.getId(), image);
        assertFalse(Files.exists(Path.of(image.getLocalFilePath())));

        TrashEntry row = trashed(entry).orElseThrow();
        assertEquals("a harbor at dawn", row.name());
        assertEquals(I18nManager.getText("trash.context.images.singular", "Hafen", 1, String.format("%.2f", 0.04)),
                row.context());
        assertEquals(List.of(image.getId()), row.parts().stream().map(TrashEntry.Part::id).toList());
        assertEquals(trashManager.getTrashPath(image.getLocalFilePath(), false), row.parts().get(0).preview());

        assertTrue(group.restore(image.getId()));

        assertTrue(Files.exists(Path.of(image.getLocalFilePath())));
        assertFalse(Files.exists(Path.of(trashManager.getTrashPath(image.getLocalFilePath(), false))));
        assertEquals(2, repository.findGeneratedImagesByEntry(entry.getId()).size());
        assertEquals(0.0, repository.getDeletedCostForEntry(entry.getId()), 0.0001);
        assertTrue(trashed(entry).isEmpty());
        assertFalse(group.restore(image.getId()));
    }

    @Test
    void restoresAWholeEntryWithItsFourImagesAndTheCostOfItsChat() throws Exception {
        Chat chat = chat("Sturm");
        ChatEntry entry = entry(chat, "a lighthouse in a storm", 0.16, 4);
        double headerCost = headerCost(chat);
        List<GeneratedImage> images = images(entry);
        history.deleteEntry(entry.getId(), images);
        assertEquals(0.16, repository.findChatById(chat.getId()).orElseThrow().getDeletedCost(), 0.0001);

        TrashEntry row = trashed(entry).orElseThrow();
        assertEquals(4, row.parts().size());
        assertEquals(I18nManager.getText("trash.context.images.plural", "Sturm", 4, String.format("%.2f", 0.16)),
                row.context());

        assertTrue(group.restore(entry.getId()));

        assertNull(repository.findChatEntryById(entry.getId()).orElseThrow().getDeletedAt());
        assertEquals(4, repository.findGeneratedImagesByEntry(entry.getId()).size());
        for (GeneratedImage image : images)
            assertTrue(Files.exists(Path.of(image.getLocalFilePath())));
        assertEquals(0.0, repository.getDeletedCostForEntry(entry.getId()), 0.0001);
        assertEquals(0.0, repository.findChatById(chat.getId()).orElseThrow().getDeletedCost(), 0.0001);
        assertEquals(headerCost, headerCost(chat), 0.0001);
        assertTrue(trashed(entry).isEmpty());
    }

    @Test
    void bringsBackADeletedEntryWithASingleImageWhileTheOthersStayInTheTrash() throws Exception {
        Chat chat = chat("Wald");
        ChatEntry entry = entry(chat, "a forest", 0.08, 2);
        double headerCost = headerCost(chat);
        List<GeneratedImage> images = images(entry);
        history.deleteEntry(entry.getId(), images);

        assertTrue(group.restore(images.get(1).getId()));

        assertNull(repository.findChatEntryById(entry.getId()).orElseThrow().getDeletedAt());
        assertEquals(List.of(images.get(1).getId()),
                repository.findGeneratedImagesByEntry(entry.getId()).stream().map(GeneratedImage::getId).toList());
        assertEquals(0.04, repository.getDeletedCostForEntry(entry.getId()), 0.0001);
        assertEquals(0.0, repository.findChatById(chat.getId()).orElseThrow().getDeletedCost(), 0.0001);
        assertEquals(headerCost, headerCost(chat), 0.0001);
        assertEquals(List.of(images.get(0).getId()),
                trashed(entry).orElseThrow().parts().stream().map(TrashEntry.Part::id).toList());
    }

    @Test
    void deletesImagesForGoodWithTheirFilesAndADeletedEntryWithThem() throws Exception {
        Chat chat = chat("Weg");
        ChatEntry kept = entry(chat, "kept", 0.08, 2);
        GeneratedImage single = images(kept).get(0);
        history.deleteImage(kept.getId(), single);
        ChatEntry gone = entry(chat, "gone", 0.08, 2);
        List<GeneratedImage> images = images(gone);
        history.deleteEntry(gone.getId(), images);

        assertTrue(group.deletePermanently(single.getId()));
        assertTrue(group.deletePermanently(gone.getId()));

        assertTrue(repository.findGeneratedImageById(single.getId()).isEmpty());
        assertFalse(Files.exists(Path.of(trashManager.getTrashPath(single.getLocalFilePath(), false))));
        assertEquals(1, repository.findGeneratedImagesByEntry(kept.getId()).size());
        assertEquals(0.04, repository.getDeletedCostForEntry(kept.getId()), 0.0001);
        assertTrue(repository.findChatEntryById(gone.getId()).isEmpty());
        for (GeneratedImage image : images)
            assertFalse(Files.exists(Path.of(trashManager.getTrashPath(image.getLocalFilePath(), false))));
        assertTrue(trashed(kept).isEmpty());
        assertTrue(trashed(gone).isEmpty());
        assertFalse(group.deletePermanently(gone.getId()));
    }

    @Test
    void leavesTheImagesOfADeletedChatToTheChats() throws Exception {
        Chat chat = chat("Ganz");
        ChatEntry entry = entry(chat, "whole chat", 0.08, 2);
        history.deleteImage(entry.getId(), images(entry).get(0));
        assertTrue(trashed(entry).isPresent());

        repository.softDeleteChat(chat.getId());

        assertTrue(trashed(entry).isEmpty());
    }

    @Test
    void theCleanupRemovesEachImageSixtyDaysAfterItsOwnDeletion() throws Exception {
        Chat chat = chat("Alt");
        ChatEntry entry = entry(chat, "old and new", 0.12, 3);
        List<GeneratedImage> images = images(entry);
        history.deleteImage(entry.getId(), images.get(0));
        history.deleteImage(entry.getId(), images.get(1));
        backdate("generated_images", images.get(0).getId(), 61);
        ChatEntry expired = entry(chat, "expired entry", 0.04, 1);
        GeneratedImage expiredImage = images(expired).get(0);
        history.deleteEntry(expired.getId(), List.of(expiredImage));
        backdate("generated_images", expiredImage.getId(), 61);
        backdate("chat_entries", expired.getId(), 61);

        TrashCenter center = new TrashCenter();
        center.register(group);
        List<String> reported = new ArrayList<>();
        center.addListener(new TrashCenter.Listener() {
            @Override
            public void restored(TrashGroup.Kind kind, String id) {
            }

            @Override
            public void deleted(TrashGroup.Kind kind, List<String> ids) {
                reported.addAll(ids);
            }
        });
        TrashCleanupService cleanup = new TrashCleanupService(center, 60);
        try {
            assertEquals(2, cleanup.forceCleanup());
        }
        finally {
            cleanup.shutdown();
        }

        assertTrue(repository.findGeneratedImageById(images.get(0).getId()).isEmpty());
        assertFalse(Files.exists(Path.of(trashManager.getTrashPath(images.get(0).getLocalFilePath(), false))));
        assertEquals(List.of(images.get(1).getId()),
                trashed(entry).orElseThrow().parts().stream().map(TrashEntry.Part::id).toList());
        assertTrue(repository.findChatEntryById(expired.getId()).isEmpty());
        assertEquals(2, reported.size());
    }

    private Chat chat(String name) throws Exception {
        Chat chat = new Chat.Builder().name(name).build();
        repository.saveChat(chat);
        return chat;
    }

    /** Stores an entry with images of a model that costs 0.04 per image when deleted, each with its file. */
    private ChatEntry entry(Chat chat, String prompt, double cost, int images) throws Exception {
        ChatEntry entry = new ChatEntry.Builder().chatId(chat.getId()).userPrompt(prompt).cost(cost).build();
        repository.saveChatEntry(entry);
        Path directory = Files.createDirectories(tempDir.resolve(".moodframe").resolve("images").resolve(chat.getId()));
        for (int i = 0; i < images; i++) {
            Path file = Files.writeString(directory.resolve(entry.getId() + "-" + i + ".png"), "image " + i);
            repository.saveGeneratedImage(new GeneratedImage.Builder().chatEntryId(entry.getId()).model("flux-pro")
                    .localFilePath(file.toString()).sortOrder(i).build());
        }
        return entry;
    }

    private List<GeneratedImage> images(ChatEntry entry) throws Exception {
        return repository.findGeneratedImagesByEntry(entry.getId());
    }

    private Optional<TrashEntry> trashed(ChatEntry entry) {
        return group.list().stream().filter((row) -> row.id().equals(entry.getId())).findFirst();
    }

    /** The total the header of the main window shows for a chat: its entries plus the cost of its deleted entries. */
    private double headerCost(Chat chat) throws Exception {
        return repository.findChatEntriesByChatId(chat.getId()).stream().mapToDouble(ChatEntry::getCost).sum()
                + repository.findChatById(chat.getId()).orElseThrow().getDeletedCost();
    }

    /** Moves the deletion of a row back in time, in the format of CURRENT_TIMESTAMP. */
    private void backdate(String table, String id, int days) throws Exception {
        String sql = "UPDATE " + table + " SET deleted_at = ? WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, LocalDateTime.now().minusDays(days)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            stmt.setString(2, id);
            stmt.executeUpdate();
        }
    }
}
