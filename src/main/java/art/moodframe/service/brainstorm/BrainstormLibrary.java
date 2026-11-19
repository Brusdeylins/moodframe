package art.moodframe.service.brainstorm;

import art.moodframe.model.CameraSettings;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.Frame;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.model.SavedImage;
import art.moodframe.service.library.FavoriteService;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.SavedImageService;
import art.moodframe.service.prompt.CameraPromptBuilder;
import art.moodframe.service.prompt.PromptComposer;
import art.moodframe.service.provider.ChatTool;
import art.moodframe.service.provider.ChatTurn;
import art.moodframe.util.I18nManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The tools of the brainstorm assistant on the library: the moods, the frames and the saved images, standard entries
 * and favorites included, listed with id, name and a short description and looked up one by one, so the system
 * prompt needs no list of them. Every call reads the library anew; an answer is compact JSON of at most
 * {@link BrainstormTools#TOOL_RESULT_MAX_LENGTH} characters, a longer list continues from an offset.
 *
 * @since 2.1.4
 */
public class BrainstormLibrary {

    private static final JSONObject LIST_ARGUMENTS = new JSONObject().put("type", "object")
            .put("properties", new JSONObject().put("offset", new JSONObject().put("type", "integer")
                    .put("description", "Where to continue a list cut short, the next_offset of its answer")));

    static final ChatTool LIST_MOODS = new ChatTool("list_moods", "Lists the moods of the app, standard and the "
            + "user's own, favorites first: id, name and the names of their building blocks. Use it to find a "
            + "fitting mood.", LIST_ARGUMENTS);
    static final ChatTool GET_MOOD = new ChatTool("get_mood", "Gets a mood by its id: each building block with id, "
            + "name and text (a color with its colors).", idArgument("The id of the mood, see list_moods"));
    static final ChatTool LIST_FRAMES = new ChatTool("list_frames", "Lists the frames of the app, standard and the "
            + "user's own, favorites first: id, name and their aspect ratio and resolution. A frame is a named "
            + "aspect ratio and resolution for a purpose (e.g. a social post).", LIST_ARGUMENTS);
    static final ChatTool GET_FRAME = new ChatTool("get_frame", "Gets a frame by its id: its aspect ratio and "
            + "resolution.", idArgument("The id of the frame, see list_frames"));
    static final ChatTool LIST_SAVED_IMAGES = new ChatTool("list_saved_images", "Lists the images the user saved "
            + "to attach to an order again and again (e.g. a logo, a product photo), favorites first: id, name and "
            + "the description of what each shows and when it fits.", LIST_ARGUMENTS);

    private final MoodService moods;
    private final FrameService frames;
    private final FavoriteService favorites;
    private final SavedImageService savedImages;

    /**
     * Creates the tools.
     *
     * @param moods       the moods, which also find their building blocks
     * @param frames      the frames
     * @param favorites   the favorites, listed first (may be null)
     * @param savedImages the saved images (may be null: no tool for them)
     */
    public BrainstormLibrary(MoodService moods, FrameService frames, FavoriteService favorites,
            SavedImageService savedImages) {
        this.moods = moods;
        this.frames = frames;
        this.favorites = favorites;
        this.savedImages = savedImages;
    }

    private static JSONObject idArgument(String description) {
        return new JSONObject().put("type", "object")
                .put("properties", new JSONObject().put("id", new JSONObject().put("type", "string")
                        .put("description", description)))
                .put("required", new JSONArray().put("id"));
    }

    /** The declarations of the tools. */
    List<ChatTool> tools() {
        return savedImages != null ? List.of(LIST_MOODS, GET_MOOD, LIST_FRAMES, GET_FRAME, LIST_SAVED_IMAGES)
                : List.of(LIST_MOODS, GET_MOOD, LIST_FRAMES, GET_FRAME);
    }

    /**
     * Tells whether a call is one of these tools.
     *
     * @param name the name of the tool
     * @return whether it is a tool on the library
     */
    boolean offers(String name) {
        return tools().stream().anyMatch((ChatTool tool) -> tool.name().equals(name));
    }

    /**
     * Runs a call of one of the tools.
     *
     * @param call   the call
     * @param status told what the tool looks at, before it reads it
     * @return the answer for the model
     * @throws IllegalArgumentException if the id of a mood or frame is unknown
     */
    JSONObject run(ChatTurn.ToolCall call, Consumer<String> status) {
        int offset = Math.max(0, call.args().optInt("offset", 0));
        String id = call.args().optString("id", "");
        switch (call.name()) {
            case "list_moods" -> {
                status.accept(I18nManager.getText("brainstorm.tool.moods"));
                return list("moods", ordered(moods.getAll(), FavoriteService.Kind.MOOD, Mood::getId, Mood::getName),
                        this::moodEntry, offset);
            }
            case "get_mood" -> {
                Mood mood = moods.find(id);
                if (mood == null)
                    throw new IllegalArgumentException("No mood with the id '" + id + "', see list_moods");
                status.accept(I18nManager.getText("brainstorm.tool.mood", mood.getName()));
                return mood(mood);
            }
            case "list_frames" -> {
                status.accept(I18nManager.getText("brainstorm.tool.frames"));
                return list("frames", ordered(frames.getAll(), FavoriteService.Kind.FRAME, Frame::getId,
                        Frame::getName), (Frame frame) -> entry(frame.getId(), frame.getName(),
                        favorites != null && favorites.isFavorite(FavoriteService.Kind.FRAME, frame.getId()))
                        .put("format", FrameService.describe(frame)), offset);
            }
            case "get_frame" -> {
                Frame frame = frames.find(id);
                if (frame == null)
                    throw new IllegalArgumentException("No frame with the id '" + id + "', see list_frames");
                status.accept(I18nManager.getText("brainstorm.tool.frame", frame.getName()));
                return entry(frame.getId(), frame.getName(),
                        favorites != null && favorites.isFavorite(FavoriteService.Kind.FRAME, frame.getId()))
                        .put("standard", frame.isDefault()).put("aspect_ratio", frame.getAspectRatio())
                        .put("resolution", frame.getResolution() != null ? frame.getResolution() : JSONObject.NULL);
            }
            case "list_saved_images" -> {
                status.accept(I18nManager.getText("brainstorm.tool.saved.images"));
                return list("saved_images", savedImages.getAll(), (SavedImage image) -> entry(image.id(),
                        image.name(), image.favorite())
                        .put("description", image.description() != null ? image.description() : "")
                        .put("size", image.describe()), offset);
            }
            default -> throw new IllegalArgumentException("Unknown tool " + call.name());
        }
    }

    /*  the entries of a list in the order of the lists of the app: the favorites first, then by name  */
    private <T> List<T> ordered(List<T> entries, FavoriteService.Kind kind, Function<T, String> id,
            Function<T, String> name) {
        return FavoriteService.order(entries, (T entry) -> favorites != null
                && favorites.isFavorite(kind, id.apply(entry)), (T entry) -> 0, name);
    }

    /**
     * Lists the entries from an offset as long as the answer stays below the length of a tool result; a list cut
     * short names the offset to continue from.
     */
    private static <T> JSONObject list(String key, List<T> entries, Function<T, JSONObject> entry, int offset) {
        JSONArray array = new JSONArray();
        int length = 0;
        int index = offset;
        for (; index < entries.size(); index++) {
            String json = entry.apply(entries.get(index)).toString();
            if (length + json.length() > BrainstormTools.TOOL_RESULT_MAX_LENGTH - 100 && !array.isEmpty())
                break;
            array.put(new JSONObject(json));
            length += json.length() + 1;
        }
        JSONObject result = new JSONObject().put(key, array).put("total", entries.size());
        if (index < entries.size())
            result.put("next_offset", index);
        return result;
    }

    private static JSONObject entry(String id, String name, boolean favorite) {
        JSONObject json = new JSONObject().put("id", id).put("name", name);
        return favorite ? json.put("favorite", true) : json;
    }

    /*  a mood of the list: the names of its building blocks in use, e.g. "style Analog Film, light Golden Hour"  */
    private JSONObject moodEntry(Mood mood) {
        PromptComposer.Selection selection = moods.selection(mood);
        List<String> parts = new ArrayList<>();
        blocks(selection).forEach((Mood.Field field, String name) -> parts.add(key(field) + " " + name));
        return entry(mood.getId(), mood.getName(),
                favorites != null && favorites.isFavorite(FavoriteService.Kind.MOOD, mood.getId()))
                .put("blocks", String.join(", ", parts));
    }

    /*  a mood with its building blocks in use: id, name and text, a palette with its colors  */
    private JSONObject mood(Mood mood) {
        PromptComposer.Selection selection = moods.selection(mood);
        JSONObject fields = new JSONObject();
        fields.put(key(Mood.Field.STYLE), block(selection.style()));
        fields.put(key(Mood.Field.ATMOSPHERE), block(selection.atmosphere()));
        fields.put(key(Mood.Field.LIGHT), block(selection.light()));
        ColorStyle palette = selection.palette();
        fields.put(key(Mood.Field.COLOR), palette == null ? JSONObject.NULL : new JSONObject()
                .put("id", palette.getId()).put("name", palette.getName())
                .put("colors", new JSONArray(palette.getColors())));
        fields.put(key(Mood.Field.CAMERA), block(selection.camera()));
        return entry(mood.getId(), mood.getName(),
                favorites != null && favorites.isFavorite(FavoriteService.Kind.MOOD, mood.getId()))
                .put("standard", mood.isDefault()).put("blocks", fields);
    }

    /*  a text block with its text; a camera has its settings as text, as a photographic style shows them  */
    private static Object block(PromptBlock block) {
        if (block == null)
            return JSONObject.NULL;
        String text = block.getType() == PromptBlock.Type.CAMERA
                ? CameraPromptBuilder.build(CameraSettings.fromJson(block.getParams()), true) : block.getText();
        return new JSONObject().put("id", block.getId()).put("name", block.getName()).put("text", text);
    }

    /*  the names of the building blocks in use by their field, in the order of the draft  */
    private static Map<Mood.Field, String> blocks(PromptComposer.Selection selection) {
        Map<Mood.Field, String> names = new LinkedHashMap<>();
        if (selection.style() != null)
            names.put(Mood.Field.STYLE, selection.style().getName());
        if (selection.atmosphere() != null)
            names.put(Mood.Field.ATMOSPHERE, selection.atmosphere().getName());
        if (selection.light() != null)
            names.put(Mood.Field.LIGHT, selection.light().getName());
        if (selection.palette() != null)
            names.put(Mood.Field.COLOR, selection.palette().getName());
        if (selection.camera() != null)
            names.put(Mood.Field.CAMERA, selection.camera().getName());
        return names;
    }

    private static String key(Mood.Field field) {
        return field.name().toLowerCase(Locale.ROOT);
    }
}
