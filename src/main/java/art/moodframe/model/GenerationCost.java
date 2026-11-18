package art.moodframe.model;

/**
 * The price of a generation, in one place for the send button, the MCP preview, the chat entry and the stored image:
 * the price per image of the model, Google Search grounding and thinking tokens of Gemini, and the models without a
 * known price (OpenAI).
 *
 * @since 1.9.1
 */
public final class GenerationCost {

    /** The price of Google Search grounding of one Gemini image in USD. */
    public static final double SEARCH_PER_IMAGE = 0.03;

    /** The price of thinking (reasoning) tokens in USD per million tokens. */
    public static final double REASONING_PER_MILLION_TOKENS = 12.0;

    /**
     * The estimated price of an order before it is sent.
     *
     * @param perImage the price of one image (video: of the video) in USD, Google Search included; 0 if unknown
     * @param total    the price of all images in USD; 0 if unknown
     * @param known    false if the provider has no known price (OpenAI), the amounts are 0 then
     */
    public record Quote(double perImage, double total, boolean known) {
    }

    private GenerationCost() {
    }

    /**
     * Estimates the price of an order before the size is known: the price per image of the model plus Google Search
     * grounding, times the number of images. Thinking tokens are unknown before the answer and not included. This is
     * the rule of the send button, the MCP preview and the first cost of a chat entry.
     *
     * @param model        the model, null for the default model
     * @param videoSeconds the chosen video length, used for video models (may be null)
     * @param googleSearch whether Google Search grounding is switched on (may be null)
     * @param imageCount   the number of images
     * @return the estimate
     */
    public static Quote quote(GenerationModel model, Integer videoSeconds, Boolean googleSearch, int imageCount) {
        if (!isKnown(model))
            return new Quote(0.0, 0.0, false);
        double perImage = estimate(model, videoSeconds) + search(model, googleSearch);
        return new Quote(perImage, perImage * imageCount, true);
    }

    /**
     * Checks if the price of a model is known: not for the models of OpenAI and its compatible gateways.
     *
     * @param model the model, null for the default model
     * @return true if the price is known
     */
    public static boolean isKnown(GenerationModel model) {
        return model == null || model.getProvider() != ApiProvider.OPENAI;
    }

    /**
     * Gets the price of Google Search grounding of one image: only a Gemini image model grounds.
     *
     * @param model        the model (may be null)
     * @param googleSearch whether Google Search grounding is switched on (may be null)
     * @return the price in USD, 0 without grounding
     */
    public static double search(GenerationModel model, Boolean googleSearch) {
        return model != null && model.supportsGoogleSearch() && Boolean.TRUE.equals(googleSearch)
                ? SEARCH_PER_IMAGE : 0.0;
    }

    /**
     * Gets the price of the thinking tokens of one image.
     *
     * @param thinkingTokens the thinking tokens the provider reported (may be null)
     * @return the price in USD, 0 without tokens
     */
    public static double reasoning(Integer thinkingTokens) {
        return thinkingTokens != null && thinkingTokens > 0
                ? thinkingTokens / 1_000_000.0 * REASONING_PER_MILLION_TOKENS : 0.0;
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
