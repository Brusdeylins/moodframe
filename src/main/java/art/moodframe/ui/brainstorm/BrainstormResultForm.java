package art.moodframe.ui.brainstorm;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.AspectRatios;
import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.BrainstormFact;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ModelFamilies;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.brainstorm.BrainstormContext;
import art.moodframe.service.brainstorm.YouTubeClip;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.prompt.CameraPromptBuilder;
import art.moodframe.ui.component.ColorBox;
import art.moodframe.ui.component.DialogButtons;
import art.moodframe.ui.support.BoxInset;
import art.moodframe.ui.support.SystemOpen;
import art.moodframe.util.I18nManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The result column of the brainstorm window: the prompt, the facts, the target model with its resolution, the mood
 * and its building blocks, the aspect ratio and the duration of a video, each with the checkbox that adopts it, and
 * "Adopt". It shows the draft of the shown chat, maps it onto the lists of the main window, tells the window about
 * every change the user makes, which the AI then learns, and builds the adopted values.
 *
 * <p>The target model starts with the model of the main window and is always adopted. All other values are adopted
 * only if their checkbox is ticked. The look is a mood and the building blocks style, atmosphere, light and color:
 * each one an entry of the lists or a new block the AI proposes, which goes into the list of the user on "Adopt". A
 * field locked in the main window is not adopted.</p>
 *
 * @since 2.1.3
 */
final class BrainstormResultForm extends VBox {

    private static final String NONE_ITEM_KEY = "brainstorm.none";
    private static final List<String> DURATIONS = List.of("3s", "4s", "5s", "6s", "7s", "8s", "9s", "10s");

    /** What the window does for the form. */
    interface Listener {

        /** The user changed a field: the AI of the shown chat learns it with this event. */
        void changed(JSONObject event);

        /** The user edited the prompt of the shown draft. */
        void promptEdited(String prompt);

        /** The user adopted the ticked values. */
        void adopted(BrainstormDialog.Result result);
    }

    private final ApplicationConfig config;
    private final BrainstormContext context;
    private final Listener listener;
    /** The draft shown, the one of the shown chat. */
    private BrainstormDraft draft = new BrainstormDraft();
    private boolean updating;

    private final VBox fields = new VBox(8.0);
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
    private final CheckBox durationCheck = new CheckBox();
    private final VBox factsBox = new VBox(6.0);
    private final Button adoptButton = new Button();

    /**
     * Creates the column.
     *
     * @param config       the remembered resolutions (may be null: none)
     * @param context      the selectable values and the current main window state
     * @param currentModel localized display name of the model currently selected in the main window
     * @param listener     what the window does
     */
    BrainstormResultForm(ApplicationConfig config, BrainstormContext context, String currentModel, Listener listener) {
        this.config = config;
        this.context = context;
        this.listener = listener;

        promptArea.setWrapText(true);
        promptArea.setPrefRowCount(6);
        promptArea.textProperty().addListener((obs, old, text) ->
                promptCounter.setText(text.length() + " / 3000"));
        promptArea.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused && !updating && !Objects.equals(promptArea.getText(), draft.getPrompt()))
                listener.promptEdited(promptArea.getText());
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
            if (updating || model == null) {
                return;
            }
            refreshModelDependent();
            showVariant(model, null);

