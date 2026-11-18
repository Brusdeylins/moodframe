package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationModel;
import art.moodframe.service.provider.MockProviders;
import art.moodframe.util.I18nManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Checks the check of the accounts at the program start: connection, language model and web search. */
class ApiProviderValidatorTest {

    private final Map<String, Boolean> flags = new HashMap<>();
    private ApplicationConfig config;
    private BflApiService bfl;
    private GeminiApiService gemini;
    private OpenAiApiService openAi;
    private ApiProviderValidator validator;

    @BeforeEach
    void setUp() {
        config = mock(ApplicationConfig.class);
        flag("bfl.validated", ApplicationConfig::isBflProviderValidated, ApplicationConfig::setBflProviderValidated);
        flag("bfl.enabled", ApplicationConfig::isBflProviderEnabled, ApplicationConfig::setBflProviderEnabled);
        flag("gemini.validated", ApplicationConfig::isGeminiProviderValidated, ApplicationConfig::setGeminiProviderValidated);
        flag("gemini.enabled", ApplicationConfig::isGeminiProviderEnabled, ApplicationConfig::setGeminiProviderEnabled);
        flag("gemini.chat_ok", ApplicationConfig::isGeminiChatOk, ApplicationConfig::setGeminiChatOk);
        flag("gemini.web_search", ApplicationConfig::isGeminiWebSearch, ApplicationConfig::setGeminiWebSearch);
        flag("openai.validated", ApplicationConfig::isOpenAiProviderValidated, ApplicationConfig::setOpenAiProviderValidated);
        flag("openai.enabled", ApplicationConfig::isOpenAiProviderEnabled, ApplicationConfig::setOpenAiProviderEnabled);
        flag("openai.chat_ok", ApplicationConfig::isOpenAiChatOk, ApplicationConfig::setOpenAiChatOk);
        flag("openai.web_search", ApplicationConfig::isOpenAiWebSearch, ApplicationConfig::setOpenAiWebSearch);
        doCallRealMethod().when(config).isOpenAiChatAvailable();
        doCallRealMethod().when(config).isOpenAiBrainstormAvailable();
        doCallRealMethod().when(config).isGeminiBrainstormAvailable();
        doCallRealMethod().when(config).hasApiKey(any(ApiProvider.class));
        doCallRealMethod().when(config).isProviderValidated(any());
        doCallRealMethod().when(config).setProviderValidated(any(), anyBoolean());
        doCallRealMethod().when(config).isProviderEnabled(any());
        doCallRealMethod().when(config).setProviderEnabled(any(), anyBoolean());
        doCallRealMethod().when(config).hasProviderEnabledPreference(any());

        /*  Every account configured and enabled by the user, the brainstorm talks to Gemini  */
        flags.put("bfl.enabled", true);
        flags.put("gemini.enabled", true);
        flags.put("openai.enabled", true);
        when(config.hasApiKey()).thenReturn(true);
        when(config.hasGeminiApiKey()).thenReturn(true);
        when(config.hasOpenAiApiKey()).thenReturn(true);
        when(config.hasBflProviderEnabledPreference()).thenReturn(true);
        when(config.hasGeminiProviderEnabledPreference()).thenReturn(true);
        when(config.hasOpenAiProviderEnabledPreference()).thenReturn(true);
        when(config.getOpenAiChatModel()).thenReturn("gpt-x");
        when(config.getDefaultModel()).thenReturn("flux2-pro");
        when(config.getBrainstormEngine()).thenReturn("gemini");

        bfl = mock(BflApiService.class);
        gemini = mock(GeminiApiService.class);
        openAi = mock(OpenAiApiService.class);
        when(bfl.testConnection()).thenReturn(CompletableFuture.completedFuture(true));
        when(gemini.testConnection()).thenReturn(CompletableFuture.completedFuture(true));
        when(gemini.checkChatModel()).thenReturn(CompletableFuture.completedFuture(null));
        when(gemini.checkWebSearch()).thenReturn(CompletableFuture.completedFuture(null));
        when(openAi.testConnection()).thenReturn(CompletableFuture.completedFuture(true));
        when(openAi.checkChatModel("gpt-x")).thenReturn(CompletableFuture.completedFuture(null));
        when(openAi.checkWebSearch("gpt-x")).thenReturn(CompletableFuture.completedFuture(null));
        validator = new ApiProviderValidator(config, MockProviders.registry(bfl, gemini, openAi));
    }

