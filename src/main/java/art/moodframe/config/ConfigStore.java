package art.moodframe.config;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The settings file {@code config.properties}: its properties, the immediate and the delayed writing, and the
 * secrets, which are stored encrypted.
 *
 * <p>The sections of {@link ApplicationConfig} ({@link UiState}, {@link GenerationDefaults},
 * {@link ProviderAccounts}, {@link ProxySettings}, {@link McpSettings}) read and write their keys here; the keys
 * are those of all former versions, so an existing file loads unchanged.</p>
 */
class ConfigStore {

    private static final Logger logger = LoggerFactory.getLogger(ConfigStore.class);

    /** Quiet time after the last change of a dragged value (divider, window, slider) before it is written. */
    static final long SAVE_DELAY_MILLIS = 500;

    /*  One writer thread for the delayed saves, created on the first one; static, so mocks in tests have it too  */
    private static final ScheduledExecutorService WRITER = Executors.newSingleThreadScheduledExecutor((runnable) -> {
        Thread thread = new Thread(runnable, "config-writer");
        thread.setDaemon(true);
        return thread;
    });
    private static final Object PENDING_LOCK = new Object();

    private final Properties properties = new Properties();
    private final LibraryPaths paths;
    private final Path configPath;
    private final EncryptionUtil encryption;
    private ScheduledFuture<?> pendingSave;

    /**
     * Reads the settings file of the library, or creates it with the defaults.
     *
     * @param paths the folders of the library
     */
    ConfigStore(LibraryPaths paths) {
        this.paths = paths;
        this.configPath = paths.configFile();
        this.encryption = new EncryptionUtil(paths.secretKey());
        initializeDirectories();
        load();
    }

    /** @return the folders of the library with the default images folder */
    LibraryPaths paths() {
        return paths;
    }

    /*  The folders of the library, the database, the logs and the images  */
    private void initializeDirectories() {
        try {
            Files.createDirectories(paths.root());
            Files.createDirectories(paths.databaseDirectory());
            Files.createDirectories(paths.logs());
            Files.createDirectories(paths.images());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create configuration directories", e);
        }
    }

    /*  Reads the file; without one, or if it cannot be read, the defaults; a missing file is written at once  */
    private void load() {
        if (Files.exists(configPath)) {
            try (FileInputStream fis = new FileInputStream(configPath.toFile())) {
                properties.load(fis);
            } catch (IOException e) {
                loadDefaults();
            }
        } else {
            loadDefaults();
            save();
        }
    }

    /**
     * Sets the values a fresh configuration writes: the BFL connection, theme, language and window size, the folders
     * of the library, the model defaults and the environment.
     */
    void loadDefaults() {
        properties.setProperty("api.base_url", "https://api.bfl.ai");
        properties.setProperty("api.timeout_seconds", "600");

        properties.setProperty("ui.theme", "primer-dark");
        properties.setProperty("ui.language", "de");
        properties.setProperty("ui.window.width", "1200");
        properties.setProperty("ui.window.height", "800");

        properties.setProperty("path.database", paths.database().toString());
        properties.setProperty("path.logs", paths.logs().toString());
        properties.setProperty("path.images", paths.images().toString());

        properties.setProperty("model.default", "flux.3-image");
        properties.setProperty("model.default_aspect_ratio", "16:9");
        properties.setProperty("model.default_image_count", "1");
        properties.setProperty("model.default_safety_tolerance", "2");

        properties.setProperty("dev.environment", detectEnvironment());
    }

    /*  "dev" in the IDE or with Gradle (or as the system property moodframe.env says), else "prod"  */
    private static String detectEnvironment() {
        String env = System.getProperty("moodframe.env");
        if (env != null)
            return env;
        return System.getProperty("java.class.path").contains("gradle") ? "dev" : "prod";
    }

    /**
     * Resets every setting to its default, except the secrets.
     */
    void resetToDefaults() {
        Map<String, String> secrets = new HashMap<>();
        for (Secret secret : Secret.values()) {
            String value = properties.getProperty(secret.key());
            if (value != null && !value.isBlank())
                secrets.put(secret.key(), value);
        }

        properties.clear();
        loadDefaults();
        properties.setProperty("api.region", "Global");
        properties.setProperty("generation.default_prompt_upsampling", "false");
        secrets.forEach(properties::setProperty);
        save();
    }

    /*  ===== Properties =====  */

    /**
     * Gets a value.
     *
     * @param key the key
     * @return the value, or null if not set
     */
    public String get(String key) {
        return properties.getProperty(key);
    }

