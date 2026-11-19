package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryFrame;
import art.moodframe.model.EntryMood;
import art.moodframe.model.Frame;
import art.moodframe.model.Mood;
import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.FavoriteService;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.ui.cell.FrameListCell;
import art.moodframe.ui.cell.MoodListCell;
import art.moodframe.ui.component.DialogButtons;
import art.moodframe.ui.component.FavoriteStar;
import art.moodframe.ui.component.FrameChoice;
import art.moodframe.ui.component.MoodChoice;
import art.moodframe.util.I18nManager;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The groups Mood and Frame of the right panel of the main window ({@code MoodFramePanel.fxml}): the mood with its
 * building blocks (atmosphere, style, color, light, camera) and their locks, and the frame with its target aspect ratio
 * and resolution. A part of its own, which the brainstorm dialog can show as well.
 *
 * <p>The host loads the FXML, follows the chosen model ({@link #followModel}), which the hints below aspect ratio and
 * resolution name, and then hands over the services ({@link #init}). It reads the choice with {@link #selection()} and
 * the prompt blocks with {@link #promptBlocks()}, sets it with {@link #chooseMood(Mood)}, {@link #chooseFrame(Frame)},
 * {@link #setAspectRatio}, {@link #setResolution} or from a chat entry, and learns of every change of the fields by
 * {@link Listener#changed()}. It passes the changes of the library ({@link #onLibraryChanged}) and of the language
 * ({@link #updateTexts()}) on.</p>
 *
 * @since 2.1.3
 */
public class MoodFramePanelController {

    private static final Logger logger = LoggerFactory.getLogger(MoodFramePanelController.class);

    /**
     * The services of the panel.
     *
     * @param config    remembers the choices and the locks (may be null)
     * @param blocks    the building blocks of text
     * @param colors    the palettes
     * @param moods     the moods (may be null: no mood selection)
     * @param frames    the frames (may be null: no frame selection)
     * @param favorites the favorites, listed first (may be null)
     * @param analysis  finds the building blocks of an image for "Mood from image" (may be null: none)
     * @param trash     opens the trash on the blocks, moods and frames (may be null: no trash)
     */
    public record Services(ApplicationConfig config, PromptBlockService blocks, ColorStyleService colors,
                           MoodService moods, FrameService frames, FavoriteService favorites,
                           art.moodframe.service.brainstorm.MoodAnalysisService analysis,
                           art.moodframe.ui.dialog.TrashWindow.Opener trash) {

        /** The services without a trash, e.g. for a window that offers none. */
        public Services(ApplicationConfig config, PromptBlockService blocks, ColorStyleService colors,
                        MoodService moods, FrameService frames, FavoriteService favorites,
                        art.moodframe.service.brainstorm.MoodAnalysisService analysis) {
            this(config, blocks, colors, moods, frames, favorites, analysis, null);
        }
    }

    /** Learns of the changes of the fields. */
    public interface Listener {

        /** A building block, the mood, "Apply mood", the frame, the aspect ratio or the resolution changed. */
        void changed();
    }

    /**
     * What the panel shows as chosen.
     *
     * @param mood         the chosen mood, null for "No mood"
     * @param moodChanged  whether a field differs from the mood
     * @param blocks       the ids of the blocks of the fields, an empty field missing
     * @param locked       the fields whose lock is closed
     * @param frame        the chosen frame, null for "No frame"
     * @param frameChanged whether aspect ratio or resolution differ from the frame
     * @param aspectRatio  the target aspect ratio
     * @param resolution   the target resolution
     */
    public record Selection(Mood mood, boolean moodChanged, Map<Mood.Field, String> blocks, Set<Mood.Field> locked,
                            Frame frame, boolean frameChanged, String aspectRatio, String resolution) {
    }

    // Mood
    @FXML
    private VBox moodGroupPane;
    @FXML
    private Label moodTitleLabel;
    @FXML
    private CheckBox moodApplyCheckBox;
    @FXML
    private Button moodFromImageButton;
    @FXML
    private Button saveMoodButton;
    @FXML
    private Button saveMoodAsButton;
    @FXML
    private Button manageMoodsButton;
    @FXML
    private ComboBox<MoodChoice> moodComboBox;
    @FXML
    private Button moodIncompleteButton;

    // Building blocks
    @FXML
    private ComboBox<art.moodframe.model.StylePresetItem> atmosphereComboBox;
    @FXML
    private Label atmosphereLabel;
    @FXML
    private ToggleButton atmosphereLockButton;
    @FXML
    private ComboBox<art.moodframe.model.StylePresetItem> stylePresetsComboBox;
    @FXML
    private Label styleLabel;
    @FXML
    private ToggleButton styleLockButton;
    @FXML
    private ComboBox<art.moodframe.model.ColorStyleItem> colorStyleComboBox;
    @FXML
    private Label colorStyleLabel;
    @FXML
    private ToggleButton colorLockButton;
    @FXML
    private ComboBox<art.moodframe.model.StylePresetItem> lightComboBox;
    @FXML
    private Label lightLabel;
    @FXML
    private ToggleButton lightLockButton;
    @FXML
    private Label lightLayoutHint;
    @FXML
    private ComboBox<art.moodframe.model.StylePresetItem> cameraComboBox;
    @FXML
    private Label cameraLabel;
    @FXML
    private ToggleButton cameraLockButton;
    @FXML
    private Label cameraHint;

    // Frame
    @FXML
    private VBox frameGroupPane;
    @FXML
    private Label frameTitleLabel;
    @FXML
    private Button saveFrameButton;
    @FXML
    private Button saveFrameAsButton;
    @FXML
    private Button manageFramesButton;
    @FXML
    private ComboBox<FrameChoice> frameComboBox;
    @FXML
    private Label aspectRatioLabel;
    @FXML
    private ChoiceBox<String> aspectRatioComboBox;
    @FXML
    private Label aspectRatioHintLabel;
    @FXML
    private Label resolutionLabel;
    @FXML
    private ChoiceBox<String> resolutionChoiceBox;
    @FXML
    private Label resolutionHintLabel;

    /*  The lists of the text blocks by their type and the list of the palettes  */
    private final Map<art.moodframe.model.PromptBlock.Type,
            art.moodframe.ui.component.BlockChoice<art.moodframe.model.StylePresetItem>> blockChoices =
            new java.util.EnumMap<>(art.moodframe.model.PromptBlock.Type.class);
    private art.moodframe.ui.component.BlockChoice<art.moodframe.model.ColorStyleItem> colorChoice;

    private ApplicationConfig config;
    private PromptBlockService blockService;
    private ColorStyleService colorService;
    private FavoriteService favoriteService;
    private StatusSink status;
    private Listener listener = () -> { };

    /*  the chosen model, which the hints below aspect ratio and resolution name  */
    private ObservableValue<String> model;

    /*  whether the image gets a reference image: the blocks then apply only with "Apply mood"  */
    private boolean reference;

    /*  The moods, and a guard while the mood selection sets the fields; whether the chosen mood is changed is
        compared anew from the fields each time (see isMoodChanged)  */
    private MoodService moodService;
    private boolean updatingMood;

    /*  The frames: whether aspect ratio or resolution differ from the chosen frame, and a guard while the frame
        selection sets them  */
    private FrameService frameService;
    private art.moodframe.service.brainstorm.MoodAnalysisService moodAnalysis;
    private art.moodframe.ui.dialog.TrashWindow.Opener trash;
    private boolean frameChanged;
    private boolean updatingFrame;

    @FXML
    private void initialize() {
        /*  the targets of all models, of which the chosen model takes its closest one  */
        aspectRatioComboBox.getItems().addAll(art.moodframe.model.AspectRatios.ALL);
        aspectRatioComboBox.valueProperty().addListener((obs, oldVal, newVal) -> updateAspectRatioHint());
        resolutionChoiceBox.getItems().addAll(art.moodframe.model.ModelFamilies.RESOLUTIONS);
        resolutionChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> updateResolutionHint());
    }

    /**
     * Follows the chosen model, whose name and closest aspect ratio and resolution the hints show; before
     * {@link #init}, which takes the resolution of the model as target if none is remembered.
     *
     * @param model the item of the chosen model, as the model dropdown lists it
     */
    public void followModel(ObservableValue<String> model) {
        this.model = model;
        model.addListener((obs, oldVal, newVal) -> {
            updateAspectRatioHint();
            updateResolutionHint();
        });
    }

    /*  the item of the chosen model, null without  */
    private String modelItem() {
        return model != null ? model.getValue() : null;
    }

    /**
     * Fills the lists from the services, restores the remembered choices and from then on remembers every choice.
     *
     * @param services the services of the panel
     * @param status   shows what the panel did
     * @param listener learns of the changes of the fields
     */
    public void init(Services services, StatusSink status, Listener listener) {
        this.config = services.config();
        this.blockService = services.blocks();
        this.colorService = services.colors();
        this.favoriteService = services.favorites();
        this.moodService = services.moods();
        this.frameService = services.frames();
        this.moodAnalysis = services.analysis();
        this.trash = services.trash();
        this.status = status;

        setupBlockChoices();
        setupBlockLocks();
        loadSelections();
        setupPersistenceListeners();
        setupMoods();
        setupFrames();
        this.listener = listener;
        for (ObservableValue<?> value : List.<ObservableValue<?>>of(atmosphereComboBox.valueProperty(),
                stylePresetsComboBox.valueProperty(), colorStyleComboBox.valueProperty(), lightComboBox.valueProperty(),
                cameraComboBox.valueProperty(), moodApplyCheckBox.selectedProperty(), frameComboBox.valueProperty(),
                aspectRatioComboBox.valueProperty(), resolutionChoiceBox.valueProperty()))
            value.addListener((obs, oldVal, newVal) -> listener.changed());

        /*  The open lists start flush with their field and are as wide as it  */
        java.util.stream.Stream.of(moodComboBox, atmosphereComboBox, stylePresetsComboBox, colorStyleComboBox,
                lightComboBox, cameraComboBox, frameComboBox)
                .forEach(art.moodframe.ui.component.DropdownBounds::keepInWindow);
        java.util.stream.Stream.of(aspectRatioComboBox, resolutionChoiceBox)
                .forEach(art.moodframe.ui.component.DropdownBounds::keepInWindow);
    }

    /** The group Mood, a card of the panel. */
    public VBox moodGroup() {
        return moodGroupPane;
    }

    /** The group Frame, a card of the panel. */
    public VBox frameGroup() {
        return frameGroupPane;
    }

    /** The target resolution, which the model follows. */
    public ReadOnlyObjectProperty<String> resolutionProperty() {
        return resolutionChoiceBox.valueProperty();
    }

    /** The field of the target resolution, for the tests. */
    ChoiceBox<String> resolutionChoice() {
        return resolutionChoiceBox;
    }

    /** The hint below the resolution, for the tests. */
    Label resolutionHint() {
        return resolutionHintLabel;
    }

    /** What the panel shows as chosen. */
    public Selection selection() {
        return new Selection(chosenMood(), isMoodChanged(), currentMoodFields(), lockedMoodFields(), chosenFrame(),
                frameChanged, aspectRatioComboBox.getValue(), resolutionChoiceBox.getValue());
    }

    /**
     * Tells whether the image gets a reference image: then the building blocks go into the prompt only with
     * "Apply mood", which shows only then.
     *
     * @param reference whether an image is attached
     */
    public void setReference(boolean reference) {
        this.reference = reference;
    }

    /** The building blocks that go into the prompt; none with a reference image unless "Apply mood" is on. */
    public art.moodframe.service.prompt.PromptComposer.Selection promptBlocks() {
        return selectedBlocks();
    }

    /** The target aspect ratio. */
    public String aspectRatio() {
        return aspectRatioComboBox.getValue();
    }

    /** The target resolution. */
    public String resolution() {
        return resolutionChoiceBox.getValue();
    }

    /**
     * Sets the target aspect ratio, if it is one of the list.
     *
     * @param aspectRatio the aspect ratio, for example "16:9"
     */
    public void setAspectRatio(String aspectRatio) {
        if (aspectRatio != null && aspectRatioComboBox.getItems().contains(aspectRatio))
            aspectRatioComboBox.setValue(aspectRatio);
    }

    /**
     * Sets the target resolution, if it is one of the list.
     *
     * @param resolution the resolution, for example "2K"
     */
    public void setResolution(String resolution) {
        if (resolution != null && resolutionChoiceBox.getItems().contains(resolution))
            resolutionChoiceBox.setValue(resolution);
    }

    /**
     * Chooses a mood by its id, as the list by mood opens it; an unknown id changes nothing.
     *
     * @param moodId the id of the mood
     */
    public void chooseMoodById(String moodId) {
        Mood mood = moodService != null ? moodService.find(moodId) : null;
        if (mood != null)
            chooseMood(mood);
    }

    /**
     * Chooses a frame by its id, as the list by frame opens it; an unknown id changes nothing.
     *
     * @param frameId the id of the frame
     */
    public void chooseFrameById(String frameId) {
        Frame frame = frameService != null ? frameService.find(frameId) : null;
        if (frame != null)
            chooseFrame(frame);
    }

    /**
     * Shows a change of the library in the lists, keeping their choice; whoever made it.
     *
     * @param kind what changed
     */
    public void onLibraryChanged(LibraryEvents.Kind kind) {
        switch (kind) {
            case MOOD -> refreshMoods();
            case FRAME -> refreshFrames();
            case ATMOSPHERE, STYLE, LIGHT, CAMERA, COLOR -> {
                refreshBlockChoices();
                refreshMoods();
            }
            case FAVORITE -> reorderFavorites();
            default -> {
            }
        }
    }

    /**
     * Enables the fields while the window can take a prompt. The mood fields are greyed while a reference image is
     * attached without "Apply mood"; the light also for a layout style, keeping its choice (the composer leaves it
     * out).
     *
     * @param enableInput whether the fields can be used
     */
    public void setInputEnabled(boolean enableInput) {
        aspectRatioComboBox.setDisable(!enableInput);
        aspectRatioLabel.setDisable(!enableInput);
        resolutionChoiceBox.setDisable(!enableInput);
        frameComboBox.setDisable(!enableInput);
        saveFrameAsButton.setDisable(!enableInput);
        manageFramesButton.setDisable(!enableInput);
        saveFrameButton.setDisable(frameComboBox.isDisable() || isFrameSaveLocked());

        boolean moodApplies = enableInput && (!reference || moodApplyCheckBox.isSelected());
        art.moodframe.model.StylePresetItem styleItem = stylePresetsComboBox.getValue();
        boolean layoutStyle = styleItem != null && styleItem.getBlock() != null && styleItem.getBlock().isLayout();
        moodApplyCheckBox.setVisible(reference);
        moodApplyCheckBox.setManaged(reference);
        moodApplyCheckBox.setDisable(!enableInput);
        for (Node field : List.of(moodComboBox, moodFromImageButton, saveMoodAsButton, manageMoodsButton,
                atmosphereLabel, atmosphereComboBox, styleLabel, stylePresetsComboBox, colorStyleLabel,
                colorStyleComboBox))
            field.setDisable(!moodApplies);
        saveMoodButton.setDisable(moodComboBox.isDisable() || isMoodSaveLocked());
        lightLabel.setDisable(!moodApplies || layoutStyle);
        lightComboBox.setDisable(!moodApplies || layoutStyle);
        lightLayoutHint.setVisible(layoutStyle);
        lightLayoutHint.setManaged(layoutStyle);
        cameraLabel.setDisable(!moodApplies);
        cameraComboBox.setDisable(!moodApplies);
        updateCameraHint();
    }

    /** Shows the texts of the panel in the current language; the lists of moods and frames name "No mood" anew. */
    public void updateTexts() {
        moodTitleLabel.setText(I18nManager.getText("params.mood.title"));
        frameTitleLabel.setText(I18nManager.getText("params.frame.title"));
        moodApplyCheckBox.setText(I18nManager.getText("params.mood.apply"));
        moodApplyCheckBox.setTooltip(new Tooltip(I18nManager.getText("params.mood.apply.tooltip")));
        moodFromImageButton.setTooltip(new Tooltip(I18nManager.getText("params.mood.from.image.tooltip")));
        saveMoodButton.setTooltip(new Tooltip(I18nManager.getText("params.mood.save.tooltip")));
        saveMoodAsButton.setTooltip(new Tooltip(I18nManager.getText("params.mood.save.as.tooltip")));
        manageMoodsButton.setTooltip(new Tooltip(I18nManager.getText("params.mood.manage.tooltip")));
        refreshMoods();
        saveFrameButton.setTooltip(new Tooltip(I18nManager.getText("params.frame.save.tooltip")));
        saveFrameAsButton.setTooltip(new Tooltip(I18nManager.getText("params.frame.save.as.tooltip")));
        manageFramesButton.setTooltip(new Tooltip(I18nManager.getText("params.frame.manage.tooltip")));
        refreshFrames();

        styleLabel.setText(I18nManager.getText("params.style"));
        lightLabel.setText(I18nManager.getText("params.light"));
        lightLayoutHint.setText(I18nManager.getText("params.light.layout.hint"));
        cameraLabel.setText(I18nManager.getText("params.camera"));
        updateCameraHint();
        atmosphereLabel.setText(I18nManager.getText("params.atmosphere"));
        colorStyleLabel.setText(I18nManager.getText("params.colorstyle"));
        for (ToggleButton lock : List.of(atmosphereLockButton, styleLockButton, colorLockButton, lightLockButton,
                cameraLockButton))
            lock.setTooltip(new Tooltip(I18nManager.getText("params.block.lock.tooltip")));
        blockChoices.values().forEach(art.moodframe.ui.component.BlockChoice::updateTexts);
        if (colorChoice != null)
            colorChoice.updateTexts();
        aspectRatioLabel.setText(I18nManager.getText("params.aspect.ratio"));
        resolutionLabel.setText(I18nManager.getText("params.resolution"));
        updateResolutionHint();
    }

    /**
     * Describes the mood and the building blocks for the brainstorm: the mood, marked if changed, and each block field
     * with its entry and its lock, one per line.
     *
     * @return the lines, each starting with a line break
     */
    public String describe() {
        Set<Mood.Field> locked = lockedMoodFields();
        Mood mood = moodService != null ? chosenMood() : null;
        return "\nMood: " + (mood != null ? mood.getName() + (isMoodChanged() ? " (changed)" : "") : "None")
                + fieldState("Style", stylePresetsComboBox.getValue(), locked.contains(Mood.Field.STYLE))
                + fieldState("Atmosphere", atmosphereComboBox.getValue(), locked.contains(Mood.Field.ATMOSPHERE))
                + fieldState("Light", lightComboBox.getValue(), locked.contains(Mood.Field.LIGHT))
                + fieldState("Color", colorStyleComboBox.getValue(), locked.contains(Mood.Field.COLOR))
                + fieldState("Camera", cameraComboBox.getValue(), locked.contains(Mood.Field.CAMERA));
    }

    /**
     * Takes over the mood and the building blocks adopted in the brainstorm popup: the mood sets the fields that are
     * not locked, an adopted block then replaces the block of its field and a field that is not adopted keeps its
     * block; a locked field keeps its choice.
     *
     * @param moodId the id of the adopted mood, null for none
     * @param blocks the adopted blocks by their field
     */
    public void adoptBrainstorm(String moodId, Map<Mood.Field, art.moodframe.model.BrainstormDraft.Block> blocks) {
        Map<Mood.Field, String> before = currentMoodFields();
        Set<Mood.Field> locked = lockedMoodFields();
        Mood mood = moodId != null && moodService != null ? moodService.find(moodId) : null;
        if (mood != null)
            chooseMood(mood);
        for (Mood.Field field : art.moodframe.model.BrainstormDraft.FIELDS) {
            if (locked.contains(field))
                continue;
            art.moodframe.model.BrainstormDraft.Block block = blocks.get(field);
            if (block != null)
                adoptBlock(field, block);
            else if (mood != null)
                selectField(field, before.get(field));
        }
    }

    /** Saves the fields as a new mood under a name the user enters, as "Save as..." does. */
    public void saveMoodAsNew() {
        if (moodService != null)
            saveMood(true);
    }

    /*  the window of the panel, the owner of its dialogs  */
    private Window window() {
        return moodGroupPane.getScene() != null ? moodGroupPane.getScene().getWindow() : null;
    }

    /**
     * Restores the remembered choices: resolution (before there was one, the one closest to the resolution of the
     * model), aspect ratio and the building blocks; a merged former style resolves to the one it went into, a renamed
     * default palette to its new name.
     */
    private void loadSelections() {
        if (config == null)
            return;
        try {
            String savedResolution = config.generation().getDefaultResolution();
            resolutionChoiceBox.setValue(savedResolution != null ? savedResolution
                    : art.moodframe.model.ModelFamilies.nearest(art.moodframe.model.ModelFamilies.resolutionOf(
                            modelItem()), art.moodframe.model.ModelFamilies.RESOLUTIONS));
            aspectRatioComboBox.setValue(config.generation().getDefaultAspectRatio());

            art.moodframe.model.PromptBlock savedBlock = blockService != null
                    ? blockService.find(art.moodframe.model.PromptBlock.Type.STYLE,
                            config.generation().getDefaultStyle()) : null;
            String savedStyle = savedBlock != null ? savedBlock.getName() : config.generation().getDefaultStyle();
            if (savedStyle != null)
                stylePresetsComboBox.getItems().stream().filter((item) -> savedStyle.equals(item.getName()))
                        .findFirst().ifPresent(stylePresetsComboBox::setValue);

            art.moodframe.model.ColorStyle savedPalette = colorService != null
                    ? colorService.getStyleByName(config.generation().getDefaultColorStyle()) : null;
            String savedColorStyle = savedPalette != null ? savedPalette.getName()
                    : config.generation().getDefaultColorStyle();
            if (savedColorStyle != null)
                colorStyleComboBox.getItems().stream().filter((item) -> savedColorStyle.equals(item.getName()))
                        .findFirst().ifPresent(colorStyleComboBox::setValue);

            for (Map.Entry<ComboBox<art.moodframe.model.StylePresetItem>, String> saved : Map.of(
                    lightComboBox, String.valueOf(config.generation().getDefaultLight()),
                    cameraComboBox, String.valueOf(config.generation().getDefaultCamera()),
                    atmosphereComboBox, String.valueOf(config.generation().getDefaultAtmosphere())).entrySet())
                saved.getKey().getItems().stream().filter((item) -> item.getName().equals(saved.getValue()))
                        .findFirst().ifPresent(saved.getKey()::setValue);
        } catch (Exception e) {
            logger.error("Failed to load the choices of mood and frame from the configuration", e);
            aspectRatioComboBox.setValue("16:9");
            fillBlockChoice(art.moodframe.model.PromptBlock.Type.STYLE, null);
        }
    }

    /** Remembers the choices of aspect ratio, resolution and building blocks; an action of a list is no choice. */
    private void setupPersistenceListeners() {
        if (config == null)
            return;
        aspectRatioComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null)
                config.generation().setDefaultAspectRatio(newVal);
        });
        resolutionChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null)
                config.generation().setDefaultResolution(newVal);
        });
        stylePresetsComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && blockChoices.get(art.moodframe.model.PromptBlock.Type.STYLE).isEntry(newVal))
                config.generation().setDefaultStyle(newVal.getName());
        });
        colorStyleComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && colorChoice.isEntry(newVal))
                config.generation().setDefaultColorStyle(newVal.getName());
        });
        lightComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && blockChoices.get(art.moodframe.model.PromptBlock.Type.LIGHT).isEntry(newVal))
                config.generation().setDefaultLight(newVal.getName());
        });
        cameraComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && blockChoices.get(art.moodframe.model.PromptBlock.Type.CAMERA).isEntry(newVal))
                config.generation().setDefaultCamera(newVal.getName());
        });
        atmosphereComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && blockChoices.get(art.moodframe.model.PromptBlock.Type.ATMOSPHERE).isEntry(newVal))
                config.generation().setDefaultAtmosphere(newVal.getName());
        });
    }

    /**
     * Sets up the building block lists: style, atmosphere, light and camera from the blocks, the color from the
     * palettes. Each list holds "None" (the default) and the entries, the styles grouped into photo, art and layout;
     * its actions "Edit", "New" and "Manage" sit as icons left of its lock (see
     * {@link art.moodframe.ui.component.BlockChoice}).
     */
    private void setupBlockChoices() {
        java.util.Map<art.moodframe.model.PromptBlock.Type, ToggleButton> locks =
                java.util.Map.of(art.moodframe.model.PromptBlock.Type.STYLE, styleLockButton,
                        art.moodframe.model.PromptBlock.Type.ATMOSPHERE, atmosphereLockButton,
                        art.moodframe.model.PromptBlock.Type.LIGHT, lightLockButton,
                        art.moodframe.model.PromptBlock.Type.CAMERA, cameraLockButton);
        locks.forEach((type, lock) -> {
            ComboBox<art.moodframe.model.StylePresetItem> comboBox = blockComboBox(type);
            art.moodframe.ui.component.BlockChoice<art.moodframe.model.StylePresetItem> choice =
                    new art.moodframe.ui.component.BlockChoice<>(comboBox, lock,
                            (name) -> new art.moodframe.model.StylePresetItem(name, "", false),
                            (item) -> item.getBlock() != null,
                            new art.moodframe.ui.component.BlockChoice.Actions<>() {
                                @Override
                                public void edit(art.moodframe.model.StylePresetItem entry) {
                                    editBlock(type, entry.getBlock());
                                }

                                @Override
                                public void create() {
                                    editBlock(type, null);
                                }

                                @Override
                                public void manage() {
                                    manageBlocks(type);
                                }
                            });
            FavoriteStar.Favorites<art.moodframe.model.StylePresetItem> favorites = favorites(comboBox,
                    FavoriteService.Kind.of(type), MoodFramePanelController::blockId);
            comboBox.setCellFactory((listView) -> new art.moodframe.ui.cell.StylePresetListCell(choice,
                    favorites));
            comboBox.setButtonCell(new art.moodframe.ui.cell.StylePresetListCell());
            blockChoices.put(type, choice);
            fillBlockChoice(type, null);
        });

        colorChoice = new art.moodframe.ui.component.BlockChoice<>(colorStyleComboBox, colorLockButton,
                (name) -> new art.moodframe.model.ColorStyleItem(name, java.util.Collections.emptyList(), true),
                (item) -> !"None".equals(item.getName()),
                new art.moodframe.ui.component.BlockChoice.Actions<>() {
                    @Override
                    public void edit(art.moodframe.model.ColorStyleItem entry) {
                        editPalette(entry);
                    }

                    @Override
                    public void create() {
                        editPalette(null);
                    }

                    @Override
                    public void manage() {
                        managePalettes();
                    }
                });
        FavoriteStar.Favorites<art.moodframe.model.ColorStyleItem> paletteFavorites = favorites(colorStyleComboBox,
                FavoriteService.Kind.COLOR, art.moodframe.model.ColorStyleItem::getId);
        colorStyleComboBox.setCellFactory((listView) -> new art.moodframe.ui.cell.ColorStyleListCell(
                paletteFavorites));
        colorStyleComboBox.setButtonCell(new art.moodframe.ui.cell.ColorStyleListCell());
        fillColorChoice(null);
    }

    /**
     * Fills the list of a text block type with "None" and the blocks: the favorites first, then the others, the styles
     * ordered by their groups behind a group of the favorites; each part alphabetically.
     *
     * @param type the type of the blocks
     * @param name the name of the block to select, null for "None"
     */
    private void fillBlockChoice(art.moodframe.model.PromptBlock.Type type, String name) {
        List<art.moodframe.model.StylePresetItem> entries = new java.util.ArrayList<>();
        entries.add(new art.moodframe.model.StylePresetItem("None", "", false));
        art.moodframe.service.library.PromptBlockService service = blockService;
        boolean grouped = type == art.moodframe.model.PromptBlock.Type.STYLE;
        java.util.function.Predicate<art.moodframe.model.StylePresetItem> favorite = (item) -> item.getBlock() != null
                && isFavorite(FavoriteService.Kind.of(type), item.getBlock().getId());
        if (service == null)
            logger.warn("PromptBlockService not available, {} list only offers None", type);
        else
            entries.addAll(FavoriteService.order(service.getAll(type).stream()
                    .map(art.moodframe.model.StylePresetItem::new).toList(), favorite,
                    (item) -> grouped ? STYLE_GROUPS.indexOf(styleGroupKey(item)) : 0,
                    art.moodframe.model.StylePresetItem::getName));
        blockChoices.get(type).setEntries(entries, grouped ? (item) -> favorite.test(item) ? "block.group.favorites"
                : styleGroupKey(item) : null, name);
    }

    /*  the id of the block of a list item  */
    private static String blockId(art.moodframe.model.StylePresetItem item) {
        return item.getBlock().getId();
    }

    /*  The headers of the style groups: the two media photo and art, then layout  */
    private static final List<String> STYLE_GROUPS = List.of("block.group.photo", "block.group.art",
            "block.group.layout");

    /** The header of the group of a style; a style of the user without group goes by its photographic flag. */
    private static String styleGroupKey(art.moodframe.model.StylePresetItem item) {
        art.moodframe.model.PromptBlock block = item.getBlock();
        if (block == null)
            return null;
        return block.isLayout() ? "block.group.layout" : block.isPhotographic() ? "block.group.photo" : "block.group.art";
    }

    /**
     * Fills the color list with "None" and the palettes: the favorites first, then the others, each part
     * alphabetically.
     *
     * @param name the name of the palette to select, null for "None"
     */
    private void fillColorChoice(String name) {
        List<art.moodframe.model.ColorStyleItem> entries = new java.util.ArrayList<>();
        entries.add(new art.moodframe.model.ColorStyleItem("None", java.util.Collections.emptyList(), true));
        art.moodframe.service.library.ColorStyleService service = colorService;
        if (service == null)
            logger.warn("ColorStyleService not available, color list only offers None");
        else
            entries.addAll(FavoriteService.order(service.getAllStyles().stream()
                    .map(art.moodframe.model.ColorStyle::toColorStyleItem).toList(),
                    (item) -> isFavorite(FavoriteService.Kind.COLOR, item.getId()), (item) -> 0,
                    art.moodframe.model.ColorStyleItem::getName));
        colorChoice.setEntries(entries, null, name);
    }

    /**
     * Loads all building block lists again, e.g. after an import or a restore from the trash; each keeps its
     * selection while its entry exists.
     */
    private void refreshBlockChoices() {
        blockChoices.forEach((type, choice) -> {
            ComboBox<art.moodframe.model.StylePresetItem> comboBox = blockComboBox(type);
            fillBlockChoice(type, comboBox.getValue() != null ? comboBox.getValue().getName() : null);
        });
        if (colorChoice != null)
            fillColorChoice(colorStyleComboBox.getValue() != null ? colorStyleComboBox.getValue().getName() : null);
    }

    /**
     * The favorites of a list, for the stars of its rows; a switched favorite is stored at once, and the lists follow
     * it ({@link #reorderFavorites}).
     *
     * @param field the field whose open list shows the rows, null for a list in a dialog
     * @param kind  the kind of the entries
     * @param id    tells the id of an entry
     */
    private <T> FavoriteStar.Favorites<T> favorites(ComboBox<?> field, FavoriteService.Kind kind,
            java.util.function.Function<T, String> id) {
        return new FavoriteStar.Favorites<>(field, (entry) -> isFavorite(kind, id.apply(entry)), (entry) -> {
            FavoriteService service = favoriteService;
            if (service != null)
                service.toggle(kind, id.apply(entry));
        });
    }

    /**
     * Lists the moods, the frames and the building blocks again in the order of the favorites, each keeping its
     * choice; an open list keeps its order until its field opens it the next time, so no row moves away under the
     * pointer.
     */
    private void reorderFavorites() {
        java.util.Map<ComboBox<?>, Runnable> reloads = new java.util.LinkedHashMap<>();
        if (moodService != null)
            reloads.put(moodComboBox, () -> {
                Mood chosen = chosenMood();
                reloadMoods(chosen != null ? chosen.getId() : "");
            });
        if (frameService != null)
            reloads.put(frameComboBox, () -> {
                Frame chosen = chosenFrame();
                reloadFrames(chosen != null ? chosen.getId() : "");
            });
        blockChoices.keySet().forEach((type) -> reloads.put(blockComboBox(type), () -> {
            ComboBox<art.moodframe.model.StylePresetItem> comboBox = blockComboBox(type);
            updatingMood = true;
            fillBlockChoice(type, comboBox.getValue() != null ? comboBox.getValue().getName() : null);
            updatingMood = false;
        }));
        if (colorChoice != null)
            reloads.put(colorStyleComboBox, () -> {
                updatingMood = true;
                fillColorChoice(colorStyleComboBox.getValue() != null ? colorStyleComboBox.getValue().getName() : null);
                updatingMood = false;
            });
        reloads.forEach((field, reload) -> {
            if (field.isShowing())
                field.setOnShowing((event) -> {
                    field.setOnShowing(null);
                    reload.run();
                });
            else
                reload.run();
        });
    }

    /** Whether an entry is a favorite of the user; none without the service. */
    private boolean isFavorite(FavoriteService.Kind kind, String id) {
        FavoriteService service = favoriteService;
        return service != null && service.isFavorite(kind, id);
    }

    private ComboBox<art.moodframe.model.StylePresetItem> blockComboBox(art.moodframe.model.PromptBlock.Type type) {
        return switch (type) {
            case STYLE -> stylePresetsComboBox;
            case ATMOSPHERE -> atmosphereComboBox;
            case LIGHT -> lightComboBox;
            case CAMERA -> cameraComboBox;
        };
    }

    /**
     * Creates or edits a text building block in its dialog and selects the stored block. A block of the user is
     * saved in place or as a new block, a standard block only as a new one, which keeps the group of its origin.
     * A camera has a dialog of its own (see {@link #editCamera}).
     *
     * @param type  the type of the block
     * @param block the block to edit, null for a new one
     */
    private String editBlock(art.moodframe.model.PromptBlock.Type type, art.moodframe.model.PromptBlock block) {
        art.moodframe.service.library.PromptBlockService service = blockService;
        if (service == null)
            return null;
        if (type == art.moodframe.model.PromptBlock.Type.CAMERA)
            return editCamera(service, block);

        art.moodframe.ui.dialog.BlockEditDialog dialog = new art.moodframe.ui.dialog.BlockEditDialog(type, block,
                blockService);
        dialog.initOwner(activeWindow());
        String[] storedId = { null };
        dialog.showAndWait().ifPresent((result) -> {
            boolean inPlace = block != null && result.save() == art.moodframe.ui.dialog.BlockEditDialog.Save.SAVE;
            art.moodframe.model.PromptBlock stored = new art.moodframe.model.PromptBlock(
                    inPlace ? block.getId() : null, type, result.name(), result.text(),
                    block != null ? block.getGroup() : null, result.photographic());
            if (block != null) {
                stored.setParams(block.getParams());
                stored.setSortOrder(block.getSortOrder());
            }
            boolean saved = inPlace ? service.updateCustom(stored)
                    : result.save() == art.moodframe.ui.dialog.BlockEditDialog.Save.SAVE ? service.addCustom(stored)
                    : service.addCopy(stored);
            if (saved) {
                fillBlockChoice(type, stored.getName());
                storedId[0] = stored.getId();
            }
            else
                status.status(I18nManager.getText("block.error.save", result.name()));
        });
        return storedId[0];
    }

    /**
     * Creates or edits a camera in its dialog of settings and selects the stored camera. A camera of the user is saved
     * in place or as a new one, a standard camera only as a new one.
     *
     * @param service the building blocks
     * @param block   the camera to edit, null for a new one
     */
    private String editCamera(art.moodframe.service.library.PromptBlockService service,
                              art.moodframe.model.PromptBlock block) {
        art.moodframe.ui.dialog.CameraEditDialog dialog = new art.moodframe.ui.dialog.CameraEditDialog(block,
                blockService);
        dialog.initOwner(activeWindow());
        String[] storedId = { null };
        dialog.showAndWait().ifPresent((result) -> {
            boolean inPlace = block != null && result.save() == art.moodframe.ui.dialog.BlockEditDialog.Save.SAVE;
            art.moodframe.model.PromptBlock stored = art.moodframe.service.library.PromptBlockService.camera(
                    inPlace ? block.getId() : null, result.name(), result.settings());
            if (block != null)
                stored.setSortOrder(block.getSortOrder());
            boolean saved = inPlace ? service.updateCustom(stored)
                    : result.save() == art.moodframe.ui.dialog.BlockEditDialog.Save.SAVE ? service.addCustom(stored)
                    : service.addCopy(stored);
            if (saved) {
                fillBlockChoice(art.moodframe.model.PromptBlock.Type.CAMERA, stored.getName());
                storedId[0] = stored.getId();

                /*  a camera changed in place keeps its name, so the list reports no change of the selection  */
                listener.changed();
            }
            else
                status.status(I18nManager.getText("block.error.save", result.name()));
        });
        return storedId[0];
    }

    /**
     * Creates or edits a palette in its dialog and selects the stored palette. A palette of the user is saved in
     * place or as a new one, a default palette only as a new one.
     *
     * @param item the palette to edit, null for a new one
     */
    private String editPalette(art.moodframe.model.ColorStyleItem item) {
        art.moodframe.service.library.ColorStyleService service = colorService;
        if (service == null)
            return null;

        art.moodframe.model.ColorStyle palette = item != null ? service.getStyleByName(item.getName()) : null;
        art.moodframe.ui.dialog.ColorStyleEditDialog dialog =
                new art.moodframe.ui.dialog.ColorStyleEditDialog(palette, colorService);
        dialog.initOwner(activeWindow());
        String[] storedId = { null };
        dialog.showAndWait().ifPresent((result) -> {
            art.moodframe.model.ColorStyle stored = new art.moodframe.model.ColorStyle(result.name(), result.colors(),
                    false);
            boolean save = result.save() == art.moodframe.ui.dialog.BlockEditDialog.Save.SAVE;
            boolean saved = palette != null && save ? service.updateCustomStyle(palette.getName(), stored)
                    : save ? service.addCustomStyle(stored) : service.addCopy(stored);
            if (saved) {
                fillColorChoice(stored.getName());
                art.moodframe.model.ColorStyle now = service.getStyleByName(stored.getName());
                storedId[0] = now != null ? now.getId() : "";
            }
            else
                status.status(I18nManager.getText("block.error.save", result.name()));
        });
        return storedId[0];
    }

    /*  the focused window of the app (a manage dialog, if open), so a nested dialog opens on top of it  */
    private javafx.stage.Window activeWindow() {
        return javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isFocused).findFirst()
                .orElse(window());
    }

    /**
     * Opens the "Manage…" dialog for the text blocks of the user of a type; the lists follow its changes.
     *
     * @param type the type of the blocks
     */
    private void manageBlocks(art.moodframe.model.PromptBlock.Type type) {
        art.moodframe.service.library.PromptBlockService service = blockService;
        if (service == null)
            return;

        String typeKey = type.name().toLowerCase(java.util.Locale.ROOT);
        new art.moodframe.ui.dialog.BlockManageDialog<>(window(),
                I18nManager.getText("block.manage.title." + typeKey), "moodframe-" + typeKey + ".json",
                new art.moodframe.ui.dialog.BlockManageDialog.Library<art.moodframe.model.StylePresetItem>() {
                    @Override
                    public List<art.moodframe.model.StylePresetItem> list() {
                        return service.getCustom(type).stream().map(art.moodframe.model.StylePresetItem::new).toList();
                    }

                    @Override
                    public String id(art.moodframe.model.StylePresetItem entry) {
                        return entry.getBlock().getId();
                    }

                    @Override
                    public String name(art.moodframe.model.StylePresetItem entry) {
                        return entry.getName();
                    }

                    @Override
                    public boolean delete(art.moodframe.model.StylePresetItem entry) {
                        return service.deleteCustom(id(entry));
                    }

                    @Override
                    public boolean reorder(List<art.moodframe.model.StylePresetItem> entries) {
                        return service.reorderCustom(type, entries.stream().map(this::id).toList());
                    }

                    @Override
                    public void exportTo(File file) throws IOException {
                        service.exportCustom(type, file);
                    }

                    @Override
                    public int importFrom(File file) throws IOException {
                        return service.importCustom(type, file);
                    }

                    @Override
                    public String create() {
                        return editBlock(type, null);
                    }

                    @Override
                    public String edit(art.moodframe.model.StylePresetItem entry) {
                        return editBlock(type, entry.getBlock());
                    }

                    @Override
                    public boolean editable() {
                        return true;
                    }
                }, (listView) -> new art.moodframe.ui.cell.StylePresetListCell(null, favorites(null,
                        FavoriteService.Kind.of(type), MoodFramePanelController::blockId)), moodService, trash)
                .showAndWait();
    }

    /**
     * Opens the "Manage…" dialog for the palettes of the user; the lists follow its changes.
     */
    private void managePalettes() {
        art.moodframe.service.library.ColorStyleService service = colorService;
        if (service == null)
            return;

        new art.moodframe.ui.dialog.BlockManageDialog<>(window(),
                I18nManager.getText("block.manage.title.palette"), "moodframe-palettes.json",
                new art.moodframe.ui.dialog.BlockManageDialog.Library<art.moodframe.model.ColorStyleItem>() {
                    @Override
                    public List<art.moodframe.model.ColorStyleItem> list() {
                        return service.getCustomStyles().stream()
                                .map(art.moodframe.model.ColorStyle::toColorStyleItem).toList();
                    }

                    @Override
                    public String id(art.moodframe.model.ColorStyleItem entry) {
                        art.moodframe.model.ColorStyle palette = service.getStyleByName(entry.getName());
                        return palette != null ? palette.getId() : "";
                    }

                    @Override
                    public String name(art.moodframe.model.ColorStyleItem entry) {
                        return entry.getName();
                    }

                    @Override
                    public boolean delete(art.moodframe.model.ColorStyleItem entry) {
                        return service.deleteCustomStyle(entry.getName());
                    }

                    @Override
                    public boolean reorder(List<art.moodframe.model.ColorStyleItem> entries) {
                        return service.reorderCustomStyles(entries.stream().map(this::id).toList());
                    }

                    @Override
                    public void exportTo(File file) throws IOException {
                        if (!service.exportStyles(file))
                            throw new IOException("Failed to write " + file);
                    }

                    @Override
                    public int importFrom(File file) {
                        return service.importStyles(file, true);
                    }

                    @Override
                    public String create() {
                        return editPalette(null);
                    }

                    @Override
                    public String edit(art.moodframe.model.ColorStyleItem entry) {
                        return editPalette(entry);
                    }

                    @Override
                    public boolean editable() {
                        return true;
                    }
                }, (listView) -> new art.moodframe.ui.cell.ColorStyleListCell(favorites(null,
                        FavoriteService.Kind.COLOR, art.moodframe.model.ColorStyleItem::getId)), moodService, trash)
                .showAndWait();
    }

    /**
     * Sets up the locks of the building block fields and the "Apply mood" switch, both remembered. A locked field
     * keeps its choice when a mood or the brainstorm sets the fields; the lock shows while the pointer is over the
     * field, and always once locked (see main-window.css).
     */
    private void setupBlockLocks() {
        java.util.Map<ToggleButton, String> locks = java.util.Map.of(atmosphereLockButton, "atmosphere",
                styleLockButton, "style", colorLockButton, "palette", lightLockButton, "light", cameraLockButton,
                "camera");
        locks.forEach((lock, field) -> {
            lock.selectedProperty().addListener((obs, oldVal, newVal) -> {
                ((FontIcon) lock.getGraphic()).setIconLiteral(newVal ? "mdi2l-lock" : "mdi2l-lock-open-variant-outline");
                if (config != null)
                    config.ui().setBlockLocked(field, newVal);
            });
            lock.setSelected(config != null && config.ui().isBlockLocked(field));
        });

        moodApplyCheckBox.setSelected(config != null && config.ui().isMoodAppliedToReference());
        moodApplyCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (config != null)
                config.ui().setMoodAppliedToReference(newVal);
        });
    }

    /**
     * Sets up the mood selection: "No mood" and the moods; the actions sit as icons in the header of the group.
     * Choosing a mood sets the fields that are not locked; the mood shows as changed while a field differs from it. A
     * remembered former look style (1.x) is chosen as the standard mood of the same name.
     */
    private void setupMoods() {
        if (moodService == null || moodComboBox == null) {
            logger.warn("MoodService not available, no mood selection");
            return;
        }

        FavoriteStar.Favorites<Mood> favorites = favorites(moodComboBox, FavoriteService.Kind.MOOD, Mood::getId);
        moodComboBox.setCellFactory((listView) -> new MoodListCell(this::isMoodIncomplete, favorites));
        moodComboBox.setButtonCell(new MoodListCell(this::isMoodChanged));
        moodComboBox.valueProperty().addListener((obs, oldVal, newVal) -> onMoodChoice(newVal));
        for (javafx.scene.control.ComboBox<?> field : List.of(atmosphereComboBox, stylePresetsComboBox,
                colorStyleComboBox, lightComboBox, cameraComboBox))
            field.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (!updatingMood)
                    updateMoodState();
            });

        Mood legacyMood = moodService.findForLegacyStyle(config != null ? config.generation().getDefaultStyle() : null);
        reloadMoods(config != null ? config.ui().getSelectedMood() : "");
        if (legacyMood != null) {
            chooseMood(legacyMood);

            /*  The former look style is taken over once, also while the style field is locked  */
            art.moodframe.model.StylePresetItem style = stylePresetsComboBox.getValue();
            config.generation().setDefaultStyle(style != null ? style.getName() : "None");
        }
        else
            updateMoodState();
    }

    /** The mood shown as chosen, null for "No mood". */
    public Mood chosenMood() {
        MoodChoice choice = moodComboBox != null ? moodComboBox.getValue() : null;
        return choice != null ? choice.mood() : null;
    }

    private boolean isMoodIncomplete(Mood mood) {
        return !moodService.trashedBlocks(mood).isEmpty();
    }

    /** "Save" is not available for a standard mood, which can only be saved as a new one. */
    private boolean isMoodSaveLocked() {
        Mood mood = chosenMood();
        return mood != null && mood.isDefault();
    }

    /**
     * Lists "No mood" and the moods, the favorites first, each part alphabetically, and shows the mood with this id as
     * chosen, without applying it.
     */
    private void reloadMoods(String selectId) {
        List<MoodChoice> items = MoodChoice.listOf(FavoriteService.order(moodService.getAll(),
                (mood) -> isFavorite(FavoriteService.Kind.MOOD, mood.getId()), (mood) -> 0, Mood::getName));

        updatingMood = true;
        moodComboBox.getItems().setAll(items);
        moodComboBox.setValue(items.stream().filter((item) -> item.mood() != null && item.mood().getId().equals(selectId))
                .findFirst().orElse(items.get(0)));
        updatingMood = false;
    }

    /** Lists the moods again, keeping the choice, for example once a block or a mood left the trash. */
    private void refreshMoods() {
        if (moodService == null)
            return;
        Mood chosen = chosenMood();
        reloadMoods(chosen != null ? chosen.getId() : "");
        updateMoodState();
    }

    /** Applies a chosen mood. */
    private void onMoodChoice(MoodChoice newVal) {
        if (!updatingMood && newVal != null)
            chooseMood(newVal.mood());
    }

    /** Sets the fields that are not locked to the blocks of a mood, null for "No mood", and shows it unchanged. */
    public void chooseMood(Mood mood) {
        chooseMood(mood, mood);
    }

    /**
     * Sets the fields that are not locked to the blocks of a combination and shows a mood as chosen, null for
     * "No mood"; the mood shows as changed if the fields then differ from it.
     */
    private void chooseMood(Mood combination, Mood mood) {
        Map<Mood.Field, String> fields = moodService.apply(combination, currentMoodFields(),
                lockedMoodFields());

        art.moodframe.model.ColorStyle palette = colorStyleService().getStyleById(fields.get(Mood.Field.COLOR));
        String paletteName = palette != null ? palette.getName() : "None";

        updatingMood = true;
        selectBlock(art.moodframe.model.PromptBlock.Type.ATMOSPHERE, fields.get(Mood.Field.ATMOSPHERE));
        selectBlock(art.moodframe.model.PromptBlock.Type.STYLE, fields.get(Mood.Field.STYLE));
        selectBlock(art.moodframe.model.PromptBlock.Type.LIGHT, fields.get(Mood.Field.LIGHT));
        selectBlock(art.moodframe.model.PromptBlock.Type.CAMERA, fields.get(Mood.Field.CAMERA));

        /*  only entries, as in selectBlock  */
        colorStyleComboBox.getItems().stream().filter((item) -> colorChoice.isEntry(item)
                && item.getName().equals(paletteName)).findFirst()
                .or(() -> colorStyleComboBox.getItems().stream().filter(colorChoice::isEntry).findFirst())
                .ifPresent(colorStyleComboBox::setValue);
        moodComboBox.getItems().stream().filter((item) -> mood == null ? item.kind() == MoodChoice.Kind.NONE
                : item.mood() != null && item.mood().getId().equals(mood.getId()))
                .findFirst().ifPresent(moodComboBox::setValue);
        updatingMood = false;
        updateMoodState();
    }

    /**
     * Takes over the mood and the building blocks of a chat entry into the fields that are not locked. A block that is
     * not in use anymore can be created anew from its copy at the entry. With {@code save}, the blocks are stored as a
     * new mood first, under a name the user enters.
     *
     * @param entry the chat entry
     * @param save  whether to save the blocks as a new mood
     * @return whether the blocks were taken over, false if cancelled or failed
     */
    public boolean takeOverEntryMood(ChatEntry entry, boolean save) {
        if (moodService == null)
            return false;

        /*  An entry from before 2.0 with a former look style takes over the standard mood of its name  */
        EntryMood copy = entry.getMood();
        Mood combination = copy != null ? new Mood(copy.moodId(), copy.moodName())
                : moodService.findForLegacyStyle(entry.getStyle());
        if (combination == null)
            return false;
        if (copy != null) {
            List<Mood.Field> missing = moodService.missingBlocks(copy);
            Boolean recreate = missing.isEmpty() ? Boolean.FALSE : askToRecreateBlocks(copy, missing);
            if (recreate == null)
                return false;
            moodService.references(copy, recreate).forEach(combination::setReference);
            if (recreate)
                refreshBlockChoices();
        }

        /*  A combination of its own shows the mood it started from as chosen and changed  */
        Mood mood = moodService.find(copy != null && copy.moodId() == null ? copy.baseId() : combination.getId());
        if (save) {
            String name = art.moodframe.ui.dialog.MoodManageDialog.askName(window(),
                    moodService, I18nManager.getText("mood.name.new"),
                    combination.getName() != null ? moodService.freeName(combination.getName()) : "", null);
            if (name == null)
                return false;
            mood = new Mood(null, name);
            combination.getReferences().forEach(mood::setReference);
            if (!moodService.add(mood)) {
                status.failure(I18nManager.getText("error.mood.save"));
                return false;
            }
            reloadMoods(mood.getId());
            combination = mood;
        }

        /*  The mood field names the saved mood the fields then show exactly, else the mood as changed  */
        if (!save)
            mood = moodService.matchingMood(mood, moodService.apply(combination, currentMoodFields(),
                    lockedMoodFields()));
        chooseMood(combination, mood);
        status.status(save ? I18nManager.getText("status.mood.saved", mood.getName())
                : I18nManager.getText("status.entry.mood"));
        return true;
    }

    /**
     * "Use mood" at an image: takes over the mood saved at its entry at once, without analyzing the image; prompt,
     * frame and model stay.
     *
     * @param entry the chat entry of the image
     */
    public void useImageMood(ChatEntry entry) {
        if (takeOverEntryMood(entry, false))
            status.status(I18nManager.getText("status.image.mood"));
    }

    /**
     * Sets aspect ratio and resolution to the targets of a chat entry and shows its frame as chosen; a setting of its
     * own shows the frame it started from as changed. An entry from before the frames were copied takes the aspect
     * ratio and the resolution of its model, so the model keeps the resolution the entry was made with.
     */
    public void reuseFrame(ChatEntry entry) {
        if (frameService == null)
            return;
        EntryFrame copy = entry.getFrame();
        art.moodframe.model.GenerationModel model = art.moodframe.model.GenerationModel.fromDisplayName(entry.getModel());
        String aspectRatio = copy != null ? copy.aspectRatio() : entry.getSize();
        String resolution = copy != null ? copy.resolution() : model != null ? model.getResolutionLabel() : null;
        Frame frame = copy != null ? frameService.find(copy.frameId() != null ? copy.frameId() : copy.baseId()) : null;

        updatingFrame = true;
        if (aspectRatio != null && aspectRatioComboBox.getItems().contains(aspectRatio))
            aspectRatioComboBox.setValue(aspectRatio);
        if (resolution != null && resolutionChoiceBox.getItems().contains(resolution))
            resolutionChoiceBox.setValue(resolution);
        frameComboBox.getItems().stream().filter((item) -> frame == null ? item.kind() == FrameChoice.Kind.NONE
                : item.frame() != null && item.frame().getId().equals(frame.getId()))
                .findFirst().ifPresent(frameComboBox::setValue);
        updatingFrame = false;
        updateFrameState();
        status.status(I18nManager.getText("status.entry.frame"));
    }

    /**
     * Saves aspect ratio and resolution of a chat entry as a new frame under a name entered and chooses it, as "Save as
     * mood..." does with the mood of an entry.
     *
     * @param entry the chat entry
     */
    public void saveEntryFrame(ChatEntry entry) {
        EntryFrame copy = entry.getFrame();
        if (frameService == null || copy == null)
            return;
        String name = copy.frameName() != null ? copy.frameName() : copy.baseName();
        name = art.moodframe.ui.dialog.FrameManageDialog.askName(window(), frameService,
                I18nManager.getText("frame.name.new"), name != null ? frameService.freeName(name) : "", null);
        if (name == null)
            return;
        Frame frame = new Frame(null, name);
        frame.setAspectRatio(copy.aspectRatio());
        frame.setResolution(copy.resolution());
        if (!frameService.add(frame)) {
            status.failure(I18nManager.getText("error.frame.save"));
            return;
        }
        reloadFrames(frame.getId());
        chooseFrame(frame);
        status.status(I18nManager.getText("status.frame.saved", frame.getName()));
    }

    /** Takes over the mood of a chat entry; an entry without building blocks sets "No mood". */
    public void reuseMood(ChatEntry entry) {
        if (moodService == null)
            return;
        if (entry.getMood() != null || moodService.findForLegacyStyle(entry.getStyle()) != null)
            takeOverEntryMood(entry, false);
        else
            chooseMood(null);
    }

    /**
     * Asks whether to create the blocks of a chat entry that are not in use anymore anew from their copies.
     *
     * @return true to create them, false to leave their fields empty, null to cancel
     */
    private Boolean askToRecreateBlocks(EntryMood copy, List<Mood.Field> missing) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(window());
        alert.setTitle(I18nManager.getText("entry.mood.missing.title"));
        alert.setHeaderText(I18nManager.getText("entry.mood.missing.header",
                String.join(", ", missing.stream().map((field) -> copy.blocks().get(field).name()).toList())));
        alert.setContentText(I18nManager.getText("entry.mood.missing.content"));
        ButtonType recreate = new ButtonType(I18nManager.getText("entry.mood.missing.recreate"),
                ButtonBar.ButtonData.YES);
        ButtonType skip = new ButtonType(I18nManager.getText("entry.mood.missing.skip"), ButtonBar.ButtonData.NO);
        ButtonType cancel = DialogButtons.cancel();
        alert.getButtonTypes().setAll(recreate, skip, cancel);
        ButtonType answer = alert.showAndWait().orElse(cancel);
        return answer == cancel ? null : answer == recreate;
    }

    /**
     * Selects the item of a block in a field, "None" (the first entry) if the block is not listed. Only entries are
     * chosen, never a group header.
     */
    private void selectBlock(art.moodframe.model.PromptBlock.Type type, String blockId) {
        javafx.scene.control.ComboBox<art.moodframe.model.StylePresetItem> comboBox = blockComboBox(type);
        art.moodframe.ui.component.BlockChoice<art.moodframe.model.StylePresetItem> choice = blockChoices.get(type);
        comboBox.getItems().stream().filter((item) -> choice.isEntry(item) && item.getBlock() != null
                && item.getBlock().getId().equals(blockId)).findFirst()
                .or(() -> comboBox.getItems().stream().filter(choice::isEntry).findFirst())
                .ifPresent(comboBox::setValue);
    }

    /** The block ids of the fields; an empty field is missing. */
    private Map<Mood.Field, String> currentMoodFields() {
        Map<Mood.Field, String> fields = new java.util.EnumMap<>(Mood.Field.class);
        java.util.Map.of(Mood.Field.ATMOSPHERE, atmosphereComboBox, Mood.Field.STYLE, stylePresetsComboBox,
                Mood.Field.LIGHT, lightComboBox, Mood.Field.CAMERA, cameraComboBox).forEach((field, comboBox) -> {
            art.moodframe.model.StylePresetItem item = comboBox.getValue();
            if (item != null && item.getBlock() != null)
                fields.put(field, item.getBlock().getId());
        });
        art.moodframe.model.ColorStyleItem colorItem = colorStyleComboBox.getValue();
        art.moodframe.model.ColorStyle palette = colorItem != null && !"None".equals(colorItem.getName())
                ? colorStyleService().getStyleByName(colorItem.getName()) : null;
        if (palette != null)
            fields.put(Mood.Field.COLOR, palette.getId());
        return fields;
    }

    /** The fields whose lock is closed. */
    public Set<Mood.Field> lockedMoodFields() {
        Set<Mood.Field> locked = java.util.EnumSet.noneOf(Mood.Field.class);
        java.util.Map.of(Mood.Field.ATMOSPHERE, atmosphereLockButton, Mood.Field.STYLE, styleLockButton,
                Mood.Field.COLOR, colorLockButton, Mood.Field.LIGHT, lightLockButton, Mood.Field.CAMERA,
                cameraLockButton).forEach((field, lock) -> {
            if (lock.isSelected())
                locked.add(field);
        });
        return locked;
    }

    private art.moodframe.service.library.ColorStyleService colorStyleService() {
        return colorService;
    }

    /** Whether a field differs from the chosen mood, compared anew each time; "No mood" is never changed. */
    public boolean isMoodChanged() {
        return moodService != null && moodService.isChanged(chosenMood(), currentMoodFields());
    }

    /*  The action which saves the changed mood or frame: shown also without the pointer over the group, "Save" for
        one of the user, "Save as..." for a standard one (see main-window.css)  */
    private static final javafx.css.PseudoClass UNSAVED = javafx.css.PseudoClass.getPseudoClass("unsaved");

    /**
     * Shows the chosen mood as changed while a field differs from it, also by keeping its save action shown, remembers
     * the choice, shows the warning of a mood with a block in the trash and locks "Save" for a standard mood.
     */
    private void updateMoodState() {
        if (moodService == null)
            return;
        Mood mood = chosenMood();
        boolean changed = isMoodChanged();
        if (config != null)
            config.ui().setSelectedMood(mood != null ? mood.getId() : "", changed);

        boolean incomplete = mood != null && isMoodIncomplete(mood);
        moodIncompleteButton.setVisible(incomplete);
        moodIncompleteButton.setManaged(incomplete);
        moodIncompleteButton.setTooltip(incomplete ? new Tooltip(I18nManager.getText("params.mood.incomplete")) : null);
        saveMoodButton.setDisable(moodComboBox.isDisable() || isMoodSaveLocked());
        saveMoodButton.pseudoClassStateChanged(UNSAVED, changed && !isMoodSaveLocked());
        saveMoodAsButton.pseudoClassStateChanged(UNSAVED, changed && isMoodSaveLocked());
        ((MoodListCell) moodComboBox.getButtonCell()).refresh();
    }

    /** Saves the fields over the chosen mood of the user, or as a new mood under a name the user enters. */
    private void saveMood(boolean asNew) {
        Mood chosen = chosenMood();
        Mood mood;
        if (!asNew && chosen != null && !chosen.isDefault())
            mood = chosen;
        else {
            String name = art.moodframe.ui.dialog.MoodManageDialog.askName(window(),
                    moodService, I18nManager.getText("mood.name.new"),
                    chosen != null ? moodService.freeName(chosen.getName()) : "", null);
            if (name == null)
                return;
            mood = new Mood(null, name);
        }
        Map<Mood.Field, String> references = moodService.capture(chosen, currentMoodFields());
        for (Mood.Field field : Mood.Field.values())
            mood.setReference(field, references.get(field));

        if (!(mood.getId() == null ? moodService.add(mood) : moodService.update(mood))) {
            status.failure(I18nManager.getText("error.mood.save"));
            return;
        }
        reloadMoods(mood.getId());
        updateMoodState();
        status.status(I18nManager.getText("status.mood.saved", mood.getName()));
    }

    /** Opens the window for the moods of the user; the list follows its changes. */
    private void manageMoods() {
        new art.moodframe.ui.dialog.MoodManageDialog(window(), moodService, favoriteService, trash).showAndWait();
    }

    @FXML
    private void onSaveMoodClicked() {
        if (moodService != null)
            saveMood(false);
    }

    @FXML
    private void onSaveMoodAsClicked() {
        if (moodService != null)
            saveMood(true);
    }

    @FXML
    private void onManageMoodsClicked() {
        if (moodService != null)
            manageMoods();
    }

    @FXML
    private void onMoodFromImageClicked() {
        openMoodFromImage(null);
    }

    /**
     * Opens the dialog "Mood from image", which finds the building blocks of an image for the fields of the panel.
     *
     * @param imageFile the image to analyze at once, null to let the user give one
     */
    public void openMoodFromImage(File imageFile) {
                art.moodframe.service.library.ColorStyleService colorService = colorStyleService();
        if (blockService == null || colorService == null)
            return;

        art.moodframe.service.brainstorm.BrainstormContext context =
                new art.moodframe.service.brainstorm.BrainstormContext(
                blockService.getAll(art.moodframe.model.PromptBlock.Type.STYLE),
                blockService.getAll(art.moodframe.model.PromptBlock.Type.ATMOSPHERE),
                blockService.getAll(art.moodframe.model.PromptBlock.Type.LIGHT),
                blockService.getAll(art.moodframe.model.PromptBlock.Type.CAMERA), colorService.getAllStyles(),
                List.of(), List.of(), "", List.of(), lockedMoodFields());
        new art.moodframe.ui.dialog.MoodImageDialog(window(), moodAnalysis, context, imageFile,
                this::applyMoodFromImage).show();
    }

    /**
     * Sets the building blocks adopted in the dialog "Mood from image" into the fields that are not locked, a new block
     * into the list of the user first, and saves the fields as a new mood if asked to.
     */
    private void applyMoodFromImage(art.moodframe.ui.dialog.MoodImageDialog.Result result) {
        Set<Mood.Field> locked = lockedMoodFields();
        result.blocks().forEach((field, block) -> {
            if (!locked.contains(field))
                adoptBlock(field, block);
        });
        if (result.saveMood() && moodService != null)
            saveMood(true);
    }

    /** Shows the first block of the chosen mood that lies in the trash. */
    @FXML
    private void onMoodIncompleteClicked() {
        Mood mood = chosenMood();
        List<String> trashed = mood != null ? moodService.trashedBlocks(mood) : List.of();
        if (!trashed.isEmpty() && trash != null)
            trash.open(window(), art.moodframe.service.trash.TrashGroup.Kind.BLOCKS, trashed.get(0));
    }

    /** Asks for every mood that lost its last block whether to move it to the trash. */
    public void askToDeleteEmptiedMoods() {
        if (moodService == null)
            return;
        List<Mood> emptied = moodService.takeEmptied();
        for (Mood mood : emptied) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.initOwner(window());
            alert.setTitle(I18nManager.getText("mood.empty.title"));
            alert.setHeaderText(I18nManager.getText("mood.empty.header", mood.getName()));
            alert.setContentText(I18nManager.getText("mood.empty.content"));
            ButtonType delete = new ButtonType(I18nManager.getText("mood.empty.delete"), ButtonBar.ButtonData.YES);
            ButtonType keep = new ButtonType(I18nManager.getText("mood.empty.keep"), ButtonBar.ButtonData.NO);
            alert.getButtonTypes().setAll(delete, keep);
            if (alert.showAndWait().orElse(keep) == delete)
                moodService.moveToTrash(mood.getId());
        }
    }

    /**
     * Sets up the frame selection: "No frame" and the frames; the actions sit as icons in the header of the group.
     * Choosing a frame sets its target aspect ratio and resolution, of which the model takes the closest ones it can
     * do; either changed afterwards shows the frame as changed until it is saved or chosen again.
     */
    private void setupFrames() {
        if (frameService == null || frameComboBox == null) {
            logger.warn("FrameService not available, no frame selection");
            return;
        }

        FavoriteStar.Favorites<Frame> favorites = favorites(frameComboBox, FavoriteService.Kind.FRAME, Frame::getId);
        frameComboBox.setCellFactory((listView) -> new FrameListCell(favorites));
        frameComboBox.setButtonCell(new FrameListCell(() -> frameChanged));
        frameComboBox.valueProperty().addListener((obs, oldVal, newVal) -> onFrameChoice(newVal));
        aspectRatioComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingFrame)
                updateFrameState();
        });
        resolutionChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingFrame)
                updateFrameState();
        });

        reloadFrames(config != null ? config.ui().getSelectedFrame() : "");
        updateFrameState();
    }

    /** The frame shown as chosen, null for "No frame". */
    public Frame chosenFrame() {
        FrameChoice choice = frameComboBox != null ? frameComboBox.getValue() : null;
        return choice != null ? choice.frame() : null;
    }

    /** "Save" is not available for a standard frame, which can only be saved as a new one. */
    private boolean isFrameSaveLocked() {
        Frame frame = chosenFrame();
        return frame != null && frame.isDefault();
    }

    /**
     * Lists "No frame" and the frames, the favorites first, each part alphabetically, and shows the frame with this id
     * as chosen, without applying it.
     */
    private void reloadFrames(String selectId) {
        List<FrameChoice> items = FrameChoice.listOf(FavoriteService.order(frameService.getAll(),
                (frame) -> isFavorite(FavoriteService.Kind.FRAME, frame.getId()), (frame) -> 0, Frame::getName));

        updatingFrame = true;
        frameComboBox.getItems().setAll(items);
        frameComboBox.setValue(items.stream().filter((item) -> item.frame() != null
                && item.frame().getId().equals(selectId)).findFirst().orElse(items.get(0)));
        updatingFrame = false;
    }

    /** Lists the frames again, keeping the choice, for example once a frame left the trash or was renamed. */
    private void refreshFrames() {
        if (frameService == null)
            return;
        Frame chosen = chosenFrame();
        reloadFrames(chosen != null ? chosen.getId() : "");
        updateFrameState();
    }

    /** Applies a chosen frame. */
    private void onFrameChoice(FrameChoice newVal) {
        if (!updatingFrame && newVal != null)
            chooseFrame(newVal.frame());
    }

    /**
     * Sets aspect ratio and resolution to the targets of a frame and shows it unchanged; a frame without resolution
     * leaves the resolution, "No frame" (null) both as they are.
     */
    public void chooseFrame(Frame frame) {
        updatingFrame = true;
        if (frame != null) {
            aspectRatioComboBox.setValue(frame.getAspectRatio());
            if (frame.getResolution() != null)
                resolutionChoiceBox.setValue(frame.getResolution());
        }
        frameComboBox.getItems().stream().filter((item) -> frame == null ? item.kind() == FrameChoice.Kind.NONE
                : item.frame() != null && item.frame().getId().equals(frame.getId()))
                .findFirst().ifPresent(frameComboBox::setValue);
        updatingFrame = false;
        updateFrameState();
    }

    /**
     * Marks the chosen frame as changed while aspect ratio or resolution differ from it, also by keeping the action
     * shown which saves it, remembers the choice and locks "Save" for a standard frame.
     */
    private void updateFrameState() {
        if (frameService == null)
            return;
        Frame frame = chosenFrame();
        frameChanged = frame != null && (!frame.getAspectRatio().equals(aspectRatioComboBox.getValue())
                || frame.getResolution() != null && !frame.getResolution().equals(resolutionChoiceBox.getValue()));
        String id = frame != null ? frame.getId() : "";
        if (config != null && !id.equals(config.ui().getSelectedFrame()))
            config.ui().setSelectedFrame(id);
        saveFrameButton.setDisable(frameComboBox.isDisable() || isFrameSaveLocked());
        saveFrameButton.pseudoClassStateChanged(UNSAVED, frameChanged && !isFrameSaveLocked());
        saveFrameAsButton.pseudoClassStateChanged(UNSAVED, frameChanged && isFrameSaveLocked());
        ((FrameListCell) frameComboBox.getButtonCell()).refresh();
    }

    /** Saves aspect ratio and resolution over the chosen frame of the user, or as a new frame under a name entered. */
    private void saveFrame(boolean asNew) {
        Frame chosen = chosenFrame();
        Frame frame;
        if (!asNew && chosen != null && !chosen.isDefault())
            frame = chosen;
        else {
            String name = art.moodframe.ui.dialog.FrameManageDialog.askName(window(),
                    frameService, I18nManager.getText("frame.name.new"),
                    chosen != null ? frameService.freeName(chosen.getName()) : "", null);
            if (name == null)
                return;
            frame = new Frame(null, name);
        }
        frame.setAspectRatio(aspectRatioComboBox.getValue());
        frame.setResolution(resolutionChoiceBox.getValue());

        if (!(frame.getId() == null ? frameService.add(frame) : frameService.update(frame))) {
            status.failure(I18nManager.getText("error.frame.save"));
            return;
        }
        reloadFrames(frame.getId());
        updateFrameState();
        status.status(I18nManager.getText("status.frame.saved", frame.getName()));
    }

    /** Opens the window for the frames of the user; the list follows its changes. */
    private void manageFrames() {
        new art.moodframe.ui.dialog.FrameManageDialog(window(), frameService, favoriteService, trash).showAndWait();
    }

    @FXML
    private void onSaveFrameClicked() {
        if (frameService != null)
            saveFrame(false);
    }

    @FXML
    private void onSaveFrameAsClicked() {
        if (frameService != null)
            saveFrame(true);
    }

    @FXML
    private void onManageFramesClicked() {
        if (frameService != null)
            manageFrames();
    }

    /**
     * Sets a building block adopted in the brainstorm popup into its field. A new block is stored in the list of the
     * user first; a new style counts as photographic, like every style of the user, a new camera brings its settings.
     */
    private void adoptBlock(Mood.Field field, art.moodframe.model.BrainstormDraft.Block block) {
        if (!block.isNew()) {
            selectField(field, block.id());
            return;
        }

        boolean saved;
        if (field == Mood.Field.COLOR) {
            art.moodframe.service.library.ColorStyleService service = colorStyleService();
            saved = service != null && service.addCustomStyle(
                    new art.moodframe.model.ColorStyle(block.name(), block.colors(), false));
            if (saved)
                fillColorChoice(block.name());
        }
        else {
            art.moodframe.service.library.PromptBlockService service = blockService;
            art.moodframe.model.PromptBlock.Type type = art.moodframe.model.PromptBlock.Type.valueOf(field.name());
            saved = service != null && service.addCustom(type == art.moodframe.model.PromptBlock.Type.CAMERA
                    ? art.moodframe.service.library.PromptBlockService.camera(null, block.name(), block.settings())
                    : new art.moodframe.model.PromptBlock(null, type, block.name(), block.text(), null,
                            type == art.moodframe.model.PromptBlock.Type.STYLE));
            if (saved)
                fillBlockChoice(type, block.name());
        }
        if (!saved)
            status.status(I18nManager.getText("block.error.save", block.name()));
    }

    /** Selects the entry with this id in the field of a building block, "None" if the field does not list it. */
    private void selectField(Mood.Field field, String id) {
        if (field != Mood.Field.COLOR) {
            selectBlock(art.moodframe.model.PromptBlock.Type.valueOf(field.name()), id);
            return;
        }
        art.moodframe.model.ColorStyle palette = colorStyleService().getStyleById(id);
        String paletteName = palette != null ? palette.getName() : "None";
        colorStyleComboBox.getItems().stream().filter((item) -> colorChoice.isEntry(item)
                && item.getName().equals(paletteName)).findFirst()
                .or(() -> colorStyleComboBox.getItems().stream().filter(colorChoice::isEntry).findFirst())
                .ifPresent(colorStyleComboBox::setValue);
    }

    /** The building blocks selected in the main window; none with a reference image unless "Apply mood" is on. */
    private art.moodframe.service.prompt.PromptComposer.Selection selectedBlocks() {
        if (reference && !moodApplyCheckBox.isSelected())
            return new art.moodframe.service.prompt.PromptComposer.Selection(null, null, null, null, null);

        art.moodframe.model.StylePresetItem styleItem = stylePresetsComboBox != null ? stylePresetsComboBox.getValue() : null;
        art.moodframe.model.StylePresetItem atmosphereItem = atmosphereComboBox != null ? atmosphereComboBox.getValue() : null;
        art.moodframe.model.StylePresetItem lightItem = lightComboBox != null ? lightComboBox.getValue() : null;
        art.moodframe.model.StylePresetItem cameraItem = cameraComboBox != null ? cameraComboBox.getValue() : null;
        art.moodframe.model.ColorStyleItem colorItem = colorStyleComboBox != null ? colorStyleComboBox.getValue() : null;
        art.moodframe.model.ColorStyle palette = colorItem != null && !"None".equals(colorItem.getName())
                ? art.moodframe.model.ColorStyle.fromColorStyleItem(colorItem) : null;
        return new art.moodframe.service.prompt.PromptComposer.Selection(
                styleItem != null ? styleItem.getBlock() : null,
                atmosphereItem != null ? atmosphereItem.getBlock() : null, lightItem != null ? lightItem.getBlock() : null,
                cameraItem != null ? cameraItem.getBlock() : null, palette);
    }

    /**
     * Shows the hints below the camera: for a style that is not photographic, layout included, only perspective and
     * shot size apply (see {@link art.moodframe.service.prompt.PromptComposer}); with a reference image, a stage that
     * changes the geometry may show only weakly, as an edit hardly changes it.
     */
    private void updateCameraHint() {
        art.moodframe.model.StylePresetItem styleItem = stylePresetsComboBox.getValue();
        boolean framingOnly = styleItem != null && styleItem.getBlock() != null
                && !styleItem.getBlock().isPhotographic();
        art.moodframe.model.PromptBlock camera = reference ? selectedBlocks().camera() : null;
        List<Enum<?>> weak = camera != null ? art.moodframe.service.prompt.CameraPromptBuilder.weakInEdits(
                art.moodframe.model.CameraSettings.fromJson(camera.getParams()), !framingOnly) : List.of();
        List<String> hints = new java.util.ArrayList<>();
        if (framingOnly)
            hints.add(I18nManager.getText("params.camera.framing.hint"));
        if (!weak.isEmpty())
            hints.add(I18nManager.getText("params.camera.edit.hint", String.join(", ", weak.stream()
                    .map(art.moodframe.ui.component.CameraExample::name).toList())));
        cameraHint.setText(String.join("\n", hints));
        cameraHint.setVisible(!hints.isEmpty());
        cameraHint.setManaged(!hints.isEmpty());
    }

    /*  the hint below aspect ratio or resolution while the chosen model cannot do the target  */
    private static final javafx.css.PseudoClass TARGET_CHANGED = javafx.css.PseudoClass.getPseudoClass("changed");

    /**
     * Shows below the aspect ratio what the chosen model makes of the target, for example "FLUX.2 [pro] → 5:4", but
     * only, highlighted, while it differs from the target.
     */
    private void updateAspectRatioHint() {
        String item = modelItem();
        String ratio = modelAspectRatio();
        if (aspectRatioHintLabel == null || item == null || ratio == null)
            return;
        aspectRatioHintLabel.setText(art.moodframe.model.ModelFamilies.familyOf(item) + " → " + ratio);
        showTargetHint(aspectRatioHintLabel, !ratio.equals(aspectRatioComboBox.getValue()));
    }

    /*  shows a hint below aspect ratio or resolution, highlighted, only while the model does not do the target  */
    private static void showTargetHint(Label hint, boolean changed) {
        hint.pseudoClassStateChanged(TARGET_CHANGED, changed);
        hint.setVisible(changed);
        hint.setManaged(changed);
    }

    /**
     * The aspect ratio the chosen model makes of the target: the target itself if the model can do it, else its
     * closest one.
     *
     * @return the aspect ratio, null without target
     */
    public String modelAspectRatio() {
        String target = aspectRatioComboBox.getValue();
        return target != null ? art.moodframe.model.AspectRatios.nearestForItem(target, modelItem()) : null;
    }

    /**
     * Shows below the resolution what the chosen model makes of the target, for example "Nano Banana 2 Lite → 1K", but
     * only, highlighted, while it differs from the target; a model without resolution choice keeps its fixed size.
     */
    private void updateResolutionHint() {
        String item = modelItem();
        if (resolutionHintLabel == null || item == null)
            return;
        String resolution = modelResolution();
        resolutionHintLabel.setText(art.moodframe.model.ModelFamilies.familyOf(item) + " → "
                + (resolution != null ? resolution : I18nManager.getText("params.resolution.fixed")));
        showTargetHint(resolutionHintLabel, resolution == null || !resolution.equals(resolutionChoiceBox.getValue()));
    }

    /**
     * The resolution the chosen model generates: of free sizes the one closest to the target, else the one of its
     * variant, which is always the one of its family closest to the target (see {@code ModelFamilySelector}).
     *
     * @return the resolution, null for a model with a fixed size
     */
    public String modelResolution() {
        String item = modelItem();
        java.util.List<String> free = art.moodframe.model.ModelFamilies.freeResolutions(item);
        return free != null ? art.moodframe.model.ModelFamilies.nearest(resolutionChoiceBox.getValue(), free)
                : item != null ? art.moodframe.model.ModelFamilies.resolutionOf(item) : null;
    }

    /**
     * Auto-detects the aspect ratio from an uploaded image and sets the closest
     * available option from the currently available ratios in the combo box.
     */
    public void matchAspectRatio(File imageFile) {
        if (imageFile == null || aspectRatioComboBox == null) {
            return;
        }

        try {
            // Read the dimensions from the header of the file, without decoding the image on the FX thread
            art.moodframe.util.ImageSize size = art.moodframe.util.ImageSize.read(imageFile.toPath());
            if (size == null) {
                logger.warn("Could not read image dimensions from: {}", imageFile.getName());
                return;
            }

            int width = size.width();
            int height = size.height();
            double imageRatio = (double) width / height;

            // Use the aspect ratios of the combo box, the targets of all models
            // (the chosen model takes the closest one it can do)
            var availableItems = aspectRatioComboBox.getItems();
            if (availableItems.isEmpty()) {
                return;
            }

            // Find closest matching aspect ratio
            String closestRatio = "1:1"; // Default fallback
            double smallestDifference = Double.MAX_VALUE;

            for (String ratioStr : availableItems) {
                double ratioValue = parseRatioToDouble(ratioStr);
                double difference = Math.abs(imageRatio - ratioValue);
                if (difference < smallestDifference) {
                    smallestDifference = difference;
                    closestRatio = ratioStr;
                }
            }

            // Set the closest aspect ratio
            String currentRatio = aspectRatioComboBox.getValue();
            if (!closestRatio.equals(currentRatio)) {
                aspectRatioComboBox.setValue(closestRatio);
                status.status(java.text.MessageFormat.format(
                        I18nManager.getText("status.aspect.ratio.detected"), closestRatio));
            }

        } catch (Exception e) {
            logger.error("Failed to detect aspect ratio from image: {}", imageFile.getName(), e);
        }
    }

    /**
     * Parses an aspect ratio string (e.g., "16:9") to its numeric value.
     *
     * @param ratioStr the ratio string in format "W:H"
     * @return the numeric ratio value (width / height)
     */
    private double parseRatioToDouble(String ratioStr) {
        if (ratioStr == null || !ratioStr.contains(":")) {
            return 1.0;
        }
        try {
            String[] parts = ratioStr.split(":");
            double w = Double.parseDouble(parts[0]);
            double h = Double.parseDouble(parts[1]);
            return w / h;
        } catch (Exception e) {
            return 1.0;
        }
    }

    /** One line of the current state for the brainstorm: a building block field, its entry and its lock. */
    private static String fieldState(String label, Object entry, boolean locked) {
        return "\n" + label + ": " + (entry != null ? entry.toString() : "None") + (locked ? " (locked)" : "");
    }
}
