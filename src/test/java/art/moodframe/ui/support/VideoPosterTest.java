package art.moodframe.ui.support;

import javafx.application.Platform;
import javafx.scene.image.Image;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The poster of a video via ffmpeg, where JavaFX Media cannot play the video: the call, the poster it writes, and
 * the placeholder without ffmpeg. A shell script stands in for ffmpeg.
 */
class VideoPosterTest {

    /*  The poster is loaded as a JavaFX image, which needs the toolkit  */
    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await(10, TimeUnit.SECONDS);
    }

    @AfterEach
    void forget() {
        MediaSupport.reset();
    }

    private static Path script(Path dir, String body) throws Exception {
        Path script = Files.writeString(dir.resolve("ffmpeg"), "#!/bin/sh\nfor last; do :; done\n" + body);
        assertTrue(script.toFile().setExecutable(true));
        return script;
    }

    @Test
    void ffmpegTakesOneFrameIntoThePosterPath() {
        Path ffmpeg = Path.of("/usr/bin/ffmpeg");
        Path video = Path.of("/videos/clip.mp4");
        Path poster = VideoPoster.posterPath(video);
        assertEquals(Path.of("/videos/clip_poster.jpg"), poster);
        assertEquals(List.of(ffmpeg.toString(), "-nostdin", "-y", "-loglevel", "error", "-ss", "0.5",
                "-i", video.toString(), "-frames:v", "1", "-vf", "scale=400:-2", "-q:v", "3", poster.toString()),
                VideoPoster.ffmpegCommand(ffmpeg, video, poster));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void loadsThePosterWrittenByFfmpeg(@TempDir Path dir) throws Exception {
        Path frame = dir.resolve("frame.jpg");
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "jpg", frame.toFile());
        Path ffmpeg = script(dir, "cp '" + frame + "' \"$last\"\n");
        Path video = Files.write(dir.resolve("clip.mp4"), new byte[] { 0 });
        Path poster = VideoPoster.posterPath(video);

        Image image = VideoPoster.runFfmpeg(ffmpeg, video, poster);
        assertFalse(image.isError());
        assertTrue(Files.isRegularFile(poster));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aFailingFfmpegLeavesNoPartialPoster(@TempDir Path dir) throws Exception {
        Path ffmpeg = script(dir, "echo partial > \"$last\"\nexit 1\n");
        Path video = Files.write(dir.resolve("clip.mp4"), new byte[] { 0 });
        Path poster = VideoPoster.posterPath(video);

        assertThrows(IllegalStateException.class, () -> VideoPoster.runFfmpeg(ffmpeg, video, poster));
        assertFalse(Files.exists(poster));
    }

    @Test
    void withoutFfmpegThePlaceholderStays(@TempDir Path dir) throws Exception {
        Path video = Files.write(dir.resolve("clip.mp4"), new byte[] { 0 });
        Path poster = VideoPoster.posterPath(video);
        assertThrows(IllegalStateException.class, () -> VideoPoster.runFfmpeg(null, video, poster));
        assertFalse(Files.exists(poster));

        /*  With JavaFX Media known to be unusable, the poster goes straight to ffmpeg, here to none  */
        assumeTrue(SystemOpen.findProgram("ffmpeg") == null, "ffmpeg is in the PATH");
        MediaSupport.failed(new RuntimeException("Could not create player!"));
        CompletableFuture<Image> load = VideoPoster.load(video);
        assertThrows(ExecutionException.class, () -> load.get(10, TimeUnit.SECONDS));
        assertFalse(Files.exists(poster));
    }

    @Test
    void aStoredPosterIsDecodedOnTheDiskWorkerAndKeptInTheCache(@TempDir Path dir) throws Exception {
        Path video = Files.write(dir.resolve("clip.mp4"), new byte[] { 0 });
        BufferedImage frame = new BufferedImage(800, 450, BufferedImage.TYPE_INT_RGB);
        assertTrue(ImageIO.write(frame, "jpg", VideoPoster.posterPath(video).toFile()));
        ImageCache cache = new ImageCache();
        List<Runnable> queued = new ArrayList<>();

        /*  Nothing is read on the calling thread, the JavaFX thread of the views  */
        CompletableFuture<Image> load = VideoPoster.load(video, cache, queued::add);
        assertFalse(load.isDone());
        assertEquals(1, queued.size());

        queued.get(0).run();
        Image poster = load.get(10, TimeUnit.SECONDS);
        assertEquals(400.0, poster.getWidth());

        /*  A row that shows the video again takes the poster from the cache  */
        CompletableFuture<Image> again = VideoPoster.load(video, cache, queued::add);
        assertTrue(again.isDone());
        assertSame(poster, again.get());
        assertEquals(1, queued.size());
    }
}
