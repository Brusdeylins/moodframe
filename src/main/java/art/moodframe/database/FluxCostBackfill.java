package art.moodframe.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Migration V029: the completed FLUX entries saved with the cost 0 (before 2.1.0) get the list price of their images.
 *
 * <p>A migration must give the same result in every later version, so it knows the FLUX models and their prices
 * itself ({@link #MODELS}, frozen at 2.1.3) instead of asking {@code GenerationModel} and {@code GenerationCost},
 * which change with the price list.</p>
 *
 * @since 2.1.0
 */
final class FluxCostBackfill {

    private static final Logger logger = LoggerFactory.getLogger(FluxCostBackfill.class);

    private record Entry(String id, String model, boolean deleted, double deletedCost) {
    }

    private record Image(String id, String model, int width, int height, boolean billed) {
    }

    /**
     * A FLUX model as the price list of 2.1.3 had it: the name up to its tagline, the model id of the API, the price
     * of an image (FLUX 3) or of a megapixel (FLUX.2), and the price of the first megapixel (negative if every
     * megapixel costs the same).
     */
    private record Model(String name, String apiModelId, double price, double firstMegapixel) {

        /*  the list price of an image, as GenerationCost.perImage computed it in 2.1.3  */
        double perImage(int width, int height) {
            if (!apiModelId.startsWith("flux.2-"))
                return price;
            double megapixels = width > 0 && height > 0 ? (width * height) / 1_000_000.0 : 1.0;
            return firstMegapixel < 0 ? price * megapixels
                    : firstMegapixel + Math.max(0.0, megapixels - 1.0) * price;
        }
    }

    /*  The FLUX models of GenerationModel with their prices, frozen at 2.1.3  */
    private static final List<Model> MODELS = List.of(
            new Model("FLUX 3 Image 768", "flux.3-image", 0.041, -1),
            new Model("FLUX 3 Image 1K", "flux.3-image", 0.048, -1),
            new Model("FLUX 3 Image 1.5K", "flux.3-image", 0.07, -1),
            new Model("FLUX 3 Image 2K", "flux.3-image", 0.10, -1),
            new Model("FLUX 3 Image 4K", "flux.3-image", 0.607, -1),
            new Model("FLUX 3 Image Klein 768", "flux.3-image-klein", 0.008, -1),
            new Model("FLUX 3 Image Klein 1K", "flux.3-image-klein", 0.009, -1),
            new Model("FLUX 3 Image Klein 1.5K", "flux.3-image-klein", 0.011, -1),
            new Model("FLUX 3 Image Klein 2K", "flux.3-image-klein", 0.014, -1),
            new Model("FLUX 3 Image Klein 4K", "flux.3-image-klein", 0.036, -1),
            new Model("FLUX.2 [pro]", "flux.2-pro", 0.015, 0.03),
            new Model("FLUX.2 [max]", "flux.2-max", 0.03, 0.07),
            new Model("FLUX.2 [flex]", "flux.2-flex", 0.05, -1),
            new Model("FLUX.2 [klein] 4B", "flux.2-klein-4b", 0.001, 0.014),
            new Model("FLUX.2 [klein] 9B", "flux.2-klein-9b", 0.002, 0.015));

    private FluxCostBackfill() {
    }

    /**
     * Fills the cost of the completed FLUX entries with the cost 0: every image its list price, the entry the sum of
     * its images, deleted or not. The deletions keep their costs, which do not depend on the entry, only an entry
     * deleted as a whole gets its cost as deleted cost. Entries of an unknown model keep the cost 0.
     *
     * @param connection the connection, inside the transaction of the migration
     * @return the number of filled entries
     * @throws SQLException if the database fails
     */
    static int apply(Connection connection) throws SQLException {
        List<Entry> entries = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT id, model, deleted_at, deleted_cost "
                + "FROM chat_entries WHERE status = 'COMPLETED' AND cost = 0");
                ResultSet rs = select.executeQuery()) {
            while (rs.next())
                entries.add(new Entry(rs.getString("id"), rs.getString("model"), rs.getString("deleted_at") != null,
                        rs.getDouble("deleted_cost")));
        }

        int filled = 0;
        double total = 0.0;
        try (PreparedStatement select = connection.prepareStatement("SELECT id, model, width, height, "
                        + "generation_job_id, failure_reason FROM generated_images WHERE chat_entry_id = ?");
                PreparedStatement updateImage = connection.prepareStatement(
                        "UPDATE generated_images SET base_cost = ? WHERE id = ?");
                PreparedStatement updateEntry = connection.prepareStatement(
                        "UPDATE chat_entries SET cost = ?, deleted_cost = ? WHERE id = ?")) {
            for (Entry entry : entries) {
                List<Image> images = new ArrayList<>();
                select.setString(1, entry.id());
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next())
                        images.add(new Image(rs.getString("id"), rs.getString("model"), rs.getInt("width"),
                                rs.getInt("height"), rs.getString("generation_job_id") != null
                                        && rs.getString("failure_reason") == null));
                }
                Model model = fluxModel(entry.model(), images.isEmpty() ? null : images.get(0).model());
                if (model == null)
                    continue;

                /*  BFL bills the finished jobs only, a failed or moderated one keeps the cost 0  */
                double cost = 0.0;
                for (Image image : images) {
                    double price = image.billed() ? model.perImage(image.width(), image.height()) : 0.0;
                    updateImage.setDouble(1, price);
                    updateImage.setString(2, image.id());
                    updateImage.executeUpdate();
                    cost += price;
                }
                if (cost <= 0)
                    continue;

                /*  as softDeleteChatEntry does it: an entry deleted as a whole has its cost as deleted cost  */
                updateEntry.setDouble(1, cost);
                updateEntry.setDouble(2, entry.deleted() && entry.deletedCost() == 0 ? cost : entry.deletedCost());
                updateEntry.setString(3, entry.id());
                updateEntry.executeUpdate();
                filled++;
                total += cost;
            }
        }
        logger.info("Backfilled the cost of {} FLUX entries saved without cost: ${}", filled,
                String.format("%.4f", total));
        return filled;
    }

    /*  the FLUX model of the entry name, whose tagline and price changed over the versions, else of the model id of
        its images if that names a single model (not FLUX 3, whose resolutions share one); null if unknown  */
    private static Model fluxModel(String name, String modelId) {
        String stem = stem(name);
        Model model = single((Model candidate) -> stem != null && stem.equals(candidate.name()));
        return model != null ? model : single((Model candidate) -> candidate.apiModelId().equals(modelId));
    }

    /*  the display name up to its tagline: "FLUX 3 Image 1K - Standard ($0.048)" -> "FLUX 3 Image 1K"  */
    private static String stem(String name) {
        int tagline = name == null ? -1 : name.indexOf(" - ");
        return tagline > 0 ? name.substring(0, tagline) : name;
    }

    /*  the only model that passes the test, null if none or several do  */
    private static Model single(Predicate<Model> test) {
        List<Model> found = MODELS.stream().filter(test).toList();
        return found.size() == 1 ? found.get(0) : null;
    }
}
