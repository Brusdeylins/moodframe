package art.moodframe.ui.component;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.scene.text.TextAlignment;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;

import art.moodframe.config.ServiceFactory;
import art.moodframe.model.CameraSettings;
import art.moodframe.model.CameraSettings.Aperture;
import art.moodframe.model.CameraSettings.Body;
import art.moodframe.model.CameraSettings.Iso;
import art.moodframe.model.CameraSettings.Lens;
import art.moodframe.model.CameraSettings.Perspective;
import art.moodframe.model.CameraSettings.Shot;
import art.moodframe.model.CameraSettings.Shutter;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.CameraPromptBuilder;
import art.moodframe.service.PromptBlockService;
import art.moodframe.util.I18nChangeListener;
import art.moodframe.util.I18nManager;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntToDoubleFunction;

/**
 * Dialog for creating and editing a camera: no free text, but settings from which the app builds the fragment.
 *
 * <p>
 * Left the settings (tiles for the camera type with its model, sliders with detents for focal length and aperture,
 * symbol rows for shutter, ISO, perspective and shot size), right a schematic preview and an example picture of the
 * stage under the mouse, below the generated prompt, all live. Every setting starts at "Any". A stage whose prompt
 * text contradicts the chosen type or lens is blocked and names the reason ({@link CameraSettings#blocker}). A camera
 * of the user is saved in place or as a new one, a standard camera only as a new one.
 * </p>
 *
 * @since 2.0.0
 */
public class CameraEditDialog extends Dialog<CameraEditDialog.Result> implements I18nChangeListener {

    /**
     * Result of the dialog.
     *
     * @param save     how the camera is stored
     * @param name     the name
     * @param settings the settings
     */
    public record Result(BlockEditDialog.Save save, String name, CameraSettings settings) {
    }

    /*  a control of a stage that a chosen type or lens can block, in the wrapper naming the reason: a disabled control
        gets no mouse events  */
    private record Blockable(Enum<?> stage, Control control, Node wrapper, Tooltip reason) {
    }

    /*  The detents of the focal length slider: "Any", then the focal lengths from wide to long  */
    private static final List<Lens> FOCAL_LENGTHS = List.of(Lens.MM_14, Lens.MM_24, Lens.MM_35, Lens.MM_50,
            Lens.MM_85, Lens.MM_135, Lens.MM_400);
    private static final List<Lens> SPECIAL_LENSES = List.of(Lens.MACRO, Lens.FISHEYE, Lens.TILT_SHIFT,
            Lens.ANAMORPHIC);
    private static final double SETTINGS_WIDTH = 484;

    private static final Map<Body, String> BODY_ICONS = Map.of(Body.SMARTPHONE, "mdi2c-cellphone",
            Body.COMPACT_35MM, "mdi2f-film", Body.FULL_FRAME, "mdi2c-camera",
            Body.MEDIUM_FORMAT, "mdi2c-camera-iris", Body.LARGE_FORMAT, "mdi2c-crop-free",
            Body.CINEMA, "mdi2m-movie-open-outline", Body.INSTANT, "mdi2i-image-frame", Body.DRONE, "mdi2q-quadcopter",
            Body.ACTION_CAM, "mdi2c-camera-gopro");
    private static final Map<Shutter, String> SHUTTER_ICONS = Map.of(Shutter.FROZEN, "mdi2s-snowflake",
            Shutter.NORMAL, "mdi2c-camera-iris", Shutter.MOTION_BLUR, "mdi2r-run-fast",
            Shutter.LONG_EXPOSURE, "mdi2t-timer-sand");
    private static final Map<Perspective, String> PERSPECTIVE_ICONS = Map.of(
            Perspective.EYE_LEVEL, "mdi2e-eye-outline", Perspective.LOW_ANGLE, "mdi2a-arrow-up-bold-outline",
            Perspective.HIGH_ANGLE, "mdi2a-arrow-down-bold-outline", Perspective.TOP_DOWN, "mdi2a-arrow-collapse-down",
            Perspective.DUTCH_ANGLE, "mdi2r-rotate-right");
    private static final Map<Shot, String> SHOT_ICONS = Map.of(Shot.EXTREME_WIDE, "mdi2i-image-filter-hdr",
            Shot.WIDE, "mdi2a-account-multiple-outline", Shot.MEDIUM, "mdi2a-account-outline",
            Shot.CLOSE_UP, "mdi2a-account-box-outline", Shot.DETAIL, "mdi2m-magnify-plus-outline");

