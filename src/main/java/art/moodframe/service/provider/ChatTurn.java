package art.moodframe.service.provider;

import org.json.JSONObject;

import java.util.List;

/**
 * One turn of a conversation with a language model, in the format of no provider: a turn of the user (text and
 * images), an answer of the model (text, or the tools it calls) or the results of those tools. The caller keeps the
 * conversation as a list of turns and owns its tools; each chat client translates the turns into its own request and
 * its answer back into a {@link Model} turn.
 *
 * @since 2.1.3
 */
public sealed interface ChatTurn {

    /**
     * A turn of the user.
     *
     * @param text   the text, may be empty
     * @param images the attached images, in their order
     */
    record User(String text, List<Image> images) implements ChatTurn {

        public User {
            images = List.copyOf(images);
        }

        /**
         * A turn of the user with text only.
         *
         * @param text the text
         */
        public User(String text) {
            this(text, List.of());
        }
    }

    /**
     * An answer of the model: its text, or the tools it calls.
     *
     * @param text   the text, empty if the model only calls tools
     * @param calls  the tool calls, empty if the model answered with text
     * @param replay the answer as the provider sent it, which the same provider gets back unchanged in the next request
     *               of the turn (Gemini requires the thought signatures of its tool calls); null if there is none, then
     *               the client builds the turn from the text and the calls
     */
    record Model(String text, List<ToolCall> calls, String replay) implements ChatTurn {

        public Model {
            calls = List.copyOf(calls);
        }

        /**
         * An answer with text only.
         *
         * @param text the text
         */
        public Model(String text) {
            this(text, List.of(), null);
        }
    }

    /**
     * The results of the tool calls of the answer before, in the order of the calls.
     *
     * @param results the results
     */
    record ToolResults(List<ToolResult> results) implements ChatTurn {

        public ToolResults {
            results = List.copyOf(results);
        }
    }

    /**
     * An image of a user turn.
     *
     * @param mimeType the type, for example {@code image/jpeg}
     * @param base64   the bytes in Base64
     */
    record Image(String mimeType, String base64) {
    }

    /**
     * A tool the model calls.
     *
     * @param id   the id the result refers to: the one of the provider, else one the client made up
     * @param name the name of the tool
     * @param args the arguments, an empty object if there are none
     */
    record ToolCall(String id, String name, JSONObject args) {
    }

    /**
     * The result of a tool call.
     *
     * @param call     the call
     * @param response what the tool found, or an error, as a JSON object for the model
     */
    record ToolResult(ToolCall call, JSONObject response) {
    }
}
