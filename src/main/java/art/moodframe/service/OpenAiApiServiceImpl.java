package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.CorporateProxyConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.BrainstormTurn;
import art.moodframe.model.GenerationJobInfo;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.util.I18nManager;
import art.moodframe.util.TrustStoreManager;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * OkHttp implementation of {@link OpenAiApiService}.
 *
 * @author Matthias Brusdeylins
 * @since 1.9.0
 */
public class OpenAiApiServiceImpl implements OpenAiApiService {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiApiServiceImpl.class);

    /*  A gateway queues the images of one request series, the last one answers after minutes  */
    private static final int TIMEOUT_SECONDS = 3600;
    private static final long MAX_SIDE = 4096;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final ApplicationConfig config;
    private final OkHttpClient httpClient;
    private String apiKey;
    private String baseUrl;

    /**
     * Creates the service with the token and base URL of the configuration.
     *
     * @param config the application configuration
     */
    public OpenAiApiServiceImpl(ApplicationConfig config) {
        this.config = config;
        this.apiKey = config.getOpenAiApiKey();
        this.baseUrl = config.getOpenAiBaseUrl();

        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        configureSSLContext(clientBuilder, config);
        this.httpClient = clientBuilder.build();
    }

    static void configureSSLContext(OkHttpClient.Builder clientBuilder, ApplicationConfig config) {
        try {
            TrustStoreManager trustStoreManager = new TrustStoreManager();
            var certificatePaths = new CorporateProxyConfig(config).getCertificatePaths();

            if (!certificatePaths.isEmpty()) {
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStoreManager.createCorporateKeyStore(certificatePaths));
                TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
                SSLContext sslContext = trustStoreManager.createSSLContext();

                if (trustManagers.length > 0 && trustManagers[0] instanceof X509TrustManager) {
                    clientBuilder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0]);
                    logger.info("OpenAI SSL context configured with {} corporate certificate(s)", certificatePaths.size());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to configure SSL context with corporate certificates, using the default", e);
        }
    }

    @Override
    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConfigured()) {
                logger.warn("Cannot test OpenAI API connection: no bearer token configured");
                return false;
            }

            /*  A URL without a path is usually meant as ".../v1": if that answers too, the other endpoints are there  */
            if (java.net.URI.create(baseUrl).getPath().isEmpty() && answersModels(baseUrl + "/v1")) {
                logger.info("OpenAI base URL {} answers under /v1, using {}/v1", baseUrl, baseUrl);
                baseUrl = baseUrl + "/v1";
            }

            Request request = new Request.Builder()
                    .url(baseUrl + "/models")
                    .header("Authorization", "Bearer " + apiKey)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    logger.warn("OpenAI API connection test failed: HTTP {} - {}", response.code(), body);
                    return false;
                }

                List<String> ids = parseModelIds(body);
                config.setOpenAiAvailableModels(ids);

                /*  Keep the user's selection, preselect the known image names only the first time  */
                List<String> selected = config.hasOpenAiModelSelection()
                        ? config.getOpenAiModels().stream().filter(ids::contains).toList()
                        : imageModelsOf(ids);
                config.setOpenAiModels(selected);
                logger.info("OpenAI API connection test successful, {} model(s), selected: {}", ids.size(), selected);
                return true;
            } catch (Exception e) {
                logger.error("OpenAI API connection test failed with exception", e);
                return false;
            }
        });
    }

    private boolean answersModels(String base) {
        Request request = new Request.Builder().url(base + "/models")
                .header("Authorization", "Bearer " + apiKey).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /**
     * Extracts all model ids from the answer of {@code GET /models}. The standard list has no capability field,
     * so it holds chat, embedding and image models alike.
     *
     * @param responseBody the JSON answer ({@code {"data": [{"id": "..."}]}})
     * @return the sorted ids
     */
    static List<String> parseModelIds(String responseBody) {
        List<String> ids = new ArrayList<>();
        try {
            JSONArray data = new JSONObject(responseBody).optJSONArray("data");
            if (data == null)
                logger.warn("OpenAI model list has no \"data\" array: {}", responseBody.substring(0, Math.min(responseBody.length(), 500)));
            for (int i = 0; data != null && i < data.length(); i++) {
                String id = data.getJSONObject(i).optString("id", "");
                if (!id.isBlank() && !id.contains(",") && !ids.contains(id))
                    ids.add(id);
            }
        } catch (JSONException e) {
            logger.warn("OpenAI model list is no valid JSON: {}", e.getMessage());
        }
        ids.sort(null);
        return ids;
    }

    /**
     * Picks the models with a known image name ({@code dall-e-*}, {@code gpt-image-*}, {@code chatgpt-image-*}).
     *
     * @param ids the model ids
     * @return the image model ids, used as the initial selection
     */
    static List<String> imageModelsOf(List<String> ids) {
        return ids.stream().filter(OpenAiApiServiceImpl::isImageModel).toList();
    }

    /**
     * Checks if a model id belongs to an image model: {@code dall-e-*}, {@code gpt-image-*}, {@code chatgpt-image-*}.
     *
     * @param id the model id
     * @return true for image models
     */
    static boolean isImageModel(String id) {
        return GenerationModel.isOpenAiImageModelId(id);
    }

    @Override
    public List<String> getImageModels() {
        return config.getOpenAiModels();
    }

    @Override
    public CompletableFuture<GenerationJobInfo> submitGenerationRequest(ImageGenerationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConfigured())
                throw new IllegalStateException("OpenAI API key not configured");

            try {
                String model = request.getModel();
                List<byte[]> inputImages = inputImages(request);
                String path;
                java.util.function.Supplier<RequestBody> body;

                if (inputImages.isEmpty()) {
                    JSONObject json = buildGenerationBody(request);
                    logger.info("Submitting OpenAI generation request: model={}, size={}", model, json.getString("size"));
                    path = "/images/generations";
                    body = () -> RequestBody.create(json.toString(), JSON);
                } else {
                    logger.info("Submitting OpenAI edit request: model={}, {} image(s)", model, inputImages.size());
                    path = "/images/edits";
                    body = () -> buildEditBody(request, inputImages);
                }

                try (Response response = post(path, body)) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        logger.error("OpenAI API request failed: HTTP {} - {}", response.code(), responseBody);
                        throw new RuntimeException(extractError(response.code(), responseBody));
                    }

                    String base64Image = extractBase64(responseBody);
                    return new GenerationJobInfo.Builder()
                            .jobId(java.util.UUID.randomUUID().toString())
                            .status("completed")
                            .base64Image(base64Image)
                            .isSynchronous(true)
                            .provider(ApiProvider.OPENAI)
                            .build();
                }
            } catch (java.net.SocketTimeoutException e) {
                throw new RuntimeException("OpenAI image generation took too long. Please try again.", e);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                logger.error("Failed to submit OpenAI generation request", e);
                throw new RuntimeException("OpenAI generation failed: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<BrainstormTurn> chatTurn(String model, String systemPrompt, JSONArray contents,
            boolean withTools, boolean noMoreCalls, boolean videoTool) {
        return CompletableFuture.supplyAsync(() -> {
            JSONObject message = postChat(buildChatBody(model, systemPrompt, contents, withTools, noMoreCalls, videoTool));
            String text = message.isNull("content") ? "" : message.optString("content", "");
            JSONArray parts = new JSONArray();
            List<BrainstormTurn.Call> calls = new ArrayList<>();
            if (!text.isEmpty())
                parts.put(new JSONObject().put("text", text));
            JSONArray toolCalls = message.optJSONArray("tool_calls");
            for (int i = 0; toolCalls != null && i < toolCalls.length(); i++) {
                JSONObject function = toolCalls.getJSONObject(i).optJSONObject("function");
                if (function == null)
                    continue;
                JSONObject args;
                try {
                    args = new JSONObject(function.optString("arguments", "{}"));
                } catch (JSONException e) {
                    args = new JSONObject();
                }
                parts.put(new JSONObject().put("functionCall",
                        new JSONObject().put("name", function.optString("name")).put("args", args)));
                calls.add(new BrainstormTurn.Call(function.optString("name"), args));
            }
            if (text.isEmpty() && calls.isEmpty())
                throw new RuntimeException(I18nManager.getText("brainstorm.error.no.response"));
            return new BrainstormTurn(text, parts, calls);
        });
    }

    @Override
    public CompletableFuture<String> checkChatModel(String model) {
        return CompletableFuture.supplyAsync(() -> {
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
                return e.getMessage();
            }
        });
    }

    @Override
    public CompletableFuture<String> complete(String model, String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            JSONObject body = new JSONObject().put("model", model).put("messages", new JSONArray()
                    .put(new JSONObject().put("role", "user").put("content", prompt)));
            JSONObject message = postChat(body);
            return message.isNull("content") ? "" : message.optString("content", "");
        });
    }

    /**
     * Builds the body of {@code POST /chat/completions} from a conversation in the Gemini format: a model turn with
     * {@code functionCall} parts becomes an assistant message with tool calls, the following user turn with
     * {@code functionResponse} parts becomes one tool message per call, in the order of the calls. A user turn with
     * {@code inlineData} image parts becomes a message with a text part and one {@code image_url} data URL per image.
     *
     * @param model        the model id
     * @param systemPrompt the system prompt
     * @param contents     the conversation in the Gemini format
     * @param withTools    offer the brainstorm tools
     * @param noMoreCalls  forbid further tool calls
     * @param videoTool    also offer {@code analyze_video}
     * @return the JSON body
     */
    static JSONObject buildChatBody(String model, String systemPrompt, JSONArray contents, boolean withTools,
            boolean noMoreCalls, boolean videoTool) {
        JSONArray messages = new JSONArray().put(new JSONObject().put("role", "system").put("content", systemPrompt));
        List<String> pendingIds = new ArrayList<>();

        for (int i = 0; i < contents.length(); i++) {
            JSONObject content = contents.getJSONObject(i);
            JSONArray parts = content.getJSONArray("parts");
            boolean fromModel = "model".equals(content.optString("role"));
            StringBuilder text = new StringBuilder();
            JSONArray toolCalls = new JSONArray();
            List<JSONObject> responses = new ArrayList<>();
            JSONArray images = new JSONArray();
            for (int k = 0; k < parts.length(); k++) {
                JSONObject part = parts.getJSONObject(k);
                text.append(part.optString("text", ""));
                JSONObject image = part.optJSONObject("inlineData");
                if (image != null)
                    images.put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url",
                            "data:" + image.optString("mimeType") + ";base64," + image.optString("data"))));
                JSONObject call = part.optJSONObject("functionCall");
                if (call != null) {
                    String id = "call_" + i + "_" + k;
                    pendingIds.add(id);
                    JSONObject args = call.optJSONObject("args") != null ? call.getJSONObject("args") : new JSONObject();
                    toolCalls.put(new JSONObject().put("id", id).put("type", "function").put("function",
                            new JSONObject().put("name", call.optString("name")).put("arguments", args.toString())));
                }
                if (part.optJSONObject("functionResponse") != null)
                    responses.add(part.getJSONObject("functionResponse"));
            }

            if (fromModel) {
                JSONObject assistant = new JSONObject().put("role", "assistant")
                        .put("content", text.length() > 0 ? text.toString() : JSONObject.NULL);
                if (!toolCalls.isEmpty())
                    assistant.put("tool_calls", toolCalls);
                messages.put(assistant);
            } else if (!responses.isEmpty()) {
                for (int k = 0; k < responses.size(); k++)
                    messages.put(new JSONObject().put("role", "tool")
                            .put("tool_call_id", k < pendingIds.size() ? pendingIds.get(k) : "call_" + i + "_" + k)
                            .put("content", responses.get(k).optJSONObject("response") != null
                                    ? responses.get(k).getJSONObject("response").toString() : "{}"));
                pendingIds.clear();
            } else if (!images.isEmpty()) {
                JSONArray multimodal = text.length() > 0
                        ? new JSONArray().put(new JSONObject().put("type", "text").put("text", text.toString()))
                        : new JSONArray();
                messages.put(new JSONObject().put("role", "user").put("content", multimodal.putAll(images)));
            } else
                messages.put(new JSONObject().put("role", "user").put("content", text.toString()));
        }

        JSONObject body = new JSONObject().put("model", model).put("messages", messages)
                .put("response_format", new JSONObject().put("type", "json_object"));
        if (withTools) {
            JSONArray tools = new JSONArray();
            for (int i = 0; i < GeminiApiServiceImpl.BRAINSTORM_TOOLS.length(); i++) {
                JSONObject declaration = GeminiApiServiceImpl.BRAINSTORM_TOOLS.getJSONObject(i);
                if (!videoTool && "analyze_video".equals(declaration.optString("name")))
                    continue;
                tools.put(new JSONObject().put("type", "function").put("function", declaration));
            }
            body.put("tools", tools).put("tool_choice", noMoreCalls ? "none" : "auto");
        }
        return body;
    }

    private Response post(String path, java.util.function.Supplier<RequestBody> body) throws java.io.IOException {
        Response response = httpClient.newCall(request(baseUrl, path, body)).execute();
        if (response.code() != 404 || !java.net.URI.create(baseUrl).getPath().isEmpty())
            return response;

        /*  A base URL without a path is often meant as ".../v1"; the model list answers there, the rest does not  */
        String versioned = baseUrl + "/v1";
        Response retry = httpClient.newCall(request(versioned, path, body)).execute();
        response.close();
        if (retry.code() == 404)
            return retry;

        logger.info("OpenAI base URL {} has no endpoint {}, using {}", baseUrl, path, versioned);
        baseUrl = versioned;
        config.setOpenAiBaseUrl(versioned);
        return retry;
    }

    private Request request(String base, String path, java.util.function.Supplier<RequestBody> body) {
        return new Request.Builder()
                .url(base + path)
                .header("Authorization", "Bearer " + apiKey)
                .post(body.get())
                .build();
    }

    /**
     * Posts a chat request and returns the message of the first choice. A gateway that rejects the JSON mode gets
     * the request again without it, the prompt asks for JSON anyway.
     */
    private JSONObject postChat(JSONObject body) {
        if (!isConfigured())
            throw new IllegalStateException("OpenAI API key not configured");

        try {
            for (int attempt = 0; ; attempt++) {
                try (Response response = post("/chat/completions", () -> RequestBody.create(body.toString(), JSON))) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        JSONArray choices = new JSONObject(responseBody).optJSONArray("choices");
                        if (choices == null || choices.isEmpty() || choices.getJSONObject(0).optJSONObject("message") == null)
                            throw new RuntimeException(I18nManager.getText("brainstorm.error.no.response"));
                        return choices.getJSONObject(0).getJSONObject("message");
                    }
                    if (attempt == 0 && response.code() == 400 && body.has("response_format")
                            && responseBody.contains("response_format")) {
                        logger.warn("OpenAI chat: JSON mode rejected, retrying without it");
                        body.remove("response_format");
                        continue;
                    }
                    logger.error("OpenAI chat request failed: HTTP {} - {}", response.code(), responseBody);
                    throw new RuntimeException(extractError(response.code(), responseBody));
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logger.error("OpenAI chat request failed", e);
            throw new RuntimeException("OpenAI chat failed: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the body of {@code POST /images/generations}.
     *
     * @param request the generation request
     * @return the JSON body
     */
    static JSONObject buildGenerationBody(ImageGenerationRequest request) {
        String model = request.getModel();
        JSONObject body = new JSONObject()
                .put("model", model)
                .put("prompt", request.getPrompt())
                .put("n", 1)
                .put("size", sizeFor(model, request.getAspectRatio(), request.getImageSize()));

        /* dall-e answers with a URL unless asked for Base64, the gpt-image models reject the parameter */
        if (model.toLowerCase(Locale.ROOT).startsWith("dall-e"))
            body.put("response_format", "b64_json");
        return body;
    }

    private RequestBody buildEditBody(ImageGenerationRequest request, List<byte[]> inputImages) {
        String model = request.getModel();
        boolean dalle2 = model.toLowerCase(Locale.ROOT).equals("dall-e-2");
        if (model.toLowerCase(Locale.ROOT).equals("dall-e-3"))
            throw new IllegalArgumentException("dall-e-3 cannot edit images, choose a gpt-image model");

        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("model", model)
                .addFormDataPart("prompt", request.getPrompt())
                .addFormDataPart("n", "1")
                .addFormDataPart("size", sizeFor(model, request.getAspectRatio(), request.getImageSize()));
        if (dalle2)
            builder.addFormDataPart("response_format", "b64_json");

        for (int i = 0; i < inputImages.size(); i++) {
            byte[] bytes = inputImages.get(i);
            String mime = mimeType(bytes);
            String extension = mime.substring(mime.indexOf('/') + 1);
            /* dall-e-2 takes one square PNG as "image", the gpt-image models a list as "image[]" */
            builder.addFormDataPart(dalle2 ? "image" : "image[]", "image" + i + "." + extension,
                    RequestBody.create(bytes, MediaType.parse(mime)));
            if (dalle2)
                break;
        }
        return builder.build();
    }

    private static List<byte[]> inputImages(ImageGenerationRequest request) {
        String[] images = {request.getInputImage(), request.getInputImage2(), request.getInputImage3(),
            request.getInputImage4(), request.getInputImage5(), request.getInputImage6(), request.getInputImage7(),
            request.getInputImage8(), request.getInputImage9(), request.getInputImage10(), request.getInputImage11(),
            request.getInputImage12(), request.getInputImage13(), request.getInputImage14()};
        List<byte[]> result = new ArrayList<>();
        for (String image : images)
            if (image != null && !image.isEmpty())
                result.add(Base64.getDecoder().decode(image));
        return result;
    }

    /**
     * Detects the image type from the first bytes.
     *
     * @param bytes the image file content
     * @return the MIME type: {@code image/png}, {@code image/webp} or {@code image/jpeg}
     */
    static String mimeType(byte[] bytes) {
        if (bytes.length > 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G')
            return "image/png";
        if (bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[8] == 'W' && bytes[9] == 'E')
            return "image/webp";
        return "image/jpeg";
    }

    /**
     * Maps an aspect ratio to the nearest size the model supports.
     *
     * @param model       the model id
     * @param aspectRatio the aspect ratio like {@code 3:2} (null = square)
     * @param imageSize   {@code 1K}, {@code 2K} or {@code 4K}, only used for models of a gateway (null = 1K)
     * @return the size like {@code 1536x1024}
     */
    static String sizeFor(String model, String aspectRatio, String imageSize) {
        double ratio = 1.0;
        if (aspectRatio != null && aspectRatio.matches("\\d+:\\d+")) {
            String[] parts = aspectRatio.split(":");
            ratio = Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
        }

        /*  Any other model of a gateway: 1K, 2K or 4K as the square side 1024, 2048 or 4096, multiples of 16  */
        if (!GenerationModel.isOpenAiImageModelId(model)) {
            double side = 1024.0 * ("4K".equals(imageSize) ? 4 : "2K".equals(imageSize) ? 2 : 1);
            long width = Math.round(Math.sqrt(side * side * ratio) / 16.0) * 16;
            long height = Math.round(width / ratio / 16.0) * 16;

            /*  The gateway accepts 256..4096 per side: a wide 4K keeps the ratio with the long side at 4096  */
            if (Math.max(width, height) > MAX_SIDE) {
                double scale = (double) MAX_SIDE / Math.max(width, height);
                width = Math.round(width * scale / 16.0) * 16;
                height = Math.round(height * scale / 16.0) * 16;
            }
            return width + "x" + height;
        }

        String lower = model.toLowerCase(Locale.ROOT);
        if (lower.equals("dall-e-2"))
            return "1024x1024";
        boolean dalle3 = lower.equals("dall-e-3");
        if (ratio > 1.2)
            return dalle3 ? "1792x1024" : "1536x1024";
        if (ratio < 0.83)
            return dalle3 ? "1024x1792" : "1024x1536";
        return "1024x1024";
    }

    private static String extractBase64(String responseBody) {
        JSONArray data = new JSONObject(responseBody).optJSONArray("data");
        if (data == null || data.isEmpty() || data.getJSONObject(0).optString("b64_json", "").isEmpty())
            throw new RuntimeException("OpenAI response contains no image");
        return data.getJSONObject(0).getString("b64_json");
    }

    private static String extractError(int code, String body) {
        String message = null;
        try {
            message = new JSONObject(body).getJSONObject("error").optString("message", null);
        } catch (JSONException e) {
            /* no JSON error object, the status text below is enough */
        }
        return switch (code) {
            case 401 -> "OpenAI: invalid bearer token (401)";
            case 429 -> "OpenAI: rate limit or quota exceeded (429)" + (message != null ? " - " + message : "");
            default -> "OpenAI error " + code + (message != null ? ": " + message : "");
        };
    }

    @Override
    public String getApiKey() {
        return apiKey;
    }

    @Override
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? ApplicationConfig.OPENAI_DEFAULT_BASE_URL
                : baseUrl.trim().replaceAll("/+$", "");
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }
}
