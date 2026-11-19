package art.moodframe.ui.component;

import art.moodframe.model.GenerationModel;
import art.moodframe.model.ModelFamilies;
import art.moodframe.service.generation.ModelLabels;
import javafx.application.Platform;
import javafx.scene.control.ChoiceBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives the model choice with the target resolution of the frame on the JavaFX thread without showing it. */
class ModelFamilySelectorTest {

    private static final String NANO_BANANA_2_1K = ModelLabels.displayName(GenerationModel.NANO_BANANA2_1K);
    private static final String NANO_BANANA_2_4K = ModelLabels.displayName(GenerationModel.NANO_BANANA2_4K);
    private static final String NANO_BANANA_PRO_2K = ModelLabels.displayName(GenerationModel.GEMINI_PRO_2K);
    private static final String NANO_BANANA_PRO_4K = ModelLabels.displayName(GenerationModel.GEMINI_PRO_4K);
    private static final String FLUX_2_PRO = ModelLabels.displayName(GenerationModel.FLUX2_PRO);

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
    void theFamilyTakesTheVariantClosestToTheTarget() throws Exception {
        ChoiceBox<String> variants = new ChoiceBox<>();
        ChoiceBox<String> families = new ChoiceBox<>();
        ChoiceBox<String> target = new ChoiceBox<>();

        onFxThread(() -> {
            new ModelFamilySelector(variants, families, target);
            target.getItems().setAll(ModelFamilies.RESOLUTIONS);
            variants.getItems().setAll(List.of(NANO_BANANA_2_1K, NANO_BANANA_2_4K, NANO_BANANA_PRO_2K,
                    NANO_BANANA_PRO_4K, FLUX_2_PRO));
            variants.setValue(FLUX_2_PRO);
            target.setValue("4K");
        });
        assertEquals(FLUX_2_PRO, variants.getValue(), "a model without resolution choice keeps its fixed size");

        onFxThread(() -> families.setValue("Nano Banana 2"));
        assertEquals(NANO_BANANA_2_4K, variants.getValue(), "the family takes the variant of the target");

        onFxThread(() -> target.setValue("2K"));
        assertEquals(NANO_BANANA_2_1K, variants.getValue(), "2K lies as far from 1K as from 4K: the smaller one");

        onFxThread(() -> families.setValue("Nano Banana Pro"));
        assertEquals(NANO_BANANA_PRO_2K, variants.getValue());

        onFxThread(() -> target.setValue("4K"));
        assertEquals(NANO_BANANA_PRO_4K, variants.getValue());

        onFxThread(() -> {
            target.setValue("1K");
            families.setValue("Nano Banana 2");
        });
        assertEquals(NANO_BANANA_2_1K, variants.getValue());
    }

    @Test
    void aVariantSetElsewhereFollowsTheTarget() throws Exception {
        ChoiceBox<String> variants = new ChoiceBox<>();
        ChoiceBox<String> families = new ChoiceBox<>();
        ChoiceBox<String> target = new ChoiceBox<>();
        List<String> seen = new java.util.ArrayList<>();

        onFxThread(() -> {
            new ModelFamilySelector(variants, families, target);
            variants.valueProperty().addListener((obs, old, item) -> seen.add(item));
            target.getItems().setAll(ModelFamilies.RESOLUTIONS);
            variants.getItems().setAll(List.of(NANO_BANANA_2_1K, NANO_BANANA_2_4K, NANO_BANANA_PRO_2K,
                    NANO_BANANA_PRO_4K, FLUX_2_PRO));
            target.setValue("4K");
            variants.setValue(NANO_BANANA_PRO_2K);
        });
        assertEquals(NANO_BANANA_PRO_4K, variants.getValue(), "for example a reused entry with the target unchanged");
        assertEquals(NANO_BANANA_PRO_4K, seen.get(seen.size() - 1), "a later listener ends with the 4K variant");
        assertEquals("Nano Banana Pro", families.getValue());

        onFxThread(() -> families.setValue("Nano Banana Pro"));
        assertEquals(NANO_BANANA_PRO_4K, variants.getValue(), "choosing the same family again keeps the 4K variant");

        onFxThread(() -> variants.setValue(FLUX_2_PRO));
        assertEquals(FLUX_2_PRO, variants.getValue(), "a model without resolution choice keeps its fixed size");
    }

    @Test
    void aRebuiltListTakesTheVariantClosestToTheTarget() throws Exception {
        ChoiceBox<String> variants = new ChoiceBox<>();
        ChoiceBox<String> families = new ChoiceBox<>();
        ChoiceBox<String> target = new ChoiceBox<>();

        onFxThread(() -> {
            new ModelFamilySelector(variants, families, target);
            target.getItems().setAll(ModelFamilies.RESOLUTIONS);
            variants.getItems().setAll(List.of(NANO_BANANA_PRO_2K, NANO_BANANA_PRO_4K, FLUX_2_PRO));
            target.setValue("4K");
        });

        /*  like the attachment filter: the list is rebuilt and the 2K variant restored before the 4K one is back  */
        onFxThread(() -> {
            variants.getItems().clear();
            variants.getItems().add(NANO_BANANA_PRO_2K);
            variants.setValue(NANO_BANANA_PRO_2K);
            variants.getItems().addAll(NANO_BANANA_PRO_4K, FLUX_2_PRO);
        });
        assertEquals(NANO_BANANA_PRO_4K, variants.getValue());

        onFxThread(() -> target.setValue("2K"));
        assertEquals(NANO_BANANA_PRO_2K, variants.getValue());

        onFxThread(() -> target.setValue(null));
        onFxThread(() -> variants.setValue(NANO_BANANA_PRO_4K));
        assertEquals(NANO_BANANA_PRO_4K, variants.getValue(), "without target the variant stays as set");
    }
}
