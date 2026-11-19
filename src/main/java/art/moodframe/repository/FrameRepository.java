package art.moodframe.repository;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Frame;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the frames of the user in SQLite (table {@code frames}).
 *
 * <p>The standard frames come from code and are never stored. The finders skip the frames in the trash
 * ({@code deleted_at} set), except {@link #findDeleted()} and {@link #exists(String)}; a finder of one frame answers
 * with an {@link Optional}.</p>
 *
 * <p>A database error is thrown as {@link SQLException}, the caller decides what follows.</p>
 *
 * @since 2.0.0
 */
public class FrameRepository {

    private final DatabaseManager database;

    /**
     * Creates the repository.
     *
     * @param database the database of the frames
     */
    public FrameRepository(DatabaseManager database) {
        this.database = database;
    }

    /**
     * Stores a new frame; a frame without id gets a random one.
     *
     * @param frame the frame to store, with an aspect ratio
     * @return true if it was stored
     * @throws SQLException if the database fails
     */
    public boolean save(Frame frame) throws SQLException {
        String sql = """
            INSERT INTO frames (name, aspect_ratio, resolution, sort_order, id)
            VALUES (?, ?, ?, ?, ?)
            """;
        if (frame.getId() == null || frame.getId().isEmpty())
            frame.setId(UUID.randomUUID().toString());

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            bind(stmt, frame);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Updates name, aspect ratio, resolution and sort order of a stored frame.
     *
     * @param frame the frame with its id
     * @return true if it was updated
     * @throws SQLException if the database fails
     */
    public boolean update(Frame frame) throws SQLException {
        String sql = """
            UPDATE frames
            SET name = ?, aspect_ratio = ?, resolution = ?, sort_order = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            bind(stmt, frame);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Removes a stored frame for good.
     *
     * @param id the frame id
     * @return true if it was removed
     * @throws SQLException if the database fails
     */
    public boolean delete(String id) throws SQLException {
        return execute("DELETE FROM frames WHERE id = ?", id) > 0;
    }

    /**
     * Moves a frame to the trash.
     *
     * @param id the frame id
     * @return true if it was in use and is in the trash now
     * @throws SQLException if the database fails
     */
    public boolean moveToTrash(String id) throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE frames SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL")) {
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(2, id);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Takes a frame out of the trash, back into use.
     *
     * @param id the frame id
     * @return true if it was in the trash and is in use now
     * @throws SQLException if the database fails
     */
    public boolean restore(String id) throws SQLException {
        return execute("UPDATE frames SET deleted_at = NULL WHERE id = ? AND deleted_at IS NOT NULL", id) > 0;
    }

    /**
     * Checks if a frame, in use or in the trash, has this id.
     *
     * @param id the frame id
     * @return true if the id is taken
     * @throws SQLException if the database fails
     */
    public boolean exists(String id) throws SQLException {
        return !query("SELECT * FROM frames WHERE id = ?", id).isEmpty();
    }

    /**
     * Finds a stored frame by its id.
     *
     * @param id the frame id
     * @return the frame, empty if there is none in use
     * @throws SQLException if the database fails
     */
    public Optional<Frame> findById(String id) throws SQLException {
        return first(query("SELECT * FROM frames WHERE id = ? AND deleted_at IS NULL", id));
    }

    /**
     * Finds a stored frame by its name.
     *
     * @param name the frame name
     * @return the frame, empty if there is none in use
     * @throws SQLException if the database fails
     */
    public Optional<Frame> findByName(String name) throws SQLException {
        return first(query("SELECT * FROM frames WHERE name = ? AND deleted_at IS NULL", name));
    }

    /**
     * Returns all stored frames in use, ordered by sort order and name.
     *
     * @return the frames
     * @throws SQLException if the database fails
     */
    public List<Frame> findAll() throws SQLException {
        return query("SELECT * FROM frames WHERE deleted_at IS NULL ORDER BY sort_order, name");
    }

    /**
     * Returns the frames in the trash.
     *
     * @return the frames, the latest deletion first
     * @throws SQLException if the database fails
     */
    public List<Frame> findDeleted() throws SQLException {
        return query("SELECT * FROM frames WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC");
    }

    /** Binds the columns of a frame in the order of {@link #save} and {@link #update}, the id last. */
    private static void bind(PreparedStatement stmt, Frame frame) throws SQLException {
        stmt.setString(1, frame.getName());
        stmt.setString(2, frame.getAspectRatio());
        stmt.setString(3, frame.getResolution());
        stmt.setInt(4, frame.getSortOrder());
        stmt.setString(5, frame.getId());
    }

    private int execute(String sql, String value) throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            return stmt.executeUpdate();
        }
    }

    private static Optional<Frame> first(List<Frame> frames) {
        return frames.isEmpty() ? Optional.empty() : Optional.of(frames.get(0));
    }

    private List<Frame> query(String sql, String... values) throws SQLException {
        List<Frame> frames = new ArrayList<>();

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++)
                stmt.setString(i + 1, values[i]);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next())
                    frames.add(map(rs));
            }
        }
        return frames;
    }

    private static Frame map(ResultSet rs) throws SQLException {
        Frame frame = new Frame(rs.getString("id"), rs.getString("name"));
        frame.setAspectRatio(rs.getString("aspect_ratio"));
        frame.setResolution(rs.getString("resolution"));
        frame.setSortOrder(rs.getInt("sort_order"));
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        frame.setDeletedAt(deletedAt != null ? deletedAt.toLocalDateTime() : null);
        return frame;
    }
}
