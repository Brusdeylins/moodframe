package art.moodframe.database;

import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationCost;
import art.moodframe.model.GenerationModel;
import art.moodframe.repository.ChatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Migration V029 fills the completed FLUX entries saved with the cost 0 with the list price, once. */
class FluxCostBackfillTest {

    private static final double DELTA = 1e-9;

    @TempDir
    Path tempDir;

    private ChatRepository chats;
    private Chat chat;

    @Test
    void fillsTheFluxEntriesWithoutCostWithTheListPriceOnce() throws Exception {
        Path file = tempDir.resolve("db").resolve("test.db");
        DatabaseManager database = new DatabaseManager(file);
        database.initialize();
        chats = new ChatRepository(database);
        chat = new Chat.Builder().name("Lighthouse series").build();
        chats.saveChat(chat);

        /*  the library as before 2.1.0: completed FLUX entries with the cost 0, images without base cost  */
        String flux3 = entry(GenerationModel.FLUX3_IMAGE_1K.getDisplayName(), ChatEntry.Status.COMPLETED, 0.0);
        List<String> flux3Images = List.of(image(flux3, "flux.3-image", 1024, 1024, 0.0),
                image(flux3, "flux.3-image", 1024, 1024, 0.0));
        String klein = entry(GenerationModel.FLUX3_KLEIN_1K.getDisplayName(), ChatEntry.Status.COMPLETED, 0.0);
        image(klein, "flux.3-image-klein", 1024, 1024, 0.0);
        String pro = entry(GenerationModel.FLUX2_PRO.getDisplayName(), ChatEntry.Status.COMPLETED, 0.0);
        String proImage = image(pro, "flux.2-pro", 1440, 1440, 0.0);
        String moderated = image(pro, "flux.2-pro", 1440, 1440, 0.0);
        String proOlder = entry("FLUX.2 [pro] - Production Grade ($0.03/MP)", ChatEntry.Status.COMPLETED, 0.0);
        image(proOlder, "flux.2-pro", 1024, 1024, 0.0);
        String deletedImage = entry(GenerationModel.FLUX3_KLEIN_1K.getDisplayName(), ChatEntry.Status.COMPLETED, 0.0);
        image(deletedImage, "flux.3-image-klein", 1024, 1024, 0.0);
        String inTrash = image(deletedImage, "flux.3-image-klein", 1024, 1024, 0.0);
        String deletedEntry = entry(GenerationModel.FLUX2_PRO.getDisplayName(), ChatEntry.Status.COMPLETED, 0.0);
        image(deletedEntry, "flux.2-pro", 1024, 1024, 0.0);

        /*  what stays: a paid FLUX entry, Gemini, a failed entry, an unknown FLUX 3 resolution, FLUX.1  */
        String paid = entry(GenerationModel.FLUX3_IMAGE_1K.getDisplayName(), ChatEntry.Status.COMPLETED, 0.5);
        image(paid, "flux.3-image", 1024, 1024, 0.5);
        String gemini = entry(GenerationModel.GEMINI_PRO_2K.getDisplayName(), ChatEntry.Status.COMPLETED, 0.0);
        image(gemini, GenerationModel.GEMINI_PRO_2K.getApiModelId(), 2048, 2048, 0.0);
        String failed = entry(GenerationModel.FLUX3_IMAGE_1K.getDisplayName(), ChatEntry.Status.FAILED, 0.0);
        image(failed, "flux.3-image", 1024, 1024, 0.0);
        String unknownResolution = entry("FLUX 3 Image", ChatEntry.Status.COMPLETED, 0.0);
        image(unknownResolution, "flux.3-image", 1024, 1024, 0.0);
        String fluxOne = entry("FLUX.1 [dev]", ChatEntry.Status.COMPLETED, 0.0);
        image(fluxOne, "flux-dev", 1024, 1024, 0.0);

        try (Connection connection = database.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("UPDATE generated_images SET failure_reason = 'Content Moderated' WHERE id = '"
                    + moderated + "'");
            stmt.executeUpdate("DELETE FROM schema_migrations WHERE version = '029'");
        }
        chats.softDeleteImage(inTrash);
        chats.softDeleteChatEntry(deletedEntry);
        Map<String, Double> before = costs(database);

        new DatabaseManager(file).initialize();
        Map<String, Double> after = costs(database);

        double flux3Price = GenerationModel.FLUX3_IMAGE_1K.getPricePerImage();
        for (String image : flux3Images)
            assertEquals(flux3Price, after.get("image " + image + " base_cost"), DELTA, "FLUX 3: the price of the model");
        assertEquals(2 * flux3Price, after.get("entry " + flux3 + " cost"), DELTA, "the entry costs its images");
        assertEquals(GenerationModel.FLUX3_KLEIN_1K.getPricePerImage(), after.get("entry " + klein + " cost"), DELTA);

        double proPrice = GenerationCost.perImage(GenerationModel.FLUX2_PRO, 1440, 1440, null);
        assertEquals(0.03 + (1440 * 1440 / 1e6 - 1) * 0.015, proPrice, DELTA, "FLUX.2: by the megapixels");
        assertEquals(proPrice, after.get("image " + proImage + " base_cost"), DELTA);
        assertEquals(0.0, after.get("image " + moderated + " base_cost"), DELTA, "a moderated job is not billed");
        assertEquals(proPrice, after.get("entry " + pro + " cost"), DELTA);
        assertEquals(GenerationCost.perImage(GenerationModel.FLUX2_PRO, 1024, 1024, null),
                after.get("entry " + proOlder + " cost"), DELTA, "an older tagline names the model, too");

        double kleinPrice = GenerationModel.FLUX3_KLEIN_1K.getPricePerImage();
        assertEquals(2 * kleinPrice, after.get("entry " + deletedImage + " cost"), DELTA, "a deleted image counts");
        assertEquals(kleinPrice, after.get("image " + inTrash + " base_cost"), DELTA);
        assertEquals(before.get("entry " + deletedImage + " deleted_cost"),
                after.get("entry " + deletedImage + " deleted_cost"), DELTA, "the deletion keeps its cost");
        assertEquals(before.get("image " + inTrash + " deleted_cost"), after.get("image " + inTrash + " deleted_cost"),
                DELTA);
        double deletedEntryPrice = GenerationCost.perImage(GenerationModel.FLUX2_PRO, 1024, 1024, null);
        assertEquals(deletedEntryPrice, after.get("entry " + deletedEntry + " cost"), DELTA);
        assertEquals(deletedEntryPrice, after.get("entry " + deletedEntry + " deleted_cost"), DELTA,
                "an entry deleted as a whole has its cost as deleted cost");
        assertEquals(before.get("chat " + chat.getId() + " deleted_cost"),
                after.get("chat " + chat.getId() + " deleted_cost"), DELTA);

        for (String unchanged : List.of(paid, gemini, failed, unknownResolution, fluxOne))
            assertEquals(subset(before, unchanged), subset(after, unchanged), "unchanged: " + unchanged);

        /*  the migration is recorded, and a second run would find nothing to fill  */
        new DatabaseManager(file).initialize();
        assertEquals(after, costs(database), "the second start changes nothing");
        try (Connection connection = database.getConnection()) {
            assertEquals(0, FluxCostBackfill.apply(connection));
        }
        assertEquals(after, costs(database), "the second run changes nothing");
    }

