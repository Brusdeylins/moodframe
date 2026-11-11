package tools.moodframe.service;

import tools.moodframe.config.ApplicationConfig;
import tools.moodframe.model.ResearchNote;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Research without Gemini: searches the web (DuckDuckGo HTML page, no key needed), reads the best pages and returns
 * their text with the sources. The language model of the brainstorm summarizes it. Only public http(s) pages are
 * read, never addresses of the local machine or network.
 *
 * @author Matthias Brusdeylins
 * @since 1.9.0
 */
public class WebResearchService {

    private static final Logger logger = LoggerFactory.getLogger(WebResearchService.class);

    private static final String SEARCH_URL = "https://html.duckduckgo.com/html/?q=";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; Moodframe research)";
    private static final int MAX_RESULTS = 4;
    private static final int MAX_PAGE_BYTES = 1_500_000;
    private static final int MAX_EXCERPT_LENGTH = 3000;
    private static final Pattern RESULT_LINK = Pattern.compile(
            "<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL);

    private final OkHttpClient httpClient;

    /**
     * Creates the service with the corporate certificates of the configuration.
     *
     * @param config the application configuration
     */
    public WebResearchService(ApplicationConfig config) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS);
        /*  Every hop, also a redirect, has to stay on a public address  */
        builder.addNetworkInterceptor((chain) -> {
            if (!isPublicHttpUrl(chain.request().url().toString()))
                throw new IOException("Blocked non-public address " + chain.request().url().host());
            return chain.proceed(chain.request());
        });
        OpenAiApiServiceImpl.configureSSLContext(builder, config);
        this.httpClient = builder.build();
    }

    /**
     * Searches the web and reads the best pages.
     *
     * @param query what to find out
     * @return the page excerpts with their sources
     */
    public CompletableFuture<ResearchNote> research(String query) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, String> hits = search(query);
            if (hits.isEmpty())
                throw new IllegalStateException("The web search found nothing for this query");

            List<CompletableFuture<String>> pages = new ArrayList<>();
            List<String> urls = new ArrayList<>(hits.keySet());
            for (String url : urls)
                pages.add(CompletableFuture.supplyAsync(() -> readPage(url)));

            StringBuilder result = new StringBuilder();
            List<ResearchNote.Source> sources = new ArrayList<>();
            for (int i = 0; i < urls.size(); i++) {
                String text = pages.get(i).join();
                if (text == null || text.isBlank())
                    continue;
                String url = urls.get(i);
                sources.add(new ResearchNote.Source(domainOf(url), url));
                result.append("SOURCE ").append(sources.size()).append(": ").append(hits.get(url)).append(" (")
                        .append(domainOf(url)).append(")\n").append(text).append("\n\n");
            }
            if (sources.isEmpty())
                throw new IllegalStateException("None of the found pages could be read");
            return new ResearchNote(query, result.toString().trim(), sources);
        });
    }

    /** Returns the result pages of the search: url to title, in the order of the search. */
    private Map<String, String> search(String query) {
        Request request = new Request.Builder()
                .url(SEARCH_URL + URLEncoder.encode(query, StandardCharsets.UTF_8))
                .header("User-Agent", USER_AGENT)
                .build();
        Map<String, String> hits = new LinkedHashMap<>();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null)
                throw new IllegalStateException("The web search failed (HTTP " + response.code() + ")");

            Matcher matcher = RESULT_LINK.matcher(response.body().string());
            while (matcher.find() && hits.size() < MAX_RESULTS) {
                String url = resolveResultUrl(matcher.group(1));
                if (url != null && isPublicHttpUrl(url) && !hits.containsKey(url))
                    hits.put(url, plainText(matcher.group(2)));
            }
        } catch (IOException e) {
            throw new IllegalStateException("The web search failed: " + e.getMessage(), e);
        }
        return hits;
    }

    /** The result links of the search page point to a redirect with the real address in {@code uddg}. */
    static String resolveResultUrl(String href) {
        String link = href.replace("&amp;", "&");
        int marker = link.indexOf("uddg=");
        if (marker >= 0) {
            String encoded = link.substring(marker + 5);
            int end = encoded.indexOf('&');
            return URLDecoder.decode(end >= 0 ? encoded.substring(0, end) : encoded, StandardCharsets.UTF_8);
        }
        return link.startsWith("//") ? "https:" + link : link;
    }

    /** Checks that the address is http(s) and its host is no loopback, private or link-local address. */
    static boolean isPublicHttpUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()) || uri.getHost() == null)
                return false;
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                        || address.isAnyLocalAddress() || address.isMulticastAddress())
                    return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String readPage(String url) {
        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();
        try (Response response = httpClient.newCall(request).execute()) {
            String type = response.header("Content-Type", "");
            if (!response.isSuccessful() || response.body() == null || !type.contains("text"))
                return null;

            byte[] bytes = response.body().byteStream().readNBytes(MAX_PAGE_BYTES);
            String text = plainText(new String(bytes, StandardCharsets.UTF_8)
                    .replaceAll("(?is)<(script|style|noscript|nav|header|footer|svg|form)[^>]*>.*?</\\1>", " "));
            return text.length() > MAX_EXCERPT_LENGTH ? text.substring(0, MAX_EXCERPT_LENGTH) : text;
        } catch (IOException | RuntimeException e) {
            logger.warn("Could not read {}: {}", url, e.getMessage());
            return null;
        }
    }

    /** Removes tags and the common entities, collapses the whitespace. */
    static String plainText(String html) {
        return html.replaceAll("(?s)<[^>]*>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#x27;", "'").replace("&#39;", "'")
                .replaceAll("\\s+", " ").trim();
    }

    private static String domainOf(String url) {
        String host = URI.create(url).getHost();
        return host != null && host.startsWith("www.") ? host.substring(4) : host;
    }
}
