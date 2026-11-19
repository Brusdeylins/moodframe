package art.moodframe.service.generation;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationModel;
import art.moodframe.service.provider.ImageProvider;
import art.moodframe.service.provider.ProviderRegistry;
import art.moodframe.util.I18nManager;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The model dropdown items in the current language: the localized display names of the models and the items of the
 * OpenAI models. The model itself knows only the i18n key ({@link GenerationModel#displayNameKey()}); an item in
 * any language leads back to its model with {@link GenerationModel#fromLocalizedDisplayName(String)}.
 *
 * @since 2.1.3
 */
public final class ModelLabels {

    private ModelLabels() {
    }

    /**
     * Gets the localized display name of a model.
     *
     * @param model the model
     * @return the display name in the current language, the display name for a model without localized name
     */
    public static String displayName(GenerationModel model) {
        String key = model.displayNameKey();
        return key == null ? model.getDisplayName() : I18nManager.getText(key);
    }

    /**
     * Gets the dropdown item of a fixed id (see {@link GenerationModel#idOf(String)}), as shown in the current
     * language.
     *
     * @param id the id (may be null)
     * @return the localized display name of a constant, {@link GenerationModel#OPENAI_PREFIX} + id for a model of the
     *         gateway, null without id
     */
    public static String itemOf(String id) {
        if (id == null)
            return null;
        GenerationModel model = GenerationModel.constantOf(id);
        return model != null ? displayName(model) : GenerationModel.OPENAI_PREFIX + id;
    }

    /**
     * Gets the name of the model of a chat entry in the current language, see {@link GenerationModel#fromEntryModel}.
     *
     * @param stored the model the entry stores (may be null)
     * @return the localized display name of its model, else the stored text (the item of a gateway model, the name of
     *         an older entry whose model is gone), null without model
     */
    public static String entryName(String stored) {
        GenerationModel model = GenerationModel.fromEntryModel(stored);
        return model != null ? displayName(model) : stored;
    }

    /**
     * Gets the dropdown items of the models to choose from: the selectable constants of the usable providers of the
     * registry, then the models these providers list beyond the constants ({@link ImageProvider#listedModels()},
     * today those of the OpenAI-compatible gateway).
     *
     * @param usable    whether a provider is enabled and has a key
     * @param providers the registered providers
     * @return the items, in the order of the dropdown
     */
    public static List<String> availableItems(Predicate<ApiProvider> usable, ProviderRegistry providers) {
        Predicate<ApiProvider> offered = (ApiProvider provider) -> providers.ids().contains(provider)
                && usable.test(provider);
        return Stream.concat(GenerationModel.selectable().stream()
                .filter((GenerationModel model) -> offered.test(model.getProvider()))
                .map(ModelLabels::displayName),
                providers.all().stream().filter((ImageProvider provider) -> offered.test(provider.getProvider()))
                        .flatMap((ImageProvider provider) -> provider.listedModels().stream())
                        .map(ModelLabels::itemOf)).toList();
    }
}
