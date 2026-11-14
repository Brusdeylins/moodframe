package art.moodframe.mcp;

import art.moodframe.model.AspectRatios;
import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.CameraSettings;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.Frame;
import art.moodframe.model.ModelFamilies;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.BrainstormContext;
import art.moodframe.service.CameraPromptBuilder;
import art.moodframe.service.ChatLibrary;
import art.moodframe.service.ColorStyleService;
import art.moodframe.service.FavoriteService;
import art.moodframe.service.FrameService;
import art.moodframe.service.MoodAnalysisService;
import art.moodframe.service.MoodService;
import art.moodframe.service.PromptBlockService;
import art.moodframe.service.TrashCenter;
import art.moodframe.service.TrashGroup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The tools that change the library: {@code create_mood}, {@code update_mood}, {@code delete_mood},
 * {@code mood_from_image}, {@code create_block}, {@code update_block}, {@code delete_block}, {@code create_frame},
 * {@code update_frame}, {@code delete_frame}, {@code set_favorite} and {@code restore}. As in the main window, the
 * standard entries can only be read, a taken name is refused, and deleting moves to the trash; deleting for good is
 * left to the trash window. The services report every change, so the main window shows it at once.
 *
 * @since 2.1.0
 */
class LibraryEditTools {

    /*  the types of building blocks and the fields of a mood, in the order of the right panel  */
    private static final List<String> TYPES = List.of("atmosphere", "style", "color", "light", "camera");

    /*  the arguments that carry the content of a block, and those each type takes  */
    private static final List<String> CONTENT = List.of("text", "photographic", "colors", "settings");
    private static final Map<String, Set<String>> CONTENT_OF = Map.of("atmosphere", Set.of("text"),
            "style", Set.of("text", "photographic"), "light", Set.of("text"), "color", Set.of("colors"),
            "camera", Set.of("settings"));

    private static final List<String> FAVORITES = List.of("mood", "frame", "atmosphere", "style", "color", "light",
            "camera");
    private static final List<String> TRASH = Arrays.stream(TrashGroup.Kind.values())
            .map((TrashGroup.Kind kind) -> kind.name().toLowerCase(Locale.ROOT)).toList();

    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");
    private static final int MAX_COLORS = 5;

    /*  the long side of the image the analysis gets, as in the dialog "Mood from image"  */
    private static final int ANALYSIS_SIDE = 1024;

    private final ChatLibrary chats;
    private final MoodService moods;
    private final PromptBlockService blocks;
    private final ColorStyleService colors;
    private final FrameService frames;
    private final FavoriteService favorites;
    private final TrashCenter trash;
    private final MoodAnalysisService analysis;

    /**
     * Creates the tools.
     *
     * @param chats     the chats, for images named by their ID
     * @param moods     the moods
     * @param blocks    the text building blocks
     * @param colors    the palettes
     * @param frames    the frames
     * @param favorites the favorites
     * @param trash     the trash
     * @param analysis  finds the building blocks of an image
     */
    LibraryEditTools(ChatLibrary chats, MoodService moods, PromptBlockService blocks, ColorStyleService colors,
            FrameService frames, FavoriteService favorites, TrashCenter trash, MoodAnalysisService analysis) {
        this.chats = chats;
        this.moods = moods;
        this.blocks = blocks;
        this.colors = colors;
        this.frames = frames;
        this.favorites = favorites;
        this.trash = trash;
        this.analysis = analysis;
    }

