package art.moodframe.mcp;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.service.brainstorm.MoodAnalysisService;
import art.moodframe.service.chat.ChatLibrary;
import art.moodframe.service.generation.GenerationService;
import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.FavoriteService;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.provider.ProviderException;
import art.moodframe.service.provider.ProviderRegistry;
import art.moodframe.service.trash.TrashCenter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tools of the MCP server, each with name, description, JSON schema of its input and handler. A handler answers
 * with structured JSON and optionally an image; an unknown ID or an invalid input comes back as tool error with a
 * clear message ({@code isError}), not as protocol error.
 *
 * @since 2.1.0
 */
public class McpTools {

    private static final Logger logger = LoggerFactory.getLogger(McpTools.class);

    /** Builds the JSON of the tools. */
    static final ObjectMapper JSON = new ObjectMapper();

    /** An unknown ID or an invalid input; the client gets the message as tool error. */
    public static class ToolException extends Exception {

        /**
         * Creates the error.
         *
         * @param message what is wrong, for the client
         */
        public ToolException(String message) {
            super(message);
        }
    }

    /**
     * An image a tool returns besides its structured result.
     *
     * @param data     the bytes of the image
     * @param mimeType the MIME type, for example {@code image/png}
     */
    public record Image(byte[] data, String mimeType) {
    }

    /**
     * What a tool returns.
     *
     * @param structured the result as JSON object
     * @param image      an image, null for none
     */
    public record Result(ObjectNode structured, Image image) {

        /**
         * Creates a result without image.
         *
         * @param structured the result as JSON object
         */
        public Result(ObjectNode structured) {
            this(structured, null);
        }
    }

    /** Carries out a call of a tool, on a thread of the server. */
    @FunctionalInterface
    public interface Handler {

        /**
         * Carries out the call.
         *
         * @param arguments the arguments, an empty object without arguments
         * @return the result
         * @throws ToolException for an unknown ID or an invalid input
         * @throws IOException   if a file cannot be read
         * @throws SQLException  if the database cannot be written
         */
        Result call(JsonNode arguments) throws ToolException, IOException, SQLException;
    }

    private record Tool(String description, ObjectNode inputSchema, Handler handler) {
    }

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    /**
     * Creates the reading tools of the chats, the library and the models.
     *
     * @param config      the configuration (enabled providers, keys, defaults of the parameters)
     * @param chats       the chats of the main window
     * @param moods       the moods
     * @param blocks      the text building blocks
     * @param colors      the palettes
     * @param frames      the frames
     * @param favorites   the favorites
     * @param trash       the trash
     * @param events      where a download is reported for the status bar
     * @param providers   the providers: their models to choose from and their prompt limits
     * @return the tools
     */
    public static McpTools reading(ApplicationConfig config, ChatLibrary chats, MoodService moods,
            PromptBlockService blocks, ColorStyleService colors, FrameService frames, FavoriteService favorites,
            TrashCenter trash, LibraryEvents events, ProviderRegistry providers) {
        McpTools tools = new McpTools();
        new ChatTools(chats, events, config.paths().downloads()).addTo(tools);
        new LibraryTools(config, moods, blocks, colors, frames, favorites, trash, providers).addTo(tools);
        return tools;
    }

    /**
     * Creates all tools: those that read, those that change the chats and the library, and those that generate.
     *
     * @param config     the configuration (enabled providers, keys, defaults of the parameters)
     * @param chats      the chats of the main window
     * @param moods      the moods
     * @param blocks     the text building blocks
     * @param colors     the palettes
     * @param frames     the frames
     * @param favorites  the favorites
     * @param trash      the trash
     * @param events     where a download is reported for the status bar
     * @param generation carries out and cancels the generation orders
     * @param analysis   finds the building blocks of an image
     * @return the tools
     */
    public static McpTools all(ApplicationConfig config, ChatLibrary chats, MoodService moods,
            PromptBlockService blocks, ColorStyleService colors, FrameService frames, FavoriteService favorites,
            TrashCenter trash, LibraryEvents events, GenerationService generation, MoodAnalysisService analysis) {
        McpTools tools = reading(config, chats, moods, blocks, colors, frames, favorites, trash, events,
                generation.providers());
        new ChatEditTools(chats, generation).addTo(tools);
        new LibraryEditTools(chats, moods, blocks, colors, frames, favorites, trash, analysis).addTo(tools);
        new GenerateTools(config, chats, moods, blocks, colors, frames, generation).addTo(tools);
        return tools;
    }

