package art.moodframe.ui.support;

import art.moodframe.service.library.LibraryEvents;
import javafx.application.Platform;

/**
 * Registers views for the changes of the library: they learn of a change on the JavaFX thread, later in its queue,
 * also when the write happened there, so a listener never runs in the middle of the write that caused it.
 * {@link LibraryEvents} itself knows no JavaFX.
 *
 * @since 2.1.3
 */
public final class FxLibraryEvents {

    private FxLibraryEvents() {
    }

    /**
     * Adds a listener of a view, called on the JavaFX thread; it is removed with
     * {@link LibraryEvents#removeListener(LibraryEvents.Listener)}.
     *
     * @param events   the reports of the library
     * @param listener the listener to add
     */
    public static void addListener(LibraryEvents events, LibraryEvents.Listener listener) {
        events.addListener(listener, Platform::runLater);
    }
}
