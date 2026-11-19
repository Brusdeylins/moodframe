package art.moodframe.service.provider;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.GenerationJobInfo;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.model.ResearchNote;
import art.moodframe.net.IoExecutor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * OkHttp implementation of {@link OpenAiApiService}: a facade over the account, image and chat parts, which share one
 * {@link OpenAiTransport} and its error mapping. The blocking requests run on the executor of the constructor.
 *
 * @author Matthias Brusdeylins
 * @since 1.9.0
 */
public class OpenAiApiServiceImpl implements OpenAiApiService {

    private final OpenAiTransport transport;
    private final OpenAiAccounts accounts;
    private final OpenAiImageClient image;
    private final OpenAiChatClient chat;

    /**
     * Creates the service with the token and base URL of the configuration, sending on the {@link IoExecutor}.
     *
     * @param config the application configuration
     */
    public OpenAiApiServiceImpl(ApplicationConfig config) {
        this(config, IoExecutor.get());
    }

    /**
     * Creates the service with the token and base URL of the configuration.
     *
     * @param config the application configuration
     * @param io     runs the blocking requests
     */
    public OpenAiApiServiceImpl(ApplicationConfig config, Executor io) {
        this(new OpenAiTransport(config, io));
    }

    /*  a client of a transport, the configured one or one with its own token and base URL  */
    private OpenAiApiServiceImpl(OpenAiTransport transport) {
        this.transport = transport;
        this.accounts = new OpenAiAccounts(transport);
        this.image = new OpenAiImageClient(transport);
        this.chat = new OpenAiChatClient(transport);
    }

    @Override
    public OpenAiApiService withCredentials(String apiKey, String endpoint) {
        return new OpenAiApiServiceImpl(transport.withCredentials(apiKey, endpoint));
    }

    @Override
    public CompletableFuture<Boolean> testConnection() {
        return accounts.testConnection();
    }

    @Override
    public CompletableFuture<Account> testAccount(String apiKey, String baseUrl) {
        return new OpenAiAccounts(transport.withCredentials(apiKey, baseUrl)).testAccount();
    }

    @Override
    public List<String> getImageModels() {
        return accounts.imageModels();
    }

    @Override
    public CompletableFuture<GenerationJobInfo> submitGenerationRequest(ImageGenerationRequest request) {
        return image.submit(request);
    }

    @Override
    public CompletableFuture<ChatTurn.Model> chatTurn(String model, String systemPrompt, List<ChatTurn> turns,
            List<ChatTool> tools, boolean noMoreCalls) {
        return chat.chatTurn(model, systemPrompt, turns, tools, noMoreCalls);
    }

    @Override
    public CompletableFuture<String> checkChatModel(String model) {
        return chat.checkChatModel(model);
    }

    @Override
    public CompletableFuture<ResearchNote> research(String model, String query) {
        return chat.research(model, query);
    }

    @Override
    public CompletableFuture<String> checkWebSearch(String model) {
        return chat.checkWebSearch(model);
    }

    @Override
    public CompletableFuture<String> complete(String model, String prompt) {
        return chat.complete(model, prompt);
    }

    @Override
    public String getApiKey() {
        return transport.getApiKey();
    }

    @Override
    public String getBaseUrl() {
        return transport.getBaseUrl();
    }

    @Override
    public boolean isConfigured() {
        return transport.isConfigured();
    }
}