    /*  a flag of the configuration, kept in the map like in the file  */
    private void flag(String key, Function<ApplicationConfig, Boolean> getter,
            BiConsumer<ApplicationConfig, Boolean> setter) {
        when(getter.apply(config)).thenAnswer((invocation) -> flags.getOrDefault(key, false));
        setter.accept(doAnswer((invocation) -> flags.put(key, invocation.getArgument(0))).when(config), anyBoolean());
    }

    @Test
    void accountsWithConnectionLanguageModelAndWebSearchCanBrainstorm() {
        validator.validateAllProviders().join();

        assertTrue(config.isOpenAiBrainstormAvailable());
        assertTrue(config.isGeminiBrainstormAvailable());
        assertNull(validator.getReason(ApiProvider.OPENAI, ApiProviderValidator.Check.WEB_SEARCH));
    }

    @Test
    void aFailingOpenAiSearchTakesTheBrainstormButKeepsTheChoicesOfTheUser() {
        when(openAi.checkWebSearch("gpt-x")).thenReturn(CompletableFuture.completedFuture("OpenAI error 404"));

        validator.validateAllProviders().join();

        assertFalse(config.isOpenAiBrainstormAvailable());
        assertTrue(config.isOpenAiChatOk());
        assertEquals("OpenAI error 404", validator.getReason(ApiProvider.OPENAI, ApiProviderValidator.Check.WEB_SEARCH));
        assertTrue(config.isOpenAiProviderEnabled());
        verify(config, never()).setOpenAiProviderEnabled(anyBoolean());
        verify(config, never()).setOpenAiChatModel(anyString());
    }

    @Test
    void aFailingGeminiSearchTakesTheBrainstormButKeepsTheChoicesOfTheUser() {
        when(gemini.checkWebSearch()).thenReturn(CompletableFuture.completedFuture("The model did not search the web."));

        validator.validateAllProviders().join();

        assertFalse(config.isGeminiBrainstormAvailable());
        assertTrue(config.isGeminiChatOk());
        assertEquals("The model did not search the web.",
                validator.getReason(ApiProvider.GOOGLE_GEMINI, ApiProviderValidator.Check.WEB_SEARCH));
        assertTrue(config.isGeminiProviderEnabled());
        verify(config, never()).setGeminiProviderEnabled(anyBoolean());
    }

    @Test
    void bflGetsItsConnectionCheckedOnly() {
        validator.validateAllProviders().join();

        verify(bfl).testConnection();
        verifyNoMoreInteractions(bfl);
        assertTrue(config.isBflProviderValidated());
    }

    @Test
    void withoutConnectionNothingMoreIsCheckedAndTheLastDecidedResultsStay() {
        when(gemini.testConnection()).thenReturn(CompletableFuture.completedFuture(false));
        flags.put("gemini.chat_ok", true);
        flags.put("gemini.web_search", true);

        validator.validateAllProviders().join();

        verify(gemini, never()).checkChatModel();
        verify(gemini, never()).checkWebSearch();
        assertTrue(config.isGeminiChatOk(), "no connection decides nothing about the language model");
        assertTrue(config.isGeminiWebSearch(), "no connection decides nothing about the search");
        assertFalse(config.isGeminiBrainstormAvailable(), "without connection no brainstorm");
    }

    @Test
    void aDisabledProviderGetsNoPaidCheckAndKeepsItsResults() {
        flags.put("gemini.enabled", false);
        flags.put("openai.enabled", false);
        flags.put("gemini.web_search", true);
        flags.put("openai.web_search", true);

        validator.validateAllProviders().join();

        verify(gemini).testConnection();
        verify(openAi).testConnection();
        verify(gemini, never()).checkChatModel();
        verify(gemini, never()).checkWebSearch();
        verify(openAi, never()).checkChatModel(anyString());
        verify(openAi, never()).checkWebSearch(anyString());
        assertTrue(config.isGeminiWebSearch(), "a disabled provider keeps its last result");
        assertTrue(config.isOpenAiWebSearch(), "a disabled provider keeps its last result");
    }

    @Test
    void withoutChosenModelOpenAiIsNotCheckedAndCannotBrainstorm() {
        when(config.getOpenAiChatModel()).thenReturn("");
        flags.put("openai.chat_ok", true);
        flags.put("openai.web_search", true);

        validator.validateAllProviders().join();

        verify(openAi, never()).checkChatModel(anyString());
        verify(openAi, never()).checkWebSearch(anyString());
        assertFalse(config.isOpenAiChatOk(), "a result of an earlier model does not stay");
        assertFalse(config.isOpenAiWebSearch(), "a result of an earlier model does not stay");
    }

