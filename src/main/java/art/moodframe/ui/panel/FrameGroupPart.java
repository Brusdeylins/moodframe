package art.moodframe.ui.panel;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.AspectRatios;
import art.moodframe.model.Frame;
import art.moodframe.model.ModelFamilies;
import art.moodframe.service.library.FavoriteService;
import art.moodframe.service.library.FrameService;
import art.moodframe.ui.cell.FrameListCell;
import art.moodframe.ui.component.FavoriteStar;
import art.moodframe.ui.component.FrameChoice;
import art.moodframe.ui.dialog.FrameManageDialog;
import art.moodframe.ui.dialog.TrashWindow;
import art.moodframe.ui.support.StatusSink;
import art.moodframe.util.I18nManager;
import art.moodframe.util.ImageSize;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The group Frame of the right panel: the frame selection with its actions, the target aspect ratio and resolution
 * and the hints of what the chosen model makes of them. Choosing a frame sets its targets; either changed afterwards
 * shows the frame as changed until it is saved or chosen again.
 */
final class FrameGroupPart {

    private static final Logger logger = LoggerFactory.getLogger(FrameGroupPart.class);

    /*  an action that would save a change, highlighted while there is one  */
    private static final PseudoClass UNSAVED = PseudoClass.getPseudoClass("unsaved");

    /*  a hint below aspect ratio or resolution while the model does not do the target  */
    private static final PseudoClass TARGET_CHANGED = PseudoClass.getPseudoClass("changed");

    /**
     * The nodes of {@code MoodFramePanel.fxml} the group works on.
     */
    record Nodes(ComboBox<FrameChoice> frameComboBox, ChoiceBox<String> aspectRatioComboBox,
                 Label aspectRatioHintLabel, ChoiceBox<String> resolutionChoiceBox, Label resolutionHintLabel,
                 Button saveFrameButton, Button saveFrameAsButton) {
    }

    private final ComboBox<FrameChoice> frameComboBox;
    private final ChoiceBox<String> aspectRatioComboBox;
    private final Label aspectRatioHintLabel;
    private final ChoiceBox<String> resolutionChoiceBox;
    private final Label resolutionHintLabel;
    private final Button saveFrameButton;
    private final Button saveFrameAsButton;
    private final Supplier<String> modelItem;

    private ApplicationConfig config;
    private FrameService frameService;
    private TrashWindow.Opener trash;
    private PanelWorker writes;
    private StatusSink status;
    private Supplier<Window> window;
    private Predicate<String> isFavorite;

    /*  the favorite stars of the rows of the "Manage…" window  */
    private FavoriteStar.Favorites<Frame> starred;

    /*  whether aspect ratio or resolution differ from the chosen frame, and a guard while the frame selection sets
        them  */
    private boolean frameChanged;
    private boolean updatingFrame;

