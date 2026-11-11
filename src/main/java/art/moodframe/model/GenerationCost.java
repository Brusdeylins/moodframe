package art.moodframe.model;

/**
 * The price of a generation, in one place for the send button, the chat entry and the request.
 *
 * @since 1.9.1
 */
public final class GenerationCost {

    private GenerationCost() {
    }

    /**
     * Gets the price per image before the size is known (send button, first estimate of a chat entry).
     *
     * @param model        the model, null for the default model
     * @param videoSeconds the chosen video length, used for video models (may be null)
     * @return the price in dollars; a FLUX.2 image is shown with the price of its first megapixel
     */
    public static double estimate(GenerationModel model, Integer videoSeconds) {
        if (model == null) {
            return GenerationModel.getDefault().getPricePerImage();
        }
        if (model.isVideoModel() && videoSeconds != null) {
            return model.videoPrice(videoSeconds);
        }
        return model.isFlux2Model() ? model.costForMegapixels(1.0) : model.getPricePerImage();
    }

    /**
     * Gets the price per image of a request whose size is known.
     *
     * @param model        the model
     * @param width        the width in pixels (may be null)
     * @param height       the height in pixels (may be null)
     * @param videoSeconds the video length for video models (may be null)
     * @return the price in dollars: FLUX.2 is billed per megapixel, video per second, the others per image
     */
    public static double perImage(GenerationModel model, Integer width, Integer height, Integer videoSeconds) {
        if (model.isFlux2Model() && width != null && height != null && width > 0 && height > 0) {
            return model.costForMegapixels((width * height) / 1_000_000.0);
        }
        if (model.isVideoModel() && videoSeconds != null) {
            return model.videoPrice(videoSeconds);
        }
        return model.isFlux2Model() ? model.costForMegapixels(1.0) : model.getPricePerImage();
    }
}
