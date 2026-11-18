package art.moodframe.ui.support;

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

    /*  notified with the theme name after a theme was applied (on the FX thread, once the styles are in place)  */
    private static final java.util.List<java.util.function.Consumer<String>> listeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Registers a listener that is called after every applied theme, with the name of the theme.
     *
     * @param listener the listener
     */
    public static void addThemeListener(java.util.function.Consumer<String> listener) {
        listeners.add(listener);
    }

    /**
     * Removes a listener, for example that of a window that closes.
     *
     * @param listener the listener
     */
    public static void removeThemeListener(java.util.function.Consumer<String> listener) {
        listeners.remove(listener);
    }

    /**
     * Counts the registered listeners, so a test can check that a closed window left none behind.
     *
     * @return the number of listeners
     */
    public static int getThemeListenerCount() {
        return listeners.size();
    }

    /*  the theme applied last, also while the settings still preview it unsaved  */
    private static volatile String appliedTheme;

    /**
     * Tells the listeners that the appearance changed without a new theme (for example the background image).
     */
    public static void notifyAppearanceChanged() {
        String theme = appliedTheme != null ? appliedTheme : getCurrentTheme();
        for (var listener : listeners)
            javafx.application.Platform.runLater(() -> listener.accept(theme));
    }
    private static final String DEFAULT_THEME = "primer-dark";
    
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
        logger.debug("Applying theme: {}", themeName);
        
        try {
            Theme theme = getAtlantaFXTheme(themeName);
            if (theme != null) {
                Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
                appliedTheme = themeName;
                for (var listener : listeners)
                    javafx.application.Platform.runLater(() -> listener.accept(themeName));
                logger.debug("Theme applied successfully: {}", themeName);
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
     * Applies the default theme (Primer Dark).
     *
     * @return true if default theme was applied successfully
     */
    public static boolean applyDefaultTheme() {
        try {
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
            logger.debug("Default theme applied: {}", DEFAULT_THEME);
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
        logger.debug("Changing theme to: {}", themeName);
        
        if (applyTheme(themeName)) {
            // Save theme preference
            try {
                if (config == null) {
                    config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
                }
                if (config != null) {
                    config.ui().setUiTheme(themeName);
                    logger.debug("Theme preference saved: {}", themeName);
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
                return config.ui().getUiTheme();
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
     * Detects the system theme preference (light/dark mode) on macOS, Windows and Linux, see
     * {@link SystemThemeDetector}.
     *
     * @return "dark" if system is in dark mode or the preference is unknown, "light" otherwise
     */
    public static String detectSystemThemePreference() {
        SystemThemeDetector.Preference preference;
        try {
            preference = SystemThemeDetector.detectSystem();
        }
        catch (Exception e) {
            logger.warn("Failed to detect system theme preference", e);
            preference = SystemThemeDetector.UNKNOWN;
        }
        if (preference.source() != null)
            logger.info("theme preference: {} from {}", preference.value(), preference.source());
        else
            logger.info("theme preference unknown, using {}", preference.value());
        return preference.value();
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
}