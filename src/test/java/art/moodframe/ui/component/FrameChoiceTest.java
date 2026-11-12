package art.moodframe.ui.component;

import art.moodframe.model.Frame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The frame selection lists only choices: its actions sit as icons in the header of the frame group. */
class FrameChoiceTest {

    @Test
    void theListHoldsNoFrameAndTheFramesOnly() {
        Frame square = new Frame("id-square", "Square");
        Frame cinema = new Frame("id-cinema", "Cinema");

        List<FrameChoice> rows = FrameChoice.listOf(List.of(square, cinema));

        assertEquals(List.of(FrameChoice.Kind.NONE, FrameChoice.Kind.FRAME, FrameChoice.Kind.FRAME),
                rows.stream().map(FrameChoice::kind).toList());
        assertNull(rows.get(0).frame());
        assertEquals(square, rows.get(1).frame());
        assertEquals(cinema, rows.get(2).frame());
    }

    @Test
    void aRowIsNeverAnAction() {
        assertEquals(List.of(FrameChoice.Kind.NONE, FrameChoice.Kind.FRAME), List.of(FrameChoice.Kind.values()));
    }
}
