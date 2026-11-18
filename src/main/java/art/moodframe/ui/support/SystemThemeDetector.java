package art.moodframe.ui.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects whether the operating system prefers a dark or a light appearance.
 *
 * <p>macOS asks {@code defaults} (a missing {@code AppleInterfaceStyle} means light), Windows the registry value
 * {@code AppsUseLightTheme} (a missing value means light), Linux the XDG desktop portal, then {@code gsettings},
 * then {@code GTK_THEME}. Every command has a time limit of 1 s, the whole detection of 2 s; a preference that
 * cannot be determined (unsupported system, missing program, time-out, unknown answer, no preference) is dark.</p>
 *
 * @since 2.1.0
 */
final class SystemThemeDetector {

    /*  exit code and standard output of a finished command  */
    record CommandResult(int exitCode, String output) {
    }

    /*  runs a command within the time limit, null if it cannot run or does not finish in time  */
    @FunctionalInterface
    interface CommandRunner {
        CommandResult run(long timeoutMillis, String... command);
    }

    /*  the preference ("dark" or "light") and the source it came from, source null if unknown  */
    record Preference(String value, String source) {
    }

    static final Preference UNKNOWN = new Preference("dark", null);

    private static final long COMMAND_LIMIT_MILLIS = 1000;
    private static final long TOTAL_LIMIT_MILLIS   = 2000;

    /*  color-scheme of the portal as printed by gdbus ("uint32 1"), busctl ("u 1") and dbus-send ("uint32 1")  */
    private static final Pattern PORTAL_VALUE = Pattern.compile("\\b(?:uint32|u)\\s+(\\d+)");

    private static final String PORTAL_DEST  = "org.freedesktop.portal.Desktop";
    private static final String PORTAL_PATH  = "/org/freedesktop/portal/desktop";
    private static final String PORTAL_IFACE = "org.freedesktop.portal.Settings";

    private final CommandRunner runner;
    private final Function<String, String> env;
    private long deadline;

    SystemThemeDetector(CommandRunner runner, Function<String, String> env) {
        this.runner = runner;
        this.env    = env;
    }

    /**
     * Detects the preference of the running system.
     *
     * @return the preference, dark if unknown
     */
    static Preference detectSystem() {
        return new SystemThemeDetector(SystemThemeDetector::runProcess, System::getenv)
                .detect(System.getProperty("os.name", ""));
    }

    /**
     * Detects the preference of the given system.
     *
     * @param osName the value of {@code os.name}
     * @return the preference, dark if unknown
     */
    Preference detect(String osName) {
        deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TOTAL_LIMIT_MILLIS);
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("mac"))
            return macOS();
        if (os.contains("win"))
            return windows();
        if (os.contains("linux"))
            return linux();
        return UNKNOWN;
    }

    private Preference macOS() {
        CommandResult result = run("defaults", "read", "-g", "AppleInterfaceStyle");
        if (result == null)
            return UNKNOWN;

        /*  the key exists in dark mode only, so a missing key is a definite light  */
        return new Preference(result.exitCode() == 0 ? "dark" : "light", "defaults");
    }

    private Preference windows() {
        CommandResult result = run("reg", "query",
                "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme");
        if (result == null)
            return UNKNOWN;

        /*  0x0 means dark; a missing value (reg fails) means light, as Windows itself shows it  */
        boolean dark = result.exitCode() == 0 && result.output().lines()
                .anyMatch((line) -> line.contains("AppsUseLightTheme") && line.contains("0x0"));
        return new Preference(dark ? "dark" : "light", "registry");
    }

    private Preference linux() {
        String value = portal();
        if (value != null)
            return new Preference(value, "xdg-portal");

        CommandResult result = run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
        if (result != null && result.exitCode() == 0) {
            if (result.output().contains("prefer-dark"))
                return new Preference("dark", "gsettings color-scheme");
            if (result.output().contains("prefer-light"))
                return new Preference("light", "gsettings color-scheme");
        }

        /*  a theme name tells dark only: names without "dark" are no definite light  */
        result = run("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme");
        if (result != null && result.exitCode() == 0 && result.output().toLowerCase(Locale.ROOT).contains("dark"))
            return new Preference("dark", "gsettings gtk-theme");
        String gtkTheme = env.apply("GTK_THEME");
        if (gtkTheme != null && gtkTheme.toLowerCase(Locale.ROOT).contains("dark"))
            return new Preference("dark", "GTK_THEME");
        return UNKNOWN;
    }

    /*  color-scheme of the XDG desktop portal via the first available tool, ReadOne or else the older Read  */
    private String portal() {
        List<String[]> readOne = portalCommands("ReadOne");
        List<String[]> read    = portalCommands("Read");
        for (int i = 0; i < readOne.size(); i++) {
            CommandResult result = run(readOne.get(i));
            if (result == null)
                continue;
            if (result.exitCode() != 0)
                result = run(read.get(i));
            if (result == null || result.exitCode() != 0)
                return null;
            Matcher matcher = PORTAL_VALUE.matcher(result.output());
            if (!matcher.find())
                return null;

            /*  1 = dark, 2 = light, 0 = no preference  */
            return switch (matcher.group(1)) {
                case "1" -> "dark";
                case "2" -> "light";
                default  -> null;
            };
        }
        return null;
    }

    private static List<String[]> portalCommands(String method) {
        return List.of(
                new String[] { "gdbus", "call", "--session", "--dest", PORTAL_DEST, "--object-path", PORTAL_PATH,
                        "--method", PORTAL_IFACE + "." + method, "org.freedesktop.appearance", "color-scheme" },
                new String[] { "busctl", "--user", "call", PORTAL_DEST, PORTAL_PATH, PORTAL_IFACE, method,
                        "ss", "org.freedesktop.appearance", "color-scheme" },
                new String[] { "dbus-send", "--session", "--print-reply=literal", "--dest=" + PORTAL_DEST, PORTAL_PATH,
                        PORTAL_IFACE + "." + method, "string:org.freedesktop.appearance", "string:color-scheme" });
    }

    /*  runs a command within the per-command limit and what is left of the total limit  */
    private CommandResult run(String... command) {
        long left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (left <= 0)
            return null;
        return runner.run(Math.min(COMMAND_LIMIT_MILLIS, left), command);
    }

    static CommandResult runProcess(long timeoutMillis, String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return null;
            }

            /*  read after the exit: the short answers fit into the pipe, a hanging command never blocks here  */
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(process.exitValue(), output);
        }
        catch (IOException e) {
            return null;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
