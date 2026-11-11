package art.moodframe.service;

import art.moodframe.model.ColorStyle;

import java.io.File;
import java.util.List;

/**
 * Interface for managing color styles.
 *
 * <p>Color styles contain 1-5 hex color codes that are appended to
 * prompts as their "Color palette" line.</p>
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
     * Gets the palette text of the prompt for a style.
     *
     * <p>Format: see {@link art.moodframe.model.ColorStyle#toPromptString()}</p>
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
     * Adds a palette as a new custom color style, e.g. a copy of a default one.
     *
     * @param style the palette; its ID is replaced and a taken name gets the next free counter
     * @return true if added successfully
     */
    boolean addCopy(ColorStyle style);

    /**
     * Puts the custom color styles into the given order.
     *
     * @param ids the IDs of the custom color styles in their new order
     * @return true if all of them were updated
     */
    boolean reorderCustomStyles(List<String> ids);

    /**
     * Moves a custom color style to the trash.
     *
     * @param name the style name to delete
     * @return true if it was moved to the trash
     */
    boolean deleteCustomStyle(String name);

    /**
     * Gets the custom color styles in the trash.
     *
     * @return the color styles in the trash, the latest deletion first
     */
    List<ColorStyle> getDeletedStyles();

    /**
     * Takes a custom color style out of the trash.
     *
     * @param id the ID of the color style
     * @return true if it is back in use
     */
    boolean restoreStyle(String id);

    /**
     * Removes a custom color style for good.
     *
     * @param id the ID of the color style
     * @return true if it is gone
     */
    boolean deleteStylePermanently(String id);

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

    /**
     * Adds palettes behind the custom styles.
     *
     * @param styles the palettes; a taken ID gets a new one, a taken name the next free counter
     * @return number of styles imported
     */
    int importStyles(List<ColorStyle> styles);
}
