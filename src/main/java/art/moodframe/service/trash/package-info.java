/**
 * The central trash: the groups of deletable items register here, the trash window and the automatic cleanup act
 * through it. The groups themselves live with their items in {@code library} and {@code chat}.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link TrashCenter}: the central trash with one {@link TrashGroup} per kind of item</li>
 * <li>{@link TrashCleanupService}: removes for good what has been in the trash longer than the retention period</li>
 * <li>{@link TrashManager}: the trash directory of the image files</li>
 * </ul>
 */
package art.moodframe.service.trash;
