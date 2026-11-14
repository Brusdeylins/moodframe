package art.moodframe.ui.component;

import art.moodframe.model.CameraSettings;
import art.moodframe.model.CameraSettings.Aperture;
import art.moodframe.model.CameraSettings.Lens;
import art.moodframe.model.CameraSettings.Perspective;
import art.moodframe.model.CameraSettings.Shot;
import art.moodframe.util.I18nManager;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Schematic preview of a camera: from above the field of view as a cone with the subject and the range in focus as
 * a band across it, from the side the height of the camera in front of a standing figure.
 *
 * <p>A setting "any" is drawn as a dashed neutral default.</p>
 *
 * @since 2.0.0
 */
public class CameraPreview extends VBox {

    private static final double WIDTH = 300;
    private static final Color LINE = Color.gray(0.55);
    private static final Color CONE = Color.rgb(74, 144, 217, 0.25);
    private static final Color FOCUS = Color.rgb(255, 170, 40, 0.45);
    private static final Color SUBJECT = Color.rgb(74, 144, 217);

    /*  a person facing the viewer, below the head: neck, shoulders, hanging arms, body and legs, from the feet up  */
    private static final String FIGURE = "M -2.5 -66 L -2.5 -62 Q -10 -61.5 -11.5 -57 L -15 -33 Q -14 -28.5 -11.5 -31.5"
            + " L -9 -52 L -8 -52 L -7 -44 L -8.5 -36 L -6 -2 L -7.5 0 L -1.5 0 L -1 -31 Q 0 -33 1 -31 L 1.5 0 L 7.5 0"
            + " L 6 -2 L 8.5 -36 L 7 -44 L 8 -52 L 9 -52 L 11.5 -31.5 Q 14 -28.5 15 -33 L 11.5 -57 Q 10 -61.5 2.5 -62"
            + " L 2.5 -66 Z";

    private final Label topCaption = new Label();
    private final Label sideCaption = new Label();
    private final Canvas top = new Canvas(WIDTH, 190);
    private final Canvas side = new Canvas(WIDTH, 120);

    /**
     * Creates the preview, empty until {@link #show} is called.
     */
    public CameraPreview() {
        super(6);
        topCaption.getStyleClass().addAll("text-small", "text-muted");
        sideCaption.getStyleClass().addAll("text-small", "text-muted");
        getChildren().addAll(topCaption, top, sideCaption, side);
        updateTexts();
    }

    /** Sets the captions in the current language. */
    public void updateTexts() {
        topCaption.setText(I18nManager.getText("camera.dialog.preview.top"));
        sideCaption.setText(I18nManager.getText("camera.dialog.preview.side"));
    }

    /**
     * Draws the settings.
     *
     * @param settings the settings
     */
    public void show(CameraSettings settings) {
        drawTop(settings);
        drawSide(settings.perspective());
    }

    /*  The cone opens with the horizontal angle of view of the focal length on full frame and ends in an arc with a
        margin to the edges, the subject stands at the distance of the shot size, the band of focus is as deep as the
        aperture allows  */
    private void drawTop(CameraSettings settings) {
        GraphicsContext gc = top.getGraphicsContext2D();
        double height = top.getHeight();
        double x = WIDTH / 2;
        double y = height - 14;
        gc.clearRect(0, 0, WIDTH, height);

        double angle = angleOfView(settings.lens());
        double length = Math.min(height - 26, (WIDTH / 2 - 12) / Math.sin(Math.toRadians(angle / 2)));
        double subject = length * subjectDistance(settings.lens(), settings.shot());

        /*  the cone up to its arc, which clips the band of focus  */
        gc.save();
        gc.beginPath();
        gc.moveTo(x, y);
        gc.arc(x, y, length, length, 90 - angle / 2, angle);
        gc.closePath();
        gc.setFill(CONE);
        gc.fill();
        gc.setStroke(LINE);
        gc.setLineDashes(settings.lens() == null ? 4 : 0);
        gc.stroke();
        gc.clip();

        /*  the band of focus across the cone  */
        double[] focus = focusRange(settings.aperture(), settings.lens());
        if (focus != null) {
            double near = Math.max(subject - focus[0] * length, 0);
            double far = Math.min(subject + focus[1] * length, length);
            gc.setFill(FOCUS);
            gc.fillRect(0, y - far, WIDTH, far - near);
        }
        gc.restore();

        gc.setLineDashes(0);
        gc.setFill(SUBJECT);
        gc.fillOval(x - 5, y - subject - 5, 10, 10);
        drawCamera(gc, x, y, -90, 0, settings.body() == null);
    }

