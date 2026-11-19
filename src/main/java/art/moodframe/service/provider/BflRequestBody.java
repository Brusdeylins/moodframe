package art.moodframe.service.provider;

import art.moodframe.model.ImageGenerationRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The wire format of a BFL generation request: the fields FLUX 3 and FLUX.2 accept, in the order they are sent. Both
 * reject unknown fields with HTTP 422, so each model family gets only its own fields. The reference images of the
 * request become the list {@code images} of FLUX 3 and the numbered fields {@code input_image},
 * {@code input_image_2} to {@code input_image_8} of FLUX.2.
 *
 * @since 2.1.3
 */
final class BflRequestBody {

    private static final Logger logger = LoggerFactory.getLogger(BflRequestBody.class);

    /** The most reference images FLUX.2 takes. */
    static final int FLUX2_MAX_IMAGES = 8;

    /** The most reference images FLUX 3 takes. */
    static final int FLUX3_MAX_IMAGES = 10;

    private BflRequestBody() {
    }

    /**
     * Builds the JSON body of a request.
     *
     * @param request the request, {@link ImageGenerationRequest#getModel()} is the API model id
     * @param mapper  writes the JSON
     * @return the JSON body
     * @throws JsonProcessingException  if the body cannot be written
     * @throws IllegalArgumentException if the model is no FLUX 3 or FLUX.2 model
     */
    static String json(ImageGenerationRequest request, ObjectMapper mapper) throws JsonProcessingException {
        String model = request.getModel();
        String lower = model != null ? model.toLowerCase() : "";
        if (lower.startsWith("flux.3-"))
            return mapper.writeValueAsString(flux3(request, lower.equals("flux.3-image-klein")));
        if (lower.startsWith("flux.2-")) {
            Map<String, Object> body = flux2(request, lower);
            if (logger.isDebugEnabled()) {
                logger.debug("FLUX.2 request body: prompt='{}...', width={}, height={}, safety={}, images={}",
                    request.getPrompt().substring(0, Math.min(50, request.getPrompt().length())),
                    request.getWidth(), request.getHeight(), request.getSafetyTolerance(),
                    images(request, FLUX2_MAX_IMAGES).size());
            }
            return mapper.writeValueAsString(body);
        }
        throw new IllegalArgumentException("Unsupported BFL model: " + model);
    }

    /*  FLUX 3 (OpenAPI Flux3ImageInputs, Klein adds seed and output_format): size by resolution tier, images as one
        list, no width/height, upsampling or input_image fields  */
    private static Map<String, Object> flux3(ImageGenerationRequest request, boolean klein) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", request.getPrompt());
        if (request.getAspectRatio() != null)
            body.put("aspect_ratio", request.getAspectRatio());
        if (request.getImageSize() != null)
            body.put("resolution", request.getImageSize());
        if (request.getSafetyTolerance() != null)
            body.put("safety_tolerance", request.getSafetyTolerance());
        if (klein && request.getSeed() != null)
            body.put("seed", request.getSeed());
        if (klein && request.getOutputFormat() != null)
            body.put("output_format", request.getOutputFormat());

        List<String> images = images(request, FLUX3_MAX_IMAGES);
        if (!images.isEmpty())
            body.put("images", images);

        logger.debug("FLUX 3 request body: model={}, resolution={}, aspect_ratio={}, safety={}, images={}",
            request.getModel(), request.getImageSize(), request.getAspectRatio(), request.getSafetyTolerance(),
            images.size());
        return body;
    }

    /*  FLUX.2: no aspect_ratio, raw or grounding_search; guidance and steps only for [flex]  */
    private static Map<String, Object> flux2(ImageGenerationRequest request, String lower) {
        boolean flex = lower.equals("flux.2-flex");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", request.getPrompt());
        if (request.getWidth() != null)
            body.put("width", request.getWidth());
        if (request.getHeight() != null)
            body.put("height", request.getHeight());
        if (request.getSeed() != null)
            body.put("seed", request.getSeed());
        if (request.getSafetyTolerance() != null)
            body.put("safety_tolerance", request.getSafetyTolerance());
        if (request.getOutputFormat() != null)
            body.put("output_format", request.getOutputFormat());

        // Prompt upsampling (OpenAPI): [pro] and [max] upsample by default and only offer the opt-out
        // disable_pup, [flex] has prompt_upsampling (default true), [klein] has neither
        if (request.getPromptUpsampling() != null) {
            if (flex)
                body.put("prompt_upsampling", request.getPromptUpsampling());
            else if (!lower.startsWith("flux.2-klein"))
                body.put("disable_pup", !request.getPromptUpsampling());
        }

        if (flex) {
            if (request.getGuidance() != null)
                body.put("guidance", request.getGuidance());
            if (request.getSteps() != null)
                body.put("steps", request.getSteps());
        }

        List<String> images = images(request, FLUX2_MAX_IMAGES);
        for (int i = 0; i < images.size(); i++) {
            String field = i == 0 ? "input_image" : "input_image_" + (i + 1);
            body.put(field, images.get(i));
            logger.debug("FLUX.2 {}: {} chars", field, images.get(i).length());
        }
        return body;
    }

    /*  the first images of the request, as many as the model takes  */
    private static List<String> images(ImageGenerationRequest request, int max) {
        List<String> images = request.getInputImages();
        return images.size() > max ? images.subList(0, max) : images;
    }
}
