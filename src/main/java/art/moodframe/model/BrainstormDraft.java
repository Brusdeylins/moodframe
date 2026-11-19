package art.moodframe.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The current result of a brainstorm session: the prompt and the suggested settings.
 *
 * <p>All settings are optional ({@code null} or empty means "no suggestion"). The look comes from a mood and the
 * building blocks of {@link #FIELDS}: each is an entry of the lists or a new block the AI proposes, a new camera with
 * its settings instead of a text. A frame of the list sets aspect ratio and resolution, saved images of the library
 * are attached. Its JSON form, which the language model writes and the session stores, is
 * {@code service.brainstorm.BrainstormJson}.</p>
 *
 * @since 1.7.0
 */
public class BrainstormDraft {

    /** The building blocks the brainstorm suggests, each under its lower-case name as key. */
    public static final List<Mood.Field> FIELDS = List.of(Mood.Field.STYLE, Mood.Field.ATMOSPHERE, Mood.Field.LIGHT,
            Mood.Field.COLOR, Mood.Field.CAMERA);

    /**
     * A building block of the draft: an entry of the lists, referred to by id or name, or a new block the AI proposes
     * with its text, for a palette with its colors, for a camera with its settings.
     *
     * @param id the id of the entry, null for a new block or a name that is not resolved yet
     * @param name the name of the entry, or the proposed name of a new block
     * @param text the prompt fragment of a new text block, null otherwise
     * @param colors the colors of a new palette as {@code #RRGGBB}, empty otherwise
     * @param settings the settings of a new camera, null otherwise
     * @param isNew whether the AI proposes the block as a new entry
     */
    public record Block(String id, String name, String text, List<String> colors, CameraSettings settings,
            boolean isNew) {

        /** Creates a block without camera settings. */
        public Block(String id, String name, String text, List<String> colors, boolean isNew) {
            this(id, name, text, colors, null, isNew);
        }

        /**
         * The same block, referring to its entry by id.
         *
         * @param entryId the id of the entry
         * @return the block with the id
         */
        public Block withId(String entryId) {
            return new Block(entryId, name, text, colors, settings, isNew);
        }

        /**
         * The entry of a list a block of the draft names, by id, else by name.
         *
         * @param reference the block, may be null
         * @param entries   the entries of the list
         * @param idOf      the id of an entry
         * @param nameOf    the name of an entry
         * @param <T>       the type of the entries
         * @return the entry, null if the block is null or names none of them
         */
        public static <T> T find(Block reference, List<T> entries, Function<T, String> idOf,
                Function<T, String> nameOf) {
            if (reference == null)
                return null;
            return entries.stream().filter((T entry) -> idOf.apply(entry).equals(reference.id())).findFirst()
                    .orElseGet(() -> entries.stream().filter((T entry) -> nameOf.apply(entry).equals(reference.name()))
                            .findFirst().orElse(null));
        }
    }

    private String mediaType = "image";
    private String prompt = "";
    private Block mood;
    private final Map<Mood.Field, Block> blocks = new EnumMap<>(Mood.Field.class);
    private String styleReason = "";
    private Block frame;
    private final List<Block> savedImages = new ArrayList<>();
    private String aspectRatio;
    private String targetModel;
    private String resolution;
    private String modelReason = "";
    private Integer durationSeconds;
    private final List<BrainstormFact> facts = new ArrayList<>();

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public boolean isVideo() {
        return "video".equals(mediaType);
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    /** The mood the AI chose (an entry of the list), null for none. */
    public Block getMood() {
        return mood;
    }

    public void setMood(Block mood) {
        this.mood = mood;
    }

    /**
     * The building block of a field.
     *
     * @param field one of {@link #FIELDS}
     * @return the block, null if the draft suggests none
     */
    public Block getBlock(Mood.Field field) {
        return blocks.get(field);
    }

    /**
     * Sets the building block of a field.
     *
     * @param field one of {@link #FIELDS}
     * @param block the block, null for none
     */
    public void setBlock(Mood.Field field, Block block) {
        if (block == null)
            blocks.remove(field);
        else
            blocks.put(field, block);
    }

    /** The reason for the look (mood and building blocks), one sentence or empty. */
    public String getStyleReason() {
        return styleReason;
    }

    public void setStyleReason(String styleReason) {
        this.styleReason = styleReason;
    }

    /** The frame the AI chose (an entry of the list), null for none. */
    public Block getFrame() {
        return frame;
    }

    public void setFrame(Block frame) {
        this.frame = frame;
    }

    /** The saved images of the library the AI proposes to attach, by id and name; empty for none. */
    public List<Block> getSavedImages() {
        return savedImages;
    }

    public String getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(String aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    public String getTargetModel() {
        return targetModel;
    }

    public void setTargetModel(String targetModel) {
        this.targetModel = targetModel;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getModelReason() {
        return modelReason;
    }

    public void setModelReason(String modelReason) {
        this.modelReason = modelReason;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public List<BrainstormFact> getFacts() {
        return facts;
    }
}
