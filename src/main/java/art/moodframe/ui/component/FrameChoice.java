package art.moodframe.ui.component;

import art.moodframe.model.Frame;

import java.util.ArrayList;
import java.util.List;

/**
 * One row of the frame selection: "No frame" or a frame. The actions sit as icons in the header of the frame group.
 *
 * @param kind what the row stands for
 * @param frame the frame of a {@link Kind#FRAME} row, else null
 * @since 2.0.0
 */
public record FrameChoice(Kind kind, Frame frame) {

    /** What a row of the frame selection stands for, in the order of the list. */
    public enum Kind {
        NONE, FRAME
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

    /**
     * Lists the rows of the frame selection: "No frame" first, then the frames.
     *
     * @param frames the frames in the order to show
     * @return the rows
     */
    public static List<FrameChoice> listOf(List<Frame> frames) {
        List<FrameChoice> rows = new ArrayList<>();
        rows.add(of(Kind.NONE));
        frames.forEach((frame) -> rows.add(new FrameChoice(Kind.FRAME, frame)));
        return rows;
    }
}
