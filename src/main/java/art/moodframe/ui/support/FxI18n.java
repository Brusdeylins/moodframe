package art.moodframe.ui.support;

import art.moodframe.util.I18nChangeListener;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;

import java.util.concurrent.Executor;

/**
 * Registers views for the change of the language: they are notified on the JavaFX thread, at once if the language
 * changes there, else later in its queue. {@link I18nManager} itself knows no JavaFX.
 *
 * @since 2.1.3
 */
public final class FxI18n {

    /** Runs a task on the JavaFX thread: at once if called there, else later. */
    private static final Executor FX_THREAD = (Runnable task) -> {
        if (Platform.isFxApplicationThread())
            task.run();
        else
            Platform.runLater(task);
    };

    private FxI18n() {
    }

    /**
     * Adds a listener of a view, notified on the JavaFX thread when the language changes; it is removed with
     * {@link I18nManager#removeChangeListener(I18nChangeListener)}.
     *
     * @param listener the listener to add (must not be null)
     */
    public static void addChangeListener(I18nChangeListener listener) {
        I18nManager.addChangeListener(listener, FX_THREAD);
    }
}