    @Test
    void aTransientFailureKeepsAWorkingSearch() {
        for (Throwable failure : List.of(
                new RuntimeException("OpenAI web search check failed: timeout", new SocketTimeoutException("timeout")),
                new ApiHttpException(429, "OpenAI: rate limit or quota exceeded (429)"),
                new ApiHttpException(503, "OpenAI error 503"))) {
            flags.put("openai.web_search", true);
            when(openAi.checkWebSearch("gpt-x")).thenReturn(CompletableFuture.failedFuture(failure));

            validator.validateAllProviders().join();

            assertTrue(config.isOpenAiWebSearch(), failure.getMessage());
            assertTrue(config.isOpenAiBrainstormAvailable(), failure.getMessage());
            assertEquals(I18nManager.getText("status.api.check.kept", failure.getMessage()),
                    validator.getReason(ApiProvider.OPENAI, ApiProviderValidator.Check.WEB_SEARCH));
        }
    }

    @Test
    void aDefiniteAnswerTurnsAWorkingSearchOff() {
        for (String problem : List.of("The model did not search the web.", "OpenAI error 404")) {
            flags.put("openai.web_search", true);
            when(openAi.checkWebSearch("gpt-x")).thenReturn(CompletableFuture.completedFuture(problem));

            validator.validateAllProviders().join();

            assertFalse(config.isOpenAiWebSearch(), problem);
            assertFalse(config.isOpenAiBrainstormAvailable(), problem);
        }
    }

    @Test
    void aTransientFailureKeepsAMissingSearchOff() {
        when(gemini.checkChatModel()).thenReturn(CompletableFuture.failedFuture(
                new ApiHttpException(503, "The AI did not respond (HTTP 503)")));
        when(gemini.checkWebSearch()).thenReturn(CompletableFuture.failedFuture(
                new ApiHttpException(429, "The AI did not respond (HTTP 429)")));
        flags.put("gemini.chat_ok", true);

        validator.validateAllProviders().join();

        assertTrue(config.isGeminiChatOk(), "the language model keeps its last result as well");
        assertFalse(config.isGeminiWebSearch());
        assertFalse(config.isGeminiBrainstormAvailable(), "the brainstorm stays off");
    }

    @Test
    void aModelCheckStoresItsResultWhenItEndsAfterTheModelWasSaved() {
        CompletableFuture<String> search = new CompletableFuture<>();
        when(openAi.checkChatModel("gpt-y")).thenReturn(CompletableFuture.completedFuture(null));
        when(openAi.checkWebSearch("gpt-y")).thenReturn(search);
        CompletableFuture<ApiProviderValidator.ModelCheck> check = validator.checkOpenAiModel("gpt-y");

        /*  "Save" stores the newly chosen model and closes the settings while the check still runs  */
        when(config.getOpenAiChatModel()).thenReturn("gpt-y");
        search.complete(null);

        assertTrue(check.join().search().works());
        assertTrue(config.isOpenAiChatOk());
        assertTrue(config.isOpenAiWebSearch());
    }

    @Test
    void aCheckOfAModelThatIsNotChosenStoresNothing() {
        when(openAi.checkChatModel("gpt-y")).thenReturn(CompletableFuture.completedFuture(null));
        when(openAi.checkWebSearch("gpt-y")).thenReturn(CompletableFuture.completedFuture("OpenAI error 404"));
        flags.put("openai.chat_ok", true);
        flags.put("openai.web_search", true);

        ApiProviderValidator.ModelCheck check = validator.checkOpenAiModel("gpt-y").join();

        assertFalse(check.search().works());
        assertTrue(config.isOpenAiWebSearch(), "the flags belong to the chosen model gpt-x");
    }

    @Test
    void tellsTheListenersWhenTheCheckEnded() {
        AtomicInteger ended = new AtomicInteger();
        validator.addCompletionListener(ended::incrementAndGet);

        validator.validateAllProviders().join();

        assertEquals(3, ended.get(), "each model check as soon as it ends, and the whole check at its end");
    }

