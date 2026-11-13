package art.moodframe.util;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * The pixel size of an image file, read from its header without decoding the image: PNG and JPEG through their
 * ImageIO reader, WebP (the JDK has no reader for it) from its RIFF header.
 *
 * @param width  the width in pixels
 * @param height the height in pixels
 * @since 2.0.0
 */
public record ImageSize(int width, int height) {

    /**
     * Reads the pixel size of an image file from its header.
     *
     * @param file the image file
     * @return the size, null if the file is missing or its header cannot be read
     */
    public static ImageSize read(Path file) {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = input != null ? ImageIO.getImageReaders(input) : null;
            if (readers == null || !readers.hasNext())
                return webp(file);
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new ImageSize(reader.getWidth(0), reader.getHeight(0));
            }
            finally {
                reader.dispose();
            }
        }
        catch (IOException e) {
            return null;
        }
    }

    /*  "RIFF", its size, "WEBP", then the first chunk: "VP8 " (lossy), "VP8L" (lossless) or "VP8X" (extended)  */
    private static ImageSize webp(Path file) throws IOException {
        byte[] header = new byte[30];
        try (InputStream input = Files.newInputStream(file)) {
            if (input.readNBytes(header, 0, header.length) < header.length)
                return null;
        }
        if (!"RIFF".equals(text(header, 0)) || !"WEBP".equals(text(header, 8)))
            return null;
        return switch (text(header, 12)) {
            case "VP8 " -> new ImageSize((int) number(header, 26, 2) & 0x3FFF, (int) number(header, 28, 2) & 0x3FFF);
            case "VP8L" -> {
                long bits = number(header, 21, 4);
                yield new ImageSize((int) (bits & 0x3FFF) + 1, (int) (bits >> 14 & 0x3FFF) + 1);
            }
            case "VP8X" -> new ImageSize((int) number(header, 24, 3) + 1, (int) number(header, 27, 3) + 1);
            default -> null;
        };
    }

    /*  The four ASCII characters at an offset  */
    private static String text(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }

    /*  The unsigned little-endian number of 1 to 4 bytes at an offset  */
    private static long number(byte[] bytes, int offset, int length) {
        long value = 0;
        for (int i = length - 1; i >= 0; i--)
            value = value << 8 | bytes[offset + i] & 0xFF;
        return value;
    }
}
