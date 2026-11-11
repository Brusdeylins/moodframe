package art.moodframe.util;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finds the main colors of an image locally, without any API, by median cut over its pixels.
 *
 * <p>The opaque pixels (at most {@value #SAMPLE_SIDE} per side, taken in a fixed grid) start in one box. The box with
 * the largest product of pixel count and color range is split along its widest channel at the median, moved to the
 * nearest change of value, until there are {@value #MAX_COLORS} boxes or none can be split. The average of each box
 * is one color, the largest box first. The same image always yields the same colors.</p>
 *
 * @since 2.0.0
 */
public final class PaletteExtractor {

    /** The most colors a palette holds. */
    public static final int MAX_COLORS = 5;

    private static final int SAMPLE_SIDE = 200;
    private static final int MIN_ALPHA = 128;

    private PaletteExtractor() {
    }

    /**
     * Extracts up to {@value #MAX_COLORS} main colors of an image.
     *
     * @param image the image
     * @return the colors as {@code #RRGGBB}, the most frequent first; empty for an image without opaque pixels
     */
    public static List<String> extract(BufferedImage image) {
        int step = Math.max(1, (int) Math.ceil(Math.max(image.getWidth(), image.getHeight()) / (double) SAMPLE_SIDE));
        List<int[]> pixels = new ArrayList<>();
        for (int y = 0; y < image.getHeight(); y += step) {
            for (int x = 0; x < image.getWidth(); x += step) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) >= MIN_ALPHA)
                    pixels.add(new int[] { (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF });
            }
        }

        List<List<int[]>> boxes = new ArrayList<>();
        if (!pixels.isEmpty())
            boxes.add(pixels);
        while (boxes.size() < MAX_COLORS) {
            int widest = -1;
            long best = 0;
            for (int i = 0; i < boxes.size(); i++) {
                List<int[]> box = boxes.get(i);
                long score = (long) box.size() * range(box, widestChannel(box));
                if (score > best) {
                    best = score;
                    widest = i;
                }
            }
            if (widest < 0)
                break;

            List<int[]> box = boxes.remove(widest);
            int channel = widestChannel(box);
            box.sort(Comparator.comparingInt((int[] pixel) -> pixel[channel]));

            /*  Cut where the channel value changes, nearest to the median, so a run of one value stays in one box  */
            int median = box.size() / 2;
            int value = box.get(median)[channel];
            int up = median;
            while (up < box.size() && box.get(up)[channel] == value)
                up++;
            int down = median;
            while (down > 0 && box.get(down - 1)[channel] == value)
                down--;
            int cut = up < box.size() && (down == 0 || up - median <= median - down) ? up : down;
            boxes.add(new ArrayList<>(box.subList(0, cut)));
            boxes.add(new ArrayList<>(box.subList(cut, box.size())));
        }

        /*  Boxes with the same average count as one color  */
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (List<int[]> box : boxes)
            counts.merge(average(box), box.size(), Integer::sum);
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey).toList();
    }

    /** The channel (0 red, 1 green, 2 blue) with the largest range of values, the first one on a tie. */
    private static int widestChannel(List<int[]> box) {
        int widest = 0;
        for (int channel = 1; channel < 3; channel++) {
            if (range(box, channel) > range(box, widest))
                widest = channel;
        }
        return widest;
    }

    private static int range(List<int[]> box, int channel) {
        int min = 255;
        int max = 0;
        for (int[] pixel : box) {
            min = Math.min(min, pixel[channel]);
            max = Math.max(max, pixel[channel]);
        }
        return Math.max(0, max - min);
    }

    private static String average(List<int[]> box) {
        long[] sums = new long[3];
        for (int[] pixel : box) {
            for (int channel = 0; channel < 3; channel++)
                sums[channel] += pixel[channel];
        }
        int count = box.size();
        return String.format(Locale.ROOT, "#%02X%02X%02X", Math.round(sums[0] / (double) count),
                Math.round(sums[1] / (double) count), Math.round(sums[2] / (double) count));
    }
}
