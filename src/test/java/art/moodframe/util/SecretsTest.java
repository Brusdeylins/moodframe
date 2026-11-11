package art.moodframe.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretsTest {

    @Test
    void redactsTheValueOfSecretParametersOnly() {
        assertEquals("https://host/v1beta/models/m:generateContent?key=***",
                Secrets.redact("https://host/v1beta/models/m:generateContent?key=AIzaSyTest-123_abc"));
        assertEquals("https://host/a.png?se=2026&sig=***&sp=r",
                Secrets.redact("https://host/a.png?se=2026&sig=ab%2Fcd%3D&sp=r"));
        assertEquals("api_key=*** api-key=*** apikey=*** KEY=*** access_token=*** token=*** password=***",
                Secrets.redact("api_key=a api-key=b apikey=c KEY=d access_token=e token=f password=g"));
        assertEquals("failed for \"https://host/x?key=***\" (HTTP 400)",
                Secrets.redact("failed for \"https://host/x?key=secret\" (HTTP 400)"));
    }

    @Test
    void keepsTextWithoutSecretsAsItIs() {
        String text = "https://www.youtube.com/watch?v=jNQXAC9IVRw (geminiKey=true, hasKey=false, design=flat) key=";

        assertEquals(text, Secrets.redact(text));
        assertEquals("key=***", Secrets.redact("key=***"));
        assertEquals("", Secrets.redact(""));
        assertNull(Secrets.redact(null));
    }

    @Test
    void redactsTheLogFilesOfADirectoryOnce(@TempDir Path dir) throws Exception {
        Path leaked = Files.writeString(dir.resolve("moodframe.2026-11-10.log"),
                "10:00 INFO Gr\u00fc\u00dfe an https://host/m:generateContent?key=AIzaSyTest\r\n10:01 INFO 12 bytes\r\n");
        Path clean = Files.writeString(dir.resolve("moodframe.log"), "10:00 INFO started\n");
        Path other = Files.writeString(dir.resolve("notes.txt"), "key=AIzaSyTest");

        Secrets.redactLogs(dir);

        assertEquals("10:00 INFO Gr\u00fc\u00dfe an https://host/m:generateContent?key=***\r\n10:01 INFO 12 bytes\r\n",
                Files.readString(leaked));
        assertEquals("10:00 INFO started\n", Files.readString(clean));
        assertEquals("key=AIzaSyTest", Files.readString(other));
        assertTrue(Files.exists(dir.resolve(".redacted")));

        Files.writeString(leaked, "key=AIzaSyTest");
        Secrets.redactLogs(dir);

        assertEquals("key=AIzaSyTest", Files.readString(leaked));
    }

    @Test
    void doesNothingWithoutALogDirectory(@TempDir Path dir) {
        Path missing = dir.resolve("logs");

        Secrets.redactLogs(missing);

        assertFalse(Files.exists(missing));
    }
}
