package art.moodframe.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A named setting of how an image is produced: choosing it sets model, resolution, aspect ratio, image count and,
 * for FLUX.2 [flex], guidance and steps. Seed and safety tolerance stay as they are.
 *
 * <p>The model is kept as API id plus resolution, so display names and prices may change; an OpenAI model is kept
 * as {@value #OPENAI_MODEL} plus its id. The standard frames come from code with fixed ids starting with
 * {@value PromptBlock#STANDARD_PREFIX} and name several models in the order of preference; a frame of the user is
 * stored in SQLite and names one model.</p>
 *
 * @since 2.0.0
 */
public class Frame {

    /** Prefix of the model id of an OpenAI model; without an id after it, it stands for any OpenAI model. */
    public static final String OPENAI_MODEL = "openai:";

    private String id;
    private String name;
    private List<String> models = List.of();
    private String resolution;
    private String aspectRatio;
    private int imageCount = 1;
    private Double guidance;
    private Integer steps;
    private int sortOrder;
    private LocalDateTime deletedAt;

    /**
     * Creates a frame without model.
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

    /** The model ids in the order of preference: the first available one is taken. */
    public List<String> getModels() {
        return models;
    }

    public void setModels(List<String> models) {
        this.models = List.copyOf(models);
    }

    /** The model id of a frame of the user, the preferred one of a standard frame; null without model. */
    public String getModel() {
        return models.isEmpty() ? null : models.get(0);
    }

    /**
     * Sets the one model of the frame.
     *
     * @param model the model id, null or empty for none
     */
    public void setModel(String model) {
        this.models = model == null || model.isEmpty() ? List.of() : List.of(model);
    }

    /** The resolution of the model, for example {@code 2K}; null for a model without resolution choice. */
    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    /** The aspect ratio, for example {@code 16:9}. */
    public String getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(String aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    public int getImageCount() {
        return imageCount;
    }

    public void setImageCount(int imageCount) {
        this.imageCount = imageCount;
    }

    /** The guidance of FLUX.2 [flex], null to leave it as it is. */
    public Double getGuidance() {
        return guidance;
    }

    public void setGuidance(Double guidance) {
        this.guidance = guidance;
    }

    /** The steps of FLUX.2 [flex], null to leave them as they are. */
    public Integer getSteps() {
        return steps;
    }

    public void setSteps(Integer steps) {
        this.steps = steps;
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
        return String.format("Frame{id='%s', name='%s', models=%s, resolution='%s', aspectRatio='%s', imageCount=%d}",
                id, name, models, resolution, aspectRatio, imageCount);
    }
}
