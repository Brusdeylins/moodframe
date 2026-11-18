package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
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
        ApplicationConfig config = mock(ApplicationConfig.class);
        assertEquals("brainstorm.tooltip.no.key", MainWindowController.brainstormHint(null, true));
        assertEquals("brainstorm.tooltip.no.key", MainWindowController.brainstormHint(config, true));

        /*  A key, but no account with language model and web search: no brainstorm without research  */
        when(config.hasOpenAiApiKey()).thenReturn(true);
        when(config.hasGeminiApiKey()).thenReturn(true);
        assertEquals("brainstorm.tooltip.no.search", MainWindowController.brainstormHint(config, true));

        when(config.isOpenAiBrainstormAvailable()).thenReturn(true);
        assertEquals("brainstorm.tooltip.no.chat", MainWindowController.brainstormHint(config, false));
        assertEquals("brainstorm.tooltip", MainWindowController.brainstormHint(config, true));
    }

    @Test
    void googleAloneEnablesTheButton() {
        ApplicationConfig config = mock(ApplicationConfig.class);
        when(config.hasGeminiApiKey()).thenReturn(true);
        when(config.isGeminiBrainstormAvailable()).thenReturn(true);

        assertEquals("brainstorm.tooltip", MainWindowController.brainstormHint(config, true));
    }

    @Test
    void theStatusBarListsTheChecksOfEveryConfiguredAccount() {
        ApplicationConfig config = mock(ApplicationConfig.class);
        when(config.hasApiKey()).thenReturn(true);
        when(config.isBflProviderEnabled()).thenReturn(true);
        when(config.isBflProviderValidated()).thenReturn(true);
        when(config.hasGeminiApiKey()).thenReturn(true);
        when(config.isGeminiProviderValidated()).thenReturn(false);
        when(config.hasOpenAiApiKey()).thenReturn(true);
        when(config.isOpenAiProviderEnabled()).thenReturn(true);
        when(config.isOpenAiProviderValidated()).thenReturn(true);
        when(config.getOpenAiChatModel()).thenReturn("gpt-x");
        when(config.isOpenAiChatOk()).thenReturn(true);
        when(config.isOpenAiWebSearch()).thenReturn(false);
        ApiProviderValidator validator = mock(ApiProviderValidator.class);
        when(validator.getReason(ApiProvider.OPENAI, ApiProviderValidator.Check.WEB_SEARCH))
                .thenReturn("OpenAI error 404:\n  Not found");

        String connection = I18nManager.getText("status.api.check.connection");
        assertEquals("BFL: " + connection + " ✓\n"
                        + "Google (" + I18nManager.getText("status.api.check.off") + "): " + connection + " ✗\n"
                        + "OpenAI: " + connection + " ✓, gpt-x ✓, " + I18nManager.getText("status.api.check.search")
                        + " ✗ (OpenAI error 404: Not found)",
                MainWindowController.accountChecks(config, validator));
    }

    @Test
    void theStatusBarSaysWhenACheckFailedAndKeptTheLastResult() {
        ApplicationConfig config = mock(ApplicationConfig.class);
        when(config.hasOpenAiApiKey()).thenReturn(true);
        when(config.isOpenAiProviderEnabled()).thenReturn(true);
        when(config.isOpenAiProviderValidated()).thenReturn(true);
        when(config.getOpenAiChatModel()).thenReturn("gpt-x");
        when(config.isOpenAiChatOk()).thenReturn(true);
        when(config.isOpenAiWebSearch()).thenReturn(true);
        ApiProviderValidator validator = mock(ApiProviderValidator.class);
        String kept = I18nManager.getText("status.api.check.kept", "OpenAI error 503");
        when(validator.getReason(ApiProvider.OPENAI, ApiProviderValidator.Check.WEB_SEARCH)).thenReturn(kept);

        assertEquals("OpenAI: " + I18nManager.getText("status.api.check.connection") + " ✓, gpt-x ✓, "
                + I18nManager.getText("status.api.check.search") + " ✓ (" + kept + ")",
                MainWindowController.accountChecks(config, validator));
    }

    @Test
    void theStatusBarShowsANewlyChosenModelWithoutTheReasonOfTheFormerOne() {
        ApplicationConfig config = mock(ApplicationConfig.class);
        when(config.hasOpenAiApiKey()).thenReturn(true);
        when(config.isOpenAiProviderEnabled()).thenReturn(true);
        when(config.isOpenAiProviderValidated()).thenReturn(true);
        when(config.getOpenAiChatModel()).thenReturn("gpt-x");
        OpenAiApiService openAi = mock(OpenAiApiService.class);
        when(openAi.checkChatModel(anyString())).thenReturn(CompletableFuture.completedFuture(null));
        when(openAi.checkWebSearch("gpt-x")).thenReturn(CompletableFuture.completedFuture("OpenAI error 404"));
        when(openAi.checkWebSearch("gpt-y")).thenReturn(CompletableFuture.completedFuture(null));
        ApiProviderValidator validator = new ApiProviderValidator(config,
                MockProviders.registry(mock(BflApiService.class), mock(GeminiApiService.class), openAi));
        validator.checkOpenAiModel("gpt-x").join();
        validator.checkOpenAiModel("gpt-y").join();

        /*  "Save" stores the newly chosen model with the flags of its finished check  */
        when(config.getOpenAiChatModel()).thenReturn("gpt-y");
        when(config.isOpenAiChatOk()).thenReturn(true);
        when(config.isOpenAiWebSearch()).thenReturn(true);
        validator.storeOpenAiModelCheck("gpt-y");

        assertEquals("OpenAI: " + I18nManager.getText("status.api.check.connection") + " ✓, gpt-y ✓, "
                        + I18nManager.getText("status.api.check.search") + " ✓",
                MainWindowController.accountChecks(config, validator));
    }

    @Test
    void theStatusBarSaysWhenNoLanguageModelIsChosenAndListsNoUnconfiguredAccount() {
        ApplicationConfig config = mock(ApplicationConfig.class);
        when(config.hasOpenAiApiKey()).thenReturn(true);
        when(config.isOpenAiProviderEnabled()).thenReturn(true);
        when(config.isOpenAiProviderValidated()).thenReturn(true);
        when(config.getOpenAiChatModel()).thenReturn("");

        assertEquals("OpenAI: " + I18nManager.getText("status.api.check.connection") + " ✓, "
                + I18nManager.getText("status.api.check.nomodel"), MainWindowController.accountChecks(config, null));
        assertEquals("", MainWindowController.accountChecks(mock(ApplicationConfig.class), null));
    }
}