    /**
     * Adds a tool.
     *
     * @param name        the name in snake_case
     * @param description what the tool does and returns
     * @param inputSchema the JSON schema of the arguments
     * @param handler     carries out the calls
     */
    public void add(String name, String description, ObjectNode inputSchema, Handler handler) {
        tools.put(name, new Tool(description, inputSchema, handler));
    }

    /**
     * Tells if a tool exists.
     *
     * @param name the name
     * @return true for a tool of this name
     */
    public boolean has(String name) {
        return tools.containsKey(name);
    }

    /**
     * Lists the tools as {@code tools/list} returns them.
     *
     * @return the tools in the order they were added
     */
    public ArrayNode list() {
        ArrayNode list = JSON.createArrayNode();
        tools.forEach((String name, Tool tool) -> {
            ObjectNode entry = list.addObject().put("name", name).put("description", tool.description());
            entry.set("inputSchema", tool.inputSchema());
        });
        return list;
    }

    /**
     * Calls a tool and wraps its result as {@code tools/call} returns it: an image content if any, the JSON as text
     * content and as {@code structuredContent}. Errors come back as tool error.
     *
     * @param name      the name of an existing tool
     * @param arguments the arguments, missing or null for none
     * @return the result of the call
     */
    public ObjectNode call(String name, JsonNode arguments) {
        try {
            Result result = tools.get(name).handler().call(arguments != null && arguments.isObject() ? arguments
                    : JSON.createObjectNode());
            ObjectNode response = JSON.createObjectNode();
            ArrayNode content = response.putArray("content");
            if (result.image() != null)
                content.addObject().put("type", "image").put("mimeType", result.image().mimeType())
                        .put("data", Base64.getEncoder().encodeToString(result.image().data()));
            content.addObject().put("type", "text").put("text", JSON.writeValueAsString(result.structured()));
            response.set("structuredContent", result.structured());
            return response.put("isError", false);
        }
        catch (ToolException e) {
            return error(e.getMessage());
        }
        catch (Exception e) {
            logger.error("MCP tool {} failed", name, e);
            return error(failureText(e));
        }
    }

    /**
     * Gets the text of a failure for the client, always in English and never with the name of a class: the message of
     * a provider failure however deeply it is wrapped, else a general text by the kind of failure; the log has the
     * details.
     *
     * @param failure the failure
     * @return the text
     */
    static String failureText(Throwable failure) {
        Throwable link = failure;
        for (int depth = 0; link != null && depth < 32; depth++, link = link.getCause()) {
            if (link instanceof ProviderException)
                return "The tool failed: " + ProviderException.messageOf(failure, true);
        }
        Throwable cause = ProviderException.unwrap(failure);
        if (cause instanceof IOException || cause instanceof UncheckedIOException)
            return "The tool failed: a file could not be read or written.";
        if (cause instanceof SQLException)
            return "The tool failed: the library could not be read or written.";
        return "The tool failed unexpectedly; the log of MoodFrame tells why.";
    }

    private static ObjectNode error(String message) {
        ObjectNode response = JSON.createObjectNode();
        response.putArray("content").addObject().put("type", "text").put("text", message);
        return response.put("isError", true);
    }

    /*  ===== Input schemas =====  */

    /**
     * The schema of an object with properties.
     *
     * @param properties the schemas of the properties by name
     * @param required   the names of the required properties
     * @return the schema
     */
    static ObjectNode input(ObjectNode properties, String... required) {
        ObjectNode schema = JSON.createObjectNode().put("type", "object");
        schema.set("properties", properties);
        if (required.length > 0)
            schema.set("required", JSON.valueToTree(required));
        return schema.put("additionalProperties", false);
    }

    /** The schema of a string. */
    static ObjectNode string(String description) {
        return JSON.createObjectNode().put("type", "string").put("description", description);
    }

    /** The schema of an integer within limits, with a default. */
    static ObjectNode integer(String description, int minimum, int maximum, int defaultValue) {
        return JSON.createObjectNode().put("type", "integer").put("description", description).put("minimum", minimum)
                .put("maximum", maximum).put("default", defaultValue);
    }

    /** The schema of a string or null. */
    static ObjectNode nullableString(String description) {
        ObjectNode schema = JSON.createObjectNode().put("description", description);
        schema.set("type", JSON.valueToTree(List.of("string", "null")));
        return schema;
    }

