package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.net.HttpClients;
import art.moodframe.util.VersionManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Finds a newer version of MoodFrame on the update site. Conveyor updates the packages for macOS and Windows itself at
 * the start, but not those for Linux: the .tar.gz never, the .deb only through apt, the AUR package only through an
 * AUR helper like yay. So the app reads the version from the "metadata.properties" of the site at every start, as
 * Conveyor recommends, and points the user to the update -- on macOS and Windows only seen if Conveyor's update
 * failed; it installs nothing itself.
 *
 * <p>The system properties come from Conveyor: {@code app.repositoryUrl} (the update site, only in the packaged app),
 * {@code app.version} and {@code app.dir} (the install directory). A test can replace them.</p>
 *
 * @since 2.1.0
 */
public class UpdateCheckService {

    private static final Logger logger = LoggerFactory.getLogger(UpdateCheckService.class);

    /** Where the packages can be downloaded. */
    public static final String INSTALL_URL = "https://moodframe.art/#install";

    /** The command which updates the .deb. */
    public static final String APT_COMMAND = "sudo apt update && sudo apt upgrade";

    /** The command which updates the AUR package. */
    public static final String AUR_COMMAND = "yay -Syu";

    /** The command which updates the Arch package built from the repository, in packaging/aur/moodframe-bin. */
    public static final String MAKEPKG_COMMAND = "git pull && makepkg -si";

    /**
     * Whether moodframe-bin is in the AUR; until then, the update of the Arch package is built from the repository.
     * Set it to true with the first "packaging/aur/update.sh --push", once the AUR accepts new accounts again.
     */
    public static final boolean AUR_PUBLISHED = false;

    private static final int MAX_BYTES = 64 * 1024;

    /** How MoodFrame was installed on Linux. */
    public enum Package {
        /** The .deb, under /usr or /opt, updated by apt. */
        DEB,
        /** The Arch package moodframe-bin, from the AUR or built from the repository, under /opt, owned by pacman. */
        AUR,
        /** The .tar.gz, unpacked anywhere, replaced by hand. */
        TARBALL
    }

    /**
     * The outcome of a check.
     *
     * @param installed the running version
     * @param available the version on the update site, null if it could not be read
     */
    public record Result(String installed, String available) {

        /**
         * Checks if the update site has a newer version than the running one.
         *
         * @return true for a newer version
         */
        public boolean isNewer() {
            return available != null && compareVersions(available, installed) > 0;
        }
    }

    private final ApplicationConfig config;
    private final Properties system;
    private final Supplier<OkHttpClient> httpClient;
    private final AtomicBoolean checkedAtStart = new AtomicBoolean();

    /**
     * Creates the service for the running app.
     *
     * @param config the application configuration
     */
    public UpdateCheckService(ApplicationConfig config) {
        this(config, System.getProperties());
    }

    /**
     * Creates the service with the given system properties, e.g. for a simulated Linux package.
     *
     * @param config the application configuration
     * @param system the system properties
     */
    public UpdateCheckService(ApplicationConfig config, Properties system) {
        this.config = config;
        this.system = system;

        /*  The proxy and the own root certificates of the settings, as for the API calls  */
        this.httpClient = new HttpClients(config).rebuilding(clients -> clients.okHttp()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(10, TimeUnit.SECONDS)
                .build());
    }

    /**
     * Checks if the update site is known, i.e. the app runs as a package; started from the source, it is not.
     *
     * @return true if a check is possible
     */
    public boolean isAvailable() {
        return !system.getProperty("app.repositoryUrl", "").isBlank();
    }

    /**
     * Checks if the app is a release build; a "DEV-Build" has no version to compare.
     *
     * @return true for a release build
     */
    boolean isProductionBuild() {
        return VersionManager.isProductionBuild();
    }

    /**
     * Checks if the app runs on Linux, where Conveyor does not update it.
     *
     * @return true on Linux
     */
    public boolean isLinux() {
        return system.getProperty("os.name", "").startsWith("Linux");
    }

    /**
     * Tells how the app was installed on Linux: under /usr or /opt by the AUR package if pacman owns the directory,
     * else by the .deb; anywhere else from the .tar.gz. Asks pacman, so it is not meant for the UI thread.
     *
     * @return the package
     */
    public Package packageKind() {
        String dir = system.getProperty("app.dir", system.getProperty("java.home", ""));
        if (dir.startsWith("/usr/") || dir.startsWith("/opt/"))
            return ownedByPacman(dir) ? Package.AUR : Package.DEB;
        return Package.TARBALL;
    }

