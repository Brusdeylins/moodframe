package art.moodframe.ui.component;

import art.moodframe.model.GeneratedImage;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

/**
 * The pixel size and the format of an image as a small, dark line at the bottom center of the image, shown while the
 * pointer is over it, for example "2752 × 1536 · PNG"; in the images view above the line of mood and frame. A tile
 * too small for it leaves it out, the tooltip of the image then names the size. Used by the chat entries and the
 * images view alike.
 *
 * @since 2.0.0
 */
public class ImageSizeLabel extends Label {

    /*  The distance of the line from the bottom edge of the image and from the line beneath it  */
    private static final double MARGIN = 5.0;
    private static final double GAP = 3.0;

    private final GeneratedImage image;

    /**
     * Creates the line, hidden until it is attached to an image.
     *
     * @param image the image whose size it shows
     */
    public ImageSizeLabel(GeneratedImage image) {
        this.image = image;
        getStyleClass().add("image-size");
        getStylesheets().add(ImageSizeLabel.class.getResource("/css/image-size.css").toExternalForm());
        setVisible(false);
    }

    /**
     * Shows the line at the bottom center of a finished image while the pointer is over it, above the line beneath it
     * if any. It gives way while the size is unknown, or on a tile so small that the lines would cover half the image
     * or the line would not fit across it. The pane does not lay out the line, so that it never widens a narrow image.
     *
     * @param tile  the pane holding the image
     * @param below the line beneath it, attached and laid out before, null if none
     */
    public void attachTo(Pane tile, Region below) {
        setManaged(false);
        tile.getChildren().add(this);
        tile.hoverProperty().addListener((obs, wasOver, isOver) -> {
            String size = image.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED ? text(image) : null;
            boolean shown = false;
            if (isOver && size != null) {
                setText(size);
                applyCss();
                autosize();
                double bottom = tile.getHeight() - MARGIN
                        - (below != null && below.isVisible() ? below.getHeight() + GAP : 0.0);
                shown = tile.getHeight() - bottom + getHeight() <= tile.getHeight() / 2
                        && getWidth() <= tile.getWidth() - 2 * MARGIN;
                relocate(Math.round((tile.getWidth() - getWidth()) / 2), Math.round(bottom - getHeight()));
            }
            setVisible(shown);
        });
    }

    /**
     * The pixel size and the format of an image, the format from the extension of its file if not stored.
     *
     * @param image the image
     * @return the text, for example "2752 × 1536 · PNG", null for a video or while the size is unknown
     */
    static String text(GeneratedImage image) {
        if (image.isVideo() || image.getWidth() <= 0 || image.getHeight() <= 0)
            return null;
        String format = image.getFormat();
        if (format == null || format.isEmpty() || "UNKNOWN".equals(format)) {
            String path = image.getLocalFilePath() != null ? image.getLocalFilePath().toLowerCase() : "";
            if (path.endsWith(".png"))
                format = "PNG";
            else if (path.endsWith(".jpg") || path.endsWith(".jpeg"))
                format = "JPEG";
            else if (path.endsWith(".webp"))
                format = "WEBP";
            else
                format = "IMG";
        }
        return image.getWidth() + " × " + image.getHeight() + " · " + format.toUpperCase();
    }
}
