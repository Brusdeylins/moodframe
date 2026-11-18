package art.moodframe.ui.support;

import art.moodframe.util.I18nManager;

/**
 * The text of a BFL safety tolerance (0 the strictest, 6 the most permissive), the same in the generation panel and
 * on the chat entry.
 */
public final class SafetyTolerance {

    private SafetyTolerance() {
    }

    /**
     * The description of a tolerance in the current language.
     *
     * @param value the safety tolerance (0 to 6)
     * @return the description, "unknown" for another value
     */
    public static String description(int value) {
        return I18nManager.getText(switch (value) {
            case 0 -> "params.safety.most.strict";
            case 1 -> "params.safety.very.strict";
            case 2 -> "params.safety.strict";
            case 3 -> "params.safety.balanced";
            case 4 -> "params.safety.permissive";
            case 5 -> "params.safety.very.permissive";
            case 6 -> "params.safety.most.permissive";
            default -> "params.safety.unknown";
        });
    }

    /**
     * The label of a tolerance, e.g. "Safety: Balanced".
     *
     * @param value the safety tolerance (0 to 6)
     * @return the label in the current language
     */
    public static String label(int value) {
        return I18nManager.getText("params.safety", description(value));
    }
}
