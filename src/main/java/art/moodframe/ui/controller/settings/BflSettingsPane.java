package art.moodframe.ui.controller.settings;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.util.I18nManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The BFL tab of the settings ({@code BflSettingsPane.fxml}): the API key with its connection test, the activation,
 * the region and the timeout. A typed key is only tested; "Save" stores it once a test passed.
 */
public class BflSettingsPane implements SettingsSection {

    private static final Logger logger = LoggerFactory.getLogger(BflSettingsPane.class);

    @FXML
    private PasswordField apiKeyField;
    @FXML
    private Button showApiKeyButton;
    @FXML
    private FontIcon showApiKeyIcon;
    @FXML
    private Button testConnectionButton;
    @FXML
    private HBox connectionStatusContainer;
    @FXML
    private FontIcon connectionStatusIcon;
    @FXML
    private Label connectionStatusLabel;
    @FXML
    private CheckBox bflProviderEnabledCheckBox;
    @FXML
    private ChoiceBox<String> regionComboBox;
    @FXML
    private Spinner<Integer> timeoutSpinner;
    @FXML
    private Label apiKeyLabel;
    @FXML
    private Label apiKeyDescLabel;
    @FXML
    private Label apiRegionLabel;
    @FXML
    private Label apiRegionDescLabel;
    @FXML
    private Label apiAdvancedLabel;
    @FXML
    private Label apiTimeoutLabel;

    private SettingsServices services;
    private ApplicationConfig config;
    private StatusLine status;
    private TextInputControl apiKeyInputControl;
    private boolean apiKeyVisible = false;
    private boolean apiKeyValidated = false;
    private String validatedApiKey = null;

    /*  the key and arguments of the status message, so a change of the language relabels it; null for a message
        without a key  */
    private String statusKey;
    private Object[] statusArguments;

    @Override
    public void init(SettingsServices services) {
        this.services = services;
        this.config = services.config();
        regionComboBox.getItems().addAll("Global", "EU", "US");
        regionComboBox.setValue("Global");
        timeoutSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(60, 3600, 600));

        status = new StatusLine(connectionStatusContainer, connectionStatusLabel, connectionStatusIcon);
        hideStatus();
        apiKeyInputControl = apiKeyField;

        /*  The checkbox is only enabled if the API key exists and is validated  */
        bflProviderEnabledCheckBox.setSelected(config.accounts().isEnabled(ApiProvider.BFL));
        bflProviderEnabledCheckBox.setDisable(!(config.accounts().hasApiKey(ApiProvider.BFL)
                && config.accounts().isValidated(ApiProvider.BFL)));

