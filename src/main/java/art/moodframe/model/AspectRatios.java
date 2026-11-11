package art.moodframe.model;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationModel;

/**
 * The aspect ratios the providers accept, as the single source for the parameter panel and the
 * brainstorm assistant.
 *
 * @since 1.7.0
 */
public final class AspectRatios {

    public static final String[] BFL = {"21:9", "16:9", "3:2", "5:4", "1:1", "4:5", "2:3", "9:16", "9:21"};
    /** FLUX 3 Image and Klein (OpenAPI enum of {@code aspect_ratio}, without {@code auto}). */
    public static final String[] FLUX_3 = {"21:9", "2:1", "16:9", "3:2", "7:5", "4:3", "5:4", "1:1", "4:5", "3:4",
        "5:7", "2:3", "9:16", "1:2", "9:21"};
    public static final String[] GEMINI = {"21:9", "16:9", "3:2", "4:3", "5:4", "1:1", "4:5", "3:4", "2:3", "9:16"};
    public static final String[] NANO_BANANA_2 = {"8:1", "4:1", "21:9", "16:9", "3:2", "4:3", "5:4", "1:1", "4:5",
        "3:4", "2:3", "9:16", "1:4", "1:8"};
    public static final String[] OPENAI = {"3:2", "1:1", "2:3"};
    /** Models behind an OpenAI-compatible gateway, whose image sizes are free text. */
    public static final String[] OPENAI_COMPATIBLE = {"16:9", "4:3", "3:2", "1:1", "2:3", "3:4", "9:16"};
    public static final String[] VIDEO = {"16:9", "9:16"};

    private AspectRatios() {
    }

    /**
     * Gets the aspect ratios of an OpenAI image model: the fixed sizes of the OpenAI models, or the free choice
     * for any other model id a gateway lists.
     *
     * @param modelId the model id
     * @return the aspect ratios
     */
    public static String[] forOpenAiModel(String modelId) {
        return GenerationModel.isOpenAiImageModelId(modelId) ? OPENAI : OPENAI_COMPATIBLE;
    }

    /**
     * Returns the aspect ratios valid for a model.
     *
     * @param model the generation model
     * @return the valid ratios
     */
    public static String[] forModel(GenerationModel model) {
        if (model.isVideoModel()) {
            return VIDEO;
        }
        if (model.getProvider() == ApiProvider.OPENAI) {
            return OPENAI;
        }
        if (model.getProvider() == ApiProvider.GOOGLE_GEMINI) {
            return model.supportsExtendedAspectRatios() ? NANO_BANANA_2 : GEMINI;
        }
        return model.isFlux3Model() ? FLUX_3 : BFL;
    }

    /**
     * Returns the aspect ratios valid for an item of the model dropdown.
     *
     * @param item the dropdown item, an OpenAI model or a variant of a model (may be null)
     * @return the valid ratios, those of FLUX for an unknown item
     */
    public static String[] forItem(String item) {
        String openAiId = GenerationModel.openAiModelId(item);
        if (openAiId != null) {
            return forOpenAiModel(openAiId);
        }
        GenerationModel model = GenerationModel.fromLocalizedDisplayName(item);
        return model != null ? forModel(model) : BFL;
    }

    /**
     * Picks the valid aspect ratio closest to a wanted one, comparing width by height on a logarithmic scale, so that
     * a portrait stays a portrait.
     *
     * @param ratio the wanted ratio, for example {@code 4:5}; one that cannot be read counts as {@code 1:1}
     * @param ratios the valid ratios
     * @return the ratio itself if it is valid, else the closest valid one
     */
    public static String nearest(String ratio, String[] ratios) {
        double wanted = Math.log(value(ratio));
        String nearest = ratios[0];
        for (String candidate : ratios) {
            if (Math.abs(Math.log(value(candidate)) - wanted) < Math.abs(Math.log(value(nearest)) - wanted)) {
                nearest = candidate;
            }
        }
        return nearest;
    }

    /** Width by height of a ratio like {@code 16:9}, 1 for a ratio that cannot be read. */
    private static double value(String ratio) {
        String[] parts = ratio != null ? ratio.split(":") : new String[0];
        try {
            double value = parts.length == 2 ? Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]) : 1.0;
            return value > 0 && Double.isFinite(value) ? value : 1.0;
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }
}
