package art.moodframe.ui.component;

import art.moodframe.model.Mood;

import java.util.ArrayList;
import java.util.List;

/**
 * One row of the mood selection: "No mood" or a mood. The actions sit as icons in the header of the mood group.
 *
 * @param kind what the row stands for
 * @param mood the mood of a {@link Kind#MOOD} row, else null
 * @since 2.0.0
 */
public record MoodChoice(Kind kind, Mood mood) {

    /** What a row of the mood selection stands for, in the order of the list. */
    public enum Kind {
        NONE, MOOD
    }

    /**
     * Creates a row without mood.
     *
     * @param kind what the row stands for
     * @return the row
     */
    public static MoodChoice of(Kind kind) {
        return new MoodChoice(kind, null);
    }

    /**
     * Lists the rows of the mood selection: "No mood" first, then the moods.
     *
     * @param moods the moods in the order to show
     * @return the rows
     */
    public static List<MoodChoice> listOf(List<Mood> moods) {
        List<MoodChoice> rows = new ArrayList<>();
        rows.add(of(Kind.NONE));
        moods.forEach((mood) -> rows.add(new MoodChoice(Kind.MOOD, mood)));
        return rows;
    }
}
