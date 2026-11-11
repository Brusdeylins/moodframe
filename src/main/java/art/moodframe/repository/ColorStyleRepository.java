package art.moodframe.repository;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.database.DatabaseConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.model.ColorStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repository for managing ColorStyle persistence in SQLite.
 *
 * <p>This repository implements the Repository pattern for ColorStyle entities,
 * providing CRUD operations with proper transaction management and error handling.
 * It serves as the data access layer between the service layer and SQLite database.</p>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 * <li>Full CRUD operations for color styles</li>
 * <li>Transaction management for data consistency</li>
 * <li>Prepared statements to prevent SQL injection</li>
 * <li>Support for 1-5 hex color codes per style</li>
 * </ul>
 *
 * @since 1.3.0
 */
public class ColorStyleRepository {

    private static final Logger logger = LoggerFactory.getLogger(ColorStyleRepository.class);
    private final ApplicationConfig config;

    public ColorStyleRepository() {
        this.config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
    }

    /**
     * Saves a new color style to the database.
     *
     * @param colorStyle the color style to save
     * @return true if save was successful, false otherwise
     */
    public boolean save(ColorStyle colorStyle) {
        String sql = """
            INSERT INTO color_styles (id, name, color1, color2, color3, color4, color5, is_default, sort_order)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String id = colorStyle.getId();
            if (id == null || id.isEmpty()) {
                id = UUID.randomUUID().toString();
                colorStyle.setId(id);
            }

            stmt.setString(1, id);
            stmt.setString(2, colorStyle.getName());
            stmt.setString(3, colorStyle.getColor(0));
            stmt.setString(4, colorStyle.getColor(1));
            stmt.setString(5, colorStyle.getColor(2));
            stmt.setString(6, colorStyle.getColor(3));
            stmt.setString(7, colorStyle.getColor(4));
            stmt.setBoolean(8, colorStyle.isDefault());
            stmt.setInt(9, colorStyle.getSortOrder());

            int rowsAffected = stmt.executeUpdate();
            boolean success = rowsAffected > 0;

            if (success && config != null && config.getLoggingEnabled()) {
                logger.debug("Saved color style: {}", colorStyle.getName());
            }

            return success;

        } catch (SQLException e) {
            logger.error("Failed to save color style: " + colorStyle.getName(), e);
            return false;
        }
    }

    /**
     * Updates an existing color style in the database.
     *
     * @param colorStyle the color style to update
     * @return true if update was successful, false otherwise
     */
    public boolean update(ColorStyle colorStyle) {
        String sql = """
            UPDATE color_styles
            SET name = ?, color1 = ?, color2 = ?, color3 = ?, color4 = ?, color5 = ?,
                is_default = ?, sort_order = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, colorStyle.getName());
            stmt.setString(2, colorStyle.getColor(0));
            stmt.setString(3, colorStyle.getColor(1));
            stmt.setString(4, colorStyle.getColor(2));
            stmt.setString(5, colorStyle.getColor(3));
            stmt.setString(6, colorStyle.getColor(4));
            stmt.setBoolean(7, colorStyle.isDefault());
            stmt.setInt(8, colorStyle.getSortOrder());
            stmt.setString(9, colorStyle.getId());

            int rowsAffected = stmt.executeUpdate();
            boolean success = rowsAffected > 0;

            if (success && config != null && config.getLoggingEnabled()) {
                logger.debug("Updated color style: {}", colorStyle.getName());
            }

