package art.moodframe.ui.controller.settings;

import art.moodframe.util.I18nManager;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * The helpers of the connection tests in the settings: the one way a test runs in the background, and the field of a
 * key that shows it as text or hidden.
 */
final class ConnectionTest {

    private ConnectionTest() {
    }

    /**
     * Runs a connection test on a worker thread. The button is disabled while it runs; the result, or the
     * exception the test ended with, is handed over on the FX thread.
     *
     * @param button     the button that started the test
     * @param buttonText the text of the button after the test
     * @param test       the test, run on the worker
     * @param onResult   takes whether the test passed
     * @param onError    takes the exception the test ended with
     */
    static void run(Button button, String buttonText, Callable<Boolean> test, Consumer<Boolean> onResult,
            Consumer<Throwable> onError) {
        button.setDisable(true);
        button.setText(I18nManager.getText("settings.test.testing"));

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return test.call();
            }
        };
        task.setOnSucceeded((event) -> {
            button.setDisable(false);
            button.setText(buttonText);
            onResult.accept(Boolean.TRUE.equals(task.getValue()));
        });
        task.setOnFailed((event) -> {
            button.setDisable(false);
            button.setText(buttonText);
            onError.accept(task.getException());
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Replaces a key field by a field that shows the key as text, or back by a password field. The text, width and
     * place in the row stay.
     *
     * @param current  the field shown now
     * @param showText true for a text field, false for a password field
     * @param prompt   the prompt text of the new field
     * @return the new field
     */
    static TextInputControl swapKeyField(TextInputControl current, boolean showText, String prompt) {
        HBox row = (HBox) current.getParent();
        int index = row.getChildren().indexOf(current);
        TextInputControl field = showText ? new TextField() : new PasswordField();
        field.setText(current.getText());
        field.setPromptText(prompt);
        field.setPrefWidth(current.getPrefWidth());
        field.setMaxWidth(current.getMaxWidth());
        HBox.setHgrow(field, Priority.ALWAYS);
        row.getChildren().remove(current);
        row.getChildren().add(index, field);
        return field;
    }
}
