package art.moodframe.ui.controller.settings;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.service.provider.ApiProviderValidator;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Gemini tab of the settings ({@code GeminiSettingsPane.fxml}): the API key with its connection test and the
 * activation. After a passed test the brainstorm language model is checked as well. A typed key is only tested;
 * "Save" stores it once a test passed.
 */
public class GeminiSettingsPane implements SettingsSection {

    private static final Logger logger = LoggerFactory.getLogger(GeminiSettingsPane.class);

    @FXML
    private PasswordField geminiApiKeyField;
    @FXML
    private Button showGeminiApiKeyButton;
    @FXML
    private FontIcon showGeminiApiKeyIcon;
    @FXML
    private Button testGeminiConnectionButton;
    @FXML
    private HBox geminiConnectionStatusContainer;
    @FXML
    private FontIcon geminiConnectionStatusIcon;
    @FXML
    private Label geminiConnectionStatusLabel;
    @FXML
    private CheckBox geminiProviderEnabledCheckBox;

    private SettingsServices services;
    private ApplicationConfig config;
    private StatusLine status;
    private TextInputControl geminiApiKeyInputControl;
    private boolean geminiApiKeyVisible = false;
    private boolean geminiApiKeyValidated = false;
    private String validatedGeminiApiKey = null;

    @Override
    public void init(SettingsServices services) {
        this.services = services;
        this.config = services.config();
        status = new StatusLine(geminiConnectionStatusContainer, geminiConnectionStatusLabel,
                geminiConnectionStatusIcon);
        status.hide();
        geminiApiKeyInputControl = geminiApiKeyField;

        /*  The checkbox is only enabled if the API key exists and is validated  */
        geminiProviderEnabledCheckBox.setSelected(config.accounts().isEnabled(ApiProvider.GOOGLE_GEMINI));
        geminiProviderEnabledCheckBox.setDisable(!(config.accounts().hasApiKey(ApiProvider.GOOGLE_GEMINI)
                && config.accounts().isValidated(ApiProvider.GOOGLE_GEMINI)));

        if (config.accounts().isApiKeyEditable(ApiProvider.GOOGLE_GEMINI))
            geminiApiKeyInputControl.textProperty().addListener((obs, oldText, newText) -> keyEdited(newText));
    }

    /*  A changed key needs a new connection test, the provider is off until then  */
    private void keyEdited(String newText) {
        if (!newText.equals(validatedGeminiApiKey)) {
            geminiApiKeyValidated = false;
            validatedGeminiApiKey = null;
            status.hide();
            geminiProviderEnabledCheckBox.setDisable(true);
            geminiProviderEnabledCheckBox.setSelected(false);
        }
    }

    @Override
    public void load() {
        String geminiApiKeySource = config.accounts().getApiKeySource(ApiProvider.GOOGLE_GEMINI);
        String geminiApiKey = config.accounts().getApiKey(ApiProvider.GOOGLE_GEMINI);

        if (geminiApiKey != null && !geminiApiKey.trim().isEmpty()) {
            geminiApiKeyValidated = true;
            validatedGeminiApiKey = geminiApiKey;
            geminiApiKeyInputControl.setText(geminiApiKey);
        } else {
            /*  No key: the provider cannot be enabled  */
            geminiProviderEnabledCheckBox.setDisable(true);
            geminiProviderEnabledCheckBox.setSelected(false);
            if (config.accounts().isEnabled(ApiProvider.GOOGLE_GEMINI))
                config.accounts().setEnabled(ApiProvider.GOOGLE_GEMINI, false);
        }

        boolean geminiIsEditable = config.accounts().isApiKeyEditable(ApiProvider.GOOGLE_GEMINI);
        geminiApiKeyInputControl.setDisable(!geminiIsEditable);
        showGeminiApiKeyButton.setDisable(!geminiIsEditable);
        testGeminiConnectionButton.setDisable(!geminiIsEditable);

        if (!"none".equals(geminiApiKeySource) && !"configuration".equals(geminiApiKeySource)) {
            String sourceText = switch (geminiApiKeySource) {
                case "environment" -> "Gemini API key loaded from environment variable GEMINI_API_KEY (read-only)";
                case "system_property" -> "Gemini API key loaded from system property gemini.api.key (read-only)";
                default -> "";
            };
            status.show("info", sourceText);
        } else if (geminiApiKeyValidated && "configuration".equals(geminiApiKeySource)) {
            status.show("success", I18nManager.getText("settings.gemini.validated.ready"));
        }
    }

    /** Saves the key once a test passed and removes a cleared one. */
    @Override
    public void apply() {
        if (!config.accounts().isApiKeyEditable(ApiProvider.GOOGLE_GEMINI))
            return;
        String currentGeminiApiKey = geminiApiKeyInputControl.getText();
        if (currentGeminiApiKey != null && !currentGeminiApiKey.trim().isEmpty()) {
            /*  Only saved if validated, all other settings still are  */
            if (!geminiApiKeyValidated || !currentGeminiApiKey.equals(validatedGeminiApiKey)) {
                logger.warn("Attempted to save unvalidated Gemini API key");
            } else {
                config.accounts().setApiKey(ApiProvider.GOOGLE_GEMINI, currentGeminiApiKey.trim());
                logger.debug("Validated Gemini API key saved to configuration");
            }
        } else if (config.accounts().hasApiKey(ApiProvider.GOOGLE_GEMINI)
                && "configuration".equals(config.accounts().getApiKeySource(ApiProvider.GOOGLE_GEMINI))) {
            /*  The field was cleared: the key is removed  */
            config.accounts().setApiKey(ApiProvider.GOOGLE_GEMINI, "");
            geminiApiKeyValidated = false;
            validatedGeminiApiKey = null;
            logger.debug("Gemini API key removed from configuration");
        }
    }

