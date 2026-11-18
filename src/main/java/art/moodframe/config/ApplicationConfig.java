package art.moodframe.config;

import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The settings of the application, stored in {@code ~/.moodframe/config.properties}.
 *
 * <p>One {@link ConfigStore} holds the file, writes it at once or after a quiet time, and keeps the secrets
 * encrypted. The settings are grouped in sections on that store:</p>
 * <ul>
 * <li>{@link #ui()}: theme, language, window bounds, dividers, selections of the main window</li>
 * <li>{@link #generation()}: the defaults of a generation and the parameters of the models</li>
 * <li>{@link #accounts()}: the provider accounts by {@code ApiProvider}, the BFL and OpenAI extras, the brainstorm</li>
 * <li>{@link #proxy()}: the corporate proxy and its certificates folder</li>
 * <li>{@link #mcp()}: the MCP server</li>
 * </ul>
 *
 * <p>This class itself keeps what belongs to the whole application: the folders of the library, detailed logging,
 * the update hint, the raw properties and the reset. A few getters of the sections are repeated here for the
 * callers in {@code service.provider}, {@code service.generation} and {@code mcp.LibraryTools}.</p>
 *
 * <p>If no configuration file exists, one is created with the defaults.</p>
 *
 * @since 1.0.0
 */
public class ApplicationConfig {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationConfig.class);

    /** Quiet time after the last change of a dragged value (divider, window, slider) before it is written. */
    static final long SAVE_DELAY_MILLIS = ConfigStore.SAVE_DELAY_MILLIS;

    /** The base URL of the OpenAI API, used when none is configured. */
    public static final String OPENAI_DEFAULT_BASE_URL = ProviderAccounts.OPENAI_DEFAULT_BASE_URL;

    private final ConfigStore store;
    private final UiState ui;
    private final GenerationDefaults generation;
    private final ProviderAccounts accounts;
    private final ProxySettings proxy;
    private final McpSettings mcp;

    /**
     * Reads the configuration of the library in the given folders, or creates it with the defaults.
     *
     * @param paths the folders of the library
     */
    public ApplicationConfig(LibraryPaths paths) {
        this(new ConfigStore(paths));
    }

    /**
     * Creates the configuration on a store; for tests that watch the writing.
     *
     * @param store the store
     */
    ApplicationConfig(ConfigStore store) {
        this.store = store;
        this.ui = new UiState(store);
        this.generation = new GenerationDefaults(store);
        this.accounts = new ProviderAccounts(store);
        this.proxy = new ProxySettings(store, store.paths());
        this.mcp = new McpSettings(store);
    }

    /*  ===== Sections =====  */

    /** @return the look and the remembered state of the windows */
    public UiState ui() {
        return ui;
    }

    /** @return the defaults of a generation */
    public GenerationDefaults generation() {
        return generation;
    }

    /** @return the accounts of the providers */
    public ProviderAccounts accounts() {
        return accounts;
    }

    /** @return the corporate proxy */
    public ProxySettings proxy() {
        return proxy;
    }

    /** @return the MCP server */
    public McpSettings mcp() {
        return mcp;
    }

    /*  ===== Folders =====  */

    /**
     * Gets the folders of the library, with the images folder as configured.
     *
     * @return the paths
     */
    public LibraryPaths paths() {
        return store.paths().withImages(Path.of(getImagesPath()));
    }

    /**
     * Gets the path to the SQLite database file as configured in {@code path.database}.
     *
     * @return the absolute path to the database file
     */
    public String getDatabasePath() {
        return store.get("path.database", store.paths().database().toString());
    }

    /** @return the folder of the log files as configured in {@code path.logs} */
    public String getLogsPath() {
        return store.get("path.logs", store.paths().logs().toString());
    }

    /**
     * Gets the folder of the images as configured in {@code path.images}; a fresh configuration writes the default
     * {@code ~/.moodframe/images} there.
     *
     * @return the images folder
     */
    public String getImagesPath() {
        return store.get("path.images", store.paths().images().toString());
    }

    /*  ===== Application =====  */

    /** @return true if detailed logging is enabled */
    public boolean getLoggingEnabled() {
        return store.getBoolean("logging.detailed", false);
    }

    /**
     * Enables or disables detailed logging and sets the log level at once.
     *
     * @param enabled true for detailed logging
     */
    public void setLoggingEnabled(boolean enabled) {
        store.set("logging.detailed", String.valueOf(enabled));
        DetailedLogging.apply(enabled);
    }

    /**
     * Gets the version whose update hint the user hid.
     *
     * @return the version, or null if no hint was hidden
     * @since 2.1.0
     */
    public String getUpdateDismissedVersion() {
        return store.get("update.dismissed.version");
    }

    /**
     * Hides the update hint for a version; a later version shows it again.
     *
     * @param version the version
     * @since 2.1.0
     */
    public void setUpdateDismissedVersion(String version) {
        store.set("update.dismissed.version", version);
    }

    /*  ===== Raw properties, reset and writing =====  */

    /**
     * Sets a configuration property and writes the file. Secrets go through their section, never through here.
     *
     * @param key   the property key
     * @param value the property value
     */
    public void setProperty(String key, String value) {
        store.set(key, value);
    }

    /**
     * Gets a configuration property.
     *
     * @param key the property key
     * @return the value, or null if not set
     */
    public String getProperty(String key) {
        return store.get(key);
    }

    /**
     * Gets a configuration property.
     *
     * @param key          the property key
     * @param defaultValue the value if none is set
     * @return the value
     */
    public String getProperty(String key, String defaultValue) {
        return store.get(key, defaultValue);
    }

    /**
     * Resets all settings to their defaults, except every stored secret: the API keys of all providers, the MCP
     * token and the proxy password.
     *
     * @since 1.0.0
     */
    public void resetToDefaults() {
        store.resetToDefaults();
        logger.info("Configuration reset to defaults (API keys, MCP token and proxy password preserved)");
    }

    /**
     * Writes the configuration file now.
     */
    public void saveConfiguration() {
        store.save();
    }

    /**
     * Writes a delayed save now instead of later; called on shutdown, so no change of the last moments gets lost.
     */
    public void flushConfiguration() {
        store.flush();
    }

    /*  ===== Shortcuts to the sections for service.provider, service.generation and mcp.LibraryTools =====  */

    /** @return see {@link ProviderAccounts#getApiRegion()} */
    public String getApiRegion() {
        return accounts().getApiRegion();
    }

    /** @return see {@link ProviderAccounts#getApiTimeoutSeconds()} */
    public int getApiTimeoutSeconds() {
        return accounts().getApiTimeoutSeconds();
    }

    /** @return see {@link ProviderAccounts#getOpenAiBaseUrl()} */
    public String getOpenAiBaseUrl() {
        return accounts().getOpenAiBaseUrl();
    }

    /** @param baseUrl see {@link ProviderAccounts#setOpenAiBaseUrl(String)} */
    public void setOpenAiBaseUrl(String baseUrl) {
        accounts().setOpenAiBaseUrl(baseUrl);
    }

    /** @return see {@link ProviderAccounts#getOpenAiModels()} */
    public List<String> getOpenAiModels() {
        return accounts().getOpenAiModels();
    }

    /** @param models see {@link ProviderAccounts#setOpenAiModels(List)} */
    public void setOpenAiModels(List<String> models) {
        accounts().setOpenAiModels(models);
    }

    /** @return see {@link ProviderAccounts#hasOpenAiModelSelection()} */
    public boolean hasOpenAiModelSelection() {
        return accounts().hasOpenAiModelSelection();
    }

    /** @param models see {@link ProviderAccounts#setOpenAiAvailableModels(List)} */
    public void setOpenAiAvailableModels(List<String> models) {
        accounts().setOpenAiAvailableModels(models);
    }

    /** @return see {@link ProviderAccounts#getOpenAiChatModel()} */
    public String getOpenAiChatModel() {
        return accounts().getOpenAiChatModel();
    }

    /** @return see {@link GenerationDefaults#getDefaultModel()} */
    public String getDefaultModel() {
        return generation().getDefaultModel();
    }

    /** @return see {@link GenerationDefaults#getFlux2SafetyTolerance()} */
    public int getFlux2SafetyTolerance() {
        return generation().getFlux2SafetyTolerance();
    }

    /** @param safetyTolerance see {@link GenerationDefaults#setFlux2SafetyTolerance(int)} */
    public void setFlux2SafetyTolerance(int safetyTolerance) {
        generation().setFlux2SafetyTolerance(safetyTolerance);
    }

    /** @return see {@link GenerationDefaults#getFlux2FlexGuidance()} */
    public double getFlux2FlexGuidance() {
        return generation().getFlux2FlexGuidance();
    }

    /** @return see {@link GenerationDefaults#getFlux2FlexSteps()} */
    public int getFlux2FlexSteps() {
        return generation().getFlux2FlexSteps();
    }

    /** @return see {@link ProxySettings#isProxyEnabled()} */
    public boolean isProxyEnabled() {
        return proxy().isProxyEnabled();
    }
}
