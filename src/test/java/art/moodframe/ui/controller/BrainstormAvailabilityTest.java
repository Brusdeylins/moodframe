package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ConfigMocks;
import art.moodframe.model.ApiProvider;
import art.moodframe.service.provider.ApiProviderValidator;
import art.moodframe.service.provider.BflApiService;
import art.moodframe.service.provider.GeminiApiService;
import art.moodframe.service.provider.MockProviders;
import art.moodframe.service.provider.OpenAiApiService;
import art.moodframe.util.I18nManager;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Checks when the brainstorm button is enabled, what its hint says, and the account checks of the status bar. */
class BrainstormAvailabilityTest {

    @Test
    void theButtonNamesWhyItIsDisabled() {
        ApplicationConfig config = ConfigMocks.config();
        assertEquals("brainstorm.tooltip.no.key", InputBarController.brainstormHint(null, true));
        assertEquals("brainstorm.tooltip.no.key", InputBarController.brainstormHint(config, true));

        /*  A key, but no account with language model and web search: no brainstorm without research  */
        when(config.accounts().hasApiKey(ApiProvider.OPENAI)).thenReturn(true);
        when(config.accounts().hasApiKey(ApiProvider.GOOGLE_GEMINI)).thenReturn(true);
        assertEquals("brainstorm.tooltip.no.search", InputBarController.brainstormHint(config, true));

        when(config.accounts().isBrainstormAvailable(ApiProvider.OPENAI)).thenReturn(true);
        assertEquals("brainstorm.tooltip.no.chat", InputBarController.brainstormHint(config, false));
        assertEquals("brainstorm.tooltip", InputBarController.brainstormHint(config, true));
    }

    @Test
    void googleAloneEnablesTheButton() {
        ApplicationConfig config = ConfigMocks.config();
        when(config.accounts().hasApiKey(ApiProvider.GOOGLE_GEMINI)).thenReturn(true);
        when(config.accounts().isBrainstormAvailable(ApiProvider.GOOGLE_GEMINI)).thenReturn(true);

        assertEquals("brainstorm.tooltip", InputBarController.brainstormHint(config, true));
    }

    @Test
    void theStatusBarListsTheChecksOfEveryConfiguredAccount() {
        ApplicationConfig config = ConfigMocks.config();
        when(config.accounts().hasApiKey(ApiProvider.BFL)).thenReturn(true);
        when(config.accounts().isEnabled(ApiProvider.BFL)).thenReturn(true);
        when(config.accounts().isValidated(ApiProvider.BFL)).thenReturn(true);
        when(config.accounts().hasApiKey(ApiProvider.GOOGLE_GEMINI)).thenReturn(true);
        when(config.accounts().isValidated(ApiProvider.GOOGLE_GEMINI)).thenReturn(false);
        when(config.accounts().hasApiKey(ApiProvider.OPENAI)).thenReturn(true);
        when(config.accounts().isEnabled(ApiProvider.OPENAI)).thenReturn(true);
        when(config.accounts().isValidated(ApiProvider.OPENAI)).thenReturn(true);
        when(config.accounts().getOpenAiChatModel()).thenReturn("gpt-x");
        when(config.accounts().isChatOk(ApiProvider.OPENAI)).thenReturn(true);
        when(config.accounts().isWebSearch(ApiProvider.OPENAI)).thenReturn(false);
        ApiProviderValidator validator = mock(ApiProviderValidator.class);
        when(validator.getReason(ApiProvider.OPENAI, ApiProviderValidator.Check.WEB_SEARCH))
                .thenReturn("OpenAI error 404:\n  Not found");

        String connection = I18nManager.getText("status.api.check.connection");
        assertEquals("BFL: " + connection + " ✓\n"
                        + "Google (" + I18nManager.getText("status.api.check.off") + "): " + connection + " ✗\n"
                        + "OpenAI: " + connection + " ✓, gpt-x ✓, " + I18nManager.getText("status.api.check.search")
                        + " ✗ (OpenAI error 404: Not found)",
                StatusBarController.accountChecks(config, validator));
    }

