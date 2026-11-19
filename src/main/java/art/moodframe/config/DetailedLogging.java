package art.moodframe.config;

import ch.qos.logback.classic.Level;
import org.slf4j.LoggerFactory;

/**
 * Turns the setting "detailed logging" into the log level of the application: with it, the logger
 * {@code art.moodframe} logs at DEBUG (or finer, if configured so), without it at the level of the logback
 * configuration again.
 *
 * @since 2.1.3
 */
public final class DetailedLogging {

    static final String APP_LOGGER = "art.moodframe";

    /*  the level from the logback configuration, remembered at the first switch  */
    private static Level configuredLevel;
    private static boolean remembered;

    private DetailedLogging() {
    }

    /**
     * Sets the log level of the application for the setting.
     *
     * @param enabled true for DEBUG, false for the configured level
     */
    public static synchronized void apply(boolean enabled) {
        if (!(LoggerFactory.getLogger(APP_LOGGER) instanceof ch.qos.logback.classic.Logger logger))
            return;
        if (!remembered) {
            configuredLevel = logger.getLevel();
            remembered = true;
        }
        boolean finer = configuredLevel != null && !configuredLevel.isGreaterOrEqual(Level.DEBUG);
        logger.setLevel(enabled && !finer ? Level.DEBUG : configuredLevel);
    }
}