    @Test
    void aTransientConnectionFailureKeepsTheBrainstorm() {
        for (Throwable failure : List.of(
                new UncheckedIOException(new UnknownHostException("api.example.com")),
                new ApiHttpException(429, "OpenAI: rate limit or quota exceeded (429)"),
                new ApiHttpException(503, "The AI did not respond (HTTP 503)"))) {
            List.of("validated", "chat_ok", "web_search").forEach((flag) -> {
                flags.put("openai." + flag, true);
                flags.put("gemini." + flag, true);
            });
            when(openAi.testConnection()).thenReturn(CompletableFuture.failedFuture(failure));
            when(gemini.testConnection()).thenReturn(CompletableFuture.failedFuture(failure));

            validator.validateAllProviders().join();

            assertTrue(config.isOpenAiProviderValidated(), failure.getMessage());
            assertTrue(config.isOpenAiBrainstormAvailable(), failure.getMessage());
            assertTrue(config.isGeminiProviderValidated(), failure.getMessage());
            assertTrue(config.isGeminiBrainstormAvailable(), failure.getMessage());
            assertEquals(I18nManager.getText("status.api.check.kept", failure.getMessage()),
                    validator.getReason(ApiProvider.OPENAI, ApiProviderValidator.Check.CONNECTION));
        }

        /*  No paid check runs at an account that is offline or rate limited for the moment  */
        verify(gemini, never()).checkChatModel();
        verify(gemini, never()).checkWebSearch();
        verify(openAi, never()).checkChatModel(anyString());
        verify(openAi, never()).checkWebSearch(anyString());
    }

    @Test
    void aCheckThatEndsJustBeforeSaveStillGivesItsFlags() {
        flags.put("openai.web_search", false);
        when(openAi.checkChatModel("gpt-y")).thenReturn(CompletableFuture.completedFuture(null));
        when(openAi.checkWebSearch("gpt-y")).thenReturn(CompletableFuture.completedFuture(null));

        /*  The check ends while gpt-x is still the saved model, so it stores nothing itself  */
        validator.checkOpenAiModel("gpt-y").join();
        assertFalse(config.isOpenAiWebSearch());

        /*  "Save" then takes the finished check of gpt-y from the validator  */
        when(config.getOpenAiChatModel()).thenReturn("gpt-y");
        validator.storeOpenAiModelCheck("gpt-y");

        assertTrue(config.isOpenAiChatOk());
        assertTrue(config.isOpenAiWebSearch());
    }

    @Test
    void withOpenAiAsTheOnlyProviderGoogleAndBflAreNotChecked() {
        when(config.getDefaultModel()).thenReturn(GenerationModel.OPENAI_PREFIX + "gpt-image-1");
        when(config.getBrainstormEngine()).thenReturn("openai");
        List.of("validated", "chat_ok", "web_search").forEach((flag) -> flags.put("openai." + flag, true));

        validator.validateAllProviders().join();

        verify(gemini, never()).testConnection();
        verify(bfl, never()).testConnection();
        assertEquals(I18nManager.getText("status.api.check.skipped", (Object) null),
                validator.getReason(ApiProvider.GOOGLE_GEMINI, ApiProviderValidator.Check.CONNECTION));
    }

    @Test
    void anOpenAiWithoutWebSearchLeavesTheBrainstormToGemini() {
        when(config.getDefaultModel()).thenReturn(GenerationModel.OPENAI_PREFIX + "gpt-image-1");
        when(config.getBrainstormEngine()).thenReturn("openai");
        List.of("validated", "chat_ok", "web_search").forEach((flag) -> flags.put("openai." + flag, true));
        when(openAi.checkWebSearch("gpt-x")).thenReturn(CompletableFuture.completedFuture("OpenAI error 404"));

        validator.validateAllProviders().join();

        verify(gemini).checkWebSearch();
        assertTrue(config.isGeminiBrainstormAvailable(), "Gemini is checked and offered instead");
    }

    @Test
    void theFreshOpenAiCheckDecidesIfGoogleIsContacted() {
        when(config.getDefaultModel()).thenReturn(GenerationModel.OPENAI_PREFIX + "gpt-image-1");
        when(config.getBrainstormEngine()).thenReturn("openai");

        /*  The first start after the update: no stored web search yet, the check of this start finds it  */
        flags.put("openai.validated", true);

        validator.validateAllProviders().join();

        assertTrue(config.isOpenAiBrainstormAvailable());
        verify(gemini, never()).testConnection();
        verify(bfl, never()).testConnection();
    }

