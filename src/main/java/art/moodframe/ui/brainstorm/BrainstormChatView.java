package art.moodframe.ui.brainstorm;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.ResearchNote;
import art.moodframe.service.brainstorm.BrainstormJson;
import art.moodframe.service.brainstorm.BrainstormReply;
import art.moodframe.service.brainstorm.BrainstormService;
import art.moodframe.service.brainstorm.YouTubeClip;
import art.moodframe.ui.component.DialogButtons;
import art.moodframe.ui.support.BoxInset;
import art.moodframe.ui.support.SystemOpen;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.json.JSONException;
import org.json.JSONObject;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The chat column of the brainstorm window: the title and the steps of the shown chat, its messages with the research
 * and the linked videos, the quick replies, the input with the choice of the language model, and the note that only
 * Gemini watches videos. Every chat keeps its messages in its {@link BrainstormChat}, so an answer lands in the chat
 * that asked, whichever is shown.
 *
 * @since 2.1.3
 */
final class BrainstormChatView extends VBox {

    private static final Logger logger = LoggerFactory.getLogger(BrainstormChatView.class);

    private static final String BUBBLE_STYLE = "-fx-padding: 8 12 8 12; -fx-background-radius: 10;";
    /** The width of the button column of the input row, as in the main window. */
    private static final double ACTIONS_WIDTH = 180.0;

    /** What the window does for the chat. */
    interface Listener {

        /** Sends a message of the user in the shown chat. */
        void send(String text);

        /** Tells the AI of the shown chat about a change. */
        void sendEvent(JSONObject event);

        /** The user chose the language model of the shown chat, "gemini" or "openai". */
        void engineChosen(String engine);
    }

    private final ApplicationConfig config;
    private final BrainstormService service;
    private final Listener listener;
    /** The chat that is shown. */
    private BrainstormChat chat = new BrainstormChat(null);
    private boolean updating;

    private final Label titleLabel = new Label();
    private final List<Text> stepTexts = new ArrayList<>();
    private final Label statusLabel = new Label();
    private final ScrollPane messageScroll = new ScrollPane();
    private final FlowPane quickReplyPane = new FlowPane(8.0, 8.0);
    private final TextArea inputArea = new TextArea();
    private final Button sendButton = new Button();
    /** Gemini or the OpenAI language model, for the shown chat; null if only one or none can answer. */
    private ComboBox<String> engineBox;
    private String geminiItem;
    private String openAiItem;
    private final ProgressIndicator progress = new ProgressIndicator();
    /** The note under the input that only Gemini can watch a linked video, while OpenAI answers. */
    private final HBox videoHint = new HBox(10.0);
    private final Hyperlink switchToGeminiLink = new Hyperlink();

    /**
     * Creates the column.
     *
     * @param config   the accounts (may be null: no choice of the language model)
     * @param service  the brainstorm service, which looks up the linked videos
     * @param listener what the window does
     */
    BrainstormChatView(ApplicationConfig config, BrainstormService service, Listener listener) {
        this.config = config;
        this.service = service;
        this.listener = listener;

        // Wraps into several lines instead of truncating the step names in a narrow window
        FlowPane stepper = new FlowPane(18.0, 4.0);
        stepper.setAlignment(Pos.CENTER);
        stepper.setPadding(new Insets(12.0));
        for (int i = 1; i <= BrainstormReply.MAX_STEP; i++) {
            Text text = new Text(i + "  " + I18nManager.getText("brainstorm.step." + i));
            int step = i;
            text.setOnMouseClicked((event) -> reopenStep(step));
            stepTexts.add(text);
            stepper.getChildren().add(text);
        }

        messageScroll.setFitToWidth(true);
        messageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(messageScroll, Priority.ALWAYS);

        quickReplyPane.setPadding(new Insets(0, 15.0, 0, BoxInset.LEFT));
        statusLabel.getStyleClass().addAll("text-muted", "text-small");
        statusLabel.setPadding(new Insets(0, 15.0, 6.0, BoxInset.LEFT));
        statusLabel.setVisible(false);
        statusLabel.managedProperty().bind(statusLabel.visibleProperty());

        inputArea.setPromptText(I18nManager.getText("brainstorm.input.prompt"));
        inputArea.setWrapText(true);
        inputArea.setPrefRowCount(4);
        inputArea.setMinWidth(0);
        HBox.setHgrow(inputArea, Priority.ALWAYS);

        /*  The field is flush with the chat, its text starts on the inset of the boxes  */
        inputArea.setPadding(new Insets(2.0, 2.0, 2.0, BoxInset.LEFT - BoxInset.TEXT));
        inputArea.setOnKeyPressed((event) -> {
            if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
                event.consume();
                sendInput();
            }
        });
        sendButton.setText(I18nManager.getText("brainstorm.send"));
        sendButton.getStyleClass().add("accent");
        sendButton.setMinWidth(Region.USE_PREF_SIZE);
        sendButton.setOnAction((event) -> sendInput());
        // The spinner sits in the status line of the chat, next to "researching ..."
        progress.setPrefSize(16, 16);
        progress.setMinSize(16, 16);
        progress.setMaxSize(16, 16);
        statusLabel.setGraphic(progress);
        statusLabel.setGraphicTextGap(8.0);

