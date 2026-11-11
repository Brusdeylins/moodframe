package tools.moodframe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import tools.moodframe.config.ApplicationConfig;
import tools.moodframe.config.ServiceFactory;
import tools.moodframe.model.StylePreset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Utility class for exporting and importing application configuration.
 * 
 * <p>This class handles the serialization and deserialization of ApplicationConfig
 * objects to/from JSON format for backup and sharing purposes.</p>
 * 
 * <p><strong>Features:</strong></p>
 * <ul>
 * <li>Complete configuration export to JSON format</li>
 * <li>Selective export of specific configuration categories</li>
 * <li>Import validation with version compatibility checking</li>
 * <li>Human-readable JSON output with proper formatting</li>
 * <li>Metadata inclusion (export timestamp, version, etc.)</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class ConfigurationExporter {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationExporter.class);
    
    // Export format version for compatibility checking
    private static final String EXPORT_FORMAT_VERSION = "1.0";
    private static final String APPLICATION_VERSION = "1.0.0";
    
    private final ObjectMapper objectMapper;
    
    /**
     * Configuration categories that can be selectively exported/imported.
     */
    public enum ConfigCategory {
        API_SETTINGS("API Settings", "API region, timeout, retry settings, defaults"),
        UI_PREFERENCES("UI Preferences", "Theme, language, window settings"),
        PROXY_SETTINGS("Proxy Settings", "Proxy configuration and authentication"),
        ADVANCED_SETTINGS("Advanced Settings", "Logging, development mode, SSL bypass"),
        CUSTOM_STYLES("Custom Styles", "User-created style presets with prompts"),
        ALL("All Settings", "Complete configuration export");
        
        private final String displayName;
        private final String description;
        
        ConfigCategory(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    /**
     * Result of a configuration import operation.
     */
    public static class ImportResult {
        private final boolean success;
        private final String message;
        private final Map<String, Object> changes;
        private final String version;
        
        public ImportResult(boolean success, String message, Map<String, Object> changes, String version) {
            this.success = success;
            this.message = message;
            this.changes = changes != null ? new HashMap<>(changes) : new HashMap<>();
            this.version = version;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Map<String, Object> getChanges() { return new HashMap<>(changes); }
        public String getVersion() { return version; }
        public int getChangeCount() { return changes.size(); }
    }
    
    private final StylePresetService stylePresetService;
    private final ApplicationConfig config;
    
    /**
     * Creates a new configuration exporter.
     */
    public ConfigurationExporter() {
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.stylePresetService = new StylePresetService();
        this.config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
    }
    
    /**
     * Exports the complete configuration to a JSON file.
     * 
     * @param config the configuration to export
     * @param exportFile the target file for export
     * @throws IOException if export fails
     */
    public void exportConfiguration(ApplicationConfig config, File exportFile) throws IOException {
        exportConfiguration(config, exportFile, Set.of(ConfigCategory.ALL));
    }
    
    /**
     * Exports selected configuration categories to a JSON file.
     * 
     * @param config the configuration to export
     * @param exportFile the target file for export
     * @param categories the categories to include in export
     * @throws IOException if export fails
     */
    public void exportConfiguration(ApplicationConfig config, File exportFile, Set<ConfigCategory> categories) throws IOException {
        if (config == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }

        if (config.getLoggingEnabled()) {
            logger.info("Exporting configuration to: {}", exportFile.getAbsolutePath());
        }

        Map<String, Object> exportData = new HashMap<>();
        
        // Add metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("exportVersion", EXPORT_FORMAT_VERSION);
        metadata.put("applicationVersion", APPLICATION_VERSION);
        metadata.put("exportTimestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        metadata.put("exportedCategories", categories.stream().map(ConfigCategory::name).toList());
        exportData.put("metadata", metadata);
        
        // Add configuration data based on selected categories
        Map<String, Object> configData = new HashMap<>();
        
        if (categories.contains(ConfigCategory.ALL) || categories.contains(ConfigCategory.API_SETTINGS)) {
            Map<String, Object> apiSettings = new HashMap<>();
            // Note: apiBaseUrl removed - it's now calculated from apiRegion automatically
            apiSettings.put("apiTimeoutSeconds", config.getApiTimeoutSeconds());
            apiSettings.put("apiRegion", config.getApiRegion());
            apiSettings.put("defaultSafetyTolerance", config.getDefaultSafetyTolerance());
            apiSettings.put("defaultModel", config.getDefaultModel());
            apiSettings.put("defaultAspectRatio", config.getDefaultAspectRatio());
            apiSettings.put("defaultImageCount", config.getDefaultImageCount());
            apiSettings.put("defaultStyle", config.getDefaultStyle());
            apiSettings.put("defaultPromptUpsampling", config.getDefaultBflPromptUpsampling());
            // Note: API key is intentionally excluded for security reasons
            configData.put("apiSettings", apiSettings);
        }
        
        if (categories.contains(ConfigCategory.ALL) || categories.contains(ConfigCategory.UI_PREFERENCES)) {
            Map<String, Object> uiPreferences = new HashMap<>();
            uiPreferences.put("uiTheme", config.getUiTheme());
            uiPreferences.put("uiLanguage", config.getUiLanguage());
            uiPreferences.put("splitterPosition", config.getSplitterPosition());
            configData.put("uiPreferences", uiPreferences);
        }
        
        if (categories.contains(ConfigCategory.ALL) || categories.contains(ConfigCategory.PROXY_SETTINGS)) {
            Map<String, Object> proxySettings = new HashMap<>();
            proxySettings.put("proxyEnabled", config.isProxyEnabled());
            proxySettings.put("proxyHost", config.getProxyHost());
            proxySettings.put("proxyPort", config.getProxyPort());
            proxySettings.put("proxyUsername", config.getProxyUsername());
            // Note: Proxy password is intentionally excluded for security reasons
            configData.put("proxySettings", proxySettings);
        }
        
        if (categories.contains(ConfigCategory.ALL) || categories.contains(ConfigCategory.ADVANCED_SETTINGS)) {
            Map<String, Object> advancedSettings = new HashMap<>();
            advancedSettings.put("loggingEnabled", config.getLoggingEnabled());
            // Note: certificatePath is auto-detected and not exported/imported
            configData.put("advancedSettings", advancedSettings);
        }
        
        if (categories.contains(ConfigCategory.ALL) || categories.contains(ConfigCategory.CUSTOM_STYLES)) {
            List<Map<String, Object>> customStylesList = new ArrayList<>();
            List<StylePreset> customStyles = stylePresetService.getCustomStyles();
            
            for (StylePreset style : customStyles) {
                Map<String, Object> styleData = new HashMap<>();
                styleData.put("name", style.getName());
                styleData.put("promptText", style.getPromptText());
                // SortOrder is not exported - will be assigned automatically during import
                customStylesList.add(styleData);
            }
            
            configData.put("customStyles", customStylesList);
        }
        
        exportData.put("configuration", configData);
        
        // Write to file
        objectMapper.writeValue(exportFile, exportData);
        
        if (config != null && config.getLoggingEnabled()) {
            logger.info("Configuration exported successfully. Categories: {}, File size: {} bytes", 
                       categories.size(), exportFile.length());
        }
    }
    
    /**
     * Imports configuration from a JSON file with validation.
     * 
     * @param importFile the file to import from
     * @param targetConfig the configuration object to update
     * @return import result with success status and change details
     */
    public ImportResult importConfiguration(File importFile, ApplicationConfig targetConfig) {
        try {
            if (config != null && config.getLoggingEnabled()) {
                logger.info("Importing configuration from: {}", importFile.getAbsolutePath());
            }
            
            if (!importFile.exists()) {
                return new ImportResult(false, "Import file does not exist", null, null);
            }
            
            // Parse the import file
            @SuppressWarnings("unchecked")
            Map<String, Object> importData = objectMapper.readValue(importFile, Map.class);
            
            // Validate metadata
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) importData.get("metadata");
            if (metadata == null) {
                return new ImportResult(false, "Invalid configuration file: missing metadata", null, null);
            }
            
            String exportVersion = (String) metadata.get("exportVersion");
            String applicationVersion = (String) metadata.get("applicationVersion");
            
            // Version compatibility check
            if (!EXPORT_FORMAT_VERSION.equals(exportVersion)) {
                logger.warn("Configuration file version mismatch. Expected: {}, Found: {}", 
                           EXPORT_FORMAT_VERSION, exportVersion);
                return new ImportResult(false, 
                    String.format("Incompatible configuration file version. Expected: %s, Found: %s", 
                                 EXPORT_FORMAT_VERSION, exportVersion), null, exportVersion);
            }
            
            // Get configuration data
            @SuppressWarnings("unchecked")
            Map<String, Object> configData = (Map<String, Object>) importData.get("configuration");
            if (configData == null) {
                return new ImportResult(false, "Invalid configuration file: missing configuration data", null, exportVersion);
            }
            
            // Track changes for confirmation dialog
            Map<String, Object> changes = new HashMap<>();
            
            // Import API settings
            @SuppressWarnings("unchecked")
            Map<String, Object> apiSettings = (Map<String, Object>) configData.get("apiSettings");
            if (apiSettings != null) {
                if (apiSettings.containsKey("apiTimeoutSeconds")) {
                    Integer newValue = (Integer) apiSettings.get("apiTimeoutSeconds");
                    if (!Objects.equals(newValue, targetConfig.getApiTimeoutSeconds())) {
                        changes.put("API Timeout", newValue + " seconds");
                        targetConfig.setProperty("api.timeout_seconds", newValue.toString());
                    }
                }
                if (apiSettings.containsKey("apiRegion")) {
                    String newValue = (String) apiSettings.get("apiRegion");
                    if (!Objects.equals(newValue, targetConfig.getApiRegion())) {
                        changes.put("API Region", newValue + " (endpoint will be: " + getEndpointForRegion(newValue) + ")");
                        targetConfig.setApiRegion(newValue);
                    }
                }
                if (apiSettings.containsKey("defaultSafetyTolerance")) {
                    Integer newValue = (Integer) apiSettings.get("defaultSafetyTolerance");
                    if (!newValue.equals(targetConfig.getDefaultSafetyTolerance())) {
                        changes.put("Default Safety Tolerance", newValue);
                        targetConfig.setDefaultSafetyTolerance(newValue);
                    }
                }
                if (apiSettings.containsKey("defaultModel")) {
                    String newValue = (String) apiSettings.get("defaultModel");
                    if (!Objects.equals(newValue, targetConfig.getDefaultModel())) {
                        changes.put("Default Model", newValue);
                        targetConfig.setDefaultModel(newValue);
                    }
                }
                if (apiSettings.containsKey("defaultAspectRatio")) {
                    String newValue = (String) apiSettings.get("defaultAspectRatio");
                    if (!Objects.equals(newValue, targetConfig.getDefaultAspectRatio())) {
                        changes.put("Default Aspect Ratio", newValue);
                        targetConfig.setDefaultAspectRatio(newValue);
                    }
                }
                if (apiSettings.containsKey("defaultImageCount")) {
                    Integer newValue = (Integer) apiSettings.get("defaultImageCount");
                    if (!newValue.equals(targetConfig.getDefaultImageCount())) {
                        changes.put("Default Image Count", newValue);
                        targetConfig.setDefaultImageCount(newValue);
                    }
                }
                if (apiSettings.containsKey("defaultStyle")) {
                    String newValue = (String) apiSettings.get("defaultStyle");
                    if (!Objects.equals(newValue, targetConfig.getDefaultStyle())) {
                        changes.put("Default Style", newValue);
                        targetConfig.setDefaultStyle(newValue);
                    }
                }
                if (apiSettings.containsKey("defaultPromptUpsampling")) {
                    Boolean newValue = (Boolean) apiSettings.get("defaultPromptUpsampling");
                    if (!newValue.equals(targetConfig.getDefaultBflPromptUpsampling())) {
                        changes.put("Default Prompt Upsampling", newValue ? "Yes" : "No");
                        targetConfig.setDefaultBflPromptUpsampling(newValue);
                    }
                }
            }
            
            // Import UI preferences
            @SuppressWarnings("unchecked")
            Map<String, Object> uiPreferences = (Map<String, Object>) configData.get("uiPreferences");
            if (uiPreferences != null) {
                if (uiPreferences.containsKey("uiTheme")) {
                    String newValue = (String) uiPreferences.get("uiTheme");
                    if (!Objects.equals(newValue, targetConfig.getUiTheme())) {
                        changes.put("UI Theme", newValue);
                        targetConfig.setUiTheme(newValue);
                    }
                }
                if (uiPreferences.containsKey("uiLanguage")) {
                    String newValue = (String) uiPreferences.get("uiLanguage");
                    if (!Objects.equals(newValue, targetConfig.getUiLanguage())) {
                        changes.put("UI Language", newValue);
                        targetConfig.setUiLanguage(newValue);
                    }
                }
                if (uiPreferences.containsKey("splitterPosition")) {
                    Double newValue = (Double) uiPreferences.get("splitterPosition");
                    if (!newValue.equals(targetConfig.getSplitterPosition())) {
                        changes.put("Splitter Position", String.format("%.1f%%", newValue * 100));
                        targetConfig.setSplitterPosition(newValue);
                    }
                }
            }
            
            // Import proxy settings
            @SuppressWarnings("unchecked")
            Map<String, Object> proxySettings = (Map<String, Object>) configData.get("proxySettings");
            if (proxySettings != null) {
                if (proxySettings.containsKey("proxyEnabled")) {
                    Boolean newValue = (Boolean) proxySettings.get("proxyEnabled");
                    if (!newValue.equals(targetConfig.isProxyEnabled())) {
                        changes.put("Proxy Enabled", newValue ? "Yes" : "No");
                        targetConfig.setProxyEnabled(newValue);
                    }
                }
                if (proxySettings.containsKey("proxyHost")) {
                    String newValue = (String) proxySettings.get("proxyHost");
                    if (!Objects.equals(newValue, targetConfig.getProxyHost())) {
                        changes.put("Proxy Host", newValue);
                        targetConfig.setProxyHost(newValue);
                    }
                }
                if (proxySettings.containsKey("proxyPort")) {
                    Integer newValue = (Integer) proxySettings.get("proxyPort");
                    if (!newValue.equals(targetConfig.getProxyPort())) {
                        changes.put("Proxy Port", newValue);
                        targetConfig.setProxyPort(newValue);
                    }
                }
                if (proxySettings.containsKey("proxyUsername")) {
                    String newValue = (String) proxySettings.get("proxyUsername");
                    if (!Objects.equals(newValue, targetConfig.getProxyUsername())) {
                        changes.put("Proxy Username", newValue);
                        targetConfig.setProxyUsername(newValue);
                    }
                }
            }
            
            // Import advanced settings
            @SuppressWarnings("unchecked")
            Map<String, Object> advancedSettings = (Map<String, Object>) configData.get("advancedSettings");
            if (advancedSettings != null) {
                if (advancedSettings.containsKey("loggingEnabled")) {
                    Boolean newValue = (Boolean) advancedSettings.get("loggingEnabled");
                    if (!newValue.equals(targetConfig.getLoggingEnabled())) {
                        changes.put("Logging Enabled", newValue ? "Yes" : "No");
                        targetConfig.setLoggingEnabled(newValue);
                    }
                }
                // Note: certificatePath is auto-detected and not imported
                // Removed certificate path import logic as it's now auto-determined
            }
            
            // Import custom styles
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> customStylesList = (List<Map<String, Object>>) configData.get("customStyles");
            if (customStylesList != null && !customStylesList.isEmpty()) {
                // Delete all existing custom styles before import to avoid duplicates
                int deletedCount = stylePresetService.resetToDefaults();
                if (config != null && config.getLoggingEnabled()) {
                    logger.info("Deleted {} existing custom styles before import", deletedCount);
                }
                
                int importedStyles = 0;
                for (Map<String, Object> styleData : customStylesList) {
                    String name = (String) styleData.get("name");
                    String promptText = (String) styleData.get("promptText");
                    
                    if (name != null && promptText != null) {
                        StylePreset newStyle = new StylePreset(name, promptText, false);
                        // SortOrder is automatically assigned by addCustomStyle()
                        
                        if (stylePresetService.addCustomStyle(newStyle)) {
                            importedStyles++;
                        }
                    }
                }
                
                if (importedStyles > 0) {
                    changes.put("Custom Styles", String.format("%d styles imported (replaced all existing custom styles)", importedStyles));
                }
            }
            
            if (config != null && config.getLoggingEnabled()) {
                logger.info("Configuration imported successfully. {} settings changed", changes.size());
            }
            
            return new ImportResult(true, 
                String.format("Configuration imported successfully. %d settings updated.", changes.size()), 
                changes, applicationVersion);
                
        } catch (IOException e) {
            logger.error("Failed to import configuration", e);
            return new ImportResult(false, "Failed to read configuration file: " + e.getMessage(), null, null);
        } catch (Exception e) {
            logger.error("Failed to parse configuration file", e);
            return new ImportResult(false, "Invalid configuration file format: " + e.getMessage(), null, null);
        }
    }
    
    /**
     * Generates a default filename for configuration export.
     * 
     * @return a filename with timestamp
     */
    public String generateExportFilename() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("moodframe_config_%s.json", timestamp);
    }
    
    /**
     * Helper method to get the endpoint URL for a given region.
     */
    private String getEndpointForRegion(String region) {
        switch (region) {
            case "EU":
                return "https://api.eu.bfl.ai";
            case "US":
                return "https://api.us.bfl.ai";
            case "Global":
            default:
                return "https://api.bfl.ai";
        }
    }
}