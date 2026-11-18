package art.moodframe.ui.controller.settings;

import art.moodframe.ui.component.DialogButtons;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.List;

/**
 * The message boxes and file choosers of the settings sections. The dialog uses {@link #standard()}; a test answers
 * them itself.
 */
public interface SettingsPrompts {

    /**
     * Shows an error and waits until it is closed.
     *
     * @param title   the title
     * @param message the message
     */
    void error(String title, String message);

    /**
     * Shows an information and waits until it is closed.
     *
     * @param title   the title
     * @param message the message
     */
    void info(String title, String message);

    /**
     * Asks for a confirmation.
     *
     * @param title   the title
     * @param header  the header
     * @param content the question
     * @param yesNo   true for the buttons Yes and No, false for OK and Cancel
     * @return true if confirmed with Yes or OK
     */
    boolean confirm(String title, String header, String content, boolean yesNo);

    /**
     * Lets the user choose a file to save.
     *
     * @param chooser the prepared chooser
     * @param owner   the owner window
     * @return the file, or null if cancelled
     */
    File saveFile(FileChooser chooser, Window owner);

    /**
     * Lets the user choose a file to open.
     *
     * @param chooser the prepared chooser
     * @param owner   the owner window
     * @return the file, or null if cancelled
     */
    File openFile(FileChooser chooser, Window owner);

    /**
     * Lets the user choose files to open.
     *
     * @param chooser the prepared chooser
     * @param owner   the owner window
     * @return the files, or null if cancelled
     */
    List<File> openFiles(FileChooser chooser, Window owner);

    /**
     * The JavaFX message boxes and file choosers.
     *
     * @return the prompts
     */
    static SettingsPrompts standard() {
        return new SettingsPrompts() {

            @Override
            public void error(String title, String message) {
                show(Alert.AlertType.ERROR, title, message);
            }

            @Override
            public void info(String title, String message) {
                show(Alert.AlertType.INFORMATION, title, message);
            }

            private void show(Alert.AlertType type, String title, String message) {
                Alert alert = new Alert(type);
                alert.getButtonTypes().setAll(DialogButtons.ok());
                alert.setTitle(title);
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
            }

            @Override
            public boolean confirm(String title, String header, String content, boolean yesNo) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                ButtonType confirmed = yesNo ? DialogButtons.yes() : DialogButtons.ok();
                alert.getButtonTypes().setAll(confirmed, yesNo ? DialogButtons.no() : DialogButtons.cancel());
                alert.setTitle(title);
                alert.setHeaderText(header);
                alert.setContentText(content);
                return alert.showAndWait().orElse(null) == confirmed;
            }

            @Override
            public File saveFile(FileChooser chooser, Window owner) {
                return chooser.showSaveDialog(owner);
            }

            @Override
            public File openFile(FileChooser chooser, Window owner) {
                return chooser.showOpenDialog(owner);
            }

            @Override
            public List<File> openFiles(FileChooser chooser, Window owner) {
                return chooser.showOpenMultipleDialog(owner);
            }
        };
    }
}
