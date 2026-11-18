package art.moodframe.service.provider;

/**
 * An error answer of an AI API, with its HTTP status code, so a check of an account can tell a definite answer from
 * a transient failure.
 *
 * @since 2.1.2
 */
public class ApiHttpException extends ProviderException {

    private final int statusCode;

    /**
     * Creates the exception.
     *
     * @param statusCode the HTTP status code
     * @param message    the user-friendly message
     */
    public ApiHttpException(int statusCode, String message) {
        super(message, null);
        this.statusCode = statusCode;
    }

    /**
     * Creates the exception with a message from the i18n resources.
     *
     * @param statusCode the HTTP status code
     * @param key        the key of the message
     * @param arguments  the arguments of the message
     */
    protected ApiHttpException(int statusCode, String key, Object... arguments) {
        super(null, key, arguments);
        this.statusCode = statusCode;
    }

    /**
     * Creates the exception with a message from the i18n resources.
     *
     * @param statusCode the HTTP status code
     * @param key        the key of the message
     * @param arguments  the arguments of the message
     * @return the exception
     * @since 2.1.3
     */
    public static ApiHttpException localized(int statusCode, String key, Object... arguments) {
        return new ApiHttpException(statusCode, key, arguments);
    }

    /**
     * Gets the HTTP status code.
     *
     * @return the status code
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Checks if an HTTP status is a definite answer about an account: 400, 401, 403 or 404. Any other status (e.g.
     * 429 or 5xx) is transient and says nothing about the account.
     *
     * @param statusCode the HTTP status code
     * @return true for a definite answer
     */
    public static boolean isDefinite(int statusCode) {
        return statusCode == 400 || statusCode == 401 || statusCode == 403 || statusCode == 404;
    }

    /**
     * Checks if a failure is a definite answer about an account: an HTTP 400, 401, 403 or 404. Any other failure (no
     * network, timeout, HTTP 429 or 5xx, an unreadable answer) is transient.
     *
     * @param failure the failure
     * @return true for a definite answer
     */
    public static boolean isDefinite(Throwable failure) {
        return failure instanceof ApiHttpException http && isDefinite(http.statusCode);
    }
}
