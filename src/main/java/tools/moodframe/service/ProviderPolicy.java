package tools.moodframe.service;

import tools.moodframe.config.ApplicationConfig;
import tools.moodframe.model.GenerationModel;

/**
 * Which providers may be contacted, decided from the configuration.
 *
 * @since 1.9.1
 */
public final class ProviderPolicy {

    private ProviderPolicy() {
    }

    /**
     * Checks if OpenAI is the only provider in use: an OpenAI image model is selected and the brainstorm talks to
     * OpenAI. Then no other provider (Gemini, BFL) may be contacted.
     *
     * @param config the application configuration
     * @return true if only OpenAI is to be contacted
     */
    public static boolean isOpenAiOnly(ApplicationConfig config) {
        return GenerationModel.openAiModelId(config.getDefaultModel()) != null
                && "openai".equals(config.getBrainstormEngine()) && config.isOpenAiBrainstormAvailable();
    }
}
