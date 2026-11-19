package art.moodframe.config;

import art.moodframe.model.ApiProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sections read and write the keys of the configuration files of former versions. */
class ConfigFileCompatibilityTest {

    @TempDir
    Path home;

    /*  a configuration file as a former version wrote it  */
    private ApplicationConfig withFile(Properties stored) throws Exception {
        LibraryPaths paths = LibraryPaths.under(home);
        Files.createDirectories(paths.root());
        try (Writer writer = Files.newBufferedWriter(paths.configFile())) {
            stored.store(writer, null);
        }
        return new ApplicationConfig(paths);
    }

    private Properties file() throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(LibraryPaths.under(home).configFile())) {
            properties.load(reader);
        }
        return properties;
    }

    @Test
    void aFileWithoutTheModelDefaultsGetsTheValuesOfAFreshInstallation() throws Exception {
        Properties stored = new Properties();
        stored.setProperty("ui.theme", "nord-dark");
        ApplicationConfig former = withFile(stored);

        ApplicationConfig fresh = new ApplicationConfig(LibraryPaths.under(home.resolve("fresh")));
        assertEquals(fresh.generation().getDefaultImageCount(), former.generation().getDefaultImageCount());
        assertEquals(fresh.generation().getDefaultSafetyTolerance(), former.generation().getDefaultSafetyTolerance());
        assertEquals(1, former.generation().getDefaultImageCount());
        assertEquals(2, former.generation().getDefaultSafetyTolerance());
    }

    @Test
    void readsTheKeysOfAFormerVersion() throws Exception {
        Properties stored = new Properties();
        stored.setProperty("provider.bfl.validated", "true");
        stored.setProperty("provider.gemini.enabled", "true");
        stored.setProperty("provider.gemini.chat_ok", "true");
        stored.setProperty("provider.gemini.web_search", "true");
        stored.setProperty("provider.openai.models", "gpt-image-1,qwen-image");
        stored.setProperty("provider.openai.chat_model", "gpt-x");
        stored.setProperty("brainstorm.engine", "openai");
        stored.setProperty("api.region", "EU");
        stored.setProperty("ui.theme", "nord-dark");
        stored.setProperty("sidebar.by.mood", "true");
        stored.setProperty("model.default", "flux2-pro");
        stored.setProperty("veo.default_duration", "4s");
        stored.setProperty("mcp.enabled", "true");
        stored.setProperty("mcp.port", "9000");
        stored.setProperty("proxy.host", "proxy.example");
        stored.setProperty("proxy.port", "3128");
        ApplicationConfig config = withFile(stored);

        ProviderAccounts accounts = config.accounts();
        assertTrue(accounts.isValidated(ApiProvider.BFL));
        assertFalse(accounts.isEnabled(ApiProvider.BFL));
        assertTrue(accounts.hasEnabledPreference(ApiProvider.GOOGLE_GEMINI));
        assertFalse(accounts.hasEnabledPreference(ApiProvider.OPENAI));
        assertTrue(accounts.isChatOk(ApiProvider.GOOGLE_GEMINI));
        assertTrue(accounts.isWebSearch(ApiProvider.GOOGLE_GEMINI));
        assertEquals(List.of("gpt-image-1", "qwen-image"), accounts.getOpenAiModels());
        assertEquals("gpt-x", accounts.getOpenAiChatModel());
        assertEquals("openai", accounts.getBrainstormEngine());
        assertEquals("EU", accounts.getApiRegion());
        assertEquals("nord-dark", config.ui().getUiTheme());
        assertEquals("mood", config.ui().getSidebarMode());
        assertEquals("flux2-pro", config.generation().getDefaultModel());
        assertEquals("4s", config.generation().getDefaultVideoDuration());
        assertTrue(config.mcp().isMcpEnabled());
        assertEquals(9000, config.mcp().getMcpPort());
        assertEquals("proxy.example", config.proxy().getProxyHost());
        assertEquals(3128, config.proxy().getProxyPort());
    }

    @Test
    void writesTheSameKeys() throws Exception {
        ApplicationConfig config = withFile(new Properties());
        config.accounts().setEnabled(ApiProvider.OPENAI, true);
        config.accounts().setValidated(ApiProvider.GOOGLE_GEMINI, true);
        config.accounts().setChatOk(ApiProvider.OPENAI, true);
        config.accounts().setWebSearch(ApiProvider.GOOGLE_GEMINI, true);
        config.accounts().setApiKey(ApiProvider.BFL, "bfl-key");
        config.accounts().setApiKey(ApiProvider.GOOGLE_GEMINI, "gemini-key");
        config.mcp().setMcpToken("token");
        config.ui().setSidebarMode("frame");

        Properties file = file();
        assertEquals("true", file.getProperty("provider.openai.enabled"));
        assertEquals("true", file.getProperty("provider.gemini.validated"));
        assertEquals("true", file.getProperty("provider.openai.chat_ok"));
        assertEquals("true", file.getProperty("provider.gemini.web_search"));
        assertTrue(file.containsKey("api.key"));
        assertTrue(file.containsKey("gemini.api.key"));
        assertTrue(file.containsKey("mcp.token"));
        assertFalse(file.getProperty("api.key").contains("bfl-key"), "keys are stored encrypted");
        assertEquals("frame", file.getProperty("sidebar.mode"));
    }
}
