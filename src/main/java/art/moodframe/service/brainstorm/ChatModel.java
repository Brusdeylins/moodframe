package art.moodframe.service.brainstorm;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ResearchNote;
import art.moodframe.service.provider.ChatTool;
import art.moodframe.service.provider.ChatTurn;
import art.moodframe.service.provider.GeminiApiService;
import art.moodframe.service.provider.OpenAiApiService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The language model of the brainstorm and of the analysis of an image: Gemini or an OpenAI language model, chosen
 * once with {@link #choose} instead of asking at every call which one answers.
 *
 * @since 2.1.3
 */
interface ChatModel {

    /*  The title request of a language model without one of its own, as Gemini asks for it  */
    String TITLE_PROMPT = "Name this session in ";
    String TITLE_PROMPT_RULES = " with one or two words (at most 24 characters), based on what the user wants to "
            + "create. Answer with the name only, no quotes, no punctuation.\n\n";

    /**
     * Sends one request of a conversation; the client of the provider translates it into its own format.
     *
     * @param systemPrompt the system instruction
     * @param turns        the conversation so far
     * @param tools        the tools offered, empty for none
     * @param noMoreCalls  whether the model must answer now
     * @return the answer: text, or tool calls
     */
    CompletableFuture<ChatTurn.Model> chatTurn(String systemPrompt, List<ChatTurn> turns, List<ChatTool> tools,
            boolean noMoreCalls);

    /**
     * Researches a question on the web.
     *
     * @param query what to find out
     * @return the found text and its sources
     */
    CompletableFuture<ResearchNote> research(String query);

    /**
     * Asks for a short name of a session.
     *
     * @param content  what the session is about
     * @param language the language name of the title
     * @return the title, may be empty
     */
    CompletableFuture<String> suggestTitle(String content, String language);

    /**
     * Tells if the model watches YouTube videos: Gemini does; while OpenAI answers, Google is not contacted.
     *
     * @return true for Gemini
     */
    boolean watchesVideos();

    /**
     * Chooses the language model: OpenAI if its language model is available and either chosen or the only one,
     * else Gemini.
     *
     * @param gemini        the Gemini client (may be null)
     * @param openAi        the OpenAI client (may be null)
     * @param config        the configuration with the OpenAI language model (may be null: Gemini)
     * @param openAiChosen  whether OpenAI is the chosen engine and can do what the caller needs
     * @param geminiUsable  whether Gemini can do what the caller needs
     * @return the model; Gemini also if it is not usable, the caller decides about that
     */
    static ChatModel choose(GeminiApiService gemini, OpenAiApiService openAi, ApplicationConfig config,
            boolean openAiChosen, boolean geminiUsable) {
        if (openAi != null && config != null && config.accounts().isOpenAiChatAvailable() && (openAiChosen || !geminiUsable))
            return openAi(openAi, config.getOpenAiChatModel());
        return gemini(gemini);
    }

    /**
     * Gets Gemini as language model.
     *
     * @param gemini the Gemini client
     * @return the model
     */
    static ChatModel gemini(GeminiApiService gemini) {
        return new ChatModel() {
            @Override
            public CompletableFuture<ChatTurn.Model> chatTurn(String systemPrompt, List<ChatTurn> turns,
                    List<ChatTool> tools, boolean noMoreCalls) {
                return gemini.chatTurn(systemPrompt, turns, tools, noMoreCalls);
            }

            @Override
            public CompletableFuture<ResearchNote> research(String query) {
                return gemini.research(query);
            }

            @Override
            public CompletableFuture<String> suggestTitle(String content, String language) {
                return gemini.suggestTitle(content, language);
            }

            @Override
            public boolean watchesVideos() {
                return true;
            }
        };
    }

    /**
     * Gets an OpenAI language model; it watches no videos, the videos are Gemini's.
     *
     * @param openAi the OpenAI client
     * @param model  the model id
     * @return the model
     */
    static ChatModel openAi(OpenAiApiService openAi, String model) {
        return new ChatModel() {
            @Override
            public CompletableFuture<ChatTurn.Model> chatTurn(String systemPrompt, List<ChatTurn> turns,
                    List<ChatTool> tools, boolean noMoreCalls) {
                return openAi.chatTurn(model, systemPrompt, turns, tools, noMoreCalls);
            }

            @Override
            public CompletableFuture<ResearchNote> research(String query) {
                return openAi.research(model, query);
            }

            @Override
            public CompletableFuture<String> suggestTitle(String content, String language) {
                return openAi.complete(model, TITLE_PROMPT + language + TITLE_PROMPT_RULES + content);
            }

            @Override
            public boolean watchesVideos() {
                return false;
            }
        };
    }
}
