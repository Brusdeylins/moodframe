package art.moodframe.repository;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Mood;

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
 * Repository for the moods of the user in SQLite (table {@code moods}).
 *
 * <p>The standard moods come from code and are never stored. The finders skip the moods in the trash
 * ({@code deleted_at} set), except {@link #findDeleted()} and {@link #exists(String)}; a finder of one mood answers
 * with an {@link Optional}.</p>
 *
 * <p>A database error is thrown as {@link SQLException}, the caller decides what follows.</p>
 *
 * @since 2.0.0
 */
public class MoodRepository {

    private final DatabaseManager database;

    /**
     * Creates the repository.
     *
     * @param database the database of the moods
     */
    public MoodRepository(DatabaseManager database) {
        this.database = database;
    }

    /**
     * Stores a new mood; a mood without id gets a random one.
     *
     * @param mood the mood to store
     * @return true if it was stored
     * @throws SQLException if the database fails
     */
    public boolean save(Mood mood) throws SQLException {
        String sql = """
            INSERT INTO moods (id, name, atmosphere_id, style_id, color_id, light_id, camera_id, sort_order)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        if (mood.getId() == null || mood.getId().isEmpty())
            mood.setId(UUID.randomUUID().toString());

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, mood.getId());
            stmt.setString(2, mood.getName());
            int index = 3;
            for (Mood.Field field : Mood.Field.values())
                stmt.setString(index++, mood.getReference(field));
            stmt.setInt(index, mood.getSortOrder());
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Updates name, blocks and sort order of a stored mood.
     *
     * @param mood the mood with its id
     * @return true if it was updated
     * @throws SQLException if the database fails
     */
    public boolean update(Mood mood) throws SQLException {
        String sql = """
            UPDATE moods
            SET name = ?, atmosphere_id = ?, style_id = ?, color_id = ?, light_id = ?, camera_id = ?, sort_order = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, mood.getName());
            int index = 2;
            for (Mood.Field field : Mood.Field.values())
                stmt.setString(index++, mood.getReference(field));
            stmt.setInt(index++, mood.getSortOrder());
            stmt.setString(index, mood.getId());
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Removes a mood in the trash for good; a mood in use, e.g. restored meanwhile, stays.
     *
     * @param id the mood id
     * @return true if it was in the trash and is removed
     * @throws SQLException if the database fails
     */
    public boolean delete(String id) throws SQLException {
        return execute("DELETE FROM moods WHERE id = ? AND deleted_at IS NOT NULL", id) > 0;
    }

    /**
     * Moves a mood to the trash.
     *
     * @param id the mood id
     * @return true if it was in use and is in the trash now
     * @throws SQLException if the database fails
     */
    public boolean moveToTrash(String id) throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE moods SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL")) {
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(2, id);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Takes a mood out of the trash, back into use.
     *
     * @param id the mood id
     * @return true if it was in the trash and is in use now
     * @throws SQLException if the database fails
     */
    public boolean restore(String id) throws SQLException {
        return execute("UPDATE moods SET deleted_at = NULL WHERE id = ? AND deleted_at IS NOT NULL", id) > 0;
    }

    /**
     * Empties every field of the stored moods, in use or in the trash, that refers to a block, in one transaction.
     *
     * @param blockId the id of the block
     * @return the number of emptied fields
     * @throws SQLException if the database fails
     */
    public int dropReference(String blockId) throws SQLException {
        return database.inTransaction((Connection conn) -> dropReferences(conn, "= ?", blockId));
    }

    /**
     * Empties every field of the stored moods whose block id meets a condition, in the transaction of the caller.
     *
     * @param conn      the connection of the transaction
     * @param condition the condition on the column of the block id with one parameter, e.g. {@code "= ?"}
     * @param value     the value of the parameter
     * @return the number of emptied fields
     * @throws SQLException if the database fails
     */
    static int dropReferences(Connection conn, String condition, String value) throws SQLException {
        int dropped = 0;
        for (Mood.Field field : Mood.Field.values()) {
            String column = column(field);
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE moods SET " + column
                    + " = NULL, updated_at = CURRENT_TIMESTAMP WHERE " + column + " " + condition)) {
                stmt.setString(1, value);
                dropped += stmt.executeUpdate();
            }
        }
        return dropped;
    }

    /*  The column of the block id of a field in the table moods  */
    private static String column(Mood.Field field) {
        return switch (field) {
            case ATMOSPHERE -> "atmosphere_id";
            case STYLE -> "style_id";
            case COLOR -> "color_id";
            case LIGHT -> "light_id";
            case CAMERA -> "camera_id";
        };
    }

    /**
     * Checks if a mood, in use or in the trash, has this id.
     *
     * @param id the mood id
     * @return true if the id is taken
     * @throws SQLException if the database fails
     */
    public boolean exists(String id) throws SQLException {
        return !query("SELECT * FROM moods WHERE id = ?", id).isEmpty();
    }

    /**
     * Finds a stored mood by its id.
     *
     * @param id the mood id
     * @return the mood, empty if there is none in use
     * @throws SQLException if the database fails
     */
    public Optional<Mood> findById(String id) throws SQLException {
        return first(query("SELECT * FROM moods WHERE id = ? AND deleted_at IS NULL", id));
    }

    /**
     * Finds a stored mood by its name.
     *
     * @param name the mood name
     * @return the mood, empty if there is none in use
     * @throws SQLException if the database fails
     */
    public Optional<Mood> findByName(String name) throws SQLException {
        return first(query("SELECT * FROM moods WHERE name = ? AND deleted_at IS NULL", name));
    }

    /**
     * Returns all stored moods in use, ordered by sort order and name.
     *
     * @return the moods
     * @throws SQLException if the database fails
     */
    public List<Mood> findAll() throws SQLException {
        return query("SELECT * FROM moods WHERE deleted_at IS NULL ORDER BY sort_order, name");
    }

    /**
     * Returns the moods in the trash.
     *
     * @return the moods, the latest deletion first
     * @throws SQLException if the database fails
     */
    public List<Mood> findDeleted() throws SQLException {
        return query("SELECT * FROM moods WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC");
    }

    private int execute(String sql, String value) throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            return stmt.executeUpdate();
        }
    }

    private static Optional<Mood> first(List<Mood> moods) {
        return moods.isEmpty() ? Optional.empty() : Optional.of(moods.get(0));
    }

    private List<Mood> query(String sql, String... values) throws SQLException {
        List<Mood> moods = new ArrayList<>();

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++)
                stmt.setString(i + 1, values[i]);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next())
                    moods.add(map(rs));
            }
        }
        return moods;
    }

    private static Mood map(ResultSet rs) throws SQLException {
        Mood mood = new Mood(rs.getString("id"), rs.getString("name"));
        for (Mood.Field field : Mood.Field.values())
            mood.setReference(field, rs.getString(column(field)));
        mood.setSortOrder(rs.getInt("sort_order"));
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        mood.setDeletedAt(deletedAt != null ? deletedAt.toLocalDateTime() : null);
        return mood;
    }
}
