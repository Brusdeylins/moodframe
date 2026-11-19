package art.moodframe.service.brainstorm;

import art.moodframe.model.ResearchNote;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrainstormJsonTest {

    @Test
    void aResearchNoteSurvivesStoringAsAnEventAndCutsTheResult() {
        ResearchNote note = new ResearchNote("nautilus shell", "x".repeat(50),
                List.of(new ResearchNote.Source("ai.example", "https://ai.example/a")));

        JSONObject event = BrainstormJson.researchEvent(note, 10);
        ResearchNote read = BrainstormJson.researchNote(new JSONObject(event.toString()));

        assertEquals(BrainstormJson.RESEARCH_EVENT, event.getString("event"));
        assertEquals("nautilus shell", read.query());
        assertEquals("xxxxxxxxxx...", read.result());
        assertEquals(note.sources(), read.sources());
    }
}