    @Test
    void aSaveTakesTheCheckOfItsOwnModelAlsoAfterALaterCheckOfAnother() {
        when(openAi.checkChatModel("gpt-y")).thenReturn(CompletableFuture.completedFuture(null));
        when(openAi.checkWebSearch("gpt-y")).thenReturn(CompletableFuture.completedFuture(null));
        when(openAi.checkWebSearch("gpt-x")).thenReturn(CompletableFuture.completedFuture("OpenAI error 404"));
        when(config.getOpenAiChatModel()).thenReturn("gpt-z");

        /*  gpt-y is chosen in the settings and passes, then the start check of the former model ends  */
        validator.checkOpenAiModel("gpt-y").join();
        validator.checkOpenAiModel("gpt-x").join();
        when(config.getOpenAiChatModel()).thenReturn("gpt-y");
        validator.storeOpenAiModelCheck("gpt-y");

        assertTrue(config.isOpenAiWebSearch());
    }

    @Test
    void aRefusedConnectionNamesItsReasonAndASuccessfulTestDropsAnOldOne() {
        when(openAi.testConnection()).thenReturn(CompletableFuture.completedFuture(false));
        validator.validateAllProviders().join();
        assertEquals(I18nManager.getText("status.api.check.refused", (Object) null),
                validator.getReason(ApiProvider.OPENAI, ApiProviderValidator.Check.CONNECTION));

        /*  A successful test in the settings  */
        validator.connectionTested(ApiProvider.OPENAI, true);

        assertTrue(config.isOpenAiProviderValidated());
        assertNull(validator.getReason(ApiProvider.OPENAI, ApiProviderValidator.Check.CONNECTION));
    }

    @Test
    void aDefiniteConnectionRefusalTakesTheBrainstorm() {
        flags.put("openai.validated", true);
        flags.put("gemini.validated", true);

        /*  The services answer a definite HTTP 401 with false  */
        when(openAi.testConnection()).thenReturn(CompletableFuture.completedFuture(false));
        when(gemini.testConnection()).thenReturn(CompletableFuture.completedFuture(false));

        validator.validateAllProviders().join();

        assertFalse(config.isOpenAiProviderValidated());
        assertFalse(config.isOpenAiBrainstormAvailable());
        assertFalse(config.isGeminiProviderValidated());
        assertFalse(config.isGeminiBrainstormAvailable());
    }

    @Test
    void aLateModelCheckTellsTheListenersAfterStoringItsFlags() {
        List<String> seen = new CopyOnWriteArrayList<>();
        validator.addCompletionListener(() -> seen.add(config.isOpenAiWebSearch() + "/" + config.isGeminiWebSearch()));

        validator.checkOpenAiModel("gpt-x").join();
        validator.checkGeminiModel().join();

        assertEquals(List.of("true/false", "true/true"), seen, "OpenAI/Gemini web search seen by the listeners");
    }

    @Test
    void savingANewlyChosenModelReplacesTheFlagsAndReasonsOfTheFormerModel() {
        when(openAi.checkWebSearch("gpt-x")).thenReturn(CompletableFuture.completedFuture("OpenAI error 404"));
        validator.validateAllProviders().join();
        when(openAi.checkChatModel("gpt-y")).thenReturn(CompletableFuture.completedFuture(null));
        when(openAi.checkWebSearch("gpt-y")).thenReturn(CompletableFuture.completedFuture(null));
        validator.checkOpenAiModel("gpt-y").join();

        /*  "Save" stores the newly chosen model with its finished check  */
        when(config.getOpenAiChatModel()).thenReturn("gpt-y");
        validator.storeOpenAiModelCheck("gpt-y");

        assertTrue(config.isOpenAiBrainstormAvailable());
        assertNull(validator.getReason(ApiProvider.OPENAI, ApiProviderValidator.Check.WEB_SEARCH),
                "the reason of the former model does not stay");
    }

    @Test
    void choosingNoModelClearsTheFlagsAndReasons() {
        when(openAi.checkWebSearch("gpt-x")).thenReturn(CompletableFuture.completedFuture("OpenAI error 404"));
        validator.validateAllProviders().join();

        /*  "Save" with "(none)"  */
        when(config.getOpenAiChatModel()).thenReturn("");
        validator.storeOpenAiModelCheck("");

        assertFalse(config.isOpenAiChatOk());
        assertFalse(config.isOpenAiWebSearch());
        assertNull(validator.getReason(ApiProvider.OPENAI, ApiProviderValidator.Check.LANGUAGE_MODEL));
        assertNull(validator.getReason(ApiProvider.OPENAI, ApiProviderValidator.Check.WEB_SEARCH));
    }
}
