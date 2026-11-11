package art.moodframe.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A parsed AI reply of the brainstorm chat: the chat text, quick replies and the updated draft.
 *
 * @since 1.7.0
 */
public class BrainstormReply {

    /** The number of steps of the brainstorm process. */
    public static final int MAX_STEP = 8;

    private final int step;
    private final String message;
    private final List<String> quickReplies;
    private final BrainstormDraft draft;
    private List<ResearchNote> research = List.of();
    private List<Integer> skippedSteps = List.of();

    private BrainstormReply(int step, String message, List<String> quickReplies, BrainstormDraft draft) {
        this.step = step;
        this.message = message;
        this.quickReplies = quickReplies;
        this.draft = draft;
    }

    /**
     * Parses the raw text of an AI reply.
     *
     * <p>Tolerates a surrounding Markdown code fence or text around the JSON object.</p>
     *
     * @param raw the raw reply text
     * @return the parsed reply
     * @throws IllegalArgumentException if the text holds no valid reply JSON
     */
    public static BrainstormReply parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("empty reply");
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("reply holds no JSON object");
        }

        try {
            JSONObject json = new JSONObject(raw.substring(start, end + 1));
            String message = json.optString("message", "").trim();
            if (message.isEmpty()) {
                throw new IllegalArgumentException("reply has no message");
            }

            List<String> quickReplies = new ArrayList<>();
            JSONArray replies = json.optJSONArray("quick_replies");
            for (int i = 0; replies != null && i < replies.length() && quickReplies.size() < 4; i++) {
                String reply = replies.optString(i, "").trim();
                if (!reply.isEmpty()) {
                    quickReplies.add(reply);
                }
            }

            JSONObject draftJson = json.optJSONObject("draft");
            BrainstormDraft draft = draftJson != null ? BrainstormDraft.fromJson(draftJson) : new BrainstormDraft();
            if ("video".equals(json.optString("media_type"))) {
                draft.setMediaType("video");
            }
            BrainstormReply reply = new BrainstormReply(
                    Math.max(1, Math.min(MAX_STEP, json.optInt("step", 1))), message, quickReplies, draft);
            List<Integer> skipped = new ArrayList<>();
            JSONArray skippedArray = json.optJSONArray("skipped_steps");
            for (int i = 0; skippedArray != null && i < skippedArray.length(); i++) {
                int skippedStep = skippedArray.optInt(i, 0);
                if (skippedStep >= 1 && skippedStep <= MAX_STEP && !skipped.contains(skippedStep)) {
                    skipped.add(skippedStep);
                }
            }
            reply.skippedSteps = skipped;
            return reply;
        } catch (JSONException e) {
            throw new IllegalArgumentException("reply is not valid JSON", e);
        }
    }

    public int getStep() {
        return step;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getQuickReplies() {
        return quickReplies;
    }

    public BrainstormDraft getDraft() {
        return draft;
    }

    /** The steps before the current one that the AI skipped (they are shown struck through). */
    public List<Integer> getSkippedSteps() {
        return skippedSteps;
    }

    /** The research the AI did for this reply, in the order of the calls. */
    public List<ResearchNote> getResearch() {
        return research;
    }

    public void setResearch(List<ResearchNote> research) {
        this.research = research;
    }
}
