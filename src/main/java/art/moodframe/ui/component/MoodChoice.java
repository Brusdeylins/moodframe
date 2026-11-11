package art.moodframe.ui.component;

import art.moodframe.model.Mood;

/**
 * One row of the mood selection: an action, the separator line, "No mood" or a mood.
 *
 * @param kind what the row stands for
 * @param mood the mood of a {@link Kind#MOOD} row, else null
 * @since 2.0.0
 */
public record MoodChoice(Kind kind, Mood mood) {

    /** What a row of the mood selection stands for, in the order of the list. */
    public enum Kind {
        SAVE, SAVE_AS, MANAGE, SEPARATOR, NONE, MOOD
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

    /** True for the actions and the separator, which run something instead of being chosen. */
    public boolean isAction() {
        return kind != Kind.NONE && kind != Kind.MOOD;
    }
}
