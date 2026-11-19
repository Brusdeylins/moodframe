package art.moodframe.ui.brainstorm;

import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.BrainstormFact;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.Frame;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ModelFamilies;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.model.SavedImage;
import art.moodframe.service.brainstorm.BrainstormContext;
import art.moodframe.service.brainstorm.YouTubeClip;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.prompt.CameraPromptBuilder;
import art.moodframe.ui.component.ColorBox;
import art.moodframe.ui.component.DialogButtons;
import art.moodframe.ui.component.ModelFamilySelector;
import art.moodframe.ui.panel.AdoptionColumn;
import art.moodframe.ui.panel.MoodFramePanelController;
import art.moodframe.ui.panel.PanelGroups;
import art.moodframe.ui.support.BoxInset;
import art.moodframe.ui.support.StatusSink;
import art.moodframe.ui.support.SystemOpen;
import art.moodframe.util.I18nManager;
import javafx.fxml.FXMLLoader;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * The result column of the brainstorm window: the prompt, the facts, the groups Mood and Frame as the right panel of
 * the main window shows them, the target model, the duration of a video and the saved images to attach, each with the
 * checkbox that adopts it, and "Adopt". It shows the draft of the shown chat, tells the window about every change the
 * user makes, which the AI then learns, and builds the adopted values.
 *
 * <p>The groups start with what the main window has chosen and show every draft over it: what the draft sets is
 * ticked, a field left unticked shows what the main window keeps. The target model starts with the model of the main
 * window and is always adopted; as in the main window, its variant follows the target resolution of the group Frame.
 * The look is a mood and the building blocks: each one an entry of the lists or a new block the AI proposes, shown
 * below its field, which goes into the list of the user on "Adopt". A field locked in the main window is not
 * adopted.</p>
 *
 * @since 2.1.3
 */
final class BrainstormResultForm extends VBox {

    private static final Logger logger = LoggerFactory.getLogger(BrainstormResultForm.class);
    private static final String GROUPS_FXML = "/fxml/panel/MoodFramePanel.fxml";
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

    private final BrainstormContext context;
    private final Listener listener;
    /** What the main window has chosen, which the groups show under every draft. */
    private final MoodFramePanelController.Selection start;
    /** The draft shown, the one of the shown chat. */
    private BrainstormDraft draft = new BrainstormDraft();
    private boolean updating;

    private final VBox fields = new VBox(8.0);
    private final TextArea promptArea = new TextArea();
    private final Label promptCounter = new Label();
    /** Holds the variant (the main window's dropdown item) that the family and the target resolution select; not shown. */
    private final ChoiceBox<String> modelBox = new ChoiceBox<>();
    private final ChoiceBox<String> familyBox = new ChoiceBox<>();
    private final MoodFramePanelController groups;
    private final AdoptionColumn column;
    private final Map<Mood.Field, Proposal> proposals = new EnumMap<>(Mood.Field.class);
    private final Label styleReasonLabel = new Label();
    private final CheckBox saveMoodCheck = new CheckBox();
    private final VBox videoBox = new VBox(8.0);
    private final ChoiceBox<String> durationBox = new ChoiceBox<>();
    private final CheckBox durationCheck = new CheckBox();
    private final VBox imagesBox = new VBox(6.0);
    private final Label imagesTitle;
    /** The checkboxes of the saved images the draft proposes, by their id. */
    private final Map<String, CheckBox> imageChecks = new LinkedHashMap<>();
    private final VBox factsBox = new VBox(6.0);
    private final Button adoptButton = new Button();
    /** The labels of the form by the keys of their texts, set anew on a change of the language. */
    private final Map<Label, String> labels = new LinkedHashMap<>();

