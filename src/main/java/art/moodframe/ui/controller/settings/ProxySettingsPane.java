package art.moodframe.ui.controller.settings;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.net.CertificateStore;
import art.moodframe.net.HttpClients;
import art.moodframe.util.I18nManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

/**
 * The corporate proxy section of the settings ({@code ProxySettingsPane.fxml}): the proxy with its test, and the
 * certificate folder ({@link CertificateStore}) with import, removal and an SSL test. The proxy is saved with
 * "Save"; certificates are imported and removed at once.
 */
public class ProxySettingsPane implements SettingsSection {

    private static final Logger logger = LoggerFactory.getLogger(ProxySettingsPane.class);

    /** What a status line shows, as far as a change of the language relabels it. */
    enum Shown {
        /** "No test yet", the text of the FXML. */
        NONE,
        /** The success of the test. */
        SUCCESS,
        /** Another text, e.g. with the details of an error, which stays. */
        OTHER
    }

    @FXML
    private CheckBox enableProxyCheckBox;
    @FXML
    private TextField proxyHostField;
    @FXML
    private Spinner<Integer> proxyPortSpinner;
    @FXML
    private TextField proxyUsernameField;
    @FXML
    private PasswordField proxyPasswordField;
    @FXML
    private Label proxyTitleLabel;
    @FXML
    private Label proxyDescLabel;
    @FXML
    private Label proxyHostLabel;
    @FXML
    private Label proxyPortLabel;
    @FXML
    private Label proxyAuthLabel;
    @FXML
    private Label proxyUsernameLabel;
    @FXML
    private Label proxyPasswordLabel;
    @FXML
    private Label certificatesTitleLabel;
    @FXML
    private Label certificatesDescLabel;
    @FXML
    private Label certificatesDirectoryLabel;
    @FXML
    private Label certificatesUploadTitleLabel;
    @FXML
    private Label certificatesFormatsLabel;
    @FXML
    private Label certificatesInstalledLabel;
    @FXML
    private HBox proxyConfigSourceContainer;
    @FXML
    private FontIcon proxyConfigSourceIcon;
    @FXML
    private Label proxyConfigSourceLabel;
    @FXML
    private Label certificatePathLabel;
    @FXML
    private Button uploadCertificateButton;
    @FXML
    private Label certificateUploadStatus;
    @FXML
    private ListView<String> installedCertificatesListView;
    @FXML
    private Button removeCertificateButton;
    @FXML
    private Button refreshCertificatesButton;
    @FXML
    private Button testProxyConnectionButton;
    @FXML
    private Button resetProxyConfigButton;
    @FXML
    private HBox proxyConnectionStatusContainer;
    @FXML
    private FontIcon proxyConnectionStatusIcon;
    @FXML
    private Label proxyConnectionStatusLabel;
    @FXML
    private Button testCertificateButton;
    @FXML
    private HBox certificateConnectionStatusContainer;
    @FXML
    private FontIcon certificateConnectionStatusIcon;
    @FXML
    private Label certificateConnectionStatusLabel;

    private SettingsServices services;
    private ApplicationConfig config;
    private StatusLine proxyStatus;
    private StatusLine certificateStatus;
    private Shown proxyShown = Shown.NONE;
    private Shown certificateShown = Shown.NONE;
    private ObservableList<String> certificatesList;

    @Override
    public void init(SettingsServices services) {
        this.services = services;
        this.config = services.config();
        try {
            proxyPortSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 65535, 8080));

            certificatesList = FXCollections.observableArrayList();
            installedCertificatesListView.setItems(certificatesList);
            installedCertificatesListView
                    .setPlaceholder(new Label(I18nManager.getText("settings.certificates.none.found")));
            installedCertificatesListView.setPrefHeight(120.0);
            installedCertificatesListView.setMaxHeight(150.0);

            /*  A selected certificate can be removed  */
            installedCertificatesListView.getSelectionModel().selectedItemProperty().addListener(
                    (obs, oldSelection, newSelection) -> removeCertificateButton.setDisable(
                            newSelection == null || newSelection.trim().isEmpty()));
            installedCertificatesListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

