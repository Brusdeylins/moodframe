package tools.moodframe.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Copies a generated image or video into the Downloads folder of the user, named by time and with the
 * extension of its real format.
 *
 * @since 1.9.1
 */
public final class DownloadsFolder {

    private static final Logger logger = LoggerFactory.getLogger(DownloadsFolder.class);

    private DownloadsFolder() {
    }

    /**
     * Copies a file into {@code ~/Downloads} as {@code moodframe_image_<time>.<ext>} or
     * {@code moodframe_video_<time>.mp4}.
     *
     * @param source the image or video file
     * @return the name of the copy
     * @throws IOException if the copy fails
     */
    public static String copy(File source) throws IOException {
        File downloadsDir = new File(System.getProperty("user.home"), "Downloads");
        if (!downloadsDir.exists())
            downloadsDir.mkdirs();

        boolean video = source.getName().toLowerCase().endsWith(".mp4");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = (video ? "moodframe_video_" : "moodframe_image_") + timestamp + (video ? ".mp4" : extensionOf(source));
        File target = new File(downloadsDir, filename);
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        logger.info("Image downloaded to Downloads folder: {}", target.getAbsolutePath());
        return filename;
    }

    /**
     * Gets the extension of an image by its first bytes: JPEG (FF D8; the third byte varies by JFIF, Exif, ...),
     * PNG (89 50 4E 47) or WebP (RIFF), PNG if unknown or unreadable.
     *
     * @param image the image file
     * @return {@code .jpeg}, {@code .png} or {@code .webp}
     */
    static String extensionOf(File image) {
        byte[] header = new byte[4];
        int bytesRead;
        try (FileInputStream in = new FileInputStream(image)) {
            bytesRead = in.read(header);
        } catch (IOException e) {
            logger.warn("Could not detect image format, using .png as fallback", e);
            return ".png";
        }

        if (bytesRead >= 2 && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8)
            return ".jpeg";
        if (bytesRead >= 4 && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46)
            return ".webp";
        return ".png";
    }
}
