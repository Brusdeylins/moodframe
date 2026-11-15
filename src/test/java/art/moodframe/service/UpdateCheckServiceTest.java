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
import java.util.Collections;
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
 * The update check reads the version of the update site, compares it as numbers and asks on every system in the
 * packaged release build, at most once per start; offline, errors and a slow site give no version and no hint.
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

    /*  a service with the given system properties in a release build or, without it, in a "DEV-Build"  */
    private UpdateCheckService service(Properties system, boolean productionBuild) {
        return new UpdateCheckService(config, system) {
            @Override
            boolean isProductionBuild() {
                return productionBuild;
            }
        };
    }

    private UpdateCheckService packaged(String os, String dir) {
        return service(system(os, dir, true), true);
    }

    private UpdateCheckService linux(String dir) {
        return packaged("Linux", dir);
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
    void asksOnEverySystemInThePackagedApp() throws Exception {
        UpdateCheckService tarball = linux("/home/user/moodframe-2.0.1");
        assertEquals(UpdateCheckService.Package.TARBALL, tarball.packageKind());
        assertEquals(Optional.of(new UpdateCheckService.Result("2.0.1", "2.1.0")), tarball.checkAtStart().get());

        UpdateCheckService deb = linux("/usr/lib/moodframe");
        assertEquals(UpdateCheckService.Package.DEB, deb.packageKind());
        assertEquals(UpdateCheckService.Package.DEB, linux("/opt/moodframe").packageKind());
        assertEquals("2.1.0", deb.checkAtStart().get().orElseThrow().available());

        /*  Conveyor updates macOS and Windows at the start, so a newer version there means its update failed  */
        assertEquals("2.1.0", packaged("Mac OS X", "/Applications/MoodFrame.app/Contents/app").checkAtStart().get()
                .orElseThrow().available());
        assertEquals("2.1.0", packaged("Windows 11", "C:\\Program Files\\MoodFrame").checkAtStart().get()
                .orElseThrow().available());
        assertEquals(Collections.nCopies(4, "/download/metadata.properties"), requests);
    }

    @Test
    void asksNeitherFromTheSourceNorInADevBuild() throws Exception {
        UpdateCheckService source = service(system("Linux", "/home/user/moodframe", false), true);
        assertFalse(source.isAvailable());
        assertEquals(Optional.empty(), source.checkAtStart().get());
        assertEquals(Optional.empty(), service(system("Mac OS X", "/Applications/MoodFrame.app/Contents/app", true),
                false).checkAtStart().get(), "DEV-Build");
        assertEquals(List.of(), requests, "no request");
    }

    /*  on Linux with a pacman which owns every path and notes the paths it was asked for  */
    private UpdateCheckService withPacman(String dir, List<String> asked) {
        return new UpdateCheckService(config, system("Linux", dir, true)) {
            @Override
            boolean ownedByPacman(String path) {
                asked.add(path);
                return true;
            }
        };
    }

    @Test
    void pacmanTellsTheAurPackage() {
        List<String> asked = new CopyOnWriteArrayList<>();
        assertEquals(UpdateCheckService.Package.AUR, withPacman("/opt/moodframe/lib/app", asked).packageKind());
        assertEquals(List.of("/opt/moodframe/lib/app"), asked);

        /*  The .tar.gz in the home directory needs no pacman; a path pacman does not own is no AUR package  */
        assertEquals(UpdateCheckService.Package.TARBALL, withPacman("/home/user/moodframe", asked).packageKind());
        assertEquals(1, asked.size(), "pacman not asked");
        assertFalse(linux("/opt/moodframe").ownedByPacman("/opt/moodframe-missing/lib/app"));
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
        assertEquals(Optional.empty(), packaged("Mac OS X", "/Applications/MoodFrame.app/Contents/app").checkAtStart()
                .get(), "on every system");
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
        assertEquals(Optional.empty(), updates.checkAtStart().get(), "no hint");
        status = 200;
        body = "<html>maintenance</html>";
        assertNull(updates.check().get().available(), "unreadable file");

        Properties offline = system("Linux", "/home/user/moodframe", true);
        try (ServerSocket free = new ServerSocket(0)) {
            offline.setProperty("app.repositoryUrl", "http://127.0.0.1:" + free.getLocalPort() + "/download");
        }
        assertNull(new UpdateCheckService(config, offline).check().get().available(), "offline");
        assertEquals(Optional.empty(), service(offline, true).checkAtStart().get(), "offline, no hint");
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
