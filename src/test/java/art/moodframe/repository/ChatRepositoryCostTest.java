package art.moodframe.repository;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatCost;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The costs a deletion books, against a fresh SQLite database: a deleted image costs its share of the entry, whatever
 * the model, and the total of the chat stays what was spent, through deletion and restore.
 */
class ChatRepositoryCostTest {

    @TempDir
    static Path tempDir;

    private static DatabaseManager databaseManager;
    private static ChatRepository repository;

    @BeforeAll
    static void createDatabase() throws Exception {
        databaseManager = new DatabaseManager(tempDir.resolve("db").resolve("test.db"));
        databaseManager.initialize();
        repository = new ChatRepository(databaseManager);
    }

    @Test
    void aDeletedFluxTwoImageCostsWhatItWasBilled() throws Exception {
        Chat chat = chat("FLUX.2");
        ChatEntry entry = entry(chat, "flux-2-pro", 0.09, new double[] {0.03, 0.06});
        GeneratedImage large = images(entry).get(1);

        assertTrue(repository.softDeleteImage(large.getId()));

        assertEquals(0.06, imageDeletedCost(large), 1e-9);
        assertEquals(0.06, repository.getDeletedCostForEntry(entry.getId()), 1e-9);
        assertEquals(0.09, total(chat), 1e-9);
        assertFalse(repository.softDeleteImage(large.getId()), "a deleted image is not booked twice");
        assertEquals(0.06, repository.getDeletedCostForEntry(entry.getId()), 1e-9);
    }

    @Test
    void aDeletedGeminiImageCostsItsGenerationSearchAndReasoning() throws Exception {
        Chat chat = chat("Gemini");
        ChatEntry entry = save(new ChatEntry.Builder().chatId(chat.getId()).userPrompt("gemini").cost(0.152).build());
        image(entry, "gemini-3-pro-image-preview", 0.134, 0.0, 0.0);
        GeneratedImage second = image(entry, "gemini-3-pro-image-preview", 0.0, 0.014, 0.004);

        assertTrue(repository.softDeleteImage(second.getId()));

        assertEquals(0.018, imageDeletedCost(second), 1e-9);
        assertEquals(0.018, repository.getDeletedCostForEntry(entry.getId()), 1e-9);
    }

    @Test
    void theLastDeletedImageTakesItsEntryAndTheChatBooksTheWholeCost() throws Exception {
        Chat chat = chat("Letztes Bild");
        ChatEntry entry = entry(chat, "gpt-image-1", 0.08, new double[] {0.04, 0.04});
        List<GeneratedImage> images = images(entry);

        repository.softDeleteImage(images.get(0).getId());
        repository.softDeleteImage(images.get(1).getId());

        ChatEntry deleted = repository.findChatEntryById(entry.getId()).orElseThrow();
        assertNotNull(deleted.getDeletedAt());
        assertEquals(0.08, deleted.getDeletedCost(), 1e-9);
        assertEquals(0.08, repository.findChatById(chat.getId()).orElseThrow().getDeletedCost(), 1e-9);
        assertEquals(0.08, total(chat), 1e-9);
    }

    @Test
    void anEntryDeletedAfterOneOfItsImagesKeepsTheTotalThroughDeletionAndRestore() throws Exception {
        Chat chat = chat("Hin und zurück");
        ChatEntry entry = entry(chat, "veo-3", 0.50, new double[] {0.20, 0.30});
        List<GeneratedImage> images = images(entry);

        repository.softDeleteImage(images.get(0).getId());
        assertTrue(repository.softDeleteChatEntry(entry.getId()));
        assertEquals(0.50, repository.findChatById(chat.getId()).orElseThrow().getDeletedCost(), 1e-9);
        assertEquals(0.50, total(chat), 1e-9);

        /*  The image deleted with the entry comes back, the one deleted before stays in the trash  */
        assertTrue(repository.restoreImage(images.get(1).getId()));
        assertEquals(0.0, repository.findChatById(chat.getId()).orElseThrow().getDeletedCost(), 1e-9);
        assertEquals(0.20, repository.getDeletedCostForEntry(entry.getId()), 1e-9);
        assertEquals(0.50, total(chat), 1e-9);

        assertTrue(repository.softDeleteChatEntry(entry.getId()));
        assertTrue(repository.restoreChatEntry(entry.getId()));
        assertEquals(0.0, repository.findChatById(chat.getId()).orElseThrow().getDeletedCost(), 1e-9);
        assertEquals(0.0, repository.getDeletedCostForEntry(entry.getId()), 1e-9);
        assertEquals(0.50, total(chat), 1e-9);
    }

    @Test
    void imagesDeletedWithTheirEntryGetTheirShareWhenOneOfThemComesBack() throws Exception {
        Chat chat = chat("Teilweise");
        ChatEntry entry = entry(chat, "flux-2-flex", 0.12, new double[] {0.02, 0.04, 0.06});
        List<GeneratedImage> images = images(entry);
        repository.softDeleteChatEntry(entry.getId());

        assertTrue(repository.restoreImage(images.get(0).getId()));

        assertEquals(0.04, imageDeletedCost(images.get(1)), 1e-9);
        assertEquals(0.06, imageDeletedCost(images.get(2)), 1e-9);
        assertEquals(0.10, repository.getDeletedCostForEntry(entry.getId()), 1e-9);
        assertEquals(0.12, total(chat), 1e-9);
    }

    private Chat chat(String name) throws Exception {
        Chat chat = new Chat.Builder().name(name).build();
        repository.saveChat(chat);
        return chat;
    }

    private ChatEntry save(ChatEntry entry) throws Exception {
        repository.saveChatEntry(entry);
        return entry;
    }

    /** Stores an entry with an image for each stored cost. */
    private ChatEntry entry(Chat chat, String model, double cost, double[] imageCosts) throws Exception {
        ChatEntry entry = save(new ChatEntry.Builder().chatId(chat.getId()).userPrompt(model).cost(cost).build());
        for (double imageCost : imageCosts)
            image(entry, model, imageCost, 0.0, 0.0);
        return entry;
    }

    private GeneratedImage image(ChatEntry entry, String model, double base, double search, double reasoning)
            throws Exception {
        GeneratedImage image = new GeneratedImage.Builder().chatEntryId(entry.getId()).model(model).baseCost(base)
                .searchCost(search).reasoningCost(reasoning).sortOrder(images(entry).size()).build();
        repository.saveGeneratedImage(image);
        return image;
    }

    private List<GeneratedImage> images(ChatEntry entry) throws Exception {
        return repository.findGeneratedImagesByEntry(entry.getId());
    }

    /** The total of the chat header, as stored. */
    private double total(Chat chat) throws Exception {
        return ChatCost.total(repository.findChatEntriesByChatId(chat.getId()),
                repository.findChatById(chat.getId()).orElseThrow().getDeletedCost());
    }

    private double imageDeletedCost(GeneratedImage image) throws Exception {
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        "SELECT deleted_cost FROM generated_images WHERE id = ?")) {
            stmt.setString(1, image.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                return rs.getDouble("deleted_cost");
            }
        }
    }
}
