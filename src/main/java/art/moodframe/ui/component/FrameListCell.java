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
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.BooleanSupplier;

/**
 * Row of the frame selection: "No frame" and the frames. A standard frame shows a star, a frame of the user a brush;
 * the tooltip names its aspect ratio. As the button cell, it shows the chosen frame in italics with a dot once the
 * aspect ratio differs from it.
 *
 * @since 2.0.0
 */
public class FrameListCell extends ListCell<FrameChoice> {

    private final BooleanSupplier changed;

    private final FontIcon icon = new FontIcon();
    private final Tooltip builtin = new Tooltip(I18nManager.getText("params.builtin"));
    private final Label nameLabel = new Label();
    private final HBox content = new HBox(6, icon, nameLabel);

    /**
     * Creates a row.
     *
     * @param changed tells if the aspect ratio differs from the chosen frame; null for a row of the list
     */
    public FrameListCell(BooleanSupplier changed) {
        this.changed = changed;

        icon.getStyleClass().add("ikonli-font-icon");
        icon.setIconSize(14);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(2, 0, 2, 0));
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

        icon.getStyleClass().removeAll("accent", "success");
        Tooltip.uninstall(icon, builtin);
        nameLabel.setStyle("");
        switch (item.kind()) {
            case NONE -> {
                show(null, I18nManager.getText("params.frame.none"));
                nameLabel.setStyle("-fx-text-fill: -color-fg-muted;");
            }
            case FRAME -> {
                Frame frame = item.frame();
                boolean edited = changed != null && changed.getAsBoolean();
                show(frame.isDefault() ? "mdi2s-star-outline" : "mdi2b-brush", frame.getName() + (edited ? " •" : ""));
                icon.getStyleClass().add(frame.isDefault() ? "accent" : "success");
                if (frame.isDefault())
                    Tooltip.install(icon, builtin);
                nameLabel.setStyle((frame.isDefault() ? "-fx-font-weight: normal;" : "-fx-font-weight: bold;")
                        + (edited ? " -fx-font-style: italic;" : ""));

                /*  The tooltip of the row hides the one of the star, so in the list it names the standard, too  */
                if (edited)
                    setTooltip(new Tooltip(I18nManager.getText("params.frame.changed")));
                else if (changed == null)
                    setTooltip(new Tooltip(FrameService.describe(frame)
                            + (frame.isDefault() ? " · " + builtin.getText() : "")));
            }
        }
        setGraphic(content);
    }

    /** Shows an icon, none for null, and a text. */
    private void show(String iconLiteral, String text) {
        icon.setVisible(iconLiteral != null);
        icon.setManaged(iconLiteral != null);
        if (iconLiteral != null)
            icon.setIconLiteral(iconLiteral);
        nameLabel.setText(text);
    }
}
