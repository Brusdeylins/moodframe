package art.moodframe.repository;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.PromptBlock;

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
 * Repository for the prompt building blocks of the user in SQLite (table {@code prompt_blocks}).
 *
 * <p>The standard blocks come from code and are never stored. All queries skip the blocks
 * in the trash ({@code deleted_at} set), except those for the trash itself.</p>
 *
 * <p>A database error is thrown as {@link SQLException}, the caller decides what follows.</p>
 *
 * @since 2.0.0
 */
public class PromptBlockRepository {

    private final DatabaseManager database;

    /**
     * Creates the repository.
     *
     * @param database the database of the building blocks
     */
    public PromptBlockRepository(DatabaseManager database) {
        this.database = database;
    }

    /**
     * Stores a new block; a block without id gets a random one.
     *
     * @param block the block to store
     * @return true if it was stored
     * @throws SQLException if the database fails
     */
    public boolean save(PromptBlock block) throws SQLException {
        String sql = """
            INSERT INTO prompt_blocks (id, type, name, text, block_group, photographic, params, sort_order)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        if (block.getId() == null || block.getId().isEmpty())
            block.setId(UUID.randomUUID().toString());

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, block.getId());
            stmt.setString(2, block.getType().name());
            stmt.setString(3, block.getName());
            stmt.setString(4, block.getText());
            stmt.setString(5, block.getGroup());
            stmt.setBoolean(6, block.isPhotographic());
            stmt.setString(7, block.getParams());
            stmt.setInt(8, block.getSortOrder());
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Updates name, text, group, photographic flag, parameters and sort order of a stored block.
     *
     * @param block the block with its id
     * @return true if it was updated
     * @throws SQLException if the database fails
     */
    public boolean update(PromptBlock block) throws SQLException {
        String sql = """
            UPDATE prompt_blocks
            SET name = ?, text = ?, block_group = ?, photographic = ?, params = ?, sort_order = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, block.getName());
            stmt.setString(2, block.getText());
            stmt.setString(3, block.getGroup());
            stmt.setBoolean(4, block.isPhotographic());
            stmt.setString(5, block.getParams());
            stmt.setInt(6, block.getSortOrder());
            stmt.setString(7, block.getId());
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Removes a stored block.
     *
     * @param id the block id
     * @return true if it was removed
     * @throws SQLException if the database fails
     */
    public boolean delete(String id) throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM prompt_blocks WHERE id = ?")) {
            stmt.setString(1, id);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Moves a stored block to the trash or takes it out again.
     *
     * @param id the block id
     * @param deletedAt when it was moved to the trash, null to take it out
     * @return true if the block was changed
     * @throws SQLException if the database fails
     */
    public boolean setDeletedAt(String id, LocalDateTime deletedAt) throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE prompt_blocks SET deleted_at = ? WHERE id = ?")) {
            stmt.setTimestamp(1, deletedAt != null ? Timestamp.valueOf(deletedAt) : null);
            stmt.setString(2, id);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Checks if a block with this id is stored, in use or in the trash.
     *
     * @param id the block id
     * @return true if the id is taken
     * @throws SQLException if the database fails
     */
    public boolean existsById(String id) throws SQLException {
        return !query("SELECT * FROM prompt_blocks WHERE id = ?", id).isEmpty();
    }

    /**
     * Returns the blocks in the trash, the latest deletion first.
     *
     * @return the blocks
     * @throws SQLException if the database fails
     */
    public List<PromptBlock> findDeleted() throws SQLException {
        return query("SELECT * FROM prompt_blocks WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC");
    }

    /**
     * Finds a block in the trash by its id.
     *
     * @param id the block id
     * @return the block, null if there is none in the trash
     * @throws SQLException if the database fails
     */
    public PromptBlock findDeletedById(String id) throws SQLException {
        List<PromptBlock> blocks = query("SELECT * FROM prompt_blocks WHERE id = ? AND deleted_at IS NOT NULL", id);
        return blocks.isEmpty() ? null : blocks.get(0);
    }

    /**
     * Removes all stored blocks of a type.
     *
     * @param type the block type
     * @return the number of removed blocks
     * @throws SQLException if the database fails
     */
    public int deleteAll(PromptBlock.Type type) throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM prompt_blocks WHERE type = ?")) {
            stmt.setString(1, type.name());
            return stmt.executeUpdate();
        }
    }

    /**
     * Finds a stored block by its id.
     *
     * @param id the block id
     * @return the block, null if there is none
     * @throws SQLException if the database fails
     */
    public PromptBlock findById(String id) throws SQLException {
        List<PromptBlock> blocks = query("SELECT * FROM prompt_blocks WHERE id = ? AND deleted_at IS NULL", id);
        return blocks.isEmpty() ? null : blocks.get(0);
    }

    /**
     * Finds a stored block in the trash by its id.
     *
     * @param id the block id
     * @return the block, null if it is not in the trash
     * @throws SQLException if the database fails
     */
    public PromptBlock findInTrash(String id) throws SQLException {
        List<PromptBlock> blocks = query("SELECT * FROM prompt_blocks WHERE id = ? AND deleted_at IS NOT NULL", id);
        return blocks.isEmpty() ? null : blocks.get(0);
    }

    /**
     * Finds a stored block of a type by its name.
     *
     * @param type the block type
     * @param name the block name
     * @return the block, null if there is none
     * @throws SQLException if the database fails
     */
    public PromptBlock findByName(PromptBlock.Type type, String name) throws SQLException {
        List<PromptBlock> blocks = query(
                "SELECT * FROM prompt_blocks WHERE type = ? AND name = ? AND deleted_at IS NULL", type.name(), name);
        return blocks.isEmpty() ? null : blocks.get(0);
    }

    /**
     * Returns all stored blocks of a type, ordered by sort order and name.
     *
     * @param type the block type
     * @return the blocks
     * @throws SQLException if the database fails
     */
    public List<PromptBlock> findAll(PromptBlock.Type type) throws SQLException {
        return query("SELECT * FROM prompt_blocks WHERE type = ? AND deleted_at IS NULL ORDER BY sort_order, name",
                type.name());
    }

    private List<PromptBlock> query(String sql, String... values) throws SQLException {
        List<PromptBlock> blocks = new ArrayList<>();

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++)
                stmt.setString(i + 1, values[i]);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next())
                    blocks.add(map(rs));
            }
        }
        return blocks;
    }

    private static PromptBlock map(ResultSet rs) throws SQLException {
        PromptBlock block = new PromptBlock(rs.getString("id"), PromptBlock.Type.valueOf(rs.getString("type")),
                rs.getString("name"), rs.getString("text"), rs.getString("block_group"), rs.getBoolean("photographic"));
        block.setParams(rs.getString("params"));
        block.setSortOrder(rs.getInt("sort_order"));
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        block.setDeletedAt(deletedAt != null ? deletedAt.toLocalDateTime() : null);
        return block;
    }
}
