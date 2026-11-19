package art.moodframe.repository;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.SavedImage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for the saved images in SQLite (table {@code saved_images}); their files lie in a folder of the library
 * and are copied and removed by the service.
 *
 * <p>The finders skip the images in the trash ({@code deleted_at} set), except {@link #findDeleted()},
 * {@link #findInTrash(String)} and {@link #findBySha256(String)}, as the same file is stored only once; a finder of
 * one image answers with an {@link Optional}. A name is unique among the images in use, a SHA-256 among all.</p>
 *
 * <p>A database error is thrown as {@link SQLException}, the caller decides what follows.</p>
 *
 * @since 2.1.4
 */
public class SavedImageRepository {

    private final DatabaseManager database;

    /**
     * Creates the repository.
     *
     * @param database the database of the saved images
     */
    public SavedImageRepository(DatabaseManager database) {
        this.database = database;
    }

    /**
     * Stores a new saved image.
     *
     * @param image the image with id, name, file and SHA-256 no image has
     * @return true if it was stored
     * @throws SQLException if the database fails, e.g. the name or the file is taken
     */
    public boolean insert(SavedImage image) throws SQLException {
        String sql = """
            INSERT INTO saved_images (id, name, file, width, height, sha256, description, favorite, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, image.id());
            stmt.setString(2, image.name());
            stmt.setString(3, image.file());
            stmt.setInt(4, image.width());
            stmt.setInt(5, image.height());
            stmt.setString(6, image.sha256());
            stmt.setString(7, image.description());
            stmt.setBoolean(8, image.favorite());
            stmt.setTimestamp(9, Timestamp.valueOf(image.createdAt()));
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Gives an image in use a new name.
     *
     * @param id   the id
     * @param name the new name
     * @return true if it was renamed
     * @throws SQLException if the database fails, e.g. the name is taken
     */
    public boolean rename(String id, String name) throws SQLException {
        return update("UPDATE saved_images SET name = ? WHERE id = ? AND deleted_at IS NULL", name, id) > 0;
    }

    /**
     * Gives an image in use a new description.
     *
     * @param id          the id
     * @param description the description, null for none
     * @return true if it was changed
     * @throws SQLException if the database fails
     */
    public boolean describe(String id, String description) throws SQLException {
        return update("UPDATE saved_images SET description = ? WHERE id = ? AND deleted_at IS NULL", description,
                id) > 0;
    }

    /**
     * Marks an image in use as favorite or not.
     *
     * @param id       the id
     * @param favorite whether it is a favorite
     * @return true if it was changed
     * @throws SQLException if the database fails
     */
    public boolean setFavorite(String id, boolean favorite) throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE saved_images SET favorite = ? WHERE id = ? AND deleted_at IS NULL")) {
            stmt.setBoolean(1, favorite);
            stmt.setString(2, id);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Moves an image to the trash.
     *
     * @param id the id
     * @return true if it was in use and is in the trash now
     * @throws SQLException if the database fails
     */
    public boolean moveToTrash(String id) throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE saved_images SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL")) {
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(2, id);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Takes an image out of the trash, back into use, under a name and with a description.
     *
     * @param id          the id
     * @param name        the name, which no image in use has
     * @param description the description, null for none
     * @return true if it was in the trash and is in use now
     * @throws SQLException if the database fails, e.g. the name is taken
     */
    public boolean restore(String id, String name, String description) throws SQLException {
        return update("UPDATE saved_images SET deleted_at = NULL, name = ?, description = ? "
                + "WHERE id = ? AND deleted_at IS NOT NULL", name, description, id) > 0;
    }

    /**
     * Removes an image in the trash for good; an image in use, e.g. restored meanwhile, stays.
     *
     * @param id the id
     * @return true if it was in the trash and is removed
     * @throws SQLException if the database fails
     */
    public boolean delete(String id) throws SQLException {
        return update("DELETE FROM saved_images WHERE id = ? AND deleted_at IS NOT NULL", id) > 0;
    }

    /**
     * Finds an image in use by its id.
     *
     * @param id the id
     * @return the image, empty if there is none in use
     * @throws SQLException if the database fails
     */
    public Optional<SavedImage> findById(String id) throws SQLException {
        return first(query("SELECT * FROM saved_images WHERE id = ? AND deleted_at IS NULL", id));
    }

    /**
     * Finds an image in use by its name.
     *
     * @param name the name
     * @return the image, empty if there is none in use
     * @throws SQLException if the database fails
     */
    public Optional<SavedImage> findByName(String name) throws SQLException {
        return first(query("SELECT * FROM saved_images WHERE name = ? AND deleted_at IS NULL", name));
    }

    /**
     * Finds the image of a file by its SHA-256, in use or in the trash.
     *
     * @param sha256 the SHA-256 of the file
     * @return the image with its {@code deletedAt}, empty if no image has this file
     * @throws SQLException if the database fails
     */
    public Optional<SavedImage> findBySha256(String sha256) throws SQLException {
        return first(query("SELECT * FROM saved_images WHERE sha256 = ?", sha256));
    }

    /**
     * Finds an image in the trash by its id.
     *
     * @param id the id
     * @return the image, empty if there is none in the trash
     * @throws SQLException if the database fails
     */
    public Optional<SavedImage> findInTrash(String id) throws SQLException {
        return first(query("SELECT * FROM saved_images WHERE id = ? AND deleted_at IS NOT NULL", id));
    }

    /**
     * Returns the images in use, the favorites first, then by name.
     *
     * @return the images
     * @throws SQLException if the database fails
     */
    public List<SavedImage> findAll() throws SQLException {
        return query("SELECT * FROM saved_images WHERE deleted_at IS NULL ORDER BY favorite DESC, name COLLATE NOCASE");
    }

    /**
     * Returns the images in the trash.
     *
     * @return the images, the latest deletion first
     * @throws SQLException if the database fails
     */
    public List<SavedImage> findDeleted() throws SQLException {
        return query("SELECT * FROM saved_images WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC");
    }

    private int update(String sql, String... values) throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++)
                stmt.setString(i + 1, values[i]);
            return stmt.executeUpdate();
        }
    }

    private static Optional<SavedImage> first(List<SavedImage> images) {
        return images.isEmpty() ? Optional.empty() : Optional.of(images.get(0));
    }

    private List<SavedImage> query(String sql, String... values) throws SQLException {
        List<SavedImage> images = new ArrayList<>();

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++)
                stmt.setString(i + 1, values[i]);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next())
                    images.add(map(rs));
            }
        }
        return images;
    }

    private static SavedImage map(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        return new SavedImage(rs.getString("id"), rs.getString("name"), rs.getString("file"), rs.getInt("width"),
                rs.getInt("height"), rs.getString("sha256"), rs.getString("description"), rs.getBoolean("favorite"),
                createdAt != null ? createdAt.toLocalDateTime() : null,
                deletedAt != null ? deletedAt.toLocalDateTime() : null);
    }
}
