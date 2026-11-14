package art.moodframe.util;

import art.moodframe.util.SystemThemeDetector.CommandResult;
import art.moodframe.util.SystemThemeDetector.CommandRunner;
import art.moodframe.util.SystemThemeDetector.Preference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The light or dark preference of macOS, Windows and Linux, with fake commands instead of the real ones. */
class SystemThemeDetectorTest {

    /*  a fake command: the program and a text its command line contains  */
    private record Rule(String program, String contains, CommandResult result) {
    }

    private static Rule rule(String program, String contains, int exitCode, String output) {
        return new Rule(program, contains, new CommandResult(exitCode, output));
    }

    /*  the command lines asked for, in order  */
    private final List<String> calls = new ArrayList<>();

    /*  answers by the first matching rule, a program without a rule is missing  */
    private Preference detect(String osName, Map<String, String> env, Rule... rules) {
        CommandRunner runner = (timeoutMillis, command) -> {
            String line = String.join(" ", command);
            calls.add(line);
            assertTrue(timeoutMillis > 0 && timeoutMillis <= 1000, "time limit " + timeoutMillis);
            for (Rule rule : rules)
                if (command[0].equals(rule.program()) && line.contains(rule.contains()))
                    return rule.result();
            return null;
        };
        return new SystemThemeDetector(runner, env::get).detect(osName);
    }

    private Preference detectLinux(Rule... rules) {
        return detect("Linux", Map.of(), rules);
    }

    @Test
    void readsTheColorSchemeOfThePortal() {
        assertEquals(new Preference("dark", "xdg-portal"),
                detectLinux(rule("gdbus", "ReadOne", 0, "(<uint32 1>,)\n")));
        assertEquals(new Preference("light", "xdg-portal"),
                detectLinux(rule("gdbus", "ReadOne", 0, "(<uint32 2>,)\n")));

        /*  0 is no preference: the next sources decide, else dark  */
        assertEquals(SystemThemeDetector.UNKNOWN, detectLinux(rule("gdbus", "ReadOne", 0, "(<uint32 0>,)\n")));
        assertEquals(new Preference("light", "gsettings color-scheme"), detectLinux(
                rule("gdbus", "ReadOne", 0, "(<uint32 0>,)\n"),
                rule("gsettings", "color-scheme", 0, "'prefer-light'\n")));

        /*  an unknown answer is no preference either  */
        assertEquals(SystemThemeDetector.UNKNOWN, detectLinux(rule("gdbus", "ReadOne", 0, "(<uint32 7>,)\n")));
        assertEquals(SystemThemeDetector.UNKNOWN, detectLinux(rule("gdbus", "ReadOne", 0, "Error\n")));
    }

    @Test
    void asksThePortalWithTheToolAtHand() {
        assertEquals(new Preference("light", "xdg-portal"), detectLinux(rule("busctl", "ReadOne", 0, "v u 2\n")));
        assertEquals(new Preference("dark", "xdg-portal"), detectLinux(
                rule("dbus-send", "ReadOne", 0, "   variant       uint32 1\n")));

        /*  portals before ReadOne answer Read only, with the value in a further variant  */
        assertEquals(new Preference("light", "xdg-portal"), detectLinux(
                rule("gdbus", "ReadOne", 1, ""),
                rule("gdbus", "Read ", 0, "(<<uint32 2>>,)\n")));
        assertEquals(new Preference("dark", "xdg-portal"), detectLinux(
                rule("busctl", "ReadOne", 1, ""),
                rule("busctl", "Read ", 0, "v v u 1\n")));
        assertEquals(new Preference("dark", "xdg-portal"), detectLinux(
                rule("dbus-send", "ReadOne", 1, ""),
                rule("dbus-send", "Read ", 0, "   variant       variant          uint32 1\n")));
    }

    @Test
    void goesOnWithGsettingsWithoutAPortal() {
        assertEquals(new Preference("dark", "gsettings color-scheme"), detectLinux(
                rule("gdbus", "Read", 1, ""),
                rule("gsettings", "color-scheme", 0, "'prefer-dark'\n")));

        /*  the tool ran but no portal answered, so the other tools are not asked  */
        assertEquals(3, calls.size(), calls.toString());
        assertTrue(calls.get(2).startsWith("gsettings"), calls.toString());

        assertEquals(new Preference("light", "gsettings color-scheme"), detectLinux(
                rule("gsettings", "color-scheme", 0, "'prefer-light'\n")));
        assertEquals(new Preference("dark", "gsettings gtk-theme"), detectLinux(
                rule("gsettings", "color-scheme", 0, "'default'\n"),
                rule("gsettings", "gtk-theme", 0, "'Adwaita-dark'\n")));
        assertEquals(new Preference("dark", "gsettings gtk-theme"), detectLinux(
                rule("gsettings", "gtk-theme", 0, "'Yaru-Dark'\n")));

        /*  a theme name without "dark" is no definite light  */
        assertEquals(SystemThemeDetector.UNKNOWN, detectLinux(
                rule("gsettings", "color-scheme", 0, "'default'\n"),
                rule("gsettings", "gtk-theme", 0, "'Adwaita'\n")));

        /*  GNOME before 42 has no color-scheme key  */
        assertEquals(new Preference("dark", "gsettings gtk-theme"), detectLinux(
                rule("gsettings", "color-scheme", 1, ""),
                rule("gsettings", "gtk-theme", 0, "'Adwaita-dark'\n")));
    }