    /**
     * Creates the column.
     *
     * @param context      the selectable values and the current main window state
     * @param currentModel localized display name of the model currently selected in the main window
     * @param main         the groups Mood and Frame as the main window has them
     * @param worker       writes the library for the groups (the favorites of their lists), off the JavaFX thread
     * @param listener     what the window does
     */
    BrainstormResultForm(BrainstormContext context, String currentModel, BrainstormDialog.Groups main, Executor worker,
            Listener listener) {
        this.context = context;
        this.listener = listener;
        this.start = main.selection();

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
        styleReasonLabel.managedProperty().bind(styleReasonLabel.visibleProperty());
        familyBox.setMaxWidth(Double.MAX_VALUE);
        durationBox.setMaxWidth(Double.MAX_VALUE);

        /*  The groups of the main window with the checkboxes of the adoption; they remember nothing, as their services
            carry no configuration  */
        FXMLLoader loader = new FXMLLoader(BrainstormResultForm.class.getResource(GROUPS_FXML));
        Node groupsNode;
        try {
            groupsNode = loader.load();
        }
        catch (IOException e) {
            throw new UncheckedIOException("Cannot load " + GROUPS_FXML, e);
        }
        groups = loader.getController();
        groups.followModel(modelBox.valueProperty());
        groups.init(main.services(), worker, status(), () -> { });
        column = groups.showAdoptionColumn(start.locked(), this::fieldChanged);
        for (Mood.Field field : BrainstormDraft.FIELDS)
            proposals.put(field, new Proposal(field));

        // The target model is always adopted: ticked and locked
        CheckBox modelCheck = new CheckBox();
        modelCheck.setSelected(true);
        modelCheck.setDisable(true);
        modelBox.valueProperty().addListener((obs, old, model) -> {
            if (model == null)
                return;
            refreshModelDependent();

            /*  A variant of the same family follows the target resolution, which the AI learns as such  */
            if (updating || old == null || ModelFamilies.familyOf(model).equals(ModelFamilies.familyOf(old)))
                return;
            listener.changed(new JSONObject().put("event", "target_model_changed").put("from", modelName(old))
                    .put("to", modelName(model)));
        });

        durationBox.getItems().setAll(DURATIONS);
        durationCheck.setSelected(true);
        videoBox.getChildren().add(row("brainstorm.result.duration", durationBox, durationCheck));
        videoBox.managedProperty().bind(videoBox.visibleProperty());

        imagesTitle = label("brainstorm.result.images");
        imagesTitle.getStyleClass().addAll("text-bold", "text-small");
        imagesBox.managedProperty().bind(imagesBox.visibleProperty());
        imagesBox.setVisible(false);

        adoptButton.getStyleClass().add("accent");
        adoptButton.setMaxWidth(Double.MAX_VALUE);
        adoptButton.setOnAction((event) -> adopt());

        Label resultTitle = label("brainstorm.result.title");
        Label promptLabel = label("brainstorm.result.prompt");
        promptLabel.getStyleClass().addAll("text-bold", "text-small");

        factsBox.managedProperty().bind(factsBox.visibleProperty());
        factsBox.setVisible(false);

        /*  In the order of the right panel of the main window: Mood, Frame, then the model of the group Generation  */
        fields.getChildren().addAll(promptLabel, promptArea, promptCounter, factsBox, groupsNode, styleReasonLabel,
                saveMoodCheck, row("brainstorm.result.model", familyBox, modelCheck), videoBox, imagesBox);

        /*  The groups make the column taller than the dialog; the fields keep 10 px off the scrollbar, as in the right
            panel of the main window, and fold as there  */
        fields.setPadding(new Insets(0, 10.0, 0, 0));
        ScrollPane resultScroll = new ScrollPane(fields);
        resultScroll.setFitToWidth(true);
        resultScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        resultScroll.getStyleClass().add("edge-to-edge");
        resultScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(resultScroll, Priority.ALWAYS);
        VBox.setMargin(adoptButton, new Insets(10.0, 0, 0, 0));
        new PanelGroups(resultScroll, groups.moodGroup(), groups.frameGroup(), null, null);

        getChildren().addAll(BrainstormHeading.row(resultTitle, 0, 0), resultScroll, adoptButton);
        getStyleClass().add("bg-inset");
        setPadding(new Insets(0, 15.0, 15.0, BoxInset.LEFT));
        setPrefWidth(380.0);
        setMinWidth(320.0);
        setMaxWidth(620.0);

        updating = true;
        modelBox.getItems().setAll(context.modelNames());
        modelBox.setValue(currentModel);
        groups.showSelection(start);
        new ModelFamilySelector(modelBox, familyBox, groups.resolutionProperty());
        updating = false;
        refreshModelDependent();
        relabel();
    }

