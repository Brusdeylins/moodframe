package art.moodframe.database;

import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.repository.ChatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Migration V030 books the deleted costs of the price table up to 2.1.2 again as a deletion books them now, once: the
 * library deleted by the old code ends up as the same library deleted by the code now.
 */
class DeletedCostCorrectionTest {

    private static final double DELTA = 1e-9;

    /** What the entries removed for good before booked to the chat. */
    private static final double EARLIER = 0.5;

    @TempDir
    Path tempDir;

    /** The deletions of a library, by the old code or the code now. */
    private interface Deletion {
        void image(String imageId) throws SQLException;

        void entry(String entryId) throws SQLException;
    }

    @Test
    void booksTheDeletionsOfTheOldCodeAsTheCodeNowOnce() throws Exception {
        Path file = tempDir.resolve("old").resolve("test.db");
        DatabaseManager oldDatabase = new DatabaseManager(file);
        oldDatabase.initialize();
        ChatRepository oldChats = new ChatRepository(oldDatabase);
        library(oldDatabase, oldChats);
        DatabaseManager newDatabase = new DatabaseManager(tempDir.resolve("new").resolve("test.db"));
        newDatabase.initialize();
        ChatRepository newChats = new ChatRepository(newDatabase);
        library(newDatabase, newChats);

        /*  the same deletions, up to 2.1.2 and now  */
        try (Connection connection = oldDatabase.getConnection()) {
            deleteAll(new OldDeletion(connection));
        }
        oldChats.deleteImagePermanently("removed-a");
        deleteAll(new Deletion() {
            @Override
            public void image(String imageId) throws SQLException {
                assertTrue(newChats.softDeleteImage(imageId));
            }

            @Override
            public void entry(String entryId) throws SQLException {
                assertTrue(newChats.softDeleteChatEntry(entryId));
            }
        });
        newChats.deleteImagePermanently("removed-a");

        try (Connection connection = oldDatabase.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM schema_migrations WHERE version = '030'");
        }
        Map<String, Double> before = costs(oldDatabase);
        new DatabaseManager(file).initialize();
        Map<String, Double> after = costs(oldDatabase);
        Map<String, Double> expected = costs(newDatabase);

        /*  the old code booked the table price (0.025, flux.1-pro 0.04) and too little to the chats  */
        assertEquals(0.025, before.get("image flux2-b deleted_cost"), DELTA);
        assertEquals(EARLIER + 2 * 0.025, before.get("chat together deleted_cost"), DELTA);
        assertEquals(0.0, before.get("chat last deleted_cost"), DELTA);
        assertEquals(0.08, before.get("chat whole deleted_cost"), DELTA);

        /*  now as the code now books the same deletions  */
        assertEquals(expected.keySet(), after.keySet());
        for (String key : expected.keySet()) {
            if (!key.startsWith("image removed-") && !key.startsWith("entry removed"))
                assertEquals(expected.get(key), after.get(key), DELTA, key);
        }
        assertEquals(0.045, after.get("image flux2-b deleted_cost"), DELTA, "FLUX.2: what it was billed");
        assertEquals(0.018, after.get("image gemini-b deleted_cost"), DELTA, "Gemini: generation, search, reasoning");
        assertEquals(0.1 / 3, after.get("image even-a deleted_cost"), DELTA, "without stored costs an even share");
        assertEquals(EARLIER + 0.12, after.get("chat together deleted_cost"), DELTA, "the chat books the whole cost");
        assertEquals(0.08, after.get("chat last deleted_cost"), DELTA, "also of an entry deleted by its last image");
        assertEquals(0.07, after.get("chat whole deleted_cost"), DELTA, "the cost, not the table prices");

        /*  an image removed for good keeps what it booked, the rest of the entry cost is shared anew  */
        assertEquals(before.get("entry removed deleted_cost") - before.get("image removed-b deleted_cost"),
                after.get("entry removed deleted_cost") - after.get("image removed-b deleted_cost"), DELTA);
        assertEquals((0.09 - 0.025) / 2, after.get("image removed-b deleted_cost"), DELTA);

        /*  what is not deleted stays  */
        for (String key : before.keySet()) {
            if (key.contains("untouched") || key.endsWith(" cost") || key.endsWith(" base_cost"))
                assertEquals(before.get(key), after.get(key), DELTA, "unchanged: " + key);
        }

        /*  recorded once, the next start changes nothing  */
        new DatabaseManager(file).initialize();
        assertEquals(after, costs(oldDatabase), "the second start changes nothing");
        try (Connection connection = oldDatabase.getConnection(); Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_migrations WHERE version = '030'")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }

        /*  a restore takes back what the entry booked, not what other entries booked  */
        assertTrue(oldChats.restoreChatEntry("together"));
        assertEquals(EARLIER, oldChats.findChatById("together").orElseThrow().getDeletedCost(), DELTA);
    }

    @Test
    void changesNothingWithoutDeletions() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("db").resolve("test.db"));
        database.initialize();
        library(database, new ChatRepository(database));
        Map<String, Double> before = costs(database);

