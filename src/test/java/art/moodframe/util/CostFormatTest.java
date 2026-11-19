package art.moodframe.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Amounts and the numbers in the texts follow the language of the user interface, not that of the system: a German
 * system shows "$0.07" in the English interface.
 */
class CostFormatTest {

    private Locale system;
    private Locale interfaceLocale;

    @BeforeEach
    void setUp() {
        system = Locale.getDefault();
        interfaceLocale = I18nManager.getCurrentLocale();
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(system);
        I18nManager.setLocale(interfaceLocale);
    }

    @Test
    void anAmountTakesTheDecimalSeparatorOfTheInterface() {
        Locale.setDefault(Locale.GERMANY);
        I18nManager.setLocale(Locale.ENGLISH);
        assertEquals("0.07", I18nManager.formatCost(0.07));
        assertEquals("1234.50", I18nManager.formatCost(1234.5));

        Locale.setDefault(Locale.US);
        I18nManager.setLocale(Locale.GERMAN);
        assertEquals("0,07", I18nManager.formatCost(0.07));
    }

    @Test
    void theNumbersInATextTakeTheFormatOfTheInterface() {
        Locale.setDefault(Locale.GERMANY);
        I18nManager.setLocale(Locale.ENGLISH);
        assertEquals("1,234 images · $0.07", I18nManager.getText("trash.context.chat.plural", 1234,
                I18nManager.formatCost(0.07)));
    }
}
