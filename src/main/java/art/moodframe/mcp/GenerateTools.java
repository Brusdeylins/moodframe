package art.moodframe.mcp;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.AspectRatios;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.Frame;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.model.ModelFamilies;
import art.moodframe.model.ModelParameter;
import art.moodframe.model.ModelParameters;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.model.SavedImage;
import art.moodframe.service.chat.ChatLibrary;
import art.moodframe.service.chat.ImageAttachmentService;
import art.moodframe.service.generation.GenerationOrder;
import art.moodframe.service.generation.GenerationService;
import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.library.SavedImageService;
import art.moodframe.service.prompt.PromptComposer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The tools that generate: {@code preview_generation} tells without any request what an order makes,
 * {@code generate} places it through the {@link GenerationService} like the send button of the main window, with the
 * same prompt, mood copy, format per model, cost and trash. The entry shows in its chat in MoodFrame at once, without
 * changing what the main window shows or has chosen.
 *
 * @since 2.1.0
 */
class GenerateTools {

    private static final int MAX_COUNT = ImageGenerationRequest.MAX_IMAGE_COUNT;
    private static final int MAX_REFERENCES = 14;
    private static final int DEFAULT_WAIT = 120;
    private static final int MAX_WAIT = 600;

    /*  without frame and aspect ratio, as the request of a model without target  */
    private static final String SQUARE = "1:1";

    private final ApplicationConfig config;
    private final ChatLibrary chats;
    private final MoodService moods;
    private final PromptBlockService blocks;
    private final ColorStyleService colors;
    private final FrameService frames;
    private final SavedImageService savedImages;
    private final GenerationService generation;

    /**
     * Creates the tools.
     *
     * @param config     the models to choose from and the defaults of their parameters
     * @param chats      the chats of the main window
     * @param moods      the moods
     * @param blocks     the text building blocks
     * @param colors     the palettes
     * @param frames      the frames
     * @param savedImages the saved images, attached by their names
     * @param generation  carries out the orders
     */
    GenerateTools(ApplicationConfig config, ChatLibrary chats, MoodService moods, PromptBlockService blocks,
            ColorStyleService colors, FrameService frames, SavedImageService savedImages,
            GenerationService generation) {
        this.config = config;
        this.chats = chats;
        this.moods = moods;
        this.blocks = blocks;
        this.colors = colors;
        this.frames = frames;
        this.savedImages = savedImages;
        this.generation = generation;
    }