    /**
     * Adds the tools.
     *
     * @param tools where they are added
     */
    void addTo(McpTools tools) {
        ObjectNode fields = McpTools.JSON.createObjectNode().put("type", "object")
                .put("description", "The building blocks by field, each by its id or name (see list_blocks); null or "
                        + "empty leaves the field empty")
                .put("additionalProperties", false);
        ObjectNode fieldSchemas = fields.putObject("properties");
        for (String type : TYPES)
            fieldSchemas.set(type, McpTools.nullableString("The " + type + " block"));

        ObjectNode createMood = McpTools.JSON.createObjectNode();
        createMood.set("name", McpTools.string("The name, not taken by another mood"));
        createMood.set("blocks", fields.deepCopy().put("description", "The building blocks by field, each by its id "
                + "or name (see list_blocks); a missing, null or empty field stays empty"));
        tools.add("create_mood", "Creates an own mood from a name and up to five building blocks; returns its id.",
                McpTools.input(createMood, "name"), this::createMood);

        ObjectNode updateMood = McpTools.JSON.createObjectNode();
        updateMood.set("moodId", McpTools.string("The ID of an own mood, see list_moods"));
        updateMood.set("name", McpTools.string("The new name, not taken by another mood"));
        updateMood.set("blocks", fields.deepCopy().put("description", "The building blocks to change by field, each "
                + "by its id or name (see list_blocks); null or empty empties the field, a missing field stays"));
        tools.add("update_mood", "Renames an own mood or changes its building blocks. Standard moods can only be "
                + "read.", McpTools.input(updateMood, "moodId"), this::updateMood);

        ObjectNode moodId = McpTools.JSON.createObjectNode();
        moodId.set("moodId", McpTools.string("The ID of an own mood, see list_moods"));
        tools.add("delete_mood", "Moves an own mood to the trash; restore brings it back.",
                McpTools.input(moodId, "moodId"), this::deleteMood);

        ObjectNode image = McpTools.JSON.createObjectNode();
        image.set("image", McpTools.string("The ID of an image (see get_entry) or the absolute path of an image "
                + "file"));
        tools.add("mood_from_image", "Finds the building blocks of an image like 'Mood from image' in MoodFrame: "
                + "the palette from its pixels, atmosphere, style, light and camera by the language model of the "
                + "brainstorm, which costs one request (without one set up, only the palette). Returns per field an "
                + "existing block (id) or a new one (name and content); nothing is stored, create_block and "
                + "create_mood take the result.", McpTools.input(image, "image"), this::moodFromImage);

        ObjectNode createBlock = blockSchema();
        createBlock.set("name", McpTools.string("The name, not taken by another block of the type"));
        tools.add("create_block", "Creates an own building block: atmosphere, style and light with a prompt text "
                + "(a style also photographic or not, default true), color as a palette of 1 to 5 colors, camera "
                + "with settings whose values get_capabilities lists. Returns its id.",
                McpTools.input(createBlock, "type", "name"), this::createBlock);

        ObjectNode updateBlock = blockSchema();
        updateBlock.set("blockId", McpTools.string("The ID of an own block, see list_blocks"));
        updateBlock.set("name", McpTools.string("The new name, not taken by another block of the type"));
        tools.add("update_block", "Changes an own building block: name and content as for create_block; what is "
                + "missing stays, camera settings change one by one (null for any). Standard blocks can only be read.",
                McpTools.input(updateBlock, "type", "blockId"), this::updateBlock);

        ObjectNode block = McpTools.JSON.createObjectNode();
        block.set("type", McpTools.choice("The type of building block; 'color' are the palettes", TYPES, null));
        block.set("blockId", McpTools.string("The ID of an own block, see list_blocks"));
        tools.add("delete_block", "Moves an own building block to the trash and names the moods that use it; their "
                + "field stays empty until restore brings the block back.", McpTools.input(block, "type", "blockId"),
                this::deleteBlock);

        ObjectNode createFrame = McpTools.JSON.createObjectNode();
        createFrame.set("name", McpTools.string("The name, not taken by another frame"));
        createFrame.set("aspectRatio", McpTools.choice("The target aspect ratio", Arrays.asList(AspectRatios.ALL),
                null));
        createFrame.set("resolution", McpTools.choice("The target resolution; without one, the frame keeps the "
                + "resolution", ModelFamilies.RESOLUTIONS, null));
        tools.add("create_frame", "Creates an own frame: name, aspect ratio and resolution, which the models take "
                + "as closely as they can; returns its id.", McpTools.input(createFrame, "name", "aspectRatio"),
                this::createFrame);

        ObjectNode updateFrame = McpTools.JSON.createObjectNode();
        updateFrame.set("frameId", McpTools.string("The ID of an own frame, see list_frames"));
        updateFrame.set("name", McpTools.string("The new name, not taken by another frame"));
        updateFrame.set("aspectRatio", McpTools.choice("The target aspect ratio", Arrays.asList(AspectRatios.ALL),
                null));
        ObjectNode resolution = McpTools.nullableString("The target resolution, null for none");
        resolution.set("enum", McpTools.JSON.valueToTree(ModelFamilies.RESOLUTIONS));
        ((ArrayNode) resolution.get("enum")).addNull();
        updateFrame.set("resolution", resolution);
        tools.add("update_frame", "Changes name, aspect ratio or resolution of an own frame; what is missing stays. "
                + "Standard frames can only be read.", McpTools.input(updateFrame, "frameId"), this::updateFrame);

        ObjectNode frameId = McpTools.JSON.createObjectNode();
        frameId.set("frameId", McpTools.string("The ID of an own frame, see list_frames"));
        tools.add("delete_frame", "Moves an own frame to the trash; restore brings it back.",
                McpTools.input(frameId, "frameId"), this::deleteFrame);

        ObjectNode favorite = McpTools.JSON.createObjectNode();
        favorite.set("kind", McpTools.choice("What the id names; 'color' are the palettes", FAVORITES, null));
        favorite.set("id", McpTools.string("The ID of a mood, frame or building block, standard or own"));
        favorite.set("favorite", McpTools.bool("true marks it as favorite, false takes the mark away"));
        tools.add("set_favorite", "Marks a mood, a frame or a building block as favorite or takes the mark away; "
                + "favorites come first in the lists of MoodFrame.", McpTools.input(favorite, "kind", "id",
                "favorite"), this::setFavorite);

        ObjectNode restore = McpTools.JSON.createObjectNode();
        restore.set("kind", McpTools.choice("The group of the trash, see list_trash", TRASH, null));
        restore.set("id", McpTools.string("The ID of the item or of one of its parts, see list_trash"));
        tools.add("restore", "Takes an item out of the trash, back into use; one whose name was taken meanwhile gets "
                + "the next free one.", McpTools.input(restore, "kind", "id"), this::restore);
    }

