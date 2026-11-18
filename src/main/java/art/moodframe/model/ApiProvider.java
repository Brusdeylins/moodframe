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
    BFL("BFL (Blackforest Labs)", "bfl.api.key", "provider.bfl", 10),

    /**
     * Google Gemini - Provider for Gemini image generation models.
     */
    GOOGLE_GEMINI("Google Gemini", "gemini.api.key", "provider.gemini", 20),

    /**
     * OpenAI (or an OpenAI-compatible gateway) - Provider for the image models of the OpenAI Images API.
     */
    OPENAI("OpenAI", "openai.api.key", "provider.openai", 50);

    private final String displayName;
    private final String apiKeyConfigKey;
    private final String providerConfigPrefix;
    private final int maxFileSizeMegabytes;

    ApiProvider(String displayName, String apiKeyConfigKey, String providerConfigPrefix, int maxFileSizeMegabytes) {
        this.displayName = displayName;
        this.apiKeyConfigKey = apiKeyConfigKey;
        this.providerConfigPrefix = providerConfigPrefix;
        this.maxFileSizeMegabytes = maxFileSizeMegabytes;
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

    /**
     * Gets the largest image file the provider takes as an attachment: BFL 10 MB, Google Gemini 20 MB, OpenAI 50 MB.
     *
     * @return the size in bytes
     */
    public long getMaxFileSize() {
        return maxFileSizeMegabytes * 1024L * 1024;
    }
}
