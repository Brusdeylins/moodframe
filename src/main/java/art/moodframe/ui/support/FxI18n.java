package art.moodframe.ui.support;

import art.moodframe.util.I18nChangeListener;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

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

    /**
     * Adds the listener of a dialog, removed when the dialog is hidden, so a closed dialog is released.
     *
     * @param listener the listener to add (must not be null)
     * @param dialog   the dialog that ends the listening when it is hidden
     */
    public static void addChangeListener(I18nChangeListener listener, Dialog<?> dialog) {
        addChangeListener(listener);
        dialog.addEventHandler(DialogEvent.DIALOG_HIDDEN, (DialogEvent event) -> I18nManager.removeChangeListener(
                listener));
    }

    /**
     * Adds the listener of a view, removed when the window that shows the node is hidden, so a closed window is
     * released; the node may get its window later.
     *
     * @param listener the listener to add (must not be null)
     * @param node     the node whose window ends the listening when it is hidden
     */
    public static void addChangeListener(I18nChangeListener listener, Node node) {
        addChangeListener(listener);
        ObservableValue<Window> window = node.sceneProperty().flatMap(Scene::windowProperty);
        window.subscribe((Window shown) -> {
            if (shown != null)
                shown.addEventHandler(WindowEvent.WINDOW_HIDDEN, (WindowEvent event) ->
                        I18nManager.removeChangeListener(listener));
        });
    }
}
