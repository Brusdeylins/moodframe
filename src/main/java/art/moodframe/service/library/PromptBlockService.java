package art.moodframe.service.library;

import art.moodframe.model.CameraSettings;
import art.moodframe.model.PromptBlock;
import art.moodframe.repository.PromptBlockRepository;
import art.moodframe.service.prompt.CameraPromptBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Service for the prompt building blocks: the standard blocks from code with fixed ids,
 * the blocks of the user from SQLite.
 *
 * <p>The standard blocks and the rules of their texts are in {@link StandardBlocks}.</p>
 *
 * <p>Every successful write is reported with the kind of its block type ({@link LibraryEvents.Kind#of}). The writes
 * are serialized, so parallel callers cannot take the same name.</p>
 *
 * @since 2.0.0
 */
public class PromptBlockService {

    private static final Logger logger = LoggerFactory.getLogger(PromptBlockService.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Former infographic styles that were merged into the remaining ones, by name. */
    private static final Map<String, String> MERGED_STYLES = Map.of(
            "Infographic Modern", "Infographic Flat",
            "Infographic Minimalist", "Infographic Minimal",
            "Infographic Educational", "Infographic Sketch",
            "Infographic Timeline", "Infographic Flat",
            "Infographic Flowchart", "Infographic Flat",
            "Infographic Mind Map", "Infographic Flat",
            "Infographic Comparison", "Infographic Flat",
            "Infographic Data", "Infographic Flat");

    private final PromptBlockRepository repository;
    private final List<PromptBlock> defaultStyles;
    private final List<PromptBlock> defaultAtmospheres;
    private final List<PromptBlock> defaultLights;
    private final List<PromptBlock> defaultCameras;
    private final LibraryEvents events;
    private final RepositoryAccess access = new RepositoryAccess(logger);

    /**
     * Creates the service on the given repository; it reads and writes nothing until it is used.
     *
     * @param repository the building blocks of the user
     * @param events     where the writes are reported
     */
    public PromptBlockService(PromptBlockRepository repository, LibraryEvents events) {
        this.repository = repository;
        this.events = events;
        this.defaultStyles = StandardBlocks.styles();
        this.defaultAtmospheres = StandardBlocks.atmospheres();
        this.defaultLights = StandardBlocks.lights();
        this.defaultCameras = StandardBlocks.cameras();
    }

    /**
     * Starts the service once at the program start, before the library is used: a block of the user named like a
     * standard block, which a new version may have added, gets the next free name (see {@link #freeName}). Not in
     * the constructor, so creating the service writes nothing.
     */
    public void start() {
        renameCustomBlocksNamedLikeDefaults();
    }


    /*  reports a successful write  */
    private boolean changed(PromptBlock.Type type, boolean success, String id) {
        if (success)
            events.changed(LibraryEvents.Kind.of(type), id);
        return success;
    }

    /*  reads and writes of the repository; a database error is logged and gives no blocks, null or false  */
    private List<PromptBlock> findAll(PromptBlock.Type type) {
        return access.call(() -> repository.findAll(type), new ArrayList<>(), "Failed to load prompt blocks");
    }

    private PromptBlock findById(String id) {
        return access.call(() -> repository.findById(id).orElse(null), null, "Failed to load prompt blocks");
    }

    private PromptBlock findByName(PromptBlock.Type type, String name) {
        return access.call(() -> repository.findByName(type, name).orElse(null), null,
                "Failed to load prompt blocks");
    }

    private PromptBlock findDeletedById(String id) {
        return access.call(() -> repository.findInTrash(id).orElse(null), null, "Failed to load prompt blocks");
    }

    private boolean save(PromptBlock block) {
        return access.call(() -> repository.save(block), false, "Failed to save prompt block: " + block.getName());
    }

    private boolean update(PromptBlock block) {
        return access.call(() -> repository.update(block), false, "Failed to update prompt block: " + block.getName());
    }

    private boolean moveToTrash(String id) {
        return access.call(() -> repository.moveToTrash(id), false, "Failed to move prompt block to the trash: " + id);
    }

    private boolean takeFromTrash(String id) {
        return access.call(() -> repository.restore(id), false, "Failed to restore prompt block: " + id);
    }

    private boolean delete(String id) {
        return access.call(() -> repository.delete(id), false, "Failed to delete prompt block: " + id);
    }

    /**
     * A block of the user named like a standard block (which may be new) keeps its text and gets the
     * next free name with a counter (see {@link #freeName}), so every name stays selectable.
     */
    private void renameCustomBlocksNamedLikeDefaults() {
        for (PromptBlock.Type type : PromptBlock.Type.values()) {
            for (PromptBlock custom : findAll(type)) {
                if (!isDefault(type, custom.getName()))
                    continue;
                String original = custom.getName();
                custom.setName(freeName(type, original));
                if (update(custom))
                    logger.info("Renamed {} '{}' of the user to '{}', a standard block has its name",
                            type.name().toLowerCase(Locale.ROOT), original, custom.getName());
            }
        }
    }

    /**
     * A camera block from its settings; its text is the fragment for a photographic style, for display and export.
     * The prompt itself is built from the settings, see {@link CameraPromptBuilder}.
     *
     * @param id       the id, null to let the repository assign one
     * @param name     the name
     * @param settings the settings
     * @return the block
     */
    public static PromptBlock camera(String id, String name, CameraSettings settings) {
        PromptBlock block = new PromptBlock(id, PromptBlock.Type.CAMERA, name,
                CameraPromptBuilder.build(settings, true), null, false);
        block.setParams(settings.toJson());
        return block;
    }

    /**
     * The fixed id of a standard block, for example {@code std:style:watercolor}.
     *
     * @param type the block type
     * @param name the block name
     * @return the id
     */
    static String standardId(PromptBlock.Type type, String name) {
        return PromptBlock.STANDARD_PREFIX + type.name().toLowerCase(Locale.ROOT) + ":" + slug(name);
    }

    /**
     * The name in lower case, every run of other characters than letters and digits replaced by one dash.
     *
     * @param name the name
     * @return the slug, for example {@code ocean-breeze}
     */
    public static String slug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    /**
     * Returns the standard blocks of a type, followed by those of the user.
     *
     * @param type the block type
     * @return the blocks
     */
    public List<PromptBlock> getAll(PromptBlock.Type type) {
        List<PromptBlock> blocks = getDefaults(type);
        blocks.addAll(findAll(type));
        return blocks;
    }

    /**
     * Returns the standard blocks of a type.
     *
     * @param type the block type
     * @return the blocks
     */
    public List<PromptBlock> getDefaults(PromptBlock.Type type) {
        return switch (type) {
            case STYLE -> new ArrayList<>(defaultStyles);
            case ATMOSPHERE -> new ArrayList<>(defaultAtmospheres);
            case LIGHT -> new ArrayList<>(defaultLights);
            case CAMERA -> new ArrayList<>(defaultCameras);
        };
    }

    /**
     * Returns the blocks of a type the user created.
     *
     * @param type the block type
     * @return the blocks
     */
    public List<PromptBlock> getCustom(PromptBlock.Type type) {
        return findAll(type);
    }

    /**
     * Finds a block by id or name. A former infographic style that was merged into another one
     * resolves to that one.
     *
     * @param type the block type
     * @param reference the id or the name of the block
     * @return the block, null if there is none
     */
    public PromptBlock find(PromptBlock.Type type, String reference) {
        if (reference == null || reference.isEmpty())
            return null;

        for (PromptBlock block : getDefaults(type)) {
            if (block.getId().equals(reference) || block.getName().equals(reference))
                return block;
        }
        PromptBlock custom = findById(reference);
        if (custom == null || custom.getType() != type)
            custom = findByName(type, reference);
        if (custom == null && type == PromptBlock.Type.STYLE && MERGED_STYLES.containsKey(reference))
            return find(type, MERGED_STYLES.get(reference));
        return custom;
    }

    /**
     * Finds a block of the user in the trash.
     *
     * @param id the block id
     * @return the block, null if it is not in the trash
     */
    public PromptBlock findInTrash(String id) {
        return id == null || id.startsWith(PromptBlock.STANDARD_PREFIX) ? null
                : findDeletedById(id);
    }

    /**
     * Returns the name unchanged if no block of the type has it yet, else the name with the next free counter,
     * e.g. {@code "Analog Film (1)"}.
     *
     * @param type the block type
     * @param name the wanted name
     * @return a name no block of the type has
     */
    public String freeName(PromptBlock.Type type, String name) {
        if (!exists(type, name))
            return name;
        int counter = 1;
        while (exists(type, name + " (" + counter + ")"))
            counter++;
        return name + " (" + counter + ")";
    }

    /**
     * Checks if a standard block or a block of the user has this name.
     *
     * @param type the block type
     * @param name the name
     * @return true if the name is taken
     */
    public boolean exists(PromptBlock.Type type, String name) {
        return isDefault(type, name) || findByName(type, name) != null;
    }

    /**
     * Checks if a standard block has this name.
     *
     * @param type the block type
     * @param name the name
     * @return true if a standard block has the name
     */
    public boolean isDefault(PromptBlock.Type type, String name) {
        return getDefaults(type).stream().anyMatch((PromptBlock block) -> block.getName().equals(name));
    }

    /**
     * Stores a new block of the user behind the existing ones.
     *
     * @param block the block, without id
     * @return true if it was stored, false if the name is taken
     */
    public synchronized boolean addCustom(PromptBlock block) {
        if (exists(block.getType(), block.getName())) {
            logger.warn("Cannot add prompt block '{}' - name already exists", block.getName());
            return false;
        }

        /*  Blocks of the user follow the standard blocks, from 1000 on  */
        block.setSortOrder(Math.max(1000, findAll(block.getType()).stream()
                .mapToInt((PromptBlock existing) -> existing.getSortOrder() + 1).max().orElse(1000)));
        return changed(block.getType(), save(block), block.getId());
    }

    /**
     * Updates a block of the user; standard blocks cannot be changed.
     *
     * @param block the changed block with its id
     * @return true if it was updated, false for a standard block, an unknown id or a taken name
     */
    public synchronized boolean updateCustom(PromptBlock block) {
        PromptBlock existing = block.isDefault() ? null : findById(block.getId());
        if (existing == null) {
            logger.warn("Cannot update prompt block '{}' - no block of the user", block.getName());
            return false;
        }
        if (!existing.getName().equals(block.getName()) && exists(block.getType(), block.getName())) {
            logger.warn("Cannot rename prompt block to '{}' - name already exists", block.getName());
            return false;
        }
        return changed(block.getType(), update(block), block.getId());
    }

    /**
     * Stores a block as a new block of the user, e.g. a copy of a standard block; a taken name gets the next free
     * counter (see {@link #freeName}).
     *
     * @param block the block; its id is replaced by a new one
     * @return true if it was stored
     */
    public synchronized boolean addCopy(PromptBlock block) {
        block.setId(null);
        block.setName(freeName(block.getType(), block.getName()));
        return addCustom(block);
    }

    /**
     * Puts the blocks of the user of a type into the given order.
     *
     * @param type the block type
     * @param ids the ids of the blocks of the user in their new order
     * @return true if all of them were updated
     */
    public synchronized boolean reorderCustom(PromptBlock.Type type, List<String> ids) {
        boolean updated = true;
        for (PromptBlock block : findAll(type)) {
            int index = ids.indexOf(block.getId());
            if (index < 0 || block.getSortOrder() == 1000 + index)
                continue;
            block.setSortOrder(1000 + index);
            updated &= update(block);
        }
        return changed(type, updated, null);
    }

    /**
     * Moves a block of the user to the trash; standard blocks cannot be deleted.
     *
     * @param id the block id
     * @return true if it was moved to the trash
     */
    public synchronized boolean deleteCustom(String id) {
        PromptBlock block = id != null && !id.startsWith(PromptBlock.STANDARD_PREFIX) ? findById(id) : null;
        return block != null && changed(block.getType(), moveToTrash(id), id);
    }

    /**
     * Returns the blocks of the user in the trash, all types, the latest deletion first.
     *
     * @return the blocks
     */
    public List<PromptBlock> getDeleted() {
        return access.call(repository::findDeleted, new ArrayList<>(), "Failed to load prompt blocks");
    }

    /**
     * Takes a block out of the trash. As the name of a block in the trash is free, a block that took it meanwhile
     * keeps it and the restored one gets the next free counter.
     *
     * @param id the block id
     * @return true if it is back in use
     */
    public synchronized boolean restore(String id) {
        PromptBlock block = findDeletedById(id);
        if (block == null)
            return false;

        String name = freeName(block.getType(), block.getName());
        if (!name.equals(block.getName())) {
            block.setName(name);
            update(block);
        }
        return changed(block.getType(), takeFromTrash(id), id);
    }

    /**
     * Removes a block of the user for good.
     *
     * @param id the block id
     * @return true if it is gone
     */
    public synchronized boolean deletePermanently(String id) {
        if (id == null || id.startsWith(PromptBlock.STANDARD_PREFIX))
            return false;

        /*  usually a block in the trash  */
        PromptBlock block = findDeletedById(id);
        if (block == null)
            block = findById(id);
        return block != null && changed(block.getType(), delete(id), id);
    }

    /**
     * Returns the blocks of the user of a type as plain data for a JSON file: id, type, name, text, group,
     * photographic and parameters.
     *
     * @param type the block type
     * @return one map per block, in their order
     */
    public List<Map<String, Object>> exportCustom(PromptBlock.Type type) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (PromptBlock block : findAll(type)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", block.getId());
            entry.put("type", block.getType().name());
            entry.put("name", block.getName());
            entry.put("text", block.getText());
            entry.put("group", block.getGroup());
            entry.put("photographic", block.isPhotographic());
            entry.put("params", block.getParams());
            entries.add(entry);
        }
        return entries;
    }

    /**
     * Adds the blocks of a type from plain data (see {@link #exportCustom(PromptBlock.Type)}) behind the existing
     * ones. An entry of another type is skipped, an id that is taken gets a new one, a taken name the next free
     * counter.
     *
     * @param type the block type
     * @param entries the blocks as maps; {@code "promptText"} stands for the text in older exports
     * @return the number of added blocks
     */
    public synchronized int importCustom(PromptBlock.Type type, List<Map<String, Object>> entries) {
        int imported = 0;
        for (Map<String, Object> entry : entries) {
            Object entryType = entry.get("type");
            Object entryText = entry.containsKey("text") ? entry.get("text") : entry.get("promptText");
            if ((entryType != null && !type.name().equals(entryType)) || !(entry.get("name") instanceof String name)
                    || !(entryText instanceof String text))
                continue;

            /*  Older exports have no flag: styles of the user count as photographic  */
            PromptBlock block = new PromptBlock(null, type, freeName(type, name), text, (String) entry.get("group"),
                    !Boolean.FALSE.equals(entry.get("photographic")));
            block.setParams((String) entry.get("params"));
            if (entry.get("id") instanceof String id && !id.startsWith(PromptBlock.STANDARD_PREFIX)
                    && !access.call(() -> repository.existsById(id), false, "Failed to load prompt blocks"))
                block.setId(id);
            if (addCustom(block))
                imported++;
        }
        return imported;
    }

    /**
     * Writes the blocks of the user of a type into a JSON file.
     *
     * @param type the block type
     * @param file the target file
     * @throws IOException if the file cannot be written
     */
    public void exportCustom(PromptBlock.Type type, File file) throws IOException {
        JSON.writerWithDefaultPrettyPrinter().writeValue(file, exportCustom(type));
    }

    /**
     * Adds the blocks of a type from a JSON file written by {@link #exportCustom(PromptBlock.Type, File)}.
     *
     * @param type the block type
     * @param file the source file
     * @return the number of added blocks
     * @throws IOException if the file cannot be read or is no list of blocks
     */
    public int importCustom(PromptBlock.Type type, File file) throws IOException {
        return importCustom(type, JSON.readValue(file, new TypeReference<List<Map<String, Object>>>() { }));
    }

    /**
     * Deletes all blocks of a type the user created.
     *
     * @param type the block type
     * @return the number of deleted blocks
     */
    public synchronized int resetToDefaults(PromptBlock.Type type) {
        int deleted = access.call(() -> repository.deleteAll(type), 0,
                "Failed to delete prompt blocks of type " + type);
        changed(type, true, null);
        return deleted;
    }
}
