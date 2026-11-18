package art.moodframe.database;

import art.moodframe.model.ChatCost;
import art.moodframe.model.GeneratedImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Migration V030: the deleted costs booked up to 2.1.2 by a fixed FLUX.1 price table are booked again as a deletion
 * books them since 2.1.3 (see {@link ChatCost}).
 *
 * <p>Up to 2.1.2 an image deleted by itself cost the table price of its model, 0.025 for every model the table did
 * not know, and its entry the sum; an entry deleted as a whole booked to its chat the table prices of the images
 * deleted with it (which keep the deleted cost 0), an entry deleted with its last image booked nothing. Since 2.1.3 an
 * image costs its share of the entry cost, and the chat books the whole cost of a deleted entry, which a restore takes
 * back.</p>
 *
 * @since 2.1.3
 */
final class DeletedCostCorrection {

    private static final Logger logger = LoggerFactory.getLogger(DeletedCostCorrection.class);

    /** Below this a cost counts as unchanged. */
    private static final double UNCHANGED = 1e-9;

    private record Entry(String id, String chatId, double cost, boolean deleted, double deletedCost) {
    }

    private record Image(GeneratedImage image, boolean deleted, double deletedCost) {
    }

    private DeletedCostCorrection() {
    }

    /**
     * Books the deleted costs of the entries still in the database again: an image deleted by itself gets its share
     * of the entry cost, an entry in use the sum of them, a deleted entry its cost; the chat of a deleted entry gets
     * the difference between its cost and the table prices booked for it. What images removed for good had booked
     * stays with their entry in use (or deleted with its last image); entries removed for good cannot be booked again.
     *
     * @param connection the connection, inside the transaction of the migration
     * @return the number of changed images, entries and chats
     * @throws SQLException if the database fails
     */
    static int apply(Connection connection) throws SQLException {
        List<Entry> entries = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT id, chat_id, cost, deleted_at, "
                + "deleted_cost FROM chat_entries e WHERE deleted_at IS NOT NULL OR EXISTS (SELECT 1 FROM "
                + "generated_images WHERE chat_entry_id = e.id AND deleted_at IS NOT NULL)");
                ResultSet rs = select.executeQuery()) {
            while (rs.next())
                entries.add(new Entry(rs.getString("id"), rs.getString("chat_id"), rs.getDouble("cost"),
                        rs.getString("deleted_at") != null, rs.getDouble("deleted_cost")));
        }

        int images = 0;
        int changedEntries = 0;
        Map<String, Double> chatDifferences = new TreeMap<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT id, model, base_cost, search_cost, "
                        + "reasoning_cost, deleted_at, deleted_cost FROM generated_images WHERE chat_entry_id = ?");
                PreparedStatement updateImage = connection.prepareStatement(
                        "UPDATE generated_images SET deleted_cost = ? WHERE id = ?");
                PreparedStatement updateEntry = connection.prepareStatement(
                        "UPDATE chat_entries SET deleted_cost = ? WHERE id = ?")) {
            for (Entry entry : entries) {
                List<Image> all = images(select, entry.id());

                /*  of a deleted entry, the images deleted with it are those without a cost of their own  */
                List<Image> together = all.stream().filter((Image image) -> entry.deleted() && image.deleted()
                        && image.deletedCost() == 0).toList();
                List<Image> alone = all.stream().filter((Image image) -> image.deleted()
                        && (!entry.deleted() || image.deletedCost() != 0)).toList();

                /*  what images removed for good booked; unknown for an entry deleted as a whole  */
                double booked = alone.stream().mapToDouble(Image::deletedCost).sum();
                double removed = !entry.deleted() || together.isEmpty()
                        ? Math.min(Math.max(0.0, entry.deletedCost() - booked), entry.cost()) : 0.0;

                /*  the share of each image does not depend on the order of the deletions  */
                List<GeneratedImage> costs = all.stream().map(Image::image).toList();
                double shares = 0.0;
                for (Image image : alone) {
                    double share = ChatCost.imageShare(entry.cost() - removed, costs, image.image());
                    shares += share;
                    if (Math.abs(share - image.deletedCost()) > UNCHANGED) {
                        updateImage.setDouble(1, share);
                        updateImage.setString(2, image.image().getId());
                        updateImage.executeUpdate();
                        images++;
                    }
                }

                double deletedCost = entry.deleted() ? entry.cost() : removed + shares;
                if (Math.abs(deletedCost - entry.deletedCost()) > UNCHANGED) {
                    updateEntry.setDouble(1, deletedCost);
                    updateEntry.setString(2, entry.id());
                    updateEntry.executeUpdate();
                    changedEntries++;
                }

                if (entry.deleted()) {
                    double tablePrices = together.stream().mapToDouble((Image image) -> tablePrice(image.image()))
                            .sum();
                    chatDifferences.merge(entry.chatId(), entry.cost() - tablePrices, Double::sum);
                }
            }
        }

        int chats = 0;
        double total = 0.0;
        try (PreparedStatement select = connection.prepareStatement("SELECT deleted_cost FROM chats WHERE id = ?");
                PreparedStatement update = connection.prepareStatement(
                        "UPDATE chats SET deleted_cost = ? WHERE id = ?")) {
            for (Map.Entry<String, Double> difference : chatDifferences.entrySet()) {
                select.setString(1, difference.getKey());
                double before;
                try (ResultSet rs = select.executeQuery()) {
                    if (!rs.next())
                        continue;
                    before = rs.getDouble("deleted_cost");
                }
                double after = Math.max(0.0, before + difference.getValue());
                if (Math.abs(after - before) <= UNCHANGED)
                    continue;

                update.setDouble(1, after);
                update.setString(2, difference.getKey());
                update.executeUpdate();
                chats++;
                total += after - before;
            }
        }
        logger.info("Booked the deleted costs again: {} images, {} entries, {} chats changed by ${}", images,
                changedEntries, chats, String.format("%.4f", total));
        return images + changedEntries + chats;
    }

    /*  the images of an entry with their stored costs  */
    private static List<Image> images(PreparedStatement select, String entryId) throws SQLException {
        List<Image> images = new ArrayList<>();
        select.setString(1, entryId);
        try (ResultSet rs = select.executeQuery()) {
            while (rs.next())
                images.add(new Image(new GeneratedImage.Builder().id(rs.getString("id")).chatEntryId(entryId)
                        .model(rs.getString("model")).baseCost(rs.getDouble("base_cost"))
                        .searchCost(rs.getDouble("search_cost")).reasoningCost(rs.getDouble("reasoning_cost"))
                        .build(), rs.getString("deleted_at") != null, rs.getDouble("deleted_cost")));
        }
        return images;
    }

    /*  the price up to 2.1.2 booked for a deleted image, by the FLUX.1 table of ChatRepository.calculateImageCost  */
    private static double tablePrice(GeneratedImage image) {
        String model = image.getModel();
        if (model == null)
            return 0.0;

        return switch (model.toLowerCase()) {
            case "flux.1-pro", "flux-pro", "flux.1.1-pro", "flux.1-kontext-pro" -> 0.04;
            case "flux.1.1-pro-ultra" -> 0.06;
            case "flux.1-kontext-max" -> 0.08;
            default -> 0.025;
        };
    }
}
