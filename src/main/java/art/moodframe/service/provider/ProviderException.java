package art.moodframe.service.provider;

import art.moodframe.util.I18nManager;

import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * A failure of a provider with a message for the user. A message from the i18n resources is in the language of the
 * user interface and can also be had in English, which the MCP clients get.
 *
 * @since 2.1.3
 */
public class ProviderException extends RuntimeException {

    private final String key;
    private final Object[] arguments;

    /**
     * Creates the exception with a fixed message, e.g. the text of the provider.
     *
     * @param message the message
     * @param cause   the cause (may be null)
     */
    public ProviderException(String message, Throwable cause) {
        super(message, cause);
        this.key = null;
        this.arguments = new Object[0];
    }

    /**
     * Creates the exception with a message from the i18n resources.
     *
     * @param cause     the cause (may be null)
     * @param key       the key of the message
     * @param arguments the arguments of the message
     */
    protected ProviderException(Throwable cause, String key, Object... arguments) {
        super(I18nManager.getText(key, arguments), cause);
        this.key = key;
        this.arguments = arguments.clone();
    }

    /**
     * Creates the exception with a message from the i18n resources.
     *
     * @param key       the key of the message
     * @param arguments the arguments of the message
     * @return the exception
     */
    public static ProviderException localized(String key, Object... arguments) {
        return new ProviderException(null, key, arguments);
    }

    /**
     * Creates the exception with a message from the i18n resources.
     *
     * @param cause     the cause
     * @param key       the key of the message
     * @param arguments the arguments of the message
     * @return the exception
     */
    public static ProviderException localized(Throwable cause, String key, Object... arguments) {
        return new ProviderException(cause, key, arguments);
    }

    /**
     * Gets the message in a language.
     *
     * @param locale the language
     * @return the message, a fixed one as it is
     */
    public String getMessage(Locale locale) {
        return key != null ? I18nManager.getTextIn(locale, key, arguments) : getMessage();
    }

    /**
     * Gets the message of a failure for the user: in English for the MCP clients, else in the language of the user
     * interface. A {@link CompletionException} or {@link ExecutionException} is unwrapped.
     *
     * @param failure the failure
     * @param english true for the English message
     * @return the message, that of the failure itself for one that is no {@code ProviderException}
     */
    public static String messageOf(Throwable failure, boolean english) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null)
            cause = cause.getCause();
        if (english && cause instanceof ProviderException provider)
            return provider.getMessage(Locale.ENGLISH);
        return cause.getMessage();
    }
}
