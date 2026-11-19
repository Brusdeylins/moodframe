package art.moodframe.service;

import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.provider.BflApiService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.PromptBlock;
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
        CUSTOM_STYLES("Custom Building Blocks", "User-created styles, atmospheres, lights and palettes"),
        MOODS("Moods", "User-created moods, the saved combinations of building blocks"),
        FRAMES("Frames", "User-created frames, the saved target aspect ratios and resolutions"),
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
    
    private final PromptBlockService promptBlockService;
    private final ColorStyleService colorStyleService;
    private final MoodService moodService;
    private final FrameService frameService;

    /**
     * Creates a new configuration exporter on the services of the application, so an import reaches the main window
     * through their library events.
     *
     * @param promptBlockService the building blocks
     * @param colorStyleService  the palettes
     * @param moodService        the moods
     * @param frameService       the frames
     */
    public ConfigurationExporter(PromptBlockService promptBlockService, ColorStyleService colorStyleService,
            MoodService moodService, FrameService frameService) {
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        this.promptBlockService = promptBlockService;
        this.colorStyleService = colorStyleService;
        this.moodService = moodService;
        this.frameService = frameService;
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

        logger.debug("Exporting configuration to: {}", exportFile.getAbsolutePath());

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
            apiSettings.put("apiTimeoutSeconds", config.accounts().getApiTimeoutSeconds());
            apiSettings.put("apiRegion", config.accounts().getApiRegion());
            apiSettings.put("defaultSafetyTolerance", config.generation().getDefaultSafetyTolerance());
            apiSettings.put("defaultModel", config.generation().getDefaultModel());
            apiSettings.put("defaultAspectRatio", config.generation().getDefaultAspectRatio());
            apiSettings.put("defaultImageCount", config.generation().getDefaultImageCount());
            apiSettings.put("defaultStyle", config.generation().getDefaultStyle());
            apiSettings.put("defaultPromptUpsampling", config.generation().getDefaultBflPromptUpsampling());
            // Note: API key is intentionally excluded for security reasons
            configData.put("apiSettings", apiSettings);
        }
        
        if (categories.contains(ConfigCategory.ALL) || categories.contains(ConfigCategory.UI_PREFERENCES)) {
            Map<String, Object> uiPreferences = new HashMap<>();
            uiPreferences.put("uiTheme", config.ui().getUiTheme());
            uiPreferences.put("uiLanguage", config.ui().getUiLanguage());
            uiPreferences.put("splitterPosition", config.ui().getSplitterPosition());
            configData.put("uiPreferences", uiPreferences);
        }
        
        if (categories.contains(ConfigCategory.ALL) || categories.contains(ConfigCategory.PROXY_SETTINGS)) {
            Map<String, Object> proxySettings = new HashMap<>();
            proxySettings.put("proxyEnabled", config.proxy().isProxyEnabled());
            proxySettings.put("proxyHost", config.proxy().getProxyHost());
            proxySettings.put("proxyPort", config.proxy().getProxyPort());
            proxySettings.put("proxyUsername", config.proxy().getProxyUsername());
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

            /*  The building blocks and palettes of the user with their IDs, in their order  */
            List<Map<String, Object>> customBlocks = new ArrayList<>();
            for (PromptBlock.Type type : PromptBlock.Type.values())
                customBlocks.addAll(promptBlockService.exportCustom(type));
            configData.put("customBlocks", customBlocks);
            configData.put("customPalettes", colorStyleService.getCustomStyles());
        }
        
        if (categories.contains(ConfigCategory.ALL) || categories.contains(ConfigCategory.MOODS)) {

            /*  The moods of the user with their IDs and the IDs of their blocks  */
            configData.put("moods", moodService.exportCustom());
        }

        if (categories.contains(ConfigCategory.ALL) || categories.contains(ConfigCategory.FRAMES)) {

            /*  The frames of the user with their IDs, target aspect ratios and target resolutions  */
            configData.put("frames", frameService.exportCustom());
        }

        exportData.put("configuration", configData);
        
        // Write to file
        objectMapper.writeValue(exportFile, exportData);
        
        logger.debug("Configuration exported successfully. Categories: {}, File size: {} bytes",
                   categories.size(), exportFile.length());
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
            logger.debug("Importing configuration from: {}", importFile.getAbsolutePath());
            
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

            /*  Every value is read and checked first, the settings are changed only once all of them could be read,
                so a broken file changes nothing  */
            List<Runnable> settings = new ArrayList<>();

            // Import API settings
            @SuppressWarnings("unchecked")
            Map<String, Object> apiSettings = (Map<String, Object>) configData.get("apiSettings");
            if (apiSettings != null) {
                if (apiSettings.containsKey("apiTimeoutSeconds")) {
                    Integer newValue = (Integer) apiSettings.get("apiTimeoutSeconds");
                    if (!Objects.equals(newValue, targetConfig.accounts().getApiTimeoutSeconds())) {
                        changes.put("API Timeout", newValue + " seconds");
                        settings.add(() -> targetConfig.setProperty("api.timeout_seconds", newValue.toString()));
                    }
                }
                if (apiSettings.containsKey("apiRegion")) {
                    String newValue = (String) apiSettings.get("apiRegion");
                    if (!Objects.equals(newValue, targetConfig.accounts().getApiRegion())) {
                        changes.put("API Region", newValue + " (endpoint will be: "
                                + BflApiService.endpointOf(newValue) + ")");
                        settings.add(() -> targetConfig.accounts().setApiRegion(newValue));
                    }
                }
                if (apiSettings.containsKey("defaultSafetyTolerance")) {
                    Integer newValue = (Integer) apiSettings.get("defaultSafetyTolerance");
                    if (!newValue.equals(targetConfig.generation().getDefaultSafetyTolerance())) {
                        changes.put("Default Safety Tolerance", newValue);
                        settings.add(() -> targetConfig.generation().setDefaultSafetyTolerance(newValue));
                    }
                }
                if (apiSettings.containsKey("defaultModel")) {
                    String newValue = (String) apiSettings.get("defaultModel");
                    if (!Objects.equals(newValue, targetConfig.generation().getDefaultModel())) {
                        changes.put("Default Model", newValue);
                        settings.add(() -> targetConfig.generation().setDefaultModel(newValue));
                    }
                }
                if (apiSettings.containsKey("defaultAspectRatio")) {
                    String newValue = (String) apiSettings.get("defaultAspectRatio");
                    if (!Objects.equals(newValue, targetConfig.generation().getDefaultAspectRatio())) {
                        changes.put("Default Aspect Ratio", newValue);
                        settings.add(() -> targetConfig.generation().setDefaultAspectRatio(newValue));
                    }
                }
                if (apiSettings.containsKey("defaultImageCount")) {
                    Integer newValue = (Integer) apiSettings.get("defaultImageCount");
                    if (!newValue.equals(targetConfig.generation().getDefaultImageCount())) {
                        changes.put("Default Image Count", newValue);
                        settings.add(() -> targetConfig.generation().setDefaultImageCount(newValue));
                    }
                }
                if (apiSettings.containsKey("defaultStyle")) {
                    String newValue = (String) apiSettings.get("defaultStyle");
                    if (!Objects.equals(newValue, targetConfig.generation().getDefaultStyle())) {
                        changes.put("Default Style", newValue);
                        settings.add(() -> targetConfig.generation().setDefaultStyle(newValue));
                    }
                }
                if (apiSettings.containsKey("defaultPromptUpsampling")) {
                    Boolean newValue = (Boolean) apiSettings.get("defaultPromptUpsampling");
                    if (!newValue.equals(targetConfig.generation().getDefaultBflPromptUpsampling())) {
                        changes.put("Default Prompt Upsampling", newValue ? "Yes" : "No");
                        settings.add(() -> targetConfig.generation().setDefaultBflPromptUpsampling(newValue));
                    }
                }
            }
            
            // Import UI preferences
            @SuppressWarnings("unchecked")
            Map<String, Object> uiPreferences = (Map<String, Object>) configData.get("uiPreferences");
            if (uiPreferences != null) {
                if (uiPreferences.containsKey("uiTheme")) {
                    String newValue = (String) uiPreferences.get("uiTheme");
                    if (!Objects.equals(newValue, targetConfig.ui().getUiTheme())) {
                        changes.put("UI Theme", newValue);
                        settings.add(() -> targetConfig.ui().setUiTheme(newValue));
                    }
                }
                if (uiPreferences.containsKey("uiLanguage")) {
                    String newValue = (String) uiPreferences.get("uiLanguage");
                    if (!Objects.equals(newValue, targetConfig.ui().getUiLanguage())) {
                        changes.put("UI Language", newValue);
                        settings.add(() -> targetConfig.ui().setUiLanguage(newValue));
                    }
                }
                if (uiPreferences.containsKey("splitterPosition")) {
                    Double newValue = (Double) uiPreferences.get("splitterPosition");
                    if (!newValue.equals(targetConfig.ui().getSplitterPosition())) {
                        changes.put("Splitter Position", String.format("%.1f%%", newValue * 100));
                        settings.add(() -> targetConfig.ui().setSplitterPosition(newValue));
                    }
                }
            }
            
            // Import proxy settings
            @SuppressWarnings("unchecked")
            Map<String, Object> proxySettings = (Map<String, Object>) configData.get("proxySettings");
            if (proxySettings != null) {
                if (proxySettings.containsKey("proxyEnabled")) {
                    Boolean newValue = (Boolean) proxySettings.get("proxyEnabled");
                    if (!newValue.equals(targetConfig.proxy().isProxyEnabled())) {
                        changes.put("Proxy Enabled", newValue ? "Yes" : "No");
                        settings.add(() -> targetConfig.proxy().setProxyEnabled(newValue));
                    }
                }
                if (proxySettings.containsKey("proxyHost")) {
                    String newValue = (String) proxySettings.get("proxyHost");
                    if (!Objects.equals(newValue, targetConfig.proxy().getProxyHost())) {
                        changes.put("Proxy Host", newValue);
                        settings.add(() -> targetConfig.proxy().setProxyHost(newValue));
                    }
                }
                if (proxySettings.containsKey("proxyPort")) {
                    Integer newValue = (Integer) proxySettings.get("proxyPort");
                    if (!newValue.equals(targetConfig.proxy().getProxyPort())) {
                        changes.put("Proxy Port", newValue);
                        settings.add(() -> targetConfig.proxy().setProxyPort(newValue));
                    }
                }
                if (proxySettings.containsKey("proxyUsername")) {
                    String newValue = (String) proxySettings.get("proxyUsername");
                    if (!Objects.equals(newValue, targetConfig.proxy().getProxyUsername())) {
                        changes.put("Proxy Username", newValue);
                        settings.add(() -> targetConfig.proxy().setProxyUsername(newValue));
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
                        settings.add(() -> targetConfig.setLoggingEnabled(newValue));
                    }
                }
                // Note: certificatePath is auto-detected and not imported
                // Removed certificate path import logic as it's now auto-determined
            }
            
            /*  The items of the library, read before anything is changed  */
            List<Map<String, Object>> customBlocks = items(configData.get("customBlocks"));
            List<Map<String, Object>> customStyles = items(configData.get("customStyles"));
            Object customPalettes = configData.get("customPalettes");
            List<ColorStyle> palettes = customPalettes != null ? objectMapper.convertValue(customPalettes,
                    new TypeReference<List<ColorStyle>>() { }) : null;
            List<Map<String, Object>> moods = items(configData.get("moods"));
            List<Map<String, Object>> frames = items(configData.get("frames"));
            settings.forEach(Runnable::run);

            // Import the building blocks and palettes of the user next to the existing ones
            int importedBlocks = 0;
            if (customBlocks != null) {
                for (PromptBlock.Type type : PromptBlock.Type.values())
                    importedBlocks += promptBlockService.importCustom(type, customBlocks);
            }

            /*  Older exports carry the styles of the user only, without type and ID  */
            if (customStyles != null)
                importedBlocks += promptBlockService.importCustom(PromptBlock.Type.STYLE, customStyles);
            if (importedBlocks > 0)
                changes.put("Building Blocks", String.format("%d building blocks imported", importedBlocks));

            if (palettes != null) {
                int importedPalettes = colorStyleService.importStyles(palettes);
                if (importedPalettes > 0)
                    changes.put("Palettes", String.format("%d palettes imported", importedPalettes));
            }

            /*  The moods of the user next to the existing ones; an ID or name that is taken gets a new one  */
            if (moods != null) {
                int importedMoods = moodService.importCustom(moods);
                if (importedMoods > 0)
                    changes.put("Moods", String.format("%d moods imported", importedMoods));
            }

            /*  The frames of the user next to the existing ones; an ID or name that is taken gets a new one  */
            if (frames != null) {
                int importedFrames = frameService.importCustom(frames);
                if (importedFrames > 0)
                    changes.put("Frames", String.format("%d frames imported", importedFrames));
            }

            logger.debug("Configuration imported successfully. {} settings changed", changes.size());
            
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
    
    /*  a list of items of the library in the file, checked to be objects; null if the file has none  */
    private List<Map<String, Object>> items(Object list) {
        return list != null ? objectMapper.convertValue(list, new TypeReference<List<Map<String, Object>>>() { })
                : null;
    }
}