/**
 * General helpers used by all layers, free of JavaFX: internationalization, images, secrets, the version, the
 * downloads folder.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link I18nManager}: localized texts and the change of the language, with {@link I18nChangeListener}</li>
 * <li>{@link ImageEncoder}: resizing and Base64 encoding of attached images</li>
 * <li>{@link ImageSize}: the pixel size of an image file, read from its header</li>
 * <li>{@link Secrets}: keeps keys and tokens out of logs and messages</li>
 * <li>{@link VersionManager}: the version of the application</li>
 * <li>{@link DownloadsFolder}: copies a generated image or video into the Downloads folder</li>
 * </ul>
 */
package art.moodframe.util;
