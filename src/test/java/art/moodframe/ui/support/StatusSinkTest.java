package art.moodframe.ui.support;

import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A failure reported from any thread shows as error on the JavaFX thread, in the language of the window. */
class StatusSinkTest {

    private final Locale locale = I18nManager.getCurrentLocale();

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

    @AfterEach
    void restoreTheLanguage() {
        I18nManager.setLocale(locale);
    }

    /*  Reports a failure from this thread and returns the error shown, with whether it showed on the JavaFX thread  */
    private static String failure(String detail) throws Exception {
        CompletableFuture<String> shown = new CompletableFuture<>();
        StatusSink sink = new StatusSink() {
            @Override
            public void status(String message) {
            }

            @Override
            public void error(String message) {
                shown.complete(Platform.isFxApplicationThread() + " " + message);
            }
        };
        sink.failure(detail);
        return shown.get(10, TimeUnit.SECONDS);
    }

    @Test
    void aFailureShowsInTheLanguageOfTheWindow() throws Exception {
        I18nManager.setLocale(Locale.ENGLISH);
        assertEquals("true Error: the disk is full", failure("the disk is full"));

        I18nManager.setLocale(Locale.GERMAN);
        assertEquals("true Fehler: die Platte ist voll", failure("die Platte ist voll"));
    }
}
