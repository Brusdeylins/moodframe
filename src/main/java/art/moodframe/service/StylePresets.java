package art.moodframe.service;

import art.moodframe.model.StylePreset;
import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Interface for managing style presets.
 * 
 * @since 1.0.0
 */
public interface StylePresets {
    
    /**
     * Gets all available style presets (both default and custom).
     * 
     * @return list of all style presets
     */
    List<StylePreset> getAllStyles();
    
    /**
     * Gets all default (system) style presets.
     * 
     * @return list of default style presets
     */
    List<StylePreset> getDefaultStyles();
    
    /**
     * Gets all custom (user-created) style presets.
     * 
     * @return list of custom style presets
     */
    List<StylePreset> getCustomStyles();
    
    /**
     * Gets a style preset by name.
     * 
     * @param name the style name
     * @return the style preset, or null if not found
     */
    StylePreset getStyleByName(String name);
    
    /**
     * Gets multiple style presets by their names.
     * 
     * @param names collection of style names
     * @return list of found style presets
     */
    List<StylePreset> getStyles(Collection<String> names);
    
    /**
     * Gets the prompt text for a style preset.
     * 
     * @param styleName the style name
     * @return the prompt text, or empty string if not found
     */
    String getStylePromptText(String styleName);
    
    /**
     * Combines prompt texts from multiple style presets.
     * 
     * @param styleNames collection of style names
     * @return combined prompt text
     */
    String combineStylePrompts(Collection<String> styleNames);
    
    /**
     * Adds a new custom style preset.
     * 
     * @param style the style preset to add
     * @return true if added successfully
     */
    boolean addCustomStyle(StylePreset style);
    
    /**
     * Updates an existing custom style preset.
     * 
     * @param oldName the current name of the style
     * @param updatedStyle the updated style preset
     * @return true if updated successfully
     */
    boolean updateCustomStyle(String oldName, StylePreset updatedStyle);
    
    /**
     * Deletes a custom style preset.
     * 
     * @param name the style name to delete
     * @return true if deleted successfully
     */
    boolean deleteCustomStyle(String name);
    
    /**
     * Resets all styles to defaults.
     * 
     * @return number of custom styles deleted
     */
    int resetToDefaults();
    
    /**
     * Checks if a style exists.
     * 
     * @param name the style name
     * @return true if the style exists
     */
    boolean styleExists(String name);
    
    /**
     * Checks if a style is a default style.
     * 
     * @param name the style name
     * @return true if it's a default style
     */
    boolean isDefaultStyle(String name);
    
    /**
     * Exports custom styles to a file.
     * 
     * @param file the target file
     * @return true if exported successfully
     */
    boolean exportStyles(File file);
    
    /**
     * Imports styles from a file.
     * 
     * @param file the source file
     * @param mergeWithExisting whether to merge with existing styles
     * @return number of styles imported
     */
    int importStyles(File file, boolean mergeWithExisting);
    
    /**
     * Exports all styles to a file.
     * 
     * @param file the target file
     * @return true if exported successfully
     */
    boolean exportAllStyles(File file);
}