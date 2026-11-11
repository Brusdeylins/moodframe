package tools.moodframe.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Model class representing a style preset for image generation.
 * 
 * <p>This entity is used for persisting custom style presets that users
 * can create, edit, and apply to their image generations. Style presets
 * allow users to save and reuse common prompt modifiers.</p>
 * 
 * <p>Style presets are stored as JSON in the application configuration
 * and can be combined with user prompts during image generation.</p>
 * 
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StylePreset {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("promptText")
    private String promptText;
    
    @JsonProperty("isDefault")
    private boolean isDefault;
    
    @JsonProperty("sortOrder")
    private int sortOrder;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public StylePreset() {
        this.sortOrder = 999; // Default to end of list
    }
    
    /**
     * Creates a new style preset.
     * 
     * @param name the display name of the style
     * @param promptText the prompt modifier text
     * @param isDefault whether this is a system default style
     */
    public StylePreset(String name, String promptText, boolean isDefault) {
        this.name = name;
        this.promptText = promptText;
        this.isDefault = isDefault;
        this.sortOrder = isDefault ? 0 : 999;
    }
    
    /**
     * Creates a custom user style preset.
     * 
     * @param name the display name of the style
     * @param promptText the prompt modifier text
     */
    public StylePreset(String name, String promptText) {
        this(name, promptText, false);
    }
    
    // Getters and Setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getPromptText() {
        return promptText;
    }
    
    public void setPromptText(String promptText) {
        this.promptText = promptText;
    }
    
    public boolean isDefault() {
        return isDefault;
    }
    
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
    
    public int getSortOrder() {
        return sortOrder;
    }
    
    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
    
    /**
     * Converts this preset to a StylePresetItem for UI display.
     * 
     * @return a new StylePresetItem with the same data
     */
    public StylePresetItem toStylePresetItem() {
        return new StylePresetItem(name, promptText, isDefault);
    }
    
    /**
     * Creates a StylePreset from a StylePresetItem.
     * 
     * @param item the UI item to convert
     * @return a new StylePreset with the same data
     */
    public static StylePreset fromStylePresetItem(StylePresetItem item) {
        return new StylePreset(item.getName(), item.getPromptText(), item.isDefault());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        StylePreset that = (StylePreset) obj;
        return name != null && name.equals(that.name);
    }
    
    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
    
    @Override
    public String toString() {
        return String.format("StylePreset{name='%s', isDefault=%s}", name, isDefault);
    }
}