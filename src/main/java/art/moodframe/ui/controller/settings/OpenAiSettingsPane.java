package art.moodframe.ui.controller.settings;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.service.provider.ApiProviderValidator;
import art.moodframe.service.provider.OpenAiApiService;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The OpenAI tab of the settings ({@code OpenAiSettingsPane.fxml}): base URL and bearer token with their connection
 * test, the image models and the language model, and the activation. Token and URL are only tested; the test
 * stores the models the API offers, "Save" stores token, URL and the chosen models.
 */
public class OpenAiSettingsPane implements SettingsSection {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiSettingsPane.class);

    @FXML
    private Label openaiTitleLabel;
    @FXML
    private Label openaiDescriptionLabel;
    @FXML
    private Label openaiModelsLabel;
    @FXML
    private Label openaiChatLabel;
    @FXML
    private TextField openaiBaseUrlField;
    @FXML
    private PasswordField openaiApiKeyField;
    @FXML
    private Button showOpenaiApiKeyButton;
    @FXML
    private FontIcon showOpenaiApiKeyIcon;
    @FXML
    private Button testOpenaiConnectionButton;
    @FXML
    private HBox openaiConnectionStatusContainer;
    @FXML
    private FontIcon openaiConnectionStatusIcon;
    @FXML
    private Label openaiConnectionStatusLabel;
    @FXML
    private CheckBox openaiProviderEnabledCheckBox;
    @FXML
    private VBox openaiModelsContainer;
    @FXML
    private ListView<String> openaiModelList;
    @FXML
    private ComboBox<String> openaiChatModelBox;

    private SettingsServices services;
    private ApplicationConfig config;
    private StatusLine status;
    private TextInputControl openaiApiKeyInputControl;
    private boolean openaiApiKeyVisible = false;
    private boolean openaiApiKeyValidated = false;
    private String validatedOpenaiApiKey = null;
    private String validatedOpenaiBaseUrl = null;
    private boolean openaiChatModelUpdating = false;
    private final Map<String, BooleanProperty> openaiModelChecks = new LinkedHashMap<>();

    @Override
    public void init(SettingsServices services) {
        this.services = services;
        this.config = services.config();
        status = new StatusLine(openaiConnectionStatusContainer, openaiConnectionStatusLabel,
                openaiConnectionStatusIcon);
        status.hide();
        openaiApiKeyInputControl = openaiApiKeyField;
        openaiChatModelBox.valueProperty().addListener((obs, old, chosen) -> onOpenaiChatModelChosen(chosen));
        openaiModelList.setCellFactory(CheckBoxListCell.forListView(openaiModelChecks::get));

        /*  The checkbox is only enabled if the token exists and is validated  */
        openaiProviderEnabledCheckBox.setSelected(config.accounts().isEnabled(ApiProvider.OPENAI));
        openaiProviderEnabledCheckBox.setDisable(!(config.accounts().hasApiKey(ApiProvider.OPENAI)
                && config.accounts().isValidated(ApiProvider.OPENAI)));

        /*  A changed token or base URL needs a new connection test  */
        ChangeListener<String> openaiResetListener = (obs, oldText, newText) -> resetOpenaiValidation();
        if (config.accounts().isApiKeyEditable(ApiProvider.OPENAI))
            openaiApiKeyInputControl.textProperty().addListener(openaiResetListener);
        openaiBaseUrlField.textProperty().addListener(openaiResetListener);
    }

    @Override
    public void load() {
        String openaiApiKeySource = config.accounts().getApiKeySource(ApiProvider.OPENAI);
        String openaiApiKey = config.accounts().getApiKey(ApiProvider.OPENAI);
        openaiBaseUrlField.setText(config.accounts().getOpenAiBaseUrl());

        if (openaiApiKey != null && !openaiApiKey.trim().isEmpty()) {
            openaiApiKeyInputControl.setText(openaiApiKey);
            openaiApiKeyValidated = true;
            validatedOpenaiApiKey = openaiApiKey;
            validatedOpenaiBaseUrl = config.accounts().getOpenAiBaseUrl();
        } else {
            /*  No token: the provider cannot be enabled  */
            openaiProviderEnabledCheckBox.setDisable(true);
            openaiProviderEnabledCheckBox.setSelected(false);
            if (config.accounts().isEnabled(ApiProvider.OPENAI))
                config.accounts().setEnabled(ApiProvider.OPENAI, false);
        }

        boolean openaiIsEditable = config.accounts().isApiKeyEditable(ApiProvider.OPENAI);
        openaiApiKeyInputControl.setDisable(!openaiIsEditable);
        showOpenaiApiKeyButton.setDisable(!openaiIsEditable);

        if (!"none".equals(openaiApiKeySource) && !"configuration".equals(openaiApiKeySource)) {
            String sourceText = switch (openaiApiKeySource) {
                case "environment" -> "OpenAI API key loaded from environment variable OPENAI_API_KEY (read-only)";
                case "system_property" -> "OpenAI API key loaded from system property openai.api.key (read-only)";
                default -> "OpenAI API key loaded from " + openaiApiKeySource;
            };
            status.show("info", sourceText);
        } else if (openaiApiKeyValidated) {
            status.show("success", MessageFormat.format(I18nManager.getText("settings.openai.validated.ready"),
                    config.accounts().getOpenAiModels().size()));
            showOpenaiModels(config.accounts().getOpenAiAvailableModels(), config.accounts().getOpenAiModels());
        }
    }

    /** Saves the base URL, the chosen models and, once a test passed, the token; removes a cleared token. */
    @Override
    public void apply() {
        config.accounts().setOpenAiBaseUrl(openaiBaseUrlField.getText());
        if (!openaiModelChecks.isEmpty()) {
            String chatModel = openaiChatModelBox.getValue();
            boolean noChatModel = chatModel == null
                    || chatModel.equals(I18nManager.getText("settings.openai.chat.none"));
            boolean chatModelChanged = !(noChatModel ? "" : chatModel)
                    .equals(config.accounts().getOpenAiChatModel());
            config.accounts().setOpenAiChatModel(noChatModel ? "" : chatModel);

            /*  The flags follow the saved model; a running check and that of an unchanged model store them  */
            ApiProviderValidator validator = services.validator();
            if ((noChatModel || chatModelChanged) && validator != null)
                validator.storeOpenAiModelCheck(noChatModel ? "" : chatModel);
        }
        if (!openaiModelChecks.isEmpty())
            config.accounts().setOpenAiModels(openaiModelChecks.entrySet().stream()
                    .filter((e) -> e.getValue().get()).map(Map.Entry::getKey).toList());
        if (!config.accounts().isApiKeyEditable(ApiProvider.OPENAI))
            return;

        String currentOpenaiApiKey = openaiApiKeyInputControl.getText();
        if (currentOpenaiApiKey != null && !currentOpenaiApiKey.trim().isEmpty()) {
            /*  Only saved if validated, all other settings still are  */
            if (!openaiApiKeyValidated || !currentOpenaiApiKey.equals(validatedOpenaiApiKey)) {
                logger.warn("Attempted to save unvalidated OpenAI API key");
            } else {
                config.accounts().setApiKey(ApiProvider.OPENAI, currentOpenaiApiKey.trim());
                logger.info("Validated OpenAI API key saved to configuration");
            }
        } else if (config.accounts().hasApiKey(ApiProvider.OPENAI)
                && "configuration".equals(config.accounts().getApiKeySource(ApiProvider.OPENAI))) {
            /*  The field was cleared: the token is removed and the provider is off  */
            config.accounts().setApiKey(ApiProvider.OPENAI, "");
            config.accounts().setEnabled(ApiProvider.OPENAI, false);
            config.accounts().setValidated(ApiProvider.OPENAI, false);
            openaiApiKeyValidated = false;
            validatedOpenaiApiKey = null;
            logger.info("OpenAI API key removed from configuration");
        }
    }

    /** Resets the validation when the token or the base URL differs from the tested ones. */
    private void resetOpenaiValidation() {
        if (!openaiApiKeyValidated)
            return;
        if (Objects.equals(openaiApiKeyInputControl.getText(), validatedOpenaiApiKey)
                && Objects.equals(openaiBaseUrlField.getText().trim().replaceAll("/+$", ""), validatedOpenaiBaseUrl))
            return;

        openaiApiKeyValidated = false;
        validatedOpenaiApiKey = null;
        validatedOpenaiBaseUrl = null;
        status.hide();
        showOpenaiModels(List.of(), List.of());
        openaiProviderEnabledCheckBox.setDisable(true);
        openaiProviderEnabledCheckBox.setSelected(false);
    }

    @Override
    public void relabel() {
        openaiTitleLabel.setText(I18nManager.getText("settings.openai.title"));
        openaiDescriptionLabel.setText(I18nManager.getText("settings.openai.description"));
        openaiBaseUrlField.setPromptText(I18nManager.getText("settings.openai.baseurl.placeholder"));
        openaiApiKeyInputControl.setPromptText(I18nManager.getText("settings.openai.key.placeholder"));
        showOpenaiApiKeyButton.setTooltip(new Tooltip(I18nManager.getText("settings.openai.key.toggle.tooltip")));
        testOpenaiConnectionButton.setText(I18nManager.getText("settings.openai.test"));
        openaiModelsLabel.setText(I18nManager.getText("settings.openai.models.label"));
        openaiChatLabel.setText(I18nManager.getText("settings.openai.chat.label"));
        openaiProviderEnabledCheckBox.setText(I18nManager.getText("settings.openai.enable"));
        openaiProviderEnabledCheckBox.setTooltip(new Tooltip(I18nManager.getText("settings.openai.enable.tooltip")));
    }

    /** Shows the token as text or hidden, switching between a text field and a password field. */
    @FXML
    private void onShowOpenaiApiKeyToggle() {
        try {
            openaiApiKeyInputControl = ConnectionTest.swapKeyField(openaiApiKeyInputControl, !openaiApiKeyVisible,
                    I18nManager.getText("settings.openai.key.placeholder"));
            showOpenaiApiKeyIcon.setIconLiteral(openaiApiKeyVisible ? "mdi2e-eye" : "mdi2e-eye-off");
            openaiApiKeyVisible = !openaiApiKeyVisible;
            openaiApiKeyInputControl.textProperty().addListener((obs, oldText, newText) -> resetOpenaiValidation());
        } catch (Exception e) {
            logger.error("Failed to toggle OpenAI API key visibility", e);
        }
    }

    /** Lists the models with the typed token and shows them; token and URL are saved with "Save". */
    @FXML
    private void onTestOpenaiConnection() {
        String apiKey = openaiApiKeyInputControl.getText();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            status.show("error", I18nManager.getText("settings.openai.enter.key"));
            return;
        }
        String baseUrl = openaiBaseUrlField.getText() == null || openaiBaseUrlField.getText().isBlank()
                ? ApplicationConfig.OPENAI_DEFAULT_BASE_URL
                : openaiBaseUrlField.getText().trim().replaceAll("/+$", "");

        status.show("testing", I18nManager.getText("settings.openai.testing.status"));
        AtomicReference<OpenAiApiService.Account> tested = new AtomicReference<>();
        ConnectionTest.run(testOpenaiConnectionButton, I18nManager.getText("settings.openai.test"), () -> {
            tested.set(services.openAi().testAccount(apiKey, baseUrl).get());
            return tested.get().works();
        }, (success) -> {
            if (!success) {
                testFailed("settings.openai.test.failed");
                return;
            }

            openaiApiKeyValidated = true;
            validatedOpenaiApiKey = apiKey;
            validatedOpenaiBaseUrl = baseUrl;

            /*  The test may have added "/v1"; the field shows it, so "Save" keeps it  */
            OpenAiApiService.Account account = tested.get();
            if (!baseUrl.equals(account.baseUrl())) {
                validatedOpenaiBaseUrl = account.baseUrl();
                openaiBaseUrlField.setText(validatedOpenaiBaseUrl);
            }

            /*  The models of the API are shown on the next opening, the selection is saved with "Save"  */
            List<String> available = account.models();
            config.accounts().setOpenAiAvailableModels(available);
            status.show("success", available.isEmpty()
                    ? I18nManager.getText("settings.openai.test.success.nomodels")
                    : MessageFormat.format(I18nManager.getText("settings.openai.test.success"), available.size()));
            showOpenaiModels(available, account.selected());

            openaiProviderEnabledCheckBox.setDisable(false);
            if (!config.accounts().isEnabled(ApiProvider.OPENAI)) {
                openaiProviderEnabledCheckBox.setSelected(true);
                config.accounts().setEnabled(ApiProvider.OPENAI, true);
            }
            services.connectionTested(ApiProvider.OPENAI, true);

            /*  A changed token or base URL can change what the chosen language model can do: check it again  */
            onOpenaiChatModelChosen(openaiChatModelBox.getValue());
        }, (exception) -> {
            /*  Not decided (no network, timeout, HTTP 429 or 5xx): typed token and URL not taken, the rest stays  */
            logger.warn("OpenAI API connection test could not decide", exception);
            openaiApiKeyValidated = false;
            validatedOpenaiApiKey = null;
            validatedOpenaiBaseUrl = null;
            status.show("error", I18nManager.getText("settings.test.undecided",
                    ApiProviderValidator.reasonOf(exception)));
        });
    }

    /** Shows a failed test, puts the saved token and URL back and switches the provider off. */
    private void testFailed(String messageKey) {
        openaiApiKeyValidated = false;
        validatedOpenaiApiKey = null;
        validatedOpenaiBaseUrl = null;
        status.show("error", I18nManager.getText(messageKey));
        openaiProviderEnabledCheckBox.setDisable(true);
        openaiProviderEnabledCheckBox.setSelected(false);
        services.connectionTested(ApiProvider.OPENAI, false);
        config.accounts().setEnabled(ApiProvider.OPENAI, false);
    }

    /** Switches the provider on or off at once; switched on, its language model is checked. */
    @FXML
    private void onOpenaiProviderToggle() {
        boolean enabled = openaiProviderEnabledCheckBox.isSelected();
        config.accounts().setEnabled(ApiProvider.OPENAI, enabled);
        logger.info("OpenAI provider " + (enabled ? "enabled" : "disabled"));

        /*  A disabled provider was not checked at the start: switched on, its language model is checked now  */
        if (enabled && config.accounts().isValidated(ApiProvider.OPENAI))
            onOpenaiChatModelChosen(openaiChatModelBox.getValue());
    }

    /**
     * Tests a chosen language model (plain answer, function call and web search). The validator stores the results
     * as soon as the check ends, also after the dialog was closed; "Save" takes the finished check of a newly chosen
     * model. The status line shows the outcome, see {@link #openaiModelStatus}.
     *
     * @param chosen the chosen entry of the language model choice
     */
    private void onOpenaiChatModelChosen(String chosen) {
        if (openaiChatModelUpdating || chosen == null
                || chosen.equals(I18nManager.getText("settings.openai.chat.none")))
            return;

        ApiProviderValidator validator = services.validator();
        if (validator == null)
            return;

        openaiChatModelBox.setDisable(true);
        status.show("testing", I18nManager.getText("settings.openai.chat.testing", chosen));
        /*  With the token and URL the dialog validated, which may not be saved yet  */
        validator.checkOpenAiModel(chosen, validatedOpenaiApiKey, validatedOpenaiBaseUrl).whenComplete(
                (ApiProviderValidator.ModelCheck check, Throwable error) -> Platform.runLater(() -> {
            openaiChatModelBox.setDisable(false);
            OpenaiModelStatus modelStatus = openaiModelStatus(chosen, check, error);
            if (!modelStatus.keepsChoice()) {
                openaiChatModelUpdating = true;
                openaiChatModelBox.setValue(I18nManager.getText("settings.openai.chat.none"));
                openaiChatModelUpdating = false;
            }
            status.show(modelStatus.type(), modelStatus.message());
        }));
    }

    /**
     * The status after the check of a chosen OpenAI language model.
     *
     * @param keepsChoice false if the choice goes back to "none"
     * @param type        the status type: {@code success}, {@code info} or {@code error}
     * @param message     the status line
     */
    record OpenaiModelStatus(boolean keepsChoice, String type, String message) {
    }

    /**
     * Decides on the status after the check of a chosen OpenAI language model. Only a model that definitely does not
     * answer or call tools goes back to "none"; one without web search stays chosen for the mood analysis and the
     * other chat features, it is only not offered for the brainstorm; an undecided check keeps it, too.
     *
     * @param model the checked model
     * @param check the outcomes of the check, or null if the check failed as a whole
     * @param error the failure of the check as a whole, or null
     * @return the status
     */
    static OpenaiModelStatus openaiModelStatus(String model, ApiProviderValidator.ModelCheck check, Throwable error) {
        /*  A check that could not decide (no network, timeout, HTTP 429 or 5xx) keeps the model: test again  */
        if (check == null || !check.chat().decided())
            return new OpenaiModelStatus(true, "error", I18nManager.getText("settings.openai.chat.check.failed", model,
                    check != null ? check.chat().problem()
                            : error != null ? ApiProviderValidator.reasonOf(error) : null));
        if (!check.chat().works())
            return new OpenaiModelStatus(false, "error", I18nManager.getText("settings.openai.chat.failed", model,
                    check.chat().problem()));
        if (check.search().works())
            return new OpenaiModelStatus(true, "success", I18nManager.getText("settings.openai.chat.ok", model));
        return new OpenaiModelStatus(true, "info", I18nManager.getText(check.search().decided()
                        ? "settings.openai.chat.nosearch" : "settings.openai.chat.search.kept",
                model, check.search().problem()));
    }

    /**
     * Shows the models of the OpenAI API with a check box each, the selected ones are the image models.
     *
     * @param available all model ids, hides the list if empty
     * @param selected  the ids to check
     */
    private void showOpenaiModels(List<String> available, List<String> selected) {
        openaiModelChecks.clear();
        for (String id : available)
            openaiModelChecks.put(id, new SimpleBooleanProperty(selected.contains(id)));
        openaiModelList.getItems().setAll(available);

        /*  The first entry is "none", the chosen language model stays selected if the API still lists it  */
        String none = I18nManager.getText("settings.openai.chat.none");
        String chatModel = openaiChatModelBox.getValue() == null || openaiChatModelBox.getValue().equals(none)
                ? config.accounts().getOpenAiChatModel() : openaiChatModelBox.getValue();
        openaiChatModelUpdating = true;
        openaiChatModelBox.getItems().setAll(none);
        openaiChatModelBox.getItems().addAll(available);
        openaiChatModelBox.setValue(available.contains(chatModel) ? chatModel : none);
        openaiChatModelUpdating = false;
        openaiModelsContainer.setVisible(!available.isEmpty());
        openaiModelsContainer.setManaged(!available.isEmpty());
    }
}
