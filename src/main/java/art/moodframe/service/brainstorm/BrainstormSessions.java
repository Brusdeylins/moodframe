package art.moodframe.service.brainstorm;

import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.BrainstormMessage;
import art.moodframe.model.BrainstormSession;
import art.moodframe.repository.BrainstormRepository;
import art.moodframe.service.library.RepositoryAccess;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The stored brainstorm sessions with their messages and drafts. A database error is logged and answered with
 * nothing (false, empty), so the window stays usable; it runs on the calling thread, never the JavaFX thread.
 *
 * @since 2.1.3
 */
public class BrainstormSessions {

    private static final Logger logger = LoggerFactory.getLogger(BrainstormSessions.class);

    /** The start of a stored app event, which the assistant reads as a user turn. */
    static final String EVENT_PREFIX = "[APP EVENT] ";

    private final BrainstormRepository repository;
    private final RepositoryAccess access = new RepositoryAccess(logger);

    /**
     * Creates the store.
     *
     * @param repository the session storage
     */
    public BrainstormSessions(BrainstormRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a new empty session.
     *
     * @param title  the title
     * @param engine "gemini" or "openai", null to follow the choice of the window
     * @return the session, empty if it could not be stored
     */
    Optional<BrainstormSession> create(String title, String engine) {
        return access.call(() -> Optional.of(repository.createSession(title, engine)), Optional.empty(),
                "Failed to create brainstorm session");
    }

    List<BrainstormSession> list() {
        return access.call(repository::findAllSessions, new ArrayList<>(), "Failed to load brainstorm sessions");
    }

    void storeOrder(List<BrainstormSession> order) {
        access.run(() -> repository.updateSortOrders(order), "Failed to store the order of the brainstorm sessions");
    }

    void updateTitle(String sessionId, String title) {
        access.run(() -> repository.updateTitle(sessionId, title),
                "Failed to update brainstorm session title: " + sessionId);
    }

    boolean replaceTitle(String sessionId, String expected, String title) {
        return access.call(() -> repository.replaceTitle(sessionId, expected, title), false,
                "Failed to update brainstorm session title: " + sessionId);
    }

    void updateEngine(String sessionId, String engine) {
        access.run(() -> repository.updateEngine(sessionId, engine),
                "Failed to update brainstorm session engine: " + sessionId);
    }

    boolean delete(String sessionId) {
        return access.call(() -> repository.deleteSession(sessionId), false,
                "Failed to delete brainstorm session: " + sessionId);
    }

    void addMessage(String sessionId, BrainstormMessage message) {
        access.run(() -> repository.addMessage(sessionId, message),
                "Failed to store brainstorm message for session: " + sessionId);
    }

    List<BrainstormMessage> messages(String sessionId) {
        return access.call(() -> repository.findMessages(sessionId), new ArrayList<>(),
                "Failed to load brainstorm messages for session: " + sessionId);
    }

    void addDraft(String sessionId, BrainstormDraft draft) {
        String json = BrainstormJson.toJson(draft).toString();
        access.run(() -> repository.addDraft(sessionId, json),
                "Failed to store brainstorm draft for session: " + sessionId);
    }

    Optional<BrainstormDraft> latestDraft(String sessionId) {
        Optional<String> json = access.call(() -> repository.findLatestDraft(sessionId), Optional.empty(),
                "Failed to load latest brainstorm draft for session: " + sessionId);
        try {
            return json.map((String text) -> BrainstormJson.draft(new JSONObject(text)));
        }
        catch (JSONException e) {
            logger.error("Unreadable latest brainstorm draft for session: {}", sessionId, e);
            return Optional.empty();
        }
    }
}
