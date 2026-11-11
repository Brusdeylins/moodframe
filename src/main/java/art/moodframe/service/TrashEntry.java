package art.moodframe.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One deleted item as the central trash shows it.
 *
 * @param id        the ID of the item within its group
 * @param name      the name of the item
 * @param deletedAt when the item was moved to the trash; for an item with parts when its first part was
 * @param context   what the row tells besides the name, in the language of the user
 * @param parts     what of the item leaves the trash on its own, like the images of an entry; empty for none
 * @since 2.0.0
 */
public record TrashEntry(String id, String name, LocalDateTime deletedAt, String context, List<Part> parts) {

    /**
     * A part of an item that is restored or deleted on its own: the group of the item takes its ID as well.
     *
     * @param id        the ID of the part within the group of the item
     * @param deletedAt when the part was moved to the trash
     * @param preview   the file of a preview image, null for none
     */
    public record Part(String id, LocalDateTime deletedAt, String preview) {
    }

    /**
     * Creates an item without parts.
     *
     * @param id        the ID of the item within its group
     * @param name      the name of the item
     * @param deletedAt when the item was moved to the trash
     * @param context   what the row tells besides the name, in the language of the user
     */
    public TrashEntry(String id, String name, LocalDateTime deletedAt, String context) {
        this(id, name, deletedAt, context, List.of());
    }
}
