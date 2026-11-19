package art.moodframe.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * A parameter a model takes besides prompt, target and reference images, e.g. the guidance of FLUX.2 [flex]: its
 * name, kind, limits, default and slider step. The one description the main window, the MCP schema, the MCP
 * validation and the provider share; see {@link ModelParameters} for the parameters of a model.
 *
 * @param name         the name, as the MCP tools take it, e.g. {@code guidance}
 * @param type         the kind of value
 * @param minimum      the smallest value of a number, null without limit or for other kinds
 * @param maximum      the largest value of a number, null without limit or for other kinds
 * @param defaultValue the value without a choice, null if there is none (the provider decides)
 * @param step         the step of a slider for a number (its ticks), null for other kinds
 * @param choices      the allowed values ({@link Type#CHOICE}, or an integer out of fixed values), empty for any
 * @param description  a short description for the MCP clients, null for none
 * @since 2.1.3
 */
public record ModelParameter(String name, Type type, Number minimum, Number maximum, Object defaultValue,
        Number step, List<?> choices, String description) {

    /** The kind of value of a parameter. */
    public enum Type {
        /** A whole number, e.g. the seed. */
        INTEGER,
        /** A decimal number, e.g. the guidance. */
        NUMBER,
        /** On or off. */
        BOOLEAN,
        /** A text out of {@link ModelParameter#choices()}. */
        CHOICE
    }

    /**
     * Creates the parameter.
     */
    public ModelParameter {
        choices = choices != null ? List.copyOf(choices) : List.of();
    }

    /**
     * Checks if a number is within the limits and among the choices of this parameter.
     *
     * @param value the value (may be null)
     * @return true if the parameter takes it; false for null
     */
    public boolean accepts(Number value) {
        if (value == null)
            return false;
        if (type == Type.INTEGER && value.doubleValue() != Math.rint(value.doubleValue()))
            return false;
        if (minimum != null && value.doubleValue() < minimum.doubleValue())
            return false;
        if (maximum != null && value.doubleValue() > maximum.doubleValue())
            return false;
        return choices.isEmpty() || choices.stream().anyMatch(
                (Object choice) -> choice instanceof Number number && number.doubleValue() == value.doubleValue());
    }

    /**
     * Gets the limits of a number for a message, e.g. {@code from 1.5 to 10}.
     *
     * @return the limits without trailing zeros
     */
    public String range() {
        return "from " + plain(minimum) + " to " + plain(maximum);
    }

    private static String plain(Number number) {
        return number == null ? "?" : new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
    }
}
