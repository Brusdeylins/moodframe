/**
 * Helpers of the user interface that need JavaFX or the desktop: themes, videos, opening files and web pages,
 * listener cleanup, loading on a worker, the decoded images, the reordering of list items, the common inset and the
 * texts shared by several views.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link ThemeManager}: the AtlantaFX themes, with {@link SystemThemeDetector}</li>
 * <li>{@link VideoPoster}: poster images of videos, with {@link MediaSupport} and {@link VideoRemedy}</li>
 * <li>{@link SystemOpen}: opens files and web pages in the default application</li>
 * <li>{@link Subscription}: removable listeners, collected in {@link CompositeDisposable}</li>
 * <li>{@link CoalescedLoad}: loads data for the JavaFX thread on a worker, one query per burst of requests</li>
 * <li>{@link ImageCache}: the decoded JavaFX images of the thumbnails, loaded once per file without a global lock</li>
 * <li>{@link FxI18n}: registers views for the change of the language, a dialog or window until it closes</li>
 * <li>{@link ListReorder}: reorders the items of a list by dragging, in the main window and the brainstorm</li>
 * <li>{@link BoxInset}: the common inset of the boxes of the main window and the brainstorm dialog</li>
 * <li>{@link SafetyTolerance}: the text of a BFL safety tolerance, in the generation panel and on an entry</li>
 * <li>{@link StatusSink}: where a part of the main window reports a status message or an error</li>
 * </ul>
 */
package art.moodframe.ui.support;
