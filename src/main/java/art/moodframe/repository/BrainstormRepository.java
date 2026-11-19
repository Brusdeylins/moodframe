package art.moodframe.repository;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.BrainstormMessage;
import art.moodframe.model.BrainstormSession;

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
 * <p>Deleting a session removes its messages and drafts through {@code ON DELETE CASCADE}; a message or draft of
 * a session that does not exist is refused.</p>
 *
 * <p>A database error is thrown as {@link SQLException}, the caller decides what follows.</p>
 *
 * @since 1.7.0
 */
public class BrainstormRepository {

    private final DatabaseManager database;

    /**
     * Creates the repository.
     *
     * @param database the database of the brainstorm sessions
     */
    public BrainstormRepository(DatabaseManager database) {
        this.database = database;
    }

    /**
     * Creates and stores a new empty session.
     *
     * @param title initial title of the session
     * @return the created session
     * @throws SQLException if the database fails
     */
    public BrainstormSession createSession(String title) throws SQLException {
        return createSession(title, null);
    }

    /**
     * Creates and stores a new empty session that is answered by the given language model, on top of the list.
     *
     * @param title initial title of the session
     * @param engine "gemini" or "openai", null to follow the choice of the window
     * @return the created session
     * @throws SQLException if the database fails
     * @since 1.9.2
     */
    public BrainstormSession createSession(String title, String engine) throws SQLException {
        LocalDateTime now = LocalDateTime.now();
        BrainstormSession session = new BrainstormSession(UUID.randomUUID().toString(), title, now, now);
        session.setEngine(engine);
        String sql = """
            INSERT INTO brainstorm_sessions (id, title, created_at, updated_at, engine, sort_order)
            VALUES (?, ?, ?, ?, ?, (SELECT COALESCE(MIN(sort_order), 1) - 1 FROM brainstorm_sessions))
            """;

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, session.getId());
            stmt.setString(2, title);
            stmt.setTimestamp(3, Timestamp.valueOf(now));
            stmt.setTimestamp(4, Timestamp.valueOf(now));
            stmt.setString(5, engine);
            stmt.executeUpdate();
            return session;
        }
    }

    /**
     * Returns all sessions in the order the user gave them, a new one first.
     *
     * @return the sessions
     * @throws SQLException if the database fails
     */
    public List<BrainstormSession> findAllSessions() throws SQLException {
        String sql = "SELECT id, title, created_at, updated_at, engine FROM brainstorm_sessions "
                + "ORDER BY sort_order, updated_at DESC";
        List<BrainstormSession> sessions = new ArrayList<>();

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                BrainstormSession session = new BrainstormSession(rs.getString("id"), rs.getString("title"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime());
                session.setEngine(rs.getString("engine"));
                sessions.add(session);
            }
        }
        return sessions;
    }

    /**
     * Stores the order of the sessions in one transaction: numbers them from 0 on, as they follow in the list.
     *
     * @param sessions all sessions in their new order
     * @throws SQLException if the database fails
     * @since 2.0.0
     */
    public void updateSortOrders(List<BrainstormSession> sessions) throws SQLException {
        String sql = "UPDATE brainstorm_sessions SET sort_order = ? WHERE id = ?";

        database.inTransaction((Connection conn) -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < sessions.size(); i++) {
                    stmt.setInt(1, i);
                    stmt.setString(2, sessions.get(i).getId());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
            return null;
        });
    }

    /**
     * Updates the title of a session and marks it as changed now.
     *
     * @param sessionId the session id
     * @param title the new title
     * @throws SQLException if the database fails
     */
    public void updateTitle(String sessionId, String title) throws SQLException {
        String sql = "UPDATE brainstorm_sessions SET title = ?, updated_at = ? WHERE id = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, title);
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(3, sessionId);
            stmt.executeUpdate();
        }
    }

    /**
     * Replaces the title of a session only if it is still the expected one, and marks it as changed now; a title
     * the user changed meanwhile stays.
     *
     * @param sessionId the session id
     * @param expected  the title the session must still have
     * @param title     the new title
     * @return true if the title was replaced
     * @throws SQLException if the database fails
     */
    public boolean replaceTitle(String sessionId, String expected, String title) throws SQLException {
        String sql = "UPDATE brainstorm_sessions SET title = ?, updated_at = ? WHERE id = ? AND title = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, title);
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(3, sessionId);
            stmt.setString(4, expected);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Stores which language model answers a session (does not count as a change of the session).
     *
     * @param sessionId the session id
     * @param engine "gemini" or "openai"
     * @throws SQLException if the database fails
     * @since 1.9.2
     */
    public void updateEngine(String sessionId, String engine) throws SQLException {
        String sql = "UPDATE brainstorm_sessions SET engine = ? WHERE id = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, engine);
            stmt.setString(2, sessionId);
            stmt.executeUpdate();
        }
    }

    /**
     * Deletes a session with all its messages and drafts (cascade), immediately and permanently.
     *
     * @param sessionId the session id
     * @return true if the session was deleted
     * @throws SQLException if the database fails
     */
    public boolean deleteSession(String sessionId) throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM brainstorm_sessions WHERE id = ?")) {
            stmt.setString(1, sessionId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Appends a message to a session and marks the session as changed now, in one transaction.
     *
     * @param sessionId the session id
     * @param message the message to store
     * @throws SQLException if the database fails; then neither is stored
     */
    public void addMessage(String sessionId, BrainstormMessage message) throws SQLException {
        String insert = "INSERT INTO brainstorm_messages (session_id, role, content, created_at) VALUES (?, ?, ?, ?)";
        String touch = "UPDATE brainstorm_sessions SET updated_at = ? WHERE id = ?";
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        database.inTransaction((Connection conn) -> {
            try (PreparedStatement stmt = conn.prepareStatement(insert);
                 PreparedStatement touchStmt = conn.prepareStatement(touch)) {
                stmt.setString(1, sessionId);
                stmt.setString(2, message.getRole());
                stmt.setString(3, message.getContent());
                stmt.setTimestamp(4, now);
                stmt.executeUpdate();

                touchStmt.setTimestamp(1, now);
                touchStmt.setString(2, sessionId);
                touchStmt.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Returns all messages of a session in chronological order.
     *
     * @param sessionId the session id
     * @return the messages
     * @throws SQLException if the database fails
     */
    public List<BrainstormMessage> findMessages(String sessionId) throws SQLException {
        String sql = "SELECT role, content FROM brainstorm_messages WHERE session_id = ? ORDER BY id";
        List<BrainstormMessage> messages = new ArrayList<>();

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(new BrainstormMessage(rs.getString("role"), rs.getString("content")));
                }
            }
        }
        return messages;
    }

    /**
     * Stores a draft as the next version of a session.
     *
     * @param sessionId the session id
     * @param draftJson the JSON form of the draft
     * @return the version number of the stored draft
     * @throws SQLException if the database fails
     */
    public int addDraft(String sessionId, String draftJson) throws SQLException {
        String sql = """
            INSERT INTO brainstorm_drafts (session_id, version, draft_json, created_at)
            VALUES (?, (SELECT COALESCE(MAX(version), 0) + 1 FROM brainstorm_drafts WHERE session_id = ?), ?, ?)
            """;

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            stmt.setString(2, sessionId);
            stmt.setString(3, draftJson);
            stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            stmt.executeUpdate();
        }
        return countDrafts(sessionId);
    }

    /**
     * Returns the number of draft versions of a session.
     *
     * @param sessionId the session id
     * @return the number of versions
     * @throws SQLException if the database fails
     */
    public int countDrafts(String sessionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM brainstorm_drafts WHERE session_id = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Returns the JSON form of the latest draft of a session.
     *
     * @param sessionId the session id
     * @return the JSON form of the latest draft, or empty if the session has none
     * @throws SQLException if the database fails
     */
    public Optional<String> findLatestDraft(String sessionId) throws SQLException {
        String sql = "SELECT draft_json FROM brainstorm_drafts WHERE session_id = ? ORDER BY version DESC LIMIT 1";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString("draft_json")) : Optional.empty();
            }
        }
    }
}
