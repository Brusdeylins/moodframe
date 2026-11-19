package art.moodframe.service.provider.bfl;

import art.moodframe.service.provider.ApiHttpException;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * An error answer of the BFL API, with the answer body for the details BFL sends, e.g. the safety tolerance the
 * account is approved for.
 *
 * @since 1.0.0
 */
public class BflApiException extends ApiHttpException {

    private final String responseBody;

    /**
     * Creates the exception.
     *
     * @param statusCode   the HTTP status code
     * @param message      the message
     * @param responseBody the answer body (may be null)
     */
    public BflApiException(int statusCode, String message, String responseBody) {
        super(statusCode, message);
        this.responseBody = responseBody;
    }

    /**
     * Gets the answer body.
     *
     * @return the body, null if there was none
     */
    public String getResponseBody() {
        return responseBody;
    }

    /**
     * The highest safety tolerance BFL approved for the account, from an HTTP 422 with the code
     * {@code safety_tolerance_not_approved}.
     *
     * @return the approved maximum, or null for any other error or without the value
     */
    public Integer getMaximumApprovedSafetyTolerance() {
        if (getStatusCode() != 422 || responseBody == null)
            return null;
        try {
            var detail = new ObjectMapper().readTree(responseBody).path("detail");
            var maximum = detail.path("maximum_approved_safety_tolerance");
            if ("safety_tolerance_not_approved".equals(detail.path("code").asText()) && maximum.isIntegralNumber())
                return maximum.asInt();
        } catch (Exception e) {
            /*  no JSON body: no approved maximum  */
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("BflApiException{statusCode=%d, message='%s'}", getStatusCode(), getMessage());
    }
}
