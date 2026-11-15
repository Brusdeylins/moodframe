package art.moodframe.ui.dialog;

import art.moodframe.service.UpdateCheckService;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The steps of the update in the dialog, in English and German: the download page on macOS and Windows, apt for the
 * .deb, the Arch package built anew from the repository as long as it is not in the AUR, the new archive for the
 * .tar.gz.
 */
class UpdateDialogTest {

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

    /*  the texts of the steps, one per line: the sentences, the link and the command to copy  */
    private static String texts(VBox steps) {
        return steps.getChildren().stream()
                .map((node) -> node instanceof TextField field ? field.getText() : ((Labeled) node).getText())
                .collect(Collectors.joining("\n"));
    }

    @Test
    void showsTheStepsOfEverySystem() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Locale before = I18nManager.getCurrentLocale();
        Platform.runLater(() -> {
            try {
                for (Locale locale : List.of(Locale.ENGLISH, Locale.GERMAN)) {
                    I18nManager.setLocale(locale);

                    /*  macOS and Windows have no package: Conveyor's update failed, so the download page  */
                    VBox conveyor = UpdateDialog.steps(null);
                    assertTrue(texts(conveyor).startsWith(I18nManager.getText("update.conveyor")), locale + "");
                    assertInstanceOf(Hyperlink.class, conveyor.getChildren().get(1));
                    assertTrue(texts(conveyor).endsWith(UpdateCheckService.INSTALL_URL));

                    assertTrue(texts(UpdateDialog.steps(UpdateCheckService.Package.DEB))
                            .endsWith(UpdateCheckService.APT_COMMAND));
                    String tarball = texts(UpdateDialog.steps(UpdateCheckService.Package.TARBALL));
                    assertTrue(tarball.endsWith(UpdateCheckService.INSTALL_URL));
                    assertFalse(tarball.contains("AUR"), "no hint at the AUR: " + tarball);

                    String arch = texts(UpdateDialog.steps(UpdateCheckService.Package.AUR));
                    if (UpdateCheckService.AUR_PUBLISHED)
                        assertTrue(arch.endsWith(UpdateCheckService.AUR_COMMAND));
                    else {
                        assertTrue(arch.contains("packaging/aur/moodframe-bin"), arch);
                        assertTrue(arch.endsWith("git pull && makepkg -si"), arch);
                        assertFalse(arch.contains("AUR") || arch.contains("yay") || arch.contains("paru"),
                                "the AUR is not recommended before the package is in it: " + arch);
                    }
                }
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                I18nManager.setLocale(before);
                done.countDown();
            }
        });

        assertTrue(done.await(20, TimeUnit.SECONDS), "the steps were not built in time");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }
}
