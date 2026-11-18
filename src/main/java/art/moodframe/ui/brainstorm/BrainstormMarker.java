package art.moodframe.ui.brainstorm;

/**
 * What the brainstorm history list shows next to a chat.
 *
 * @since 1.8.4
 */
enum BrainstormMarker {
    /** Nothing to report. */
    IDLE,
    /** The AI is working on a request of this chat. */
    RUNNING,
    /** An answer arrived while another chat was open and has not been seen yet. */
    UNREAD,
    /** A request failed while another chat was open. */
    FAILED
}
