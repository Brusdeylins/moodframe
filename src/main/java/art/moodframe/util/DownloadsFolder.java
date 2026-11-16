package art.moodframe.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Copies a generated image or video into the Downloads folder of the user, named by its chat and creation time like
 * a macOS screenshot and with the extension of its real format.
 *
 * @since 1.9.1
 */
public final class DownloadsFolder {

    private static final Logger logger = LoggerFactory.getLogger(DownloadsFolder.class);

    /*  Characters a file system does not allow in a name, and control characters  */
    private static final Pattern FORBIDDEN = Pattern.compile("[/\\\\:*?\"<>|\\p{Cntrl}]");
    private static final int MAX_CHAT_NAME = 80;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss");

    private DownloadsFolder() {
    }

    /**
     * Copies a file into {@code ~/Downloads} as {@code <chat> <yyyy-MM-dd> <HH.mm.ss>.<ext>}, e.g.
     * {@code Lighthouse series 2026-11-16 17.36.17.png}; an existing file is kept and the copy numbered
     * {@code ... 2.png}, {@code ... 3.png} and so on.
     *
     * @param source    the image or video file
     * @param chatName  the name of the chat of the image, null without chat
     * @param createdAt the creation time of the image, null for the time the file was last changed
     * @return the name of the copy
     * @throws IOException if the copy fails
     */
    public static String copy(File source, String chatName, LocalDateTime createdAt) throws IOException {
        return copy(source, chatName, createdAt, new File(System.getProperty("user.home"), "Downloads"));
    }

    /** Copies a file into the given folder, see {@link #copy(File, String, LocalDateTime)}. */
    static String copy(File source, String chatName, LocalDateTime createdAt, File downloadsDir) throws IOException {
        if (!downloadsDir.exists())
            downloadsDir.mkdirs();

        boolean video = source.getName().toLowerCase().endsWith(".mp4");
        String extension = video ? ".mp4" : extensionOf(source);
        LocalDateTime time = createdAt != null ? createdAt
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(source.lastModified()), ZoneId.systemDefault());
        String name = chatName(chatName) + " " + time.format(TIME);
        String filename = name + extension;
        for (int number = 2; new File(downloadsDir, filename).exists(); number++)
            filename = name + " " + number + extension;
        File target = new File(downloadsDir, filename);
        Files.copy(source.toPath(), target.toPath());
        logger.info("Image downloaded to Downloads folder: {}", target.getAbsolutePath());
        return filename;
    }

    /**
     * Gets the chat name as part of a file name: in Unicode NFC with umlauts and other letters kept, without the
     * characters a file system does not allow, the white space collapsed, at most 80 characters; {@code MoodFrame}
     * without a name.
     *
     * @param chatName the name of the chat (may be null)
     * @return the cleaned name
     */
    static String chatName(String chatName) {
        /*  NFC, as macOS hands out names decomposed (NFD) at times and the numbering compares the names  */
        String name = chatName == null ? "" : Normalizer.normalize(chatName, Normalizer.Form.NFC);
        name = FORBIDDEN.matcher(name.replaceAll("\\s", " ")).replaceAll("").replaceAll(" +", " ").trim();

        /*  Never cut a character outside the BMP, like an emoji, in half  */
        if (name.length() > MAX_CHAT_NAME)
            name = name.substring(0, Character.isHighSurrogate(name.charAt(MAX_CHAT_NAME - 1))
                    ? MAX_CHAT_NAME - 1 : MAX_CHAT_NAME).trim();
        return name.isEmpty() ? "MoodFrame" : name;
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
