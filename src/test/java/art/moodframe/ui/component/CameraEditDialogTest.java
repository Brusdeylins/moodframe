package art.moodframe.ui.component;

import art.moodframe.model.CameraSettings;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.PromptBlockService;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                CameraSettings.Lens.TILT_SHIFT, CameraSettings.Aperture.F_16, CameraSettings.Shutter.MOTION_BLUR,
                CameraSettings.Iso.ISO_3200, CameraSettings.Perspective.DUTCH_ANGLE, CameraSettings.Shot.DETAIL);
        PromptBlock tabletop = PromptBlockService.camera("std:camera:product-tabletop", "Product Tabletop", settings);

        CameraEditDialog dialog = onFxThread(() -> new CameraEditDialog(tabletop));
        List<ButtonType> buttons = dialog.getDialogPane().getButtonTypes();
        CameraEditDialog.Result result = onFxThread(() -> dialog.getResultConverter().call(buttons.get(0)));

        assertEquals(2, buttons.size(), "a standard camera has no \"Save\"");
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
}
