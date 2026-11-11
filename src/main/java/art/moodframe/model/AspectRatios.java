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
}