    /** The schema of a boolean. */
    static ObjectNode bool(String description) {
        return JSON.createObjectNode().put("type", "boolean").put("description", description);
    }

    /** The schema of a list of strings with at most this many items. */
    static ObjectNode strings(String description, int maxItems) {
        ObjectNode schema = JSON.createObjectNode().put("type", "array").put("description", description)
                .put("maxItems", maxItems);
        schema.putObject("items").put("type", "string");
        return schema;
    }

    /** The schema of a string out of fixed values, with a default (null for none). */
    static ObjectNode choice(String description, List<String> values, String defaultValue) {
        ObjectNode schema = string(description);
        schema.set("enum", JSON.valueToTree(values));
        return defaultValue != null ? schema.put("default", defaultValue) : schema;
    }

    /*  ===== Arguments =====  */

    /**
     * A required string argument.
     *
     * @param arguments the arguments
     * @param name      the name of the argument
     * @return the trimmed value
     * @throws ToolException if it is missing, empty or no string
     */
    static String text(JsonNode arguments, String name) throws ToolException {
        JsonNode value = arguments.get(name);
        if (value == null || !value.isTextual() || value.asText().isBlank())
            throw new ToolException("The argument '" + name + "' is required and must be a non-empty string");
        return value.asText().trim();
    }

    /**
     * An optional string argument.
     *
     * @param arguments the arguments
     * @param name      the name of the argument
     * @return the trimmed value, null if it is missing or null
     * @throws ToolException if it is empty or no string
     */
    static String optionalText(JsonNode arguments, String name) throws ToolException {
        JsonNode value = arguments.get(name);
        return value == null || value.isNull() ? null : text(arguments, name);
    }

    /**
     * An optional boolean argument.
     *
     * @param arguments    the arguments
     * @param name         the name of the argument
     * @param defaultValue the value if it is missing
     * @return the value
     * @throws ToolException if it is no boolean
     */
    static boolean flag(JsonNode arguments, String name, boolean defaultValue) throws ToolException {
        JsonNode value = arguments.get(name);
        if (value == null || value.isNull())
            return defaultValue;
        if (!value.isBoolean())
            throw new ToolException("The argument '" + name + "' must be true or false");
        return value.asBoolean();
    }

    /**
     * An optional integer argument within limits.
     *
     * @param arguments    the arguments
     * @param name         the name of the argument
     * @param minimum      the smallest value
     * @param maximum      the largest value
     * @param defaultValue the value if it is missing
     * @return the value
     * @throws ToolException if it is no integer or out of the limits
     */
    static int number(JsonNode arguments, String name, int minimum, int maximum, int defaultValue)
            throws ToolException {
        JsonNode value = arguments.get(name);
        if (value == null || value.isNull())
            return defaultValue;
        if (!value.canConvertToInt() || !value.isIntegralNumber() || value.asInt() < minimum
                || value.asInt() > maximum)
            throw new ToolException("The argument '" + name + "' must be an integer from " + minimum + " to "
                    + maximum);
        return value.asInt();
    }

    /**
     * A string argument out of fixed values.
     *
     * @param arguments    the arguments
     * @param name         the name of the argument
     * @param values       the allowed values
     * @param defaultValue the value if it is missing, null if the argument is required
     * @return the value
     * @throws ToolException if it is missing though required, or not one of the values
     */
    static String choice(JsonNode arguments, String name, List<String> values, String defaultValue)
            throws ToolException {
        JsonNode value = arguments.get(name);
        if ((value == null || value.isNull()) && defaultValue != null)
            return defaultValue;
        if (value == null || !values.contains(value.asText()))
            throw new ToolException("The argument '" + name + "' must be one of " + String.join(", ", values));
        return value.asText();
    }

    /**
     * An amount in USD, rounded to hundredths of a cent.
     *
     * @param amount the amount
     * @return the rounded amount
     */
    static double usd(double amount) {
        return Math.round(amount * 10000.0) / 10000.0;
    }

    /**
     * Reads JSON text the app wrote itself.
     *
     * @param json the JSON text
     * @return the JSON
     */
    static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid JSON of the app", e);
        }
    }

    /**
     * A point in time as JSON text.
     *
     * @param time the time (may be null)
     * @return the ISO text, null without time
     */
    static String time(LocalDateTime time) {
        return time != null ? time.toString() : null;
    }
}
