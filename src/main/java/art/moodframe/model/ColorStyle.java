package art.moodframe.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Model class representing a color style for image generation.
 *
 * <p>A color style contains 1-5 hex color codes that are appended to the
 * generation prompt as its "Color palette" line (see {@link #toPromptString()}).</p>
 *
 * <p>Color styles can be combined with style presets to provide both
 * artistic direction and color palette guidance to the AI model.</p>
 *
 * @since 1.3.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ColorStyle {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("colors")
    private List<String> colors;

    @JsonProperty("isDefault")
    private boolean isDefault;

    @JsonProperty("sortOrder")
    private int sortOrder;

    /**
     * Default constructor for JSON deserialization.
     */
    public ColorStyle() {
        this.colors = new ArrayList<>();
        this.sortOrder = 999;
    }

    /**
     * Creates a new color style.
     *
     * @param name the display name of the color style
     * @param colors list of hex color codes (1-5 colors, e.g., "#48BDFF")
     * @param isDefault whether this is a system default style
     */
    public ColorStyle(String name, List<String> colors, boolean isDefault) {
        this.name = name;
        this.colors = colors != null ? new ArrayList<>(colors) : new ArrayList<>();
        this.isDefault = isDefault;
        this.sortOrder = isDefault ? 0 : 999;
    }

    /**
     * Creates a custom user color style.
     *
     * @param name the display name of the color style
     * @param colors list of hex color codes
     */
    public ColorStyle(String name, List<String> colors) {
        this(name, colors, false);
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

    public List<String> getColors() {
        return colors;
    }

    public void setColors(List<String> colors) {
        this.colors = colors != null ? new ArrayList<>(colors) : new ArrayList<>();
    }

    /**
     * Gets the color at a specific index (0-4).
     *
     * @param index the color index (0-4)
     * @return the hex color code, or null if not set
     */
    public String getColor(int index) {
        if (index >= 0 && index < colors.size()) {
            return colors.get(index);
        }
        return null;
    }

    /**
     * Sets a color at a specific index (0-4).
     *
     * @param index the color index (0-4)
     * @param color the hex color code
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
     * Converts the colors to the palette text of the prompt (behind "Color palette: "), phrased positively.
     *
     * <p>Format: "the image is composed predominantly of these colors: color 1 hex #XXXXXX,
     * color 2 hex #YYYYYY. The palette takes precedence over ... no written color codes, color names or swatches."</p>
     *
     * @return the palette text, or empty string if no colors defined
     */
    public String toPromptString() {
        if (colors == null || colors.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int colorNum = 1;

        for (String color : colors) {
            if (color != null && !color.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append("color ").append(colorNum).append(" hex ").append(color);
                colorNum++;
            }
        }

        if (sb.length() == 0) {
            return "";
        }

        /*  Hex colors work best bound to objects; the image must not show the codes themselves  */
        return "the image is composed predominantly of these colors: " + sb
                + ". The palette takes precedence over the color of the light. The colors appear only as colors"
                + " of objects, surfaces and light; the image contains no written color codes, color names or swatches.";
    }

    /**
     * Converts this color style to a ColorStyleItem for UI display.
     *
     * @return a new ColorStyleItem with the same data
     */
    public ColorStyleItem toColorStyleItem() {
        return new ColorStyleItem(name, colors, isDefault);
    }

    /**
     * Creates a ColorStyle from a ColorStyleItem.
     *
     * @param item the UI item to convert
     * @return a new ColorStyle with the same data
     */
    public static ColorStyle fromColorStyleItem(ColorStyleItem item) {
        return new ColorStyle(item.getName(), new ArrayList<>(item.getColors()), item.isDefault());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        ColorStyle that = (ColorStyle) obj;
        return name != null && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    @Override
    public String toString() {
        return String.format("ColorStyle{name='%s', colors=%s, isDefault=%s}",
            name, colors, isDefault);
    }
}
