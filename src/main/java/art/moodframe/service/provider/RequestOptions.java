package art.moodframe.service.provider;

import java.util.List;

/**
 * What an order asks of a request beyond prompt, size and the general parameters, for
 * {@link ImageProvider#prepare(art.moodframe.model.ImageGenerationRequest, art.moodframe.model.GenerationModel,
 * RequestOptions)}: each provider takes what its model knows.
 *
 * @param item                the dropdown item of the model (an OpenAI-compatible gateway: its model id)
 * @param targetResolution    the resolution of the target, for example {@code 2K} (may be null)
 * @param fluxSafetyTolerance the safety tolerance of FLUX.2 and FLUX 3
 * @param googleSearch        whether Gemini grounds the image in a Google Search (may be null)
 * @param thinkingLevel       the thinking level of Nano Banana 2 and Lite (may be null)
 * @param guidance            the guidance of FLUX.2 [flex] (may be null)
 * @param steps               the steps of FLUX.2 [flex] (may be null)
 * @param videoSeconds        the video length, null for 8 seconds
 * @param referenceImages     the reference images in Base64, not null
 * @since 2.1.3
 */
public record RequestOptions(String item, String targetResolution, int fluxSafetyTolerance, Boolean googleSearch,
        String thinkingLevel, Double guidance, Integer steps, Integer videoSeconds, List<String> referenceImages) {
}
