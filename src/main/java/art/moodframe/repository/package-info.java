/**
 * Data access with plain JDBC on SQLite, one repository per aggregate. Deleting usually sets {@code deleted_at}, so
 * the central trash can restore an item; the finders skip deleted rows.
 *
 * <p>Error contract: a repository never swallows a database error but throws it as {@link java.sql.SQLException}, so
 * "not found" (null, false, empty) stays apart from "the database failed". The calling service decides what follows,
 * usually it logs the error and answers with a fallback.</p>
 *
 * <p>Transactions: a write that depends on what it reads runs in
 * {@link art.moodframe.database.DatabaseManager#inTransaction}, which begins IMMEDIATE, reads inside the transaction
 * and guards its updates ({@code AND deleted_at IS NULL}, checked row counts), so a deletion from the main window and
 * one from an agent at the same time book once. Files are moved or removed after the commit.</p>
 *
 * <p>Time columns: {@code created_at}, {@code deleted_at} and {@code chats.updated_at} are written as epoch
 * milliseconds with {@code setTimestamp(Timestamp.valueOf(LocalDateTime.now()))}, i.e. the local time of the writer
 * as an instant, and read with {@code getTimestamp}; SQL does not write them with {@code CURRENT_TIMESTAMP}, which
 * is UTC text. Chats, entries and images put in the trash before 2.1.3 hold deleted_at as text, which
 * {@code ChatRepository} still reads correctly. The {@code updated_at} of moods, frames, blocks and palettes is set
 * by SQL and read nowhere.</p>
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
