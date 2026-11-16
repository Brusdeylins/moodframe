package art.moodframe.ui.component;

import art.moodframe.util.I18nManager;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;

/**
 * The standard buttons of the dialogs in the language of the app. JavaFX labels its built-in ones (OK, Cancel, …) in
 * the language of the system, so an English app on a German system shows "Abbrechen". Every call returns a new button
 * in the current language with the role of the built-in one; compare the answer of a dialog with the instance given
 * to it or with its {@link ButtonData}.
 */
public final class DialogButtons {

    private DialogButtons() {
    }

    public static ButtonType ok() {
        return create("button.ok", ButtonData.OK_DONE);
    }

    public static ButtonType cancel() {
        return create("button.cancel", ButtonData.CANCEL_CLOSE);
    }

    public static ButtonType close() {
        return create("button.close", ButtonData.CANCEL_CLOSE);
    }

    public static ButtonType yes() {
        return create("button.yes", ButtonData.YES);
    }

    public static ButtonType no() {
        return create("button.no", ButtonData.NO);
    }

    private static ButtonType create(String key, ButtonData data) {
        return new ButtonType(I18nManager.getText(key), data);
    }
}
