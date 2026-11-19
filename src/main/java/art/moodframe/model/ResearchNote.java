package art.moodframe.model;

import java.util.List;

/**
 * The result of one research tool call of the brainstorm AI: what was asked, what was found and where. Its app event,
 * stored with the session, is {@code service.brainstorm.BrainstormJson.researchEvent}.
 *
 * @param query what was researched (a search question, or the URL of an analyzed video)
 * @param result the found text
 * @param sources the pages the result is based on
 * @since 1.8.1
 */
public record ResearchNote(String query, String result, List<Source> sources) {

    /**
     * A source page.
     *
     * @param domain the domain shown to the user, for example "ai.google.dev"
     * @param url the URL that is opened on click
     */
    public record Source(String domain, String url) {
    }
}
