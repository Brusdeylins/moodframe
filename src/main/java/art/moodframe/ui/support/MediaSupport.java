package art.moodframe.ui.support;

import javafx.scene.media.MediaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remembers for the session whether JavaFX Media can play videos on this system: under Linux it needs a plugin
 * matching the ffmpeg of the system, which JavaFX 23 brings only up to libavcodec 61. After the first error which
 * points at a missing plugin or codec, videos go straight to their fallback instead of failing one by one.
 *
 * @since 2.1.0
 */
public final class MediaSupport {

    private static final Logger logger = LoggerFactory.getLogger(MediaSupport.class);

    /*  The message of JavaFX Media when it finds no player for a video, e.g. without a matching libav plugin  */
    private static final String NO_PLAYER = "Could not create player";

    /*  An unsupported media due to the content of the file, like "Empty signature!", is a problem of that file  */
    private static final String SIGNATURE = "signature";

    private static volatile boolean unusable = false;

    private MediaSupport() {
    }

    /**
     * Tells whether JavaFX Media is worth a try for a video.
     *
     * @return false once an error pointed at missing video support of the system
     */
    public static boolean isUsable() {
        return !unusable;
    }

    /**
     * Notes an error of JavaFX Media: if it points at missing video support of the system, JavaFX Media is not
     * tried again in this session.
     *
     * @param error the error, possibly wrapped
     */
    public static void failed(Throwable error) {
        if (!unusable && missingSupport(error)) {
            unusable = true;
            logger.warn("JavaFX Media cannot play videos on this system, using the fallback from now on: {}",
                    error.getMessage());
        }
    }

    /*  An unsupported media, but not due to its file, or no player at all, anywhere in the chain of causes  */
    static boolean missingSupport(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof MediaException media && media.getType() == MediaException.Type.MEDIA_UNSUPPORTED
                    && (media.getMessage() == null || !media.getMessage().contains(SIGNATURE)))
                return true;
            if (cause.getMessage() != null && cause.getMessage().contains(NO_PLAYER))
                return true;
        }
        return false;
    }

    /*  Forgets a noted error, for the tests  */
    static void reset() {
        unusable = false;
    }
}
