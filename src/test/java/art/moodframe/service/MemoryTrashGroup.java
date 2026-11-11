package art.moodframe.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A trash group in memory, for tests: what is put in can be restored or deleted, a stuck item refuses both. */
public class MemoryTrashGroup implements TrashGroup {

    private final Kind kind;
    private final Map<String, TrashEntry> entries = new LinkedHashMap<>();
    private final Set<String> stuck = new HashSet<>();

    public MemoryTrashGroup(Kind kind) {
        this.kind = kind;
    }

    /** Puts an item into the trash that was deleted the given number of days ago. */
    public MemoryTrashGroup put(String id, int daysAgo) {
        entries.put(id, new TrashEntry(id, "Name of " + id, LocalDateTime.now().minusDays(daysAgo),
                "Context of " + id));
        return this;
    }

    /** Lets restoring and deleting an item fail. */
    public MemoryTrashGroup stuck(String id) {
        stuck.add(id);
        return this;
    }

    @Override
    public Kind kind() {
        return kind;
    }

    @Override
    public List<TrashEntry> list() {
        return new ArrayList<>(entries.values());
    }

    @Override
    public boolean restore(String id) {
        return !stuck.contains(id) && entries.remove(id) != null;
    }

    @Override
    public boolean deletePermanently(String id) {
        return !stuck.contains(id) && entries.remove(id) != null;
    }
}
