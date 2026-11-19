package art.moodframe.service.provider;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * The image generation of the OpenAI client (the Images API or an OpenAI-compatible gateway): the bodies of
 * {@code /images/generations} and {@code /images/edits}, the sizes of the models and the answer with the image.
 *
 * @since 2.1.3
 */
final class OpenAiImageClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiImageClient.class);

    private static final long MAX_SIDE = 4096;

    private final OpenAiTransport transport;

    OpenAiImageClient(OpenAiTransport transport) {
        this.transport = transport;
    }

    /**
     * Generates one image: {@code POST /images/generations}, or {@code POST /images/edits} with reference images.
     *
     * @param request the request
     * @return a future with the finished job, the image as Base64
     */
    CompletableFuture<GenerationJobInfo> submit(ImageGenerationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            if (!transport.isConfigured())
                throw ProviderException.noKey("OpenAI");

            try {
                String model = request.getModel();
                List<byte[]> inputImages = inputImages(request);
                String path;
                java.util.function.Supplier<RequestBody> body;

                if (inputImages.isEmpty()) {
                    JSONObject json = buildGenerationBody(request);
                    logger.info("Submitting OpenAI generation request: model={}, size={}", model, json.getString("size"));
                    path = "/images/generations";
                    body = () -> RequestBody.create(json.toString(), OpenAiTransport.JSON);
                } else {
                    logger.info("Submitting OpenAI edit request: model={}, {} image(s)", model, inputImages.size());
                    path = "/images/edits";
                    body = () -> buildEditBody(request, inputImages);
                }

                try (Response response = transport.post(path, body)) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        logger.error("OpenAI API request failed: HTTP {} - {}", response.code(), responseBody);
                        throw OpenAiTransport.httpError(response.code(), responseBody);
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
                throw ProviderException.localized(e, "openai.error.timeout");
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                logger.error("Failed to submit OpenAI generation request", e);
                throw ProviderException.localized(e, "openai.error.failed", e.getMessage());
            }
        }, transport.io());
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
        List<byte[]> result = new ArrayList<>();
        for (String image : request.getInputImages())
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
            throw ProviderException.localized("openai.error.no.image");
        return data.getJSONObject(0).getString("b64_json");
    }
}
