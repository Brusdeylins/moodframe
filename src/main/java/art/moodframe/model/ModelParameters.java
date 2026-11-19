package art.moodframe.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The parameters the models take besides prompt, target and reference images, as the main window offers them: the
 * one table the MCP schema ({@code get_capabilities}), the MCP validation ({@code generate}), the providers and the
 * main window share. A default here is the one of the provider; the settings may hold another (safety tolerance,
 * guidance and steps), which the callers take instead.
 *
 * @since 2.1.3
 */
public final class ModelParameters {

    /** A fixed seed of FLUX.2 and FLUX 3 [klein]; random without. */
    public static final ModelParameter SEED = new ModelParameter("seed", ModelParameter.Type.INTEGER, null, null,
            null, null, List.of(), "Fixed seed; random without");

    /** The safety tolerance of the BFL models, 0 strict to 5 permissive. */
    public static final ModelParameter SAFETY_TOLERANCE = new ModelParameter("safetyTolerance",
            ModelParameter.Type.INTEGER, 0, 5, 2, 1, List.of(), "0 strict to 5 permissive");

    /** Whether BFL enhances the prompt (FLUX.2 except [klein]). */
    public static final ModelParameter PROMPT_UPSAMPLING = new ModelParameter("promptUpsampling",
            ModelParameter.Type.BOOLEAN, null, null, null, null, List.of(), "BFL enhances the prompt");

    /** The prompt adherence of FLUX.2 [flex], higher is stricter. */
    public static final ModelParameter GUIDANCE = new ModelParameter("guidance", ModelParameter.Type.NUMBER, 1.5,
            10.0, 5.0, 0.5, List.of(), null);

    /** The inference steps of FLUX.2 [flex], more give more detail. */
    public static final ModelParameter STEPS = new ModelParameter("steps", ModelParameter.Type.INTEGER, 1, 50, 50,
            1, List.of(), "Inference steps");

    /** The thinking level of Nano Banana 2 and Lite. */
    public static final ModelParameter THINKING_LEVEL = new ModelParameter("thinkingLevel",
            ModelParameter.Type.CHOICE, null, null, null, null, List.of("minimal", "high"),
            "Thinking before the image");

    /** Whether Gemini grounds the image in a Google Search. */
    public static final ModelParameter GOOGLE_SEARCH = new ModelParameter("googleSearch",
            ModelParameter.Type.BOOLEAN, null, null, null, null, List.of(), "Grounds the image in a Google Search");

    /** The name of the length of a video in seconds, whose choices depend on the model. */
    public static final String VIDEO_SECONDS = "videoSeconds";

    /** The video length without a choice. */
    public static final int DEFAULT_VIDEO_SECONDS = 8;

    private ModelParameters() {
    }

    /**
     * Gets the parameters a model takes, in the order the MCP tools list them.
     *
     * @param model the model
     * @return the parameters, empty for none
     */
    public static List<ModelParameter> of(GenerationModel model) {
        List<ModelParameter> parameters = new ArrayList<>();
        if (model.isFlux2Model() || model.isFlux3Klein())
            parameters.add(SEED);
        if (model.supportsSafetyTolerance())
            parameters.add(SAFETY_TOLERANCE);
        if (model.isFlux2Model() && !model.isFlux2Klein())
            parameters.add(PROMPT_UPSAMPLING);
        if (model.isFlux2Flex()) {
            parameters.add(GUIDANCE);
            parameters.add(STEPS);
        }
        if (model.supportsThinkingLevel())
            parameters.add(THINKING_LEVEL);
        if (model.supportsGoogleSearch())
            parameters.add(GOOGLE_SEARCH);
        if (model.isVideoModel())
            parameters.add(videoSeconds(model));
        return List.copyOf(parameters);
    }

    /**
     * Gets a parameter of a model by its name.
     *
     * @param model the model
     * @param name  the name, e.g. {@code guidance}
     * @return the parameter, null if the model does not take it
     */
    public static ModelParameter find(GenerationModel model, String name) {
        return of(model).stream().filter((ModelParameter parameter) -> parameter.name().equals(name)).findFirst()
                .orElse(null);
    }

    /**
     * Gets every parameter any model takes, each name once, in the order of {@link #of(GenerationModel)}.
     *
     * @return the parameters, the video length with the choices of the first video model
     */
    public static List<ModelParameter> all() {
        GenerationModel video = null;
        for (GenerationModel model : GenerationModel.values()) {
            if (model.isVideoModel()) {
                video = model;
                break;
            }
        }
        List<ModelParameter> all = new ArrayList<>(List.of(SEED, SAFETY_TOLERANCE, PROMPT_UPSAMPLING, GUIDANCE,
                STEPS, THINKING_LEVEL, GOOGLE_SEARCH));
        if (video != null)
            all.add(videoSeconds(video));
        return List.copyOf(all);
    }

    /*  the length of a video out of the durations of the model  */
    private static ModelParameter videoSeconds(GenerationModel model) {
        return new ModelParameter(VIDEO_SECONDS, ModelParameter.Type.INTEGER, null, null, DEFAULT_VIDEO_SECONDS, null,
                model.getVideoDurationChoices(), null);
    }
}
