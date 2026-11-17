package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.util.I18nManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

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
 * <p>After a successful connection, Google Gemini and OpenAI are also checked for the brainstorm: the language model
 * and its web search. Only a decided result is stored, also for the connection: a check that could not decide (no
 * network, timeout, HTTP 429 or 5xx) keeps the stored state; the reasons of failed checks are kept in memory.</p>
 *
 * @author Matthias Brusdeylins
 * @since 1.5.0
 */
public class ApiProviderValidator {

    private static final Logger logger = LoggerFactory.getLogger(ApiProviderValidator.class);

    /*  The question of every web search check: no model knows the current time from its training, only a search  */
    static final String SEARCH_CHECK_QUESTION = "Search the web: what time is it right now in Berlin? Answer with "
            + "the time only.";

    /**
     * A check of an account: every account gets its connection checked, Google Gemini and OpenAI also their
     * language model and its web search.
     *
     * @since 2.1.2
     */
    public enum Check { CONNECTION, LANGUAGE_MODEL, WEB_SEARCH }

    /**
     * The outcome of a check of a language model or its web search. A check that could not decide (no network,
     * timeout, HTTP 429 or 5xx) says nothing about the account, so its stored result stays.
     *
     * @param problem the reason why it does not work, or null if it works
     * @param decided false if the check could not decide
     * @since 2.1.2
     */
    public record Outcome(String problem, boolean decided) {

        /**
         * Checks if the check decided that it works.
         *
         * @return true if it works
         */
        public boolean works() {
            return decided && problem == null;
        }

        /*  the outcome of a check future: its value is decided, its failure undecided  */
        static Outcome of(String problem, Throwable error) {
            return error == null ? new Outcome(problem, true) : new Outcome(reasonOf(error), false);
        }
    }

    /**
     * The outcomes of the checks of a language model: its answer (OpenAI: and tool call) and its web search.
     *
     * @param chat   the outcome of the language model check
     * @param search the outcome of the web search check
     * @since 2.1.2
     */
    public record ModelCheck(Outcome chat, Outcome search) {
    }

    private record Key(ApiProvider provider, Check check) {
    }

    /*  a reason of a failed check: the problem as the API told it, wrapped by an i18n key if not null  */
    private record Reason(String key, String problem) {
    }

    private final ApplicationConfig config;
    private final BflApiService bflApiService;
    private final GeminiApiService geminiApiService;
    private final OpenAiApiService openAiApiService;
    private final Map<Key, Reason> reasons = new ConcurrentHashMap<>();
    private final List<Runnable> completionListeners = new CopyOnWriteArrayList<>();
    private final Map<String, ModelCheck> lastOpenAiChecks = new HashMap<>();

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
     * <li>If a key is configured but refused (HTTP 400, 401, 403, 404): provider marked as not validated</li>
     * <li>If validation fails for the moment (no network, timeout, HTTP 429 or 5xx): the last status stays</li>
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

        /*  OpenAI first: only its fresh result tells if OpenAI is the only provider in use, whose
            other providers are not contacted and keep their stored status  */
        CompletableFuture<Boolean> openAiValidation = validateOpenAiProvider();
        CompletableFuture<Boolean> openAiOnly = openAiValidation.handle((valid, error) -> {
            boolean only = ProviderPolicy.isOpenAiOnly(config);
            if (only) {
                logger.info("OpenAI is the only provider in use, BFL and Gemini are not validated");
                Reason skipped = new Reason("status.api.check.skipped", null);
                reasons.put(new Key(ApiProvider.BFL, Check.CONNECTION), skipped);
                reasons.put(new Key(ApiProvider.GOOGLE_GEMINI, Check.CONNECTION), skipped);
            }
            return only;
        });
        CompletableFuture<Boolean> bflValidation = openAiOnly.thenCompose((only) -> only
                ? CompletableFuture.completedFuture(config.isBflProviderValidated()) : validateBflProvider());
        CompletableFuture<Boolean> geminiValidation = openAiOnly.thenCompose((only) -> only
                ? CompletableFuture.completedFuture(config.isGeminiProviderValidated()) : validateGeminiProvider());

