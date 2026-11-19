package art.moodframe.service.generation;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The threads of the generation besides the I/O executor: the polling of the jobs ({@code generation-poll}) and the
 * one thread of the database writes ({@code generation-store}), which keeps them in the order of the changes they
 * store. Named daemon threads, started on their first task; the composition root creates them once and hands them to
 * the generation.
 *
 * @since 2.1.3
 */
public class GenerationWorkers {

    private final ScheduledExecutorService polling = Executors.newScheduledThreadPool(2,
            daemonThreads("generation-poll"));
    private final ExecutorService store = Executors.newSingleThreadExecutor(daemonThreads("generation-store"));

    /**
     * Gets the threads that poll the jobs.
     *
     * @return the scheduler
     */
    public ScheduledExecutorService polling() {
        return polling;
    }

    /**
     * Gets the one thread of the database writes of the generation: the chat entry of an order and the changes of its
     * entry and images, in their order.
     *
     * @return the executor
     */
    public ExecutorService store() {
        return store;
    }

    /**
     * Stops the polling and ends the database writes asked for, waiting up to 5 seconds for each.
     */
    public void shutdown() {
        polling.shutdown();
        store.shutdown();
        try {
            if (!polling.awaitTermination(5, TimeUnit.SECONDS))
                polling.shutdownNow();
            if (!store.awaitTermination(5, TimeUnit.SECONDS))
                store.shutdownNow();
        }
        catch (InterruptedException e) {
            polling.shutdownNow();
            store.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /*  named daemon threads, so the workers never keep the application alive  */
    private static ThreadFactory daemonThreads(String name) {
        AtomicInteger count = new AtomicInteger();
        return (Runnable runnable) -> {
            Thread thread = new Thread(runnable, name + "-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
