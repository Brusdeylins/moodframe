package art.moodframe.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The central trash: one {@link TrashGroup} per kind of deletable item. Everything that leaves the trash goes
 * through here, so the listeners learn about it no matter who asked: the trash window or the automatic cleanup.
 *
 * @since 2.0.0
 */
public class TrashCenter {

    /**
     * Learns what left the trash. It is called on the thread that took the items out.
     */
    public interface Listener {

        /**
         * An item is back in use.
         *
         * @param kind the group of the item
         * @param id   the ID of the item
         */
        void restored(TrashGroup.Kind kind, String id);

        /**
         * Items are gone for good.
         *
         * @param kind the group of the items
         * @param ids  the IDs of the items
         */
        void deleted(TrashGroup.Kind kind, List<String> ids);
    }

    private final Map<TrashGroup.Kind, TrashGroup> groups = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Docks a group to the trash. It replaces an earlier group of the same kind.
     *
     * @param group the group
     */
    public void register(TrashGroup group) {
        groups.put(group.kind(), group);
    }

    /**
     * Adds a listener for what leaves the trash.
     *
     * @param listener the listener
     */
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    /**
     * Lists the deleted items of a kind.
     *
     * @param kind the group
     * @return the items in the trash, the latest deletion first; empty as long as no group of this kind is docked
     */
    public List<TrashEntry> list(TrashGroup.Kind kind) {
        TrashGroup group = groups.get(kind);
        return group != null ? group.list() : List.of();
    }

    /**
     * Takes an item out of the trash, back into use.
     *
     * @param kind the group of the item
     * @param id   the ID of the item
     * @return true if the item is restored
     */
    public boolean restore(TrashGroup.Kind kind, String id) {
        TrashGroup group = groups.get(kind);
        boolean restored = group != null && group.restore(id);
        if (restored)
            listeners.forEach((listener) -> listener.restored(kind, id));
        return restored;
    }

    /**
     * Removes items for good.
     *
     * @param kind the group of the items
     * @param ids  the IDs of the items
     * @return the IDs of the items that are gone
     */
    public List<String> deletePermanently(TrashGroup.Kind kind, List<String> ids) {
        TrashGroup group = groups.get(kind);
        List<String> deleted = group != null ? ids.stream().filter(group::deletePermanently).toList() : List.of();
        if (!deleted.isEmpty())
            listeners.forEach((listener) -> listener.deleted(kind, deleted));
        return deleted;
    }
}
