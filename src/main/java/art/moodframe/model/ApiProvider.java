package art.moodframe.model;

/**
 * Enum representing available API providers for image generation.
 *
 * @author Matthias Brusdeylins
 * @since 1.5.0
 */
public enum ApiProvider {

    /**
     * BFL (Blackforest Labs) - Provider for FLUX models.
     */
    BFL("BFL (Blackforest Labs)", "bfl.api.key", "provider.bfl"),

    /**
     * Google Gemini - Provider for Gemini image generation models.
     */
    GOOGLE_GEMINI("Google Gemini", "gemini.api.key", "provider.gemini"),

    /**
     * OpenAI (or an OpenAI-compatible gateway) - Provider for the image models of the OpenAI Images API.
     */
    OPENAI("OpenAI", "openai.api.key", "provider.openai");

    private final String displayName;
    private final String apiKeyConfigKey;
    private final String providerConfigPrefix;

    ApiProvider(String displayName, String apiKeyConfigKey, String providerConfigPrefix) {
        this.displayName = displayName;
        this.apiKeyConfigKey = apiKeyConfigKey;
        this.providerConfigPrefix = providerConfigPrefix;
    }

    /**
     * Gets the display name of this provider.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the configuration key for the API key of this provider.
     *
     * @return the API key configuration key
     */
    public String getApiKeyConfigKey() {
        return apiKeyConfigKey;
    }

    /**
     * Gets the configuration key prefix for provider settings.
     *
     * @return the provider configuration prefix
     */
    public String getProviderConfigPrefix() {
        return providerConfigPrefix;
    }
}
