package art.moodframe.ui.dialog;

import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.BrainstormReply;
import art.moodframe.model.BrainstormSession;
import art.moodframe.ui.cell.BrainstormMarker;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The state of one brainstorm chat: its session, draft, messages and whether the AI is working on it. Every chat
 * has its own instance, so several chats can wait for the AI at the same time and an answer always lands in the
 * chat that asked. The {@link BrainstormMarker} tells the history list what happened in the background.
 *
 * @since 1.8.4
 */
public class BrainstormChat {

    /** The state of a step in the stepper of the chat. */
    enum StepState { DONE, CURRENT, OPEN, SKIPPED }

    /** The session, null for a new chat that is stored with its first message. */
    BrainstormSession session;
    BrainstormDraft draft = new BrainstormDraft();
    Set<Integer> skippedSteps = new HashSet<>();
    Set<Integer> doneSteps = new HashSet<>();
    int currentStep = 1;
    /** The image of the chat the AI works on, counted from 1: a chat defines one image per round. */
    int round = 1;
    List<String> quickReplies = List.of();
    boolean busy;
    String statusText = "";
    /** Whether the stored messages were already shown in {@link #messageBox}. */
    boolean loaded;
    final VBox messageBox = new VBox(10.0);
    double scrollPosition;
    String inputText = "";
    /** The language model of a new chat until it is stored with its first message ("gemini" or "openai"). */
    String engine;

    private final ObjectProperty<BrainstormMarker> marker = new SimpleObjectProperty<>(BrainstormMarker.IDLE);

    BrainstormChat(BrainstormSession session) {
        this.session = session;
        messageBox.setPadding(new Insets(15.0));
    }

    public ObjectProperty<BrainstormMarker> markerProperty() {
        return marker;
    }

    BrainstormMarker getMarker() {
        return marker.get();
    }

    /** A request was sent. */
    void started() {
        busy = true;
        marker.set(BrainstormMarker.RUNNING);
    }

    /** The answer arrived; it is unread if the chat is not the open one. */
    void answered(boolean open) {
        busy = false;
        marker.set(open ? BrainstormMarker.IDLE : BrainstormMarker.UNREAD);
    }

    /** The request failed; the failure is unread if the chat is not the open one. */
    void failed(boolean open) {
        busy = false;
        marker.set(open ? BrainstormMarker.IDLE : BrainstormMarker.FAILED);
    }

    /** The chat was opened: an unread answer or failure counts as seen, a running request keeps running. */
    void opened() {
        if (marker.get() == BrainstormMarker.UNREAD || marker.get() == BrainstormMarker.FAILED) {
            marker.set(BrainstormMarker.IDLE);
        }
    }

    /**
     * Takes over the progress of an AI reply: the current, skipped and done steps and the round. The round only counts
     * up, so a reply that names no round stays in the current one.
     *
     * @param reply the reply
     * @return whether the reply starts a new round, that is another image of the chat
     */
    boolean progress(BrainstormReply reply) {
        boolean newRound = reply.getRound() > round;
        currentStep = reply.getStep();
        skippedSteps = new HashSet<>(reply.getSkippedSteps());
        doneSteps = new HashSet<>(reply.getDoneSteps());
        round = Math.max(round, reply.getRound());
        return newRound;
    }

    /** The state of a step: the current one wins over skipped, skipped over done; a later step may be done, too. */
    StepState stateOf(int step) {
        return step == currentStep ? StepState.CURRENT
                : skippedSteps.contains(step) ? StepState.SKIPPED
                : doneSteps.contains(step) ? StepState.DONE : StepState.OPEN;
    }
}
