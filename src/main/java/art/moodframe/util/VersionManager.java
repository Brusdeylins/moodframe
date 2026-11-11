package art.moodframe.util;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Utility class for managing application version information.
 * 
 * <p>
 * This class reads version information from the JAR manifest or
 * properties files created during the build process. It provides
 * a consistent way to access version information throughout the
 * application.
 * </p>
 * 
 * <p>
 * <strong>Version Sources (in order of preference):</strong>
 * </p>
 * <ol>
 * <li>JAR Manifest - Implementation-Version (production builds)</li>
 * <li>Build properties file - build.version (development)</li>
 * <li>Fallback - "DEV-Build" for development environments</li>
 * </ol>
 * 
 * <p>
 * <strong>Usage Example:</strong>
 * </p>
 * <pre>
 * String version = VersionManager.getVersion();
 * String fullInfo = VersionManager.getVersionInfo();
 * </pre>
 * 
 * @since 1.0.0 Epic 9 - Internationalization
 */
public class VersionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(VersionManager.class);
    
    // Cached version information
    private static volatile String cachedVersion = null;
    private static volatile String cachedBuildInfo = null;
    
    // Constants
    private static final String DEV_BUILD_VERSION = "DEV-Build";
    private static final String BUILD_PROPERTIES_PATH = "/build.properties";
    private static final String UNKNOWN_VERSION = "Unknown";
    
    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with static methods only.
     */
    private VersionManager() {
        // Utility class - no instances allowed
    }
    
    /**
     * Gets the application version string.
     * 
     * <p>
     * This method returns the version in the following format:
     * </p>
     * <ul>
     * <li><strong>Production:</strong> "1.0.0" (from JAR manifest)</li>
     * <li><strong>Development:</strong> "DEV-Build" (when running from IDE)</li>
     * <li><strong>Build:</strong> Version from build.properties if available</li>
     * </ul>
     * 
     * @return the application version string (never null)
     */
    public static String getVersion() {
        if (cachedVersion == null) {
            synchronized (VersionManager.class) {
                if (cachedVersion == null) {
                    cachedVersion = detectVersion();
                }
            }
        }
        return cachedVersion;
    }
    
    /**
     * Gets detailed version information including build details.
     * 
     * <p>
     * Returns a formatted string with version and additional build
     * information such as build timestamp, Git commit, etc.
     * </p>
     * 
     * @return detailed version information
     */
    public static String getVersionInfo() {
        if (cachedBuildInfo == null) {
            synchronized (VersionManager.class) {
                if (cachedBuildInfo == null) {
                    cachedBuildInfo = buildVersionInfo();
                }
            }
        }
        return cachedBuildInfo;
    }
    
    /**
     * Gets the application name with version.
     * 
     * @return formatted application name and version
     */
    public static String getApplicationNameWithVersion() {
        return "Moodframe v" + getVersion();
    }
    
    
    /**
     * Checks if this is a production build.
     * 
     * @return true if running as a packaged application
     */
    public static boolean isProductionBuild() {
        String version = getVersion();
        return !DEV_BUILD_VERSION.equals(version) && !UNKNOWN_VERSION.equals(version);
    }
    
    /**
     * Clears the cached version information.
     * 
     * <p>
     * This method is primarily for testing purposes to force
     * re-detection of version information.
     * </p>
     */
    public static synchronized void clearCache() {
        cachedVersion = null;
        cachedBuildInfo = null;
    }
    
    /**
     * Detects the application version from various sources.
     * 
     * @return the detected version string
     */
    private static String detectVersion() {
        
        // 1. Try to get version from JAR manifest (production builds)
        String manifestVersion = getVersionFromManifest();
        if (manifestVersion != null && !manifestVersion.trim().isEmpty()) {
            ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
            if (config != null && config.getLoggingEnabled()) {
                logger.info("Version detected from JAR manifest: {}", manifestVersion);
            }
            return manifestVersion;
        }
        
        // 2. Try to get version from build properties file
        String buildVersion = getVersionFromBuildProperties();
        if (buildVersion != null && !buildVersion.trim().isEmpty()) {
            ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
            if (config != null && config.getLoggingEnabled()) {
                logger.info("Version detected from build properties: {}", buildVersion);
            }
            return buildVersion;
        }
        
        // 3. Development fallback
        ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        if (config != null && config.getLoggingEnabled()) {
            logger.info("No version found in manifest or build properties, using development fallback");
        }
        return DEV_BUILD_VERSION;
    }
    
    /**
     * Attempts to read version from JAR manifest.
     * 
     * @return version from manifest or null if not found
     */
    private static String getVersionFromManifest() {
        try {
            Package pkg = VersionManager.class.getPackage();
            if (pkg != null) {
                String implementationVersion = pkg.getImplementationVersion();
                if (implementationVersion != null && !implementationVersion.trim().isEmpty()) {
                    return implementationVersion;
                }
                
                String specificationVersion = pkg.getSpecificationVersion();
                if (specificationVersion != null && !specificationVersion.trim().isEmpty()) {
                    return specificationVersion;
                }
            }
            
            return null;
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Attempts to read version from build.properties file.
     * 
     * @return version from properties file or null if not found
     */
    private static String getVersionFromBuildProperties() {
        try (InputStream is = VersionManager.class.getResourceAsStream(BUILD_PROPERTIES_PATH)) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                
                String version = props.getProperty("build.version");
                if (version != null && !version.trim().isEmpty()) {
                    return version;
                }
            }
            
            return null;
            
        } catch (IOException e) {
            return null;
        }
    }
    
    /**
     * Builds detailed version information string.
     * 
     * @return formatted version information
     */
    private static String buildVersionInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Moodframe v").append(getVersion());
        
        // Add build type information
        if (isProductionBuild()) {
            info.append(" (Production Build)");
        }
        
        // Try to add additional build information from properties
        try (InputStream is = VersionManager.class.getResourceAsStream(BUILD_PROPERTIES_PATH)) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                
                String buildTime = props.getProperty("build.timestamp");
                if (buildTime != null) {
                    info.append(", Built: ").append(buildTime);
                }
                
                String gitCommit = props.getProperty("build.git.commit");
                if (gitCommit != null) {
                    // Show short commit hash (first 8 characters)
                    String shortCommit = gitCommit.length() > 8 ? 
                                        gitCommit.substring(0, 8) : gitCommit;
                    info.append(", Commit: ").append(shortCommit);
                }
            }
        } catch (IOException e) {
        }
        
        return info.toString();
    }
}