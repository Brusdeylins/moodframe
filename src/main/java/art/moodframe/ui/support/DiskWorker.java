package art.moodframe.ui.support;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The executor of the blocking work of the views that is no network: reading and decoding pictures, writing and
 * comparing attached files, ffmpeg and the other processes of the desktop. Such work must not run on the JavaFX
 * thread nor on {@link java.util.concurrent.ForkJoinPool#commonPool()}, which the parallel streams share.
 *
 * <p>The pool has at most {@link #MAX_THREADS} daemon threads named {@code moodframe-disk-N}, so a chat full of
 * pictures does not read the disk with more threads at once; idle threads end after a minute and further tasks wait
 * in the queue. No task waits for another task of this executor, which might wait in the queue behind it.</p>
 *
 * @since 2.1.3
 */
public final class DiskWorker {

    /** The most threads at once. */
    public static final int MAX_THREADS = 4;

    private static final Executor EXECUTOR = create();

    private DiskWorker() {
    }

    /**
     * Gets the executor.
     *
     * @return the executor of the blocking disk and process work of the views
     */
    public static Executor get() {
        return EXECUTOR;
    }

    private static Executor create() {
        AtomicInteger count = new AtomicInteger();
        ThreadFactory threads = (Runnable task) -> {
            Thread thread = new Thread(task, "moodframe-disk-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        ThreadPoolExecutor pool = new ThreadPoolExecutor(MAX_THREADS, MAX_THREADS, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), threads);
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }
}
