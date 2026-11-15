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
 * Keeps the open list of a field below its field. JavaFX makes a list as wide as its widest row, so in the right panel
 * a long name lets it reach past the panel. Here the list is as wide as its field and flush with its left edge; names
 * that do not fit end in "…" and show in full in a tooltip ({@link #showCutName}). It opens downwards or upwards as
 * before.
 *
 * @since 2.0.0
 */
public final class DropdownBounds {

    /** The space a list keeps beside its rows for its edges and scroll bar, as its skin reckons. */
    private static final double ROW_SPARE = 30;

    private DropdownBounds() {
    }

    /**
     * Keeps the open list of a field as wide as the field and flush with its left edge.
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
            list.setPrefWidth(Region.USE_COMPUTED_SIZE);
            double content = list.prefWidth(-1);
            double width = field.getWidth();
            list.setMinWidth(width);
            list.setPrefWidth(width);
            list.setMaxWidth(width);

            /*  The skin sized the list when it opened and does not size it down by itself  */
            list.resize(width, list.getHeight());
            alignLeft(field, popup);

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
     * Keeps the open menu of a field flush with its left edge and at least as wide as the field. Its entries are
     * short, so the menu needs no more room than the field.
     *
     * @param field the field
     */
    public static void keepInWindow(ChoiceBox<?> field) {
        Runnable fit = () -> {
            for (Window window : Window.getWindows())
                if (window instanceof ContextMenu menu && menu.isShowing() && menu.getOwnerNode() == field
                        && menu.getSkin() != null && menu.getSkin().getNode() instanceof Region content) {
                    content.setMinWidth(field.getWidth());
                    alignLeft(field, menu);
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

    /*  lays an open list flush with the left edge of its field, also once JavaFX moved it, as it does with a list
        that opened wider than the room right of the field, on the next layout of the field  */
    private static void alignLeft(Node field, PopupWindow popup) {
        if (popup.getProperties().put(DropdownBounds.class, Boolean.TRUE) == null)
            popup.anchorXProperty().addListener((obs, oldVal, newVal) -> place(field, popup));
        place(field, popup);
    }

    private static void place(Node field, PopupWindow popup) {
        Bounds bounds = field.localToScreen(field.getLayoutBounds());
        if (popup.isShowing() && bounds != null && popup.getAnchorX() != bounds.getMinX())
            popup.setAnchorX(bounds.getMinX());
    }

    /**
     * Ends the name of a row in "…" while the list cuts it and shows it whole in a tooltip.
     *
     * @param name the name of the row
     */
    public static void showCutName(Label name) {
        name.setEllipsisString("…");
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
