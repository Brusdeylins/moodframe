/**
 * The library of the user apart from the chats: moods, frames, building blocks, palettes and favorites, each with the
 * standard items from code and the items of the user from the database, and their groups of the central trash.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link MoodService}: standard moods from code and the moods of the user; likewise {@link FrameService},
 *     {@link PromptBlockService} and {@link ColorStyleService}</li>
 * <li>{@link FavoriteService}: the favorites among moods, frames and building blocks</li>
 * <li>{@link LibraryEvents}: reports changes of the library to the main window</li>
 * <li>{@link RepositoryAccess}: logs a database error of a repository call and answers a fallback</li>
 * <li>{@link BlockTrashGroup}, {@link MoodTrashGroup}, {@link FrameTrashGroup}: their items in the central trash</li>
 * </ul>
 */
package art.moodframe.service.library;
