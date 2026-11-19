package art.moodframe.ui.controller;

import art.moodframe.model.GenerationModel;
import art.moodframe.service.chat.ImageAttachmentService;
import art.moodframe.ui.support.StatusSink;
import javafx.application.Platform;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The edit pencil attaches an image: the generation panel hears of it once, the first image (told by the panel) as one
 * that replaces it.
 */
class GenerationAttachmentsTest {

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
    }

    @Test
    void anImageIsAttachedOnce(@TempDir Path dir) throws Exception {
        List<String> heard = new CopyOnWriteArrayList<>();
        GenerationAttachments attachments = onFx(() -> {
            GenerationAttachments part = new GenerationAttachments(new VBox(), () -> GenerationModel.FLUX2_PRO);
            part.setUp(new ImageAttachmentService(), new StatusSink() {
                @Override
                public void status(String message) {
                }

                @Override
                public void error(String message) {
                }
            }, new GenerationAttachments.Host() {
                @Override
                public void imageAttached() {
                    heard.add("attached");
                }

                @Override
                public void attachmentChanged(boolean hasAttachment, File firstImage) {
                    heard.add("changed");
                }

                @Override
                public void imagesChanged(int imageCount) {
                }

                @Override
                public void moodFromImage(File image) {
                }
            });
            return part;
        });

        onFx(() -> attachments.attach(image(dir, "first.png")));
        settle(attachments, "first.png");
        assertEquals(List.of("changed", "attached"), heard, "the first image");

        heard.clear();
        onFx(() -> attachments.attach(image(dir, "second.png")));
        settle(attachments, "second.png");
        assertEquals(List.of("attached", "changed"), heard, "an image that replaces the only one");
        onFx(() -> {
            attachments.dispose();
            return null;
        });
    }

    /*  waits until the image is the first one and the JavaFX thread ran what it started  */
    private static void settle(GenerationAttachments attachments, String name) throws Exception {
        long until = System.currentTimeMillis() + 10_000;
        while (!name.equals(onFx(() -> nameOf(attachments.first()))) && System.currentTimeMillis() < until)
            Thread.sleep(20);
        assertEquals(name, onFx(() -> nameOf(attachments.first())));
        for (int pass = 0; pass < 5; pass++)
            onFx(() -> null);
    }

    private static String nameOf(File file) {
        return file != null ? file.getName() : null;
    }

    private static File image(Path dir, String name) throws Exception {
        File file = dir.resolve(name).toFile();
        ImageIO.write(new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB), "png", file);
        return file;
    }

    private static <T> T onFx(Callable<T> step) throws Exception {
        FutureTask<T> task = new FutureTask<>(step);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }
}
