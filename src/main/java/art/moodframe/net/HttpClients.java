package art.moodframe.net;

import art.moodframe.config.ApplicationConfig;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Builds the HTTP clients of the app, for {@link java.net.http.HttpClient} as for {@link OkHttpClient}, with the
 * corporate network settings: the proxy of the settings with its bypass rules and credentials, and the trust store
 * with the corporate certificates. The caller adds the timeouts to the returned builder.
 *
 * <p>The proxy applies only when it is enabled and valid. Loopback hosts and the hosts of the system property
 * {@code http.nonProxyHosts} bypass it. The credentials belong to the built client, not to the JVM.</p>
 *
 * <p>A client obtained through {@link #rebuilding(Function)} is built again after {@link #settingsChanged()}, which
 * the settings call when the proxy or the certificates change.</p>
 *
 * @since 2.1.3
 */
public class HttpClients {

    private static final Logger logger = LoggerFactory.getLogger(HttpClients.class);

    /*  Bumped by every change of the settings, the built clients compare it with the one they were built for  */
    private static final AtomicLong generation = new AtomicLong();

    private static final String LOOPBACK_HOSTS = "localhost|127.*|[::1]|::1";

    private final CorporateProxyConfig proxyConfig;
    private final List<Pattern> nonProxyHosts;
    private long loadedGeneration = -1;
    private SSLContext sslContext;
    private X509TrustManager trustManager;

    /**
     * Creates the factory for the network settings of the configuration.
     *
     * @param config the application configuration
     */
    public HttpClients(ApplicationConfig config) {
        this(new CorporateProxyConfig(config), System.getProperty("http.nonProxyHosts"));
    }

    /**
     * Creates the factory for the given proxy configuration and bypass rules.
     *
     * @param proxyConfig the proxy configuration
     * @param nonProxyHosts the hosts which bypass the proxy, separated by "|", each with an optional "*" at the start
     *                      or end, as in {@code http.nonProxyHosts}; may be null
     */
    HttpClients(CorporateProxyConfig proxyConfig, String nonProxyHosts) {
        this.proxyConfig = proxyConfig;
        this.nonProxyHosts = parseHostPatterns(LOOPBACK_HOSTS + (nonProxyHosts != null ? "|" + nonProxyHosts : ""));
    }

    /**
     * Tells all factories that the proxy or certificate settings changed: they read them again, and the clients from
     * {@link #rebuilding(Function)} are built again on their next use.
     */
    public static void settingsChanged() {
        generation.incrementAndGet();
    }

    /**
     * Gets the proxy configuration, as currently read from the settings.
     *
     * @return the proxy configuration
     */
    public synchronized CorporateProxyConfig getProxyConfig() {
        load();
        return proxyConfig;
    }

    /**
     * Creates a builder for an OkHttp client with the proxy and the trust store applied.
     *
     * @return the builder, for the timeouts of the caller
     */
    public synchronized OkHttpClient.Builder okHttp() {
        load();
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        ProxySelector selector = proxySelector();
        if (selector != null) {
            builder.proxySelector(selector);
            String username = proxyConfig.getProxyUsername();
            String password = proxyConfig.getProxyPassword();
            if (proxyConfig.hasProxyAuthentication() && username != null && password != null)
                builder.proxyAuthenticator((route, response) -> response.request().header("Proxy-Authorization") != null
                        ? null
                        : response.request().newBuilder()
                                .header("Proxy-Authorization", Credentials.basic(username, password)).build());
        }
        if (sslContext != null)
            builder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);
        return builder;
    }

    /**
     * Creates a builder for a java.net.http client with the proxy and the trust store applied.
     *
     * @return the builder, for the timeouts of the caller
     */
    public synchronized HttpClient.Builder javaHttp() {
        load();
        HttpClient.Builder builder = HttpClient.newBuilder();
        ProxySelector selector = proxySelector();
        if (selector != null) {
            builder.proxy(selector);
            String username = proxyConfig.getProxyUsername();
            String password = proxyConfig.getProxyPassword();
            if (proxyConfig.hasProxyAuthentication() && username != null && password != null)
                builder.authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return getRequestorType() == RequestorType.PROXY
                                ? new PasswordAuthentication(username, password.toCharArray())
                                : null;
                    }
                });
        }
        if (sslContext != null)
            builder.sslContext(sslContext);
        return builder;
    }

    /**
     * Gives a client which is built on first use and built again after {@link #settingsChanged()}.
     *
     * @param build builds the client from this factory, e.g. {@code clients -> clients.okHttp().build()}
     * @param <T> the type of the client
     * @return the supplier of the current client
     */
    public <T> Supplier<T> rebuilding(Function<HttpClients, T> build) {
        return new Supplier<>() {
            private T client;
            private long builtGeneration;

            @Override
            public synchronized T get() {
                long current = generation.get();
                if (client == null || builtGeneration != current) {
                    client = build.apply(HttpClients.this);
                    builtGeneration = current;
                }
                return client;
            }
        };
    }

    /*  Reads the settings and the certificates again if they changed since the last time  */
    private void load() {
        long current = generation.get();
        if (loadedGeneration == current)
            return;
        if (loadedGeneration >= 0)
            proxyConfig.loadProxyConfiguration();
        loadedGeneration = current;
        sslContext = null;
        trustManager = null;

        List<Path> certificatePaths = proxyConfig.getCertificatePaths();
        if (certificatePaths.isEmpty())
            return;
        try {
            TrustStoreManager trustStoreManager = new TrustStoreManager();
            trustStoreManager.createCorporateKeyStore(certificatePaths);
            SSLContext context = trustStoreManager.createSSLContext();
            X509TrustManager manager = trustStoreManager.getTrustManager();
            if (manager != null) {
                sslContext = context;
                trustManager = manager;
                logger.info("SSL context configured with {} corporate certificate(s)", certificatePaths.size());
            }
            else
                logger.warn("No X509TrustManager for the corporate certificates, using the default SSL context");
        }
        catch (RuntimeException e) {
            logger.error("Failed to configure SSL context with corporate certificates, using the default", e);
        }
    }

    /*  The selector of the corporate proxy, null if no proxy is enabled: then the JVM default applies  */
    private ProxySelector proxySelector() {
        if (!proxyConfig.isProxyEnabled() || !proxyConfig.isProxyConfigurationValid())
            return null;
        return new CorporateProxySelector(proxyConfig.getProxyHost(), proxyConfig.getProxyPort(), nonProxyHosts);
    }

    /*  "*.corp.example|localhost" as case-insensitive patterns  */
    private static List<Pattern> parseHostPatterns(String hosts) {
        List<Pattern> patterns = new ArrayList<>();
        for (String host : hosts.split("\\|")) {
            String trimmed = host.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty())
                continue;
            patterns.add(Pattern.compile(Arrays.stream(trimmed.split("\\*", -1))
                    .map(Pattern::quote)
                    .collect(Collectors.joining(".*"))));
        }
        return patterns;
    }

    /**
     * Routes through the corporate proxy, except for the hosts which bypass it.
     */
    private static final class CorporateProxySelector extends ProxySelector {

        private final String host;
        private final int port;
        private final List<Pattern> nonProxyHosts;

        CorporateProxySelector(String host, int port, List<Pattern> nonProxyHosts) {
            this.host = host;
            this.port = port;
            this.nonProxyHosts = nonProxyHosts;
        }

        @Override
        public List<Proxy> select(URI uri) {
            String target = uri.getHost();
            if (target == null || isBypassed(target.toLowerCase(Locale.ROOT)))
                return List.of(Proxy.NO_PROXY);
            return List.of(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)));
        }

        private boolean isBypassed(String target) {
            for (Pattern pattern : nonProxyHosts)
                if (pattern.matcher(target).matches())
                    return true;
            return false;
        }

        @Override
        public void connectFailed(URI uri, SocketAddress address, IOException e) {
            logger.error("Corporate proxy connection failed for {}: {}", uri, e.getMessage());
        }
    }
}
