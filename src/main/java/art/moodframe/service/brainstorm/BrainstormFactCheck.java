package art.moodframe.service.brainstorm;

import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.BrainstormFact;
import art.moodframe.model.BrainstormMessage;
import art.moodframe.model.ResearchNote;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the facts of a draft against the research of the session: a fact keeps a source only if the session
 * researched it, else it is marked as unverified.
 *
 * @since 2.1.3
 */
final class BrainstormFactCheck {

    private static final Logger logger = LoggerFactory.getLogger(BrainstormFactCheck.class);

    private BrainstormFactCheck() {
    }

    /**
     * Takes the source of every researched fact from the research of the session instead of what the AI wrote:
     * a fact whose domain matches a researched source (also a sub domain), or whose URL links a watched video, gets
     * that source's domain and URL, any other fact is marked as unverified. The researched sources are those of the
     * earlier research events and of the research of this turn.
     *
     * @param draft   the draft whose facts are checked
     * @param history the stored messages of the session
     * @param notes   the research of this turn
     */
    static void markUnverifiedFacts(BrainstormDraft draft, List<BrainstormMessage> history, List<ResearchNote> notes) {
        List<ResearchNote.Source> known = new ArrayList<>();
        for (BrainstormMessage message : history) {
            if (BrainstormMessage.ROLE_EVENT.equals(message.getRole())
                    && message.getContent().startsWith(BrainstormSessions.EVENT_PREFIX)) {
                try {
                    JSONObject event = new JSONObject(
                            message.getContent().substring(BrainstormSessions.EVENT_PREFIX.length()));
                    if (ResearchNote.EVENT.equals(event.optString("event")))
                        known.addAll(ResearchNote.fromEventJson(event).sources());
                }
                catch (JSONException e) {
                    logger.debug("Skipping unreadable event", e);
                }
            }
        }
        notes.forEach((ResearchNote note) -> known.addAll(note.sources()));

        for (BrainstormFact fact : draft.getFacts()) {
            if (fact.isFromUser()) {
                fact.setVerified(true);
                continue;
            }
            String domain = normalizeDomain(fact.getDomain());
            YouTubeClip video = YouTubeClip.of(fact.getUrl(), 0, 0);
            ResearchNote.Source match = known.stream()
                    .filter((ResearchNote.Source source) -> video != null ? sameVideo(video, source.url())
                            : !domain.isEmpty() && sameSite(normalizeDomain(source.domain()), domain))
                    .findFirst().orElse(null);
            if (match != null)
                fact.setSource(match.domain(), match.url());
            fact.setVerified(match != null);
        }
    }

    /** Whether a source URL links the same video, at any time. */
    private static boolean sameVideo(YouTubeClip video, String url) {
        YouTubeClip other = YouTubeClip.of(url, 0, 0);
        return other != null && other.videoId().equals(video.videoId());
    }

    /** Whether two domains are the same site, so that "en.wikipedia.org" matches "wikipedia.org". */
    private static boolean sameSite(String a, String b) {
        return a.equals(b) || a.endsWith("." + b) || b.endsWith("." + a);
    }

    private static String normalizeDomain(String domain) {
        String lower = domain.trim().toLowerCase();
        return lower.startsWith("www.") ? lower.substring(4) : lower;
    }
}
