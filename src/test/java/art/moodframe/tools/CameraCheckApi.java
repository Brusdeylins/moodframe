package art.moodframe.tools;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * The web APIs of the camera check: the image models under test and the vision model that rates the images blindly.
 * Keys come from the environment only, never from {@code ~/.moodframe}.
 *
 * @since 2.0.2
 */
final class CameraCheckApi {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private static final String GEMINI = "https://generativelanguage.googleapis.com/v1beta/models/";

    /*  Vision model and its token prices in USD per million (estimate after the Gemini 3 Flash list price)  */
    static final String VISION_MODEL = "gemini-3.8-flash";
    private static final double VISION_INPUT_PRICE = 0.50;
    private static final double VISION_OUTPUT_PRICE = 3.00;

    private CameraCheckApi() {
    }

    /**
     * An image model under test.
     *
     * @param id     the short id, used in file names
     * @param label  the name in the report
     * @param seeded whether it takes a seed, so all stages of an axis can share one
     * @param cost   the price of one 1K image in USD
     * @param envKey the environment variable holding the key
     */
    record Generator(String id, String label, boolean seeded, double cost, String envKey) {

        /** The key from the environment, null if not set. */
        String key() {
            String key = System.getenv(envKey);
            return key == null || key.isBlank() ? null : key.trim();
        }

        /**
         * Generates one image at 1K and 3:2.
         *
         * @param prompt the prompt
         * @param seed   the seed, null for a model without seed
         * @return the image file content
         * @throws Exception if the request fails for good
         */
        byte[] generate(String prompt, Long seed) throws Exception {
            return switch (id) {
                case "nano-banana-pro" -> gemini(key(), prompt);
                case "flux2-pro" -> flux(key(), "/v1/flux-2-pro", new JSONObject().put("prompt", prompt)
                        .put("width", 1248).put("height", 832).put("seed", seed).put("output_format", "png"));
                case "flux3" -> flux(key(), "/v1/flux-3-image", new JSONObject().put("prompt", prompt)
                        .put("aspect_ratio", "3:2").put("resolution", "1K"));
                case "gpt-image" -> openAi(key(), prompt);
                default -> throw new IllegalArgumentException(id);
            };
        }
    }

    /** The image models in the order of their priority under the cost cap. */
    static final List<Generator> GENERATORS = List.of(
            new Generator("nano-banana-pro", "Nano Banana Pro", false, 0.134, "GEMINI_API_KEY"),
            new Generator("flux2-pro", "FLUX.2 [pro]", true, 0.03, "BFL_API_KEY"),
            new Generator("flux3", "FLUX 3", false, 0.048, "BFL_API_KEY"),
            new Generator("gpt-image", "OpenAI gpt-image", false, 0.063, "OPENAI_API_KEY"));

