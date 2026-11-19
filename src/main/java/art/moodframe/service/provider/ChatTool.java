package art.moodframe.service.provider;

import org.json.JSONObject;

/**
 * A tool the caller offers a language model, in the format of no provider; each chat client declares it in its own
 * request (a Gemini function declaration, an OpenAI function tool).
 *
 * @param name        the name the model calls it by
 * @param description what it does and when to use it, for the model
 * @param parameters  its arguments as a JSON Schema object
 * @since 2.1.3
 */
public record ChatTool(String name, String description, JSONObject parameters) {
}