        // The AI researches on its own: the tooltip tells where the search terms go
        inputArea.setTooltip(new Tooltip(I18nManager.getText("brainstorm.research.hint")));

        /*  The language model belongs to the chat, so it is chosen where the chat is written  */
        engineBox = createEngineBox();
        VBox actions = new VBox(8.0, sendButton);
        if (engineBox != null)
            actions.getChildren().add(0, engineBox);

        /*  As in the main window: on top, level with the input field, and wide enough for the language model. A chat
            too narrow for both at 180 px takes from the field and the column alike.  */
        actions.setAlignment(Pos.TOP_CENTER);
        actions.setFillWidth(true);
        actions.setPrefWidth(ACTIONS_WIDTH);
        actions.setMaxWidth(ACTIONS_WIDTH);
        inputArea.setPrefWidth(ACTIONS_WIDTH);
        sendButton.setMaxWidth(Double.MAX_VALUE);

        /*  Flush with the message area at both sides, the gap above it is the divider  */
        HBox inputRow = new HBox(8.0, inputArea, actions);
        inputRow.setAlignment(Pos.BOTTOM_LEFT);
        VBox.setVgrow(inputRow, Priority.ALWAYS);
        VBox inputBox = new VBox(6.0, inputRow, createVideoHint());
        inputBox.setPadding(new Insets(0, 0, 15.0, 0));
        inputBox.setMinHeight(90.0);
        inputArea.textProperty().addListener((obs, old, text) -> updateVideoHint());
        if (engineBox != null)
            engineBox.valueProperty().addListener((obs, old, item) -> updateVideoHint());

        // The divider above the input row can be dragged, like in the main window
        VBox messages = new VBox(messageScroll, statusLabel, quickReplyPane);
        SplitPane chatSplit = new SplitPane(messages, inputBox);
        chatSplit.getStyleClass().add("grip-split-pane");
        chatSplit.setOrientation(Orientation.VERTICAL);
        chatSplit.setDividerPositions(0.72);
        SplitPane.setResizableWithParent(inputBox, false);

