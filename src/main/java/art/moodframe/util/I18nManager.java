package art.moodframe.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * Central internationalization manager for MoodFrame application.
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
    
    // List of components that need to be notified when language changes, each with the thread it is notified on
    private static final List<Registration> changeListeners = new CopyOnWriteArrayList<>();

    /** A listener with the executor that notifies it, e.g. the JavaFX thread for a view. */
    private record Registration(I18nChangeListener listener, Executor executor) {
    }
    
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
        
        logger.debug("Changing application locale from {} to {}", currentLocale, locale);
        currentLocale = locale;
        
        // Pre-load the resource bundle for the new locale to detect any issues early
        try {
            getResourceBundle(locale);
            logger.debug("Successfully loaded resource bundle for locale: {}", locale);
            
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
     * Formats an amount of money with two decimals in the number format of the language of the user interface, e.g.
     * "0.07" in English and "0,07" in German, whatever the language of the system; the currency is up to the caller.
     *
     * @param amount the amount
     * @return the amount with two decimals
     * @since 2.1.3
     */
    public static String formatCost(double amount) {
        return String.format(currentLocale, "%.2f", amount);
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
            /*  numbers in the parameters follow the language of the user interface, not that of the system  */
            return new MessageFormat(rawText, currentLocale).format(parameters);
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to format message for key '{}' with parameters: {}. Raw text: '{}'", 
                       key, java.util.Arrays.toString(parameters), rawText, e);
            return rawText; // Return unformatted text as fallback
        }
    }
    
    /**
     * Retrieves a text in a given language, independent of the language of the user interface, e.g. the English text
     * of an error for the MCP clients.
     *
     * @param locale     the language
     * @param key        the resource key (must not be null or empty)
     * @param parameters the parameters to substitute (can be empty)
     * @return the text in that language, else in English
     * @since 2.1.3
     */
    public static String getTextIn(Locale locale, String key, Object... parameters) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        String rawText = getTextInternal(key, locale);
        if (parameters == null || parameters.length == 0) {
            return rawText;
        }
        try {
            return new MessageFormat(rawText, locale).format(parameters);
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to format message for key '{}' in {}", key, locale, e);
            return rawText;
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
     * Adds a change listener that will be notified when the language changes, through the given executor. A view
     * registers with {@code ui.support.FxI18n}, which notifies it on the JavaFX thread.
     *
     * @param listener the listener to add (must not be null)
     * @param executor runs the notification, e.g. on the JavaFX thread (must not be null)
     * @throws IllegalArgumentException if listener or executor is null
     * @since 2.1.3
     */
    public static void addChangeListener(I18nChangeListener listener, Executor executor) {
        if (listener == null || executor == null) {
            throw new IllegalArgumentException("Listener and executor cannot be null");
        }

        changeListeners.add(new Registration(listener, executor));
    }
    
    /**
     * Removes a change listener.
     * 
     * @param listener the listener to remove
     * @return true if the listener was removed, false if it was not found
     */
    public static boolean removeChangeListener(I18nChangeListener listener) {
        for (Registration registration : changeListeners) {
            if (registration.listener().equals(listener)) {
                return changeListeners.remove(registration);
            }
        }
        return false;
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
     * Notifies all registered listeners that the language has changed, each through its executor.
     */
    private static void notifyLanguageChanged() {
        if (changeListeners.isEmpty()) {
            return;
        }
        
        logger.debug("Notifying {} I18n change listeners about language change", changeListeners.size());
        
        for (Registration registration : changeListeners) {
            /*  a listener removed before its executor runs the notification is not notified  */
            registration.executor().execute(() -> {
                if (changeListeners.contains(registration)) {
                    notifyListener(registration.listener());
                }
            });
        }
    }
    
    /**
     * Notifies one listener, logging its failure.
     */
    private static void notifyListener(I18nChangeListener listener) {
        try {
            listener.onLanguageChanged();
        } catch (Exception e) {
            logger.error("Error notifying I18n change listener: {}", listener.getClass().getSimpleName(), e);
        }
    }
}