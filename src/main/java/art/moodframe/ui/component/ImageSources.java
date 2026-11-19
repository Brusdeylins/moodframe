package art.moodframe.ui.component;

import art.moodframe.service.chat.ImageSizeCheck;
import art.moodframe.service.chat.ThumbnailManager;

/**
 * Where the tiles of the chat entries and the images view get their pictures: the thumbnail files and the check of
 * the size of an image on its first display. The coordinator of the main window hands them to its parts.
 *
 * @param thumbnails finds and creates the thumbnail files (may be null: the original files, scaled down)
 * @param sizeCheck  corrects a missing or wrong size from the file (may be null: no check)
 */
public record ImageSources(ThumbnailManager thumbnails, ImageSizeCheck sizeCheck) {

    /** No thumbnails and no check, e.g. in a test. */
    public static final ImageSources NONE = new ImageSources(null, null);
}