    /*  the type and the content of a block, shared by create_block and update_block  */
    private static ObjectNode blockSchema() {
        ObjectNode schema = McpTools.JSON.createObjectNode();
        schema.set("type", McpTools.choice("The type of building block; 'color' are the palettes", TYPES, null));
        schema.set("text", McpTools.string("The prompt text of an atmosphere, a style or a light"));
        schema.set("photographic", McpTools.bool("Whether a style describes a photograph (camera, else framing)"));
        schema.set("colors", McpTools.strings("The colors of a palette as #RRGGBB", MAX_COLORS));
        ObjectNode settings = McpTools.JSON.createObjectNode().put("type", "object")
                .put("description", "The settings of a camera; each value one of get_capabilities.camera, null or "
                        + "missing for any")
                .put("additionalProperties", false);
        ObjectNode properties = settings.putObject("properties");
        for (String setting : List.of("body", "model", "lens", "aperture", "shutter", "iso", "perspective", "shot"))
            properties.set(setting, McpTools.nullableString("The " + setting));
        schema.set("settings", settings);
        return schema;
    }

    /*  ===== Moods =====  */

    private McpTools.Result createMood(JsonNode arguments) throws McpTools.ToolException {
        String name = McpTools.text(arguments, "name");
        if (moods.exists(name))
            throw taken("mood", name, moods.freeName(name));
        Mood mood = new Mood(null, name);
        setBlocks(blocks, colors, mood, arguments.get("blocks"));
        if (!moods.add(mood))
            throw new McpTools.ToolException("The mood '" + name + "' could not be stored");
        return new McpTools.Result(mood(mood));
    }

