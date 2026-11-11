package art.moodframe.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Represents a Style Preset item for image generation.
 * 
 * <p>This class holds the name and prompt text for a custom style preset
 * that can be applied to image generation requests. It uses JavaFX properties
 * for UI binding compatibility.</p>
 * 
 * <p><strong>Phase 1 Implementation:</strong> This class serves as both mock data
 * container and future real data model. Currently used with static default styles,
 * will be extended in Phase 2 for persistence.</p>
 * 
 * @since 1.0.0
 */
public class StylePresetItem {
    
    private final StringProperty name;
    private final StringProperty promptText;
    private final boolean isDefault;
    
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
        this.name = new SimpleStringProperty(name);
        this.promptText = new SimpleStringProperty(promptText);
        this.isDefault = isDefault;
    }
    
    // Property getters
    public StringProperty nameProperty() {
        return name;
    }
    
    public StringProperty promptTextProperty() {
        return promptText;
    }
    
    // Value getters/setters
    public String getName() {
        return name.get();
    }
    
    public void setName(String name) {
        this.name.set(name);
    }
    
    public String getPromptText() {
        return promptText.get();
    }
    
    public void setPromptText(String promptText) {
        this.promptText.set(promptText);
    }
    
    public boolean isDefault() {
        return isDefault;
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