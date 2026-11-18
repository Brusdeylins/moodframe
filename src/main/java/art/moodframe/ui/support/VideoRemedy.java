package art.moodframe.ui.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Tells what the user can do where JavaFX Media cannot play videos under Linux: JavaFX 23 needs the ffmpeg library
 * libavcodec in a version from {@link #MIN_LIBAVCODEC} to {@link #MAX_LIBAVCODEC}, which newer distributions no
 * longer bring. The remedy follows from the distribution in {@code /etc/os-release} and the libavcodec found.
 *
 * @since 2.1.0
 */
public final class VideoRemedy {

    private static final Logger logger = LoggerFactory.getLogger(VideoRemedy.class);

    /** The oldest libavcodec JavaFX 23 can play videos with under Linux. */
    public static final int MIN_LIBAVCODEC = 54;

    /** The newest libavcodec JavaFX 23 can play videos with under Linux. */
    public static final int MAX_LIBAVCODEC = 61;

    static final Path OS_RELEASE = Path.of("/etc/os-release");
    static final List<Path> LIBRARY_DIRS = List.of(Path.of("/usr/lib"), Path.of("/usr/lib64"),
            Path.of("/usr/lib/x86_64-linux-gnu"), Path.of("/usr/lib/aarch64-linux-gnu"));

    /*  A libavcodec with its major version, like libavcodec.so.61 or libavcodec.so.61.19.100  */
    private static final Pattern LIBAVCODEC = Pattern.compile("libavcodec\\.so\\.(\\d{1,4})(\\..*)?");

    /** The case of a system without a libavcodec JavaFX can play videos with. */
    public enum Kind {

        /** Arch Linux and its derivatives: the package ffmpeg4.4 brings libavcodec 58. */
        ARCH("sudo pacman -S ffmpeg4.4"),

        /** The Debian family without any libavcodec: the package ffmpeg brings one, which may be too new. */
        DEBIAN_MISSING("sudo apt install ffmpeg"),

        /** The Debian family with a libavcodec newer than JavaFX can play videos with only. */
        DEBIAN_NEWER(null),

        /** Any other Linux. */
        OTHER(null);

        private final String command;

        Kind(String command) {
            this.command = command;
        }

        /**
         * Tells the command which brings a libavcodec JavaFX can play videos with.
         *
         * @return the command, or null if there is none
         */
        public String command() {
            return command;
        }
    }

    /**
     * What the user can do about videos JavaFX cannot play.
     *
     * @param kind the case of the system
     * @param version the major version of the newest libavcodec found, 0 if none
     */
    public record Remedy(Kind kind, int version) {
    }

    private static CompletableFuture<Remedy> detected;

    private VideoRemedy() {
    }

    /**
     * Detects the remedy of this system, once per session and in the background.
     *
     * @return the remedy, or null if there is none: not Linux, or a libavcodec JavaFX can play videos with is there
     */
    public static synchronized CompletableFuture<Remedy> detect() {
        if (detected == null)
            detected = CompletableFuture
                    .supplyAsync(() -> detect(System.getProperty("os.name", ""), OS_RELEASE, LIBRARY_DIRS))
                    .exceptionally((Throwable error) -> {
                        logger.warn("Cannot tell what helps to play videos on this system: {}", error.getMessage());
                        return null;
                    });
        return detected;
    }

    /**
     * Detects the remedy of the given system.
     *
     * @param osName the value of {@code os.name}
     * @param osRelease the os-release file of the distribution
     * @param libraryDirs the directories with the system libraries
     * @return the remedy, or null if there is none
     */
    static Remedy detect(String osName, Path osRelease, List<Path> libraryDirs) {
        if (!osName.toLowerCase(Locale.ROOT).contains("linux"))
            return null;
        List<Integer> versions = libavcodecVersions(libraryDirs);
        if (versions.stream().anyMatch((Integer version) -> version >= MIN_LIBAVCODEC && version <= MAX_LIBAVCODEC))
            return null;
        int newest = versions.stream().max(Integer::compare).orElse(0);
        List<String> family = family(osRelease);
        if (family.contains("arch"))
            return new Remedy(Kind.ARCH, newest);
        boolean debian = family.contains("debian") || family.contains("ubuntu");
        if (debian && newest == 0)
            return new Remedy(Kind.DEBIAN_MISSING, newest);
        if (debian && newest > MAX_LIBAVCODEC)
            return new Remedy(Kind.DEBIAN_NEWER, newest);
        return new Remedy(Kind.OTHER, newest);
    }

    /*  The major versions of the libavcodec files in the library directories  */
    private static List<Integer> libavcodecVersions(List<Path> libraryDirs) {
        List<Integer> versions = new ArrayList<>();
        for (Path dir : libraryDirs) {
            try (Stream<Path> files = Files.list(dir)) {
                files.map((Path file) -> LIBAVCODEC.matcher(file.getFileName().toString()))
                        .filter(Matcher::matches)
                        .forEach((Matcher match) -> versions.add(Integer.parseInt(match.group(1))));
            }
            catch (IOException | UncheckedIOException e) {
                /*  A directory which is missing or cannot be read has no libavcodec  */
            }
        }
        return versions;
    }

    /*  The ID and ID_LIKE of the distribution, like "manjaro" and "arch", empty without os-release  */
    private static List<String> family(Path osRelease) {
        List<String> family = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(osRelease)) {
                String[] entry = line.split("=", 2);
                if (entry.length == 2 && (entry[0].trim().equals("ID") || entry[0].trim().equals("ID_LIKE")))
                    family.addAll(Arrays.asList(entry[1].replace("\"", "").replace("'", "").trim()
                            .toLowerCase(Locale.ROOT).split("\\s+")));
            }
        }
        catch (IOException | UncheckedIOException e) {
            logger.debug("No os-release at {}: {}", osRelease, e.getMessage());
        }
        return family;
    }

    /*  Takes the detection of this session, also a still running one, with null for no remedy, for the tests  */
    static synchronized void assume(CompletableFuture<Remedy> detection) {
        detected = detection;
    }

    /*  Forgets the detection, for the tests  */
    static synchronized void reset() {
        detected = null;
    }
}
