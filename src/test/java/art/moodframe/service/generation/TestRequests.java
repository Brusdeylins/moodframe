package art.moodframe.service.generation;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.repository.ChatRepository;
import art.moodframe.service.provider.ProviderRegistry;

import java.net.http.HttpClient;
import java.util.concurrent.Executor;

/**
 * Wires a {@link RequestResponseManager} for the tests as the composition root does: a tracker on the store thread of
 * its own {@link GenerationWorkers}, a file store with a plain download client.
 */
public final class TestRequests {

    private TestRequests() {
    }

    /**
     * Creates the manager; {@link RequestResponseManager#shutdown()} stops its workers.
     *
     * @param config     the configuration
     * @param repository where the entries and images are stored
     * @param providers  the providers
     * @param io         runs the blocking work
     * @return the manager
     */
    public static RequestResponseManager create(ApplicationConfig config, ChatRepository repository,
            ProviderRegistry providers, Executor io) {
        GenerationWorkers workers = new GenerationWorkers();
        return new RequestResponseManager(config, providers, new ChatEntryTracker(repository, workers.store()),
                new ImageFileStore(config, HttpClient::newHttpClient), workers, io);
    }
}
