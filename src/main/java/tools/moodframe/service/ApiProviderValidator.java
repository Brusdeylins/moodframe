package tools.moodframe.service;

import tools.moodframe.config.ApplicationConfig;
import tools.moodframe.model.ApiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for validating API provider credentials on application startup.
 *
 * <p>This service automatically tests configured API keys for all providers
 * (BFL, Google Gemini and OpenAI) and updates their validation status in the configuration.
 * Validation results indicate whether a provider CAN work (has valid credentials),
 * but do NOT override the user's manual enable/disable preference.</p>
 *
 * <p><strong>Important:</strong> This validator only updates validation status, not
 * the enabled status. User preferences for provider activation are always respected
 * and persisted across application restarts.</p>
 *
 * @author Matthias Brusdeylins
 * @since 1.5.0
 */
public class ApiProviderValidator {

    private static final Logger logger = LoggerFactory.getLogger(ApiProviderValidator.class);

    private final ApplicationConfig config;
    private final BflApiService bflApiService;
    private final GeminiApiService geminiApiService;
    private final OpenAiApiService openAiApiService;

    /**
     * Creates a new API provider validator.
     *
     * @param config the application configuration
     * @param bflApiService the BFL API service for validation
     * @param geminiApiService the Gemini API service for validation
     * @param openAiApiService the OpenAI API service for validation
     */
    public ApiProviderValidator(ApplicationConfig config,
                               BflApiService bflApiService,
                               GeminiApiService geminiApiService,
                               OpenAiApiService openAiApiService) {
        this.config = config;
        this.bflApiService = bflApiService;
        this.geminiApiService = geminiApiService;
        this.openAiApiService = openAiApiService;
    }

    /**
     * Validates all configured API providers asynchronously.
     *
     * <p>This method tests the connection for each provider that has a configured
     * API key and updates the validation status in the configuration. User preferences
     * for provider activation (enabled/disabled) are NOT modified.</p>
     *
     * <p>Validation results:</p>
     * <ul>
     * <li>If a key is configured and validation succeeds: provider marked as validated</li>
     * <li>If a key is configured but validation fails: provider marked as not validated</li>
     * <li>If no key is configured: provider marked as not validated</li>
     * </ul>
     *
     * <p><strong>Note:</strong> Enabled status is controlled separately by user preference
     * and persists across application restarts.</p>
     *
     * @return a CompletableFuture containing a map of provider to validation status
     */
    public CompletableFuture<Map<ApiProvider, Boolean>> validateAllProviders() {
        logger.info("Starting API provider validation...");

        /*  Only OpenAI in use: the other providers are not contacted, their stored status stays  */
        boolean openAiOnly = ProviderPolicy.isOpenAiOnly(config);
        if (openAiOnly)
            logger.info("OpenAI is the only provider in use, BFL and Gemini are not validated");
        CompletableFuture<Boolean> bflValidation = openAiOnly
                ? CompletableFuture.completedFuture(config.isBflProviderValidated()) : validateBflProvider();
        CompletableFuture<Boolean> geminiValidation = openAiOnly
                ? CompletableFuture.completedFuture(config.isGeminiProviderValidated()) : validateGeminiProvider();
        CompletableFuture<Boolean> openAiValidation = validateOpenAiProvider();

        return CompletableFuture.allOf(bflValidation, geminiValidation, openAiValidation)
                .thenApply(v -> {
                    Map<ApiProvider, Boolean> results = new HashMap<>();
                    results.put(ApiProvider.BFL, bflValidation.join());
                    results.put(ApiProvider.GOOGLE_GEMINI, geminiValidation.join());
                    results.put(ApiProvider.OPENAI, openAiValidation.join());

                    logger.info("API provider validation completed: BFL={}, Gemini={}, OpenAI={}",
                            results.get(ApiProvider.BFL),
                            results.get(ApiProvider.GOOGLE_GEMINI),
                            results.get(ApiProvider.OPENAI));

                    return results;
                });
    }

    /**
     * Validates the BFL API provider.
     *
     * <p>Note: This method only updates the validation status. The enabled status is
     * only auto-set on first successful validation (when no user preference exists).
     * After initial setup, the enabled status is controlled by user preference and
     * will not be overridden by automatic validation.</p>
     *
     * @return a CompletableFuture that resolves to true if validation succeeded
     */
    public CompletableFuture<Boolean> validateBflProvider() {
        if (!config.hasApiKey()) {
            logger.info("BFL API key not configured, skipping validation");
            config.setBflProviderValidated(false);
            // Don't change enabled status - respect user preference
            return CompletableFuture.completedFuture(false);
        }

        logger.info("Validating BFL API key...");
        final boolean hasUserPreference = config.hasBflProviderEnabledPreference();

        return bflApiService.testConnection()
                .thenApply(isValid -> {
                    config.setBflProviderValidated(isValid);

                    if (isValid) {
                        // Only auto-enable on first successful validation (no user preference exists)
                        if (!hasUserPreference) {
                            config.setBflProviderEnabled(true);
                            logger.info("BFL API validation successful - provider auto-enabled (first time setup)");
                        } else {
                            logger.info("BFL API validation successful - provider status: {} (user preference)",
                                    config.isBflProviderEnabled() ? "enabled" : "disabled");
                        }
                    } else {
                        logger.warn("BFL API validation failed - provider status: {}",
                                config.isBflProviderEnabled() ? "enabled (invalid key)" : "disabled");
                    }

                    return isValid;
                })
                .exceptionally(ex -> {
                    logger.error("BFL API validation failed with exception", ex);
                    config.setBflProviderValidated(false);
                    // Don't change enabled status - respect user preference
                    return false;
                });
    }

