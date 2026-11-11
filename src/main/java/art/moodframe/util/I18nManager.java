package art.moodframe.util;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central internationalization manager for Moodframe application.
 * 
 * <p>
 * This manager provides thread-safe access to localized strings using
 * ResourceBundle files. It supports dynamic language switching and
 * parameter formatting using MessageFormat.
 * </p>
 * 
 * <p>
 * <strong>Usage Examples:</strong>
 * </p>
 * <pre>
 * // Simple text retrieval
 * String title = I18nManager.getText("app.title");
 * 
 * // Text with parameters
 * String count = I18nManager.getText("params.image.count", 3);
 * 
 * // Change language
 * I18nManager.setLocale(Locale.GERMAN);
 * </pre>
 * 
 * <p>
 * <strong>Supported Languages:</strong>
 * </p>
 * <ul>
 * <li>English (en) - Default fallback language</li>
 * <li>German (de) - Full localization</li>
 * </ul>
 * 
 * @since 1.0.0 Epic 9 - Internationalization
 */
public class I18nManager {
    
    private static final Logger logger = LoggerFactory.getLogger(I18nManager.class);
    
    // Resource bundle base name
    private static final String BUNDLE_BASE_NAME = "i18n.messages";
    
    // Current locale - defaults to English
    private static volatile Locale currentLocale = Locale.ENGLISH;
    
    // Cache for ResourceBundles to avoid repeated loading
    private static final ConcurrentMap<Locale, ResourceBundle> bundleCache = new ConcurrentHashMap<>();
    
    // Fallback locale when key is not found
    private static final Locale FALLBACK_LOCALE = Locale.ENGLISH;
    
    // List of components that need to be notified when language changes
    private static final List<I18nChangeListener> changeListeners = new CopyOnWriteArrayList<>();
    
    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with static methods only.
     */
    private I18nManager() {
        // Utility class - no instances allowed
    }
    
    /**
     * Sets the current locale for the application.
     * 
     * <p>
     * This method will immediately switch the language for all
     * subsequent calls to getText() methods. Existing UI elements
     * may need to be refreshed to show the new language.
     * </p>
     * 
     * @param locale the new locale to use (must not be null)
     * @throws IllegalArgumentException if locale is null
     */
    public static synchronized void setLocale(Locale locale) {
        if (locale == null) {
            throw new IllegalArgumentException("Locale cannot be null");
        }
        
        // Only log if logging is enabled
        ApplicationConfig appConfig = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        if (appConfig != null && appConfig.getLoggingEnabled()) {
            logger.info("Changing application locale from {} to {}", currentLocale, locale);
        }
        currentLocale = locale;
        
        // Pre-load the resource bundle for the new locale to detect any issues early
        try {
            getResourceBundle(locale);
            if (appConfig != null && appConfig.getLoggingEnabled()) {
                logger.info("Successfully loaded resource bundle for locale: {}", locale);
            }
            
            // Notify all registered listeners about the language change
            notifyLanguageChanged();
            
        } catch (Exception e) {
            logger.warn("Failed to load resource bundle for locale {}, falling back to English", locale, e);
            currentLocale = FALLBACK_LOCALE;
        }
    }
    
    /**
     * Gets the current application locale.
     * 
     * @return the current locale (never null)
     */
    public static Locale getCurrentLocale() {
        return currentLocale;
    }
    
    /**
     * Retrieves a localized text string for the given key.
     * 
     * <p>
     * If the key is not found in the current locale, it will fall back
     * to the English locale. If the key is still not found, the key
     * itself will be returned with a warning prefix.
     * </p>
     * 
     * @param key the resource key (must not be null or empty)
     * @return the localized text, or a fallback message if not found
     * @throws IllegalArgumentException if key is null or empty
     */
    public static String getText(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        
        return getTextInternal(key, currentLocale);
    }
    
