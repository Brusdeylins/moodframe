package art.moodframe.model;

/**
 * Unified interface for all image generation models across different providers.
 * This interface allows handling BFL FLUX models and Google Gemini models uniformly.
 *
 * @author Matthias Brusdeylins
 * @since 1.5.0
 */
public interface UnifiedModel {

    /**
     * Gets the display name of this model.
     *
     * @return the display name
     */
    String getDisplayName();

    /**
     * Gets the localized display name of this model.
     *
     * @return the localized display name
     */
    String getLocalizedDisplayName();

    /**
     * Gets the API model identifier used in API requests.
     *
     * @return the API model ID
     */
    String getApiModelId();

    /**
     * Gets the price per generated image for this model.
     *
     * @return the price per image in USD
     */
    double getPricePerImage();

    /**
     * Gets the API provider for this model.
     *
     * @return the API provider
     */
    ApiProvider getProvider();

    /**
     * Gets a description of this model.
     *
     * @return the model description
     */
    String getDescription();
}
