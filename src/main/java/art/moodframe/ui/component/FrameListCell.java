package art.moodframe.ui.component;

import art.moodframe.model.Frame;
import art.moodframe.service.FrameService;
import art.moodframe.util.I18nManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Row of the frame selection: the actions "Save", "Save as..." and "Manage...", a separator line, "No frame" and the
 * frames. A standard frame shows a lock, a frame of the user a brush, a frame whose model is not available a warning;
 * the tooltip names its settings. As the button cell, it shows the chosen frame in italics with a dot once a field
 * was changed after choosing it.
 *
 * @since 2.0.0
 */
public class FrameListCell extends ListCell<FrameChoice> {

    private final Predicate<Frame> unavailable;
    private final BooleanSupplier changed;
    private final BooleanSupplier saveLocked;

    private final FontIcon icon = new FontIcon();
    private final Label nameLabel = new Label();
    private final FontIcon warning = new FontIcon("mdi2a-alert-outline");
    private final HBox content = new HBox(6, icon, nameLabel, warning);
    private final Separator separator = new Separator();

    /**
     * Creates a row.
     *
     * @param unavailable tells if no model of a frame is available
     * @param changed tells if a field was changed after the frame was chosen; null for a row of the list
     * @param saveLocked tells if "Save" is not available, as a standard frame is chosen
     */
    public FrameListCell(Predicate<Frame> unavailable, BooleanSupplier changed, BooleanSupplier saveLocked) {
        this.unavailable = unavailable;
        this.changed = changed;
        this.saveLocked = saveLocked;

        icon.getStyleClass().add("ikonli-font-icon");
        icon.setIconSize(14);
        warning.getStyleClass().addAll("ikonli-font-icon", "warning");
        warning.setIconSize(14);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(2, 0, 2, 0));
    }

    /** Draws the row again, for example once a field was changed after the frame was chosen. */
    public void refresh() {
        updateItem(getItem(), isEmpty());
    }

    @Override
    protected void updateItem(FrameChoice item, boolean empty) {
        super.updateItem(item, empty);
        setText(null);
        setDisable(false);
        setTooltip(null);
        if (empty || item == null) {
            setGraphic(null);
            return;
        }

        icon.getStyleClass().removeAll("accent", "success");
        nameLabel.setStyle("");
        warning.setVisible(false);
        warning.setManaged(false);
        switch (item.kind()) {
            case SEPARATOR -> {
                setGraphic(separator);
                setDisable(true);
                return;
            }
            case SAVE -> {
                show("mdi2c-content-save-outline", I18nManager.getText("params.frame.save"));
                setDisable(saveLocked.getAsBoolean());
            }
            case SAVE_AS -> show("mdi2c-content-save-edit-outline", I18nManager.getText("params.frame.save.as"));
            case MANAGE -> show("mdi2c-cog-outline", I18nManager.getText("params.frame.manage"));
            case NONE -> {
                show(null, I18nManager.getText("params.frame.none"));
                nameLabel.setStyle("-fx-text-fill: -color-fg-muted;");
            }
            case FRAME -> {
                Frame frame = item.frame();
                boolean edited = changed != null && changed.getAsBoolean();
                show(frame.isDefault() ? "mdi2l-lock" : "mdi2b-brush", frame.getName() + (edited ? " •" : ""));
                icon.getStyleClass().add(frame.isDefault() ? "accent" : "success");
                nameLabel.setStyle((frame.isDefault() ? "-fx-font-weight: normal;" : "-fx-font-weight: bold;")
                        + (edited ? " -fx-font-style: italic;" : ""));

                /*  The button cell leaves the warning to the sign next to the field  */
                boolean warn = changed == null && unavailable.test(frame);
                warning.setVisible(warn);
                warning.setManaged(warn);
                if (edited)
                    setTooltip(new Tooltip(I18nManager.getText("params.frame.changed")));
                else if (changed == null)
                    setTooltip(new Tooltip(FrameService.describe(frame)
                            + (warn ? "\n" + I18nManager.getText("params.frame.unavailable") : "")));
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
