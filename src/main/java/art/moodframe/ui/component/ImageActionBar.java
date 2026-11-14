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
 * view, edit, download, mood of the image and delete. Used by the chat entries and the compact view alike; the compact
 * view leads with "Use all" of the entry of the image, which the chat entries show at the entry itself.
 *
 * @since 2.0.0
 */
public class ImageActionBar extends HBox {

    /** The actions of the bar, in their order, each with its icon and the key of its tooltip. */
    public enum Action {
        REUSE("mdi2c-content-copy", "entry.reuse.all"),
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
    private boolean moodSaved;

    /**
     * Creates the bar without "Use all", hidden until it is attached to an image.
     *
     * @param onAction called with the action the user chose
     */
    public ImageActionBar(Consumer<Action> onAction) {
        this(onAction, false);
    }

    /**
     * Creates the bar, hidden until it is attached to an image.
     *
     * @param onAction called with the action the user chose
     * @param reuse whether the bar leads with "Use all"
     */
    public ImageActionBar(Consumer<Action> onAction, boolean reuse) {
        getStyleClass().add("image-action-bar");
        getStylesheets().add(ImageActionBar.class.getResource("/css/image-action-bar.css").toExternalForm());
        setAlignment(Pos.CENTER);
        setVisible(false);

        for (Action action : Action.values()) {
            if (action == Action.REUSE && !reuse)
                continue;
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
     * bar, so that it never widens a narrow image; on a narrow image the bar shrinks to fit, so no action is cut off.
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

                /*  The bar scales around its center, so its top edge moves down by half of what it loses  */
                double scale = Math.min(1.0, (tile.getWidth() - 2 * MARGIN) / getWidth());
                setScaleX(scale);
                setScaleY(scale);
                relocate(Math.round((tile.getWidth() - getWidth()) / 2), MARGIN - (1.0 - scale) * getHeight() / 2);
            }
        });
    }

    /**
     * Enables the actions that need the file only once it is there; "Mood from image" needs a still image, "Use all"
     * and "Delete" are always available.
     *
     * @param available whether the file of the image is there
     * @param video whether it is a video
     */
    public void setAvailable(boolean available, boolean video) {
        buttons.forEach((action, button) -> button.setDisable(action != Action.DELETE && action != Action.REUSE
                && (!available || action == Action.MOOD && video)));
    }

    /**
     * Lets the mood action take over the mood saved at the entry of the image ("Use mood") instead of analyzing the
     * image ("Mood from image..."), with the same icon.
     *
     * @param moodSaved whether the entry of the image carries its building blocks
     */
    public void setMoodSaved(boolean moodSaved) {
        this.moodSaved = moodSaved;
        updateTexts();
    }

    /** Sets the tooltips in the current language. */
    public void updateTexts() {
        buttons.forEach((action, button) -> button.setTooltip(new Tooltip(I18nManager.getText(
                action == Action.MOOD && moodSaved ? "image.mood.use.tooltip" : action.tooltipKey))));
    }
}
