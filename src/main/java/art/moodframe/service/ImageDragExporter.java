package art.moodframe.service;

import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/**
 * Starts an outbound OS drag-and-drop for a generated image file, so the user can
 * drag a finished image out of the application into the Finder/Explorer (the file is
 * copied to the drop target) or into a PowerPoint slide / image placeholder.
 *
 * <p>The drag carries both a file reference ({@code putFiles}) and the bitmap data
 * ({@code putImage}) for maximum target-application compatibility, and shows a drag
 * preview ("ghost") snapshotted from the rendered image so the user sees what is being
 * dragged.
 */
public final class ImageDragExporter {
    private static final Logger logger = LoggerFactory.getLogger(ImageDragExporter.class);

    private ImageDragExporter() {
    }

    /**
     * Begins a copy drag-and-drop gesture exporting the given image file.
     *
     * @param source    the ImageView the drag originates from; its rendered content is
     *                  snapshotted to produce the drag preview
     * @param imageFile the existing image file on disk to export
     */
    public static void startDrag(ImageView source, File imageFile) {
        Dragboard db = source.startDragAndDrop(TransferMode.COPY);
        ClipboardContent content = new ClipboardContent();
        content.putFiles(List.of(imageFile));

        // Drag preview ("ghost"): snapshot the rendered ImageView so it matches exactly what
        // the user sees (correct size and aspect ratio), with a transparent background.
        Image rendered = source.getImage();
        if (rendered != null) {
            try {
                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.TRANSPARENT);
                Image dragView = source.snapshot(params, null);
                db.setDragView(dragView, dragView.getWidth() / 2, dragView.getHeight() / 2);
            }
            catch (Exception e) {
                logger.debug("Could not build drag preview for {}", imageFile.getName(), e);
            }
            content.putImage(rendered); // bitmap data for apps that accept images directly
        }

        db.setContent(content);
        logger.debug("Started outbound image drag for {}", imageFile.getName());
    }
}
