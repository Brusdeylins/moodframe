package art.moodframe.ui.component;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.stage.PopupWindow;
import javafx.stage.Window;

import java.util.Objects;

/**
 * Keeps the open list of a field inside its window. JavaFX lays a list that is wider than its field flush left under
 * the field, so in the right panel it reaches past the edge of the window. Here the list ends flush with the right
 * edge of the field and grows to the left, at most up to the left edge of the window; names that still do not fit
 * end in "…" and show in full in a tooltip ({@link #showCutName}). It opens downwards or upwards as before.
 *
 * @since 2.0.0
 */
public final class DropdownBounds {

    /** The space the open list keeps to the left edge of the window. */
    static final double MARGIN = 10;

    /** The space a list keeps beside its rows for its edges and scroll bar, as its skin reckons. */
    private static final double ROW_SPARE = 30;

    private DropdownBounds() {
    }

    /**
     * The width of an open list: as wide as its rows need, at least as wide as its field, at most as wide as the
     * room left of the right edge of the field.
     *
     * @param field   the width of the field
     * @param content the width the rows of the list need
     * @param room    the room from the left edge of the window, less the margin, to the right edge of the field
     * @return the width of the list
     */
    static double width(double field, double content, double room) {
        return Math.max(field, Math.min(content, room));
    }

    /**
     * Keeps the open list of a field flush with its right edge and inside its window.
     *
     * @param field the field
     */
    public static void keepInWindow(ComboBox<?> field) {

        /*  Once shown, as the field may fill its list anew just before (see the favorites)  */
        field.addEventHandler(ComboBoxBase.ON_SHOWN, (event) -> {
            if (!(field.getSkin() instanceof ComboBoxListViewSkin<?> skin)
                    || !(skin.getPopupContent() instanceof ListView<?> list) || list.getScene() == null
                    || !(list.getScene().getWindow() instanceof PopupWindow popup))
                return;

            /*  The rows tell the width they need while they are not cut  */
            Object cutBefore = list.getProperties().remove(DropdownBounds.class);
            Bounds bounds = field.localToScreen(field.getLayoutBounds());
            list.setPrefWidth(Region.USE_COMPUTED_SIZE);
            double content = list.prefWidth(-1);
            double width = width(bounds.getWidth(), content,
                    bounds.getMaxX() - field.getScene().getWindow().getX() - MARGIN);
            list.setMinWidth(width);
            list.setPrefWidth(width);
            list.setMaxWidth(width);

            /*  The skin sized the list when it opened and does not size it down by itself  */
            list.resize(width, list.getHeight());
            alignRight(field, popup, width);

            /*  Rows wider than the list would scroll sideways, so they are cut to it and drawn anew  */
            if (width < content)
                list.getProperties().put(DropdownBounds.class, width - ROW_SPARE);
            if (!Objects.equals(cutBefore, list.getProperties().get(DropdownBounds.class)))
                list.refresh();
        });
    }

    /**
     * The width of a row of an open list: the width its name needs, but no wider than the list that cuts it.
     *
     * @param row     the row
     * @param natural the width the row needs
     * @return the width of the row
     */
    public static double rowWidth(ListCell<?> row, double natural) {
        ListView<?> list = row.getListView();
        return list != null && list.getProperties().get(DropdownBounds.class) instanceof Double max
                ? Math.min(natural, max) : natural;
    }

    /**
     * Keeps the open menu of a field flush with its right edge and at least as wide as the field. Its entries are
     * short, so the menu needs no room beyond the window.
     *
     * @param field the field
     */
    public static void keepInWindow(ChoiceBox<?> field) {
        Runnable fit = () -> {
            for (Window window : Window.getWindows())
                if (window instanceof ContextMenu menu && menu.isShowing() && menu.getOwnerNode() == field
                        && menu.getSkin() != null && menu.getSkin().getNode() instanceof Region content) {
                    Bounds bounds = field.localToScreen(field.getLayoutBounds());
                    content.setMinWidth(bounds.getWidth());
                    alignRight(field, menu, Math.max(bounds.getWidth(), content.prefWidth(-1)));
                    return;
                }
        };
        field.addEventHandler(ChoiceBox.ON_SHOWN, (event) -> fit.run());

        /*  The skin sizes the field to its entries while it opens the menu, the next layout gives it its width back  */
        field.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (field.isShowing())
                fit.run();
        });
    }

    /*  lays an open list of the given width flush with the right edge of its field, also once JavaFX laid it flush
        left again, as the list of a combo box does on the next layout of its field  */
    private static void alignRight(Node field, PopupWindow popup, double width) {
        if (popup.getProperties().put(DropdownBounds.class, width) == null)
            popup.anchorXProperty().addListener((obs, oldVal, newVal) -> place(field, popup));
        place(field, popup);
    }

    private static void place(Node field, PopupWindow popup) {
        Bounds bounds = field.localToScreen(field.getLayoutBounds());
        if (!popup.isShowing() || bounds == null)
            return;
        double left = bounds.getMaxX() - (Double) popup.getProperties().get(DropdownBounds.class);
        if (popup.getAnchorX() != left)
            popup.setAnchorX(left);
    }

    /**
     * Shows the whole name of a row in a tooltip while the list cuts it.
     *
     * @param name the name of the row
     */
    public static void showCutName(Label name) {
        Tooltip tooltip = new Tooltip();
        name.addEventHandler(MouseEvent.MOUSE_ENTERED, (event) -> {
            tooltip.setText(name.getText());
            name.setTooltip(name.getWidth() + 0.5 < name.prefWidth(-1) ? tooltip : null);
        });

        /*  A tooltip of the row would take over the one of the name  */
        name.addEventHandler(MouseEvent.MOUSE_MOVED, (event) -> {
            if (name.getTooltip() != null)
                event.consume();
        });
    }
}
