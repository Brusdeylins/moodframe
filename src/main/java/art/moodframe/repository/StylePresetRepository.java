package art.moodframe.repository;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.database.DatabaseConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.model.StylePreset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repository for managing StylePreset persistence in SQLite.
 * 
 * <p>This repository implements the Repository pattern for StylePreset entities,
 * providing CRUD operations with proper transaction management and error handling.
 * It serves as the data access layer between the service layer and SQLite database.</p>
 * 
 * <p><strong>Features:</strong></p>
 * <ul>
 * <li>Full CRUD operations for style presets</li>
 * <li>Transaction management for data consistency</li>
 * <li>Prepared statements to prevent SQL injection</li>
 * <li>Automatic migration from JSON-based storage</li>
 * <li>Optimized queries with proper indexing</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class StylePresetRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(StylePresetRepository.class);
    private final ApplicationConfig config;
    
    public StylePresetRepository() {
        this.config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
    }
    
    /**
     * Saves a new style preset to the database.
     * 
     * @param stylePreset the style preset to save
     * @return true if save was successful, false otherwise
     */
    public boolean save(StylePreset stylePreset) {
        String sql = """
            INSERT INTO style_presets (id, name, prompt_text, is_default, sort_order)
            VALUES (?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            String id = stylePreset.getId();
            if (id == null || id.isEmpty()) {
                id = UUID.randomUUID().toString();
                stylePreset.setId(id);
            }
            
            stmt.setString(1, id);
            stmt.setString(2, stylePreset.getName());
            stmt.setString(3, stylePreset.getPromptText());
            stmt.setBoolean(4, stylePreset.isDefault());
            stmt.setInt(5, stylePreset.getSortOrder());
            
            int rowsAffected = stmt.executeUpdate();
            boolean success = rowsAffected > 0;
            
            if (success) {
                logger.warn("Failed to save style preset: {}", stylePreset.getName());
            }
            
            return success;
            
        } catch (SQLException e) {
            logger.error("Failed to save style preset: " + stylePreset.getName(), e);
            return false;
        }
    }
    
    /**
     * Updates an existing style preset in the database.
     * 
     * @param stylePreset the style preset to update
     * @return true if update was successful, false otherwise
     */
    public boolean update(StylePreset stylePreset) {
        String sql = """
            UPDATE style_presets 
            SET name = ?, prompt_text = ?, is_default = ?, sort_order = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, stylePreset.getName());
            stmt.setString(2, stylePreset.getPromptText());
            stmt.setBoolean(3, stylePreset.isDefault());
            stmt.setInt(4, stylePreset.getSortOrder());
            stmt.setString(5, stylePreset.getId());
            
            int rowsAffected = stmt.executeUpdate();
            boolean success = rowsAffected > 0;
            
            if (success) {
                logger.warn("Failed to update style preset: {}", stylePreset.getName());
            }
            
            return success;
            
        } catch (SQLException e) {
            logger.error("Failed to update style preset: " + stylePreset.getName(), e);
            return false;
        }
    }
    
    /**
     * Deletes a style preset from the database.
     * 
     * @param id the ID of the style preset to delete
     * @return true if deletion was successful, false otherwise
     */
    public boolean delete(String id) {
        String sql = "DELETE FROM style_presets WHERE id = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, id);
            
            int rowsAffected = stmt.executeUpdate();
            boolean success = rowsAffected > 0;
            
            if (success) {
                logger.warn("Failed to delete style preset with ID: {}", id);
            }
            
            return success;
            
        } catch (SQLException e) {
            logger.error("Failed to delete style preset with ID: " + id, e);
            return false;
        }
    }
    
    /**
     * Finds a style preset by its ID.
     * 
     * @param id the ID of the style preset
     * @return the style preset if found, null otherwise
     */
    public StylePreset findById(String id) {
        String sql = "SELECT * FROM style_presets WHERE id = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToStylePreset(rs);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find style preset by ID: " + id, e);
        }
        
        return null;
    }
    
    /**
     * Finds a style preset by its name.
     * 
     * @param name the name of the style preset
     * @return the style preset if found, null otherwise
     */
    public StylePreset findByName(String name) {
        String sql = "SELECT * FROM style_presets WHERE name = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, name);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToStylePreset(rs);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find style preset by name: " + name, e);
        }
        
        return null;
    }
    
    /**
     * Retrieves all style presets from the database.
     * 
     * @return list of all style presets, ordered by sort_order and name
     */
    public List<StylePreset> findAll() {
        String sql = "SELECT * FROM style_presets ORDER BY sort_order, name";
        List<StylePreset> presets = new ArrayList<>();
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                presets.add(mapResultSetToStylePreset(rs));
            }
            
        } catch (SQLException e) {
            logger.error("Failed to retrieve all style presets", e);
        }
        
        return presets;
    }
    
    /**
     * Retrieves all default style presets.
     * 
     * @return list of default style presets
     */
    public List<StylePreset> findAllDefault() {
        String sql = "SELECT * FROM style_presets WHERE is_default = true ORDER BY sort_order, name";
        List<StylePreset> presets = new ArrayList<>();
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                presets.add(mapResultSetToStylePreset(rs));
            }
            
        } catch (SQLException e) {
            logger.error("Failed to retrieve default style presets", e);
        }
        
        return presets;
    }
    
    /**
     * Retrieves all custom (non-default) style presets.
     * 
     * @return list of custom style presets
     */
    public List<StylePreset> findAllCustom() {
        String sql = "SELECT * FROM style_presets WHERE is_default = false ORDER BY sort_order, name";
        List<StylePreset> presets = new ArrayList<>();
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                presets.add(mapResultSetToStylePreset(rs));
            }
            
        } catch (SQLException e) {
            logger.error("Failed to retrieve custom style presets", e);
        }
        
        return presets;
    }
    
    /**
     * Checks if a style preset with the given name exists.
     * 
     * @param name the name to check
     * @return true if a style preset with this name exists
     */
    public boolean existsByName(String name) {
        String sql = "SELECT COUNT(*) FROM style_presets WHERE name = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, name);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to check if style preset exists: " + name, e);
        }
        
        return false;
    }
    
    /**
     * Deletes all custom (non-default) style presets.
     *
     * @return number of deleted presets
     */
    public int deleteAllCustom() {
        String sql = "DELETE FROM style_presets WHERE is_default = false";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int rowsAffected = stmt.executeUpdate();

            if (config != null && config.getLoggingEnabled()) {
                logger.info("Deleted {} custom style presets", rowsAffected);
            }
            return rowsAffected;

        } catch (SQLException e) {
            logger.error("Failed to delete all custom style presets", e);
            return 0;
        }
    }

    /**
     * Deletes all default style presets from the database.
     * Used during cleanup when defaults are served from code instead of DB.
     *
     * @return number of deleted presets
     */
    public int deleteAllDefault() {
        String sql = "DELETE FROM style_presets WHERE is_default = true";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected;

        } catch (SQLException e) {
            logger.error("Failed to delete all default style presets", e);
            return 0;
        }
    }

    /**
     * Finds a custom style preset by its name.
     * Only searches custom styles, not default styles.
     *
     * @param name the name of the style preset
     * @return the custom style preset if found, null otherwise
     */
    public StylePreset findCustomByName(String name) {
        String sql = "SELECT * FROM style_presets WHERE name = ? AND is_default = false";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToStylePreset(rs);
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to find custom style preset by name: " + name, e);
        }

        return null;
    }

    /**
     * Checks if a custom style preset with the given name exists.
     * Only checks custom styles, not default styles from code.
     *
     * @param name the name to check
     * @return true if a custom style preset with this name exists
     */
    public boolean existsCustomByName(String name) {
        String sql = "SELECT COUNT(*) FROM style_presets WHERE name = ? AND is_default = false";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to check if custom style preset exists: " + name, e);
        }

        return false;
    }
    
    /**
     * Batch insert multiple style presets for efficient migration.
     * 
     * @param stylePresets the list of style presets to insert
     * @return number of successfully inserted presets
     */
    public int batchInsert(List<StylePreset> stylePresets) {
        String sql = """
            INSERT OR IGNORE INTO style_presets (id, name, prompt_text, is_default, sort_order)
            VALUES (?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            conn.setAutoCommit(false); // Start transaction
            
            int insertedCount = 0;
            
            for (StylePreset preset : stylePresets) {
                String id = preset.getId();
                if (id == null || id.isEmpty()) {
                    id = UUID.randomUUID().toString();
                    preset.setId(id);
                }
                
                stmt.setString(1, id);
                stmt.setString(2, preset.getName());
                stmt.setString(3, preset.getPromptText());
                stmt.setBoolean(4, preset.isDefault());
                stmt.setInt(5, preset.getSortOrder());
                
                stmt.addBatch();
            }
            
            int[] results = stmt.executeBatch();
            conn.commit(); // Commit transaction
            
            // Count successful insertions
            for (int result : results) {
                if (result > 0) {
                    insertedCount++;
                }
            }
            
            if (config != null && config.getLoggingEnabled()) {
                logger.info("Batch inserted {} style presets", insertedCount);
            }
            return insertedCount;
            
        } catch (SQLException e) {
            logger.error("Failed to batch insert style presets", e);
            return 0;
        }
    }
    
    /**
     * Maps a ResultSet row to a StylePreset object.
     * 
     * @param rs the ResultSet positioned at a valid row
     * @return the mapped StylePreset
     * @throws SQLException if database access error occurs
     */
    private StylePreset mapResultSetToStylePreset(ResultSet rs) throws SQLException {
        StylePreset preset = new StylePreset(
            rs.getString("name"),
            rs.getString("prompt_text"),
            rs.getBoolean("is_default")
        );
        
        preset.setId(rs.getString("id"));
        preset.setSortOrder(rs.getInt("sort_order"));
        
        return preset;
    }
}