/**
 * The chat entries and their images as the chat area of the main window shows them: the entry cards, the images
 * view and the full view of an image or video. Only the controllers use this package; it uses the generic controls
 * of {@code ui.component}, the drag and drop of {@code ui.dnd} and {@code ui.support}.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link ChatEntryCard}: one chat entry with its prompt, settings and images, the pictures of its tiles from
 * {@link EntryTilePictures} and its context menus from {@link EntryMenus}; the thumbnails come as
 * {@link ImageSources}</li>
 * <li>{@link ImageGrid}: the images of a chat or a group in a grid</li>
 * <li>{@link ImagePreviewOverlay}: the large preview of an image or video, which plays a video with
 * {@link VideoPlayback}</li>
 * <li>{@link ImageActionBar}, {@link ImageSizeLabel}, {@link EntryBadge}: the action bar, the size line and the
 * badges the cards and the images view share</li>
 * </ul>
 */
package art.moodframe.ui.entry;
