package art.moodframe.ui.component;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.util.I18nManager;
import art.moodframe.util.MediaSupport;
import art.moodframe.util.SystemOpen;
import art.moodframe.util.ThemeManager;
import art.moodframe.util.VideoRemedy;
import art.moodframe.util.VideoRemedy.Kind;
import art.moodframe.util.VideoRemedy.Remedy;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The full view of a video which JavaFX cannot play, here an unreadable one: a hint and the video player of the
 * system instead of an empty area and play/pause; closing and paging as before. Without video support of the system,
 * what helps under Linux, with a command to copy. Against a throwaway {@code user.home}.
 */
class ImagePreviewOverlayTest {

    private final Logger appLogger = (Logger) LoggerFactory.getLogger("art.moodframe");
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final List<Throwable> uncaught = new CopyOnWriteArrayList<>();
    private String home;

    @TempDir
    Path dir;

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

    @BeforeEach
    void setUp() throws Exception {
        home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectory(dir.resolve("home")).toString());
        forgetMediaSupport();
        forgetRemedy();
        logs.start();
        appLogger.addAppender(logs);
        onFxThread(() -> {
            Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> uncaught.add(error));
            return null;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        onFxThread(() -> {
            Thread.currentThread().setUncaughtExceptionHandler(null);
            return null;
        });
        appLogger.detachAppender(logs);
        forgetMediaSupport();
        forgetRemedy();
        System.setProperty("user.home", home);
    }

    /*  An unusable JavaFX Media is remembered for the whole session, which the tests must not leave behind  */
    private static void forgetMediaSupport() throws Exception {
        Method reset = MediaSupport.class.getDeclaredMethod("reset");
        reset.setAccessible(true);
        reset.invoke(null);
    }

    /*  The test system is no Linux without video support, so the remedy is taken as detected, null for none  */
    private static void assumeRemedy(Remedy remedy) throws Exception {
        assumeDetection(CompletableFuture.completedFuture(remedy));
    }

    private static void assumeDetection(CompletableFuture<Remedy> detection) throws Exception {
        Method assume = VideoRemedy.class.getDeclaredMethod("assume", CompletableFuture.class);
        assume.setAccessible(true);
        assume.invoke(null, detection);
    }

    private static void forgetRemedy() throws Exception {
        Method reset = VideoRemedy.class.getDeclaredMethod("reset");
        reset.setAccessible(true);
        reset.invoke(null);
    }