    /*  A standing figure, the camera at the height of the perspective, aimed at the head or the body  */
    private void drawSide(Perspective perspective) {
        GraphicsContext gc = side.getGraphicsContext2D();
        double height = side.getHeight();
        double ground = height - 12;
        double figure = WIDTH * 0.68;
        double head = ground - 72;
        gc.clearRect(0, 0, WIDTH, height);
        gc.setLineDashes(0);
        gc.setStroke(LINE);
        gc.setLineWidth(1);
        gc.strokeLine(10, ground, WIDTH - 10, ground);

        Perspective shown = perspective != null ? perspective : Perspective.EYE_LEVEL;
        double cameraX = shown == Perspective.TOP_DOWN ? figure : WIDTH * 0.2;
        double cameraY = switch (shown) {
            case LOW_ANGLE -> ground - 8;
            case HIGH_ANGLE -> head - 30;
            case TOP_DOWN -> 12;
            default -> head;
        };
        double targetX = shown == Perspective.TOP_DOWN ? figure : figure - 10;
        double targetY = shown == Perspective.LOW_ANGLE || shown == Perspective.HIGH_ANGLE ? ground - 48 : head;
        double aim = Math.toDegrees(Math.atan2(targetY - cameraY, targetX - cameraX));

        gc.setLineDashes(4);
        gc.strokeLine(cameraX, cameraY, targetX, targetY);

        /*  the figure over the line of sight, so the line ends at its outline  */
        gc.setFill(SUBJECT);
        gc.fillOval(figure - 6.5, head - 6.5, 13, 13);
        gc.save();
        gc.translate(figure, ground);
        gc.beginPath();
        gc.appendSVGPath(FIGURE);
        gc.fill();
        gc.restore();
        drawCamera(gc, cameraX, cameraY, aim, shown == Perspective.DUTCH_ANGLE ? 25 : 0, perspective == null);
    }

    /*  A camera body with its lens pointing in the direction, rolled by the tilt; dashed while the setting is "any"  */
    private static void drawCamera(GraphicsContext gc, double x, double y, double direction, double tilt, boolean any) {
        gc.save();
        gc.translate(x, y);
        gc.rotate(direction + tilt);
        gc.setLineDashes(any ? 3 : 0);
        gc.setFill(any ? Color.TRANSPARENT : LINE);
        gc.setStroke(LINE);
        gc.fillRect(-10, -6, 14, 12);
        gc.strokeRect(-10, -6, 14, 12);
        gc.fillPolygon(new double[] { 4, 10, 10 }, new double[] { 0, -5, 5 }, 3);
        gc.strokePolygon(new double[] { 4, 10, 10 }, new double[] { 0, -5, 5 }, 3);
        gc.restore();
    }

    /** The horizontal angle of view in degrees; a focal length on full frame, a special lens by its kind. */
    private static double angleOfView(Lens lens) {
        if (lens == null)
            return 50;
        return switch (lens) {
            case MACRO -> 30;
            case FISHEYE -> 180;
            case TILT_SHIFT -> 46;
            case ANAMORPHIC -> 70;
            default -> Math.toDegrees(2 * Math.atan(18.0 / lens.millimeters()));
        };
    }

    /** The distance of the subject as a share of the cone length. */
    private static double subjectDistance(Lens lens, Shot shot) {
        if (lens == Lens.MACRO)
            return 0.12;
        if (shot == null)
            return 0.5;
        return switch (shot) {
            case EXTREME_WIDE -> 0.85;
            case WIDE -> 0.65;
            case MEDIUM -> 0.45;
            case CLOSE_UP -> 0.3;
            case DETAIL -> 0.16;
        };
    }

    /** How far the focus reaches before and behind the subject, as shares of the cone length; null for "any". */
    private static double[] focusRange(Aperture aperture, Lens lens) {
        if (lens == Lens.TILT_SHIFT)
            return new double[] { 0.02, 0.02 };
        if (aperture == null)
            return null;
        return switch (aperture) {
            case F_1_4 -> new double[] { 0.02, 0.03 };
            case F_4 -> new double[] { 0.07, 0.12 };
            case F_11 -> new double[] { 1, 2 };
        };
    }
}
