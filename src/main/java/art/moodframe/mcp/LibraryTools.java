package art.moodframe.mcp;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.AspectRatios;
import art.moodframe.model.CameraSettings;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.Frame;
import art.moodframe.model.GenerationCost;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ModelFamilies;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.CameraPromptBuilder;
import art.moodframe.service.ColorStyleService;
import art.moodframe.service.FavoriteService;
import art.moodframe.service.FrameService;
import art.moodframe.service.GenerationService;
import art.moodframe.service.MoodService;
import art.moodframe.service.PromptBlockService;
import art.moodframe.service.PromptComposer;
import art.moodframe.service.TrashCenter;
import art.moodframe.service.TrashEntry;
import art.moodframe.service.TrashGroup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The tools that read the library and the models: {@code get_capabilities}, {@code list_moods}, {@code get_mood},
 * {@code list_blocks}, {@code get_block}, {@code list_frames} and {@code list_trash}. IDs are the internal ones, the
 * names come along.
 *
 * @since 2.1.0
 */
class LibraryTools {

    /*  the types of building blocks, the palette ("color") included  */
    private static final List<String> TYPES = List.of("atmosphere", "style", "color", "light", "camera");

    /*  stands for the prompt of the user in the prompt of a mood  */
    private static final String SUBJECT = "[subject]";

    private final ApplicationConfig config;
    private final MoodService moods;
    private final PromptBlockService blocks;
    private final ColorStyleService colors;
    private final FrameService frames;
    private final FavoriteService favorites;
    private final TrashCenter trash;

    /**
     * Creates the tools.
     *
     * @param config    the configuration (enabled providers, keys, OpenAI models, defaults of the parameters)
     * @param moods     the moods
     * @param blocks    the text building blocks
     * @param colors    the palettes
     * @param frames    the frames
     * @param favorites the favorites
     * @param trash     the trash
     */
    LibraryTools(ApplicationConfig config, MoodService moods, PromptBlockService blocks, ColorStyleService colors,
            FrameService frames, FavoriteService favorites, TrashCenter trash) {
        this.config = config;
        this.moods = moods;
        this.blocks = blocks;
        this.colors = colors;
        this.frames = frames;
        this.favorites = favorites;
        this.trash = trash;
    }

    /**
     * Adds the tools.
     *
     * @param tools where they are added
     */
    void addTo(McpTools tools) {
        ObjectNode none = McpTools.JSON.createObjectNode();
        tools.add("get_capabilities", "Gets what can be generated: the models of the enabled providers with a key "
                + "(fixed id, name, family, provider, aspect ratios, resolutions, maximum reference images, estimated "
                + "cost in USD per image (FLUX.2: for 1 megapixel, video: for 8 s, null if unknown), maximum prompt "
                + "length in characters, additional parameters as JSON schema) and the values of the camera "
                + "settings.", McpTools.input(none), this::capabilities);

        tools.add("list_moods", "Lists the moods: id, name, standard or own, favorite and the five building blocks "
                + "(id and name; inTrash if the block lies in the trash).", McpTools.input(none), this::listMoods);

        ObjectNode mood = McpTools.JSON.createObjectNode();
        mood.set("moodId", McpTools.string("The ID of the mood, see list_moods"));
        tools.add("get_mood", "Gets a mood like list_moods, plus the resolved texts of its building blocks and the "
                + "prompt the mood makes, with " + SUBJECT + " for the prompt of the user.",
                McpTools.input(mood, "moodId"), this::getMood);

        ObjectNode type = McpTools.JSON.createObjectNode();
        type.set("type", McpTools.choice("The type of building block; 'color' are the palettes", TYPES, null));
        tools.add("list_blocks", "Lists the building blocks of a type, standard and own: id, name, favorite, group "
                + "(palettes: the colors).", McpTools.input(type, "type"), this::listBlocks);

        ObjectNode block = McpTools.JSON.createObjectNode();
        block.set("type", McpTools.choice("The type of building block; 'color' are the palettes", TYPES, null));
        block.set("blockId", McpTools.string("The ID of the building block, see list_blocks"));
        tools.add("get_block", "Gets a building block with its prompt text; a palette with its colors, a camera "
                + "with its settings.", McpTools.input(block, "type", "blockId"), this::getBlock);

        tools.add("list_frames", "Lists the frames: id, name, aspect ratio, resolution, standard or own, favorite.",
                McpTools.input(none), this::listFrames);

        tools.add("list_trash", "Lists the content of the trash by kind (chats, images, blocks, moods, frames): id, "
                + "name, deleted at, context and the IDs of its parts.", McpTools.input(none), this::listTrash);
    }

