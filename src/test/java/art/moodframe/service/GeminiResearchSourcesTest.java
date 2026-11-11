package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ResearchNote;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/** Checks how the sources of a researched answer are read, based on the shapes the live API returned. */
class GeminiResearchSourcesTest {

    private static GeminiApiServiceImpl service;
    private static Method extract;

    @BeforeAll
    static void setUp() throws Exception {
        ApplicationConfig config = mock(ApplicationConfig.class, invocation ->
                invocation.getMethod().getReturnType() == String.class ? "" : org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation));
        service = new GeminiApiServiceImpl(config);
        extract = GeminiApiServiceImpl.class.getDeclaredMethod("extractSources", JSONObject.class);
        extract.setAccessible(true);
    }

    @SuppressWarnings("unchecked")
    private static List<ResearchNote.Source> sources(String candidate) throws Exception {
        return (List<ResearchNote.Source>) extract.invoke(service, new JSONObject(candidate));
    }

    @Test
    void takesTheDomainFromTheTitleOfAGroundingChunkAndKeepsTheRedirectUrl() throws Exception {
        List<ResearchNote.Source> sources = sources("""
            {"groundingMetadata": {"groundingChunks": [
              {"web": {"uri": "https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZ1", "title": "www.wikipedia.org"}},
              {"web": {"uri": "https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZ1", "title": "wikipedia.org"}},
              {"web": {"uri": "https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZ2", "title": "ai.google.dev"}}]}}
            """);

        assertEquals(2, sources.size(), "the same url is listed once");
        assertEquals("wikipedia.org", sources.get(0).domain(), "www. is removed");
        assertEquals("https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZ1", sources.get(0).url());
        assertEquals("ai.google.dev", sources.get(1).domain());
    }

    @Test
    void takesPagesReadByTheUrlContextWhenTheirRetrievalSucceeded() throws Exception {
        List<ResearchNote.Source> sources = sources("""
            {"urlContextMetadata": {"urlMetadata": [
              {"retrievedUrl": "https://ai.google.dev/gemini-api/docs/omni", "urlRetrievalStatus": "URL_RETRIEVAL_STATUS_SUCCESS"},
              {"retrievedUrl": "https://broken.example/x", "urlRetrievalStatus": "URL_RETRIEVAL_STATUS_ERROR"}]}}
            """);

        assertEquals(List.of(new ResearchNote.Source("ai.google.dev", "https://ai.google.dev/gemini-api/docs/omni")), sources);
    }

    @Test
    void findsNoSourcesInAnAnswerWithoutMetadata() throws Exception {
        assertEquals(List.of(), sources("{\"content\": {}}"));
    }
}
