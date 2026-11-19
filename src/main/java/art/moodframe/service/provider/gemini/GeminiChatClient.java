package art.moodframe.service.provider.gemini;

import art.moodframe.model.ResearchNote;
import art.moodframe.service.provider.ApiHttpException;
import art.moodframe.service.provider.ChatTool;
import art.moodframe.service.provider.ChatTurn;
import art.moodframe.service.provider.ProviderException;
import art.moodframe.util.I18nManager;
import okhttp3.MediaType;
import okhttp3.Request;
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
 * The text model of Gemini: the brainstorm chat with its tools, the research with Google Search, the session title,
 * the checks of the language model and the analysis of YouTube videos.
 *
 * @since 2.1.3
 */
final class GeminiChatClient {

    private static final Logger logger = LoggerFactory.getLogger(GeminiChatClient.class);

    private static final int TIMEOUT_SECONDS = 120;
    private static final String BRAINSTORM_MODEL = "gemini-3.8-flash";
    private static final int RESEARCH_TIMEOUT_SECONDS = 60;
    /* Without the demand to search, the model often answers from memory: no sources, so the facts cannot be cited */
    private static final String RESEARCH_INSTRUCTION = "You MUST search the web with Google Search for this question "
            + "and base your answer only on what the search results say, never on memory. Research the following "
            + "for an image or video brief. Answer concisely in the language of the question with concrete, "
            + "checkable facts (numbers, names, terms), at most 300 words. Question: ";
    private static final String RESEARCH_RETRY_INSTRUCTION = "Use the Google Search tool now, you did not search. ";

    private final GeminiTransport transport;

    GeminiChatClient(GeminiTransport transport) {
        this.transport = transport;
    }

