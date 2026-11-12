package art.moodframe.service;

import art.moodframe.model.Frame;
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
 * <p>A frame keeps a target aspect ratio only; the model of the main window makes the closest ratio it can do of it
 * ({@link art.moodframe.model.AspectRatios#nearestForItem}).</p>
 *
 * @since 2.0.0
 */
public class FrameService {

    private static final Logger logger = LoggerFactory.getLogger(FrameService.class);

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

        /*  Id, name and target aspect ratio  */
        frames.add(frame("social-post", "Social Post", "4:5"));
        frames.add(frame("story-reel", "Story / Reel", "9:16"));
        frames.add(frame("presentation", "Präsentation", "16:9"));
        frames.add(frame("web-banner", "Web-Banner", "21:9"));
        frames.add(frame("poster-print", "Poster / Druck", "2:3"));
        frames.add(frame("text-in-image", "Text im Bild", "16:9"));
        frames.add(frame("quick-sketches", "Schnelle Skizzen", "1:1"));

        for (int i = 0; i < frames.size(); i++)
            frames.get(i).setSortOrder(i);
        return frames;
    }

    private static Frame frame(String slug, String name, String aspectRatio) {
        Frame frame = new Frame(PromptBlock.STANDARD_PREFIX + "frame:" + slug, name);
        frame.setAspectRatio(aspectRatio);
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
     * @param frame the frame with an aspect ratio, without id or with an id no frame has
     * @return true if it was stored, false if the name is taken or the aspect ratio is missing
     */
    public boolean add(Frame frame) {
        if (frame.getName() == null || frame.getName().isBlank() || exists(frame.getName())
                || frame.getAspectRatio() == null) {
            logger.warn("Cannot add frame '{}' - name is empty or taken, or aspect ratio is missing", frame.getName());
            return false;
        }

        /*  Frames of the user follow the standard frames, from 1000 on  */
        frame.setSortOrder(Math.max(1000, repository.findAll().stream()
                .mapToInt((Frame existing) -> existing.getSortOrder() + 1).max().orElse(1000)));
        return repository.save(frame);
    }

    /**
     * Updates a frame of the user: its name and its aspect ratio. Standard frames cannot be changed.
     *
     * @param frame the changed frame with its id
     * @return true if it was updated, false for a standard frame, an unknown id, a taken name or a missing aspect ratio
     */
    public boolean update(Frame frame) {
        Frame existing = frame.isDefault() ? null : repository.findById(frame.getId());
        if (existing == null || frame.getAspectRatio() == null) {
            logger.warn("Cannot update frame '{}' - no frame of the user, or aspect ratio is missing", frame.getName());
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
     * Names the setting of a frame, for example {@code "4:5"}.
     *
     * @param frame the frame
     * @return its target aspect ratio
     */
    public static String describe(Frame frame) {
        return frame.getAspectRatio();
    }

    /**
     * Returns the frames of the user as plain data, e.g. for the full backup ({@link ConfigurationExporter}).
     *
     * @return per frame its id, name and aspect ratio
     */
    public List<Map<String, Object>> exportCustom() {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Frame frame : getCustom()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", frame.getId());
            entry.put("name", frame.getName());
            entry.put("aspectRatio", frame.getAspectRatio());
            entries.add(entry);
        }
        return entries;
    }

    /**
     * Adds frames from plain data (see {@link #exportCustom()}) behind the frames of the user. A frame keeps its id
     * unless a frame has it already, and its name unless a frame in use has it already; then it gets a new one. The
     * model, resolution, image count, guidance and steps of an older export are passed over.
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
            frame.setAspectRatio(entry.get("aspectRatio") instanceof String ratio ? ratio : null);
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
