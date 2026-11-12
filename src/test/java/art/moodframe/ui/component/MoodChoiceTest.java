package art.moodframe.ui.component;

import art.moodframe.model.Mood;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The mood selection lists only choices: its actions sit as icons in the header of the mood group. */
class MoodChoiceTest {

    @Test
    void theListHoldsNoMoodAndTheMoodsOnly() {
        Mood calm = new Mood("id-calm", "Calm");
        Mood storm = new Mood("id-storm", "Storm");

        List<MoodChoice> rows = MoodChoice.listOf(List.of(calm, storm));

        assertEquals(List.of(MoodChoice.Kind.NONE, MoodChoice.Kind.MOOD, MoodChoice.Kind.MOOD),
                rows.stream().map(MoodChoice::kind).toList());
        assertNull(rows.get(0).mood());
        assertEquals(calm, rows.get(1).mood());
        assertEquals(storm, rows.get(2).mood());
    }

    @Test
    void aRowIsNeverAnAction() {
        assertEquals(List.of(MoodChoice.Kind.NONE, MoodChoice.Kind.MOOD), List.of(MoodChoice.Kind.values()));
    }
}