    private final PromptBlock block;
    private final PromptBlockService blockService;
    private final ButtonType saveButtonType;
    private final ButtonType saveAsNewButtonType;
    private final Map<Object, String> texts = new IdentityHashMap<>();
    private final List<Runnable> headings = new ArrayList<>();
    private final List<Blockable> blockables = new ArrayList<>();
    private final TextField nameField = new TextField();
    private final ToggleGroup bodyGroup = new ToggleGroup();
    private final ChoiceBox<CameraPromptBuilder.Model> modelBox = new ChoiceBox<>();
    private final Slider focalSlider = new Slider(0, FOCAL_LENGTHS.size(), 0);
    private final ToggleGroup specialGroup = new ToggleGroup();
    private final Slider apertureSlider = new Slider(0, Aperture.values().length, 0);
    private final ToggleGroup shutterGroup = new ToggleGroup();
    private final ToggleGroup isoGroup = new ToggleGroup();
    private final ToggleGroup perspectiveGroup = new ToggleGroup();
    private final ToggleGroup shotGroup = new ToggleGroup();
    private final CameraPreview preview = new CameraPreview();
    private final CameraExample example = new CameraExample();
    private final TextArea promptArea = new TextArea();
    private final Label validationLabel = new Label();
    private final Label helpLabel = new Label();

