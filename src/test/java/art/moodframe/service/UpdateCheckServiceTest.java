package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The update check reads the version of the update site, compares it as numbers and asks only on Linux in the
 * packaged app, at most once per start; offline, errors and a slow site give no version.
 */
class UpdateCheckServiceTest {

    private static final String METADATA = """
            #Sat Nov 14 11:53:49 CET 2026
            app.long-fsname=moodframe
            app.revision=0
            app.version=%s
            windows.site-base-url=https\\://moodframe.art/download/
            """;

    private final ApplicationConfig config = mock(ApplicationConfig.class);
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private HttpServer server;
    private volatile int status = 200;
    private volatile String body = METADATA.formatted("2.1.0");
    private volatile long delayMillis = 0;

    @BeforeEach
    void start(@TempDir Path certificates) throws Exception {
        when(config.getCertificatePath()).thenReturn(certificates.toString());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", (exchange) -> {
            requests.add(exchange.getRequestURI().getPath());
            try {
                Thread.sleep(delayMillis);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            catch (Exception e) {
                exchange.close();
            }
        });
        server.setExecutor(executor);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    private Properties system(String os, String dir, boolean packaged) {
        Properties system = new Properties();
        system.setProperty("os.name", os);
        system.setProperty("app.dir", dir);
        system.setProperty("app.version", "2.0.1");
        if (packaged)
            system.setProperty("app.repositoryUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/download");
        return system;
    }

    private UpdateCheckService linux(String dir) {
        return new UpdateCheckService(config, system("Linux", dir, true));
    }

    @Test
    void readsTheVersionFromTheMetadata() {
        assertEquals("2.0.1", UpdateCheckService.parseVersion(METADATA.formatted("2.0.1")));
        assertEquals("2.10.0", UpdateCheckService.parseVersion("app.version = 2.10.0 \n"));
        assertNull(UpdateCheckService.parseVersion("app.revision=0\n"), "missing key");
        assertNull(UpdateCheckService.parseVersion("app.version=\n"), "empty value");
        assertNull(UpdateCheckService.parseVersion("<html>Not Found</html>"), "no properties file");
    }

    @Test
    void comparesVersionsAsNumbers() {
        assertEquals(0, UpdateCheckService.compareVersions("2.0.1", "2.0.1"));
        assertTrue(UpdateCheckService.compareVersions("2.0.0", "2.0.1") < 0, "older");
        assertTrue(UpdateCheckService.compareVersions("2.1.0", "2.0.1") > 0, "newer");
        assertTrue(UpdateCheckService.compareVersions("2.10.0", "2.9.0") > 0, "not as text");
        assertTrue(UpdateCheckService.compareVersions("3", "2.99.99") > 0);
        assertEquals(0, UpdateCheckService.compareVersions("2.1", "2.1.0"), "a missing part counts as 0");
        assertTrue(UpdateCheckService.compareVersions("2.0.1.1", "2.0.1") > 0);
        assertFalse(new UpdateCheckService.Result("2.0.1", null).isNewer(), "no version, nothing newer");
    }

    @Test
    void asksOnlyOnLinuxInThePackagedApp() throws Exception {
        UpdateCheckService tarball = linux("/home/user/moodframe-2.0.1");
        assertEquals(UpdateCheckService.Package.TARBALL, tarball.packageKind());
        assertEquals(Optional.of(new UpdateCheckService.Result("2.0.1", "2.1.0")), tarball.checkAtStart().get());

        UpdateCheckService deb = linux("/usr/lib/moodframe");
        assertEquals(UpdateCheckService.Package.DEB, deb.packageKind());
        assertEquals(UpdateCheckService.Package.DEB, linux("/opt/moodframe").packageKind());
        assertEquals("2.1.0", deb.checkAtStart().get().orElseThrow().available());
        assertEquals(List.of("/download/metadata.properties", "/download/metadata.properties"), requests);

        /*  Conveyor updates macOS and Windows itself, the source build has no update site  */
        assertEquals(Optional.empty(), new UpdateCheckService(config,
                system("Mac OS X", "/Applications/MoodFrame.app/Contents/app", true)).checkAtStart().get());
        assertEquals(Optional.empty(), new UpdateCheckService(config,
                system("Windows 11", "C:\\Program Files\\MoodFrame", true)).checkAtStart().get());
        UpdateCheckService source = new UpdateCheckService(config, system("Linux", "/home/user/moodframe", false));
        assertFalse(source.isAvailable());
        assertEquals(Optional.empty(), source.checkAtStart().get());
        assertEquals(2, requests.size(), "no further request");
    }

    @Test
    void asksOncePerStartAndShowsNoOlderOrEqualVersion() throws Exception {
        UpdateCheckService updates = linux("/home/user/moodframe");
        body = METADATA.formatted("2.0.1");
        assertEquals(Optional.empty(), updates.checkAtStart().get(), "same version");
        body = METADATA.formatted("2.1.0");
        assertEquals(Optional.empty(), updates.checkAtStart().get(), "only once per start");
        assertEquals(1, requests.size());

        body = METADATA.formatted("2.0.0");
        assertEquals(Optional.empty(), linux("/home/user/moodframe").checkAtStart().get(), "older version");
    }

    @Test
    void aHiddenVersionShowsNoHintALaterOneDoes() throws Exception {
        when(config.getUpdateDismissedVersion()).thenReturn("2.1.0");
        assertEquals(Optional.empty(), linux("/home/user/moodframe").checkAtStart().get());
        body = METADATA.formatted("2.1.1");
        assertEquals("2.1.1", linux("/home/user/moodframe").checkAtStart().get().orElseThrow().available());

        linux("/home/user/moodframe").dismiss("2.1.1");
        verify(config).setUpdateDismissedVersion("2.1.1");
    }

    @Test
    void errorsGiveNoVersion() throws Exception {
        UpdateCheckService updates = linux("/home/user/moodframe");
        status = 404;
        assertEquals(new UpdateCheckService.Result("2.0.1", null), updates.check().get());
        status = 500;
        assertNull(updates.check().get().available());
        status = 200;
        body = "<html>maintenance</html>";
        assertNull(updates.check().get().available(), "unreadable file");

        Properties offline = system("Linux", "/home/user/moodframe", true);
        try (ServerSocket free = new ServerSocket(0)) {
            offline.setProperty("app.repositoryUrl", "http://127.0.0.1:" + free.getLocalPort() + "/download");
        }
        assertNull(new UpdateCheckService(config, offline).check().get().available(), "offline");
    }

    @Test
    void aSlowSiteRunsIntoTheTimeLimit() throws Exception {
        delayMillis = 15_000;
        long start = System.nanoTime();
        assertNull(linux("/home/user/moodframe").check().get(20, TimeUnit.SECONDS).available());
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(11), "gives up after at most 10 s");
    }

    @Test
    void aTrailingSlashOfTheSiteIsIgnored() throws Exception {
        Properties system = system("Linux", "/home/user/moodframe", true);
        system.setProperty("app.repositoryUrl", system.getProperty("app.repositoryUrl") + "/");
        assertEquals("2.1.0", new UpdateCheckService(config, system).check().get().available());
        assertEquals(List.of("/download/metadata.properties"), requests);
    }
}
