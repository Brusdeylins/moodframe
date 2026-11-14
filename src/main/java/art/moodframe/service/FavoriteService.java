package art.moodframe.service;

import art.moodframe.model.PromptBlock;
import art.moodframe.repository.FavoriteRepository;

import java.text.Collator;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
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
 * @since 2.0.0
 */
public class FavoriteService {

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

    private final FavoriteRepository repository = new FavoriteRepository();
    private final Map<Kind, Set<String>> favorites = new EnumMap<>(Kind.class);

    /*  the ids of the favorites of a kind, loaded once  */
    private Set<String> ids(Kind kind) {
        return favorites.computeIfAbsent(kind, (key) -> repository.findIds(key.name()));
    }

    /**
     * Tells if an entry is a favorite.
     *
     * @param kind the kind of the entry
     * @param id   the entry id, null for "None"
     * @return true for a favorite
     */
    public boolean isFavorite(Kind kind, String id) {
        return id != null && ids(kind).contains(id);
    }

    /**
     * Makes an entry a favorite, or takes its favorite away.
     *
     * @param kind the kind of the entry
     * @param id   the entry id
     * @return true if it is a favorite now, the former state if it cannot be stored
     */
    public boolean toggle(Kind kind, String id) {
        boolean favorite = !isFavorite(kind, id);
        if (favorite && repository.add(kind.name(), id))
            ids(kind).add(id);
        else if (!favorite && repository.remove(kind.name(), id))
            ids(kind).remove(id);
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
