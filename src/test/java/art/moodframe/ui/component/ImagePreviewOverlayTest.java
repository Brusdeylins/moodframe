package art.moodframe.ui.component;

import art.moodframe.util.I18nManager;
import art.moodframe.util.MediaSupport;
import art.moodframe.util.SystemOpen;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The full view of a video which JavaFX cannot play, here an unreadable one: a hint and the video player of the
 * system instead of an empty area and play/pause; closing and paging as before. Against a throwaway
 * {@code user.home}.
 */
class ImagePreviewOverlayTest {

    private final Logger appLogger = (Logger) LoggerFactory.getLogger("art.moodframe");
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final List<Throwable> uncaught = new CopyOnWriteArrayList<>();
    private String home;

    @TempDir
    Path dir;

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

    @BeforeEach
    void setUp() throws Exception {
        home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectory(dir.resolve("home")).toString());
        forgetMediaSupport();
        logs.start();
        appLogger.addAppender(logs);
        onFxThread(() -> {
            Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> uncaught.add(error));
            return null;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        onFxThread(() -> {
            Thread.currentThread().setUncaughtExceptionHandler(null);
            return null;
        });
        appLogger.detachAppender(logs);
        forgetMediaSupport();
        System.setProperty("user.home", home);
    }

    /*  An unusable JavaFX Media is remembered for the whole session, which the tests must not leave behind  */
    private static void forgetMediaSupport() throws Exception {
        Method reset = MediaSupport.class.getDeclaredMethod("reset");
        reset.setAccessible(true);
        reset.invoke(null);
    }

    /*  Runs a step on the FX thread, and hands a failed assertion of it to the test  */
    private static <T> T onFxThread(Supplier<T> step) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(step.get());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() instanceof Error error)
            throw error;
        if (failure.get() != null)
            throw new IllegalStateException(failure.get());
        return result.get();
    }

    private static <T extends Node> List<T> find(Parent root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Node child : root.getChildrenUnmodifiable()) {
            if (type.isInstance(child))
                found.add(type.cast(child));
            if (child instanceof Parent parent)
                found.addAll(find(parent, type));
        }
        return found;
    }

    /*  Shown on the screen: the node and all its parents are visible  */
    private static boolean shown(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible())
                return false;
        }
        return true;
    }

    private static Label hint(ImagePreviewOverlay overlay) {
        return find(overlay, Label.class).stream()
                .filter((label) -> label.getText() != null
                        && label.getText().startsWith(I18nManager.getText("video.fallback.hint")))
                .findFirst().orElse(null);
    }

    private static Button openButton(ImagePreviewOverlay overlay) {
        return find(overlay, Button.class).stream()
                .filter((button) -> I18nManager.getText("video.fallback.open").equals(button.getText()))
                .findFirst().orElse(null);
    }

    private static Button playPauseButton(ImagePreviewOverlay overlay) {
        return find(overlay, Button.class).stream()
                .filter((button) -> button.getGraphic() instanceof FontIcon icon
                        && icon.getIconLiteral().matches("mdi2p-(play|pause)"))
                .findFirst().orElse(null);
    }

    private static void press(ImagePreviewOverlay overlay, KeyCode key) {
        overlay.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, false, false, false, false));
    }

    /*  Waits until the fallback of the video is shown, as JavaFX may report the error only while opening it  */
    private static Label awaitHint(ImagePreviewOverlay overlay) throws Exception {
        for (int i = 0; i < 200; i++) {
            Label hint = onFxThread(() -> hint(overlay));
            if (hint != null && onFxThread(() -> shown(hint)))
                return hint;
            Thread.sleep(100);
        }
        return null;
    }

    @Test
    void aVideoJavaFxCannotPlayShowsHintAndVideoPlayerButton() throws Exception {
        /*  JavaFX reports no error for a broken video on the headless test platform, but for an unreadable one  */
        Path video = Files.write(dir.resolve("locked.mp4"), new byte[] { 0, 0, 0, 24, 'f', 't', 'y', 'p' });
        assumeTrue(video.toFile().setReadable(false) && !Files.isReadable(video), "the video stays readable");
        Path image = dir.resolve("image.png");
        ImageIO.write(new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB), "png", image.toFile());

        ImagePreviewOverlay overlay = onFxThread(() -> {
            ImagePreviewOverlay created = new ImagePreviewOverlay();
            new Scene(created, 1000, 700);
            created.showImages(List.of(video.toString(), image.toString()), 0);
            return created;
        });

        Label hint = awaitHint(overlay);
        assertNotNull(hint, "no hint for the video");
        onFxThread(() -> {
            boolean opens = SystemOpen.isAvailable();
            Button open = openButton(overlay);
            assertNotNull(open);
            assertEquals(opens, shown(open));
            assertEquals(!opens, hint.getText().contains(video.toAbsolutePath().toString()));
            assertFalse(shown(playPauseButton(overlay)));

            /*  Space plays nothing, paging shows the image without the fallback, Esc closes  */
            press(overlay, KeyCode.SPACE);
            assertFalse(shown(playPauseButton(overlay)));
            press(overlay, KeyCode.RIGHT);
            assertFalse(shown(hint));
            press(overlay, KeyCode.LEFT);
            assertTrue(shown(hint));
            press(overlay, KeyCode.ESCAPE);
            assertFalse(overlay.isVisible());
            assertFalse(shown(hint));
            return null;
        });

        assertTrue(uncaught.isEmpty(), () -> "uncaught: " + uncaught);
        assertNull(logs.list.stream().filter((event) -> event.getThrowableProxy() != null).findFirst().orElse(null));

        /*  A problem of the file is not one of the system, the next video tries JavaFX Media again  */
        assertTrue(MediaSupport.isUsable());
    }

    @Test
    void anUnusableJavaFxMediaGoesStraightToTheFallback() throws Exception {
        Path video = Files.write(dir.resolve("clip.mp4"), new byte[] { 0 });
        MediaSupport.failed(new RuntimeException("com.sun.media.jfxmedia.MediaException: Could not create player!"));
        assertFalse(MediaSupport.isUsable());

        onFxThread(() -> {
            ImagePreviewOverlay overlay = new ImagePreviewOverlay();
            new Scene(overlay, 1000, 700);
            overlay.showImage(video.toString());
            assertTrue(shown(hint(overlay)));
            assertFalse(shown(playPauseButton(overlay)));
            overlay.closeOverlay();
            return null;
        });
        assertTrue(uncaught.isEmpty(), () -> "uncaught: " + uncaught);
    }
}
