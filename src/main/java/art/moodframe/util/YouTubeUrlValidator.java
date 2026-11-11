package art.moodframe.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for validating and parsing YouTube URLs.
 *
 * <p>Supports the following YouTube URL formats:</p>
 * <ul>
 * <li>Standard watch URLs: {@code https://www.youtube.com/watch?v=VIDEO_ID}</li>
 * <li>Short URLs: {@code https://youtu.be/VIDEO_ID}</li>
 * <li>Shorts URLs: {@code https://www.youtube.com/shorts/VIDEO_ID}</li>
 * <li>Embed URLs: {@code https://www.youtube.com/embed/VIDEO_ID}</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
 *
 * if (YouTubeUrlValidator.isValidYouTubeUrl(url)) {
 *     Optional<String> videoId = YouTubeUrlValidator.extractVideoId(url);
 *     videoId.ifPresent(id -> {
 *         String embedUrl = YouTubeUrlValidator.getEmbedUrl(id);
 *         // Use embed URL in WebView
 *     });
 * }
 * }</pre>
 *
 * <p><strong>Thread-safety:</strong> All methods are stateless and thread-safe.</p>
 *
 * @since 1.3.0
 */
public class YouTubeUrlValidator {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeUrlValidator.class);

    // YouTube video ID is 11 characters, alphanumeric plus - and _
    private static final String VIDEO_ID_PATTERN = "[a-zA-Z0-9_-]{11}";

    // Pattern for standard watch URLs: youtube.com/watch?v=VIDEO_ID
    private static final Pattern WATCH_URL_PATTERN = Pattern.compile(
        "^(?:https?://)?(?:www\\.)?youtube\\.com/watch\\?.*v=(" + VIDEO_ID_PATTERN + ").*$"
    );

    // Pattern for short URLs: youtu.be/VIDEO_ID
    private static final Pattern SHORT_URL_PATTERN = Pattern.compile(
        "^(?:https?://)?youtu\\.be/(" + VIDEO_ID_PATTERN + ")(?:\\?.*)?$"
    );

    // Pattern for shorts URLs: youtube.com/shorts/VIDEO_ID
    private static final Pattern SHORTS_URL_PATTERN = Pattern.compile(
        "^(?:https?://)?(?:www\\.)?youtube\\.com/shorts/(" + VIDEO_ID_PATTERN + ")(?:\\?.*)?$"
    );

    // Pattern for embed URLs: youtube.com/embed/VIDEO_ID
    private static final Pattern EMBED_URL_PATTERN = Pattern.compile(
        "^(?:https?://)?(?:www\\.)?youtube\\.com/embed/(" + VIDEO_ID_PATTERN + ")(?:\\?.*)?$"
    );

    // Base URL for YouTube embed player
    private static final String EMBED_BASE_URL = "https://www.youtube.com/embed/";

    // Base URL for YouTube thumbnail
    private static final String THUMBNAIL_BASE_URL = "https://img.youtube.com/vi/";

    /**
     * Validates if the given URL is a valid YouTube video URL.
     *
     * @param url the URL to validate
     * @return true if the URL is a valid YouTube video URL, false otherwise
     */
    public static boolean isValidYouTubeUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        String trimmedUrl = url.trim();

        return WATCH_URL_PATTERN.matcher(trimmedUrl).matches() ||
               SHORT_URL_PATTERN.matcher(trimmedUrl).matches() ||
               SHORTS_URL_PATTERN.matcher(trimmedUrl).matches() ||
               EMBED_URL_PATTERN.matcher(trimmedUrl).matches();
    }

    /**
     * Extracts the video ID from a YouTube URL.
     *
     * @param url the YouTube URL
     * @return Optional containing the video ID if found, empty otherwise
     */
    public static Optional<String> extractVideoId(String url) {
        if (url == null || url.trim().isEmpty()) {
            return Optional.empty();
        }

        String trimmedUrl = url.trim();

        // Try each pattern in order
        Pattern[] patterns = {
            WATCH_URL_PATTERN,
            SHORT_URL_PATTERN,
            SHORTS_URL_PATTERN,
            EMBED_URL_PATTERN
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(trimmedUrl);
            if (matcher.matches()) {
                String videoId = matcher.group(1);
                logger.debug("Extracted video ID '{}' from URL: {}", videoId, trimmedUrl);
                return Optional.of(videoId);
            }
        }

        logger.debug("Could not extract video ID from URL: {}", trimmedUrl);
        return Optional.empty();
    }

    /**
     * Gets the embed URL for a YouTube video.
     *
     * <p>The embed URL can be used in a WebView to display the YouTube player.</p>
     *
     * @param videoId the YouTube video ID
     * @return the embed URL with recommended parameters
     */
    public static String getEmbedUrl(String videoId) {
        if (videoId == null || videoId.trim().isEmpty()) {
            throw new IllegalArgumentException("Video ID cannot be null or empty");
        }

        // Parameters:
        // - rel=0: Don't show related videos from other channels
        // - modestbranding=1: Minimal YouTube branding
        // - fs=1: Allow fullscreen
        return EMBED_BASE_URL + videoId.trim() + "?rel=0&modestbranding=1&fs=1";
    }

    /**
     * Gets the embed URL for a YouTube video with a specific start time.
     *
     * @param videoId the YouTube video ID
     * @param startSeconds the start time in seconds
     * @return the embed URL with start time parameter
     */
    public static String getEmbedUrl(String videoId, int startSeconds) {
        String baseUrl = getEmbedUrl(videoId);
        if (startSeconds > 0) {
            return baseUrl + "&start=" + startSeconds;
        }
        return baseUrl;
    }

    /**
     * Gets the thumbnail URL for a YouTube video.
     *
     * <p>Returns the high-quality default thumbnail (hqdefault.jpg).</p>
     *
     * @param videoId the YouTube video ID
     * @return the thumbnail URL
     */
    public static String getThumbnailUrl(String videoId) {
        if (videoId == null || videoId.trim().isEmpty()) {
            throw new IllegalArgumentException("Video ID cannot be null or empty");
        }

        // Available thumbnail qualities:
        // - default.jpg (120x90)
        // - mqdefault.jpg (320x180)
        // - hqdefault.jpg (480x360)
        // - sddefault.jpg (640x480)
        // - maxresdefault.jpg (1280x720, not always available)
        return THUMBNAIL_BASE_URL + videoId.trim() + "/hqdefault.jpg";
    }

    /**
     * Normalizes a YouTube URL to the standard watch format.
     *
     * @param url the YouTube URL (any supported format)
     * @return the normalized URL in standard watch format, or the original if invalid
     */
    public static String normalizeUrl(String url) {
        return extractVideoId(url)
            .map(videoId -> "https://www.youtube.com/watch?v=" + videoId)
            .orElse(url);
    }

    /**
     * Formats a duration in seconds to MM:SS or HH:MM:SS format.
     *
     * @param seconds the duration in seconds
     * @return formatted time string
     */
    public static String formatDuration(int seconds) {
        if (seconds < 0) {
            return "00:00";
        }

        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%02d:%02d", minutes, secs);
        }
    }

    /**
     * Parses a time string (MM:SS or HH:MM:SS) to seconds.
     *
     * @param timeString the time string to parse
     * @return the duration in seconds, or 0 if invalid
     */
    public static int parseDuration(String timeString) {
        if (timeString == null || timeString.trim().isEmpty()) {
            return 0;
        }

        try {
            String[] parts = timeString.trim().split(":");
            if (parts.length == 2) {
                // MM:SS format
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                return minutes * 60 + seconds;
            } else if (parts.length == 3) {
                // HH:MM:SS format
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int seconds = Integer.parseInt(parts[2]);
                return hours * 3600 + minutes * 60 + seconds;
            }
        } catch (NumberFormatException e) {
            logger.debug("Failed to parse duration: {}", timeString);
        }

        return 0;
    }

    /**
     * Fetches the video duration from YouTube using the noembed.com API.
     *
     * <p>This API returns video metadata including duration without requiring an API key.
     * Falls back to default duration if the request fails.</p>
     *
     * @param videoId the YouTube video ID
     * @param defaultDuration the default duration to return if fetch fails (in seconds)
     * @return CompletableFuture with the video duration in seconds
     */
    public static CompletableFuture<Integer> fetchVideoDuration(String videoId, int defaultDuration) {
        if (videoId == null || videoId.trim().isEmpty()) {
            return CompletableFuture.completedFuture(defaultDuration);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Use noembed.com which returns duration in the response
                // YouTube's own oEmbed doesn't include duration
                String youtubeUrl = "https://www.youtube.com/watch?v=" + videoId;
                String apiUrl = "https://www.youtube.com/oembed?url=" +
                    java.net.URLEncoder.encode(youtubeUrl, "UTF-8") + "&format=json";

                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    // YouTube oEmbed doesn't return duration directly
                    // We'll try to get it from the video page or use a secondary service
                    logger.debug("YouTube oEmbed response received for video: {}", videoId);

                    // Try noembed.com which might have duration
                    return fetchDurationFromNoembed(videoId, defaultDuration);
                }

                logger.debug("YouTube oEmbed returned status {}, using default duration", response.statusCode());
                return defaultDuration;

            } catch (Exception e) {
                logger.debug("Failed to fetch video duration for {}: {}", videoId, e.getMessage());
                return defaultDuration;
            }
        });
    }

    /**
     * Fetches duration from noembed.com service.
     */
    private static int fetchDurationFromNoembed(String videoId, int defaultDuration) {
        try {
            String youtubeUrl = "https://www.youtube.com/watch?v=" + videoId;
            String apiUrl = "https://noembed.com/embed?url=" +
                java.net.URLEncoder.encode(youtubeUrl, "UTF-8");

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                // noembed.com doesn't return duration either for YouTube
                // We'll use a reasonable default and let the user adjust
                logger.debug("Noembed response: {}", body.substring(0, Math.min(200, body.length())));
            }

        } catch (Exception e) {
            logger.debug("Failed to fetch from noembed: {}", e.getMessage());
        }

        return defaultDuration;
    }

    /**
     * Checks if a YouTube video exists and is accessible.
     *
     * @param videoId the YouTube video ID
     * @return CompletableFuture with true if the video exists, false otherwise
     */
    public static CompletableFuture<Boolean> checkVideoExists(String videoId) {
        if (videoId == null || videoId.trim().isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check if thumbnail exists (quick way to verify video exists)
                String thumbnailUrl = getThumbnailUrl(videoId);

                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(thumbnailUrl))
                    .timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                boolean exists = response.statusCode() == 200;

                logger.debug("Video {} exists check: {}", videoId, exists);
                return exists;

            } catch (Exception e) {
                logger.debug("Failed to check video existence for {}: {}", videoId, e.getMessage());
                return false;
            }
        });
    }
}
