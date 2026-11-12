package art.moodframe.model;

import java.time.LocalDateTime;

/**
 * A named target aspect ratio and target resolution of an image. A frame names no model, as several providers serve
 * the images: choosing it, and every later change of the model, takes the closest ratio and resolution the chosen
 * model can do ({@link AspectRatios#nearestForItem}, {@link ModelFamilies#nearestForItem}).
 *
 * <p>The standard frames come from code with fixed ids starting with {@value PromptBlock#STANDARD_PREFIX}; a frame
 * of the user is stored in SQLite.</p>
 *
 * @since 2.0.0
 */
public class Frame {

    private String id;
    private String name;
    private String aspectRatio;
    private String resolution;
    private int sortOrder;
    private LocalDateTime deletedAt;

    /**
     * Creates a frame without aspect ratio.
     *
     * @param id the id, null to let the repository assign one
     * @param name the display name
     */
    public Frame(String id, String name) {
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

    /** The target aspect ratio, for example {@code 16:9}, one of {@link AspectRatios#ALL}. */
    public String getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(String aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    /** The target resolution, for example {@code 2K}, one of {@link ModelFamilies#RESOLUTIONS}; null to keep it. */
    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** When the frame was moved to the trash, null while it is in use. */
    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    /** True for a standard frame from code, which can be neither changed nor deleted. */
    public boolean isDefault() {
        return id != null && id.startsWith(PromptBlock.STANDARD_PREFIX);
    }

    @Override
    public String toString() {
        return String.format("Frame{id='%s', name='%s', aspectRatio='%s', resolution='%s'}", id, name, aspectRatio,
                resolution);
    }
}
