package art.moodframe.service.library;

import art.moodframe.model.SavedImage;
import art.moodframe.service.trash.TrashEntry;
import art.moodframe.service.trash.TrashGroup;

import java.util.List;
import java.util.Locale;

/**
 * The saved images in the central trash, each with its size, format and description.
 *
 * @since 2.1.4
 */
public class SavedImageTrashGroup implements TrashGroup {

    private final SavedImageService savedImages;

    /**
     * Creates the group.
     *
     * @param savedImages where the saved images are kept
     */
    public SavedImageTrashGroup(SavedImageService savedImages) {
        this.savedImages = savedImages;
    }

    @Override
    public Kind kind() {
        return Kind.SAVED_IMAGES;
    }

    @Override
    public List<TrashEntry> list(Locale locale) {
        return savedImages.getDeleted().stream().map((SavedImage image) -> new TrashEntry(image.id(), image.name(),
                image.deletedAt(), image.description() != null ? image.describe() + " · " + image.description()
                        : image.describe())).toList();
    }

    @Override
    public boolean restore(String id) {
        return savedImages.restore(id);
    }

    @Override
    public boolean deletePermanently(String id) {
        return savedImages.deletePermanently(id);
    }
}
