package art.moodframe.util;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the local palette extraction: main colors by frequency, deterministic, without transparent pixels. */
class PaletteExtractorTest {

    /** An image of horizontal stripes, each color with the given number of rows of 10 pixels. */
    private static BufferedImage stripes(int[] colors, int[] rows) {
        int height = 0;
        for (int count : rows)
            height += count;
        BufferedImage image = new BufferedImage(10, height, BufferedImage.TYPE_INT_ARGB);
        int y = 0;
        for (int i = 0; i < colors.length; i++) {
            for (int row = 0; row < rows[i]; row++, y++) {
                for (int x = 0; x < 10; x++)
                    image.setRGB(x, y, colors[i]);
            }
        }
        return image;
    }

    @Test
    void findsTheDistinctColorsTheMostFrequentFirst() {
        BufferedImage image = stripes(new int[] { 0xFFFF0000, 0xFF0000FF, 0xFF00FF00, 0xFFFFFFFF },
                new int[] { 60, 20, 12, 8 });

        assertEquals(List.of("#FF0000", "#0000FF", "#00FF00", "#FFFFFF"), PaletteExtractor.extract(image));
    }

    @Test
    void sameImageGivesTheSameColors() {
        BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++)
                image.setRGB(x, y, (x * 7 % 256) << 16 | (y * 13 % 256) << 8 | (x + y) % 256);
        }

        List<String> first = PaletteExtractor.extract(image);
        assertEquals(PaletteExtractor.MAX_COLORS, first.size());
        assertEquals(first, PaletteExtractor.extract(image));
        assertTrue(first.stream().allMatch((String color) -> color.matches("#[0-9A-F]{6}")));
    }

    @Test
    void oneColorGivesOneColor() {
        assertEquals(List.of("#336699"), PaletteExtractor.extract(stripes(new int[] { 0xFF336699 }, new int[] { 5 })));
    }

    @Test
    void transparentPixelsDoNotCount() {
        BufferedImage image = stripes(new int[] { 0x00FF0000, 0xFF00FF00 }, new int[] { 90, 10 });

        assertEquals(List.of("#00FF00"), PaletteExtractor.extract(image));
        assertEquals(List.of(), PaletteExtractor.extract(stripes(new int[] { 0x00000000 }, new int[] { 3 })));
    }

    @Test
    void manyColorsGiveAtMostFive() {
        BufferedImage image = stripes(
                new int[] { 0xFF000000, 0xFFFFFFFF, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00, 0xFF00FFFF },
                new int[] { 10, 10, 10, 10, 10, 10, 10 });

        assertEquals(PaletteExtractor.MAX_COLORS, PaletteExtractor.extract(image).size());
    }
}
