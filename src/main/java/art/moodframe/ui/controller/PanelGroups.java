package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import javafx.beans.InvalidationListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The groups of the right panel of the main window (Mood, Frame, Generation): each always shows its summary field,
 * the arrow or the title unfold its detail fields and fold the other groups, or fold it again; the unfolded group is
 * remembered. A line in the color of the group joins title, summary and detail fields. With the scroll bar of the
 * panel, the groups get narrower and keep a gap to it.
 *
 * @since 2.1.3
 */
final class PanelGroups {

    /*  the gap of the groups to the scroll bar of the right panel, in place of their padding to the window edge  */
    private static final double PANEL_SCROLL_BAR_GAP = 6.0;

    /*  The line of a group of the right panel: its middle from the left edge of the group, its start above the middle
        of the header, its gap below the summary field and its bend inwards at 45° (see main-window.css)  */
    private static final double PANEL_GROUP_LINE_X = 12.0;
    private static final double PANEL_GROUP_LINE_TOP = 9.0;
    private static final double PANEL_GROUP_LINE_GAP = 8.0;
    private static final double PANEL_GROUP_LINE_BEND = 14.0;

    /*  the groups of the right panel by their name in the configuration, in the order of the panel  */
    private final Map<String, VBox> panelGroups = new LinkedHashMap<>();

    private final ApplicationConfig config;

    /**
     * Sets up the groups and unfolds the one remembered.
     *
     * @param panelScrollPane the scroll pane of the panel
     * @param mood            the group Mood
     * @param frame           the group Frame
     * @param generation      the group Generation, whose line runs straight
     * @param config          remembers the unfolded group (may be null)
     */
    PanelGroups(ScrollPane panelScrollPane, VBox mood, VBox frame, VBox generation, ApplicationConfig config) {
        this.config = config;
        panelGroups.put("mood", mood);
        panelGroups.put("frame", frame);
        panelGroups.put("generation", generation);
        panelGroups.forEach((group, pane) -> {
            Runnable toggle = () -> open(pane.lookup(".panel-group-details").isVisible() ? "" : group);
            ((Button) pane.lookup(".panel-group-arrow")).setOnAction((event) -> toggle.run());
            title(pane).setOnMouseClicked((event) -> toggle.run());

            /*  The line follows the fields, which move and grow, e.g. with the fields of another model; it bends only
                in Mood and Frame, which save their detail fields  */
            boolean bends = !group.equals("generation");
            InvalidationListener redraw = (obs) -> drawPanelGroupLine(pane, bends);
            pane.lookup(".panel-group-header").boundsInParentProperty().addListener(redraw);
            pane.lookup(".panel-group-summary").boundsInParentProperty().addListener(redraw);
            pane.lookup(".panel-group-details").boundsInParentProperty().addListener(redraw);
            pane.lookup(".panel-group-details").visibleProperty().addListener(redraw);

            /*  The minimum width of a group follows the panel: without this, the widest field would keep it wide,
                e.g. when the scroll bar shows  */
            pane.setMinWidth(0);
        });
        show(config != null ? config.ui().getOpenPanelGroup() : "mood");

        /*  The scroll bar narrows the viewport: the right padding of the panel then shrinks to the gap to it  */
        Region panel = (Region) panelScrollPane.getContent();
        Insets padding = panel.getPadding();
        panelScrollPane.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> {
            Insets insets = panelScrollPane.getInsets();
            boolean scrollBar = newVal.getWidth()
                    < panelScrollPane.getWidth() - insets.getLeft() - insets.getRight() - 1.0;
            panel.setPadding(new Insets(padding.getTop(),
                    scrollBar ? PANEL_SCROLL_BAR_GAP : padding.getRight(), padding.getBottom(), padding.getLeft()));
        });
    }

    /*  the title of a group, the label in its header  */
    private static Label title(VBox pane) {
        return (Label) ((HBox) pane.lookup(".panel-group-header")).getChildren().stream()
                .filter(Label.class::isInstance).findFirst().orElseThrow();
    }

    /**
     * Unfolds a group as a click on its title does, folds the others and remembers it.
     *
     * @param open the name of the group to unfold, empty to fold all
     */
    void open(String open) {
        show(open);
        if (config != null)
            config.ui().setOpenPanelGroup(open);
    }

    /**
     * Unfolds the detail fields of one group of the right panel and folds those of the others, whose fields then
     * cannot take the focus either; the arrow of a group points down while it is unfolded, else to the right.
     *
     * @param open the name of the group to unfold, empty to fold all
     */
    private void show(String open) {
        panelGroups.forEach((group, pane) -> {
            boolean unfolded = group.equals(open);
            Node details = pane.lookup(".panel-group-details");
            details.setVisible(unfolded);
            details.setManaged(unfolded);
            ((FontIcon) ((Button) pane.lookup(".panel-group-arrow")).getGraphic())
                    .setIconLiteral(unfolded ? "mdi2c-chevron-down" : "mdi2c-chevron-right");
        });
    }

    /**
     * Draws the line of a group of the right panel: from the height of the title down beside the summary field, and
     * while the group is unfolded, on beside the detail fields, bent inwards at 45° below the summary field or
     * straight.
     *
     * @param pane  the group
     * @param bends whether the line bends inwards to the detail fields
     */
    private static void drawPanelGroupLine(VBox pane, boolean bends) {
        double top = pane.lookup(".panel-group-header").getBoundsInParent().getCenterY() - PANEL_GROUP_LINE_TOP;
        double summary = pane.lookup(".panel-group-summary").getBoundsInParent().getMaxY();
        Node details = pane.lookup(".panel-group-details");
        Path line = (Path) pane.lookup(".panel-group-line");
        line.getElements().setAll(new MoveTo(PANEL_GROUP_LINE_X, top));
        if (details.isVisible() && !bends)
            line.getElements().add(new LineTo(PANEL_GROUP_LINE_X,
                    Math.max(summary, details.getBoundsInParent().getMaxY())));
        else if (details.isVisible()) {
            double bend = summary + PANEL_GROUP_LINE_GAP;
            double inner = PANEL_GROUP_LINE_X + PANEL_GROUP_LINE_BEND;
            line.getElements().addAll(new LineTo(PANEL_GROUP_LINE_X, bend),
                    new LineTo(inner, bend + PANEL_GROUP_LINE_BEND),
                    new LineTo(inner, Math.max(bend + PANEL_GROUP_LINE_BEND, details.getBoundsInParent().getMaxY())));
        }
        else
            line.getElements().add(new LineTo(PANEL_GROUP_LINE_X, summary));
    }
}
