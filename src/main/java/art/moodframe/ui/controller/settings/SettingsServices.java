package art.moodframe.ui.controller.settings;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.mcp.McpService;
import art.moodframe.model.ApiProvider;
import art.moodframe.service.ConfigurationExporter;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.provider.ImageProvider;
import art.moodframe.service.provider.ProviderRegistry;
import art.moodframe.service.provider.bfl.BflApiService;
import art.moodframe.service.provider.gemini.GeminiApiService;
import art.moodframe.service.provider.openai.OpenAiApiService;
import art.moodframe.service.provider.validation.ApiProviderValidator;

/**
 * What the sections of the settings dialog work with, looked up once by the dialog and handed to every section.
 * Only the configuration is required; a missing service makes the action that needs it fail or do nothing, as when
 * it was looked up on use.
 *
 * @param config    the settings
 * @param providers the provider clients for the connection tests, or null
 * @param validator stores the results of the connection and model checks, or null
 * @param mcp       starts and stops the MCP server, or null
 * @param exporter  exports and imports the settings and the library, or null
 * @param blocks    the building blocks, whose custom styles a reset deletes, or null
 * @param prompts   the message boxes and file choosers of the sections
 */
public record SettingsServices(ApplicationConfig config, ProviderRegistry providers, ApiProviderValidator validator,
        McpService mcp, ConfigurationExporter exporter, PromptBlockService blocks, SettingsPrompts prompts) {

    /** @return the live BFL client, for the tests with a typed key, the proxy and the certificates */
    BflApiService bfl() {
        return provider(ApiProvider.BFL, BflApiService.class, "BFL API service not available");
    }

    /** @return the live Gemini client, for the test of a typed key */
    GeminiApiService gemini() {
        return provider(ApiProvider.GOOGLE_GEMINI, GeminiApiService.class, "Gemini API service not available");
    }

    /** @return the live OpenAI client, for the test of a typed token */
    OpenAiApiService openAi() {
        return provider(ApiProvider.OPENAI, OpenAiApiService.class, "OpenAI API service not available");
    }

    /*  the client of a provider; the message names the missing one, as the connection test shows it  */
    private <T extends ImageProvider> T provider(ApiProvider provider, Class<T> type, String missing) {
        if (providers == null)
            throw new RuntimeException(missing);
        return providers.get(provider, type);
    }

    /**
     * Stores a decided connection test, so the status bar also drops the reason of an earlier check.
     *
     * @param provider the tested provider
     * @param works    whether the connection works
     */
    void connectionTested(ApiProvider provider, boolean works) {
        if (validator != null)
            validator.connectionTested(provider, works);
        else
            config.accounts().setValidated(provider, works);
    }
}
