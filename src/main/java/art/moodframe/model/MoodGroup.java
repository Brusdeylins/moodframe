package art.moodframe.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * The chat entries of the active chats that share the mood stored at them (see {@link EntryMood}), for the left
 * sidebar by mood: those of one mood, the combinations of their own (blocks set, but no saved mood or the chosen one
 * changed), or those without blocks, like the entries from before 2.0.
 *
 * @param kind the kind of the group
 * @param moodId the id of the mood, null for the other kinds
 * @param name the current name of a mood of the user in use, else the name stored at the latest entry; null for the
 *             other kinds
 * @param deleted whether the mood of the user lies in the trash or is deleted for good
 * @param colors the hex colors of the palette at the latest entry of a mood, empty for none and the other kinds
 * @param imageCount the number of generated images that are not deleted
 * @param lastUsed the creation time of the latest entry, null for a group without entries
 * @since 2.0.0
 */
public record MoodGroup(Kind kind, String moodId, String name, boolean deleted, List<String> colors, int imageCount,
        LocalDateTime lastUsed) implements EntryGroup {

    /** The kinds of groups, in the order of the list. */
    public enum Kind {
        MOOD, CUSTOM, NONE
    }

    @Override
    public boolean holds(ChatEntry entry) {
        EntryMood mood = entry.getMood();
        return switch (kind) {
            case MOOD -> mood != null && moodId.equals(mood.moodId());
            case CUSTOM -> mood != null && mood.moodId() == null;
            case NONE -> mood == null;
        };
    }

    @Override
    public boolean sameAs(EntryGroup other) {
        return other instanceof MoodGroup group && kind == group.kind && Objects.equals(moodId, group.moodId);
    }
}