    /**
     * Gets a value.
     *
     * @param key          the key
     * @param defaultValue the value if none is set
     * @return the value
     */
    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Gets a boolean value.
     *
     * @param key          the key
     * @param defaultValue the value if none is set
     * @return the value
     */
    boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(properties.getProperty(key, String.valueOf(defaultValue)));
    }

    /**
     * Tells whether a value is set.
     *
     * @param key the key
     * @return true if set
     */
    boolean contains(String key) {
        return properties.containsKey(key);
    }

    /**
     * Sets a value without writing the file; {@link #save()} or {@link #saveSoon()} follows.
     *
     * @param key   the key
     * @param value the value
     */
    void put(String key, String value) {
        properties.setProperty(key, value);
    }

    /**
     * Removes a value without writing the file.
     *
     * @param key the key
     */
    void remove(String key) {
        properties.remove(key);
    }

    /**
     * Sets a value and writes the file at once.
     *
     * @param key   the key
     * @param value the value
     */
    void set(String key, String value) {
        properties.setProperty(key, value);
        save();
    }

    /*  ===== Writing =====  */

    /**
     * Writes the file now, replacing it atomically; calls are serialized. A failure is logged, not thrown.
     */
    public synchronized void save() {
        /*  Write beside the file and move it over, so a crash never leaves a truncated configuration  */
        Path temp = configPath.resolveSibling(configPath.getFileName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(temp.toFile())) {
            properties.store(fos, "MoodFrame Configuration");
            fos.getFD().sync();
            Files.move(temp, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.error("Failed to save the configuration to {}", configPath, e);
        }
    }

    /**
     * Writes the file on the writer thread once no further change came for {@link #SAVE_DELAY_MILLIS}.
     *
     * <p>For values that change with every pixel of a drag, so that a drag writes the file once and the JavaFX
     * thread never waits for the disk.</p>
     */
    void saveSoon() {
        synchronized (PENDING_LOCK) {
            if (pendingSave != null)
                pendingSave.cancel(false);
            pendingSave = WRITER.schedule(this::save, SAVE_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Writes a delayed save now instead of later; called on shutdown, so no change of the last moments gets lost.
     */
    void flush() {
        ScheduledFuture<?> pending;
        synchronized (PENDING_LOCK) {
            pending = pendingSave;
            pendingSave = null;
        }
        if (pending == null)
            return;
        if (pending.cancel(false)) {
            save();
            return;
        }

        /*  The writer has already started it: wait until the file is written  */
        try {
            pending.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            logger.error("Failed to save the configuration to {}", configPath, e);
        }
    }

    /*  ===== Secrets =====  */

    /**
     * Reads a secret: environment, then system property, then the decrypted configuration value.
     *
     * @param secret the secret
     * @return the value, or null if none
     */
    String readSecret(Secret secret) {
        String value = System.getenv(secret.environment());
        if (value == null || value.isBlank())
            value = System.getProperty(secret.property());
        if (value != null && !value.isBlank())
            return value.trim();

        String encrypted = properties.getProperty(secret.key());
        if (encrypted == null || encrypted.isBlank())
            return null;
        try {
            String decrypted = encryption.decrypt(encrypted);
            return decrypted == null || decrypted.isBlank() ? null : decrypted;
        } catch (Exception e) {
            logger.error("Failed to decrypt {} from configuration", secret.label(), e);
            return null;
        }
    }

    /**
     * Stores a secret encrypted and writes the file; null or empty removes it.
     *
     * @param secret the secret
     * @param value  the value
     */
    void storeSecret(Secret secret, String value) {
        if (value == null || value.trim().isEmpty()) {
            properties.remove(secret.key());
            logger.info("{} removed from configuration", secret.label());
        } else {
            try {
                properties.setProperty(secret.key(), encryption.encrypt(value.trim()));
                logger.info("{} encrypted and stored in configuration", secret.label());
            } catch (Exception e) {
                logger.error("Failed to encrypt {}", secret.label(), e);
                throw new RuntimeException("Failed to store " + secret.label(), e);
            }
        }
        save();
    }

    /**
     * Tells where the active value of a secret comes from.
     *
     * @param secret the secret
     * @return "environment", "system_property", "configuration" or "none"
     */
    String secretSource(Secret secret) {
        String env = System.getenv(secret.environment());
        if (env != null && !env.isBlank())
            return "environment";
        String property = System.getProperty(secret.property());
        if (property != null && !property.isBlank())
            return "system_property";
        String encrypted = properties.getProperty(secret.key());
        return encrypted != null && !encrypted.isBlank() ? "configuration" : "none";
    }

    /**
     * Tells whether the user may change a secret in the settings: not if the environment or a system property sets
     * it, as they take precedence.
     *
     * @param secret the secret
     * @return true if editable
     */
    boolean isSecretEditable(Secret secret) {
        String source = secretSource(secret);
        return "none".equals(source) || "configuration".equals(source);
    }
}
