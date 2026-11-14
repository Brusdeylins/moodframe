package art.moodframe.repository;

import art.moodframe.database.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * Repository for the favorites of the user in SQLite (table {@code favorites}): per kind of entry (mood, frame or
 * building block) the ids of the favorite entries. Deleting an entry of the user for good drops its favorite by a
 * trigger of migration V027.
 *
 * @since 2.0.0
 */
public class FavoriteRepository {

    private static final Logger logger = LoggerFactory.getLogger(FavoriteRepository.class);

    /**
     * Returns the ids of the favorites of a kind.
     *
     * @param kind the kind of the entries, for example {@code MOOD}
     * @return the ids, empty on failure
     */
    public Set<String> findIds(String kind) {
        Set<String> ids = new HashSet<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT entry_id FROM favorites WHERE kind = ?")) {
            stmt.setString(1, kind);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next())
                    ids.add(rs.getString("entry_id"));
            }
        } catch (SQLException e) {
            logger.error("Failed to load the favorites of {}", kind, e);
        }
        return ids;
    }

    /**
     * Stores an entry as favorite; an entry that is one already stays one.
     *
     * @param kind the kind of the entry
     * @param id   the entry id
     * @return true if it is a favorite now
     */
    public boolean add(String kind, String id) {
        return execute("INSERT OR IGNORE INTO favorites (kind, entry_id) VALUES (?, ?)", kind, id);
    }

    /**
     * Removes the favorite of an entry, if it has one.
     *
     * @param kind the kind of the entry
     * @param id   the entry id
     * @return true if it is no favorite now
     */
    public boolean remove(String kind, String id) {
        return execute("DELETE FROM favorites WHERE kind = ? AND entry_id = ?", kind, id);
    }

    private boolean execute(String sql, String kind, String id) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, kind);
            stmt.setString(2, id);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error("Failed to change the favorite {} of {}", id, kind, e);
            return false;
        }
    }
}
