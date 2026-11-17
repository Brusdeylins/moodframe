package art.moodframe.ui.controller;

import art.moodframe.service.ApiProviderValidator.ModelCheck;
import art.moodframe.service.ApiProviderValidator.Outcome;
import art.moodframe.util.I18nManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the status of the settings after the check of a chosen OpenAI language model. */
class OpenaiModelStatusTest {

    private static final Outcome WORKS = new Outcome(null, true);

    @Test
    void aModelWithoutWebSearchStaysChosenAndIsNotOfferedForTheBrainstorm() {
        SettingsController.OpenaiModelStatus status = SettingsController.openaiModelStatus("gpt-x",
                new ModelCheck(WORKS, new Outcome("OpenAI error 404", true)), null);

        assertTrue(status.keepsChoice());
        assertEquals("info", status.type());
        assertEquals(I18nManager.getText("settings.openai.chat.nosearch", "gpt-x", "OpenAI error 404"), status.message());
        assertTrue(status.message().contains("gpt-x ✓") && status.message().contains("(OpenAI error 404)"),
                status.message());
    }

    @Test
    void aModelWhoseSearchCouldNotBeCheckedStaysChosen() {
        SettingsController.OpenaiModelStatus status = SettingsController.openaiModelStatus("gpt-x",
                new ModelCheck(WORKS, new Outcome("OpenAI error 503", false)), null);

        assertTrue(status.keepsChoice());
        assertEquals(I18nManager.getText("settings.openai.chat.search.kept", "gpt-x", "OpenAI error 503"),
                status.message());
    }

    @Test
    void aModelWithWebSearchWorks() {
        SettingsController.OpenaiModelStatus status = SettingsController.openaiModelStatus("gpt-x",
                new ModelCheck(WORKS, WORKS), null);

        assertTrue(status.keepsChoice());
        assertEquals("success", status.type());
    }

    @Test
    void onlyADefinitelyFailingAnswerOrToolCallTakesTheChoiceBack() {
        SettingsController.OpenaiModelStatus failing = SettingsController.openaiModelStatus("gpt-x",
                new ModelCheck(new Outcome("The model does not call functions (tool calls).", true), WORKS), null);

        assertFalse(failing.keepsChoice());
        assertEquals("error", failing.type());
        assertEquals(I18nManager.getText("settings.openai.chat.failed", "gpt-x",
                "The model does not call functions (tool calls)."), failing.message());
    }

    @Test
    void anUndecidedCheckKeepsTheModelAndAsksToTestAgain() {
        SettingsController.OpenaiModelStatus limited = SettingsController.openaiModelStatus("gpt-x",
                new ModelCheck(new Outcome("OpenAI: rate limit or quota exceeded (429)", false), WORKS), null);
        SettingsController.OpenaiModelStatus broken = SettingsController.openaiModelStatus("gpt-x", null,
                new IllegalStateException("broken"));

        assertTrue(limited.keepsChoice());
        assertEquals(I18nManager.getText("settings.openai.chat.check.failed", "gpt-x",
                "OpenAI: rate limit or quota exceeded (429)"), limited.message());
        assertTrue(broken.keepsChoice());
        assertEquals(I18nManager.getText("settings.openai.chat.check.failed", "gpt-x", "broken"), broken.message());
    }
}
