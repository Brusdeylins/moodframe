package art.moodframe.ui.controller.settings;

import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A status line of the settings: an icon, or a spinner while a test runs, and a message. The icon and the spinner
 * share a box of fixed size at the start of the line, so the message does not move.
 */
final class StatusLine {

    private static final double SIZE = 16.0;

    private final HBox container;
    private final Label label;
    private final FontIcon icon;
    private final ProgressIndicator progress = new ProgressIndicator();

    /**
     * Puts the icon of the line and a spinner into a box of fixed size.
     *
     * @param container the line
     * @param label     the message
     * @param icon      the icon, the first child of the line
     */
    StatusLine(HBox container, Label label, FontIcon icon) {
        this.container = container;
        this.label = label;
        this.icon = icon;
        progress.setMaxSize(SIZE, SIZE);
        progress.setPrefSize(SIZE, SIZE);
        progress.setMinSize(SIZE, SIZE);
        progress.setVisible(false);

        StackPane box = new StackPane();
        box.setPrefSize(SIZE, SIZE);
        box.setMinSize(SIZE, SIZE);
        box.setMaxSize(SIZE, SIZE);
        box.getChildren().addAll(icon, progress);
        container.getChildren().remove(icon);
        container.getChildren().add(0, box);
    }

    /** @return the message of the line */
    Label label() {
        return label;
    }

    /** @return true while the spinner is shown */
    boolean isTesting() {
        return progress.isVisible();
    }

    /** Hides the line. */
    void hide() {
        container.setVisible(false);
    }

    /**
     * Shows the result of a test: icon and color for {@code success}, {@code error} and {@code info}, a spinner for
     * {@code testing}.
     *
     * @param status  the status
     * @param message the message
     */
    void show(String status, String message) {
        container.setVisible(true);
        label.setText(message);
        label.getStyleClass().clear();
        progress.setVisible(false);
        icon.setVisible(true);
        icon.getStyleClass().clear();
        icon.getStyleClass().add("ikonli-font-icon");

        switch (status) {
            case "success" -> {
                icon.setIconLiteral("mdi2c-check-circle");
                icon.getStyleClass().add("success");
                label.setStyle("-fx-text-fill: -color-success-emphasis;");
            }
            case "error" -> {
                icon.setIconLiteral("mdi2c-close-circle");
                icon.getStyleClass().add("danger");
                label.setStyle("-fx-text-fill: -color-danger-emphasis;");
            }
            case "testing" -> {
                icon.setVisible(false);
                progress.setVisible(true);
                progress.setProgress(-1.0);
                label.setStyle("-fx-text-fill: -color-accent-emphasis;");
            }
            case "info" -> {
                icon.setIconLiteral("mdi2i-information");
                icon.getStyleClass().add("accent");
                label.setStyle("-fx-text-fill: -color-accent-emphasis;");
            }
            default -> {
            }
        }
    }
}
