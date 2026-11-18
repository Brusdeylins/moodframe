package art.moodframe.service.provider;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ProviderAccounts;
import art.moodframe.config.ConfigMocks;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationModel;
import art.moodframe.util.I18nManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private ProviderAccounts accounts;
    private BflApiService bfl;
    private GeminiApiService gemini;
    private OpenAiApiService openAi;
    private ApiProviderValidator validator;

    @BeforeEach
    void setUp() {
        config = ConfigMocks.config();
        accounts = config.accounts();
        when(accounts.isValidated(any())).thenAnswer((invocation) -> flag(invocation.getArgument(0), "validated"));
        doAnswer((invocation) -> setFlag(invocation, "validated")).when(accounts).setValidated(any(), anyBoolean());
        when(accounts.isEnabled(any())).thenAnswer((invocation) -> flag(invocation.getArgument(0), "enabled"));
        doAnswer((invocation) -> setFlag(invocation, "enabled")).when(accounts).setEnabled(any(), anyBoolean());
        when(accounts.isChatOk(any())).thenAnswer((invocation) -> flag(invocation.getArgument(0), "chat_ok"));
        doAnswer((invocation) -> setFlag(invocation, "chat_ok")).when(accounts).setChatOk(any(), anyBoolean());
        when(accounts.isWebSearch(any())).thenAnswer((invocation) -> flag(invocation.getArgument(0), "web_search"));
        doAnswer((invocation) -> setFlag(invocation, "web_search")).when(accounts).setWebSearch(any(), anyBoolean());
        doCallRealMethod().when(accounts).isOpenAiChatAvailable();
        doCallRealMethod().when(accounts).isBrainstormAvailable(any());

        /*  Every account configured and enabled by the user, the brainstorm talks to Gemini  */
        flags.put("bfl.enabled", true);
        flags.put("gemini.enabled", true);
        flags.put("openai.enabled", true);
        when(accounts.hasApiKey(ApiProvider.BFL)).thenReturn(true);
        when(accounts.hasApiKey(ApiProvider.GOOGLE_GEMINI)).thenReturn(true);
        when(accounts.hasApiKey(ApiProvider.OPENAI)).thenReturn(true);
        when(accounts.hasEnabledPreference(ApiProvider.BFL)).thenReturn(true);
        when(accounts.hasEnabledPreference(ApiProvider.GOOGLE_GEMINI)).thenReturn(true);
        when(accounts.hasEnabledPreference(ApiProvider.OPENAI)).thenReturn(true);
        when(accounts.getOpenAiChatModel()).thenReturn("gpt-x");
        when(config.getDefaultModel()).thenReturn("flux2-pro");
        when(accounts.getBrainstormEngine()).thenReturn("gemini");

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

    /*  the flags of the configuration, kept in the map like in the file: "openai.web_search" and so on  */
    private static String key(ApiProvider provider, String name) {
        return switch (provider) {
            case BFL -> "bfl";
            case GOOGLE_GEMINI -> "gemini";
            case OPENAI -> "openai";
        } + "." + name;
    }

    private boolean flag(ApiProvider provider, String name) {
        return flags.getOrDefault(key(provider, name), false);
    }

    private Object setFlag(InvocationOnMock invocation, String name) {
        return flags.put(key(invocation.getArgument(0), name), invocation.getArgument(1));
    }

    @Test
    void accountsWithConnectionLanguageModelAndWebSearchCanBrainstorm() {
        validator.validateAllProviders().join();

        assertTrue(accounts.isBrainstormAvailable(ApiProvider.OPENAI));
        assertTrue(accounts.isBrainstormAvailable(ApiProvider.GOOGLE_GEMINI));
        assertNull(validator.getReason(ApiProvider.OPENAI, ApiProviderValidator.Check.WEB_SEARCH));
    }

    @Test
    void aFailingOpenAiSearchTakesTheBrainstormButKeepsTheChoicesOfTheUser() {
        when(openAi.checkWebSearch("gpt-x")).thenReturn(CompletableFuture.completedFuture("OpenAI error 404"));

        validator.validateAllProviders().join();

        assertFalse(accounts.isBrainstormAvailable(ApiProvider.OPENAI));
        assertTrue(accounts.isChatOk(ApiProvider.OPENAI));
        assertEquals("OpenAI error 404", validator.getReason(ApiProvider.OPENAI, ApiProviderValidator.Check.WEB_SEARCH));
        assertTrue(accounts.isEnabled(ApiProvider.OPENAI));
        verify(accounts, never()).setEnabled(eq(ApiProvider.OPENAI), anyBoolean());
        verify(accounts, never()).setOpenAiChatModel(anyString());
    }

    @Test
    void aFailingGeminiSearchTakesTheBrainstormButKeepsTheChoicesOfTheUser() {
        when(gemini.checkWebSearch()).thenReturn(CompletableFuture.completedFuture("The model did not search the web."));

        validator.validateAllProviders().join();

        assertFalse(accounts.isBrainstormAvailable(ApiProvider.GOOGLE_GEMINI));
        assertTrue(accounts.isChatOk(ApiProvider.GOOGLE_GEMINI));
        assertEquals("The model did not search the web.",
                validator.getReason(ApiProvider.GOOGLE_GEMINI, ApiProviderValidator.Check.WEB_SEARCH));
        assertTrue(accounts.isEnabled(ApiProvider.GOOGLE_GEMINI));
        verify(accounts, never()).setEnabled(eq(ApiProvider.GOOGLE_GEMINI), anyBoolean());
    }

    @Test
    void bflGetsItsConnectionCheckedOnly() {
        validator.validateAllProviders().join();

        verify(bfl).testConnection();
        verifyNoMoreInteractions(bfl);
        assertTrue(accounts.isValidated(ApiProvider.BFL));
    }

    @Test
    void withoutConnectionNothingMoreIsCheckedAndTheLastDecidedResultsStay() {
        when(gemini.testConnection()).thenReturn(CompletableFuture.completedFuture(false));
        flags.put("gemini.chat_ok", true);
        flags.put("gemini.web_search", true);

        validator.validateAllProviders().join();

        verify(gemini, never()).checkChatModel();
        verify(gemini, never()).checkWebSearch();
        assertTrue(accounts.isChatOk(ApiProvider.GOOGLE_GEMINI),
                "no connection decides nothing about the language model");
        assertTrue(accounts.isWebSearch(ApiProvider.GOOGLE_GEMINI), "no connection decides nothing about the search");
        assertFalse(accounts.isBrainstormAvailable(ApiProvider.GOOGLE_GEMINI), "without connection no brainstorm");
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
        assertTrue(accounts.isWebSearch(ApiProvider.GOOGLE_GEMINI), "a disabled provider keeps its last result");
        assertTrue(accounts.isWebSearch(ApiProvider.OPENAI), "a disabled provider keeps its last result");
    }

    @Test
    void withoutChosenModelOpenAiIsNotCheckedAndCannotBrainstorm() {
        when(config.getOpenAiChatModel()).thenReturn("");
        flags.put("openai.chat_ok", true);
        flags.put("openai.web_search", true);

        validator.validateAllProviders().join();

        verify(openAi, never()).checkChatModel(anyString());
        verify(openAi, never()).checkWebSearch(anyString());
        assertFalse(accounts.isChatOk(ApiProvider.OPENAI), "a result of an earlier model does not stay");
        assertFalse(accounts.isWebSearch(ApiProvider.OPENAI), "a result of an earlier model does not stay");
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

            assertTrue(accounts.isWebSearch(ApiProvider.OPENAI), failure.getMessage());
            assertTrue(accounts.isBrainstormAvailable(ApiProvider.OPENAI), failure.getMessage());
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

            assertFalse(accounts.isWebSearch(ApiProvider.OPENAI), problem);
            assertFalse(accounts.isBrainstormAvailable(ApiProvider.OPENAI), problem);
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

        assertTrue(accounts.isChatOk(ApiProvider.GOOGLE_GEMINI), "the language model keeps its last result as well");
        assertFalse(accounts.isWebSearch(ApiProvider.GOOGLE_GEMINI));
        assertFalse(accounts.isBrainstormAvailable(ApiProvider.GOOGLE_GEMINI), "the brainstorm stays off");
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
        assertTrue(accounts.isChatOk(ApiProvider.OPENAI));
        assertTrue(accounts.isWebSearch(ApiProvider.OPENAI));
    }

    @Test
    void aCheckOfAModelThatIsNotChosenStoresNothing() {
        when(openAi.checkChatModel("gpt-y")).thenReturn(CompletableFuture.completedFuture(null));
        when(openAi.checkWebSearch("gpt-y")).thenReturn(CompletableFuture.completedFuture("OpenAI error 404"));
        flags.put("openai.chat_ok", true);
        flags.put("openai.web_search", true);

        ApiProviderValidator.ModelCheck check = validator.checkOpenAiModel("gpt-y").join();

        assertFalse(check.search().works());
        assertTrue(accounts.isWebSearch(ApiProvider.OPENAI), "the flags belong to the chosen model gpt-x");
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

            assertTrue(accounts.isValidated(ApiProvider.OPENAI), failure.getMessage());
            assertTrue(accounts.isBrainstormAvailable(ApiProvider.OPENAI), failure.getMessage());
            assertTrue(accounts.isValidated(ApiProvider.GOOGLE_GEMINI), failure.getMessage());
            assertTrue(accounts.isBrainstormAvailable(ApiProvider.GOOGLE_GEMINI), failure.getMessage());
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
        assertFalse(accounts.isWebSearch(ApiProvider.OPENAI));

        /*  "Save" then takes the finished check of gpt-y from the validator  */
        when(config.getOpenAiChatModel()).thenReturn("gpt-y");
        validator.storeOpenAiModelCheck("gpt-y");

        assertTrue(accounts.isChatOk(ApiProvider.OPENAI));
        assertTrue(accounts.isWebSearch(ApiProvider.OPENAI));
    }

    @Test
    void withOpenAiAsTheOnlyProviderGoogleAndBflAreNotChecked() {
        when(config.getDefaultModel()).thenReturn(GenerationModel.OPENAI_PREFIX + "gpt-image-1");
        when(accounts.getBrainstormEngine()).thenReturn("openai");
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
        when(accounts.getBrainstormEngine()).thenReturn("openai");
        List.of("validated", "chat_ok", "web_search").forEach((flag) -> flags.put("openai." + flag, true));
        when(openAi.checkWebSearch("gpt-x")).thenReturn(CompletableFuture.completedFuture("OpenAI error 404"));

        validator.validateAllProviders().join();

        verify(gemini).checkWebSearch();
        assertTrue(accounts.isBrainstormAvailable(ApiProvider.GOOGLE_GEMINI), "Gemini is checked and offered instead");
    }

    @Test
    void theFreshOpenAiCheckDecidesIfGoogleIsContacted() {
        when(config.getDefaultModel()).thenReturn(GenerationModel.OPENAI_PREFIX + "gpt-image-1");
        when(accounts.getBrainstormEngine()).thenReturn("openai");

        /*  The first start after the update: no stored web search yet, the check of this start finds it  */
        flags.put("openai.validated", true);

        validator.validateAllProviders().join();

        assertTrue(accounts.isBrainstormAvailable(ApiProvider.OPENAI));
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

        assertTrue(accounts.isWebSearch(ApiProvider.OPENAI));
    }

    @Test
    void aRefusedConnectionNamesItsReasonAndASuccessfulTestDropsAnOldOne() {
        when(openAi.testConnection()).thenReturn(CompletableFuture.completedFuture(false));
        validator.validateAllProviders().join();
        assertEquals(I18nManager.getText("status.api.check.refused", (Object) null),
                validator.getReason(ApiProvider.OPENAI, ApiProviderValidator.Check.CONNECTION));

        /*  A successful test in the settings  */
        validator.connectionTested(ApiProvider.OPENAI, true);

        assertTrue(accounts.isValidated(ApiProvider.OPENAI));
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

        assertFalse(accounts.isValidated(ApiProvider.OPENAI));
        assertFalse(accounts.isBrainstormAvailable(ApiProvider.OPENAI));
        assertFalse(accounts.isValidated(ApiProvider.GOOGLE_GEMINI));
        assertFalse(accounts.isBrainstormAvailable(ApiProvider.GOOGLE_GEMINI));
    }

    @Test
    void aLateModelCheckTellsTheListenersAfterStoringItsFlags() {
        List<String> seen = new CopyOnWriteArrayList<>();
        validator.addCompletionListener(() -> seen.add(accounts.isWebSearch(ApiProvider.OPENAI) + "/"
                + accounts.isWebSearch(ApiProvider.GOOGLE_GEMINI)));

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

        assertTrue(accounts.isBrainstormAvailable(ApiProvider.OPENAI));
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

        assertFalse(accounts.isChatOk(ApiProvider.OPENAI));
        assertFalse(accounts.isWebSearch(ApiProvider.OPENAI));
        assertNull(validator.getReason(ApiProvider.OPENAI, ApiProviderValidator.Check.LANGUAGE_MODEL));
        assertNull(validator.getReason(ApiProvider.OPENAI, ApiProviderValidator.Check.WEB_SEARCH));
    }
}
