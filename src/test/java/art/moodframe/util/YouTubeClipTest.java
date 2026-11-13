package art.moodframe.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouTubeClipTest {

    private static final String ID = "dQw4w9WgXcQ";
    private static final String OTHER = "aqz-KE-bpKQ";

    @Test
    void findsWatchShortLinkAndShortsLinks() {
        assertEquals(List.of(new YouTubeClip(ID, 0, 0)),
                YouTubeClip.findAll("Look at https://www.youtube.com/watch?v=" + ID + " please"));
        assertEquals(List.of(new YouTubeClip(ID, 0, 0)), YouTubeClip.findAll("youtu.be/" + ID + "?si=abc"));
        assertEquals(List.of(new YouTubeClip(ID, 0, 0)),
                YouTubeClip.findAll("https://m.youtube.com/shorts/" + ID + "."));
        assertEquals(List.of(new YouTubeClip(ID, 0, 0)),
                YouTubeClip.findAll("https://www.youtube.com/watch?feature=share&v=" + ID + "&t=5s"));
    }

    @Test
    void findsNothingWithoutYouTubeLink() {
        assertTrue(YouTubeClip.findAll(null).isEmpty());
        assertTrue(YouTubeClip.findAll("").isEmpty());
        assertTrue(YouTubeClip.findAll("von 0:42 bis 1:05 auf https://vimeo.com/123456").isEmpty());
        assertTrue(YouTubeClip.findAll("https://www.youtube.com/@channel").isEmpty());
    }

    @Test
    void takesTheTimeRangeOfTheMessage() {
        String link = " https://youtu.be/" + ID;
        assertEquals(new YouTubeClip(ID, 42, 65), YouTubeClip.findAll(link + " von 0:42 bis 1:05").get(0));
        assertEquals(new YouTubeClip(ID, 130, 0), YouTubeClip.findAll(link + " ab 2:10").get(0));
        assertEquals(new YouTubeClip(ID, 80, 105), YouTubeClip.findAll(link + " 1:20–1:45").get(0));
        assertEquals(new YouTubeClip(ID, 80, 105), YouTubeClip.findAll(link + " (1:20-1:45)").get(0));
        assertEquals(new YouTubeClip(ID, 42, 65), YouTubeClip.findAll(link + " from 0:42 to 1:05").get(0));
        assertEquals(new YouTubeClip(ID, 60, 120), YouTubeClip.findAll(link + " zwischen 1:00 und 2:00").get(0));
        assertEquals(new YouTubeClip(ID, 3723, 3780), YouTubeClip.findAll(link + " 1:02:03–1:03:00").get(0));
    }

    @Test
    void takesARangeBeforeTheFirstLink() {
        assertEquals(List.of(new YouTubeClip(ID, 130, 0)),
                YouTubeClip.findAll("Was zeigt das Video ab 2:10? https://youtu.be/" + ID));
    }

    @Test
    void ignoresARangeThatEndsBeforeItStarts() {
        assertEquals(List.of(new YouTubeClip(ID, 0, 0)),
                YouTubeClip.findAll("https://youtu.be/" + ID + " 1:05–0:42"));
    }

    @Test
    void eachOfSeveralLinksTakesTheRangeWrittenAfterIt() {
        List<YouTubeClip> clips = YouTubeClip.findAll("Vergleiche https://youtu.be/" + ID + " ab 0:30 mit "
                + "https://www.youtube.com/watch?v=" + OTHER + " von 1:00 bis 1:30 und https://youtu.be/" + ID
                + " ab 0:30");

        assertEquals(List.of(new YouTubeClip(ID, 30, 0), new YouTubeClip(OTHER, 60, 90)), clips,
                "each video and range once, in the order of the text");
    }

    @Test
    void readsALinkWithARange() {
        assertEquals(new YouTubeClip(ID, 50, 0), YouTubeClip.of("https://www.youtube.com/watch?v=" + ID, 50, 0));
        assertEquals(new YouTubeClip(ID, 0, 0),
                YouTubeClip.of("https://www.youtube.com/watch?v=" + ID + "&t=50s", -3, 0));
        assertNull(YouTubeClip.of("https://example.com/watch?v=" + ID, 0, 0));
        assertNull(YouTubeClip.of(null, 0, 0));
    }

    @Test
    void changesTheRangeToWhatTheUserTyped() {
        YouTubeClip clip = new YouTubeClip(ID, 42, 65);

        assertEquals(new YouTubeClip(ID, 10, 20), clip.withRange("0:10–0:20"));
        assertEquals(new YouTubeClip(ID, 130, 0), clip.withRange("ab 2:10"));
        assertEquals(new YouTubeClip(ID, 130, 0), clip.withRange("2:10"));
        assertEquals(new YouTubeClip(ID, 0, 0), clip.withRange(" "));
        assertNull(clip.withRange("später"));
        assertNull(clip.withRange("0:20–0:10"));
    }

    @Test
    void removesTheLinksOfAMessage() {
        assertEquals("Was sagt er von 0:42 bis 1:05?",
                YouTubeClip.withoutLinks("Was sagt er https://youtu.be/" + ID + " von 0:42 bis 1:05?"));
        assertEquals("", YouTubeClip.withoutLinks("https://www.youtube.com/watch?v=" + ID));
    }

    @Test
    void linksTheVideoAtTheStartOfTheRange() {
        assertEquals("https://www.youtube.com/watch?v=" + ID + "&t=42s", new YouTubeClip(ID, 42, 65).watchUrl());
        assertEquals("https://www.youtube.com/watch?v=" + ID, new YouTubeClip(ID, 0, 65).watchUrl());
        assertEquals("0:42–1:05", new YouTubeClip(ID, 42, 65).rangeLabel());
        assertFalse(new YouTubeClip(ID, 0, 0).hasRange());
    }

    @Test
    void takesThePreviewNearestToTheMiddleOfTheRange() {
        String base = "https://i.ytimg.com/vi/" + ID + "/";

        assertEquals(base + "hqdefault.jpg", new YouTubeClip(ID, 0, 0).thumbnailUrl(400), "no range");
        assertEquals(base + "hqdefault.jpg", new YouTubeClip(ID, 100, 200).thumbnailUrl(0), "length unknown");
        assertEquals(base + "hq1.jpg", new YouTubeClip(ID, 0, 100).thumbnailUrl(400), "middle at 1/8");
        assertEquals(base + "hq2.jpg", new YouTubeClip(ID, 150, 250).thumbnailUrl(400), "middle at 1/2");
        assertEquals(base + "hq3.jpg", new YouTubeClip(ID, 300, 0).thumbnailUrl(400), "open end, middle at 7/8");
        assertEquals(base + "hq3.jpg", new YouTubeClip(ID, 300, 900).thumbnailUrl(400), "end after the length");
    }

    @Test
    void writesAndReadsTimes() {
        assertEquals("0:05", YouTubeClip.formatTime(5));
        assertEquals("2:10", YouTubeClip.formatTime(130));
        assertEquals("1:02:03", YouTubeClip.formatTime(3723));
        assertEquals(130, YouTubeClip.parseTime(" 2:10 "));
        assertEquals(3723, YouTubeClip.parseTime("1:02:03"));
        assertEquals(-1, YouTubeClip.parseTime(""));
        assertEquals(-1, YouTubeClip.parseTime(null));
        assertEquals(-1, YouTubeClip.parseTime("2.10"));
    }
}
