package art.moodframe.ui.controller.settings;

/**
 * A section of the settings dialog with its own FXML file and controller. The dialog hands it the services once,
 * then asks it to load, save, reset and relabel its values; the section knows its controls and keys.
 */
public interface SettingsSection {

    /**
     * Prepares the controls with the services, before any value is loaded.
     *
     * @param services the services of the dialog
     */
    void init(SettingsServices services);

    /** Shows the stored values, at the opening and after an import. */
    default void load() {
    }

    /** Shows the values after the settings were reset to their defaults. */
    default void reset() {
    }

    /** Saves the values that are taken on "Save", when the dialog closes. */
    default void apply() {
    }

    /** Sets the texts in the current language. */
    default void relabel() {
    }
}
