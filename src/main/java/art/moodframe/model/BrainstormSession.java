package art.moodframe.model;

import java.time.LocalDateTime;

/**
 * A saved brainstorm chat session.
 *
 * @since 1.7.0
 */
public class BrainstormSession {

    private final String id;
    private String title;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String engine;

    /**
     * Creates a session.
     *
     * @param id unique session id
     * @param title display title in the history list
     * @param createdAt creation time
     * @param updatedAt time of the last change
     */
    public BrainstormSession(String id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Gets the language model that answers this session.
     *
     * @return "gemini" or "openai", null for an older session that follows the choice of the window
     * @since 1.9.2
     */
    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }
}
