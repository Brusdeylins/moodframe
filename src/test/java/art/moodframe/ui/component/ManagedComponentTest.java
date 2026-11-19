package art.moodframe.ui.component;

import art.moodframe.util.I18nChangeListener;
import art.moodframe.util.I18nManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A managed component lets go of the listener of the language it registered, whichever it was. */
class ManagedComponentTest {

    /*  A component that registers a listener of its own instead of itself  */
    private static final class Component extends ManagedComponent {

        final I18nChangeListener listener = () -> { };

        Component() {
            registerI18nListener(listener);
        }
    }

    @Test
    void disposeRemovesTheListenerThatWasRegistered() {
        int before = I18nManager.getChangeListenerCount();
        Component component = new Component();
        assertEquals(before + 1, I18nManager.getChangeListenerCount());

        component.dispose();
        assertEquals(before, I18nManager.getChangeListenerCount(), "the registered listener left");
        assertEquals(false, I18nManager.removeChangeListener(component.listener));
    }
}
