package art.moodframe.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A YouTube video linked in a chat message, with the time range the message gives for it. The links are found in the
 * text (watch, youtu.be, shorts and embed links), each with the first range written after it, the first link also
 * with a range written before it ("von 0:42 bis 1:05", "ab 2:10", "1:20–1:45", "from 0:42 to 1:05").
 *
 * @param videoId the 11 character id of the video
 * @param startSeconds the start of the range, 0 for the start of the video
 * @param endSeconds the end of the range, 0 for the end of the video
 * @since 2.0.0
 */
public record YouTubeClip(String videoId, int startSeconds, int endSeconds) {

    private static final String WATCH_URL = "https://www.youtube.com/watch?v=";
    private static final String THUMBNAIL_URL = "https://i.ytimg.com/vi/";
    private static final Pattern LINK = Pattern.compile("(?:https?://)?(?:www\\.|m\\.)?(?:youtube\\.com/"
            + "(?:watch\\?(?:\\S*?&)?v=|shorts/|embed/)|youtu\\.be/)([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])\\S*");
    private static final String TIME = "(?<![\\d:])(?:\\d{1,2}:)?\\d{1,2}:\\d{2}(?![\\d:])";
    private static final Pattern RANGE = Pattern.compile(
            "\\b(?:zwischen|between)\\s+(" + TIME + ")\\s+(?:und|and)\\s+(" + TIME + ")"
            + "|(?:\\b(?:von|from)\\s+)?(" + TIME + ")\\s*(?:-|–|—|\\bbis\\b|\\bto\\b|\\buntil\\b|\\btill\\b)\\s*("
            + TIME + ")"
            + "|\\b(?:ab|von|from|starting at|start at)\\s+(" + TIME + ")",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Finds the YouTube links of a text with their time ranges. A range whose end is not after its start is ignored.
     *
     * @param text the text of a message
     * @return the linked videos in the order of the text, each video and range once, empty for none
     */
    public static List<YouTubeClip> findAll(String text) {
        List<YouTubeClip> clips = new ArrayList<>();
        if (text == null)
            return clips;

        List<MatchResult> links = LINK.matcher(text).results().toList();
        for (int i = 0; i < links.size(); i++) {
            int next = i + 1 < links.size() ? links.get(i + 1).start() : text.length();
            int[] range = rangeIn(text, links.get(i).end(), next);
            if (range == null && i == 0)
                range = rangeIn(text, 0, links.get(i).start());
            YouTubeClip clip = range != null ? new YouTubeClip(links.get(i).group(1), range[0], range[1])
                    : new YouTubeClip(links.get(i).group(1), 0, 0);
            if (!clips.contains(clip))
                clips.add(clip);
        }
        return clips;
    }

    /** The first time range in a part of the text as start and end seconds (end 0 for "from"), null if none. */
    private static int[] rangeIn(String text, int from, int to) {
        Matcher matcher = RANGE.matcher(text).region(from, to);
        if (!matcher.find())
            return null;

        String start = matcher.group(1) != null ? matcher.group(1) : matcher.group(3) != null ? matcher.group(3)
                : matcher.group(5);
        String end = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
        int startSeconds = parseTime(start);
        int endSeconds = end != null ? parseTime(end) : 0;
        return end != null && endSeconds <= startSeconds ? null : new int[] { startSeconds, endSeconds };
    }

    /**
     * The video of a YouTube link with a time range.
     *
     * @param url a watch, youtu.be, shorts or embed link
     * @param startSeconds the start of the range, 0 for the start of the video
     * @param endSeconds the end of the range, 0 for the end of the video
     * @return the clip, null if the URL is no YouTube link
     */
    public static YouTubeClip of(String url, int startSeconds, int endSeconds) {
        Matcher matcher = url != null ? LINK.matcher(url.trim()) : null;
        return matcher != null && matcher.matches()
                ? new YouTubeClip(matcher.group(1), Math.max(0, startSeconds), Math.max(0, endSeconds)) : null;
    }

    /**
     * Removes the YouTube links of a text, for a message that is sent without its videos.
     *
     * @param text the text of a message
     * @return the text without links
     */
    public static String withoutLinks(String text) {
        return LINK.matcher(text).replaceAll("").replaceAll("[ \\t]{2,}", " ").trim();
    }

    /**
     * The same video with a range the user typed: "0:42–1:05", "ab 2:10", or a single time as start.
     *
     * @param text the range, empty for the whole video
     * @return the clip, null if the text is no range
     */
    public YouTubeClip withRange(String text) {
        if (text == null || text.isBlank())
            return new YouTubeClip(videoId, 0, 0);

        int[] range = rangeIn(text, 0, text.length());
        int start = parseTime(text);
        return range != null ? new YouTubeClip(videoId, range[0], range[1])
                : start >= 0 ? new YouTubeClip(videoId, start, 0) : null;
    }

    /** Whether only a part of the video is meant. */
    public boolean hasRange() {
        return startSeconds > 0 || endSeconds > 0;
    }

    /** The watch link of the video, at the start of the range. */
    public String watchUrl() {
        return watchUrlAt(startSeconds);
    }

    /**
     * The watch link of the video at a time.
     *
     * @param seconds the time in the video, 0 for the start
     * @return the link
     */
    public String watchUrlAt(int seconds) {
        return WATCH_URL + videoId + (seconds > 0 ? "&t=" + seconds + "s" : "");
    }

    /** The range as text, for example "0:42–1:05" or "from 2:10". */
    public String rangeLabel() {
        return endSeconds > 0 ? formatTime(startSeconds) + "–" + formatTime(endSeconds)
                : I18nManager.getText("brainstorm.video.from", formatTime(startSeconds));
    }

    /**
     * The preview image for the range. YouTube makes previews of its own only at about a quarter, a half and three
     * quarters of the length (hq1, hq2, hq3), not at any time: the one nearest to the middle of the range is taken.
     * Without a range or a known length it is the normal preview, which the uploader may have chosen.
     *
     * @param durationSeconds the length of the video, 0 if unknown
     * @return the URL of the preview image (480 x 360)
     */
    public String thumbnailUrl(int durationSeconds) {
        if (!hasRange() || durationSeconds <= 0)
            return defaultThumbnailUrl();

        int end = endSeconds > 0 ? Math.min(endSeconds, durationSeconds) : durationSeconds;
        double middle = (startSeconds + end) / 2.0 / durationSeconds;
        return THUMBNAIL_URL + videoId + "/hq" + (middle < 0.375 ? 1 : middle < 0.625 ? 2 : 3) + ".jpg";
    }

    /** The normal preview image of the video (480 x 360), which exists for every video. */
    public String defaultThumbnailUrl() {
        return THUMBNAIL_URL + videoId + "/hqdefault.jpg";
    }

    /**
     * Writes a time as "m:ss", or "h:mm:ss" from one hour on.
     *
     * @param seconds the time in seconds
     * @return the time as text
     */
    public static String formatTime(int seconds) {
        int hours = seconds / 3600;
        return hours > 0 ? String.format("%d:%02d:%02d", hours, seconds % 3600 / 60, seconds % 60)
                : String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * Reads a time written as "m:ss" or "h:mm:ss".
     *
     * @param text the time
     * @return the time in seconds, -1 if the text is no time
     */
    public static int parseTime(String text) {
        if (text == null || !text.trim().matches("(?:\\d{1,2}:)?\\d{1,2}:\\d{2}"))
            return -1;

        int seconds = 0;
        for (String part : text.trim().split(":"))
            seconds = seconds * 60 + Integer.parseInt(part);
        return seconds;
    }
}
