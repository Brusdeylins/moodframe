package art.moodframe.service;

import art.moodframe.model.ColorStyle;
import art.moodframe.model.PromptBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Composes the final prompt from the subject of the user and the selected building blocks.
 *
 * <p>One labeled line per block, the medium first (BFL "Building a Good Prompt": medium, subject,
 * environment, lighting, framing); empty blocks are left out:</p>
 * <pre>
 * Image style: &lt;style&gt;.
 * &lt;subject&gt;
 * Atmosphere: &lt;atmosphere&gt;.
 * Lighting: &lt;light&gt;.
 * Camera: &lt;camera&gt;.            (Framing: for a style that is not photographic)
 * Color palette: &lt;palette&gt;
 * </pre>
 *
 * <p>Light and camera do not apply to layout styles; these get a rule for their text instead.</p>
 *
 * @since 2.0.0
 */
public class PromptComposer {

    /** Appended once to the style line of every layout style. */
    public static final String LAYOUT_TEXT_RULE =
            "All text is short, correctly spelled and clearly legible, set in one consistent typeface.";

    /**
     * The selected building blocks; every part may be null.
     *
     * @param style the style
     * @param atmosphere the atmosphere
     * @param light the light
     * @param camera the camera text
     * @param palette the color palette
     */
    public record Selection(PromptBlock style, PromptBlock atmosphere, PromptBlock light, String camera,
            ColorStyle palette) {
    }

    private final int maxPromptLength;

    /**
     * Creates a composer for a model.
     *
     * @param maxPromptLength the maximum number of characters the model accepts for the whole prompt
     */
    public PromptComposer(int maxPromptLength) {
        this.maxPromptLength = maxPromptLength;
    }

    /**
     * Composes the prompt.
     *
     * @param subject the prompt of the user
     * @param content additional content of the subject (for example a video analysis), null for none
     * @param selection the selected building blocks
     * @return the prompt, empty if the subject is empty
     */
    public String compose(String subject, String content, Selection selection) {
        if (subject == null || subject.isBlank())
            return "";

        PromptBlock style = selection.style();
        boolean layout = style != null && style.isLayout();
        List<String> lines = new ArrayList<>();

        if (style != null && !isBlank(style.getText()))
            lines.add("Image style: " + sentence(style.getText()) + (layout ? " " + LAYOUT_TEXT_RULE : ""));
        lines.add(subject.trim());
        if (!isBlank(content))
            lines.add("Content: " + content.trim());
        if (selection.atmosphere() != null && !isBlank(selection.atmosphere().getText()))
            lines.add("Atmosphere: " + sentence(selection.atmosphere().getText()));
        if (!layout && selection.light() != null && !isBlank(selection.light().getText()))
            lines.add("Lighting: " + sentence(selection.light().getText()));
        if (!layout && !isBlank(selection.camera()))
            lines.add((style == null || style.isPhotographic() ? "Camera: " : "Framing: ")
                    + sentence(selection.camera()));
        if (selection.palette() != null && !selection.palette().toPromptString().isEmpty())
            lines.add("Color palette: " + selection.palette().toPromptString());
        return String.join("\n", lines);
    }

    /**
     * Checks the whole prompt against the length limit of the model.
     *
     * @param prompt the composed prompt
     * @return true if the model accepts it
     */
    public boolean fits(String prompt) {
        return prompt.length() <= maxPromptLength;
    }

    /** The text trimmed and ending with a period, unless it already ends with a sentence mark. */
    private static String sentence(String text) {
        String trimmed = text.trim();
        return ".!?".indexOf(trimmed.charAt(trimmed.length() - 1)) >= 0 ? trimmed : trimmed + ".";
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