            String to = ModelFamilies.familyOf(model)
                    + (resolutionBox.getValue() != null ? " " + resolutionBox.getValue() : "");
            String from = ModelFamilies.familyOf(old)
                    + (ModelFamilies.resolutionOf(old) != null ? " " + ModelFamilies.resolutionOf(old) : "");
            listener.changed(new JSONObject().put("event", "target_model_changed").put("from", from).put("to", to));
        });
        familyBox.valueProperty().addListener((obs, old, family) -> {
            if (updating || family == null) {
                return;
            }
            String item = ModelFamilies.variantFor(modelBox.getItems(), family, remembered(family),
                    resolutionBox.getValue());
            if (item != null) {
                modelBox.setValue(item);
            }
        });
        resolutionBox.valueProperty().addListener((obs, old, resolution) -> {
            if (updating || resolution == null || familyBox.getValue() == null) {
                return;
            }
            if (config != null) {
                config.ui().setModelResolution(familyBox.getValue(), resolution);
            }

            /*  A model without resolution variants keeps its item, the others switch to the variant  */
            if (ModelFamilies.freeResolutions(modelBox.getValue()) != null) {
                listener.changed(new JSONObject().put("event", "setting_changed").put("name", "resolution")
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
            if (updating || value == null)
                return;

            /*  The mood sets its blocks in the rows, the AI learns only about the mood  */
            Mood mood = moodNamed(value);
            updating = true;
            showMood(mood);
            updating = false;
            moodCheck.setSelected(mood != null);
            listener.changed(new JSONObject().put("event", "setting_changed").put("name", "mood")
                    .put("value", mood != null ? value : ""));
        });
        ratioBox.valueProperty().addListener((obs, old, value) -> settingChanged("aspect_ratio", value, ratioCheck));
        saveMoodCheck.setText(I18nManager.getText("brainstorm.result.save.mood"));
        saveMoodCheck.setTooltip(new Tooltip(I18nManager.getText("brainstorm.result.save.mood.tooltip")));

        durationBox.getItems().setAll(DURATIONS);
        durationCheck.setSelected(true);
        VBox durationRow = row(I18nManager.getText("brainstorm.result.duration"), durationBox, durationCheck);
        durationRow.managedProperty().bind(durationRow.visibleProperty());
        videoBox.getChildren().add(durationRow);
        videoBox.managedProperty().bind(videoBox.visibleProperty());

        adoptButton.setText(I18nManager.getText("brainstorm.adopt"));
        adoptButton.getStyleClass().add("accent");
        adoptButton.setMaxWidth(Double.MAX_VALUE);
        adoptButton.setOnAction((event) -> adopt());

        Label resultTitle = new Label(I18nManager.getText("brainstorm.result.title"));
        Label promptLabel = new Label(I18nManager.getText("brainstorm.result.prompt"));
        promptLabel.getStyleClass().addAll("text-bold", "text-small");

        factsBox.managedProperty().bind(factsBox.visibleProperty());
        factsBox.setVisible(false);

        fields.getChildren().addAll(promptLabel, promptArea, promptCounter, factsBox,
                row(I18nManager.getText("brainstorm.result.model"), familyBox, modelCheck),
                resolutionRow,
                row(I18nManager.getText("params.mood.title"), moodBox, moodCheck));
        blockRows.values().forEach((BlockRow blockRow) -> fields.getChildren().add(blockRow.node));
        fields.getChildren().addAll(styleReasonLabel, saveMoodCheck,
                row(I18nManager.getText("brainstorm.result.ratio"), ratioBox, ratioCheck),
                videoBox);

        /*  The mood and the four building blocks make the panel taller than the dialog; the fields keep 10 px off the
            scrollbar, as in the right panel of the main window  */
        fields.setPadding(new Insets(0, 10.0, 0, 0));
        ScrollPane resultScroll = new ScrollPane(fields);
        resultScroll.setFitToWidth(true);
        resultScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        resultScroll.getStyleClass().add("edge-to-edge");
        resultScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(resultScroll, Priority.ALWAYS);
        VBox.setMargin(adoptButton, new Insets(10.0, 0, 0, 0));

        getChildren().addAll(BrainstormHeading.row(resultTitle, 0, 0), resultScroll, adoptButton);
        getStyleClass().add("bg-inset");
        setPadding(new Insets(0, 15.0, 15.0, BoxInset.LEFT));
        setPrefWidth(380.0);
        setMinWidth(320.0);
        setMaxWidth(620.0);

        updating = true;
        modelBox.getItems().setAll(context.modelNames());
        familyBox.getItems().setAll(context.modelNames().stream().map(ModelFamilies::familyOf).distinct().toList());
        modelBox.setValue(currentModel);
        updating = false;
        showVariant(currentModel, null);
        refreshModelDependent();
    }

    /**
     * Disables the fields while the shown chat waits for the AI.
     *
     * @param busy whether the chat waits
     */
    void setBusy(boolean busy) {
        fields.setDisable(busy);
    }

    /** A labeled control with its checkbox on the right (the checkbox may be null). */
    private VBox row(String label, Node control, CheckBox check) {
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
        if (updating || value == null) {
            return;
        }
        boolean none = I18nManager.getText(NONE_ITEM_KEY).equals(value);
        check.setSelected(!none);
        listener.changed(new JSONObject().put("event", "setting_changed").put("name", name)
                .put("value", none ? "" : value));
    }

    /** The resolution remembered for a family, or null. */
    private String remembered(String family) {
        return config == null ? null : config.ui().getModelResolution(family);
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

        boolean wasUpdating = updating;
        updating = true;
        familyBox.setValue(family);
        resolutionBox.getItems().setAll(choices);
        resolutionBox.setValue(resolution != null && choices.contains(resolution) ? resolution : null);
        resolutionRow.setVisible(choices.size() > 1);
        updating = wasUpdating;
    }

    /** Adapts the ratios, the video fields and the duration choices to the selected target model. */
    private void refreshModelDependent() {
        GenerationModel model = GenerationModel.fromLocalizedDisplayName(modelBox.getValue());
        if (model == null) {
            return;
        }
        boolean wasUpdating = updating;
        updating = true;
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
        updating = wasUpdating;
    }

    /**
     * Shows the draft of a chat. Values the draft does not set keep the current selection.
     *
     * @param newDraft the draft
     */
    void show(BrainstormDraft newDraft) {
        draft = newDraft;
        updating = true;
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
        updating = false;
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
                listener.changed(new JSONObject().put("event", "fact_changed").put("text", fact.getText())
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
                link.setOnAction((event) -> SystemOpen.browse(timed ? video.watchUrlAt(seconds)
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
                    ButtonType yes = DialogButtons.yes();
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                            I18nManager.getText("brainstorm.block.taken", name, free), yes, DialogButtons.no());
                    alert.initOwner(getScene() != null ? getScene().getWindow() : null);
                    alert.setHeaderText(null);
                    if (alert.showAndWait().orElse(null) != yes) {
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

        listener.adopted(new BrainstormDialog.Result(
                promptArea.getText().trim(),
                modelBox.getValue(),
                mood != null ? mood.getId() : null,
                blocks,
                ratioCheck.isSelected() ? ratioBox.getValue() : null,
                video && durationCheck.isSelected() ? parseSeconds(durationBox.getValue()) : null,
                ModelFamilies.freeResolutions(modelBox.getValue()) != null ? resolutionBox.getValue() : null,
                saveMoodCheck.isSelected()));
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
                if (updating || value == null)
                    return;

                check.setSelected(!isNone() && !locked());
                listener.changed(new JSONObject().put("event", "setting_changed")
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
