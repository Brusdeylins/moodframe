package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.GeneratedImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageFileStoreTest {

    private static GeneratedImage image() {
        GeneratedImage image = new GeneratedImage("0123456789abcdef");
        image.setCreatedAt(LocalDateTime.of(2026, 10, 26, 14, 30, 5));
        image.setModel("Nano Banana 2 1K");
        return image;
    }

    private static String base64(String format) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 4, BufferedImage.TYPE_INT_RGB), format, bytes);
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    @Test
    void detectsPngAndJpegByTheirMagicBytes() {
        assertEquals("PNG", ImageFileStore.detectFormat(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0}));
        assertEquals("JPEG", ImageFileStore.detectFormat(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
        assertEquals("UNKNOWN", ImageFileStore.detectFormat(new byte[] {1, 2, 3, 4}));
        assertEquals("UNKNOWN", ImageFileStore.detectFormat(null));
    }

    @Test
    void namesFilesByTimeModelAndId() {
        assertEquals("flux_20261026_143005_NanoBanana21K_01234567", ImageFileStore.filenameWithoutExtension(image()));
    }

    @Test
    void writesABase64JpegAsPngWithItsDimensions(@TempDir Path dir) throws Exception {
        ApplicationConfig config = mock(ApplicationConfig.class);
        when(config.getImagesPath()).thenReturn(dir.toString());

        ImageFileStore.StoredImage stored = new ImageFileStore(config, null).writeBase64(base64("jpg"), image());

        assertEquals("PNG", stored.format());
        assertEquals("image/png", stored.mimeType());
        assertEquals(8, stored.width());
        assertEquals(4, stored.height());
        assertTrue(stored.filename().endsWith(".png"));
        assertEquals(Files.size(stored.path()), stored.size());
        assertEquals(dir, stored.path().getParent());
    }

    @Test
    void doesNotQuoteTheSignatureOfARejectedUrl(@TempDir Path dir) {
        ApplicationConfig config = mock(ApplicationConfig.class);
        when(config.getImagesPath()).thenReturn(dir.toString());
        GeneratedImage image = image();
        image.setImageUrl("https://delivery.example/result image.png?sp=r&sig=SECRETSIG");

        Exception error = assertThrows(java.io.IOException.class, () -> new ImageFileStore(config, null).download(image));

        assertTrue(error.getMessage().contains("sig=***"), error.getMessage());
        assertFalse(error.getMessage().contains("SECRETSIG"), error.getMessage());
    }
}
