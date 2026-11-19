package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.service.provider.ApiProviderValidator;
import art.moodframe.ui.component.MemoryStatusComponent;
import art.moodframe.ui.support.StatusSink;
import art.moodframe.util.I18nManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The status bar of the main window ({@code StatusBar.fxml}): the status message, the memory display and the status of
 * the API keys with the last checks of the accounts in its tooltip. An error stays shown for 4 seconds against the
 * next status messages.
 *
 * @since 2.1.3
 */
public class StatusBarController implements StatusSink {

    private static final Logger logger = LoggerFactory.getLogger(StatusBarController.class);

    /*  how long an error stays against the next status messages  */
    private static final long ERROR_SHOWN_MILLIS = 4000;

    @FXML
    private HBox statusBar;
    @FXML
    private Label statusLabel;
    @FXML
    private HBox apiKeyStatusGroup;
    @FXML
    private FontIcon apiKeyStatusIcon;
    @FXML
    private Label apiKeyStatusLabel;
    private Tooltip apiKeyStatusTooltip;

    /*  the memory display, whose timeline runs until the window closes  */
    private MemoryStatusComponent memoryStatus;

    /** The status of the API keys the bar shows. */
    enum KeyStatus {
        /** every enabled provider has a key */
        VALID,
        /** a key is missing */
        NONE
    }

    private KeyStatus keyStatus = KeyStatus.NONE;

    /*  whether the status message is "Ready", which a change of the language translates  */
    private boolean ready;

    /*  until when an error is protected against the next status messages  */
    private long errorShownUntil;

    private ApplicationConfig config;
    private ApiProviderValidator validator;

    @FXML
    private void initialize() {
        /*  symbol and text of the API key status share one tooltip on their group, like the other displays of the
            status bar, so it stays while the pointer moves between them  */
        apiKeyStatusTooltip = new Tooltip();
        apiKeyStatusTooltip.setShowDelay(javafx.util.Duration.millis(500));
        apiKeyStatusTooltip.setShowDuration(javafx.util.Duration.INDEFINITE);
        Tooltip.install(apiKeyStatusGroup, apiKeyStatusTooltip);

        /*  the memory display in front of the API key status  */
        memoryStatus = new MemoryStatusComponent();
        statusBar.getChildren().add(statusBar.getChildren().indexOf(apiKeyStatusGroup), memoryStatus);
    }

    /** Stops the memory display, once the window closes. */
    public void dispose() {
        memoryStatus.shutdown();
    }

    /**
     * Gives the bar what it shows the status of the accounts from.
     *
     * @param config    the configuration with the keys and the flags of the last checks
     * @param validator the checks of the program start, for the reasons of a failure (may be null)
     */
    public void init(ApplicationConfig config, ApiProviderValidator validator) {
        this.config = config;
        this.validator = validator;
    }

    @Override
    public void status(String message) {
        show(message, false);
    }

    @Override
    public void error(String message) {
        show(message, true);
    }

    /** Shows "Ready", which follows a change of the language. */
    public void ready() {
        ready = show(I18nManager.getText("status.ready"), false);
    }

    /*  shows a message unless it is no error and an error is protected; tells whether it is shown  */
    private boolean show(String message, boolean error) {
        if (!error && System.currentTimeMillis() < errorShownUntil)
            return false;
        if (error)
            errorShownUntil = System.currentTimeMillis() + ERROR_SHOWN_MILLIS;
        statusLabel.setText(message);
        ready = false;
        return true;
    }

    /**
     * Checks the API keys and shows the result: "valid" (green) only if every enabled provider (BFL, Gemini) has a key,
     * or with no provider enabled, at least one has a key.
     */
    public void checkApiKeys() {
        boolean hasValidKey = false;
        try {
            if (config != null) {
                boolean bflEnabled = config.accounts().isEnabled(ApiProvider.BFL);
                boolean geminiEnabled = config.accounts().isEnabled(ApiProvider.GOOGLE_GEMINI);
                boolean bflHasKey = config.accounts().hasApiKey(ApiProvider.BFL);
                boolean geminiHasKey = config.accounts().hasApiKey(ApiProvider.GOOGLE_GEMINI);
                hasValidKey = !bflEnabled && !geminiEnabled ? bflHasKey || geminiHasKey
                        : (!bflEnabled || bflHasKey) && (!geminiEnabled || geminiHasKey);
            }
            else
                logger.warn("ApplicationConfig not available, assuming no API key");
        } catch (Exception e) {
            logger.error("Error checking API key status", e);
        }
        keyStatus = hasValidKey ? KeyStatus.VALID : KeyStatus.NONE;
        showKeyStatus();
    }

    /** Whether the bar shows the API keys as valid. */
    public boolean hasValidKeys() {
        return keyStatus == KeyStatus.VALID;
    }

