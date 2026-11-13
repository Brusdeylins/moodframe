package art.moodframe.ui.dialog;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.BrainstormFact;
import art.moodframe.model.BrainstormMessage;
import art.moodframe.model.BrainstormReply;
import art.moodframe.model.BrainstormSession;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.Mood;
import art.moodframe.model.ResearchNote;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.BrainstormContext;
import art.moodframe.service.BrainstormService;
import art.moodframe.service.CameraPromptBuilder;
import art.moodframe.service.MoodService;
import art.moodframe.ui.BoxInset;
import art.moodframe.ui.cell.BrainstormSessionListCell;
import art.moodframe.ui.component.ColorBox;
import art.moodframe.model.AspectRatios;
import art.moodframe.util.I18nManager;
import art.moodframe.model.ModelFamilies;
import art.moodframe.util.YouTubeClip;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.json.JSONException;
import org.json.JSONObject;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Popup of the brainstorm assistant: a guided AI chat that develops a prompt together with the
 * suggested settings. The history of earlier sessions is listed on the left, the result on the right.
 *
 * <p>The target model starts with the model of the main window and is always adopted. All other
 * values are adopted only if their checkbox is ticked. Changes in the result panel are sent to the
 * AI as app events, so it can rewrite the prompt.</p>
 *
 * <p>The look is a mood and the building blocks style, atmosphere, light and color: each one an entry of the lists or
 * a new block the AI proposes, which goes into the list of the user on "Adopt". A field locked in the main window is
 * not adopted.</p>
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
    private static final String NONE_ITEM_KEY = "brainstorm.none";
    private static final String BUBBLE_STYLE = "-fx-padding: 8 12 8 12; -fx-background-radius: 10;";
    private static final List<String> DURATIONS = List.of("3s", "4s", "5s", "6s", "7s", "8s", "9s", "10s");
    /** The width of the button column of the input row, as in the main window. */
    private static final double ACTIONS_WIDTH = 180.0;

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
    private final BrainstormContext context;
    private final Consumer<Result> onAdopt;

    /** The state of every chat of the history that was touched, by session id. */
    private final Map<String, BrainstormChat> chats = new HashMap<>();
    /** The chat that is shown. */
    private BrainstormChat chat = new BrainstormChat(null);
    private boolean updatingUi;

    // History
    private final ListView<BrainstormSession> historyList = new ListView<>();

    // Chat
    private final Label chatTitleLabel = new Label();
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

    // Result panel
    private final VBox resultPanel = new VBox(8.0);
    private final TextArea promptArea = new TextArea();
    private final Label promptCounter = new Label();
    /** Holds the variant (the main window's dropdown item) that the family and the resolution select; not shown. */
    private final ChoiceBox<String> modelBox = new ChoiceBox<>();
    private final ChoiceBox<String> familyBox = new ChoiceBox<>();
    private final ChoiceBox<String> resolutionBox = new ChoiceBox<>();
    private final VBox resolutionRow = new VBox(3.0);
    private final ChoiceBox<String> moodBox = new ChoiceBox<>();
    private final CheckBox moodCheck = new CheckBox();
    private final Map<Mood.Field, BlockRow> blockRows = new EnumMap<>(Mood.Field.class);
    private final Label styleReasonLabel = new Label();
    private final CheckBox saveMoodCheck = new CheckBox();
    private final ChoiceBox<String> ratioBox = new ChoiceBox<>();
    private final CheckBox ratioCheck = new CheckBox();
    private final VBox videoBox = new VBox(8.0);
    private final ChoiceBox<String> durationBox = new ChoiceBox<>();
    private VBox durationRow;
    private final CheckBox durationCheck = new CheckBox();
    private final VBox factsBox = new VBox(6.0);
    private final Button adoptButton = new Button();

    /**
     * Creates the dialog.
     *
     * @param owner the owner window
     * @param service the brainstorm service
     * @param context the selectable values and the current main window state
     * @param currentModel localized display name of the model currently selected in the main window
     * @param onAdopt called with the adopted values when the user clicks "Adopt"
     */
    public BrainstormDialog(Stage owner, BrainstormService service, BrainstormContext context,
                            String currentModel, Consumer<Result> onAdopt) {
        this.service = service;
        this.context = context;
        this.onAdopt = onAdopt;
        service.setOnTitleChanged((BrainstormSession named) -> Platform.runLater(() -> refreshHistory(chat.session)));
        service.setOnToolStatus((String sessionId, String status) -> Platform.runLater(() -> {
            BrainstormChat researching = chats.get(sessionId);
            if (researching != null && researching.busy) {
                researching.statusText = I18nManager.getText("brainstorm.research.status", shorten(status, 90));
                if (researching == chat) {
                    applyBusy(researching);
                }
            }
        }));

        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(I18nManager.getText("brainstorm.title"));

        VBox history = createHistory();
        VBox chat = createChat();
        VBox result = createResultPanel();
        SplitPane root = new SplitPane(history, chat, result);
        root.getStyleClass().add("grip-split-pane");
        root.setDividerPositions(0.19, 0.70);
        SplitPane.setResizableWithParent(history, false);
        SplitPane.setResizableWithParent(result, false);
        setScene(new Scene(root, DIALOG_WIDTH, DIALOG_HEIGHT));
        getScene().getStylesheets().add(
                BrainstormDialog.class.getResource("/css/split-pane-grips.css").toExternalForm());
        setMinWidth(800);
        setMinHeight(520);

        if (owner != null) {
            setX(owner.getX() + (owner.getWidth() - DIALOG_WIDTH) / 2);
            setY(owner.getY() + (owner.getHeight() - DIALOG_HEIGHT) / 2);
        }

        updatingUi = true;
        modelBox.getItems().setAll(context.modelNames());
        familyBox.getItems().setAll(context.modelNames().stream().map(ModelFamilies::familyOf).distinct().toList());
        modelBox.setValue(currentModel);
        updatingUi = false;
        showVariant(currentModel, null);
        refreshModelDependent();
        showNewSession();
        refreshHistory(null);

        // The history is sorted by the latest change, so the first entry is the chat that was worked on last
        if (!historyList.getItems().isEmpty()) {
            historyList.getSelectionModel().select(0);
        }
    }

    // ===== History =====

    private VBox createHistory() {
        FontIcon newIcon = new FontIcon("mdi2p-plus");
        newIcon.setIconSize(20);
        Button newButton = new Button();
        newButton.setGraphic(newIcon);
        newButton.getStyleClass().addAll("button-icon", "flat");
        newButton.setTooltip(new Tooltip(I18nManager.getText("brainstorm.history.new")));
        newButton.setOnAction(event -> showNewSession());

        historyList.setCellFactory(list -> new BrainstormSessionListCell(this::deleteSession, this::renameSession,
                (BrainstormSession listed) -> chatOf(listed).markerProperty()));
        historyList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null && (chat.session == null || !selected.getId().equals(chat.session.getId()))) {
                loadSession(selected);
            }
        });
        VBox.setVgrow(historyList, Priority.ALWAYS);

        /*  The heading starts on the inset of the boxes, the line of the titles in the list. The gap to the chat is
            the divider, the list ends at the bottom with the input field and "Adopt".  */
        Label title = new Label(I18nManager.getText("brainstorm.history.title"));
        VBox box = new VBox(headingRow(title, BoxInset.LEFT, 0, newButton), historyList);
        box.getStyleClass().add("bg-inset");
        box.setPadding(new Insets(0, 0, 15.0, 10.0));
        box.setPrefWidth(240.0);
        box.setMinWidth(180.0);
        box.setMaxWidth(420.0);
        return box;
    }

    /**
     * The heading row of a column, as high as the heading rows of the main window, so that the headings of the three
     * columns share one baseline. The heading starts at the given left inset, the actions sit on the right.
     */
    private static HBox headingRow(Label title, double left, double right, javafx.scene.Node... actions) {
        title.getStyleClass().add("title-4");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(5.0, title, spacer);
        row.getChildren().addAll(actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10.0, right, 15.0, left));
        row.setMinHeight(45.0);
        row.setPrefHeight(45.0);
        row.setMaxHeight(45.0);
        return row;
    }

    /**
     * Creates the choice between Google Gemini and the OpenAI language model of the settings, that passed the test
     * there. It lists what can answer and is disabled with only one entry. The choice belongs to the shown chat and
     * is stored with it; the last choice is also the default for new chats.
     *
     * @return the choice, or null if nothing can answer
     */
    private ComboBox<String> createEngineBox() {
        ApplicationConfig appConfig = config();
        if (appConfig == null)
            return null;

        boolean geminiUsable = appConfig.hasGeminiApiKey() && appConfig.isGeminiProviderEnabled();
        boolean openAiUsable = appConfig.isOpenAiBrainstormAvailable();
        if (!geminiUsable && !openAiUsable)
            return null;

        geminiItem = I18nManager.getText("brainstorm.engine.gemini");
        openAiItem = I18nManager.getText("brainstorm.engine.openai", appConfig.getOpenAiChatModel());
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
            if (updatingUi || selected == null)
                return;

            String engine = openAiItem.equals(selected) ? "openai" : "gemini";
            chat.engine = engine;
            if (chat.session != null)
                service.setEngine(chat.session, engine);
            appConfig.setBrainstormEngine(engine);
        });
        return box;
    }

    /** Shows the language model of a chat: its own, else the default; one that cannot answer any more is replaced. */
    private void showEngine(BrainstormChat target) {
        if (engineBox == null)
            return;

        ApplicationConfig appConfig = config();
        String engine = target.session != null && target.session.getEngine() != null ? target.session.getEngine()
                : target.engine != null ? target.engine
                : appConfig != null ? appConfig.getBrainstormEngine() : "gemini";
        String item = "openai".equals(engine) ? openAiItem : geminiItem;
        updatingUi = true;
        engineBox.setValue(engineBox.getItems().contains(item) ? item : engineBox.getItems().get(0));
        updatingUi = false;
    }

    /** The language model chosen for the shown chat, null if there is no choice. */
    private String chosenEngine() {
        return engineBox == null || engineBox.getValue() == null ? null
                : openAiItem.equals(engineBox.getValue()) ? "openai" : "gemini";
    }

    private void refreshHistory(BrainstormSession select) {
        updatingUi = true;
        historyList.getItems().setAll(service.listSessions());
        if (select != null) {
            historyList.getItems().stream().filter(s -> s.getId().equals(select.getId())).findFirst()
                    .ifPresent(s -> historyList.getSelectionModel().select(s));
        } else {
            historyList.getSelectionModel().clearSelection();
        }
        updatingUi = false;
        showChatTitle();
    }

    /** Shows the title of the shown chat as listed (the AI or the user may have renamed it), for a new chat its own. */
    private void showChatTitle() {
        String title = chat.session == null ? I18nManager.getText("brainstorm.session.new")
                : historyList.getItems().stream()
                        .filter((BrainstormSession listed) -> listed.getId().equals(chat.session.getId()))
                        .map(BrainstormSession::getTitle).findFirst().orElse(chat.session.getTitle());
        chatTitleLabel.setText(title);
    }

    private void renameSession(BrainstormSession toRename, String title) {
        service.renameSession(toRename.getId(), title);
        toRename.setTitle(title);
        refreshHistory(chat.session);
    }

    /** Deletes a session, after a question if the AI is still working on it. Its running request is stopped. */
    private void deleteSession(BrainstormSession toDelete) {
        BrainstormChat running = chats.get(toDelete.getId());
        if (running != null && running.busy) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    I18nManager.getText("brainstorm.history.delete.running"), ButtonType.YES, ButtonType.NO);
            alert.initOwner(this);
            alert.setHeaderText(null);
            if (alert.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                return;
            }
        }
        service.deleteSession(toDelete.getId());
        chats.remove(toDelete.getId());
        boolean wasOpen = chat.session != null && chat.session.getId().equals(toDelete.getId());
        if (wasOpen) {
            showNewSession();
        }
        refreshHistory(wasOpen ? null : chat.session);
    }

    /** The chat of a session of the history, created empty on first use. */
    private BrainstormChat chatOf(BrainstormSession listed) {
        return chats.computeIfAbsent(listed.getId(), (String id) -> new BrainstormChat(listed));
    }

    // ===== Chat =====

    private VBox createChat() {
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
        inputArea.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER && !event.isShiftDown()) {
                event.consume();
                sendInput();
            }
        });
        sendButton.setText(I18nManager.getText("brainstorm.send"));
        sendButton.getStyleClass().add("accent");
        sendButton.setMinWidth(Region.USE_PREF_SIZE);
        sendButton.setOnAction(event -> sendInput());
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
        chatSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        chatSplit.setDividerPositions(0.72);
        SplitPane.setResizableWithParent(inputBox, false);

        /*  The title starts on the left edge of the bubbles (the inset of the boxes), 1 px further out for the side
            bearing of its letters  */
        VBox box = new VBox(headingRow(chatTitleLabel, BoxInset.LEFT - 1.0, 15.0), stepper, chatSplit);
        box.getStyleClass().add("bg-default");
        VBox.setVgrow(chatSplit, Priority.ALWAYS);
        return box;
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
        switchToGeminiLink.setOnAction(event -> engineBox.setValue(geminiItem));
        switchToGeminiLink.managedProperty().bind(switchToGeminiLink.visibleProperty());
        Hyperlink sendLink = new Hyperlink(I18nManager.getText("brainstorm.video.openai.send"));
        sendLink.setOnAction(event -> sendWithoutVideo());
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
            send(text);
    }

    /** Shows an empty new session. The session is stored with the first message. */
    private void showNewSession() {
        BrainstormChat fresh = new BrainstormChat(null);
        addBubble(fresh, I18nManager.getText("brainstorm.greeting"), false);
        fresh.quickReplies = List.of(I18nManager.getText("brainstorm.quick.image"),
                I18nManager.getText("brainstorm.quick.video"));
        fresh.loaded = true;
        activate(fresh);
        historyList.getSelectionModel().clearSelection();
    }

    private void loadSession(BrainstormSession selected) {
        BrainstormChat target = chatOf(selected);
        if (!target.loaded) {
            for (BrainstormMessage message : service.loadMessages(selected.getId())) {
                switch (message.getRole()) {
                    case BrainstormMessage.ROLE_USER -> addBubble(target, message.getContent(), true);
                    case BrainstormMessage.ROLE_EVENT -> addStoredEvent(target, message.getContent());
                    default -> {
                        try {
                            BrainstormReply reply = BrainstormReply.parse(message.getContent());
                            if (target.progress(reply))
                                addEvent(target, I18nManager.getText("brainstorm.round", reply.getRound()));
                            addBubble(target, reply.getMessage(), false);
                        } catch (IllegalArgumentException e) {
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

    /** Shows a chat: its messages, steps, draft, quick replies and whether it waits for the AI. */
    private void activate(BrainstormChat target) {
        chat.inputText = inputArea.getText();
        chat.scrollPosition = messageScroll.getVvalue();
        chat = target;
        target.opened();

        showChatTitle();
        messageScroll.setContent(target.messageBox);
        setQuickReplies(target.quickReplies);
        updateStepper();
        updatingUi = true;
        promptArea.clear();
        updatingUi = false;
        applyDraft(target.draft);
        inputArea.setText(target.inputText);
        showEngine(target);
        applyBusy(target);
        Platform.runLater(() -> messageScroll.setVvalue(target.scrollPosition >= 1.0
                ? messageScroll.getVmax() : target.scrollPosition));
    }

    private void sendInput() {
        String text = inputArea.getText().trim();
        if (text.isEmpty() || chat.busy || videoHint.isVisible()) {
            return;
        }
        inputArea.clear();
        send(text);
    }

    private void send(String text) {
        if (!ensureSession()) {
            return;
        }
        BrainstormChat target = chat;
        addBubble(target, text, true);
        ask(target, () -> service.sendUserMessage(target.session, text, context));
    }

    private void sendEvent(JSONObject event) {
        if (chat.session == null || updatingUi || chat.busy) {
            return;
        }
        BrainstormChat target = chat;
        addEvent(target, describeEvent(event.toString()));
        ask(target, () -> service.sendEvent(target.session, event.toString(), context));
    }

    private boolean ensureSession() {
        if (chat.session != null) {
            return true;
        }
        chat.session = service.startSession(chosenEngine()).orElse(null);
        if (chat.session == null) {
            addEvent(chat, I18nManager.getText("brainstorm.error.session"));
            return false;
        }
        chats.put(chat.session.getId(), chat);
        refreshHistory(chat.session);
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
            setQuickReplies(List.of());
        }

        request.get().whenComplete((reply, error) -> Platform.runLater(() -> {
            Throwable cause = error instanceof CompletionException && error.getCause() != null
                    ? error.getCause() : error;
            boolean deleted = target.session == null || chats.get(target.session.getId()) != target;
            if (deleted || cause instanceof CancellationException) {
                return;
            }

            boolean open = target == chat;
            if (cause != null) {
                logger.error("Brainstorm request failed", cause);
                target.failed(open);
                addEvent(target, cause.getMessage());
            } else {
                target.answered(open);
                showReply(target, reply);
            }
            if (open) {
                applyBusy(target);
            }
            refreshHistory(chat.session);
        }));
    }

    private void showReply(BrainstormChat target, BrainstormReply reply) {
        reply.getResearch().forEach((ResearchNote note) -> addResearch(target, note));
        if (target.progress(reply))
            addEvent(target, I18nManager.getText("brainstorm.round", reply.getRound()));
        addBubble(target, reply.getMessage(), false);
        target.quickReplies = reply.getQuickReplies();
        target.draft = reply.getDraft();
        if (target == chat) {
            setQuickReplies(target.quickReplies);
            updateStepper();
            applyDraft(target.draft);
        }
    }

    /** Shows whether the chat waits for the AI: the status line and the input are disabled only for this chat. */
    private void applyBusy(BrainstormChat target) {
        statusLabel.setText(target.statusText);
        statusLabel.setVisible(target.busy);
        inputArea.setDisable(target.busy);
        if (engineBox != null)
            engineBox.setDisable(target.busy || engineBox.getItems().size() < 2);
        resultPanel.setDisable(target.busy);
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

        send(I18nManager.getText("brainstorm.step.reopen", I18nManager.getText("brainstorm.step." + step)));
    }

    private void addBubble(BrainstormChat target, String text, boolean fromUser) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(520.0);
        label.setStyle(BUBBLE_STYLE + (fromUser
                ? "-fx-background-color: -color-accent-emphasis; -fx-text-fill: white;"
                : "-fx-background-color: -color-bg-subtle;"));
        HBox row = new HBox(label);
        row.setAlignment(fromUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        appendToChat(target, row, fromUser);
        if (!fromUser)
            return;

        /*  Every linked video gets a card under the message, its range belongs to this message  */
        for (YouTubeClip clip : YouTubeClip.findAll(text)) {
            BrainstormVideoCard card = new BrainstormVideoCard(clip, service,
                    (BrainstormVideoCard clicked) -> changeRange(target, clicked, text), this::openInBrowser);
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
        dialog.initOwner(this);
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
        sendEvent(BrainstormService.videoRangeEvent(changed, instruction));
    }

    private void addEvent(BrainstormChat target, String text) {
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
    private void appendToChat(BrainstormChat target, javafx.scene.Node row, boolean alwaysScroll) {
        if (target != chat) {
            target.messageBox.getChildren().add(row);
            target.scrollPosition = 1.0;
            return;
        }
        boolean follow = alwaysScroll || messageScroll.getVvalue() >= messageScroll.getVmax() - 0.02
                || target.messageBox.getHeight() <= messageScroll.getViewportBounds().getHeight();
        target.messageBox.getChildren().add(row);
        if (follow) {
            Platform.runLater(() -> messageScroll.setVvalue(messageScroll.getVmax()));
        }
    }

    private void setQuickReplies(List<String> replies) {
        quickReplyPane.getChildren().clear();
        quickReplyPane.setManaged(!replies.isEmpty());
        for (String reply : replies) {
            Button button = new Button(reply);
            button.getStyleClass().add("button-outlined");
            button.setOnAction(event -> send(reply));
            quickReplyPane.getChildren().add(button);
        }
    }

    /** Shows a stored event: a research result as sources row, any other event as a short line. */
    private void addStoredEvent(BrainstormChat target, String stored) {
        try {
            JSONObject event = new JSONObject(stored.substring(stored.indexOf('{')));
            if (ResearchNote.EVENT.equals(event.optString("event"))) {
                addResearch(target, ResearchNote.fromEventJson(event));
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
        } catch (JSONException | IndexOutOfBoundsException e) {
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
            link.setOnAction(event -> openInBrowser(note.sources().get(0).url()));
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
            link.setOnAction(event -> openInBrowser(url));
            links.getChildren().add(link);
        });

        VBox box = new VBox(2.0, head, links);
        box.setAlignment(Pos.CENTER);
        appendToChat(target, box, false);
    }

    /** Opens an http or https URL in the default browser; anything else is ignored. */
    private void openInBrowser(String url) {
        if (!(url.startsWith("https://") || url.startsWith("http://"))) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                if (java.awt.Desktop.isDesktopSupported()
                        && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                }
            } catch (java.io.IOException | RuntimeException e) {
                logger.warn("Could not open {} in the browser", url, e);
            }
        });
    }

    private static String shorten(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) + "…" : text;
    }

    /** Describes an app event (stored as text with a prefix) as a short line for the chat. */
    private String describeEvent(String stored) {
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
        } catch (JSONException | IndexOutOfBoundsException e) {
            return stored;
        }
    }

    // ===== Result panel =====

    private VBox createResultPanel() {
        promptArea.setWrapText(true);
        promptArea.setPrefRowCount(6);
        promptArea.textProperty().addListener((obs, old, text) ->
                promptCounter.setText(text.length() + " / 3000"));
        promptArea.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused && !updatingUi && !Objects.equals(promptArea.getText(), chat.draft.getPrompt())
                    && chat.session != null) {
                chat.draft.setPrompt(promptArea.getText());
                sendEvent(new JSONObject().put("event", "prompt_edited").put("prompt", promptArea.getText()));
            }
        });
        promptCounter.getStyleClass().addAll("text-muted", "text-small");

        styleReasonLabel.setWrapText(true);
        styleReasonLabel.getStyleClass().addAll("text-muted", "text-small");

        familyBox.setMaxWidth(Double.MAX_VALUE);
        resolutionBox.setMaxWidth(Double.MAX_VALUE);
        moodBox.setMaxWidth(Double.MAX_VALUE);
        ratioBox.setMaxWidth(Double.MAX_VALUE);
        durationBox.setMaxWidth(Double.MAX_VALUE);

        moodBox.getItems().add(I18nManager.getText(NONE_ITEM_KEY));
        context.moods().stream().map(Mood::getName).forEach(moodBox.getItems()::add);
        for (Mood.Field field : BrainstormDraft.FIELDS)
            blockRows.put(field, new BlockRow(field));

        // The target model is always adopted: ticked and locked
        CheckBox modelCheck = new CheckBox();
        modelCheck.setSelected(true);
        modelCheck.setDisable(true);

        modelBox.valueProperty().addListener((obs, old, model) -> {
            if (updatingUi || model == null) {
                return;
            }
            refreshModelDependent();
            showVariant(model, null);

            String to = ModelFamilies.familyOf(model)
                    + (resolutionBox.getValue() != null ? " " + resolutionBox.getValue() : "");
            String from = ModelFamilies.familyOf(old)
                    + (ModelFamilies.resolutionOf(old) != null ? " " + ModelFamilies.resolutionOf(old) : "");
            sendEvent(new JSONObject().put("event", "target_model_changed").put("from", from).put("to", to));
        });
        familyBox.valueProperty().addListener((obs, old, family) -> {
            if (updatingUi || family == null) {
                return;
            }
            String item = ModelFamilies.variantFor(modelBox.getItems(), family, remembered(family),
                    resolutionBox.getValue());
            if (item != null) {
                modelBox.setValue(item);
            }
        });
        resolutionBox.valueProperty().addListener((obs, old, resolution) -> {
            if (updatingUi || resolution == null || familyBox.getValue() == null) {
                return;
            }
            ApplicationConfig appConfig = config();
            if (appConfig != null) {
                appConfig.setProperty(ModelFamilies.resolutionKey(familyBox.getValue()), resolution);
            }

            /*  A model without resolution variants keeps its item, the others switch to the variant  */
            if (ModelFamilies.freeResolutions(modelBox.getValue()) != null) {
                sendEvent(new JSONObject().put("event", "setting_changed").put("name", "resolution")
                        .put("value", resolution));
            } else {
                String item = ModelFamilies.variantFor(modelBox.getItems(), familyBox.getValue(), resolution, null);
                if (item != null && !item.equals(modelBox.getValue())) {
                    modelBox.setValue(item);
                }
            }
        });
        /*  The resolution is part of the target model, so it is adopted like the model: ticked and locked  */
        CheckBox resolutionCheck = new CheckBox();
        resolutionCheck.setSelected(true);
        resolutionCheck.setDisable(true);
        resolutionRow.getChildren().add(row(I18nManager.getText("brainstorm.result.resolution"), resolutionBox,
                resolutionCheck));
        resolutionRow.managedProperty().bind(resolutionRow.visibleProperty());
        moodBox.valueProperty().addListener((obs, old, value) -> {
            if (updatingUi || value == null)
                return;

            /*  The mood sets its blocks in the rows, the AI learns only about the mood  */
            Mood mood = moodNamed(value);
            updatingUi = true;
            showMood(mood);
            updatingUi = false;
            moodCheck.setSelected(mood != null);
            sendEvent(new JSONObject().put("event", "setting_changed").put("name", "mood")
                    .put("value", mood != null ? value : ""));
        });
        ratioBox.valueProperty().addListener((obs, old, value) -> settingChanged("aspect_ratio", value, ratioCheck));
        saveMoodCheck.setText(I18nManager.getText("brainstorm.result.save.mood"));
        saveMoodCheck.setTooltip(new Tooltip(I18nManager.getText("brainstorm.result.save.mood.tooltip")));

        durationBox.getItems().setAll(DURATIONS);
        durationCheck.setSelected(true);
        durationRow = row(I18nManager.getText("brainstorm.result.duration"), durationBox, durationCheck);
        durationRow.managedProperty().bind(durationRow.visibleProperty());
        videoBox.getChildren().add(durationRow);
        videoBox.managedProperty().bind(videoBox.visibleProperty());

        adoptButton.setText(I18nManager.getText("brainstorm.adopt"));
        adoptButton.getStyleClass().add("accent");
        adoptButton.setMaxWidth(Double.MAX_VALUE);
        adoptButton.setOnAction(event -> adopt());

        Label resultTitle = new Label(I18nManager.getText("brainstorm.result.title"));
        Label promptLabel = new Label(I18nManager.getText("brainstorm.result.prompt"));
        promptLabel.getStyleClass().addAll("text-bold", "text-small");

        factsBox.managedProperty().bind(factsBox.visibleProperty());
        factsBox.setVisible(false);

        resultPanel.getChildren().addAll(promptLabel, promptArea, promptCounter, factsBox,
                row(I18nManager.getText("brainstorm.result.model"), familyBox, modelCheck),
                resolutionRow,
                row(I18nManager.getText("params.mood.title"), moodBox, moodCheck));
        blockRows.values().forEach((BlockRow blockRow) -> resultPanel.getChildren().add(blockRow.node));
        resultPanel.getChildren().addAll(styleReasonLabel, saveMoodCheck,
                row(I18nManager.getText("brainstorm.result.ratio"), ratioBox, ratioCheck),
                videoBox);

        /*  The mood and the four building blocks make the panel taller than the dialog; the fields keep 10 px off the
            scrollbar, as in the right panel of the main window  */
        resultPanel.setPadding(new Insets(0, 10.0, 0, 0));
        ScrollPane resultScroll = new ScrollPane(resultPanel);
        resultScroll.setFitToWidth(true);
        resultScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        resultScroll.getStyleClass().add("edge-to-edge");
        resultScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(resultScroll, Priority.ALWAYS);
        VBox.setMargin(adoptButton, new Insets(10.0, 0, 0, 0));

        VBox box = new VBox(headingRow(resultTitle, 0, 0), resultScroll, adoptButton);
        box.getStyleClass().add("bg-inset");
        box.setPadding(new Insets(0, 15.0, 15.0, BoxInset.LEFT));
        box.setPrefWidth(380.0);
        box.setMinWidth(320.0);
        box.setMaxWidth(620.0);
        return box;
    }

    /** A labeled control with its checkbox on the right (the checkbox may be null). */
    private VBox row(String label, javafx.scene.Node control, CheckBox check) {
        HBox line = new HBox(8.0, control, check);
        line.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);

        /*  The control gives way, the text of the checkbox ("Add to my list") stays readable  */
        check.setMinWidth(Region.USE_PREF_SIZE);
        VBox box = new VBox(3.0);
        if (label != null) {
            Label title = new Label(label);
            title.getStyleClass().addAll("text-bold", "text-small");
            box.getChildren().add(title);
        }
        box.getChildren().add(line);
        return box;
    }

    private void settingChanged(String name, String value, CheckBox check) {
        if (updatingUi || value == null) {
            return;
        }
        boolean none = I18nManager.getText(NONE_ITEM_KEY).equals(value);
        check.setSelected(!none);
        sendEvent(new JSONObject().put("event", "setting_changed").put("name", name).put("value", none ? "" : value));
    }

    private ApplicationConfig config() {
        return art.moodframe.config.ServiceFactory.getInstance().getService(ApplicationConfig.class);
    }

    /** The resolution remembered for a family, or null. */
    private String remembered(String family) {
        ApplicationConfig appConfig = config();
        return appConfig == null ? null : appConfig.getProperty(ModelFamilies.resolutionKey(family), null);
    }

    /**
     * Shows the family and the resolution choice of an item. A model without resolution variants takes the wanted
     * resolution, else the remembered one, else the first.
     */
    private void showVariant(String item, String wanted) {
        if (item == null) {
            return;
        }
        String family = ModelFamilies.familyOf(item);
        List<String> choices = ModelFamilies.resolutionsOf(modelBox.getItems(), item);
        String resolution = ModelFamilies.resolutionOf(item);
        if (ModelFamilies.freeResolutions(item) != null) {
            String saved = remembered(family);

            /*  List.of(...).contains(null) throws, and neither value need exist  */
            resolution = wanted != null && choices.contains(wanted) ? wanted
                    : saved != null && choices.contains(saved) ? saved : choices.get(0);
        }

        boolean wasUpdating = updatingUi;
        updatingUi = true;
        familyBox.setValue(family);
        resolutionBox.getItems().setAll(choices);
        resolutionBox.setValue(resolution != null && choices.contains(resolution) ? resolution : null);
        resolutionRow.setVisible(choices.size() > 1);
        updatingUi = wasUpdating;
    }

    /** Adapts the ratios, the video fields and the duration choices to the selected target model. */
    private void refreshModelDependent() {
        GenerationModel model = GenerationModel.fromLocalizedDisplayName(modelBox.getValue());
        if (model == null) {
            return;
        }
        boolean wasUpdating = updatingUi;
        updatingUi = true;
        String oldRatio = ratioBox.getValue();

        /*  The OpenAI models share one constant; their ratios depend on the model id  */
        String openAiId = GenerationModel.openAiModelId(modelBox.getValue());
        ratioBox.getItems().setAll(openAiId != null ? AspectRatios.forOpenAiModel(openAiId)
                : AspectRatios.forModel(model));
        ratioBox.setValue(ratioBox.getItems().contains(oldRatio) ? oldRatio : ratioBox.getItems().get(0));

        boolean video = model.isVideoModel();
        videoBox.setVisible(video);
        if (video) {
            // The choices follow the model
            List<String> choices = model.getVideoDurationChoices().stream()
                    .map((Integer seconds) -> seconds + "s").toList();
            String oldDuration = durationBox.getValue();
            durationBox.getItems().setAll(choices);
            durationBox.setValue(choices.contains(oldDuration) ? oldDuration
                    : choices.contains("8s") ? "8s" : choices.get(0));
        }
        updatingUi = wasUpdating;
    }

    /** Shows a draft in the result panel. Values the draft does not set keep the current selection. */
    private void applyDraft(BrainstormDraft newDraft) {
        updatingUi = true;
        promptArea.setText(newDraft.getPrompt());

        String wanted = newDraft.getTargetModel();
        String item = null;
        if (wanted != null) {
            /*  The model is a family name, or the name of one variant; without a resolution the shown one stays  */
            String current = modelBox.getValue();
            if (modelBox.getItems().contains(wanted)) {
                item = wanted;
            } else if (wanted.equals(ModelFamilies.familyOf(current)) && newDraft.getResolution() == null) {
                item = current;
            } else {
                item = ModelFamilies.variantFor(modelBox.getItems(), wanted, newDraft.getResolution(),
                        remembered(wanted));
            }
        }
        if (item != null) {
            modelBox.setValue(item);
        }
        refreshModelDependent();
        showVariant(modelBox.getValue(), newDraft.getResolution());

        showFacts(newDraft.getFacts());

        /*  The mood sets its blocks, a block of the draft replaces the one of the mood; a former look style (1.x) of
            an older draft is the standard mood of the same name  */
        Mood mood = findMood(newDraft.getMood());
        BrainstormDraft.Block style = newDraft.getBlock(Mood.Field.STYLE);
        if (mood == null && style != null && !style.isNew() && MoodService.isLegacyLook(style.name())
                && context.idOf(Mood.Field.STYLE, style.name()) == null) {
            mood = findMood(style);
            style = null;
        }
        moodBox.setValue(mood != null ? mood.getName() : I18nManager.getText(NONE_ITEM_KEY));
        moodCheck.setSelected(mood != null);
        showMood(mood);
        for (Mood.Field field : BrainstormDraft.FIELDS) {
            BrainstormDraft.Block block = field == Mood.Field.STYLE ? style : newDraft.getBlock(field);
            if (block != null)
                blockRows.get(field).show(block);
        }
        styleReasonLabel.setText(newDraft.getStyleReason());
        if (newDraft.getAspectRatio() != null && ratioBox.getItems().contains(newDraft.getAspectRatio())) {
            ratioBox.setValue(newDraft.getAspectRatio());
            ratioCheck.setSelected(true);
        }

        if (newDraft.getDurationSeconds() != null
                && durationBox.getItems().contains(newDraft.getDurationSeconds() + "s")) {
            durationBox.setValue(newDraft.getDurationSeconds() + "s");
        }
        updatingUi = false;
    }

    /**
     * Lists the facts of the draft: a checkbox keeps or drops each one, with its source domain (a click opens
     * the page), or the note that it comes from the user, or the warning that its source was not researched.
     */
    private void showFacts(List<BrainstormFact> facts) {
        factsBox.getChildren().clear();
        factsBox.setVisible(!facts.isEmpty());
        if (facts.isEmpty()) {
            return;
        }

        Label title = new Label(I18nManager.getText("brainstorm.facts.title"));
        title.getStyleClass().addAll("text-bold", "text-small");
        factsBox.getChildren().add(title);

        for (BrainstormFact fact : facts) {
            CheckBox check = new CheckBox(fact.getText());
            check.setWrapText(true);
            check.setMaxWidth(Double.MAX_VALUE);
            check.setSelected(fact.isConfirmed());
            check.selectedProperty().addListener((obs, old, kept) -> {
                fact.setConfirmed(kept);
                sendEvent(new JSONObject().put("event", "fact_changed").put("text", fact.getText())
                        .put("confirmed", kept));
            });

            HBox origin = new HBox(8.0);
            origin.setPadding(new Insets(0, 0, 0, 22.0));
            if (fact.isFromUser()) {
                Label own = new Label(I18nManager.getText("brainstorm.facts.user"));
                own.getStyleClass().addAll("text-muted", "text-small");
                origin.getChildren().add(own);
            } else {
                /*  A fact from a video names its time in the video and opens the video there  */
                YouTubeClip video = YouTubeClip.of(fact.getUrl(), 0, 0);
                int seconds = YouTubeClip.parseTime(fact.getTime());
                boolean timed = video != null && seconds >= 0;
                Hyperlink link = new Hyperlink(fact.getDomain()
                        + (timed ? " · " + YouTubeClip.formatTime(seconds) : ""));
                link.setStyle("-fx-font-size: 11px; -fx-padding: 0;");
                link.setOnAction(event -> openInBrowser(timed ? video.watchUrlAt(seconds)
                        : fact.getUrl().isEmpty() ? "https://" + fact.getDomain() : fact.getUrl()));
                origin.getChildren().add(link);
                if (!fact.isVerified()) {
                    Label warning = new Label(I18nManager.getText("brainstorm.facts.unverified"));
                    warning.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-warning-fg;");
                    origin.getChildren().add(warning);
                }
            }
            factsBox.getChildren().add(new VBox(0.0, check, origin));
        }
    }

    /** The mood of a draft by id, else by name; null if the draft names none or the mood is gone. */
    private Mood findMood(BrainstormDraft.Block reference) {
        if (reference == null)
            return null;
        return context.moods().stream().filter((Mood mood) -> reference.id() != null
                && reference.id().equals(mood.getId())).findFirst().orElseGet(() -> moodNamed(reference.name()));
    }

    private Mood moodNamed(String name) {
        return context.moods().stream().filter((Mood mood) -> mood.getName().equals(name)).findFirst().orElse(null);
    }

    /** Shows the blocks of a mood in the rows, "None" where the mood sets no block in use; null empties every row. */
    private void showMood(Mood mood) {
        for (Mood.Field field : BrainstormDraft.FIELDS) {
            String id = mood != null ? mood.getReference(field) : null;
            String name = context.nameOf(field, id);
            blockRows.get(field).show(name != null ? new BrainstormDraft.Block(id, name, null, List.of(), false)
                    : null);
        }
    }

    /**
     * Adopts the ticked values. A new block needs a name; if an entry of its list has the name already, the user is
     * asked whether to take the next free name, else the dialog stays open.
     */
    private void adopt() {
        GenerationModel model = GenerationModel.fromLocalizedDisplayName(modelBox.getValue());
        boolean video = model != null && model.isVideoModel();

        Map<Mood.Field, BrainstormDraft.Block> blocks = new EnumMap<>(Mood.Field.class);
        for (BlockRow blockRow : blockRows.values()) {
            BrainstormDraft.Block block = blockRow.adopted();
            if (block != null && block.isNew()) {
                String name = block.name();
                List<String> taken = blockRow.names();
                if (name.isEmpty()) {
                    blockRow.nameField.requestFocus();
                    return;
                }
                if (taken.contains(name)) {
                    String free = name;
                    for (int counter = 1; taken.contains(free); counter++)
                        free = name + " (" + counter + ")";
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                            I18nManager.getText("brainstorm.block.taken", name, free), ButtonType.YES, ButtonType.NO);
                    alert.initOwner(this);
                    alert.setHeaderText(null);
                    if (alert.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                        blockRow.nameField.requestFocus();
                        return;
                    }
                    blockRow.nameField.setText(free);
                    block = blockRow.adopted();
                }
            }
            if (block != null)
                blocks.put(blockRow.field, block);
        }
        Mood mood = moodCheck.isSelected() ? moodNamed(moodBox.getValue()) : null;

        Result result = new Result(
                promptArea.getText().trim(),
                modelBox.getValue(),
                mood != null ? mood.getId() : null,
                blocks,
                ratioCheck.isSelected() ? ratioBox.getValue() : null,
                video && durationCheck.isSelected() ? parseSeconds(durationBox.getValue()) : null,
                ModelFamilies.freeResolutions(modelBox.getValue()) != null ? resolutionBox.getValue() : null,
                saveMoodCheck.isSelected());

        /*  The AI reads it with the next message: another image asked for then starts a new round  */
        if (chat.session != null)
            service.noteAdopted(chat.session);
        onAdopt.accept(result);
        close();
    }

    private static Integer parseSeconds(String value) {
        return value == null ? null : Integer.valueOf(value.replace("s", ""));
    }

    /**
     * The row of a building block in the result panel: "None", a new block the AI proposes (its name editable, its
     * text, colors or camera fragment shown below) or an entry of the list. The checkbox adopts the block, a new one
     * into the list of the user; for a field locked in the main window it stays off.
     */
    private final class BlockRow {

        private final Mood.Field field;
        private final ChoiceBox<String> box = new ChoiceBox<>();
        private final CheckBox check = new CheckBox();
        private final TextField nameField = new TextField();
        private final Label textLabel = new Label();
        private final HBox colorBoxes = new HBox(3.0);
        private final VBox newBox;
        private final VBox node;
        private final String newItem = I18nManager.getText("brainstorm.block.new");
        private BrainstormDraft.Block proposed;

        BlockRow(Mood.Field field) {
            this.field = field;
            box.setMaxWidth(Double.MAX_VALUE);
            box.getItems().add(I18nManager.getText(NONE_ITEM_KEY));
            box.getItems().addAll(names());
            nameField.setPromptText(I18nManager.getText("block.dialog.name.placeholder"));
            textLabel.setWrapText(true);
            textLabel.getStyleClass().addAll("text-muted", "text-small");
            newBox = new VBox(4.0, nameField, field == Mood.Field.COLOR ? colorBoxes : textLabel);
            newBox.setPadding(new Insets(0, 0, 0, 12.0));
            newBox.managedProperty().bind(newBox.visibleProperty());
            newBox.setVisible(false);
            if (locked()) {
                check.setDisable(true);
                check.setTooltip(new Tooltip(I18nManager.getText("brainstorm.block.locked")));
            }

            String typeKey = field == Mood.Field.COLOR ? "palette" : field.name().toLowerCase(Locale.ROOT);
            node = new VBox(3.0, row(I18nManager.getText("block.type." + typeKey), box, check), newBox);
            box.valueProperty().addListener((obs, old, value) -> {
                boolean isNewItem = newItem.equals(value);
                newBox.setVisible(isNewItem);
                check.setText(isNewItem ? I18nManager.getText("brainstorm.block.mylist") : "");
                if (updatingUi || value == null)
                    return;

                check.setSelected(!isNone() && !locked());
                sendEvent(new JSONObject().put("event", "setting_changed")
                        .put("name", field.name().toLowerCase(Locale.ROOT))
                        .put("value", isNone() ? "" : isNewItem ? nameField.getText().trim() : value));
            });
        }

        /** The names of the entries of the list of the field. */
        List<String> names() {
            return field == Mood.Field.COLOR ? context.colorStyles().stream().map(ColorStyle::getName).toList()
                    : context.blocks(field).stream().map(PromptBlock::getName).toList();
        }

        private boolean locked() {
            return context.locked().contains(field);
        }

        private boolean isNone() {
            return box.getValue() == null || I18nManager.getText(NONE_ITEM_KEY).equals(box.getValue());
        }

        /**
         * Shows a block: an entry of the list by id, else by name, or a new block; "None" for null or an entry that
         * is not listed. The checkbox is ticked for a block, unless the field is locked.
         */
        void show(BrainstormDraft.Block block) {
            box.getItems().remove(newItem);
            proposed = block != null && block.isNew() && (field == Mood.Field.COLOR ? !block.colors().isEmpty()
                    : field == Mood.Field.CAMERA ? block.settings() != null : block.text() != null) ? block : null;
            String value = I18nManager.getText(NONE_ITEM_KEY);
            if (proposed != null) {
                box.getItems().add(1, newItem);
                nameField.setText(proposed.name());

                /*  a new camera shows the fragment of its settings  */
                textLabel.setText(proposed.settings() != null ? CameraPromptBuilder.build(proposed.settings(), true)
                        : proposed.text());
                colorBoxes.getChildren().setAll(proposed.colors().stream().map(ColorBox::new).toList());
                value = newItem;
            }
            else if (block != null && !block.isNew()) {
                String name = context.nameOf(field, block.id());
                name = name != null ? name : block.name();
                if (box.getItems().contains(name))
                    value = name;
            }
            box.setValue(value);
            check.setSelected(!isNone() && !locked());
        }

        /** The block to adopt, null if the checkbox is off or the row shows "None". */
        BrainstormDraft.Block adopted() {
            if (!check.isSelected() || isNone())
                return null;
            if (newItem.equals(box.getValue()))
                return new BrainstormDraft.Block(null, nameField.getText().trim(), proposed.text(), proposed.colors(),
                        proposed.settings(), true);
            return new BrainstormDraft.Block(context.idOf(field, box.getValue()), box.getValue(), null, List.of(),
                    false);
        }
    }
}