    /**
     * Retrieves a localized text string with parameter substitution.
     * 
     * <p>
     * This method uses MessageFormat to substitute parameters into
     * the localized text. Parameters are referenced using {0}, {1}, etc.
     * in the resource bundle values.
     * </p>
     * 
     * <p>
     * Example: If the resource contains "Hello {0}, you have {1} messages"
     * and you call getText("greeting", "John", 5), the result will be
     * "Hello John, you have 5 messages".
     * </p>
     * 
     * @param key the resource key (must not be null or empty)
     * @param parameters the parameters to substitute (can be empty)
     * @return the localized and formatted text
     * @throws IllegalArgumentException if key is null or empty
     */
    public static String getText(String key, Object... parameters) {
        String rawText = getText(key);
        
        if (parameters == null || parameters.length == 0) {
            return rawText;
        }
        
        try {
            return MessageFormat.format(rawText, parameters);
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to format message for key '{}' with parameters: {}. Raw text: '{}'", 
                       key, java.util.Arrays.toString(parameters), rawText, e);
            return rawText; // Return unformatted text as fallback
        }
    }
    
    /**
     * Gets the current ResourceBundle for use with JavaFX FXML loading.
     * 
     * @return the ResourceBundle for the current locale
     */
    public static ResourceBundle getCurrentResourceBundle() {
        return getResourceBundle(currentLocale);
    }
    
    /**
     * Checks if a localized text exists for the given key.
     * 
     * @param key the resource key to check
     * @return true if the key exists in current or fallback locale
     */
    public static boolean hasText(String key) {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }
        
        try {
            ResourceBundle bundle = getResourceBundle(currentLocale);
            if (bundle.containsKey(key)) {
                return true;
            }
            
            // Check fallback locale if different
            if (!currentLocale.equals(FALLBACK_LOCALE)) {
                ResourceBundle fallbackBundle = getResourceBundle(FALLBACK_LOCALE);
                return fallbackBundle.containsKey(key);
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Gets all available locales supported by this application.
     * 
     * @return array of supported locales
     */
    public static Locale[] getAvailableLocales() {
        return new Locale[] {
            Locale.ENGLISH,    // English (default)
            Locale.GERMAN      // German
        };
    }
    
    /**
     * Adds a change listener that will be notified when the language changes.
     * 
     * @param listener the listener to add (must not be null)
     * @throws IllegalArgumentException if listener is null
     */
    public static void addChangeListener(I18nChangeListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        
        changeListeners.add(listener);
    }
    
    /**
     * Removes a change listener.
     * 
     * @param listener the listener to remove
     * @return true if the listener was removed, false if it was not found
     */
    public static boolean removeChangeListener(I18nChangeListener listener) {
        boolean removed = changeListeners.remove(listener);
        return removed;
    }
    
    /**
     * Gets the number of registered change listeners.
     * 
     * @return the number of listeners
     */
    public static int getChangeListenerCount() {
        return changeListeners.size();
    }
    
    /**
     * Clears the resource bundle cache.
     * 
     * <p>
     * This method is primarily for testing purposes or when
     * resource bundles need to be reloaded at runtime.
     * </p>
     */
    public static synchronized void clearCache() {
        bundleCache.clear();
    }
    
    /**
     * Internal method to get text with fallback logic.
     * 
     * @param key the resource key
     * @param locale the preferred locale
     * @return the localized text or fallback
     */
    private static String getTextInternal(String key, Locale locale) {
        try {
            ResourceBundle bundle = getResourceBundle(locale);
            if (bundle.containsKey(key)) {
                return bundle.getString(key);
            }
            
            // Try fallback locale if different from current
            if (!locale.equals(FALLBACK_LOCALE)) {
                
                ResourceBundle fallbackBundle = getResourceBundle(FALLBACK_LOCALE);
                if (fallbackBundle.containsKey(key)) {
                    return fallbackBundle.getString(key);
                }
            }
            
            // Key not found in any locale
            logger.warn("Key '{}' not found in any resource bundle", key);
            return "!" + key + "!"; // Return key with warning markers
            
        } catch (Exception e) {
            logger.error("Failed to get text for key '{}' in locale {}", key, locale, e);
            return "!" + key + "!"; // Return key with warning markers
        }
    }
    
    /**
     * Gets or loads a ResourceBundle for the specified locale.
     * 
     * @param locale the locale to get the bundle for
     * @return the ResourceBundle for the locale
     * @throws RuntimeException if the bundle cannot be loaded
     */
    private static ResourceBundle getResourceBundle(Locale locale) {
        return bundleCache.computeIfAbsent(locale, loc -> {
            try {
                ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, loc);
                return bundle;
            } catch (Exception e) {
                logger.error("Failed to load resource bundle for locale: {}", loc, e);
                throw new RuntimeException("Could not load resource bundle for locale: " + loc, e);
            }
        });
    }
    
    /**
     * Gets current locale information as a formatted string.
     * Useful for debugging and status display.
     * 
     * @return formatted locale information
     */
    public static String getLocaleInfo() {
        return String.format("Current locale: %s (%s), Available: %s, Cache size: %d",
                           currentLocale.toString(),
                           currentLocale.getDisplayName(),
                           java.util.Arrays.toString(getAvailableLocales()),
                           bundleCache.size());
    }
    
    /**
     * Notifies all registered listeners that the language has changed.
     * 
     * <p>
     * This method ensures that notifications are sent on the JavaFX
     * Application Thread to allow safe UI updates.
     * </p>
     */
    private static void notifyLanguageChanged() {
        if (changeListeners.isEmpty()) {
            return;
        }
        
        ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        if (config != null && config.getLoggingEnabled()) {
            logger.info("Notifying {} I18n change listeners about language change", changeListeners.size());
        }
        
        // Ensure UI updates happen on the JavaFX Application Thread
        if (Platform.isFxApplicationThread()) {
            // Already on JavaFX thread, notify directly
            notifyListenersDirectly();
        } else {
            // Schedule on JavaFX thread
            Platform.runLater(I18nManager::notifyListenersDirectly);
        }
    }
    
    /**
     * Directly notifies all listeners (must be called on JavaFX Application Thread).
     */
    private static void notifyListenersDirectly() {
        for (I18nChangeListener listener : changeListeners) {
            try {
                listener.onLanguageChanged();
            } catch (Exception e) {
                logger.error("Error notifying I18n change listener: {}", listener.getClass().getSimpleName(), e);
            }
        }
    }
}