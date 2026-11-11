package tools.moodframe.util;

/**
 * Interface for components that need to be notified when the application language changes.
 * 
 * <p>
 * Components that implement this interface will automatically receive updates
 * when the user changes the language in the settings. This allows for immediate
 * UI refresh without requiring an application restart.
 * </p>
 * 
 * <p>
 * <strong>Usage Example:</strong>
 * </p>
 * <pre>
 * public class MyController implements I18nChangeListener {
 *     
 *     @Override
 *     public void onLanguageChanged() {
 *         // Update all text elements
 *         myButton.setText(I18nManager.getText("button.save"));
 *         myLabel.setText(I18nManager.getText("label.status"));
 *     }
 *     
 *     @FXML
 *     private void initialize() {
 *         // Register for language change notifications
 *         I18nManager.addChangeListener(this);
 *         
 *         // Set initial texts
 *         onLanguageChanged();
 *     }
 * }
 * </pre>
 * 
 * @since 1.0.0 Epic 9 - Internationalization
 */
@FunctionalInterface
public interface I18nChangeListener {
    
    /**
     * Called when the application language has changed.
     * 
     * <p>
     * Implementations should update all text elements to reflect
     * the new language. This method is called on the JavaFX
     * Application Thread, so UI updates are safe.
     * </p>
     */
    void onLanguageChanged();
}