package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks up the title and the length of a public YouTube video and loads its preview images, without an API key:
 * the title comes from the oEmbed interface of YouTube, the length from the watch page, as oEmbed has none. A found
 * title and length are kept while the app runs.
 *
 * @since 2.0.0
 */
public class YouTubeInfoService {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeInfoService.class);

    private static final String OEMBED_URL = "https://www.youtube.com/oembed?format=json&url=";
    private static final String WATCH_URL = "https://www.youtube.com/watch?v=";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; MoodFrame)";
    private static final int MAX_PAGE_BYTES = 2_000_000;
    private static final Pattern ISO_DURATION = Pattern.compile(
            "itemprop=\"duration\" content=\"PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?\"");
    private static final Pattern LENGTH_SECONDS = Pattern.compile("\"lengthSeconds\":\"(\\d+)\"");

    /**
     * What is known about a video.
     *
     * @param title the title, empty if unknown
     * @param durationSeconds the length, 0 if unknown
     */
    public record Info(String title, int durationSeconds) {
    }

    private final OkHttpClient httpClient;
    private final Map<String, CompletableFuture<Info>> infos = new ConcurrentHashMap<>();

    /**
     * Creates the service with the corporate certificates of the configuration.
     *
     * @param config the application configuration
     */
    public YouTubeInfoService(ApplicationConfig config) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS);
        OpenAiApiServiceImpl.configureSSLContext(builder, config);
        this.httpClient = builder.build();
    }

    /**
     * Looks up the title and the length of a video. A failed lookup gives empty values and is tried again next time.
     *
     * @param videoId the id of the video
     * @return the title and the length; never fails
     */
    public CompletableFuture<Info> info(String videoId) {
        CompletableFuture<Info> info = infos.computeIfAbsent(videoId,
                (String id) -> CompletableFuture.supplyAsync(() -> load(id)));
        info.thenAccept((Info found) -> {
            if (found.title().isEmpty())
                infos.remove(videoId, info);
        });
        return info;
    }

    private Info load(String videoId) {
        String title = "";
        try {
            JSONObject oembed = new JSONObject(get(OEMBED_URL
                    + URLEncoder.encode(WATCH_URL + videoId, StandardCharsets.UTF_8), Integer.MAX_VALUE));
            title = oembed.optString("title", "").trim();
        } catch (IOException | RuntimeException e) {
            logger.warn("No title for the YouTube video {}: {}", videoId, e.getMessage());
        }
        int duration = 0;
        try {
            duration = parseDuration(get(WATCH_URL + videoId, MAX_PAGE_BYTES));
        } catch (IOException | RuntimeException e) {
            logger.warn("No length for the YouTube video {}: {}", videoId, e.getMessage());
        }
        return new Info(title, duration);
    }

    /**
     * Loads a preview image.
     *
     * @param url the URL of the image
     * @return the bytes of the image; fails if it does not exist
     */
    public CompletableFuture<byte[]> image(String url) {
        return CompletableFuture.supplyAsync(() -> {
            Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null)
                    throw new IllegalStateException("HTTP " + response.code() + " for " + url);
                return response.body().bytes();
            } catch (IOException e) {
                throw new IllegalStateException("Could not load " + url + ": " + e.getMessage(), e);
            }
        });
    }

    private String get(String url, int maxBytes) throws IOException {
        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en").build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null)
                throw new IOException("HTTP " + response.code());
            return new String(response.body().byteStream().readNBytes(maxBytes), StandardCharsets.UTF_8);
        }
    }

    /**
     * Reads the length of a video from its watch page: the ISO duration of the page data ("PT3M34S"), else the
     * length in seconds of the player data.
     *
     * @param page the HTML of the watch page
     * @return the length in seconds, 0 if the page names none
     */
    static int parseDuration(String page) {
        Matcher iso = ISO_DURATION.matcher(page);
        if (iso.find()) {
            int seconds = 0;
            for (int group = 1; group <= 3; group++)
                seconds = seconds * 60 + (iso.group(group) != null ? Integer.parseInt(iso.group(group)) : 0);
            return seconds;
        }
        Matcher length = LENGTH_SECONDS.matcher(page);
        return length.find() ? Integer.parseInt(length.group(1)) : 0;
    }
}