    /*  shows the status of the API keys with theme-aware colors  */
    private void showKeyStatus() {
        boolean valid = keyStatus == KeyStatus.VALID;
        apiKeyStatusIcon.getStyleClass().setAll("ikonli-font-icon", valid ? "success" : "danger");
        apiKeyStatusIcon.setIconLiteral("mdi2k-key");
        apiKeyStatusLabel.getStyleClass().clear();
        apiKeyStatusLabel.setText(I18nManager.getText(valid ? "status.api.key.valid" : "status.api.key.none"));
        apiKeyStatusLabel.setStyle(valid ? "-fx-text-fill: -color-success-emphasis;"
                : "-fx-text-fill: -color-danger-emphasis;");
        apiKeyStatusTooltip.setText(apiKeyHint(valid ? "status.api.key.valid.tooltip" : "status.api.key.none.tooltip"));
    }

    /** Shows the texts of the bar in the current language. */
    public void updateTexts() {
        showKeyStatus();
        if (ready)
            ready();
    }

    /**
     * Builds the hint of the API key display: the given text, followed by the result of the last check of every
     * configured account.
     *
     * @param key the i18n key of the text
     * @return the hint
     */
    private String apiKeyHint(String key) {
        String checks = config != null ? accountChecks(config, validator) : "";
        return I18nManager.getText(key) + (checks.isEmpty() ? "" : "\n\n" + checks);
    }

    /**
     * Describes the last check of every configured account in one line each: connection, language model and web
     * search with ✓ or ✗ and the reason of a failure (BFL: connection only), e.g.
     * "OpenAI: connection ✓, gpt-x ✓, web search ✗ (HTTP 404)"; a check that could not decide keeps the last
     * result and says so; a switched-off account is marked. The flags stem from the configuration, the reasons of
     * the running program from the validator.
     *
     * @param config    the application configuration
     * @param validator the validator of the program start, may be null
     * @return the lines, empty if no account is configured
     */
    static String accountChecks(ApplicationConfig config, ApiProviderValidator validator) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        art.moodframe.config.ProviderAccounts accounts = config.accounts();
        ApiProvider gemini = ApiProvider.GOOGLE_GEMINI;
        ApiProvider openAi = ApiProvider.OPENAI;
        if (accounts.hasApiKey(ApiProvider.BFL))
            lines.add(accountCheck(account("BFL", accounts.isEnabled(ApiProvider.BFL)), ApiProvider.BFL,
                    accounts.isValidated(ApiProvider.BFL), null, false, false, validator));
        if (accounts.hasApiKey(gemini))
            lines.add(accountCheck(account("Google", accounts.isEnabled(gemini)), gemini, accounts.isValidated(gemini),
                    I18nManager.getText("status.api.check.model"), accounts.isChatOk(gemini),
                    accounts.isWebSearch(gemini), validator));
        if (accounts.hasApiKey(openAi))
            lines.add(accountCheck(account("OpenAI", accounts.isEnabled(openAi)), openAi, accounts.isValidated(openAi),
                    accounts.getOpenAiChatModel(), accounts.isChatOk(openAi), accounts.isWebSearch(openAi), validator));
        return String.join("\n", lines);
    }

    /*  the name of an account, marked if the user switched it off: then it is not offered whatever its checks say  */
    private static String account(String name, boolean enabled) {
        return enabled ? name : name + " (" + I18nManager.getText("status.api.check.off") + ")";
    }

    /*  one line of the checks of an account; without a model (BFL) only the connection, with a blank one "no model"  */
    private static String accountCheck(String account, ApiProvider provider, boolean connection, String model,
            boolean chatOk, boolean webSearch, ApiProviderValidator validator) {
        StringBuilder line = new StringBuilder(account).append(": ").append(checkResult(
                I18nManager.getText("status.api.check.connection"), connection, provider,
                ApiProviderValidator.Check.CONNECTION, validator));
        if (connection && model != null && model.isBlank())
            line.append(", ").append(I18nManager.getText("status.api.check.nomodel"));
        else if (connection && model != null) {
            line.append(", ").append(checkResult(model, chatOk, provider, ApiProviderValidator.Check.LANGUAGE_MODEL,
                    validator));
            line.append(", ").append(checkResult(I18nManager.getText("status.api.check.search"), webSearch, provider,
                    ApiProviderValidator.Check.WEB_SEARCH, validator));
        }
        return line.toString();
    }

    /*  "name ✓" or "name ✗", with the reason of the last check if known (also for a kept ✓), cut short  */
    private static String checkResult(String name, boolean ok, ApiProvider provider,
            ApiProviderValidator.Check check, ApiProviderValidator validator) {
        String result = name + (ok ? " ✓" : " ✗");
        String reason = validator != null ? validator.getReason(provider, check) : null;
        if (reason == null || reason.isBlank())
            return result;
        reason = reason.strip().replaceAll("\\s+", " ");
        return result + " (" + (reason.length() > 80 ? reason.substring(0, 79) + "…" : reason) + ")";
    }

}
