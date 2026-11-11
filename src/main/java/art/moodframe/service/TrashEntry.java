package art.moodframe.service;

import java.time.LocalDateTime;

/**
 * One deleted item as the central trash shows it.
 *
 * @param id        the ID of the item within its group
 * @param name      the name of the item
 * @param deletedAt when the item was moved to the trash
 * @param context   what the row tells besides the name, in the language of the user
 * @since 2.0.0
 */
public record TrashEntry(String id, String name, LocalDateTime deletedAt, String context) {
}
