package tools.moodframe.service;

import tools.moodframe.model.ColorStyle;

import java.io.File;
import java.util.List;

/**
 * Interface for managing color styles.
 *
 * <p>Color styles contain 1-5 hex color codes that are appended to
 * prompts in the format "Color 1 = #XXXXXX, Color 2 = #YYYYYY, ...".</p>
 *
 * @since 1.3.0
 */
public interface ColorStyles {

    /**
     * Gets all available color styles (both default and custom).
     *
     * @return list of all color styles
     */
    List<ColorStyle> getAllStyles();

    /**
     * Gets all default (system) color styles.
     *
     * @return list of default color styles
     */
    List<ColorStyle> getDefaultStyles();

    /**
     * Gets all custom (user-created) color styles.
     *
     * @return list of custom color styles
     */
    List<ColorStyle> getCustomStyles();

    /**
     * Gets a color style by name.
     *
     * @param name the style name
     * @return the color style, or null if not found
     */
    ColorStyle getStyleByName(String name);

    /**
     * Gets the formatted color prompt text for a style.
     *
     * <p>Format: "Color 1 = #XXXXXX, Color 2 = #YYYYYY, ..."</p>
     *
     * @param styleName the style name
     * @return the formatted color prompt, or empty string if not found
     */
    String getColorPromptText(String styleName);

    /**
     * Adds a new custom color style.
     *
     * @param style the color style to add
     * @return true if added successfully
     */
    boolean addCustomStyle(ColorStyle style);

    /**
     * Updates an existing custom color style.
     *
     * @param oldName the current name of the style
     * @param updatedStyle the updated color style
     * @return true if updated successfully
     */
    boolean updateCustomStyle(String oldName, ColorStyle updatedStyle);

    /**
     * Deletes a custom color style.
     *
     * @param name the style name to delete
     * @return true if deleted successfully
     */
    boolean deleteCustomStyle(String name);

    /**
     * Resets all styles to defaults (deletes all custom styles).
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
}
