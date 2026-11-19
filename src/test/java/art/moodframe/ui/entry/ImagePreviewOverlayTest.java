package art.moodframe.ui.entry;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ConfigMocks;
import art.moodframe.util.I18nManager;
import art.moodframe.ui.support.MediaSupport;
import art.moodframe.ui.support.SystemOpen;
import art.moodframe.ui.support.ThemeManager;
import art.moodframe.ui.support.VideoRemedy;
import art.moodframe.ui.support.VideoRemedy.Kind;
import art.moodframe.ui.support.VideoRemedy.Remedy;
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
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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

    /*  The tests of the layout measure the image at once; theNextImageIsDecodedInTheBackground checks the loading  */
    private static ImagePreviewOverlay atOnce() {
        return new ImagePreviewOverlay(null, false);
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
            ImagePreviewOverlay created = atOnce();
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
            ImagePreviewOverlay overlay = atOnce();
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
                    ImagePreviewOverlay created = atOnce();
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
            ImagePreviewOverlay created = atOnce();
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
            ImagePreviewOverlay overlay = atOnce();
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
            ImagePreviewOverlay overlay = atOnce();
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
                    ImagePreviewOverlay overlay = atOnce();
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

    /*  The overlay at the size of the area it covers in the main window, with its buttons styled as in the app, as
        their size borders the safe area of the image, and laid out as by a pulse  */
    private static ImagePreviewOverlay overlayOfSize(double width, double height) {
        return overlayOfSize(width, height, null);
    }

    private static ImagePreviewOverlay overlayOfSize(double width, double height, ApplicationConfig config) {
        return overlayOfSize(width, height, config, false);
    }

    private static ImagePreviewOverlay overlayOfSize(double width, double height, ApplicationConfig config,
            boolean decodeInBackground) {
        ImagePreviewOverlay overlay = new ImagePreviewOverlay(config, decodeInBackground);
        Scene scene = new Scene(overlay, width, height);
        scene.getStylesheets().add(ImagePreviewOverlay.class.getResource("/css/icon-button.css").toExternalForm());
        overlay.applyCss();
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
    void theNextImageIsDecodedInTheBackground() throws Exception {
        Path first = image(dir, "first.png", 640, 480, 0x202020);
        Path second = image(dir, "second.png", 1600, 900, 0x404040);
        ImagePreviewOverlay overlay = onFxThread(() -> {
            ImagePreviewOverlay created = overlayOfSize(1000, 700, null, true);
            created.showImages(List.of(first.toString(), second.toString()), 0);
            return created;
        });
        try {
            waitUntilLoaded(overlay);
            assertEquals(640.0, onFxThread(() -> imageView(overlay).getImage().getWidth()));

            /*  the next image comes once it is decoded, and is fitted from its pixel size  */
            onFxThread(() -> {
                press(overlay, KeyCode.RIGHT);
                return null;
            });
            waitUntilLoaded(overlay);
            assertEquals(1600.0, onFxThread(() -> imageView(overlay).getImage().getWidth()));
            assertTrue(onFxThread(() -> imageView(overlay).getBoundsInParent().getWidth()) <= 1000.0);
        }
        finally {
            onFxThread(() -> {
                overlay.closeOverlay();
                return I18nManager.removeChangeListener(overlay);
            });
        }
        assertTrue(uncaught.isEmpty(), () -> "uncaught: " + uncaught);
    }

    private static void waitUntilLoaded(ImagePreviewOverlay overlay) throws Exception {
        for (int i = 0; i < 200 && onFxThread(overlay::isLoading); i++)
            Thread.sleep(25);
        assertFalse(onFxThread(overlay::isLoading), "the image is decoded");
    }

    /*  the view of the images, the largest image view of the overlay  */
    private static ImageView imageView(ImagePreviewOverlay overlay) {
        return find(overlay, ImageView.class).stream().filter((ImageView view) -> view.getImage() != null)
                .max(java.util.Comparator.comparingDouble((ImageView view) -> view.getImage().getWidth()))
                .orElseThrow();
    }

    @Test
    void theColorsTurnWhileThePreviewIsShownOnly() throws Exception {
        Path image = image(dir, "image.png", 640, 480, 0x202020);
        ApplicationConfig config = config(true);
        ImagePreviewOverlay overlay = onFxThread(() -> overlayOfSize(1000, 700, config));
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
            onFxThread(() -> {
                overlay.closeOverlay();
                return I18nManager.removeChangeListener(overlay);
            });
        }
    }

    @Test
    void switchedOffInTheSettingsTheFrameStandsStill() throws Exception {
        Path image = image(dir, "image.png", 640, 480, 0xffffff);
        ApplicationConfig config = config(false);
        ImagePreviewOverlay overlay = onFxThread(() -> overlayOfSize(1000, 700, config));
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
                    ImagePreviewOverlay overlay = atOnce();
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
        ImagePreviewOverlay overlay = atOnce();
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

    /*  The gaps of the image to the nearest shown buttons on its left, right, top and bottom, the box of the gallery
        counting as a button at the bottom, and the window edge where none shows  */
    private static double[] gaps(ImagePreviewOverlay overlay) {
        Bounds image = imageBounds(overlay);
        double middle = overlay.getHeight() / 2;
        double above = 0.0;
        double below = overlay.getHeight();
        List<Node> shownNodes = new ArrayList<>(previewButtons(overlay));
        find(overlay, Label.class).stream().filter((label) -> shown(label) && label.getText() != null
                && label.getText().matches("\\d+ / \\d+")).forEach((label) -> shownNodes.add(label.getParent()));
        for (Node node : shownNodes) {
            Bounds bounds = node.localToScene(node.getLayoutBounds());
            if (bounds.getMaxY() < middle)
                above = Math.max(above, bounds.getMaxY());
            else if (bounds.getMinY() > middle)
                below = Math.min(below, bounds.getMinY());
        }
        return new double[] {
            image.getMinX() - buttonBounds(overlay, "mdi2c-chevron-left").getMaxX(),
            buttonBounds(overlay, "mdi2c-chevron-right").getMinX() - image.getMaxX(),
            image.getMinY() - above,
            below - image.getMaxY()
        };
    }

    /*  The image lies centered in the safe area, as far from the buttons on the left as on the right and above as
        below, on its tighter axis by the extent of the frame  */
    private static void assertCentered(ImagePreviewOverlay overlay, String what) {
        double[] gaps = gaps(overlay);
        String where = what + ": " + imageBounds(overlay) + ", gaps " + Arrays.toString(gaps);
        assertEquals(gaps[0], gaps[1], 1.0, where);
        assertEquals(gaps[2], gaps[3], 1.0, where);
        assertEquals(PreviewFrame.EXTENT, Math.min(gaps[0], gaps[2]), 1.0, () -> where + " does not fill the area");
    }

    /*  Fitted: the image lies centered in the safe area, no button lies on it  */
    private static void assertFitted(ImagePreviewOverlay overlay, double width, double height) {
        Bounds image = imageBounds(overlay);
        String size = width + "x" + height + ": " + image;
        assertCentered(overlay, size);
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

            /*  zoomed in, a smaller window keeps the zoom, and the image pans until its frame fills the gap to the
                buttons  */
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
                for (double gap : gaps(overlay))
                    assertTrue(gap <= frame + 0.5, image::toString);
                drag(overlay, 2000, 2000);
                double[] gaps = gaps(overlay);
                assertEquals(frame, gaps[0], 0.5);
                assertEquals(frame, gaps[2], 0.5);
                assertFramed(overlay);
                return null;
            });

            /*  a larger window takes the image back to the edge of the safe area, the zoom stays  */
            onFxThread(() -> {
                resize(overlay, 1400, 900);
                assertEquals(zoomed, imageBounds(overlay).getWidth(), 0.5);
                double[] gaps = gaps(overlay);
                assertEquals(frame, gaps[0], 0.5);
                assertEquals(frame, gaps[2], 0.5);
                drag(overlay, -3000, -3000);
                gaps = gaps(overlay);
                assertEquals(frame, gaps[1], 0.5);
                assertEquals(frame, gaps[3], 0.5);
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

    /*  Zoomed in, the image pans into each of the four corners up to the extent of the frame off the buttons  */
    private static void assertSameGapsInAllCorners(ImagePreviewOverlay overlay, String what) {
        for (double[] corner : new double[][] { { 3000, 3000 }, { -3000, 3000 }, { 3000, -3000 }, { -3000, -3000 } }) {
            drag(overlay, corner[0], corner[1]);
            double[] gaps = gaps(overlay);
            String where = what + ", " + Arrays.toString(corner) + ": " + Arrays.toString(gaps);
            assertEquals(PreviewFrame.EXTENT, gaps[corner[0] > 0 ? 0 : 1], 1.0, where);
            assertEquals(PreviewFrame.EXTENT, gaps[corner[1] > 0 ? 2 : 3], 1.0, where);
            assertFramed(overlay);
        }
    }

    @Test
    void theImageKeepsTheSameGapToTheButtonsOnAllFourSides() throws Exception {
        Path big = image(dir, "big.png", 2400, 1600, 0x808080);
        List<String> paths = List.of(big.toString(), big.toString(), big.toString());
        String before = Application.getUserAgentStylesheet();
        ImagePreviewOverlay overlay = onFxThread(() -> {
            ImagePreviewOverlay created = styledOverlayOfSize("primer-dark", 1400, 900);
            created.showImages(paths, 1);
            created.updateChatNavigationButtons(true, true);
            created.layout();
            return created;
        });
        double frame = PreviewFrame.EXTENT;
        try {

            /*  in a small and in a large window: fitted, then at 100 % in each corner, then fitted again  */
            for (double[] size : new double[][] { { 800, 600 }, { 1400, 900 } }) {
                onFxThread(() -> {
                    resize(overlay, size[0], size[1]);
                    assertFitted(overlay, size[0], size[1]);
                    press(overlay, KeyCode.F);
                    assertSameGapsInAllCorners(overlay, size[0] + "x" + size[1]);
                    press(overlay, KeyCode.F);
                    assertFitted(overlay, size[0], size[1]);
                    return null;
                });
            }

            /*  without a next chat nothing lies below the image, which then pans up to the extent of the frame off
                the window edge; play/pause, as for a video, borders it again  */
            onFxThread(() -> {
                overlay.updateChatNavigationButtons(true, false);
                overlay.layout();
                assertCentered(overlay, "no next chat");
                press(overlay, KeyCode.F);
                drag(overlay, -3000, -3000);
                assertEquals(900 - frame, imageBounds(overlay).getMaxY(), 1.0);
                playPauseButton(overlay).setVisible(true);
                overlay.requestLayout();
                overlay.layout();
                assertSameGapsInAllCorners(overlay, "play/pause");
                playPauseButton(overlay).setVisible(false);
                return null;
            });

            /*  the gallery of the images view: its box with counter and line borders the image below  */
            onFxThread(() -> {
                overlay.closeOverlay();
                overlay.showGallery(paths, List.of("Mood · Frame · Model", "Mood", "Frame"), 1);
                overlay.applyCss();
                overlay.layout();
                assertCentered(overlay, "gallery");
                press(overlay, KeyCode.F);
                assertSameGapsInAllCorners(overlay, "gallery");
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

    /*  The shown button of the given icon, in the coordinates of the overlay  */
    private static Bounds buttonBounds(ImagePreviewOverlay overlay, String iconPattern) {
        Button button = previewButtons(overlay).stream()
                .filter((candidate) -> candidate.getGraphic() instanceof FontIcon icon
                        && icon.getIconLiteral().matches(iconPattern))
                .findFirst().orElseThrow(() -> new AssertionError("no shown button " + iconPattern));
        return button.localToScene(button.getLayoutBounds());
    }

    @Test
    void theTopButtonsKeepTheSideMarginOfTheImageArrows() throws Exception {
        Path image = image(dir, "image.png", 640, 480, 0x808080);
        String before = Application.getUserAgentStylesheet();
        ImagePreviewOverlay overlay = onFxThread(() -> {
            ImagePreviewOverlay created = styledOverlayOfSize("primer-dark", 1000, 700);
            created.showImages(List.of(image.toString(), image.toString(), image.toString()), 1);
            created.layout();
            return created;
        });
        try {
            onFxThread(() -> {
                Bounds close    = buttonBounds(overlay, "mdi2c-close");
                Bounds zoom     = buttonBounds(overlay, "mdi2m-magnify(-plus|-minus)?");
                Bounds next     = buttonBounds(overlay, "mdi2c-chevron-right");
                Bounds download = buttonBounds(overlay, "mdi2d-download");
                Bounds edit     = buttonBounds(overlay, "mdi2p-pencil");
                Bounds previous = buttonBounds(overlay, "mdi2c-chevron-left");

                /*  the top buttons stand exactly above the arrows, 30 px off the sides, 20 px below the top  */
                assertEquals(next.getMaxX(), close.getMaxX(), 1.0);
                assertEquals(previous.getMinX(), download.getMinX(), 1.0);
                assertEquals(1000 - 30, close.getMaxX(), 1.0);
                assertEquals(30, download.getMinX(), 1.0);
                assertEquals(1000 - 80, zoom.getMaxX(), 1.0);
                assertEquals(80, edit.getMinX(), 1.0);
                for (Bounds top : List.of(close, zoom, download, edit))
                    assertEquals(20, top.getMinY(), 1.0);
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

    /*  As in the main window: the preview above the layout of the app, which needs 894 px at least  */
    private static StackPane mainWindowOfSize(ImagePreviewOverlay overlay, double width, double height) {
        Region app = new Region();
        app.setMinWidth(894.0);
        StackPane root = new StackPane(app, overlay);
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(ImagePreviewOverlay.class.getResource("/css/icon-button.css").toExternalForm());
        layOut(root, width, height);
        return root;
    }

    /*  Lays out the window, not shown, at the given size, as a pulse does  */
    private static void layOut(StackPane root, double width, double height) {
        root.applyCss();
        root.resize(width, height);
        root.layout();
    }

    /*  The preview covers exactly the window, with every shown button and label inside it, the side buttons 30 px
        off its edges, the arrows of the chat centered, the image in the middle of the safe area  */
    private static void assertCoversTheWindow(ImagePreviewOverlay overlay, double width, double height) {
        String size = width + "x" + height;
        Bounds covered = overlay.localToScene(overlay.getLayoutBounds());
        assertEquals(0.0, covered.getMinX(), 0.5, size);
        assertEquals(0.0, covered.getMinY(), 0.5, size);
        assertEquals(width, covered.getWidth(), 0.5, size);
        assertEquals(height, covered.getHeight(), 0.5, size);
        List<Node> shownNodes = new ArrayList<>(previewButtons(overlay));
        assertTrue(shownNodes.size() >= 6, () -> size + ": " + shownNodes.size() + " buttons");
        find(overlay, Label.class).stream().filter((label) -> shown(label) && label.getText() != null
                && !label.getText().isEmpty()).forEach(shownNodes::add);
        for (Node node : shownNodes) {
            Bounds bounds = node.localToScene(node.getLayoutBounds());
            assertTrue(bounds.getMinX() >= -0.5 && bounds.getMaxX() <= width + 0.5 && bounds.getMinY() >= -0.5
                    && bounds.getMaxY() <= height + 0.5, () -> size + ": " + node + " at " + bounds);
        }
        for (String left : List.of("mdi2d-download", "mdi2c-chevron-left"))
            assertEquals(30.0, buttonBounds(overlay, left).getMinX(), 1.0, size + ", " + left);
        for (String right : List.of("mdi2c-close", "mdi2c-chevron-right"))
            assertEquals(width - 30.0, buttonBounds(overlay, right).getMaxX(), 1.0, size + ", " + right);
        for (Button chat : previewButtons(overlay))
            if (chat.getGraphic() instanceof FontIcon icon && icon.getIconLiteral().matches("mdi2c-chevron-(up|down)"))
                assertEquals(width / 2, chat.localToScene(chat.getLayoutBounds()).getCenterX(), 1.0, size);
        Bounds image = imageBounds(overlay);
        double[] gaps = gaps(overlay);
        assertEquals(width / 2, image.getCenterX(), 1.0, size + ": " + image);
        assertEquals(gaps[2], gaps[3], 1.0, size + ": " + image + ", gaps " + Arrays.toString(gaps));
    }

    @Test
    void inAWindowShrunkBelowTheMinimumOfTheAppThePreviewCoversItExactly() throws Exception {
        Path image = image(dir, "image.png", 1200, 900, 0x808080);
        List<String> paths = List.of(image.toString(), image.toString(), image.toString());
        String before = Application.getUserAgentStylesheet();
        Platform.setImplicitExit(false);
        AtomicReference<Stage> stage = new AtomicReference<>();
        ImagePreviewOverlay overlay = onFxThread(() -> {
            Application.setUserAgentStylesheet(ThemeManager.getAtlantaFXTheme("primer-dark").getUserAgentStylesheet());
            return atOnce();
        });
        try {

            /*  open in a large window which then shrinks to the smallest size of the app; laid out without showing
                it, as the headless screen of the tests holds no window larger than 1280 x 800  */
            onFxThread(() -> {
                StackPane root = mainWindowOfSize(overlay, 1800, 1100);
                overlay.showImages(paths, 1);
                overlay.updateChatNavigationButtons(true, true);
                layOut(root, 1800, 1100);
                layOut(root, 800, 600);
                assertTrue(root.minWidth(-1) > 800, () -> "the app needs " + root.minWidth(-1));
                assertCoversTheWindow(overlay, 800, 600);
                return null;
            });

            /*  shown in a window of that size, then the gallery of the images view with its counter and line  */
            onFxThread(() -> {
                StackPane root = (StackPane) overlay.getParent();
                stage.set(new Stage());
                stage.get().setScene(root.getScene());
                stage.get().setWidth(800);
                stage.get().setHeight(600);
                stage.get().show();
                layOut(root, 800, 600);
                assertCoversTheWindow(overlay, 800, 600);
                overlay.closeOverlay();
                overlay.showGallery(paths, List.of("Mood · Frame · Model", "Mood", "Frame"), 1);
                layOut(root, 800, 600);
                assertCoversTheWindow(overlay, 800, 600);
                return null;
            });
        }
        finally {
            onFxThread(() -> {
                overlay.closeOverlay();
                if (stage.get() != null)
                    stage.get().hide();
                Application.setUserAgentStylesheet(before);
                return I18nManager.removeChangeListener(overlay);
            });
        }
        assertTrue(uncaught.isEmpty(), () -> "uncaught: " + uncaught);
    }

    /*  Every edge of the image on a whole device pixel of the given screen scale, the line of the frame flush  */
    private static void assertOnDevicePixels(ImagePreviewOverlay overlay, double scale) {
        Bounds image = imageBounds(overlay);
        for (double edge : new double[] { image.getMinX(), image.getMaxX(), image.getMinY(), image.getMaxY() })
            assertEquals(Math.round(edge * scale) / scale, edge, 1e-6, () -> scale + ": " + image);
        assertFramed(overlay);
    }

    /*  In the picture of a white image, the last pixel at each of the given number of shown edges belongs to the
        image, the two right outside to the 2 px line of the frame, in one color: no seam of the dark stage  */
    private static void assertNoSeam(ImagePreviewOverlay overlay, int shownEdges) {
        Bounds image = imageBounds(overlay);
        PixelReader pixels = overlay.getScene().snapshot(null).getPixelReader();
        int x0 = (int) image.getMinX();
        int x1 = (int) image.getMaxX();
        int y0 = (int) image.getMinY();
        int y1 = (int) image.getMaxY();
        int cx = (int) Math.max(x0, Math.min(x1, overlay.getWidth() / 2));
        int cy = (int) Math.max(y0, Math.min(y1, overlay.getHeight() / 2));
        int checked = 0;
        int[][] edges = { { x0, cy, -1, 0 }, { x1 - 1, cy, 1, 0 }, { cx, y0, 0, -1 }, { cx, y1 - 1, 0, 1 } };
        for (int[] edge : edges) {
            int x = edge[0];
            int y = edge[1];
            int outerX = x + 2 * edge[2];
            int outerY = y + 2 * edge[3];
            if (outerX < 0 || outerY < 0 || outerX >= overlay.getWidth() || outerY >= overlay.getHeight()
                    || x < 0 || y < 0 || x >= overlay.getWidth() || y >= overlay.getHeight())
                continue;
            Color inside = pixels.getColor(x, y);
            Color line = pixels.getColor(x + edge[2], y + edge[3]);
            Color outer = pixels.getColor(outerX, outerY);
            String what = image + " at " + x + "," + y + ": " + inside + ", " + line + ", " + outer;
            assertTrue(inside.getRed() > 0.98 && inside.getGreen() > 0.98 && inside.getBlue() > 0.98, what);
            assertTrue(Math.max(line.getRed(), Math.max(line.getGreen(), line.getBlue())) > 0.6, what);
            assertTrue(Math.abs(line.getRed() - outer.getRed()) < 0.03 && Math.abs(line.getGreen() - outer.getGreen())
                    < 0.03 && Math.abs(line.getBlue() - outer.getBlue()) < 0.03, what);
            checked++;
        }
        assertEquals(shownEdges, checked, image::toString);
    }

    @Test
    void imageAndFrameLieOnWholePixelsWithoutASeam() throws Exception {
        Path white = image(dir, "white.png", 2000, 1500, 0xffffff);
        ImagePreviewOverlay overlay = onFxThread(() -> {
            ImagePreviewOverlay created = overlayOfSize(1000, 700);
            created.showImages(List.of(white.toString()), 0);

            /*  a chat with entries before and after, whose arrows border the image above and below evenly  */
            created.updateChatNavigationButtons(true, true);
            created.layout();
            return created;
        });
        try {

            /*  fitted, its size and its centered position would be fractions of pixels  */
            onFxThread(() -> {
                assertOnDevicePixels(overlay, 1.0);
                assertNoSeam(overlay, 4);
                return null;
            });

            /*  zoomed in, still within the window, then larger than it  */
            for (int edges : new int[] { 4, 0 }) {
                onFxThread(() -> {
                    press(overlay, KeyCode.PLUS);
                    assertOnDevicePixels(overlay, 1.0);
                    assertNoSeam(overlay, edges);
                    return null;
                });
            }

            /*  panned by fractions of pixels, up to the upper left edges, a little back, up to the lower right ones  */
            double[][] deltas = { { 10.3, -7.7 }, { 3000.0, 3000.0 }, { -0.4, 0.6 }, { -3000.0, -3000.0 } };
            int[] shown = { 0, 2, 2, 2 };
            for (int i = 0; i < deltas.length; i++) {
                double[] delta = deltas[i];
                int edges = shown[i];
                onFxThread(() -> {
                    drag(overlay, delta[0], delta[1]);
                    assertOnDevicePixels(overlay, 1.0);
                    assertNoSeam(overlay, edges);
                    return null;
                });
            }

            /*  a window of odd size, the image there panned up to the lower right edges once more  */
            onFxThread(() -> {
                resize(overlay, 917, 633);
                assertOnDevicePixels(overlay, 1.0);
                drag(overlay, -3000.0, -3000.0);
                assertOnDevicePixels(overlay, 1.0);
                assertNoSeam(overlay, 2);
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
    void atAScreenScaleOf2ImageAndFrameLieOnItsDevicePixels() throws Exception {
        Path white = image(dir, "white.png", 2000, 1500, 0xffffff);
        Platform.setImplicitExit(false);
        AtomicReference<Stage> stage = new AtomicReference<>();
        ImagePreviewOverlay overlay = onFxThread(() -> {
            ImagePreviewOverlay created = atOnce();
            stage.set(new Stage());
            stage.get().setScene(new Scene(created, 1000, 700));
            stage.get().show();
            stage.get().setRenderScaleX(2.0);
            stage.get().setRenderScaleY(2.0);
            resize(created, 1000, 700);
            created.showImages(List.of(white.toString()), 0);
            created.layout();
            return created;
        });
        try {
            onFxThread(() -> {
                assertOnDevicePixels(overlay, 2.0);
                Bounds image = imageBounds(overlay);
                assertTrue(image.getMinX() % 1.0 != 0.0 || image.getMaxX() % 1.0 != 0.0
                        || image.getMinY() % 1.0 != 0.0 || image.getMaxY() % 1.0 != 0.0,
                        () -> "on half pixels, not just on whole ones: " + image);
                press(overlay, KeyCode.PLUS);
                press(overlay, KeyCode.PLUS);
                drag(overlay, 10.3, -7.7);
                assertOnDevicePixels(overlay, 2.0);
                return null;
            });
        }
        finally {
            onFxThread(() -> {
                overlay.closeOverlay();
                stage.get().hide();
                return I18nManager.removeChangeListener(overlay);
            });
        }
        assertTrue(uncaught.isEmpty(), () -> "uncaught: " + uncaught);
    }

    /*  A configuration without file that lets the frame turn or not  */
    private static ApplicationConfig config(boolean animated) {
        ApplicationConfig config = ConfigMocks.config();
        when(config.ui().isPreviewFrameAnimated()).thenReturn(animated);
        return config;
    }
}
