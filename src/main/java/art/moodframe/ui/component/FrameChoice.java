package art.moodframe.ui.component;

import art.moodframe.model.Frame;

/**
 * One row of the frame selection: an action, the separator line, "No frame" or a frame.
 *
 * @param kind what the row stands for
 * @param frame the frame of a {@link Kind#FRAME} row, else null
 * @since 2.0.0
 */
public record FrameChoice(Kind kind, Frame frame) {

    /** What a row of the frame selection stands for, in the order of the list. */
    public enum Kind {
        SAVE, SAVE_AS, MANAGE, SEPARATOR, NONE, FRAME
    }

    /**
     * Creates a row without frame.
     *
     * @param kind what the row stands for
     * @return the row
     */
    public static FrameChoice of(Kind kind) {
        return new FrameChoice(kind, null);
    }

    /** True for the actions and the separator, which run something instead of being chosen. */
    public boolean isAction() {
        return kind != Kind.NONE && kind != Kind.FRAME;
    }
}
