package art.moodframe.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void cleansTheChatNameForTheFileName() {
        assertEquals("Lighthouse series", DownloadsFolder.chatName("Lighthouse series"));
        assertEquals("ab cd efghi j", DownloadsFolder.chatName(" a/b\\ c:d *?\"e<f>g|h\u0000i\tj "));
        assertEquals("Harbor at dawn", DownloadsFolder.chatName("Harbor \n  at   dawn"));
        assertEquals("x".repeat(80), DownloadsFolder.chatName("x".repeat(120)));
        assertEquals("MoodFrame", DownloadsFolder.chatName(null));
        assertEquals("MoodFrame", DownloadsFolder.chatName(" /:? "));
        assertEquals("x".repeat(79), DownloadsFolder.chatName("x".repeat(79) + "😀y"));
    }

    @Test
    void keepsUmlautsAndNamesANameGivenInNfdLikeInNfc(@TempDir Path dir) throws Exception {
        File png = file(dir, "moodframe_20261116_173617_nano-banana-2_c8db09f7.png", 0x89, 0x50, 0x4E, 0x47);
        File downloads = dir.resolve("Downloads").toFile();
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 16, 17, 36, 17);
        String nfc = "Größe Übung – Café";
        String nfd = Normalizer.normalize(nfc, Normalizer.Form.NFD);

        assertNotEquals(nfc, nfd);
        assertEquals("Größe Übung – Café 2026-11-16 17.36.17.png",
                DownloadsFolder.copy(png, nfc, createdAt, downloads));
        assertEquals("Größe Übung – Café 2026-11-16 17.36.17 2.png",
                DownloadsFolder.copy(png, nfd, createdAt, downloads));
    }

    @Test
    void namesTheCopyByChatAndCreationTimeAndNumbersItIfTheNameIsTaken(@TempDir Path dir) throws Exception {
        File png = file(dir, "moodframe_20261116_173617_nano-banana-2_c8db09f7.png", 0x89, 0x50, 0x4E, 0x47);
        File downloads = dir.resolve("Downloads").toFile();
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 16, 17, 36, 17);

        assertEquals("Lighthouse series 2026-11-16 17.36.17.png",
                DownloadsFolder.copy(png, "Lighthouse series", createdAt, downloads));
        assertEquals("Lighthouse series 2026-11-16 17.36.17 2.png",
                DownloadsFolder.copy(png, "Lighthouse series", createdAt, downloads));
        assertEquals("Lighthouse series 2026-11-16 17.36.17 3.png",
                DownloadsFolder.copy(png, "Lighthouse series", createdAt, downloads));
        assertTrue(new File(downloads, "Lighthouse series 2026-11-16 17.36.17 2.png").exists());
    }

    @Test
    void namesAVideoWithoutChatAfterMoodFrame(@TempDir Path dir) throws Exception {
        File video = file(dir, "moodframe_20261116_173617_gemini-omni_c8db09f7.mp4", 0, 0, 0, 0x18);

        assertEquals("MoodFrame 2026-01-02 03.04.05.mp4", DownloadsFolder.copy(video, null,
                LocalDateTime.of(2026, 1, 2, 3, 4, 5), dir.resolve("Downloads").toFile()));
    }
}
