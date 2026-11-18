package art.moodframe.ui.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Loads data for the JavaFX thread on a worker: a request while a load runs gives one more load after it, so a burst of
 * changes queries once or twice instead of once per change, and an older result never overtakes a newer one.
 *
 * <p>Requested and applied on the JavaFX thread only. The load is prepared there from the current state of the view:
 * a {@link Callable} for the worker that returns what then runs on the JavaFX thread, or null if nothing is to be
 * loaded.</p>
 *
 * @since 2.1.3
 */
final class CoalescedLoad {

    private static final Logger logger = LoggerFactory.getLogger(CoalescedLoad.class);

    private final String what;
    private final Executor worker;
    private final Executor fxThread;
    private final Supplier<Callable<Runnable>> prepare;

    /*  Whether a load runs and whether a request came meanwhile; touched on the JavaFX thread only  */
    private boolean running;
    private boolean again;

    /**
     * Creates the load.
     *
     * @param what     what is loaded, for the log
     * @param worker   where the query runs
     * @param fxThread where its result is applied, {@code Platform::runLater}
     * @param prepare  prepares a load from the current view, null if there is nothing to load
     */
    CoalescedLoad(String what, Executor worker, Executor fxThread, Supplier<Callable<Runnable>> prepare) {
        this.what = what;
        this.worker = worker;
        this.fxThread = fxThread;
        this.prepare = prepare;
    }

    /** Loads anew; while a load runs, once more after it. */
    void request() {
        if (running) {
            again = true;
            return;
        }
        Callable<Runnable> load = prepare.get();
        if (load == null)
            return;
        running = true;
        worker.execute(() -> {
            Runnable apply = null;
            try {
                apply = load.call();
            } catch (Exception e) {
                logger.error("Failed to load {}", what, e);
            }
            Runnable result = apply;
            fxThread.execute(() -> finish(result));
        });
    }

    /*  Applies a result and starts the load that was requested meanwhile  */
    private void finish(Runnable apply) {
        running = false;
        try {
            if (apply != null)
                apply.run();
        } finally {
            if (again) {
                again = false;
                request();
            }
        }
    }
}
