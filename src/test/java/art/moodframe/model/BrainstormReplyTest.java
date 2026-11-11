package art.moodframe.model;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        assertEquals("Business Photography", draft.getBlock(Mood.Field.STYLE).name());
        assertNull(draft.getBlock(Mood.Field.COLOR), "JSON null means no suggestion");
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
    void readsTheDoneStepsAlsoAfterTheCurrentOneAndTheRound() {
        BrainstormReply reply = BrainstormReply.parse("""
            {"step": 6, "message": "m", "skipped_steps": [3, 4, 5], "done_steps": [1, 2, 7, 7, 0, 9], "round": 2}
            """);

        assertEquals(List.of(1, 2, 7), reply.getDoneSteps(), "invalid and doubled steps are dropped");
        assertEquals(2, reply.getRound());
        assertTrue(BrainstormReply.parse("{\"step\": 3, \"message\": \"m\", \"done_steps\": []}").getDoneSteps()
                .isEmpty(), "an empty list means nothing is done");
    }

    @Test
    void countsEveryStepBeforeTheCurrentOneAsDoneInRoundOneForAnOlderReply() {
        BrainstormReply reply = BrainstormReply.parse("{\"step\": 6, \"message\": \"m\", \"skipped_steps\": [3, 4]}");

        assertEquals(List.of(1, 2, 5), reply.getDoneSteps(), "the skipped steps are not done");
        assertEquals(1, reply.getRound());
        assertEquals(1, BrainstormReply.parse("{\"step\": 1, \"message\": \"m\", \"round\": 0}").getRound());
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
        assertEquals(original.getBlock(Mood.Field.STYLE), copy.getBlock(Mood.Field.STYLE));
        assertEquals(original.getAspectRatio(), copy.getAspectRatio());
        assertEquals(original.getTargetModel(), copy.getTargetModel());
        assertNull(copy.getBlock(Mood.Field.COLOR));
    }

    @Test
    void readsTheStyleAndThePaletteOfAnOlderDraftAsReferencesByName() {
        BrainstormDraft draft = BrainstormDraft.fromJson(new JSONObject("""
            {"prompt": "p", "style": "Pixel Art", "color_style": "Ocean Breeze", "style_reason": "r"}
            """));

        assertEquals(new BrainstormDraft.Block(null, "Pixel Art", null, List.of(), false),
                draft.getBlock(Mood.Field.STYLE));
        assertEquals(new BrainstormDraft.Block(null, "Ocean Breeze", null, List.of(), false),
                draft.getBlock(Mood.Field.COLOR));
        assertNull(draft.getMood());
        assertNull(draft.getBlock(Mood.Field.ATMOSPHERE));
        assertEquals("r", draft.getStyleReason());

        JSONObject written = draft.toJson();
        assertFalse(written.has("color_style"), "a draft is written in the new format only");
        assertEquals("Ocean Breeze", written.getJSONObject("color").getString("name"));
    }

    @Test
    void readsEntriesNewBlocksAndTheMoodOfTheNewFormat() {
        BrainstormDraft draft = BrainstormReply.parse("""
            {"step": 7, "message": "m", "draft": {"prompt": "A lighthouse on a cliff",
              "mood": "Wild North",
              "style": {"name": "Watercolor", "is_new": false},
              "atmosphere": {"name": "Storm Front", "text": "an approaching storm, wind-torn spray", "is_new": true},
              "light": {"name": "Overcast", "id": "std:light:overcast"},
              "color": {"name": "Sea Glass", "colors": ["#a8d5ba", "#2F4858", "teal", "#ABCDEF0"], "is_new": true}}}
            """).getDraft();

        assertEquals("Wild North", draft.getMood().name());
        assertEquals(new BrainstormDraft.Block(null, "Watercolor", null, List.of(), false),
                draft.getBlock(Mood.Field.STYLE));
        assertEquals(new BrainstormDraft.Block(null, "Storm Front", "an approaching storm, wind-torn spray", List.of(),
                true), draft.getBlock(Mood.Field.ATMOSPHERE));
        assertEquals("std:light:overcast", draft.getBlock(Mood.Field.LIGHT).id());
        assertEquals(List.of("#A8D5BA", "#2F4858"), draft.getBlock(Mood.Field.COLOR).colors(),
                "only colors in the form #RRGGBB are kept, in upper case");

        BrainstormDraft copy = BrainstormDraft.fromJson(new JSONObject(draft.toJson().toString()));
        for (Mood.Field field : BrainstormDraft.FIELDS)
            assertEquals(draft.getBlock(field), copy.getBlock(field), field.name());
        assertEquals(draft.getMood(), copy.getMood());
    }

    @Test
    void readsACameraAsEntryOrAsNewCameraWithItsSettings() {
        BrainstormDraft draft = BrainstormReply.parse("""
            {"step": 7, "message": "m", "draft": {"prompt": "A harbor",
              "camera": {"name": "Harbor Tele", "is_new": true,
                "settings": {"body": "FULL_FRAME", "lens": "MM_200", "shot": "WIDE", "iso": "ISO_9"}}}}
            """).getDraft();

        BrainstormDraft.Block camera = draft.getBlock(Mood.Field.CAMERA);
        assertTrue(camera.isNew());
        assertEquals(new CameraSettings(CameraSettings.Body.FULL_FRAME, null, CameraSettings.Lens.MM_200, null, null,
                null, null, CameraSettings.Shot.WIDE), camera.settings(), "an unknown value counts as any");
        assertEquals(camera, BrainstormDraft.fromJson(new JSONObject(draft.toJson().toString()))
                .getBlock(Mood.Field.CAMERA));

        BrainstormDraft entry = BrainstormDraft.fromJson(new JSONObject("""
            {"prompt": "p", "camera": {"name": "Classic Portrait", "is_new": false}}
            """));
        assertEquals(new BrainstormDraft.Block(null, "Classic Portrait", null, List.of(), false),
                entry.getBlock(Mood.Field.CAMERA));
        assertNull(BrainstormDraft.fromJson(new JSONObject("""
            {"prompt": "p", "camera": {"name": "Anything", "settings": {"lens": "MM_7"}, "is_new": true}}
            """)).getBlock(Mood.Field.CAMERA), "a new camera whose settings leave every value any has no content");
    }

    @Test
    void dropsBlocksWithoutNameAndNewBlocksWithoutContent() {
        BrainstormDraft draft = BrainstormDraft.fromJson(new JSONObject("""
            {"prompt": "p", "mood": "null",
             "style": {"text": "ink wash"},
             "atmosphere": {"name": "Empty", "is_new": true},
             "light": {"name": "Neon Edge", "text": "thin neon rim light from behind"},
             "color": {"name": "Grey", "colors": ["grey"], "is_new": true}}
            """));

        assertNull(draft.getMood(), "the word null is no name");
        assertNull(draft.getBlock(Mood.Field.STYLE), "a block needs a name");
        assertNull(draft.getBlock(Mood.Field.ATMOSPHERE), "a new block needs a text");
        assertTrue(draft.getBlock(Mood.Field.LIGHT).isNew(), "a block with a text counts as new");
        assertNull(draft.getBlock(Mood.Field.COLOR), "a new palette needs a valid color");
    }
}
