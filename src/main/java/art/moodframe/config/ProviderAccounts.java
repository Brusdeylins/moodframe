package art.moodframe.config;

import java.util.List;

import art.moodframe.model.ApiProvider;

/**
 * The accounts of the image providers, by {@link ApiProvider}: API key, the result of the connection test, whether
 * the user enabled the provider, what its chat model can do; the extras of BFL (region, timeout) and OpenAI (base
 * URL, models, chat model) and the choice of the brainstorm engine.
 *
 * <p>The keys in {@code config.properties} are {@code <prefix>.validated}, {@code <prefix>.enabled},
 * {@code <prefix>.chat_ok} and {@code <prefix>.web_search} with the prefix of
 * {@link ApiProvider#getProviderConfigPrefix()}, as in all former versions.</p>
 */
public class ProviderAccounts {

    /** The base URL of the OpenAI API, used when none is configured. */
    public static final String OPENAI_DEFAULT_BASE_URL = "https://api.openai.com/v1";

    private final ConfigStore store;

    ProviderAccounts(ConfigStore store) {
        this.store = store;
    }

    /*  the encrypted key of a provider  */
    private static Secret secret(ApiProvider provider) {
        return switch (provider) {
            case BFL -> Secret.BFL_API_KEY;
            case GOOGLE_GEMINI -> Secret.GEMINI_API_KEY;
            case OPENAI -> Secret.OPENAI_API_KEY;
        };
    }

    /*  a key of the provider's settings, like provider.gemini.enabled  */
    private static String key(ApiProvider provider, String name) {
        return provider.getProviderConfigPrefix() + "." + name;
    }

    /*  ===== API key =====  */

    /**
     * Gets the API key of a provider: from its environment variable ({@code BFL_API_KEY}, {@code GEMINI_API_KEY},
     * {@code OPENAI_API_KEY}), its system property or, decrypted, from the configuration file.
     *
     * @param provider the provider
     * @return the key, or null if none is configured
     */
    public String getApiKey(ApiProvider provider) {
        return store.readSecret(secret(provider));
    }

    /**
     * Stores the API key of a provider encrypted; null or empty removes it.
     *
     * @param provider the provider
     * @param apiKey   the key
     */
    public void setApiKey(ApiProvider provider, String apiKey) {
        store.storeSecret(secret(provider), apiKey);
    }

    /**
     * @param provider the provider
     * @return true if a key of the provider is configured from any source
     */
    public boolean hasApiKey(ApiProvider provider) {
        return getApiKey(provider) != null;
    }

    /**
     * @param provider the provider
     * @return where the active key comes from: "environment", "system_property", "configuration" or "none"
     */
    public String getApiKeySource(ApiProvider provider) {
        return store.secretSource(secret(provider));
    }

    /**
     * @param provider the provider
     * @return false if the environment or a system property sets the key, which the settings cannot override
     */
    public boolean isApiKeyEditable(ApiProvider provider) {
        return store.isSecretEditable(secret(provider));
    }

    /** @return true if a key of at least one provider is configured */
    public boolean hasAnyApiKey() {
        for (ApiProvider provider : ApiProvider.values())
            if (hasApiKey(provider))
                return true;
        return false;
    }

    /*  ===== Validation and activation =====  */

    /**
     * @param provider the provider
     * @return true if the provider can generate: enabled by the user and with a key
     */
    public boolean isUsable(ApiProvider provider) {
        return isEnabled(provider) && hasApiKey(provider);
    }

    /**
     * @param provider the provider
     * @return true if the last connection test of the provider succeeded
     */
    public boolean isValidated(ApiProvider provider) {
        return store.getBoolean(key(provider, "validated"), false);
    }

    /**
     * @param provider  the provider
     * @param validated the result of the connection test
     */
    public void setValidated(ApiProvider provider, boolean validated) {
        store.set(key(provider, "validated"), String.valueOf(validated));
    }

    /**
     * @param provider the provider
     * @return true if the user enabled the provider (default off)
     */
    public boolean isEnabled(ApiProvider provider) {
        return store.getBoolean(key(provider, "enabled"), false);
    }

    /**
     * @param provider the provider
     * @param enabled  true to enable the provider
     */
    public void setEnabled(ApiProvider provider, boolean enabled) {
        store.set(key(provider, "enabled"), String.valueOf(enabled));
    }

    /**
     * @param provider the provider
     * @return true if the user ever switched the provider on or off, so the start does not enable it on its own
     */
    public boolean hasEnabledPreference(ApiProvider provider) {
        return store.contains(key(provider, "enabled"));
    }

    /*  ===== Chat model (Gemini and OpenAI) =====  */

    /**
     * @param provider the provider
     * @return true if the chat model of the provider answered the connection test
     */
    public boolean isChatOk(ApiProvider provider) {
        return store.getBoolean(key(provider, "chat_ok"), false);
    }

