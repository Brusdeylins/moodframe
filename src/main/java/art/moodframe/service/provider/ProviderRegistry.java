package art.moodframe.service.provider;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationModel;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * The image providers of the application by their {@link ApiProvider}, built once at the start and passed to the
 * services that dispatch by provider. A new provider is one more {@link ImageProvider} here and its models in
 * {@link GenerationModel}, besides the other places of the checklist of decision D4 in docs/ARCHITECTURE.md.
 *
 * @since 2.1.3
 */
public class ProviderRegistry {

    private final Map<ApiProvider, ImageProvider> providers = new EnumMap<>(ApiProvider.class);

    /**
     * Creates the registry.
     *
     * @param providers the providers, each with its own {@link ImageProvider#getProvider()}
     * @throws IllegalArgumentException if a provider has no id or two have the same
     */
    public ProviderRegistry(ImageProvider... providers) {
        for (ImageProvider provider : providers) {
            ApiProvider id = provider.getProvider();
            if (id == null || this.providers.putIfAbsent(id, provider) != null)
                throw new IllegalArgumentException("No or a second provider for " + id);
        }
    }

    /**
     * Gets the provider of an id.
     *
     * @param provider the id
     * @return the provider
     * @throws IllegalStateException if none is registered
     */
    public ImageProvider get(ApiProvider provider) {
        ImageProvider found = providers.get(provider);
        if (found == null)
            throw new IllegalStateException("No provider registered for " + provider);
        return found;
    }

    /**
     * Gets the provider of an id as its client interface, for the extras beyond the {@link ImageProvider}.
     *
     * @param provider the id
     * @param type     the client interface, e.g. {@code GeminiApiService.class}
     * @param <T>      the client interface
     * @return the provider
     * @throws IllegalStateException if none is registered or it has another type
     */
    public <T extends ImageProvider> T get(ApiProvider provider, Class<T> type) {
        ImageProvider found = get(provider);
        if (!type.isInstance(found))
            throw new IllegalStateException("The provider for " + provider + " is no " + type.getSimpleName());
        return type.cast(found);
    }

    /**
     * Gets all providers.
     *
     * @return the providers in the order of {@link ApiProvider}
     */
    public Collection<ImageProvider> all() {
        return Collections.unmodifiableCollection(providers.values());
    }

    /**
     * Gets the ids of all providers.
     *
     * @return the ids in their order
     */
    public Set<ApiProvider> ids() {
        return Collections.unmodifiableSet(providers.keySet());
    }

    /**
     * Finds the provider of an API model id: the provider of its {@link GenerationModel} constant, else the provider
     * that {@link ImageProvider#servesModel(String) serves} it (an OpenAI-compatible gateway), else BFL, whose former
     * FLUX models are no constants any more.
     *
     * @param modelId the API model id of a request (may be null)
     * @return the provider id
     */
    public ApiProvider providerOf(String modelId) {
        GenerationModel model = GenerationModel.fromApiModelId(modelId);
        if (model != null)
            return model.getProvider();
        for (ImageProvider provider : providers.values()) {
            if (modelId != null && provider.servesModel(modelId))
                return provider.getProvider();
        }
        return ApiProvider.BFL;
    }

    /**
     * Gets the prompt limit of a model in characters, as its provider tells it.
     *
     * @param modelId the fixed id of the model, see {@link GenerationModel#idOf(String)} (may be null)
     * @return the limit, that of the provider of an unknown model id (see {@link #providerOf(String)}) without model
     */
    public int promptLimit(String modelId) {
        GenerationModel model = GenerationModel.fromId(modelId);
        if (model == null)
            return forModel(null).maxPromptLength(null);
        return get(model.getProvider()).maxPromptLength(apiModelId(modelId));
    }

    /**
     * Gets the API model id of a fixed id (see {@link GenerationModel#idOf(String)}).
     *
     * @param modelId the fixed id (may be null)
     * @return the API id of its constant, the id itself for a model a provider lists beyond the constants
     */
    public static String apiModelId(String modelId) {
        GenerationModel model = GenerationModel.constantOf(modelId);
        return model != null ? model.getApiModelId() : modelId;
    }

    /**
     * Checks if a fixed id names a model that can be ordered: a constant, or a model a provider
     * {@link ImageProvider#listedModels() lists}.
     *
     * @param modelId the fixed id (may be null)
     * @return true if it can be ordered
     */
    public boolean isOrderable(String modelId) {
        if (GenerationModel.constantOf(modelId) != null)
            return true;
        for (ImageProvider provider : providers.values()) {
            if (modelId != null && provider.listedModels().contains(modelId))
                return true;
        }
        return false;
    }

    /**
     * Gets the provider of an API model id, see {@link #providerOf(String)}.
     *
     * @param modelId the API model id of a request (may be null)
     * @return the provider
     * @throws IllegalStateException if none is registered for it
     */
    public ImageProvider forModel(String modelId) {
        return get(providerOf(modelId));
    }
}
