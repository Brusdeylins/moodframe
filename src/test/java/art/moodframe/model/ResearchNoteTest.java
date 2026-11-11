package art.moodframe.model;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResearchNoteTest {

    @Test
    void survivesStoringAsAnEventAndCutsTheResult() {
        ResearchNote note = new ResearchNote("nautilus shell", "x".repeat(50),
                List.of(new ResearchNote.Source("ai.example", "https://ai.example/a")));

        JSONObject event = note.toEventJson(10);
        ResearchNote read = ResearchNote.fromEventJson(new JSONObject(event.toString()));

        assertEquals(ResearchNote.EVENT, event.getString("event"));
        assertEquals("nautilus shell", read.query());
        assertEquals("xxxxxxxxxx...", read.result());
        assertEquals(note.sources(), read.sources());
    }
}
