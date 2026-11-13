package art.moodframe.ui.cell;

import art.moodframe.ui.BoxInset;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SkinBase;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

/**
 * Puts the items of a list in another order, like the chats of the main window and the brainstorm sessions:
 * dragging the grip of a row shows a line in the accent color where the item lands and scrolls the list near its
 * upper and lower edge; Esc or a release outside the list cancels. Alt+Up and Alt+Down (Option on macOS) move the
 * selected item by one place.
 *
 * @param <T> the type of the items
 * @since 2.0.0
 */
public class ListReorder<T> {

    /** Moves an item to a gap between the shown items: 0 is above the first one, the number of items below the last. */
    @FunctionalInterface
    public interface Mover<T> {
        void move(T item, int gap);
    }

    private static final DataFormat ITEM_INDEX = new DataFormat("application/x-moodframe-list-index");

    /*  Near the upper and lower edge, the list scrolls by up to SCROLL_STEP pixels per frame, faster towards the edge  */
    private static final double SCROLL_ZONE = 24.0;
    private static final double SCROLL_STEP = 8.0;

    private final ListView<T> listView;
    private final Mover<T> mover;
    private final Region line = new Region();
    private final Timeline scrolling;

    private T dragged;
    private int gap = -1;
    private double pointerY;

    /**
     * Lets the user reorder the items of a list.
     *
     * @param listView the list of the items
     * @param mover    moves an item and stores the new order
     */
    public ListReorder(ListView<T> listView, Mover<T> mover) {
        this.listView = listView;
        this.mover = mover;

        line.setStyle("-fx-background-color: -color-accent-emphasis;");
        line.setManaged(false);
        line.setMouseTransparent(true);
        line.setVisible(false);

        scrolling = new Timeline(new KeyFrame(Duration.millis(16), (event) -> scroll()));
        scrolling.setCycleCount(Animation.INDEFINITE);

        listView.setOnDragOver(this::dragOver);
        listView.setOnDragDropped(this::dragDropped);
        listView.setOnDragExited((event) -> {
            scrolling.stop();
            showGap(-1);
        });
        listView.addEventFilter(KeyEvent.KEY_PRESSED, this::keyPressed);
    }

    /**
     * Creates the grip of a row, to be placed left in the row: it is hidden until the row shows it on hover, sits in
     * the empty inset left of the name, over the cell padding, so it takes no space and the name does not move.
     * Pressing it does not select the row.
     *
     * @return the grip
     */
    public static Label grip() {
        FontIcon gripIcon = new FontIcon("mdi2d-drag-vertical");
        gripIcon.setIconSize(14);
        Label grip = new Label(null, gripIcon);
        grip.getStyleClass().add("text-muted");
        grip.setAlignment(Pos.CENTER);
        grip.setMinWidth(BoxInset.LEFT);
        grip.setMaxWidth(BoxInset.LEFT);
        grip.setTranslateX(-BoxInset.LIST);
        grip.setMaxHeight(Double.MAX_VALUE);
        grip.setCursor(Cursor.OPEN_HAND);
        grip.setVisible(false);
        grip.addEventHandler(MouseEvent.MOUSE_PRESSED, Event::consume);
        return grip;
    }

    /** Lets the grip of a cell drag its item. */
    public void attach(ListCell<T> cell, Node grip) {
        grip.setOnDragDetected((MouseEvent event) -> {
            if (cell.getItem() == null || !event.isPrimaryButtonDown())
                return;
            dragged = cell.getItem();
            Dragboard board = grip.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.put(ITEM_INDEX, String.valueOf(cell.getIndex()));
            board.setContent(content);

            /*  The row hangs at the pointer where the grip was taken  */
            SnapshotParameters parameters = new SnapshotParameters();
            parameters.setFill(Color.TRANSPARENT);
            Point2D taken = cell.sceneToLocal(event.getSceneX(), event.getSceneY());
            board.setDragView(cell.snapshot(parameters, null), taken.getX(), taken.getY());
            event.consume();
        });
        grip.setOnDragDone((event) -> endDrag());
    }

    private void dragOver(DragEvent event) {
        if (dragged == null)
            return;
        event.acceptTransferModes(TransferMode.MOVE);
        pointerY = event.getY();
        showGap(gapAt(pointerY));
        if (scrollSpeed() != 0)
            scrolling.play();
        else
            scrolling.stop();
        event.consume();
    }

