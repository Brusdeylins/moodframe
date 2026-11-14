package art.moodframe.ui.component;

import art.moodframe.model.Frame;
import art.moodframe.service.FrameService;
import art.moodframe.util.I18nManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.BooleanSupplier;

/**
 * Row of the frame selection: "No frame" and the frames. In the list, a frame of the user shows a brush in the icon
 * column and every frame the favorite star at the right ({@link FavoriteStar}); the tooltip names its aspect ratio. As
 * the button cell, it shows the name only, on the edge of the other fields, in italics with a dot once the aspect
 * ratio differs from the chosen frame.
 *
 * @since 2.0.0
 */
public class FrameListCell extends ListCell<FrameChoice> {

    private final BooleanSupplier changed;

    private final FontIcon icon = new FontIcon("mdi2b-brush");
    private final Label nameLabel = new Label();
    private final HBox content = new HBox(6, nameLabel);
    private final FavoriteStar<Frame> star;

    /**
     * Creates the button cell.
     *
     * @param changed tells if the aspect ratio differs from the chosen frame
     */
    public FrameListCell(BooleanSupplier changed) {
        this(changed, null);
    }

    /**
     * Creates a row of the list.
     *
     * @param favorites the favorites of the list
     */
    public FrameListCell(FavoriteStar.Favorites<Frame> favorites) {
        this(null, favorites);
    }

    private FrameListCell(BooleanSupplier changed, FavoriteStar.Favorites<Frame> favorites) {
        this.changed = changed;

        icon.getStyleClass().addAll("ikonli-font-icon", "success");
        icon.setIconSize(14);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(2, 0, 2, 0));

        /*  A row of the list adds the icon column and the star to the name  */
        star = favorites != null ? new FavoriteStar<>(this, favorites) : null;
        if (star != null) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            content.getChildren().add(0, icon);
            content.getChildren().addAll(spacer, star);
        }
    }

    /** Draws the row again, for example once the aspect ratio was changed after the frame was chosen. */
    public void refresh() {
        updateItem(getItem(), isEmpty());
    }

    @Override
    protected void updateItem(FrameChoice item, boolean empty) {
        super.updateItem(item, empty);
        setText(null);
        setTooltip(null);
        if (empty || item == null) {
            setGraphic(null);
            return;
        }

        nameLabel.setStyle("");
        icon.setVisible(false);
        switch (item.kind()) {
            case NONE -> {
                nameLabel.setText(I18nManager.getText("params.frame.none"));
                nameLabel.setStyle("-fx-text-fill: -color-fg-muted;");
            }
            case FRAME -> {
                Frame frame = item.frame();
                boolean edited = changed != null && changed.getAsBoolean();
                nameLabel.setText(frame.getName() + (edited ? " •" : ""));

                /*  Only the frames of the user carry the icon, the others keep its space  */
                icon.setVisible(!frame.isDefault());
                nameLabel.setStyle((frame.isDefault() ? "-fx-font-weight: normal;" : "-fx-font-weight: bold;")
                        + (edited ? " -fx-font-style: italic;" : ""));
                if (edited)
                    setTooltip(new Tooltip(I18nManager.getText("params.frame.changed")));
                else if (star != null)
                    setTooltip(new Tooltip(FrameService.describe(frame)));
            }
        }
        if (star != null)
            star.show(item.frame());
        setGraphic(content);
    }
}
