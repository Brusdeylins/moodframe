package art.moodframe.model;

import java.time.LocalDateTime;

/**
 * One text building block of the image prompt: a style, an atmosphere or a light.
 *
 * <p>Each type covers exactly one aspect of the image: the style the medium and its rendering,
 * the atmosphere mood, weather and sense of space, the light its source, direction and quality.
 * The standard blocks come from code and carry fixed ids starting with {@value #STANDARD_PREFIX};
 * the blocks of the user are stored in SQLite.</p>
 *
 * @since 2.0.0
 */
public class PromptBlock {

    /** Prefix of the fixed ids of the standard blocks, for example {@code std:style:watercolor}. */
    public static final String STANDARD_PREFIX = "std:";

    /** Group of the photographic styles. */
    public static final String GROUP_PHOTO = "Photo";

    /** Group of the painted, drawn and digital styles. */
    public static final String GROUP_ART = "Art";

    /** Group of the layout styles (infographics, logo, comic); light and camera do not apply to them. */
    public static final String GROUP_LAYOUT = "Layout";

    /** The aspect of the image a block describes. */
    public enum Type {
        STYLE, ATMOSPHERE, LIGHT
    }

    private String id;
    private Type type;
    private String name;
    private String text;
    private String group;
    private boolean photographic;
    private String params;
    private int sortOrder;
    private LocalDateTime deletedAt;

    /**
     * Creates a block.
     *
     * @param id the id, null to let the repository assign one
     * @param type the aspect of the image the block describes
     * @param name the display name
     * @param text the prompt fragment
     * @param group the group within the type, null for none
     * @param photographic whether the block describes a photograph (decides between "Camera" and "Framing")
     */
    public PromptBlock(String id, Type type, String name, String text, String group, boolean photographic) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.text = text;
        this.group = group;
        this.photographic = photographic;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Type getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public boolean isPhotographic() {
        return photographic;
    }

    public void setPhotographic(boolean photographic) {
        this.photographic = photographic;
    }

    /** Additional parameters of the block as JSON text, null for none. */
    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** When the block was moved to the trash, null while it is in use. */
    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    /** True for a standard block from code, which can be neither changed nor deleted. */
    public boolean isDefault() {
        return id != null && id.startsWith(STANDARD_PREFIX);
    }

    /** True for a style of the group "Layout". */
    public boolean isLayout() {
        return GROUP_LAYOUT.equals(group);
    }

    @Override
    public String toString() {
        return String.format("PromptBlock{id='%s', type=%s, name='%s'}", id, type, name);
    }
}
