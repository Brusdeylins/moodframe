package art.moodframe.service.provider.gemini;

import art.moodframe.service.provider.GenerationJobInfo;
import art.moodframe.service.provider.ProviderException;
import art.moodframe.util.I18nManager;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A blocked Gemini image fails with a localizable reason, so the window shows it in its language and an MCP client
 * gets it in English, whatever the language of the window.
 */
class GeminiBlockingTest {

    @Test
    void aBlockedPromptNamesTheAttachedImageInEveryLanguage() {
        ProviderException blocked = GeminiImageClient.checkForContentBlocking(
                "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}", true);
        assertEquals(I18nManager.getTextIn(Locale.ENGLISH, "gemini.error.safety.with.image"),
                blocked.getMessage(Locale.ENGLISH));
        assertEquals(I18nManager.getTextIn(Locale.GERMAN, "gemini.error.safety.with.image"),
                blocked.getMessage(Locale.GERMAN));
    }

    @Test
    void aFinishReasonOrAnUnknownReasonIsLocalizable() {
        ProviderException recitation = GeminiImageClient.checkForContentBlocking(
                "{\"candidates\":[{\"finishReason\":\"IMAGE_RECITATION\"}]}", false);
        assertEquals(I18nManager.getTextIn(Locale.ENGLISH, "gemini.error.recitation"),
                recitation.getMessage(Locale.ENGLISH));
        ProviderException unknown = GeminiImageClient.checkForContentBlocking(
                "{\"promptFeedback\":{\"blockReason\":\"NEW_REASON\"}}", false);
        assertEquals(I18nManager.getTextIn(Locale.ENGLISH, "gemini.error.default", "NEW_REASON"),
                unknown.getMessage(Locale.ENGLISH));
        assertNull(GeminiImageClient.checkForContentBlocking("{\"candidates\":[{\"finishReason\":\"STOP\"}]}",
                false));
    }

    @Test
    void theFailedJobCarriesTheReason() {
        ProviderException reason = ProviderException.localized("gemini.error.spii");
        GenerationJobInfo job = new GenerationJobInfo.Builder().failure(reason).isSynchronous(true).build();
        assertEquals(reason, job.getFailure());
        assertEquals("failed", job.getStatus());
        assertEquals(reason.getMessage(), job.getErrorMessage());
    }
}
