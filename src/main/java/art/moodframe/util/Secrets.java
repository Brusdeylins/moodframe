package art.moodframe.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Keeps keys, tokens and passwords out of logs, error messages and status lines.
 *
 * @since 2.0.0
 */
public final class Secrets {

    private static final Logger logger = LoggerFactory.getLogger(Secrets.class);

    /*  A secret as a parameter of a URL or a form ("key", "api_key", "token", "sig", ...), its value ends at the separator  */
    private static final Pattern PARAMETER = Pattern.compile(
            "(?i)\\b((?:api[_-]?)?key|(?:access_)?token|sig(?:nature)?|password)=[^&\\s\"'<>]+");
    private static final String LOGS_MARKER = ".redacted";

    private Secrets() {
    }

    /**
     * Replaces the value of every secret parameter in a text by {@code ***}, for example
     * {@code ?key=abc} by {@code ?key=***}.
     *
     * @param text a URL, a message or a whole log, may be null
     * @return the text without the secret values
     */
    public static String redact(String text) {
        return text == null ? null : PARAMETER.matcher(text).replaceAll("$1=***");
    }

    /**
     * Redacts the log files of a directory once: earlier versions wrote the Gemini key into them. A marker
     * file remembers the run, it is written only if every file could be redacted.
     *
     * @param logsDir the directory of the log files
     */
    public static void redactLogs(Path logsDir) {
        Path marker = logsDir.resolve(LOGS_MARKER);
        if (!Files.isDirectory(logsDir) || Files.exists(marker))
            return;

        try (Stream<Path> files = Files.list(logsDir)) {
            for (Path file : files.filter((path) -> path.getFileName().toString().endsWith(".log")).toList()) {
                /*  ISO-8859-1 maps every byte to one character and back, so the file keeps its encoding  */
                String content = Files.readString(file, StandardCharsets.ISO_8859_1);
                String redacted = redact(content);

                /*  Written in place, the logger keeps the current file open and goes on appending to it  */
                if (!redacted.equals(content))
                    Files.writeString(file, redacted, StandardCharsets.ISO_8859_1);
            }
            Files.createFile(marker);
        }
        catch (IOException | UncheckedIOException e) {
            logger.warn("Could not redact the log files in {}", logsDir, e);
        }
    }
}
