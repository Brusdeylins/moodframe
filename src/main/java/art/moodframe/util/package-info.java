/**
 * General helpers used by all layers: internationalization, themes, images and videos, opening files and web pages,
 * listener cleanup.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link I18nManager}: localized texts and the change of the language, with {@link I18nChangeListener}</li>
 * <li>{@link ThemeManager}: the AtlantaFX themes, with {@link SystemThemeDetector}</li>
 * <li>{@link ImageEncoder}: resizing and Base64 encoding of attached images</li>
 * <li>{@link PaletteExtractor}: the main colors of an image by median cut</li>
 * <li>{@link SystemOpen}: opens files and web pages in the default application</li>
 * <li>{@link Secrets}: keeps keys and tokens out of logs and messages</li>
 * <li>{@link VersionManager}: the version of the application</li>
 * <li>{@link Subscription}: removable listeners, collected in {@link CompositeDisposable}</li>
 * <li>{@link VideoPoster}: poster images of videos, with {@link MediaSupport} and {@link VideoRemedy}</li>
 * </ul>
 */
package art.moodframe.util;