    /**
     * Adds the tools.
     *
     * @param tools where they are added
     */
    void addTo(McpTools tools) {
        ObjectNode order = McpTools.JSON.createObjectNode();
        order.set("chatId", McpTools.string("The ID of the chat that takes the entry, see list_chats or "
                + "create_chat"));
        order.set("prompt", McpTools.string("The prompt of the user; the building blocks are added to it"));
        order.set("model", McpTools.string("The fixed id of the model, see get_capabilities"));
        order.set("moodId", McpTools.string("The ID or name of a mood whose building blocks go into the prompt, see "
                + "list_moods; without one, no mood"));
        ObjectNode fields = McpTools.JSON.createObjectNode().put("type", "object")
                .put("description", "Building blocks by field, each by its id or name (see list_blocks), replacing "
                        + "those of the mood; null or empty leaves the field empty")
                .put("additionalProperties", false);
        ObjectNode fieldSchemas = fields.putObject("properties");
        for (Mood.Field field : Mood.Field.values())
            fieldSchemas.set(key(field), McpTools.nullableString("The " + key(field) + " block"));
        order.set("blocks", fields);
        order.set("frameId", McpTools.string("The ID or name of a frame whose aspect ratio and resolution are the "
                + "target, see list_frames"));
        order.set("aspectRatio", McpTools.choice("The target aspect ratio, replacing that of the frame; without "
                + "frame and aspect ratio a square", Arrays.asList(AspectRatios.ALL), null));
        order.set("resolution", McpTools.choice("The target resolution, replacing that of the frame",
                ModelFamilies.RESOLUTIONS, null));
        order.set("count", McpTools.integer("The number of images; a video is always one", 1, MAX_COUNT, 1));
        order.set("referenceImages", McpTools.strings("Reference images: IDs of images (see get_entry) or absolute "
                + "paths of image files, at most get_capabilities.maxReferenceImages of the model", MAX_REFERENCES));
        order.set("savedImages", McpTools.strings("Saved images attached after the reference images, by name or ID "
                + "(see list_saved_images), e.g. a logo; they count against maxReferenceImages and the entry keeps "
                + "them", MAX_REFERENCES));
        ObjectNode params = McpTools.JSON.createObjectNode().put("type", "object")
                .put("description", "The parameters of the model as get_capabilities lists them for it; a missing "
                        + "one takes the default of MoodFrame")
                .put("additionalProperties", false);
        ObjectNode paramSchemas = params.putObject("properties");
        for (ModelParameter parameter : ModelParameters.all())
            paramSchemas.putObject(parameter.name()).put("type", switch (parameter.type()) {
                case INTEGER -> "integer";
                case NUMBER -> "number";
                case BOOLEAN -> "boolean";
                case CHOICE -> "string";
            });
        order.set("params", params);

        ObjectNode preview = order.deepCopy();
        preview.remove("chatId");
        tools.add("preview_generation", "Tells what generate would make, without any request and without cost: the "
                + "final prompt with the building blocks and whether the model takes its length, the aspect ratio and "
                + "resolution the model makes of the target (the closest it can), and the estimated cost in USD "
                + "(null if unknown). Takes the arguments of generate.", McpTools.input(preview, "prompt", "model"),
                this::preview);

        order.set("wait", McpTools.integer("Seconds to wait for the result; 0 returns at once", 0, MAX_WAIT,
                DEFAULT_WAIT));
        tools.add("generate", "Generates images or a video in a chat like the send button of MoodFrame; this costs "
                + "money at the provider, see preview_generation for the estimate. The entry shows in the chat in "
                + "MoodFrame at once. Waits up to 'wait' seconds and returns the entry id, its status, the format "
                + "the model takes, the cost in USD and, once completed, the images with id, file path and "
                + "downloadName, the file name the download button of MoodFrame gives them (see download_image); a "
                + "longer job returns PENDING or PROCESSING, get_entry tells the rest later.",
                McpTools.input(order, "chatId", "prompt", "model"), this::generate);
    }

    private static String key(Mood.Field field) {
        return field.name().toLowerCase(Locale.ROOT);
    }

    private McpTools.Result preview(JsonNode arguments) throws McpTools.ToolException, IOException {
        GenerationOrder order = order(arguments, null, false);
        GenerationService.Preview preview = generation.preview(order);
        ObjectNode result = McpTools.JSON.createObjectNode().put("prompt", preview.prompt())
                .put("promptLength", preview.prompt().length()).put("maxPromptLength", preview.maxPromptLength())
                .put("fits", preview.fits());
        format(result, order, preview);
        result.set("costPerImage", cost(preview.costPerImage()));
        result.set("cost", cost(preview.cost()));
        result.put("mood", order.mood() != null ? order.mood().getName() : null).put("moodChanged", order.moodChanged());
        ObjectNode used = result.putObject("blocks");
        PromptComposer.Selection selection = order.blocks();
        used.put("atmosphere", name(selection.atmosphere())).put("style", name(selection.style()))
                .put("color", selection.palette() != null ? selection.palette().getName() : null)
                .put("light", name(selection.light())).put("camera", name(selection.camera()));
        Frame frame = order.target().frame();
        return new McpTools.Result(result.put("frame", frame != null ? frame.getName() : null)
                .put("frameChanged", order.target().changed()));
    }

