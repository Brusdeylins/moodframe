package art.moodframe.ui.brainstorm;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.BrainstormMessage;
import art.moodframe.model.BrainstormSession;
import art.moodframe.model.Mood;
import art.moodframe.service.brainstorm.BrainstormContext;
import art.moodframe.service.brainstorm.BrainstormReply;
import art.moodframe.service.brainstorm.BrainstormService;
import art.moodframe.ui.component.DialogButtons;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.SplitPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Popup of the brainstorm assistant: a guided AI chat that develops a prompt together with the suggested settings.
 * The window composes three parts: the history of the sessions on the left ({@link BrainstormHistory}), the chat in
 * the middle ({@link BrainstormChatView}) and the result on the right ({@link BrainstormResultForm}). It keeps the
 * chats, sends the requests of the shown chat and hands every answer to the chat that asked.
 *
 * <p>Every chat has its own {@link BrainstormChat}: several chats can wait for the AI at the same time, the answer
 * lands in the chat that asked, and the history list marks chats that are running or have an unread answer.</p>
 *
 * @since 1.7.0
 */
public class BrainstormDialog extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(BrainstormDialog.class);

    private static final double DIALOG_WIDTH = 1280;
    private static final double DIALOG_HEIGHT = 820;

    /**
     * The values adopted into the main window. A {@code null} value is not adopted.
     *
     * @param prompt the prompt for the input field
     * @param targetModel localized display name of the target model (always adopted)
     * @param moodId the id of the mood, which sets its blocks before the adopted blocks
     * @param blocks the adopted building blocks by field: an entry of the lists with its id, or a new block for the
     *               list of the user; a field that is not adopted is missing
     * @param aspectRatio the aspect ratio
     * @param durationSeconds the video duration in seconds
     * @param resolution the resolution of a model without resolution variants (1K, 2K, 4K), else null
     * @param saveMood whether the fields are saved as a new mood afterwards
     */
    public record Result(String prompt, String targetModel, String moodId,
                         Map<Mood.Field, BrainstormDraft.Block> blocks, String aspectRatio, Integer durationSeconds,
                         String resolution, boolean saveMood) {
    }

    private final BrainstormService service;
    private final ApplicationConfig config;
    private final BrainstormContext context;
    private final Consumer<Result> onAdopt;

    /** The state of every chat of the history that was touched, by session id. */
    private final Map<String, BrainstormChat> chats = new HashMap<>();
    /** The chat that is shown. */
    private BrainstormChat chat = new BrainstormChat(null);

    private final BrainstormHistory history;
    private final BrainstormChatView chatView;
    private final BrainstormResultForm resultForm;

    /**
     * Creates the dialog.
     *
     * @param owner the owner window
     * @param config the accounts and the remembered resolutions (may be null: none)
     * @param service the brainstorm service
     * @param context the selectable values and the current main window state
     * @param currentModel localized display name of the model currently selected in the main window
     * @param onAdopt called with the adopted values when the user clicks "Adopt"
     */
    public BrainstormDialog(Stage owner, ApplicationConfig config, BrainstormService service,
                            BrainstormContext context, String currentModel, Consumer<Result> onAdopt) {
        this.config = config;
        this.service = service;
        this.context = context;
        this.onAdopt = onAdopt;

        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(I18nManager.getText("brainstorm.title"));

        history = new BrainstormHistory(service, (BrainstormSession listed) -> chatOf(listed).markerProperty(),
                new HistoryListener());
        chatView = new BrainstormChatView(config, service, new ChatListener());
        resultForm = new BrainstormResultForm(config, context, currentModel, new ResultListener());
        SplitPane root = new SplitPane(history, chatView, resultForm);
        root.getStyleClass().add("grip-split-pane");
        root.setDividerPositions(0.19, 0.70);
        SplitPane.setResizableWithParent(history, false);
        SplitPane.setResizableWithParent(resultForm, false);
        setScene(new Scene(root, DIALOG_WIDTH, DIALOG_HEIGHT));
        getScene().getStylesheets().addAll(
                BrainstormDialog.class.getResource("/css/logo-colors.css").toExternalForm(),
                BrainstormDialog.class.getResource("/css/split-pane-grips.css").toExternalForm());
        setMinWidth(800);
        setMinHeight(520);

        if (owner != null) {
            setX(owner.getX() + (owner.getWidth() - DIALOG_WIDTH) / 2);
            setY(owner.getY() + (owner.getHeight() - DIALOG_HEIGHT) / 2);
        }

        service.setOnTitleChanged((BrainstormSession named) -> Platform.runLater(history::refresh));
        service.setOnToolStatus((String sessionId, String status) -> Platform.runLater(() -> {
            BrainstormChat researching = chats.get(sessionId);
            if (researching != null && researching.busy) {
                researching.statusText = I18nManager.getText("brainstorm.research.status",
                        BrainstormChatView.shorten(status, 90));
                if (researching == chat)
                    chatView.applyBusy(researching);
            }
        }));

        showNewSession();
        history.refresh();

        // The chat that was worked on last opens, wherever the user put it in the history
        history.selectLast();
    }

    /** What the history does in the window. */
    private final class HistoryListener implements BrainstormHistory.Listener {

        @Override
        public void open(BrainstormSession session) {
            loadSession(session);
        }

        @Override
        public void startNew() {
            showNewSession();
        }

        @Override
        public void delete(BrainstormSession session) {
            deleteSession(session);
        }

        @Override
        public BrainstormSession shown() {
            return chat.session;
        }

        @Override
        public void listed() {
            showChatTitle();
        }
    }

    /** What the chat does in the window. */
    private final class ChatListener implements BrainstormChatView.Listener {

        @Override
        public void send(String text) {
            BrainstormDialog.this.send(text);
        }

        @Override
        public void sendEvent(JSONObject event) {
            BrainstormDialog.this.sendEvent(event);
        }

        @Override
        public void engineChosen(String engine) {
            chat.engine = engine;
            if (chat.session != null)
                service.setEngine(chat.session, engine);
            if (config != null)
                config.accounts().setBrainstormEngine(engine);
        }
    }

    /** What the result does in the window. */
    private final class ResultListener implements BrainstormResultForm.Listener {

        @Override
        public void changed(JSONObject event) {
            sendEvent(event);
        }

        @Override
        public void promptEdited(String prompt) {
            if (chat.session == null)
                return;
            chat.draft.setPrompt(prompt);
            sendEvent(new JSONObject().put("event", "prompt_edited").put("prompt", prompt));
        }

        @Override
        public void adopted(Result result) {

            /*  The AI reads it with the next message: another image asked for then starts a new round  */
            if (chat.session != null)
                service.noteAdopted(chat.session);
            onAdopt.accept(result);
            close();
        }
    }

    /** Shows the title of the shown chat as listed (the AI or the user may have renamed it), for a new chat its own. */
    private void showChatTitle() {
        chatView.showTitle(chat.session == null ? I18nManager.getText("brainstorm.session.new")
                : history.titleOf(chat.session));
    }

    /** Deletes a session, after a question if the AI is still working on it. Its running request is stopped. */
    private void deleteSession(BrainstormSession toDelete) {
        BrainstormChat running = chats.get(toDelete.getId());
        if (running != null && running.busy) {
            ButtonType yes = DialogButtons.yes();
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    I18nManager.getText("brainstorm.history.delete.running"), yes, DialogButtons.no());
            alert.initOwner(this);
            alert.setHeaderText(null);
            if (alert.showAndWait().orElse(null) != yes)
                return;
        }
        service.deleteSession(toDelete.getId());
        chats.remove(toDelete.getId());
        if (chat.session != null && chat.session.getId().equals(toDelete.getId()))
            showNewSession();
        history.refresh();
    }

    /** The chat of a session of the history, created empty on first use. */
    private BrainstormChat chatOf(BrainstormSession listed) {
        return chats.computeIfAbsent(listed.getId(), (String id) -> new BrainstormChat(listed));
    }

    /** Shows an empty new session. The session is stored with the first message. */
    private void showNewSession() {
        BrainstormChat fresh = new BrainstormChat(null);
        chatView.addBubble(fresh, I18nManager.getText("brainstorm.greeting"), false);
        fresh.quickReplies = List.of(I18nManager.getText("brainstorm.quick.image"),
                I18nManager.getText("brainstorm.quick.video"));
        fresh.loaded = true;
        activate(fresh);
        history.clearSelection();
    }

    private void loadSession(BrainstormSession selected) {
        BrainstormChat target = chatOf(selected);
        if (!target.loaded) {
            for (BrainstormMessage message : service.loadMessages(selected.getId())) {
                switch (message.getRole()) {
                    case BrainstormMessage.ROLE_USER -> chatView.addBubble(target, message.getContent(), true);
                    case BrainstormMessage.ROLE_EVENT -> chatView.addStoredEvent(target, message.getContent());
                    default -> {
                        try {
                            BrainstormReply reply = BrainstormReply.parse(message.getContent());
                            if (target.progress(reply))
                                chatView.addEvent(target, I18nManager.getText("brainstorm.round", reply.getRound()));
                            chatView.addBubble(target, reply.getMessage(), false);
                        }
                        catch (IllegalArgumentException e) {
                            logger.warn("Skipping unreadable stored brainstorm reply", e);
                        }
                    }
                }
            }
            target.draft = service.latestDraft(selected.getId()).orElseGet(BrainstormDraft::new);
            target.scrollPosition = 1.0;
            target.loaded = true;
        }
        activate(target);
    }

    /** Shows a chat in the parts: its messages, steps, draft, quick replies and whether it waits for the AI. */
    private void activate(BrainstormChat target) {
        chat = target;
        target.opened();
        showChatTitle();
        chatView.show(target);
        resultForm.show(target.draft);
        resultForm.setBusy(target.busy);
    }

    private void send(String text) {
        if (!ensureSession())
            return;
        BrainstormChat target = chat;
        chatView.addBubble(target, text, true);
        ask(target, () -> service.sendUserMessage(target.session, text, context));
    }

    private void sendEvent(JSONObject event) {
        if (chat.session == null || chat.busy)
            return;
        BrainstormChat target = chat;
        chatView.addEvent(target, chatView.describeEvent(event.toString()));
        ask(target, () -> service.sendEvent(target.session, event.toString(), context));
    }

    private boolean ensureSession() {
        if (chat.session != null)
            return true;
        chat.session = service.startSession(chatView.chosenEngine()).orElse(null);
        if (chat.session == null) {
            chatView.addEvent(chat, I18nManager.getText("brainstorm.error.session"));
            return false;
        }
        chats.put(chat.session.getId(), chat);
        history.refresh();
        return true;
    }

    /**
     * Runs a request of a chat. The chat waits for the AI, the other chats stay usable. The reply or the error goes
     * to this chat, whichever chat is shown when it arrives; the history list marks it if it is not the open one.
     */
    private void ask(BrainstormChat target, Supplier<CompletableFuture<BrainstormReply>> request) {
        target.started();
        target.statusText = I18nManager.getText("brainstorm.status.thinking");
        target.quickReplies = List.of();
        if (target == chat) {
            applyBusy(target);
            chatView.setQuickReplies(List.of());
        }

        request.get().whenComplete((BrainstormReply reply, Throwable error) -> Platform.runLater(() -> {
            Throwable cause = error instanceof CompletionException && error.getCause() != null
                    ? error.getCause() : error;
            boolean deleted = target.session == null || chats.get(target.session.getId()) != target;
            if (deleted || cause instanceof CancellationException)
                return;

            boolean open = target == chat;
            if (cause != null) {
                logger.error("Brainstorm request failed", cause);
                target.failed(open);
                chatView.addEvent(target, cause.getMessage());
            }
            else {
                target.answered(open);
                chatView.showReply(target, reply);
                target.draft = reply.getDraft();
                if (open)
                    resultForm.show(target.draft);
            }
            if (open)
                applyBusy(target);
            history.refresh();
        }));
    }

    /** Shows whether the shown chat waits for the AI: its input and the result are disabled meanwhile. */
    private void applyBusy(BrainstormChat target) {
        chatView.applyBusy(target);
        resultForm.setBusy(target.busy);
    }
}
