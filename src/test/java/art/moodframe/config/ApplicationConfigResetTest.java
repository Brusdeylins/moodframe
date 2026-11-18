package art.moodframe.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A reset of the settings keeps every stored secret and forgets the rest. */
class ApplicationConfigResetTest {

    @TempDir
    Path home;

    @Test
    void keepsEveryKeyAndTokenAndThePassword() {
        LibraryPaths paths = LibraryPaths.under(home);
        ApplicationConfig config = new ApplicationConfig(paths);
        config.setApiKey("bfl-key");
        config.setGeminiApiKey("gemini-key");
        config.setOpenAiApiKey("openai-key");
        config.setMcpToken("mcp-token");
        config.setProxyPassword("proxy-password");
        config.setUiLanguage("en");

        config.resetToDefaults();

        /*  read back from the file, as after a restart  */
        ApplicationConfig stored = new ApplicationConfig(paths);
        assertEquals("bfl-key", stored.getApiKey());
        assertEquals("gemini-key", stored.getGeminiApiKey());
        assertEquals("openai-key", stored.getOpenAiApiKey());
        assertEquals("mcp-token", stored.getMcpToken());
        assertEquals("proxy-password", stored.getProxyPassword());
        assertEquals("de", stored.getUiLanguage());
    }

    @Test
    void keepsNoSecretThatWasNotThere() {
        ApplicationConfig config = new ApplicationConfig(LibraryPaths.under(home));
        config.setApiKey("bfl-key");

        config.resetToDefaults();

        assertEquals("bfl-key", config.getApiKey());
        assertNull(config.getGeminiApiKey());
        assertNull(config.getProperty("gemini.api.key"));
        assertNull(config.getMcpToken());
    }
}
