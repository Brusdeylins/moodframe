package art.moodframe.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImageSizeTest {

    @TempDir
    Path dir;

    @Test
    void readsTheSizeOfAPngAndAJpeg() throws Exception {
        assertEquals(new ImageSize(160, 90), ImageSize.read(image("png")));
        assertEquals(new ImageSize(160, 90), ImageSize.read(image("jpg")));
    }

    @Test
    void readsTheSizeOfALossyWebp() throws Exception {
        Path file = webp("VP8 ", (header) -> header.put(23, (byte) 0x9D).put(24, (byte) 0x01).put(25, (byte) 0x2A)
                .putShort(26, (short) 2752).putShort(28, (short) 1536));

        assertEquals(new ImageSize(2752, 1536), ImageSize.read(file));
    }

    @Test
    void readsTheSizeOfALosslessWebp() throws Exception {
        Path file = webp("VP8L", (header) -> header.put(20, (byte) 0x2F).putInt(21, 999 | 749 << 14));

        assertEquals(new ImageSize(1000, 750), ImageSize.read(file));
    }

    @Test
    void readsTheCanvasSizeOfAnExtendedWebp() throws Exception {
        Path file = webp("VP8X", (header) -> header.putShort(24, (short) 4095).putShort(27, (short) 2303));

        assertEquals(new ImageSize(4096, 2304), ImageSize.read(file));
    }

    @Test
    void knowsNoSizeOfAMissingFileOrOfOtherBytes() throws Exception {
        assertNull(ImageSize.read(dir.resolve("missing.png")));
        assertNull(ImageSize.read(Files.writeString(dir.resolve("note.png"), "no image")));
        assertNull(ImageSize.read(Files.writeString(dir.resolve("long.png"), "no image, but longer than a header")));
    }

    /** Writes an image of 160 by 90 pixels in a format of ImageIO. */
    private Path image(String format) throws Exception {
        Path file = dir.resolve("image." + format);
        ImageIO.write(new BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB), format, file.toFile());
        return file;
    }

    /** Writes the 30 header bytes of a WebP file whose first chunk is filled in by {@code data}. */
    private Path webp(String chunk, Consumer<ByteBuffer> data) throws Exception {
        ByteBuffer header = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
        header.put(0, "RIFF".getBytes(US_ASCII)).putInt(4, 22).put(8, "WEBP".getBytes(US_ASCII))
                .put(12, chunk.getBytes(US_ASCII));
        data.accept(header);
        return Files.write(dir.resolve(chunk.trim() + ".webp"), header.array());
    }
}
