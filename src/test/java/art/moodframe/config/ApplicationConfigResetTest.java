package art.moodframe.config;

import art.moodframe.model.ApiProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void aReaderNeverSeesTheSettingsEmptyDuringAReset() throws Exception {
        ConfigStore store = new ConfigStore(LibraryPaths.under(home));
        AtomicBoolean done = new AtomicBoolean();
        AtomicInteger missing = new AtomicInteger();
        Thread reader = new Thread(() -> {
            while (!done.get())
                if (store.get("ui.theme") == null || store.get("api.base_url") == null)
                    missing.incrementAndGet();
        });
        reader.start();
        try {
            for (int i = 0; i < 100; i++)
                store.resetToDefaults();
        }
        finally {
            done.set(true);
            reader.join();
        }
        assertEquals(0, missing.get(), "a reader saw the settings in the middle of a reset");
    }
}
