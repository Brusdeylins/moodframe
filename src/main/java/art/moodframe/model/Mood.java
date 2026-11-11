package art.moodframe.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * A named combination of building blocks: choosing it sets the fields atmosphere, style, color, light and camera.
 *
 * <p>A mood refers to its blocks by id and adds no prompt text of its own; every field is optional. The standard
 * moods come from code and carry fixed ids starting with {@value PromptBlock#STANDARD_PREFIX}; the moods of the
 * user are stored in SQLite.</p>
 *
 * @since 2.0.0
 */
public class Mood {

    /** The fields a mood sets, each with the column of its block id in the table {@code moods}. */
    public enum Field {
        ATMOSPHERE("atmosphere_id"), STYLE("style_id"), COLOR("color_id"), LIGHT("light_id"), CAMERA("camera_id");

        private final String column;

        Field(String column) {
            this.column = column;
        }

        /** The column of the block id in the table {@code moods}. */
        public String column() {
            return column;
        }
    }

    private String id;
    private String name;
    private final Map<Field, String> references = new EnumMap<>(Field.class);
    private int sortOrder;
    private LocalDateTime deletedAt;

    /**
     * Creates a mood without blocks.
     *
     * @param id the id, null to let the repository assign one
     * @param name the display name
     */
    public Mood(String id, String name) {
        this.id = id;
        this.name = name;
    }

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

    /**
     * The block a field refers to.
     *
     * @param field the field
     * @return the id of the block, null if the mood leaves the field empty
     */
    public String getReference(Field field) {
        return references.get(field);
    }

    /**
     * Sets the block a field refers to.
     *
     * @param field the field
     * @param blockId the id of the block, null or empty to leave the field empty
     */
    public void setReference(Field field, String blockId) {
        if (blockId == null || blockId.isEmpty())
            references.remove(field);
        else
            references.put(field, blockId);
    }

    /** The block ids of the fields the mood sets. */
    public Map<Field, String> getReferences() {
        return Collections.unmodifiableMap(references);
    }

    /** True if the mood refers to no block at all. */
    public boolean isEmpty() {
        return references.isEmpty();
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** When the mood was moved to the trash, null while it is in use. */
    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    /** True for a standard mood from code, which can be neither changed nor deleted. */
    public boolean isDefault() {
        return id != null && id.startsWith(PromptBlock.STANDARD_PREFIX);
    }

    @Override
    public String toString() {
        return String.format("Mood{id='%s', name='%s', references=%s}", id, name, references);
    }
}