    /**
     * @param provider the provider
     * @param chatOk   true if the chat model answered
     */
    public void setChatOk(ApiProvider provider, boolean chatOk) {
        store.set(key(provider, "chat_ok"), String.valueOf(chatOk));
    }

    /**
     * @param provider the provider
     * @return true if the chat model of the provider can search the web, as the brainstorm needs
     */
    public boolean isWebSearch(ApiProvider provider) {
        return store.getBoolean(key(provider, "web_search"), false);
    }

    /**
     * @param provider  the provider
     * @param webSearch true if the chat model can search the web
     */
    public void setWebSearch(ApiProvider provider, boolean webSearch) {
        store.set(key(provider, "web_search"), String.valueOf(webSearch));
    }

    /*  ===== Brainstorm =====  */

    /** @return the engine of the brainstorm, "gemini" (default) or "openai" */
    public String getBrainstormEngine() {
        return "openai".equals(store.get("brainstorm.engine", "gemini")) ? "openai" : "gemini";
    }

    /** @param engine "gemini" or "openai"; anything else counts as "gemini" */
    public void setBrainstormEngine(String engine) {
        store.set("brainstorm.engine", "openai".equals(engine) ? "openai" : "gemini");
    }

    /**
     * @return true if OpenAI can chat: key, enabled and a chat model chosen; enough for the analysis of an image
     */
    public boolean isOpenAiChatAvailable() {
        return hasApiKey(ApiProvider.OPENAI) && isEnabled(ApiProvider.OPENAI) && !getOpenAiChatModel().isBlank();
    }

    /**
     * Tells whether a provider can run the brainstorm: key, enabled, validated, its chat model answered and can
     * search the web (OpenAI also needs a chosen chat model). BFL has no chat model.
     *
     * @param provider the provider
     * @return true if available
     */
    public boolean isBrainstormAvailable(ApiProvider provider) {
        return switch (provider) {
            case BFL -> false;
            case GOOGLE_GEMINI -> hasApiKey(provider) && isEnabled(provider) && isValidated(provider)
                    && isChatOk(provider) && isWebSearch(provider);
            case OPENAI -> isOpenAiChatAvailable() && isValidated(provider) && isChatOk(provider)
                    && isWebSearch(provider);
        };
    }

    /*  ===== BFL =====  */

    /** @return the BFL region, "Global" (default), "EU" or "US" */
    public String getApiRegion() {
        return store.get("api.region", "Global");
    }

    /** @param region the BFL region, "Global", "EU" or "US" */
    public void setApiRegion(String region) {
        store.set("api.region", region);
    }

    /** @return the timeout of a provider request in seconds (default 600) */
    public int getApiTimeoutSeconds() {
        return Integer.parseInt(store.get("api.timeout_seconds", "600"));
    }

    /** @param seconds the timeout of a provider request in seconds */
    public void setApiTimeoutSeconds(int seconds) {
        store.set("api.timeout_seconds", String.valueOf(seconds));
    }

    /*  ===== OpenAI =====  */

    /** @return the base URL of the OpenAI API or a compatible gateway, without a trailing slash */
    public String getOpenAiBaseUrl() {
        String url = store.get("openai.base_url");
        if (url == null || url.trim().isEmpty())
            return OPENAI_DEFAULT_BASE_URL;
        return url.trim().replaceAll("/+$", "");
    }

    /** @param baseUrl the base URL; null, empty or the default removes the setting */
    public void setOpenAiBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()
                || baseUrl.trim().replaceAll("/+$", "").equals(OPENAI_DEFAULT_BASE_URL))
            store.remove("openai.base_url");
        else
            store.put("openai.base_url", baseUrl.trim().replaceAll("/+$", ""));
        store.save();
    }

    /** @return the ids of the image models the user chose, empty if none */
    public List<String> getOpenAiModels() {
        return splitIds(store.get("provider.openai.models", ""));
    }

    /** @param models the ids of the chosen image models */
    public void setOpenAiModels(List<String> models) {
        store.set("provider.openai.models", String.join(",", models));
    }

    /** @return true if the user ever chose the image models, so a new list keeps the choice */
    public boolean hasOpenAiModelSelection() {
        return store.contains("provider.openai.models");
    }

    /** @return the ids of the image models the account offers, as found by the last connection test */
    public List<String> getOpenAiAvailableModels() {
        return splitIds(store.get("provider.openai.available", ""));
    }

    /** @param models the ids of the image models the account offers */
    public void setOpenAiAvailableModels(List<String> models) {
        store.set("provider.openai.available", String.join(",", models));
    }

    /** @return the id of the chat model of OpenAI, empty if none is chosen */
    public String getOpenAiChatModel() {
        return store.get("provider.openai.chat_model", "");
    }

    /** @param model the id of the chat model, null or empty for none */
    public void setOpenAiChatModel(String model) {
        store.set("provider.openai.chat_model", model == null ? "" : model.trim());
    }

    private static List<String> splitIds(String ids) {
        return ids.isBlank() ? List.of() : List.of(ids.split(","));
    }
}