    private void dragDropped(DragEvent event) {
        if (dragged == null || gap < 0)
            return;
        move(dragged, gap);
        event.setDropCompleted(true);
        event.consume();
        endDrag();
    }

    private void endDrag() {
        dragged = null;
        scrolling.stop();
        showGap(-1);
    }

    /*  Alt+Up and Alt+Down move the selected item, except while its name is edited  */
    private void keyPressed(KeyEvent event) {
        if (!event.isAltDown() || event.getCode() != KeyCode.UP && event.getCode() != KeyCode.DOWN
                || event.getTarget() instanceof TextInputControl)
            return;
        T item = listView.getSelectionModel().getSelectedItem();
        int index = listView.getSelectionModel().getSelectedIndex();
        int target = event.getCode() == KeyCode.UP ? index - 1 : index + 2;
        if (item != null && target >= 0 && target <= listView.getItems().size()) {
            move(item, target);
            if (flow() instanceof VirtualFlow<?> flow)
                flow.scrollTo(listView.getItems().indexOf(item));
        }
        event.consume();
    }

    /*  The gap right above or below the item itself leaves it where it is  */
    private void move(T item, int target) {
        int index = listView.getItems().indexOf(item);
        if (index >= 0 && target != index && target != index + 1)
            mover.move(item, target);
    }

    /** The gap at a height of the list: above an item in its upper half, below it in its lower half. */
    private int gapAt(double y) {
        for (ListCell<?> cell : shownCells()) {
            Bounds bounds = bounds(cell);
            if (y >= bounds.getMinY() && y < bounds.getMaxY())
                return cell.isEmpty() ? listView.getItems().size()
                        : cell.getIndex() + (y < bounds.getCenterY() ? 0 : 1);
        }
        return gap;
    }

    /** Draws the line at a gap, above the item that follows it or below the last item; -1 hides it. */
    private void showGap(int gap) {
        this.gap = gap;
        int size = listView.getItems().size();
        int row = gap < size ? gap : size - 1;
        ListCell<?> cell = gap < 0 ? null : shownCells().stream()
                .filter((shown) -> !shown.isEmpty() && shown.getIndex() == row).findFirst().orElse(null);
        if (cell == null) {
            line.setVisible(false);
            return;
        }
        if (line.getParent() == null && listView.getSkin() instanceof SkinBase<?> skin)
            skin.getChildren().add(line);

        /*  A row partly scrolled out keeps the line at the edge of the list  */
        Bounds bounds = bounds(cell);
        double y = (gap < size ? bounds.getMinY() : bounds.getMaxY()) - 1;
        y = Math.max(0, Math.min(y, listView.getHeight() - 2));
        line.resizeRelocate(bounds.getMinX(), y, bounds.getWidth(), 2);
        line.setVisible(true);
    }

    private void scroll() {
        double speed = scrollSpeed();
        if (speed == 0 || !(flow() instanceof VirtualFlow<?> flow)) {
            scrolling.stop();
            return;
        }
        flow.scrollPixels(speed);
        showGap(gapAt(pointerY));
    }

    /** Pixels to scroll per frame, upwards below 0; 0 outside the zones at the edges. */
    private double scrollSpeed() {
        double toBottom = listView.getHeight() - pointerY;
        if (pointerY < SCROLL_ZONE)
            return -SCROLL_STEP * (1 - Math.max(0, pointerY) / SCROLL_ZONE);
        if (toBottom < SCROLL_ZONE)
            return SCROLL_STEP * (1 - Math.max(0, toBottom) / SCROLL_ZONE);
        return 0;
    }

    private Node flow() {
        return listView.lookup(".virtual-flow");
    }

    /*  The list keeps unused cells and a cell for measuring hidden  */
    private List<ListCell<?>> shownCells() {
        return listView.lookupAll(".list-cell").stream()
                .filter((node) -> node instanceof ListCell<?> cell && cell.getListView() == listView
                        && node.isVisible() && node.getParent().isVisible())
                .<ListCell<?>>map((node) -> (ListCell<?>) node).toList();
    }

    private Bounds bounds(Node cell) {
        return listView.sceneToLocal(cell.localToScene(cell.getLayoutBounds()));
    }
}