    /*  Runs a step on the FX thread, and hands a failed assertion of it to the test  */
    private static <T> T onFxThread(Supplier<T> step) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(step.get());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() instanceof Error error)
            throw error;
        if (failure.get() != null)
            throw new IllegalStateException(failure.get());
        return result.get();
    }

    private static <T extends Node> List<T> find(Parent root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Node child : root.getChildrenUnmodifiable()) {
            if (type.isInstance(child))
                found.add(type.cast(child));
            if (child instanceof Parent parent)
                found.addAll(find(parent, type));
        }
        return found;
    }

    /*  Shown on the screen: the node and all its parents are visible  */
    private static boolean shown(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible())
                return false;
        }
        return true;
    }

    private static Label hint(ImagePreviewOverlay overlay) {
        return find(overlay, Label.class).stream()
                .filter((label) -> label.getText() != null
                        && label.getText().startsWith(I18nManager.getText("video.fallback.hint")))
                .findFirst().orElse(null);
    }

    private static Button openButton(ImagePreviewOverlay overlay) {
        return find(overlay, Button.class).stream()
                .filter((button) -> I18nManager.getText("video.fallback.open").equals(button.getText()))
                .findFirst().orElse(null);
    }

    private static Button playPauseButton(ImagePreviewOverlay overlay) {
        return find(overlay, Button.class).stream()
                .filter((button) -> button.getGraphic() instanceof FontIcon icon
                        && icon.getIconLiteral().matches("mdi2p-(play|pause)"))
                .findFirst().orElse(null);
    }

    private static Label labelWithText(ImagePreviewOverlay overlay, String text) {
        return find(overlay, Label.class).stream()
                .filter((label) -> text.equals(label.getText()))
                .findFirst().orElse(null);
    }

    private static Button copyButton(ImagePreviewOverlay overlay) {
        return find(overlay, Button.class).stream()
                .filter((button) -> button.getGraphic() instanceof FontIcon icon
                        && "mdi2c-content-copy".equals(icon.getIconLiteral()))
                .findFirst().orElse(null);
    }

    /*  The text a label shows on the screen, shortened with an ellipsis if it does not fit  */
    private static String rendered(Label label) {
        return find(label, Text.class).stream().map(Text::getText).findFirst().orElse("");
    }

    private static void press(ImagePreviewOverlay overlay, KeyCode key) {
        overlay.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, false, false, false, false));
    }

    /*  Waits until the fallback of the video is shown, as JavaFX may report the error only while opening it  */
    private static Label awaitHint(ImagePreviewOverlay overlay) throws Exception {
        for (int i = 0; i < 200; i++) {
            Label hint = onFxThread(() -> hint(overlay));
            if (hint != null && onFxThread(() -> shown(hint)))
                return hint;
            Thread.sleep(100);
        }
        return null;
    }

    @Test
    void aVideoJavaFxCannotPlayShowsHintAndVideoPlayerButton() throws Exception {
        /*  JavaFX reports no error for a broken video on the headless test platform, but for an unreadable one  */
        Path video = Files.write(dir.resolve("locked.mp4"), new byte[] { 0, 0, 0, 24, 'f', 't', 'y', 'p' });
        assumeTrue(video.toFile().setReadable(false) && !Files.isReadable(video), "the video stays readable");
        Path image = dir.resolve("image.png");
        ImageIO.write(new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB), "png", image.toFile());

        ImagePreviewOverlay overlay = onFxThread(() -> {
            ImagePreviewOverlay created = new ImagePreviewOverlay();
            new Scene(created, 1000, 700);
            created.showImages(List.of(video.toString(), image.toString()), 0);
            return created;
        });

        Label hint = awaitHint(overlay);
        assertNotNull(hint, "no hint for the video");
        onFxThread(() -> {
            boolean opens = SystemOpen.isAvailable();
            Button open = openButton(overlay);
            assertNotNull(open);
            assertEquals(opens, shown(open));
            assertEquals(!opens, hint.getText().contains(video.toAbsolutePath().toString()));
            assertFalse(shown(playPauseButton(overlay)));

            /*  Space plays nothing, paging shows the image without the fallback, Esc closes  */
            press(overlay, KeyCode.SPACE);
            assertFalse(shown(playPauseButton(overlay)));
            press(overlay, KeyCode.RIGHT);
            assertFalse(shown(hint));
            press(overlay, KeyCode.LEFT);
            assertTrue(shown(hint));
            press(overlay, KeyCode.ESCAPE);
            assertFalse(overlay.isVisible());
            assertFalse(shown(hint));
            return null;
        });

        assertTrue(uncaught.isEmpty(), () -> "uncaught: " + uncaught);
        assertNull(logs.list.stream().filter((event) -> event.getThrowableProxy() != null).findFirst().orElse(null));

        /*  A problem of the file is not one of the system, the next video tries JavaFX Media again  */
        assertTrue(MediaSupport.isUsable());
    }

    @Test
    void anUnusableJavaFxMediaGoesStraightToTheFallback() throws Exception {
        Path video = Files.write(dir.resolve("clip.mp4"), new byte[] { 0 });
        MediaSupport.failed(new RuntimeException("com.sun.media.jfxmedia.MediaException: Could not create player!"));
        assertFalse(MediaSupport.isUsable());

        onFxThread(() -> {
            ImagePreviewOverlay overlay = new ImagePreviewOverlay();
            new Scene(overlay, 1000, 700);
            overlay.showImage(video.toString());
            assertTrue(shown(hint(overlay)));
            assertFalse(shown(playPauseButton(overlay)));
            overlay.closeOverlay();
            return null;
        });
        assertTrue(uncaught.isEmpty(), () -> "uncaught: " + uncaught);
    }

    /*  The remedy of a case, its text in English and German, the command to copy and the note, null for none  */
    private record Case(Remedy remedy, String key, Object[] parameters, String english, String german,
            String command, String noteKey) {
    }

    @Test
    void withoutVideoSupportTheHintTellsWhatHelpsUnderLinux() throws Exception {
        Path video = Files.write(dir.resolve("clip.mp4"), new byte[] { 0 });
        MediaSupport.failed(new RuntimeException("com.sun.media.jfxmedia.MediaException: Could not create player!"));
        List<Case> cases = List.of(
                new Case(new Remedy(Kind.ARCH, 62), "video.fallback.remedy.arch", new Object[0],
                        "restart MoodFrame", "starte MoodFrame neu", "sudo pacman -S ffmpeg4.4", null),
                new Case(new Remedy(Kind.DEBIAN_MISSING, 0), "video.fallback.remedy.debian.missing", new Object[0],
                        "Install ffmpeg", "Installiere ffmpeg", "sudo apt install ffmpeg",
                        "video.fallback.remedy.debian.missing.note"),
                new Case(new Remedy(Kind.DEBIAN_NEWER, 62), "video.fallback.remedy.debian.newer",
                        new Object[] { "62" }, "(libavcodec 62) is newer", "(libavcodec 62) ist neuer", null, null),
                new Case(new Remedy(Kind.OTHER, 0), "video.fallback.remedy.other", new Object[] { "54", "61" },
                        "libavcodec 54 to 61", "libavcodec 54 bis 61", null, null));
        Locale before = I18nManager.getCurrentLocale();
        try {
            for (Case remedyCase : cases) {
                assumeRemedy(remedyCase.remedy());
                List<String> status = new ArrayList<>();
                ImagePreviewOverlay overlay = onFxThread(() -> {
                    I18nManager.setLocale(Locale.ENGLISH);
                    ImagePreviewOverlay created = new ImagePreviewOverlay();
                    new Scene(created, 1000, 700);
                    created.setOnStatusMessage(status::add);
                    created.showImage(video.toString());
                    return created;
                });
                for (Locale locale : List.of(Locale.ENGLISH, Locale.GERMAN)) {
                    String fragment = Locale.GERMAN.equals(locale) ? remedyCase.german() : remedyCase.english();
                    onFxThread(() -> {
                        I18nManager.setLocale(locale);
                        String text = I18nManager.getText(remedyCase.key(), remedyCase.parameters());
                        assertTrue(text.contains(fragment), text);
                        Label remedy = labelWithText(overlay, text);
                        assertNotNull(remedy, () -> "no remedy " + text);
                        assertTrue(shown(remedy));
                        assertTrue(shown(hint(overlay)));
                        assertEquals(remedyCase.command() != null, shown(copyButton(overlay)));

                        /*  The note follows the command, as it is about what happens after running it  */
                        String note = I18nManager.getText("video.fallback.remedy.debian.missing.note");
                        assertEquals(Locale.GERMAN.equals(locale), note.startsWith("Läuft das Video danach"), note);
                        Label noteLabel = labelWithText(overlay, note);
                        if (remedyCase.noteKey() == null)
                            assertNull(noteLabel);
                        else {
                            assertNotNull(noteLabel, () -> "no note " + note);
                            assertTrue(shown(noteLabel));
                            VBox box = (VBox) noteLabel.getParent();
                            assertTrue(box.getChildren().indexOf(noteLabel)
                                    > box.getChildren().indexOf(copyButton(overlay).getParent()));
                        }
                        return null;
                    });
                }
                if (remedyCase.command() != null)
                    onFxThread(() -> {
                        Label command = labelWithText(overlay, remedyCase.command());
                        assertNotNull(command);
                        assertTrue(shown(command));
                        copyButton(overlay).fire();
                        assertEquals(remedyCase.command(), Clipboard.getSystemClipboard().getString());
                        assertEquals(List.of(I18nManager.getText("video.fallback.remedy.copied")), status);
                        return null;
                    });
                onFxThread(() -> {
                    overlay.closeOverlay();
                    return null;
                });
            }
        }
        finally {
            onFxThread(() -> {
                I18nManager.setLocale(before);
                return null;
            });
        }
        assertTrue(uncaught.isEmpty(), () -> "uncaught: " + uncaught);
    }

    @Test
    void theRemedyAppearsOnceTheDetectionIsDone() throws Exception {
        Path video = Files.write(dir.resolve("clip.mp4"), new byte[] { 0 });
        MediaSupport.failed(new RuntimeException("com.sun.media.jfxmedia.MediaException: Could not create player!"));
        CompletableFuture<Remedy> detection = new CompletableFuture<>();
        assumeDetection(detection);
        String arch = I18nManager.getText("video.fallback.remedy.arch");
        String command = "sudo pacman -S ffmpeg4.4";

        /*  While the detection runs, the hint is shown as before, and every change of a label is recorded  */
        List<String> changes = new CopyOnWriteArrayList<>();
        ImagePreviewOverlay overlay = onFxThread(() -> {
            ImagePreviewOverlay created = new ImagePreviewOverlay();
            new Scene(created, 1000, 700);
            created.showImage(video.toString());
            assertTrue(shown(hint(created)));
            assertNull(labelWithText(created, arch));
            assertFalse(shown(copyButton(created)));
            for (Label label : find(created, Label.class)) {
                label.textProperty().addListener((observable, before, after) ->
                        changes.add("text " + before + " -> " + after));
                label.visibleProperty().addListener((observable, before, after) ->
                        changes.add("visible " + label.getText() + " -> " + after));
            }
            return created;
        });

        /*  Once it is done, the remedy shows up in one go, the hint itself stays untouched  */
        detection.complete(new Remedy(Kind.ARCH, 62));
        onFxThread(() -> {
            assertTrue(shown(labelWithText(overlay, arch)));
            assertTrue(shown(labelWithText(overlay, command)));
            assertTrue(shown(copyButton(overlay)));
            assertTrue(shown(hint(overlay)));
            assertEquals(List.of("text null -> " + arch, "visible " + arch + " -> true",
                    "text null -> " + command), changes);
            overlay.closeOverlay();
            return null;
        });
        assertTrue(uncaught.isEmpty(), () -> "uncaught: " + uncaught);
    }

    @Test
    void macOSAndWindowsAndProblemsOfTheFileGetNoRemedy() throws Exception {
        Path video = Files.write(dir.resolve("clip.mp4"), new byte[] { 0 });
        String arch = I18nManager.getText("video.fallback.remedy.arch");

        /*  JavaFX Media is still usable: the problem is the file, not the system  */
        assumeRemedy(new Remedy(Kind.ARCH, 0));
        onFxThread(() -> {
            ImagePreviewOverlay overlay = new ImagePreviewOverlay();
            new Scene(overlay, 1000, 700);
            overlay.showImage(dir.resolve("gone.mp4").toString());
            assertNull(labelWithText(overlay, arch));
            overlay.closeOverlay();
            return null;
        });

        /*  macOS and Windows have no remedy: the hint as before  */
        assumeRemedy(null);
        MediaSupport.failed(new RuntimeException("com.sun.media.jfxmedia.MediaException: Could not create player!"));
        onFxThread(() -> {
            ImagePreviewOverlay overlay = new ImagePreviewOverlay();
            new Scene(overlay, 1000, 700);
            overlay.showImage(video.toString());
            assertTrue(shown(hint(overlay)));
            assertNull(labelWithText(overlay, arch));
            assertFalse(shown(copyButton(overlay)));
            long texts = find(overlay, Label.class).stream()
                    .filter((label) -> shown(label) && label.getText() != null && !label.getText().isEmpty())
                    .count();
            assertEquals(1, texts, "only the hint");
            overlay.closeOverlay();
            return null;
        });
        assertTrue(uncaught.isEmpty(), () -> "uncaught: " + uncaught);
    }

    @Test
    void theRemedyIsReadableInAllThemes() throws Exception {
        Path video = Files.write(dir.resolve("clip.mp4"), new byte[] { 0 });
        MediaSupport.failed(new RuntimeException("com.sun.media.jfxmedia.MediaException: Could not create player!"));
        assumeRemedy(new Remedy(Kind.DEBIAN_MISSING, 0));
        String before = Application.getUserAgentStylesheet();
        String iconButtonCss = ImagePreviewOverlay.class.getResource("/css/icon-button.css").toExternalForm();
        try {
            for (String theme : List.of("primer-light", "primer-dark", "nord-light", "nord-dark", "cupertino-light",
                    "cupertino-dark", "dracula")) {
                onFxThread(() -> {
                    Application.setUserAgentStylesheet(ThemeManager.getAtlantaFXTheme(theme).getUserAgentStylesheet());

                    /*  Narrow on purpose: the text wraps, the command does not  */
                    ImagePreviewOverlay overlay = new ImagePreviewOverlay();
                    Scene scene = new Scene(overlay, 420, 700);
                    scene.getStylesheets().add(iconButtonCss);
                    overlay.showImage(video.toString());
                    overlay.applyCss();
                    overlay.layout();
                    Label remedy = labelWithText(overlay,
                            I18nManager.getText("video.fallback.remedy.debian.missing"));
                    Label command = labelWithText(overlay, "sudo apt install ffmpeg");
                    Label note = labelWithText(overlay,
                            I18nManager.getText("video.fallback.remedy.debian.missing.note"));
                    assertEquals(Color.WHITE, remedy.getTextFill(), theme);
                    assertEquals(Color.WHITE, command.getTextFill(), theme);
                    assertEquals(Color.WHITE, note.getTextFill(), theme);
                    assertEquals(Color.WHITE, ((FontIcon) copyButton(overlay).getGraphic()).getIconColor(), theme);
                    assertTrue(remedy.isWrapText(), theme);
                    assertTrue(note.isWrapText(), theme);
                    assertEquals(command.getText(), rendered(command), theme);
                    assertTrue(command.getFont().getFamily().toLowerCase(Locale.ROOT).contains("mono"),
                            command.getFont().getFamily());
                    overlay.closeOverlay();
                    return null;
                });
            }
        }
        finally {
            onFxThread(() -> {
                Application.setUserAgentStylesheet(before);
                return null;
            });
        }
        assertTrue(uncaught.isEmpty(), () -> "uncaught: " + uncaught);
    }

    /*  The overlay at the size of the area it covers in the main window, laid out as by a pulse  */
    private static ImagePreviewOverlay overlayOfSize(double width, double height) {
        ImagePreviewOverlay overlay = new ImagePreviewOverlay();
        new Scene(overlay, width, height);
        resize(overlay, width, height);
        return overlay;
    }

    private static void resize(ImagePreviewOverlay overlay, double width, double height) {
        overlay.resize(width, height);
        overlay.layout();
    }

    private static PreviewFrame frame(ImagePreviewOverlay overlay) {
        return find(overlay, PreviewFrame.class).get(0);
    }

    private static Path image(Path dir, String name, int width, int height, int rgb) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++)
                image.setRGB(x, y, rgb);
        }
        Path file = dir.resolve(name);
        ImageIO.write(image, "png", file.toFile());
        return file;
    }

    /*  The line of the frame lies flush outside the shown image on all four sides, its glow behind the image  */
    private static void assertFramed(ImagePreviewOverlay overlay) {
        ImageView view = find(overlay, ImageView.class).get(0);
        PreviewFrame frame = frame(overlay);
        Bounds image = view.localToParent(view.getLayoutBounds());
        assertTrue(image.getWidth() > 0 && image.getHeight() > 0);
        assertTrue(shown(frame));
        List<Node> siblings = frame.getParent().getChildrenUnmodifiable();
        assertTrue(siblings.indexOf(frame) < siblings.indexOf(view));
        List<Bounds> lines = frame.getChildren().stream().filter(Rectangle.class::isInstance)
                .map((line) -> line.localToParent(line.getLayoutBounds())).toList();
        assertEquals(4, lines.size());
        assertEquals(image.getMinY(), lines.get(0).getMaxY(), 0.001);
        assertEquals(image.getMaxX(), lines.get(1).getMinX(), 0.001);
        assertEquals(image.getMaxY(), lines.get(2).getMinY(), 0.001);
        assertEquals(image.getMinX(), lines.get(3).getMaxX(), 0.001);
        assertEquals(image.getMinX() - 2.0, lines.stream().mapToDouble(Bounds::getMinX).min().orElseThrow(), 0.001);
        assertEquals(image.getMaxX() + 2.0, lines.stream().mapToDouble(Bounds::getMaxX).max().orElseThrow(), 0.001);
        assertEquals(image.getMinY() - 2.0, lines.stream().mapToDouble(Bounds::getMinY).min().orElseThrow(), 0.001);
        assertEquals(image.getMaxY() + 2.0, lines.stream().mapToDouble(Bounds::getMaxY).max().orElseThrow(), 0.001);
        for (Bounds line : lines) {
            double overlapX = Math.min(line.getMaxX(), image.getMaxX()) - Math.max(line.getMinX(), image.getMinX());
            double overlapY = Math.min(line.getMaxY(), image.getMaxY()) - Math.max(line.getMinY(), image.getMinY());
            assertTrue(overlapX <= 0.001 || overlapY <= 0.001, () -> "the line " + line + " covers " + image);
        }
    }

    @Test
    void theMoodFrameLiesFlushAroundTheImageAndFollowsIt() throws Exception {
        Path dark = image(dir, "dark.png", 1600, 900, 0x050505);
        Path tall = image(dir, "tall.png", 300, 500, 0xffffff);
        ImagePreviewOverlay overlay = onFxThread(() -> {
            ImagePreviewOverlay created = overlayOfSize(1000, 700);
            created.showImages(List.of(dark.toString(), tall.toString()), 0);
            return created;
        });
        try {
            onFxThread(() -> {
                assertFramed(overlay);
                return null;
            });

            /*  a smaller window, zooming and panning, the next image and back  */
            onFxThread(() -> {
                resize(overlay, 700, 500);
                return null;
            });
            onFxThread(() -> {
                assertFramed(overlay);
                press(overlay, KeyCode.PLUS);
                assertFramed(overlay);
                press(overlay, KeyCode.RIGHT);
                assertFramed(overlay);
                press(overlay, KeyCode.LEFT);
                return null;
            });
            onFxThread(() -> {
                assertFramed(overlay);
                return null;
            });
        }
        finally {
            onFxThread(() -> {
                overlay.closeOverlay();
                return I18nManager.removeChangeListener(overlay);
            });
        }
        assertTrue(uncaught.isEmpty(), () -> "uncaught: " + uncaught);
    }

    @Test
    void theColorsTurnWhileThePreviewIsShownOnly() throws Exception {
        Path image = image(dir, "image.png", 640, 480, 0x202020);
        Object realConfig = useConfig(true);
        ImagePreviewOverlay overlay = onFxThread(() -> overlayOfSize(1000, 700));
        PreviewFrame frame = onFxThread(() -> frame(overlay));
        try {
            assertFalse(onFxThread(frame::isRunning));
            onFxThread(() -> {
                overlay.showImage(image.toString());
                return null;
            });
            assertTrue(onFxThread(frame::isRunning));
            double before = onFxThread(frame::getPhase);
            Thread.sleep(1000);
            double after = onFxThread(frame::getPhase);
            assertNotEquals(before, after, 0.01);

            /*  closed, the frame stands still, opened again it turns on  */
            onFxThread(() -> {
                press(overlay, KeyCode.ESCAPE);
                return null;
            });
            assertFalse(onFxThread(frame::isRunning));
            double closed = onFxThread(frame::getPhase);
            Thread.sleep(300);
            assertEquals(closed, onFxThread(frame::getPhase));
            onFxThread(() -> {
                overlay.showImage(image.toString());
                return null;
            });
            assertTrue(onFxThread(frame::isRunning));
        }
        finally {
            restoreConfig(realConfig);
            onFxThread(() -> {
                overlay.closeOverlay();
                return I18nManager.removeChangeListener(overlay);
            });
        }
    }

    @Test
    void switchedOffInTheSettingsTheFrameStandsStill() throws Exception {
        Path image = image(dir, "image.png", 640, 480, 0xffffff);
        Object realConfig = useConfig(false);
        ImagePreviewOverlay overlay = onFxThread(() -> overlayOfSize(1000, 700));
        try {
            PreviewFrame frame = onFxThread(() -> {
                overlay.showImage(image.toString());
                assertFramed(overlay);
                return frame(overlay);
            });
            assertFalse(onFxThread(frame::isRunning));
            double before = onFxThread(frame::getPhase);
            Thread.sleep(300);
            assertEquals(before, onFxThread(frame::getPhase));
        }
        finally {
            restoreConfig(realConfig);
            onFxThread(() -> {
                overlay.closeOverlay();
                return I18nManager.removeChangeListener(overlay);
            });
        }
    }

    /*  The relative luminance of WCAG 2.x: 0 for black, 1 for white  */
    private static double luminance(Color color) {
        double[] channels = { color.getRed(), color.getGreen(), color.getBlue() };
        for (int i = 0; i < channels.length; i++)
            channels[i] = channels[i] <= 0.04045 ? channels[i] / 12.92 : Math.pow((channels[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
    }

    private static double contrast(Color one, Color other) {
        double lighter = Math.max(luminance(one), luminance(other));
        double darker  = Math.min(luminance(one), luminance(other));
        return (lighter + 0.05) / (darker + 0.05);
    }

    @Test
    void inLightThemesThePreviewIsADarkStageWithReadableButtons() throws Exception {
        Path image = image(dir, "white.png", 640, 480, 0xffffff);
        String before = Application.getUserAgentStylesheet();
        String iconButtonCss = ImagePreviewOverlay.class.getResource("/css/icon-button.css").toExternalForm();
        try {
            for (String theme : List.of("primer-light", "nord-light", "cupertino-light")) {
                onFxThread(() -> {
                    Application.setUserAgentStylesheet(ThemeManager.getAtlantaFXTheme(theme).getUserAgentStylesheet());

                    /*  The app behind the preview at its brightest: white  */
                    Region app = new Region();
                    app.setStyle("-fx-background-color: white;");
                    ImagePreviewOverlay overlay = new ImagePreviewOverlay();
                    Scene scene = new Scene(new StackPane(app, overlay), 1000, 700);
                    scene.getStylesheets().add(iconButtonCss);
                    overlay.showImage(image.toString());
                    overlay.applyCss();
                    overlay.layout();

                    /*  A corner of the stage, away from the image, its glow and the buttons  */
                    Color stage = scene.snapshot(null).getPixelReader().getColor(5, 695);
                    assertTrue(luminance(stage) < 0.01, () -> theme + ": stage " + stage);
                    int checked = 0;
                    for (Button button : find(overlay, Button.class)) {
                        if (shown(button) && button.getGraphic() instanceof FontIcon icon
                                && icon.getIconColor() instanceof Color color) {
                            assertTrue(contrast(color, stage) >= 7.0, () -> theme + ": " + icon.getIconLiteral());
                            checked++;
                        }
                    }
                    assertTrue(checked >= 4, theme + ": " + checked + " buttons");
                    overlay.closeOverlay();
                    return I18nManager.removeChangeListener(overlay);
                });
            }
        }
        finally {
            onFxThread(() -> {
                Application.setUserAgentStylesheet(before);
                return null;
            });
        }
        assertTrue(uncaught.isEmpty(), () -> "uncaught: " + uncaught);
    }

    /*  The overlay of the given size in a theme, styled as in the app and laid out as by a pulse  */
    private static ImagePreviewOverlay styledOverlayOfSize(String theme, double width, double height) {
        Application.setUserAgentStylesheet(ThemeManager.getAtlantaFXTheme(theme).getUserAgentStylesheet());
        ImagePreviewOverlay overlay = new ImagePreviewOverlay();
        Scene scene = new Scene(overlay, width, height);
        scene.getStylesheets().add(ImagePreviewOverlay.class.getResource("/css/icon-button.css").toExternalForm());
        overlay.applyCss();
        resize(overlay, width, height);
        return overlay;
    }

    /*  The shown image and the shown buttons, in the coordinates of the overlay, which is the root of its scene  */
    private static Bounds imageBounds(ImagePreviewOverlay overlay) {
        ImageView view = find(overlay, ImageView.class).get(0);
        return view.localToScene(view.getLayoutBounds());
    }

    private static List<Button> previewButtons(ImagePreviewOverlay overlay) {
        return find(overlay, Button.class).stream()
                .filter((button) -> button.getStyleClass().contains("preview-button") && shown(button))
                .toList();
    }

    /*  Fitted: the image lies within the overlay minus the margins for the buttons, which lie off the image  */
    private static void assertFitted(ImagePreviewOverlay overlay, double width, double height) {
        Bounds image = imageBounds(overlay);
        String size = width + "x" + height + ": " + image;
        assertTrue(image.getMinX() >= 80 - 0.5 && image.getMaxX() <= width - 80 + 0.5, size);
        assertTrue(image.getMinY() >= 60 - 0.5 && image.getMaxY() <= height - 60 + 0.5, size);
        assertTrue(Math.abs(image.getWidth() - (width - 160)) < 0.5
                || Math.abs(image.getHeight() - (height - 120)) < 0.5, () -> size + " does not fill the area");
        List<Button> buttons = previewButtons(overlay);
        assertTrue(buttons.size() >= 8, size + ": " + buttons.size() + " buttons");
        for (Button button : buttons) {
            Bounds bounds = button.localToScene(button.getLayoutBounds());
            assertFalse(bounds.intersects(image), () -> size + ": " + button.getTooltip().getText() + " " + bounds);
        }
        assertFramed(overlay);
    }

    /*  Pans the image by dragging over the overlay, as the user does  */
    private static void drag(ImagePreviewOverlay overlay, double deltaX, double deltaY) {
        Node layer = overlay.getChildrenUnmodifiable().get(1);
        double x = overlay.getWidth() / 2;
        double y = overlay.getHeight() / 2;
        Event.fireEvent(layer, mouse(MouseEvent.MOUSE_PRESSED, x, y));
        Event.fireEvent(layer, mouse(MouseEvent.MOUSE_DRAGGED, x + deltaX, y + deltaY));
        Event.fireEvent(layer, mouse(MouseEvent.MOUSE_RELEASED, x + deltaX, y + deltaY));
    }

    private static MouseEvent mouse(EventType<MouseEvent> type, double x, double y) {
        return new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1, false, false, false, false, true, false, false,
                false, false, false, null);
    }

    @Test
    void theImageFitsTheWindowAnewAndPansUpToItsFrame() throws Exception {
        Path big = image(dir, "big.png", 2400, 1600, 0x808080);
        String before = Application.getUserAgentStylesheet();
        ImagePreviewOverlay overlay = onFxThread(() -> {
            ImagePreviewOverlay created = styledOverlayOfSize("primer-dark", 1400, 900);
            created.showImages(List.of(big.toString(), big.toString(), big.toString()), 1);
            created.updateChatNavigationButtons(true, true);
            created.layout();
            return created;
        });
        double frame = PreviewFrame.EXTENT;
        try {
            Bounds fitted = onFxThread(() -> {
                assertFitted(overlay, 1400, 900);
                return imageBounds(overlay);
            });

            /*  smaller, very small, zooming out there and larger again: fitted each time, as large as at first  */
            for (double[] size : new double[][] { { 800, 600 }, { 300, 200 }, { 1400, 900 } }) {
                onFxThread(() -> {
                    resize(overlay, size[0], size[1]);
                    assertFitted(overlay, size[0], size[1]);
                    press(overlay, KeyCode.MINUS);
                    assertFitted(overlay, size[0], size[1]);
                    return null;
                });
            }
            onFxThread(() -> {
                assertEquals(fitted.getWidth(), imageBounds(overlay).getWidth(), 0.5);
                assertEquals(fitted.getHeight(), imageBounds(overlay).getHeight(), 0.5);
                return null;
            });

            /*  zoomed in, a smaller window keeps the zoom, and the image pans until its frame shows at the edge  */
            double zoomed = onFxThread(() -> {
                for (int i = 0; i < 3; i++)
                    press(overlay, KeyCode.PLUS);
                resize(overlay, 800, 600);
                return imageBounds(overlay).getWidth();
            });
            assertTrue(zoomed > 2 * fitted.getWidth() / 1.5, "zoomed: " + zoomed);
            onFxThread(() -> {
                Bounds image = imageBounds(overlay);
                assertEquals(zoomed, image.getWidth(), 0.5);
                assertTrue(image.getMinX() <= frame + 0.5 && image.getMaxX() >= 800 - frame - 0.5, image::toString);
                assertTrue(image.getMinY() <= frame + 0.5 && image.getMaxY() >= 600 - frame - 0.5, image::toString);
                drag(overlay, 2000, 2000);
                image = imageBounds(overlay);
                assertEquals(frame, image.getMinX(), 0.5);
                assertEquals(frame, image.getMinY(), 0.5);
                assertFramed(overlay);
                return null;
            });

            /*  a larger window takes the image back to its edge, the zoom stays  */
            onFxThread(() -> {
                resize(overlay, 1400, 900);
                Bounds image = imageBounds(overlay);
                assertEquals(zoomed, image.getWidth(), 0.5);
                assertEquals(frame, image.getMinX(), 0.5);
                assertEquals(frame, image.getMinY(), 0.5);
                drag(overlay, -3000, -3000);
                image = imageBounds(overlay);
                assertEquals(1400 - frame, image.getMaxX(), 0.5);
                assertEquals(900 - frame, image.getMaxY(), 0.5);
                return null;
            });
        }
        finally {
            onFxThread(() -> {
                overlay.closeOverlay();
                Application.setUserAgentStylesheet(before);
                return I18nManager.removeChangeListener(overlay);
            });
        }
        assertTrue(uncaught.isEmpty(), () -> "uncaught: " + uncaught);
    }

    @Test
    void everyButtonShowsOnWhiteAndOnBlackImagesInAllThemes() throws Exception {
        Path white = image(dir, "white.png", 2400, 1600, 0xffffff);
        Path black = image(dir, "black.png", 2400, 1600, 0x000000);
        String before = Application.getUserAgentStylesheet();
        try {
            for (String theme : List.of("primer-light", "primer-dark", "nord-light", "nord-dark", "cupertino-light",
                    "cupertino-dark", "dracula")) {
                for (Path image : List.of(white, black)) {
                    onFxThread(() -> {
                        ImagePreviewOverlay overlay = styledOverlayOfSize(theme, 1000, 700);
                        overlay.showImages(List.of(image.toString(), image.toString(), image.toString()), 1);
                        overlay.updateChatNavigationButtons(true, true);

                        /*  At 100 % the image lies under all buttons  */
                        press(overlay, KeyCode.F);
                        overlay.applyCss();
                        overlay.layout();
                        Bounds shown = imageBounds(overlay);
                        assertTrue(shown.getMinX() < 0 && shown.getMaxX() > 1000, shown::toString);
                        assertTrue(shown.getMinY() < 0 && shown.getMaxY() > 700, shown::toString);
                        PixelReader pixels = overlay.getScene().snapshot(null).getPixelReader();
                        List<Button> buttons = previewButtons(overlay);
                        assertTrue(buttons.size() >= 8, theme + ": " + buttons.size() + " buttons");
                        for (Button button : buttons) {

                            /*  The surface just inside the rim, above the icon, against the brightest icon pixel  */
                            Bounds bounds = button.localToScene(button.getLayoutBounds());
                            Color surface = pixels.getColor((int) bounds.getCenterX(), (int) bounds.getMinY() + 4);
                            Bounds glyph = button.getGraphic().localToScene(button.getGraphic().getLayoutBounds());
                            Color icon = surface;
                            for (int y = (int) glyph.getMinY(); y < glyph.getMaxY(); y++) {
                                for (int x = (int) glyph.getMinX(); x < glyph.getMaxX(); x++) {
                                    Color pixel = pixels.getColor(x, y);
                                    if (luminance(pixel) > luminance(icon))
                                        icon = pixel;
                                }
                            }
                            String what = theme + ", " + image.getFileName() + ", " + button.getTooltip().getText()
                                    + ": " + icon + " on " + surface;
                            assertTrue(contrast(icon, surface) >= 3.0, what + " = " + contrast(icon, surface));
                            assertTrue(luminance(surface) < 0.25, what);
                        }
                        overlay.closeOverlay();
                        return I18nManager.removeChangeListener(overlay);
                    });
                }
            }
        }
        finally {
            onFxThread(() -> {
                Application.setUserAgentStylesheet(before);
                return null;
            });
        }
        assertTrue(uncaught.isEmpty(), () -> "uncaught: " + uncaught);
    }

    /*  A configuration without file that lets the frame turn or not, instead of what another test left behind  */
    private static Object useConfig(boolean animated) throws Exception {
        Object realConfig = services().get(ApplicationConfig.class);
        ApplicationConfig config = mock(ApplicationConfig.class);
        when(config.isPreviewFrameAnimated()).thenReturn(animated);
        ServiceFactory.getInstance().registerService(ApplicationConfig.class, config);
        return realConfig;
    }

    private static void restoreConfig(Object realConfig) throws Exception {
        if (realConfig != null)
            services().put(ApplicationConfig.class, realConfig);
        else
            services().remove(ApplicationConfig.class);
    }

    /*  the services the factory holds, of which this test takes out again what it put in  */
    @SuppressWarnings("unchecked")
    private static Map<Class<?>, Object> services() throws Exception {
        Field services = ServiceFactory.class.getDeclaredField("services");
        services.setAccessible(true);
        return (Map<Class<?>, Object>) services.get(ServiceFactory.getInstance());
    }
}
