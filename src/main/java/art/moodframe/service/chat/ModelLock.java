package art.moodframe.service.chat;

import java.util.function.Supplier;

/**
 * The lock of the chat models: the list of the chats of the {@link ChatLibrary}, the entries of each chat and the
 * images of each entry. These lists are shared by several threads, so one rule holds for them:
 *
 * <ul>
 *     <li>They are changed only under the lock ({@link #change}, {@link #changeAndGet}): by the library on the
 *     calling thread (the MCP server, the worker of the window), by a generation on the JavaFX thread.</li>
 *     <li>Every other access reads them under the lock, also on the JavaFX thread: {@link #read}, or a snapshot of a
 *     list ({@link ChatLibrary#entriesOf}, {@link ChatLibrary#imagesOf}). Nobody iterates a live list outside the
 *     lock, unless the work is part of a change and holds the lock anyway.</li>
 * </ul>
 *
 * <p>Only short work in memory runs under the lock, never a database query, a file or a wait for another thread, so
 * the JavaFX thread waits at most for such work, and nobody holding the lock waits for the JavaFX thread.</p>
 *
 * @since 2.1.3
 */
public final class ModelLock {

    private static final Object LOCK = new Object();

    private ModelLock() {
    }

    /**
     * Reads the models under the lock. The work must not change them; what it returns must not be a live list of the
     * models (copy it).
     *
     * @param work what to read
     * @param <T>  the type of the result
     * @return the result of the work
     */
    public static <T> T read(Supplier<T> work) {
        synchronized (LOCK) {
            return work.get();
        }
    }

    /**
     * Changes the models under the lock.
     *
     * @param change the change
     */
    public static void change(Runnable change) {
        synchronized (LOCK) {
            change.run();
        }
    }

    /**
     * Changes the models under the lock and tells a result of the change.
     *
     * @param change the change
     * @param <T>    the type of the result
     * @return the result of the change
     */
    public static <T> T changeAndGet(Supplier<T> change) {
        synchronized (LOCK) {
            return change.get();
        }
    }
}