        return CompletableFuture.allOf(bflValidation, geminiValidation, openAiValidation)
                .whenComplete((v, error) -> completionListeners.forEach(Runnable::run))
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
            setReason(ApiProvider.BFL, Check.CONNECTION, null);
            // Don't change enabled status - respect user preference
            return CompletableFuture.completedFuture(false);
        }

        logger.info("Validating BFL API key...");
        final boolean hasUserPreference = config.hasBflProviderEnabledPreference();

        return bflApiService.testConnection()
                .thenApply(isValid -> {
                    storeConnection(ApiProvider.BFL, isValid);

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
                .exceptionally(ex -> connectionKept(ApiProvider.BFL, ex));
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
            setReason(ApiProvider.GOOGLE_GEMINI, Check.CONNECTION, null);
            clearModelChecks(ApiProvider.GOOGLE_GEMINI, config::setGeminiChatOk, config::setGeminiWebSearch);
            // Don't change enabled status - respect user preference
            return CompletableFuture.completedFuture(false);
        }

        logger.info("Validating Gemini API key...");
        final boolean hasUserPreference = config.hasGeminiProviderEnabledPreference();

        return geminiApiService.testConnection()
                .thenApply(isValid -> {
                    storeConnection(ApiProvider.GOOGLE_GEMINI, isValid);

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
                .thenCompose((isValid) -> {
                    /*  Without a connection, language model and search stay as last decided: no brainstorm anyway;
                        a disabled provider keeps them too, so no paid check runs for an account switched off  */
                    if (!isValid || !config.isGeminiProviderEnabled())
                        return CompletableFuture.completedFuture(isValid);
                    return checkGeminiModel().thenApply((check) -> true);
                })
                .exceptionally(ex -> connectionKept(ApiProvider.GOOGLE_GEMINI, ex));
    }

    /**
     * Checks the brainstorm language model of Gemini and its Google Search, both at once, and stores the decided
     * results as flags as soon as the checks end, with the reasons in memory; an undecided check keeps its flag.
     *
     * @return the outcomes of the checks
     * @since 2.1.2
     */
    public CompletableFuture<ModelCheck> checkGeminiModel() {
        return geminiApiService.checkChatModel().handle(Outcome::of)
                .thenCombine(geminiApiService.checkWebSearch().handle(Outcome::of), ModelCheck::new)
                .thenApply((ModelCheck check) -> {
                    storeModelCheck(ApiProvider.GOOGLE_GEMINI, check, config::setGeminiChatOk,
                            config::setGeminiWebSearch);
                    completionListeners.forEach(Runnable::run);
                    return check;
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
            setReason(ApiProvider.OPENAI, Check.CONNECTION, null);
            clearModelChecks(ApiProvider.OPENAI, config::setOpenAiChatOk, config::setOpenAiWebSearch);
            return CompletableFuture.completedFuture(false);
        }

        logger.info("Validating OpenAI API key...");
        final boolean hasUserPreference = config.hasOpenAiProviderEnabledPreference();

        return openAiApiService.testConnection()
                .thenApply(isValid -> {
                    storeConnection(ApiProvider.OPENAI, isValid);

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
                .thenCompose((isValid) -> {
                    /*  Without a connection, or for a disabled provider, the last decided results stay  */
                    String model = config.getOpenAiChatModel();
                    if (!isValid || !config.isOpenAiProviderEnabled())
                        return CompletableFuture.completedFuture(isValid);
                    if (!model.isBlank())
                        return checkOpenAiModel(model).thenApply((check) -> true);

                    /*  No chosen model: no brainstorm  */
                    clearModelChecks(ApiProvider.OPENAI, config::setOpenAiChatOk, config::setOpenAiWebSearch);
                    return CompletableFuture.completedFuture(true);
                })
                .exceptionally(ex -> connectionKept(ApiProvider.OPENAI, ex));
    }

    /**
     * Checks an OpenAI language model (answer and tool call) and its web search, both at once. The decided results
     * are stored as flags as soon as the checks end, also after the settings dialog that started them was closed,
     * but only if the model is the chosen one of the configuration by then; an undecided check keeps its flag.
     *
     * @param model the model id
     * @return the outcomes of the checks
     * @since 2.1.2
     */
    public CompletableFuture<ModelCheck> checkOpenAiModel(String model) {
        return openAiApiService.checkChatModel(model).handle(Outcome::of)
                .thenCombine(openAiApiService.checkWebSearch(model).handle(Outcome::of), ModelCheck::new)
                .thenApply((ModelCheck check) -> {
                    synchronized (this) {
                        lastOpenAiChecks.put(model, check);
                        if (model.equals(config.getOpenAiChatModel())) {
                            storeModelCheck(ApiProvider.OPENAI, check, config::setOpenAiChatOk,
                                    config::setOpenAiWebSearch);
                            completionListeners.forEach(Runnable::run);
                        }
                    }
                    return check;
                });
    }

    /**
     * Stores the flags of a newly saved OpenAI language model together with their reasons, replacing those of the
     * former model: the outcomes of its last finished check, or none (no brainstorm) if no model is chosen or its
     * check still runs, which then stores them itself. Called after the model was set in the configuration.
     *
     * @param model the saved model id, empty for none
     * @since 2.1.2
     */
    public synchronized void storeOpenAiModelCheck(String model) {
        clearModelChecks(ApiProvider.OPENAI, config::setOpenAiChatOk, config::setOpenAiWebSearch);
        ModelCheck check = lastOpenAiChecks.get(model);
        if (!model.isBlank() && check != null)
            storeModelCheck(ApiProvider.OPENAI, check, config::setOpenAiChatOk, config::setOpenAiWebSearch);
        completionListeners.forEach(Runnable::run);
    }

    /**
     * Stores the decided result of a connection test, of the program start or of the settings: validated, or refused
     * (HTTP 400, 401, 403, 404) with its reason; the reason of an earlier undecided or skipped check goes.
     *
     * @param provider the API provider
     * @param works    true if the connection works
     * @since 2.1.2
     */
    public void connectionTested(ApiProvider provider, boolean works) {
        storeConnection(provider, works);
        completionListeners.forEach(Runnable::run);
    }

    private void storeConnection(ApiProvider provider, boolean works) {
        switch (provider) {
            case BFL -> config.setBflProviderValidated(works);
            case GOOGLE_GEMINI -> config.setGeminiProviderValidated(works);
            case OPENAI -> config.setOpenAiProviderValidated(works);
        }
        if (works)
            reasons.remove(new Key(provider, Check.CONNECTION));
        else
            reasons.put(new Key(provider, Check.CONNECTION), new Reason("status.api.check.refused", null));
    }

    /*  a transient failure of a connection test (no network, timeout, HTTP 429 or 5xx) keeps the last decided state  */
    private boolean connectionKept(ApiProvider provider, Throwable error) {
        logger.warn("{} API validation failed for the moment, last result kept: {}", provider, reasonOf(error));
        reasons.put(new Key(provider, Check.CONNECTION), new Reason("status.api.check.kept", reasonOf(error)));
        return isProviderValidated(provider);
    }

    /*  stores the decided flags of a model check with their reasons; an undecided check keeps its flag  */
    private void storeModelCheck(ApiProvider provider, ModelCheck check, Consumer<Boolean> chatFlag,
            Consumer<Boolean> searchFlag) {
        storeOutcome(provider, Check.LANGUAGE_MODEL, check.chat(), chatFlag);
        storeOutcome(provider, Check.WEB_SEARCH, check.search(), searchFlag);
        logger.info("{} brainstorm check: language model {}, web search {}", provider, check.chat(), check.search());
    }

    private void storeOutcome(ApiProvider provider, Check check, Outcome outcome, Consumer<Boolean> flag) {
        if (outcome.decided()) {
            flag.accept(outcome.works());
            setReason(provider, check, outcome.problem());
        }
        else
            reasons.put(new Key(provider, check), new Reason("status.api.check.kept", outcome.problem()));
    }

    /*  without an account or a chosen language model there is nothing to check: no brainstorm, no reasons  */
    private void clearModelChecks(ApiProvider provider, Consumer<Boolean> chatFlag, Consumer<Boolean> searchFlag) {
        chatFlag.accept(false);
        searchFlag.accept(false);
        setReason(provider, Check.LANGUAGE_MODEL, null);
        setReason(provider, Check.WEB_SEARCH, null);
    }

    /**
     * Registers a listener that runs on the thread of the validation whenever {@link #validateAllProviders()} has
     * finished or a later check has stored its flags, e.g. to refresh the brainstorm button and the status bar.
     *
     * @param listener the listener
     * @since 2.1.2
     */
    public void addCompletionListener(Runnable listener) {
        completionListeners.add(listener);
    }

    /**
     * Gets the reason of a failed check of the last run, kept in memory only. A check that could not decide (no
     * network, timeout, HTTP 429 or 5xx) says so, as its stored result stays.
     *
     * @param provider the API provider
     * @param check    the check
     * @return the reason, or null if the check worked, did not run, or failed without a known reason
     * @since 2.1.2
     */
    public String getReason(ApiProvider provider, Check check) {
        /*  translated on reading, so a change of the language also reaches the reasons of earlier checks  */
        Reason reason = reasons.get(new Key(provider, check));
        if (reason == null)
            return null;
        return reason.key() == null ? reason.problem() : I18nManager.getText(reason.key(), reason.problem());
    }

    private void setReason(ApiProvider provider, Check check, String problem) {
        if (problem == null)
            reasons.remove(new Key(provider, check));
        else
            reasons.put(new Key(provider, check), new Reason(null, problem));
    }

    /**
     * Gets the message of a failed check, unwrapped from the exception of its future.
     *
     * @param error the failure
     * @return the message, or the failure itself if it has none
     * @since 2.1.2
     */
    public static String reasonOf(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null)
            cause = cause.getCause();
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
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
