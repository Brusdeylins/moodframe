package art.moodframe.service.brainstorm;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The queue of every brainstorm session: the work of one session runs one after the other, the work of different
 * sessions in parallel. It belongs to the shared {@link BrainstormSessions}, so it spans all brainstorm windows: a
 * turn that a closed window started still holds back the next request of the session in a window opened later.
 *
 * @since 2.1.3
 */
final class BrainstormQueue {

    /** The latest work of each session, the next work of the session waits for it. */
    private final Map<String, CompletableFuture<?>> tails = new ConcurrentHashMap<>();
    private final Set<String> deleted = ConcurrentHashMap.newKeySet();

    /**
     * Runs work of a session after the work of the session queued before it.
     *
     * @param sessionId the session id
     * @param work      the work
     * @param executor  runs the work
     * @return the result of the work
     */
    <T> CompletableFuture<T> enqueue(String sessionId, Supplier<T> work, Executor executor) {
        CompletableFuture<T> result;
        synchronized (tails) {
            CompletableFuture<?> previous = tails.getOrDefault(sessionId, CompletableFuture.completedFuture(null));
            result = previous.handle((Object ignored, Throwable error) -> null)
                    .thenApplyAsync((Object ignored) -> work.get(), executor);
            tails.put(sessionId, result);
        }
        result.whenComplete((T done, Throwable error) -> tails.remove(sessionId, result));
        return result;
    }

    /**
     * The work of a session queued so far.
     *
     * @param sessionId the session id
     * @return completes when that work is done, whether it failed or not; at once if there is none
     */
    CompletableFuture<Void> pending(String sessionId) {
        return tails.getOrDefault(sessionId, CompletableFuture.completedFuture(null))
                .handle((Object ignored, Throwable error) -> null);
    }

    /**
     * Marks a session as deleted and cancels its queued work; the work that already runs checks
     * {@link #isDeleted} before every further call or write.
     *
     * @param sessionId the session id
     */
    void delete(String sessionId) {
        deleted.add(sessionId);
        CompletableFuture<?> running = tails.get(sessionId);
        if (running != null)
            running.cancel(true);
    }

    boolean isDeleted(String sessionId) {
        return deleted.contains(sessionId);
    }
}
