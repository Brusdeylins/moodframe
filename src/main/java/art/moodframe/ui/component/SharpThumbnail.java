package art.moodframe.ui.component;

import art.moodframe.service.ThumbnailManager;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Window;

/**
 * Keeps a thumbnail sharp: once its view shows it larger than its pixels on the screen, like a thumbnail of 400 px
 * from before on a Retina screen, the thumbnail is created anew at full size in the background and takes the place
 * of the old one, which stays in view until then. Used by the chat entries and the images view alike.
 *
 * @since 2.0.0
 */
final class SharpThumbnail {

    private SharpThumbnail() {
    }

    /**
     * Follows the image of a view, its size and the scale of the screen it is on, until the thumbnail is too small
     * once; then it asks for the larger thumbnail and shows it.
     *
     * @param view       the view showing the thumbnail of an image
     * @param path       the file of the image
     * @param thumbnails the thumbnails
     */
    static void follow(ImageView view, String path, ThumbnailManager thumbnails) {
        boolean[] requested = { false };
        InvalidationListener check = (obs) -> {
            Image image = view.getImage();
            Window window = view.getScene() != null ? view.getScene().getWindow() : null;
            if (requested[0] || image == null || window == null || !isTooSmall(image.getWidth(), image.getHeight(),
                    view.getFitWidth(), view.getFitHeight(), window.getOutputScaleX()))
                return;
            requested[0] = true;
            thumbnails.enlargeThumbnail(path)
                    .thenApplyAsync((enlarged) -> enlarged
                            ? thumbnails.getCachedImage(thumbnails.getThumbnailPath(path)) : null)
                    .thenAccept((larger) -> Platform.runLater(() -> {
                        if (larger != null && view.getImage() == image)
                            view.setImage(larger);
                    }));
        };
        view.imageProperty().addListener(check);
        view.fitWidthProperty().addListener(check);
        view.fitHeightProperty().addListener(check);

        /*  The window outlives the view, so it holds the check only weakly; the view holds it strongly  */
        view.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null && scene.getWindow() != null)
                scene.getWindow().outputScaleXProperty().addListener(new WeakInvalidationListener(check));
            check.invalidated(obs);
        });
    }

    /**
     * Whether a view shows its image larger than its pixels on the screen, so it looks blurred; a thumbnail of full
     * size never counts as too small.
     *
     * @param width       the width of the image in pixels
     * @param height      the height of the image in pixels
     * @param fitWidth    the width the view fits the image into
     * @param fitHeight   the height the view fits the image into
     * @param outputScale the scale of the screen
     * @return true if the image is too small for the view
     */
    static boolean isTooSmall(double width, double height, double fitWidth, double fitHeight, double outputScale) {
        if (width <= 0 || height <= 0 || Math.max(width, height) >= ThumbnailManager.MAX_THUMBNAIL_SIZE)
            return false;
        return Math.min(fitWidth / width, fitHeight / height) * outputScale > 1.0;
    }
}
