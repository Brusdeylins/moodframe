package art.moodframe.ui.controller.settings;

import art.moodframe.util.I18nManager;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A change of the language relabels the status lines of the proxy section by what they show, in any language. */
class ProxyStatusTextTest {

    private static final String NONE = "settings.proxy.connection.none";
    private static final String SUCCESS = "settings.test.proxy.success";

    @Test
    void relabelsNoTestAndASuccessButKeepsAnotherText() {
        Locale before = I18nManager.getCurrentLocale();
        try {
            I18nManager.setLocale(Locale.ENGLISH);
            String germanSuccess = I18nManager.getTextIn(Locale.GERMAN, SUCCESS);
            String germanNone = I18nManager.getTextIn(Locale.GERMAN, NONE);

            assertEquals(I18nManager.getText(SUCCESS),
                    ProxySettingsPane.text(ProxySettingsPane.Shown.SUCCESS, germanSuccess, NONE, SUCCESS));
            assertEquals(I18nManager.getText(NONE),
                    ProxySettingsPane.text(ProxySettingsPane.Shown.NONE, germanNone, NONE, SUCCESS));
            assertEquals("Timeout (407)",
                    ProxySettingsPane.text(ProxySettingsPane.Shown.OTHER, "Timeout (407)", NONE, SUCCESS));
        }
        finally {
            I18nManager.setLocale(before);
        }
    }
}