    /*  ===== Models =====  */

    private McpTools.Result capabilities(JsonNode arguments) {
        ObjectNode result = McpTools.JSON.createObjectNode();
        ArrayNode list = result.putArray("models");
        for (String item : GenerationService.availableItems(config)) {
            String id = GenerationModel.idOf(item);
            GenerationModel model = GenerationModel.fromId(id);
            List<String> free = ModelFamilies.freeResolutions(item);
            List<String> resolutions = free != null ? free
                    : model.getResolutionLabel() != null ? List.of(model.getResolutionLabel()) : List.of();
            double cost = GenerationCost.estimate(model, model.isVideoModel() ? 8 : null);
            ObjectNode node = list.addObject().put("id", id).put("name", item)
                    .put("family", ModelFamilies.familyOf(item)).put("provider", model.getProvider().getDisplayName())
                    .put("video", model.isVideoModel());
            node.set("aspectRatios", McpTools.JSON.valueToTree(AspectRatios.forItem(item)));
            node.set("resolutions", McpTools.JSON.valueToTree(resolutions));
            node.put("maxReferenceImages", model.getMaxInputImages());
            if (cost > 0)
                node.put("cost", McpTools.usd(cost));
            else
                node.putNull("cost");
            node.put("maxPromptLength", GenerationService.maxPromptLength(id));
            node.set("parameters", parameters(config, model));
        }
        result.set("camera", camera());
        return new McpTools.Result(result);
    }

    /**
     * The parameters a model takes besides prompt, target and images, as the main window offers them.
     *
     * @param config the defaults of the parameters
     * @param model  the model
     * @return the JSON schemas of the parameters by name
     */
    static ObjectNode parameters(ApplicationConfig config, GenerationModel model) {
        ObjectNode parameters = McpTools.JSON.createObjectNode();
        if (model.isFlux2Model() || model.isFlux3Klein())
            parameters.set("seed", McpTools.JSON.createObjectNode().put("type", "integer")
                    .put("description", "Fixed seed; random without"));
        if (model.getProvider() == ApiProvider.BFL)
            parameters.set("safetyTolerance", McpTools.integer("0 strict to 5 permissive", 0, 5,
                    config.getFlux2SafetyTolerance()));
        if (model.isFlux2Model() && !model.isFlux2Klein())
            parameters.set("promptUpsampling", McpTools.JSON.createObjectNode().put("type", "boolean")
                    .put("description", "BFL enhances the prompt"));
        if (model.isFlux2Flex()) {
            parameters.set("guidance", McpTools.JSON.createObjectNode().put("type", "number").put("minimum", 1.5)
                    .put("maximum", 10.0).put("default", config.getFlux2FlexGuidance()));
            parameters.set("steps", McpTools.integer("Inference steps", 1, 50, config.getFlux2FlexSteps()));
        }
        if (model.supportsThinkingLevel())
            parameters.set("thinkingLevel", McpTools.choice("Thinking before the image", List.of("minimal", "high"),
                    null));
        if (model.getProvider() == ApiProvider.GOOGLE_GEMINI && !model.isVideoModel()
                && model.supportsGroundingSearch())
            parameters.set("googleSearch", McpTools.JSON.createObjectNode().put("type", "boolean")
                    .put("description", "Grounds the image in a Google Search"));
        if (model.isVideoModel()) {
            ObjectNode seconds = McpTools.JSON.createObjectNode().put("type", "integer").put("default", 8);
            seconds.set("enum", McpTools.JSON.valueToTree(model.getVideoDurationChoices()));
            parameters.set("videoSeconds", seconds);
        }
        return parameters;
    }