    private String entry(String model, ChatEntry.Status status, double cost) throws Exception {
        ChatEntry entry = new ChatEntry.Builder().chatId(chat.getId()).userPrompt("a lighthouse").model(model)
                .status(status).cost(cost).build();
        chats.saveChatEntry(entry);
        return entry.getId();
    }

    private String image(String entryId, String model, int width, int height, double baseCost) throws Exception {
        GeneratedImage image = new GeneratedImage.Builder().chatEntryId(entryId).generationJobId("job-" + entryId)
                .model(model).width(width).height(height).baseCost(baseCost)
                .downloadStatus(GeneratedImage.DownloadStatus.CACHED).build();
        chats.saveGeneratedImage(image);
        return image.getId();
    }

    /*  all costs of the library by "<table> <id> <column>"  */
    private static Map<String, Double> costs(DatabaseManager database) throws Exception {
        Map<String, Double> costs = new TreeMap<>();
        try (Connection connection = database.getConnection(); PreparedStatement select = connection.prepareStatement(
                "SELECT 'entry ' || id, cost, deleted_cost FROM chat_entries "
                + "UNION ALL SELECT 'image ' || id, base_cost, deleted_cost FROM generated_images "
                + "UNION ALL SELECT 'chat ' || id, 0, deleted_cost FROM chats");
                ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                String key = rs.getString(1);
                if (!key.startsWith("chat "))
                    costs.put(key + (key.startsWith("entry ") ? " cost" : " base_cost"), rs.getDouble(2));
                costs.put(key + " deleted_cost", rs.getDouble(3));
            }
        }
        return costs;
    }

    /*  the costs of an entry and of its images  */
    private Map<String, Double> subset(Map<String, Double> costs, String entryId) throws Exception {
        List<String> ids = new ArrayList<>(List.of("entry " + entryId));
        for (GeneratedImage image : chats.findGeneratedImagesByEntry(entryId))
            ids.add("image " + image.getId());
        Map<String, Double> subset = new TreeMap<>();
        costs.forEach((key, value) -> {
            if (ids.stream().anyMatch((id) -> key.startsWith(id + " ")))
                subset.put(key, value);
        });
        return subset;
    }
}
