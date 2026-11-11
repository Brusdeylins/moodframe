package art.moodframe.util;

import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import atlantafx.base.theme.*;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;

/**
 * Utility class for managing AtlantaFX themes in the application.
 * 
 * <p>This class provides centralized theme management functionality including:</p>
 * <ul>
 * <li>Theme switching at runtime</li>
 * <li>Theme validation and fallback handling</li>
 * <li>Integration with application configuration</li>
 * <li>Support for all AtlantaFX built-in themes</li>
 * </ul>
 * 
 * <p><strong>Supported Themes:</strong></p>
 * <ul>
 * <li><strong>primer-light</strong> - GitHub Primer Light theme</li>
 * <li><strong>primer-dark</strong> - GitHub Primer Dark theme</li>
 * <li><strong>nord-light</strong> - Nord Light theme</li>
 * <li><strong>nord-dark</strong> - Nord Dark theme</li>
 * <li><strong>cupertino-light</strong> - macOS-inspired Light theme</li>
 * <li><strong>cupertino-dark</strong> - macOS-inspired Dark theme</li>
 * <li><strong>dracula</strong> - Popular Dracula Dark theme</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class ThemeManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ThemeManager.class);
    private static final String DEFAULT_THEME = "primer-light";
    
    /**
     * Maps theme names to AtlantaFX Theme objects.
     * 
     * @param themeName the theme name from configuration
     * @return the corresponding AtlantaFX Theme, or null if not found
     */
    public static Theme getAtlantaFXTheme(String themeName) {
        return switch (themeName) {
            case "primer-light" -> new PrimerLight();
            case "primer-dark" -> new PrimerDark();
            case "nord-light" -> new NordLight();
            case "nord-dark" -> new NordDark();
            case "cupertino-light" -> new CupertinoLight();
            case "cupertino-dark" -> new CupertinoDark();
            case "dracula" -> new Dracula();
            default -> null;
        };
    }
    
    /**
     * Applies the specified theme to the application.
     * 
     * @param themeName the name of the theme to apply
     * @return true if theme was applied successfully, false otherwise
     */
    public static boolean applyTheme(String themeName) {
        ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        if (config != null && config.getLoggingEnabled()) {
            logger.info("Applying theme: {}", themeName);
        }
        
        try {
            Theme theme = getAtlantaFXTheme(themeName);
            if (theme != null) {
                Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
                if (config != null && config.getLoggingEnabled()) {
                    logger.info("Theme applied successfully: {}", themeName);
                }
                return true;
            } else {
                logger.warn("Unknown theme '{}', falling back to default", themeName);
                return applyDefaultTheme();
            }
        } catch (Exception e) {
            logger.error("Failed to apply theme: " + themeName, e);
            return applyDefaultTheme();
        }
    }
    
    /**
     * Applies the default theme (Primer Light).
     * 
     * @return true if default theme was applied successfully
     */
    public static boolean applyDefaultTheme() {
        try {
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
            if (config != null && config.getLoggingEnabled()) {
                logger.info("Default theme applied: {}", DEFAULT_THEME);
            }
            return true;
        } catch (Exception e) {
            logger.error("Failed to apply default theme", e);
            return false;
        }
    }
    
    /**
     * Changes the application theme and saves the preference.
     * 
     * @param themeName the name of the theme to apply
     * @return true if theme was changed successfully
     */
    public static boolean changeTheme(String themeName) {
        ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        if (config != null && config.getLoggingEnabled()) {
            logger.info("Changing theme to: {}", themeName);
        }
        
        if (applyTheme(themeName)) {
            // Save theme preference
            try {
                if (config == null) {
                    config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
                }
                if (config != null) {
                    config.setUiTheme(themeName);
                    if (config.getLoggingEnabled()) {
                        logger.info("Theme preference saved: {}", themeName);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to save theme preference", e);
                // Don't fail the theme change just because we couldn't save the preference
            }
            return true;
        }
        return false;
    }
    
    /**
     * Gets the current theme from application configuration.
     * 
     * @return the current theme name, or default if not found
     */
    public static String getCurrentTheme() {
        try {
            ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
            if (config != null) {
                return config.getUiTheme();
            }
        } catch (Exception e) {
            logger.warn("Failed to get current theme from config", e);
        }
        return DEFAULT_THEME;
    }
    
    /**
     * Gets all available theme names.
     * 
     * @return array of available theme names
     */
    public static String[] getAvailableThemes() {
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
    
    /**
     * Checks if a theme name is valid.
     * 
     * @param themeName the theme name to validate
     * @return true if the theme exists
     */
    public static boolean isValidTheme(String themeName) {
        return getAtlantaFXTheme(themeName) != null;
    }
    
    /**
     * Gets a user-friendly display name for a theme.
     * 
     * @param themeName the theme name
     * @return user-friendly display name
     */
    public static String getThemeDisplayName(String themeName) {
        return switch (themeName) {
            case "primer-light" -> "GitHub Primer (Light)";
            case "primer-dark" -> "GitHub Primer (Dark)";
            case "nord-light" -> "Nord (Light)";
            case "nord-dark" -> "Nord (Dark)";
            case "cupertino-light" -> "Cupertino (Light)";
            case "cupertino-dark" -> "Cupertino (Dark)";
            case "dracula" -> "Dracula";
            default -> themeName;
        };
    }
    
    /**
     * Determines if a theme is a dark theme.
     * 
     * @param themeName the theme name
     * @return true if the theme is dark
     */
    public static boolean isDarkTheme(String themeName) {
        return themeName.contains("dark") || "dracula".equals(themeName);
    }
    
    /**
     * Detects the system theme preference (light/dark mode).
     * 
     * @return "dark" if system is in dark mode, "light" otherwise
     */
    public static String detectSystemThemePreference() {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase();
            
            if (osName.contains("mac")) {
                return detectMacOSTheme();
            } else if (osName.contains("win")) {
                return detectWindowsTheme();
            } else {
                // For Linux and other systems, default to light
                logger.info("System theme detection not supported on {}, defaulting to light", osName);
                return "light";
            }
        } catch (Exception e) {
            logger.warn("Failed to detect system theme preference", e);
            return "light";
        }
    }
    
    private static String detectMacOSTheme() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                "defaults", "read", "-g", "AppleInterfaceStyle"
            });
            process.waitFor();
            
            if (process.exitValue() == 0) {
                // If command succeeds, dark mode is enabled
                return "dark";
            } else {
                // If command fails, light mode is default
                return "light";
            }
        } catch (Exception e) {
            logger.warn("Failed to detect macOS theme preference", e);
        }
        return "light";
    }
    
    private static String detectWindowsTheme() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                "reg", "query", 
                "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "AppsUseLightTheme"
            });
            process.waitFor();
            
            if (process.exitValue() == 0) {
                java.io.InputStream inputStream = process.getInputStream();
                try (java.util.Scanner scanner = new java.util.Scanner(inputStream)) {
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        if (line.contains("AppsUseLightTheme")) {
                            // Value 0x0 means dark mode, 0x1 means light mode
                            return line.contains("0x0") ? "dark" : "light";
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to detect system theme preference", e);
        }
        return "light";
    }
    
    /**
     * Gets a suitable theme based on system preference.
     * 
     * @return theme name that matches system preference
     */
    public static String getSystemPreferredTheme() {
        String systemPreference = detectSystemThemePreference();
        String currentTheme = getCurrentTheme();
        
        // If current theme already matches system preference, keep it
        boolean currentIsDark = isDarkTheme(currentTheme);
        if ((systemPreference.equals("dark") && currentIsDark) || 
            (systemPreference.equals("light") && !currentIsDark)) {
            return currentTheme;
        }
        
        // Otherwise, choose appropriate theme
        if (systemPreference.equals("dark")) {
            return "primer-dark";  // Default dark theme
        } else {
            return "primer-light"; // Default light theme
        }
    }
    
    /**
     * Applies theme based on system preference if not already applied.
     * 
     * @return true if theme was changed, false if already matching
     */
    public static boolean applySystemPreferredTheme() {
        String preferredTheme = getSystemPreferredTheme();
        String currentTheme = getCurrentTheme();
        
        if (!preferredTheme.equals(currentTheme)) {
            ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
            if (config != null && config.getLoggingEnabled()) {
                logger.info("Applying system-preferred theme: {} (was: {})", preferredTheme, currentTheme);
            }
            return changeTheme(preferredTheme);
        }
        
        return false; // No change needed
    }
    
    /**
     * Demonstrates theme switching by cycling through all available themes.
     * This method is useful for testing and demonstration purposes.
     * 
     * @return the name of the next theme that was applied
     */
    public static String cycleToNextTheme() {
        String currentTheme = getCurrentTheme();
        String[] themes = getAvailableThemes();
        
        // Find current theme index
        int currentIndex = 0;
        for (int i = 0; i < themes.length; i++) {
            if (themes[i].equals(currentTheme)) {
                currentIndex = i;
                break;
            }
        }
        
        // Get next theme (cycle back to 0 if at end)
        int nextIndex = (currentIndex + 1) % themes.length;
        String nextTheme = themes[nextIndex];
        
        changeTheme(nextTheme);
        ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        if (config != null && config.getLoggingEnabled()) {
            logger.info("Cycled from '{}' to '{}' theme", currentTheme, nextTheme);
        }
        
        return nextTheme;
    }
}