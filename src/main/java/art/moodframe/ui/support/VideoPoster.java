package art.moodframe.ui.support;

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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Creates the poster image of a video tile: a frame of the video, taken with JavaFX Media, or with ffmpeg where
 * JavaFX Media cannot play the video, and stored as a JPEG next to the video, so it is created only once.
 *
 * @since 1.8.1
 * @updated 2.1.0 - Frame via ffmpeg where JavaFX Media cannot play the video
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
     * Loads the poster of a video, and creates it from a frame of the video if it does not exist yet: with JavaFX
     * Media, and if that fails or JavaFX Media is known to be unusable, with ffmpeg if it is in the PATH. A stored
     * poster is read and decoded on the {@link DiskWorker}, never on the calling thread.
     *
     * @param video the video file
     * @return the poster, or a failed future if no frame could be taken
     */
    public static CompletableFuture<Image> load(Path video) {
        return load(video, null);
    }

    /**
     * Loads the poster of a video like {@link #load(Path)}, a stored one through a cache, so the rows that show the
     * video again take it without reading the disk.
     *
     * @param video the video file
     * @param cache the decoded images by path, null for none
     * @return the poster, or a failed future if no frame could be taken
     */
    public static CompletableFuture<Image> load(Path video, ImageCache cache) {
        return load(video, cache, DiskWorker.get());
    }

    /*  Loads the poster, a stored one read and decoded on the given executor  */
    static CompletableFuture<Image> load(Path video, ImageCache cache, Executor disk) {
        Path poster = posterPath(video);
        Image cached = cache != null ? cache.getIfCached(poster.toString()) : null;
        if (cached != null)
            return CompletableFuture.completedFuture(cached);
        return CompletableFuture.supplyAsync(() -> cache != null ? cache.get(poster.toString(), VideoPoster::decode)
                        : decode(poster.toString()), disk)
                .thenCompose((Image stored) -> stored != null ? CompletableFuture.completedFuture(stored)
                        : create(video, poster));
    }

    /*  Decodes a stored poster completely, null if there is none yet  */
    private static Image decode(String poster) {
        Path file = Path.of(poster);
        if (!Files.isRegularFile(file))
            return null;
        Image image = new Image(file.toUri().toString(), POSTER_WIDTH, 0, true, true);
        return image.isError() ? null : image;
    }

    /*  Creates the poster from a frame of the video, one video after the other  */
    private static CompletableFuture<Image> create(Path video, Path poster) {
        synchronized (VideoPoster.class) {
            CompletableFuture<Image> next = queue.handle((Object ignored, Throwable error) -> null)
                    .thenCompose((Object ignored) -> MediaSupport.isUsable()
                            ? capture(video)
                                    .thenApply((WritableImage frame) -> {
                                        writeJpeg(frame, poster.toFile());
                                        return (Image) frame;
                                    })
                                    .exceptionallyCompose((Throwable error) -> {
                                        MediaSupport.failed(error);
                                        return extract(video, poster);
                                    })
                            : extract(video, poster));
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

    /*  Takes the frame with the ffmpeg of the PATH in the background, as a process never runs on the FX thread  */
    private static CompletableFuture<Image> extract(Path video, Path poster) {
        return CompletableFuture.supplyAsync(() -> runFfmpeg(SystemOpen.findProgram("ffmpeg"), video, poster),
                DiskWorker.get());
    }

    /**
     * Takes the frame of the poster with ffmpeg and loads the poster.
     *
     * @param ffmpeg the ffmpeg executable, or null without ffmpeg
     * @param video the video file
     * @param poster the poster file to write
     * @return the poster
     * @throws IllegalStateException without ffmpeg, or if ffmpeg fails or takes too long
     */
    static Image runFfmpeg(Path ffmpeg, Path video, Path poster) {
        if (ffmpeg == null)
            throw new IllegalStateException("No ffmpeg in the PATH for the poster of " + video);
        try {
            Process process = new ProcessBuilder(ffmpegCommand(ffmpeg, video, poster))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished)
                process.destroyForcibly();
            if (!finished || process.exitValue() != 0 || !Files.isRegularFile(poster)) {
                /*  A partial poster would be taken as the cached one on the next load  */
                Files.deleteIfExists(poster);
                throw new IllegalStateException("ffmpeg could not take the poster of " + video);
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return new Image(poster.toUri().toString(), POSTER_WIDTH, 0, true, true);
    }

    /**
     * Returns the ffmpeg call for the poster: one frame at the frame time, as wide as the poster, without any
     * question on the console.
     *
     * @param ffmpeg the ffmpeg executable
     * @param video the video file
     * @param poster the poster file to write
     * @return the command line
     */
    static List<String> ffmpegCommand(Path ffmpeg, Path video, Path poster) {
        return List.of(ffmpeg.toString(), "-nostdin", "-y", "-loglevel", "error",
                "-ss", String.valueOf(FRAME_AT_SECONDS), "-i", video.toString(),
                "-frames:v", "1", "-vf", "scale=" + (int) POSTER_WIDTH + ":-2", "-q:v", "3", poster.toString());
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
