package art.moodframe.service;

import art.moodframe.model.AspectRatios;
import art.moodframe.model.Frame;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ModelFamilies;
import art.moodframe.model.PromptBlock;
import art.moodframe.repository.FrameRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for the frames: the standard frames from code with fixed ids, the frames of the user from SQLite.
 *
 * <p>A frame is never resolved on its own: a model that is not available (shut down, provider without API key,
 * OpenAI model no longer listed) keeps the frame as it is and shows it with a warning. Availability is judged by
 * the items of the model dropdown of the main window, which list exactly the models that can be chosen.</p>
 *
 * @since 2.0.0
 */
public class FrameService {

    private static final Logger logger = LoggerFactory.getLogger(FrameService.class);

    /*  The models of the standard frames, as API ids  */
    private static final String NANO_BANANA_2 = GenerationModel.NANO_BANANA2_1K.getApiModelId();
    private static final String NANO_BANANA_2_LITE = GenerationModel.NANO_BANANA2_LITE.getApiModelId();
    private static final String NANO_BANANA_PRO = GenerationModel.GEMINI_PRO_2K.getApiModelId();
    private static final String FLUX_3_IMAGE = GenerationModel.FLUX3_IMAGE_1K.getApiModelId();
    private static final String FLUX_3_KLEIN = GenerationModel.FLUX3_KLEIN_1K.getApiModelId();
    private static final String FLUX_2_FLEX = GenerationModel.FLUX2_FLEX.getApiModelId();

    /**
     * The fields a frame sets when it is chosen.
     *
     * @param item        the model dropdown item, the current one if the model of the frame is not available
     * @param resolution  the resolution of a model with free resolution choice, else null to keep it
     * @param aspectRatio the aspect ratio, valid for the model
     * @param imageCount  the image count, 1 for a video model
     * @param guidance    the guidance of FLUX.2 [flex], null to keep it
     * @param steps       the steps of FLUX.2 [flex], null to keep them
     * @param available   false if no model of the frame is available, so the model stays as it is
     */
    public record Fields(String item, String resolution, String aspectRatio, int imageCount, Double guidance,
            Integer steps, boolean available) {
    }

    private final FrameRepository repository;
    private final List<Frame> defaultFrames;

    /**
     * Creates the service.
     */
    public FrameService() {
        this.repository = new FrameRepository();
        this.defaultFrames = defaultFrames();
    }

    private static List<Frame> defaultFrames() {
        List<Frame> frames = new ArrayList<>();

        /*  Id, name, aspect ratio, resolution, image count and the models in the order of preference  */
        frames.add(frame("social-post", "Social Post", "4:5", "2K", 4, NANO_BANANA_2, FLUX_3_IMAGE,
                Frame.OPENAI_MODEL));
        frames.add(frame("story-reel", "Story / Reel", "9:16", "2K", 4, NANO_BANANA_2, FLUX_3_IMAGE,
                Frame.OPENAI_MODEL));
        frames.add(frame("presentation", "Präsentation", "16:9", "2K", 2, FLUX_3_IMAGE, NANO_BANANA_2,
                Frame.OPENAI_MODEL));
        frames.add(frame("web-banner", "Web-Banner", "21:9", "2K", 1, FLUX_3_IMAGE, NANO_BANANA_2));
        frames.add(frame("poster-print", "Poster / Druck", "2:3", "4K", 1, FLUX_3_IMAGE, NANO_BANANA_PRO));
        frames.add(frame("text-in-image", "Text im Bild", "16:9", "2K", 2, NANO_BANANA_PRO, FLUX_2_FLEX,
                Frame.OPENAI_MODEL));
        frames.add(frame("quick-sketches", "Schnelle Skizzen", "1:1", "1K", 4, FLUX_3_KLEIN, NANO_BANANA_2_LITE,
                Frame.OPENAI_MODEL));

        for (int i = 0; i < frames.size(); i++)
            frames.get(i).setSortOrder(i);
        return frames;
    }

    private static Frame frame(String slug, String name, String aspectRatio, String resolution, int imageCount,
            String... models) {
        Frame frame = new Frame(PromptBlock.STANDARD_PREFIX + "frame:" + slug, name);
        frame.setModels(List.of(models));
        frame.setResolution(resolution);
        frame.setAspectRatio(aspectRatio);
        frame.setImageCount(imageCount);
        return frame;
    }