        try (Connection connection = database.getConnection()) {
            assertEquals(0, DeletedCostCorrection.apply(connection));
        }
        assertEquals(before, costs(database));
    }

    /*  the deletions of each case  */
    private static void deleteAll(Deletion deletion) throws SQLException {
        deletion.image("flux2-b");
        deletion.image("gemini-b");
        deletion.image("together-a");
        deletion.entry("together");
        deletion.image("last-a");
        deletion.image("last-b");
        deletion.entry("whole");
        deletion.image("even-a");
        deletion.image("removed-a");
        deletion.image("removed-b");
    }

    /*  one chat per case, with fixed ids, so both libraries match  */
    private static void library(DatabaseManager database, ChatRepository chats) throws Exception {
        entry(chats, "flux2", "flux.2-pro", 0.135, 0.03, 0.045, 0.06);
        entry(chats, "untouched", "flux.2-pro", 0.2, 0.2);
        saveEntry(chats, "gemini", "gemini", 0.152);
        image(chats, "gemini", "gemini-a", "gemini-3-pro-image-preview", 0.134, 0.0, 0.0);
        image(chats, "gemini", "gemini-b", "gemini-3-pro-image-preview", 0.0, 0.014, 0.004);
        entry(chats, "together", "flux.2-flex", 0.12, 0.02, 0.04, 0.06);
        entry(chats, "last", "gpt-image-1", 0.08, 0.04, 0.04);
        entry(chats, "whole", "flux.1-pro", 0.07, 0.035, 0.035);
        entry(chats, "even", "flux-dev", 0.1, 0.0, 0.0, 0.0);
        entry(chats, "removed", "flux.2-pro", 0.09, 0.03, 0.03, 0.03);
        try (Connection connection = database.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("UPDATE chats SET deleted_cost = " + EARLIER + " WHERE id = 'together'");
        }
    }

    /*  a chat named by its case with an entry of the same id, and an image "<id>-a", "-b", ... per stored cost  */
    private static void entry(ChatRepository chats, String id, String model, double cost, double... imageCosts)
            throws Exception {
        saveEntry(chats, id, model, cost);
        for (int i = 0; i < imageCosts.length; i++)
            image(chats, id, id + "-" + (char) ('a' + i), model, imageCosts[i], 0.0, 0.0);
    }

    private static void saveEntry(ChatRepository chats, String id, String model, double cost) throws Exception {
        chats.saveChat(new Chat.Builder().id(id).name(id).build());
        chats.saveChatEntry(new ChatEntry.Builder().id(id).chatId(id).userPrompt(id).model(model).cost(cost).build());
    }

    private static void image(ChatRepository chats, String entryId, String id, String model, double base,
            double search, double reasoning) throws Exception {
        chats.saveGeneratedImage(new GeneratedImage.Builder().id(id).chatEntryId(entryId).model(model).baseCost(base)
                .searchCost(search).reasoningCost(reasoning).build());
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

    /** The deletions as ChatRepository did them up to 2.1.2 (see commit 51181fe). */
    private record OldDeletion(Connection connection) implements Deletion {

        @Override
        public void image(String imageId) throws SQLException {
            String entryId;
            double price;
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT chat_entry_id, model FROM generated_images WHERE id = ?")) {
                select.setString(1, imageId);
                try (ResultSet rs = select.executeQuery()) {
                    assertTrue(rs.next());
                    entryId = rs.getString("chat_entry_id");
                    price = tablePrice(rs.getString("model"));
                }
            }
            update("UPDATE generated_images SET deleted_at = CURRENT_TIMESTAMP, deleted_cost = ?, is_in_trash = TRUE "
                    + "WHERE id = ?", price, imageId);
            update("UPDATE chat_entries SET deleted_cost = deleted_cost + ? WHERE id = ?", price, entryId);
            update("UPDATE chat_entries SET deleted_at = CURRENT_TIMESTAMP WHERE id = ? AND NOT EXISTS "
                    + "(SELECT 1 FROM generated_images WHERE chat_entry_id = ? AND deleted_at IS NULL)", entryId,
                    entryId);
        }

        @Override
        public void entry(String entryId) throws SQLException {
            double prices = 0.0;
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT model FROM generated_images WHERE chat_entry_id = ? AND deleted_at IS NULL")) {
                select.setString(1, entryId);
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next())
                        prices += tablePrice(rs.getString("model"));
                }
            }
            update("UPDATE generated_images SET deleted_at = CURRENT_TIMESTAMP, is_in_trash = TRUE "
                    + "WHERE chat_entry_id = ? AND deleted_at IS NULL", entryId);
            update("UPDATE chat_entries SET deleted_at = CURRENT_TIMESTAMP, deleted_cost = cost WHERE id = ?",
                    entryId);
            update("UPDATE chats SET deleted_cost = deleted_cost + ? "
                    + "WHERE id = (SELECT chat_id FROM chat_entries WHERE id = ?)", prices, entryId);
        }

        private void update(String sql, Object... parameters) throws SQLException {
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.length; i++)
                    stmt.setObject(i + 1, parameters[i]);
                stmt.executeUpdate();
            }
        }

        /*  the removed ChatRepository.calculateImageCost  */
        private static double tablePrice(String model) {
            if (model == null)
                return 0.0;

            return switch (model.toLowerCase()) {
                case "flux.1-dev", "flux-dev" -> 0.025;
                case "flux.1-pro", "flux-pro" -> 0.04;
                case "flux.1.1-pro" -> 0.04;
                case "flux.1.1-pro-ultra" -> 0.06;
                case "flux.1-kontext-pro" -> 0.04;
                case "flux.1-kontext-max" -> 0.08;
                default -> 0.025;
            };
        }
    }
}
