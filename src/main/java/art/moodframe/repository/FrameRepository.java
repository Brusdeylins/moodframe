package art.moodframe.repository;

import art.moodframe.database.DatabaseConfig;
import art.moodframe.model.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repository for the frames of the user in SQLite (table {@code frames}).
 *
 * <p>The standard frames come from code and are never stored. The finders skip the frames in the trash
 * ({@code deleted_at} set), except {@link #findDeleted()} and {@link #exists(String)}.</p>
 *
 * @since 2.0.0
 */
public class FrameRepository {

    private static final Logger logger = LoggerFactory.getLogger(FrameRepository.class);

    /**
     * Stores a new frame; a frame without id gets a random one.
     *
     * @param frame the frame to store, with a model
     * @return true if it was stored
     */
    public boolean save(Frame frame) {
        String sql = """
            INSERT INTO frames (name, model_id, resolution, aspect_ratio, image_count, guidance, steps, sort_order, id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        if (frame.getId() == null || frame.getId().isEmpty())
            frame.setId(UUID.randomUUID().toString());

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            bind(stmt, frame);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to save frame: {}", frame.getName(), e);
            return false;
        }
    }

    /**
     * Updates name, settings and sort order of a stored frame.
     *
     * @param frame the frame with its id
     * @return true if it was updated
     */
    public boolean update(Frame frame) {
        String sql = """
            UPDATE frames
            SET name = ?, model_id = ?, resolution = ?, aspect_ratio = ?, image_count = ?, guidance = ?, steps = ?,
                sort_order = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            bind(stmt, frame);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to update frame: {}", frame.getName(), e);
            return false;
        }
    }

    /**
     * Removes a stored frame for good.
     *
     * @param id the frame id
     * @return true if it was removed
     */
    public boolean delete(String id) {
        return execute("DELETE FROM frames WHERE id = ?", id) > 0;
    }

    /**
     * Moves a frame to the trash.
     *
     * @param id the frame id
     * @return true if it was in use and is in the trash now
     */
    public boolean moveToTrash(String id) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE frames SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL")) {
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(2, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to move frame to the trash: {}", id, e);
            return false;
        }
    }

    /**
     * Takes a frame out of the trash, back into use.
     *
     * @param id the frame id
     * @return true if it was in the trash and is in use now
     */
    public boolean restore(String id) {
        return execute("UPDATE frames SET deleted_at = NULL WHERE id = ? AND deleted_at IS NOT NULL", id) > 0;
    }

    /**
     * Checks if a frame, in use or in the trash, has this id.
     *
     * @param id the frame id
     * @return true if the id is taken
     */
    public boolean exists(String id) {
        return !query("SELECT * FROM frames WHERE id = ?", id).isEmpty();
    }

    /**
     * Finds a stored frame by its id.
     *
     * @param id the frame id
     * @return the frame, null if there is none in use
     */
    public Frame findById(String id) {
        List<Frame> frames = query("SELECT * FROM frames WHERE id = ? AND deleted_at IS NULL", id);
        return frames.isEmpty() ? null : frames.get(0);
    }

    /**
     * Finds a stored frame by its name.
     *
     * @param name the frame name
     * @return the frame, null if there is none in use
     */
    public Frame findByName(String name) {
        List<Frame> frames = query("SELECT * FROM frames WHERE name = ? AND deleted_at IS NULL", name);
        return frames.isEmpty() ? null : frames.get(0);
    }

    /**
     * Returns all stored frames in use, ordered by sort order and name.
     *
     * @return the frames, empty on failure
     */
    public List<Frame> findAll() {
        return query("SELECT * FROM frames WHERE deleted_at IS NULL ORDER BY sort_order, name");
    }

    /**
     * Returns the frames in the trash.
     *
     * @return the frames, the latest deletion first, empty on failure
     */
    public List<Frame> findDeleted() {
        return query("SELECT * FROM frames WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC");
    }

    /** Binds the columns of a frame in the order of {@link #save} and {@link #update}, the id last. */
    private static void bind(PreparedStatement stmt, Frame frame) throws SQLException {
        stmt.setString(1, frame.getName());
        stmt.setString(2, frame.getModel());
        stmt.setString(3, frame.getResolution());
        stmt.setString(4, frame.getAspectRatio());
        stmt.setInt(5, frame.getImageCount());
        if (frame.getGuidance() != null)
            stmt.setDouble(6, frame.getGuidance());
        else
            stmt.setNull(6, Types.REAL);
        if (frame.getSteps() != null)
            stmt.setInt(7, frame.getSteps());
        else
            stmt.setNull(7, Types.INTEGER);
        stmt.setInt(8, frame.getSortOrder());
        stmt.setString(9, frame.getId());
    }

    private int execute(String sql, String value) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to change frames for {}", value, e);
            return 0;
        }
    }

    private List<Frame> query(String sql, String... values) {
        List<Frame> frames = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++)
                stmt.setString(i + 1, values[i]);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next())
                    frames.add(map(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to load frames", e);
        }
        return frames;
    }

    private static Frame map(ResultSet rs) throws SQLException {
        Frame frame = new Frame(rs.getString("id"), rs.getString("name"));
        frame.setModel(rs.getString("model_id"));
        frame.setResolution(rs.getString("resolution"));
        frame.setAspectRatio(rs.getString("aspect_ratio"));
        frame.setImageCount(rs.getInt("image_count"));
        double guidance = rs.getDouble("guidance");
        frame.setGuidance(rs.wasNull() ? null : guidance);
        int steps = rs.getInt("steps");
        frame.setSteps(rs.wasNull() ? null : steps);
        frame.setSortOrder(rs.getInt("sort_order"));
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        frame.setDeletedAt(deletedAt != null ? deletedAt.toLocalDateTime() : null);
        return frame;
    }
}