    /**
     * Returns the standard frames, followed by those of the user.
     *
     * @return the frames in use
     */
    public List<Frame> getAll() {
        List<Frame> frames = getDefaults();
        frames.addAll(repository.findAll());
        return frames;
    }

    /**
     * Returns the standard frames.
     *
     * @return the frames
     */
    public List<Frame> getDefaults() {
        return new ArrayList<>(defaultFrames);
    }

    /**
     * Returns the frames the user saved and has in use.
     *
     * @return the frames
     */
    public List<Frame> getCustom() {
        return repository.findAll();
    }

    /**
     * Returns the frames of the user in the trash.
     *
     * @return the frames, the latest deletion first
     */
    public List<Frame> getDeleted() {
        return repository.findDeleted();
    }

    /**
     * Finds a frame in use by its id.
     *
     * @param id the frame id
     * @return the frame, null if there is none in use
     */
    public Frame find(String id) {
        if (id == null || id.isEmpty())
            return null;
        for (Frame frame : defaultFrames) {
            if (frame.getId().equals(id))
                return frame;
        }
        return repository.findById(id);
    }

    /**
     * Checks if a standard frame or a frame of the user in use has this name.
     *
     * @param name the name
     * @return true if the name is taken
     */
    public boolean exists(String name) {
        return defaultFrames.stream().anyMatch((Frame frame) -> frame.getName().equals(name))
                || repository.findByName(name) != null;
    }

    /**
     * Returns the name unchanged if no frame has it yet, else the name with the next free counter,
     * e.g. {@code "Social Post (1)"}.
     *
     * @param name the wanted name
     * @return a name no frame in use has
     */
    public String freeName(String name) {
        if (!exists(name))
            return name;
        int counter = 1;
        while (exists(name + " (" + counter + ")"))
            counter++;
        return name + " (" + counter + ")";
    }

    /**
     * Stores a new frame of the user behind the existing ones.
     *
     * @param frame the frame with a model, without id or with an id no frame has
     * @return true if it was stored, false if the name is taken or the model or aspect ratio is missing
     */
    public boolean add(Frame frame) {
        if (frame.getName() == null || frame.getName().isBlank() || exists(frame.getName())
                || frame.getModel() == null || frame.getAspectRatio() == null) {
            logger.warn("Cannot add frame '{}' - name is empty or taken, or model or aspect ratio is missing",
                    frame.getName());
            return false;
        }

        /*  Frames of the user follow the standard frames, from 1000 on  */
        frame.setSortOrder(Math.max(1000, repository.findAll().stream()
                .mapToInt((Frame existing) -> existing.getSortOrder() + 1).max().orElse(1000)));
        return repository.save(frame);
    }

    /**
     * Updates a frame of the user: its name and its settings. Standard frames cannot be changed.
     *
     * @param frame the changed frame with its id
     * @return true if it was updated, false for a standard frame, an unknown id, a taken name or a missing model
     */
    public boolean update(Frame frame) {
        Frame existing = frame.isDefault() ? null : repository.findById(frame.getId());
        if (existing == null || frame.getModel() == null || frame.getAspectRatio() == null) {
            logger.warn("Cannot update frame '{}' - no frame of the user, or model or aspect ratio is missing",
                    frame.getName());
            return false;
        }
        if (frame.getName() == null || frame.getName().isBlank()
                || !existing.getName().equals(frame.getName()) && exists(frame.getName())) {
            logger.warn("Cannot rename frame to '{}' - name is empty or already exists", frame.getName());
            return false;
        }
        frame.setSortOrder(existing.getSortOrder());
        return repository.update(frame);
    }

    /**
     * Puts the frames of the user in a new order.
     *
     * @param ids the ids of the frames of the user in the wanted order
     */
    public void reorder(List<String> ids) {
        for (int i = 0; i < ids.size(); i++) {
            Frame frame = repository.findById(ids.get(i));
            if (frame != null) {
                frame.setSortOrder(1000 + i);
                repository.update(frame);
            }
        }
    }

    /**
     * Moves a frame of the user to the trash; standard frames cannot be deleted.
     *
     * @param id the frame id
     * @return true if it is in the trash now
     */
    public boolean moveToTrash(String id) {
        return id != null && !id.startsWith(PromptBlock.STANDARD_PREFIX) && repository.moveToTrash(id);
    }

