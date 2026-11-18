/**
 * Helpers of the user interface that need JavaFX or the desktop: themes, videos, opening files and web pages,
 * listener cleanup, loading on a worker.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link ThemeManager}: the AtlantaFX themes, with {@link SystemThemeDetector}</li>
 * <li>{@link VideoPoster}: poster images of videos, with {@link MediaSupport} and {@link VideoRemedy}</li>
 * <li>{@link SystemOpen}: opens files and web pages in the default application</li>
 * <li>{@link Subscription}: removable listeners, collected in {@link CompositeDisposable}</li>
 * <li>{@link CoalescedLoad}: loads data for the JavaFX thread on a worker, one query per burst of requests</li>
 * </ul>
 */
package art.moodframe.ui.support;
