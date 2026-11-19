package art.moodframe.service.brainstorm;

import art.moodframe.model.BrainstormSession;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The queue of every brainstorm session: the work of one session runs one after the other, the work of different
 * sessions in parallel. It belongs to the shared {@link BrainstormSessions}, so it spans all brainstorm windows: a
 * turn that a closed window started still holds back the next request of the session in a window opened later, and
 * the events of a session (the research a turn starts, the short name) reach the windows open when they happen, not
 * the window that sent the request.
 *
 * @since 2.1.3
 */
final class BrainstormQueue {

    /** The latest work of each session, the next work of the session waits for it. */
    private final Map<String, CompletableFuture<?>> tails = new ConcurrentHashMap<>();
    private final Set<String> deleted = ConcurrentHashMap.newKeySet();
    /*  the listeners of the open windows, kept only as long as their service is  */
    private final Set<Listener> listeners = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    /** Who learns of the events of the sessions: the service of an open brainstorm window. */
    interface Listener {

        /**
         * Tells what a turn is about to research, from a background thread.
         *
         * @param sessionId the session
         * @param status    the search question or the video URL
         */
        void toolStatus(String sessionId, String status);

        /**
         * Tells that a session got its short name, from a background thread.
         *
         * @param session the session with its new title
         */
        void titleChanged(BrainstormSession session);
    }

    /**
     * Adds a listener for the events of all sessions; it is held weakly, so a window that is gone without
     * {@link #ignore} is not kept.
     *
     * @param listener the listener
     */
    void listen(Listener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a listener, when its window closes.
     *
     * @param listener the listener
     */
    void ignore(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * Tells the listeners what a turn of a session is about to research.
     *
     * @param sessionId the session
     * @param status    the search question or the video URL
     */
    void toolStatus(String sessionId, String status) {
        listeners().forEach((Listener listener) -> listener.toolStatus(sessionId, status));
    }

    /**
     * Tells the listeners that a session got its short name.
     *
     * @param session the session
     */
    void titleChanged(BrainstormSession session) {
        listeners().forEach((Listener listener) -> listener.titleChanged(session));
    }

    private List<Listener> listeners() {
        synchronized (listeners) {
            return List.copyOf(listeners);
        }
    }

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