    private McpTools.Result updateMood(JsonNode arguments) throws McpTools.ToolException {
        Mood mood = ownMood(McpTools.text(arguments, "moodId"));
        String name = McpTools.optionalText(arguments, "name");
        if (name != null && !name.equals(mood.getName()) && moods.exists(name))
            throw taken("mood", name, moods.freeName(name));
        if (name != null)
            mood.setName(name);
        setBlocks(blocks, colors, mood, arguments.get("blocks"));
        if (!moods.update(mood))
            throw new McpTools.ToolException("The mood '" + mood.getName() + "' could not be stored");
        return new McpTools.Result(mood(mood));
    }

    private McpTools.Result deleteMood(JsonNode arguments) throws McpTools.ToolException {
        Mood mood = ownMood(McpTools.text(arguments, "moodId"));
        if (!moods.moveToTrash(mood.getId()))
            throw new McpTools.ToolException("The mood '" + mood.getName() + "' could not be moved to the trash");
        return new McpTools.Result(trashed(mood.getId(), mood.getName()));
    }

    /*  a mood of the user in use; a standard mood can only be read  */
    private Mood ownMood(String id) throws McpTools.ToolException {
        Mood mood = moods.find(id);
        if (mood == null)
            throw new McpTools.ToolException("No mood with the ID '" + id + "', see list_moods");
        if (mood.isDefault())
            throw standard("mood", mood.getName(), "create_mood");
        return mood;
    }

    /**
     * Sets the fields of a mood the JSON object names: a block by its id or name, null or empty for none.
     *
     * @param blocks the text building blocks
     * @param colors the palettes
     * @param mood   the mood
     * @param fields the JSON object, null to set nothing
     * @throws McpTools.ToolException for an unknown field or block
     */
    static void setBlocks(PromptBlockService blocks, ColorStyleService colors, Mood mood, JsonNode fields)
            throws McpTools.ToolException {
        if (fields == null || fields.isNull())
            return;
        if (!fields.isObject())
            throw new McpTools.ToolException("The argument 'blocks' must be an object with the fields "
                    + String.join(", ", TYPES));
        for (Iterator<String> names = fields.fieldNames(); names.hasNext(); ) {
            String key = names.next();
            JsonNode value = fields.get(key);
            if (!TYPES.contains(key) || !value.isNull() && !value.isTextual())
                throw new McpTools.ToolException("'blocks' takes the fields " + String.join(", ", TYPES)
                        + ", each with the id or name of a block or null");
            Mood.Field field = Mood.Field.valueOf(key.toUpperCase(Locale.ROOT));
            mood.setReference(field, value.isNull() || value.asText().isBlank() ? null
                    : blockId(blocks, colors, field, value.asText().trim()));
        }
    }

    /**
     * Finds a building block in use by its id or name.
     *
     * @param blocks    the text building blocks
     * @param colors    the palettes
     * @param field     the field the block is for
     * @param reference its id or name
     * @return its id
     * @throws McpTools.ToolException if there is no such block
     */
    static String blockId(PromptBlockService blocks, ColorStyleService colors, Mood.Field field, String reference)
            throws McpTools.ToolException {
        String id;
        if (field == Mood.Field.COLOR) {
            ColorStyle palette = colors.getStyleById(reference);
            palette = palette != null ? palette : colors.getStyleByName(reference);
            id = palette != null ? palette.getId() : null;
        }
        else {
            PromptBlock block = blocks.find(PromptBlock.Type.valueOf(field.name()), reference);
            id = block != null ? block.getId() : null;
        }
        if (id == null)
            throw new McpTools.ToolException("No " + field.name().toLowerCase(Locale.ROOT) + " block with the id or "
                    + "name '" + reference + "', see list_blocks");
        return id;
    }

    /*  a mood with the id of the block of each field  */
    private static ObjectNode mood(Mood mood) {
        ObjectNode node = McpTools.JSON.createObjectNode().put("id", mood.getId()).put("name", mood.getName());
        ObjectNode fields = node.putObject("blocks");
        for (Mood.Field field : Mood.Field.values())
            fields.put(field.name().toLowerCase(Locale.ROOT), mood.getReference(field));
        return node;
    }

