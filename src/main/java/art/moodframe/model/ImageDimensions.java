package art.moodframe.model;

/**
 * The pixel size of an image request: the largest size of the aspect ratio that the model allows, in the
 * alignment the BFL API needs. FLUX.2 has sides of its own; FLUX 3 and an unknown model (null) take the sides of the
 * former FLUX models.
 *
 * @since 1.9.1
 */
public final class ImageDimensions {

    /*  the largest side of the former FLUX models, for an unknown model  */
    private static final int FORMER_FLUX_MAX = 2048;

    private ImageDimensions() {
    }

    /**
     * Gets the alignment of the dimensions: FLUX.2 uses multiples of 16, the other models of 32.
     *
     * @param model the model (may be null)
     * @return 16 or 32
     */
    public static int multipleFor(GenerationModel model) {
        return model != null && model.isFlux2Model() ? 16 : 32;
    }

    /**
     * Gets the smallest width or height a model sized by width and height takes: FLUX.2 64, the other models 256.
     *
     * @param model the model (may be null)
     * @return 64 or 256
     */
    public static int minimumFor(GenerationModel model) {
        return model != null && model.isFlux2Model() ? 64 : 256;
    }

    /**
     * Gets the largest width or height a model sized by width and height takes: its
     * {@link GenerationModel#getMaxResolution() resolution limit}, 2048 for a former FLUX model, which is no constant.
     *
     * @param model the model (may be null)
     * @return the side in pixels
     */
    public static int maximumFor(GenerationModel model) {
        return model != null ? model.getMaxResolution() : FORMER_FLUX_MAX;
    }

    /**
     * Gets the dimensions of an aspect ratio for a model: within its {@link #minimumFor smallest} and
     * {@link #maximumFor largest} side, in its {@link #multipleFor alignment}.
     *
     * @param aspectRatio the ratio like {@code 16:9}; anything unreadable counts as square
     * @param model       the model (may be null)
     * @return {@code [width, height]}
     */
    public static int[] forAspectRatio(String aspectRatio, GenerationModel model) {
        int[] ratio = parse(aspectRatio);
        int[] sides = ratio != null ? ratio : new int[] { 1, 1 };
        return optimal(sides[0], sides[1], maximumFor(model), multipleFor(model), minimumFor(model));
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
     * @param maxValue     maximum allowed width or height (see {@link #maximumFor})
     * @param multiple     dimension alignment (see {@link #multipleFor})
     * @param minDimension minimum allowed width or height (see {@link #minimumFor})
     * @return [width, height] as multiples of the alignment
     */
    static int[] optimal(int aspectWidth, int aspectHeight, int maxValue, int multiple, int minDimension) {
        // SIMPLE AND CORRECT APPROACH:
        // 1. Start with the maximum allowed dimension
        // 2. Calculate the other dimension based on aspect ratio
        // 3. If either exceeds maxValue, scale down
        // 4. Round to multiples of the alignment
        // 5. Ensure the minimum side

        double aspectRatio = (double) aspectWidth / aspectHeight;
        int width, height;

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

        // Round down to multiples of the alignment
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