    /**
     * Takes a frame out of the trash. If another frame took its name meanwhile, it gets the next free one.
     *
     * @param id the frame id
     * @return true if it is in use again
     */
    public boolean restore(String id) {
        Frame frame = repository.findDeleted().stream().filter((Frame deleted) -> deleted.getId().equals(id))
                .findFirst().orElse(null);
        if (frame == null)
            return false;
        if (exists(frame.getName())) {
            frame.setName(freeName(frame.getName()));
            repository.update(frame);
        }
        return repository.restore(id);
    }

    /**
     * Removes a frame of the user for good.
     *
     * @param id the frame id
     * @return true if it is gone
     */
    public boolean deletePermanently(String id) {
        return id != null && !id.startsWith(PromptBlock.STANDARD_PREFIX) && repository.delete(id);
    }

    /**
     * The model id of an item of the model dropdown, as a frame keeps it.
     *
     * @param item the dropdown item (may be null)
     * @return the API id of the model, {@value Frame#OPENAI_MODEL} plus the id for an OpenAI model, null if unknown
     */
    public static String modelId(String item) {
        String openAiId = GenerationModel.openAiModelId(item);
        if (openAiId != null)
            return Frame.OPENAI_MODEL + openAiId;
        GenerationModel model = GenerationModel.fromLocalizedDisplayName(item);
        return model != null ? model.getApiModelId() : null;
    }

    /**
     * Finds the dropdown item a frame chooses: the first of its models that is available, in the resolution of the
     * frame if the model has it, else in 1K or its first one.
     *
     * @param frame the frame
     * @param items the items of the model dropdown, the models that can be chosen now
     * @return the item, null if no model of the frame is available
     */
    public String resolve(Frame frame, List<String> items) {
        for (String model : frame.getModels()) {
            String item = item(model, frame.getResolution(), items);
            if (item != null)
                return item;
        }
        return null;
    }

    /** The item of one model of a frame, null if it is not listed. */
    private static String item(String model, String resolution, List<String> items) {
        if (model.startsWith(Frame.OPENAI_MODEL)) {
            String openAiId = model.substring(Frame.OPENAI_MODEL.length());
            return items.stream().filter((String item) -> openAiId.isEmpty()
                    ? GenerationModel.openAiModelId(item) != null : item.equals(GenerationModel.OPENAI_PREFIX + openAiId))
                    .findFirst().orElse(null);
        }

        /*  All variants of a family share the API id, the resolution picks one of them  */
        GenerationModel generationModel = GenerationModel.fromApiModelId(model);
        return generationModel != null
                ? ModelFamilies.variantFor(items, generationModel.getFamilyName(), resolution, null) : null;
    }

    /**
     * The fields a chosen frame sets. A frame whose models are all unavailable leaves the model as it is and sets
     * the other fields; the aspect ratio then is the closest one the current model can do.
     *
     * @param frame the chosen frame
     * @param items the items of the model dropdown, the models that can be chosen now
     * @param current the item of the model dropdown now (may be null)
     * @return the fields to set
     */
    public Fields apply(Frame frame, List<String> items, String current) {
        String resolved = resolve(frame, items);
        String item = resolved != null ? resolved : current;
        List<String> free = resolved != null ? ModelFamilies.freeResolutions(resolved) : null;
        GenerationModel model = GenerationModel.fromLocalizedDisplayName(item);
        return new Fields(item, free != null && free.contains(frame.getResolution()) ? frame.getResolution() : null,
                AspectRatios.nearest(frame.getAspectRatio(), AspectRatios.forItem(item)),
                model != null && model.isVideoModel() ? 1 : frame.getImageCount(), frame.getGuidance(),
                frame.getSteps(), resolved != null);
    }

    /**
     * Takes the fields of the main window into a frame to save. Guidance and steps are kept only for FLUX.2 [flex],
     * the only model that shows them.
     *
     * @param frame the frame to fill
     * @param item the item of the model dropdown
     * @param resolution the chosen resolution, null for a model without resolution choice
     * @param aspectRatio the aspect ratio
     * @param imageCount the image count
     * @param guidance the guidance of FLUX.2 [flex]
     * @param steps the steps of FLUX.2 [flex]
     */
    public static void capture(Frame frame, String item, String resolution, String aspectRatio, int imageCount,
            double guidance, int steps) {
        GenerationModel model = GenerationModel.fromLocalizedDisplayName(item);
        boolean flex = model != null && model.isFlux2Flex();
        frame.setModel(modelId(item));
        frame.setResolution(ModelFamilies.freeResolutions(item) != null ? resolution : ModelFamilies.resolutionOf(item));
        frame.setAspectRatio(aspectRatio);
        frame.setImageCount(imageCount);
        frame.setGuidance(flex ? guidance : null);
        frame.setSteps(flex ? steps : null);
    }

