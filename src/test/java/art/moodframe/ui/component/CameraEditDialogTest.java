package art.moodframe.ui.component;

import art.moodframe.model.CameraSettings;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.PromptBlockService;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
import javafx.scene.shape.Line;
import javafx.stage.Screen;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds the camera dialog on the JavaFX thread, mostly without showing it, works its controls and reads its result.
 */
class CameraEditDialogTest {

    private static final List<CameraSettings.Lens> FOCAL_LENGTHS = Arrays.stream(CameraSettings.Lens.values())
            .filter((lens) -> !lens.isSpecial()).toList();
    private static final List<CameraSettings.Lens> SPECIAL_LENSES = Arrays.stream(CameraSettings.Lens.values())
            .filter(CameraSettings.Lens::isSpecial).toList();

    /*  the dialogs opened by a test: never shown, they keep following the language until released  */
    private static final List<CameraEditDialog> opened = new ArrayList<>();

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }

        /*  closing the one dialog a test shows must not end the toolkit for the tests after it  */
        Platform.setImplicitExit(false);
        started.await(10, TimeUnit.SECONDS);
    }

    /*  the full test run fills the heap of its JVM already, so a dialog of this class leaves nothing behind  */
    @AfterEach
    void releaseDialogs() {
        opened.forEach(I18nManager::removeChangeListener);
        opened.clear();
    }

    /** Opens the dialog for a camera of the user, released after the test. */
    private static CameraEditDialog open(CameraSettings settings) throws Exception {
        CameraEditDialog dialog = onFxThread(() -> new CameraEditDialog(PromptBlockService.camera("mine", "Mine",
                settings)));
        opened.add(dialog);
        return dialog;
    }

    /** Runs a step on the JavaFX thread and returns its result; a failed assertion of the step fails the test. */
    private static <T> T onFxThread(Supplier<T> step) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<RuntimeException> exception = new AtomicReference<>();
        AtomicReference<Error> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(step.get());
            } catch (RuntimeException failure) {
                exception.set(failure);
            } catch (Error failure) {
                error.set(failure);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (error.get() != null)
            throw error.get();
        if (exception.get() != null)
            throw exception.get();
        return result.get();
    }

    @Test
    void showsTheSettingsOfAStandardCameraAndSavesThemOnlyAsANewOne() throws Exception {
        CameraSettings settings = new CameraSettings(CameraSettings.Body.MEDIUM_FORMAT, CameraSettings.Lens.TILT_SHIFT,
                CameraSettings.Aperture.F_4, CameraSettings.Shutter.MOTION_BLUR, CameraSettings.Iso.ISO_3200,
                CameraSettings.Perspective.DUTCH_ANGLE, CameraSettings.Shot.DETAIL);
        PromptBlock tabletop = PromptBlockService.camera("std:camera:product-tabletop", "Product Tabletop", settings);

        CameraEditDialog dialog = onFxThread(() -> new CameraEditDialog(tabletop));
        List<ButtonType> buttons = dialog.getDialogPane().getButtonTypes();
        CameraEditDialog.Result result = onFxThread(() -> dialog.getResultConverter().call(buttons.get(0)));

        assertEquals(2, buttons.size(), "a standard camera has no \"Save\"");
        assertEquals(ButtonBar.ButtonData.OK_DONE, buttons.get(0).getButtonData(), "\"As New Entry\" is the main one");
        assertEquals(BlockEditDialog.Save.SAVE_AS_NEW, result.save());
        assertEquals("Product Tabletop", result.name());
        assertEquals(settings, result.settings(), "every setting reaches its control and comes back");
    }

    @Test
    void startsANewCameraWithEverySettingAtAny() throws Exception {
        CameraEditDialog dialog = onFxThread(() -> new CameraEditDialog(null));
        CameraEditDialog.Result result = onFxThread(() ->
                dialog.getResultConverter().call(dialog.getDialogPane().getButtonTypes().get(0)));

        assertEquals(BlockEditDialog.Save.SAVE, result.save());
        assertEquals(CameraSettings.ANY, result.settings());
    }

    @Test
    void namesTheLensStageLensAndObjektivAsItHoldsTheSpecialLensesToo() throws Exception {
        Locale before = I18nManager.getCurrentLocale();
        try {
            for (Map.Entry<Locale, String> stage : Map.of(Locale.ENGLISH, "Lens", Locale.GERMAN, "Objektiv")
                    .entrySet()) {
                CameraEditDialog dialog = onFxThread(() -> {
                    I18nManager.setLocale(stage.getKey());
                    return new CameraEditDialog(null);
                });

                assertEquals(1, labels(dialog, stage.getValue()), stage.getKey() + ": the heading of the lens stage");
            }
        } finally {
            onFxThread(() -> {
                I18nManager.setLocale(before);
                return null;
            });
        }
    }

    @Test
    void showsShutterAndIsoAsSlidersWithTheirExposureTimesAndNumbers() throws Exception {
        CameraEditDialog dialog = onFxThread(() -> new CameraEditDialog(PromptBlockService.camera("mine", "Mine",
                new CameraSettings(null, null, null, CameraSettings.Shutter.MOTION_BLUR, CameraSettings.Iso.ISO_3200,
                        null, null))));
        List<Slider> sliders = sliders(dialog);
        List<String> labels = dialog.getDialogPane().lookupAll(".label").stream().map(Label.class::cast)
                .map(Label::getText).toList();

        assertEquals(4, sliders.size(), "focal length, aperture, shutter and ISO");
        assertEquals(3.0, sliders.get(2).getValue(), "1/15 s");
        assertEquals(2.0, sliders.get(3).getValue(), "ISO 3200");
        assertTrue(labels.containsAll(List.of("1/4000 s", "1/125 s", "1/15 s", "5 s", "100", "3200")), labels.toString());
        assertTrue(dialog.getDialogPane().lookupAll(".toggle-button").stream()
                .noneMatch((button) -> button.getUserData() instanceof CameraSettings.Shutter
                        || button.getUserData() instanceof CameraSettings.Iso), "no symbol rows for shutter and ISO");
    }

    @Test
    void fixesLensAndApertureOfTheActionCamReadOnlyAndReleasesThemForAnotherType() throws Exception {
        CameraEditDialog dialog = onFxThread(() -> new CameraEditDialog(PromptBlockService.camera("mine", "Mine",
                new CameraSettings(CameraSettings.Body.FULL_FRAME, CameraSettings.Lens.MM_85,
                        CameraSettings.Aperture.F_1_4, null, null, null, null))));
        List<ButtonType> buttons = dialog.getDialogPane().getButtonTypes();
        List<Slider> sliders = sliders(dialog);
        String fixed = I18nManager.getText("camera.dialog.fixed", I18nManager.getText("camera.body.action_cam"));

        onFxThread(() -> {
            toggle(dialog, CameraSettings.Body.ACTION_CAM).fire();
            return null;
        });
        CameraEditDialog.Result result = onFxThread(() -> dialog.getResultConverter().call(buttons.get(1)));

        assertEquals(new CameraSettings(CameraSettings.Body.ACTION_CAM, CameraSettings.Lens.MM_14,
                CameraSettings.Aperture.F_11, null, null, null, null), result.settings(),
                "the type sets its 14 mm lens and its aperture itself");
        assertEquals(1.0, sliders.get(0).getValue());
        assertEquals(3.0, sliders.get(1).getValue());
        for (Slider slider : sliders.subList(0, 2)) {
            assertTrue(slider.isMouseTransparent() && !slider.isFocusTraversable(), "read-only");
            assertFalse(slider.isDisable(), "not greyed: it shows the value of the type");
        }
        assertTrue(toggle(dialog, CameraSettings.Lens.FISHEYE).isMouseTransparent());
        assertEquals(2, labels(dialog, fixed), "beside focal length and aperture");
        assertFalse(sliders.get(2).isMouseTransparent(), "the shutter stays free");

        onFxThread(() -> {
            toggle(dialog, CameraSettings.Body.FULL_FRAME).fire();
            return null;
        });
        assertEquals(new CameraSettings(CameraSettings.Body.FULL_FRAME, null, null, null, null, null, null),
                onFxThread(() -> dialog.getResultConverter().call(buttons.get(1))).settings(),
                "the values of the type go back to \"Any\"");
        assertFalse(sliders.get(0).isMouseTransparent());
        assertEquals(0, labels(dialog, fixed));
    }

    @Test
    void showsTheAutomaticOfTheInstantCameraAsAutoWithoutAny() throws Exception {
        CameraEditDialog dialog = onFxThread(() -> new CameraEditDialog(PromptBlockService.camera("mine", "Mine",
                new CameraSettings(null, null, null, CameraSettings.Shutter.FROZEN, null, null, null))));
        List<ButtonType> buttons = dialog.getDialogPane().getButtonTypes();
        List<Slider> sliders = sliders(dialog);

        onFxThread(() -> {
            toggle(dialog, CameraSettings.Body.INSTANT).fire();
            return null;
        });

        assertEquals(new CameraSettings(CameraSettings.Body.INSTANT, CameraSettings.Lens.MM_50, null, null, null,
                null, null), onFxThread(() -> dialog.getResultConverter().call(buttons.get(1))).settings());
        assertEquals(0.0, sliders.get(2).getValue(), "1/4000 s goes, the shutter is automatic");
        assertTrue(sliders.get(2).isMouseTransparent());
        assertEquals(2, labels(dialog, I18nManager.getText("camera.dialog.auto")), "aperture and shutter");
        assertEquals(2, labels(dialog, I18nManager.getText("camera.dialog.any")), "focal length and ISO");
    }

    @Test
    void blocksWhatContradictsTheTypeAndResetsAChosenStageToAny() throws Exception {
        CameraSettings settings = new CameraSettings(CameraSettings.Body.FULL_FRAME, CameraSettings.Lens.MM_85,
                null, null, null, CameraSettings.Perspective.EYE_LEVEL, CameraSettings.Shot.CLOSE_UP);
        CameraEditDialog dialog = onFxThread(() -> new CameraEditDialog(PromptBlockService.camera("mine", "Mine",
                settings)));
        List<ButtonType> buttons = dialog.getDialogPane().getButtonTypes();
        assertEquals(ButtonBar.ButtonData.LEFT, buttons.get(0).getButtonData(), "next to \"Save\" it stands left");

        onFxThread(() -> {
            toggle(dialog, CameraSettings.Body.DRONE).fire();
            return null;
        });
        CameraEditDialog.Result result = onFxThread(() -> dialog.getResultConverter().call(buttons.get(1)));
        ToggleButton eyeLevel = toggle(dialog, CameraSettings.Perspective.EYE_LEVEL);
        Tooltip reason = (Tooltip) eyeLevel.getParent().getProperties().get("javafx.scene.control.Tooltip");

        assertEquals(new CameraSettings(CameraSettings.Body.DRONE, CameraSettings.Lens.MM_24, null, null, null,
                null, null), result.settings(), "the drone fixes its lens, eye level and close-up go back to \"Any\"");
        assertTrue(eyeLevel.isDisable());
        assertEquals(I18nManager.getText("camera.dialog.blocked", I18nManager.getText("camera.body.drone")),
                reason.getText());
        assertTrue(toggle(dialog, CameraSettings.Lens.MACRO).isDisable());
        assertTrue(toggle(dialog, CameraSettings.Shot.MEDIUM).isDisable());
        assertTrue(toggle(dialog, CameraSettings.Shot.DETAIL).isDisable());
        assertFalse(toggle(dialog, CameraSettings.Perspective.TOP_DOWN).isDisable());
    }

    @Test
    void resetsAPerspectiveBlockedByTheShiftLensAndUpdatesThePrompt() throws Exception {
        CameraEditDialog dialog = onFxThread(() -> new CameraEditDialog(PromptBlockService.camera("mine", "Mine",
                new CameraSettings(CameraSettings.Body.FULL_FRAME, null, null, null, null,
                        CameraSettings.Perspective.LOW_ANGLE, null))));
        List<ButtonType> buttons = dialog.getDialogPane().getButtonTypes();
        TextArea prompt = (TextArea) dialog.getDialogPane().lookup(".text-area");
        assertTrue(prompt.getText().contains("a low angle"));

        onFxThread(() -> {
            toggle(dialog, CameraSettings.Lens.SHIFT).fire();
            return null;
        });
        String perspective = I18nManager.getText("camera.dialog.perspective");

        assertEquals(new CameraSettings(CameraSettings.Body.FULL_FRAME, CameraSettings.Lens.SHIFT, null, null, null,
                null, null), onFxThread(() -> dialog.getResultConverter().call(buttons.get(1))).settings(),
                "the shift lens keeps the camera level");
        assertTrue(toggle(dialog, CameraSettings.Perspective.LOW_ANGLE).isDisable());
        assertTrue(toggle(dialog, CameraSettings.Perspective.DUTCH_ANGLE).isDisable());
        assertFalse(prompt.getText().contains("a low angle"), "the prompt follows the reset");
        assertTrue(prompt.getText().contains("shift lens: the camera level"));
        assertEquals(perspective + ": " + I18nManager.getText("camera.dialog.any"),
                onFxThread(() -> heading(dialog, perspective)));
    }

    @Test
    void skipsABlockedDetentOfASlider() throws Exception {
        CameraEditDialog dialog = onFxThread(() -> new CameraEditDialog(PromptBlockService.camera("phone", "Phone",
                new CameraSettings(CameraSettings.Body.SMARTPHONE, null, null, null, null, null, null))));
        List<Slider> sliders = sliders(dialog);

        assertEquals(3.0, onFxThread(() -> {
            sliders.get(1).setValue(1);
            return sliders.get(1).getValue();
        }), "a step goes on to the next allowed f-number, f/11");
        assertEquals(0.0, onFxThread(() -> {
            sliders.get(1).setValue(1.4);
            return sliders.get(1).getValue();
        }), "dragging takes the nearest allowed detent, \"Any\"");
        assertEquals(6.0, onFxThread(() -> {
            sliders.getFirst().setValue(7);
            return sliders.getFirst().getValue();
        }), "with no allowed focal length after 400 mm, 135 mm is the nearest one before it");
    }

    /** Every camera type, "Any" first, with every special lens, none first. */
    static Stream<Arguments> typesAndSpecialLenses() {
        return Stream.concat(Stream.of((CameraSettings.Body) null), Arrays.stream(CameraSettings.Body.values()))
                .flatMap((body) -> Stream.concat(Stream.of((CameraSettings.Lens) null), SPECIAL_LENSES.stream())
                        .map((lens) -> Arguments.of(body, lens)));
    }

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("typesAndSpecialLenses")
    void showsEverySettingAtItsControlAndGreysExactlyWhatCannotBeChosen(CameraSettings.Body body,
            CameraSettings.Lens special) throws Exception {
        CameraEditDialog dialog = open(new CameraSettings(null, CameraSettings.Lens.MM_85, CameraSettings.Aperture.F_4,
                CameraSettings.Shutter.FROZEN, CameraSettings.Iso.ISO_3200, CameraSettings.Perspective.LOW_ANGLE,
                CameraSettings.Shot.WIDE));
        Slider focal = sliders(dialog).getFirst();

        onFxThread(() -> {
            toggle(dialog, body).fire();
            assertShowsSettings(dialog);
            if (special == null)
                return null;
            ToggleButton lens = toggle(dialog, special);
            lens.fire();
            assertShowsSettings(dialog);
            if (!lens.isSelected()) {
                assertTrue(lens.isDisabled(), "a lens is left unchosen only if it cannot be chosen");
                return null;
            }
            assertEquals(0.0, focal.getValue(), "the focal length goes to \"Any\"");
            assertTrue(focal.isDisabled(), "and is locked");
            focal.setValue(4);
            assertEquals(0.0, focal.getValue(), "a focal length set anyway goes back to \"Any\"");
            assertEquals(special, settings(dialog).lens(), "and keeps the special lens");

            lens.fire();
            assertShowsSettings(dialog);
            assertFalse(lens.isSelected(), "a second click deselects the special lens");
            assertEquals(0.0, focal.getValue(), "the free slider stands at \"Any\"");
            assertFalse(focal.isDisabled());
            return null;
        });
    }

    @Test
    void namesTheReasonOnEveryGreyedControlOfTheLens() throws Exception {
        CameraEditDialog dialog = open(new CameraSettings(CameraSettings.Body.FULL_FRAME, CameraSettings.Lens.MM_85,
                null, null, null, null, null));
        Slider focal = sliders(dialog).getFirst();
        Node focalBox = focal.getParent().getParent();
        String fixed = I18nManager.getText("camera.dialog.fixed", I18nManager.getText("camera.body.action_cam"));

        onFxThread(() -> {
            toggle(dialog, CameraSettings.Lens.FISHEYE).fire();
            return null;
        });
        assertEquals(I18nManager.getText("camera.dialog.replaced", I18nManager.getText("camera.lens.fisheye")),
                reason(focalBox), "the replaced slider names the lens");

        onFxThread(() -> {
            toggle(dialog, CameraSettings.Body.ACTION_CAM).fire();
            return null;
        });
        assertNull(reason(focalBox), "the fixed lens replaces no slider");
        assertFalse(focal.isDisabled(), "the fixed focal length stays readable");
        for (CameraSettings.Lens lens : SPECIAL_LENSES)
            assertEquals(fixed, reason(toggle(dialog, lens).getParent()), lens + " is set by the type");
        assertEquals(fixed, reason(labels(focal).getFirst().getParent()), "\"Any\" is set by the type");
        assertEquals(fixed, reason(labels(focal).get(2).getParent()), "24 mm is set by the type");
        assertNull(reason(labels(focal).get(1).getParent()), "14 mm is the value of the type");
    }

    @Test
    void showsNoExampleForAStageThatCannotBeChosen() throws Exception {
        CameraEditDialog dialog = open(new CameraSettings(CameraSettings.Body.SMARTPHONE, null, null, null, null, null,
                null));
        Slider focal = sliders(dialog).getFirst();
        ImageView picture = (ImageView) dialog.getDialogPane().lookupAll(".image-view").stream()
                .filter((node) -> node.getParent().getParent() instanceof CameraExample).findFirst().orElseThrow();
        onFxThread(() -> {
            DialogPane pane = dialog.getDialogPane();
            pane.applyCss();
            pane.resize(pane.prefWidth(-1), pane.prefHeight(-1));
            pane.layout();
            return null;
        });

        hover(focal, 6);
        assertTrue(picture.getImage().getUrl().endsWith(CameraExample.resource(CameraSettings.Lens.MM_135)),
                "an allowed focal length shows its example");
        hover(focal, 7);
        assertNull(picture.getImage(), "400 mm, blocked by the smartphone, shows none");

        onFxThread(() -> {
            toggle(dialog, CameraSettings.Body.FULL_FRAME).fire();
            toggle(dialog, CameraSettings.Lens.FISHEYE).fire();
            return null;
        });
        hover(focal, 4);
        assertNull(picture.getImage(), "the slider replaced by the fisheye shows no 50 mm, nor the lens chosen last");

        onFxThread(() -> {
            toggle(dialog, CameraSettings.Lens.FISHEYE).fire();
            toggle(dialog, CameraSettings.Body.ACTION_CAM).fire();
            return null;
        });
        hover(focal, 2);
        assertNull(picture.getImage(), "24 mm, not the lens of the action cam, shows none");
        hover(focal, 1);
        assertTrue(picture.getImage().getUrl().endsWith(CameraExample.resource(CameraSettings.Lens.MM_14)),
                "the fixed 14 mm shows its example");
    }

    @Test
    void dragsNoFocalLengthIntoTheSliderASpecialLensReplaced() throws Exception {
        CameraEditDialog dialog = open(new CameraSettings(CameraSettings.Body.FULL_FRAME, null, null, null, null, null,
                null));
        Slider focal = sliders(dialog).getFirst();
        onFxThread(() -> {
            dialog.setX(0);
            dialog.setY(0);
            dialog.setHeight(700);
            dialog.show();
            return null;
        });
        try {
            drag(focal);
            assertTrue(focal.getValue() > 0, "the drag works on the free slider");

            onFxThread(() -> {
                toggle(dialog, CameraSettings.Lens.FISHEYE).fire();
                return null;
            });
            drag(focal);
            assertEquals(0.0, focal.getValue(), "dragging the replaced slider changes nothing");
            assertEquals(CameraSettings.Lens.FISHEYE, onFxThread(() -> settings(dialog)).lens());
        } finally {

            /*  the mouse leaves the dialog first, else the tooltip under it keeps the closed dialog  */
            onFxThread(() -> {
                new Robot().mouseMove(Screen.getPrimary().getBounds().getMaxX() - 1,
                        Screen.getPrimary().getBounds().getMaxY() - 1);
                return null;
            });
            onFxThread(() -> {
                dialog.getDialogPane().getScene().getWindow().hide();
                return null;
            });
        }
    }

    @Test
    void showsTheExampleOfAValueSetByKeyboardUntilItsSliderLosesTheFocus() throws Exception {
        CameraEditDialog dialog = open(CameraSettings.ANY);
        List<Slider> sliders = sliders(dialog);
        Slider shutter = sliders.get(2);
        ImageView picture = (ImageView) dialog.getDialogPane().lookupAll(".image-view").stream()
                .filter((node) -> node.getParent().getParent() instanceof CameraExample).findFirst().orElseThrow();
        assertNull(picture.getImage(), "the empty frame before any change");

        onFxThread(() -> {
            dialog.getDialogPane().applyCss();
            shutter.requestFocus();
            Event.fireEvent(shutter, key(KeyCode.RIGHT));
            return null;
        });
        assertEquals(1.0, shutter.getValue(), "one step to the right: 1/4000 s");
        assertTrue(picture.getImage().getUrl().endsWith(CameraExample.resource(CameraSettings.Shutter.FROZEN)),
                picture.getImage().getUrl());

        ToggleButton topDown = toggle(dialog, CameraSettings.Perspective.TOP_DOWN);
        onFxThread(() -> {
            Event.fireEvent(topDown, mouse(MouseEvent.MOUSE_MOVED));
            return null;
        });
        assertTrue(picture.getImage().getUrl().endsWith(CameraExample.resource(CameraSettings.Perspective.TOP_DOWN)),
                "the value under the mouse");
        onFxThread(() -> {
            Event.fireEvent(topDown, mouse(MouseEvent.MOUSE_EXITED));
            return null;
        });
        assertTrue(picture.getImage().getUrl().endsWith(CameraExample.resource(CameraSettings.Shutter.FROZEN)),
                "back to the value set by keyboard, as the slider keeps the focus");

        onFxThread(() -> {
            Event.fireEvent(shutter, key(KeyCode.TAB));
            return null;
        });
        assertTrue(onFxThread(() -> dialog.getDialogPane().getScene().getFocusOwner() != shutter), "tab moves on");
        assertNull(picture.getImage(), "the empty frame once the slider loses the focus");
    }

    @Test
    void keepsTheExampleOfATileChosenByArrowKeyAsTheFocusMovesOnToIt() throws Exception {
        CameraEditDialog dialog = open(CameraSettings.ANY);
        ToggleButton fullFrame = toggle(dialog, CameraSettings.Body.FULL_FRAME);
        ToggleButton mediumFormat = toggle(dialog, CameraSettings.Body.MEDIUM_FORMAT);
        ImageView picture = (ImageView) dialog.getDialogPane().lookupAll(".image-view").stream()
                .filter((node) -> node.getParent().getParent() instanceof CameraExample).findFirst().orElseThrow();

        onFxThread(() -> {
            dialog.getDialogPane().applyCss();
            fullFrame.requestFocus();
            Event.fireEvent(fullFrame, key(KeyCode.RIGHT));
            return null;
        });
        assertTrue(mediumFormat.isSelected(), "the arrow key chooses the next tile");
        assertTrue(onFxThread(() -> dialog.getDialogPane().getScene().getFocusOwner() == mediumFormat));
        assertTrue(picture.getImage().getUrl().endsWith(CameraExample.resource(CameraSettings.Body.MEDIUM_FORMAT)),
                "the tile chosen by keyboard, which now has the focus");
    }

    @Test
    void clearsTheExampleOnceTheMouseLeavesAClickedTileOrAReleasedSlider() throws Exception {
        CameraEditDialog dialog = open(CameraSettings.ANY);
        ToggleButton tile = toggle(dialog, CameraSettings.Body.FULL_FRAME);
        Slider aperture = sliders(dialog).get(1);
        ImageView picture = (ImageView) dialog.getDialogPane().lookupAll(".image-view").stream()
                .filter((node) -> node.getParent().getParent() instanceof CameraExample).findFirst().orElseThrow();
        onFxThread(() -> {
            dialog.setX(0);
            dialog.setY(0);
            dialog.setHeight(700);
            dialog.show();
            return null;
        });
        Robot robot = onFxThread(Robot::new);
        try {
            onFxThread(() -> {
                Bounds bounds = tile.localToScreen(tile.getLayoutBounds());
                robot.mouseMove(bounds.getCenterX(), bounds.getCenterY());
                robot.mouseClick(MouseButton.PRIMARY);
                return null;
            });
            assertTrue(onFxThread(tile::isSelected));
            assertTrue(picture.getImage().getUrl().endsWith(CameraExample.resource(CameraSettings.Body.FULL_FRAME)),
                    "the clicked tile while the mouse is over it");
            leave(robot);
            assertNull(picture.getImage(), "the empty frame once the mouse leaves the clicked tile");

            Point2D from = onFxThread(() -> {
                Node thumb = aperture.lookup(".thumb");
                Bounds bounds = thumb.localToScreen(thumb.getLayoutBounds());
                return new Point2D(bounds.getCenterX(), bounds.getCenterY());
            });
            onFxThread(() -> {
                robot.mouseMove(from);
                robot.mousePress(MouseButton.PRIMARY);
                robot.mouseMove(from.getX() + 60, from.getY());
                robot.mouseMove(from.getX() + 120, from.getY());
                return null;
            });
            CameraSettings.Aperture dragged = onFxThread(() -> CameraSettings.Aperture.values()[
                    (int) Math.round(aperture.getValue()) - 1]);
            assertTrue(picture.getImage().getUrl().endsWith(CameraExample.resource(dragged)),
                    "the f-number under the thumb while dragging");
            onFxThread(() -> {
                robot.mouseMove(from.getX() + 120, from.getY() + 200);
                return null;
            });
            assertTrue(picture.getImage().getUrl().endsWith(CameraExample.resource(dragged)),
                    "the drag keeps the example below the slider");
            onFxThread(() -> {
                robot.mouseRelease(MouseButton.PRIMARY);
                return null;
            });
            onFxThread(() -> null);
            assertNull(picture.getImage(), "the empty frame once released away from the slider");
        } finally {
            leave(robot);
            onFxThread(() -> {
                dialog.getDialogPane().getScene().getWindow().hide();
                return null;
            });
        }
    }

    @Test
    void drawsTheGroundLineOfTheSideViewAsWideAsTheExampleBelow() throws Exception {
        CameraEditDialog dialog = onFxThread(() -> new CameraEditDialog(null));
        double[] edges = onFxThread(() -> {
            DialogPane pane = dialog.getDialogPane();
            pane.applyCss();
            pane.resize(pane.prefWidth(-1), pane.prefHeight(-1));
            pane.layout();
            Canvas side = pane.lookupAll("Canvas").stream().filter((node) -> node.getParent() instanceof CameraPreview)
                    .map(Canvas.class::cast).toList().getLast();
            Node frame = pane.lookupAll(".image-view").stream()
                    .filter((node) -> node.getParent().getParent() instanceof CameraExample).findFirst().orElseThrow()
                    .getParent();
            Bounds example = frame.localToScene(frame.getLayoutBounds());
            double[] line = groundLine(side);
            return new double[] { side.localToScene(line[0], 0).getX(), side.localToScene(line[1], 0).getX(),
                    example.getMinX(), example.getMaxX() };
        });

        assertEquals(edges[2], edges[0], 1, "the ground line starts at the left edge of the example");
        assertEquals(edges[3], edges[1], 1, "the ground line ends at the right edge of the example");
    }

    private static KeyEvent key(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    /** Moves the mouse of the system off the dialog, so the tooltip under it keeps no closed dialog either. */
    private static void leave(Robot robot) throws Exception {
        onFxThread(() -> {
            robot.mouseMove(Screen.getPrimary().getBounds().getMaxX() - 1,
                    Screen.getPrimary().getBounds().getMaxY() - 1);
            return null;
        });
        onFxThread(() -> null);
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type) {
        return new MouseEvent(type, 5, 5, 5, 5, MouseButton.NONE, 0, false, false, false, false, false, false, false,
                false, false, false, null);
    }

    /** The left and right edge of the ground line of the side view: the row of a canvas with most painted pixels. */
    private static double[] groundLine(Canvas canvas) {
        SnapshotParameters transparent = new SnapshotParameters();
        transparent.setFill(Color.TRANSPARENT);
        PixelReader pixels = canvas.snapshot(transparent, null).getPixelReader();
        int[] ground = new int[0];
        for (int y = 0; y < canvas.getHeight(); y++) {
            int row = y;
            int[] painted = IntStream.range(0, (int) canvas.getWidth())
                    .filter((x) -> pixels.getColor(x, row).getOpacity() > 0).toArray();
            if (painted.length > ground.length)
                ground = painted;
        }
        assertTrue(ground.length > 0, "nothing drawn");
        return new double[] { ground[0], ground[ground.length - 1] + 1 };
    }

    /**
     * Checks every control against the settings of the dialog, on the JavaFX thread: each shows its setting, and a
     * stage is greyed exactly when it is blocked, replaced by a special lens or, as part of a setting the type fixes,
     * cannot be chosen.
     */
    private static void assertShowsSettings(CameraEditDialog dialog) {
        CameraSettings settings = settings(dialog);
        List<Slider> sliders = sliders(dialog);
        boolean replaced = settings.lens() != null && settings.lens().isSpecial();
        assertDetents(sliders.get(0), CameraSettings.Lens.class, FOCAL_LENGTHS, settings.lens(), settings, replaced);
        assertDetents(sliders.get(1), CameraSettings.Aperture.class, List.of(CameraSettings.Aperture.values()),
                settings.aperture(), settings, false);
        assertDetents(sliders.get(2), CameraSettings.Shutter.class, List.of(CameraSettings.Shutter.values()),
                settings.shutter(), settings, false);
        assertDetents(sliders.get(3), CameraSettings.Iso.class, List.of(CameraSettings.Iso.values()), settings.iso(),
                settings, false);
        assertToggles(dialog, CameraSettings.Body.class, settings.body(), settings);
        assertToggles(dialog, CameraSettings.Lens.class, settings.lens(), settings);
        assertToggles(dialog, CameraSettings.Perspective.class, settings.perspective(), settings);
        assertToggles(dialog, CameraSettings.Shot.class, settings.shot(), settings);
    }

    private static <E extends Enum<E>> void assertDetents(Slider slider, Class<E> setting, List<E> stages, E value,
            CameraSettings settings, boolean replaced) {
        String name = setting.getSimpleName();
        assertEquals(value != null ? stages.indexOf(value) + 1 : 0, slider.getValue(),
                name + " stands at " + value);
        assertEquals(replaced, slider.isDisabled(), name + " is greyed while replaced only");
        assertEquals(settings.fixes(setting), slider.isMouseTransparent(), name + " is read-only while fixed");
        List<Label> labels = labels(slider);
        for (int index = 0; index <= stages.size(); index++) {
            E stage = index > 0 ? stages.get(index - 1) : null;
            assertEquals(replaced || greyed(settings, setting, stage), labels.get(index).isDisabled(),
                    name + " " + stage + " greyed");
        }
        for (Line tick : ticks(slider))
            assertEquals(replaced ? 0.4 : 1.0, tick.getOpacity(), name + " tick greyed with its track");
    }

    private static <E extends Enum<E>> void assertToggles(CameraEditDialog dialog, Class<E> setting, E value,
            CameraSettings settings) {
        ToggleGroup group = dialog.getDialogPane().lookupAll(".toggle-button").stream().map(ToggleButton.class::cast)
                .filter((button) -> setting.isInstance(button.getUserData())).findFirst().orElseThrow()
                .getToggleGroup();
        for (Toggle toggle : group.getToggles()) {
            E stage = setting.cast(toggle.getUserData());
            String name = setting.getSimpleName() + " " + stage;
            assertEquals(stage == value, toggle.isSelected(), name + " selected");
            assertEquals(greyed(settings, setting, stage), ((Node) toggle).isDisabled(), name + " greyed");
        }
    }

    /** Whether a stage cannot be chosen: the type fixes its setting to another value, or the type or lens blocks it. */
    private static <E extends Enum<E>> boolean greyed(CameraSettings settings, Class<E> setting, E stage) {
        return settings.fixes(setting) && stage != settings.fixed(setting) || settings.blocker(stage) != null;
    }

    private static CameraSettings settings(CameraEditDialog dialog) {
        return dialog.getResultConverter().call(dialog.getDialogPane().getButtonTypes().getFirst()).settings();
    }

    /** Moves the mouse over a detent of a slider, on the track that shows the example. */
    private static void hover(Slider slider, int detent) throws Exception {
        onFxThread(() -> {
            Line tick = ticks(slider).get(detent);
            Point2D point = tick.localToScene(tick.getStartX(), tick.getEndY());
            Event.fireEvent(slider.getParent(), new MouseEvent(MouseEvent.MOUSE_MOVED, point.getX(), point.getY(),
                    point.getX(), point.getY(), MouseButton.NONE, 0, false, false, false, false, false, false, false,
                    false, false, false, null));
            return null;
        });
    }

    /** Drags the thumb of a shown slider to the right with the mouse of the system, as the user would. */
    private static void drag(Slider slider) throws Exception {
        Robot robot = onFxThread(Robot::new);
        Point2D from = onFxThread(() -> {
            Node thumb = slider.lookup(".thumb");
            Bounds bounds = thumb.localToScreen(thumb.getLayoutBounds());
            return new Point2D(bounds.getCenterX(), bounds.getCenterY());
        });
        onFxThread(() -> {
            robot.mouseMove(from);
            robot.mousePress(MouseButton.PRIMARY);
            return null;
        });
        for (int step = 1; step <= 10; step++) {
            double x = from.getX() + step * 20;
            onFxThread(() -> {
                robot.mouseMove(x, from.getY());
                return null;
            });
        }
        onFxThread(() -> {
            robot.mouseRelease(MouseButton.PRIMARY);
            return null;
        });
        onFxThread(() -> null);
    }

    /** The text of the tooltip naming why the control in a wrapper is greyed, null without one. */
    private static String reason(Node wrapper) {
        Tooltip tooltip = (Tooltip) wrapper.getProperties().get("javafx.scene.control.Tooltip");
        return tooltip != null ? tooltip.getText() : null;
    }

    /** The labels below the detents of a slider, "Any" first. */
    private static List<Label> labels(Slider slider) {
        return scale(slider).getChildren().stream().filter(StackPane.class::isInstance)
                .map((wrapper) -> (Label) ((StackPane) wrapper).getChildren().getFirst()).toList();
    }

    private static List<Line> ticks(Slider slider) {
        return scale(slider).getChildren().stream().filter(Line.class::isInstance).map(Line.class::cast).toList();
    }

    /*  the ticks and labels below a slider, the last part of its track  */
    private static Pane scale(Slider slider) {
        return (Pane) ((VBox) slider.getParent()).getChildren().getLast();
    }

    private static List<Slider> sliders(CameraEditDialog dialog) {
        return dialog.getDialogPane().lookupAll(".slider").stream().map(Slider.class::cast).toList();
    }

    private static ToggleButton toggle(CameraEditDialog dialog, Enum<?> stage) {
        return dialog.getDialogPane().lookupAll(".toggle-button").stream().map(ToggleButton.class::cast)
                .filter((button) -> button.getUserData() == stage).findFirst().orElseThrow();
    }

    private static long labels(CameraEditDialog dialog, String text) {
        return dialog.getDialogPane().lookupAll(".label").stream().map(Label.class::cast)
                .filter((label) -> text.equals(label.getText())).count();
    }

    private static String heading(CameraEditDialog dialog, String setting) {
        return dialog.getDialogPane().lookupAll(".label").stream().map(Label.class::cast).map(Label::getText)
                .filter((text) -> text != null && text.startsWith(setting + ": ")).findFirst().orElseThrow();
    }
}