    /**
     * Asks pacman if a package owns a path; without pacman, as on Debian, it does not.
     *
     * @param path the path
     * @return true if owned by a package of pacman
     */
    boolean ownedByPacman(String path) {
        try {
            Process process = new ProcessBuilder("pacman", "-Qo", path)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        }
        catch (IOException e) {
            return false;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Gets the running version: the one of the package, else the one of the build.
     *
     * @return the version
     */
    public String installedVersion() {
        String version = system.getProperty("app.version");
        return version != null && !version.isBlank() ? version : VersionManager.getVersion();
    }

    /**
     * Reads the version of the update site in the background. Offline, an error or an unreadable file give no
     * version and a line in the log.
     *
     * @return the installed and the available version; never fails
     */
    public CompletableFuture<Result> check() {
        String installed = installedVersion();
        String url = system.getProperty("app.repositoryUrl", "").replaceAll("/+$", "") + "/metadata.properties";
        return CompletableFuture.supplyAsync(() -> {
            try (Response response = httpClient.get().newCall(new Request.Builder().url(url)
                    .header("User-Agent", "MoodFrame/" + installed).build()).execute()) {
                if (!response.isSuccessful() || response.body() == null)
                    throw new IOException("HTTP " + response.code());
                String available = parseVersion(
                        new String(response.body().byteStream().readNBytes(MAX_BYTES), StandardCharsets.UTF_8));
                if (available == null)
                    throw new IOException("no app.version");
                logger.info("Update check: installed {}, available {}", installed, available);
                return new Result(installed, available);
            }
            catch (IOException | RuntimeException e) {
                logger.warn("Update check of {} failed: {}", url, e.getMessage());
                return new Result(installed, null);
            }
        });
    }

    /**
     * Looks for a newer version once per start, on every system, in the packaged release build only; a version whose
     * hint the user hid does not count.
     *
     * @return the result if a newer version is to be shown, else empty; never fails
     */
    public CompletableFuture<Optional<Result>> checkAtStart() {
        if (!isAvailable() || !isProductionBuild() || !checkedAtStart.compareAndSet(false, true))
            return CompletableFuture.completedFuture(Optional.empty());
        return check().thenApply((Result result) -> result.isNewer() && !isDismissed(result.available())
                ? Optional.of(result) : Optional.empty());
    }

    /**
     * Checks if the user hid the hint for a version.
     *
     * @param version the version
     * @return true if hidden
     */
    public boolean isDismissed(String version) {
        return version.equals(config.getUpdateDismissedVersion());
    }

    /**
     * Hides the hint for a version; a later version shows it again.
     *
     * @param version the version
     */
    public void dismiss(String version) {
        config.setUpdateDismissedVersion(version);
    }

    /**
     * Reads the version from the "metadata.properties" of the update site.
     *
     * @param metadata the content of the file
     * @return the value of {@code app.version}, null if it is missing or empty
     */
    static String parseVersion(String metadata) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(metadata));
        }
        catch (IOException | IllegalArgumentException e) {
            return null;
        }
        String version = properties.getProperty("app.version", "").trim();
        return version.isEmpty() ? null : version;
    }

    /**
     * Compares two versions part by part as numbers, so 2.10.0 is newer than 2.9.0; a missing part counts as 0.
     *
     * @param a the one version
     * @param b the other version
     * @return a negative number, 0 or a positive number as a is older than, equal to or newer than b
     */
    public static int compareVersions(String a, String b) {
        String[] partsA = a.trim().split("\\.");
        String[] partsB = b.trim().split("\\.");
        for (int i = 0; i < Math.max(partsA.length, partsB.length); i++) {
            int diff = number(partsA, i).compareTo(number(partsB, i));
            if (diff != 0)
                return diff;
        }
        return 0;
    }

    /*  The leading digits of a part, 0 for a missing part or one without digits  */
    private static BigInteger number(String[] parts, int index) {
        String digits = index < parts.length ? parts[index].replaceFirst("^(\\d*).*$", "$1") : "";
        return digits.isEmpty() ? BigInteger.ZERO : new BigInteger(digits);
    }
}
