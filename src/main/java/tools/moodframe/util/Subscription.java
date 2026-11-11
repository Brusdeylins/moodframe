package tools.moodframe.util;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a subscription to an observable that can be cleanly unsubscribed.
 * 
 * <p>This class provides a unified way to manage listener subscriptions and
 * ensures that all listeners are properly removed when no longer needed,
 * preventing memory leaks.</p>
 * 
 * <p><strong>Usage Examples:</strong></p>
 * <pre>
 * // Property listener subscription
 * Subscription sub = Subscription.of(property, listener);
 * 
 * // Later, clean up
 * sub.unsubscribe();
 * 
 * // Batch cleanup
 * List&lt;Subscription&gt; subs = Arrays.asList(sub1, sub2, sub3);
 * subs.forEach(Subscription::unsubscribe);
 * </pre>
 * 
 * @since 1.2.0 - Memory Leak Prevention
 */
public class Subscription {
    
    private static final Logger logger = LoggerFactory.getLogger(Subscription.class);
    
    private final Runnable cleanup;
    private volatile boolean unsubscribed = false;
    private final String description;
    
    /**
     * Creates a new subscription with the given cleanup action.
     * 
     * @param cleanup the action to perform when unsubscribing
     * @param description optional description for debugging
     */
    private Subscription(Runnable cleanup, String description) {
        this.cleanup = cleanup != null ? cleanup : () -> {};
        this.description = description != null ? description : "Unknown";
    }
    
    /**
     * Creates a subscription for an InvalidationListener on an Observable.
     * 
     * @param observable the observable to listen to
     * @param listener the listener to add
     * @return a subscription that will remove the listener when unsubscribed
     */
    public static Subscription of(Observable observable, InvalidationListener listener) {
        if (observable == null || listener == null) {
            logger.warn("Cannot create subscription with null observable or listener");
            return empty();
        }
        
        observable.addListener(listener);
        String desc = String.format("InvalidationListener on %s", observable.getClass().getSimpleName());
        
        return new Subscription(() -> {
            try {
                observable.removeListener(listener);
                logger.trace("Removed InvalidationListener from {}", observable.getClass().getSimpleName());
            } catch (Exception e) {
                logger.warn("Failed to remove InvalidationListener", e);
            }
        }, desc);
    }
    
    /**
     * Creates a subscription for a ChangeListener on an ObservableValue.
     * 
     * @param observable the observable value to listen to  
     * @param listener the listener to add
     * @return a subscription that will remove the listener when unsubscribed
     */
    public static <T> Subscription of(ObservableValue<T> observable, ChangeListener<? super T> listener) {
        if (observable == null || listener == null) {
            logger.warn("Cannot create subscription with null observable or listener");
            return empty();
        }
        
        observable.addListener(listener);
        String desc = String.format("ChangeListener on %s", observable.getClass().getSimpleName());
        
        return new Subscription(() -> {
            try {
                observable.removeListener(listener);
                logger.trace("Removed ChangeListener from {}", observable.getClass().getSimpleName());
            } catch (Exception e) {
                logger.warn("Failed to remove ChangeListener", e);
            }
        }, desc);
    }
    
    /**
     * Creates a subscription with a custom cleanup action.
     * 
     * @param cleanup the cleanup action to perform
     * @param description description of the subscription for debugging
     * @return a subscription that will execute the cleanup when unsubscribed
     */
    public static Subscription of(Runnable cleanup, String description) {
        return new Subscription(cleanup, description);
    }
    
    /**
     * Creates an empty subscription that does nothing when unsubscribed.
     * Useful for error cases or optional subscriptions.
     * 
     * @return an empty subscription
     */
    public static Subscription empty() {
        return new Subscription(() -> {}, "Empty");
    }
    
    /**
     * Unsubscribes this subscription, performing the cleanup action.
     * 
     * <p>This method is idempotent - calling it multiple times will
     * only perform the cleanup once.</p>
     */
    public void unsubscribe() {
        if (unsubscribed) {
            return;
        }
        
        try {
            cleanup.run();
            logger.trace("Unsubscribed: {}", description);
        } catch (Exception e) {
            logger.error("Error during unsubscribe of {}: {}", description, e.getMessage(), e);
        } finally {
            unsubscribed = true;
        }
    }
    
    /**
     * Returns whether this subscription has been unsubscribed.
     * 
     * @return true if unsubscribed, false otherwise
     */
    public boolean isUnsubscribed() {
        return unsubscribed;
    }
    
    /**
     * Returns the description of this subscription.
     * 
     * @return the subscription description
     */
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return String.format("Subscription[%s, unsubscribed=%s]", description, unsubscribed);
    }
}