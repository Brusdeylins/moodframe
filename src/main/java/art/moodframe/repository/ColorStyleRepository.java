package art.moodframe.repository;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.ColorStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 * <p>The finders skip color styles in the trash ({@code deleted_at} set); the name checks do not,
 * as such a name stays taken (unique column) until the style is deleted for good. A finder of one style answers
 * with an {@link Optional}.</p>
 *
 * <p>A database error is thrown as {@link SQLException}, the caller decides what follows.</p>
 *
 * @since 1.3.0
 */
public class ColorStyleRepository {

    private static final Logger logger = LoggerFactory.getLogger(ColorStyleRepository.class);
    private final DatabaseManager database;

    /**
     * Creates the repository.
     *
     * @param database the database of the color styles
     */
    public ColorStyleRepository(DatabaseManager database) {
        this.database = database;
    }

    /**
     * Saves a new color style to the database.
     *
     * @param colorStyle the color style to save
     * @return true if save was successful, false otherwise
     * @throws SQLException if the database fails
     */
    public boolean save(ColorStyle colorStyle) throws SQLException {
        String sql = """
            INSERT INTO color_styles (id, name, color1, color2, color3, color4, color5, is_default, sort_order)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = database.getConnection();
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

            if (success) {
                logger.debug("Saved color style: {}", colorStyle.getName());
            }

            return success;
        }
    }

    /**
     * Updates an existing color style in the database.
     *
     * @param colorStyle the color style to update
     * @return true if update was successful, false otherwise
     * @throws SQLException if the database fails
     */
    public boolean update(ColorStyle colorStyle) throws SQLException {
        String sql = """
            UPDATE color_styles
            SET name = ?, color1 = ?, color2 = ?, color3 = ?, color4 = ?, color5 = ?,
                is_default = ?, sort_order = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

        try (Connection conn = database.getConnection();
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

            if (success) {
                logger.debug("Updated color style: {}", colorStyle.getName());
            }

            return success;
        }
    }

    /**
     * Deletes a color style from the database.
     *
     * @param id the ID of the color style to delete
     * @return true if deletion was successful, false otherwise
     * @throws SQLException if the database fails
     */
    public boolean delete(String id) throws SQLException {
        String sql = "DELETE FROM color_styles WHERE id = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);

            int rowsAffected = stmt.executeUpdate();
            boolean success = rowsAffected > 0;

            if (success) {
                logger.debug("Deleted color style with ID: {}", id);
            }

            return success;
        }
    }

    /**
     * Finds a color style by its ID.
     *
     * @param id the ID of the color style
     * @return the color style, empty if there is none in use
     * @throws SQLException if the database fails
     */
    public Optional<ColorStyle> findById(String id) throws SQLException {
        String sql = "SELECT * FROM color_styles WHERE id = ? AND deleted_at IS NULL";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToColorStyle(rs));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Finds a color style in the trash by its ID.
     *
     * @param id the ID of the color style
     * @return the color style, empty if it is not in the trash
     * @throws SQLException if the database fails
     */
    public Optional<ColorStyle> findInTrash(String id) throws SQLException {
        String sql = "SELECT * FROM color_styles WHERE id = ? AND deleted_at IS NOT NULL";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next())
                    return Optional.of(mapResultSetToColorStyle(rs));
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a color style by its name.
     *
     * @param name the name of the color style
     * @return the color style, empty if there is none in use
     * @throws SQLException if the database fails
     */
    public Optional<ColorStyle> findByName(String name) throws SQLException {
        String sql = "SELECT * FROM color_styles WHERE name = ? AND deleted_at IS NULL";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToColorStyle(rs));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Retrieves all color styles from the database.
     *
     * @return list of all color styles, ordered by sort_order and name
     * @throws SQLException if the database fails
     */
    public List<ColorStyle> findAll() throws SQLException {
        String sql = "SELECT * FROM color_styles WHERE deleted_at IS NULL ORDER BY sort_order, name";
        List<ColorStyle> styles = new ArrayList<>();

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                styles.add(mapResultSetToColorStyle(rs));
            }
        }

        return styles;
    }

    /**
     * Retrieves all custom (non-default) color styles.
     *
     * @return list of custom color styles
     * @throws SQLException if the database fails
     */
    public List<ColorStyle> findAllCustom() throws SQLException {
        String sql = "SELECT * FROM color_styles WHERE is_default = false AND deleted_at IS NULL ORDER BY sort_order, name";
        List<ColorStyle> styles = new ArrayList<>();

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                styles.add(mapResultSetToColorStyle(rs));
            }
        }

        return styles;
    }

    /**
     * Checks if a color style with the given name exists.
     *
     * @param name the name to check
     * @return true if a color style with this name exists
     * @throws SQLException if the database fails
     */
    public boolean existsByName(String name) throws SQLException {
        String sql = "SELECT COUNT(*) FROM color_styles WHERE name = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Checks if a custom color style with the given name exists.
     *
     * @param name the name to check
     * @return true if a custom color style with this name exists
     * @throws SQLException if the database fails
     */
    public boolean existsCustomByName(String name) throws SQLException {
        String sql = "SELECT COUNT(*) FROM color_styles WHERE name = ? AND is_default = false";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Moves a color style to the trash.
     *
     * @param id the ID of the color style
     * @return true if it was in use and is in the trash now
     * @throws SQLException if the database fails
     */
    public boolean moveToTrash(String id) throws SQLException {
        String sql = "UPDATE color_styles SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(java.time.LocalDateTime.now()));
            stmt.setString(2, id);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Takes a color style out of the trash, back into use.
     *
     * @param id the ID of the color style
     * @return true if it was in the trash and is in use now
     * @throws SQLException if the database fails
     */
    public boolean restore(String id) throws SQLException {
        String sql = "UPDATE color_styles SET deleted_at = NULL WHERE id = ? AND deleted_at IS NOT NULL";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Checks if a color style with this ID is stored, in use or in the trash.
     *
     * @param id the ID to check
     * @return true if the ID is taken
     * @throws SQLException if the database fails
     */
    public boolean existsById(String id) throws SQLException {
        String sql = "SELECT COUNT(*) FROM color_styles WHERE id = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Retrieves the color styles in the trash, the latest deletion first.
     *
     * @return list of the color styles in the trash
     * @throws SQLException if the database fails
     */
    public List<ColorStyle> findDeleted() throws SQLException {
        String sql = "SELECT * FROM color_styles WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC";
        List<ColorStyle> styles = new ArrayList<>();

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next())
                styles.add(mapResultSetToColorStyle(rs));
        }

        return styles;
    }

    /**
     * Deletes all custom (non-default) color styles.
     *
     * @return number of deleted styles
     * @throws SQLException if the database fails
     */
    public int deleteAllCustom() throws SQLException {
        String sql = "DELETE FROM color_styles WHERE is_default = false";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int rowsAffected = stmt.executeUpdate();

            logger.debug("Deleted {} custom color styles", rowsAffected);
            return rowsAffected;
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
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        style.setDeletedAt(deletedAt != null ? deletedAt.toLocalDateTime() : null);

        return style;
    }
}