    /*  The groups report what they did: the window has no status bar, so only a failure shows, in a message box  */
    private StatusSink status() {
        return new StatusSink() {
            @Override
            public void status(String message) {
                logger.debug("Brainstorm groups: {}", message);
            }

            @Override
            public void error(String message) {
                Alert alert = new Alert(Alert.AlertType.ERROR, message, DialogButtons.ok());
                alert.initOwner(getScene() != null ? getScene().getWindow() : null);
                alert.setHeaderText(null);
                alert.show();
            }
        };
    }

    /*  the family and the resolution of a model item, as the AI names the target model  */
    private static String modelName(String item) {
        String resolution = ModelFamilies.resolutionOf(item);
        return ModelFamilies.familyOf(item) + (resolution != null ? " " + resolution : "");
    }

    /**
     * Sets the texts of the form in the current language. The choices stay, the AI learns nothing of it; the names of
     * the models stay as the main window gave them.
     */
    void relabel() {
        boolean wasUpdating = updating;
        updating = true;
        labels.forEach((Label label, String key) -> label.setText(I18nManager.getText(key)));
        saveMoodCheck.setText(I18nManager.getText("brainstorm.result.save.mood"));
        saveMoodCheck.setTooltip(new Tooltip(I18nManager.getText("brainstorm.result.save.mood.tooltip")));
        adoptButton.setText(I18nManager.getText("brainstorm.adopt"));
        groups.updateTexts();
        for (Mood.Field field : BrainstormDraft.FIELDS)
            if (column.isLocked(field))
                column.check(AdoptionColumn.Field.of(field))
                        .setTooltip(new Tooltip(I18nManager.getText("brainstorm.block.locked")));
        proposals.values().forEach(Proposal::relabel);
        showFacts(draft.getFacts());
        updating = wasUpdating;
    }

    /*  a label whose text follows the language  */
    private Label label(String key) {
        Label label = new Label(I18nManager.getText(key));
        labels.put(label, key);
        return label;
    }

    /**
     * Disables the fields while the shown chat waits for the AI.
     *
     * @param busy whether the chat waits
     */
    void setBusy(boolean busy) {
        fields.setDisable(busy);
    }

