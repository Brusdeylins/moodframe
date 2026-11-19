package art.moodframe.service.trash;

import java.util.List;

/**
 * One group of the central trash: it lists what is deleted and takes it out again, back into use or for good.
 * Every kind of deletable item brings its own group and registers it at the {@link TrashCenter}.
 *
 * @since 2.0.0
 */
public interface TrashGroup {

    /** The groups of the trash, in the order of their tabs. */
    enum Kind {
        CHATS, IMAGES, BLOCKS, MOODS, FRAMES
    }

    /**
     * Tells which items this group holds.
     *
     * @return the kind of the group
     */
    Kind kind();

    /**
     * Lists the deleted items.
     *
     * @return the items in the trash, the latest deletion first
     */
    List<TrashEntry> list();

    /**
     * Takes an item out of the trash, back into use.
     *
     * @param id the ID of the item, or of one of its parts
     * @return true if the item is restored
     */
    boolean restore(String id);

    /**
     * Removes an item in the trash for good. An item no longer in the trash, e.g. restored meanwhile by the main
     * window or an agent, stays.
     *
     * @param id the ID of the item, or of one of its parts
     * @return true if the item was in the trash and is gone
     */
    boolean deletePermanently(String id);
}
