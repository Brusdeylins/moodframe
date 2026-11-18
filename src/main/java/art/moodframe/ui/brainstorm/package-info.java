/**
 * The brainstorm assistant: its window with the chats, the history and the result fields, as one feature slice. Only
 * {@link BrainstormDialog} is public; the main window opens it.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link BrainstormDialog}: the brainstorm window</li>
 * <li>{@link BrainstormChat}: the state of one chat of the window, with its {@link BrainstormMarker}</li>
 * <li>{@link BrainstormSessionListCell}: a session of the brainstorm history</li>
 * <li>{@link BrainstormMarkdown}: the light Markdown of the AI answers</li>
 * <li>{@link BrainstormVideoCard}: a YouTube video linked in a message</li>
 * </ul>
 */
package art.moodframe.ui.brainstorm;
