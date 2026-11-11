package art.moodframe.service;

import art.moodframe.model.ColorStyle;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.PromptBlock;

import java.util.List;

/**
 * What the brainstorm assistant may choose from, and the current state of the main window.
 *
 * @param styles all style blocks
 * @param colorStyles all color styles
 * @param models the models that can be selected in the main window
 * @param currentState text description of the current main window selection
 * @param modelNames the name of each model as the main window shows it (parallel to {@code models}); the OpenAI
 *                   models share one placeholder constant and differ only by this name
 * @since 1.7.0
 */
public record BrainstormContext(
        List<PromptBlock> styles,
        List<ColorStyle> colorStyles,
        List<GenerationModel> models,
        String currentState,
        List<String> modelNames) {

    /** Creates the context with the localized display name of each model as its name. */
    public BrainstormContext(List<PromptBlock> styles, List<ColorStyle> colorStyles, List<GenerationModel> models,
            String currentState) {
        this(styles, colorStyles, models, currentState,
                models.stream().map(GenerationModel::getLocalizedDisplayName).toList());
    }
}
