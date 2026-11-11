package art.moodframe.model;

/**
 * A single message of a brainstorm session.
 *
 * <p>The role is {@link #ROLE_USER}, {@link #ROLE_ASSISTANT} or {@link #ROLE_EVENT}
 * (an app event such as a changed target model, sent to the AI as a user turn).</p>
 *
 * @since 1.7.0
 */
public class BrainstormMessage {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_EVENT = "event";

    private final String role;
    private final String content;

    /**
     * Creates a message.
     *
     * @param role one of the ROLE constants
     * @param content message text (for the assistant: the raw JSON reply)
     */
    public BrainstormMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }
}
