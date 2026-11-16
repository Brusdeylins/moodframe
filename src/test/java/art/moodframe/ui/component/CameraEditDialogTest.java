package art.moodframe.ui.component;

import art.moodframe.model.CameraSettings;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.PromptBlockService;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Builds the camera dialog on the JavaFX thread without showing it, works its controls and reads its result. */
class CameraEditDialogTest {

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await(10, TimeUnit.SECONDS);
    }

    /** Runs a step on the JavaFX thread and returns its result. */
    private static <T> T onFxThread(Supplier<T> step) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            result.set(step.get());
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
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

    @Test
    void showsTheExampleOfAValueChangedByKeyboardAndKeepsItAfterTheMouseLeaves() throws Exception {
        CameraEditDialog dialog = onFxThread(() -> new CameraEditDialog(null));
        List<Slider> sliders = sliders(dialog);
        Slider shutter = sliders.get(2);
        ImageView picture = (ImageView) dialog.getDialogPane().lookupAll(".image-view").stream()
                .filter((node) -> node.getParent().getParent() instanceof CameraExample).findFirst().orElseThrow();
        assertNull(picture.getImage(), "the empty frame before any change");

        onFxThread(() -> {
            dialog.getDialogPane().applyCss();
            Event.fireEvent(shutter, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT, false, false, false,
                    false));
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
                "back to the value changed last instead of the empty frame");
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
