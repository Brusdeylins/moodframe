package art.moodframe.model;

/**
 * The pixel size of an image request: the largest size of the aspect ratio that the model allows, in the
 * alignment the BFL API needs.
 *
 * @since 1.9.1
 */
public final class ImageDimensions {

    private ImageDimensions() {
    }

    /**
     * Gets the alignment of the dimensions: FLUX.2 uses multiples of 16, FLUX.1 (and the default) of 32.
     *
     * @param model the model (may be null)
     * @return 16 or 32
     */
    public static int multipleFor(GenerationModel model) {
        return model != null && model.isFlux2Model() ? 16 : 32;
    }

    /**
     * Gets the dimensions of an aspect ratio.
     *
     * @param aspectRatio the ratio like {@code 16:9}; anything unreadable counts as square
     * @param maxValue    the largest allowed width or height
     * @param multiple    the alignment (16 for FLUX.2, 32 for FLUX.1)
     * @return {@code [width, height]}
     */
    public static int[] forAspectRatio(String aspectRatio, int maxValue, int multiple) {
        int[] ratio = parse(aspectRatio);
        return ratio == null ? optimal(1, 1, maxValue, multiple) : optimal(ratio[0], ratio[1], maxValue, multiple);
    }

    /**
     * Parses aspect ratio string (e.g., "16:9") and returns [width_ratio,
     * height_ratio].
     */
    static int[] parse(String aspectRatio) {
        if (aspectRatio == null || !aspectRatio.contains(":")) {
            return null;
        }

        String[] parts = aspectRatio.split(":");
        if (parts.length != 2) {
            return null;
        }

        try {
            int widthRatio = Integer.parseInt(parts[0].trim());
            int heightRatio = Integer.parseInt(parts[1].trim());
            return new int[] { widthRatio, heightRatio };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Calculates optimal dimensions for given aspect ratio and maximum resolution.
     * Returns [width, height] that are multiples of the specified alignment and respect API limits.
     *
     * @param aspectWidth  width component of aspect ratio
     * @param aspectHeight height component of aspect ratio
     * @param maxValue     maximum allowed width or height (1440 for standard, 2048 for Ultra/FLUX.2)
     * @param multiple     dimension alignment (16 for FLUX.2, 32 for FLUX.1)
     * @return [width, height] as multiples of the alignment
     */
    static int[] optimal(int aspectWidth, int aspectHeight, int maxValue, int multiple) {
        // SIMPLE AND CORRECT APPROACH:
        // 1. Start with the maximum allowed dimension
        // 2. Calculate the other dimension based on aspect ratio
        // 3. If either exceeds maxValue, scale down
        // 4. Round to multiples of 16 (FLUX.2) or 32 (FLUX.1)
        // 5. Ensure minimum 64px (FLUX.2) or 256px (FLUX.1)

        double aspectRatio = (double) aspectWidth / aspectHeight;
        int width, height;
        int minDimension = (multiple == 16) ? 64 : 256; // FLUX.2: min 64, FLUX.1: min 256

        if (aspectRatio >= 1.0) {
            // Landscape or square - start with max width
            width = maxValue;
            height = (int) (width / aspectRatio);

            // If height exceeds limit, recalculate from height
            if (height > maxValue) {
                height = maxValue;
                width = (int) (height * aspectRatio);
            }
        } else {
            // Portrait - start with max height
            height = maxValue;
            width = (int) (height * aspectRatio);

            // If width exceeds limit, recalculate from width
            if (width > maxValue) {
                width = maxValue;
                height = (int) (width / aspectRatio);
            }
        }

        // Round down to multiples of the alignment (16 for FLUX.2, 32 for FLUX.1)
        width = (width / multiple) * multiple;
        height = (height / multiple) * multiple;

        // Ensure minimum dimensions
        if (width < minDimension)
            width = minDimension;
        if (height < minDimension)
            height = minDimension;

        // Final safety check - NEVER exceed maxValue
        if (width > maxValue)
            width = ((maxValue / multiple) * multiple);
        if (height > maxValue)
            height = ((maxValue / multiple) * multiple);


        return new int[] { width, height };
    }
}