        /*  The title starts on the left edge of the bubbles (the inset of the boxes), 1 px further out for the side
            bearing of its letters  */
        getChildren().addAll(BrainstormHeading.row(titleLabel, BoxInset.LEFT - 1.0, 15.0), stepper, chatSplit);
        getStyleClass().add("bg-default");
        VBox.setVgrow(chatSplit, Priority.ALWAYS);
    }

    /**
     * Creates the choice between Google Gemini and the OpenAI language model of the settings. It lists only the
     * accounts whose last check found a language model and a web search, and is disabled with only one entry. The
     * choice belongs to the shown chat and is stored with it; the last choice is also the default for new chats.
     *
     * @return the choice, or null if nothing can answer
     */
    private ComboBox<String> createEngineBox() {
        if (config == null)
            return null;

        boolean geminiUsable = config.accounts().isBrainstormAvailable(ApiProvider.GOOGLE_GEMINI);
        boolean openAiUsable = config.accounts().isBrainstormAvailable(ApiProvider.OPENAI);
        if (!geminiUsable && !openAiUsable)
            return null;

        geminiItem = I18nManager.getText("brainstorm.engine.gemini");
        openAiItem = I18nManager.getText("brainstorm.engine.openai", config.accounts().getOpenAiChatModel());
        ComboBox<String> box = new ComboBox<>();
        if (geminiUsable)
            box.getItems().add(geminiItem);
        if (openAiUsable)
            box.getItems().add(openAiItem);
        box.setDisable(box.getItems().size() < 2);
        box.setMaxWidth(Double.MAX_VALUE);

        /*  The tooltip names the chosen entry in full, in case it is too long for the box  */
        String hint = I18nManager.getText("brainstorm.engine.tooltip");
        Tooltip tooltip = new Tooltip(hint);
        tooltip.textProperty().bind(box.valueProperty().map((String item) -> item + "\n" + hint).orElse(hint));
        box.setTooltip(tooltip);
        box.valueProperty().addListener((obs, old, selected) -> {
            if (updating || selected == null)
                return;

            listener.engineChosen(openAiItem.equals(selected) ? "openai" : "gemini");
        });
        return box;
    }

    /** Shows the language model of a chat: its own, else the default; one that cannot answer any more is replaced. */
    private void showEngine(BrainstormChat target) {
        if (engineBox == null)
            return;

        String engine = target.session != null && target.session.getEngine() != null ? target.session.getEngine()
                : target.engine != null ? target.engine
                : config != null ? config.accounts().getBrainstormEngine() : "gemini";
        String item = "openai".equals(engine) ? openAiItem : geminiItem;
        updating = true;
        engineBox.setValue(engineBox.getItems().contains(item) ? item : engineBox.getItems().get(0));
        updating = false;
    }

    /**
     * The language model chosen for the shown chat.
     *
     * @return "gemini" or "openai", null if there is no choice
     */
    String chosenEngine() {
        return engineBox == null || engineBox.getValue() == null ? null
                : openAiItem.equals(engineBox.getValue()) ? "openai" : "gemini";
    }

    /**
     * Shows the title of the shown chat.
     *
     * @param title the title
     */
    void showTitle(String title) {
        titleLabel.setText(title);
    }

    /**
     * Creates the note that only Gemini can watch YouTube videos, with the choice to switch to Gemini or to send the
     * message without its links.
     */
    private HBox createVideoHint() {
        FontIcon icon = new FontIcon("mdi2y-youtube");
        icon.setIconSize(16);
        icon.getStyleClass().add("warning");
        Label text = new Label(I18nManager.getText("brainstorm.video.openai.hint"));
        text.setWrapText(true);
        text.getStyleClass().add("text-small");
        switchToGeminiLink.setText(I18nManager.getText("brainstorm.video.openai.switch"));
        switchToGeminiLink.setOnAction((event) -> engineBox.setValue(geminiItem));
        switchToGeminiLink.managedProperty().bind(switchToGeminiLink.visibleProperty());
        Hyperlink sendLink = new Hyperlink(I18nManager.getText("brainstorm.video.openai.send"));
        sendLink.setOnAction((event) -> sendWithoutVideo());
        videoHint.getChildren().addAll(icon, text, switchToGeminiLink, sendLink);
        videoHint.setAlignment(Pos.CENTER_LEFT);
        videoHint.managedProperty().bind(videoHint.visibleProperty());
        videoHint.setVisible(false);
        return videoHint;
    }

    /**
     * Shows the note under the input while OpenAI answers the shown chat and the input links a YouTube video, which
     * only Gemini can watch. Until the user switches to Gemini or sends without the video, nothing is sent.
     */
    private void updateVideoHint() {
        boolean show = !chat.busy && "openai".equals(chosenEngine())
                && !YouTubeClip.findAll(inputArea.getText()).isEmpty();
        videoHint.setVisible(show);
        switchToGeminiLink.setVisible(engineBox != null && engineBox.getItems().contains(geminiItem));
        sendButton.setDisable(chat.busy || show);
    }

    /** Sends the input without its YouTube links, as OpenAI cannot watch them. */
    private void sendWithoutVideo() {
        String text = YouTubeClip.withoutLinks(inputArea.getText().trim());
        inputArea.clear();
        if (!text.isEmpty() && !chat.busy)
            listener.send(text);
    }

    private void sendInput() {
        String text = inputArea.getText().trim();
        if (text.isEmpty() || chat.busy || videoHint.isVisible())
            return;
        inputArea.clear();
        listener.send(text);
    }

    /**
     * Shows a chat: its messages, steps, quick replies, input, language model and whether it waits for the AI. The
     * input and the scroll position of the chat shown before stay with it.
     *
     * @param target the chat
     */
    void show(BrainstormChat target) {
        chat.inputText = inputArea.getText();
        chat.scrollPosition = messageScroll.getVvalue();
        chat = target;
        messageScroll.setContent(target.messageBox);
        setQuickReplies(target.quickReplies);
        updateStepper();
        inputArea.setText(target.inputText);
        showEngine(target);
        applyBusy(target);
        Platform.runLater(() -> messageScroll.setVvalue(target.scrollPosition >= 1.0
                ? messageScroll.getVmax() : target.scrollPosition));
    }

    /**
     * Shows the reply of the AI in its chat: the research, a new round, the message and the quick replies.
     *
     * @param target the chat that asked
     * @param reply  the reply
     */
    void showReply(BrainstormChat target, BrainstormReply reply) {
        reply.getResearch().forEach((ResearchNote note) -> addResearch(target, note));
        if (target.progress(reply))
            addEvent(target, I18nManager.getText("brainstorm.round", reply.getRound()));
        addBubble(target, reply.getMessage(), false);
        target.quickReplies = reply.getQuickReplies();
        if (target == chat) {
            setQuickReplies(target.quickReplies);
            updateStepper();
        }
    }

    /**
     * Shows whether the shown chat waits for the AI: the status line and the input are disabled only for this chat.
     *
     * @param target the shown chat
     */
    void applyBusy(BrainstormChat target) {
        statusLabel.setText(target.statusText);
        statusLabel.setVisible(target.busy);
        inputArea.setDisable(target.busy);
        if (engineBox != null)
            engineBox.setDisable(target.busy || engineBox.getItems().size() < 2);
        updateVideoHint();
    }

    /**
     * Shows the progress of the shown chat: finished steps get a check mark (also after the current one, when a round
     * went back), the current one is highlighted, open ones are neutral, skipped ones grey and struck through. A
     * finished or skipped step can be clicked to open it again.
     */
    private void updateStepper() {
        for (int i = 1; i <= stepTexts.size(); i++) {
            Text text = stepTexts.get(i - 1);
            BrainstormChat.StepState state = chat.stateOf(i);
            boolean done = state == BrainstormChat.StepState.DONE;
            boolean skipped = state == BrainstormChat.StepState.SKIPPED;
            text.setText((done ? "✓" : String.valueOf(i)) + "  " + I18nManager.getText("brainstorm.step." + i));
            text.setStrikethrough(skipped);
            text.setStyle("-fx-font-size: 13px; " + switch (state) {
                case CURRENT -> "-fx-fill: -color-accent-fg; -fx-font-weight: bold;";
                case SKIPPED -> "-fx-fill: -color-fg-subtle;";
                case DONE    -> "-fx-fill: -color-success-fg;";
                case OPEN    -> "-fx-fill: -color-fg-muted;";
            });
            text.setCursor(done || skipped ? Cursor.HAND : Cursor.DEFAULT);
        }
    }

    /** Asks the AI to open a finished or skipped step again; the later steps that build on it become open. */
    private void reopenStep(int step) {
        BrainstormChat.StepState state = chat.stateOf(step);
        if (chat.session == null || chat.busy
                || state != BrainstormChat.StepState.DONE && state != BrainstormChat.StepState.SKIPPED)
            return;

        listener.send(I18nManager.getText("brainstorm.step.reopen", I18nManager.getText("brainstorm.step." + step)));
    }

    /**
     * Adds a message to a chat: one of the user, with a card for each linked video, or one of the AI in light
     * Markdown.
     *
     * @param target   the chat
     * @param text     the message
     * @param fromUser whether the user wrote it
     */
    void addBubble(BrainstormChat target, String text, boolean fromUser) {
        Region bubble;
        if (fromUser) {
            Label label = new Label(text);
            label.setWrapText(true);
            bubble = label;
        }
        else {

            /*  The AI writes light Markdown: bold, italic and bullets show, the markers vanish  */
            bubble = BrainstormMarkdown.toFlow(text);
        }
        bubble.setMaxWidth(520.0);
        bubble.setStyle(BUBBLE_STYLE + (fromUser
                ? "-fx-background-color: -color-accent-emphasis; -fx-text-fill: -color-fg-emphasis;"
                : "-fx-background-color: -color-bg-subtle;"));
        HBox row = new HBox(bubble);
        row.setAlignment(fromUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        appendToChat(target, row, fromUser);
        if (!fromUser)
            return;

        /*  Every linked video gets a card under the message, its range belongs to this message  */
        for (YouTubeClip clip : YouTubeClip.findAll(text)) {
            BrainstormVideoCard card = new BrainstormVideoCard(clip, service,
                    (BrainstormVideoCard clicked) -> changeRange(target, clicked, text), SystemOpen::browse);
            target.videoCards.add(card);
            HBox cardRow = new HBox(card);
            cardRow.setAlignment(Pos.CENTER_RIGHT);
            appendToChat(target, cardRow, true);
        }
    }

    /**
     * Asks for a new time range of a linked video and lets Gemini watch the new clip, with the message that linked
     * the video as instruction. OpenAI cannot watch videos, so then the chat only shows the note.
     */
    private void changeRange(BrainstormChat target, BrainstormVideoCard card, String instruction) {
        if (target != chat || target.session == null || target.busy)
            return;
        if ("openai".equals(chosenEngine())) {
            addEvent(target, I18nManager.getText("brainstorm.video.openai.hint"));
            return;
        }

        YouTubeClip clip = card.clip();
        TextInputDialog dialog = new TextInputDialog(clip.hasRange() ? clip.rangeLabel() : "");
        dialog.getDialogPane().getButtonTypes().setAll(DialogButtons.ok(), DialogButtons.cancel());
        dialog.initOwner(getScene() != null ? getScene().getWindow() : null);
        dialog.setTitle(I18nManager.getText("brainstorm.video.range.title"));
        dialog.setHeaderText(null);
        dialog.setContentText(I18nManager.getText("brainstorm.video.range.prompt"));
        String text = dialog.showAndWait().orElse(null);
        if (text == null)
            return;

        YouTubeClip changed = clip.withRange(text);
        if (changed == null) {
            addEvent(target, I18nManager.getText("brainstorm.video.range.invalid", text.trim()));
            return;
        }
        if (changed.equals(clip))
            return;
        card.setClip(changed);
        listener.sendEvent(BrainstormService.videoRangeEvent(changed, instruction));
    }

    /**
     * Adds a short line to a chat, such as an app event or an error.
     *
     * @param target the chat
     * @param text   the line
     */
    void addEvent(BrainstormChat target, String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().addAll("text-muted", "text-small");
        HBox row = new HBox(label);
        row.setAlignment(Pos.CENTER);
        appendToChat(target, row, false);
    }

    /**
     * Adds a row to the chat and scrolls down only if the user was already at the bottom (so that reading further
     * up is not interrupted), or if the user just wrote the message.
     */
    private void appendToChat(BrainstormChat target, Node row, boolean alwaysScroll) {
        if (target != chat) {
            target.messageBox.getChildren().add(row);
            target.scrollPosition = 1.0;
            return;
        }
        boolean follow = alwaysScroll || messageScroll.getVvalue() >= messageScroll.getVmax() - 0.02
                || target.messageBox.getHeight() <= messageScroll.getViewportBounds().getHeight();
        target.messageBox.getChildren().add(row);
        if (follow)
            Platform.runLater(() -> messageScroll.setVvalue(messageScroll.getVmax()));
    }

    /**
     * Shows the quick replies of the shown chat.
     *
     * @param replies the replies, empty for none
     */
    void setQuickReplies(List<String> replies) {
        quickReplyPane.getChildren().clear();
        quickReplyPane.setManaged(!replies.isEmpty());
        for (String reply : replies) {
            Button button = new Button(reply);
            button.getStyleClass().add("button-outlined");
            button.setOnAction((event) -> listener.send(reply));
            quickReplyPane.getChildren().add(button);
        }
    }

    /**
     * Shows a stored event: a research result as sources row, any other event as a short line.
     *
     * @param target the chat
     * @param stored the stored event, as text with a prefix
     */
    void addStoredEvent(BrainstormChat target, String stored) {
        try {
            JSONObject event = new JSONObject(stored.substring(stored.indexOf('{')));
            if (BrainstormJson.RESEARCH_EVENT.equals(event.optString("event"))) {
                addResearch(target, BrainstormJson.researchNote(event));
                return;
            }

            /*  The card of the video shows the changed range, as the message still names the old one  */
            YouTubeClip changed = YouTubeClip.of(event.optString("url"), event.optInt("start_seconds"),
                    event.optInt("end_seconds"));
            if ("video_range_changed".equals(event.optString("event")) && changed != null) {
                for (int i = target.videoCards.size() - 1; i >= 0; i--) {
                    if (target.videoCards.get(i).clip().videoId().equals(changed.videoId())) {
                        target.videoCards.get(i).setClip(changed);
                        break;
                    }
                }
            }
        }
        catch (JSONException | IndexOutOfBoundsException e) {
            logger.debug("Stored event is not JSON: {}", stored);
        }
        addEvent(target, describeEvent(stored));
    }

    /**
     * Shows what was researched with its sources as domains; a click opens the page in the browser. A watched video
     * shows as its source with title and range.
     */
    private void addResearch(BrainstormChat target, ResearchNote note) {
        if (note.sources().size() == 1 && YouTubeClip.of(note.sources().get(0).url(), 0, 0) != null) {
            Label watched = new Label(I18nManager.getText("brainstorm.event.video"));
            watched.getStyleClass().addAll("text-muted", "text-small");
            Hyperlink link = new Hyperlink(shorten(note.query(), 90));
            link.setStyle("-fx-font-size: 11px;");
            link.setOnAction((event) -> SystemOpen.browse(note.sources().get(0).url()));
            HBox row = new HBox(4.0, watched, link);
            row.setAlignment(Pos.CENTER);
            appendToChat(target, row, false);
            return;
        }

        Label head = new Label(I18nManager.getText("brainstorm.event.research", shorten(note.query(), 90)));
        head.setWrapText(true);
        head.getStyleClass().addAll("text-muted", "text-small");

        FlowPane links = new FlowPane(10.0, 2.0);
        links.setAlignment(Pos.CENTER);
        Map<String, String> byDomain = new LinkedHashMap<>();
        note.sources().forEach((ResearchNote.Source source) -> byDomain.putIfAbsent(source.domain(), source.url()));
        byDomain.forEach((String domain, String url) -> {
            Hyperlink link = new Hyperlink(domain);
            link.setStyle("-fx-font-size: 11px;");
            link.setOnAction((event) -> SystemOpen.browse(url));
            links.getChildren().add(link);
        });

        VBox box = new VBox(2.0, head, links);
        box.setAlignment(Pos.CENTER);
        appendToChat(target, box, false);
    }

    /**
     * Shortens a text for a line of the chat.
     *
     * @param text      the text
     * @param maxLength the longest text kept
     * @return the text, cut with an ellipsis if it is longer
     */
    static String shorten(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) + "…" : text;
    }

    /**
     * Describes an app event (stored as text with a prefix) as a short line for the chat.
     *
     * @param stored the event
     * @return the line, the event itself if it is no JSON
     */
    String describeEvent(String stored) {
        try {
            JSONObject event = new JSONObject(stored.substring(stored.indexOf('{')));
            return switch (event.optString("event")) {
                case "target_model_changed" -> I18nManager.getText("brainstorm.event.model",
                        event.optString("from"), event.optString("to"));
                case "prompt_edited" -> I18nManager.getText("brainstorm.event.prompt");
                case "draft_adopted" -> I18nManager.getText("brainstorm.event.adopted");
                case "video_range_changed" -> {
                    YouTubeClip clip = YouTubeClip.of(event.optString("url"), event.optInt("start_seconds"),
                            event.optInt("end_seconds"));
                    yield I18nManager.getText("brainstorm.event.video.range", clip != null && clip.hasRange()
                            ? clip.rangeLabel() : I18nManager.getText("brainstorm.video.whole"));
                }
                case "fact_changed" -> I18nManager.getText(event.optBoolean("confirmed")
                        ? "brainstorm.event.fact.kept" : "brainstorm.event.fact.dropped",
                        shorten(event.optString("text"), 80));
                default -> I18nManager.getText("brainstorm.event.setting",
                        event.optString("name"), event.optString("value"));
            };
        }
        catch (JSONException | IndexOutOfBoundsException e) {
            return stored;
        }
    }
}
