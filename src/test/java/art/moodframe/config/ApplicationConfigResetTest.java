package art.moodframe.config;

import art.moodframe.model.ApiProvider;
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
        config.accounts().setApiKey(ApiProvider.BFL, "bfl-key");
        config.accounts().setApiKey(ApiProvider.GOOGLE_GEMINI, "gemini-key");
        config.accounts().setApiKey(ApiProvider.OPENAI, "openai-key");
        config.mcp().setMcpToken("mcp-token");
        config.proxy().setProxyPassword("proxy-password");
        config.ui().setUiLanguage("en");

        config.resetToDefaults();

        /*  read back from the file, as after a restart  */
        ApplicationConfig stored = new ApplicationConfig(paths);
        assertEquals("bfl-key", stored.accounts().getApiKey(ApiProvider.BFL));
        assertEquals("gemini-key", stored.accounts().getApiKey(ApiProvider.GOOGLE_GEMINI));
        assertEquals("openai-key", stored.accounts().getApiKey(ApiProvider.OPENAI));
        assertEquals("mcp-token", stored.mcp().getMcpToken());
        assertEquals("proxy-password", stored.proxy().getProxyPassword());
        assertEquals("de", stored.ui().getUiLanguage());
    }

    @Test
    void keepsNoSecretThatWasNotThere() {
        ConfigStore store = new ConfigStore(LibraryPaths.under(home));
        ApplicationConfig config = new ApplicationConfig(store);
        config.accounts().setApiKey(ApiProvider.BFL, "bfl-key");

        config.resetToDefaults();

        assertEquals("bfl-key", config.accounts().getApiKey(ApiProvider.BFL));
        assertNull(config.accounts().getApiKey(ApiProvider.GOOGLE_GEMINI));
        assertNull(store.get("gemini.api.key"));
        assertNull(config.mcp().getMcpToken());
    }
}
