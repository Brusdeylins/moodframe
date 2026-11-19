package art.moodframe.service.brainstorm;

import art.moodframe.model.ColorStyle;
import art.moodframe.model.Frame;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.model.SavedImage;
import art.moodframe.service.generation.ModelLabels;

import java.util.List;
import java.util.Set;

/**
 * What the brainstorm assistant may choose from, and the current state of the main window.
 *
 * @param styles all style blocks
 * @param atmospheres all atmosphere blocks
 * @param lights all light blocks
 * @param cameras all camera blocks
 * @param colorStyles all color styles
 * @param moods all moods, which the draft refers to; the assistant looks them up with its tools
 * @param models the models that can be selected in the main window
 * @param currentState text description of the current main window selection, see {@link WindowState#describe()}
 * @param modelNames the name of each model as the main window shows it (parallel to {@code models}); the OpenAI
 *                   models share one placeholder constant and differ only by this name
 * @param locked the building block fields locked in the main window, which keep their choice on "Adopt"
 * @param frames all frames, which the draft refers to; the assistant looks them up with its tools
 * @param savedImages the saved images of the library, which the draft proposes to attach
 * @since 1.7.0
 */
public record BrainstormContext(
        List<PromptBlock> styles,
        List<PromptBlock> atmospheres,
        List<PromptBlock> lights,
        List<PromptBlock> cameras,
        List<ColorStyle> colorStyles,
        List<Mood> moods,
        List<GenerationModel> models,
        String currentState,
        List<String> modelNames,
        Set<Mood.Field> locked,
        List<Frame> frames,
        List<SavedImage> savedImages) {

    /**
     * What the main window has chosen when the brainstorm opens, which the assistant starts from.
     *
     * @param targetModel  the item of the chosen model, as the main window shows it
     * @param resolution   the chosen resolution, null if the model has none to choose
     * @param look         the mood and the building blocks with their locks, one line each, every line starting with
     *                     a line break
     * @param aspectRatio  the chosen aspect ratio
     * @param frame        the name of the chosen frame, null for none
     * @param savedImages  the names of the saved images attached
     * @param prompt       the text in the input field
     */
    public record WindowState(String targetModel, String resolution, String look, String aspectRatio, String frame,
            List<String> savedImages, String prompt) {

        /**
         * Describes the state for the system prompt of the assistant, in English.
         *
         * @return the lines
         */
        public String describe() {
            return "Target model: " + targetModel
                    + (resolution != null ? "\nResolution: " + resolution : "")
                    + look
                    + "\nAspect ratio: " + aspectRatio
                    + (frame != null ? "\nFrame: " + frame : "")
                    + (savedImages.isEmpty() ? "" : "\nAttached saved images: " + String.join(", ", savedImages))
                    + "\nText currently in the input field: " + (prompt.isBlank() ? "(empty)" : prompt.trim());
        }
    }

    /** Creates the context with styles and color styles only, no field locked, the display name of each model. */
    public BrainstormContext(List<PromptBlock> styles, List<ColorStyle> colorStyles, List<GenerationModel> models,
            String currentState) {
        this(styles, List.of(), List.of(), List.of(), colorStyles, List.of(), models, currentState,
                models.stream().map(ModelLabels::displayName).toList(), Set.of(), List.of(), List.of());
    }

    /**
     * The blocks of a field.
     *
     * @param field the style, atmosphere, light or camera field
     * @return the blocks, empty for the color
     */
    public List<PromptBlock> blocks(Mood.Field field) {
        return switch (field) {
            case STYLE -> styles;
            case ATMOSPHERE -> atmospheres;
            case LIGHT -> lights;
            case CAMERA -> cameras;
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