    /**
     * Lists the targets of all models, of which the chosen model takes its closest one.
     *
     * @param nodes     the nodes of the group
     * @param modelItem the item of the chosen model, which the hints name (may give null)
     */
    FrameGroupPart(Nodes nodes, Supplier<String> modelItem) {
        this.frameComboBox = nodes.frameComboBox();
        this.aspectRatioComboBox = nodes.aspectRatioComboBox();
        this.aspectRatioHintLabel = nodes.aspectRatioHintLabel();
        this.resolutionChoiceBox = nodes.resolutionChoiceBox();
        this.resolutionHintLabel = nodes.resolutionHintLabel();
        this.saveFrameButton = nodes.saveFrameButton();
        this.saveFrameAsButton = nodes.saveFrameAsButton();
        this.modelItem = modelItem;

        aspectRatioComboBox.getItems().addAll(AspectRatios.ALL);
        aspectRatioComboBox.valueProperty().addListener((obs, oldVal, newVal) -> updateAspectRatioHint());
        resolutionChoiceBox.getItems().addAll(ModelFamilies.RESOLUTIONS);
        resolutionChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> updateResolutionHint());
    }

    /**
     * Sets up the frame selection: "No frame" and the frames; the actions sit as icons in the header of the group.
     *
     * @param config         remembers the chosen frame (may be null)
     * @param frameService   the frames (may be null: no frame selection)
     * @param trash          opens the trash on the frames (may be null)
     * @param writes         stores the frames off the JavaFX thread
     * @param status         shows what the group did
     * @param window         the window of the panel, owner of the dialogs
     * @param stars          the favorite stars of the rows of the list
     * @param isFavorite     whether the frame of an id is a favorite
     */
    void setUp(ApplicationConfig config, FrameService frameService, TrashWindow.Opener trash, PanelWorker writes,
               StatusSink status, Supplier<Window> window, FavoriteStar.Favorites<Frame> stars,
               Predicate<String> isFavorite) {
        this.config = config;
        this.frameService = frameService;
        this.trash = trash;
        this.writes = writes;
        this.status = status;
        this.window = window;
        this.isFavorite = isFavorite;
        this.starred = new FavoriteStar.Favorites<>(null, stars.favorite(), stars.toggle());
        if (frameService == null || frameComboBox == null) {
            logger.warn("FrameService not available, no frame selection");
            return;
        }

        frameComboBox.setCellFactory((listView) -> new FrameListCell(stars));
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
    Frame chosenFrame() {
        FrameChoice choice = frameComboBox != null ? frameComboBox.getValue() : null;
        return choice != null ? choice.frame() : null;
    }

    /** @return whether aspect ratio or resolution differ from the chosen frame */
    boolean isFrameChanged() {
        return frameChanged;
    }

    /** "Save" is not available for a standard frame, which can only be saved as a new one. */
    private boolean isFrameSaveLocked() {
        Frame frame = chosenFrame();
        return frame != null && frame.isDefault();
    }

    /** Enables "Save" as the field and the chosen frame allow, e.g. once the input is enabled or disabled. */
    void updateSaveLock() {
        saveFrameButton.setDisable(frameComboBox.isDisable() || isFrameSaveLocked());
    }

    /**
     * Lists "No frame" and the frames, the favorites first, each part alphabetically, and shows the frame with this id
     * as chosen, without applying it.
     */
    void reloadFrames(String selectId) {
        List<FrameChoice> items = FrameChoice.listOf(FavoriteService.order(frameService.getAll(),
                (frame) -> isFavorite.test(frame.getId()), (frame) -> 0, Frame::getName));

        updatingFrame = true;
        frameComboBox.getItems().setAll(items);
        frameComboBox.setValue(items.stream().filter((item) -> item.frame() != null
                && item.frame().getId().equals(selectId)).findFirst().orElse(items.get(0)));
        updatingFrame = false;
    }

    /** Lists the frames again in the order of the favorites, keeping the choice without applying it. */
    void reloadKeepingChoice() {
        Frame chosen = chosenFrame();
        reloadFrames(chosen != null ? chosen.getId() : "");
    }

    /** Lists the frames again, keeping the choice, for example once a frame left the trash or was renamed. */
    void refreshFrames() {
        if (frameService == null)
            return;
        reloadKeepingChoice();
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
    void chooseFrame(Frame frame) {
        updatingFrame = true;
        if (frame != null) {
            aspectRatioComboBox.setValue(frame.getAspectRatio());
            if (frame.getResolution() != null)
                resolutionChoiceBox.setValue(frame.getResolution());
        }
        showChosen(frame);
        updatingFrame = false;
        updateFrameState();
    }

    /**
     * Sets aspect ratio and resolution as given and shows a frame as chosen, which the targets may differ from, as a
     * chat entry with a setting of its own does.
     *
     * @param frame       the frame, null for "No frame"
     * @param aspectRatio the aspect ratio, unchanged if null or unknown
     * @param resolution  the resolution, unchanged if null or unknown
     */
    void takeTargets(Frame frame, String aspectRatio, String resolution) {
        updatingFrame = true;
        if (aspectRatio != null && aspectRatioComboBox.getItems().contains(aspectRatio))
            aspectRatioComboBox.setValue(aspectRatio);
        if (resolution != null && resolutionChoiceBox.getItems().contains(resolution))
            resolutionChoiceBox.setValue(resolution);
        showChosen(frame);
        updatingFrame = false;
        updateFrameState();
    }

    /*  shows a frame, or "No frame" for null, as chosen  */
    private void showChosen(Frame frame) {
        frameComboBox.getItems().stream().filter((item) -> frame == null ? item.kind() == FrameChoice.Kind.NONE
                : item.frame() != null && item.frame().getId().equals(frame.getId()))
                .findFirst().ifPresent(frameComboBox::setValue);
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
        updateSaveLock();
        saveFrameButton.pseudoClassStateChanged(UNSAVED, frameChanged && !isFrameSaveLocked());
        saveFrameAsButton.pseudoClassStateChanged(UNSAVED, frameChanged && isFrameSaveLocked());
        ((FrameListCell) frameComboBox.getButtonCell()).refresh();
    }

    /**
     * Saves aspect ratio and resolution over the chosen frame of the user, or as a new frame under a name entered; the
     * worker stores it, then the list shows it as chosen.
     */
    void saveFrame(boolean asNew) {
        if (frameService == null)
            return;
        Frame chosen = chosenFrame();
        Frame frame;
        if (!asNew && chosen != null && !chosen.isDefault())
            frame = chosen;
        else {
            String name = FrameManageDialog.askName(window.get(), frameService::exists,
                    I18nManager.getText("frame.name.new"), chosen != null ? frameService.freeName(chosen.getName()) : "",
                    null);
            if (name == null)
                return;
            frame = new Frame(null, name);
        }
        frame.setAspectRatio(aspectRatioComboBox.getValue());
        frame.setResolution(resolutionChoiceBox.getValue());

        String failure = I18nManager.getText("error.frame.save");
        boolean add = frame.getId() == null;
        writes.run(() -> add ? frameService.add(frame) : frameService.update(frame), false, failure, (saved) -> {
            if (!saved) {
                status.failure(failure);
                return;
            }
            reloadFrames(frame.getId());
            updateFrameState();
            status.status(I18nManager.getText("status.frame.saved", frame.getName()));
        });
    }

    /**
     * Saves aspect ratio and resolution as a new frame under a name entered and chooses it once the worker stored it.
     *
     * @param name        the suggested name
     * @param aspectRatio the aspect ratio
     * @param resolution  the resolution
     */
    void saveAsNew(String name, String aspectRatio, String resolution) {
        if (frameService == null)
            return;
        name = FrameManageDialog.askName(window.get(), frameService::exists, I18nManager.getText("frame.name.new"),
                name != null ? frameService.freeName(name) : "", null);
        if (name == null)
            return;
        Frame frame = new Frame(null, name);
        frame.setAspectRatio(aspectRatio);
        frame.setResolution(resolution);
        String failure = I18nManager.getText("error.frame.save");
        writes.run(() -> frameService.add(frame), false, failure, (saved) -> {
            if (!saved) {
                status.failure(failure);
                return;
            }
            reloadFrames(frame.getId());
            chooseFrame(frame);
            status.status(I18nManager.getText("status.frame.saved", frame.getName()));
        });
    }

    /** Opens the window for the frames of the user; the list follows its changes. */
    void manageFrames() {
        if (frameService != null)
            new FrameManageDialog(window.get(), ManagedLibraries.frames(frameService, writes), starred, trash)
                    .showAndWait();
    }

    /** Shows the hints of what the chosen model makes of aspect ratio and resolution. */
    void updateHints() {
        updateAspectRatioHint();
        updateResolutionHint();
    }

    /**
     * Shows below the aspect ratio what the chosen model makes of the target, for example "FLUX.2 [pro] → 5:4", but
     * only, highlighted, while it differs from the target.
     */
    private void updateAspectRatioHint() {
        String item = modelItem.get();
        String ratio = modelAspectRatio();
        if (aspectRatioHintLabel == null || item == null || ratio == null)
            return;
        aspectRatioHintLabel.setText(ModelFamilies.familyOf(item) + " → " + ratio);
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
    String modelAspectRatio() {
        String target = aspectRatioComboBox.getValue();
        return target != null ? AspectRatios.nearestForItem(target, modelItem.get()) : null;
    }

    /**
     * Shows below the resolution what the chosen model makes of the target, for example "Nano Banana 2 Lite → 1K", but
     * only, highlighted, while it differs from the target; a model without resolution choice keeps its fixed size.
     */
    void updateResolutionHint() {
        String item = modelItem.get();
        if (resolutionHintLabel == null || item == null)
            return;
        String resolution = modelResolution();
        resolutionHintLabel.setText(ModelFamilies.familyOf(item) + " → "
                + (resolution != null ? resolution : I18nManager.getText("params.resolution.fixed")));
        showTargetHint(resolutionHintLabel, resolution == null || !resolution.equals(resolutionChoiceBox.getValue()));
    }

    /**
     * The resolution the chosen model generates: of free sizes the one closest to the target, else the one of its
     * variant, which is always the one of its family closest to the target (see {@code ModelFamilySelector}).
     *
     * @return the resolution, null for a model with a fixed size
     */
    String modelResolution() {
        String item = modelItem.get();
        List<String> free = ModelFamilies.freeResolutions(item);
        return free != null ? ModelFamilies.nearest(resolutionChoiceBox.getValue(), free)
                : item != null ? ModelFamilies.resolutionOf(item) : null;
    }

    /**
     * Auto-detects the aspect ratio from an uploaded image and sets the closest
     * available option from the currently available ratios in the combo box.
     */
    void matchAspectRatio(File imageFile) {
        if (imageFile == null || aspectRatioComboBox == null) {
            return;
        }

        try {
            // Read the dimensions from the header of the file, without decoding the image on the FX thread
            ImageSize size = ImageSize.read(imageFile.toPath());
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
    private static double parseRatioToDouble(String ratioStr) {
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
}
