package art.moodframe.service;

import art.moodframe.model.AttachedImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageAttachmentServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void attachesAnImageAboveTheLimitAsAJpegWithinTheLimit() throws Exception {
        BufferedImage noise = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(1);
        for (int y = 0; y < noise.getHeight(); y++)
            for (int x = 0; x < noise.getWidth(); x++)
                noise.setRGB(x, y, random.nextInt(0x1000000));
        File png = tempDir.resolve("large.png").toFile();
        ImageIO.write(noise, "png", png);

        ImageAttachmentService service = new ImageAttachmentService();
        service.setMaxFileSize(100L * 1024);
        assertTrue(png.length() > 100L * 1024, "the test image must exceed the limit");

        AttachedImage attachment = service.createAttachment(png);

        assertTrue(attachment.getImageFile().getName().endsWith(".jpg"));
        assertTrue(attachment.getImageFile().length() <= 100L * 1024);
        assertEquals(4.0 / 3.0, (double) attachment.getWidth() / attachment.getHeight(), 0.01);
    }

    @Test
    void scalesAnImageAboveSixteenMegapixelsDownEvenIfItsFileIsSmall() throws Exception {
        File png = tempDir.resolve("wide.png").toFile();
        ImageIO.write(new BufferedImage(5000, 4000, BufferedImage.TYPE_INT_RGB), "png", png);

        AttachedImage attachment = new ImageAttachmentService().createAttachment(png);

        assertTrue((long) attachment.getWidth() * attachment.getHeight() <= ImageAttachmentService.MAX_PIXELS);
        assertEquals(1.25, (double) attachment.getWidth() / attachment.getHeight(), 0.01);
    }

    @Test
    void keepsAnImageWithinTheLimit() throws Exception {
        File png = tempDir.resolve("small.png").toFile();
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", png);

        AttachedImage attachment = new ImageAttachmentService().createAttachment(png);

        assertEquals(png, attachment.getImageFile());
    }
}
