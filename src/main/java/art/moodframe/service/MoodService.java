package art.moodframe.service;

import art.moodframe.model.ColorStyle;
import art.moodframe.model.EntryMood;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.repository.MoodRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Service for the moods: the standard moods from code with fixed ids, the moods of the user from SQLite.
 *
 * <p>A mood is never resolved on its own: a block it refers to that lies in the trash leaves its field empty and
 * marks the mood incomplete until the block is restored. Only when the block is deleted for good does the reference
 * go ({@link #blocksDeleted}); a mood that loses its last block this way is reported once ({@link #takeEmptied}),
 * so the user can decide whether to delete it.</p>
 *
 * @since 2.0.0
 */
public class MoodService implements BlockUsage {

    private static final Logger logger = LoggerFactory.getLogger(MoodService.class);

    /** The looks that left the styles in 2.0; each lives on as the standard mood of the same name. */
    private static final List<String> LEGACY_LOOKS = List.of("Business Photography", "Film Noir", "Cinematic",
            "Blockbuster", "Arthouse", "Sci-Fi Cinema", "Retro 80s", "Vintage Film", "Studio Portrait");

    private final MoodRepository repository;
    private final PromptBlockService blocks;
    private final ColorStyleService colors;
    private final List<Mood> defaultMoods;
    private final List<Mood> emptied = new ArrayList<>();

    /**
     * Creates the service.
     *
     * @param blocks the building blocks the moods refer to
     * @param colors the palettes the moods refer to
     */
    public MoodService(PromptBlockService blocks, ColorStyleService colors) {
        this.repository = new MoodRepository();
        this.blocks = blocks;
        this.colors = colors;
        this.defaultMoods = defaultMoods();
    }

    private static List<Mood> defaultMoods() {
        List<Mood> moods = new ArrayList<>();

        /*  Style, atmosphere, light, palette, camera; the first nine carry the names of the former looks  */
        moods.add(mood("Business Photography", "Business Photo", "Open Business", "Soft Natural", null, null));
        moods.add(mood("Film Noir", "Black-and-White Film", "Menacing", "Chiaroscuro", "Silver Screen", null));
        moods.add(mood("Cinematic", "Cinematic Film Still", null, "Rim Light", "Cinematic Teal-Orange",
                "Cinematic Anamorphic"));
        moods.add(mood("Blockbuster", "Cinematic Film Still", "Epic", "Light Rays", "Cinematic Teal-Orange",
                "Epic Wide Low Angle"));
        moods.add(mood("Arthouse", "Analog Film", "Melancholic", "Window Light", "Faded Cream", null));
        moods.add(mood("Sci-Fi Cinema", "Cinematic Film Still", "Futuristic", "Neon Practicals", "Neon Nights", null));
        moods.add(mood("Retro 80s", "Synthwave", "Euphoric", "Neon Practicals", "Synthwave Sunset", null));
        moods.add(mood("Vintage Film", "Analog Film", "Nostalgic", "Golden Hour", "Sunset Amber", "Street 35mm"));
        moods.add(mood("Studio Portrait", "Portrait Photography", "Clean and Factual", "Studio Softbox",
                "Warm Skin Studio", "Classic Portrait"));
        moods.add(mood("Quiet Morning", "Photography", "Calm", "Blue Hour", "Arctic Ice", null));
        moods.add(mood("Dreamy Pastel", "Watercolor", "Dreamlike", "High-Key", "Pastel Serenity", null));
        moods.add(mood("Candle Glow", "Portrait Photography", "Intimate", "Candlelight", "Autumn Leaves",
                "Classic Portrait"));
        moods.add(mood("Wild North", "Documentary", "Raw", "Overcast", "Forest Dusk", null));
        moods.add(mood("Clean Product", "Product Photography", "Clean and Factual", "Studio Softbox", "Monochrome Pro",
                "Product Tabletop"));

        for (int i = 0; i < moods.size(); i++)
            moods.get(i).setSortOrder(i);
        return moods;
    }

    /** A standard mood from the names of its standard blocks; a null name leaves the field empty. */
    private static Mood mood(String name, String style, String atmosphere, String light, String palette,
            String camera) {
        Mood mood = new Mood(PromptBlock.STANDARD_PREFIX + "mood:" + PromptBlockService.slug(name), name);
        mood.setReference(Mood.Field.STYLE, standardId(PromptBlock.Type.STYLE, style));
        mood.setReference(Mood.Field.ATMOSPHERE, standardId(PromptBlock.Type.ATMOSPHERE, atmosphere));
        mood.setReference(Mood.Field.LIGHT, standardId(PromptBlock.Type.LIGHT, light));
        mood.setReference(Mood.Field.CAMERA, standardId(PromptBlock.Type.CAMERA, camera));

        /*  The fixed ids of the standard palettes (ColorStyleService)  */
        mood.setReference(Mood.Field.COLOR, palette != null ? "std:color:" + PromptBlockService.slug(palette) : null);
        return mood;
    }

    private static String standardId(PromptBlock.Type type, String name) {
        return name != null ? PromptBlockService.standardId(type, name) : null;
    }

    /**
     * Returns the standard moods, followed by those of the user.
     *
     * @return the moods in use
     */
    public List<Mood> getAll() {
        List<Mood> moods = getDefaults();
        moods.addAll(repository.findAll());
        return moods;
    }

    /**
     * Returns the standard moods.
     *
     * @return the moods
     */
    public List<Mood> getDefaults() {
        return new ArrayList<>(defaultMoods);
    }

    /**
     * Returns the moods the user saved and has in use.
     *
     * @return the moods
     */
    public List<Mood> getCustom() {
        return repository.findAll();
    }

    /**
     * Returns the moods of the user in the trash.
     *
     * @return the moods, the latest deletion first
     */
    public List<Mood> getDeleted() {
        return repository.findDeleted();
    }

    /**
     * Finds a mood in use by its id.
     *
     * @param id the mood id
     * @return the mood, null if there is none in use
     */
    public Mood find(String id) {
        if (id == null || id.isEmpty())
            return null;
        for (Mood mood : defaultMoods) {
            if (mood.getId().equals(id))
                return mood;
        }
        return repository.findById(id);
    }

    /**
     * Checks if a standard mood or a mood of the user in use has this name.
     *
     * @param name the name
     * @return true if the name is taken
     */
    public boolean exists(String name) {
        return defaultMoods.stream().anyMatch((Mood mood) -> mood.getName().equals(name))
                || repository.findByName(name) != null;
    }

    /**
     * Returns the name unchanged if no mood has it yet, else the name with the next free counter,
     * e.g. {@code "Film Noir (1)"}.
     *
     * @param name the wanted name
     * @return a name no mood in use has
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
     * Stores a new mood of the user behind the existing ones.
     *
     * @param mood the mood, without id or with an id no mood has
     * @return true if it was stored, false if the name is taken
     */
    public boolean add(Mood mood) {
        if (mood.getName() == null || mood.getName().isBlank() || exists(mood.getName())) {
            logger.warn("Cannot add mood '{}' - name is empty or already exists", mood.getName());
            return false;
        }

        /*  Moods of the user follow the standard moods, from 1000 on  */
        mood.setSortOrder(Math.max(1000, repository.findAll().stream()
                .mapToInt((Mood existing) -> existing.getSortOrder() + 1).max().orElse(1000)));
        return repository.save(mood);
    }

    /**
     * Updates a mood of the user: its name and its blocks. Standard moods cannot be changed.
     *
     * @param mood the changed mood with its id
     * @return true if it was updated, false for a standard mood, an unknown id or a taken name
     */
    public boolean update(Mood mood) {
        Mood existing = mood.isDefault() ? null : repository.findById(mood.getId());
        if (existing == null) {
            logger.warn("Cannot update mood '{}' - no mood of the user", mood.getName());
            return false;
        }
        if (mood.getName() == null || mood.getName().isBlank()
                || !existing.getName().equals(mood.getName()) && exists(mood.getName())) {
            logger.warn("Cannot rename mood to '{}' - name is empty or already exists", mood.getName());
            return false;
        }
        mood.setSortOrder(existing.getSortOrder());
        return repository.update(mood);
    }

    /**
     * Puts the moods of the user in a new order.
     *
     * @param ids the ids of the moods of the user in the wanted order
     */
    public void reorder(List<String> ids) {
        for (int i = 0; i < ids.size(); i++) {
            Mood mood = repository.findById(ids.get(i));
            if (mood != null) {
                mood.setSortOrder(1000 + i);
                repository.update(mood);
            }
        }
    }

    /**
     * Moves a mood of the user to the trash; standard moods cannot be deleted.
     *
     * @param id the mood id
     * @return true if it is in the trash now
     */
    public boolean moveToTrash(String id) {
        return id != null && !id.startsWith(PromptBlock.STANDARD_PREFIX) && repository.moveToTrash(id);
    }

    /**
     * Takes a mood out of the trash. If another mood took its name meanwhile, it gets the next free one.
     *
     * @param id the mood id
     * @return true if it is in use again
     */
    public boolean restore(String id) {
        Mood mood = repository.findDeleted().stream().filter((Mood deleted) -> deleted.getId().equals(id))
                .findFirst().orElse(null);
        if (mood == null)
            return false;
        if (exists(mood.getName())) {
            mood.setName(freeName(mood.getName()));
            repository.update(mood);
        }
        return repository.restore(id);
    }

    /**
     * Removes a mood of the user for good.
     *
     * @param id the mood id
     * @return true if it is gone
     */
    public boolean deletePermanently(String id) {
        return id != null && !id.startsWith(PromptBlock.STANDARD_PREFIX) && repository.delete(id);
    }

    /**
     * The block ids the fields take when a mood is chosen: a locked field keeps its block, every other field takes
     * the block of the mood, or none if the mood leaves it empty or its block is not in use.
     *
     * @param mood the chosen mood, null for "No mood", which empties every field that is not locked
     * @param current the block ids of the fields now; a missing field is empty
     * @param locked the locked fields
     * @return the block ids of the fields; a missing field is empty
     */
    public Map<Mood.Field, String> apply(Mood mood, Map<Mood.Field, String> current, Set<Mood.Field> locked) {
        Map<Mood.Field, String> fields = new EnumMap<>(Mood.Field.class);
        for (Mood.Field field : Mood.Field.values()) {
            boolean keep = locked.contains(field);
            String id = keep ? current.get(field) : mood != null ? mood.getReference(field) : null;
            if (id != null && (keep || inUse(field, id)))
                fields.put(field, id);
        }
        return fields;
    }

    /**
     * Whether the fields differ from a chosen mood: a field, locked or not, holds another block than the mood sets.
     * A field left empty as the block of the mood is not in use counts as unchanged.
     *
     * @param mood the chosen mood, null for "No mood", which is never changed
     * @param fields the block ids of the fields; a missing field is empty
     * @return whether a field differs from the mood
     */
    public boolean isChanged(Mood mood, Map<Mood.Field, String> fields) {
        return mood != null && !apply(mood, fields, Set.of()).equals(fields);
    }

    /**
     * The blocks a mood saved from the fields refers to: the block of each field. A field left empty as its block
     * lies in the trash keeps the reference of the chosen mood.
     *
     * @param chosen the chosen mood, null for "No mood"
     * @param fields the block ids of the fields; a missing field is empty
     * @return the block ids of the mood to save; a missing field is empty
     */
    public Map<Mood.Field, String> capture(Mood chosen, Map<Mood.Field, String> fields) {
        Map<Mood.Field, String> references = new EnumMap<>(Mood.Field.class);
        List<String> trashed = chosen != null ? trashedBlocks(chosen) : List.of();
        for (Mood.Field field : Mood.Field.values()) {
            String kept = chosen != null ? chosen.getReference(field) : null;
            String id = fields.get(field) != null ? fields.get(field)
                    : kept != null && trashed.contains(kept) ? kept : null;
            if (id != null)
                references.put(field, id);
        }
        return references;
    }

    /**
     * Lists the moods that use a building block, for the question before the block goes to the trash.
     *
     * @param blockId the ID of a text block or a palette
     * @return the names of the moods, empty if none uses the block
     */
    @Override
    public List<String> moodsUsing(String blockId) {
        return getAll().stream().filter((Mood mood) -> mood.getReferences().containsValue(blockId))
                .map(Mood::getName).toList();
    }

    /**
     * The blocks of a mood that lie in the trash; their fields stay empty until they are restored.
     *
     * @param mood the mood
     * @return the ids of the blocks in the trash, in the order of the fields
     */
    public List<String> trashedBlocks(Mood mood) {
        List<String> trashed = new ArrayList<>();
        mood.getReferences().forEach((Mood.Field field, String id) -> {
            if (inTrash(field, id))
                trashed.add(id);
        });
        return trashed;
    }

    /**
     * The building blocks a mood puts into the prompt (see {@link PromptComposer}); the mood adds no text of its own.
     *
     * @param mood the mood
     * @return the blocks in use
     */
    public PromptComposer.Selection selection(Mood mood) {
        Map<Mood.Field, String> fields = apply(mood, Map.of(), Set.of());
        return new PromptComposer.Selection(block(Mood.Field.STYLE, fields.get(Mood.Field.STYLE)),
                block(Mood.Field.ATMOSPHERE, fields.get(Mood.Field.ATMOSPHERE)),
                block(Mood.Field.LIGHT, fields.get(Mood.Field.LIGHT)),
                block(Mood.Field.CAMERA, fields.get(Mood.Field.CAMERA)), colors.getStyleById(fields.get(Mood.Field.COLOR)));
    }

    /**
     * Names the blocks of a mood, in use or in the trash, for example {@code "Melancholic · Analog Film"}.
     *
     * @param mood the mood
     * @return the names joined by a middle dot, empty if no block is known
     */
    public String describe(Mood mood) {
        List<String> names = new ArrayList<>();
        mood.getReferences().forEach((Mood.Field field, String id) -> {
            if (field == Mood.Field.COLOR) {
                ColorStyle palette = colors.getStyleById(id);
                palette = palette != null ? palette : colors.findInTrash(id);
                if (palette != null)
                    names.add(palette.getName());
            }
            else {
                PromptBlock block = block(field, id);
                block = block != null ? block : blocks.findInTrash(id);
                if (block != null)
                    names.add(block.getName());
            }
        });
        return String.join(" · ", names);
    }

    /**
     * Copies the mood and the building blocks that go into the prompt of a chat entry, so the entry stays traceable
     * when a block is changed or deleted later; a camera keeps its settings. A changed mood becomes the base of a
     * combination of its own, with the fields whose block differs from it.
     *
     * @param mood the chosen mood, null for "No mood"
     * @param changed whether the fields differ from the chosen mood
     * @param selection the blocks that go into the prompt
     * @return the copy, null if no block goes into the prompt
     */
    public EntryMood snapshot(Mood mood, boolean changed, PromptComposer.Selection selection) {
        Map<Mood.Field, EntryMood.Block> copies = new EnumMap<>(Mood.Field.class);
        copy(copies, Mood.Field.ATMOSPHERE, selection.atmosphere());
        copy(copies, Mood.Field.STYLE, selection.style());
        copy(copies, Mood.Field.LIGHT, selection.light());
        copy(copies, Mood.Field.CAMERA, selection.camera());

        /*  The palette of the main window carries no id, the stored one of the same name does  */
        ColorStyle palette = selection.palette();
        if (palette != null) {
            ColorStyle stored = palette.getId() != null ? palette : colors.getStyleByName(palette.getName());
            copies.put(Mood.Field.COLOR, new EntryMood.Block(stored != null ? stored.getId() : null, palette.getName(),
                    null, null, false, palette.getColors().stream()
                            .filter((String color) -> color != null && !color.isEmpty()).toList(), null));
        }
        if (copies.isEmpty())
            return null;
        if (mood == null || !changed)
            return new EntryMood(mood != null ? mood.getId() : null, mood != null ? mood.getName() : null, copies);
        Set<Mood.Field> differing = EnumSet.noneOf(Mood.Field.class);
        for (Mood.Field field : Mood.Field.values()) {
            EntryMood.Block copy = copies.get(field);
            if (!Objects.equals(mood.getReference(field), copy != null ? copy.id() : null))
                differing.add(field);
        }
        return new EntryMood(null, null, copies, mood.getId(), mood.getName(), differing);
    }

    /**
     * The fields of the copy at a chat entry whose block is not in use anymore, as it was deleted or lies in the trash.
     *
     * @param entryMood the copy at the entry
     * @return the fields, in their order
     */
    public List<Mood.Field> missingBlocks(EntryMood entryMood) {
        return entryMood.blocks().entrySet().stream()
                .filter((Map.Entry<Mood.Field, EntryMood.Block> copy) -> !inUse(copy.getKey(), copy.getValue().id()))
                .map(Map.Entry::getKey).toList();
    }

    /**
     * The blocks the copies at a chat entry stand for now: a block in use stays itself; a block not in use anymore is
     * created anew as a block of the user from its copy, under the next free name, if {@code recreate} is set.
     *
     * @param entryMood the copy at the entry
     * @param recreate whether to create the blocks not in use anymore from their copies
     * @return the block ids per field; a field whose block is not in use and not created anew is missing
     */
    public Map<Mood.Field, String> references(EntryMood entryMood, boolean recreate) {
        Map<Mood.Field, String> references = new EnumMap<>(Mood.Field.class);
        entryMood.blocks().forEach((Mood.Field field, EntryMood.Block copy) -> {
            String id = inUse(field, copy.id()) ? copy.id() : recreate ? recreate(field, copy) : null;
            if (id != null)
                references.put(field, id);
        });
        return references;
    }

    /**
     * Checks if a style name is one of the looks that left the styles in 2.0.
     *
     * @param styleName the style name of an old selection, chat entry or brainstorm draft
     * @return true if a standard mood of the same name took its place
     */
    public static boolean isLegacyLook(String styleName) {
        return styleName != null && LEGACY_LOOKS.contains(styleName);
    }

    /**
     * The standard mood that took the place of a former look style.
     *
     * @param styleName the style name of an old selection, chat entry or brainstorm draft
     * @return the mood of the same name, null if the name is no former look
     */
    public Mood findForLegacyStyle(String styleName) {

        /*  a style that exists under this name (for example one of the user called "Film Noir") stays a style  */
        if (!isLegacyLook(styleName) || blocks.exists(PromptBlock.Type.STYLE, styleName))
            return null;
        return find(PromptBlock.STANDARD_PREFIX + "mood:" + PromptBlockService.slug(styleName));
    }

    /**
     * Drops the references to blocks that are deleted for good. A mood in use that loses its last block this way is
     * remembered for {@link #takeEmptied}.
     *
     * @param blockIds the ids of the deleted blocks
     */
    public synchronized void blocksDeleted(List<String> blockIds) {
        List<Mood> affected = repository.findAll().stream().filter((Mood mood) -> !mood.isEmpty()
                && mood.getReferences().values().stream().anyMatch(blockIds::contains)).toList();
        blockIds.forEach(repository::dropReference);
        for (Mood mood : affected) {
            Mood after = repository.findById(mood.getId());
            if (after != null && after.isEmpty())
                emptied.add(after);
        }
    }

    /**
     * Returns the moods that lost their last block since the last call, and forgets them.
     *
     * @return the moods that are still in use and still empty
     */
    public synchronized List<Mood> takeEmptied() {
        List<Mood> moods = emptied.stream().map((Mood mood) -> repository.findById(mood.getId()))
                .filter((Mood mood) -> mood != null && mood.isEmpty()).toList();
        emptied.clear();
        return moods;
    }

    /**
     * Returns the moods of the user as plain data, e.g. for the full backup ({@link ConfigurationExporter}).
     *
     * @return per mood its id, its name and the block id of each field it sets, keyed by the field in lower case
     */
    public List<Map<String, Object>> exportCustom() {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Mood mood : getCustom()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", mood.getId());
            entry.put("name", mood.getName());
            mood.getReferences().forEach((Mood.Field field, String id) ->
                    entry.put(field.name().toLowerCase(Locale.ROOT), id));
            entries.add(entry);
        }
        return entries;
    }

    /**
     * Adds moods from plain data (see {@link #exportCustom()}) behind the moods of the user. A mood keeps its id
     * unless a mood has it already, and its name unless a mood in use has it already; then it gets a new one.
     *
     * @param entries the moods as maps
     * @return the number of imported moods
     */
    public int importCustom(List<Map<String, Object>> entries) {
        int imported = 0;
        for (Map<String, Object> entry : entries) {
            String name = entry.get("name") instanceof String text ? text.trim() : "";
            if (name.isEmpty())
                continue;
            String id = entry.get("id") instanceof String text ? text : "";
            Mood mood = new Mood(id.isEmpty() || id.startsWith(PromptBlock.STANDARD_PREFIX) || repository.exists(id)
                    ? null : id, freeName(name));
            for (Mood.Field field : Mood.Field.values())
                mood.setReference(field, entry.get(field.name().toLowerCase(Locale.ROOT)) instanceof String blockId
                        ? blockId : null);
            if (add(mood))
                imported++;
        }
        return imported;
    }

    /**
     * Exports the moods of the user to a JSON file.
     *
     * @param file the target file
     * @return true if it was written
     */
    public boolean exportMoods(File file) {
        try {
            Files.writeString(file.toPath(), new JSONArray(exportCustom()).toString(2));
            return true;
        } catch (Exception e) {
            logger.error("Failed to export moods to {}", file.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Imports moods from a JSON file behind the moods of the user (see {@link #importCustom}).
     *
     * @param file the source file
     * @return the number of imported moods, -1 if the file cannot be read
     */
    public int importMoods(File file) {
        List<Map<String, Object>> entries = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(Files.readString(file.toPath()));
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json != null)
                    entries.add(json.toMap());
            }
        } catch (Exception e) {
            logger.error("Failed to import moods from {}", file.getAbsolutePath(), e);
            return -1;
        }
        return importCustom(entries);
    }

    private static void copy(Map<Mood.Field, EntryMood.Block> copies, Mood.Field field, PromptBlock block) {
        if (block != null)
            copies.put(field, new EntryMood.Block(block.getId(), block.getName(), block.getText(), block.getGroup(),
                    block.isPhotographic(), List.of(), block.getParams()));
    }

    /** Creates a block of the user from a copy under the next free name; returns its id, null if it was not stored. */
    private String recreate(Mood.Field field, EntryMood.Block copy) {
        if (field == Mood.Field.COLOR) {
            ColorStyle palette = new ColorStyle(copy.name(), copy.colors());
            return colors.addCopy(palette) ? palette.getId() : null;
        }

        /*  The text fields carry the names of their block types; a camera brings its settings  */
        PromptBlock block = new PromptBlock(null, PromptBlock.Type.valueOf(field.name()), copy.name(), copy.text(),
                copy.group(), copy.photographic());
        block.setParams(copy.params());
        return blocks.addCopy(block) ? block.getId() : null;
    }

    /** True if the block a field refers to is in use. */
    private boolean inUse(Mood.Field field, String id) {
        return switch (field) {
            case ATMOSPHERE, STYLE, LIGHT, CAMERA -> block(field, id) != null;
            case COLOR -> colors.getStyleById(id) != null;
        };
    }

    private boolean inTrash(Mood.Field field, String id) {
        return field == Mood.Field.COLOR ? colors.findInTrash(id) != null : blocks.findInTrash(id) != null;
    }

    /** The block in use with exactly this id, null for none or the color. */
    private PromptBlock block(Mood.Field field, String id) {
        PromptBlock.Type type = switch (field) {
            case ATMOSPHERE -> PromptBlock.Type.ATMOSPHERE;
            case STYLE -> PromptBlock.Type.STYLE;
            case LIGHT -> PromptBlock.Type.LIGHT;
            case CAMERA -> PromptBlock.Type.CAMERA;
            default -> null;
        };
        PromptBlock block = type != null && id != null ? blocks.find(type, id) : null;
        return block != null && block.getId().equals(id) ? block : null;
    }
}
