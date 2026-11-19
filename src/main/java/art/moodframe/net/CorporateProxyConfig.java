package art.moodframe.net;

import art.moodframe.config.ApplicationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration management for corporate proxy settings and certificate handling.
 * 
 * <p>This class manages corporate proxy configuration for enterprise environments
 * with support for ZScaler and other corporate proxies that intercept HTTPS traffic.</p>
 * 
 * <p><strong>Corporate Proxy Support:</strong></p>
 * <ul>
 * <li>HTTP/HTTPS proxy configuration with authentication</li>
 * <li>ZScaler root certificate integration</li>
 * <li>Multi-source configuration (Environment, System Properties, Config File)</li>
 * <li>Certificate format support: .crt, .cer, .pem</li>
 * </ul>
 * 
 * <p><strong>External References:</strong></p>
 * <ul>
 * <li><a href="https://docs.bfl.ai">BFL API Documentation</a></li>
 * <li><a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html">Java HTTP Client Documentation</a></li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class CorporateProxyConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(CorporateProxyConfig.class);
    
    private final ApplicationConfig applicationConfig;
    
    // Proxy Configuration Properties
    private String proxyHost;
    private int proxyPort;
    private String proxyUsername;
    private String proxyPassword;
    private boolean proxyEnabled;
    
    // Certificate Management
    private List<Path> certificatePaths;
    
    /**
     * Creates a new corporate proxy configuration instance.
     * 
     * @param applicationConfig the application configuration instance
     */
    public CorporateProxyConfig(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
        this.certificatePaths = new ArrayList<>();
        
        // Initialize configuration from multiple sources
        loadProxyConfiguration();
    }
    
    /**
     * Loads proxy configuration from multiple sources with priority hierarchy.
     * 
     * @since 1.0.0
     */
    public void loadProxyConfiguration() {
        logger.debug("Loading corporate proxy configuration from multiple sources");
        
        // Load proxy settings from ApplicationConfig (which handles priority)
        this.proxyHost = applicationConfig.proxy().getProxyHost();
        this.proxyPort = applicationConfig.proxy().getProxyPort();
        this.proxyUsername = applicationConfig.proxy().getProxyUsername();
        this.proxyPassword = applicationConfig.proxy().getProxyPassword();
        this.proxyEnabled = applicationConfig.proxy().isProxyEnabled();
        
        // Log configuration source for debugging
        String configSource = applicationConfig.proxy().getProxyConfigurationSource();
        if (!"none".equals(configSource)) {
            logger.debug("Corporate proxy configured from source: {} ({}:{})",
                configSource, proxyHost, proxyPort);
        }
        
        // Load certificate paths
        loadCertificatePaths();
        
        // Auto-detection as fallback
        if (!isProxyConfigured()) {
            detectSystemProxy();
        }
    }
    
    /**
     * Loads certificate paths from configuration.
     */
    private void loadCertificatePaths() {
        certificatePaths.clear();
        
        // Get certificate path from configuration
        String certPath = applicationConfig.proxy().getCertificatePath();
        if (certPath == null) {
            return;
        }
        Path certDirectory = Paths.get(certPath);
        
        try {
            // Ensure certificate directory exists
            if (!Files.exists(certDirectory)) {
                Files.createDirectories(certDirectory);
            }
            
            // Scan for certificate files
            if (Files.isDirectory(certDirectory)) {
                Files.list(certDirectory)
                    .filter(path -> isCertificateFile(path))
                    .forEach(certificatePaths::add);
                    
                logger.debug("Found {} certificate files in directory: {}",
                    certificatePaths.size(), certDirectory);
            } else if (Files.exists(certDirectory) && isCertificateFile(certDirectory)) {
                // Single certificate file
                certificatePaths.add(certDirectory);
                logger.debug("Found single certificate file: {}", certDirectory);
            }
            
        } catch (Exception e) {
            logger.error("Failed to load certificate paths from: {}", certDirectory, e);
        }
    }
    
    /**
     * Checks if a file is a supported certificate format.
     */
    private boolean isCertificateFile(Path path) {
        return CertificateStore.isCertificateFile(path);
    }
    
    /**
     * Attempts to detect system proxy settings automatically.
     */
    private void detectSystemProxy() {
        try {
            // Use system proxy selector to detect proxy
            ProxySelector defaultSelector = ProxySelector.getDefault();
            if (defaultSelector != null) {
                var proxies = defaultSelector.select(URI.create("https://api.bfl.ai"));
                
                for (var proxy : proxies) {
                    if (proxy.type() == java.net.Proxy.Type.HTTP && proxy.address() instanceof InetSocketAddress) {
                        InetSocketAddress address = (InetSocketAddress) proxy.address();
                        
                        this.proxyHost = address.getHostName();
                        this.proxyPort = address.getPort();
                        this.proxyEnabled = true;
                        
                        logger.debug("Auto-detected system proxy: {}:{}", proxyHost, proxyPort);
                        return;
                    }
                }
            }
            
        } catch (Exception e) {
            logger.warn("Failed to auto-detect system proxy settings", e);
        }
    }
    
    /**
     * Validates the current proxy configuration.
     * 
     * @return true if proxy configuration is valid and complete
     */
    public boolean isProxyConfigurationValid() {
        if (!proxyEnabled) {
            return true; // No proxy is valid
        }
        
        // Check required fields
        if (proxyHost == null || proxyHost.trim().isEmpty()) {
            logger.warn("Proxy configuration invalid: missing proxy host");
            return false;
        }
        
        if (proxyPort <= 0 || proxyPort > 65535) {
            logger.warn("Proxy configuration invalid: invalid port number: {}", proxyPort);
            return false;
        }
        
        return true;
    }
    
    /**
     * Checks if proxy is configured (regardless of enabled state).
     */
    public boolean isProxyConfigured() {
        return proxyHost != null && !proxyHost.trim().isEmpty() && proxyPort > 0;
    }
    
    // Getters and Setters
    
    public String getProxyHost() {
        return proxyHost;
    }
    
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
        applicationConfig.proxy().setProxyHost(proxyHost);
    }
    
    public int getProxyPort() {
        return proxyPort;
    }
    
    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
        applicationConfig.proxy().setProxyPort(proxyPort);
    }
    
    public String getProxyUsername() {
        return proxyUsername;
    }
    
    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
        applicationConfig.proxy().setProxyUsername(proxyUsername);
    }
    
    public String getProxyPassword() {
        return proxyPassword;
    }
    
    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
        applicationConfig.proxy().setProxyPassword(proxyPassword);
    }
    
    public boolean isProxyEnabled() {
        return proxyEnabled;
    }
    
    public void setProxyEnabled(boolean proxyEnabled) {
        this.proxyEnabled = proxyEnabled;
        applicationConfig.proxy().setProxyEnabled(proxyEnabled);
    }
    
    public List<Path> getCertificatePaths() {
        return new ArrayList<>(certificatePaths);
    }
    
    
    /**
     * Checks if proxy authentication is configured.
     */
    public boolean hasProxyAuthentication() {
        return applicationConfig.proxy().hasProxyCredentials();
    }
    
    /**
     * Gets the source of the current proxy configuration.
     */
    public String getConfigurationSource() {
        return applicationConfig.proxy().getProxyConfigurationSource();
    }
}
