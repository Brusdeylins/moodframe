package art.moodframe.ui.component;

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
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
}
