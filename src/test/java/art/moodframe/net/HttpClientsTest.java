package art.moodframe.net;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ConfigMocks;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Both kinds of clients route through the enabled proxy with its credentials, except for the bypassed hosts, go
 * their default way while the proxy is disabled, and are built again after a change of the settings. Nothing here
 * connects: only the routes and credentials are asked for.
 */
class HttpClientsTest {

    private static final URI API = URI.create("https://api.openai.com/v1/models");

    private final ApplicationConfig config = ConfigMocks.config();

    @BeforeEach
    void setUp(@TempDir Path certificates) {
        when(config.proxy().getCertificatePath()).thenReturn(certificates.toString());
        when(config.proxy().getProxyHost()).thenReturn("10.0.0.1");
        when(config.proxy().getProxyPort()).thenReturn(8080);
        when(config.proxy().isProxyEnabled()).thenReturn(true);
    }

    private HttpClients clients(String nonProxyHosts) {
        return new HttpClients(new CorporateProxyConfig(config), nonProxyHosts);
    }

    private static boolean viaProxy(ProxySelector selector, String uri) {
        List<Proxy> proxies = selector.select(URI.create(uri));
        return proxies.size() == 1 && proxies.get(0).type() == Proxy.Type.HTTP
                && proxies.get(0).address().equals(new InetSocketAddress("10.0.0.1", 8080));
    }

    @Test
    void theEnabledProxyAppliesToBothKindsOfClients() {
        HttpClients clients = clients(null);

        assertTrue(viaProxy(clients.okHttp().build().proxySelector(), API.toString()));
        assertTrue(viaProxy(clients.javaHttp().build().proxy().orElseThrow(), API.toString()));
    }

    @Test
    void theDisabledProxyLeavesTheDefaultRoute() {
        when(config.proxy().isProxyEnabled()).thenReturn(false);
        HttpClients clients = clients(null);

        assertFalse(viaProxy(clients.okHttp().build().proxySelector(), API.toString()));
        HttpClient client = clients.javaHttp().build();
        assertTrue(client.proxy().isEmpty());
        assertTrue(client.authenticator().isEmpty());
    }

    @Test
    void loopbackAndTheNonProxyHostsBypassTheProxy() {
        ProxySelector selector = clients("*.corp.example|intranet").okHttp().build().proxySelector();

        assertEquals(List.of(Proxy.NO_PROXY), selector.select(URI.create("http://localhost:11434/v1")));
        assertEquals(List.of(Proxy.NO_PROXY), selector.select(URI.create("http://127.0.0.1:8080/")));
        assertEquals(List.of(Proxy.NO_PROXY), selector.select(URI.create("http://[::1]:8080/")));
        assertEquals(List.of(Proxy.NO_PROXY), selector.select(URI.create("https://git.corp.example/")));
        assertEquals(List.of(Proxy.NO_PROXY), selector.select(URI.create("https://INTRANET/")));
        assertTrue(viaProxy(selector, "https://corp.example.com/"));
        assertTrue(viaProxy(selector, "https://intranet.example/"));
    }

    @Test
    void theCredentialsAuthenticateAtTheProxyOfTheClientOnly() throws Exception {
        when(config.proxy().hasProxyCredentials()).thenReturn(true);
        when(config.proxy().getProxyUsername()).thenReturn("user");
        when(config.proxy().getProxyPassword()).thenReturn("secret");
        HttpClients clients = clients(null);

        OkHttpClient okHttp = clients.okHttp().build();
        Request request = new Request.Builder().url(API.toString()).build();
        Response challenge = new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(407)
                .message("Proxy Authentication Required").build();
        Request answer = okHttp.proxyAuthenticator().authenticate(null, challenge);
        assertEquals(Credentials.basic("user", "secret"), answer.header("Proxy-Authorization"));

        Authenticator authenticator = clients.javaHttp().build().authenticator().orElseThrow();
        PasswordAuthentication proxy = authenticator.requestPasswordAuthenticationInstance("10.0.0.1", null, 8080,
                "http", "realm", "Basic", API.toURL(), Authenticator.RequestorType.PROXY);
        assertEquals("user", proxy.getUserName());
        assertArrayEquals("secret".toCharArray(), proxy.getPassword());
        assertNull(authenticator.requestPasswordAuthenticationInstance("api.openai.com", null, 443,
                "https", "realm", "Basic", API.toURL(), Authenticator.RequestorType.SERVER));
    }

    @Test
    void aRebuildingClientFollowsAChangeOfTheSettings() {
        Supplier<HttpClient> client = clients(null).rebuilding(clients -> clients.javaHttp().build());
        HttpClient first = client.get();
        assertSame(first, client.get());
        assertTrue(first.proxy().isPresent());

        when(config.proxy().isProxyEnabled()).thenReturn(false);
        HttpClients.settingsChanged();

        HttpClient second = client.get();
        assertNotSame(first, second);
        assertTrue(second.proxy().isEmpty());
    }
}
