package art.moodframe.service.library;

import art.moodframe.model.PromptBlock;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * Reports what changed in the library: the services report every successful write, no matter who asked for it, so
 * the main window shows the changes of other callers without a restart.
 *
 * <p>Each listener learns of a change on the executor it was added with, which must run the call later, never in
 * the middle of the write that caused it. The user interface adds its listeners with {@code Platform::runLater}
 * (see {@code ui.support.FxLibraryEvents}), so they run on the JavaFX thread, later in its queue, also when the
 * write happened on it. The library itself knows no JavaFX.</p>
 *
 * @since 2.1.0
 */
public class LibraryEvents {

    /** What changed; {@link #DOWNLOAD} changes nothing, an image was copied into a folder for the status bar. */
    public enum Kind {
        MOOD, FRAME, ATMOSPHERE, STYLE, LIGHT, CAMERA, COLOR, FAVORITE, CHAT, ENTRY, DOWNLOAD, SAVED_IMAGE;

        /**
         * Tells the kind of a text building block.
         *
         * @param type the type of the block
         * @return its kind
         */
        public static Kind of(PromptBlock.Type type) {
            return valueOf(type.name());
        }
    }

    /** Learns of a change, on the executor it was added with. */
    public interface Listener {

        /**
         * Something changed.
         *
         * @param kind what changed
         * @param id   the ID of the changed entry, null if several changed (for example a new order or an import);
         *             the name of the copy for {@link Kind#DOWNLOAD}
         */
        void changed(Kind kind, String id);
    }

    /*  a listener with the executor that calls it  */
    private record Registration(Listener listener, Executor executor) {
    }

    private final List<Registration> listeners = new CopyOnWriteArrayList<>();

    /**
     * Adds a listener.
     *
     * @param listener the listener
     * @param executor calls the listener for each change, later than the write (e.g. {@code Platform::runLater})
     */
    public void addListener(Listener listener, Executor executor) {
        listeners.add(new Registration(listener, executor));
    }

    /**
     * Removes a listener.
     *
     * @param listener the listener
     */
    public void removeListener(Listener listener) {
        listeners.removeIf((Registration registration) -> registration.listener() == listener);
    }

    /**
     * Reports a change to the listeners, each on its executor; without listeners nothing happens.
     *
     * @param kind what changed
     * @param id   the ID of the changed entry, null if several changed
     */
    public void changed(Kind kind, String id) {
        for (Registration registration : listeners)
            registration.executor().execute(() -> registration.listener().changed(kind, id));
    }
}