    /*  the values of every camera setting, as they are stored in a camera block  */
    private static ObjectNode camera() {
        ObjectNode camera = McpTools.JSON.createObjectNode();
        camera.set("body", names(CameraSettings.Body.values()));
        camera.set("lens", names(CameraSettings.Lens.values()));
        camera.set("aperture", names(CameraSettings.Aperture.values()));
        camera.set("shutter", names(CameraSettings.Shutter.values()));
        camera.set("iso", names(CameraSettings.Iso.values()));
        camera.set("perspective", names(CameraSettings.Perspective.values()));
        camera.set("shot", names(CameraSettings.Shot.values()));
        return camera;
    }

    private static ArrayNode names(Enum<?>[] values) {
        return McpTools.JSON.valueToTree(Arrays.stream(values).map(Enum::name).toList());
    }

    /*  ===== Moods =====  */

    private McpTools.Result listMoods(JsonNode arguments) {
        ObjectNode result = McpTools.JSON.createObjectNode();
        ArrayNode list = result.putArray("moods");
        for (Mood mood : moods.getAll())
            list.add(mood(mood, false));
        return new McpTools.Result(result);
    }

    private McpTools.Result getMood(JsonNode arguments) throws McpTools.ToolException {
        String moodId = McpTools.text(arguments, "moodId");
        Mood mood = moods.find(moodId);
        if (mood == null)
            throw new McpTools.ToolException("No mood with the ID '" + moodId + "', see list_moods");
        ObjectNode result = mood(mood, true);
        result.put("prompt", new PromptComposer(Integer.MAX_VALUE).compose(SUBJECT, moods.selection(mood)));
        return new McpTools.Result(result);
    }

    /*  a mood with its five blocks, resolved with their texts or by id and name only  */
    private ObjectNode mood(Mood mood, boolean resolved) {
        ObjectNode node = McpTools.JSON.createObjectNode().put("id", mood.getId()).put("name", mood.getName())
                .put("standard", mood.isDefault())
                .put("favorite", favorites.isFavorite(FavoriteService.Kind.MOOD, mood.getId()));
        ObjectNode fields = node.putObject("blocks");
        for (Mood.Field field : Mood.Field.values()) {
            String key = field.name().toLowerCase(Locale.ROOT);
            String id = mood.getReference(field);
            if (id == null) {
                fields.putNull(key);
                continue;
            }
            ObjectNode inUse = field == Mood.Field.COLOR ? palette(id, resolved)
                    : textBlock(PromptBlock.Type.valueOf(field.name()), id, resolved);
            if (inUse != null) {
                fields.set(key, inUse);
                continue;
            }

            /*  a block in the trash leaves the field empty until it is restored  */
            String trashed = field == Mood.Field.COLOR ? nameOf(colors.findInTrash(id))
                    : nameOf(blocks.findInTrash(id));
            fields.putObject(key).put("id", id).put("name", trashed).put("inTrash", true);
        }
        return node;
    }

    private static String nameOf(ColorStyle palette) {
        return palette != null ? palette.getName() : null;
    }

    private static String nameOf(PromptBlock block) {
        return block != null ? block.getName() : null;
    }

    /*  ===== Building blocks =====  */

    private McpTools.Result listBlocks(JsonNode arguments) throws McpTools.ToolException {
        String type = McpTools.choice(arguments, "type", TYPES, null);
        ObjectNode result = McpTools.JSON.createObjectNode().put("type", type);
        ArrayNode list = result.putArray("blocks");
        if ("color".equals(type)) {
            for (ColorStyle palette : colors.getAllStyles())
                list.add(palette(palette, false));
        }
        else {
            for (PromptBlock block : blocks.getAll(PromptBlock.Type.valueOf(type.toUpperCase(Locale.ROOT))))
                list.add(textBlock(block, false));
        }
        return new McpTools.Result(result);
    }

