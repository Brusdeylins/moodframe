package tools.moodframe.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * One answer of the Gemini chat model: its text, or the tool calls it wants to have run.
 *
 * @param text the concatenated text parts
 * @param parts the raw parts of the answer; they are sent back unchanged in the next request of the same turn,
 *              because the API requires the thought signature of a function call
 * @param calls the function calls of the answer, empty if the model answered with text
 * @since 1.8.1
 */
public record BrainstormTurn(String text, JSONArray parts, List<Call> calls) {

    /**
     * A function call of the model.
     *
     * @param name the function name
     * @param args the arguments
     */
    public record Call(String name, JSONObject args) {
    }
}