    private McpTools.Result generate(JsonNode arguments) throws McpTools.ToolException, IOException {
        String chatId = McpTools.text(arguments, "chatId");
        Chat chat = chats.read((List<Chat> all) -> all.stream().filter((Chat candidate) -> candidate.getId()
                .equals(chatId) && ChatTools.shown(candidate)).findFirst().orElse(null));
        if (chat == null)
            throw new McpTools.ToolException("No chat with the ID '" + chatId + "', see list_chats");
        int wait = McpTools.number(arguments, "wait", 0, MAX_WAIT, DEFAULT_WAIT);
        GenerationOrder order = order(arguments, chat, true);
        GenerationService.Preview preview = generation.preview(order);

        GenerationService.Submission submission;
        try {
            submission = generation.submit(order);
        }
        catch (GenerationService.GenerationException e) {
            throw new McpTools.ToolException(e.getMessage());
        }

        /*  The entry is in its chat once stored, so get_entry finds it; it ends once the provider reports it, a
            longer job is followed by get_entry  */
        boolean finished = false;
        try {
            submission.stored().get();
            if (wait > 0) {
                submission.finished().get(wait, TimeUnit.SECONDS);
                finished = true;
            }
        }
        catch (TimeoutException | ExecutionException e) {
            finished = false;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ChatEntry entry = submission.entry();
        List<ChatTools.Found> files = new ArrayList<>();
        ObjectNode result = chats.read((List<Chat> all) -> {
            ObjectNode node = McpTools.JSON.createObjectNode().put("entryId", entry.getId()).put("chatId", chatId)
                    .put("status", entry.getStatus().name()).put("error", entry.getErrorMessage());
            format(node, order, preview);
            node.put("cost", McpTools.usd(entry.getCost()));
            ArrayNode images = node.putArray("images");
            entry.getImages().stream().filter((GeneratedImage image) -> image.getDownloadStatus()
                    == GeneratedImage.DownloadStatus.CACHED).forEach((GeneratedImage image) -> {
                images.addObject().put("id", image.getId()).put("path", image.getLocalFilePath())
                        .put("video", image.isVideo()).put("width", image.getImageWidth())
                        .put("height", image.getImageHeight());
                files.add(ChatTools.found(chat, image));
            });
            return node;
        });
        ChatTools.putDownloadNames((ArrayNode) result.path("images"), files);
        if (!finished)
            result.put("hint", "Still running" + (wait > 0 ? " after " + wait + " s" : "") + "; get_entry with the "
                    + "entryId tells the status and the images later");
        return new McpTools.Result(result);
    }

    /*  the model and the format it makes of the target  */
    private static void format(ObjectNode node, GenerationOrder order, GenerationService.Preview preview) {
        node.put("model", order.modelId()).put("name", GenerationModel.displayNameOf(order.modelId()))
                .put("aspectRatio", preview.aspectRatio()).put("resolution", preview.resolution())
                .put("count", order.imageCount());
    }

    /*  a cost in USD, null if unknown  */
    private static JsonNode cost(double amount) {
        return amount > 0 ? McpTools.JSON.getNodeFactory().numberNode(McpTools.usd(amount))
                : McpTools.JSON.getNodeFactory().nullNode();
    }

    private static String name(PromptBlock block) {
        return block != null ? block.getName() : null;
    }

    /*  ===== The order =====  */

    /**
     * Builds the order of the arguments as the main window builds it of its panel.
     *
     * @param arguments the arguments
     * @param chat      the chat that takes the entry, null for a preview
     * @param encode    whether the reference images are read for sending, else only checked
     */
    private GenerationOrder order(JsonNode arguments, Chat chat, boolean encode)
            throws McpTools.ToolException, IOException {
        String prompt = McpTools.text(arguments, "prompt");
        String modelId = McpTools.text(arguments, "model");
        if (generation.availableItems(config, 0).stream()
                .noneMatch((String item) -> modelId.equals(GenerationModel.idOf(item))))
            throw new McpTools.ToolException("No model with the id '" + modelId + "' to generate with, see "
                    + "get_capabilities");
        GenerationModel model = GenerationModel.fromId(modelId);

        /*  The blocks of the mood, replaced field by field; the mood counts as changed if they differ from it  */
        Mood mood = null;
        String moodReference = McpTools.optionalText(arguments, "moodId");
        if (moodReference != null) {
            mood = moods.find(moodReference);
            mood = mood != null ? mood : moods.getAll().stream()
                    .filter((Mood candidate) -> candidate.getName().equals(moodReference)).findFirst().orElse(null);
            if (mood == null)
                throw new McpTools.ToolException("No mood with the id or name '" + moodReference + "', see "
                        + "list_moods");
        }
        Mood combination = new Mood(null, null);
        if (mood != null)
            moods.apply(mood, Map.of(), Set.of()).forEach(combination::setReference);
        LibraryEditTools.setBlocks(blocks, colors, combination, arguments.get("blocks"));
        boolean moodChanged = moods.isChanged(mood, combination.getReferences());

        /*  The target of the frame, replaced by aspect ratio and resolution; the frame counts as changed then  */
        Frame frame = null;
        String frameReference = McpTools.optionalText(arguments, "frameId");
        if (frameReference != null) {
            frame = frames.find(frameReference);
            frame = frame != null ? frame : frames.getAll().stream()
                    .filter((Frame candidate) -> candidate.getName().equals(frameReference)).findFirst().orElse(null);
            if (frame == null)
                throw new McpTools.ToolException("No frame with the id or name '" + frameReference + "', see "
                        + "list_frames");
        }
        String aspectRatio = arguments.hasNonNull("aspectRatio")
                ? McpTools.choice(arguments, "aspectRatio", Arrays.asList(AspectRatios.ALL), null)
                : frame != null ? frame.getAspectRatio() : SQUARE;
        String resolution = arguments.hasNonNull("resolution")
                ? McpTools.choice(arguments, "resolution", ModelFamilies.RESOLUTIONS, null)
                : frame != null ? frame.getResolution() : null;
        boolean frameChanged = frame != null && (!aspectRatio.equals(frame.getAspectRatio())
                || frame.getResolution() != null && !frame.getResolution().equals(resolution));

        int count = model.isVideoModel() ? 1 : McpTools.number(arguments, "count", 1, MAX_COUNT, 1);
        List<SavedImage> saved = savedImages(arguments.get("savedImages"));
        return new GenerationOrder(chat, prompt, moods.selection(combination), mood, moodChanged,
                new GenerationOrder.Target(frame, frameChanged, aspectRatio, resolution), modelId, count,
                parameters(arguments.get("params"), model), null, references(arguments.get("referenceImages"), saved,
                model, encode), ChatEntry.Source.MCP, saved.stream().map(SavedImage::id).toList());
    }

    /*  the saved images by their names or IDs, in their order  */
    private List<SavedImage> savedImages(JsonNode references) throws McpTools.ToolException {
        if (references == null || references.isNull() || references.isEmpty())
            return List.of();
        if (!references.isArray())
            throw new McpTools.ToolException("The argument 'savedImages' must be a list of names of saved images");
        List<SavedImage> saved = new ArrayList<>();
        for (JsonNode reference : references) {
            SavedImage image = reference.isTextual() ? savedImages.find(reference.asText().trim()) : null;
            if (image == null)
                throw new McpTools.ToolException("No saved image with the name or ID '" + reference.asText()
                        + "', see list_saved_images");
            saved.add(image);
        }
        return saved;
    }

    /*  the parameters the model takes, as get_capabilities lists them; a missing one takes the default  */
    private GenerationOrder.Parameters parameters(JsonNode params, GenerationModel model)
            throws McpTools.ToolException {
        if (params == null || params.isNull())
            params = McpTools.JSON.createObjectNode();
        if (!params.isObject())
            throw new McpTools.ToolException("The argument 'params' must be an object, see get_capabilities");
        ObjectNode allowed = LibraryTools.parameters(config, model);
        for (Iterator<String> names = params.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!allowed.has(name))
                throw new McpTools.ToolException("The model " + model.name() + " takes no parameter '" + name
                        + "'; it takes " + (allowed.isEmpty() ? "none" : String.join(", ", names(allowed)))
                        + ", see get_capabilities");
        }

        Number seed = (Number) value(params, model.parameter(ModelParameters.SEED.name()));
        Number safety = (Number) value(params, model.parameter(ModelParameters.SAFETY_TOLERANCE.name()));
        Number guidance = valueOrDefault(params, model.parameter(ModelParameters.GUIDANCE.name()));
        Number steps = valueOrDefault(params, model.parameter(ModelParameters.STEPS.name()));
        String thinkingLevel = (String) value(params, model.parameter(ModelParameters.THINKING_LEVEL.name()));
        Boolean googleSearch = (Boolean) value(params, model.parameter(ModelParameters.GOOGLE_SEARCH.name()));
        Number videoSeconds = valueOrDefault(params, model.parameter(ModelParameters.VIDEO_SECONDS));
        Boolean upsampling = (Boolean) value(params, model.parameter(ModelParameters.PROMPT_UPSAMPLING.name()));
        int tolerance = safety != null ? safety.intValue() : config.generation().getFlux2SafetyTolerance();
        return new GenerationOrder.Parameters(seed != null ? seed.longValue() : null, tolerance,
                safety != null ? safety.intValue() : null, upsampling != null && upsampling, thinkingLevel,
                googleSearch, guidance != null ? guidance.doubleValue() : null,
                steps != null ? steps.intValue() : null, videoSeconds != null ? videoSeconds.intValue() : null);
    }