    CompletableFuture<String> analyzeYouTubeVideo(
            String youtubeUrl,
            int startSeconds,
            int endSeconds,
            String analysisPrompt) {

        return transport.async(() -> {
            if (!transport.hasKey()) {
                throw ProviderException.localized("brainstorm.video.error.no.api.key");
            }

            if (youtubeUrl == null || youtubeUrl.trim().isEmpty()) {
                throw new IllegalArgumentException(I18nManager.getText("brainstorm.video.error.empty.url"));
            }

            try {
                // Build request body for video analysis
                JSONObject requestBody = buildYouTubeAnalysisRequestBody(
                    youtubeUrl, startSeconds, endSeconds, analysisPrompt);

                // Use text generation model for video understanding
                // gemini-3.5-flash supports video understanding (gemini-2.0-flash EOL June 2026)
                String modelName = "gemini-3.5-flash";
                String path = "/models/" + modelName + ":generateContent";

                logger.info("Submitting YouTube video analysis request to: {}{}", transport.baseUrl(), path);
                logger.debug("Analyzing video: {} ({}s - {}s)",
                    youtubeUrl, startSeconds, endSeconds > 0 ? endSeconds : "end");

                // Create HTTP request
                RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    MediaType.parse("application/json; charset=utf-8")
                );

                Request httpRequest = transport.request(path).post(body).build();

                // Use longer timeout for video analysis (5 minutes)
                try (Response response = transport.client(300).newCall(httpRequest).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        logger.info("YouTube video analysis response received: {} bytes", responseBody.length());

                        // Extract text response
                        String analysisResult = extractTextFromVideoResponse(responseBody);
                        logger.info("Video analysis completed: {} characters extracted", analysisResult.length());

                        return analysisResult;

                    } else {
                        String errorBody = response.body() != null ? response.body().string() : "No error details";
                        logger.error("YouTube video analysis failed: HTTP {} - {}", response.code(), errorBody);

                        throw GeminiErrors.http(response.code(), errorBody, GeminiErrors.Call.VIDEO_ANALYSIS);
                    }
                }

            } catch (java.net.SocketTimeoutException e) {
                logger.error("YouTube video analysis timed out", e);
                throw ProviderException.localized(e, "brainstorm.video.error.timeout");
            } catch (IllegalStateException | IllegalArgumentException e) {
                throw e; // Re-throw validation errors
            } catch (RuntimeException e) {
                throw e; // Re-throw runtime errors with user-friendly messages
            } catch (Exception e) {
                logger.error("Failed to analyze YouTube video", e);
                throw ProviderException.localized(e, "brainstorm.video.error.analysis.failed", e.getMessage());
            }
        });
    }

    /**
     * Builds the request body for YouTube video analysis.
     *
     * @param youtubeUrl the YouTube video URL
     * @param startSeconds start time in seconds (0 for beginning)
     * @param endSeconds end time in seconds (0 for entire video)
     * @param analysisPrompt optional custom analysis prompt
     * @return JSON request body
     */
    private static JSONObject buildYouTubeAnalysisRequestBody(
            String youtubeUrl,
            int startSeconds,
            int endSeconds,
            String analysisPrompt) {

        JSONObject body = new JSONObject();

        // Contents Array
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();

        // File Data Part (YouTube URL)
        JSONObject fileDataPart = new JSONObject();
        JSONObject fileData = new JSONObject();
        fileData.put("fileUri", youtubeUrl.trim());
        fileDataPart.put("file_data", fileData);

        // Add video metadata with time offsets if specified
        if (startSeconds > 0 || endSeconds > 0) {
            JSONObject videoMetadata = new JSONObject();

            if (startSeconds > 0) {
                JSONObject startOffset = new JSONObject();
                startOffset.put("seconds", startSeconds);
                videoMetadata.put("start_offset", startOffset);
            }

            if (endSeconds > 0) {
                JSONObject endOffset = new JSONObject();
                endOffset.put("seconds", endSeconds);
                videoMetadata.put("end_offset", endOffset);
            }

            fileDataPart.put("video_metadata", videoMetadata);
            logger.debug("Video analysis with time range: {}s - {}s",
                startSeconds, endSeconds > 0 ? endSeconds : "end");
        }

        parts.put(fileDataPart);

        // Text Part (Analysis Prompt)
        JSONObject textPart = new JSONObject();
        String prompt = (analysisPrompt != null && !analysisPrompt.trim().isEmpty())
            ? analysisPrompt.trim()
            : I18nManager.getText("brainstorm.video.prompt.default");
        textPart.put("text", prompt);
        parts.put(textPart);

        content.put("parts", parts);
        contents.put(content);
        body.put("contents", contents);

        // Generation Config - request TEXT output only
        JSONObject genConfig = new JSONObject();
        genConfig.put("responseModalities", new JSONArray().put("TEXT"));
        body.put("generationConfig", genConfig);

        logger.debug("Built YouTube analysis request body: url={}, prompt={} chars",
            youtubeUrl, prompt.length());

        return body;
    }

    /**
     * Extracts text content from a Gemini video analysis response.
     *
     * @param responseBody the JSON response from Gemini API
     * @return the extracted text analysis
     */
    private static String extractTextFromVideoResponse(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);

            // Check for error
            if (json.has("error")) {
                JSONObject error = json.getJSONObject("error");
                String errorMessage = error.optString("message", "Unknown API error");
                throw ProviderException.localized("brainstorm.video.error.api", errorMessage);
            }

            // Check for candidates
            if (!json.has("candidates")) {
                // Check if there's a promptFeedback with blockReason
                if (json.has("promptFeedback")) {
                    JSONObject promptFeedback = json.getJSONObject("promptFeedback");
                    if (promptFeedback.has("blockReason")) {
                        String blockReason = promptFeedback.getString("blockReason");
                        logger.warn("Video analysis blocked: {}", blockReason);
                        throw ProviderException.localized("brainstorm.video.error.blocked", blockReason);
                    }
                }
                throw ProviderException.localized("brainstorm.video.error.no.response");
            }

            // Navigate: candidates[0].content.parts
            JSONArray candidates = json.getJSONArray("candidates");
            if (candidates.length() == 0) {
                throw ProviderException.localized("brainstorm.video.error.no.response");
            }

            JSONObject candidate = candidates.getJSONObject(0);

            // Check for finish reason indicating an issue
            if (candidate.has("finishReason")) {
                String finishReason = candidate.getString("finishReason");
                if (!"STOP".equals(finishReason) && !"MAX_TOKENS".equals(finishReason)) {
                    logger.warn("Video analysis finished with reason: {}", finishReason);
                    if ("SAFETY".equals(finishReason)) {
                        throw ProviderException.localized("brainstorm.video.error.safety");
                    }
                }
            }

            JSONArray parts = candidate.getJSONObject("content").getJSONArray("parts");

            // Extract all text parts and concatenate
            StringBuilder textBuilder = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.getJSONObject(i);
                if (part.has("text")) {
                    if (textBuilder.length() > 0) {
                        textBuilder.append("\n\n");
                    }
                    textBuilder.append(part.getString("text"));
                }
            }

            if (textBuilder.length() == 0) {
                throw ProviderException.localized("brainstorm.video.error.no.text");
            }

            return textBuilder.toString().trim();

        } catch (JSONException e) {
            logger.error("Failed to parse video analysis response: {}", e.getMessage());
            throw ProviderException.localized(e, "brainstorm.video.error.parse");
        }
    }

    CompletableFuture<ChatTurn.Model> chatTurn(String systemPrompt, List<ChatTurn> turns, List<ChatTool> tools,
            boolean noMoreCalls) {
        return transport.async(() -> {
            requireApiKey();

            JSONObject requestBody = buildChatBody(systemPrompt, turns, tools, noMoreCalls);
            JSONObject candidate = postBrainstorm(BRAINSTORM_MODEL, requestBody, TIMEOUT_SECONDS);
            JSONArray parts = candidate.optJSONObject("content") != null
                    ? candidate.getJSONObject("content").optJSONArray("parts") : null;
            if (parts == null) {
                throw ProviderException.localized("brainstorm.error.no.response");
            }
            ChatTurn.Model answer = parseAnswer(parts);
            if (answer.text().isEmpty() && answer.calls().isEmpty()) {
                throw ProviderException.localized("brainstorm.error.no.response");
            }
            return answer;
        });
    }

    /**
     * Reads the parts of an answer: the text parts joined, the {@code functionCall} parts as tool calls (with the id
     * of the call, else {@code call_<index>}), and the parts as they are to send them back unchanged.
     *
     * @param parts the parts of the first candidate
     * @return the answer
     */
    static ChatTurn.Model parseAnswer(JSONArray parts) {
        StringBuilder text = new StringBuilder();
        List<ChatTurn.ToolCall> calls = new ArrayList<>();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.getJSONObject(i);
            text.append(part.optString("text", ""));
            JSONObject call = part.optJSONObject("functionCall");
            if (call != null) {
                JSONObject args = call.optJSONObject("args") != null ? call.getJSONObject("args") : new JSONObject();
                calls.add(new ChatTurn.ToolCall(call.optString("id", "call_" + i), call.optString("name"), args));
            }
        }
        return new ChatTurn.Model(text.toString(), calls, parts.toString());
    }

    /**
     * Builds the body of a generateContent request of a conversation. A user turn becomes a {@code user} content with
     * its images as {@code inlineData} parts ({@code mimeType}, Base64 {@code data}) before its text; an answer of the
     * model goes back as Gemini sent it (with the thought signatures of its calls), or, from another model, as text
     * and {@code functionCall} parts; the tool results become one {@code user} content of {@code functionResponse}
     * parts.
     *
     * @param systemPrompt the system instruction
     * @param turns        the conversation
     * @param tools        the tools offered, empty for none
     * @param noMoreCalls  forbid further tool calls (only with tools)
     * @return the JSON body
     */
    static JSONObject buildChatBody(String systemPrompt, List<ChatTurn> turns, List<ChatTool> tools,
            boolean noMoreCalls) {
        JSONArray contents = new JSONArray();
        for (ChatTurn turn : turns)
            contents.put(content(turn));
        JSONObject requestBody = new JSONObject()
                .put("systemInstruction", new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject().put("text", systemPrompt))))
                .put("contents", contents)
                .put("generationConfig", new JSONObject().put("responseMimeType", "application/json"));
        if (!tools.isEmpty()) {
            JSONArray declarations = new JSONArray();
            for (ChatTool tool : tools)
                declarations.put(new JSONObject().put("name", tool.name()).put("description", tool.description())
                        .put("parameters", tool.parameters()));
            requestBody.put("tools", new JSONArray().put(new JSONObject().put("functionDeclarations", declarations)));
            if (noMoreCalls) {
                requestBody.put("toolConfig", new JSONObject()
                        .put("functionCallingConfig", new JSONObject().put("mode", "NONE")));
            }
        }
        return requestBody;
    }

    /** One turn as Gemini content. */
    private static JSONObject content(ChatTurn turn) {
        JSONArray parts = new JSONArray();
        String role = "user";
        switch (turn) {
            case ChatTurn.User user -> {
                for (ChatTurn.Image image : user.images())
                    parts.put(new JSONObject().put("inlineData", new JSONObject().put("mimeType", image.mimeType())
                            .put("data", image.base64())));
                if (!user.text().isEmpty() || user.images().isEmpty())
                    parts.put(new JSONObject().put("text", user.text()));
            }
            case ChatTurn.Model model -> {
                role = "model";
                if (model.replay() != null)
                    parts = new JSONArray(model.replay());
                else {
                    if (!model.text().isEmpty())
                        parts.put(new JSONObject().put("text", model.text()));
                    for (ChatTurn.ToolCall call : model.calls())
                        parts.put(new JSONObject().put("functionCall",
                                new JSONObject().put("name", call.name()).put("args", call.args())));
                }
            }
            case ChatTurn.ToolResults results -> {
                for (ChatTurn.ToolResult result : results.results())
                    parts.put(new JSONObject().put("functionResponse", new JSONObject()
                            .put("name", result.call().name()).put("response", result.response())));
            }
        }
        return new JSONObject().put("role", role).put("parts", parts);
    }

    CompletableFuture<String> suggestTitle(String content, String language) {
        return transport.async(() -> {
            requireApiKey();

            JSONObject requestBody = new JSONObject()
                    .put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts",
                            new JSONArray().put(new JSONObject().put("text", "Name this session in " + language
                                    + " with one or two words (at most 24 characters), based on what the user wants "
                                    + "to create. Answer with the name only, no quotes, no punctuation.\n\n" + content)))))
                    .put("generationConfig", new JSONObject()
                            .put("thinkingConfig", new JSONObject().put("thinkingLevel", "low")));

            JSONObject candidate = postBrainstorm(BRAINSTORM_MODEL, requestBody, TIMEOUT_SECONDS);
            JSONArray parts = candidate.optJSONObject("content") != null
                    ? candidate.getJSONObject("content").optJSONArray("parts") : null;
            StringBuilder text = new StringBuilder();
            for (int i = 0; parts != null && i < parts.length(); i++) {
                text.append(parts.getJSONObject(i).optString("text", ""));
            }
            return text.toString();
        });
    }

    CompletableFuture<ResearchNote> research(String query) {
        return transport.async(() -> {
            requireApiKey();
            return researchWithRetry(query);
        });
    }

    /*  a research that is asked again once if the model answered without searching  */
    private ResearchNote researchWithRetry(String query) {
        ResearchNote note = researchOnce(RESEARCH_INSTRUCTION + query, query);
        if (note.sources().isEmpty()) {
            logger.info("Research without sources, asking again: {}", query);
            note = researchOnce(RESEARCH_RETRY_INSTRUCTION + RESEARCH_INSTRUCTION + query, query);
        }
        return note;
    }

    CompletableFuture<String> checkChatModel() {
        return transport.async(() -> {
            try {
                requireApiKey();
                JSONObject requestBody = new JSONObject()
                        .put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts",
                                new JSONArray().put(new JSONObject().put("text", "Answer with the word OK.")))))
                        .put("generationConfig", new JSONObject()
                                .put("thinkingConfig", new JSONObject().put("thinkingLevel", "low")));
                JSONObject candidate = postBrainstorm(BRAINSTORM_MODEL, requestBody, RESEARCH_TIMEOUT_SECONDS);
                JSONArray parts = candidate.optJSONObject("content") != null
                        ? candidate.getJSONObject("content").optJSONArray("parts") : null;
                return parts != null && !parts.isEmpty() ? null : "The model gave no answer.";
            } catch (RuntimeException e) {
                logger.warn("Gemini language model check failed: {}", e.getMessage());
                if (!ApiHttpException.isDefinite(e))
                    throw e;
                return e.getMessage();
            }
        });
    }

    CompletableFuture<String> checkWebSearch() {
        return transport.async(() -> {
            try {
                requireApiKey();
                ResearchNote note = researchWithRetry(ChatTurn.SEARCH_CHECK_QUESTION);
                return note.sources().isEmpty() ? "The model did not search the web." : null;
            } catch (RuntimeException e) {
                logger.warn("Gemini web search check failed: {}", e.getMessage());
                if (!ApiHttpException.isDefinite(e))
                    throw e;
                return e.getMessage();
            }
        });
    }

    private ResearchNote researchOnce(String prompt, String query) {
        JSONObject requestBody = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts",
                        new JSONArray().put(new JSONObject().put("text", prompt)))))
                .put("tools", new JSONArray()
                        .put(new JSONObject().put("google_search", new JSONObject()))
                        .put(new JSONObject().put("url_context", new JSONObject())))
                .put("generationConfig", new JSONObject()
                        .put("thinkingConfig", new JSONObject().put("thinkingLevel", "low")));

        JSONObject candidate = postBrainstorm(BRAINSTORM_MODEL, requestBody, RESEARCH_TIMEOUT_SECONDS);
        JSONArray parts = candidate.optJSONObject("content") != null
                ? candidate.getJSONObject("content").optJSONArray("parts") : null;
        StringBuilder text = new StringBuilder();
        for (int i = 0; parts != null && i < parts.length(); i++) {
            text.append(parts.getJSONObject(i).optString("text", ""));
        }
        if (text.length() == 0) {
            throw ProviderException.localized("brainstorm.error.no.response");
        }
        return new ResearchNote(query, text.toString().trim(), extractSources(candidate));
    }

    private void requireApiKey() {
        if (!transport.hasKey()) {
            throw ProviderException.noKey("Gemini");
        }
    }

    /**
     * Sends a generateContent request of the brainstorm assistant and returns the first candidate.
     *
     * @throws RuntimeException with a user-friendly message if the request fails or has no candidate
     */
    private JSONObject postBrainstorm(String model, JSONObject requestBody, int readTimeoutSeconds) {
        Request httpRequest = transport.request("/models/" + model + ":generateContent")
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json; charset=utf-8")))
                .build();

        try (Response response = transport.client(readTimeoutSeconds).newCall(httpRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                logger.error("Brainstorm request failed: HTTP {} - {}", response.code(), responseBody);
                throw GeminiErrors.http(response.code(), responseBody, GeminiErrors.Call.CHAT);
            }
            JSONArray candidates = new JSONObject(responseBody).optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                throw ProviderException.localized("brainstorm.error.no.response");
            }
            return candidates.getJSONObject(0);
        } catch (java.io.IOException e) {
            logger.error("Brainstorm request failed", e);
            throw ProviderException.localized(e, "brainstorm.error.request", e.getMessage());
        } catch (JSONException e) {
            logger.error("Failed to parse brainstorm response: {}", e.getMessage());
            throw ProviderException.localized(e, "brainstorm.error.no.response");
        }
    }

    /**
     * Collects the pages a researched answer is based on: the grounding chunks of the Google search (the URI is a
     * Google redirect, the title is the domain) and the pages read by the URL context.
     */
    static List<ResearchNote.Source> extractSources(JSONObject candidate) {
        List<ResearchNote.Source> sources = new ArrayList<>();

        JSONObject grounding = candidate.optJSONObject("groundingMetadata");
        JSONArray chunks = grounding != null ? grounding.optJSONArray("groundingChunks") : null;
        for (int i = 0; chunks != null && i < chunks.length(); i++) {
            JSONObject web = chunks.getJSONObject(i).optJSONObject("web");
            if (web != null && !web.optString("uri").isEmpty()) {
                addSource(sources, web.optString("title"), web.getString("uri"));
            }
        }

        JSONObject urlContext = candidate.optJSONObject("urlContextMetadata");
        JSONArray pages = urlContext != null ? urlContext.optJSONArray("urlMetadata") : null;
        for (int i = 0; pages != null && i < pages.length(); i++) {
            JSONObject page = pages.getJSONObject(i);
            if (page.optString("urlRetrievalStatus").endsWith("SUCCESS")) {
                addSource(sources, "", page.optString("retrievedUrl"));
            }
        }
        return sources;
    }

    private static void addSource(List<ResearchNote.Source> sources, String title, String url) {
        if (url.isEmpty() || sources.stream().anyMatch((ResearchNote.Source source) -> source.url().equals(url))) {
            return;
        }
        String domain = title;
        if (domain.isEmpty()) {
            String host = java.net.URI.create(url).getHost();
            domain = host != null ? host : url;
        }
        sources.add(new ResearchNote.Source(domain.startsWith("www.") ? domain.substring(4) : domain, url));
    }
}
