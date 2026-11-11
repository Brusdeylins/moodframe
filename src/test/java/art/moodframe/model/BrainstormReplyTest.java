package art.moodframe.model;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrainstormReplyTest {

    private static final String IMAGE_REPLY = """
        {
          "step": 2,
          "media_type": "image",
          "message": "Wie viele Personen sind zu sehen?",
          "quick_replies": ["1", "2", "3", "4", "5"],
          "draft": {
            "prompt": "Three colleagues fix a server outage in a modern office.",
            "style": "Business Photography",
            "style_reason": "People in a business setting",
            "color_style": null,
            "aspect_ratio": "16:9",
            "target_model": "FLUX.2 [pro] - Production Grade ($0.03/MP)",
            "model_reason": "",
            "video": {"duration_seconds": null}
          }
        }
        """;

    @Test
    void parsesImageReply() {
        BrainstormReply reply = BrainstormReply.parse(IMAGE_REPLY);

        assertEquals(2, reply.getStep());
        assertEquals("Wie viele Personen sind zu sehen?", reply.getMessage());
        assertEquals(4, reply.getQuickReplies().size(), "quick replies are capped at four");

        BrainstormDraft draft = reply.getDraft();
        assertFalse(draft.isVideo());
        assertEquals("Business Photography", draft.getStyle());
        assertNull(draft.getColorStyle(), "JSON null means no suggestion");
        assertEquals("16:9", draft.getAspectRatio());
        assertNull(draft.getDurationSeconds());
    }

    @Test
    void readsTheSkippedStepsAndIgnoresInvalidOnes() {
        BrainstormReply reply = BrainstormReply.parse(
                "{\"step\": 6, \"message\": \"m\", \"skipped_steps\": [3, 4, 4, 0, 9, 5]}");

        assertEquals(java.util.List.of(3, 4, 5), reply.getSkippedSteps());
        assertTrue(BrainstormReply.parse("{\"step\": 2, \"message\": \"m\"}").getSkippedSteps().isEmpty());
    }

    @Test
    void keepsStepEightOfTheEightStepProcess() {
        assertEquals(8, BrainstormReply.parse("{\"step\": 8, \"message\": \"m\"}").getStep());
    }

    @Test
    void readsAndWritesFactsOfTheDraft() {
        BrainstormDraft draft = BrainstormReply.parse("""
            {"step": 5, "message": "m", "draft": {"prompt": "p", "facts": [
              {"text": "Septa divide the shell", "domain": "ai.example", "url": "https://ai.example/a",
               "origin": "source", "confirmed": false},
              {"text": "My own number", "origin": "user"},
              {"text": " "}]}}
            """).getDraft();

        assertEquals(2, draft.getFacts().size(), "a fact without text is dropped");
        assertFalse(draft.getFacts().get(0).isConfirmed());
        assertFalse(draft.getFacts().get(0).isFromUser());
        assertTrue(draft.getFacts().get(1).isFromUser());
        assertTrue(draft.getFacts().get(1).isConfirmed(), "confirmed by default");

        BrainstormDraft again = BrainstormDraft.fromJson(draft.toJson());
        assertEquals("Septa divide the shell", again.getFacts().get(0).getText());
        assertEquals("ai.example", again.getFacts().get(0).getDomain());
        assertFalse(again.getFacts().get(0).isConfirmed());
    }

    @Test
    void parsesVideoReplyWithVideoFields() {
        BrainstormReply reply = BrainstormReply.parse("""
            {"step": 4, "media_type": "video", "message": "ok",
             "draft": {"prompt": "p", "video": {"duration_seconds": 8}}}
            """);

        assertTrue(reply.getDraft().isVideo());
        assertEquals(8, reply.getDraft().getDurationSeconds());
    }

    @Test
    void toleratesCodeFenceAndSurroundingText() {
        BrainstormReply reply = BrainstormReply.parse("```json\n" + IMAGE_REPLY + "\n```");

        assertEquals(2, reply.getStep());
    }

    @Test
    void clampsStepIntoRange() {
        assertEquals(8, BrainstormReply.parse("{\"step\": 9, \"message\": \"m\"}").getStep());
        assertEquals(1, BrainstormReply.parse("{\"message\": \"m\"}").getStep());
    }

    @Test
    void rejectsUnusableReplies() {
        assertThrows(IllegalArgumentException.class, () -> BrainstormReply.parse(null));
        assertThrows(IllegalArgumentException.class, () -> BrainstormReply.parse("no json here"));
        assertThrows(IllegalArgumentException.class, () -> BrainstormReply.parse("{\"step\": 1}"));
        assertThrows(IllegalArgumentException.class, () -> BrainstormReply.parse("{ broken"));
    }

    @Test
    void draftSurvivesJsonRoundTrip() {
        BrainstormDraft original = BrainstormReply.parse(IMAGE_REPLY).getDraft();

        BrainstormDraft copy = BrainstormDraft.fromJson(new JSONObject(original.toJson().toString()));

        assertEquals(original.getPrompt(), copy.getPrompt());
        assertEquals(original.getStyle(), copy.getStyle());
        assertEquals(original.getAspectRatio(), copy.getAspectRatio());
        assertEquals(original.getTargetModel(), copy.getTargetModel());
        assertNull(copy.getColorStyle());
    }
}
