/**
 * MoodFrame, a desktop app that generates images and videos with FLUX (BFL), Gemini and OpenAI from prompts built out
 * of moods, frames and building blocks. See {@code docs/ARCHITECTURE.md} for the layers and their allowed
 * dependencies.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link MoodframeApplication}: the JavaFX application and composition root: the preparation on a startup
 *     thread behind the splash (configuration, database, services, trash cleanup, chats), the main window and the
 *     shutdown</li>
 * </ul>
 */
package art.moodframe;
