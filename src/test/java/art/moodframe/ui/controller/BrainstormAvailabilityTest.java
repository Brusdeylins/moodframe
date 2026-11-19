package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ConfigMocks;
import art.moodframe.config.ProviderAccounts;
import art.moodframe.model.ApiProvider;
import art.moodframe.service.provider.MockProviders;
import art.moodframe.service.provider.bfl.BflApiService;
import art.moodframe.service.provider.gemini.GeminiApiService;
import art.moodframe.service.provider.openai.OpenAiApiService;
import art.moodframe.service.provider.validation.ApiProviderValidator;
import art.moodframe.util.I18nManager;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Checks when the brainstorm button is enabled, what its hint says, the hint of the empty prompt, and the key status
 * and the account checks of the status bar.
 */
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
    void aKeyCountsOnlyForAProviderWithALanguageModel() {
        assertEquals(List.of(ApiProvider.GOOGLE_GEMINI, ApiProvider.OPENAI), Arrays.stream(ApiProvider.values())
                .filter(ApiProvider::hasLanguageModel).toList());
        ApplicationConfig config = ConfigMocks.config();
        when(config.accounts().hasApiKey(ApiProvider.BFL)).thenReturn(true);
        assertEquals("brainstorm.tooltip.no.key", InputBarController.brainstormHint(config, true));
    }

    @Test
    void googleAloneEnablesTheButton() {
        ApplicationConfig config = ConfigMocks.config();
        when(config.accounts().hasApiKey(ApiProvider.GOOGLE_GEMINI)).thenReturn(true);
        when(config.accounts().isBrainstormAvailable(ApiProvider.GOOGLE_GEMINI)).thenReturn(true);

        assertEquals("brainstorm.tooltip", InputBarController.brainstormHint(config, true));
    }

    /**
     * The key status of the status bar asks every provider: OpenAI alone, enabled with its key, is valid; an enabled
     * provider without a key is not; with no provider enabled any key will do.
     */
    @Test
    void theKeyStatusOfTheStatusBarAsksEveryProvider() {
        ProviderAccounts accounts = ConfigMocks.config().accounts();
        assertFalse(StatusBarController.keysValid(accounts), "no key");
        when(accounts.hasApiKey(ApiProvider.OPENAI)).thenReturn(true);
        assertTrue(StatusBarController.keysValid(accounts), "no provider enabled, but a key");
        when(accounts.isEnabled(ApiProvider.OPENAI)).thenReturn(true);
        assertTrue(StatusBarController.keysValid(accounts), "OpenAI alone");
        when(accounts.isEnabled(ApiProvider.BFL)).thenReturn(true);
        assertFalse(StatusBarController.keysValid(accounts), "BFL enabled without a key");
    }

    /**
     * The hint of the empty prompt names the provider whose key is missing, OpenAI as well, in the current language.
     */
    @Test
    void theHintOfTheEmptyPromptNamesTheProviderWithoutKey() {
        for (ApiProvider provider : ApiProvider.values())
            assertEquals(I18nManager.getText("status.error.api.key.missing.provider", provider.getDisplayName()),
                    InputBarController.placeholder(false, true, false, provider), provider.name());
        assertEquals(I18nManager.getText("status.error.api.key.missing"),
                InputBarController.placeholder(false, true, false, null));
        assertEquals(I18nManager.getText("input.prompt.placeholder"),
                InputBarController.placeholder(true, true, false, ApiProvider.OPENAI));
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
