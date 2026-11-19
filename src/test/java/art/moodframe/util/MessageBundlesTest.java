package art.moodframe.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The English and the German messages have the same keys, so no text falls back to English. */
class MessageBundlesTest {

    @Test
    void englishAndGermanHaveTheSameKeys() throws IOException {
        Set<String> english = keys("/i18n/messages_en.properties");
        Set<String> german = keys("/i18n/messages_de.properties");

        Set<String> onlyEnglish = new TreeSet<>(english);
        onlyEnglish.removeAll(german);
        Set<String> onlyGerman = new TreeSet<>(german);
        onlyGerman.removeAll(english);
        assertEquals(Set.of(), onlyEnglish, "only in messages_en");
        assertEquals(Set.of(), onlyGerman, "only in messages_de");
    }

    private static Set<String> keys(String resource) throws IOException {
        try (InputStream stream = MessageBundlesTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            Properties messages = new Properties();
            messages.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return messages.stringPropertyNames();
        }
    }
}
