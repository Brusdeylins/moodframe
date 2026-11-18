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
            services.prompts().error("Initialization Error",
                    "Failed to initialize Corporate Proxy settings: " + e.getMessage());
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
            services.prompts().error("Proxy Settings Load Error", "Failed to load proxy settings: " + e.getMessage());
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

        /*  The status lines are relabeled only while they show a known text  */
        String proxyText = proxyConnectionStatusLabel.getText();
        if (proxyText.contains("No proxy connection"))
            proxyConnectionStatusLabel.setText(I18nManager.getText("settings.proxy.connection.none"));
        else if (proxyText.contains("Proxy connection successful") || proxyText.contains("Proxy-Verbindung erfolgreich"))
            proxyConnectionStatusLabel.setText(I18nManager.getText("settings.test.proxy.success"));
        String certificateText = certificateConnectionStatusLabel.getText();
        if (certificateText.contains("No certificate connection"))
            certificateConnectionStatusLabel.setText(I18nManager.getText("settings.certificates.connection.none"));
        else if (certificateText.contains("Certificate connection successful")
                || certificateText.contains("Zertifikat-Verbindung erfolgreich"))
            certificateConnectionStatusLabel.setText(I18nManager.getText("settings.test.certificate.success"));
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
                case "environment" -> "Proxy configuration loaded from environment variables (read-only)";
                case "system_property" -> "Proxy configuration loaded from system properties (read-only)";
                default -> "Proxy configuration source: " + source;
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
                certificateUploadStatus.setText("Certificate directory will be created when uploading");
                removeCertificateButton.setDisable(true);
            }
        } catch (Exception e) {
            logger.error("Failed to load installed certificates", e);
            certificateUploadStatus.setText("Error loading certificates");
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
            services.prompts().error("Directory Error", "Failed to create certificate directory: " + e.getMessage());
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Certificate Files");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Certificate Files", "*.crt", "*.cer", "*.pem"),
                new FileChooser.ExtensionFilter("CRT Files", "*.crt"),
                new FileChooser.ExtensionFilter("CER Files", "*.cer"),
                new FileChooser.ExtensionFilter("PEM Files", "*.pem"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        List<File> selectedFiles = services.prompts().openFiles(fileChooser,
                uploadCertificateButton.getScene().getWindow());
        if (selectedFiles == null || selectedFiles.isEmpty())
            return;

        int successCount = 0;
        int failedCount = 0;
        StringBuilder failedFiles = new StringBuilder();
        for (File sourceFile : selectedFiles) {
            try {
                if (certificates.contains(sourceFile.getName()) && !services.prompts().confirm("File Exists",
                        "Overwrite existing certificate?",
                        "The file '" + sourceFile.getName() + "' already exists. Replace it?", false)) {
                    failedCount++;
                    failedFiles.append("\n- ").append(sourceFile.getName()).append(" (skipped)");
                    continue;
                }
                certificates.importFile(sourceFile.toPath());
                successCount++;
            } catch (IOException e) {
                logger.error("Failed to upload certificate: {}", sourceFile.getName(), e);
                failedCount++;
                failedFiles.append("\n- ").append(sourceFile.getName()).append(" (error: ").append(e.getMessage())
                        .append(")");
            }
        }

        loadInstalledCertificates();
        if (successCount > 0 && failedCount == 0) {
            certificateUploadStatus.setText(successCount + " certificate(s) uploaded successfully");
        } else if (successCount > 0) {
            services.prompts().error("Partial Upload Success",
                    successCount + " certificate(s) uploaded, " + failedCount + " failed:" + failedFiles);
            certificateUploadStatus.setText(successCount + " uploaded, " + failedCount + " failed");
        } else {
            services.prompts().error("Upload Failed", "All certificate uploads failed:" + failedFiles);
            certificateUploadStatus.setText("Upload failed");
        }
    }

    /** Removes the selected certificate after a confirmation. */
    @FXML
    private void onRemoveCertificate() {
        String selectedCertificate = installedCertificatesListView.getSelectionModel().getSelectedItem();
        if (selectedCertificate == null)
            return;
        if (!services.prompts().confirm("Remove Certificate", "Remove Certificate",
                "Are you sure you want to remove the certificate '" + selectedCertificate + "'?", false))
            return;

        try {
            if (certificates().delete(selectedCertificate)) {
                loadInstalledCertificates();
                certificateUploadStatus.setText("Certificate removed: " + selectedCertificate);
            } else {
                services.prompts().error("File Not Found", "Certificate file not found: " + selectedCertificate);
            }
        } catch (IOException e) {
            logger.error("Failed to remove certificate: {}", selectedCertificate, e);
            services.prompts().error("Remove Failed", "Failed to remove certificate: " + e.getMessage());
        }
    }

    @FXML
    private void onRefreshCertificates() {
        loadInstalledCertificates();
    }

    /** Tests the saved proxy with the BFL client. */
    @FXML
    private void onTestProxyConnection() {
        proxyStatus.show("testing", I18nManager.getText("settings.test.proxy.testing.status"));
        ConnectionTest.run(testProxyConnectionButton, I18nManager.getText("settings.proxy.test"),
                () -> services.bfl().testConnectionWithProxy().get(), (success) -> {
            if (success)
                proxyStatus.show("success", I18nManager.getText("settings.test.proxy.success"));
            else
                proxyStatus.show("error", I18nManager.getText("settings.test.proxy.failed"));
        }, (exception) -> {
            String errorMessage = "Proxy connection test failed";
            String exceptionMessage = exception != null ? exception.getMessage() : null;
            if (exceptionMessage != null) {
                if (exceptionMessage.contains("timeout")) {
                    errorMessage = "Connection timeout - Check proxy host and port";
                } else if (exceptionMessage.contains("refused") || exceptionMessage.contains("ConnectException")) {
                    errorMessage = "Connection refused - Check proxy host and port";
                } else if (exceptionMessage.contains("certificate") || exceptionMessage.contains("SSL")) {
                    errorMessage = "SSL/Certificate error - Check ZScaler certificates";
                } else if (exceptionMessage.contains("Unauthorized") || exceptionMessage.contains("407")) {
                    errorMessage = "Proxy authentication failed - Check username and password";
                } else {
                    errorMessage = "Proxy test failed: " + exceptionMessage;
                }
            }
            proxyStatus.show("error", errorMessage);
            logger.warn("Proxy connection test failed: {}", exception != null ? exception.getMessage() : "Unknown error");
        });
    }

    /** Clears the proxy after a confirmation; the certificates stay. */
    @FXML
    private void onResetProxyConfig() {
        if (!services.prompts().confirm("Reset Proxy Configuration", "Reset Proxy Settings",
                "This will clear all proxy settings and certificates. Continue?", false))
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
            services.prompts().info("Configuration Reset", "Proxy configuration has been reset to defaults.");
        } catch (Exception e) {
            logger.error("Failed to reset proxy configuration", e);
            services.prompts().error("Reset Failed", "Failed to reset proxy configuration: " + e.getMessage());
        }
    }

    /** Tests an SSL connection with the certificates of the folder; no API key is needed. */
    @FXML
    private void onTestCertificateConnection() {
        if (certificatesList.isEmpty()) {
            certificateStatus.show("error", I18nManager.getText("settings.certificates.none.found"));
            return;
        }

        certificateStatus.show("testing", I18nManager.getText("settings.test.certificate.testing.status"));
        ConnectionTest.run(testCertificateButton, I18nManager.getText("settings.certificates.test"), () -> {
            var bfl = services.bfl();

            /*  The HTTP clients are built again to use the current certificates  */
            HttpClients.settingsChanged();
            return bfl.testSslConnection().get();
        }, (success) -> {
            if (success)
                certificateStatus.show("success", I18nManager.getText("settings.test.certificate.success"));
            else
                certificateStatus.show("error", "Certificate connection failed - Check certificate validity and format");
        }, (exception) -> {
            String errorMessage = "Certificate connection test failed";
            String exceptionMessage = exception != null ? exception.getMessage() : null;
            if (exceptionMessage != null) {
                if (exceptionMessage.contains("certificate") || exceptionMessage.contains("SSL")
                        || exceptionMessage.contains("TLS")) {
                    errorMessage = "SSL/Certificate error - Check certificate format and validity";
                } else if (exceptionMessage.contains("trust") || exceptionMessage.contains("PKIX")) {
                    errorMessage = "Certificate trust error - Certificate may not be properly installed";
                } else if (exceptionMessage.contains("expired") || exceptionMessage.contains("validity")) {
                    errorMessage = "Certificate expired or not yet valid";
                } else if (exceptionMessage.contains("format") || exceptionMessage.contains("parsing")) {
                    errorMessage = "Certificate format error - Check file format (.crt, .cer, .pem)";
                } else {
                    errorMessage = "Certificate test failed: " + exceptionMessage;
                }
            }
            certificateStatus.show("error", errorMessage);
            logger.warn("Certificate connection test failed: {}",
                    exception != null ? exception.getMessage() : "Unknown error");
        });
    }
}