    /**
     * Names the settings of a frame, for example {@code "Nano Banana 2 → FLUX 3 Image · 2K · 4:5 · 4×"}.
     *
     * @param frame the frame
     * @return the models, resolution, aspect ratio and image count joined by a middle dot
     */
    public static String describe(Frame frame) {
        List<String> parts = new ArrayList<>();
        parts.add(String.join(" → ", frame.getModels().stream().map(FrameService::modelName).toList()));
        if (frame.getResolution() != null)
            parts.add(frame.getResolution());
        parts.add(frame.getAspectRatio());
        parts.add(frame.getImageCount() + "×");
        return String.join(" · ", parts);
    }

    /** The family name of a model id, "OpenAI" for any OpenAI model, the id itself if the model is unknown. */
    private static String modelName(String model) {
        if (model.startsWith(Frame.OPENAI_MODEL)) {
            String openAiId = model.substring(Frame.OPENAI_MODEL.length());
            return openAiId.isEmpty() ? "OpenAI" : GenerationModel.OPENAI_PREFIX + openAiId;
        }
        GenerationModel generationModel = GenerationModel.fromApiModelId(model);
        return generationModel != null ? generationModel.getFamilyName() : model;
    }

    /**
     * Returns the frames of the user as plain data, e.g. for the full backup ({@link ConfigurationExporter}).
     *
     * @return per frame its id, name, model, resolution, aspect ratio, image count, guidance and steps
     */
    public List<Map<String, Object>> exportCustom() {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Frame frame : getCustom()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", frame.getId());
            entry.put("name", frame.getName());
            entry.put("model", frame.getModel());
            if (frame.getResolution() != null)
                entry.put("resolution", frame.getResolution());
            entry.put("aspectRatio", frame.getAspectRatio());
            entry.put("imageCount", frame.getImageCount());
            if (frame.getGuidance() != null)
                entry.put("guidance", frame.getGuidance());
            if (frame.getSteps() != null)
                entry.put("steps", frame.getSteps());
            entries.add(entry);
        }
        return entries;
    }

    /**
     * Adds frames from plain data (see {@link #exportCustom()}) behind the frames of the user. A frame keeps its id
     * unless a frame has it already, and its name unless a frame in use has it already; then it gets a new one.
     *
     * @param entries the frames as maps
     * @return the number of imported frames
     */
    public int importCustom(List<Map<String, Object>> entries) {
        int imported = 0;
        for (Map<String, Object> entry : entries) {
            String name = entry.get("name") instanceof String text ? text.trim() : "";
            if (name.isEmpty())
                continue;
            String id = entry.get("id") instanceof String text ? text : "";
            Frame frame = new Frame(id.isEmpty() || id.startsWith(PromptBlock.STANDARD_PREFIX) || repository.exists(id)
                    ? null : id, freeName(name));
            frame.setModel(entry.get("model") instanceof String model ? model : null);
            frame.setResolution(entry.get("resolution") instanceof String resolution ? resolution : null);
            frame.setAspectRatio(entry.get("aspectRatio") instanceof String ratio ? ratio : null);
            frame.setImageCount(entry.get("imageCount") instanceof Number count ? count.intValue() : 1);
            frame.setGuidance(entry.get("guidance") instanceof Number guidance ? guidance.doubleValue() : null);
            frame.setSteps(entry.get("steps") instanceof Number steps ? steps.intValue() : null);
            if (add(frame))
                imported++;
        }
        return imported;
    }

    /**
     * Exports the frames of the user to a JSON file.
     *
     * @param file the target file
     * @return true if it was written
     */
    public boolean exportFrames(File file) {
        try {
            Files.writeString(file.toPath(), new JSONArray(exportCustom()).toString(2));
            return true;
        } catch (Exception e) {
            logger.error("Failed to export frames to {}", file.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Imports frames from a JSON file behind the frames of the user (see {@link #importCustom}).
     *
     * @param file the source file
     * @return the number of imported frames, -1 if the file cannot be read
     */
    public int importFrames(File file) {
        List<Map<String, Object>> entries = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(Files.readString(file.toPath()));
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json != null)
                    entries.add(json.toMap());
            }
        } catch (Exception e) {
            logger.error("Failed to import frames from {}", file.getAbsolutePath(), e);
            return -1;
        }
        return importCustom(entries);
    }
}
