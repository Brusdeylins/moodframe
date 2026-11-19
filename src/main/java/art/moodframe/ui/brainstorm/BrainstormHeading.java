package art.moodframe.ui.brainstorm;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * The heading row of a column of the brainstorm window.
 *
 * @since 2.1.3
 */
final class BrainstormHeading {

    private BrainstormHeading() {
    }

    /**
     * The heading row of a column, as high as the heading rows of the main window, so that the headings of the three
     * columns share one baseline. The heading starts at the given left inset, the actions sit on the right.
     *
     * @param title   the heading
     * @param left    the inset of the heading
     * @param right   the inset of the actions
     * @param actions the buttons on the right
     * @return the row
     */
    static HBox row(Label title, double left, double right, Node... actions) {
        title.getStyleClass().add("title-4");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(5.0, title, spacer);
        row.getChildren().addAll(actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10.0, right, 15.0, left));
        row.setMinHeight(45.0);
        row.setPrefHeight(45.0);
        row.setMaxHeight(45.0);
        return row;
    }
}
