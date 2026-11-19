package art.moodframe.service.brainstorm;

import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.ResearchNote;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void theFrameAndTheSavedImagesOfADraftSurviveStoring() {
        BrainstormDraft draft = BrainstormJson.draft(new JSONObject("""
                {"prompt": "p", "frame": {"id": "std:frame:social-post", "name": "Social Post"},
                 "saved_images": [{"id": "img-1", "name": "ACME Logo"}, "Word Mark",
                                  {"name": "Proposed", "text": "a new one", "is_new": true}]}
                """));

        BrainstormDraft read = BrainstormJson.draft(new JSONObject(BrainstormJson.toJson(draft).toString()));

        assertEquals("std:frame:social-post", read.getFrame().id());
        assertEquals("Social Post", read.getFrame().name());
        assertEquals(List.of("ACME Logo", "Word Mark"), read.getSavedImages().stream()
                .map(BrainstormDraft.Block::name).toList(), "only references, no image proposed anew");
        assertNull(BrainstormJson.draft(new JSONObject("{\"prompt\": \"p\"}")).getFrame(), "an older draft has none");
    }
}
