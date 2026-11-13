package art.moodframe.ui.component;

import art.moodframe.model.Mood;
import art.moodframe.util.I18nManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Row of the mood selection: "No mood" and the moods. A standard mood shows a lock, a mood of the user a brush, an
 * incomplete mood a warning. As the button cell, it shows the chosen mood in italics with a dot once a field was
 * changed after choosing it.
 *
 * @since 2.0.0
 */
public class MoodListCell extends ListCell<MoodChoice> {

    private final Predicate<Mood> incomplete;
    private final BooleanSupplier changed;

    private final FontIcon icon = new FontIcon();
    private final Label nameLabel = new Label();
    private final FontIcon warning = new FontIcon("mdi2a-alert-circle-outline");
    private final HBox content = new HBox(6, icon, nameLabel, warning);

    /**
     * Creates a row.
     *
     * @param incomplete tells if a block of a mood lies in the trash
     * @param changed tells if a field differs from the chosen mood; null for a row of the list
     */
    public MoodListCell(Predicate<Mood> incomplete, BooleanSupplier changed) {
        this.incomplete = incomplete;
        this.changed = changed;

        icon.getStyleClass().add("ikonli-font-icon");
        icon.setIconSize(14);
        warning.getStyleClass().addAll("ikonli-font-icon", "warning");
        warning.setIconSize(14);
        Tooltip.install(warning, new Tooltip(I18nManager.getText("params.mood.incomplete.short")));
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(2, 0, 2, 0));
    }

    /** Draws the row again, for example once a field was changed after the mood was chosen. */
    public void refresh() {
        updateItem(getItem(), isEmpty());
    }

    @Override
    protected void updateItem(MoodChoice item, boolean empty) {
        super.updateItem(item, empty);
        setText(null);
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
            case NONE -> {
                show(null, I18nManager.getText("params.mood.none"));
                nameLabel.setStyle("-fx-text-fill: -color-fg-muted;");
            }
            case MOOD -> {
                Mood mood = item.mood();
                boolean edited = changed != null && changed.getAsBoolean();
                show(mood.isDefault() ? "mdi2l-lock" : "mdi2b-brush", mood.getName() + (edited ? " •" : ""));
                icon.getStyleClass().add(mood.isDefault() ? "accent" : "success");
                nameLabel.setStyle((mood.isDefault() ? "-fx-font-weight: normal;" : "-fx-font-weight: bold;")
                        + (edited ? " -fx-font-style: italic;" : ""));
                if (edited)
                    setTooltip(new Tooltip(I18nManager.getText("params.mood.changed")));

                /*  The button cell leaves the warning to the button next to the field, which leads to the trash  */
                warning.setVisible(changed == null && incomplete.test(mood));
                warning.setManaged(warning.isVisible());
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
