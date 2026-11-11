package art.moodframe.config;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central application configuration management.
 * 
 * <p>This class manages all application settings using a properties-based approach.
 * Configuration files are stored in the user's home directory under {@code ~/.moodframe/}
 * and include automatic directory structure initialization.</p>
 * 
 * <p><strong>Configuration Directory Structure:</strong></p>
 * <ul>
 * <li>{@code ~/.moodframe/config.properties} - Main configuration file</li>
 * <li>{@code ~/.moodframe/db/} - Database files</li>
 * <li>{@code ~/.moodframe/logs/} - Log files</li>
 * <li>{@code ~/.moodframe/images/} - Downloaded images</li>
 * </ul>
 * 
 * <p><strong>Default Configuration:</strong> If no configuration file exists,
 * the application will create one with sensible defaults for all settings.</p>
 * 
 * <p><strong>External API References:</strong></p>
 * <ul>
 * <li><a href="https://docs.bfl.ai">BFL API Documentation</a></li>
 * <li><a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Properties.html">Java Properties API</a></li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class ApplicationConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(ApplicationConfig.class);
    
    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.moodframe";
    private static final String CONFIG_FILE = "config.properties";
    private static final String DB_DIR = CONFIG_DIR + "/db";
    private static final String LOGS_DIR = CONFIG_DIR + "/logs";
    private static final String IMAGES_DIR = CONFIG_DIR + "/images";
    
    private final Properties properties = new Properties();
    private final Path configPath;
    
    public ApplicationConfig() {
        this.configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);
        initializeDirectories();
        loadConfiguration();
    }
    
    /**
     * Initializes required application directories.
     * 
     * <p>Creates the following directories if they don't exist:</p>
     * <ul>
     * <li>Main configuration directory ({@code ~/.moodframe/})</li>
     * <li>Database directory ({@code ~/.moodframe/db/})</li>
     * <li>Logs directory ({@code ~/.moodframe/logs/})</li>
     * <li>Images directory ({@code ~/.moodframe/images/})</li>
     * </ul>
     * 
     * @throws RuntimeException if directories cannot be created
     * @since 1.0.0
     */
    private void initializeDirectories() {
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));
            Files.createDirectories(Paths.get(DB_DIR));
            Files.createDirectories(Paths.get(LOGS_DIR));
            Files.createDirectories(Paths.get(IMAGES_DIR));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create configuration directories", e);
        }
    }
    
    /**
     * Loads configuration from the properties file.
     * 
     * <p>If the configuration file exists, loads properties from it.
     * If the file doesn't exist or cannot be read, falls back to
     * default configuration and creates a new config file.</p>
     * 
     * @since 1.0.0
     */
    private void loadConfiguration() {
        if (Files.exists(configPath)) {
            try (FileInputStream fis = new FileInputStream(configPath.toFile())) {
                properties.load(fis);
            } catch (IOException e) {
                // On error, continue with defaults
                loadDefaults();
            }
        } else {
            loadDefaults();
            saveConfiguration();
        }
    }
    
    /**
     * Loads default configuration values.
     * 
     * <p>Sets up default values for all configuration properties including:</p>
     * <ul>
     * <li>API settings (base URL, timeout, retries)</li>
     * <li>UI settings (theme, language, window size)</li>
     * <li>File paths (database, logs, images)</li>
     * <li>Model defaults (default FLUX model, aspect ratio)</li>
     * <li>Environment detection</li>
     * </ul>
     * 
     * <p><strong>API Reference:</strong> Default base URL points to 
     * <a href="https://docs.bfl.ai">BFL API (https://api.bfl.ai)</a></p>
     * 
     * @since 1.0.0
     */
    private void loadDefaults() {
        // API-Einstellungen
        properties.setProperty("api.base_url", "https://api.bfl.ai");
        properties.setProperty("api.timeout_seconds", "600");
        
        // UI-Einstellungen
        properties.setProperty("ui.theme", "primer-light");
        properties.setProperty("ui.language", "de");
        properties.setProperty("ui.window.width", "1200");
        properties.setProperty("ui.window.height", "800");
        
        // Pfade
        properties.setProperty("path.database", DB_DIR + "/moodframe.db");
        properties.setProperty("path.logs", LOGS_DIR);
        properties.setProperty("path.images", IMAGES_DIR);
        
        // Modell-Einstellungen
        properties.setProperty("model.default", "flux.3-image");
        properties.setProperty("model.default_aspect_ratio", "16:9");
        properties.setProperty("model.default_image_count", "1");
        properties.setProperty("model.default_safety_tolerance", "2");
        
        // Entwicklung
        properties.setProperty("dev.environment", detectEnvironment());
    }
    
    /**
     * Detects the application environment (development or production).
     * 
     * <p>Environment detection logic:</p>
     * <ol>
     * <li>Checks system property {@code moodframe.env}</li>
     * <li>If not set, checks if classpath contains "gradle" (development)</li>
     * <li>Defaults to "prod" for production environments</li>
     * </ol>
     * 
     * @return "dev" for development environment, "prod" for production
     * @since 1.0.0
     */
    private String detectEnvironment() {
        String env = System.getProperty("moodframe.env");
        if (env != null) {
            return env;
        }
        // Wenn im IDE/Debug-Modus, dann dev
        return System.getProperty("java.class.path").contains("gradle") ? "dev" : "prod";
    }
    
    /**
     * Saves the current configuration to the properties file.
     * 
     * <p>Writes all current property values to the configuration file.
     * The file is replaced atomically and calls are serialized. If saving fails,
     * the error is logged but not thrown to avoid disrupting application functionality.</p>
     * 
     * @since 1.0.0
     */
    public synchronized void saveConfiguration() {
        /*  Write beside the file and move it over, so a crash never leaves a truncated configuration  */
        Path temp = configPath.resolveSibling(CONFIG_FILE + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(temp.toFile())) {
            properties.store(fos, "MoodFrame Configuration");
            fos.getFD().sync();
            Files.move(temp, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.error("Failed to save the configuration to {}", configPath, e);
        }
    }
    
    // Configuration getter methods
    // Each getter provides access to a specific configuration value with appropriate type conversion
    
    /**
     * Where the token of a provider can come from: environment variable, system property and the key of the
     * encrypted value in the configuration file, in this order of priority.
     *
     * @param environment the environment variable
     * @param property    the system property
     * @param key         the configuration key
     * @param name        the name in the log, for example "Gemini API key"
     */
    private record TokenSource(String environment, String property, String key, String name) {
    }

    private static final TokenSource BFL = new TokenSource("BFL_API_KEY", "bfl.api.key", "api.key", "API key");
    private static final TokenSource GEMINI = new TokenSource("GEMINI_API_KEY", "gemini.api.key", "gemini.api.key",
            "Gemini API key");
    private static final TokenSource OPENAI = new TokenSource("OPENAI_API_KEY", "openai.api.key", "openai.api.key",
            "OpenAI API key");

    /** Reads a token: environment, then system property, then the decrypted configuration value; null if none. */
    private String readKey(TokenSource source) {
        String value = System.getenv(source.environment());
        if (value == null || value.isBlank())
            value = System.getProperty(source.property());
        if (value != null && !value.isBlank())
            return value.trim();

        String encrypted = properties.getProperty(source.key());
        if (encrypted == null || encrypted.isBlank())
            return null;
        try {
            String decrypted = EncryptionUtil.decrypt(encrypted);
            return decrypted == null || decrypted.isBlank() ? null : decrypted;
        } catch (Exception e) {
            logger.error("Failed to decrypt {} from configuration", source.name(), e);
            return null;
        }
    }

    /** Stores a token encrypted in the configuration file; null or empty removes it. */
    private void storeKey(TokenSource source, String value) {
        if (value == null || value.trim().isEmpty()) {
            properties.remove(source.key());
            logger.info("{} removed from configuration", source.name());
        } else {
            try {
                properties.setProperty(source.key(), EncryptionUtil.encrypt(value.trim()));
                logger.info("{} encrypted and stored in configuration", source.name());
            } catch (Exception e) {
                logger.error("Failed to encrypt {}", source.name(), e);
                throw new RuntimeException("Failed to store " + source.name(), e);
            }
        }
        saveConfiguration();
    }

    /** Tells where the active token comes from: "environment", "system_property", "configuration" or "none". */
    private String keySource(TokenSource source) {
        String env = System.getenv(source.environment());
        if (env != null && !env.isBlank())
            return "environment";
        String property = System.getProperty(source.property());
        if (property != null && !property.isBlank())
            return "system_property";
        String encrypted = properties.getProperty(source.key());
        return encrypted != null && !encrypted.isBlank() ? "configuration" : "none";
    }

    /**
     * Gets the BFL API key.
     * 
     * <p>Retrieves the API key from multiple sources in order of priority:</p>
     * <ol>
     * <li>Environment variable {@code BFL_API_KEY}</li>
     * <li>System property {@code bfl.api.key}</li>
     * <li>Encrypted key from configuration file</li>
     * </ol>
     * 
     * @return the API key, or null if not configured
     * @since 1.0.0
     */
    public String getApiKey() {
        return readKey(BFL);
    }
    
    /**
     * Sets the BFL API key.
     * 
     * <p>Encrypts the provided API key and stores it in configuration.
     * If the key is null or empty, removes the API key from configuration.</p>
     * 
     * @param apiKey the API key to store, or null to remove
     * @since 1.0.0
     */
    public void setApiKey(String apiKey) {
        storeKey(BFL, apiKey);
    }
    
    /**
     * Checks if an API key is configured from any source.
     * 
     * @return true if a valid API key is configured, false otherwise
     * @since 1.0.0
     */
    public boolean hasApiKey() {
        return readKey(BFL) != null;
    }
    
    /**
     * Gets the source of the currently active API key.
     * 
     * @return "environment", "system_property", "configuration", or "none"
     * @since 1.0.0
     */
    public String getApiKeySource() {
        return keySource(BFL);
    }
    
    /**
     * Checks if the API key can be modified through the UI.
     * Environment variables and system properties take precedence and cannot be overridden.
     * 
     * @return true if the API key can be modified through UI, false otherwise
     * @since 1.0.0
     */
    public boolean isApiKeyEditable() {
        String source = keySource(BFL);
        return "none".equals(source) || "configuration".equals(source);
    }
    
    /**
     * Gets the base URL for the BFL API.
     * 
     * @return the API base URL (default: "https://api.bfl.ai")
     * @see <a href="https://docs.bfl.ai">BFL API Documentation</a>
     * @since 1.0.0
     */
    public String getApiBaseUrl() {
        return properties.getProperty("api.base_url");
    }
    
    /**
     * Gets the API request timeout in seconds.
     * 
     * @return the timeout in seconds (default: 600)
     * @since 1.0.0
     */
    public int getApiTimeoutSeconds() {
        return Integer.parseInt(properties.getProperty("api.timeout_seconds", "600"));
    }
    
    /**
     * Gets the current UI theme.
     * 
     * @return the AtlantaFX theme name (default: "primer-light")
     * @since 1.0.0
     */
    public String getUiTheme() {
        return properties.getProperty("ui.theme", "primer-light");
    }
    
    /**
     * Gets all available AtlantaFX themes.
     * 
     * @return array of available theme names
     * @since 1.0.0
     */
    public String[] getAvailableThemes() {
        return new String[]{
            "primer-light",
            "primer-dark",
            "nord-light",
            "nord-dark",
            "cupertino-light",
            "cupertino-dark",
            "dracula"
        };
    }
    
    public String getUiLanguage() {
        return properties.getProperty("ui.language", "de");
    }
    
    public int getWindowWidth() {
        return Integer.parseInt(properties.getProperty("ui.window.width", "1200"));
    }
    
    public int getWindowHeight() {
        return Integer.parseInt(properties.getProperty("ui.window.height", "800"));
    }
    
    public double getWindowX() {
        return Double.parseDouble(properties.getProperty("ui.window.x", "-1"));
    }
    
    public double getWindowY() {
        return Double.parseDouble(properties.getProperty("ui.window.y", "-1"));
    }
    
    public boolean getWindowMaximized() {
        return Boolean.parseBoolean(properties.getProperty("ui.window.maximized", "false"));
    }
    
    /**
     * Gets the path to the SQLite database file.
     * 
     * @return the absolute path to the database file
     * @see <a href="https://github.com/xerial/sqlite-jdbc">SQLite JDBC Documentation</a>
     * @since 1.0.0
     */
    public String getDatabasePath() {
        return properties.getProperty("path.database");
    }
    
    public String getLogsPath() {
        return properties.getProperty("path.logs");
    }
    
    public String getImagesPath() {
        return properties.getProperty("path.images");
    }
    
    public String getDefaultModel() {
        return properties.getProperty("model.default", "flux.3-image");
    }
    
    public String getDefaultAspectRatio() {
        return properties.getProperty("model.default_aspect_ratio", "16:9");
    }
    
    public int getDefaultImageCount() {
        return Integer.parseInt(properties.getProperty("model.default_image_count", "2"));
    }
    
    public int getDefaultSafetyTolerance() {
        return Integer.parseInt(properties.getProperty("model.default_safety_tolerance", "6"));
    }
    
    public void setDefaultSafetyTolerance(int safetyTolerance) {
        properties.setProperty("model.default_safety_tolerance", String.valueOf(safetyTolerance));
        saveConfiguration();
    }

    /**
     * Gets the default safety tolerance for FLUX.2 models (0-5 scale).
     * FLUX 3 uses the same value (it accepts 0-7).
     *
     * @return safety tolerance for FLUX.2 (0=strictest, 5=most permissive), defaults to 5
     */
    public int getFlux2SafetyTolerance() {
        return Integer.parseInt(properties.getProperty("model.flux2_safety_tolerance", "5"));
    }

    /**
     * Sets the default safety tolerance for FLUX.2 models.
     *
     * @param safetyTolerance value between 0 (strictest) and 5 (most permissive)
     */
    public void setFlux2SafetyTolerance(int safetyTolerance) {
        // Ensure value is within valid range for FLUX.2
        int clampedValue = Math.max(0, Math.min(5, safetyTolerance));
        properties.setProperty("model.flux2_safety_tolerance", String.valueOf(clampedValue));
        saveConfiguration();
    }

    /**
     * Gets the main splitter divider position (0.0 - 1.0).
     * @return splitter position, defaults to 0.2 (20% left sidebar)
     */
    public double getSplitterPosition() {
        return Double.parseDouble(properties.getProperty("ui.splitter_position", "0.2"));
    }
    
    /**
     * Sets the main splitter divider position.
     * @param position position between 0.0 and 1.0
     */
    public void setSplitterPosition(double position) {
        properties.setProperty("ui.splitter_position", String.valueOf(position));
        saveConfiguration();
    }

    /**
     * Gets the chat-input splitter divider position (0.0 - 1.0).
     * @return splitter position, defaults to 0.84 (84% chat area, 16% input area)
     */
    public double getChatInputSplitterPosition() {
        return Double.parseDouble(properties.getProperty("ui.chat_input_splitter_position", "0.84"));
    }

    /**
     * Sets the chat-input splitter divider position.
     * @param position position between 0.0 and 1.0
     */
    public void setChatInputSplitterPosition(double position) {
        properties.setProperty("ui.chat_input_splitter_position", String.valueOf(position));
        saveConfiguration();
    }

    public String getDefaultStyle() {
        return properties.getProperty("style.default", "None");
    }

    /**
     * Whether the main window shows the colored cloud in its lower right corner.
     *
     * @return true unless the user switched it off (default: on)
     */
    public boolean isBackgroundImageEnabled() {
        return Boolean.parseBoolean(properties.getProperty("ui.background.image", "true"));
    }

    /**
     * Switches the colored cloud of the main window on or off.
     *
     * @param enabled true to show it
     */
    public void setBackgroundImageEnabled(boolean enabled) {
        properties.setProperty("ui.background.image", String.valueOf(enabled));
        saveConfiguration();
    }

    /**
     * Gets the default color style selection.
     *
     * @return the default color style name, or "None" if not set
     */
    public String getDefaultColorStyle() {
        return properties.getProperty("colorstyle.default", "None");
    }

    /**
     * Sets the default color style selection.
     *
     * @param colorStyle the color style name to set as default
     */
    public void setDefaultColorStyle(String colorStyle) {
        properties.setProperty("colorstyle.default", colorStyle);
        saveConfiguration();
    }

    /**
     * Gets the remembered light selection.
     *
     * @return the name of the light block, or "None" if not set
     */
    public String getDefaultLight() {
        return properties.getProperty("light.default", "None");
    }

    /**
     * Remembers the light selection.
     *
     * @param light the name of the light block
     */
    public void setDefaultLight(String light) {
        properties.setProperty("light.default", light);
        saveConfiguration();
    }

    /**
     * Gets the remembered camera selection.
     *
     * @return the name of the camera block, or "None" if not set
     */
    public String getDefaultCamera() {
        return properties.getProperty("camera.default", "None");
    }

    /**
     * Remembers the camera selection.
     *
     * @param camera the name of the camera block
     */
    public void setDefaultCamera(String camera) {
        properties.setProperty("camera.default", camera);
        saveConfiguration();
    }

    /**
     * Gets the remembered atmosphere selection.
     *
     * @return the name of the atmosphere block, or "None" if not set
     */
    public String getDefaultAtmosphere() {
        return properties.getProperty("atmosphere.default", "None");
    }

    /**
     * Remembers the atmosphere selection.
     *
     * @param atmosphere the name of the atmosphere block
     */
    public void setDefaultAtmosphere(String atmosphere) {
        properties.setProperty("atmosphere.default", atmosphere);
        saveConfiguration();
    }

    /**
     * Whether a building block field of the mood is locked: moods and brainstorm leave it unchanged.
     *
     * @param field the field ("atmosphere", "style", "palette", "light" or "camera")
     * @return true if locked (default: unlocked)
     */
    public boolean isBlockLocked(String field) {
        return Boolean.parseBoolean(properties.getProperty("block.lock." + field, "false"));
    }

    /**
     * Locks or unlocks a building block field of the mood.
     *
     * @param field the field ("atmosphere", "style", "palette", "light" or "camera")
     * @param locked true to lock it
     */
    public void setBlockLocked(String field, boolean locked) {
        properties.setProperty("block.lock." + field, String.valueOf(locked));
        saveConfiguration();
    }

    /**
     * Gets the remembered mood selection.
     *
     * @return the id of the mood, empty for "No mood" (default)
     */
    public String getSelectedMood() {
        return properties.getProperty("mood.selected", "");
    }

    /**
     * Whether a field was changed after the remembered mood was chosen.
     *
     * @return true if the mood shows as changed (default: unchanged)
     */
    public boolean isSelectedMoodChanged() {
        return Boolean.parseBoolean(properties.getProperty("mood.selected.changed", "false"));
    }

    /**
     * Remembers the mood selection and whether a field was changed after it.
     *
     * @param moodId the id of the mood, empty for "No mood"
     * @param changed true if a field was changed after the mood was chosen
     */
    public void setSelectedMood(String moodId, boolean changed) {
        properties.setProperty("mood.selected", moodId);
        properties.setProperty("mood.selected.changed", String.valueOf(changed));
        saveConfiguration();
    }

    /**
     * Whether the left sidebar lists the moods instead of the chats.
     *
     * @return true for the list by mood (default: the chats)
     */
    public boolean isSidebarByMood() {
        return Boolean.parseBoolean(properties.getProperty("sidebar.by.mood", "false"));
    }

    /**
     * Remembers whether the left sidebar lists the moods instead of the chats.
     *
     * @param byMood true for the list by mood
     */
    public void setSidebarByMood(boolean byMood) {
        properties.setProperty("sidebar.by.mood", String.valueOf(byMood));
        saveConfiguration();
    }

    /**
     * Gets the remembered frame selection.
     *
     * @return the id of the frame, empty for "No frame" (default)
     */
    public String getSelectedFrame() {
        return properties.getProperty("frame.selected", "");
    }

    /**
     * Whether a field was changed after the remembered frame was chosen.
     *
     * @return true if the frame shows as changed (default: unchanged)
     */
    public boolean isSelectedFrameChanged() {
        return Boolean.parseBoolean(properties.getProperty("frame.selected.changed", "false"));
    }

    /**
     * Remembers the frame selection and whether a field was changed after it.
     *
     * @param frameId the id of the frame, empty for "No frame"
     * @param changed true if a field was changed after the frame was chosen
     */
    public void setSelectedFrame(String frameId, boolean changed) {
        properties.setProperty("frame.selected", frameId);
        properties.setProperty("frame.selected.changed", String.valueOf(changed));
        saveConfiguration();
    }

    /**
     * Whether the building blocks of the mood go into the prompt while a reference image is attached.
     *
     * @return true if the mood applies (default: off, the image sets the look)
     */
    public boolean isMoodAppliedToReference() {
        return Boolean.parseBoolean(properties.getProperty("mood.apply.reference", "false"));
    }

    /**
     * Sets whether the building blocks of the mood go into the prompt while a reference image is attached.
     *
     * @param applied true to apply the mood
     */
    public void setMoodAppliedToReference(boolean applied) {
        properties.setProperty("mood.apply.reference", String.valueOf(applied));
        saveConfiguration();
    }


    // Configuration setter methods
    // Each setter updates the configuration and automatically saves to file
    
    
    /**
     * Gets the encrypted API key.
     * 
     * <p><strong>Security:</strong> Use {@link art.moodframe.config.EncryptionUtil#decrypt(String)}
     * to decrypt the returned value before using it in API calls.</p>
     * 
     * @return the encrypted API key, or null if not set
     * @since 1.0.0
     */
    public String getEncryptedApiKey() {
        return properties.getProperty("api.key_encrypted");
    }
    
    public void setUiTheme(String theme) {
        properties.setProperty("ui.theme", theme);
        // Mark that user has explicitly set a theme preference
        properties.setProperty("ui.theme.user_set", "true");
        saveConfiguration();
    }
    
    /**
     * Checks if the user has explicitly set a theme preference.
     * 
     * @return true if user has explicitly chosen a theme
     */
    public boolean hasUserSetTheme() {
        return Boolean.parseBoolean(properties.getProperty("ui.theme.user_set", "false"));
    }
    
    public void setUiLanguage(String language) {
        properties.setProperty("ui.language", language);
        saveConfiguration();
    }
    
    public void setWindowBounds(double x, double y, double width, double height, boolean maximized) {
        properties.setProperty("ui.window.x", String.valueOf(x));
        properties.setProperty("ui.window.y", String.valueOf(y));
        properties.setProperty("ui.window.width", String.valueOf((int)width));
        properties.setProperty("ui.window.height", String.valueOf((int)height));
        properties.setProperty("ui.window.maximized", String.valueOf(maximized));
        saveConfiguration();
    }
    
    // API Region Settings
    
    /**
     * Gets the configured API region.
     * 
     * @return the API region (Global, EU, US)
     */
    public String getApiRegion() {
        return properties.getProperty("api.region", "Global");
    }
    
    public void setApiRegion(String region) {
        properties.setProperty("api.region", region);
        saveConfiguration();
    }
    
    // Logging Settings
    
    /**
     * Gets whether detailed logging is enabled.
     * 
     * @return true if detailed logging is enabled, false otherwise
     */
    public boolean getLoggingEnabled() {
        return Boolean.parseBoolean(properties.getProperty("logging.detailed", "false"));
    }
    
    public void setLoggingEnabled(boolean enabled) {
        properties.setProperty("logging.detailed", String.valueOf(enabled));
        saveConfiguration();
    }
    
    public void setDefaultModel(String model) {
        properties.setProperty("model.default", model);
        saveConfiguration();
    }
    
    public void setDefaultAspectRatio(String aspectRatio) {
        properties.setProperty("model.default_aspect_ratio", aspectRatio);
        saveConfiguration();
    }
    
    public void setDefaultImageCount(int count) {
        properties.setProperty("model.default_image_count", String.valueOf(count));
        saveConfiguration();
    }
    
    public void setDefaultStyle(String style) {
        properties.setProperty("style.default", style);
        saveConfiguration();
    }
    
    /**
     * Gets the default BFL prompt upsampling setting.
     *
     * @return true if BFL prompt upsampling is enabled by default
     */
    public boolean getDefaultBflPromptUpsampling() {
        return Boolean.parseBoolean(properties.getProperty("generation.bfl_prompt_upsampling", "false"));
    }

    /**
     * Sets the default BFL prompt upsampling setting.
     *
     * @param enabled true to enable BFL prompt upsampling by default
     */
    public void setDefaultBflPromptUpsampling(boolean enabled) {
        properties.setProperty("generation.bfl_prompt_upsampling", String.valueOf(enabled));
        saveConfiguration();
    }

    /**
     * Gets the default Gemini thinking level for Gemini 3 Pro models.
     *
     * @return thinking level ("minimal" or "high"), defaults to "minimal"
     */
    public String getDefaultGeminiThinkingLevel() {
        return properties.getProperty("generation.gemini_thinking_level", "minimal");
    }

    /**
     * Sets the default Gemini thinking level for Gemini 3 Pro models.
     *
     * @param level thinking level ("low" or "high")
     */
    public void setDefaultGeminiThinkingLevel(String level) {
        properties.setProperty("generation.gemini_thinking_level", level);
        saveConfiguration();
    }

    // ===== Video Settings (the keys keep the name `veo.` of the first video model, so saved settings stay) =====

    /**
     * Gets the default video duration.
     *
     * @return duration string ("4s", "6s", or "8s"), defaults to "8s"
     */
    public String getDefaultVideoDuration() {
        return properties.getProperty("veo.default_duration", "8s");
    }

    /**
     * Sets the default video duration.
     *
     * @param duration duration string ("4s", "6s", or "8s")
     */
    public void setDefaultVideoDuration(String duration) {
        properties.setProperty("veo.default_duration", duration);
        saveConfiguration();
    }

    /**
     * Gets whether video audio generation is enabled by default.
     *
     * @return true if audio generation is enabled, defaults to false
     */
    public boolean isVideoAudioEnabled() {
        return Boolean.parseBoolean(properties.getProperty("veo.audio_enabled", "false"));
    }

    /**
     * Sets whether video audio generation is enabled by default.
     *
     * @param enabled true to enable audio generation
     */
    public void setVideoAudioEnabled(boolean enabled) {
        properties.setProperty("veo.audio_enabled", String.valueOf(enabled));
        saveConfiguration();
    }

    /**
     * Gets the default prompt upsampling setting (deprecated - use provider-specific methods).
     *
     * @deprecated Use {@link #getDefaultBflPromptUpsampling()} instead
     * @return true if prompt upsampling is enabled by default
     */
    @Deprecated
    public boolean getDefaultPromptUpsampling() {
        return getDefaultBflPromptUpsampling();
    }

    /**
     * Sets the default prompt upsampling setting (deprecated - use provider-specific methods).
     *
     * @deprecated Use {@link #setDefaultBflPromptUpsampling(boolean)} instead
     * @param enabled true to enable prompt upsampling by default
     */
    @Deprecated
    public void setDefaultPromptUpsampling(boolean enabled) {
        setDefaultBflPromptUpsampling(enabled);
    }

    /**
     * Gets the default Google Search grounding setting for Gemini Phase 2.
     *
     * @return true if Google Search grounding is enabled by default
     */
    public boolean getDefaultGoogleSearchEnabled() {
        return Boolean.parseBoolean(properties.getProperty("gemini.default_google_search", "false"));
    }

    /**
     * Sets the default Google Search grounding setting for Gemini Phase 2.
     *
     * @param enabled true to enable Google Search grounding by default
     */
    public void setDefaultGoogleSearchEnabled(boolean enabled) {
        properties.setProperty("gemini.default_google_search", String.valueOf(enabled));
        saveConfiguration();
    }

    /**
     * Gets the default thinking/reasoning mode for Gemini Phase 2.
     *
     * @return the thinking mode ("off", "low", "high")
     */
    public String getDefaultThinkingMode() {
        return properties.getProperty("gemini.default_thinking_mode", "low");
    }

    /**
     * Sets the default thinking/reasoning mode for Gemini Phase 2.
     *
     * @param mode the thinking mode ("off", "low", "high")
     */
    public void setDefaultThinkingMode(String mode) {
        properties.setProperty("gemini.default_thinking_mode", mode);
        saveConfiguration();
    }

    /**
     * Gets the default include thoughts setting for Gemini Phase 2.
     *
     * @return true if thought process should be included by default
     */
    public boolean getDefaultIncludeThoughts() {
        return Boolean.parseBoolean(properties.getProperty("gemini.default_include_thoughts", "false"));
    }

    /**
     * Sets the default include thoughts setting for Gemini Phase 2.
     *
     * @param enabled true to include thought process by default
     */
    public void setDefaultIncludeThoughts(boolean enabled) {
        properties.setProperty("gemini.default_include_thoughts", String.valueOf(enabled));
        saveConfiguration();
    }

    // ===== FLUX.2 [flex] Settings =====

    /**
     * Gets the FLUX.2 [flex] guidance parameter.
     * Controls prompt adherence strength.
     *
     * @return guidance value (1.5-10.0), defaults to 5.0
     */
    public double getFlux2FlexGuidance() {
        return Double.parseDouble(properties.getProperty("flux2.flex.guidance", "5.0"));
    }

    /**
     * Sets the FLUX.2 [flex] guidance parameter.
     *
     * @param guidance guidance value (1.5-10.0)
     */
    public void setFlux2FlexGuidance(double guidance) {
        // Clamp to valid range
        guidance = Math.max(1.5, Math.min(10.0, guidance));
        properties.setProperty("flux2.flex.guidance", String.valueOf(guidance));
        saveConfiguration();
    }

    /**
     * Gets the FLUX.2 [flex] steps parameter.
     * Controls inference quality.
     *
     * @return steps value (1-50), defaults to 50
     */
    public int getFlux2FlexSteps() {
        return Integer.parseInt(properties.getProperty("flux2.flex.steps", "50"));
    }

    /**
     * Sets the FLUX.2 [flex] steps parameter.
     *
     * @param steps steps value (1-50)
     */
    public void setFlux2FlexSteps(int steps) {
        // Clamp to valid range
        steps = Math.max(1, Math.min(50, steps));
        properties.setProperty("flux2.flex.steps", String.valueOf(steps));
        saveConfiguration();
    }

    /**
     * Gets the FLUX.2 grounding search setting.
     * Only applicable to FLUX.2 [max] model.
     *
     * @return true if grounding search is enabled
     */
    public boolean getFlux2GroundingSearchEnabled() {
        return Boolean.parseBoolean(properties.getProperty("flux2.grounding_search", "false"));
    }

    /**
     * Sets the FLUX.2 grounding search setting.
     *
     * @param enabled true to enable grounding search
     */
    public void setFlux2GroundingSearchEnabled(boolean enabled) {
        properties.setProperty("flux2.grounding_search", String.valueOf(enabled));
        saveConfiguration();
    }

    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
        saveConfiguration();
    }
    
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    /**
     * Removes a property from the configuration.
     * Used for cleaning up legacy entries.
     * 
     * @param key the property key to remove
     */
    public void removeProperty(String key) {
        properties.remove(key);
        saveConfiguration();
    }
    
    /**
     * Resets all configuration settings to default values.
     * 
     * <p><strong>Preserved Settings:</strong></p>
     * <ul>
     * <li>API Key ({@code api.key}) - User authentication preserved</li>
     * <li>Proxy Password ({@code proxy.password}) - Corporate authentication preserved</li>
     * </ul>
     * 
     * <p>All other settings including UI preferences, model defaults, 
     * and non-sensitive proxy settings are reset to defaults.</p>
     * 
     * @since 1.0.0
     */
    public void resetToDefaults() {
        // Preserve sensitive data
        String preservedApiKey = properties.getProperty("api.key");
        String preservedProxyPassword = properties.getProperty("proxy.password");
        
        // Clear all properties
        properties.clear();
        
        // Reload defaults
        loadDefaults();
        
        // Add additional default properties not included in loadDefaults()
        properties.setProperty("api.region", "Global");
        properties.setProperty("generation.default_prompt_upsampling", "false");
        
        // Restore preserved sensitive data
        if (preservedApiKey != null && !preservedApiKey.trim().isEmpty()) {
            properties.setProperty("api.key", preservedApiKey);
        }
        
        if (preservedProxyPassword != null && !preservedProxyPassword.trim().isEmpty()) {
            properties.setProperty("proxy.password", preservedProxyPassword);
        }
        
        // Save the reset configuration
        saveConfiguration();
        
        logger.info("Configuration reset to defaults (API key and proxy password preserved)");
    }
    
    // Corporate Proxy and Certificate Management
    
    /**
     * Gets the corporate proxy host.
     * 
     * <p>Retrieves proxy host from multiple sources in order of priority:</p>
     * <ol>
     * <li>Environment variable {@code PROXY_HOST}</li>
     * <li>System property {@code proxy.host}</li>
     * <li>Configuration file {@code proxy.host}</li>
     * </ol>
     * 
     * @return the proxy host, or null if not configured
     * @since 1.0.0
     */
    public String getProxyHost() {
        // 1. Check environment variable first (highest priority)
        String envHost = System.getenv("PROXY_HOST");
        if (envHost != null && !envHost.trim().isEmpty()) {
            return envHost.trim();
        }
        
        // 2. Check system property
        String sysHost = System.getProperty("proxy.host");
        if (sysHost != null && !sysHost.trim().isEmpty()) {
            return sysHost.trim();
        }
        
        // 3. Check configuration file
        String configHost = properties.getProperty("proxy.host");
        if (configHost != null && !configHost.trim().isEmpty()) {
            return configHost.trim();
        }
        
        return null;
    }
    
    /**
     * Sets the corporate proxy host.
     * 
     * @param proxyHost the proxy host to store
     * @since 1.0.0
     */
    public void setProxyHost(String proxyHost) {
        if (proxyHost == null || proxyHost.trim().isEmpty()) {
            properties.remove("proxy.host");
            if (getLoggingEnabled()) {
                logger.info("Proxy host removed from configuration");
            }
        } else {
            properties.setProperty("proxy.host", proxyHost.trim());
            if (getLoggingEnabled()) {
                logger.info("Proxy host updated in configuration");
            }
        }
        saveConfiguration();
    }
    
    /**
     * Gets the corporate proxy port.
     * 
     * @return the proxy port, or 8080 as default
     * @since 1.0.0
     */
    public int getProxyPort() {
        // Environment variable (highest priority)
        String envPort = System.getenv("PROXY_PORT");
        if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                return Integer.parseInt(envPort.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid proxy port in environment variable: {}", envPort);
            }
        }
        
        // System property
        String sysPort = System.getProperty("proxy.port");
        if (sysPort != null && !sysPort.trim().isEmpty()) {
            try {
                return Integer.parseInt(sysPort.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid proxy port in system property: {}", sysPort);
            }
        }
        
        // Configuration file
        return Integer.parseInt(properties.getProperty("proxy.port", "8080"));
    }
    
    /**
     * Sets the corporate proxy port.
     * 
     * @param proxyPort the proxy port to store
     * @since 1.0.0
     */
    public void setProxyPort(int proxyPort) {
        if (proxyPort <= 0 || proxyPort > 65535) {
            throw new IllegalArgumentException("Proxy port must be between 1 and 65535");
        }
        properties.setProperty("proxy.port", String.valueOf(proxyPort));
        saveConfiguration();
    }
    
    /**
     * Gets the corporate proxy username.
     * 
     * @return the proxy username, or null if not configured
     * @since 1.0.0
     */
    public String getProxyUsername() {
        // Environment variable (highest priority)
        String envUsername = System.getenv("PROXY_USERNAME");
        if (envUsername != null && !envUsername.trim().isEmpty()) {
            return envUsername.trim();
        }
        
        // System property
        String sysUsername = System.getProperty("proxy.username");
        if (sysUsername != null && !sysUsername.trim().isEmpty()) {
            return sysUsername.trim();
        }
        
        // Configuration file
        String configUsername = properties.getProperty("proxy.username");
        return (configUsername != null && !configUsername.trim().isEmpty()) ? configUsername.trim() : null;
    }
    
    /**
     * Sets the corporate proxy username.
     * 
     * @param proxyUsername the proxy username to store
     * @since 1.0.0
     */
    public void setProxyUsername(String proxyUsername) {
        if (proxyUsername == null || proxyUsername.trim().isEmpty()) {
            properties.remove("proxy.username");
        } else {
            properties.setProperty("proxy.username", proxyUsername.trim());
        }
        saveConfiguration();
    }
    
    /**
     * Gets the encrypted corporate proxy password.
     * 
     * @return the decrypted proxy password, or null if not configured
     * @since 1.0.0
     */
    public String getProxyPassword() {
        // Environment variable (highest priority)
        String envPassword = System.getenv("PROXY_PASSWORD");
        if (envPassword != null && !envPassword.trim().isEmpty()) {
            return envPassword.trim();
        }
        
        // System property
        String sysPassword = System.getProperty("proxy.password");
        if (sysPassword != null && !sysPassword.trim().isEmpty()) {
            return sysPassword.trim();
        }
        
        // Configuration file (encrypted)
        String encryptedPassword = properties.getProperty("proxy.password");
        if (encryptedPassword == null || encryptedPassword.trim().isEmpty()) {
            return null;
        }
        
        try {
            String decryptedPassword = art.moodframe.config.EncryptionUtil.decrypt(encryptedPassword);
            return decryptedPassword;
        } catch (Exception e) {
            logger.error("Failed to decrypt proxy password from configuration", e);
            return null;
        }
    }
    
    /**
     * Sets the corporate proxy password (encrypted).
     * 
     * @param proxyPassword the proxy password to store (will be encrypted)
     * @since 1.0.0
     */
    public void setProxyPassword(String proxyPassword) {
        if (proxyPassword == null || proxyPassword.trim().isEmpty()) {
            properties.remove("proxy.password");
            logger.info("Proxy password removed from configuration");
        } else {
            try {
                String encryptedPassword = art.moodframe.config.EncryptionUtil.encrypt(proxyPassword.trim());
                properties.setProperty("proxy.password", encryptedPassword);
                logger.info("Proxy password encrypted and stored in configuration");
            } catch (Exception e) {
                logger.error("Failed to encrypt proxy password", e);
                throw new RuntimeException("Failed to store proxy password", e);
            }
        }
        saveConfiguration();
    }
    
    /**
     * Checks if corporate proxy is enabled.
     * 
     * @return true if proxy is configured and enabled
     * @since 1.0.0
     */
    public boolean isProxyEnabled() {
        // If proxy host is configured, consider proxy enabled
        String proxyHost = getProxyHost();
        if (proxyHost != null && !proxyHost.trim().isEmpty()) {
            return Boolean.parseBoolean(properties.getProperty("proxy.enabled", "true"));
        }
        return false;
    }
    
    /**
     * Sets the corporate proxy enabled state.
     * 
     * @param proxyEnabled true to enable proxy, false to disable
     * @since 1.0.0
     */
    public void setProxyEnabled(boolean proxyEnabled) {
        properties.setProperty("proxy.enabled", String.valueOf(proxyEnabled));
        saveConfiguration();
    }
    
    /**
     * Gets the path to the ZScaler root certificate directory.
     * 
     * <p>The certificate directory is automatically determined from environment variables 
     * or defaults to the application's certificates directory. This path is not user-configurable.</p>
     * 
     * <p>Priority order:</p>
     * <ol>
     * <li>Environment variable {@code ZSCALER_CERT_PATH}</li>
     * <li>System property {@code zscaler.cert.path}</li>
     * <li>Default directory: {@code ~/.moodframe/certificates/}</li>
     * </ol>
     * 
     * @return the automatically determined certificate directory path
     * @since 1.0.0
     */
    public String getCertificatePath() {
        // Environment variable (highest priority)
        String envCertPath = System.getenv("ZSCALER_CERT_PATH");
        if (envCertPath != null && !envCertPath.trim().isEmpty()) {
            return envCertPath.trim();
        }
        
        // System property
        String sysCertPath = System.getProperty("zscaler.cert.path");
        if (sysCertPath != null && !sysCertPath.trim().isEmpty()) {
            return sysCertPath.trim();
        }
        
        // Default directory (auto-determined)
        String defaultPath = System.getProperty("user.home") + "/.moodframe/certificates/";
        return defaultPath;
    }
    
    /**
     * Checks if corporate proxy credentials are configured.
     * 
     * @return true if username and password are both configured
     * @since 1.0.0
     */
    public boolean hasProxyCredentials() {
        String username = getProxyUsername();
        String password = getProxyPassword();
        return username != null && !username.trim().isEmpty() && 
               password != null && !password.trim().isEmpty();
    }
    
    /**
     * Gets the source of the currently active proxy configuration.
     * 
     * @return "environment", "system_property", "configuration", or "none"
     * @since 1.0.0
     */
    public String getProxyConfigurationSource() {
        // Check environment variable
        String envHost = System.getenv("PROXY_HOST");
        if (envHost != null && !envHost.trim().isEmpty()) {
            return "environment";
        }
        
        // Check system property
        String sysHost = System.getProperty("proxy.host");
        if (sysHost != null && !sysHost.trim().isEmpty()) {
            return "system_property";
        }
        
        // Check configuration file
        String configHost = properties.getProperty("proxy.host");
        if (configHost != null && !configHost.trim().isEmpty()) {
            return "configuration";
        }
        
        return "none";
    }
    
    /**
     * Checks if the proxy configuration can be modified through the UI.
     * Environment variables and system properties take precedence and cannot be overridden.
     *
     * @return true if the proxy configuration can be modified through UI, false otherwise
     * @since 1.0.0
     */
    public boolean isProxyConfigurationEditable() {
        return "none".equals(getProxyConfigurationSource()) || "configuration".equals(getProxyConfigurationSource());
    }

    // Google Gemini API Configuration

    /**
     * Gets the Google Gemini API key.
     *
     * <p>Retrieves the API key from multiple sources in order of priority:</p>
     * <ol>
     * <li>Environment variable {@code GEMINI_API_KEY}</li>
     * <li>System property {@code gemini.api.key}</li>
     * <li>Encrypted key from configuration file</li>
     * </ol>
     *
     * @return the API key, or null if not configured
     * @since 1.5.0
     */
    public String getGeminiApiKey() {
        return readKey(GEMINI);
    }

    /**
     * Sets the Google Gemini API key.
     *
     * <p>Encrypts the provided API key and stores it in configuration.
     * If the key is null or empty, removes the API key from configuration.</p>
     *
     * @param apiKey the API key to store, or null to remove
     * @since 1.5.0
     */
    public void setGeminiApiKey(String apiKey) {
        storeKey(GEMINI, apiKey);
    }

    /**
     * Checks if a Gemini API key is configured from any source.
     *
     * @return true if a valid API key is configured, false otherwise
     * @since 1.5.0
     */
    public boolean hasGeminiApiKey() {
        return readKey(GEMINI) != null;
    }

    /**
     * Gets the source of the currently active Gemini API key.
     *
     * @return "environment", "system_property", "configuration", or "none"
     * @since 1.5.0
     */
    public String getGeminiApiKeySource() {
        return keySource(GEMINI);
    }

    /**
     * Checks if the Gemini API key can be modified through the UI.
     * Environment variables and system properties take precedence and cannot be overridden.
     *
     * @return true if the API key can be modified through UI, false otherwise
     * @since 1.5.0
     */
    public boolean isGeminiApiKeyEditable() {
        String source = keySource(GEMINI);
        return "none".equals(source) || "configuration".equals(source);
    }

    // OpenAI API Configuration

    /** Default base URL of the OpenAI API. */
    public static final String OPENAI_DEFAULT_BASE_URL = "https://api.openai.com/v1";

    /**
     * Gets the OpenAI bearer token.
     *
     * <p>Sources in order of priority: environment variable {@code OPENAI_API_KEY}, system property
     * {@code openai.api.key}, encrypted key from the configuration file.</p>
     *
     * @return the bearer token, or null if not configured
     * @since 1.9.0
     */
    public String getOpenAiApiKey() {
        return readKey(OPENAI);
    }

    /**
     * Sets the OpenAI bearer token (stored encrypted); null or empty removes it.
     *
     * @param apiKey the bearer token to store, or null to remove
     * @since 1.9.0
     */
    public void setOpenAiApiKey(String apiKey) {
        storeKey(OPENAI, apiKey);
    }

    /**
     * Checks if an OpenAI bearer token is configured from any source.
     *
     * @return true if a token is configured
     * @since 1.9.0
     */
    public boolean hasOpenAiApiKey() {
        return readKey(OPENAI) != null;
    }

    /**
     * Gets the source of the currently active OpenAI bearer token.
     *
     * @return "environment", "system_property", "configuration", or "none"
     * @since 1.9.0
     */
    public String getOpenAiApiKeySource() {
        return keySource(OPENAI);
    }

    /**
     * Checks if the OpenAI bearer token can be modified through the UI.
     *
     * @return true if the token comes from the configuration file or is not set
     * @since 1.9.0
     */
    public boolean isOpenAiApiKeyEditable() {
        String source = keySource(OPENAI);
        return "none".equals(source) || "configuration".equals(source);
    }

    /**
     * Gets the base URL of the OpenAI API (without trailing slash).
     *
     * @return the configured base URL, default {@link #OPENAI_DEFAULT_BASE_URL}
     * @since 1.9.0
     */
    public String getOpenAiBaseUrl() {
        String url = properties.getProperty("openai.base_url");
        if (url == null || url.trim().isEmpty()) {
            return OPENAI_DEFAULT_BASE_URL;
        }
        return url.trim().replaceAll("/+$", "");
    }

    /**
     * Sets the base URL of the OpenAI API; null or empty resets to the default.
     *
     * @param baseUrl the base URL, for example {@code https://api.openai.com/v1}
     * @since 1.9.0
     */
    public void setOpenAiBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty() || baseUrl.trim().replaceAll("/+$", "").equals(OPENAI_DEFAULT_BASE_URL)) {
            properties.remove("openai.base_url");
        } else {
            properties.setProperty("openai.base_url", baseUrl.trim().replaceAll("/+$", ""));
        }
        saveConfiguration();
    }

    /**
     * Gets the models the user selected for image generation. The list is empty until a model was selected.
     *
     * @return the model ids
     * @since 1.9.0
     */
    public java.util.List<String> getOpenAiModels() {
        return splitIds(properties.getProperty("provider.openai.models", ""));
    }

    /**
     * Stores the models selected for image generation, so they are available at the next start before the
     * connection is checked again.
     *
     * @param models the model ids
     * @since 1.9.0
     */
    public void setOpenAiModels(java.util.List<String> models) {
        properties.setProperty("provider.openai.models", String.join(",", models));
        saveConfiguration();
    }

    /**
     * Checks if the user (or the first connection check) already chose the image models.
     *
     * @return true if a selection is stored, even an empty one
     * @since 1.9.0
     */
    public boolean hasOpenAiModelSelection() {
        return properties.containsKey("provider.openai.models");
    }

    /**
     * Gets all models the OpenAI API listed at the last successful connection check. The standard model list
     * has no capability field, so this includes chat, embedding and other models.
     *
     * @return the model ids, empty if there was no successful check yet
     * @since 1.9.0
     */
    public java.util.List<String> getOpenAiAvailableModels() {
        return splitIds(properties.getProperty("provider.openai.available", ""));
    }

    /**
     * Stores all models the OpenAI API listed.
     *
     * @param models the model ids
     * @since 1.9.0
     */
    public void setOpenAiAvailableModels(java.util.List<String> models) {
        properties.setProperty("provider.openai.available", String.join(",", models));
        saveConfiguration();
    }

    /**
     * Gets the language model the brainstorm uses with OpenAI.
     *
     * @return the model id, empty if none is chosen
     * @since 1.9.0
     */
    public String getOpenAiChatModel() {
        return properties.getProperty("provider.openai.chat_model", "");
    }

    /**
     * Sets the language model the brainstorm uses with OpenAI.
     *
     * @param model the model id, null or empty for none
     * @since 1.9.0
     */
    public void setOpenAiChatModel(String model) {
        properties.setProperty("provider.openai.chat_model", model == null ? "" : model.trim());
        saveConfiguration();
    }

    /**
     * Gets the AI the brainstorm talks to.
     *
     * @return {@code "openai"} or {@code "gemini"} (default)
     * @since 1.9.0
     */
    public String getBrainstormEngine() {
        return "openai".equals(properties.getProperty("brainstorm.engine", "gemini")) ? "openai" : "gemini";
    }

    /**
     * Sets the AI the brainstorm talks to.
     *
     * @param engine {@code "openai"} or {@code "gemini"}
     * @since 1.9.0
     */
    public void setBrainstormEngine(String engine) {
        properties.setProperty("brainstorm.engine", "openai".equals(engine) ? "openai" : "gemini");
        saveConfiguration();
    }

    /**
     * Checks if the brainstorm can use OpenAI: the provider is enabled and a language model is chosen.
     *
     * @return true if OpenAI can answer in the brainstorm
     * @since 1.9.0
     */
    public boolean isOpenAiBrainstormAvailable() {
        return hasOpenAiApiKey() && isOpenAiProviderEnabled() && !getOpenAiChatModel().isBlank();
    }

    private static java.util.List<String> splitIds(String ids) {
        return ids.isBlank() ? java.util.List.of() : java.util.List.of(ids.split(","));
    }

    /**
     * Gets the OpenAI validation status from the last connection check.
     *
     * @return true if the last check was successful
     * @since 1.9.0
     */
    public boolean isOpenAiProviderValidated() {
        return Boolean.parseBoolean(properties.getProperty("provider.openai.validated", "false"));
    }

    /**
     * Sets the OpenAI validation status.
     *
     * @param validated true if the connection check was successful
     * @since 1.9.0
     */
    public void setOpenAiProviderValidated(boolean validated) {
        properties.setProperty("provider.openai.validated", String.valueOf(validated));
        saveConfiguration();
    }

    /**
     * Gets the OpenAI provider enabled/active state.
     *
     * @return true if the OpenAI provider is enabled
     * @since 1.9.0
     */
    public boolean isOpenAiProviderEnabled() {
        return Boolean.parseBoolean(properties.getProperty("provider.openai.enabled", "false"));
    }

    /**
     * Sets the OpenAI provider enabled/active state.
     *
     * @param enabled true to enable the OpenAI provider
     * @since 1.9.0
     */
    public void setOpenAiProviderEnabled(boolean enabled) {
        properties.setProperty("provider.openai.enabled", String.valueOf(enabled));
        saveConfiguration();
    }

    /**
     * Checks if the OpenAI provider enabled state has ever been explicitly set.
     *
     * @return true if a user preference exists
     * @since 1.9.0
     */
    public boolean hasOpenAiProviderEnabledPreference() {
        return properties.containsKey("provider.openai.enabled");
    }

    // API Provider Validation Status

    /**
     * Gets the BFL API validation status from last startup check.
     *
     * @return true if last validation was successful, false otherwise
     * @since 1.5.0
     */
    public boolean isBflProviderValidated() {
        return Boolean.parseBoolean(properties.getProperty("provider.bfl.validated", "false"));
    }

    /**
     * Sets the BFL API validation status.
     *
     * @param validated true if validation was successful
     * @since 1.5.0
     */
    public void setBflProviderValidated(boolean validated) {
        properties.setProperty("provider.bfl.validated", String.valueOf(validated));
        saveConfiguration();
    }

    /**
     * Gets the Gemini API validation status from last startup check.
     *
     * @return true if last validation was successful, false otherwise
     * @since 1.5.0
     */
    public boolean isGeminiProviderValidated() {
        return Boolean.parseBoolean(properties.getProperty("provider.gemini.validated", "false"));
    }

    /**
     * Sets the Gemini API validation status.
     *
     * @param validated true if validation was successful
     * @since 1.5.0
     */
    public void setGeminiProviderValidated(boolean validated) {
        properties.setProperty("provider.gemini.validated", String.valueOf(validated));
        saveConfiguration();
    }

    /**
     * Gets the BFL provider enabled/active state.
     *
     * @return true if BFL provider is enabled, false otherwise
     * @since 1.5.0
     */
    public boolean isBflProviderEnabled() {
        return Boolean.parseBoolean(properties.getProperty("provider.bfl.enabled", "false"));
    }

    /**
     * Sets the BFL provider enabled/active state.
     *
     * @param enabled true to enable BFL provider
     * @since 1.5.0
     */
    public void setBflProviderEnabled(boolean enabled) {
        properties.setProperty("provider.bfl.enabled", String.valueOf(enabled));
        saveConfiguration();
    }

    /**
     * Gets the Gemini provider enabled/active state.
     *
     * @return true if Gemini provider is enabled, false otherwise
     * @since 1.5.0
     */
    public boolean isGeminiProviderEnabled() {
        return Boolean.parseBoolean(properties.getProperty("provider.gemini.enabled", "false"));
    }

    /**
     * Sets the Gemini provider enabled/active state.
     *
     * @param enabled true to enable Gemini provider
     * @since 1.5.0
     */
    public void setGeminiProviderEnabled(boolean enabled) {
        properties.setProperty("provider.gemini.enabled", String.valueOf(enabled));
        saveConfiguration();
    }

    /**
     * Checks if the BFL provider enabled state has ever been explicitly set.
     * This is used to distinguish between default state and user preference.
     *
     * @return true if the property exists in the config file
     * @since 1.5.0
     */
    public boolean hasBflProviderEnabledPreference() {
        return properties.containsKey("provider.bfl.enabled");
    }

    /**
     * Checks if the Gemini provider enabled state has ever been explicitly set.
     * This is used to distinguish between default state and user preference.
     *
     * @return true if the property exists in the config file
     * @since 1.5.0
     */
    public boolean hasGeminiProviderEnabledPreference() {
        return properties.containsKey("provider.gemini.enabled");
    }
}