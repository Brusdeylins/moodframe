package art.moodframe.ui.panel;

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
import art.moodframe.ui.cell.MoodListCell;
import art.moodframe.ui.component.ColorStyleItem;
import art.moodframe.ui.component.DialogButtons;
import art.moodframe.ui.component.FavoriteStar;
import art.moodframe.ui.component.FrameChoice;
import art.moodframe.ui.component.MoodChoice;
import art.moodframe.ui.component.StylePresetItem;
import art.moodframe.ui.support.StatusSink;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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
    private ComboBox<StylePresetItem> atmosphereComboBox;
    @FXML
    private Label atmosphereLabel;
    @FXML
    private ToggleButton atmosphereLockButton;
    @FXML
    private ComboBox<StylePresetItem> stylePresetsComboBox;
    @FXML
    private Label styleLabel;
    @FXML
    private ToggleButton styleLockButton;
    @FXML
    private ComboBox<ColorStyleItem> colorStyleComboBox;
    @FXML
    private Label colorStyleLabel;
    @FXML
    private ToggleButton colorLockButton;
    @FXML
    private ComboBox<StylePresetItem> lightComboBox;
    @FXML
    private Label lightLabel;
    @FXML
    private ToggleButton lightLockButton;
    @FXML
    private Label lightLayoutHint;
    @FXML
    private ComboBox<StylePresetItem> cameraComboBox;
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

    /*  The lists of the building blocks and of the palettes  */
    private BlockChoices blocks;

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

    /*  The ids of the moods with a block in the trash, found on the worker once per list load, and the favorites of
        the rows, which are drawn anew once they are found  */
    private Set<String> incompleteMoods = Set.of();
    private FavoriteStar.Favorites<Mood> moodFavorites;

    /*  The frames: whether aspect ratio or resolution differ from the chosen frame, and a guard while the frame
        selection sets them  */
    private FrameService frameService;
    private art.moodframe.service.brainstorm.MoodAnalysisService moodAnalysis;
    private art.moodframe.ui.dialog.TrashWindow.Opener trash;

    /*  the group Frame  */
    private FrameGroupPart frames;

    /*  runs the writes of the library on the worker of the window  */
    private PanelWorker writes;

    /*  the new blocks adopted last (brainstorm, "Mood from image"), stored before a mood is saved of the fields  */
    private CompletableFuture<Void> adopting = CompletableFuture.completedFuture(null);

    @FXML
    private void initialize() {
        blocks = new BlockChoices(new BlockChoices.Nodes(atmosphereComboBox, atmosphereLockButton,
                stylePresetsComboBox, styleLockButton, colorStyleComboBox, colorLockButton, lightComboBox,
                lightLockButton, cameraComboBox, cameraLockButton));
        frames = new FrameGroupPart(new FrameGroupPart.Nodes(frameComboBox, aspectRatioComboBox, aspectRatioHintLabel,
                resolutionChoiceBox, resolutionHintLabel, saveFrameButton, saveFrameAsButton), this::modelItem);
    }

    /**
     * Follows the chosen model, whose name and closest aspect ratio and resolution the hints show; before
     * {@link #init}, which takes the resolution of the model as target if none is remembered.
     *
     * @param model the item of the chosen model, as the model dropdown lists it
     */
    public void followModel(ObservableValue<String> model) {
        this.model = model;
        model.addListener((obs, oldVal, newVal) -> frames.updateHints());
    }

    /*  the item of the chosen model, null without  */
    private String modelItem() {
        return model != null ? model.getValue() : null;
    }

    /**
     * Fills the lists from the services, restores the remembered choices and from then on remembers every choice.
     *
     * @param services the services of the panel
     * @param worker   runs the writes of the library one after the other, off the JavaFX thread (the worker of the
     *                 window)
     * @param status   shows what the panel did
     * @param listener learns of the changes of the fields
     */
    public void init(Services services, Executor worker, StatusSink status, Listener listener) {
        this.writes = new PanelWorker(worker, status);
        this.config = services.config();
        this.blockService = services.blocks();
        this.colorService = services.colors();
        this.favoriteService = services.favorites();
        this.moodService = services.moods();
        this.frameService = services.frames();
        this.moodAnalysis = services.analysis();
        this.trash = services.trash();
        this.status = status;

        blocks.setUp(config, blockService, colorService, favoriteService, moodService, trash, writes, status,
                this::window, () -> this.listener.changed());
        moodApplyCheckBox.setSelected(config != null && config.ui().isMoodAppliedToReference());
        moodApplyCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (config != null)
                config.ui().setMoodAppliedToReference(newVal);
        });
        loadSelections();
        setupPersistenceListeners();
        setupMoods();
        frames.setUp(config, frameService, favoriteService, trash, writes, status, this::window,
                blocks.favorites(frameComboBox, FavoriteService.Kind.FRAME, Frame::getId),
                (String id) -> blocks.isFavorite(FavoriteService.Kind.FRAME, id));
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

    /** What the panel shows as chosen. */
    public Selection selection() {
        return new Selection(chosenMood(), isMoodChanged(), currentMoodFields(), lockedMoodFields(), chosenFrame(),
                frames.isFrameChanged(), aspectRatioComboBox.getValue(), resolutionChoiceBox.getValue());
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
            case FRAME -> frames.refreshFrames();
            case ATMOSPHERE, STYLE, LIGHT, CAMERA, COLOR -> {
                blocks.refreshBlockChoices();
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
        frames.updateSaveLock();

        boolean moodApplies = enableInput && (!reference || moodApplyCheckBox.isSelected());
        StylePresetItem styleItem = stylePresetsComboBox.getValue();
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
        frames.refreshFrames();

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
        blocks.updateTexts();
        aspectRatioLabel.setText(I18nManager.getText("params.aspect.ratio"));
        resolutionLabel.setText(I18nManager.getText("params.resolution"));
        frames.updateResolutionHint();
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
     * block; a locked field keeps its choice. A new block is stored on the worker and set into its field afterwards.
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
        List<CompletableFuture<Boolean>> stored = new ArrayList<>();
        for (Mood.Field field : art.moodframe.model.BrainstormDraft.FIELDS) {
            if (locked.contains(field))
                continue;
            art.moodframe.model.BrainstormDraft.Block block = blocks.get(field);
            if (block != null)
                stored.add(adoptBlock(field, block));
            else if (mood != null)
                selectField(field, before.get(field));
        }
        adopting = CompletableFuture.allOf(stored.toArray(CompletableFuture[]::new));
    }

    /**
     * Saves the fields as a new mood under a name the user enters, as "Save as..." does; once the new blocks adopted
     * last are stored and in their fields.
     */
    public void saveMoodAsNew() {
        if (moodService != null)
            adopting.thenRun(() -> saveMood(true));
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

            for (Map.Entry<ComboBox<StylePresetItem>, String> saved : Map.of(
                    lightComboBox, String.valueOf(config.generation().getDefaultLight()),
                    cameraComboBox, String.valueOf(config.generation().getDefaultCamera()),
                    atmosphereComboBox, String.valueOf(config.generation().getDefaultAtmosphere())).entrySet())
                saved.getKey().getItems().stream().filter((item) -> item.getName().equals(saved.getValue()))
                        .findFirst().ifPresent(saved.getKey()::setValue);
        } catch (Exception e) {
            logger.error("Failed to load the choices of mood and frame from the configuration", e);
            aspectRatioComboBox.setValue("16:9");
            blocks.fillBlockChoice(art.moodframe.model.PromptBlock.Type.STYLE, null);
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
            if (newVal != null && blocks.choice(art.moodframe.model.PromptBlock.Type.STYLE).isEntry(newVal))
                config.generation().setDefaultStyle(newVal.getName());
        });
        colorStyleComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && blocks.colorChoice().isEntry(newVal))
                config.generation().setDefaultColorStyle(newVal.getName());
        });
        lightComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && blocks.choice(art.moodframe.model.PromptBlock.Type.LIGHT).isEntry(newVal))
                config.generation().setDefaultLight(newVal.getName());
        });
        cameraComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && blocks.choice(art.moodframe.model.PromptBlock.Type.CAMERA).isEntry(newVal))
                config.generation().setDefaultCamera(newVal.getName());
        });
        atmosphereComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && blocks.choice(art.moodframe.model.PromptBlock.Type.ATMOSPHERE).isEntry(newVal))
                config.generation().setDefaultAtmosphere(newVal.getName());
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
            reloads.put(frameComboBox, frames::reloadKeepingChoice);
        /*  the building block lists keep the mood as it is while they are filled anew  */
        blocks.reloads().forEach((field, reload) -> reloads.put(field, () -> {
            updatingMood = true;
            reload.run();
            updatingMood = false;
        }));
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

        moodFavorites = blocks.favorites(moodComboBox, FavoriteService.Kind.MOOD, Mood::getId);
        moodComboBox.setCellFactory((listView) -> new MoodListCell(this::isMoodIncomplete, moodFavorites));
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
            StylePresetItem style = stylePresetsComboBox.getValue();
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

    /*  whether a block of the mood lies in the trash, as found for the list shown  */
    private boolean isMoodIncomplete(Mood mood) {
        return incompleteMoods.contains(mood.getId());
    }

    /** "Save" is not available for a standard mood, which can only be saved as a new one. */
    private boolean isMoodSaveLocked() {
        Mood mood = chosenMood();
        return mood != null && mood.isDefault();
    }

    /**
     * Lists "No mood" and the moods, the favorites first, each part alphabetically, and shows the mood with this id as
     * chosen, without applying it. Which of them have a block in the trash, the worker finds afterwards.
     */
    private void reloadMoods(String selectId) {
        List<Mood> moods = moodService.getAll();
        List<MoodChoice> items = MoodChoice.listOf(FavoriteService.order(moods,
                (mood) -> blocks.isFavorite(FavoriteService.Kind.MOOD, mood.getId()), (mood) -> 0, Mood::getName));

        updatingMood = true;
        moodComboBox.getItems().setAll(items);
        moodComboBox.setValue(items.stream().filter((item) -> item.mood() != null && item.mood().getId().equals(selectId))
                .findFirst().orElse(items.get(0)));
        updatingMood = false;
        findIncompleteMoods(moods);
    }

    /*  Finds the moods with a block in the trash on the worker, as each asks the trash for up to five blocks; then
        the warning of the chosen mood and the rows of the list show them  */
    private void findIncompleteMoods(List<Mood> moods) {
        writes.call(() -> moods.stream().filter((mood) -> !moodService.trashedBlocks(mood).isEmpty())
                .map(Mood::getId).collect(java.util.stream.Collectors.toUnmodifiableSet()))
                .whenComplete((ids, failure) -> {
                    if (failure != null) {
                        logger.error("Failed to find the moods with a block in the trash", failure);
                        return;
                    }
                    incompleteMoods = ids;
                    showIncomplete();
                    moodComboBox.setCellFactory((listView) -> new MoodListCell(this::isMoodIncomplete,
                            moodFavorites));
                });
    }

    /*  shows the warning next to the field while the chosen mood has a block in the trash  */
    private void showIncomplete() {
        Mood mood = chosenMood();
        boolean incomplete = mood != null && isMoodIncomplete(mood);
        moodIncompleteButton.setVisible(incomplete);
        moodIncompleteButton.setManaged(incomplete);
        moodIncompleteButton.setTooltip(incomplete ? new Tooltip(I18nManager.getText("params.mood.incomplete")) : null);
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
        colorStyleComboBox.getItems().stream().filter((item) -> blocks.colorChoice().isEntry(item)
                && item.getName().equals(paletteName)).findFirst()
                .or(() -> colorStyleComboBox.getItems().stream().filter(blocks.colorChoice()::isEntry).findFirst())
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
     * new mood first, under a name the user enters. What is created or stored, the worker writes; the fields follow
     * afterwards.
     *
     * @param entry the chat entry
     * @param save  whether to save the blocks as a new mood
     * @return whether the blocks are taken over, false if cancelled or there is no mood; a failed write is told by the
     *         status
     */
    public boolean takeOverEntryMood(ChatEntry entry, boolean save) {
        return takeOverEntryMood(entry, save, I18nManager.getText("status.entry.mood"));
    }

    /*  takes over the mood of an entry and shows the message once its fields show it (see above)  */
    private boolean takeOverEntryMood(ChatEntry entry, boolean save, String done) {
        if (moodService == null)
            return false;

        /*  An entry from before 2.0 with a former look style takes over the standard mood of its name  */
        EntryMood copy = entry.getMood();
        Mood combination = copy != null ? new Mood(copy.moodId(), copy.moodName())
                : moodService.findForLegacyStyle(entry.getStyle());
        if (combination == null)
            return false;
        Boolean recreate = Boolean.FALSE;
        if (copy != null) {
            List<Mood.Field> missing = moodService.missingBlocks(copy);
            recreate = missing.isEmpty() ? Boolean.FALSE : askToRecreateBlocks(copy, missing);
            if (recreate == null)
                return false;
        }

        /*  A combination of its own shows the mood it started from as chosen and changed  */
        Mood base = moodService.find(copy != null && copy.moodId() == null ? copy.baseId() : combination.getId());
        Mood saved = null;
        if (save) {
            String name = art.moodframe.ui.dialog.MoodManageDialog.askName(window(),
                    moodService, I18nManager.getText("mood.name.new"),
                    combination.getName() != null ? moodService.freeName(combination.getName()) : "", null);
            if (name == null)
                return false;
            saved = new Mood(null, name);
        }

        /*  The references of the copies, the blocks created anew among them, and the new mood  */
        boolean recreated = recreate;
        Mood mood = saved;
        java.util.function.Supplier<Boolean> write = () -> {
            if (copy != null)
                moodService.references(copy, recreated).forEach(combination::setReference);
            if (mood == null)
                return true;
            combination.getReferences().forEach(mood::setReference);
            return moodService.add(mood);
        };
        java.util.function.Consumer<Boolean> show = (stored) -> {
            if (recreated)
                blocks.refreshBlockChoices();
            if (!stored) {
                status.failure(I18nManager.getText("error.mood.save"));
                return;
            }
            if (mood != null) {
                reloadMoods(mood.getId());
                chooseMood(mood, mood);
                status.status(I18nManager.getText("status.mood.saved", mood.getName()));
                return;
            }

            /*  The mood field names the mood the fields then show exactly, else the mood as changed  */
            chooseMood(combination, moodService.matchingMood(base, moodService.apply(combination,
                    currentMoodFields(), lockedMoodFields())));
            status.status(done);
        };
        if (recreated || save)
            writes.run(write, false, I18nManager.getText("error.mood.save"), show);
        else
            show.accept(write.get());
        return true;
    }

    /**
     * "Use mood" at an image: takes over the mood saved at its entry at once, without analyzing the image; prompt,
     * frame and model stay.
     *
     * @param entry the chat entry of the image
     */
    public void useImageMood(ChatEntry entry) {
        takeOverEntryMood(entry, false, I18nManager.getText("status.image.mood"));
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

        frames.takeTargets(frame, aspectRatio, resolution);
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
        frames.saveAsNew(copy.frameName() != null ? copy.frameName() : copy.baseName(), copy.aspectRatio(),
                copy.resolution());
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
        javafx.scene.control.ComboBox<StylePresetItem> comboBox = blocks.blockComboBox(type);
        art.moodframe.ui.component.BlockChoice<StylePresetItem> choice = blocks.choice(type);
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
            StylePresetItem item = comboBox.getValue();
            if (item != null && item.getBlock() != null)
                fields.put(field, item.getBlock().getId());
        });
        ColorStyleItem colorItem = colorStyleComboBox.getValue();
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

        showIncomplete();
        saveMoodButton.setDisable(moodComboBox.isDisable() || isMoodSaveLocked());
        saveMoodButton.pseudoClassStateChanged(UNSAVED, changed && !isMoodSaveLocked());
        saveMoodAsButton.pseudoClassStateChanged(UNSAVED, changed && isMoodSaveLocked());
        ((MoodListCell) moodComboBox.getButtonCell()).refresh();
    }

    /**
     * Saves the fields over the chosen mood of the user, or as a new mood under a name the user enters; the worker
     * stores it, then the list shows it as chosen.
     */
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

        String failure = I18nManager.getText("error.mood.save");
        boolean add = mood.getId() == null;
        writes.run(() -> add ? moodService.add(mood) : moodService.update(mood), false, failure, (saved) -> {
            if (!saved) {
                status.failure(failure);
                return;
            }
            reloadMoods(mood.getId());
            updateMoodState();
            status.status(I18nManager.getText("status.mood.saved", mood.getName()));
        });
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
        List<CompletableFuture<Boolean>> stored = new ArrayList<>();
        result.blocks().forEach((field, block) -> {
            if (!locked.contains(field))
                stored.add(adoptBlock(field, block));
        });
        adopting = CompletableFuture.allOf(stored.toArray(CompletableFuture[]::new));
        if (result.saveMood())
            saveMoodAsNew();
    }

    /** Shows the first block of the chosen mood that lies in the trash. */
    @FXML
    private void onMoodIncompleteClicked() {
        Mood mood = chosenMood();
        List<String> trashed = mood != null ? moodService.trashedBlocks(mood) : List.of();
        if (!trashed.isEmpty() && trash != null)
            trash.open(window(), art.moodframe.service.trash.TrashGroup.Kind.BLOCKS, trashed.get(0));
    }

    /** Asks for every mood that lost its last block whether to move it to the trash, which the worker does. */
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
            if (alert.showAndWait().orElse(keep) == delete) {
                String failure = I18nManager.getText("mood.manage.delete.failed", mood.getName());
                writes.run(() -> moodService.moveToTrash(mood.getId()), false, failure, (moved) -> {
                    if (!moved)
                        status.failure(failure);
                });
            }
        }
    }

    /** The frame shown as chosen, null for "No frame". */
    public Frame chosenFrame() {
        return frames.chosenFrame();
    }

    /**
     * Sets aspect ratio and resolution to the targets of a frame and shows it unchanged; a frame without resolution
     * leaves the resolution, "No frame" (null) both as they are.
     */
    public void chooseFrame(Frame frame) {
        frames.chooseFrame(frame);
    }

    @FXML
    private void onSaveFrameClicked() {
        frames.saveFrame(false);
    }

    @FXML
    private void onSaveFrameAsClicked() {
        frames.saveFrame(true);
    }

    @FXML
    private void onManageFramesClicked() {
        frames.manageFrames();
    }

    /**
     * Sets a building block adopted in the brainstorm popup into its field. A new block is stored in the list of the
     * user first, on the worker; a new style counts as photographic, like every style of the user, a new camera brings
     * its settings.
     *
     * @return completes on the JavaFX thread once the block is in its field, with whether it is
     */
    private CompletableFuture<Boolean> adoptBlock(Mood.Field field, art.moodframe.model.BrainstormDraft.Block block) {
        if (!block.isNew()) {
            selectField(field, block.id());
            return CompletableFuture.completedFuture(true);
        }

        String failure = I18nManager.getText("block.error.save", block.name());
        if (field == Mood.Field.COLOR) {
            art.moodframe.service.library.ColorStyleService service = colorStyleService();
            if (service == null) {
                status.status(failure);
                return CompletableFuture.completedFuture(false);
            }
            art.moodframe.model.ColorStyle palette = new art.moodframe.model.ColorStyle(block.name(), block.colors(),
                    false);
            return writes.run(() -> service.addCustomStyle(palette), false, failure, (saved) -> {
                if (saved)
                    blocks.fillColorChoice(block.name());
                else
                    status.status(failure);
            });
        }

        art.moodframe.service.library.PromptBlockService service = blockService;
        art.moodframe.model.PromptBlock.Type type = art.moodframe.model.PromptBlock.Type.valueOf(field.name());
        if (service == null) {
            status.status(failure);
            return CompletableFuture.completedFuture(false);
        }
        art.moodframe.model.PromptBlock stored = type == art.moodframe.model.PromptBlock.Type.CAMERA
                ? art.moodframe.service.library.PromptBlockService.camera(null, block.name(), block.settings())
                : new art.moodframe.model.PromptBlock(null, type, block.name(), block.text(), null,
                        type == art.moodframe.model.PromptBlock.Type.STYLE);
        return writes.run(() -> service.addCustom(stored), false, failure, (saved) -> {
            if (saved)
                blocks.fillBlockChoice(type, block.name());
            else
                status.status(failure);
        });
    }

    /** Selects the entry with this id in the field of a building block, "None" if the field does not list it. */
    private void selectField(Mood.Field field, String id) {
        if (field != Mood.Field.COLOR) {
            selectBlock(art.moodframe.model.PromptBlock.Type.valueOf(field.name()), id);
            return;
        }
        art.moodframe.model.ColorStyle palette = colorStyleService().getStyleById(id);
        String paletteName = palette != null ? palette.getName() : "None";
        colorStyleComboBox.getItems().stream().filter((item) -> blocks.colorChoice().isEntry(item)
                && item.getName().equals(paletteName)).findFirst()
                .or(() -> colorStyleComboBox.getItems().stream().filter(blocks.colorChoice()::isEntry).findFirst())
                .ifPresent(colorStyleComboBox::setValue);
    }

    /** The building blocks selected in the main window; none with a reference image unless "Apply mood" is on. */
    private art.moodframe.service.prompt.PromptComposer.Selection selectedBlocks() {
        if (reference && !moodApplyCheckBox.isSelected())
            return new art.moodframe.service.prompt.PromptComposer.Selection(null, null, null, null, null);

        StylePresetItem styleItem = stylePresetsComboBox != null ? stylePresetsComboBox.getValue() : null;
        StylePresetItem atmosphereItem = atmosphereComboBox != null ? atmosphereComboBox.getValue() : null;
        StylePresetItem lightItem = lightComboBox != null ? lightComboBox.getValue() : null;
        StylePresetItem cameraItem = cameraComboBox != null ? cameraComboBox.getValue() : null;
        ColorStyleItem colorItem = colorStyleComboBox != null ? colorStyleComboBox.getValue() : null;
        art.moodframe.model.ColorStyle palette = colorItem != null && !"None".equals(colorItem.getName())
                ? colorItem.toColorStyle() : null;
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
        StylePresetItem styleItem = stylePresetsComboBox.getValue();
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

    /**
     * The aspect ratio the chosen model makes of the target: the target itself if the model can do it, else its
     * closest one.
     *
     * @return the aspect ratio, null without target
     */
    public String modelAspectRatio() {
        return frames.modelAspectRatio();
    }

    /**
     * The resolution the chosen model generates: of free sizes the one closest to the target, else the one of its
     * variant, which is always the one of its family closest to the target (see {@code ModelFamilySelector}).
     *
     * @return the resolution, null for a model with a fixed size
     */
    public String modelResolution() {
        return frames.modelResolution();
    }

    /**
     * Auto-detects the aspect ratio from an uploaded image and sets the closest
     * available option from the currently available ratios in the combo box.
     */
    public void matchAspectRatio(File imageFile) {
        frames.matchAspectRatio(imageFile);
    }

    /** One line of the current state for the brainstorm: a building block field, its entry and its lock. */
    private static String fieldState(String label, Object entry, boolean locked) {
        return "\n" + label + ": " + (entry != null ? entry.toString() : "None") + (locked ? " (locked)" : "");
    }
}