    /*  Nano Banana Pro with the request body of the app (GeminiApiServiceImpl); the last image part is the final one,
        the ones before are thought images  */
    private static byte[] gemini(String key, String prompt) throws Exception {
        JSONObject body = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject().put("parts",
                        new JSONArray().put(new JSONObject().put("text", prompt)))))
                .put("generationConfig", new JSONObject().put("responseModalities", new JSONArray().put("IMAGE"))
                        .put("imageConfig", new JSONObject().put("aspectRatio", "3:2").put("imageSize", "1K")));
        JSONObject answer = new JSONObject(post(GEMINI + "gemini-3-pro-image:generateContent", key, body));
        JSONObject candidate = answer.getJSONArray("candidates").getJSONObject(0);
        JSONArray parts = candidate.optJSONObject("content") != null
                ? candidate.getJSONObject("content").optJSONArray("parts") : null;
        String data = null;
        for (int i = 0; parts != null && i < parts.length(); i++)
            if (parts.getJSONObject(i).has("inlineData"))
                data = parts.getJSONObject(i).getJSONObject("inlineData").getString("data");
        if (data == null)
            throw new IOException("no image, finishReason " + candidate.optString("finishReason"));
        return Base64.getDecoder().decode(data);
    }

    /*  BFL: submit, poll the polling URL until "Ready", download the sample  */
    private static byte[] flux(String key, String path, JSONObject body) throws Exception {
        HttpRequest submit = HttpRequest.newBuilder(URI.create("https://api.bfl.ai" + path))
                .header("X-Key", key).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        JSONObject job = new JSONObject(send(submit));
        for (int i = 0; i < 300; i++) {
            Thread.sleep(1000);
            HttpRequest poll = HttpRequest.newBuilder(URI.create(job.getString("polling_url"))).header("X-Key", key)
                    .GET().build();
            JSONObject status = new JSONObject(send(poll));
            if ("Ready".equalsIgnoreCase(status.optString("status")))
                return HTTP.send(HttpRequest.newBuilder(URI.create(status.getJSONObject("result").getString("sample")))
                        .GET().build(), HttpResponse.BodyHandlers.ofByteArray()).body();
            if (status.optString("status").matches("(?i).*(error|failed|moderated).*"))
                throw new IOException("BFL " + status.optString("status"));
        }
        throw new IOException("BFL timeout");
    }

    private static byte[] openAi(String key, String prompt) throws Exception {
        JSONObject body = new JSONObject().put("model", "gpt-image-1").put("prompt", prompt).put("size", "1536x1024")
                .put("quality", "medium");
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/images/generations"))
                .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(5)).POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return Base64.getDecoder().decode(new JSONObject(send(request)).getJSONArray("data").getJSONObject(0)
                .getString("b64_json"));
    }

    /**
     * An answer of the vision model.
     *
     * @param json the JSON object it answered with
     * @param cost the cost of the request in USD (estimate from the token counts)
     */
    record Rating(JSONObject json, double cost) {
    }

    /**
     * Asks the vision model one question about some images; the images are named "Image 1", "Image 2" and so on in
     * the order given, unless a label is given for each.
     *
     * @param question the question, which demands a JSON object as answer
     * @param images   the image files' content
     * @param labels   a label per image, null for "Image 1" and so on
     * @return the answer
     * @throws Exception if the request fails for good
     */
    static Rating ask(String question, List<byte[]> images, List<String> labels) throws Exception {
        JSONArray parts = new JSONArray();
        for (int i = 0; i < images.size(); i++) {
            parts.put(new JSONObject().put("text", labels != null ? labels.get(i) : "Image " + (i + 1) + ":"));
            parts.put(new JSONObject().put("inlineData", new JSONObject().put("mimeType", "image/jpeg")
                    .put("data", Base64.getEncoder().encodeToString(small(images.get(i))))));
        }
        parts.put(new JSONObject().put("text", question));
        JSONObject body = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts)))
                .put("generationConfig", new JSONObject().put("responseMimeType", "application/json"));
        JSONObject answer = new JSONObject(post(GEMINI + VISION_MODEL + ":generateContent",
                System.getenv("GEMINI_API_KEY"), body));
        JSONObject usage = answer.optJSONObject("usageMetadata", new JSONObject());
        double cost = usage.optLong("promptTokenCount") * VISION_INPUT_PRICE / 1e6
                + (usage.optLong("candidatesTokenCount") + usage.optLong("thoughtsTokenCount"))
                * VISION_OUTPUT_PRICE / 1e6;
        StringBuilder text = new StringBuilder();
        JSONArray answerParts = answer.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
                .getJSONArray("parts");
        for (int i = 0; i < answerParts.length(); i++)
            if (!answerParts.getJSONObject(i).optBoolean("thought"))
                text.append(answerParts.getJSONObject(i).optString("text"));
        String json = text.toString().trim().replaceAll("^```(json)?\\s*|\\s*```$", "");
        return new Rating(new JSONObject(json), cost);
    }

    /*  The image at most 768 pixels wide as JPEG, enough for the rating and a quarter of the tokens  */
    private static byte[] small(byte[] image) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(image));
        double scale = Math.min(1.0, 768.0 / source.getWidth());
        int width = (int) Math.round(source.getWidth() * scale);
        int height = (int) Math.round(source.getHeight() * scale);
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(target, "jpg", out);
        return out.toByteArray();
    }

    private static String post(String url, String key, JSONObject body) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(url)).header("x-goog-api-key", key)
                .header("Content-Type", "application/json").timeout(Duration.ofMinutes(4))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build());
    }

    /*  Retries rate limits and overloads with a growing pause; every other HTTP error fails at once  */
    private static String send(HttpRequest request) throws Exception {
        List<String> errors = new ArrayList<>();
        for (int attempt = 1; attempt <= 6; attempt++) {
            HttpResponse<String> response;
            try {
                response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            }
            catch (IOException e) {
                errors.add(e.toString());
                Thread.sleep(5000L * attempt);
                continue;
            }
            if (response.statusCode() / 100 == 2)
                return response.body();
            String error = "HTTP " + response.statusCode() + ": "
                    + response.body().substring(0, Math.min(300, response.body().length()));
            if (response.statusCode() != 429 && response.statusCode() / 100 != 5)
                throw new IOException(error);
            errors.add(error);
            Thread.sleep(10000L * attempt);
        }
        throw new IOException(String.join(" | ", errors));
    }
}
