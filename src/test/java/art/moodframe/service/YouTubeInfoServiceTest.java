package art.moodframe.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YouTubeInfoServiceTest {

    @Test
    void readsTheLengthFromTheWatchPage() {
        assertEquals(214, YouTubeInfoService.parseDuration("<meta itemprop=\"duration\" content=\"PT3M34S\">"));
        assertEquals(3723, YouTubeInfoService.parseDuration("<meta itemprop=\"duration\" content=\"PT1H2M3S\">"));
        assertEquals(19, YouTubeInfoService.parseDuration("{\"videoDetails\":{\"lengthSeconds\":\"19\"}}"));
        assertEquals(0, YouTubeInfoService.parseDuration("<html>no video</html>"));
    }
}
