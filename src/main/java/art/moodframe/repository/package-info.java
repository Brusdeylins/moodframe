/**
 * Data access with plain JDBC on SQLite, one repository per aggregate. Deleting usually sets {@code deleted_at}, so
 * the central trash can restore an item. The finders of lists skip the rows in the trash unless they are named for it
 * ({@code findDeleted}, {@code findDeletedChats}); so do the finders of one mood, frame, block or palette, which have
 * {@code findInTrash} or {@code exists} beside them. The finders of one chat, entry or image by id answer a row in the
 * trash as well, as the trash looks them up by id. The Javadoc of each finder says which rows it answers.
 *
 * <p>Error contract: a repository never swallows a database error but throws it as {@link java.sql.SQLException}, so
 * "not found" (an empty {@link java.util.Optional}, false, an empty list) stays apart from "the database failed". A
 * finder of one row answers with an {@code Optional}. The calling service decides what follows, usually it logs the
 * error and answers with a fallback.</p>
 *
 * <p>Transactions: a write that depends on what it reads runs in
 * {@link art.moodframe.database.DatabaseManager#inTransaction}, which begins IMMEDIATE, reads inside the transaction
 * and guards its updates ({@code AND deleted_at IS NULL}, checked row counts), so a deletion from the main window and
 * one from an agent at the same time book once. A removal for good removes only rows still in the trash
 * ({@code AND deleted_at IS NOT NULL}), so an item restored meanwhile survives; the one exception is
 * {@link PromptBlockRepository#deleteAll} of the reset of the settings, which removes the blocks of a type in use
 * and in the trash and the references of the moods to them in one transaction. Files are moved or removed after the
 * commit, by {@link ChatTrashRepository} alone, with the images whose files follow the rows its transaction changed;
 * it also removes the file of an image a generation delivered after its entry or chat went to the trash, which
 * {@link ChatRepository} did not save ({@link ChatTrashRepository#dropLateImage}).</p>
 *
 * <p>Time columns: {@code created_at} and {@code deleted_at} of the chats, entries and images, the {@code updated_at}
 * of the chats, the {@code completed_at} of the entries and the {@code downloaded_at} of the images,
 * {@code created_at} and {@code updated_at} of the brainstorm sessions (which have no trash and are deleted for good)
 * and the {@code deleted_at} of moods, frames, blocks and palettes are written as epoch milliseconds with
 * {@code setTimestamp(Timestamp.valueOf(LocalDateTime.now()))}, i.e. the local time of the writer as an instant, and
 * read with {@code getTimestamp}. Chats, entries and images put in the trash before 2.1.3 hold deleted_at as text,
 * which {@code ChatRepository} still reads; a value it cannot read counts as deleted now. The {@code created_at} and
 * {@code updated_at} of moods, frames, blocks and palettes are still SQLite's {@code CURRENT_TIMESTAMP} (UTC text, the
 * column default and the updates) and read nowhere.</p>
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link ChatRepository}: chats with their entries and generated images, and the mapping of their rows</li>
 * <li>{@link ChatTrashRepository}: the trash of chats, entries and images, the deleted costs and their files</li>
 * <li>{@link ChatGroupQueries}: the entries of the active chats grouped by mood and by frame</li>
 * <li>{@link MoodRepository}: the moods of the user</li>
 * <li>{@link FrameRepository}: the frames of the user</li>
 * <li>{@link PromptBlockRepository}: the building blocks of the user (style, atmosphere, light, camera)</li>
 * <li>{@link ColorStyleRepository}: the color palettes of the user</li>
 * <li>{@link FavoriteRepository}: the favorites per kind of entry</li>
 * <li>{@link BrainstormRepository}: brainstorm sessions with their messages and drafts, deleted for good</li>
 * <li>{@link ChatFiles}: what happens to the files of a deleted chat, entry or image, implemented in the service
 * layer</li>
 * </ul>
 */
package art.moodframe.repository;
