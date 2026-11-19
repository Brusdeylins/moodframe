package art.moodframe.ui.panel;

import art.moodframe.ui.support.StatusSink;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs the writes of the panel on the worker of its window, off the JavaFX thread, so a write that waits for the lock
 * of the database (an MCP write, for example) does not freeze the window; the panel shows the outcome on the JavaFX
 * thread afterwards. A write that throws is told by the status sink.
 */
final class PanelWorker {

    private static final Logger logger = LoggerFactory.getLogger(PanelWorker.class);

    private final Executor worker;
    private final StatusSink status;

    /**
     * Takes the worker and where a failure is told.
     *
     * @param worker runs the writes one after the other, off the JavaFX thread
     * @param status shows a failed write
     */
    PanelWorker(Executor worker, StatusSink status) {
        this.worker = worker;
        this.status = status;
    }

    /**
     * Runs a work on the worker and hands its result to the JavaFX thread.
     *
     * @param work    the work, e.g. a write of the library
     * @param failed  the result of a work that threw
     * @param failure the localized detail shown if the work throws
     * @param then    takes the result on the JavaFX thread; not called if the work threw
     * @param <T>     the type of the result
     * @return completes with the result on the JavaFX thread, once {@code then} ran or the failure was told
     */
    <T> CompletableFuture<T> run(Supplier<T> work, T failed, String failure, Consumer<T> then) {
        CompletableFuture<T> done = new CompletableFuture<>();
        worker.execute(() -> {
            try {
                T result = work.get();
                Platform.runLater(() -> {
                    try {
                        then.accept(result);
                    }
                    finally {
                        done.complete(result);
                    }
                });
            }
            catch (RuntimeException e) {
                logger.error("Failed on the worker: {}", failure, e);
                status.failure(failure);
                Platform.runLater(() -> done.complete(failed));
            }
        });
        return done;
    }

    /**
     * Runs a work on the worker; the caller tells a failure, e.g. a dialog in a message box of its own.
     *
     * @param work the work, e.g. a write of the library
     * @param <T>  the type of the result
     * @return completes on the JavaFX thread with the result, or exceptionally with what the work threw
     */
    <T> CompletableFuture<T> call(Callable<T> work) {
        CompletableFuture<T> done = new CompletableFuture<>();
        worker.execute(() -> {
            try {
                T result = work.call();
                Platform.runLater(() -> done.complete(result));
            }
            catch (Exception e) {
                Platform.runLater(() -> done.completeExceptionally(e));
            }
        });
        return done;
    }
}
