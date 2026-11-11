package art.moodframe.service;

import art.moodframe.model.ColorStyle;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;

import java.util.List;
import java.util.Set;

/**
 * What the brainstorm assistant may choose from, and the current state of the main window.
 *
 * @param styles all style blocks
 * @param atmospheres all atmosphere blocks
 * @param lights all light blocks
 * @param colorStyles all color styles
 * @param moods all moods
 * @param models the models that can be selected in the main window
 * @param currentState text description of the current main window selection
 * @param modelNames the name of each model as the main window shows it (parallel to {@code models}); the OpenAI
 *                   models share one placeholder constant and differ only by this name
 * @param locked the building block fields locked in the main window, which keep their choice on "Adopt"
 * @since 1.7.0
 */
public record BrainstormContext(
        List<PromptBlock> styles,
        List<PromptBlock> atmospheres,
        List<PromptBlock> lights,
        List<ColorStyle> colorStyles,
        List<Mood> moods,
        List<GenerationModel> models,
        String currentState,
        List<String> modelNames,
        Set<Mood.Field> locked) {

    /** Creates the context with styles and color styles only, no field locked, the display name of each model. */
    public BrainstormContext(List<PromptBlock> styles, List<ColorStyle> colorStyles, List<GenerationModel> models,
            String currentState) {
        this(styles, List.of(), List.of(), colorStyles, List.of(), models, currentState,
                models.stream().map(GenerationModel::getLocalizedDisplayName).toList(), Set.of());
    }

    /**
     * The text blocks of a field.
     *
     * @param field the style, atmosphere or light field
     * @return the blocks, empty for the color and the camera
     */
    public List<PromptBlock> blocks(Mood.Field field) {
        return switch (field) {
            case STYLE -> styles;
            case ATMOSPHERE -> atmospheres;
            case LIGHT -> lights;
            default -> List.of();
        };
    }

    /**
     * The name of the entry of a field with this id, a text block or a palette.
     *
     * @param field the field
     * @param id the id of the entry
     * @return the name, null if no entry of the field has the id
     */
    public String nameOf(Mood.Field field, String id) {
        if (id == null)
            return null;
        if (field == Mood.Field.COLOR)
            return colorStyles.stream().filter((ColorStyle palette) -> id.equals(palette.getId()))
                    .map(ColorStyle::getName).findFirst().orElse(null);
        return blocks(field).stream().filter((PromptBlock block) -> id.equals(block.getId()))
                .map(PromptBlock::getName).findFirst().orElse(null);
    }

    /**
     * The id of the entry of a field with this name, a text block or a palette.
     *
     * @param field the field
     * @param name the name of the entry
     * @return the id, null if no entry of the field has the name
     */
    public String idOf(Mood.Field field, String name) {
        if (name == null)
            return null;
        if (field == Mood.Field.COLOR)
            return colorStyles.stream().filter((ColorStyle palette) -> name.equals(palette.getName()))
                    .map(ColorStyle::getId).findFirst().orElse(null);
        return blocks(field).stream().filter((PromptBlock block) -> name.equals(block.getName()))
                .map(PromptBlock::getId).findFirst().orElse(null);
    }
}
