package art.moodframe.service.provider;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The message of a provider failure is found however deep it is wrapped. */
class ProviderErrorsTest {

    @Test
    void theMessageComesFromAProviderFailureWrappedAnyDeep() {
        ProviderException noKey = ProviderException.noKey("OpenAI");
        RuntimeException wrapped = new CompletionException(new RuntimeException("Failed to submit", noKey));
        assertEquals("OpenAI: no API key configured. Please enter it in the settings.",
                ProviderException.messageOf(wrapped, true));
        assertEquals(noKey.getMessage(), ProviderException.messageOf(wrapped, false));
        assertEquals("Failed to submit", ProviderException.messageOf(
                new CompletionException(new RuntimeException("Failed to submit")), true));
    }
}
