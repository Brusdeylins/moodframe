/**
 * The chats with their entries and images: loading and changing them, their files and thumbnails, attached images and
 * their groups of the central trash.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link ChatLibrary}: the chats with their entries, shared by the main window and the MCP tools</li>
 * <li>{@link ChatHistoryService}: loads the chats and settles what a restart left behind</li>
 * <li>{@link ChatFileService}: moves the files of a chat to the trash and back</li>
 * <li>{@link ThumbnailManager}: thumbnails of the generated images</li>
 * <li>{@link ImageAttachmentService}: checks and encodes the images attached to a prompt</li>
 * <li>{@link ChatTrashGroup}, {@link ImageTrashGroup}: chats and images in the central trash</li>
 * </ul>
 */
package art.moodframe.service.chat;
