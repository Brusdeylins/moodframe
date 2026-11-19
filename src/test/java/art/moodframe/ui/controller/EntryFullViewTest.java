package art.moodframe.ui.controller;

import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryGroup;
import art.moodframe.model.GeneratedImage;
import art.moodframe.ui.entry.ImagePreviewOverlay;
import art.moodframe.ui.support.MediaSupport;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The full view of the chat view lets go of its video when the window closes: the playback stops, not only the
 * overlay hides. JavaFX Media is taken as unusable, so the video shows its fallback, which the playback owns.
 */
class EntryFullViewTest {

    @TempDir
    Path dir;

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        }
        catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @BeforeEach
    @AfterEach
    void forgetMediaSupport() throws Exception {
        Method reset = MediaSupport.class.getDeclaredMethod("reset");
        reset.setAccessible(true);
        reset.invoke(null);
    }

    private static <T> T onFx(Callable<T> work) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(work.call());
            }
            catch (Exception | AssertionError e) {
                result.completeExceptionally(e);
            }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    /*  Whether the hint of the video fallback shows within the overlay, which may be hidden itself  */
    private static boolean hintShows(Node node, ImagePreviewOverlay overlay) {
        if (node instanceof Label label && label.getText() != null
                && label.getText().startsWith(I18nManager.getText("video.fallback.hint"))) {
            for (Node current = label; current != overlay; current = current.getParent())
                if (!current.isVisible())
                    return false;
            return true;
        }
        if (node instanceof Parent parent)
            for (Node child : parent.getChildrenUnmodifiable())
                if (hintShows(child, overlay))
                    return true;
        return false;
    }

    private static EntryFullView.Host host() {
        return new EntryFullView.Host() {
            @Override
            public Chat chat() {
                return null;
            }

            @Override
            public EntryGroup group() {
                return null;
            }

            @Override
            public List<ChatEntry> shownEntries() {
                return null;
            }

            @Override
            public String imagePath(GeneratedImage image, boolean isChatDeleted) {
                return null;
            }

            @Override
            public void edit(String path) {
            }

            @Override
            public void download(String path) {
            }

            @Override
            public void status(String message) {
            }
        };
    }

    @Test
    void theClosedWindowStopsTheVideoOfTheFullView() throws Exception {
        Path video = Files.write(dir.resolve("clip.mp4"), new byte[] { 0 });
        MediaSupport.failed(new RuntimeException("com.sun.media.jfxmedia.MediaException: Could not create player!"));
        StackPane pane = new StackPane();
        EntryFullView fullView = onFx(() -> {
            new Scene(pane, 1000, 700);
            EntryFullView created = new EntryFullView(null, pane, host());
            created.showGallery(List.of(video.toString()), List.of("clip"), 0);
            return created;
        });
        ImagePreviewOverlay overlay = onFx(() -> (ImagePreviewOverlay) pane.getChildren().get(0));
        assertTrue(onFx(() -> fullView.isShowing() && hintShows(overlay, overlay)), "the video shows its fallback");

        onFx(() -> {
            fullView.dispose();
            return null;
        });
        assertFalse(onFx(fullView::isShowing));
        assertFalse(onFx(() -> hintShows(overlay, overlay)), "the playback of the video is stopped");
    }
}
