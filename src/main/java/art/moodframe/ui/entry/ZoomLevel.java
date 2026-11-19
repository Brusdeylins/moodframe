package art.moodframe.ui.entry;

/**
 * The zoom of the full view and the limits of the pan, without the nodes: zoom 1.0 shows the image in its own pixels,
 * the fit zoom fits it into the safe area between the buttons without enlarging a small image. The zoom stays between
 * the fit zoom and 1.0; a larger image pans until its edge reaches the safe area.
 *
 * @since 2.1.3
 */
final class ZoomLevel {

    /** The step of the mouse wheel. */
    static final double FACTOR = 1.2;

    /*  how close a zoom counts as 100 % or as fitted for the zoom button  */
    private static final double TOLERANCE = 0.05;

    private double zoom = 1.0;
    private double min = 0.1;
    private double max = 1.0;
    private double fit = 1.0;

    /** The zoom: the screen pixels per pixel of the image. */
    double zoom() {
        return zoom;
    }

    /** The zoom that fits the image into the safe area, at most 1.0. */
    double fit() {
        return fit;
    }

    /** The least zoom, the fit zoom once an image is known. */
    double min() {
        return min;
    }

    /**
     * Sets the limits for an image of a pixel size in a safe area; the zoom itself stays.
     *
     * @param imageWidth  the width of the image in pixels
     * @param imageHeight the height of the image in pixels
     * @param areaWidth   the width of the safe area
     * @param areaHeight  the height of the safe area
     */
    void limit(double imageWidth, double imageHeight, double areaWidth, double areaHeight) {
        fit = Math.min(1.0, Math.min(areaWidth / imageWidth, areaHeight / imageHeight));
        max = 1.0;

        /*  also below 0.1 in a very small window  */
        min = fit;
    }

    /** Sets the limits while no image is shown. */
    void clear() {
        max = 1.0;
        min = 0.1;
        fit = 1.0;
    }

    /**
     * Sets the limits for a new size of the safe area: a fitted image is fitted anew, a zoomed one keeps its zoom
     * within the new limits.
     *
     * @param imageWidth  the width of the image in pixels
     * @param imageHeight the height of the image in pixels
     * @param areaWidth   the width of the safe area
     * @param areaHeight  the height of the safe area
     */
    void refit(double imageWidth, double imageHeight, double areaWidth, double areaHeight) {
        boolean fitted = zoom <= fit * 1.001;
        limit(imageWidth, imageHeight, areaWidth, areaHeight);
        zoom = fitted ? fit : Math.max(min, Math.min(zoom, max));
    }

    /**
     * Zooms in by a step of the wheel, at most to 1.0.
     *
     * @return whether the zoom changed
     */
    boolean zoomIn() {
        if (zoom >= max)
            return false;
        zoom = Math.min(zoom * FACTOR, max);
        return true;
    }

    /**
     * Zooms out by a step of the wheel, at least to the fit zoom.
     *
     * @return whether the zoom changed
     */
    boolean zoomOut() {
        if (zoom <= min)
            return false;
        zoom = Math.max(zoom / FACTOR, min);
        return true;
    }

    /** Shows the image in its own pixels. */
    void toOriginal() {
        zoom = 1.0;
    }

    /** Fits the image into the safe area. */
    void toFit() {
        zoom = fit;
    }

    /** Whether the image shows in its own pixels, for the zoom button. */
    boolean isOriginal() {
        return Math.abs(zoom - 1.0) < TOLERANCE;
    }

    /** Whether the image is fitted, for the zoom button. */
    boolean isFit() {
        return Math.abs(zoom - fit) < TOLERANCE;
    }

    /** Whether the zoom button can show the image in its own pixels. */
    boolean canZoomToOriginal() {
        return 1.0 <= max && Math.abs(zoom - 1.0) > TOLERANCE;
    }

    /** Whether the zoom button can fit the image. */
    boolean canZoomToFit() {
        return fit >= min && Math.abs(zoom - fit) > TOLERANCE;
    }

    /**
     * Keeps the pan of an image along one axis within the safe area: a larger image pans until its edge lies on the
     * edge of the safe area, one that fits stays centered.
     *
     * @param translate the pan from the center
     * @param imageSize the size of the image on the screen along the axis
     * @param areaSize  the size of the safe area along the axis
     * @return the pan within the limits
     */
    static double clampPan(double translate, double imageSize, double areaSize) {
        double limit = imageSize > areaSize ? (imageSize - areaSize) / 2 : 0;
        return Math.max(-limit, Math.min(limit, translate));
    }
}
