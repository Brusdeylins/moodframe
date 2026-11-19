package art.moodframe.ui.component;

import art.moodframe.model.PromptBlock;

/**
 * Represents a Style Preset item for image generation.
 *
 * <p>This class holds the name and prompt text of a building block ({@link PromptBlock}) for an entry of the
 * block selections of the main window.</p>
 *
 * @since 1.0.0
 */
public class StylePresetItem {

    private final String name;
    private final String promptText;
    private final boolean isDefault;
    private final PromptBlock block;

    /**
     * Creates a new StylePresetItem with the specified name and prompt.
     *
     * @param name the display name of the style
     * @param promptText the prompt text to append/apply for this style
     */
    public StylePresetItem(String name, String promptText) {
        this(name, promptText, false);
    }

    /**
     * Creates a new StylePresetItem with the specified name, prompt and default flag.
     *
     * @param name the display name of the style
     * @param promptText the prompt text to append/apply for this style
     * @param isDefault true if this is a default style (cannot be deleted)
     */
    public StylePresetItem(String name, String promptText, boolean isDefault) {
        this.name = name;
        this.promptText = promptText;
        this.isDefault = isDefault;
        this.block = null;
    }

    /**
     * Creates a new StylePresetItem showing a style building block.
     *
     * @param block the style block
     */
    public StylePresetItem(PromptBlock block) {
        this.name = block.getName();
        this.promptText = block.getText();
        this.isDefault = block.isDefault();
        this.block = block;
    }

    public String getName() {
        return name;
    }

    public String getPromptText() {
        return promptText;
    }

    public boolean isDefault() {
        return isDefault;
    }

    /** The style block shown, null for the "None" item. */
    public PromptBlock getBlock() {
        return block;
    }

    @Override
    public String toString() {
        return getName(); // For ListView display
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        StylePresetItem that = (StylePresetItem) obj;
        return getName().equals(that.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }
}
