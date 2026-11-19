package art.moodframe.ui.brainstorm;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The worker of the brainstorm window: the reads and writes of the stored sessions run on it, off the JavaFX thread,
 * one after the other in the order they were asked for, and their results go back to the JavaFX thread. Its thread
 * ends when idle.
 *
 * @since 2.1.3
 */
final class BrainstormWorker {

    private static final Logger logger = LoggerFactory.getLogger(BrainstormWorker.class);

    private final Executor worker;
    private final Executor fx;

    /**
     * Creates the worker.
     *
     * @param worker runs the blocking work, one task after the other
     * @param fx     runs the results on the JavaFX thread
     */
    BrainstormWorker(Executor worker, Executor fx) {
        this.worker = worker;
        this.fx = fx;
    }

    /** Creates the worker of a window: a daemon thread of its own, and {@link Platform#runLater}. */
    static BrainstormWorker create() {
        return new BrainstormWorker(new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                (Runnable runnable) -> {
                    Thread thread = new Thread(runnable, "BrainstormWindowWorker");
                    thread.setDaemon(true);
                    return thread;
                }), Platform::runLater);
    }

    /**
     * Runs work on the worker and its result on the JavaFX thread; a failure is logged.
     *
     * @param work the blocking work
     * @param then takes the result on the JavaFX thread
     * @param <T>  the type of the result
     */
    <T> void run(Supplier<T> work, Consumer<T> then) {
        supply(work).thenAcceptAsync(then, fx).exceptionally((Throwable error) -> {
            logger.error("Brainstorm window work failed", error);
            return null;
        });
    }

    /**
     * Runs work on the worker; a failure is logged.
     *
     * @param work the blocking work
     */
    void run(Runnable work) {
        run(() -> {
            work.run();
            return null;
        }, (Object done) -> { });
    }

    /**
     * Runs work on the worker.
     *
     * @param work the blocking work
     * @param <T>  the type of the result
     * @return the result, completed on the worker
     */
    <T> CompletableFuture<T> supply(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, worker);
    }

    /** The JavaFX thread, to hand a result there. */
    Executor fx() {
        return fx;
    }
}
