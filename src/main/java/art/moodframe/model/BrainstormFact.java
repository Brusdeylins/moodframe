package art.moodframe.model;

/**
 * A fact of the brainstorm draft: a statement with its source. Facts the user stated have no source, a fact from a
 * video names the time in the video.
 *
 * @since 1.8.1
 */
public class BrainstormFact {

    public static final String ORIGIN_SOURCE = "source";
    public static final String ORIGIN_USER = "user";

    private final String text;
    private String domain;
    private String url;
    private final String origin;
    private boolean confirmed;
    private boolean verified = true;
    private String time = "";

    /**
     * Creates a fact.
     *
     * @param text the statement
     * @param domain the domain of the source, empty for a fact of the user
     * @param url the URL of the source, empty for a fact of the user
     * @param origin {@link #ORIGIN_SOURCE} or {@link #ORIGIN_USER}
     * @param confirmed whether the user keeps the fact (unticked facts do not go into the prompt)
     */
    public BrainstormFact(String text, String domain, String url, String origin, boolean confirmed) {
        this.text = text;
        this.domain = domain;
        this.url = url;
        this.origin = ORIGIN_USER.equals(origin) ? ORIGIN_USER : ORIGIN_SOURCE;
        this.confirmed = confirmed;
    }

    public String getText() {
        return text;
    }

    public String getDomain() {
        return domain;
    }

    public String getUrl() {
        return url;
    }

    /** The time in the video ("m:ss") of a fact from a video, else empty. */
    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    /** Sets the source as the app knows it from the research, instead of what the AI wrote. */
    public void setSource(String domain, String url) {
        this.domain = domain;
        this.url = url;
    }

    public boolean isFromUser() {
        return ORIGIN_USER.equals(origin);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    /** Whether the source of a researched fact is among the sources that were really researched. */
    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }
}
