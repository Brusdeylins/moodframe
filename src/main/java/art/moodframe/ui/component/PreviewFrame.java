package art.moodframe.ui.component;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * The mood frame of the large preview: a 2 px line in the colors of the wordmark (orange, yellow, green, blue and
 * back to orange) directly outside the shown image or video, with a soft glow of the same colors. While it runs,
 * the colors wander clockwise around the frame, one round in 20 seconds. JavaFX has no conic gradient, so each of
 * the four edges carries a repeating linear gradient whose period is the perimeter, shifted by the same phase.
 *
 * @since 2.2.0
 */
public class PreviewFrame extends Group {

    private static final double LINE = 2.0;
    private static final double GLOW = 6.0;
    private static final double GLOW_RADIUS = 10.0;
    private static final double GLOW_OPACITY = 0.5;
    private static final Duration ROUND = Duration.seconds(20.0);

    /*  The colors of the wordmark as a closed loop, the same in all themes  */
    private static final Stop[] STOPS = {
        new Stop(0.0,  Color.web("#ff6b4a")), new Stop(0.25, Color.web("#f5b83d")),
        new Stop(0.5,  Color.web("#2fb8a6")), new Stop(0.75, Color.web("#5b6ee1")),
        new Stop(1.0,  Color.web("#ff6b4a"))
    };

    /*  top, right, bottom, left: the line and, behind it, the band that blurs into the glow  */
    private final Rectangle[] lines = new Rectangle[4];
    private final Rectangle[] glows = new Rectangle[4];

    private final DoubleProperty phase = new SimpleDoubleProperty(this, "phase", 0.0);
    private final Timeline rotation;
    private Bounds framed;

    public PreviewFrame() {
        Group glow = new Group();
        glow.setOpacity(GLOW_OPACITY);
        for (int i = 0; i < 4; i++) {
            lines[i] = new Rectangle();
            glows[i] = new Rectangle();
            glows[i].setEffect(new GaussianBlur(GLOW_RADIUS));
            glow.getChildren().add(glows[i]);
        }
        getChildren().add(glow);
        getChildren().addAll(lines);
        setManaged(false);
        setMouseTransparent(true);
        setVisible(false);

        /*  The phase runs linearly from 0 to 1 and starts over, where 1 looks exactly like 0  */
        rotation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(phase, 0.0, Interpolator.LINEAR)),
                new KeyFrame(ROUND, new KeyValue(phase, 1.0, Interpolator.LINEAR)));
        rotation.setCycleCount(Animation.INDEFINITE);
        phase.addListener((observable) -> repaint());
    }

    /**
     * Frames the given bounds, in the coordinates of the parent of this frame.
     *
     * @param bounds the bounds of the shown image or video, or null or empty for no frame
     */
    public void frame(Bounds bounds) {
        if (bounds == null || bounds.getWidth() <= 0.0 || bounds.getHeight() <= 0.0) {
            framed = null;
            setVisible(false);
            return;
        }
        framed = new BoundingBox(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
        place(lines, LINE);
        place(glows, GLOW);
        repaint();
        setVisible(true);
    }

    /*  The edges lie outside the bounds, top and bottom across the corners, left and right between them  */
    private void place(Rectangle[] edges, double width) {
        double x0 = framed.getMinX();
        double y0 = framed.getMinY();
        double x1 = framed.getMaxX();
        double y1 = framed.getMaxY();
        set(edges[0], x0 - width, y0 - width, x1 - x0 + 2 * width, width);
        set(edges[1], x1,         y0,         width,               y1 - y0);
        set(edges[2], x0 - width, y1,         x1 - x0 + 2 * width, width);
        set(edges[3], x0 - width, y0,         width,               y1 - y0);
    }

    private static void set(Rectangle edge, double x, double y, double width, double height) {
        edge.setX(x);
        edge.setY(y);
        edge.setWidth(width);
        edge.setHeight(height);
    }

    /*  The color at the distance s clockwise from the top left corner is the gradient at (s - shift) / perimeter  */
    private void repaint() {
        if (framed == null)
            return;
        double x0 = framed.getMinX();
        double y0 = framed.getMinY();
        double x1 = framed.getMaxX();
        double y1 = framed.getMaxY();
        double w = framed.getWidth();
        double h = framed.getHeight();
        double perimeter = 2 * (w + h);
        double shift = phase.get() * perimeter;
        double top    = x0 + shift;
        double right  = y0 - w + shift;
        double bottom = x1 + w + h - shift;
        double left   = y1 + 2 * w + h - shift;
        paint(0, new LinearGradient(top, 0.0, top + perimeter, 0.0, false, CycleMethod.REPEAT, STOPS));
        paint(1, new LinearGradient(0.0, right, 0.0, right + perimeter, false, CycleMethod.REPEAT, STOPS));
        paint(2, new LinearGradient(bottom, 0.0, bottom - perimeter, 0.0, false, CycleMethod.REPEAT, STOPS));
        paint(3, new LinearGradient(0.0, left, 0.0, left - perimeter, false, CycleMethod.REPEAT, STOPS));
    }

    private void paint(int edge, LinearGradient gradient) {
        lines[edge].setFill(gradient);
        glows[edge].setFill(gradient);
    }

    /**
     * Lets the colors wander around the frame, or stops them where they are.
     *
     * @param running true to turn, false to stand still (no animation runs then)
     */
    public void setRunning(boolean running) {
        if (running)
            rotation.play();
        else
            rotation.pause();
    }

    /**
     * Whether the colors wander around the frame.
     *
     * @return true while the animation runs
     */
    public boolean isRunning() {
        return rotation.getStatus() == Animation.Status.RUNNING;
    }

    /**
     * Where the colors stand in their round.
     *
     * @return the phase from 0 to 1
     */
    public double getPhase() {
        return phase.get();
    }

    /**
     * The bounds the frame surrounds.
     *
     * @return the framed bounds, or null if there is no frame
     */
    public Bounds getFramed() {
        return framed;
    }
}
