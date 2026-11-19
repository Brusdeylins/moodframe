package art.moodframe.ui.controller;

import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The status bar keeps no message of the former language: after a change it shows "Ready" in the new one. */
class StatusBarLanguageTest {

    @Test
    void aMessageOfTheFormerLanguageGivesWayToReady() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        }
        catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));

        Locale locale = I18nManager.getCurrentLocale();
        try {
            String shown = onFx(() -> {
                I18nManager.setLocale(Locale.ENGLISH);
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/StatusBar.fxml"));
                Parent root = loader.load();
                StatusBarController bar = loader.getController();
                bar.error(I18nManager.getText("status.request.failed"));
                I18nManager.setLocale(Locale.GERMAN);
                bar.updateTexts();
                String text = ((Label) root.lookup("#statusLabel")).getText();
                bar.dispose();
                return text;
            });
            assertEquals(I18nManager.getTextIn(Locale.GERMAN, "status.ready"), shown);
        }
        finally {
            onFx(() -> {
                I18nManager.setLocale(locale);
                return null;
            });
        }
    }

    /*  runs a step on the JavaFX thread and returns its result  */
    private static <T> T onFx(Callable<T> step) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(step.call());
            }
            catch (Exception e) {
                failure.set(e);
            }
            finally {
                done.countDown();
            }
        });
        assertTrue(done.await(20, TimeUnit.SECONDS), "the step ran in time");
        if (failure.get() != null)
            throw failure.get();
        return result.get();
    }
}
