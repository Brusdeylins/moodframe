package art.moodframe.service.generation;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationModel;
import art.moodframe.util.I18nManager;

import java.util.List;
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
     * @return the localized display name, {@link GenerationModel#OPENAI_PREFIX} + id for an OpenAI model, null
     *         without id
     */
    public static String itemOf(String id) {
        GenerationModel model = GenerationModel.fromId(id);
        return model == null ? null
                : model == GenerationModel.OPENAI_IMAGE ? GenerationModel.OPENAI_PREFIX + id : displayName(model);
    }

    /**
     * Gets the dropdown items of the models to choose from: the selectable models of the usable providers, then the
     * models of the OpenAI-compatible gateway.
     *
     * @param bfl whether BFL is enabled and has a key
     * @param gemini whether Gemini is enabled and has a key
     * @param openAiIds the model ids of the gateway, empty while it is disabled or has no key
     * @return the items, in the order of the dropdown
     */
    public static List<String> availableItems(boolean bfl, boolean gemini, List<String> openAiIds) {
        return Stream.concat(GenerationModel.selectable().stream()
                .filter((GenerationModel model) -> model.getProvider() == ApiProvider.BFL ? bfl
                        : model.getProvider() == ApiProvider.GOOGLE_GEMINI && gemini)
                .map(ModelLabels::displayName),
                openAiIds.stream().map((String id) -> GenerationModel.OPENAI_PREFIX + id)).toList();
    }
}
