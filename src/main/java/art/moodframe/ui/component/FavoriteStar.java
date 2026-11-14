package art.moodframe.ui.component;

import art.moodframe.util.I18nManager;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Cell;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The star at the right of a row in a list of moods, frames or building blocks: filled for a favorite and always
 * shown, hollow for the others and shown only while the pointer is over the row. A click on it switches the favorite,
 * without choosing the row and, in the open list of a field, without closing the list.
 *
 * @param <T> the type of the entries
 * @since 2.0.0
 */
public class FavoriteStar<T> extends Label {

    /**
     * The favorites of a list, for the stars of its rows.
     *
     * @param field    the field whose open list shows the rows, null for a list in a dialog
     * @param favorite tells if an entry is a favorite
     * @param toggle   switches the favorite of an entry and shows the list in its new order
     * @param <T>      the type of the entries
     */
    public record Favorites<T>(ComboBox<?> field, Predicate<T> favorite, Consumer<T> toggle) {
    }

    private final Favorites<T> favorites;
    private final FontIcon icon = new FontIcon("mdi2s-star-outline");
    private final BooleanProperty present = new SimpleBooleanProperty();
    private final BooleanProperty favorite = new SimpleBooleanProperty();
    private T entry;

    /**
     * Creates the star of a row.
     *
     * @param row       the row, whose hover shows the hollow star
     * @param favorites the favorites of the list
     */
    public FavoriteStar(Cell<?> row, Favorites<T> favorites) {
        this.favorites = favorites;
        icon.getStyleClass().add("ikonli-font-icon");
        icon.setIconSize(14);
        setGraphic(icon);
        setTooltip(new Tooltip());
        setPickOnBounds(true);
        getStyleClass().add("favorite-star");
        visibleProperty().bind(present.and(favorite.or(row.hoverProperty())));

        /*  The press would choose the row and the release close the open list of the field, so both end here  */
        addEventHandler(MouseEvent.MOUSE_PRESSED, (event) -> {
            keepListOpen(true);
            event.consume();
        });
        addEventHandler(MouseEvent.MOUSE_RELEASED, (event) -> {
            keepListOpen(false);
            event.consume();
        });
        addEventHandler(MouseEvent.MOUSE_CLICKED, (event) -> {
            if (event.getButton() == MouseButton.PRIMARY && entry != null)
                favorites.toggle().accept(entry);
            event.consume();
        });

        /*  A tooltip of the row would take over the one of the star  */
        addEventHandler(MouseEvent.MOUSE_MOVED, MouseEvent::consume);
    }

    /**
     * Shows the star of an entry.
     *
     * @param entry the entry of the row, null for a row without star ("None")
     */
    public void show(T entry) {
        this.entry = entry;
        boolean isFavorite = entry != null && favorites.favorite().test(entry);
        icon.setIconLiteral(isFavorite ? "mdi2s-star" : "mdi2s-star-outline");
        icon.getStyleClass().remove("accent");
        if (isFavorite)
            icon.getStyleClass().add("accent");
        getTooltip().setText(I18nManager.getText(isFavorite ? "favorite.remove" : "favorite.add"));
        present.set(entry != null);
        favorite.set(isFavorite);
    }

    /*  the open list of a field closes on a release in it, which the skin of the field can suspend  */
    private void keepListOpen(boolean keep) {
        if (favorites.field() != null && favorites.field().getSkin() instanceof ComboBoxListViewSkin<?> skin)
            skin.setHideOnClick(!keep);
    }
}
