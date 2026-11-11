package art.moodframe.repository;

import art.moodframe.database.DatabaseConfig;
import art.moodframe.model.Mood;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repository for the moods of the user in SQLite (table {@code moods}).
 *
 * <p>The standard moods come from code and are never stored. The finders skip the moods in the trash
 * ({@code deleted_at} set), except {@link #findDeleted()} and {@link #exists(String)}.</p>
 *
 * @since 2.0.0
 */
public class MoodRepository {

    private static final Logger logger = LoggerFactory.getLogger(MoodRepository.class);

    /**
     * Stores a new mood; a mood without id gets a random one.
     *
     * @param mood the mood to store
     * @return true if it was stored
     */
    public boolean save(Mood mood) {
        String sql = """
            INSERT INTO moods (id, name, atmosphere_id, style_id, color_id, light_id, camera_id, sort_order)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        if (mood.getId() == null || mood.getId().isEmpty())
            mood.setId(UUID.randomUUID().toString());

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, mood.getId());
            stmt.setString(2, mood.getName());
            int index = 3;
            for (Mood.Field field : Mood.Field.values())
                stmt.setString(index++, mood.getReference(field));
            stmt.setInt(index, mood.getSortOrder());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to save mood: {}", mood.getName(), e);
            return false;
        }
    }

    /**
     * Updates name, blocks and sort order of a stored mood.
     *
     * @param mood the mood with its id
     * @return true if it was updated
     */
    public boolean update(Mood mood) {
        String sql = """
            UPDATE moods
            SET name = ?, atmosphere_id = ?, style_id = ?, color_id = ?, light_id = ?, camera_id = ?, sort_order = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, mood.getName());
            int index = 2;
            for (Mood.Field field : Mood.Field.values())
                stmt.setString(index++, mood.getReference(field));
            stmt.setInt(index++, mood.getSortOrder());
            stmt.setString(index, mood.getId());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to update mood: {}", mood.getName(), e);
            return false;
        }
    }

    /**
     * Removes a stored mood for good.
     *
     * @param id the mood id
     * @return true if it was removed
     */
    public boolean delete(String id) {
        return execute("DELETE FROM moods WHERE id = ?", id) > 0;
    }

    /**
     * Moves a mood to the trash.
     *
     * @param id the mood id
     * @return true if it was in use and is in the trash now
     */
    public boolean moveToTrash(String id) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE moods SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL")) {
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(2, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to move mood to the trash: {}", id, e);
            return false;
        }
    }

    /**
     * Takes a mood out of the trash, back into use.
     *
     * @param id the mood id
     * @return true if it was in the trash and is in use now
     */
    public boolean restore(String id) {
        return execute("UPDATE moods SET deleted_at = NULL WHERE id = ? AND deleted_at IS NOT NULL", id) > 0;
    }

    /**
     * Empties every field of the stored moods, in use or in the trash, that refers to a block.
     *
     * @param blockId the id of the block
     * @return the number of emptied fields
     */
    public int dropReference(String blockId) {
        int dropped = 0;
        for (Mood.Field field : Mood.Field.values())
            dropped += execute("UPDATE moods SET " + field.column() + " = NULL, updated_at = CURRENT_TIMESTAMP WHERE "
                    + field.column() + " = ?", blockId);
        return dropped;
    }

    /**
     * Checks if a mood, in use or in the trash, has this id.
     *
     * @param id the mood id
     * @return true if the id is taken
     */
    public boolean exists(String id) {
        return !query("SELECT * FROM moods WHERE id = ?", id).isEmpty();
    }

    /**
     * Finds a stored mood by its id.
     *
     * @param id the mood id
     * @return the mood, null if there is none in use
     */
    public Mood findById(String id) {
        List<Mood> moods = query("SELECT * FROM moods WHERE id = ? AND deleted_at IS NULL", id);
        return moods.isEmpty() ? null : moods.get(0);
    }

    /**
     * Finds a stored mood by its name.
     *
     * @param name the mood name
     * @return the mood, null if there is none in use
     */
    public Mood findByName(String name) {
        List<Mood> moods = query("SELECT * FROM moods WHERE name = ? AND deleted_at IS NULL", name);
        return moods.isEmpty() ? null : moods.get(0);
    }

    /**
     * Returns all stored moods in use, ordered by sort order and name.
     *
     * @return the moods, empty on failure
     */
    public List<Mood> findAll() {
        return query("SELECT * FROM moods WHERE deleted_at IS NULL ORDER BY sort_order, name");
    }

    /**
     * Returns the moods in the trash.
     *
     * @return the moods, the latest deletion first, empty on failure
     */
    public List<Mood> findDeleted() {
        return query("SELECT * FROM moods WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC");
    }

    private int execute(String sql, String value) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to change moods for {}", value, e);
            return 0;
        }
    }

    private List<Mood> query(String sql, String... values) {
        List<Mood> moods = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++)
                stmt.setString(i + 1, values[i]);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next())
                    moods.add(map(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to load moods", e);
        }
        return moods;
    }

    private static Mood map(ResultSet rs) throws SQLException {
        Mood mood = new Mood(rs.getString("id"), rs.getString("name"));
        for (Mood.Field field : Mood.Field.values())
            mood.setReference(field, rs.getString(field.column()));
        mood.setSortOrder(rs.getInt("sort_order"));
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        mood.setDeletedAt(deletedAt != null ? deletedAt.toLocalDateTime() : null);
        return mood;
    }
}
