package art.moodframe.service.brainstorm;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.BrainstormSession;
import art.moodframe.service.provider.GeminiApiService;
import art.moodframe.service.provider.OpenAiApiService;

/**
 * The language models of the brainstorm: which one answers a session, read for every request, so a change of the
 * engine applies to the next message.
 *
 * @since 2.1.3
 */
public class BrainstormModels {

    private final GeminiApiService gemini;
    private final OpenAiApiService openAi;
    private final ApplicationConfig config;

    /**
     * Creates the models.
     *
     * @param gemini the Gemini client
     * @param openAi the OpenAI client, null if there is none
     * @param config the configuration with the chosen engine and the OpenAI language model, null for Gemini only
     */
    public BrainstormModels(GeminiApiService gemini, OpenAiApiService openAi, ApplicationConfig config) {
        this.gemini = gemini;
        this.openAi = openAi;
        this.config = config;
    }

    /**
     * The language model of a session: OpenAI if the session talks to it (its own choice, else the choice of the
     * window; or Gemini is not usable), else Gemini. A choice that is no longer usable falls back to the other one.
     */
    ChatModel of(BrainstormSession session) {
        boolean geminiUsable = config != null && config.accounts().isBrainstormAvailable(ApiProvider.GOOGLE_GEMINI);
        String engine = session.getEngine() != null ? session.getEngine()
                : config != null ? config.accounts().getBrainstormEngine() : "gemini";

        /*  Without a usable Gemini the session stays with OpenAI, so it never turns to Google unasked  */
        boolean openAiChosen = config != null && config.accounts().isBrainstormAvailable(ApiProvider.OPENAI)
                && "openai".equals(engine);
        return ChatModel.choose(gemini, openAi, config, openAiChosen, geminiUsable);
    }
}
