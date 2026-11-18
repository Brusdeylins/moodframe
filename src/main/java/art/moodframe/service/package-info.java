/**
 * The application logic between the UI (and the MCP server) and the repositories, split by topic into subpackages:
 * {@code provider} (the clients of the image and video providers), {@code generation} (carrying out an order with a
 * provider), {@code prompt} (the prompt text), {@code library} (moods, frames, building blocks, palettes,
 * favorites), {@code chat} (the chats with their images and files), {@code trash} (the central trash) and
 * {@code brainstorm} (the guided brainstorm chat). A subpackage depends only on those listed before it in
 * {@code docs/ARCHITECTURE.md}, never in a cycle.
 *
 * <p>Main classes here:</p>
 * <ul>
 * <li>{@link ConfigurationExporter}: exports and imports the settings and the library of the user</li>
 * <li>{@link UpdateCheckService}: finds a newer version of MoodFrame on the update site</li>
 * </ul>
 */
package art.moodframe.service;
