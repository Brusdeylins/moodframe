/**
 * The chats with their entries and images: loading and changing them, their files and thumbnails, attached images and
 * their groups of the central trash. The package needs no JavaFX: it changes the models under the {@link ModelLock}
 * on the calling thread and reports the changes through {@code LibraryEvents}.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link ChatLibrary}: the chats with their entries, shared by the main window and the MCP tools</li>
 * <li>{@link ModelLock}: the lock under which the chat models change and are read off the JavaFX thread</li>
 * <li>{@link ChatHistoryService}: loads the chats and settles what a restart left behind</li>
 * <li>{@link ChatFileService}: moves the files of a chat to the trash and back</li>
 * <li>{@link ThumbnailManager}: the thumbnail files of the generated images</li>
 * <li>{@link ImageAttachmentService}: checks and encodes the images attached to a prompt</li>
 * <li>{@link ChatTrashGroup}, {@link ImageTrashGroup}: chats and images in the central trash</li>
 * </ul>
 */
package art.moodframe.service.chat;
