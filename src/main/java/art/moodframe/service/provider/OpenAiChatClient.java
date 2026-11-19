package art.moodframe.service.provider;

import art.moodframe.model.ResearchNote;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The language model of the OpenAI client for the brainstorm: chat turns with the brainstorm tools
 * ({@code /chat/completions}), the research with the built-in web search ({@code /responses}), the checks of a
 * model and plain completions. The methods of the {@link OpenAiApiService} of the same names describe them.
 *
 * @since 2.1.3
 */
final class OpenAiChatClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiChatClient.class);

    private static final String RESEARCH_INSTRUCTION = "Search the web for this question and base your answer only "
            + "on what the search results say, never on memory. Research the following for an image or video brief. "
            + "Answer concisely in the language of the question with concrete, checkable facts (numbers, names, "
            + "terms), at most 300 words. Question: ";

    private final OpenAiTransport transport;

    OpenAiChatClient(OpenAiTransport transport) {
        this.transport = transport;
    }

    CompletableFuture<ChatTurn.Model> chatTurn(String model, String systemPrompt, List<ChatTurn> turns,
            List<ChatTool> tools, boolean noMoreCalls) {
        return CompletableFuture.supplyAsync(() -> {
            ChatTurn.Model answer = parseAnswer(postChat(buildChatBody(model, systemPrompt, turns, tools,
                    noMoreCalls)));
            if (answer.text().isEmpty() && answer.calls().isEmpty())
                throw ProviderException.localized("brainstorm.error.no.response");
            return answer;
        }, transport.io());
    }

    /**
     * Reads the message of an answer: its content as text and its {@code tool_calls} as tool calls with their ids
     * ({@code call_<index>} if one has none); arguments that are no JSON object count as none.
     *
     * @param message the message of the first choice
     * @return the answer, without a replay: the turn is rebuilt from the text and the calls
     */
    static ChatTurn.Model parseAnswer(JSONObject message) {
        String text = message.isNull("content") ? "" : message.optString("content", "");
        List<ChatTurn.ToolCall> calls = new ArrayList<>();
        JSONArray toolCalls = message.optJSONArray("tool_calls");
        for (int i = 0; toolCalls != null && i < toolCalls.length(); i++) {
            JSONObject function = toolCalls.getJSONObject(i).optJSONObject("function");
            if (function == null)
                continue;
            JSONObject args;
            try {
                args = new JSONObject(function.optString("arguments", "{}"));
            }
            catch (JSONException e) {
                args = new JSONObject();
            }
            String id = toolCalls.getJSONObject(i).optString("id", "");
            calls.add(new ChatTurn.ToolCall(id.isEmpty() ? "call_" + i : id, function.optString("name"), args));
        }
        return new ChatTurn.Model(text, calls, null);
    }

    CompletableFuture<String> checkChatModel(String model) {
        return CompletableFuture.supplyAsync(() -> {
            if (!transport.isConfigured())
                return "OpenAI API key not configured";
            try {
                JSONObject plain = postChat(new JSONObject().put("model", model).put("messages", new JSONArray()
                        .put(new JSONObject().put("role", "user").put("content", "Answer with the word OK."))));
                if (plain.optString("content", "").isBlank())
                    return "The model gave no answer.";

                JSONObject toolBody = new JSONObject().put("model", model)
                        .put("messages", new JSONArray().put(new JSONObject().put("role", "user")
                                .put("content", "What time is it? Use the function get_time to find out.")))
                        .put("tools", new JSONArray().put(new JSONObject().put("type", "function").put("function",
                                new JSONObject().put("name", "get_time").put("description", "Returns the current time.")
                                        .put("parameters", new JSONObject().put("type", "object")
                                                .put("properties", new JSONObject())))))
                        .put("tool_choice", "auto");
                JSONArray toolCalls = postChat(toolBody).optJSONArray("tool_calls");
                if (toolCalls == null || toolCalls.isEmpty())
                    return "The model does not call functions (tool calls).";
                return null;
            } catch (RuntimeException e) {
                logger.warn("OpenAI chat model check failed for {}: {}", model, e.getMessage());
                if (!ApiHttpException.isDefinite(e))
                    throw e;
                return e.getMessage();
            }
        }, transport.io());
    }

    CompletableFuture<ResearchNote> research(String model, String query) {
        return CompletableFuture.supplyAsync(() -> parseResearch(query,
                postResponses(webSearchBody(model, RESEARCH_INSTRUCTION + query))), transport.io());
    }

    CompletableFuture<String> checkWebSearch(String model) {
        return CompletableFuture.supplyAsync(() -> {
            if (!transport.isConfigured())
                return "OpenAI API key not configured";

            String body = webSearchBody(model, ApiProviderValidator.SEARCH_CHECK_QUESTION).toString();
            try (Response response = transport.post(transport.researchClient().get(), "/responses", () -> RequestBody.create(body, OpenAiTransport.JSON))) {
                String problem = webSearchProblem(response.code(), response.body() != null ? response.body().string() : "");
                if (problem != null)
                    logger.warn("OpenAI web search check failed for {}: {}", model, problem);
                return problem;
            } catch (java.io.IOException e) {
                logger.warn("OpenAI web search check failed for {}: {}", model, e.getMessage());
                throw ProviderException.localized(e, "openai.error.search.failed", e.getMessage());
            }
        }, transport.io());
    }

    CompletableFuture<String> complete(String model, String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            JSONObject body = new JSONObject().put("model", model).put("messages", new JSONArray()
                    .put(new JSONObject().put("role", "user").put("content", prompt)));
            JSONObject message = postChat(body);
            return message.isNull("content") ? "" : message.optString("content", "");
        }, transport.io());
    }

    /**
     * Decides on the answer of the web search check: the search works if the request succeeded and the model
     * searched, i.e. {@code output} contains a {@code web_search_call} item. An answer of the model without that
     * item did not search, even if it names a time.
     *
     * @param code         the HTTP status code
     * @param responseBody the answer body
     * @return the reason why the search does not work, or null if it works
     * @throws ApiHttpException for a transient HTTP status (e.g. 429 or 5xx), which decides nothing
     */
    static String webSearchProblem(int code, String responseBody) {
        if (code < 200 || code >= 300) {
            if (!ApiHttpException.isDefinite(code))
                throw OpenAiTransport.httpError(code, responseBody);
            return OpenAiTransport.httpError(code, responseBody).getMessage();
        }
        JSONArray output = new JSONObject(responseBody).optJSONArray("output");
        for (int i = 0; output != null && i < output.length(); i++)
            if ("web_search_call".equals(output.getJSONObject(i).optString("type"))
                    && !"failed".equals(output.getJSONObject(i).optString("status")))
                return null;
        return "The model did not search the web.";
    }

    /**
     * Builds the body of {@code POST /responses} that has to search the web with the built-in web search.
     *
     * @param model the model id
     * @param input the question
     * @return the JSON body
     */
    static JSONObject webSearchBody(String model, String input) {
        return new JSONObject().put("model", model).put("input", input)
                .put("tools", new JSONArray().put(new JSONObject().put("type", "web_search")))
                .put("tool_choice", "required");
    }

    /**
     * Reads a researched answer of {@code POST /responses}: the text of the {@code output_text} parts of the
     * {@code message} items, and as sources their {@code url_citation} annotations, each URL once, with the host
     * without {@code www.} as domain.
     *
     * @param query        what was researched
     * @param responseBody the JSON answer
     * @return the found text and its sources
     */
    static ResearchNote parseResearch(String query, String responseBody) {
        JSONArray output = new JSONObject(responseBody).optJSONArray("output");
        StringBuilder text = new StringBuilder();
        List<ResearchNote.Source> sources = new ArrayList<>();
        for (int i = 0; output != null && i < output.length(); i++) {
            JSONObject item = output.getJSONObject(i);
            JSONArray content = "message".equals(item.optString("type")) ? item.optJSONArray("content") : null;
            for (int k = 0; content != null && k < content.length(); k++) {
                JSONObject part = content.getJSONObject(k);
                if (!"output_text".equals(part.optString("type")))
                    continue;
                text.append(part.optString("text", ""));
                JSONArray annotations = part.optJSONArray("annotations");
                for (int a = 0; annotations != null && a < annotations.length(); a++) {
                    JSONObject annotation = annotations.getJSONObject(a);
                    String url = annotation.optString("url", "");
                    if (!"url_citation".equals(annotation.optString("type")) || url.isEmpty()
                            || sources.stream().anyMatch((ResearchNote.Source source) -> source.url().equals(url)))
                        continue;
                    String domain = url;
                    try {
                        String host = java.net.URI.create(url).getHost();
                        if (host != null)
                            domain = host;
                    } catch (IllegalArgumentException e) {
                        /*  A cited URL that is no valid URI keeps the raw URL as domain, the research stays  */
                    }
                    sources.add(new ResearchNote.Source(domain.startsWith("www.") ? domain.substring(4) : domain, url));
                }
            }
        }
        if (text.toString().isBlank())
            throw ProviderException.localized("brainstorm.error.no.response");
        return new ResearchNote(query, text.toString().trim(), sources);
    }

    /**
     * Builds the body of {@code POST /chat/completions} from a conversation: an answer of the model becomes an
     * assistant message with its tool calls, the tool results one tool message per call, in the order of the calls. A
     * user turn with images becomes a message with a text part and one {@code image_url} data URL per image.
     *
     * @param model        the model id
     * @param systemPrompt the system prompt
     * @param turns        the conversation
     * @param tools        the tools offered, empty for none
     * @param noMoreCalls  forbid further tool calls
     * @return the JSON body
     */
    static JSONObject buildChatBody(String model, String systemPrompt, List<ChatTurn> turns, List<ChatTool> tools,
            boolean noMoreCalls) {
        JSONArray messages = new JSONArray().put(new JSONObject().put("role", "system").put("content", systemPrompt));
        for (ChatTurn turn : turns) {
            switch (turn) {
                case ChatTurn.User user when user.images().isEmpty() ->
                        messages.put(new JSONObject().put("role", "user").put("content", user.text()));
                case ChatTurn.User user -> {
                    JSONArray content = user.text().isEmpty() ? new JSONArray()
                            : new JSONArray().put(new JSONObject().put("type", "text").put("text", user.text()));
                    for (ChatTurn.Image image : user.images())
                        content.put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject()
                                .put("url", "data:" + image.mimeType() + ";base64," + image.base64())));
                    messages.put(new JSONObject().put("role", "user").put("content", content));
                }
                case ChatTurn.Model answer -> {
                    JSONObject assistant = new JSONObject().put("role", "assistant")
                            .put("content", answer.text().isEmpty() ? JSONObject.NULL : answer.text());
                    JSONArray toolCalls = new JSONArray();
                    for (ChatTurn.ToolCall call : answer.calls())
                        toolCalls.put(new JSONObject().put("id", call.id()).put("type", "function").put("function",
                                new JSONObject().put("name", call.name()).put("arguments", call.args().toString())));
                    if (!toolCalls.isEmpty())
                        assistant.put("tool_calls", toolCalls);
                    messages.put(assistant);
                }
                case ChatTurn.ToolResults results -> {
                    for (ChatTurn.ToolResult result : results.results())
                        messages.put(new JSONObject().put("role", "tool").put("tool_call_id", result.call().id())
                                .put("content", result.response().toString()));
                }
            }
        }

        JSONObject body = new JSONObject().put("model", model).put("messages", messages)
                .put("response_format", new JSONObject().put("type", "json_object"));
        if (!tools.isEmpty()) {
            JSONArray functions = new JSONArray();
            for (ChatTool tool : tools)
                functions.put(new JSONObject().put("type", "function").put("function", new JSONObject()
                        .put("name", tool.name()).put("description", tool.description())
                        .put("parameters", tool.parameters())));
            body.put("tools", functions).put("tool_choice", noMoreCalls ? "none" : "auto");
        }
        return body;
    }

    /**
     * Posts a chat request and returns the message of the first choice. A gateway that rejects the JSON mode gets
     * the request again without it, the prompt asks for JSON anyway.
     */
    private JSONObject postChat(JSONObject body) {
        if (!transport.isConfigured())
            throw ProviderException.noKey("OpenAI");

        try {
            for (int attempt = 0; ; attempt++) {
                try (Response response = transport.post("/chat/completions", () -> RequestBody.create(body.toString(), OpenAiTransport.JSON))) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        JSONArray choices = new JSONObject(responseBody).optJSONArray("choices");
                        if (choices == null || choices.isEmpty() || choices.getJSONObject(0).optJSONObject("message") == null)
                            throw ProviderException.localized("brainstorm.error.no.response");
                        return choices.getJSONObject(0).getJSONObject("message");
                    }
                    if (attempt == 0 && response.code() == 400 && body.has("response_format")
                            && responseBody.contains("response_format")) {
                        logger.warn("OpenAI chat: JSON mode rejected, retrying without it");
                        body.remove("response_format");
                        continue;
                    }
                    logger.error("OpenAI chat request failed: HTTP {} - {}", response.code(), responseBody);
                    throw OpenAiTransport.httpError(response.code(), responseBody);
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logger.error("OpenAI chat request failed", e);
            throw ProviderException.localized(e, "openai.error.chat.failed", e.getMessage());
        }
    }

    /**
     * Posts a request of the Responses API and returns the answer body. A research waits at most 60 s for the
     * answer, like the one of Gemini, not as long as an image generation.
     *
     * @throws RuntimeException with the reason if the request fails
     */
    private String postResponses(JSONObject body) {
        if (!transport.isConfigured())
            throw ProviderException.noKey("OpenAI");

        try (Response response = transport.post(transport.researchClient().get(), "/responses",
                () -> RequestBody.create(body.toString(), OpenAiTransport.JSON))) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                logger.warn("OpenAI responses request failed: HTTP {} - {}", response.code(), responseBody);
                throw OpenAiTransport.httpError(response.code(), responseBody);
            }
            return responseBody;
        } catch (java.io.IOException e) {
            logger.error("OpenAI responses request failed", e);
            throw ProviderException.localized(e, "openai.error.search.failed", e.getMessage());
        }
    }
}
