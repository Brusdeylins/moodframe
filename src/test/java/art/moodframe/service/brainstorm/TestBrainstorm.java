package art.moodframe.service.brainstorm;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.repository.BrainstormRepository;
import art.moodframe.service.provider.gemini.GeminiApiService;
import art.moodframe.service.provider.openai.OpenAiApiService;

/**
 * The brainstorm for tests, wired as {@code MoodframeApplication} does, on sessions of its own: the service of one
 * window without the lookup of YouTube videos.
 */
public final class TestBrainstorm {

    private TestBrainstorm() {
    }

    /**
     * @param repository the session storage
     * @param gemini     the Gemini API service
     * @return the service with Gemini only
     */
    public static BrainstormService service(BrainstormRepository repository, GeminiApiService gemini) {
        return service(repository, gemini, null, null);
    }

    /**
     * @param repository the session storage
     * @param gemini     the Gemini API service, also does the research
     * @param openAi     the OpenAI API service, null if there is none
     * @param config     the configuration with the chosen engine and OpenAI language model, null for Gemini only
     * @return the service
     */
    public static BrainstormService service(BrainstormRepository repository, GeminiApiService gemini,
            OpenAiApiService openAi, ApplicationConfig config) {
        return new BrainstormService(new BrainstormSessions(repository), new BrainstormModels(gemini, openAi, config),
                new BrainstormTools(gemini, null, null));
    }
}
