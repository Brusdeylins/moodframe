package art.moodframe.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DownloadsFolderTest {

    private static File file(Path dir, String name, int... values) throws Exception {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++)
            bytes[i] = (byte) values[i];
        return Files.write(dir.resolve(name), bytes).toFile();
    }

    @Test
    void detectsTheExtensionByTheFirstBytes(@TempDir Path dir) throws Exception {
        assertEquals(".jpeg", DownloadsFolder.extensionOf(file(dir, "a", 0xFF, 0xD8, 0xE1, 0)));
        assertEquals(".png", DownloadsFolder.extensionOf(file(dir, "b", 0x89, 0x50, 0x4E, 0x47)));
        assertEquals(".webp", DownloadsFolder.extensionOf(file(dir, "c", 0x52, 0x49, 0x46, 0x46)));
        assertEquals(".png", DownloadsFolder.extensionOf(file(dir, "d", 1, 2, 3, 4)));
        assertEquals(".png", DownloadsFolder.extensionOf(dir.resolve("missing").toFile()));
    }
}
