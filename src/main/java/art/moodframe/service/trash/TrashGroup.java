package art.moodframe.service.trash;

import art.moodframe.util.I18nManager;

import java.util.List;
import java.util.Locale;

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
     * Lists the deleted items, with their context in the language of the user interface.
     *
     * @return the items in the trash, the latest deletion first
     */
    default List<TrashEntry> list() {
        return list(null);
    }

    /**
     * Lists the deleted items.
     *
     * @param locale the language of the context, e.g. English for the MCP clients, with the numbers of
     *               {@link Locale#ROOT}; null for the language of the user interface
     * @return the items in the trash, the latest deletion first
     */
    List<TrashEntry> list(Locale locale);

    /**
     * Gets a text of the context of an item, see {@link #list(Locale)}.
     *
     * @param locale    the language, null for that of the user interface
     * @param key       the key of the text
     * @param arguments the arguments of the text
     * @return the text
     */
    static String text(Locale locale, String key, Object... arguments) {
        return locale != null ? I18nManager.getTextIn(locale, key, arguments) : I18nManager.getText(key, arguments);
    }

    /**
     * Formats a cost in US dollars for the context of an item, see {@link #list(Locale)}.
     *
     * @param locale the language, null for that of the user interface
     * @param cost   the cost
     * @return the cost with two decimals
     */
    static String cost(Locale locale, double cost) {
        return locale != null ? String.format(Locale.ROOT, "%.2f", cost) : String.format("%.2f", cost);
    }

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
