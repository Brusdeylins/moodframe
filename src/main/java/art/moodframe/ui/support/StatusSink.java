package art.moodframe.ui.support;

import art.moodframe.util.I18nManager;
import javafx.application.Platform;

/**
 * Where a part of the main window reports what it did: a status message, or an error, which stays shown for a few
 * seconds against the next status messages (see {@code ui.controller.StatusBarController}).
 *
 * @since 2.1.3
 */
public interface StatusSink {

    /**
     * Shows a status message, unless an error is protected.
     *
     * @param message the localized message
     */
    void status(String message);

    /**
     * Shows an error message as it is and protects it for a few seconds.
     *
     * @param message the localized message, for example one of the {@code status.error.*} texts
     */
    void error(String message);

    /**
     * Shows the detail behind "Error:" of the current language as error, later on the JavaFX thread, so any thread
     * may report a failure.
     *
     * @param detail what failed
     */
    default void failure(String detail) {
        Platform.runLater(() -> error(I18nManager.getText("status.error.failure", detail)));
    }
}
