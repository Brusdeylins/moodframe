package art.moodframe.repository;

import art.moodframe.database.DatabaseConfig;
import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.BrainstormMessage;
import art.moodframe.model.BrainstormSession;
import org.json.JSONException;
import org.json.JSONObject;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for brainstorm sessions, their messages and draft versions in SQLite.
 *
 * <p>Deleting a session removes its messages and drafts explicitly, so it does not depend
 * on the foreign key setting of the connection.</p>
 *
 * @since 1.7.0
 */
public class BrainstormRepository {

    private static final Logger logger = LoggerFactory.getLogger(BrainstormRepository.class);

    /**
     * Creates and stores a new empty session.
     *
     * @param title initial title of the session
     * @return the created session, or empty if it could not be stored
     */
    public Optional<BrainstormSession> createSession(String title) {
        return createSession(title, null);
    }

    /**
     * Creates and stores a new empty session that is answered by the given language model.
     *
     * @param title initial title of the session
     * @param engine "gemini" or "openai", null to follow the choice of the window
     * @return the created session, or empty if it could not be stored
     * @since 1.9.2
     */
    public Optional<BrainstormSession> createSession(String title, String engine) {
        LocalDateTime now = LocalDateTime.now();
        BrainstormSession session = new BrainstormSession(UUID.randomUUID().toString(), title, now, now);
        session.setEngine(engine);
        String sql = "INSERT INTO brainstorm_sessions (id, title, created_at, updated_at, engine) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, session.getId());
            stmt.setString(2, title);
            stmt.setTimestamp(3, Timestamp.valueOf(now));
            stmt.setTimestamp(4, Timestamp.valueOf(now));
            stmt.setString(5, engine);
            stmt.executeUpdate();
            return Optional.of(session);
        } catch (SQLException e) {
            logger.error("Failed to create brainstorm session", e);
            return Optional.empty();
        }
    }

    /**
     * Returns all sessions, most recently changed first.
     *
     * @return the sessions, empty on failure
     */
    public List<BrainstormSession> findAllSessions() {
        String sql = "SELECT id, title, created_at, updated_at, engine FROM brainstorm_sessions ORDER BY updated_at DESC";
        List<BrainstormSession> sessions = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                BrainstormSession session = new BrainstormSession(rs.getString("id"), rs.getString("title"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime());
                session.setEngine(rs.getString("engine"));
                sessions.add(session);
            }
        } catch (SQLException e) {
            logger.error("Failed to load brainstorm sessions", e);
        }
        return sessions;
    }

    /**
     * Updates the title of a session and marks it as changed now.
     *
     * @param sessionId the session id
     * @param title the new title
     */
    public void updateTitle(String sessionId, String title) {
        String sql = "UPDATE brainstorm_sessions SET title = ?, updated_at = ? WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, title);
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(3, sessionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update brainstorm session title: {}", sessionId, e);
        }
    }

    /**
     * Stores which language model answers a session (does not count as a change of the session).
     *
     * @param sessionId the session id
     * @param engine "gemini" or "openai"
     * @since 1.9.2
     */
    public void updateEngine(String sessionId, String engine) {
        String sql = "UPDATE brainstorm_sessions SET engine = ? WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, engine);
            stmt.setString(2, sessionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update brainstorm session engine: {}", sessionId, e);
        }
    }

    /**
     * Deletes a session with all its messages and drafts, immediately and permanently.
     *
     * @param sessionId the session id
     * @return true if the session was deleted
     */
    public boolean deleteSession(String sessionId) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            for (String table : new String[] {"brainstorm_messages", "brainstorm_drafts"}) {
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM " + table + " WHERE session_id = ?")) {
                    stmt.setString(1, sessionId);
                    stmt.executeUpdate();
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM brainstorm_sessions WHERE id = ?")) {
                stmt.setString(1, sessionId);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            logger.error("Failed to delete brainstorm session: {}", sessionId, e);
            return false;
        }
    }

    /**
     * Appends a message to a session and marks the session as changed now.
     *
     * @param sessionId the session id
     * @param message the message to store
     */
    public void addMessage(String sessionId, BrainstormMessage message) {
        String insert = "INSERT INTO brainstorm_messages (session_id, role, content, created_at) VALUES (?, ?, ?, ?)";
        String touch = "UPDATE brainstorm_sessions SET updated_at = ? WHERE id = ?";
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insert);
             PreparedStatement touchStmt = conn.prepareStatement(touch)) {
            stmt.setString(1, sessionId);
            stmt.setString(2, message.getRole());
            stmt.setString(3, message.getContent());
            stmt.setTimestamp(4, now);
            stmt.executeUpdate();

            touchStmt.setTimestamp(1, now);
            touchStmt.setString(2, sessionId);
            touchStmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to store brainstorm message for session: {}", sessionId, e);
        }
    }

    /**
     * Returns all messages of a session in chronological order.
     *
     * @param sessionId the session id
     * @return the messages, empty on failure
     */
    public List<BrainstormMessage> findMessages(String sessionId) {
        String sql = "SELECT role, content FROM brainstorm_messages WHERE session_id = ? ORDER BY id";
        List<BrainstormMessage> messages = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(new BrainstormMessage(rs.getString("role"), rs.getString("content")));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load brainstorm messages for session: {}", sessionId, e);
        }
        return messages;
    }

    /**
     * Stores a draft as the next version of a session.
     *
     * @param sessionId the session id
     * @param draft the draft to store
     * @return the version number of the stored draft, or 0 on failure
     */
    public int addDraft(String sessionId, BrainstormDraft draft) {
        String sql = """
            INSERT INTO brainstorm_drafts (session_id, version, draft_json, created_at)
            VALUES (?, (SELECT COALESCE(MAX(version), 0) + 1 FROM brainstorm_drafts WHERE session_id = ?), ?, ?)
            """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            stmt.setString(2, sessionId);
            stmt.setString(3, draft.toJson().toString());
            stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to store brainstorm draft for session: {}", sessionId, e);
            return 0;
        }
        return countDrafts(sessionId);
    }

    /**
     * Returns the number of draft versions of a session.
     *
     * @param sessionId the session id
     * @return the number of versions, 0 on failure
     */
    public int countDrafts(String sessionId) {
        String sql = "SELECT COUNT(*) FROM brainstorm_drafts WHERE session_id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            logger.error("Failed to count brainstorm drafts for session: {}", sessionId, e);
            return 0;
        }
    }

    /**
     * Returns the latest draft of a session.
     *
     * @param sessionId the session id
     * @return the latest draft, or empty if the session has none
     */
    public Optional<BrainstormDraft> findLatestDraft(String sessionId) {
        String sql = "SELECT draft_json FROM brainstorm_drafts WHERE session_id = ? ORDER BY version DESC LIMIT 1";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(BrainstormDraft.fromJson(new JSONObject(rs.getString("draft_json"))));
                }
            }
        } catch (SQLException | JSONException e) {
            logger.error("Failed to load latest brainstorm draft for session: {}", sessionId, e);
        }
        return Optional.empty();
    }
}
