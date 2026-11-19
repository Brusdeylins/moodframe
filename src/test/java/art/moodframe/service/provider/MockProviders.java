package art.moodframe.service.provider;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.service.provider.bfl.BflApiService;
import art.moodframe.service.provider.gemini.GeminiApiService;
import art.moodframe.service.provider.openai.OpenAiApiService;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Builds a {@link ProviderRegistry} of Mockito mocks: a mock answers {@link ImageProvider#getProvider()} with the
 * default method of its client interface, e.g. BFL for a mock of {@code BflApiService}. The registry asks for the
 * ids only, so the mocks are handed back without recorded invocations.
 */
public final class MockProviders {

    private MockProviders() {
    }

    /**
     * Creates the registry.
     *
     * @param mocks the mocked client interfaces
     * @return the registry of the mocks
     */
    public static ProviderRegistry registry(ImageProvider... mocks) {
        for (ImageProvider mock : mocks)
            when(mock.getProvider()).thenCallRealMethod();
        ProviderRegistry registry = new ProviderRegistry(mocks);
        clearInvocations((Object[]) mocks);
        return registry;
    }

    /**
     * Creates a registry of mocks of the three clients that answer the prompt limits and the preparation of a
     * request with their real methods, for tests of the generation without a provider behind it.
     *
     * @return the registry
     */
    public static ProviderRegistry preparing() {
        return new ProviderRegistry(mock(BflApiService.class, CALLS_REAL_METHODS),
                mock(GeminiApiService.class, CALLS_REAL_METHODS), mock(OpenAiApiService.class, CALLS_REAL_METHODS));
    }

    /**
     * Creates the registry of {@link #preparing()} whose OpenAI client lists the gateway models of the configuration,
     * as the real client does.
     *
     * @param config the configuration with the chosen models of the gateway
     * @return the registry
     */
    public static ProviderRegistry preparing(ApplicationConfig config) {
        OpenAiApiService openAi = mock(OpenAiApiService.class, CALLS_REAL_METHODS);
        doAnswer((invocation) -> config.getOpenAiModels()).when(openAi).getImageModels();
        return new ProviderRegistry(mock(BflApiService.class, CALLS_REAL_METHODS),
                mock(GeminiApiService.class, CALLS_REAL_METHODS), openAi);
    }
}
