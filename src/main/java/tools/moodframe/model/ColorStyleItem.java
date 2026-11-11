package tools.moodframe.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

/**
 * Represents a Color Style item for image generation UI display.
 *
 * <p>This class holds the name and color codes for a color style
 * that can be applied to image generation requests. It uses JavaFX properties
 * for UI binding compatibility.</p>
 *
 * <p>A color style contains 1-5 hex color codes that are displayed
 * as colored boxes in the UI and appended to prompts.</p>
 *
 * @since 1.3.0
 */
public class ColorStyleItem {

    private final StringProperty name;
    private final ObservableList<String> colors;
    private final boolean isDefault;

    /**
     * Creates a new ColorStyleItem with the specified name and colors.
     *
     * @param name the display name of the color style
     * @param colors the list of hex color codes (1-5 colors)
     */
    public ColorStyleItem(String name, List<String> colors) {
        this(name, colors, false);
    }

    /**
     * Creates a new ColorStyleItem with the specified name, colors and default flag.
     *
     * @param name the display name of the color style
     * @param colors the list of hex color codes (1-5 colors)
     * @param isDefault true if this is a default style (cannot be deleted)
     */
    public ColorStyleItem(String name, List<String> colors, boolean isDefault) {
        this.name = new SimpleStringProperty(name);
        this.colors = FXCollections.observableArrayList(colors != null ? colors : List.of());
        this.isDefault = isDefault;
    }

    // Property getters
    public StringProperty nameProperty() {
        return name;
    }

    /**
     * Gets the observable list of colors for UI binding.
     *
     * @return the observable list of hex color codes
     */
    public ObservableList<String> getColors() {
        return colors;
    }

    // Value getters/setters
    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    /**
     * Gets the color at a specific index (0-4).
     *
     * @param index the color index (0-4)
     * @return the hex color code, or null if not set
     */
    public String getColor(int index) {
        if (index >= 0 && index < colors.size()) {
            String color = colors.get(index);
            return (color != null && !color.isEmpty()) ? color : null;
        }
        return null;
    }

    /**
     * Checks if a color slot is active (has a color defined).
     *
     * @param index the color index (0-4)
     * @return true if the slot has a valid color
     */
    public boolean hasColor(int index) {
        return getColor(index) != null;
    }

    /**
     * Sets a color at a specific index (0-4).
     *
     * @param index the color index (0-4)
     * @param color the hex color code, or null to clear
     */
    public void setColor(int index, String color) {
        if (index < 0 || index > 4) {
            return;
        }
        // Expand list if necessary
        while (colors.size() <= index) {
            colors.add(null);
        }
        colors.set(index, color);
    }

    /**
     * Gets the number of defined colors.
     *
     * @return the count of non-null colors
     */
    public int getColorCount() {
        int count = 0;
        for (String color : colors) {
            if (color != null && !color.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public boolean isDefault() {
        return isDefault;
    }

    @Override
    public String toString() {
        return getName(); // For ChoiceBox display
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ColorStyleItem that = (ColorStyleItem) obj;
        return getName() != null && getName().equals(that.getName());
    }

    @Override
    public int hashCode() {
        return getName() != null ? getName().hashCode() : 0;
    }
}
