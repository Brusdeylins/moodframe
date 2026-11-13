package art.moodframe.service;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.repository.ChatRepository;
import art.moodframe.util.ImageSize;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Runs against a fresh SQLite database with a 16:9 image file of 160 by 90 pixels. */
class ImageSizeCheckTest {

    @TempDir
    static Path tempDir;

    private static ChatRepository repository;
    private static ImageSizeCheck check;
    private static Path file;

    @BeforeAll
    static void createDatabaseAndFile() throws Exception {
        DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve("db").resolve("test.db"));
        databaseManager.initialize();
        repository = new ChatRepository(databaseManager);
        check = new ImageSizeCheck(repository);
        file = tempDir.resolve("image.png");
        ImageIO.write(new BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB), "png", file.toFile());
    }

    @Test
    void correctsAWrongSizeFromTheFileAndStoresIt() throws Exception {
        GeneratedImage image = image(1024, 1024);

        assertEquals(new ImageSize(160, 90), check.correct(image.getId(), file, 1024, 1024));
        assertEquals("160x90", repository.findGeneratedImageById(image.getId()).orElseThrow().getDimensions());
    }

    @Test
    void correctsAMissingSize() throws Exception {
        GeneratedImage image = image(0, 0);

        assertEquals(new ImageSize(160, 90), check.correct(image.getId(), file, 0, 0));
        assertEquals("160x90", repository.findGeneratedImageById(image.getId()).orElseThrow().getDimensions());
    }

    @Test
    void leavesARightSizeAndAnUnreadableFileAlone() throws Exception {
        GeneratedImage image = image(1024, 1024);

        assertNull(check.correct(image.getId(), file, 160, 90));
        assertNull(check.correct(image.getId(), tempDir.resolve("missing.png"), 1024, 1024));
        assertEquals("1024x1024", repository.findGeneratedImageById(image.getId()).orElseThrow().getDimensions());
    }

    /** Stores a downloaded image with a known size. */
    private static GeneratedImage image(int width, int height) throws Exception {
        Chat chat = new Chat.Builder().name("Küste").build();
        repository.saveChat(chat);
        ChatEntry entry = new ChatEntry.Builder().chatId(chat.getId()).userPrompt("a coast").build();
        repository.saveChatEntry(entry);
        GeneratedImage image = new GeneratedImage.Builder().chatEntryId(entry.getId())
                .downloadStatus(GeneratedImage.DownloadStatus.CACHED).width(width).height(height).build();
        repository.saveGeneratedImage(image);
        return image;
    }
}
