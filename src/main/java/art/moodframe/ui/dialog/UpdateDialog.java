package art.moodframe.ui.dialog;

import art.moodframe.service.UpdateCheckService;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Dialog with the installed and the available version of MoodFrame. On Linux, where Conveyor does not update the app,
 * a newer version comes with the steps for the package -- apt for the .deb, yay or paru for the AUR package, the new
 * archive for the .tar.gz -- and a button to hide the hint for this version.
 *
 * @since 2.1.0
 */
public final class UpdateDialog {

    private static final Logger logger = LoggerFactory.getLogger(UpdateDialog.class);

    private static final double WIDTH = 480;

    private UpdateDialog() {
    }

    /**
     * Shows the dialog and fills in the available version as soon as the check is done.
     *
     * @param owner the window the dialog belongs to
     * @param updates the update check
     * @param check the running or finished check
     * @return true if the user hid the hint for the available version
     */
    public static boolean showAndWait(Window owner, UpdateCheckService updates,
            CompletableFuture<UpdateCheckService.Result> check) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle(I18nManager.getText("update.title"));
        alert.setHeaderText(I18nManager.getText("update.checking"));
        alert.getDialogPane().setPrefWidth(WIDTH);

        Label available = new Label("…");
        GridPane versions = new GridPane();
        versions.setHgap(12);
        versions.setVgap(4);
        versions.addRow(0, new Label(I18nManager.getText("update.installed")), new Label(updates.installedVersion()));
        versions.addRow(1, new Label(I18nManager.getText("update.available")), available);
        VBox content = new VBox(12, versions);
        alert.getDialogPane().setContent(content);

        ButtonType close = new ButtonType(I18nManager.getText("button.close"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType dismiss = new ButtonType(I18nManager.getText("update.dismiss"), ButtonBar.ButtonData.OTHER);
        alert.getButtonTypes().setAll(close);

        /*  The package is told off the UI thread, as it asks pacman  */
        CompletableFuture<UpdateCheckService.Package> packageKind =
                CompletableFuture.supplyAsync(() -> updates.isLinux() ? updates.packageKind() : null);
        check.thenAcceptBoth(packageKind,
                (UpdateCheckService.Result result, UpdateCheckService.Package kind) -> Platform.runLater(() -> {
            if (result.available() == null) {
                alert.setHeaderText(I18nManager.getText("update.failed"));
                available.setText("–");
                return;
            }
            available.setText(result.available());
            if (!result.isNewer()) {
                alert.setHeaderText(I18nManager.getText("update.current"));
                return;
            }
            alert.setHeaderText(I18nManager.getText("update.newer", result.available()));

            /*  Conveyor updates macOS and Windows at the next start, Linux needs the user  */
            if (updates.isLinux()) {
                content.getChildren().add(steps(kind));
                alert.getButtonTypes().setAll(dismiss, close);

                /*  The long label keeps its width instead of being cut to the size of "Close"  */
                ButtonBar.setButtonUniformSize(alert.getDialogPane().lookupButton(dismiss), false);
            }
            if (alert.getDialogPane().getScene().getWindow() != null)
                alert.getDialogPane().getScene().getWindow().sizeToScene();
        }));

        if (alert.showAndWait().orElse(close) != dismiss)
            return false;
        updates.dismiss(check.join().available());
        return true;
    }

    /*  The steps of the update: the apt or yay command to copy, or the new archive with the link to the download  */
    private static VBox steps(UpdateCheckService.Package kind) {
        if (kind == UpdateCheckService.Package.DEB) {
            TextField command = new TextField(UpdateCheckService.APT_COMMAND);
            command.setEditable(false);
            return new VBox(6, wrapped(I18nManager.getText("update.deb")), command);
        }
        else if (kind == UpdateCheckService.Package.AUR) {
            TextField command = new TextField(UpdateCheckService.AUR_COMMAND);
            command.setEditable(false);
            return new VBox(6, wrapped(I18nManager.getText("update.aur")), command);
        }
        else {
            Hyperlink link = new Hyperlink(UpdateCheckService.INSTALL_URL);
            link.setOnAction((event) -> openInBrowser(UpdateCheckService.INSTALL_URL));
            return new VBox(6, wrapped(I18nManager.getText("update.tarball")), link);
        }
    }

    private static Label wrapped(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(WIDTH - 40);
        return label;
    }

    /*  The default browser; without the AWT desktop support, which many Linux desktops lack, via xdg-open  */
    private static void openInBrowser(String url) {
        CompletableFuture.runAsync(() -> {
            try {
                if (java.awt.Desktop.isDesktopSupported()
                        && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE))
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                else
                    new ProcessBuilder("xdg-open", url).start();
            }
            catch (IOException | RuntimeException e) {
                logger.warn("Could not open {}: {}", url, e.getMessage());
            }
        });
    }
}
