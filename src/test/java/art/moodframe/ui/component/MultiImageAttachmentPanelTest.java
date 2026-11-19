package art.moodframe.ui.component;

import art.moodframe.model.AttachedImage;
import art.moodframe.model.GenerationModel;
import art.moodframe.service.chat.ImageAttachmentService;
import javafx.application.Platform;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The attached images move up when one goes, off-screen: removing the first image leaves no empty slot before the
 * others, a smaller limit drops only the images behind it, and a saved image keeps its name and bookmark as it moves.
 */
class MultiImageAttachmentPanelTest {

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        }
        catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    void removingTheFirstImageMovesTheOthersUp() throws Exception {
        MultiImageAttachmentPanel panel = onFx(() -> {
            MultiImageAttachmentPanel created = new MultiImageAttachmentPanel();
            created.setAttachmentService(new ImageAttachmentService());
            created.setOnSaveImage((AttachedImage attachment) -> { });
            created.setSavedImageNames((String id) -> id.equals("logo-id") ? "ACME Logo" : null);
            created.setCurrentModel(GenerationModel.FLUX2_PRO);
            return created;
        });
        File one = image("one.png", Color.RED);
        File two = image("two.png", Color.GREEN);
        File three = image("three.png", Color.BLUE);
        attach(panel, one, null);
        attach(panel, two, "logo-id");
        attach(panel, three, null);
        assertEquals(List.of(one, two, three), onFx(() -> files(panel)));

        /*  Image 1 goes with its slot: the others sit on slot 1 and 2, no empty slot before them  */
        onFx(() -> {
            panel.removeSlot(panel.imageSlots.get(0));
            return null;
        });
        assertEquals(List.of(two, three), onFx(() -> files(panel)));
        assertEquals(2, (int) onFx(() -> panel.imageSlots.size()));
        assertTrue(onFx(() -> panel.imageSlots.stream().allMatch(ImageSlot::hasImage)), "no empty slot before them");
        assertEquals("logo-id", onFx(() -> panel.imageSlots.get(0).attachment().getSavedImageId()));
        assertEquals("ACME Logo", onFx(() -> chip(panel.imageSlots.get(0)).getText()));
        assertTrue(onFx(() -> chip(panel.imageSlots.get(0)).isVisible()), "the saved image keeps its chip");
        assertFalse(onFx(() -> chip(panel.imageSlots.get(1)).isVisible()));

        /*  A limit of 2 keeps both, a model of one image keeps the first one with its chip  */
        onFx(() -> {
            panel.setMaxImages(2);
            return null;
        });
        assertEquals(List.of(two, three), onFx(() -> files(panel)));
        onFx(() -> {
            panel.setCurrentModel(GenerationModel.OMNI_720P);
            return null;
        });
        onFx(() -> null);
        assertEquals(List.of(two), onFx(() -> files(panel)));
        assertEquals("ACME Logo", onFx(() -> chip(panel.imageSlots.get(0)).getText()));

        /*  The last image gone, one empty slot stays  */
        onFx(() -> {
            panel.removeSlot(panel.imageSlots.get(0));
            return null;
        });
        assertEquals(List.of(), onFx(() -> files(panel)));
        assertEquals(1, (int) onFx(() -> panel.imageSlots.size()));
        assertFalse(onFx(() -> panel.imageSlots.get(0).hasImage()));
        onFx(() -> {
            panel.dispose();
            return null;
        });
    }

    /*  attaches an image into the next free slot and waits until it is in  */
    private static void attach(MultiImageAttachmentPanel panel, File file, String savedImageId) throws Exception {
        CompletableFuture<Boolean> attached = onFx(() -> panel.attachSavedImage(file, savedImageId));
        assertTrue(attached.get(20, TimeUnit.SECONDS), "attached: " + file.getName());
    }

    private static List<File> files(MultiImageAttachmentPanel panel) {
        return panel.getAttachedImages().stream().map(AttachedImage::getImageFile).toList();
    }

    private static Label chip(ImageSlot slot) {
        return (Label) slot.lookup(".saved-image-chip");
    }

    private static File image(String name, Color color) throws Exception {
        File file = tempDir.resolve(name).toFile();
        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 40; x++)
            for (int y = 0; y < 20; y++)
                image.setRGB(x, y, color.getRGB());
        ImageIO.write(image, "png", file);
        return file;
    }

    private static <T> T onFx(Callable<T> step) throws Exception {
        FutureTask<T> task = new FutureTask<>(step);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }
}
