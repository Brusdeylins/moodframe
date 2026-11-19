package art.moodframe.service.brainstorm;

import art.moodframe.model.ApiProvider;
import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.BrainstormTurn;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.net.IoExecutor;
import art.moodframe.service.provider.GeminiApiService;
import art.moodframe.service.provider.OpenAiApiService;
import art.moodframe.service.provider.ProviderPolicy;
import art.moodframe.util.I18nManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Derives the building blocks of a mood from an image: the palette locally ({@link PaletteExtractor}), the
 * atmosphere, the style, the light and the camera from the language model of the brainstorm (Gemini or OpenAI), which
 * gets the scaled-down image, the rules of the building blocks and the lists, and names an entry or a new block for
 * each, a new camera with its settings.
 *
 * @since 2.0.0
 */
public class MoodAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(MoodAnalysisService.class);
    private static final String TEMPLATE_RESOURCE = "/brainstorm/mood-from-image.txt";
    private static final String REQUEST = "Find the building blocks of this image.";

    /** The longest side of the image the language model gets, enough to see medium, mood and light. */
    private static final int MAX_SIDE = 1024;

    /** The fields the language model fills; the palette comes from the pixels. */
    static final List<Mood.Field> MODEL_FIELDS = List.of(Mood.Field.ATMOSPHERE, Mood.Field.STYLE, Mood.Field.LIGHT,
            Mood.Field.CAMERA);

    /**
     * The blocks found in an image.
     *
     * @param blocks the blocks by field: an entry of the lists with its id, or a new block with a name no entry of
     *               its list has; a field without a block is missing
     * @param notice why the language model added nothing (none set up, or its request failed), null if it answered
     */
    public record Analysis(Map<Mood.Field, BrainstormDraft.Block> blocks, String notice) {
    }

    private final GeminiApiService geminiService;
    private final OpenAiApiService openAiService;
    private final ApplicationConfig config;
    private final String template;

    /**
     * Creates the service. The language model follows the choice of the brainstorm in the configuration, read for
     * every analysis.
     *
     * @param geminiService the Gemini API service, null if there is none
     * @param openAiService the OpenAI API service, null if there is none
     * @param config the configuration with the brainstorm engine and the OpenAI language model
     */
    public MoodAnalysisService(GeminiApiService geminiService, OpenAiApiService openAiService,
            ApplicationConfig config) {
        this.geminiService = geminiService;
        this.openAiService = openAiService;
        this.config = config;
        this.template = loadTemplate();
    }

    private static String loadTemplate() {
        try (InputStream in = MoodAnalysisService.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (in == null)
                throw new IllegalStateException("Missing resource " + TEMPLATE_RESOURCE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new IllegalStateException("Cannot read " + TEMPLATE_RESOURCE, e);
        }
    }

    /** Whether Gemini may answer: a key, the provider enabled, and not OpenAI as the only provider in use. */
    private boolean geminiUsable() {
        return geminiService != null && config.accounts().isUsable(ApiProvider.GOOGLE_GEMINI)
                && !ProviderPolicy.isOpenAiOnly(config);
    }

    /**
     * The language model of the analysis: OpenAI if the brainstorm talks to it or Gemini is not usable, as in the
     * brainstorm, else Gemini; null if neither is usable.
     */
    private ChatModel chatModel() {
        boolean geminiUsable = geminiUsable();
        ChatModel model = ChatModel.choose(geminiService, openAiService, config,
                "openai".equals(config.accounts().getBrainstormEngine()), geminiUsable);
        return model.watchesVideos() && !geminiUsable ? null : model;
    }

    /**
     * Checks if a language model is set up for the atmosphere, the style, the light and the camera.
     *
     * @return true if Gemini or an OpenAI language model can answer
     */
    public boolean hasLanguageModel() {
        return chatModel() != null;
    }

    /**
     * Analyzes an image in the background. Without a language model, or if its request fails, only the palette is
     * found and the analysis says why.
     *
     * @param image the image
     * @param context the lists of the building blocks the language model chooses from
     * @return the found blocks
     */
    public CompletableFuture<Analysis> analyze(BufferedImage image, BrainstormContext context) {
        /*  The colors and the encoded image on a worker; the answer of the language model is composed, so the worker
            never waits for another task of the IoExecutor  */
        return CompletableFuture.supplyAsync(() -> prepare(image, context), IoExecutor.get())
                .thenCompose((Prepared prepared) -> {
                    if (!prepared.asks())
                        return CompletableFuture.completedFuture(new Analysis(colorsOnly(context, prepared.colors()),
                                I18nManager.getText("mood.image.no.model")));
                    CompletableFuture<String> raw = prepared.failure() != null
                            ? CompletableFuture.failedFuture(prepared.failure())
                            : ask(prepared.systemPrompt(), prepared.contents());
                    return raw.handle((String reply, Throwable error) -> {
                        if (error == null) {
                            try {
                                return new Analysis(parseReply(reply, context, prepared.colors()), null);
                            }
                            catch (RuntimeException e) {
                                error = e;
                            }
                        }
                        Throwable cause = error instanceof CompletionException && error.getCause() != null
                                ? error.getCause() : error;
                        logger.warn("Image analysis failed: {}", cause.getMessage());
                        return new Analysis(colorsOnly(context, prepared.colors()),
                                I18nManager.getText("mood.image.error", cause.getMessage()));
                    });
                });
    }

    /** The colors of an image and the request about it; a failure to build the request is kept for the answer. */
    private record Prepared(List<String> colors, boolean asks, String systemPrompt, JSONArray contents,
                            Exception failure) {
    }

    /*  the colors, and the request if a language model is configured, on a worker  */
    private Prepared prepare(BufferedImage image, BrainstormContext context) {
        List<String> colors = PaletteExtractor.extract(image);
        if (!hasLanguageModel())
            return new Prepared(colors, false, null, null, null);
        try {
            return new Prepared(colors, true, prompt(template, context), contents(encodeScaled(image)), null);
        }
        catch (IOException | RuntimeException e) {
            return new Prepared(colors, true, null, null, e);
        }
    }

    /** Sends the image to the language model of the brainstorm, without tools, and returns its JSON text. */
    private CompletableFuture<String> ask(String systemPrompt, JSONArray contents) {
        try {
            return chatModel().chatTurn(systemPrompt, contents, false, false).thenApply(BrainstormTurn::text);
        }
        catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Fills the template with the language of the user interface, the lists of atmospheres, styles, lights and cameras
     * and the camera settings.
     *
     * @param template the template with {@code {{...}}} placeholders
     * @param context the lists
     * @return the system prompt
     */
    static String prompt(String template, BrainstormContext context) {
        return template
                .replace("{{UI_LANGUAGE}}", BrainstormService.language())
                .replace("{{ATMOSPHERES}}", BrainstormPromptBuilder.blocks(context.atmospheres()))
                .replace("{{STYLES}}", BrainstormPromptBuilder.styles(context))
                .replace("{{LIGHTS}}", BrainstormPromptBuilder.blocks(context.lights()))
                .replace("{{CAMERAS}}", BrainstormPromptBuilder.cameras(context.cameras()))
                .replace("{{CAMERA_SETTINGS}}", BrainstormPromptBuilder.cameraSettings());
    }

    /**
     * The conversation of the analysis in the Gemini format: one user turn with the image as {@code inlineData}
     * part and the request as text part.
     *
     * @param jpeg the image as JPEG
     * @return the contents
     */
    static JSONArray contents(byte[] jpeg) {
        JSONObject image = new JSONObject().put("inlineData", new JSONObject().put("mimeType", "image/jpeg")
                .put("data", Base64.getEncoder().encodeToString(jpeg)));
        return new JSONArray().put(new JSONObject().put("role", "user")
                .put("parts", new JSONArray().put(image).put(new JSONObject().put("text", REQUEST))));
    }

    /**
     * Scales the image down to at most {@value #MAX_SIDE} pixels per side, the size the analysis takes; transparent
     * parts become white.
     *
     * @param image the image
     * @return an RGB copy of the image, scaled down if it is larger
     */
    public static BufferedImage scaledDown(BufferedImage image) {
        double scale = Math.min(1.0, MAX_SIDE / (double) Math.max(image.getWidth(), image.getHeight()));
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(image, 0, 0, width, height, Color.WHITE, null);
        graphics.dispose();
        return rgb;
    }

    /**
     * Scales the image down like {@link #scaledDown(BufferedImage)} and encodes it as JPEG.
     *
     * @param image the image
     * @return the JPEG bytes
     * @throws IOException if the image cannot be encoded
     */
    static byte[] encodeScaled(BufferedImage image) throws IOException {
        BufferedImage rgb = scaledDown(image);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(rgb, "jpg", out))
            throw new IOException("No JPEG encoder");
        return out.toByteArray();
    }

    /**
     * Reads the answer of the language model. A block naming an entry of its list refers to it by id, a block naming
     * none is left out; a new block (a camera with its settings, any other with its text) whose name an entry has
     * already gets the next free name. The palette is a new block with the colors of the pixels and the name from the
     * answer.
     *
     * @param raw the answer, a JSON object possibly with text around it
     * @param context the lists
     * @param colors the colors of the pixels
     * @return the blocks by field
     * @throws IllegalArgumentException if the answer holds no JSON object
     */
    static Map<Mood.Field, BrainstormDraft.Block> parseReply(String raw, BrainstormContext context,
            List<String> colors) {
        int start = raw == null ? -1 : raw.indexOf('{');
        int end = raw == null ? -1 : raw.lastIndexOf('}');
        if (start < 0 || end <= start)
            throw new IllegalArgumentException("The answer holds no JSON object");
        JSONObject json = new JSONObject(raw.substring(start, end + 1));

        Map<Mood.Field, BrainstormDraft.Block> blocks = new EnumMap<>(Mood.Field.class);
        for (Mood.Field field : MODEL_FIELDS) {
            BrainstormDraft.Block block = BrainstormDraft.Block.fromJson(json.opt(field.name().toLowerCase(Locale.ROOT)));
            if (block == null)
                continue;
            if (block.isNew() && field == Mood.Field.CAMERA && block.settings() != null)
                blocks.put(field, new BrainstormDraft.Block(null, freeName(context, field, block.name()), null,
                        List.of(), block.settings(), true));
            else if (block.isNew() && field != Mood.Field.CAMERA && block.text() != null)
                blocks.put(field, new BrainstormDraft.Block(null, freeName(context, field, block.name()), block.text(),
                        List.of(), true));
            else if (!block.isNew() && context.idOf(field, block.name()) != null)
                blocks.put(field, block.withId(context.idOf(field, block.name())));
        }
        BrainstormDraft.Block palette = palette(context, colors, json.optString("color_name", "").trim());
        if (palette != null)
            blocks.put(Mood.Field.COLOR, palette);
        return blocks;
    }

    private static Map<Mood.Field, BrainstormDraft.Block> colorsOnly(BrainstormContext context, List<String> colors) {
        Map<Mood.Field, BrainstormDraft.Block> blocks = new EnumMap<>(Mood.Field.class);
        BrainstormDraft.Block palette = palette(context, colors, "");
        if (palette != null)
            blocks.put(Mood.Field.COLOR, palette);
        return blocks;
    }

    /** The colors as a new palette under a free name, a default name if none is given; null without colors. */
    private static BrainstormDraft.Block palette(BrainstormContext context, List<String> colors, String name) {
        if (colors.isEmpty())
            return null;
        String wanted = name.isEmpty() ? I18nManager.getText("mood.image.palette") : name;
        return new BrainstormDraft.Block(null, freeName(context, Mood.Field.COLOR, wanted), null, List.copyOf(colors),
                true);
    }

    /**
     * Returns the name unchanged if no entry of the list of the field has it, else the name with the next free
     * counter, e.g. {@code "Foggy Harbor (1)"}.
     *
     * @param context the lists
     * @param field the field
     * @param name the wanted name
     * @return a name no entry of the list has
     */
    static String freeName(BrainstormContext context, Mood.Field field, String name) {
        List<String> taken = field == Mood.Field.COLOR
                ? context.colorStyles().stream().map(ColorStyle::getName).toList()
                : context.blocks(field).stream().map(PromptBlock::getName).toList();
        String free = name;
        for (int counter = 1; taken.contains(free); counter++)
            free = name + " (" + counter + ")";
        return free;
    }
}
