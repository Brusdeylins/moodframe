package art.moodframe.ui.component;

import art.moodframe.util.I18nManager;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The actions of an image as a dark, rounded bar at the top center of the image, shown while the pointer is over it:
 * view, edit, download, mood from image and delete. Used by the chat entries and the compact view alike.
 *
 * @since 2.0.0
 */
public class ImageActionBar extends HBox {

    /** The actions of the bar, in their order, each with its icon and the key of its tooltip. */
    public enum Action {
        VIEW("mdi2e-eye", "tooltip.preview.image"),
        EDIT("mdi2p-pencil", "tooltip.edit.image"),
        DOWNLOAD("mdi2d-download", "tooltip.download.image"),
        MOOD("mdi2i-image-search-outline", "mood.image.menu"),
        DELETE("mdi2t-trash-can-outline", "tooltip.delete.image");

        private final String icon;
        private final String tooltipKey;

        Action(String icon, String tooltipKey) {
            this.icon = icon;
            this.tooltipKey = tooltipKey;
        }
    }

    /*  The distance of the bar from the top edge of the image  */
    private static final double MARGIN = 8.0;

    private final Map<Action, Button> buttons = new EnumMap<>(Action.class);

    /**
     * Creates the bar, hidden until it is attached to an image.
     *
     * @param onAction called with the action the user chose
     */
    public ImageActionBar(Consumer<Action> onAction) {
        getStyleClass().add("image-action-bar");
        getStylesheets().add(ImageActionBar.class.getResource("/css/image-action-bar.css").toExternalForm());
        setAlignment(Pos.CENTER);
        setVisible(false);

        for (Action action : Action.values()) {
            FontIcon icon = new FontIcon(action.icon);
            icon.setIconSize(14);
            Button button = new Button(null, icon);
            button.getStyleClass().add("icon-button");
            if (action == Action.DELETE)
                button.getStyleClass().add("text-danger-hover");
            button.setOnAction((event) -> onAction.accept(action));
            buttons.put(action, button);
            getChildren().add(button);
        }
        updateTexts();
    }

    /**
     * Shows the bar at the top center of an image while the pointer is over the image. The pane does not lay out the
     * bar, so that it never widens a narrow image.
     *
     * @param tile the pane holding the image
     */
    public void attachTo(Pane tile) {
        setManaged(false);
        tile.getChildren().add(this);
        visibleProperty().bind(tile.hoverProperty());
        visibleProperty().addListener((obs, wasVisible, isVisible) -> {
            if (isVisible) {
                autosize();
                relocate(Math.round((tile.getWidth() - getWidth()) / 2), MARGIN);
            }
        });
    }

    /**
     * Enables the actions that need the file only once it is there; "Mood from image" needs a still image, "Delete"
     * is always available.
     *
     * @param available whether the file of the image is there
     * @param video whether it is a video
     */
    public void setAvailable(boolean available, boolean video) {
        buttons.forEach((action, button) -> button.setDisable(action != Action.DELETE
                && (!available || action == Action.MOOD && video)));
    }

    /** Sets the tooltips in the current language. */
    public void updateTexts() {
        buttons.forEach((action, button) -> button.setTooltip(new Tooltip(I18nManager.getText(action.tooltipKey))));
    }
}
