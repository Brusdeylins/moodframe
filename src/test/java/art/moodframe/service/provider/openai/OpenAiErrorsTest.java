package art.moodframe.service.provider.openai;

import art.moodframe.service.provider.ApiHttpException;
import art.moodframe.service.provider.ProviderException;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The error answers of OpenAI become exceptions with their status and a localized message. */
class OpenAiErrorsTest {

    private static String error(String message) {
        return "{\"error\": {\"code\": 400, \"message\": \"" + message + "\"}}";
    }

    @Test
    void openAiHasOneMappingWithTheTextOfTheApi() {
        ApiHttpException notFound = OpenAiTransport.httpError(404, error("No such model"));
        assertTrue(ApiHttpException.isDefinite(notFound));
        assertEquals("OpenAI error 404: No such model", notFound.getMessage(Locale.ENGLISH));
        assertEquals("OpenAI-Fehler 404: No such model", notFound.getMessage(Locale.GERMAN));
        assertEquals("OpenAI: invalid bearer token (401)",
                OpenAiTransport.httpError(401, "").getMessage(Locale.ENGLISH));
    }

    @Test
    void theMcpClientsGetTheEnglishMessage() {
        ApiHttpException failure = OpenAiTransport.httpError(401, "");
        CompletionException wrapped = new CompletionException(failure);
        assertEquals("OpenAI: invalid bearer token (401)", ProviderException.messageOf(wrapped, true));
        assertEquals(failure.getMessage(), ProviderException.messageOf(wrapped, false));
        assertEquals("plain", ProviderException.messageOf(new IllegalStateException("plain"), true));
    }
}
