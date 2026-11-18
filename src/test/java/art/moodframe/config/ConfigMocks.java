package art.moodframe.config;

import art.moodframe.model.ApiProvider;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A mocked configuration for tests: every section is a mock of its own, reached by {@code config.accounts()} and the
 * like, so a test stubs {@code when(config.accounts().isEnabled(ApiProvider.BFL))}. The shortcuts on
 * {@link ApplicationConfig} pass to the mocked sections unless a test stubs them itself.
 */
public final class ConfigMocks {

    /*  the methods of ApplicationConfig that only pass a call to a section  */
    private static final Set<String> SHORTCUTS = Set.of("getApiRegion", "getApiTimeoutSeconds", "getOpenAiBaseUrl",
            "setOpenAiBaseUrl", "getOpenAiModels", "setOpenAiModels", "hasOpenAiModelSelection",
            "setOpenAiAvailableModels", "getOpenAiChatModel", "getDefaultModel", "getFlux2SafetyTolerance",
            "setFlux2SafetyTolerance", "getFlux2FlexGuidance", "getFlux2FlexSteps", "isProxyEnabled");

    private ConfigMocks() {
    }

    /**
     * Creates a mocked configuration with mocked sections.
     *
     * @return the configuration
     */
    public static ApplicationConfig config() {
        return config(RETURNS_DEFAULTS);
    }

    /**
     * Creates a mocked configuration with mocked sections whose texts are empty instead of null: no key, no URL.
     *
     * @return the configuration
     */
    public static ApplicationConfig withEmptyTexts() {
        return config((invocation) -> invocation.getMethod().getReturnType() == String.class ? ""
                : RETURNS_DEFAULTS.answer(invocation));
    }

    /*  a mocked configuration whose sections and other methods answer as given  */
    private static ApplicationConfig config(Answer<?> answer) {
        ApplicationConfig config = mock(ApplicationConfig.class, (invocation) -> {
            Method method = invocation.getMethod();
            return SHORTCUTS.contains(method.getName()) && method.getDeclaringClass() == ApplicationConfig.class
                    ? invocation.callRealMethod() : answer.answer(invocation);
        });
        UiState ui = mock(UiState.class, answer);
        GenerationDefaults generation = mock(GenerationDefaults.class, answer);
        ProviderAccounts accounts = mock(ProviderAccounts.class, answer);
        ProxySettings proxy = mock(ProxySettings.class, answer);
        McpSettings mcp = mock(McpSettings.class, answer);

        /*  derived from the stubbed flags, as in the real accounts  */
        when(accounts.isUsable(any())).thenCallRealMethod();
        when(accounts.hasAnyApiKey()).thenCallRealMethod();
        when(config.ui()).thenReturn(ui);
        when(config.generation()).thenReturn(generation);
        when(config.accounts()).thenReturn(accounts);
        when(config.proxy()).thenReturn(proxy);
        when(config.mcp()).thenReturn(mcp);
        return config;
    }

    /**
     * Creates a real configuration in a fresh library below the home of the tests, without any stored value.
     *
     * @return the configuration
     * @throws IOException if the folder cannot be created
     */
    public static ApplicationConfig fresh() throws IOException {
        Path home = Files.createTempDirectory(Path.of(System.getProperty("user.home")), "config");
        return new ApplicationConfig(LibraryPaths.under(home));
    }

    /**
     * Creates a real configuration in a fresh library with the keys of BFL and Gemini, both enabled, so that every
     * model of them can be chosen.
     *
     * @return the configuration
     * @throws IOException if the folder cannot be created
     */
    public static ApplicationConfig withBflAndGemini() throws IOException {
        ApplicationConfig config = fresh();
        for (ApiProvider provider : List.of(ApiProvider.BFL, ApiProvider.GOOGLE_GEMINI)) {
            config.accounts().setApiKey(provider, "test-key");
            config.accounts().setEnabled(provider, true);
        }
        return config;
    }
}
