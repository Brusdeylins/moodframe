package art.moodframe.service.library;

import art.moodframe.model.PromptBlock;
import art.moodframe.repository.FavoriteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Collator;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The favorites of the user among the moods, the frames and the building blocks, standard ones and those of the user,
 * kept by kind and id ({@link FavoriteRepository}). The lists of the right panel show them first ({@link #order}).
 *
 * <p>A switched favorite is reported as {@link LibraryEvents.Kind#FAVORITE}. The service may be called from several
 * threads. Its lock guards the favorites in memory only, so {@link #isFavorite} never waits for the database: a
 * {@link #toggle} switches the favorite in memory at once and stores it afterwards, outside that lock. The writes run
 * one after the other, each storing the state in memory as it is then, so the database ends with the last switch
 * whatever order concurrent toggles reach it in; a write that fails sets the favorite in memory back to the state
 * of the database, unless a later toggle is still to store it.</p>
 *
 * @since 2.0.0
 */
public class FavoriteService {

    private static final Logger logger = LoggerFactory.getLogger(FavoriteService.class);

    /** The kinds of entries that can be favorites, one per list of the right panel. */
    public enum Kind {
        MOOD, FRAME, ATMOSPHERE, STYLE, COLOR, LIGHT, CAMERA;

        /**
         * Tells the kind of a text building block.
         *
         * @param type the type of the block
         * @return its kind
         */
        public static Kind of(PromptBlock.Type type) {
            return valueOf(type.name());
        }
    }

    private final FavoriteRepository repository;
    private final Map<Kind, Set<String>> favorites = new EnumMap<>(Kind.class);
    private final Map<Kind, Set<String>> stored = new EnumMap<>(Kind.class);
    private final Object writing = new Object();
    private final LibraryEvents events;
    private final RepositoryAccess access = new RepositoryAccess(logger);

    /**
     * Creates the service on the given repository.
     *
     * @param repository the favorites
     * @param events     where a switched favorite is reported
     */
    public FavoriteService(FavoriteRepository repository, LibraryEvents events) {
        this.repository = repository;
        this.events = events;
    }

    /*  The ids of the favorites of a kind as shown, loaded once, and as stored in the database; only under the lock
        of the service  */
    private Set<String> ids(Kind kind) {
        return favorites.computeIfAbsent(kind, (key) -> {
            Set<String> loaded = access.call(() -> repository.findIds(key.name()), new HashSet<>(),
                    "Failed to load the favorites of " + key.name());
            stored.put(key, new HashSet<>(loaded));
            return loaded;
        });
    }

    /*  the ids of the favorites of a kind as stored in the database; only under the lock of the service  */
    private Set<String> storedIds(Kind kind) {
        ids(kind);
        return stored.get(kind);
    }

    /*  adds an id to the ids, or removes it  */
    private static void set(Set<String> ids, String id, boolean favorite) {
        if (favorite)
            ids.add(id);
        else
            ids.remove(id);
    }

    /**
     * Tells if an entry is a favorite.
     *
     * @param kind the kind of the entry
     * @param id   the entry id, null for "None"
     * @return true for a favorite
     */
    public synchronized boolean isFavorite(Kind kind, String id) {
        return id != null && ids(kind).contains(id);
    }

    /**
     * Makes an entry a favorite, or takes its favorite away: at once in memory, then in the database, outside the
     * lock that {@link #isFavorite} takes.
     *
     * @param kind the kind of the entry
     * @param id   the entry id
     * @return true if it is a favorite now, the former state if it cannot be stored
     */
    public boolean toggle(Kind kind, String id) {
        synchronized (this) {
            Set<String> ids = ids(kind);
            set(ids, id, !ids.contains(id));
        }

        /*  The latest state in memory is written, so a toggle that comes later never loses against an earlier one  */
        synchronized (writing) {
            boolean wanted;
            boolean held;
            synchronized (this) {
                wanted = ids(kind).contains(id);
                held = storedIds(kind).contains(id);
            }
            String failure = "Failed to change the favorite " + id + " of " + kind;
            boolean written = wanted == held || (wanted
                    ? access.call(() -> repository.add(kind.name(), id), false, failure)
                    : access.call(() -> repository.remove(kind.name(), id), false, failure));
            synchronized (this) {
                if (written)
                    set(storedIds(kind), id, wanted);
                else if (ids(kind).contains(id) == wanted)
                    set(ids(kind), id, held);
            }
            if (written)
                events.changed(LibraryEvents.Kind.FAVORITE, id);
        }
        return isFavorite(kind, id);
    }

    /**
     * Orders the entries of a list: the favorites first, then the others by the order of their groups; the favorites
     * and the entries of each group alphabetically, ignoring case.
     *
     * @param entries  the entries
     * @param favorite tells if an entry is a favorite
     * @param group    tells the place of the group of an entry, the same for all in a list without groups
     * @param name     tells the name of an entry
     * @param <T>      the item type of the list
     * @return the entries in their order
     */
    public static <T> List<T> order(Collection<T> entries, Predicate<T> favorite, ToIntFunction<T> group,
            Function<T, String> name) {
        Collator collator = Collator.getInstance(Locale.ROOT);
        collator.setStrength(Collator.SECONDARY);
        return entries.stream().sorted(Comparator.comparing((T entry) -> !favorite.test(entry))
                .thenComparingInt((T entry) -> favorite.test(entry) ? 0 : group.applyAsInt(entry))
                .thenComparing(name, collator)).toList();
    }
}