    /** Shows the key as text or hidden, switching between a text field and a password field. */
    @FXML
    private void onShowGeminiApiKeyToggle() {
        try {
            geminiApiKeyInputControl = ConnectionTest.swapKeyField(geminiApiKeyInputControl, !geminiApiKeyVisible,
                    I18nManager.getText("settings.gemini.key.placeholder"));
            if (geminiApiKeyInputControl instanceof PasswordField passwordField)
                geminiApiKeyField = passwordField;
            showGeminiApiKeyIcon.setIconLiteral(geminiApiKeyVisible ? "mdi2e-eye" : "mdi2e-eye-off");
            geminiApiKeyVisible = !geminiApiKeyVisible;

            /*  The new field resets the validation as well  */
            geminiApiKeyInputControl.textProperty().addListener((obs, oldText, newText) -> keyEdited(newText));
        } catch (Exception e) {
            logger.error("Failed to toggle Gemini API key visibility", e);
        }
    }

    /** Tests the typed key; it is saved with "Save". */
    @FXML
    private void onTestGeminiConnection() {
        String apiKey = geminiApiKeyInputControl.getText();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            status.show("error", I18nManager.getText("settings.gemini.enter.key"));
            return;
        }

        status.show("testing", I18nManager.getText("settings.gemini.testing.status"));
        ConnectionTest.run(testGeminiConnectionButton, I18nManager.getText("settings.gemini.test"),
                () -> services.gemini().testConnection(apiKey, null).get(), (success) -> {
            if (success) {
                geminiApiKeyValidated = true;
                validatedGeminiApiKey = apiKey;
                status.show("success", I18nManager.getText("settings.gemini.test.success"));

                /*  A failed test disables the provider, so re-enable it after a successful one  */
                geminiProviderEnabledCheckBox.setDisable(false);
                if (!config.accounts().isEnabled(ApiProvider.GOOGLE_GEMINI)) {
                    geminiProviderEnabledCheckBox.setSelected(true);
                    config.accounts().setEnabled(ApiProvider.GOOGLE_GEMINI, true);
                }
                services.connectionTested(ApiProvider.GOOGLE_GEMINI, true);
                checkBrainstorm();
            } else {
                testFailed("settings.gemini.test.failed");
            }
        }, (exception) -> {
            /*  Not decided (no network, timeout, HTTP 429 or 5xx): the typed token is not taken, the rest stays  */
            logger.warn("Gemini API connection test could not decide", exception);
            geminiApiKeyValidated = false;
            validatedGeminiApiKey = null;
            status.show("error", I18nManager.getText("settings.test.undecided",
                    ApiProviderValidator.reasonOf(exception)));
        });
    }

    /**
     * Checks the brainstorm language model of Gemini and its Google Search after a successful connection test; the
     * validator stores the results, so the brainstorm is offered without a restart. The status shows the outcome.
     */
    private void checkBrainstorm() {
        ApiProviderValidator validator = services.validator();
        if (validator == null)
            return;

        /*  The key the dialog validated, which may not be saved yet  */
        status.show("testing", I18nManager.getText("settings.gemini.check.testing"));
        validator.checkGeminiModel(validatedGeminiApiKey).whenComplete(
                (ApiProviderValidator.ModelCheck check, Throwable error) -> Platform.runLater(() -> {
            /*  A decided failure goes before an undecided one, as it is what the validator just stored  */
            ApiProviderValidator.Outcome failed = check == null ? null
                    : check.chat().decided() && !check.chat().works() ? check.chat()
                    : check.search().decided() && !check.search().works() ? check.search()
                    : !check.chat().works() ? check.chat() : !check.search().works() ? check.search() : null;
            if (check != null && failed == null) {
                status.show("success", I18nManager.getText("settings.gemini.check.ok"));
                return;
            }

            /*  An undecided check keeps the last result, which may still offer Gemini for the brainstorm  */
            if (failed == null || !failed.decided())
                status.show("info", I18nManager.getText("settings.gemini.check.kept",
                        failed == null ? ApiProviderValidator.reasonOf(error) : failed.problem()));
            else
                status.show("error", I18nManager.getText("settings.gemini.check.failed", failed.problem()));
        }));
    }

    /** Shows a failed test, puts the saved token back and switches the provider off. */
    private void testFailed(String messageKey) {
        geminiApiKeyValidated = false;
        validatedGeminiApiKey = null;
        status.show("error", I18nManager.getText(messageKey));
        geminiProviderEnabledCheckBox.setDisable(true);
        geminiProviderEnabledCheckBox.setSelected(false);
        services.connectionTested(ApiProvider.GOOGLE_GEMINI, false);
        config.accounts().setEnabled(ApiProvider.GOOGLE_GEMINI, false);
    }

    /** Switches the provider on or off at once; switched on, its language model is checked. */
    @FXML
    private void onGeminiProviderToggle() {
        boolean enabled = geminiProviderEnabledCheckBox.isSelected();
        config.accounts().setEnabled(ApiProvider.GOOGLE_GEMINI, enabled);
        logger.info("Gemini provider " + (enabled ? "enabled" : "disabled"));

        /*  A disabled provider was not checked at the start: switched on, its language model is checked now  */
        if (enabled && config.accounts().isValidated(ApiProvider.GOOGLE_GEMINI))
            checkBrainstorm();
    }
}
