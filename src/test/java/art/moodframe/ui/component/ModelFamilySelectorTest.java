package art.moodframe.ui.component;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ModelFamilies;
import javafx.application.Platform;
import javafx.scene.control.ChoiceBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Drives the model choice with the target resolution of the frame on the JavaFX thread without showing it. */
class ModelFamilySelectorTest {

    private static final String NANO_BANANA_2_1K = GenerationModel.NANO_BANANA2_1K.getLocalizedDisplayName();
    private static final String NANO_BANANA_2_4K = GenerationModel.NANO_BANANA2_4K.getLocalizedDisplayName();
    private static final String NANO_BANANA_PRO_2K = GenerationModel.GEMINI_PRO_2K.getLocalizedDisplayName();
    private static final String NANO_BANANA_PRO_4K = GenerationModel.GEMINI_PRO_4K.getLocalizedDisplayName();
    private static final String FLUX_2_PRO = GenerationModel.FLUX2_PRO.getLocalizedDisplayName();

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

    /** Runs a step on the JavaFX thread and waits for it and for what it left for later. */
    private static void onFxThread(Runnable step) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(step);

        /*  posted from the queue, so it runs after what the step itself left for later  */
        Platform.runLater(() -> Platform.runLater(done::countDown));
        assertTrue(done.await(10, TimeUnit.SECONDS));
    }

    @Test
    void theFamilyTakesTheVariantClosestToTheTargetOverTheRememberedOne() throws Exception {
        ChoiceBox<String> variants = new ChoiceBox<>();
        ChoiceBox<String> families = new ChoiceBox<>();
        ChoiceBox<String> target = new ChoiceBox<>();
        ApplicationConfig config = mock(ApplicationConfig.class);
        when(config.getProperty(anyString())).thenReturn("1K");
        when(config.getProperty(anyString(), anyString())).thenReturn("1K");

        onFxThread(() -> {
            new ModelFamilySelector(variants, families, target, config);
            target.getItems().setAll(ModelFamilies.RESOLUTIONS);
            variants.getItems().setAll(List.of(NANO_BANANA_2_1K, NANO_BANANA_2_4K, NANO_BANANA_PRO_2K,
                    NANO_BANANA_PRO_4K, FLUX_2_PRO));
            variants.setValue(FLUX_2_PRO);
            target.setValue("4K");
        });
        assertEquals(FLUX_2_PRO, variants.getValue(), "a model without resolution choice keeps its fixed size");

        onFxThread(() -> families.setValue("Nano Banana 2"));
        assertEquals(NANO_BANANA_2_4K, variants.getValue(), "the remembered 1K does not override the target");

        onFxThread(() -> target.setValue("2K"));
        assertEquals(NANO_BANANA_2_1K, variants.getValue(), "2K lies as far from 1K as from 4K: the smaller one");

        onFxThread(() -> families.setValue("Nano Banana Pro"));
        assertEquals(NANO_BANANA_PRO_2K, variants.getValue());

        onFxThread(() -> target.setValue("4K"));
        assertEquals(NANO_BANANA_PRO_4K, variants.getValue());
        verify(config).setProperty(ModelFamilies.resolutionKey("Nano Banana Pro"), "4K");

        onFxThread(() -> {
            target.setValue("1K");
            families.setValue("Nano Banana 2");
        });
        assertEquals(NANO_BANANA_2_1K, variants.getValue());
    }
}