    /**
     * Creates the dialog.
     *
     * @param block the camera to edit, null for a new one
     */
    public CameraEditDialog(PromptBlock block) {
        this.block = block;
        this.blockService = ServiceFactory.getInstance().getService(PromptBlockService.class);
        CameraSettings settings = (block != null ? CameraSettings.fromJson(block.getParams()) : CameraSettings.ANY)
                .withoutConflicts();

        I18nManager.addChangeListener(this);

        /*  A new camera and a camera of the user are saved, a standard camera only as a new one: then "As New Entry"
            is the main button, else it stands at the far left  */
        DialogPane dialogPane = getDialogPane();
        saveButtonType = block == null || !block.isDefault() ? new ButtonType("", ButtonBar.ButtonData.OK_DONE) : null;
        saveAsNewButtonType = block != null ? new ButtonType("", saveButtonType != null ? ButtonBar.ButtonData.LEFT
                : ButtonBar.ButtonData.OK_DONE) : null;
        if (saveAsNewButtonType != null)
            dialogPane.getButtonTypes().add(saveAsNewButtonType);
        if (saveButtonType != null)
            dialogPane.getButtonTypes().add(saveButtonType);
        dialogPane.getButtonTypes().add(ButtonType.CANCEL);

        /*  Name  */
        nameField.setText(block != null ? block.getName() : "");
        HBox.setHgrow(nameField, Priority.ALWAYS);
        HBox nameRow = new HBox(10, text(new Label(), "block.dialog.name"), nameField);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        /*  Camera type: tiles, "Any" first, with the model of the chosen type next to them  */
        FlowPane bodyTiles = new FlowPane(4, 4);
        bodyTiles.getChildren().add(tile(bodyGroup, null, "camera.dialog.any", "mdi2a-asterisk", settings.body()));
        for (Body body : Body.values())
            bodyTiles.getChildren().add(tile(bodyGroup, body, "camera.body." + key(body), BODY_ICONS.get(body),
                    settings.body()));
        modelBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(CameraPromptBuilder.Model model) {
                return model == null ? "" : model.name().isEmpty()
                        ? I18nManager.getText("camera.dialog.model.generic") : model.label();
            }

            @Override
            public CameraPromptBuilder.Model fromString(String text) {
                return null;
            }
        });
        modelBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(modelBox, Priority.ALWAYS);
        fillModels(settings.body(), settings.model());
        HBox modelRow = new HBox(10, text(new Label(), "camera.dialog.model"), modelBox);
        modelRow.setAlignment(Pos.CENTER_LEFT);

        /*  Focal length: a slider with detents, the special lenses beside it; a special lens overrides the slider  */
        VBox focalTrack = detents(focalSlider, FOCAL_LENGTHS, (lens) -> String.valueOf(lens.millimeters()));
        focalSlider.setValue(settings.lens() != null && !settings.lens().isSpecial()
                ? FOCAL_LENGTHS.indexOf(settings.lens()) + 1 : 0);
        HBox specialRow = new HBox(4);
        for (Lens lens : SPECIAL_LENSES) {
            ToggleButton button = text(new ToggleButton(), "camera.lens." + key(lens));
            button.setToggleGroup(specialGroup);
            button.setUserData(lens);
            button.setSelected(lens == settings.lens());
            StackPane wrapper = blockable(lens, button);
            hover(wrapper, (event) -> lens);
            specialRow.getChildren().add(wrapper);
        }
        focalSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (specialGroup.getSelectedToggle() != null && oldVal.intValue() != newVal.intValue())
                specialGroup.selectToggle(null);
        });

        /*  Aperture: a slider with detents from open to closed  */
        VBox apertureTrack = detents(apertureSlider, List.of(Aperture.values()), Aperture::label);
        apertureSlider.setValue(settings.aperture() != null ? settings.aperture().ordinal() + 1 : 0);

        /*  Shutter, ISO, perspective and shot size: rows of symbols  */
        VBox shutter = symbols("camera.dialog.shutter", shutterGroup, Shutter.values(),
                (value) -> "camera.shutter." + key(value), SHUTTER_ICONS, settings.shutter());
        VBox iso = symbols("camera.dialog.iso", isoGroup, Iso.values(), null, null, settings.iso());
        VBox perspective = symbols("camera.dialog.perspective", perspectiveGroup, Perspective.values(),
                (value) -> "camera.perspective." + key(value), PERSPECTIVE_ICONS, settings.perspective());
        VBox shot = symbols("camera.dialog.shot", shotGroup, Shot.values(), (value) -> "camera.shot." + key(value),
                SHOT_ICONS, settings.shot());

        /*  Left all settings in the order of the prompt, right the preview with the example below it  */
        VBox settingsBox = new VBox(6,
                heading("camera.dialog.type"), bodyTiles, modelRow,
                heading("camera.dialog.lens"), focalTrack, specialRow,
                heading("camera.dialog.aperture"), apertureTrack,
                new HBox(20, shutter, iso), perspective, shot);
        settingsBox.setMinWidth(SETTINGS_WIDTH);
        settingsBox.setPrefWidth(SETTINGS_WIDTH);
        bodyTiles.setPrefWrapLength(SETTINGS_WIDTH);
        VBox previewBox = new VBox(10, preview, example);

        /*  Generated prompt, below both columns  */
        promptArea.setEditable(false);
        promptArea.setWrapText(true);
        promptArea.setPrefRowCount(3);
        promptArea.setFocusTraversable(false);

        validationLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-danger-fg;");
        validationLabel.setWrapText(true);
        validationLabel.setVisible(false);
        validationLabel.setManaged(false);
        helpLabel.setStyle("-fx-font-size: 11px;");
        helpLabel.setWrapText(true);
        texts.put(helpLabel, "camera.dialog.help");

        HBox columns = new HBox(24, settingsBox, previewBox);
        VBox content = new VBox(10, nameRow, validationLabel, columns, heading("camera.dialog.prompt"), promptArea,
                helpLabel);
        content.setPadding(new Insets(20, 20, 10, 20));
        content.setPrefWidth(SETTINGS_WIDTH + 24 + 300 + 40);
        dialogPane.setContent(content);
        setResizable(true);

        updateTexts();

        /*  Every change shows at once in the preview and in the prompt  */
        bodyGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            fillModels(selected(bodyGroup, Body.class), null);
            update();
        });
        modelBox.valueProperty().addListener((obs, oldVal, newVal) -> update());
        focalSlider.valueProperty().addListener((obs, oldVal, newVal) -> update());
        specialGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> update());
        apertureSlider.valueProperty().addListener((obs, oldVal, newVal) -> update());
        for (ToggleGroup group : List.of(shutterGroup, isoGroup, perspectiveGroup, shotGroup))
            group.selectedToggleProperty().addListener((obs, oldVal, newVal) -> update());
        update();

        /*  a closed dialog no longer follows the language, so it and its pictures are released  */
        setOnHidden((event) -> I18nManager.removeChangeListener(this));

        validateName(nameField.getText().trim());
        nameField.textProperty().addListener((obs, oldText, newText) -> validateName(newText.trim()));
        Platform.runLater(nameField::requestFocus);

        setResultConverter((buttonType) -> {
            if (buttonType == null || buttonType == ButtonType.CANCEL)
                return null;
            return new Result(buttonType == saveButtonType ? BlockEditDialog.Save.SAVE
                    : BlockEditDialog.Save.SAVE_AS_NEW, nameField.getText().trim(), settings());
        });
    }

    /** The settings as chosen in the dialog. */
    private CameraSettings settings() {
        CameraPromptBuilder.Model model = modelBox.getValue();
        Lens special = selected(specialGroup, Lens.class);
        int focal = (int) Math.round(focalSlider.getValue());
        int aperture = (int) Math.round(apertureSlider.getValue());
        return new CameraSettings(selected(bodyGroup, Body.class),
                model != null && !model.name().isEmpty() ? model.name() : null,
                special != null ? special : focal > 0 ? FOCAL_LENGTHS.get(focal - 1) : null,
                aperture > 0 ? Aperture.values()[aperture - 1] : null,
                selected(shutterGroup, Shutter.class), selected(isoGroup, Iso.class),
                selected(perspectiveGroup, Perspective.class), selected(shotGroup, Shot.class));
    }

    /*  the preview and the prompt for a photographic style; a special lens dims the slider it overrides. A stage
        blocked by the type or lens just chosen goes back to "Any" first, each reset updating anew.  */
    private void update() {
        CameraSettings settings = settings();
        CameraSettings allowed = settings.withoutConflicts();
        if (!allowed.equals(settings)) {
            if (allowed.lens() != settings.lens()) {
                specialGroup.selectToggle(null);
                focalSlider.setValue(0);
            }
            if (allowed.aperture() != settings.aperture())
                apertureSlider.setValue(0);
            if (allowed.perspective() != settings.perspective())
                selectAny(perspectiveGroup);
            if (allowed.shot() != settings.shot())
                selectAny(shotGroup);
            return;
        }
        for (Blockable blockable : blockables) {
            Enum<?> blocker = settings.blocker(blockable.stage());
            blockable.control().setDisable(blocker != null);
            if (blocker != null) {
                blockable.reason().setText(I18nManager.getText("camera.dialog.blocked", CameraExample.name(blocker)));
                Tooltip.install(blockable.wrapper(), blockable.reason());
            }
            else
                Tooltip.uninstall(blockable.wrapper(), blockable.reason());
        }
        String camera = CameraPromptBuilder.build(settings, true);
        preview.show(settings);
        promptArea.setText(camera.isEmpty() ? I18nManager.getText("camera.dialog.prompt.empty")
                : "Camera: " + camera + ".");
        focalSlider.setOpacity(specialGroup.getSelectedToggle() != null ? 0.5 : 1);
    }

    /*  while the mouse is over a control, the example shows the stage under it, afterwards the empty frame  */
    private void hover(Node node, Function<MouseEvent, Enum<?>> stage) {
        node.addEventHandler(MouseEvent.MOUSE_MOVED, (event) -> example.show(stage.apply(event)));
        node.addEventHandler(MouseEvent.MOUSE_EXITED, (event) -> example.clear());
    }

    /*  a control of a stage that a chosen type or lens can block, wrapped for the reason  */
    private StackPane blockable(Enum<?> stage, Control control) {
        StackPane wrapper = new StackPane(control);
        blockables.add(new Blockable(stage, control, wrapper, new Tooltip()));
        return wrapper;
    }

    private static void selectAny(ToggleGroup group) {
        group.getToggles().stream().filter((toggle) -> toggle.getUserData() == null).findFirst()
                .ifPresent(group::selectToggle);
    }

    /*  "Generic" and the models of the type; no model without a type  */
    private void fillModels(Body body, String model) {
        CameraPromptBuilder.Model generic = new CameraPromptBuilder.Model(body, "", false);
        List<CameraPromptBuilder.Model> models = new ArrayList<>(List.of(generic));
        if (body != null)
            models.addAll(CameraPromptBuilder.models(body));
        modelBox.getItems().setAll(models);
        modelBox.setValue(models.stream().filter((item) -> item.name().equals(model)).findFirst().orElse(generic));
        modelBox.setDisable(body == null);
    }

    /*  A tile of the camera type: symbol over name  */
    private <E extends Enum<E>> ToggleButton tile(ToggleGroup group, E value, String key, String icon, E selected) {
        ToggleButton tile = text(new ToggleButton(), key);
        tile.setGraphic(icon(icon, 20));
        tile.setContentDisplay(ContentDisplay.TOP);
        tile.setPrefSize(118, 54);
        tile.getStyleClass().add("text-small");
        tile.setWrapText(true);
        tile.setTextAlignment(TextAlignment.CENTER);
        join(group, tile, value, value == selected);
        hover(tile, (event) -> value);
        return tile;
    }

    /*  A setting as a row of symbols, "Any" first, each named by its tooltip; the heading names the chosen one. The
        ISO values show their number instead, below the heading "ISO".  */
    private <E extends Enum<E>> VBox symbols(String headingKey, ToggleGroup group, E[] values,
            Function<E, String> key, Map<E, String> icons, E selected) {
        HBox row = new HBox(4);
        Map<Toggle, String> names = new IdentityHashMap<>();
        List<E> choices = new ArrayList<>();
        choices.add(null);
        choices.addAll(List.of(values));
        for (E value : choices) {
            String name = value == null ? "camera.dialog.any" : key != null ? key.apply(value) : null;
            ToggleButton button = new ToggleButton();
            if (value == null || icons != null) {
                button.setGraphic(icon(value == null ? "mdi2a-asterisk" : icons.get(value), 18));
                button.setPrefSize(38, 34);

                /*  A row of icons only is a row of icon buttons; "Any" beside the ISO values stays a chip like them  */
                if (icons != null)
                    button.getStyleClass().add("icon-button");
                Tooltip tooltip = new Tooltip();
                texts.put(tooltip, name);
                button.setTooltip(tooltip);
            }
            else {
                button.setText(((Iso) value).label().substring("ISO ".length()));
                button.setPrefHeight(34);
            }
            names.put(button, name);
            join(group, button, value, value == selected);
            StackPane wrapper = blockable(value, button);
            hover(wrapper, (event) -> value);
            row.getChildren().add(wrapper);
        }

        Label heading = heading(null);
        Runnable naming = () -> {
            String name = names.get(group.getSelectedToggle());
            heading.setText(I18nManager.getText(headingKey) + (icons != null && name != null
                    ? ": " + I18nManager.getText(name) : ""));
        };
        group.selectedToggleProperty().addListener((obs, oldVal, newVal) -> naming.run());
        headings.add(naming);
        return new VBox(6, heading, row);
    }

    /*  a member of a group in which one button stays selected: clicking the selected one keeps it  */
    private static void join(ToggleGroup group, ToggleButton button, Object value, boolean selected) {
        button.setUserData(value);
        button.setToggleGroup(group);
        button.setSelected(selected);
        if (group.getProperties().putIfAbsent("keep-selection", true) == null)
            group.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal == null && oldVal != null)
                    oldVal.setSelected(true);
            });
    }

    private static <E> E selected(ToggleGroup group, Class<E> type) {
        Toggle toggle = group.getSelectedToggle();
        return toggle != null && type.isInstance(toggle.getUserData()) ? type.cast(toggle.getUserData()) : null;
    }

    /*  A slider that stops on whole numbers only, "Any" first, then the stages, with its ticks and labels drawn below
        it, as JavaFX loses the last tick at some widths; each label stays inside the slider. A blocked detent is
        skipped: a step goes on to the next allowed detent in its direction, dragging takes the nearest allowed one.  */
    private <E extends Enum<E>> VBox detents(Slider slider, List<E> stages, Function<E, String> label) {
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setBlockIncrement(1);

        List<Line> ticks = new ArrayList<>();
        List<Node> labels = new ArrayList<>();
        for (int index = 0; index <= stages.size(); index++) {
            E stage = index > 0 ? stages.get(index - 1) : null;
            Label name = stage != null ? new Label(label.apply(stage)) : text(new Label(), "camera.dialog.any");
            name.getStyleClass().add("text-small");
            labels.add(blockable(stage, name));
            Line tick = new Line(0, 1, 0, 5);
            tick.setStyle("-fx-stroke: -color-slider-tick;");
            ticks.add(tick);
        }
        Pane scale = new Pane() {
            @Override
            protected double computePrefHeight(double width) {
                return 7 + labels.getFirst().prefHeight(-1);
            }

            @Override
            protected void layoutChildren() {
                for (int index = 0; index < labels.size(); index++) {
                    double x = Math.round(position(slider, index)) + 0.5;
                    ticks.get(index).setStartX(x);
                    ticks.get(index).setEndX(x);
                    Node name = labels.get(index);
                    double width = name.prefWidth(-1);
                    name.resizeRelocate(Math.clamp(x - width / 2, 0, Math.max(0, getWidth() - width)), 7, width,
                            name.prefHeight(width));
                }
            }
        };
        scale.getChildren().addAll(ticks);
        scale.getChildren().addAll(labels);
        scale.setMinHeight(Region.USE_PREF_SIZE);

        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            CameraSettings settings = settings();
            double value = newVal.doubleValue();
            int index = (int) Math.round(value);
            if (settings.blocker(index > 0 ? stages.get(index - 1) : null) == null)
                return;
            double step = value == index ? Math.signum(value - oldVal.doubleValue()) : 0;
            IntToDoubleFunction distance = (i) -> Math.abs(i - value) + (step * (i - value) < 0 ? stages.size() : 0);
            int target = 0;
            for (int i = 1; i <= stages.size(); i++)
                if (settings.blocker(stages.get(i - 1)) == null
                        && distance.applyAsDouble(i) < distance.applyAsDouble(target))
                    target = i;
            slider.setValue(target);
        });

        VBox track = new VBox(slider, scale);
        hover(track, (event) -> {
            int index = detent(slider, event);
            return index > 0 ? stages.get(index - 1) : null;
        });
        return track;
    }

    /*  the x of a detent within the slider, along the track the skin lays out between the thumb halves  */
    private static double position(Slider slider, double value) {
        Node thumb = slider.lookup(".thumb");
        double thumbWidth = thumb != null ? thumb.getLayoutBounds().getWidth() : 0;
        double length = slider.getWidth() - slider.snappedLeftInset() - slider.snappedRightInset() - thumbWidth;
        return slider.snappedLeftInset() + thumbWidth / 2
                + length * (value - slider.getMin()) / (slider.getMax() - slider.getMin());
    }

    /*  the detent of a slider nearest to the mouse  */
    private static int detent(Slider slider, MouseEvent event) {
        double start = position(slider, slider.getMin());
        double ratio = Math.clamp((event.getX() - start) / (position(slider, slider.getMax()) - start), 0, 1);
        return (int) Math.round(slider.getMin() + ratio * (slider.getMax() - slider.getMin()));
    }

    /*  a bold heading; without key, its text is set by the caller  */
    private Label heading(String key) {
        Label label = key != null ? text(new Label(), key) : new Label();
        label.getStyleClass().add("text-bold");
        label.setPadding(new Insets(4, 0, 0, 0));
        return label;
    }

    private <T extends Labeled> T text(T labeled, String key) {
        texts.put(labeled, key);
        return labeled;
    }

    private static FontIcon icon(String literal, int size) {
        FontIcon icon = new FontIcon(literal);
        icon.getStyleClass().add("ikonli-font-icon");
        icon.setIconSize(size);
        return icon;
    }

    private static String key(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Validates the name: it must not be empty, and saving in place needs a name no other camera has. Saving as a new
     * camera takes the next free name instead.
     *
     * @param name the name to validate
     */
    private void validateName(String name) {
        boolean taken = !name.isEmpty() && (block == null || !name.equals(block.getName())) && blockService != null
                && blockService.exists(PromptBlock.Type.CAMERA, name);

        if (saveButtonType != null)
            getDialogPane().lookupButton(saveButtonType).setDisable(name.isEmpty() || taken);
        if (saveAsNewButtonType != null)
            getDialogPane().lookupButton(saveAsNewButtonType).setDisable(name.isEmpty());

        validationLabel.setText(taken ? I18nManager.getText(saveAsNewButtonType != null
                ? "block.dialog.validation.taken.copy" : "block.dialog.validation.taken") : "");
        validationLabel.setVisible(taken);
        validationLabel.setManaged(taken);
    }

    /**
     * Updates all text elements with localized strings.
     */
    private void updateTexts() {
        setTitle(block == null ? I18nManager.getText("block.dialog.new.camera")
                : I18nManager.getText("block.dialog.edit", I18nManager.getText("block.type.camera")));
        setHeaderText(block != null && block.isDefault()
                ? I18nManager.getText("block.dialog.header.default", block.getName()) : null);
        texts.forEach((target, key) -> {
            if (target instanceof Labeled labeled)
                labeled.setText(I18nManager.getText(key));
            else if (target instanceof Tooltip tooltip)
                tooltip.setText(I18nManager.getText(key));
        });
        headings.forEach(Runnable::run);
        nameField.setPromptText(I18nManager.getText("block.dialog.name.placeholder"));
        preview.updateTexts();
        example.updateTexts();

        /*  the label of "Generic" is drawn anew  */
        CameraPromptBuilder.Model model = modelBox.getValue();
        modelBox.setValue(null);
        modelBox.setValue(model);
        update();

        if (saveButtonType != null)
            ((Button) getDialogPane().lookupButton(saveButtonType)).setText(I18nManager.getText("block.dialog.save"));
        if (saveAsNewButtonType != null)
            ((Button) getDialogPane().lookupButton(saveAsNewButtonType)).setText(
                    I18nManager.getText("block.dialog.save.new"));
    }

    @Override
    public void onLanguageChanged() {
        Platform.runLater(this::updateTexts);
    }
}