    /*  the value of a parameter the model takes, the default of the settings or the provider without one  */
    private Number valueOrDefault(JsonNode params, ModelParameter parameter) throws McpTools.ToolException {
        Object value = value(params, parameter);
        return (Number) (value != null || parameter == null ? value : LibraryTools.defaultOf(config, parameter));
    }

    /*  the value of a parameter, checked against its description; null if it is missing or the model takes none  */
    private static Object value(JsonNode params, ModelParameter parameter) throws McpTools.ToolException {
        if (parameter == null || !params.hasNonNull(parameter.name()))
            return null;
        String name = parameter.name();
        JsonNode value = params.get(name);
        switch (parameter.type()) {
            case BOOLEAN:
                return McpTools.flag(params, name, false);
            case CHOICE:
                return McpTools.choice(params, name, parameter.choices().stream().map(String::valueOf).toList(),
                        null);
            case NUMBER:
                if (!value.isNumber() || !parameter.accepts(value.asDouble()))
                    throw new McpTools.ToolException("The parameter '" + name + "' must be a number "
                            + parameter.range());
                return value.asDouble();
            default:
                if (parameter.minimum() != null && parameter.maximum() != null)
                    return McpTools.number(params, name, parameter.minimum().intValue(),
                            parameter.maximum().intValue(), 0);
                if (!parameter.choices().isEmpty()) {
                    int choice = McpTools.number(params, name, 1, Integer.MAX_VALUE, 0);
                    if (!parameter.accepts(choice))
                        throw new McpTools.ToolException("The parameter '" + name + "' must be one of "
                                + parameter.choices());
                    return choice;
                }
                if (!value.isIntegralNumber())
                    throw new McpTools.ToolException("The parameter '" + name + "' must be an integer");
                return value.asLong();
        }
    }