    /**
     * Validates the Google Gemini API provider.
     *
     * <p>Note: This method only updates the validation status. The enabled status is
     * only auto-set on first successful validation (when no user preference exists).
     * After initial setup, the enabled status is controlled by user preference and
     * will not be overridden by automatic validation.</p>
     *
     * @return a CompletableFuture that resolves to true if validation succeeded
     */
    public CompletableFuture<Boolean> validateGeminiProvider() {
        if (!config.hasGeminiApiKey()) {
            logger.info("Gemini API key not configured, skipping validation");
            config.setGeminiProviderValidated(false);
            // Don't change enabled status - respect user preference
            return CompletableFuture.completedFuture(false);
        }

        logger.info("Validating Gemini API key...");
        final boolean hasUserPreference = config.hasGeminiProviderEnabledPreference();

        return geminiApiService.testConnection()
                .thenApply(isValid -> {
                    config.setGeminiProviderValidated(isValid);

                    if (isValid) {
                        // Only auto-enable on first successful validation (no user preference exists)
                        if (!hasUserPreference) {
                            config.setGeminiProviderEnabled(true);
                            logger.info("Gemini API validation successful - provider auto-enabled (first time setup)");
                        } else if (!config.isGeminiProviderEnabled() && !config.isBflProviderEnabled()) {
                            /*  No provider usable (e.g. after migration to a new machine): enable Gemini  */
                            config.setGeminiProviderEnabled(true);
                            logger.info("Gemini API validation successful - no provider enabled, Gemini auto-enabled");
                        } else {
                            logger.info("Gemini API validation successful - provider status: {} (user preference)",
                                    config.isGeminiProviderEnabled() ? "enabled" : "disabled");
                        }
                    } else {
                        logger.warn("Gemini API validation failed - provider status: {}",
                                config.isGeminiProviderEnabled() ? "enabled (invalid key)" : "disabled");
                    }

                    return isValid;
                })
                .exceptionally(ex -> {
                    logger.error("Gemini API validation failed with exception", ex);
                    config.setGeminiProviderValidated(false);
                    // Don't change enabled status - respect user preference
                    return false;
                });
    }

    /**
     * Validates the OpenAI API provider by listing its models; a successful check also refreshes the model list.
     * Like for BFL, the enabled status is only auto-set on the first successful validation.
     *
     * @return a CompletableFuture that resolves to true if validation succeeded
     */
    public CompletableFuture<Boolean> validateOpenAiProvider() {
        if (!config.hasOpenAiApiKey()) {
            logger.info("OpenAI API key not configured, skipping validation");
            config.setOpenAiProviderValidated(false);
            return CompletableFuture.completedFuture(false);
        }

        logger.info("Validating OpenAI API key...");
        final boolean hasUserPreference = config.hasOpenAiProviderEnabledPreference();

        return openAiApiService.testConnection()
                .thenApply(isValid -> {
                    config.setOpenAiProviderValidated(isValid);

                    if (isValid && !hasUserPreference) {
                        config.setOpenAiProviderEnabled(true);
                        logger.info("OpenAI API validation successful - provider auto-enabled (first time setup)");
                    } else if (isValid) {
                        logger.info("OpenAI API validation successful - provider status: {} (user preference)",
                                config.isOpenAiProviderEnabled() ? "enabled" : "disabled");
                    } else {
                        logger.warn("OpenAI API validation failed - provider status: {}",
                                config.isOpenAiProviderEnabled() ? "enabled (invalid key)" : "disabled");
                    }

                    return isValid;
                })
                .exceptionally(ex -> {
                    logger.error("OpenAI API validation failed with exception", ex);
                    config.setOpenAiProviderValidated(false);
                    return false;
                });
    }

    /**
     * Gets the current validation status for a provider.
     *
     * @param provider the API provider
     * @return true if the provider is currently validated, false otherwise
     */
    public boolean isProviderValidated(ApiProvider provider) {
        return switch (provider) {
            case BFL -> config.isBflProviderValidated();
            case GOOGLE_GEMINI -> config.isGeminiProviderValidated();
            case OPENAI -> config.isOpenAiProviderValidated();
        };
    }

    /**
     * Gets the current enabled status for a provider.
     *
     * @param provider the API provider
     * @return true if the provider is currently enabled, false otherwise
     */
    public boolean isProviderEnabled(ApiProvider provider) {
        return switch (provider) {
            case BFL -> config.isBflProviderEnabled();
            case GOOGLE_GEMINI -> config.isGeminiProviderEnabled();
            case OPENAI -> config.isOpenAiProviderEnabled();
        };
    }
}
