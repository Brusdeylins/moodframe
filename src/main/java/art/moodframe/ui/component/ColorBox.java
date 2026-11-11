package art.moodframe.ui.component;

import javafx.geometry.Insets;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

/**
 * A small color display box component that shows a hex color or a crossed-out state.
 *
 * <p>When a color is set, the box displays that color. When no color is set (null or empty),
 * the box shows a white background with crossed lines to indicate "not defined".</p>
 *
 * <p>A tooltip displays the hex color code when hovering over the box.</p>
 *
 * @since 1.3.0
 */
public class ColorBox extends StackPane {

    private static final double DEFAULT_SIZE = 16.0;

    private final Rectangle colorRect;
    private final Line crossLine1;
    private final Line crossLine2;
    private String hexColor;
    private final double boxSize;

    /**
     * Creates a new ColorBox with default size (16x16px) and no color.
     */
    public ColorBox() {
        this(null, DEFAULT_SIZE);
    }

    /**
     * Creates a new ColorBox with default size and the specified color.
     *
     * @param hexColor the hex color code (e.g., "#48BDFF"), or null for empty state
     */
    public ColorBox(String hexColor) {
        this(hexColor, DEFAULT_SIZE);
    }

    /**
     * Creates a new ColorBox with the specified color and size.
     *
     * @param hexColor the hex color code, or null for empty state
     * @param size the box size in pixels
     */
    public ColorBox(String hexColor, double size) {
        this.hexColor = hexColor;
        this.boxSize = size;

        // Color rectangle
        colorRect = new Rectangle(boxSize, boxSize);
        colorRect.setArcWidth(2);
        colorRect.setArcHeight(2);
        colorRect.setStroke(Color.GRAY);
        colorRect.setStrokeWidth(0.5);

        // Cross lines for "empty" state
        double padding = 3;
        crossLine1 = new Line(padding, padding, boxSize - padding, boxSize - padding);
        crossLine2 = new Line(boxSize - padding, padding, padding, boxSize - padding);
        crossLine1.setStroke(Color.LIGHTGRAY);
        crossLine2.setStroke(Color.LIGHTGRAY);
        crossLine1.setStrokeWidth(1.5);
        crossLine2.setStrokeWidth(1.5);

        getChildren().addAll(colorRect, crossLine1, crossLine2);
        setPadding(new Insets(1));

        updateDisplay();
    }

    /**
     * Sets the color to display.
     *
     * @param hexColor the hex color code (e.g., "#48BDFF"), or null to show empty state
     */
    public void setColor(String hexColor) {
        this.hexColor = hexColor;
        updateDisplay();
    }

    /**
     * Gets the current color.
     *
     * @return the hex color code, or null if empty
     */
    public String getColor() {
        return hexColor;
    }

    /**
     * Checks if a color is set.
     *
     * @return true if a valid color is set, false otherwise
     */
    public boolean hasColor() {
        return hexColor != null && !hexColor.isEmpty();
    }

    /**
     * Clears the color, showing the empty (crossed-out) state.
     */
    public void clearColor() {
        setColor(null);
    }

    /**
     * Updates the visual display based on the current color state.
     */
    private void updateDisplay() {
        if (hasColor()) {
            try {
                colorRect.setFill(Color.web(hexColor));
                crossLine1.setVisible(false);
                crossLine2.setVisible(false);
                Tooltip.install(this, new Tooltip(hexColor));
            } catch (IllegalArgumentException e) {
                // Invalid hex color, show as empty
                showEmpty();
            }
        } else {
            showEmpty();
        }
    }

    /**
     * Shows the empty (crossed-out) state.
     */
    private void showEmpty() {
        colorRect.setFill(Color.WHITE);
        crossLine1.setVisible(true);
        crossLine2.setVisible(true);
        Tooltip.install(this, new Tooltip("-"));
    }

    /**
     * Gets the size of the color box.
     *
     * @return the box size in pixels
     */
    public double getBoxSize() {
        return boxSize;
    }
}
