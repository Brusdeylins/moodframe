package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.BrainstormTurn;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.util.I18nManager;
import art.moodframe.util.PaletteExtractor;
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
 * atmosphere, the style and the light from the language model of the brainstorm (Gemini or OpenAI), which gets the
 * scaled-down image, the rules of the building blocks and the lists, and names an entry or a new block for each.
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
    static final List<Mood.Field> TEXT_FIELDS = List.of(Mood.Field.ATMOSPHERE, Mood.Field.STYLE, Mood.Field.LIGHT);

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
     * Creates the service the application uses: the Gemini and OpenAI services of the application and its
     * configuration.
     *
     * @param config the application configuration
     * @return the service
     */
    public static MoodAnalysisService create(ApplicationConfig config) {
        art.moodframe.config.ServiceFactory services = art.moodframe.config.ServiceFactory.getInstance();
        return new MoodAnalysisService(services.getService(GeminiApiService.class),
                services.getService(OpenAiApiService.class), config);
    }

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
        return geminiService != null && config.hasGeminiApiKey() && config.isGeminiProviderEnabled()
                && !ProviderPolicy.isOpenAiOnly(config);
    }

    /**
     * The OpenAI language model if the analysis talks to OpenAI (the brainstorm does, or Gemini is not usable),
     * else null for Gemini; as in the brainstorm.
     */
    private String openAiModel() {
        return openAiService != null && config.isOpenAiBrainstormAvailable()
                && ("openai".equals(config.getBrainstormEngine()) || !geminiUsable()) ? config.getOpenAiChatModel() : null;
    }

    /**
     * Checks if a language model is set up for the atmosphere, the style and the light.
     *
     * @return true if Gemini or an OpenAI language model can answer
     */
    public boolean hasLanguageModel() {
        return openAiModel() != null || geminiUsable();
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
        return CompletableFuture.supplyAsync(() -> {
            List<String> colors = PaletteExtractor.extract(image);
            if (!hasLanguageModel())
                return new Analysis(colorsOnly(context, colors), I18nManager.getText("mood.image.no.model"));
            try {
                String raw = ask(prompt(template, context), contents(encodeScaled(image)));
                return new Analysis(parseReply(raw, context, colors), null);
            }
            catch (IOException | RuntimeException e) {
                Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
                logger.warn("Image analysis failed: {}", cause.getMessage());
                return new Analysis(colorsOnly(context, colors),
                        I18nManager.getText("mood.image.error", cause.getMessage()));
            }
        });
    }

    /** Sends the image to the language model of the brainstorm, without tools, and returns its JSON text. */
    private String ask(String systemPrompt, JSONArray contents) {
        String openAiModel = openAiModel();
        BrainstormTurn turn = (openAiModel != null
                ? openAiService.chatTurn(openAiModel, systemPrompt, contents, false, false, false)
                : geminiService.chatTurn(systemPrompt, contents, false, false)).join();
        return turn.text();
    }

    /**
     * Fills the template with the language of the user interface and the lists of atmospheres, styles and lights.
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
                .replace("{{LIGHTS}}", BrainstormPromptBuilder.blocks(context.lights()));
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
     * Scales the image down to at most {@value #MAX_SIDE} pixels per side and encodes it as JPEG; transparent parts
     * become white.
     *
     * @param image the image
     * @return the JPEG bytes
     * @throws IOException if the image cannot be encoded
     */
    static byte[] encodeScaled(BufferedImage image) throws IOException {
        double scale = Math.min(1.0, MAX_SIDE / (double) Math.max(image.getWidth(), image.getHeight()));
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(image, 0, 0, width, height, Color.WHITE, null);
        graphics.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(rgb, "jpg", out))
            throw new IOException("No JPEG encoder");
        return out.toByteArray();
    }

    /**
     * Reads the answer of the language model. A block naming an entry of its list refers to it by id, a block naming
     * none is left out; a new block whose name an entry has already gets the next free name. The palette is a new
     * block with the colors of the pixels and the name from the answer.
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
        for (Mood.Field field : TEXT_FIELDS) {
            BrainstormDraft.Block block = BrainstormDraft.Block.fromJson(json.opt(field.name().toLowerCase(Locale.ROOT)));
            if (block == null)
                continue;
            if (block.isNew() && block.text() != null)
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