    private static List<String> names(ObjectNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    /*  the reference images as Base64, those of the IDs or paths and then the saved images, within the limits of the
        model; unless encoded, only checked and their paths stand in, so a preview counts them like the order sent  */
    private List<String> references(JsonNode images, List<SavedImage> saved, GenerationModel model, boolean encode)
            throws McpTools.ToolException, IOException {
        if ((images == null || images.isNull() || images.isEmpty()) && saved.isEmpty())
            return List.of();
        if (images != null && !images.isNull() && !images.isArray())
            throw new McpTools.ToolException("The argument 'referenceImages' must be a list of image IDs or paths");
        int limit = model.supportsMultiImage() ? model.getMaxInputImages() : 0;
        if ((images != null ? images.size() : 0) + saved.size() > limit)
            throw new McpTools.ToolException("The model " + model.name() + " takes at most " + limit
                    + " reference images");
        List<Path> files = new ArrayList<>();
        if (images != null)
            for (JsonNode image : images) {
                if (!image.isTextual())
                    throw new McpTools.ToolException("A reference image is an image ID or an absolute path");
                files.add(ChatTools.imageFile(chats, image.asText()));
            }
        for (SavedImage image : saved)
            files.add(savedImages.fileOf(image));

        ImageAttachmentService attachments = new ImageAttachmentService();
        long maxFileSize = GenerationModel.getMaxFileSize(model.getProvider());
        List<String> encoded = new ArrayList<>();
        for (Path file : files) {
            if (!encode) {
                encoded.add(file.toString());
                continue;
            }
            try {
                encoded.add(attachments.attachImage(file.toFile(), maxFileSize).getBase64Data());
            }
            catch (IllegalArgumentException e) {
                throw new McpTools.ToolException("The reference image '" + file + "': " + e.getMessage());
            }
        }
        return encoded;
    }
}
