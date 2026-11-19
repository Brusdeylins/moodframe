package art.moodframe.service.provider.openai;

import art.moodframe.model.GenerationModel;
import art.moodframe.service.provider.ApiHttpException;
import okhttp3.Request;
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
 * The account of the OpenAI client: the connection test, which lists the models of the API ({@code GET /models}),
 * finds the API under {@code /v1} of a base URL without path and stores the models and the image models to offer.
 *
 * @since 2.1.3
 */
final class OpenAiAccounts {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiAccounts.class);

    private final OpenAiTransport transport;

    OpenAiAccounts(OpenAiTransport transport) {
        this.transport = transport;
    }

    /**
     * Tests the connection by listing the models; a successful test of the configured client stores the models of
     * the API, the image models to offer and the base URL with {@code /v1} the API answered at.
     *
     * @return a future with true if the API accepted the token, false on a definite refusal
     */
    CompletableFuture<Boolean> testConnection() {
        return testAccount().thenApply((OpenAiApiService.Account account) -> {
            if (account.works() && !transport.detached()) {
                if (!account.baseUrl().equals(transport.getBaseUrl()))
                    transport.foundVersioned(account.baseUrl());
                transport.config().accounts().setOpenAiAvailableModels(account.models());
                transport.config().accounts().setOpenAiModels(account.selected());
            }
            return account.works();
        });
    }

    /**
     * Lists the models with the token and base URL of the transport, without storing anything.
     *
     * @return a future with what the test found
     */
    CompletableFuture<OpenAiApiService.Account> testAccount() {
        return CompletableFuture.supplyAsync(() -> {
            String base = transport.getBaseUrl();
            if (!transport.isConfigured()) {
                logger.warn("Cannot test OpenAI API connection: no bearer token configured");
                return new OpenAiApiService.Account(false, base, List.of(), List.of());
            }

            /*  A URL without a path is usually meant as ".../v1": if that answers too, the other endpoints are there  */
            if (java.net.URI.create(base).getPath().isEmpty() && answersModels(base + "/v1")) {
                logger.info("OpenAI base URL {} answers under /v1, using {}/v1", base, base);
                base = base + "/v1";
            }

            Request request = new Request.Builder()
                    .url(base + "/models")
                    .header("Authorization", "Bearer " + transport.getApiKey())
                    .get()
                    .build();

            try (Response response = transport.client().newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    logger.warn("OpenAI API connection test failed: HTTP {} - {}", response.code(), body);

                    /*  Only a definite answer (HTTP 400, 401, 403, 404) decides, any other failure is transient  */
                    if (!ApiHttpException.isDefinite(response.code()))
                        throw OpenAiTransport.httpError(response.code(), body);
                    return new OpenAiApiService.Account(false, base, List.of(), List.of());
                }

                List<String> ids = parseModelIds(body);

                /*  Keep the user's selection, preselect the known image names only the first time  */
                List<String> selected = transport.config().hasOpenAiModelSelection()
                        ? transport.config().getOpenAiModels().stream().filter(ids::contains).toList()
                        : imageModelsOf(ids);
                logger.info("OpenAI API connection test successful, {} model(s), selected: {}", ids.size(), selected);
                return new OpenAiApiService.Account(true, base, ids, selected);
            } catch (java.io.IOException e) {
                logger.error("OpenAI API connection test failed with exception", e);
                throw new java.io.UncheckedIOException(e);
            }
        }, transport.io());
    }

    private boolean answersModels(String base) {
        Request request = new Request.Builder().url(base + "/models")
                .header("Authorization", "Bearer " + transport.getApiKey()).get().build();
        try (Response response = transport.client().newCall(request).execute()) {
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
        return ids.stream().filter(OpenAiAccounts::isImageModel).toList();
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

    /**
     * Gets the image models the user chose to offer.
     *
     * @return the model ids
     */
    List<String> imageModels() {
        return transport.config().getOpenAiModels();
    }
}