            proxyStatus = new StatusLine(proxyConnectionStatusContainer, proxyConnectionStatusLabel,
                    proxyConnectionStatusIcon);
            certificateStatus = new StatusLine(certificateConnectionStatusContainer, certificateConnectionStatusLabel,
                    certificateConnectionStatusIcon);
            proxyConfigSourceContainer.setVisible(false);
            proxyStatus.hide();
            certificateStatus.hide();

            enableProxyCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> updateProxyFieldsEnabledState());
        } catch (Exception e) {
            logger.error("Failed to initialize Corporate Proxy tab", e);
            services.prompts().error(I18nManager.getText("settings.proxy.error.init.title"),
                    I18nManager.getText("settings.proxy.error.init", e.getMessage()));
        }
    }

    @Override
    public void load() {
        try {
            enableProxyCheckBox.setSelected(config.proxy().isProxyEnabled());

            String proxyHost = config.proxy().getProxyHost();
            if (proxyHost != null)
                proxyHostField.setText(proxyHost);
            proxyPortSpinner.getValueFactory().setValue(config.proxy().getProxyPort());

            String proxyUsername = config.proxy().getProxyUsername();
            if (proxyUsername != null)
                proxyUsernameField.setText(proxyUsername);
            String proxyPassword = config.proxy().getProxyPassword();
            if (proxyPassword != null)
                proxyPasswordField.setText(proxyPassword);

            /*  The certificate path is detected, not chosen  */
            certificatePathLabel.setText(config.proxy().getCertificatePath());

            updateProxyFieldsEnabledState();
            loadInstalledCertificates();
        } catch (Exception e) {
            logger.error("Failed to load proxy settings", e);
            services.prompts().error(I18nManager.getText("settings.proxy.error.load.title"),
                    I18nManager.getText("settings.proxy.error.load", e.getMessage()));
        }
    }

    /** Shows the proxy after a reset; the password is kept, so its field stays. */
    @Override
    public void reset() {
        enableProxyCheckBox.setSelected(config.proxy().isProxyEnabled());

        String proxyHost = config.proxy().getProxyHost();
        if (proxyHost != null)
            proxyHostField.setText(proxyHost);
        else
            proxyHostField.clear();
        proxyPortSpinner.getValueFactory().setValue(config.proxy().getProxyPort());

        String proxyUsername = config.proxy().getProxyUsername();
        if (proxyUsername != null)
            proxyUsernameField.setText(proxyUsername);
        else
            proxyUsernameField.clear();

        updateProxyFieldsEnabledState();
        certificatePathLabel.setText(config.proxy().getCertificatePath());
    }

    /** Saves the proxy, unless a policy sets it, and rebuilds the HTTP clients of all services with it. */
    @Override
    public void apply() {
        try {
            if (!config.proxy().isProxyConfigurationEditable()) {
                logger.warn("Proxy settings are not editable due to policy configuration");
                return;
            }
            config.proxy().setProxyEnabled(enableProxyCheckBox.isSelected());

            String proxyHost = proxyHostField.getText();
            config.proxy().setProxyHost(proxyHost != null && !proxyHost.trim().isEmpty() ? proxyHost.trim() : null);
            config.proxy().setProxyPort(proxyPortSpinner.getValue());

            String proxyUsername = proxyUsernameField.getText();
            config.proxy().setProxyUsername(proxyUsername != null && !proxyUsername.trim().isEmpty()
                    ? proxyUsername.trim() : null);
            String proxyPassword = proxyPasswordField.getText();
            config.proxy().setProxyPassword(proxyPassword != null && !proxyPassword.trim().isEmpty()
                    ? proxyPassword.trim() : null);
            logger.debug("Proxy settings saved to configuration");

            HttpClients.settingsChanged();
            logger.debug("HTTP client proxy configuration refreshed");
        } catch (Exception e) {
            logger.error("Failed to save proxy settings", e);
            throw new RuntimeException("Failed to save proxy settings", e);
        }
    }

    @Override
    public void relabel() {
        testProxyConnectionButton.setText(I18nManager.getText("settings.proxy.test"));
        resetProxyConfigButton.setText(I18nManager.getText("settings.proxy.clear"));
        uploadCertificateButton.setText(I18nManager.getText("settings.certificates.upload.button"));
        removeCertificateButton.setText(I18nManager.getText("settings.certificates.remove"));
        refreshCertificatesButton.setText(I18nManager.getText("settings.certificates.refresh"));
        testCertificateButton.setText(I18nManager.getText("settings.certificates.test"));

        proxyTitleLabel.setText(I18nManager.getText("settings.proxy.title"));
        proxyDescLabel.setText(I18nManager.getText("settings.proxy.description"));
        proxyHostLabel.setText(I18nManager.getText("settings.proxy.host"));
        proxyPortLabel.setText(I18nManager.getText("settings.proxy.port"));
        proxyAuthLabel.setText(I18nManager.getText("settings.proxy.auth"));
        proxyUsernameLabel.setText(I18nManager.getText("settings.proxy.username"));
        proxyPasswordLabel.setText(I18nManager.getText("settings.proxy.password"));
        enableProxyCheckBox.setText(I18nManager.getText("settings.proxy.enable"));

        certificatesTitleLabel.setText(I18nManager.getText("settings.certificates.title"));
        certificatesDescLabel.setText(I18nManager.getText("settings.certificates.description"));
        certificatesDirectoryLabel.setText(I18nManager.getText("settings.certificates.directory"));
        certificatesUploadTitleLabel.setText(I18nManager.getText("settings.certificates.upload.title"));
        certificatesFormatsLabel.setText(I18nManager.getText("settings.certificates.formats"));
        certificatesInstalledLabel.setText(I18nManager.getText("settings.certificates.installed"));

        proxyHostField.setPromptText(I18nManager.getText("settings.proxy.host.placeholder"));
        proxyUsernameField.setPromptText(I18nManager.getText("settings.proxy.username.placeholder"));
        proxyPasswordField.setPromptText(I18nManager.getText("settings.proxy.password.placeholder"));

        /*  The status lines are relabeled only while they show a text without details  */
        proxyConnectionStatusLabel.setText(text(proxyShown, proxyConnectionStatusLabel.getText(),
                "settings.proxy.connection.none", "settings.test.proxy.success"));
        certificateConnectionStatusLabel.setText(text(certificateShown, certificateConnectionStatusLabel.getText(),
                "settings.certificates.connection.none", "settings.test.certificate.success"));
    }

    /**
     * Tells the text of a status line in the current language.
     *
     * @param shown      what the line shows
     * @param text       the text it shows
     * @param noneKey    the key of "no test yet"
     * @param successKey the key of the success
     * @return the text in the current language, or the shown text for another one
     */
    static String text(Shown shown, String text, String noneKey, String successKey) {
        return switch (shown) {
            case NONE -> I18nManager.getText(noneKey);
            case SUCCESS -> I18nManager.getText(successKey);
            case OTHER -> text;
        };
    }

    /*  shows a status of the proxy test; of its results only a success is relabeled  */
    private void showProxyStatus(String status, String message) {
        proxyShown = "success".equals(status) ? Shown.SUCCESS : Shown.OTHER;
        proxyStatus.show(status, message);
    }

    /*  shows a status of the certificate test; of its results only a success is relabeled  */
    private void showCertificateStatus(String status, String message) {
        certificateShown = "success".equals(status) ? Shown.SUCCESS : Shown.OTHER;
        certificateStatus.show(status, message);
    }

    /** Enables the proxy fields while the proxy is on and the settings, not a policy, set it. */
    private void updateProxyFieldsEnabledState() {
        boolean proxyEnabled = enableProxyCheckBox.isSelected();
        boolean isEditable = config.proxy().isProxyConfigurationEditable();

        proxyHostField.setDisable(!proxyEnabled || !isEditable);
        proxyPortSpinner.setDisable(!proxyEnabled || !isEditable);
        proxyUsernameField.setDisable(!proxyEnabled || !isEditable);
        proxyPasswordField.setDisable(!proxyEnabled || !isEditable);
        testProxyConnectionButton.setDisable(!proxyEnabled);

        if (!isEditable)
            showProxyConfigSourceInfo();
    }

    /** Tells where a proxy set by a policy comes from. */
    private void showProxyConfigSourceInfo() {
        String source = config.proxy().getProxyConfigurationSource();
        if (!"none".equals(source) && !"configuration".equals(source)) {
            String sourceText = switch (source) {
                case "environment" -> I18nManager.getText("settings.proxy.source.environment");
                case "system_property" -> I18nManager.getText("settings.proxy.source.system");
                default -> I18nManager.getText("settings.proxy.source.other", source);
            };

            proxyConfigSourceContainer.setVisible(true);
            proxyConfigSourceIcon.setIconLiteral("mdi2i-information");
            proxyConfigSourceIcon.getStyleClass().clear();
            proxyConfigSourceIcon.getStyleClass().addAll("ikonli-font-icon", "accent");
            proxyConfigSourceLabel.setText(sourceText);
        } else {
            proxyConfigSourceContainer.setVisible(false);
        }
    }

    /** Lists the certificates of the folder, keeping the selection if it is still there. */
    private void loadInstalledCertificates() {
        try {
            String previousSelection = installedCertificatesListView.getSelectionModel().getSelectedItem();
            certificatesList.clear();

            CertificateStore certificates = certificates();
            if (certificates.exists()) {
                List<String> certFiles = certificates.list();
                certificatesList.addAll(certFiles);

                if (certFiles.isEmpty()) {
                    certificateUploadStatus.setText(I18nManager.getText("settings.certificates.none.found"));
                    removeCertificateButton.setDisable(true);
                } else {
                    certificateUploadStatus
                            .setText(I18nManager.getText("settings.certificates.count.found", certFiles.size()));
                    if (previousSelection != null && certFiles.contains(previousSelection)) {
                        installedCertificatesListView.getSelectionModel().select(previousSelection);
                    } else {
                        installedCertificatesListView.getSelectionModel().clearSelection();
                        removeCertificateButton.setDisable(true);
                    }
                }
            } else {
                /*  The folder is created by the first import  */
                certificateUploadStatus.setText(I18nManager.getText("settings.certificates.directory.created"));
                removeCertificateButton.setDisable(true);
            }
        } catch (Exception e) {
            logger.error("Failed to load installed certificates", e);
            certificateUploadStatus.setText(I18nManager.getText("settings.certificates.error"));
            removeCertificateButton.setDisable(true);
        }
    }

    /*  the folder of the corporate certificates, as the settings say  */
    private CertificateStore certificates() {
        return new CertificateStore(Paths.get(config.proxy().getCertificatePath().trim()));
    }

    /** Copies the chosen certificate files into the folder, asking before one is replaced. */
    @FXML
    private void onUploadCertificate() {
        CertificateStore certificates = certificates();
        try {
            certificates.createDirectory();
        } catch (IOException e) {
            logger.error("Failed to create certificate directory: {}", certificates.directory(), e);
            services.prompts().error(I18nManager.getText("settings.certificates.error.directory.title"),
                    I18nManager.getText("settings.certificates.error.directory", e.getMessage()));
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18nManager.getText("settings.certificates.chooser.title"));
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(I18nManager.getText("settings.certificates.chooser.certificates"),
                        "*.crt", "*.cer", "*.pem"),
                new FileChooser.ExtensionFilter(I18nManager.getText("settings.certificates.chooser.type", "CRT"),
                        "*.crt"),
                new FileChooser.ExtensionFilter(I18nManager.getText("settings.certificates.chooser.type", "CER"),
                        "*.cer"),
                new FileChooser.ExtensionFilter(I18nManager.getText("settings.certificates.chooser.type", "PEM"),
                        "*.pem"),
                new FileChooser.ExtensionFilter(I18nManager.getText("settings.certificates.chooser.all"), "*.*"));
        List<File> selectedFiles = services.prompts().openFiles(fileChooser,
                uploadCertificateButton.getScene().getWindow());
        if (selectedFiles == null || selectedFiles.isEmpty())
            return;

        int successCount = 0;
        int failedCount = 0;
        StringBuilder failedFiles = new StringBuilder();
        for (File sourceFile : selectedFiles) {
            try {
                if (certificates.contains(sourceFile.getName()) && !services.prompts().confirm(
                        I18nManager.getText("settings.certificates.exists.title"),
                        I18nManager.getText("settings.certificates.exists.header"),
                        I18nManager.getText("settings.certificates.exists", sourceFile.getName()), false)) {
                    failedCount++;
                    failedFiles.append("\n- ").append(sourceFile.getName()).append(" (")
                            .append(I18nManager.getText("settings.certificates.upload.skipped")).append(")");
                    continue;
                }
                certificates.importFile(sourceFile.toPath());
                successCount++;
            } catch (IOException e) {
                logger.error("Failed to upload certificate: {}", sourceFile.getName(), e);
                failedCount++;
                failedFiles.append("\n- ").append(sourceFile.getName()).append(" (")
                        .append(I18nManager.getText("settings.certificates.upload.error", e.getMessage())).append(")");
            }
        }

        loadInstalledCertificates();
        if (successCount > 0 && failedCount == 0) {
            certificateUploadStatus.setText(I18nManager.getText("settings.certificates.uploaded", successCount));
        } else if (successCount > 0) {
            services.prompts().error(I18nManager.getText("settings.certificates.upload.partial.title"),
                    I18nManager.getText("settings.certificates.upload.partial", successCount, failedCount,
                            failedFiles.toString()));
            certificateUploadStatus.setText(I18nManager.getText("settings.certificates.upload.partial.status",
                    successCount, failedCount));
        } else {
            services.prompts().error(I18nManager.getText("settings.certificates.upload.failed.title"),
                    I18nManager.getText("settings.certificates.upload.failed", failedFiles.toString()));
            certificateUploadStatus.setText(I18nManager.getText("settings.certificates.upload.failed.status"));
        }
    }

    /** Removes the selected certificate after a confirmation. */
    @FXML
    private void onRemoveCertificate() {
        String selectedCertificate = installedCertificatesListView.getSelectionModel().getSelectedItem();
        if (selectedCertificate == null)
            return;
        String removeTitle = I18nManager.getText("settings.certificates.remove.title");
        if (!services.prompts().confirm(removeTitle, removeTitle,
                I18nManager.getText("settings.certificates.remove.confirm", selectedCertificate), false))
            return;

        try {
            if (certificates().delete(selectedCertificate)) {
                loadInstalledCertificates();
                certificateUploadStatus.setText(I18nManager.getText("settings.certificates.removed",
                        selectedCertificate));
            } else {
                services.prompts().error(I18nManager.getText("settings.certificates.error.notfound.title"),
                        I18nManager.getText("settings.certificates.error.notfound", selectedCertificate));
            }
        } catch (IOException e) {
            logger.error("Failed to remove certificate: {}", selectedCertificate, e);
            services.prompts().error(I18nManager.getText("settings.certificates.error.remove.title"),
                    I18nManager.getText("settings.certificates.error.remove", e.getMessage()));
        }
    }

    @FXML
    private void onRefreshCertificates() {
        loadInstalledCertificates();
    }

    /** Tests the saved proxy with the BFL client. */
    @FXML
    private void onTestProxyConnection() {
        showProxyStatus("testing", I18nManager.getText("settings.test.proxy.testing.status"));
        ConnectionTest.run(testProxyConnectionButton, I18nManager.getText("settings.proxy.test"),
                () -> services.bfl().testConnectionWithProxy().get(), (success) -> {
            if (success)
                showProxyStatus("success", I18nManager.getText("settings.test.proxy.success"));
            else
                showProxyStatus("error", I18nManager.getText("settings.test.proxy.failed"));
        }, (exception) -> {
            String errorMessage = I18nManager.getText("settings.test.proxy.error");
            String exceptionMessage = exception != null ? exception.getMessage() : null;
            if (exceptionMessage != null) {
                if (exceptionMessage.contains("timeout")) {
                    errorMessage = I18nManager.getText("settings.test.proxy.error.timeout");
                } else if (exceptionMessage.contains("refused") || exceptionMessage.contains("ConnectException")) {
                    errorMessage = I18nManager.getText("settings.test.proxy.error.refused");
                } else if (exceptionMessage.contains("certificate") || exceptionMessage.contains("SSL")) {
                    errorMessage = I18nManager.getText("settings.test.proxy.error.ssl");
                } else if (exceptionMessage.contains("Unauthorized") || exceptionMessage.contains("407")) {
                    errorMessage = I18nManager.getText("settings.test.proxy.error.auth");
                } else {
                    errorMessage = I18nManager.getText("settings.test.proxy.error.other", exceptionMessage);
                }
            }
            showProxyStatus("error", errorMessage);
            logger.warn("Proxy connection test failed: {}", exception != null ? exception.getMessage() : "Unknown error");
        });
    }

    /** Clears the proxy after a confirmation; the certificates stay. */
    @FXML
    private void onResetProxyConfig() {
        if (!services.prompts().confirm(I18nManager.getText("settings.proxy.reset.title"),
                I18nManager.getText("settings.proxy.reset.header"), I18nManager.getText("settings.proxy.reset.confirm"),
                false))
            return;

        try {
            config.proxy().setProxyHost(null);
            config.proxy().setProxyPort(8080);
            config.proxy().setProxyUsername(null);
            config.proxy().setProxyPassword(null);
            config.proxy().setProxyEnabled(false);
            load();

            proxyConfigSourceContainer.setVisible(false);
            proxyStatus.hide();
            certificateUploadStatus.setText("");
            services.prompts().info(I18nManager.getText("settings.proxy.reset.done.title"),
                    I18nManager.getText("settings.proxy.reset.done"));
        } catch (Exception e) {
            logger.error("Failed to reset proxy configuration", e);
            services.prompts().error(I18nManager.getText("settings.proxy.reset.error.title"),
                    I18nManager.getText("settings.proxy.reset.error", e.getMessage()));
        }
    }

    /** Tests an SSL connection with the certificates of the folder; no API key is needed. */
    @FXML
    private void onTestCertificateConnection() {
        if (certificatesList.isEmpty()) {
            showCertificateStatus("error", I18nManager.getText("settings.certificates.none.found"));
            return;
        }

        showCertificateStatus("testing", I18nManager.getText("settings.test.certificate.testing.status"));
        ConnectionTest.run(testCertificateButton, I18nManager.getText("settings.certificates.test"), () -> {
            var bfl = services.bfl();

            /*  The HTTP clients are built again to use the current certificates  */
            HttpClients.settingsChanged();
            return bfl.testSslConnection().get();
        }, (success) -> {
            if (success)
                showCertificateStatus("success", I18nManager.getText("settings.test.certificate.success"));
            else
                showCertificateStatus("error", I18nManager.getText("settings.test.certificate.failed"));
        }, (exception) -> {
            String errorMessage = I18nManager.getText("settings.test.certificate.error");
            String exceptionMessage = exception != null ? exception.getMessage() : null;
            if (exceptionMessage != null) {
                if (exceptionMessage.contains("certificate") || exceptionMessage.contains("SSL")
                        || exceptionMessage.contains("TLS")) {
                    errorMessage = I18nManager.getText("settings.test.certificate.error.ssl");
                } else if (exceptionMessage.contains("trust") || exceptionMessage.contains("PKIX")) {
                    errorMessage = I18nManager.getText("settings.test.certificate.error.trust");
                } else if (exceptionMessage.contains("expired") || exceptionMessage.contains("validity")) {
                    errorMessage = I18nManager.getText("settings.test.certificate.error.expired");
                } else if (exceptionMessage.contains("format") || exceptionMessage.contains("parsing")) {
                    errorMessage = I18nManager.getText("settings.test.certificate.error.format");
                } else {
                    errorMessage = I18nManager.getText("settings.test.certificate.error.other", exceptionMessage);
                }
            }
            showCertificateStatus("error", errorMessage);
            logger.warn("Certificate connection test failed: {}",
                    exception != null ? exception.getMessage() : "Unknown error");
        });
    }
}