    @Test
    void theStatusBarSaysWhenACheckFailedAndKeptTheLastResult() {
        ApplicationConfig config = ConfigMocks.config();
        when(config.accounts().hasApiKey(ApiProvider.OPENAI)).thenReturn(true);
        when(config.accounts().isEnabled(ApiProvider.OPENAI)).thenReturn(true);
        when(config.accounts().isValidated(ApiProvider.OPENAI)).thenReturn(true);
        when(config.accounts().getOpenAiChatModel()).thenReturn("gpt-x");
        when(config.accounts().isChatOk(ApiProvider.OPENAI)).thenReturn(true);
        when(config.accounts().isWebSearch(ApiProvider.OPENAI)).thenReturn(true);
        ApiProviderValidator validator = mock(ApiProviderValidator.class);
        String kept = I18nManager.getText("status.api.check.kept", "OpenAI error 503");
        when(validator.getReason(ApiProvider.OPENAI, ApiProviderValidator.Check.WEB_SEARCH)).thenReturn(kept);

        assertEquals("OpenAI: " + I18nManager.getText("status.api.check.connection") + " ✓, gpt-x ✓, "
                + I18nManager.getText("status.api.check.search") + " ✓ (" + kept + ")",
                StatusBarController.accountChecks(config, validator));
    }

    @Test
    void theStatusBarShowsANewlyChosenModelWithoutTheReasonOfTheFormerOne() {
        ApplicationConfig config = ConfigMocks.config();
        when(config.accounts().hasApiKey(ApiProvider.OPENAI)).thenReturn(true);
        when(config.accounts().isEnabled(ApiProvider.OPENAI)).thenReturn(true);
        when(config.accounts().isValidated(ApiProvider.OPENAI)).thenReturn(true);
        when(config.accounts().getOpenAiChatModel()).thenReturn("gpt-x");
        OpenAiApiService openAi = mock(OpenAiApiService.class);
        when(openAi.checkChatModel(anyString())).thenReturn(CompletableFuture.completedFuture(null));
        when(openAi.checkWebSearch("gpt-x")).thenReturn(CompletableFuture.completedFuture("OpenAI error 404"));
        when(openAi.checkWebSearch("gpt-y")).thenReturn(CompletableFuture.completedFuture(null));
        ApiProviderValidator validator = new ApiProviderValidator(config,
                MockProviders.registry(mock(BflApiService.class), mock(GeminiApiService.class), openAi));
        validator.checkOpenAiModel("gpt-x").join();
        validator.checkOpenAiModel("gpt-y").join();

        /*  "Save" stores the newly chosen model with the flags of its finished check  */
        when(config.accounts().getOpenAiChatModel()).thenReturn("gpt-y");
        when(config.accounts().isChatOk(ApiProvider.OPENAI)).thenReturn(true);
        when(config.accounts().isWebSearch(ApiProvider.OPENAI)).thenReturn(true);
        validator.storeOpenAiModelCheck("gpt-y");

        assertEquals("OpenAI: " + I18nManager.getText("status.api.check.connection") + " ✓, gpt-y ✓, "
                        + I18nManager.getText("status.api.check.search") + " ✓",
                StatusBarController.accountChecks(config, validator));
    }

    @Test
    void theStatusBarSaysWhenNoLanguageModelIsChosenAndListsNoUnconfiguredAccount() {
        ApplicationConfig config = ConfigMocks.config();
        when(config.accounts().hasApiKey(ApiProvider.OPENAI)).thenReturn(true);
        when(config.accounts().isEnabled(ApiProvider.OPENAI)).thenReturn(true);
        when(config.accounts().isValidated(ApiProvider.OPENAI)).thenReturn(true);
        when(config.accounts().getOpenAiChatModel()).thenReturn("");

        assertEquals("OpenAI: " + I18nManager.getText("status.api.check.connection") + " ✓, "
                + I18nManager.getText("status.api.check.nomodel"), StatusBarController.accountChecks(config, null));
        assertEquals("", StatusBarController.accountChecks(ConfigMocks.config(), null));
    }
}
