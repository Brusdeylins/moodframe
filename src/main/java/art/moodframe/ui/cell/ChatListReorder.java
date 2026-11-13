package art.moodframe.ui.cell;

import art.moodframe.model.Chat;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
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

import java.util.List;

/**
 * Puts the chats of the list in another order: dragging the grip of a row shows a line in the accent color where the
 * chat lands and scrolls the list near its upper and lower edge; Esc or a release outside the list cancels. Alt+Up and
 * Alt+Down (Option on macOS) move the selected chat by one place.
 *
 * @since 2.0.0
 */
public class ChatListReorder {

    /** Moves a chat to a gap between the shown chats: 0 is above the first one, the number of chats below the last. */
    @FunctionalInterface
    public interface Mover {
        void move(Chat chat, int gap);
    }

    private static final DataFormat CHAT_ID = new DataFormat("application/x-moodframe-chat-id");

    /*  Near the upper and lower edge, the list scrolls by up to SCROLL_STEP pixels per frame, faster towards the edge  */
    private static final double SCROLL_ZONE = 24.0;
    private static final double SCROLL_STEP = 8.0;

    private final ListView<Chat> listView;
    private final Mover mover;
    private final Region line = new Region();
    private final Timeline scrolling;

    private Chat dragged;
    private int gap = -1;
    private double pointerY;

    /**
     * Lets the user reorder the chats of a list.
     *
     * @param listView the list of the chats
     * @param mover    moves a chat and stores the new order
     */
    public ChatListReorder(ListView<Chat> listView, Mover mover) {
        this.listView = listView;
        this.mover = mover;

        line.getStyleClass().add("chat-drop-line");
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

    /** Lets the grip of a cell drag its chat. */
    public void attach(ChatListCell cell) {
        Node grip = cell.getGrip();
        grip.setOnDragDetected((MouseEvent event) -> {
            if (cell.getItem() == null || !event.isPrimaryButtonDown())
                return;
            dragged = cell.getItem();
            Dragboard board = grip.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.put(CHAT_ID, dragged.getId());
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

    /*  Alt+Up and Alt+Down move the selected chat, except while its name is edited  */
    private void keyPressed(KeyEvent event) {
        if (!event.isAltDown() || event.getCode() != KeyCode.UP && event.getCode() != KeyCode.DOWN
                || event.getTarget() instanceof TextInputControl)
            return;
        Chat chat = listView.getSelectionModel().getSelectedItem();
        int index = listView.getSelectionModel().getSelectedIndex();
        int target = event.getCode() == KeyCode.UP ? index - 1 : index + 2;
        if (chat != null && target >= 0 && target <= listView.getItems().size()) {
            move(chat, target);
            if (flow() instanceof VirtualFlow<?> flow)
                flow.scrollTo(listView.getItems().indexOf(chat));
        }
        event.consume();
    }

    /*  The gap right above or below the chat itself leaves it where it is  */
    private void move(Chat chat, int target) {
        int index = listView.getItems().indexOf(chat);
        if (index >= 0 && target != index && target != index + 1)
            mover.move(chat, target);
    }

    /** The gap at a height of the list: above a chat in its upper half, below it in its lower half. */
    private int gapAt(double y) {
        for (ChatListCell cell : shownCells()) {
            Bounds bounds = bounds(cell);
            if (y >= bounds.getMinY() && y < bounds.getMaxY())
                return cell.isEmpty() ? listView.getItems().size()
                        : cell.getIndex() + (y < bounds.getCenterY() ? 0 : 1);
        }
        return gap;
    }

    /** Draws the line at a gap, above the chat that follows it or below the last chat; -1 hides it. */
    private void showGap(int gap) {
        this.gap = gap;
        int size = listView.getItems().size();
        int row = gap < size ? gap : size - 1;
        ChatListCell cell = gap < 0 ? null : shownCells().stream()
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
    private List<ChatListCell> shownCells() {
        return listView.lookupAll(".chat-list-cell").stream()
                .filter((node) -> node instanceof ChatListCell && node.isVisible() && node.getParent().isVisible())
                .map((node) -> (ChatListCell) node).toList();
    }

    private Bounds bounds(Node cell) {
        return listView.sceneToLocal(cell.localToScene(cell.getLayoutBounds()));
    }
}