    @Test
    void readsGtkThemeLast() {
        assertEquals(new Preference("dark", "GTK_THEME"), detect("Linux", Map.of("GTK_THEME", "Adwaita:dark")));
        assertEquals(new Preference("dark", "GTK_THEME"), detect("Linux", Map.of("GTK_THEME", "Arc-Dark")));
        assertEquals(SystemThemeDetector.UNKNOWN, detect("Linux", Map.of("GTK_THEME", "Adwaita")));
    }

    @Test
    void fallsBackToDarkWithoutAnyAnswer() {
        assertEquals(SystemThemeDetector.UNKNOWN, detectLinux());
        assertEquals("dark", SystemThemeDetector.UNKNOWN.value());
        assertNull(SystemThemeDetector.UNKNOWN.source());

        /*  an unsupported system runs no command at all  */
        calls.clear();
        assertEquals(SystemThemeDetector.UNKNOWN,
                detect("FreeBSD", Map.of(), rule("gdbus", "ReadOne", 0, "(<uint32 2>,)")));
        assertTrue(calls.isEmpty(), calls.toString());
    }

    @Test
    void staysWithinTheTotalTimeLimit() {
        /*  every command hangs until its time limit: the whole detection ends after about 2 s  */
        List<Long> limits = new ArrayList<>();
        CommandRunner hanging = (timeoutMillis, command) -> {
            limits.add(timeoutMillis);
            try {
                Thread.sleep(timeoutMillis);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        };
        long start = System.nanoTime();
        Preference preference = new SystemThemeDetector(hanging, (name) -> null).detect("Linux");
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertEquals(SystemThemeDetector.UNKNOWN, preference);
        assertTrue(elapsedMillis < 2500, "took " + elapsedMillis + " ms");
        assertTrue(limits.stream().allMatch((limit) -> limit <= 1000), limits.toString());
    }

    @Test
    void keepsTheAnswersOfMacOS() {
        assertEquals(new Preference("dark", "defaults"),
                detect("Mac OS X", Map.of(), rule("defaults", "AppleInterfaceStyle", 0, "Dark\n")));

        /*  no AppleInterfaceStyle key is light, as before  */
        assertEquals(new Preference("light", "defaults"),
                detect("Mac OS X", Map.of(), rule("defaults", "AppleInterfaceStyle", 1, "")));

        /*  only a command that cannot run or hangs is unknown  */
        assertEquals(SystemThemeDetector.UNKNOWN, detect("Mac OS X", Map.of()));
    }

    @Test
    void keepsTheAnswersOfWindows() {
        String key = "HKEY_CURRENT_USER\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize\n";
        assertEquals(new Preference("dark", "registry"), detect("Windows 11", Map.of(),
                rule("reg", "AppsUseLightTheme", 0, key + "    AppsUseLightTheme    REG_DWORD    0x0\n")));
        assertEquals(new Preference("light", "registry"), detect("Windows 11", Map.of(),
                rule("reg", "AppsUseLightTheme", 0, key + "    AppsUseLightTheme    REG_DWORD    0x1\n")));

        /*  no AppsUseLightTheme value is light, as before and as Windows shows it  */
        assertEquals(new Preference("light", "registry"),
                detect("Windows 11", Map.of(), rule("reg", "AppsUseLightTheme", 1, "")));
        assertEquals(SystemThemeDetector.UNKNOWN, detect("Windows 11", Map.of()));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runsRealCommandsWithinTheirTimeLimit() {
        assertEquals(new CommandResult(0, "moodframe\n"), SystemThemeDetector.runProcess(1000, "echo", "moodframe"));
        assertEquals(1, SystemThemeDetector.runProcess(1000, "false").exitCode());
        assertNull(SystemThemeDetector.runProcess(1000, "moodframe-no-such-program"));

        long start = System.nanoTime();
        assertNull(SystemThemeDetector.runProcess(200, "sleep", "5"));
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1000);
    }
}