    private McpTools.Result getBlock(JsonNode arguments) throws McpTools.ToolException {
        String type = McpTools.choice(arguments, "type", TYPES, null);
        String blockId = McpTools.text(arguments, "blockId");
        ObjectNode block = "color".equals(type) ? palette(blockId, true)
                : textBlock(PromptBlock.Type.valueOf(type.toUpperCase(Locale.ROOT)), blockId, true);
        if (block == null)
            throw new McpTools.ToolException("No " + type + " block with the ID '" + blockId + "', see list_blocks");
        return new McpTools.Result(block);
    }

    /*  the text block in use with exactly this id, null for none  */
    private ObjectNode textBlock(PromptBlock.Type type, String id, boolean resolved) {
        PromptBlock block = blocks.find(type, id);
        return block != null && block.getId().equals(id) ? textBlock(block, resolved) : null;
    }

    private ObjectNode textBlock(PromptBlock block, boolean resolved) {
        ObjectNode node = McpTools.JSON.createObjectNode().put("id", block.getId()).put("name", block.getName())
                .put("standard", block.isDefault())
                .put("favorite", favorites.isFavorite(FavoriteService.Kind.of(block.getType()), block.getId()))
                .put("group", block.getGroup());
        if (!resolved)
            return node;
        node.put("type", block.getType().name().toLowerCase(Locale.ROOT)).put("photographic", block.isPhotographic());

        /*  A camera has no text of its own: its settings make it, for a photographic style  */
        if (block.getType() == PromptBlock.Type.CAMERA) {
            CameraSettings settings = CameraSettings.fromJson(block.getParams());
            node.set("settings", McpTools.parse(settings.toJson()));
            return node.put("text", CameraPromptBuilder.build(settings, true));
        }
        return node.put("text", block.getText());
    }

    /*  the palette in use with exactly this id, null for none  */
    private ObjectNode palette(String id, boolean resolved) {
        ColorStyle palette = colors.getStyleById(id);
        return palette != null ? palette(palette, resolved) : null;
    }

    private ObjectNode palette(ColorStyle palette, boolean resolved) {
        ObjectNode node = McpTools.JSON.createObjectNode().put("id", palette.getId()).put("name", palette.getName())
                .put("standard", palette.isDefault())
                .put("favorite", favorites.isFavorite(FavoriteService.Kind.COLOR, palette.getId()));
        node.set("colors", McpTools.JSON.valueToTree(palette.getColors().stream()
                .filter((String color) -> color != null && !color.isEmpty()).toList()));
        if (resolved)
            node.put("type", "color").put("text", palette.toPromptString());
        return node;
    }

    /*  ===== Frames and trash =====  */

    private McpTools.Result listFrames(JsonNode arguments) {
        ObjectNode result = McpTools.JSON.createObjectNode();
        ArrayNode list = result.putArray("frames");
        for (Frame frame : frames.getAll())
            list.addObject().put("id", frame.getId()).put("name", frame.getName())
                    .put("aspectRatio", frame.getAspectRatio()).put("resolution", frame.getResolution())
                    .put("standard", frame.isDefault())
                    .put("favorite", favorites.isFavorite(FavoriteService.Kind.FRAME, frame.getId()));
        return new McpTools.Result(result);
    }

    private McpTools.Result listTrash(JsonNode arguments) {
        ObjectNode result = McpTools.JSON.createObjectNode();
        for (TrashGroup.Kind kind : TrashGroup.Kind.values()) {
            ArrayNode list = result.putArray(kind.name().toLowerCase(Locale.ROOT));
            for (TrashEntry entry : trash.list(kind)) {
                ObjectNode node = list.addObject().put("id", entry.id()).put("name", entry.name())
                        .put("deletedAt", McpTools.time(entry.deletedAt())).put("context", entry.context());
                node.set("parts", McpTools.JSON.valueToTree(entry.parts().stream().map(TrashEntry.Part::id).toList()));
            }
        }
        return new McpTools.Result(result);
    }
}
