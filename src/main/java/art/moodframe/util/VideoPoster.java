package art.moodframe.util;

import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Creates the poster image of a video tile: a frame of the video, taken with JavaFX Media (no external tool),
 * and stored as a JPEG next to the video, so it is created only once.
 *
 * @since 1.8.1
 */
public final class VideoPoster {

    private static final Logger logger = LoggerFactory.getLogger(VideoPoster.class);
    private static final double POSTER_WIDTH = 400.0;
    private static final double FRAME_AT_SECONDS = 0.5;
    private static final int TIMEOUT_SECONDS = 15;

    /** The poster of a video is taken one after the other: a chat with many videos must not start many players. */
    private static CompletableFuture<?> queue = CompletableFuture.completedFuture(null);

    private VideoPoster() {
    }

    /**
     * Returns the path of the poster file of a video, whether it exists or not.
     *
     * @param video the video file
     * @return the poster path ({@code <name>_poster.jpg} next to the video)
     */
    public static Path posterPath(Path video) {
        String name = video.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return video.resolveSibling((dot > 0 ? name.substring(0, dot) : name) + "_poster.jpg");
    }

    /**
     * Loads the poster of a video, and creates it from a frame of the video if it does not exist yet.
     *
     * @param video the video file
     * @return the poster, or a failed future if no frame could be taken
     */
    public static CompletableFuture<Image> load(Path video) {
        Path poster = posterPath(video);
        if (Files.isRegularFile(poster)) {
            return CompletableFuture.completedFuture(new Image(poster.toUri().toString(), POSTER_WIDTH, 0, true, true));
        }
        synchronized (VideoPoster.class) {
            CompletableFuture<Image> next = queue.handle((Object ignored, Throwable error) -> null)
                    .thenCompose((Object ignored) -> capture(video))
                    .thenApply((WritableImage frame) -> {
                        writeJpeg(frame, poster.toFile());
                        return (Image) frame;
                    });
            queue = next;
            return next;
        }
    }

    /** Takes a frame of the video on the FX thread: plays muted to the frame time, pauses and snapshots. */
    private static CompletableFuture<WritableImage> capture(Path video) {
        CompletableFuture<WritableImage> result = new CompletableFuture<WritableImage>()
                .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Platform.runLater(() -> {
            try {
                MediaPlayer player = new MediaPlayer(new Media(video.toUri().toString()));
                player.setMute(true);
                MediaView view = new MediaView(player);
                view.setFitWidth(POSTER_WIDTH);
                view.setPreserveRatio(true);
                new Scene(new Group(view));

                player.setOnError(() -> {
                    result.completeExceptionally(player.getError());
                    player.dispose();
                });
                player.setOnReady(() -> {
                    player.seek(Duration.seconds(Math.min(FRAME_AT_SECONDS, player.getTotalDuration().toSeconds() / 2)));
                    player.play();
                });
                player.currentTimeProperty().addListener((obs, old, time) -> {
                    if (time.toSeconds() >= Math.min(FRAME_AT_SECONDS, player.getTotalDuration().toSeconds() / 2)
                            && !result.isDone()) {
                        player.pause();
                        // One more pulse so that the paused frame is rendered
                        Platform.runLater(() -> {
                            result.complete(view.snapshot(null, null));
                            player.dispose();
                        });
                    }
                });
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    private static void writeJpeg(WritableImage frame, File target) {
        int width = (int) frame.getWidth();
        int height = (int) frame.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        PixelReader reader = frame.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, reader.getArgb(x, y) & 0xFFFFFF);
            }
        }
        try {
            ImageIO.write(image, "jpg", target);
        } catch (IOException e) {
            logger.warn("Could not store the video poster {}", target, e);
        }
    }
}