        /*  A changed key needs a new connection test  */
        if (config.accounts().isApiKeyEditable(ApiProvider.BFL)) {
            apiKeyInputControl.textProperty().addListener((obs, oldText, newText) -> {
                if (!newText.equals(validatedApiKey)) {
                    apiKeyValidated = false;
                    validatedApiKey = null;
                    hideStatus();
                }
            });
        }
    }

    @Override
    public void load() {
        String apiKeySource = config.accounts().getApiKeySource(ApiProvider.BFL);
        String apiKey = config.accounts().getApiKey(ApiProvider.BFL);

        if (apiKey != null && !apiKey.trim().isEmpty()) {
            /*  A key from any source counts as validated: environment and system keys are trusted, a saved key
                was tested before it was saved  */
            apiKeyValidated = true;
            validatedApiKey = apiKey;
            apiKeyInputControl.setText(apiKey);
        } else {
            /*  No key: the provider cannot be enabled  */
            bflProviderEnabledCheckBox.setDisable(true);
            bflProviderEnabledCheckBox.setSelected(false);
            if (config.accounts().isEnabled(ApiProvider.BFL))
                config.accounts().setEnabled(ApiProvider.BFL, false);
        }

        boolean isEditable = config.accounts().isApiKeyEditable(ApiProvider.BFL);
        apiKeyInputControl.setDisable(!isEditable);
        showApiKeyButton.setDisable(!isEditable);
        testConnectionButton.setDisable(!isEditable);

        if (!"none".equals(apiKeySource) && !"configuration".equals(apiKeySource)) {
            String sourceText = switch (apiKeySource) {
                case "environment" -> "API key loaded from environment variable BFL_API_KEY (read-only)";
                case "system_property" -> "API key loaded from system property bfl.api.key (read-only)";
                default -> "";
            };
            showText("info", sourceText);
        } else if (apiKeyValidated && "configuration".equals(apiKeySource)) {
            showMessage("success", "settings.test.api.validated.ready");
        }

        timeoutSpinner.getValueFactory().setValue(config.accounts().getApiTimeoutSeconds());
        regionComboBox.setValue(config.accounts().getApiRegion());
    }

    @Override
    public void reset() {
        regionComboBox.setValue(config.accounts().getApiRegion());
        timeoutSpinner.getValueFactory().setValue(config.accounts().getApiTimeoutSeconds());
    }

    /** Saves the key once a test passed, removes a cleared one, and saves the timeout and the region. */
    @Override
    public void apply() {
        if (config.accounts().isApiKeyEditable(ApiProvider.BFL)) {
            String currentApiKey = apiKeyInputControl.getText();
            if (currentApiKey != null && !currentApiKey.trim().isEmpty()) {

                /*  An unvalidated key is not saved, but all other settings still are  */
                if (!apiKeyValidated || !currentApiKey.equals(validatedApiKey)) {
                    services.prompts().error("API Key Validation Required",
                            "Please test the API key connection before saving.\n"
                                    + "Click the 'Test' button to validate your API key.");
                    logger.warn("Attempted to save unvalidated API key");
                } else {
                    config.accounts().setApiKey(ApiProvider.BFL, currentApiKey.trim());
                    logger.debug("Validated API key saved to configuration");
                }
            } else if (config.accounts().hasApiKey(ApiProvider.BFL)
                    && "configuration".equals(config.accounts().getApiKeySource(ApiProvider.BFL))) {
                /*  The field was cleared: the key is removed  */
                config.accounts().setApiKey(ApiProvider.BFL, "");
                apiKeyValidated = false;
                validatedApiKey = null;
                logger.debug("API key removed from configuration");
            }
        }

        config.setProperty("api.timeout_seconds", timeoutSpinner.getValue().toString());
        config.accounts().setApiRegion(regionComboBox.getValue());
    }

    @Override
    public void relabel() {
        testConnectionButton.setText(I18nManager.getText("settings.api.test"));

        if (statusKey != null)
            connectionStatusLabel.setText(I18nManager.getText(statusKey, statusArguments));

        apiKeyLabel.setText(I18nManager.getText("settings.api.key"));
        apiKeyDescLabel.setText(I18nManager.getText("settings.api.key.description"));
        apiRegionLabel.setText(I18nManager.getText("settings.api.region"));
        apiRegionDescLabel.setText(I18nManager.getText("settings.api.region.description"));
        apiAdvancedLabel.setText(I18nManager.getText("settings.api.advanced"));
        apiTimeoutLabel.setText(I18nManager.getText("settings.api.timeout"));
        if (apiKeyField != null)
            apiKeyField.setPromptText(I18nManager.getText("settings.api.key.placeholder"));
        showApiKeyButton.setTooltip(new Tooltip(I18nManager.getText("settings.api.key.toggle.tooltip")));
    }

    /** Shows the key as text or hidden, switching between a text field and a password field. */
    @FXML
    private void onShowApiKeyToggle() {
        try {
            apiKeyInputControl = ConnectionTest.swapKeyField(apiKeyInputControl, !apiKeyVisible,
                    "Enter your BFL API key...");
            if (apiKeyInputControl instanceof PasswordField passwordField)
                apiKeyField = passwordField;
            showApiKeyIcon.setIconLiteral(apiKeyVisible ? "mdi2e-eye" : "mdi2e-eye-off");
            apiKeyVisible = !apiKeyVisible;
            apiKeyInputControl.requestFocus();
        } catch (Exception e) {
            logger.error("Failed to toggle API key visibility", e);
            services.prompts().error("Toggle Error", "Failed to toggle API key visibility");
        }
    }

    /** Tests the typed key against the selected region; key and region are saved with "Save". */
    @FXML
    private void onTestConnection() {
        String apiKey = apiKeyInputControl.getText();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            showMessage("error", "settings.test.api.enter.key");
            return;
        }

        String selectedRegion = regionComboBox.getValue();
        showMessage("testing", "settings.test.api.testing.status", selectedRegion);
        ConnectionTest.run(testConnectionButton, I18nManager.getText("settings.api.test"),
                () -> services.bfl().testConnection(apiKey, selectedRegion).get(), (success) -> {
            String testedRegion = regionComboBox.getValue();
            apiKeyValidated = success;
            validatedApiKey = success ? apiKey : null;
            if (success) {
                showMessage("success", "settings.test.api.connection.success", testedRegion);

                /*  A successful test unlocks the provider, like for Gemini and OpenAI  */
                bflProviderEnabledCheckBox.setDisable(false);
                if (!config.accounts().isEnabled(ApiProvider.BFL)) {
                    bflProviderEnabledCheckBox.setSelected(true);
                    config.accounts().setEnabled(ApiProvider.BFL, true);
                }
                services.connectionTested(ApiProvider.BFL, true);
            } else {
                showMessage("error", "settings.test.api.connection.failed", testedRegion);
                logger.warn("API key validation failed for region: {}", testedRegion);
            }
        }, (exception) -> {
            showText("error", testError(exception));
            apiKeyValidated = false;
            validatedApiKey = null;
            logger.warn("API connection test failed: {}", exception != null ? exception.getMessage() : "Unknown error");
        });
    }

    /** Shows a status message from the i18n resources, which a change of the language relabels. */
    private void showMessage(String kind, String key, Object... arguments) {
        statusKey = key;
        statusArguments = arguments;
        status.show(kind, I18nManager.getText(key, arguments));
    }

    /** Shows a status message without a key, which stays as it is. */
    private void showText(String kind, String message) {
        statusKey = null;
        status.show(kind, message);
    }

    private void hideStatus() {
        statusKey = null;
        status.hide();
    }

    /** The message for a BFL connection test that ended with an exception. */
    private static String testError(Throwable exception) {
        String message = exception != null ? exception.getMessage() : null;
        if (message == null)
            return "Connection test failed";
        if (message.contains("Unauthorized") || message.contains("401"))
            return "Invalid API key - Authentication failed";
        if (message.contains("Forbidden") || message.contains("403"))
            return "API key lacks necessary permissions";
        if (message.contains("timeout") || message.contains("SocketTimeout"))
            return "Connection timeout - Check your internet connection";
        if (message.contains("UnknownHost") || message.contains("ConnectException"))
            return "Cannot connect to BFL API - Check internet connection";
        if (message.contains("API key not configured"))
            return "Please enter a valid API key";
        return "Connection test failed: " + message;
    }

    /** Switches the provider on or off at once. */
    @FXML
    private void onBflProviderToggle() {
        boolean enabled = bflProviderEnabledCheckBox.isSelected();
        config.accounts().setEnabled(ApiProvider.BFL, enabled);
        logger.info("BFL provider " + (enabled ? "enabled" : "disabled"));
    }
}