    private McpTools.Result moodFromImage(JsonNode arguments) throws McpTools.ToolException, IOException {
        Path file = ChatTools.imageFile(chats, McpTools.text(arguments, "image"));
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null)
            throw new McpTools.ToolException("'" + file + "' is no image MoodFrame can read");

        BrainstormContext context = new BrainstormContext(blocks.getAll(PromptBlock.Type.STYLE),
                blocks.getAll(PromptBlock.Type.ATMOSPHERE), blocks.getAll(PromptBlock.Type.LIGHT),
                blocks.getAll(PromptBlock.Type.CAMERA), colors.getAllStyles(), List.of(), List.of(), "", List.of(),
                Set.of());
        MoodAnalysisService.Analysis found = analysis.analyze(scaled(image), context).join();

        ObjectNode result = McpTools.JSON.createObjectNode();
        ObjectNode fields = result.putObject("blocks");
        for (Mood.Field field : Mood.Field.values()) {
            BrainstormDraft.Block block = found.blocks().get(field);
            String key = field.name().toLowerCase(Locale.ROOT);
            if (block == null) {
                fields.putNull(key);
                continue;
            }
            ObjectNode node = fields.putObject(key).put("id", block.id()).put("name", block.name())
                    .put("new", block.isNew());
            if (block.isNew() && block.text() != null)
                node.put("text", block.text());
            if (block.isNew() && field == Mood.Field.COLOR)
                node.set("colors", McpTools.JSON.valueToTree(block.colors()));
            if (block.isNew() && block.settings() != null)
                node.set("settings", McpTools.parse(block.settings().toJson()));
        }
        return new McpTools.Result(result.put("notice", found.notice()));
    }

    /*  the image with its long side at most as long as the analysis takes it  */
    private static BufferedImage scaled(BufferedImage image) {
        double scale = (double) ANALYSIS_SIDE / Math.max(image.getWidth(), image.getHeight());
        if (scale >= 1)
            return image;
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(image, 0, 0, width, height, null);
        graphics.dispose();
        return scaled;
    }

    /*  ===== Building blocks =====  */

    private McpTools.Result createBlock(JsonNode arguments) throws McpTools.ToolException {
        String type = blockType(arguments);
        String name = McpTools.text(arguments, "name");
        if ("color".equals(type)) {
            if (colors.styleExists(name))
                throw taken("palette", name, colors.freeName(name));
            ColorStyle palette = new ColorStyle(name, palette(arguments.get("colors"), null), false);
            if (!colors.addCustomStyle(palette))
                throw new McpTools.ToolException("The palette '" + name + "' could not be stored");
            return new McpTools.Result(block(type, palette.getId(), name));
        }

        PromptBlock.Type blockType = PromptBlock.Type.valueOf(type.toUpperCase(Locale.ROOT));
        if (blocks.exists(blockType, name))
            throw taken(type + " block", name, blocks.freeName(blockType, name));
        PromptBlock block = blockType == PromptBlock.Type.CAMERA
                ? PromptBlockService.camera(null, name, settings(arguments.get("settings"), CameraSettings.ANY))
                : new PromptBlock(null, blockType, name, McpTools.text(arguments, "text"), null,
                        blockType == PromptBlock.Type.STYLE && McpTools.flag(arguments, "photographic", true));
        if (!blocks.addCustom(block))
            throw new McpTools.ToolException("The " + type + " block '" + name + "' could not be stored");
        return new McpTools.Result(block(type, block.getId(), name));
    }

    private McpTools.Result updateBlock(JsonNode arguments) throws McpTools.ToolException {
        String type = blockType(arguments);
        String blockId = McpTools.text(arguments, "blockId");
        String name = McpTools.optionalText(arguments, "name");
        if ("color".equals(type)) {
            ColorStyle palette = ownPalette(blockId);
            String newName = name != null ? name : palette.getName();
            if (!newName.equals(palette.getName()) && colors.styleExists(newName))
                throw taken("palette", newName, colors.freeName(newName));
            ColorStyle changed = new ColorStyle(newName, palette(arguments.get("colors"), palette.getColors()), false);
            if (!colors.updateCustomStyle(palette.getName(), changed))
                throw new McpTools.ToolException("The palette '" + newName + "' could not be stored");
            return new McpTools.Result(block(type, blockId, newName));
        }

        PromptBlock block = ownBlock(type, blockId);
        if (name != null && !name.equals(block.getName()) && blocks.exists(block.getType(), name))
            throw taken(type + " block", name, blocks.freeName(block.getType(), name));
        if (name != null)
            block.setName(name);
        if (block.getType() == PromptBlock.Type.CAMERA) {
            PromptBlock camera = PromptBlockService.camera(blockId, block.getName(),
                    settings(arguments.get("settings"), CameraSettings.fromJson(block.getParams())));
            camera.setSortOrder(block.getSortOrder());
            block = camera;
        }
        else {
            String text = McpTools.optionalText(arguments, "text");
            if (text != null)
                block.setText(text);
            block.setPhotographic(McpTools.flag(arguments, "photographic", block.isPhotographic()));
        }
        if (!blocks.updateCustom(block))
            throw new McpTools.ToolException("The " + type + " block '" + block.getName() + "' could not be stored");
        return new McpTools.Result(block(type, blockId, block.getName()));
    }

    private McpTools.Result deleteBlock(JsonNode arguments) throws McpTools.ToolException {
        String type = McpTools.choice(arguments, "type", TYPES, null);
        String blockId = McpTools.text(arguments, "blockId");
        String name;
        boolean deleted;
        if ("color".equals(type)) {
            name = ownPalette(blockId).getName();
            deleted = colors.deleteCustomStyle(name);
        }
        else {
            name = ownBlock(type, blockId).getName();
            deleted = blocks.deleteCustom(blockId);
        }
        if (!deleted)
            throw new McpTools.ToolException("The " + type + " block '" + name + "' could not be moved to the trash");
        ObjectNode result = trashed(blockId, name);
        result.set("moods", McpTools.JSON.valueToTree(moods.moodsUsing(blockId)));
        return new McpTools.Result(result);
    }

    /*  the type of the block, and no content another type takes  */
    private static String blockType(JsonNode arguments) throws McpTools.ToolException {
        String type = McpTools.choice(arguments, "type", TYPES, null);
        for (String content : CONTENT)
            if (arguments.has(content) && !CONTENT_OF.get(type).contains(content))
                throw new McpTools.ToolException("The argument '" + content + "' does not apply to a " + type
                        + " block");
        return type;
    }

    /*  a text block of the user in use with exactly this id; a standard block can only be read  */
    private PromptBlock ownBlock(String type, String id) throws McpTools.ToolException {
        PromptBlock block = blocks.find(PromptBlock.Type.valueOf(type.toUpperCase(Locale.ROOT)), id);
        if (block == null || !block.getId().equals(id))
            throw new McpTools.ToolException("No " + type + " block with the ID '" + id + "', see list_blocks");
        if (block.isDefault())
            throw standard(type + " block", block.getName(), "create_block");
        return block;
    }

    /*  a palette of the user in use; a standard palette can only be read  */
    private ColorStyle ownPalette(String id) throws McpTools.ToolException {
        ColorStyle palette = colors.getStyleById(id);
        if (palette == null)
            throw new McpTools.ToolException("No color block with the ID '" + id + "', see list_blocks");
        if (palette.isDefault())
            throw standard("palette", palette.getName(), "create_block");
        return palette;
    }

    /*  the colors of a palette: 1 to 5 as #RRGGBB, the given ones if the argument is missing  */
    private static List<String> palette(JsonNode value, List<String> given) throws McpTools.ToolException {
        if ((value == null || value.isNull()) && given != null)
            return given;
        List<String> list = new ArrayList<>();
        if (value != null && value.isArray())
            for (JsonNode color : value)
                list.add(color.asText("").toUpperCase(Locale.ROOT));
        if (list.isEmpty() || list.size() > MAX_COLORS
                || !list.stream().allMatch((String color) -> HEX_COLOR.matcher(color).matches()))
            throw new McpTools.ToolException("A palette takes 'colors': 1 to " + MAX_COLORS + " colors as #RRGGBB");
        return list;
    }

    /*  camera settings: each given value replaces the one of the base, null for any  */
    private static CameraSettings settings(JsonNode value, CameraSettings base) throws McpTools.ToolException {
        if (value == null || value.isNull())
            return base;
        if (!value.isObject())
            throw new McpTools.ToolException("The argument 'settings' must be an object, see get_capabilities.camera");
        CameraSettings.Body body = setting(value, "body", CameraSettings.Body.class, base.body());
        String model = value.has("model") && !value.get("model").isNull() ? value.get("model").asText()
                : value.has("model") ? null : base.model();
        if (model != null && CameraPromptBuilder.MODELS.stream().noneMatch((CameraPromptBuilder.Model candidate) ->
                candidate.name().equals(model) && (body == null || candidate.body() == body)))
            throw new McpTools.ToolException("'" + model + "' is no camera model" + (body != null ? " of the type "
                    + body.name() : "") + ", see get_capabilities.camera.models");
        return new CameraSettings(body, model, setting(value, "lens", CameraSettings.Lens.class, base.lens()),
                setting(value, "aperture", CameraSettings.Aperture.class, base.aperture()),
                setting(value, "shutter", CameraSettings.Shutter.class, base.shutter()),
                setting(value, "iso", CameraSettings.Iso.class, base.iso()),
                setting(value, "perspective", CameraSettings.Perspective.class, base.perspective()),
                setting(value, "shot", CameraSettings.Shot.class, base.shot())).withoutConflicts();
    }

    private static <E extends Enum<E>> E setting(JsonNode settings, String key, Class<E> type, E base)
            throws McpTools.ToolException {
        JsonNode value = settings.get(key);
        if (value == null)
            return base;
        if (value.isNull())
            return null;
        try {
            return Enum.valueOf(type, value.asText());
        }
        catch (IllegalArgumentException e) {
            throw new McpTools.ToolException("The camera setting '" + key + "' takes one of "
                    + String.join(", ", Arrays.stream(type.getEnumConstants()).map(Enum::name).toList())
                    + " or null");
        }
    }

    private static ObjectNode block(String type, String id, String name) {
        return McpTools.JSON.createObjectNode().put("id", id).put("type", type).put("name", name);
    }

    /*  ===== Frames =====  */

    private McpTools.Result createFrame(JsonNode arguments) throws McpTools.ToolException {
        String name = McpTools.text(arguments, "name");
        if (frames.exists(name))
            throw taken("frame", name, frames.freeName(name));
        Frame frame = new Frame(null, name);
        frame.setAspectRatio(McpTools.choice(arguments, "aspectRatio", Arrays.asList(AspectRatios.ALL), null));
        if (arguments.hasNonNull("resolution"))
            frame.setResolution(McpTools.choice(arguments, "resolution", ModelFamilies.RESOLUTIONS, null));
        if (!frames.add(frame))
            throw new McpTools.ToolException("The frame '" + name + "' could not be stored");
        return new McpTools.Result(frame(frame));
    }

    private McpTools.Result updateFrame(JsonNode arguments) throws McpTools.ToolException {
        Frame frame = ownFrame(McpTools.text(arguments, "frameId"));
        String name = McpTools.optionalText(arguments, "name");
        if (name != null && !name.equals(frame.getName()) && frames.exists(name))
            throw taken("frame", name, frames.freeName(name));
        if (name != null)
            frame.setName(name);
        if (arguments.hasNonNull("aspectRatio"))
            frame.setAspectRatio(McpTools.choice(arguments, "aspectRatio", Arrays.asList(AspectRatios.ALL), null));
        if (arguments.has("resolution"))
            frame.setResolution(arguments.get("resolution").isNull() ? null
                    : McpTools.choice(arguments, "resolution", ModelFamilies.RESOLUTIONS, null));
        if (!frames.update(frame))
            throw new McpTools.ToolException("The frame '" + frame.getName() + "' could not be stored");
        return new McpTools.Result(frame(frame));
    }

    private McpTools.Result deleteFrame(JsonNode arguments) throws McpTools.ToolException {
        Frame frame = ownFrame(McpTools.text(arguments, "frameId"));
        if (!frames.moveToTrash(frame.getId()))
            throw new McpTools.ToolException("The frame '" + frame.getName() + "' could not be moved to the trash");
        return new McpTools.Result(trashed(frame.getId(), frame.getName()));
    }

    /*  a frame of the user in use; a standard frame can only be read  */
    private Frame ownFrame(String id) throws McpTools.ToolException {
        Frame frame = frames.find(id);
        if (frame == null)
            throw new McpTools.ToolException("No frame with the ID '" + id + "', see list_frames");
        if (frame.isDefault())
            throw standard("frame", frame.getName(), "create_frame");
        return frame;
    }

    private static ObjectNode frame(Frame frame) {
        return McpTools.JSON.createObjectNode().put("id", frame.getId()).put("name", frame.getName())
                .put("aspectRatio", frame.getAspectRatio()).put("resolution", frame.getResolution());
    }

    /*  ===== Favorites and trash =====  */

    private McpTools.Result setFavorite(JsonNode arguments) throws McpTools.ToolException {
        String kind = McpTools.choice(arguments, "kind", FAVORITES, null);
        String id = McpTools.text(arguments, "id");
        JsonNode wanted = arguments.get("favorite");
        if (wanted == null || !wanted.isBoolean())
            throw new McpTools.ToolException("The argument 'favorite' is required and must be true or false");
        boolean exists = switch (kind) {
            case "mood" -> moods.find(id) != null;
            case "frame" -> frames.find(id) != null;
            case "color" -> colors.getStyleById(id) != null;
            default -> {
                PromptBlock block = blocks.find(PromptBlock.Type.valueOf(kind.toUpperCase(Locale.ROOT)), id);
                yield block != null && block.getId().equals(id);
            }
        };
        if (!exists)
            throw new McpTools.ToolException("No " + kind + " with the ID '" + id + "' in use");

        FavoriteService.Kind favoriteKind = FavoriteService.Kind.valueOf(kind.toUpperCase(Locale.ROOT));
        if (favorites.isFavorite(favoriteKind, id) != wanted.asBoolean())
            favorites.toggle(favoriteKind, id);
        return new McpTools.Result(McpTools.JSON.createObjectNode().put("kind", kind).put("id", id)
                .put("favorite", favorites.isFavorite(favoriteKind, id)));
    }

    private McpTools.Result restore(JsonNode arguments) throws McpTools.ToolException {
        String kind = McpTools.choice(arguments, "kind", TRASH, null);
        String id = McpTools.text(arguments, "id");
        if (!trash.restore(TrashGroup.Kind.valueOf(kind.toUpperCase(Locale.ROOT)), id))
            throw new McpTools.ToolException("Nothing with the ID '" + id + "' in the trash of " + kind
                    + ", see list_trash");
        return new McpTools.Result(McpTools.JSON.createObjectNode().put("kind", kind).put("id", id)
                .put("restored", true));
    }

    /*  ===== Answers =====  */

    private static ObjectNode trashed(String id, String name) {
        return McpTools.JSON.createObjectNode().put("id", id).put("name", name).put("inTrash", true);
    }

    /*  a standard entry, which can only be read as in the main window  */
    private static McpTools.ToolException standard(String what, String name, String copy) {
        return new McpTools.ToolException("'" + name + "' is a standard " + what + " and can only be read; " + copy
                + " makes an own copy");
    }

    /*  a name another entry has, refused as in the main window  */
    private static McpTools.ToolException taken(String what, String name, String free) {
        return new McpTools.ToolException("Another " + what + " is named '" + name + "'; choose another name, for "
                + "example '" + free + "'");
    }
}
