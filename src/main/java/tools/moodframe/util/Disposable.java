package tools.moodframe.util;

/**
 * Interface for objects that require explicit cleanup of resources.
 * 
 * <p>This interface provides a standard way to clean up resources like
 * listeners, bindings, threads, and other objects that might prevent
 * garbage collection if not properly disposed.</p>
 * 
 * <p><strong>Implementation Guidelines:</strong></p>
 * <ul>
 * <li>dispose() should be idempotent - safe to call multiple times</li>
 * <li>After disposal, the object should be in a safe, non-functional state</li>
 * <li>isDisposed() should return true after dispose() is called</li>
 * <li>Dispose children before parent objects</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * public class MyComponent extends VBox implements Disposable {
 *     private boolean disposed = false;
 *     
 *     {@literal @}Override
 *     public void dispose() {
 *         if (disposed) return;
 *         
 *         // Clean up resources
 *         removeListeners();
 *         clearChildren();
 *         
 *         disposed = true;
 *     }
 *     
 *     {@literal @}Override
 *     public boolean isDisposed() {
 *         return disposed;
 *     }
 * }
 * </pre>
 * 
 * @since 1.2.0 - Memory Leak Prevention
 */
public interface Disposable {
    
    /**
     * Disposes this object and cleans up all associated resources.
     * 
     * <p>This method should remove all listeners, clear all bindings,
     * stop all threads, close all streams, and perform any other
     * cleanup necessary to allow garbage collection.</p>
     * 
     * <p>This method should be idempotent - calling it multiple times
     * should have the same effect as calling it once.</p>
     * 
     * <p>After disposal, the object should be considered non-functional
     * and should not be used for normal operations.</p>
     */
    void dispose();
    
    /**
     * Returns whether this object has been disposed.
     * 
     * @return true if dispose() has been called, false otherwise
     */
    boolean isDisposed();
}