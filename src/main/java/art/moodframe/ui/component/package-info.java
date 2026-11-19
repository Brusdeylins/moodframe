/**
 * Reusable JavaFX controls and composite elements of the main window and the dialogs. The chat entries and their
 * images live in {@code ui.entry}.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link MultiImageAttachmentPanel}: the attached reference images, each in an {@link ImageSlot}, and
 *     {@link SavedImageChoice}, the choice of the saved images over them</li>
 * <li>{@link FavoriteStar}: the favorite star of a row</li>
 * <li>{@link DialogButtons}: the standard dialog buttons in the language of the app</li>
 * <li>{@link MoodChoice}: an entry of the mood selection; likewise {@link FrameChoice} and {@link BlockChoice}</li>
 * <li>{@link CameraPreview}: the camera preview, with {@link CameraExample}</li>
 * <li>{@link MemoryStatusComponent}: the memory display in the status bar</li>
 * <li>{@link ManagedComponent}: the base of a composite that lets go of its listeners when disposed</li>
 * </ul>
 */
package art.moodframe.ui.component;
