package art.moodframe.ui.dialog;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.BrainstormContext;
import art.moodframe.service.MoodAnalysisService;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Builds the dialog on the JavaFX thread without showing it and adopts what it found in an image. */
class MoodImageDialogTest {

    @TempDir
    static Path folder;

    private static File imageFile;

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        }
        catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await(10, TimeUnit.SECONDS);

        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++)
                image.setRGB(x, y, 0x204060);
        }
        imageFile = folder.resolve("image.png").toFile();
        ImageIO.write(image, "png", imageFile);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(action.call());
            }
            catch (Exception e) {
                failure.set(e);
            }
            finally {
                done.countDown();
            }
        });
        assertTrue(done.await(20, TimeUnit.SECONDS), "the JavaFX thread did not answer in time");
        if (failure.get() != null)
            throw failure.get();
        return result.get();
    }

    /** Builds the dialog for the image, waits for the analysis without language model and adopts the result. */
    private static MoodImageDialog.Result adopt(Set<Mood.Field> locked) throws Exception {
        BrainstormContext context = new BrainstormContext(
                List.of(new PromptBlock("std:style:pixel-art", PromptBlock.Type.STYLE, "Pixel Art", "retro pixel art",
                        PromptBlock.GROUP_ART, false)),
                List.of(), List.of(), List.of(), List.of(), List.of(), "", List.of(), locked);
        MoodAnalysisService service = new MoodAnalysisService(null, null, mock(ApplicationConfig.class));
        AtomicReference<MoodImageDialog.Result> adopted = new AtomicReference<>();
        MoodImageDialog dialog = onFx(() -> new MoodImageDialog(null, service, context, imageFile, adopted::set));

        Button apply = onFx(() -> (Button) dialog.getScene().lookup(".accent"));
        for (int i = 0; i < 100 && onFx(apply::isDisabled); i++)
            Thread.sleep(50);
        onFx(() -> {
            apply.fire();
            return null;
        });
        return adopted.get();
    }

    @Test
    void adoptsTheColorsOfTheImageAsNewPalette() throws Exception {
        MoodImageDialog.Result result = adopt(Set.of());

        assertEquals(Set.of(Mood.Field.COLOR), result.blocks().keySet());
        assertEquals(List.of("#204060"), result.blocks().get(Mood.Field.COLOR).colors());
        assertEquals(I18nManager.getText("mood.image.palette"), result.blocks().get(Mood.Field.COLOR).name());
        assertTrue(result.blocks().get(Mood.Field.COLOR).isNew());
    }

    @Test
    void aLockedFieldIsNotAdopted() throws Exception {
        assertEquals(Set.of(), adopt(Set.of(Mood.Field.COLOR)).blocks().keySet());
    }
}
