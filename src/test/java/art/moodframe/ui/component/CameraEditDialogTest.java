package art.moodframe.ui.component;

import art.moodframe.model.CameraSettings;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.PromptBlockService;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Builds the camera dialog on the JavaFX thread without showing it and reads its result. */
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
        CameraSettings settings = new CameraSettings(CameraSettings.Body.MEDIUM_FORMAT, "Hasselblad 500C/M",
                CameraSettings.Lens.TILT_SHIFT, CameraSettings.Aperture.F_4, CameraSettings.Shutter.MOTION_BLUR,
                CameraSettings.Iso.ISO_3200, CameraSettings.Perspective.DUTCH_ANGLE, CameraSettings.Shot.DETAIL);
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
    void showsTheIsoValuesAsIconButtonsLikeTheOtherRows() throws Exception {
        CameraEditDialog dialog = onFxThread(() -> new CameraEditDialog(null));
        ToggleButton iso3200 = toggle(dialog, CameraSettings.Iso.ISO_3200);
        String iso = I18nManager.getText("camera.dialog.iso");
        String any = onFxThread(() -> heading(dialog, iso));
        String chosen = onFxThread(() -> {
            iso3200.fire();
            return heading(dialog, iso);
        });

        assertTrue(iso3200.getToggleGroup().getToggles().stream().map(ToggleButton.class::cast)
                .allMatch((button) -> button.getStyleClass().contains("icon-button") && button.getGraphic() != null),
                "\"Any\" and both ISO values are icon buttons");
        assertEquals("ISO 3200", iso3200.getTooltip().getText());
        assertEquals(iso + ": " + I18nManager.getText("camera.dialog.any"), any);
        assertEquals(iso + ": 3200", chosen);
    }

    @Test
    void blocksWhatContradictsTheTypeAndResetsAChosenStageToAny() throws Exception {
        CameraSettings settings = new CameraSettings(CameraSettings.Body.FULL_FRAME, null, CameraSettings.Lens.MM_85,
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

        assertEquals(new CameraSettings(CameraSettings.Body.DRONE, null, CameraSettings.Lens.MM_85, null, null, null,
                null, null), result.settings(), "eye level and close-up go back to \"Any\"");
        assertTrue(eyeLevel.isDisable());
        assertEquals(I18nManager.getText("camera.dialog.blocked", I18nManager.getText("camera.body.drone")),
                reason.getText());
        assertTrue(toggle(dialog, CameraSettings.Lens.MACRO).isDisable());
        assertTrue(toggle(dialog, CameraSettings.Shot.DETAIL).isDisable());
        assertFalse(toggle(dialog, CameraSettings.Perspective.TOP_DOWN).isDisable());
        assertFalse(toggle(dialog, CameraSettings.Lens.FISHEYE).isDisable());
    }

    @Test
    void skipsABlockedDetentOfASlider() throws Exception {
        CameraEditDialog dialog = onFxThread(() -> new CameraEditDialog(PromptBlockService.camera("phone", "Phone",
                new CameraSettings(CameraSettings.Body.SMARTPHONE, null, null, null, null, null, null, null))));
        List<Slider> sliders = dialog.getDialogPane().lookupAll(".slider").stream().map(Slider.class::cast).toList();

        assertEquals(3.0, onFxThread(() -> {
            sliders.get(1).setValue(1);
            return sliders.get(1).getValue();
        }), "a step goes on to the next allowed f-number, f/11");
        assertEquals(0.0, onFxThread(() -> {
            sliders.get(1).setValue(1.4);
            return sliders.get(1).getValue();
        }), "dragging takes the nearest allowed detent, \"Any\"");
        assertEquals(1.0, onFxThread(() -> {
            toggle(dialog, CameraSettings.Body.ACTION_CAM).fire();
            sliders.getFirst().setValue(5);
            return sliders.getFirst().getValue();
        }), "with no allowed focal length after it, 14 mm is the nearest one before it");
    }

    private static ToggleButton toggle(CameraEditDialog dialog, Enum<?> stage) {
        return dialog.getDialogPane().lookupAll(".toggle-button").stream().map(ToggleButton.class::cast)
                .filter((button) -> button.getUserData() == stage).findFirst().orElseThrow();
    }

    private static String heading(CameraEditDialog dialog, String setting) {
        return dialog.getDialogPane().lookupAll(".label").stream().map(Label.class::cast).map(Label::getText)
                .filter((text) -> text != null && text.startsWith(setting + ": ")).findFirst().orElseThrow();
    }
}
