/**
 * The chat entries and their images as the chat area of the main window shows them: the entry cards, the images
 * view and the full view of an image or video, and the drag of an image out of the application. Only the controllers
 * use this package; it uses the generic controls of {@code ui.component} and {@code ui.support}.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link ChatEntryCard}: one chat entry with its prompt, settings and images, the pictures of its tiles from
 * {@link EntryTilePictures} and its context menus from {@link EntryMenus}; the thumbnails come as
 * {@link ImageSources}</li>
 * <li>{@link ImageGrid}: the images of a chat or a group in a grid</li>
 * <li>{@link ImagePreviewOverlay}: the large preview of an image or video, which plays a video with
 * {@link VideoPlayback} and keeps its zoom and pan in {@link ZoomLevel}</li>
 * <li>{@link ImageActionBar}, {@link ImageSizeLabel}, {@link EntryBadge}: the action bar, the size line and the
 * badges the cards and the images view share, and the tooltip of an image from {@link ImageDetails}</li>
 * <li>{@link ImageDragExporter}: drags an image of a card or of the preview out to the file manager or another
 * application; images dropped into the application land in the squares of the attachment panel
 * ({@code ui.component.ImageSlot})</li>
 * </ul>
 */
package art.moodframe.ui.entry;
