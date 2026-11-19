package art.moodframe.model;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * An image of the library the user attaches to an order again and again, e.g. a logo, a word mark or a product photo,
 * saved once instead of uploaded anew. Its description tells what it shows and when it fits; it is shown in the
 * choice and the management of the saved images and does not go into the prompt.
 *
 * @param id          the id
 * @param name        the name, unique among the saved images in use
 * @param file        the name of its file in the folder of the saved images, see
 *                    {@code config.LibraryPaths#savedImages()}
 * @param width       the width in pixels
 * @param height      the height in pixels
 * @param sha256      the SHA-256 of the file, which finds the same file again
 * @param description what it shows and when it fits, null for none
 * @param favorite    whether it is shown first in the choice
 * @param createdAt   when it was saved
 * @param deletedAt   when it went to the trash, null while in use
 * @since 2.1.4
 */
public record SavedImage(String id, String name, String file, int width, int height, String sha256,
        String description, boolean favorite, LocalDateTime createdAt, LocalDateTime deletedAt) {

    /**
     * Names the format of the file by its extension, for example {@code PNG}.
     *
     * @return the format in capitals, {@code JPEG} for {@code .jpg}
     */
    public String format() {
        int dot = file.lastIndexOf('.');
        String extension = dot >= 0 ? file.substring(dot + 1).toUpperCase(Locale.ROOT) : "";
        return extension.equals("JPG") ? "JPEG" : extension;
    }

    /**
     * Names the size and the format, for example {@code 512 × 512 · PNG}.
     *
     * @return the size in pixels and the format
     */
    public String describe() {
        return width + " × " + height + " · " + format();
    }
}
