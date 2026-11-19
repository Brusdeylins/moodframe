package art.moodframe.ui.controller;

import art.moodframe.database.TestDatabase;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.SavedImage;
import art.moodframe.service.chat.ImageAttachmentService;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.library.SavedImageService;
import art.moodframe.service.library.TestLibraryServices;
import art.moodframe.ui.component.SavedImageChoice;
import art.moodframe.ui.support.StatusSink;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kordamp.ikonli.javafx.FontIcon;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The saved images at the attached images, off-screen: the bookmark of an attached image saves it in the dialog, the
 * choice "Saved" attaches a saved image into the next free square within the limit of the model, marks it and removes
 * it again, and "Reuse" attaches the saved images of an entry again although the file first attached is gone.
 */
class SavedImageAttachmentsTest {

    @TempDir
    static Path tempDir;

    private static SavedImageService savedImages;
    private static final List<String> failures = new CopyOnWriteArrayList<>();

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
        TestDatabase.open(tempDir.resolve("db").resolve("attachments.db").toString());
        savedImages = TestLibraryServices.savedImages(tempDir.resolve("library-images"), new LibraryEvents());
    }

    @AfterAll
    static void closeWindows() throws Exception {
        onFx(() -> {
            for (Window window : List.copyOf(Window.getWindows()))
                window.hide();
            return null;
        });
    }

    @Test
    void savesChoosesAndReattachesSavedImages() throws Exception {
        GenerationAttachments attachments = onFx(() -> {
            GenerationAttachments part = new GenerationAttachments(new VBox(), () -> GenerationModel.FLUX2_PRO);
            part.setUp(new ImageAttachmentService(), new StatusSink() {
                @Override
                public void status(String message) {
                }

                @Override
                public void error(String message) {
                    failures.add(message);
                }
            }, new Host());
            part.useSavedImages(savedImages, Runnable::run, null);
            return part;
        });

        /*  The bookmark of an attached image opens the dialog; saved, the image shows its name  */
        File logo = image("acme-logo.png", Color.RED);
        onFx(() -> attachments.attach(logo));
        waitFor(() -> onFxQuietly(() -> attachments.panel().getAttachmentCount() == 1));
        Thread answer = answerSaveDialog("ACME Logo");
        onFx(() -> {
            bookmark(attachments).fire();
            return null;
        });
        answer.join(15_000);
        waitFor(() -> onFxQuietly(() -> !attachments.savedImageIds().isEmpty()));
        SavedImage acme = savedImages.find("ACME Logo");
        assertNotNull(acme, "the dialog saved the image");
        assertEquals(List.of(acme.id()), onFx(attachments::savedImageIds));
        Label chip = onFx(() -> (Label) attachments.panel().lookup(".saved-image-chip"));
        assertTrue(onFx(chip::isVisible));
        assertEquals("ACME Logo", onFx(chip::getText));

        /*  "Saved" attaches another one into the next free square, checks the attached ones and removes them again  */
        SavedImage word = savedImages.save(image("word-mark.png", Color.BLUE).toPath(), "Word Mark", null).image();
        SavedImageChoice choice = onFx(attachments::savedImageChoice);
        onFx(() -> {
            choice.load();
            return null;
        });
        assertEquals(List.of("ACME Logo", "Word Mark"), onFx(() -> shown(choice).stream().map(SavedImage::name)
                .toList()));
        choose(choice, word);
        waitFor(() -> onFxQuietly(() -> attachments.savedImageIds().size() == 2));
        assertEquals(List.of(acme.id(), word.id()), onFx(attachments::savedImageIds));
        assertEquals(Set.of(acme.id(), word.id()), checked(choice), "both are checked");
        choose(choice, acme);
        waitFor(() -> onFxQuietly(() -> attachments.savedImageIds().size() == 1));
        assertEquals(List.of(word.id()), onFx(attachments::savedImageIds));
        assertEquals(1, (int) onFx(() -> attachments.panel().getAttachmentCount()), "its square went with it");
        assertEquals(Set.of(word.id()), checked(choice));

        /*  A model that takes one image takes one saved image; another one is refused  */
        onFx(() -> {
            attachments.clearAll();
            attachments.setModel(GenerationModel.OMNI_720P);
            return null;
        });
        onFx(() -> null);
        choose(choice, word);
        waitFor(() -> onFxQuietly(() -> attachments.savedImageIds().size() == 1));
        choose(choice, acme);
        onFx(() -> null);
        assertEquals(List.of(word.id()), onFx(attachments::savedImageIds));
        assertTrue(failures.stream().anyMatch((String failure) -> failure.contains("(1 ")), failures::toString);

        /*  "Reuse" attaches the saved images of the entry again, from the library: the first file is gone  */
        onFx(() -> {
            attachments.setModel(GenerationModel.FLUX2_PRO);
            attachments.clearAll();
            return null;
        });
        Files.delete(logo.toPath());
        int missing = onFx(() -> attachments.reattach(List.of(acme.id(), word.id(), "gone")));
        assertEquals(1, missing, "an image no longer in use is counted");
        waitFor(() -> onFxQuietly(() -> attachments.savedImageIds().size() == 2));
        assertEquals(List.of(acme.id(), word.id()), onFx(attachments::savedImageIds));
        onFx(() -> {
            attachments.dispose();
            return null;
        });
    }

    /*  the bookmark of the first square that holds an image not saved yet  */
    private static Button bookmark(GenerationAttachments attachments) {
        for (Node node : attachments.panel().lookupAll(".icon-button"))
            if (node instanceof Button button && button.getGraphic() instanceof FontIcon icon
                    && icon.getIconLiteral().equals("mdi2b-bookmark-plus-outline") && button.isVisible())
                return button;
        throw new AssertionError("no bookmark");
    }

    /*  answers the dialog that saves an image once it is up: types the name and saves  */
    private static Thread answerSaveDialog(String name) {
        Thread answer = new Thread(() -> {
            long until = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < until) {
                boolean done = onFxQuietly(() -> {
                    for (Window window : List.copyOf(Window.getWindows())) {
                        Node pane = window.isShowing() && window.getScene() != null
                                ? window.getScene().getRoot().lookup(".dialog-pane") : null;
                        if (pane instanceof DialogPane dialog) {
                            ((TextField) dialog.lookup(".text-field")).setText(name);
                            ((Button) dialog.lookupButton(dialog.getButtonTypes().get(0))).fire();
                            return true;
                        }
                    }
                    return false;
                });
                if (done)
                    return;
                sleep(50);
            }
        });
        answer.setDaemon(true);
        answer.start();
        return answer;
    }

    @SuppressWarnings("unchecked")
    private static List<SavedImage> shown(SavedImageChoice choice) throws Exception {
        Method shown = SavedImageChoice.class.getDeclaredMethod("shown");
        shown.setAccessible(true);
        return (List<SavedImage>) shown.invoke(choice);
    }

    /*  the saved images the choice checks once opened again  */
    @SuppressWarnings("unchecked")
    private static Set<String> checked(SavedImageChoice choice) throws Exception {
        Field attached = SavedImageChoice.class.getDeclaredField("attached");
        attached.setAccessible(true);
        return onFx(() -> {
            choice.load();
            return (Set<String>) attached.get(choice);
        });
    }

    /*  a click on an image of the choice  */
    private static void choose(SavedImageChoice choice, SavedImage image) throws Exception {
        Method choose = SavedImageChoice.class.getDeclaredMethod("choose", SavedImage.class);
        choose.setAccessible(true);
        onFx(() -> choose.invoke(choice, image));
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

    private static void waitFor(BooleanSupplier condition) {
        long until = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < until)
            sleep(20);
        assertTrue(condition.getAsBoolean(), "within 10 seconds");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean onFxQuietly(Callable<Boolean> step) {
        try {
            return Boolean.TRUE.equals(onFx(step));
        }
        catch (Exception e) {
            return false;
        }
    }

    private static <T> T onFx(Callable<T> step) throws Exception {
        FutureTask<T> task = new FutureTask<>(step);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }

    /*  the generation panel, which the test does not watch  */
    private static class Host implements GenerationAttachments.Host {

        @Override
        public void imageAttached() {
        }

        @Override
        public void attachmentChanged(boolean hasAttachment, File firstImage) {
        }

        @Override
        public void imagesChanged(int imageCount) {
        }

        @Override
        public void moodFromImage(File image) {
        }
    }
}
