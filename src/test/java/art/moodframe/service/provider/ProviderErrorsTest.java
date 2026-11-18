package art.moodframe.service.provider;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The error answers of Gemini and OpenAI become exceptions with their status and a localized message. */
class ProviderErrorsTest {

    private static String error(String message) {
        return "{\"error\": {\"code\": 400, \"message\": \"" + message + "\"}}";
    }

    @Test
    void geminiKeepsTheStatusSoTheAccountCheckCanDecide() {
        ApiHttpException auth = GeminiErrors.http(403, error("API key not valid"), GeminiErrors.Call.IMAGE);
        assertEquals(403, auth.getStatusCode());
        assertTrue(ApiHttpException.isDefinite(auth));
        assertEquals("Gemini API authentication failed. Please check your API key in the settings.",
                auth.getMessage(Locale.ENGLISH));
        assertEquals("Gemini API Authentifizierung fehlgeschlagen. Bitte überprüfen Sie Ihren API-Schlüssel in den "
                + "Einstellungen.", auth.getMessage(Locale.GERMAN));

        ApiHttpException quota = GeminiErrors.http(429, error("Quota exceeded. Try again in 30s. Details"),
                GeminiErrors.Call.IMAGE);
        assertFalse(ApiHttpException.isDefinite(quota));
        assertEquals("Gemini API quota exceeded. Try again in 30s.", quota.getMessage(Locale.ENGLISH));
    }

    @Test
    void geminiNamesTheCauseOfAnInvalidRequestPerCall() {
        assertEquals("Invalid prompt. Please check your input.", GeminiErrors.http(400,
                error("The prompt is too long"), GeminiErrors.Call.IMAGE).getMessage(Locale.ENGLISH));
        assertEquals("Invalid request: Unsupported aspect ratio", GeminiErrors.http(400,
                error("Unsupported aspect ratio. Use 16:9"), GeminiErrors.Call.IMAGE).getMessage(Locale.ENGLISH));
        assertEquals("The input image is too large", GeminiErrors.http(400,
                error("The input image is too large"), GeminiErrors.Call.OMNI).getMessage(Locale.ENGLISH));
        assertEquals("Video is not publicly accessible", GeminiErrors.http(403,
                error("The video is private"), GeminiErrors.Call.VIDEO_ANALYSIS).getMessage(Locale.ENGLISH));
        assertEquals("Gemini API server error. Please try again later.",
                GeminiErrors.http(503, "Service Unavailable", GeminiErrors.Call.IMAGE).getMessage(Locale.ENGLISH));
    }

    @Test
    void openAiHasOneMappingWithTheTextOfTheApi() {
        ApiHttpException notFound = OpenAiApiServiceImpl.httpError(404, error("No such model"));
        assertTrue(ApiHttpException.isDefinite(notFound));
        assertEquals("OpenAI error 404: No such model", notFound.getMessage(Locale.ENGLISH));
        assertEquals("OpenAI-Fehler 404: No such model", notFound.getMessage(Locale.GERMAN));
        assertEquals("OpenAI: invalid bearer token (401)",
                OpenAiApiServiceImpl.httpError(401, "").getMessage(Locale.ENGLISH));
    }

    @Test
    void theMcpClientsGetTheEnglishMessage() {
        ApiHttpException failure = OpenAiApiServiceImpl.httpError(401, "");
        CompletionException wrapped = new CompletionException(failure);
        assertEquals("OpenAI: invalid bearer token (401)", ProviderException.messageOf(wrapped, true));
        assertEquals(failure.getMessage(), ProviderException.messageOf(wrapped, false));
        assertEquals("plain", ProviderException.messageOf(new IllegalStateException("plain"), true));
    }
}
