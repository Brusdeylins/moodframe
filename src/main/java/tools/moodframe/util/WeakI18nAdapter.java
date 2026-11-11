package tools.moodframe.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;

/**
 * A weak reference wrapper for I18nChangeListener that automatically
 * removes itself from the I18nManager when the target listener is
 * garbage collected.
 * 
 * <p>This class solves the memory leak issue where components register
 * themselves as I18n listeners but forget to unregister when they are
 * disposed, causing strong references that prevent garbage collection.</p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * public class MyComponent implements I18nChangeListener {
 *     
 *     public MyComponent() {
 *         // Use weak adapter instead of direct registration
 *         I18nManager.addChangeListener(new WeakI18nAdapter(this));
 *         // No need to manually remove - happens automatically on GC
 *     }
 *     
 *     {@literal @}Override
 *     public void onLanguageChanged() {
 *         // Update UI
 *     }
 * }
 * </pre>
 * 
 * <p><strong>Important Notes:</strong></p>
 * <ul>
 * <li>The target listener must be kept alive by other references</li>
 * <li>If the target is only referenced by this adapter, it may be GC'd immediately</li>
 * <li>For disposable components, prefer explicit removal over weak references</li>
 * </ul>
 * 
 * @since 1.2.0 - Memory Leak Prevention
 */
public class WeakI18nAdapter implements I18nChangeListener {
    
    private static final Logger logger = LoggerFactory.getLogger(WeakI18nAdapter.class);
    
    private final WeakReference<I18nChangeListener> weakTarget;
    private final String targetClassName;
    
    /**
     * Creates a new weak adapter for the given I18n listener.
     * 
     * @param target the target listener to wrap (must not be null)
     * @throws IllegalArgumentException if target is null
     */
    public WeakI18nAdapter(I18nChangeListener target) {
        if (target == null) {
            throw new IllegalArgumentException("Target listener cannot be null");
        }
        
        this.weakTarget = new WeakReference<>(target);
        this.targetClassName = target.getClass().getSimpleName();
        
        logger.trace("Created WeakI18nAdapter for: {}", targetClassName);
    }
    
    /**
     * Forwards the language change notification to the target listener,
     * or removes this adapter if the target has been garbage collected.
     */
    @Override
    public void onLanguageChanged() {
        I18nChangeListener target = weakTarget.get();
        
        if (target != null) {
            try {
                target.onLanguageChanged();
            } catch (Exception e) {
                logger.error("Error in weak I18n listener {}: {}", targetClassName, e.getMessage(), e);
            }
        } else {
            // Target has been garbage collected - remove ourselves
            logger.trace("Target {} was garbage collected, removing WeakI18nAdapter", targetClassName);
            I18nManager.removeChangeListener(this);
        }
    }
    
    /**
     * Returns whether the target listener is still alive.
     * 
     * @return true if the target is still referenced, false if garbage collected
     */
    public boolean isTargetAlive() {
        return weakTarget.get() != null;
    }
    
    /**
     * Gets the class name of the target listener for debugging.
     * 
     * @return the target's class name
     */
    public String getTargetClassName() {
        return targetClassName;
    }
    
    @Override
    public String toString() {
        return String.format("WeakI18nAdapter[target=%s, alive=%s]", targetClassName, isTargetAlive());
    }
}