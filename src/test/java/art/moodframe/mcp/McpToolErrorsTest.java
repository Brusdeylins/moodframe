package art.moodframe.mcp;

import art.moodframe.service.provider.ApiHttpException;
import art.moodframe.util.I18nManager;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A failed tool tells its client why in English, without the name of a class. */
class McpToolErrorsTest {

    /*  the text of a call of a tool that fails with the given failure  */
    private static String failWith(Exception failure) {
        McpTools tools = new McpTools();
        tools.add("broken", "fails", McpTools.input(McpTools.JSON.createObjectNode()), (arguments) -> {
            if (failure instanceof IOException io)
                throw io;
            throw (RuntimeException) failure;
        });
        ObjectNode response = tools.call("broken", null);
        assertTrue(response.path("isError").asBoolean());
        String text = response.path("content").get(0).path("text").asText();
        assertFalse(text.contains("Exception"), text);
        assertFalse(text.contains("art.moodframe"), text);
        return text;
    }

    @Test
    void aProviderFailureGivesItsMessageInEnglishPastItsWrappers() {
        ApiHttpException http = ApiHttpException.localized(500, "brainstorm.error.http", 500);
        String text = failWith(new CompletionException(new RuntimeException(http)));
        assertEquals("The tool failed: " + I18nManager.getTextIn(Locale.ENGLISH, "brainstorm.error.http", 500), text);
    }

    @Test
    void anUnexpectedFailureGivesAGeneralText() {
        String text = failWith(new IllegalStateException(new NullPointerException("secret detail")));
        assertFalse(text.contains("secret detail"), text);
        assertTrue(text.startsWith("The tool failed"), text);
    }

    @Test
    void aFileThatCannotBeReadIsNamedAsSuch() {
        assertEquals("The tool failed: a file could not be read or written.",
                failWith(new IOException("java.nio.file.AccessDeniedException: /x")));
    }
}
