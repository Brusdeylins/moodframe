package tools.moodframe.ui.dialog;

import tools.moodframe.ui.cell.BrainstormMarker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The marker transitions of a chat, as the history list shows them. */
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
}
