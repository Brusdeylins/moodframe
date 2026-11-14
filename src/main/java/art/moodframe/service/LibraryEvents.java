package art.moodframe.service;

import art.moodframe.model.PromptBlock;
import javafx.application.Platform;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reports what changed in the library: the services report every successful write, no matter who asked for it, so
 * the main window shows the changes of other callers without a restart.
 *
 * <p>The listeners learn of a change on the JavaFX thread, later in its queue, also when the write happened on it:
 * a listener never runs in the middle of the write that caused it.</p>
 *
 * @since 2.1.0
 */
public class LibraryEvents {

    /** What changed. */
    public enum Kind {
        MOOD, FRAME, ATMOSPHERE, STYLE, LIGHT, CAMERA, COLOR, FAVORITE, CHAT, ENTRY;

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

    /** Learns of a change, on the JavaFX thread. */
    public interface Listener {

        /**
         * Something changed.
         *
         * @param kind what changed
         * @param id   the ID of the changed entry, null if several changed (for example a new order or an import)
         */
        void changed(Kind kind, String id);
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Adds a listener.
     *
     * @param listener the listener
     */
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a listener.
     *
     * @param listener the listener
     */
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * Reports a change to the listeners; without listeners nothing happens, so the services also run without JavaFX.
     *
     * @param kind what changed
     * @param id   the ID of the changed entry, null if several changed
     */
    public void changed(Kind kind, String id) {
        for (Listener listener : listeners)
            Platform.runLater(() -> listener.changed(kind, id));
    }
}
