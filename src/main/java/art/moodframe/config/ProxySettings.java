package art.moodframe.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The corporate proxy and the folder of its root certificates.
 *
 * <p>Each value comes from an environment variable, a system property or the configuration file, in this order of
 * priority; the settings can change only the last. The password is a {@link Secret}, stored encrypted.</p>
 */
public class ProxySettings {

    private static final Logger logger = LoggerFactory.getLogger(ProxySettings.class);

    private final ConfigStore store;
    private final LibraryPaths paths;

    ProxySettings(ConfigStore store, LibraryPaths paths) {
        this.store = store;
        this.paths = paths;
    }

    /*  the trimmed value of the environment variable, else of the system property, else null  */
    private static String external(String environment, String property) {
        String value = System.getenv(environment);
        if (value == null || value.trim().isEmpty())
            value = System.getProperty(property);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /** @return the proxy host from {@code PROXY_HOST}, {@code proxy.host} or the file, or null if none */
    public String getProxyHost() {
        String host = external("PROXY_HOST", "proxy.host");
        if (host != null)
            return host;
        String configHost = store.get("proxy.host");
        return configHost != null && !configHost.trim().isEmpty() ? configHost.trim() : null;
    }

    /** @param proxyHost the proxy host; null or empty removes it */
    public void setProxyHost(String proxyHost) {
        if (proxyHost == null || proxyHost.trim().isEmpty()) {
            store.remove("proxy.host");
            logger.debug("Proxy host removed from configuration");
        } else {
            store.put("proxy.host", proxyHost.trim());
            logger.debug("Proxy host updated in configuration");
        }
        store.save();
    }

    /** @return the proxy port from {@code PROXY_PORT}, {@code proxy.port} or the file, 8080 by default */
    public int getProxyPort() {
        String envPort = System.getenv("PROXY_PORT");
        if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                return Integer.parseInt(envPort.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid proxy port in environment variable: {}", envPort);
            }
        }
        String sysPort = System.getProperty("proxy.port");
        if (sysPort != null && !sysPort.trim().isEmpty()) {
            try {
                return Integer.parseInt(sysPort.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid proxy port in system property: {}", sysPort);
            }
        }
        return Integer.parseInt(store.get("proxy.port", "8080"));
    }

    /**
     * @param proxyPort the proxy port
     * @throws IllegalArgumentException if the port is not between 1 and 65535
     */
    public void setProxyPort(int proxyPort) {
        if (proxyPort <= 0 || proxyPort > 65535)
            throw new IllegalArgumentException("Proxy port must be between 1 and 65535");
        store.set("proxy.port", String.valueOf(proxyPort));
    }

    /** @return the proxy user from {@code PROXY_USERNAME}, {@code proxy.username} or the file, or null if none */
    public String getProxyUsername() {
        String username = external("PROXY_USERNAME", "proxy.username");
        if (username != null)
            return username;
        String configUsername = store.get("proxy.username");
        return configUsername != null && !configUsername.trim().isEmpty() ? configUsername.trim() : null;
    }

    /** @param proxyUsername the proxy user; null or empty removes it */
    public void setProxyUsername(String proxyUsername) {
        if (proxyUsername == null || proxyUsername.trim().isEmpty())
            store.remove("proxy.username");
        else
            store.put("proxy.username", proxyUsername.trim());
        store.save();
    }

    /** @return the proxy password from {@code PROXY_PASSWORD}, {@code proxy.password} or decrypted from the file */
    public String getProxyPassword() {
        return store.readSecret(Secret.PROXY_PASSWORD);
    }

    /** @param proxyPassword the proxy password, stored encrypted; null or empty removes it */
    public void setProxyPassword(String proxyPassword) {
        store.storeSecret(Secret.PROXY_PASSWORD, proxyPassword);
    }

    /** @return true if a proxy host is configured and the proxy is not switched off */
    public boolean isProxyEnabled() {
        String proxyHost = getProxyHost();
        if (proxyHost != null && !proxyHost.trim().isEmpty())
            return store.getBoolean("proxy.enabled", true);
        return false;
    }

    /** @param proxyEnabled false to switch the configured proxy off */
    public void setProxyEnabled(boolean proxyEnabled) {
        store.set("proxy.enabled", String.valueOf(proxyEnabled));
    }

    /** @return true if user and password of the proxy are configured */
    public boolean hasProxyCredentials() {
        String username = getProxyUsername();
        String password = getProxyPassword();
        return username != null && !username.trim().isEmpty() && password != null && !password.trim().isEmpty();
    }

    /** @return where the proxy host comes from: "environment", "system_property", "configuration" or "none" */
    public String getProxyConfigurationSource() {
        String envHost = System.getenv("PROXY_HOST");
        if (envHost != null && !envHost.trim().isEmpty())
            return "environment";
        String sysHost = System.getProperty("proxy.host");
        if (sysHost != null && !sysHost.trim().isEmpty())
            return "system_property";
        String configHost = store.get("proxy.host");
        if (configHost != null && !configHost.trim().isEmpty())
            return "configuration";
        return "none";
    }

    /** @return false if the environment or a system property sets the proxy, which the settings cannot override */
    public boolean isProxyConfigurationEditable() {
        String source = getProxyConfigurationSource();
        return "none".equals(source) || "configuration".equals(source);
    }

    /**
     * Gets the folder of the corporate root certificates: from {@code ZSCALER_CERT_PATH}, {@code zscaler.cert.path}
     * or {@code ~/.moodframe/certificates/}.
     *
     * @return the folder, with a trailing slash in the default
     */
    public String getCertificatePath() {
        String path = external("ZSCALER_CERT_PATH", "zscaler.cert.path");
        return path != null ? path : paths.certificates() + "/";
    }
}
