package art.moodframe.service.generation;

import art.moodframe.service.chat.ModelLock;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The one place where a generation changes the chat entries and images the user interface is bound to: they carry
 * JavaFX properties, so they are changed on the JavaFX thread, in the order of the calls, and under the
 * {@link ModelLock}, so a reader on another thread (the MCP server) sees no change half done. Nothing blocking runs in a
 * change: the database writes of {@link ChatEntryTracker#saveImage} and {@link ChatEntryTracker#saveEntry} only hand
 * the object to their worker, files and requests run on workers before.
 *
 * @since 2.1.3
 */
final class FxModel {

    private static final Logger logger = LoggerFactory.getLogger(FxModel.class);

    private FxModel() {
    }

    /**
     * Changes chat entries or images on the JavaFX thread, after the changes queued before.
     *
     * @param change the change
     * @return completes once the change ran, exceptionally if it failed (which is logged)
     */
    static CompletableFuture<Void> change(Runnable change) {
        return changeAndGet(() -> {
            change.run();
            return null;
        });
    }

    /**
     * Changes chat entries or images on the JavaFX thread, after the changes queued before, and tells a result of the
     * change.
     *
     * @param change the change
     * @param <T>    the result
     * @return completes with the result once it ran, exceptionally if it failed (which is logged)
     */
    static <T> CompletableFuture<T> changeAndGet(Supplier<T> change) {
        CompletableFuture<T> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                done.complete(ModelLock.changeAndGet(change));
            } catch (RuntimeException e) {
                logger.error("Failed to update the chat entry of a generation", e);
                done.completeExceptionally(e);
            }
        });
        return done;
    }

    /**
     * Hands a notification of the listeners of a generation to the JavaFX thread, later in its queue.
     *
     * @param notification calls the listeners
     */
    static void later(Runnable notification) {
        Platform.runLater(notification);
    }
}
