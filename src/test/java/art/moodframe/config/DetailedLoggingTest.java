package art.moodframe.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The setting "detailed logging" switches the application logger to DEBUG and back to the configured level.
 */
class DetailedLoggingTest {

    @Test
    void switchesTheApplicationLoggerToDebugAndBack() {
        Logger logger = (Logger) LoggerFactory.getLogger(DetailedLogging.APP_LOGGER);
        Level configured = logger.getLevel();
        try {
            DetailedLogging.apply(true);
            assertTrue(logger.isDebugEnabled());
            assertTrue(LoggerFactory.getLogger(DetailedLoggingTest.class).isDebugEnabled());

            DetailedLogging.apply(false);
            assertEquals(configured, logger.getLevel());
        }
        finally {
            logger.setLevel(configured);
        }
    }
}