            return success;

        } catch (SQLException e) {
            logger.error("Failed to update color style: " + colorStyle.getName(), e);
            return false;
        }
    }

    /**
     * Deletes a color style from the database.
     *
     * @param id the ID of the color style to delete
     * @return true if deletion was successful, false otherwise
     */
    public boolean delete(String id) {
        String sql = "DELETE FROM color_styles WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);

            int rowsAffected = stmt.executeUpdate();
            boolean success = rowsAffected > 0;

            if (success && config != null && config.getLoggingEnabled()) {
                logger.debug("Deleted color style with ID: {}", id);
            }

            return success;

        } catch (SQLException e) {
            logger.error("Failed to delete color style with ID: " + id, e);
            return false;
        }
    }

    /**
     * Finds a color style by its ID.
     *
     * @param id the ID of the color style
     * @return the color style if found, null otherwise
     */
    public ColorStyle findById(String id) {
        String sql = "SELECT * FROM color_styles WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToColorStyle(rs);
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to find color style by ID: " + id, e);
        }

        return null;
    }

    /**
     * Finds a color style by its name.
     *
     * @param name the name of the color style
     * @return the color style if found, null otherwise
     */
    public ColorStyle findByName(String name) {
        String sql = "SELECT * FROM color_styles WHERE name = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToColorStyle(rs);
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to find color style by name: " + name, e);
        }

        return null;
    }

    /**
     * Retrieves all color styles from the database.
     *
     * @return list of all color styles, ordered by sort_order and name
     */
    public List<ColorStyle> findAll() {
        String sql = "SELECT * FROM color_styles ORDER BY sort_order, name";
        List<ColorStyle> styles = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                styles.add(mapResultSetToColorStyle(rs));
            }

        } catch (SQLException e) {
            logger.error("Failed to retrieve all color styles", e);
        }

        return styles;
    }

    /**
     * Retrieves all custom (non-default) color styles.
     *
     * @return list of custom color styles
     */
    public List<ColorStyle> findAllCustom() {
        String sql = "SELECT * FROM color_styles WHERE is_default = false ORDER BY sort_order, name";
        List<ColorStyle> styles = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                styles.add(mapResultSetToColorStyle(rs));
            }

        } catch (SQLException e) {
            logger.error("Failed to retrieve custom color styles", e);
        }

        return styles;
    }

    /**
     * Checks if a color style with the given name exists.
     *
     * @param name the name to check
     * @return true if a color style with this name exists
     */
    public boolean existsByName(String name) {
        String sql = "SELECT COUNT(*) FROM color_styles WHERE name = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to check if color style exists: " + name, e);
        }

        return false;
    }

    /**
     * Checks if a custom color style with the given name exists.
     *
     * @param name the name to check
     * @return true if a custom color style with this name exists
     */
    public boolean existsCustomByName(String name) {
        String sql = "SELECT COUNT(*) FROM color_styles WHERE name = ? AND is_default = false";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to check if custom color style exists: " + name, e);
        }

        return false;
    }

    /**
     * Deletes all custom (non-default) color styles.
     *
     * @return number of deleted styles
     */
    public int deleteAllCustom() {
        String sql = "DELETE FROM color_styles WHERE is_default = false";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int rowsAffected = stmt.executeUpdate();

            if (config != null && config.getLoggingEnabled()) {
                logger.info("Deleted {} custom color styles", rowsAffected);
            }
            return rowsAffected;

        } catch (SQLException e) {
            logger.error("Failed to delete all custom color styles", e);
            return 0;
        }
    }

    /**
     * Maps a ResultSet row to a ColorStyle object.
     *
     * @param rs the ResultSet positioned at a valid row
     * @return the mapped ColorStyle
     * @throws SQLException if database access error occurs
     */
    private ColorStyle mapResultSetToColorStyle(ResultSet rs) throws SQLException {
        List<String> colors = new ArrayList<>();

        // Read colors 1-5
        for (int i = 1; i <= 5; i++) {
            String color = rs.getString("color" + i);
            if (color != null && !color.isEmpty()) {
                colors.add(color);
            }
        }

        ColorStyle style = new ColorStyle(
            rs.getString("name"),
            colors,
            rs.getBoolean("is_default")
        );

        style.setId(rs.getString("id"));
        style.setSortOrder(rs.getInt("sort_order"));

        return style;
    }
}