    /** A control under the label of a key, with its checkbox on the right. */
    private VBox row(String labelKey, Node control, CheckBox check) {
        HBox line = new HBox(8.0, control, check);
        line.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);
        check.setMinWidth(Region.USE_PREF_SIZE);
        Label title = label(labelKey);
        title.getStyleClass().addAll("text-bold", "text-small");
        return new VBox(3.0, title, line);
    }

    /*  ticks the checkbox of a field, unless the field is locked in the main window; not "isDisabled", which is also
        true while the fields wait for the AI  */
    private void tick(AdoptionColumn.Field field, boolean ticked) {
        CheckBox check = column.check(field);
        check.setSelected(ticked && !check.isDisable());
    }

    /*  ticks the mood and the building blocks it sets, which it adopts with it  */
    private void tickMood(MoodFramePanelController.Selection selection) {
        tick(AdoptionColumn.Field.MOOD, selection.mood() != null);
        for (Mood.Field field : BrainstormDraft.FIELDS)
            tick(AdoptionColumn.Field.of(field), selection.mood() != null && selection.blocks().containsKey(field));
    }

    /**
     * Ticks a field the user changed in the groups and tells the AI: a mood ticks the blocks it sets and replaces the
     * proposed new blocks, a frame ticks aspect ratio and resolution, "None" unticks a block.
     */
    private void fieldChanged(AdoptionColumn.Field field) {
        if (updating)
            return;
        MoodFramePanelController.Selection now = groups.selection();
        switch (field) {
            case MOOD -> {
                proposals.values().forEach(Proposal::clear);
                tickMood(now);
                settingChanged("mood", now.mood() != null ? now.mood().getName() : "");
            }
            case FRAME -> {
                tick(AdoptionColumn.Field.FRAME, now.frame() != null);
                if (now.frame() != null) {
                    tick(AdoptionColumn.Field.ASPECT_RATIO, true);
                    tick(AdoptionColumn.Field.RESOLUTION, now.frame().getResolution() != null);
                }
                settingChanged("frame", now.frame() != null ? now.frame().getName() : "");
            }
            case ASPECT_RATIO -> {
                tick(field, true);
                settingChanged("aspect_ratio", now.aspectRatio());
            }
            case RESOLUTION -> {
                tick(field, true);
                settingChanged("resolution", now.resolution());
            }
            default -> {
                Mood.Field block = Mood.Field.valueOf(field.name());
                String id = now.blocks().get(block);
                String name = id != null ? context.nameOf(block, id) : null;
                tick(field, id != null);
                settingChanged(block.name().toLowerCase(Locale.ROOT), name != null ? name : "");
            }
        }
    }

    private void settingChanged(String name, String value) {
        listener.changed(new JSONObject().put("event", "setting_changed").put("name", name)
                .put("value", value != null ? value : ""));
    }

    /** Adapts the video fields and the duration choices to the selected target model. */
    private void refreshModelDependent() {
        GenerationModel model = GenerationModel.fromLocalizedDisplayName(modelBox.getValue());
        if (model == null)
            return;
        boolean wasUpdating = updating;
        updating = true;
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
        limitImages();
        updating = wasUpdating;
    }

    /*  keeps the ticked saved images within what the target model takes: the last ones beyond it go unticked, the
        unticked ones are disabled at the limit  */
    private void limitImages() {
        GenerationModel model = GenerationModel.fromLocalizedDisplayName(modelBox.getValue());
        int limit = model != null ? model.getMaxInputImages() : 0;
        List<CheckBox> ticked = imageChecks.values().stream().filter(CheckBox::isSelected).toList();
        for (CheckBox check : ticked.subList(Math.min(limit, ticked.size()), ticked.size()))
            check.setSelected(false);
        boolean full = ticked.size() >= limit;
        imageChecks.values().forEach((CheckBox check) -> check.setDisable(full && !check.isSelected()));
    }

    /**
     * Shows the draft of a chat over what the main window has chosen: what the draft sets is ticked, every other field
     * shows what the main window keeps. A model the draft does not name keeps the shown one.
     *
     * @param newDraft the draft
     */
    void show(BrainstormDraft newDraft) {
        draft = newDraft;
        updating = true;
        promptArea.setText(newDraft.getPrompt());
        groups.showSelection(start);
        for (AdoptionColumn.Field field : AdoptionColumn.Field.values())
            tick(field, false);
        proposals.values().forEach(Proposal::clear);

        /*  The mood sets its blocks, a block of the draft replaces the one of the mood; a former look style (1.x) of
            an older draft is the standard mood of the same name; a locked field keeps the block of the main window  */
        Mood mood = findMood(newDraft.getMood());
        BrainstormDraft.Block style = newDraft.getBlock(Mood.Field.STYLE);
        if (mood == null && style != null && !style.isNew() && MoodService.isLegacyLook(style.name())
                && context.idOf(Mood.Field.STYLE, style.name()) == null) {
            mood = findMood(style);
            style = null;
        }
        if (mood != null) {
            groups.chooseMood(mood);
            tickMood(groups.selection());
        }
        for (Mood.Field field : BrainstormDraft.FIELDS) {
            BrainstormDraft.Block block = field == Mood.Field.STYLE ? style : newDraft.getBlock(field);
            if (block == null || column.isLocked(field))
                continue;
            if (block.isNew()) {
                proposals.get(field).show(block);
                continue;
            }
            String id = block.id() != null && context.nameOf(field, block.id()) != null ? block.id()
                    : context.idOf(field, block.name());
            if (id != null) {
                groups.chooseBlock(field, id);
                tick(AdoptionColumn.Field.of(field), true);
            }
        }
        styleReasonLabel.setText(newDraft.getStyleReason());
        styleReasonLabel.setVisible(!newDraft.getStyleReason().isEmpty());

        /*  The frame sets aspect ratio and resolution, a value of the draft replaces the one of the frame  */
        Frame frame = BrainstormDraft.Block.find(newDraft.getFrame(), context.frames(), Frame::getId, Frame::getName);
        if (frame != null) {
            groups.chooseFrame(frame);
            tick(AdoptionColumn.Field.FRAME, true);
            tick(AdoptionColumn.Field.ASPECT_RATIO, true);
            tick(AdoptionColumn.Field.RESOLUTION, frame.getResolution() != null);
        }
        if (newDraft.getAspectRatio() != null) {
            groups.setAspectRatio(newDraft.getAspectRatio());
            if (newDraft.getAspectRatio().equals(groups.aspectRatio()))
                tick(AdoptionColumn.Field.ASPECT_RATIO, true);
        }

        /*  A resolution of the model that is no target, as 1080p of a video model, sets the target that selects its
            variant, an unknown one the closest  */
        String wanted = newDraft.getTargetModel();
        String family = ModelFamilies.familyOf(wanted != null ? wanted : modelBox.getValue());
        String resolution = ModelFamilies.targetFor(modelBox.getItems(), family, newDraft.getResolution());
        if (resolution != null) {
            groups.setResolution(resolution);
            if (resolution.equals(groups.resolution()))
                tick(AdoptionColumn.Field.RESOLUTION, true);
        }

        /*  The model is a family name, or the name of one variant; the variant follows the target resolution  */
        String item = wanted == null ? null : modelBox.getItems().contains(wanted) ? wanted
                : ModelFamilies.variantForTarget(modelBox.getItems(), family, groups.resolution());
        if (item != null)
            modelBox.setValue(item);
        refreshModelDependent();
        if (newDraft.getDurationSeconds() != null
                && durationBox.getItems().contains(newDraft.getDurationSeconds() + "s"))
            durationBox.setValue(newDraft.getDurationSeconds() + "s");

        showImages(newDraft.getSavedImages());
        showFacts(newDraft.getFacts());
        updating = false;
    }

    /**
     * Lists the saved images the draft proposes to attach, each with its checkbox and its description; an image no
     * longer in the library is left out. They are ticked as far as the target model takes images, none for a video
     * model, whose image becomes its start frame.
     */
    private void showImages(List<BrainstormDraft.Block> proposed) {
        GenerationModel model = GenerationModel.fromLocalizedDisplayName(modelBox.getValue());
        boolean video = model == null || model.isVideoModel();
        imageChecks.clear();
        imagesBox.getChildren().setAll(imagesTitle);
        for (BrainstormDraft.Block reference : proposed) {
            SavedImage image = context.savedImages().stream()
                    .filter((SavedImage saved) -> saved.id().equals(reference.id())).findFirst().orElse(null);
            if (image == null)
                continue;
            CheckBox check = new CheckBox(image.name());
            check.setSelected(!video);
            check.selectedProperty().addListener((obs, was, on) -> limitImages());
            check.setWrapText(true);
            imageChecks.put(image.id(), check);
            VBox entry = new VBox(0.0, check);
            if (image.description() != null && !image.description().isBlank()) {
                Label description = new Label(image.description());
                description.setWrapText(true);
                description.getStyleClass().addAll("text-muted", "text-small");
                description.setPadding(new Insets(0, 0, 0, 22.0));
                entry.getChildren().add(description);
            }
            imagesBox.getChildren().add(entry);
        }
        limitImages();
        imagesBox.setVisible(!imageChecks.isEmpty());
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
        return BrainstormDraft.Block.find(reference, context.moods(), Mood::getId, Mood::getName);
    }

    /**
     * Adopts the ticked values. A new block needs a name; if an entry of its list has the name already, the user is
     * asked whether to take the next free name, else the dialog stays open.
     */
    private void adopt() {
        GenerationModel model = GenerationModel.fromLocalizedDisplayName(modelBox.getValue());
        boolean video = model != null && model.isVideoModel();
        MoodFramePanelController.Selection now = groups.selection();

        Map<Mood.Field, BrainstormDraft.Block> blocks = new EnumMap<>(Mood.Field.class);
        for (Mood.Field field : BrainstormDraft.FIELDS) {
            Proposal proposal = proposals.get(field);
            String id = now.blocks().get(field);
            if (proposal.isAdopted()) {
                BrainstormDraft.Block block = proposal.adopted();
                if (block == null)
                    return;
                blocks.put(field, block);
            }
            else if (column.check(AdoptionColumn.Field.of(field)).isSelected() && id != null)
                blocks.put(field, new BrainstormDraft.Block(id, context.nameOf(field, id), null, List.of(), false));
        }
        Mood mood = column.check(AdoptionColumn.Field.MOOD).isSelected() ? now.mood() : null;
        Frame frame = column.check(AdoptionColumn.Field.FRAME).isSelected() ? now.frame() : null;
        List<String> images = imageChecks.entrySet().stream()
                .filter((Map.Entry<String, CheckBox> entry) -> entry.getValue().isSelected())
                .map(Map.Entry::getKey).toList();

        listener.adopted(new BrainstormDialog.Result(
                promptArea.getText().trim(),
                modelBox.getValue(),
                mood != null ? mood.getId() : null,
                blocks,
                frame != null ? frame.getId() : null,
                column.check(AdoptionColumn.Field.ASPECT_RATIO).isSelected() ? now.aspectRatio() : null,
                video && durationCheck.isSelected() ? parseSeconds(durationBox.getValue()) : null,
                column.check(AdoptionColumn.Field.RESOLUTION).isSelected() ? now.resolution() : null,
                images,
                saveMoodCheck.isSelected()));
    }

    private static Integer parseSeconds(String value) {
        return value == null ? null : Integer.valueOf(value.replace("s", ""));
    }

    /**
     * A new block the AI proposes for a field, below the field: its name editable, its text, colors or camera
     * fragment below. Its checkbox adopts it into the list of the user and the field; it excludes the checkbox of the
     * field, which adopts the entry chosen there instead.
     */
    private final class Proposal {

        private final Mood.Field field;
        private final Label title = new Label();
        private final CheckBox check = new CheckBox();
        private final TextField nameField = new TextField();
        private final Label textLabel = new Label();
        private final HBox colorBoxes = new HBox(3.0);
        private final VBox node;
        private BrainstormDraft.Block proposed;

        Proposal(Mood.Field field) {
            this.field = field;
            title.getStyleClass().addAll("text-muted", "text-small");
            textLabel.setWrapText(true);
            textLabel.getStyleClass().addAll("text-muted", "text-small");
            check.setMinWidth(Region.USE_PREF_SIZE);
            check.setDisable(column.isLocked(field));
            HBox line = new HBox(8.0, nameField, check);
            line.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(nameField, Priority.ALWAYS);
            node = new VBox(3.0, title, line, field == Mood.Field.COLOR ? colorBoxes : textLabel);

            /*  the field adopts either the entry chosen in it or the proposal  */
            CheckBox fieldCheck = column.check(AdoptionColumn.Field.of(field));
            check.selectedProperty().addListener((obs, was, on) -> {
                if (on)
                    fieldCheck.setSelected(false);
            });
            fieldCheck.selectedProperty().addListener((obs, was, on) -> {
                if (on)
                    check.setSelected(false);
            });
        }

        /** Sets the texts in the current language. */
        void relabel() {
            title.setText(I18nManager.getText("brainstorm.block.proposal"));
            nameField.setPromptText(I18nManager.getText("block.dialog.name.placeholder"));
            check.setTooltip(new Tooltip(I18nManager.getText("brainstorm.block.mylist")));
        }

        /**
         * Shows a new block with its content, ticked; a block without content of its field shows nothing.
         *
         * @param block the new block
         */
        void show(BrainstormDraft.Block block) {
            boolean content = field == Mood.Field.COLOR ? !block.colors().isEmpty()
                    : field == Mood.Field.CAMERA ? block.settings() != null : block.text() != null;
            if (!content) {
                clear();
                return;
            }
            proposed = block;
            nameField.setText(block.name());

            /*  a new camera shows the fragment of its settings  */
            textLabel.setText(block.settings() != null ? CameraPromptBuilder.build(block.settings(), true)
                    : block.text());
            colorBoxes.getChildren().setAll(block.colors().stream().map(ColorBox::new).toList());
            column.slot(field).getChildren().setAll(node);
            check.setSelected(true);
        }

        /** Removes the proposal. */
        void clear() {
            proposed = null;
            check.setSelected(false);
            column.slot(field).getChildren().clear();
        }

        /** Whether the proposal is shown and ticked. */
        boolean isAdopted() {
            return proposed != null && check.isSelected();
        }

        /**
         * The new block to adopt under the name entered. A name taken by an entry of the list asks for the next free
         * one.
         *
         * @return the block, null if the name is empty or the user keeps the taken one, so the dialog stays open
         */
        BrainstormDraft.Block adopted() {
            String name = nameField.getText().trim();
            List<String> taken = field == Mood.Field.COLOR
                    ? context.colorStyles().stream().map(ColorStyle::getName).toList()
                    : context.blocks(field).stream().map(PromptBlock::getName).toList();
            if (name.isEmpty()) {
                nameField.requestFocus();
                return null;
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
                    nameField.requestFocus();
                    return null;
                }
                nameField.setText(free);
                name = free;
            }
            return new BrainstormDraft.Block(null, name, proposed.text(), proposed.colors(), proposed.settings(),
                    true);
        }
    }
}
