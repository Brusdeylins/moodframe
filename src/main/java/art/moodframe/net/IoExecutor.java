package art.moodframe.net;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The executor of the blocking network work: the calls of the providers (some wait minutes for an image or poll a
 * video), the downloads of the results and the brainstorm tools. Such work must not run on
 * {@link java.util.concurrent.ForkJoinPool#commonPool()}, which the UI code and the parallel streams share.
 *
 * <p>The pool has at most {@link #MAX_THREADS} daemon threads named {@code moodframe-io-N}; idle threads end after a
 * minute and further tasks wait in the queue. A task may wait for another task of this executor (the brainstorm
 * waits for its research), so the bound is kept well above such chains.</p>
 *
 * <p>{@code MoodframeApplication} shuts it down on exit; a later {@link #get()} starts a new pool, e.g. in tests.</p>
 *
 * @since 2.1.3
 */
public final class IoExecutor {

    /** The most threads at once: four images of every provider, the brainstorm tools and the account checks. */
    public static final int MAX_THREADS = 32;

    private static ExecutorService executor;

    private IoExecutor() {
    }

    /**
     * Gets the executor, started on first use.
     *
     * @return the executor of the blocking network work
     */
    public static synchronized ExecutorService get() {
        if (executor == null || executor.isShutdown())
            executor = create();
        return executor;
    }

    /**
     * Stops the executor: running tasks are interrupted, waiting ones dropped. The threads are daemons, so a task
     * that ignores the interrupt does not keep the application alive.
     */
    public static synchronized void shutdown() {
        if (executor != null)
            executor.shutdownNow();
        executor = null;
    }

    private static ExecutorService create() {
        AtomicInteger count = new AtomicInteger();
        ThreadFactory threads = (Runnable task) -> {
            Thread thread = new Thread(task, "moodframe-io-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        ThreadPoolExecutor pool = new ThreadPoolExecutor(MAX_THREADS, MAX_THREADS, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), threads);
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }
}
