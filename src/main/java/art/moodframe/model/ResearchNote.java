package art.moodframe.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The result of one research tool call of the brainstorm AI: what was asked, what was found and where.
 *
 * @param query what was researched (a search question, or the URL of an analyzed video)
 * @param result the found text
 * @param sources the pages the result is based on
 * @since 1.8.1
 */
public record ResearchNote(String query, String result, List<Source> sources) {

    public static final String EVENT = "research_result";

    /**
     * A source page.
     *
     * @param domain the domain shown to the user, for example "ai.google.dev"
     * @param url the URL that is opened on click
     */
    public record Source(String domain, String url) {
    }

    /**
     * Returns the stored form of this note, sent to the AI as an app event in later turns.
     *
     * @param maxResultLength the result text is cut to this length
     * @return the event JSON
     */
    public JSONObject toEventJson(int maxResultLength) {
        JSONArray sourceArray = new JSONArray();
        sources.forEach((Source source) -> sourceArray.put(
                new JSONObject().put("domain", source.domain()).put("url", source.url())));
        return new JSONObject()
                .put("event", EVENT)
                .put("query", query)
                .put("result", result.length() > maxResultLength ? result.substring(0, maxResultLength) + "..." : result)
                .put("sources", sourceArray);
    }

    /**
     * Reads a note from its stored event form.
     *
     * @param event the event JSON written by {@link #toEventJson(int)}
     * @return the note
     */
    public static ResearchNote fromEventJson(JSONObject event) {
        List<Source> sources = new ArrayList<>();
        JSONArray array = event.optJSONArray("sources");
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject source = array.optJSONObject(i);
            if (source != null) {
                sources.add(new Source(source.optString("domain"), source.optString("url")));
            }
        }
        return new ResearchNote(event.optString("query"), event.optString("result"), sources);
    }
}
