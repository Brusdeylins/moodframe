package tools.moodframe.ui.component;

import tools.moodframe.util.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for UI components that require lifecycle management.
 * 
 * <p>This class provides automatic cleanup of resources like listeners,
 * bindings, and subscriptions. It implements the Disposable pattern to
 * prevent memory leaks in JavaFX applications.</p>
 * 
 * <p><strong>Key Features:</strong></p>
 * <ul>
 * <li>Automatic subscription management</li>
 * <li>I18n support with automatic cleanup</li>
 * <li>Thread-safe disposal</li>
 * <li>Template method for custom cleanup</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * public class MyCard extends ManagedComponent implements I18nChangeListener {
 *     
 *     public MyCard() {
 *         super();
 *         
 *         // Register for I18n (automatically cleaned up)
 *         registerI18nListener(this);
 *         
 *         // Add property listeners (automatically managed)
 *         addSubscription(Subscription.of(someProperty, this::handleChange));
 *     }
 *     
 *     {@literal @}Override
 *     protected void onDispose() {
 *         // Custom cleanup here
 *         customCleanup();
 *     }
 *     
 *     {@literal @}Override
 *     public void onLanguageChanged() {
 *         // Update UI texts
 *     }
 * }
 * </pre>
 * 
 * @since 1.2.0 - Memory Leak Prevention
 */
public abstract class ManagedComponent extends VBox implements Disposable, I18nChangeListener {
    
    private static final Logger logger = LoggerFactory.getLogger(ManagedComponent.class);
    
    private final CompositeDisposable subscriptions = new CompositeDisposable();
    private volatile boolean disposed = false;
    private final String componentName;
    
    // I18n management
    private boolean i18nRegistered = false;
    
    /**
     * Creates a new managed component.
     */
    protected ManagedComponent() {
        this.componentName = getClass().getSimpleName();
        logger.trace("Created ManagedComponent: {}", componentName);
    }
    
    /**
     * Adds a subscription to be managed by this component.
     * 
     * <p>All added subscriptions will be automatically unsubscribed
     * when this component is disposed.</p>
     * 
     * @param subscription the subscription to manage
     * @return true if the subscription was added, false if already disposed
     */
    protected final boolean addSubscription(Subscription subscription) {
        if (disposed) {
            logger.warn("Cannot add subscription to disposed component: {}", componentName);
            if (subscription != null) {
                subscription.unsubscribe();
            }
            return false;
        }
        
        return subscriptions.add(subscription);
    }
    
    /**
     * Registers this component as an I18n change listener.
     * 
     * <p>The registration will be automatically cleaned up when
     * the component is disposed.</p>
     * 
     * @param listener the listener to register (typically 'this')
     */
    protected final void registerI18nListener(I18nChangeListener listener) {
        if (disposed) {
            logger.warn("Cannot register I18n listener for disposed component: {}", componentName);
            return;
        }
        
        if (i18nRegistered) {
            logger.warn("I18n listener already registered for: {}", componentName);
            return;
        }
        
        I18nManager.addChangeListener(listener);
        i18nRegistered = true;
        
        logger.trace("Registered I18n listener for: {}", componentName);
    }
    
    /**
     * Gets the number of active subscriptions managed by this component.
     * 
     * @return the subscription count
     */
    protected final int getSubscriptionCount() {
        return subscriptions.size();
    }
    
    /**
     * Returns whether this component has been disposed.
     * 
     * @return true if disposed, false otherwise
     */
    @Override
    public final boolean isDisposed() {
        return disposed;
    }
    
    /**
     * Disposes this component and all managed resources.
     * 
     * <p>This method is idempotent and thread-safe. It will:</p>
     * <ul>
     * <li>Unsubscribe all managed subscriptions</li>
     * <li>Remove I18n listener registration</li>
     * <li>Clear all child nodes</li>
     * <li>Call the template method onDispose() for custom cleanup</li>
     * </ul>
     */
    @Override
    public final void dispose() {
        if (disposed) {
            return;
        }
        
        logger.trace("Disposing ManagedComponent: {} with {} subscriptions", 
                    componentName, subscriptions.size());
        
        try {
            // Call template method first (while component is still functional)
            onDispose();
            
            // Clean up managed resources
            subscriptions.dispose();
            
            // Remove I18n listener
            if (i18nRegistered) {
                I18nManager.removeChangeListener(this);
                i18nRegistered = false;
            }
            
            // Clear UI hierarchy
            getChildren().clear();
            
            disposed = true;
            
            logger.trace("Successfully disposed ManagedComponent: {}", componentName);
            
        } catch (Exception e) {
            logger.error("Error during disposal of {}: {}", componentName, e.getMessage(), e);
            disposed = true; // Mark as disposed even if cleanup failed
        }
    }
    
    /**
     * Template method for custom cleanup logic.
     * 
     * <p>Subclasses should override this method to perform any
     * component-specific cleanup. This method is called before
     * the standard cleanup operations.</p>
     * 
     * <p><strong>Important:</strong> Do not call dispose() from within
     * this method as it may cause infinite recursion.</p>
     */
    protected void onDispose() {
        // Default implementation does nothing
        // Subclasses can override for custom cleanup
    }
    
    /**
     * Default implementation of language change handling.
     * 
     * <p>Subclasses should override this method to update their
     * UI texts when the language changes.</p>
     */
    @Override
    public void onLanguageChanged() {
        if (disposed) {
            logger.trace("Ignoring language change for disposed component: {}", componentName);
            return;
        }
        
        // Default implementation does nothing
        // Subclasses should override to update UI
    }
    
    /**
     * Checks that the component is not disposed before performing operations.
     * 
     * @throws IllegalStateException if the component is disposed
     */
    protected final void checkNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("Component " + componentName + " has been disposed");
        }
    }
    
    @Override
    public String toString() {
        return String.format("%s[disposed=%s, subscriptions=%d, i18nRegistered=%s]", 
                           componentName, disposed, subscriptions.size(), i18nRegistered);
    }
}