package art.moodframe.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Opens a file in the default application of the system, e.g. a video in its video player: with the AWT desktop
 * support, and without it, which many Linux desktops lack, with xdg-open.
 *
 * @since 2.1.0
 */
public final class SystemOpen {

    private static final Logger logger = LoggerFactory.getLogger(SystemOpen.class);

    private SystemOpen() {
    }

    /**
     * Tells whether a file can be opened at all.
     *
     * @return true with the AWT desktop support for opening files, or with xdg-open in the PATH
     */
    public static boolean isAvailable() {
        return desktopOpens() || findProgram("xdg-open") != null;
    }

    /**
     * Opens a file in the default application of the system, in the background; a failure is only logged.
     *
     * @param file the file to open
     */
    public static void open(Path file) {
        CompletableFuture.runAsync(() -> {
            try {
                if (desktopOpens())
                    Desktop.getDesktop().open(file.toFile());
                else
                    new ProcessBuilder("xdg-open", file.toString()).start();
            }
            catch (IOException | RuntimeException e) {
                logger.warn("Could not open {}: {}", file, e.getMessage());
            }
        });
    }

    /**
     * Finds a program in the PATH.
     *
     * @param name the name of the program, without ".exe"
     * @return its executable, or null if it is not in the PATH
     */
    public static Path findProgram(String name) {
        return findProgram(name, System.getenv("PATH"));
    }

    /*  The executable of a program in a search path like PATH, under Windows with ".exe"  */
    static Path findProgram(String name, String searchPath) {
        if (searchPath == null)
            return null;
        for (String directory : searchPath.split(File.pathSeparator)) {
            if (directory.isEmpty())
                continue;
            for (String file : List.of(name, name + ".exe")) {
                try {
                    Path candidate = Path.of(directory, file);
                    if (Files.isRegularFile(candidate) && Files.isExecutable(candidate))
                        return candidate;
                }
                catch (InvalidPathException e) {
                    logger.debug("Skipping the PATH entry {}: {}", directory, e.getMessage());
                }
            }
        }
        return null;
    }

    private static boolean desktopOpens() {
        return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
    }
}
