package art.moodframe.ui.component;

import art.moodframe.model.ColorStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a Color Style item for image generation UI display.
 *
 * <p>This class holds the name and color codes of a color style ({@link ColorStyle}) for an entry of the palette
 * selection of the main window.</p>
 *
 * <p>A color style contains 1-5 hex color codes that are displayed
 * as colored boxes in the UI and appended to prompts.</p>
 *
 * @since 1.3.0
 */
public class ColorStyleItem {

    private final String id;
    private final String name;
    private final List<String> colors;
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
        this(null, name, colors, isDefault);
    }

    /**
     * Creates a new ColorStyleItem showing a stored color style.
     *
     * @param id the id of the color style, null for an item without one ("None")
     * @param name the display name of the color style
     * @param colors the list of hex color codes (1-5 colors)
     * @param isDefault true if this is a default style (cannot be deleted)
     */
    public ColorStyleItem(String id, String name, List<String> colors, boolean isDefault) {
        this.id = id;
        this.name = name;
        this.colors = Collections.unmodifiableList(new ArrayList<>(colors != null ? colors : List.of()));
        this.isDefault = isDefault;
    }

    /**
     * Creates the item showing a color style.
     *
     * @param style the color style
     * @return a new ColorStyleItem with the same data
     */
    public static ColorStyleItem of(ColorStyle style) {
        return new ColorStyleItem(style.getId(), style.getName(), style.getColors(), style.isDefault());
    }

    /**
     * Creates a ColorStyle with the data of this item.
     *
     * @return a new ColorStyle with the same data
     */
    public ColorStyle toColorStyle() {
        return new ColorStyle(getName(), new ArrayList<>(getColors()), isDefault());
    }

    /** The id of the color style shown, null for an item without one ("None"). */
    public String getId() {
        return id;
    }

    /**
     * Gets the colors of the style.
     *
     * @return the hex color codes, unmodifiable
     */
    public List<String> getColors() {
        return colors;
    }

    public String getName() {
        return name;
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
