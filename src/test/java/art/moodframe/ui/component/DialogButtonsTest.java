package art.moodframe.ui.component;

import art.moodframe.ui.dialog.CameraEditDialog;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The buttons of the dialogs speak the language of the app, not the one of the system as the built-in ones do. */
class DialogButtonsTest {

    /*  the built-in buttons of JavaFX, in the code and in the FXML files  */
    private static final Pattern BUILT_IN_CODE = Pattern.compile("ButtonType\\.(OK|CANCEL|YES|NO|CLOSE|APPLY)\\b");
    private static final Pattern BUILT_IN_FXML = Pattern.compile("<ButtonType\\s+fx:constant=");

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

    /** Runs a step on the JavaFX thread and returns its result. */
    private static <T> T onFxThread(Supplier<T> step) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            result.set(step.get());
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        return result.get();
    }

    @Test
    void noDialogUsesTheBuiltInButtons() throws IOException {
        List<String> found;
        try (Stream<Path> files = Stream.concat(Files.walk(Path.of("src/main/java")),
                Files.walk(Path.of("src/main/resources/fxml")))) {
            found = files.filter(Files::isRegularFile).flatMap((file) -> {
                Pattern pattern = file.toString().endsWith(".fxml") ? BUILT_IN_FXML : BUILT_IN_CODE;
                try {
                    List<String> lines = Files.readAllLines(file);
                    return IntStream.range(0, lines.size())
                            .filter((index) -> pattern.matcher(lines.get(index)).find())
                            .mapToObj((index) -> file + ":" + (index + 1));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).toList();
        }

        assertEquals(List.of(), found, "use DialogButtons instead");
    }

    @Test
    void keepsTheRolesOfTheBuiltInButtons() {
        assertEquals(ButtonType.OK.getButtonData(), DialogButtons.ok().getButtonData());
        assertEquals(ButtonType.CANCEL.getButtonData(), DialogButtons.cancel().getButtonData());
        assertEquals(ButtonType.CLOSE.getButtonData(), DialogButtons.close().getButtonData());
        assertEquals(ButtonType.YES.getButtonData(), DialogButtons.yes().getButtonData());
        assertEquals(ButtonType.NO.getButtonData(), DialogButtons.no().getButtonData());
    }

    @Test
    void labelsTheCancelButtonOfTheCameraDialogInTheLanguageOfTheAppOnAGermanSystem() throws Exception {
        Locale system = Locale.getDefault();
        Locale before = I18nManager.getCurrentLocale();
        try {
            Locale.setDefault(Locale.GERMAN);
            assertEquals("Abbrechen", ButtonType.CANCEL.getText(), "the built-in button follows the system");

            CameraEditDialog dialog = onFxThread(() -> {
                I18nManager.setLocale(Locale.ENGLISH);
                return new CameraEditDialog(null, null);
            });
            assertEquals("Cancel", onFxThread(() -> cancelText(dialog)));

            /*  the open dialog follows a change of the language; its texts are set anew on the next pulse  */
            onFxThread(() -> {
                I18nManager.setLocale(Locale.GERMAN);
                return null;
            });
            assertEquals("Abbrechen", onFxThread(() -> cancelText(dialog)));
        } finally {
            Locale.setDefault(system);
            onFxThread(() -> {
                I18nManager.setLocale(before);
                return null;
            });
        }
    }

    private static String cancelText(CameraEditDialog dialog) {
        ButtonType cancel = dialog.getDialogPane().getButtonTypes().stream()
                .filter((type) -> type.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE).findFirst().orElseThrow();
        return ((Button) dialog.getDialogPane().lookupButton(cancel)).getText();
    }
}
