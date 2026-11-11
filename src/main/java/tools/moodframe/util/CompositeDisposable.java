package tools.moodframe.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Collection;

/**
 * A container for multiple subscriptions that can be disposed all at once.
 * 
 * <p>This class provides a convenient way to manage multiple subscriptions
 * and ensures they are all properly cleaned up together. It's particularly
 * useful for components that create many subscriptions during their lifecycle.</p>
 * 
 * <p><strong>Thread Safety:</strong> This class is thread-safe and can be
 * used concurrently from multiple threads.</p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * public class MyComponent implements Disposable {
 *     private final CompositeDisposable disposables = new CompositeDisposable();
 *     
 *     public void initialize() {
 *         // Add subscriptions as they are created
 *         disposables.add(Subscription.of(property1, listener1));
 *         disposables.add(Subscription.of(property2, listener2));
 *     }
 *     
 *     {@literal @}Override
 *     public void dispose() {
 *         disposables.dispose(); // Cleans up all subscriptions
 *     }
 * }
 * </pre>
 * 
 * @since 1.2.0 - Memory Leak Prevention
 */
public class CompositeDisposable implements Disposable {
    
    private static final Logger logger = LoggerFactory.getLogger(CompositeDisposable.class);
    
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private volatile boolean disposed = false;
    
    /**
     * Adds a subscription to this composite.
     * 
     * <p>If this composite has already been disposed, the subscription
     * will be immediately unsubscribed.</p>
     * 
     * @param subscription the subscription to add (null subscriptions are ignored)
     * @return true if the subscription was added, false if this composite is disposed
     */
    public boolean add(Subscription subscription) {
        if (subscription == null) {
            logger.trace("Ignoring null subscription");
            return false;
        }
        
        if (disposed) {
            logger.trace("CompositeDisposable already disposed, unsubscribing immediately: {}", 
                        subscription.getDescription());
            subscription.unsubscribe();
            return false;
        }
        
        boolean added = subscriptions.add(subscription);
        if (added) {
            logger.trace("Added subscription: {}", subscription.getDescription());
        }
        return added;
    }
    
    /**
     * Adds multiple subscriptions to this composite.
     * 
     * @param subscriptions the subscriptions to add
     * @return the number of subscriptions successfully added
     */
    public int addAll(Collection<Subscription> subscriptions) {
        if (subscriptions == null || subscriptions.isEmpty()) {
            return 0;
        }
        
        int added = 0;
        for (Subscription subscription : subscriptions) {
            if (add(subscription)) {
                added++;
            }
        }
        return added;
    }
    
    /**
     * Removes a subscription from this composite without unsubscribing it.
     * 
     * @param subscription the subscription to remove
     * @return true if the subscription was removed, false otherwise
     */
    public boolean remove(Subscription subscription) {
        if (subscription == null) {
            return false;
        }
        
        boolean removed = subscriptions.remove(subscription);
        if (removed) {
            logger.trace("Removed subscription: {}", subscription.getDescription());
        }
        return removed;
    }
    
    /**
     * Removes and unsubscribes a subscription from this composite.
     * 
     * @param subscription the subscription to remove and unsubscribe
     * @return true if the subscription was removed, false otherwise
     */
    public boolean removeAndUnsubscribe(Subscription subscription) {
        if (subscription == null) {
            return false;
        }
        
        boolean removed = remove(subscription);
        if (removed || subscriptions.contains(subscription)) {
            subscription.unsubscribe();
        }
        return removed;
    }
    
    /**
     * Returns the number of active (non-disposed) subscriptions.
     * 
     * @return the number of subscriptions
     */
    public int size() {
        return subscriptions.size();
    }
    
    /**
     * Returns whether this composite is empty.
     * 
     * @return true if no subscriptions are present
     */
    public boolean isEmpty() {
        return subscriptions.isEmpty();
    }
    
    /**
     * Clears all subscriptions without unsubscribing them.
     * 
     * <p><strong>Warning:</strong> This may cause memory leaks if the
     * subscriptions are not unsubscribed elsewhere. Use {@link #dispose()}
     * instead for proper cleanup.</p>
     */
    public void clear() {
        int count = subscriptions.size();
        subscriptions.clear();
        logger.trace("Cleared {} subscriptions without unsubscribing", count);
    }
    
    /**
     * Disposes all subscriptions and clears the collection.
     * 
     * <p>This method is idempotent - calling it multiple times will
     * only dispose the subscriptions once.</p>
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        
        logger.trace("Disposing CompositeDisposable with {} subscriptions", subscriptions.size());
        
        // Copy the list to avoid concurrent modification
        List<Subscription> toDispose = List.copyOf(subscriptions);
        subscriptions.clear();
        
        // Dispose all subscriptions
        int disposed = 0;
        for (Subscription subscription : toDispose) {
            try {
                if (!subscription.isUnsubscribed()) {
                    subscription.unsubscribe();
                    disposed++;
                }
            } catch (Exception e) {
                logger.error("Error disposing subscription: {}", subscription.getDescription(), e);
            }
        }
        
        this.disposed = true;
        logger.trace("Disposed {} subscriptions", disposed);
    }
    
    /**
     * Returns whether this composite has been disposed.
     * 
     * @return true if disposed, false otherwise
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }
    
    @Override
    public String toString() {
        return String.format("CompositeDisposable[size=%d, disposed=%s]", size(), disposed);
    }
}