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
 * Left the settings (tiles for the camera type, sliders with detents for focal length, aperture, shutter and ISO,
 * symbol rows for perspective and shot size), right a schematic preview and an example picture of the stage under the
 * mouse or else of the one changed last, below the generated prompt, all live. Every setting starts at "Any". A type
 * with a fixed lens or an automatic sets those settings itself and shows them read-only ({@link CameraSettings#fixes});
 * a stage whose prompt text contradicts the chosen type or lens is blocked and names the reason
 * ({@link CameraSettings#blocker}). A camera of the user is saved in place or as a new one, a standard camera only as
 * a new one.
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
    private record Blockable<E extends Enum<E>>(Class<E> setting, E stage, Control control, Node wrapper,
            Tooltip reason) {
    }

    /*  a setting a camera type can fix: its controls read-only but not greyed, the hint beside its heading naming the
        type, its "Any" shown as "Auto" while fixed to automatic  */
    private record Fixable<E extends Enum<E>>(Class<E> setting, List<Control> controls, Label any, Label hint) {
    }

    /*  The detents of the focal length slider: "Any", then the focal lengths from wide to long  */
    private static final List<Lens> FOCAL_LENGTHS = List.of(Lens.MM_14, Lens.MM_24, Lens.MM_35, Lens.MM_50,
            Lens.MM_85, Lens.MM_135, Lens.MM_400);
    private static final List<Lens> SPECIAL_LENSES = List.of(Lens.MACRO, Lens.FISHEYE, Lens.TILT_SHIFT, Lens.SHIFT,
            Lens.ANAMORPHIC);
    private static final double SETTINGS_WIDTH = 484;
    private static final double ISO_WIDTH = 160;

    private static final Map<Body, String> BODY_ICONS = Map.of(Body.SMARTPHONE, "mdi2c-cellphone",
            Body.COMPACT_35MM, "mdi2f-film", Body.FULL_FRAME, "mdi2c-camera",
            Body.MEDIUM_FORMAT, "mdi2c-camera-iris", Body.INSTANT, "mdi2i-image-frame", Body.DRONE, "mdi2q-quadcopter",
            Body.ACTION_CAM, "mdi2c-camera-gopro");
    private static final Map<Shutter, String> SHUTTER_ICONS = Map.of(Shutter.FROZEN, "mdi2s-snowflake",
            Shutter.NORMAL, "mdi2c-camera-iris", Shutter.MOTION_BLUR, "mdi2r-run-fast",
            Shutter.LONG_EXPOSURE, "mdi2t-timer-sand");
    private static final Map<Iso, String> ISO_ICONS = Map.of(Iso.ISO_100, "mdi2w-weather-sunny",
            Iso.ISO_3200, "mdi2g-grain");
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
    private final ButtonType cancelButtonType = DialogButtons.cancel();
    private final Map<Object, String> texts = new IdentityHashMap<>();
    private final List<Runnable> headings = new ArrayList<>();
    private final List<Blockable<?>> blockables = new ArrayList<>();
    private final List<Fixable<?>> fixables = new ArrayList<>();
    private final TextField nameField = new TextField();
    private final ToggleGroup bodyGroup = new ToggleGroup();
    private final Slider focalSlider = new Slider(0, FOCAL_LENGTHS.size(), 0);
    private final VBox focalTrack;
    private final StackPane focalBox;
    private final Tooltip replaced = new Tooltip();
    private final ToggleGroup specialGroup = new ToggleGroup();
    private final Slider apertureSlider = new Slider(0, Aperture.values().length, 0);
    private final Slider shutterSlider = new Slider(0, Shutter.values().length, 0);
    private final Slider isoSlider = new Slider(0, Iso.values().length, 0);
    private final ToggleGroup perspectiveGroup = new ToggleGroup();
    private final ToggleGroup shotGroup = new ToggleGroup();
    private final CameraPreview preview = new CameraPreview();
    private final CameraExample example = new CameraExample();
    private final TextArea promptArea = new TextArea();
    private final Label validationLabel = new Label();
    private final Label helpLabel = new Label();

    /*  shows the example once the mouse leaves a value: the stage changed last, else the empty frame; a change made
        by the dialog itself, such as a reset, is none of the user  */
    private Runnable restingExample;
    private boolean adjusting;

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
        dialogPane.getButtonTypes().add(cancelButtonType);

        /*  Name  */
        nameField.setText(block != null ? block.getName() : "");
        HBox.setHgrow(nameField, Priority.ALWAYS);
        HBox nameRow = new HBox(10, text(new Label(), "block.dialog.name"), nameField);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        /*  Camera type: tiles, "Any" first  */
        FlowPane bodyTiles = new FlowPane(4, 4);
        bodyTiles.getChildren().add(tile(bodyGroup, null, "camera.dialog.any", "mdi2a-asterisk", settings.body()));
        for (Body body : Body.values())
            bodyTiles.getChildren().add(tile(bodyGroup, body, "camera.body." + key(body), BODY_ICONS.get(body),
                    settings.body()));

        /*  Focal length: a slider with detents, the special lenses beside it; a special lens replaces the slider, which
            then stands greyed at "Any", in the box naming the lens; a second click on the lens frees it again  */
        Label focalAny = text(new Label(), "camera.dialog.any");
        focalTrack = detents(focalSlider, FOCAL_LENGTHS, (lens) -> String.valueOf(lens.millimeters()), Map.of(),
                focalAny);
        focalBox = new StackPane(focalTrack);
        List<Control> lensControls = new ArrayList<>(List.of(focalSlider));
        FlowPane specialRow = new FlowPane(4, 4);
        for (Lens lens : SPECIAL_LENSES) {
            ToggleButton button = text(new ToggleButton(), "camera.lens." + key(lens));
            button.setToggleGroup(specialGroup);
            button.setUserData(lens);
            StackPane wrapper = blockable(Lens.class, lens, button);
            hover(button, (event) -> button);
            lensControls.add(button);
            specialRow.getChildren().add(wrapper);
        }

        /*  Aperture, shutter and ISO: sliders with detents, the shutter and ISO with their symbols above  */
        Label apertureAny = text(new Label(), "camera.dialog.any");
        VBox apertureTrack = detents(apertureSlider, List.of(Aperture.values()), Aperture::label, Map.of(),
                apertureAny);
        Label shutterAny = text(new Label(), "camera.dialog.any");
        VBox shutter = new VBox(6, fixable(Shutter.class, "camera.dialog.shutter", List.of(shutterSlider), shutterAny),
                detents(shutterSlider, List.of(Shutter.values()), Shutter::label, SHUTTER_ICONS, shutterAny));
        VBox iso = new VBox(6, heading("camera.dialog.iso"), detents(isoSlider, List.of(Iso.values()),
                (value) -> value.label().substring("ISO ".length()), ISO_ICONS,
                text(new Label(), "camera.dialog.any")));
        HBox.setHgrow(shutter, Priority.ALWAYS);
        iso.setMinWidth(ISO_WIDTH);
        iso.setPrefWidth(ISO_WIDTH);

        /*  Perspective and shot size: rows of symbols  */
        VBox perspective = symbols("camera.dialog.perspective", perspectiveGroup, Perspective.values(),
                (value) -> "camera.perspective." + key(value), PERSPECTIVE_ICONS, settings.perspective());
        VBox shot = symbols("camera.dialog.shot", shotGroup, Shot.values(), (value) -> "camera.shot." + key(value),
                SHOT_ICONS, settings.shot());
        show(settings);

        /*  Left all settings in the order of the prompt, right the preview with the example below it  */
        VBox settingsBox = new VBox(6,
                heading("camera.dialog.type"), bodyTiles,
                fixable(Lens.class, "camera.dialog.lens", lensControls, focalAny), focalBox, specialRow,
                fixable(Aperture.class, "camera.dialog.aperture", List.of(apertureSlider), apertureAny), apertureTrack,
                new HBox(20, shutter, iso), perspective, shot);
        settingsBox.setMinWidth(SETTINGS_WIDTH);
        settingsBox.setPrefWidth(SETTINGS_WIDTH);
        bodyTiles.setPrefWrapLength(SETTINGS_WIDTH);
        specialRow.setPrefWrapLength(SETTINGS_WIDTH);
        VBox previewBox = new VBox(10, preview, example);
        previewBox.setAlignment(Pos.TOP_CENTER);

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

        /*  Every change shows at once in the preview, the example and the prompt; leaving a type releases the
            settings it fixed  */
        restingExample = example::clear;
        for (ToggleGroup group : List.of(bodyGroup, specialGroup, perspectiveGroup, shotGroup))
            group.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null)
                    changed((Enum<?>) newVal.getUserData());
            });
        bodyGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null)
                release(oldVal != null ? (Body) oldVal.getUserData() : null);
            update();
        });
        specialGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> update());
        for (Slider slider : List.of(focalSlider, apertureSlider, shutterSlider, isoSlider))
            slider.valueProperty().addListener((obs, oldVal, newVal) -> update());
        for (ToggleGroup group : List.of(perspectiveGroup, shotGroup))
            group.selectedToggleProperty().addListener((obs, oldVal, newVal) -> update());
        update();

        /*  a closed dialog no longer follows the language, so it and its pictures are released  */
        setOnHidden((event) -> I18nManager.removeChangeListener(this));

        validateName(nameField.getText().trim());
        nameField.textProperty().addListener((obs, oldText, newText) -> validateName(newText.trim()));
        Platform.runLater(nameField::requestFocus);

        setResultConverter((buttonType) -> {
            if (buttonType == null || buttonType == cancelButtonType)
                return null;
            return new Result(buttonType == saveButtonType ? BlockEditDialog.Save.SAVE
                    : BlockEditDialog.Save.SAVE_AS_NEW, nameField.getText().trim(), settings());
        });
    }

    /** The settings as chosen in the dialog. */
    private CameraSettings settings() {
        Lens special = selected(specialGroup, Lens.class);
        Lens focal = stage(focalSlider, FOCAL_LENGTHS);
        return new CameraSettings(selected(bodyGroup, Body.class), special != null ? special : focal,
                stage(apertureSlider, List.of(Aperture.values())), stage(shutterSlider, List.of(Shutter.values())),
                stage(isoSlider, List.of(Iso.values())), selected(perspectiveGroup, Perspective.class),
                selected(shotGroup, Shot.class));
    }

    /*  sets every control to the settings; as the dialog changes them, the example keeps what the user changed  */
    private void show(CameraSettings settings) {
        boolean outer = adjusting;
        adjusting = true;
        select(bodyGroup, settings.body());
        focalSlider.setValue(settings.lens() != null ? FOCAL_LENGTHS.indexOf(settings.lens()) + 1 : 0);
        select(specialGroup, settings.lens() != null && settings.lens().isSpecial() ? settings.lens() : null);
        apertureSlider.setValue(settings.aperture() != null ? settings.aperture().ordinal() + 1 : 0);
        shutterSlider.setValue(settings.shutter() != null ? settings.shutter().ordinal() + 1 : 0);
        isoSlider.setValue(settings.iso() != null ? settings.iso().ordinal() + 1 : 0);
        select(perspectiveGroup, settings.perspective());
        select(shotGroup, settings.shot());
        adjusting = outer;
    }

    /*  the preview and the prompt for a photographic style; a special lens replaces the slider, which stands greyed
        at "Any". A setting the type fixes takes its value, a stage blocked by the type or lens just chosen goes back
        to "Any" first, each change updating anew.  */
    private void update() {
        CameraSettings settings = settings();
        CameraSettings allowed = settings.withoutConflicts();
        Lens special = selected(specialGroup, Lens.class);
        if (!allowed.equals(settings) || special != null && focalSlider.getValue() != 0) {
            show(allowed);
            return;
        }
        for (Blockable<?> blockable : blockables) {
            String reason = reason(settings, blockable);
            blockable.control().setDisable(reason != null);
            if (reason != null) {
                blockable.reason().setText(reason);
                Tooltip.install(blockable.wrapper(), blockable.reason());
            }
            else
                Tooltip.uninstall(blockable.wrapper(), blockable.reason());
        }
        for (Fixable<?> fixable : fixables)
            fix(fixable, settings);
        focalTrack.setDisable(special != null);
        if (special != null) {
            replaced.setText(I18nManager.getText("camera.dialog.replaced", CameraExample.name(special)));
            Tooltip.install(focalBox, replaced);
        }
        else
            Tooltip.uninstall(focalBox, replaced);
        String camera = CameraPromptBuilder.build(settings, true);
        preview.show(settings);
        promptArea.setText(camera.isEmpty() ? I18nManager.getText("camera.dialog.prompt.empty")
                : "Camera: " + camera + ".");
    }

    /*  why a stage cannot be chosen, null if it can: the type fixes its setting to another value, so also "Any" of a
        setting fixed to a value, or the stage contradicts the chosen type or lens  */
    private static <E extends Enum<E>> String reason(CameraSettings settings, Blockable<E> blockable) {
        if (settings.fixes(blockable.setting()) && blockable.stage() != settings.fixed(blockable.setting()))
            return I18nManager.getText("camera.dialog.fixed", CameraExample.name(settings.body()));
        Enum<?> blocker = settings.blocker(blockable.stage());
        return blocker != null ? I18nManager.getText("camera.dialog.blocked", CameraExample.name(blocker)) : null;
    }

    /*  a setting fixed by the type: read-only but not greyed, as it shows the value of the type; "Any" does not exist
        then, it reads "Auto" for an automatic or stands back greyed behind the fixed value  */
    private static <E extends Enum<E>> void fix(Fixable<E> fixable, CameraSettings settings) {
        boolean fixed = settings.fixes(fixable.setting());
        for (Control control : fixable.controls()) {
            control.setMouseTransparent(fixed);
            control.setFocusTraversable(!fixed);
        }
        fixable.hint().setText(fixed ? I18nManager.getText("camera.dialog.fixed", CameraExample.name(settings.body()))
                : "");
        fixable.any().setText(I18nManager.getText(fixed && settings.fixed(fixable.setting()) == null
                ? "camera.dialog.auto" : "camera.dialog.any"));
    }

    /*  leaving a type sets the settings it fixed back to "Any": the type chose them, not the user  */
    private void release(Body previous) {
        CameraSettings left = new CameraSettings(previous, null, null, null, null, null, null);
        CameraSettings settings = settings();
        show(new CameraSettings(settings.body(), left.fixes(Lens.class) ? null : settings.lens(),
                left.fixes(Aperture.class) ? null : settings.aperture(),
                left.fixes(Shutter.class) ? null : settings.shutter(), settings.iso(), settings.perspective(),
                settings.shot()));
    }

    /*  a value the user changed: the example shows it, also later while the mouse is over no value  */
    private void changed(Enum<?> stage) {
        if (adjusting)
            return;
        restingExample = () -> example.show(stage);
        restingExample.run();
    }

    /*  while the mouse is over a control, the example shows the stage under it, kept as its user data, afterwards the
        one changed last; a greyed stage shows none, as it cannot be chosen  */
    private void hover(Node node, Function<MouseEvent, Node> control) {
        node.addEventHandler(MouseEvent.MOUSE_MOVED, (event) -> {
            Node target = control.apply(event);
            if (target.isDisabled())
                restingExample.run();
            else
                example.show((Enum<?>) target.getUserData());
        });
        node.addEventHandler(MouseEvent.MOUSE_EXITED, (event) -> restingExample.run());
    }

    /*  the heading of a setting a camera type can fix, with the hint naming the type beside it  */
    private <E extends Enum<E>> HBox fixable(Class<E> setting, String key, List<Control> controls, Label any) {
        Label hint = new Label();
        hint.getStyleClass().addAll("text-small", "text-muted");
        fixables.add(new Fixable<>(setting, controls, any, hint));
        HBox row = new HBox(8, heading(key), hint);
        row.setAlignment(Pos.BASELINE_LEFT);
        return row;
    }

    /*  a control of a stage that a chosen type or lens can block, wrapped for the reason  */
    private <E extends Enum<E>> StackPane blockable(Class<E> setting, E stage, Control control) {
        StackPane wrapper = new StackPane(control);
        blockables.add(new Blockable<>(setting, stage, control, wrapper, new Tooltip()));
        return wrapper;
    }

    /*  selects the toggle of a value, "Any" for null; a group without "Any", such as the special lenses, none  */
    private static void select(ToggleGroup group, Enum<?> value) {
        group.selectToggle(group.getToggles().stream().filter((toggle) -> toggle.getUserData() == value).findFirst()
                .orElse(null));
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
        hover(tile, (event) -> tile);
        return tile;
    }

    /*  A setting as a row of icon buttons, "Any" first, each named by its tooltip; the heading names the chosen one  */
    private <E extends Enum<E>> VBox symbols(String headingKey, ToggleGroup group, E[] values,
            Function<E, String> key, Map<E, String> icons, E selected) {
        HBox row = new HBox(4);
        Class<E> setting = values[0].getDeclaringClass();
        Map<Toggle, String> names = new IdentityHashMap<>();
        List<E> choices = new ArrayList<>();
        choices.add(null);
        choices.addAll(List.of(values));
        for (E value : choices) {
            String name = value == null ? "camera.dialog.any" : key.apply(value);
            ToggleButton button = new ToggleButton();
            button.setGraphic(icon(value == null ? "mdi2a-asterisk" : icons.get(value), 18));
            button.setPrefSize(38, 34);
            button.getStyleClass().add("icon-button");
            Tooltip tooltip = new Tooltip();
            texts.put(tooltip, name);
            button.setTooltip(tooltip);
            names.put(button, name);
            join(group, button, value, value == selected);
            StackPane wrapper = blockable(setting, value, button);
            hover(button, (event) -> button);
            row.getChildren().add(wrapper);
        }

        Label heading = heading(null);
        Runnable naming = () -> {
            String name = names.get(group.getSelectedToggle());
            heading.setText(I18nManager.getText(headingKey) + (name != null ? ": " + I18nManager.getText(name) : ""));
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
        it, as JavaFX loses the last tick at some widths, and the symbols of the stages small above it; each label
        stays inside the slider. A blocked detent is skipped: a step goes on to the next allowed detent in its
        direction, dragging takes the nearest allowed one.  */
    private <E extends Enum<E>> VBox detents(Slider slider, List<E> stages, Function<E, String> label,
            Map<E, String> icons, Label any) {
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setBlockIncrement(1);

        Class<E> setting = stages.getFirst().getDeclaringClass();
        List<Line> ticks = new ArrayList<>();
        List<Label> names = new ArrayList<>();
        List<Node> labels = new ArrayList<>();
        List<Node> glyphs = new ArrayList<>();
        for (int index = 0; index <= stages.size(); index++) {
            E stage = index > 0 ? stages.get(index - 1) : null;
            Label name = stage != null ? new Label(label.apply(stage)) : any;
            name.getStyleClass().add("text-small");
            name.setUserData(stage);
            names.add(name);
            labels.add(blockable(setting, stage, name));
            Line tick = new Line(0, 1, 0, 5);
            tick.setStyle("-fx-stroke: -color-slider-tick;");

            /*  a greyed track fades its ticks like its labels, which the theme fades to 40 %  */
            tick.opacityProperty().bind(tick.disabledProperty().map((disabled) -> disabled ? 0.4 : 1.0));
            ticks.add(tick);
            if (stage != null && icons.containsKey(stage))
                glyphs.add(icon(icons.get(stage), 13));
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
                    center(labels.get(index), x, 7, getWidth());
                }
            }
        };
        scale.getChildren().addAll(ticks);
        scale.getChildren().addAll(labels);
        scale.setMinHeight(Region.USE_PREF_SIZE);
        Pane marks = new Pane() {
            @Override
            protected double computePrefHeight(double width) {
                return glyphs.getFirst().prefHeight(-1);
            }

            @Override
            protected void layoutChildren() {
                for (int index = 0; index < glyphs.size(); index++)
                    center(glyphs.get(index), Math.round(position(slider, index + 1)) + 0.5, 0, getWidth());
            }
        };
        marks.getChildren().addAll(glyphs);
        marks.setMinHeight(Region.USE_PREF_SIZE);

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

        slider.valueProperty().addListener((obs, oldVal, newVal) -> changed(stage(slider, stages)));

        VBox track = glyphs.isEmpty() ? new VBox(slider, scale) : new VBox(marks, slider, scale);
        hover(track, (event) -> names.get(detent(slider, event)));
        return track;
    }

    /*  the stage at the detent of a slider, null for "Any"  */
    private static <E> E stage(Slider slider, List<E> stages) {
        int index = (int) Math.round(slider.getValue());
        return index > 0 ? stages.get(index - 1) : null;
    }

    /*  a node centered on a detent at the x, kept inside the width  */
    private static void center(Node node, double x, double y, double width) {
        double own = node.prefWidth(-1);
        node.resizeRelocate(Math.clamp(x - own / 2, 0, Math.max(0, width - own)), y, own, node.prefHeight(own));
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
        update();

        if (saveButtonType != null)
            ((Button) getDialogPane().lookupButton(saveButtonType)).setText(I18nManager.getText("block.dialog.save"));
        if (saveAsNewButtonType != null)
            ((Button) getDialogPane().lookupButton(saveAsNewButtonType)).setText(
                    I18nManager.getText("block.dialog.save.new"));
        ((Button) getDialogPane().lookupButton(cancelButtonType)).setText(I18nManager.getText("button.cancel"));
    }

    @Override
    public void onLanguageChanged() {
        Platform.runLater(this::updateTexts);
    }
}
