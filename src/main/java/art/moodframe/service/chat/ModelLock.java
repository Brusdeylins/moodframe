package art.moodframe.service.chat;

import java.util.function.Supplier;

/**
 * The lock of the chat models: the list of the chats of the {@link ChatLibrary}, their entries and images. The
 * library changes them under it on the calling thread, a generation on the JavaFX thread, and a caller off the JavaFX
 * thread reads them under it (see {@link ChatLibrary#read}), so it sees no change half done. Only short work in memory
 * runs under the lock, never a database query, a file or a wait for another thread.
 *
 * @since 2.1.3
 */
public final class ModelLock {

    private static final Object LOCK = new Object();

    private ModelLock() {
    }

    /**
     * Reads (and possibly changes) the models under the lock.
     *
     * @param work what to do
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
}
