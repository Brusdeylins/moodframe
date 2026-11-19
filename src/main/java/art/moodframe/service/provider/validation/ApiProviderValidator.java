package art.moodframe.service.provider.validation;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.service.provider.ProviderPolicy;
import art.moodframe.service.provider.ProviderRegistry;
import art.moodframe.service.provider.gemini.GeminiApiService;
import art.moodframe.service.provider.openai.OpenAiApiService;
import art.moodframe.util.I18nManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Service for validating API provider credentials on application startup.
 *
 * <p>This service automatically tests configured API keys for all providers
 * (BFL, Google Gemini and OpenAI) and updates their validation status in the configuration.
 * Validation results indicate whether a provider CAN work (has valid credentials),
 * but do NOT override the user's manual enable/disable preference: a provider is enabled on its own only if the user
 * never switched it on or off and no other provider is usable, the same rule for every provider.</p>
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
    private final ProviderRegistry providers;
    private final Map<Key, Reason> reasons = new ConcurrentHashMap<>();
    private final List<Runnable> completionListeners = new CopyOnWriteArrayList<>();
    private final Map<String, ModelCheck> lastOpenAiChecks = new HashMap<>();

    /*  The checks after a decided connection test of the providers with a brainstorm language model, given the result
        of the test; every other provider has its connection tested only  */
    private final Map<ApiProvider, Function<Boolean, CompletableFuture<Boolean>>> extras =
            new EnumMap<>(ApiProvider.class);

    /*  What depends on the account of a provider with extras, cleared if it has no key  */
    private final Map<ApiProvider, Runnable> noKey = new EnumMap<>(ApiProvider.class);

    /**
     * Creates a new API provider validator.
     *
     * @param config    the application configuration
     * @param providers tests the connections of all providers; Gemini and OpenAI also check their brainstorm language
     *                  models
     */
    public ApiProviderValidator(ApplicationConfig config, ProviderRegistry providers) {
        this.config = config;
        this.providers = providers;
        extras.put(ApiProvider.GOOGLE_GEMINI, this::checkGeminiAfterConnection);
        extras.put(ApiProvider.OPENAI, this::checkOpenAiAfterConnection);
        noKey.put(ApiProvider.GOOGLE_GEMINI, () -> clearModelChecks(ApiProvider.GOOGLE_GEMINI));
        noKey.put(ApiProvider.OPENAI, () -> clearModelChecks(ApiProvider.OPENAI));
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
        Set<ApiProvider> usable = usableProviders();

        /*  OpenAI first: only its fresh result tells if OpenAI is the only provider in use, whose
            other providers are not contacted and keep their stored status  */
        Map<ApiProvider, CompletableFuture<Boolean>> validations = new EnumMap<>(ApiProvider.class);
        CompletableFuture<Boolean> openAiValidation = validate(ApiProvider.OPENAI, usable);
        validations.put(ApiProvider.OPENAI, openAiValidation);
        CompletableFuture<Boolean> openAiOnly = openAiValidation.handle((valid, error) -> {
            boolean only = ProviderPolicy.isOpenAiOnly(config);
            if (only)
                logger.info("OpenAI is the only provider in use, the others are not validated");
            return only;
        });
        for (ApiProvider id : providers.ids()) {
            if (id == ApiProvider.OPENAI)
                continue;
            validations.put(id, openAiOnly.thenCompose((only) -> {
                if (!only)
                    return validate(id, usable);
                reasons.put(new Key(id, Check.CONNECTION), new Reason("status.api.check.skipped", null));
                return CompletableFuture.completedFuture(config.accounts().isValidated(id));
            }));
        }

        return CompletableFuture.allOf(validations.values().toArray(CompletableFuture[]::new))
                .whenComplete((v, error) -> completionListeners.forEach(Runnable::run))
                .thenApply(v -> {
                    Map<ApiProvider, Boolean> results = new EnumMap<>(ApiProvider.class);
                    validations.forEach((ApiProvider id, CompletableFuture<Boolean> valid) ->
                            results.put(id, valid.join()));
                    logger.info("API provider validation completed: {}", results);
                    return results;
                });
    }

    /**
     * Validates a provider: tests its connection and, for Gemini and OpenAI, checks the brainstorm language model.
     *
     * <p>Note: This method only updates the validation status. The enabled status is only auto-set on a successful
     * validation while the user never switched the provider on or off and no other provider is usable (see
     * {@link #autoEnables}); otherwise it is controlled by user preference and never overridden.</p>
     *
     * @param provider the provider, one of the registry
     * @return a CompletableFuture that resolves to true if validation succeeded
     */
    public CompletableFuture<Boolean> validate(ApiProvider provider) {
        return validate(provider, usableProviders());
    }

    private CompletableFuture<Boolean> validate(ApiProvider provider, Set<ApiProvider> usable) {
        return validateConnection(provider, usable, noKey.getOrDefault(provider, () -> { }),
                extras.getOrDefault(provider, CompletableFuture::completedFuture));
    }

    /*  the providers enabled with a key, taken before any test of a run, so the order of the tests decides nothing  */
    private Set<ApiProvider> usableProviders() {
        Set<ApiProvider> usable = EnumSet.noneOf(ApiProvider.class);
        for (ApiProvider id : providers.ids()) {
            if (config.accounts().isUsable(id))
                usable.add(id);
        }
        return usable;
    }

    /**
     * Tells whether a successful connection test enables a provider: only if the user never switched it on or off
     * and no other provider was usable when the run began (e.g. a first start with a key from the environment), the
     * same for every provider. A user who works with another provider keeps this one as it is.
     *
     * @param provider          the provider
     * @param hasUserPreference true if the user switched the provider on or off
     * @param usable            the providers usable when the run began
     * @return true if the provider is to be enabled
     */
    static boolean autoEnables(ApiProvider provider, boolean hasUserPreference, Set<ApiProvider> usable) {
        return !hasUserPreference && usable.stream().allMatch(provider::equals);
    }

    /*  Gemini after a decided connection test: its brainstorm language model; without a connection, language model
        and search stay as last decided (no brainstorm anyway), and a disabled provider keeps them too, so no paid
        check runs for an account switched off  */
    private CompletableFuture<Boolean> checkGeminiAfterConnection(boolean isValid) {
        if (!isValid || !config.accounts().isEnabled(ApiProvider.GOOGLE_GEMINI))
            return CompletableFuture.completedFuture(isValid);
        return checkGeminiModel().thenApply((check) -> true);
    }

    /**
     * Checks the brainstorm language model of Gemini and its Google Search, both at once, and stores the decided
     * results as flags as soon as the checks end, with the reasons in memory; an undecided check keeps its flag.
     *
     * @return the outcomes of the checks
     * @since 2.1.2
     */
    public CompletableFuture<ModelCheck> checkGeminiModel() {
        return checkGeminiModel(null);
    }

    /**
     * Checks the brainstorm language model of Gemini like {@link #checkGeminiModel()}, with a key the settings dialog
     * validated but did not save yet.
     *
     * @param apiKey the key, null for the saved one
     * @return the outcomes of the checks
     * @since 2.1.3
     */
    public CompletableFuture<ModelCheck> checkGeminiModel(String apiKey) {
        GeminiApiService saved = providers.get(ApiProvider.GOOGLE_GEMINI, GeminiApiService.class);
        GeminiApiService gemini = apiKey != null ? saved.withCredentials(apiKey, null) : saved;
        return gemini.checkChatModel().handle(Outcome::of)
                .thenCombine(gemini.checkWebSearch().handle(Outcome::of), ModelCheck::new)
                .thenApply((ModelCheck check) -> {
                    storeModelCheck(ApiProvider.GOOGLE_GEMINI, check);
                    completionListeners.forEach(Runnable::run);
                    return check;
                });
    }

    /*  OpenAI after a decided connection test, which listed its models and so refreshed the model list: the chosen
        brainstorm language model; without a connection, or for a disabled provider, the last decided results stay  */
    private CompletableFuture<Boolean> checkOpenAiAfterConnection(boolean isValid) {
        String model = config.getOpenAiChatModel();
        if (!isValid || !config.accounts().isEnabled(ApiProvider.OPENAI))
            return CompletableFuture.completedFuture(isValid);
        if (!model.isBlank())
            return checkOpenAiModel(model).thenApply((check) -> true);

        /*  No chosen model: no brainstorm  */
        clearModelChecks(ApiProvider.OPENAI);
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Tests the connection of a provider and stores the decided result; the enabled status is only auto-set as
     * {@link #autoEnables} tells. A transient failure keeps the last decided state.
     *
     * @param provider the provider
     * @param usable   the providers usable when the run began
     * @param noKey    clears what depends on the account if no key is configured
     * @param then     the checks after a decided connection test, given its result
     * @return a future with true if the connection works
     */
    private CompletableFuture<Boolean> validateConnection(ApiProvider provider, Set<ApiProvider> usable,
            Runnable noKey, Function<Boolean, CompletableFuture<Boolean>> then) {
        String name = provider.getDisplayName();
        if (!config.accounts().hasApiKey(provider)) {
            logger.info("{} API key not configured, skipping validation", name);
            config.accounts().setValidated(provider, false);
            setReason(provider, Check.CONNECTION, null);
            noKey.run();
            // Don't change enabled status - respect user preference
            return CompletableFuture.completedFuture(false);
        }

        logger.info("Validating {} API key...", name);
        boolean autoEnable = autoEnables(provider, config.accounts().hasEnabledPreference(provider), usable);

        return providers.get(provider).testConnection()
                .thenApply(isValid -> {
                    storeConnection(provider, isValid);

                    if (isValid && autoEnable) {
                        config.accounts().setEnabled(provider, true);
                        logger.info("{} API validation successful - provider auto-enabled (no other provider)", name);
                    } else if (isValid) {
                        logger.info("{} API validation successful - provider status: {} (user preference)", name,
                                config.accounts().isEnabled(provider) ? "enabled" : "disabled");
                    } else {
                        logger.warn("{} API validation failed - provider status: {}", name,
                                config.accounts().isEnabled(provider) ? "enabled (invalid key)" : "disabled");
                    }

                    return isValid;
                })
                .thenCompose(then)
                .exceptionally(ex -> connectionKept(provider, ex));
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
        return checkOpenAiModel(model, null, null);
    }

    /**
     * Checks an OpenAI language model like {@link #checkOpenAiModel(String)}, with a token and base URL the settings
     * dialog validated but did not save yet.
     *
     * @param model   the model id
     * @param apiKey  the token, null for the saved one (and the saved base URL)
     * @param baseUrl the base URL, null for the saved one
     * @return the outcomes of the checks
     * @since 2.1.3
     */
    public CompletableFuture<ModelCheck> checkOpenAiModel(String model, String apiKey, String baseUrl) {
        OpenAiApiService saved = providers.get(ApiProvider.OPENAI, OpenAiApiService.class);
        OpenAiApiService openAi = apiKey != null ? saved.withCredentials(apiKey, baseUrl) : saved;
        return openAi.checkChatModel(model).handle(Outcome::of)
                .thenCombine(openAi.checkWebSearch(model).handle(Outcome::of), ModelCheck::new)
                .thenApply((ModelCheck check) -> {
                    boolean chosen;
                    synchronized (this) {
                        lastOpenAiChecks.put(model, check);
                        chosen = model.equals(config.getOpenAiChatModel());
                        if (chosen)
                            storeModelCheck(ApiProvider.OPENAI, check);
                    }

                    /*  told outside the monitor, so a listener that asks the validator waits for nothing  */
                    if (chosen)
                        completionListeners.forEach(Runnable::run);
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
    public void storeOpenAiModelCheck(String model) {
        synchronized (this) {
            clearModelChecks(ApiProvider.OPENAI);
            ModelCheck check = lastOpenAiChecks.get(model);
            if (!model.isBlank() && check != null)
                storeModelCheck(ApiProvider.OPENAI, check);
        }
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
        config.accounts().setValidated(provider, works);
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
    private void storeModelCheck(ApiProvider provider, ModelCheck check) {
        storeOutcome(provider, Check.LANGUAGE_MODEL, check.chat(), (ok) -> config.accounts().setChatOk(provider, ok));
        storeOutcome(provider, Check.WEB_SEARCH, check.search(), (ok) -> config.accounts().setWebSearch(provider, ok));
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
    private void clearModelChecks(ApiProvider provider) {
        config.accounts().setChatOk(provider, false);
        config.accounts().setWebSearch(provider, false);
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
     * Removes a listener, for example that of a window that closes.
     *
     * @param listener the listener
     */
    public void removeCompletionListener(Runnable listener) {
        completionListeners.remove(listener);
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
        return config.accounts().isValidated(provider);
    }
}
