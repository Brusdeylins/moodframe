package art.moodframe.ui.dialog;

import art.moodframe.model.BrainstormReply;
import art.moodframe.ui.cell.BrainstormMarker;
import art.moodframe.ui.dialog.BrainstormChat.StepState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The marker transitions of a chat, as the history list shows them, and the states of its steps. */
class BrainstormChatTest {

    private final BrainstormChat chat = new BrainstormChat(null);

    @Test
    void startsIdle() {
        assertEquals(BrainstormMarker.IDLE, chat.getMarker());
        assertFalse(chat.busy);
    }

    @Test
    void runsWhileTheAiWorks() {
        chat.started();

        assertEquals(BrainstormMarker.RUNNING, chat.getMarker());
        assertTrue(chat.busy);
    }

    @Test
    void anAnswerInTheOpenChatIsNeverUnread() {
        chat.started();
        chat.answered(true);

        assertEquals(BrainstormMarker.IDLE, chat.getMarker());
        assertFalse(chat.busy);
    }

    @Test
    void anAnswerInAnotherChatIsUnreadUntilItIsOpened() {
        chat.started();
        chat.answered(false);
        assertEquals(BrainstormMarker.UNREAD, chat.getMarker());

        chat.opened();
        assertEquals(BrainstormMarker.IDLE, chat.getMarker());
    }

    @Test
    void aFailureInAnotherChatIsMarkedUntilItIsOpened() {
        chat.started();
        chat.failed(false);
        assertEquals(BrainstormMarker.FAILED, chat.getMarker());
        assertFalse(chat.busy);

        chat.opened();
        assertEquals(BrainstormMarker.IDLE, chat.getMarker());
    }

    @Test
    void openingARunningChatKeepsItRunning() {
        chat.started();
        chat.opened();

        assertEquals(BrainstormMarker.RUNNING, chat.getMarker());
        chat.answered(false);
        assertEquals(BrainstormMarker.UNREAD, chat.getMarker(), "it was left again before the answer came");
    }

    @Test
    void aNewRoundBackAtTheSettingKeepsTheLaterStepsDone() {
        assertFalse(chat.progress(BrainstormReply.parse("""
            {"step": 8, "message": "m", "skipped_steps": [3, 4, 5], "done_steps": [1, 2, 6, 7], "round": 1}
            """)));

        assertTrue(chat.progress(BrainstormReply.parse("""
            {"step": 6, "message": "m", "skipped_steps": [3, 4, 5], "done_steps": [1, 2, 7], "round": 2}
            """)), "the second image starts a new round");
        assertEquals(2, chat.round);
        assertEquals(StepState.CURRENT, chat.stateOf(6));
        assertEquals(StepState.DONE, chat.stateOf(2));
        assertEquals(StepState.DONE, chat.stateOf(7), "a step after the current one may be done");
        assertEquals(StepState.OPEN, chat.stateOf(8));
        assertEquals(StepState.SKIPPED, chat.stateOf(3));
    }

    @Test
    void aReplyWithoutRoundStaysInTheRoundAndCountsTheStepsBeforeTheCurrentOne() {
        chat.progress(BrainstormReply.parse(
                "{\"step\": 6, \"message\": \"m\", \"done_steps\": [1, 2, 7], \"round\": 2}"));

        assertFalse(chat.progress(BrainstormReply.parse("{\"step\": 7, \"message\": \"m\", \"skipped_steps\": [3]}")));
        assertEquals(2, chat.round, "the round only counts up");
        assertEquals(StepState.DONE, chat.stateOf(6));
        assertEquals(StepState.SKIPPED, chat.stateOf(3));
        assertEquals(StepState.CURRENT, chat.stateOf(7));
        assertEquals(StepState.OPEN, chat.stateOf(8));
    }

    @Test
    void theCurrentStepWinsOverDoneAndSkipped() {
        chat.progress(BrainstormReply.parse(
                "{\"step\": 8, \"message\": \"m\", \"skipped_steps\": [8], \"done_steps\": [1, 8]}"));

        assertEquals(StepState.CURRENT, chat.stateOf(8));
        assertEquals(StepState.DONE, chat.stateOf(1));
        assertEquals(StepState.OPEN, chat.stateOf(2));
    }
}
