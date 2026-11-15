package art.moodframe.util;

import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The errors of JavaFX Media which point at missing video support of the system, and remembering them.
 */
class MediaSupportTest {

    /*  The error of JavaFX 21 under Linux without a plugin for the ffmpeg of the system, as the poster sees it  */
    private static final Throwable NO_PLAYER = new CompletionException(new RuntimeException(
            "com.sun.media.jfxmedia.MediaException: Could not create player!"));

    @AfterEach
    void forget() {
        MediaSupport.reset();
    }

    @Test
    void noPlayerAndUnsupportedMediaMeanMissingSupport(@TempDir Path dir) throws Exception {
        assertTrue(MediaSupport.missingSupport(NO_PLAYER));

        /*  JavaFX supports no FLV any longer  */
        Path flv = Files.write(dir.resolve("clip.flv"), new byte[] { 'F', 'L', 'V', 1 });
        MediaException unsupported = assertThrows(MediaException.class, () -> new Media(flv.toUri().toString()));
        assertEquals(MediaException.Type.MEDIA_UNSUPPORTED, unsupported.getType());
        assertTrue(MediaSupport.missingSupport(new CompletionException(unsupported)));
    }

    @Test
    void errorsOfTheFileAreNotMissingSupport(@TempDir Path dir) throws Exception {
        MediaException missing = assertThrows(MediaException.class,
                () -> new Media(dir.resolve("gone.mp4").toUri().toString()));
        assertEquals(MediaException.Type.MEDIA_UNAVAILABLE, missing.getType());
        assertFalse(MediaSupport.missingSupport(missing));

        Path unknown = Files.write(dir.resolve("clip.xyz"), new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 });
        MediaException signature = assertThrows(MediaException.class, () -> new Media(unknown.toUri().toString()));
        assertEquals(MediaException.Type.MEDIA_UNSUPPORTED, signature.getType());
        assertFalse(MediaSupport.missingSupport(signature));
        assertFalse(MediaSupport.missingSupport(new CompletionException(new TimeoutException())));
        assertFalse(MediaSupport.missingSupport(new IllegalStateException((String) null)));
    }

    @Test
    void remembersMissingSupportOnly() {
        assertTrue(MediaSupport.isUsable());
        MediaSupport.failed(new CompletionException(new TimeoutException()));
        assertTrue(MediaSupport.isUsable());
        MediaSupport.failed(NO_PLAYER);
        assertFalse(MediaSupport.isUsable());
    }
}
