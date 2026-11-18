/**
 * Data access with plain JDBC on SQLite, one repository per aggregate. Deleting usually sets {@code deleted_at}, so
 * the central trash can restore an item; the finders skip deleted rows.
 *
 * <p>Error contract: a repository never swallows a database error but throws it as {@link java.sql.SQLException}, so
 * "not found" (null, false, empty) stays apart from "the database failed". The calling service decides what follows,
 * usually it logs the error and answers with a fallback.</p>
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link ChatRepository}: chats with their entries and generated images</li>
 * <li>{@link MoodRepository}: the moods of the user</li>
 * <li>{@link FrameRepository}: the frames of the user</li>
 * <li>{@link PromptBlockRepository}: the building blocks of the user (style, atmosphere, light, camera)</li>
 * <li>{@link ColorStyleRepository}: the color palettes of the user</li>
 * <li>{@link FavoriteRepository}: the favorites per kind of entry</li>
 * <li>{@link BrainstormRepository}: brainstorm sessions with their messages and drafts</li>
 * <li>{@link ChatFiles}: what happens to the files of a deleted chat, implemented in the service layer</li>
 * </ul>
 */
package art.moodframe.repository;
