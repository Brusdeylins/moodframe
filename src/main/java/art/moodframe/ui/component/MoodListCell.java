package art.moodframe.ui.component;

import art.moodframe.model.Mood;
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
import java.util.function.Predicate;

/**
 * Row of the mood selection: "No mood" and the moods. In the list, a mood of the user shows a brush in the icon
 * column, an incomplete mood a warning, and every mood the favorite star at the right ({@link FavoriteStar}). As the
 * button cell, it shows the name only, on the edge of the other fields, in italics with a dot once a field was changed
 * after choosing the mood.
 *
 * @since 2.0.0
 */
public class MoodListCell extends ListCell<MoodChoice> {

    private final Predicate<Mood> incomplete;
    private final BooleanSupplier changed;

    private final FontIcon icon = new FontIcon("mdi2b-brush");
    private final Label nameLabel = new Label();
    private final FontIcon warning = new FontIcon("mdi2a-alert-circle-outline");
    private final HBox content = new HBox(6, nameLabel);
    private final FavoriteStar<Mood> star;

    /**
     * Creates the button cell.
     *
     * @param changed tells if a field differs from the chosen mood
     */
    public MoodListCell(BooleanSupplier changed) {
        this(null, changed, null);
    }

    /**
     * Creates a row of the list.
     *
     * @param incomplete tells if a block of a mood lies in the trash
     * @param favorites the favorites of the list
     */
    public MoodListCell(Predicate<Mood> incomplete, FavoriteStar.Favorites<Mood> favorites) {
        this(incomplete, null, favorites);
    }

    private MoodListCell(Predicate<Mood> incomplete, BooleanSupplier changed, FavoriteStar.Favorites<Mood> favorites) {
        this.incomplete = incomplete;
        this.changed = changed;

        icon.getStyleClass().addAll("ikonli-font-icon", "success");
        icon.setIconSize(14);
        warning.getStyleClass().addAll("ikonli-font-icon", "warning");
        warning.setIconSize(14);
        Tooltip.install(warning, new Tooltip(I18nManager.getText("params.mood.incomplete.short")));
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(2, 0, 2, 0));

        /*  A row of the list adds the icon column, the warning and the star to the name  */
        star = favorites != null ? new FavoriteStar<>(this, favorites) : null;
        if (star != null) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            content.getChildren().add(0, icon);
            content.getChildren().addAll(warning, spacer, star);
            DropdownBounds.showCutName(nameLabel);
        }
    }

    @Override
    protected double computePrefWidth(double height) {
        return DropdownBounds.rowWidth(this, super.computePrefWidth(height));
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

        nameLabel.setStyle("");
        icon.setVisible(false);
        warning.setVisible(false);
        warning.setManaged(false);
        switch (item.kind()) {
            case NONE -> {
                nameLabel.setText(I18nManager.getText("params.mood.none"));
                nameLabel.setStyle("-fx-text-fill: -color-fg-muted;");
            }
            case MOOD -> {
                Mood mood = item.mood();
                boolean edited = changed != null && changed.getAsBoolean();
                nameLabel.setText(mood.getName() + (edited ? " •" : ""));

                /*  Only the moods of the user carry the icon, the others keep its space  */
                icon.setVisible(!mood.isDefault());
                nameLabel.setStyle((mood.isDefault() ? "-fx-font-weight: normal;" : "-fx-font-weight: bold;")
                        + (edited ? " -fx-font-style: italic;" : ""));
                if (edited)
                    setTooltip(new Tooltip(I18nManager.getText("params.mood.changed")));

                /*  The button cell leaves the warning to the button next to the field, which leads to the trash  */
                warning.setVisible(incomplete != null && incomplete.test(mood));
                warning.setManaged(warning.isVisible());
            }
        }
        if (star != null)
            star.show(item.mood());
        setGraphic(content);
    }
}
