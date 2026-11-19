package art.moodframe.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a request for image or video generation, independent of the provider.
 *
 * <p>This class encapsulates all parameters needed to generate images
 * including the prompt, model selection, dimensions, reference images
 * and generation settings; each provider client builds the body of its API from it.</p>
 * 
 * @since 1.0.0
 */
public class ImageGenerationRequest {

    /** The most images one request makes. */
    public static final int MAX_IMAGE_COUNT = 4;

    private String prompt;
    
    // Note: Model is determined by endpoint, not a request parameter
    
    private Integer width;
    
    private Integer height;
    
    private String outputFormat;
    
    private Integer safetyTolerance;
    
    private Boolean promptUpsampling;
    
    private Long seed;
    
    private String aspectRatio;
    
    // Image count (handled at client level, not API level) - NOT part of BFL API
    private Integer imageCount = 1;
    
    // Model identifier (used for endpoint selection, not sent in request)
    private String model;
    
    // Style preset identifier (for UI tracking, not sent in request)
    private String stylePreset;
    
    // Reference images in Base64 (FLUX.2 up to 8, FLUX 3 up to 10, Gemini up to 14); each client maps them to
    // the wire format of its API
    private final List<String> inputImages = new ArrayList<>();

    // Gemini-specific fields (not used for BFL)
    private String imageSize; // "1K", "2K", "4K" (Gemini only)

    // Gemini Phase 2 Advanced Features
    private Boolean enableGoogleSearch; // Google Search grounding (adds ~$0.03 per request)
    private String thinkingMode; // "off", "low", "high" - unified for both Gemini 2.5 and 3

    // FLUX.2 specific parameters
    private Double guidance; // FLUX.2 [flex] only: 1.5-10.0, controls prompt adherence

    private Integer steps; // FLUX.2 [flex] only: 1-50, inference steps

    // Note: grounding_search parameter was removed as it does not exist in the BFL API
    // The CLAUDE.md documentation incorrectly listed this feature for FLUX.2 [max]

    // Video-specific parameters (Gemini Omni)
    private String videoResolution;     // "360p", "720p", "1080p", "4k"
    private Integer videoDuration;      // 3 to 10 seconds

    // Cost tracking - actual model base cost (not sent to API, for internal tracking)
    private Double baseCostPerImage; // The actual model's base generation cost

    /**
     * Default constructor.
     */
    public ImageGenerationRequest() {}
    
    /**
     * Creates a new image generation request with the specified prompt and model.
     * 
     * @param prompt the text prompt for image generation
     * @param model the FLUX model to use for endpoint selection (e.g., "flux-dev")
     */
    public ImageGenerationRequest(String prompt, String model) {
        this.prompt = prompt;
        this.model = model;
        this.outputFormat = "png"; // Default format - always PNG
        this.width = 1024; // Default width
        this.height = 1024; // Default height
        this.promptUpsampling = true; // Default upsampling enabled for variety
    }
    
    /**
     * Gets the text prompt for image generation.
     * 
     * @return the prompt text
     */
    public String getPrompt() {
        return prompt;
    }
    
    /**
     * Sets the text prompt for image generation.
     * 
     * @param prompt the prompt text (max 3000 characters)
     */
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
    
    /**
     * Gets the FLUX model to use for generation.
     * 
     * @return the model identifier
     */
    public String getModel() {
        return model;
    }
    
    /**
     * Sets the FLUX model to use for generation.
     * 
     * @param model the model identifier (e.g., "flux.3-image", "flux.2-pro")
     */
    public void setModel(String model) {
        this.model = model;
    }
    
    /**
     * Gets the image width in pixels.
     * 
     * @return the width, or null if using aspect ratio
     */
    public Integer getWidth() {
        return width;
    }
    
    /**
     * Sets the image width in pixels.
     * 
     * @param width the width (must be multiple of 32, max 2048)
     */
    public void setWidth(Integer width) {
        this.width = width;
    }
    
    /**
     * Gets the image height in pixels.
     * 
     * @return the height, or null if using aspect ratio
     */
    public Integer getHeight() {
        return height;
    }
    
    /**
     * Sets the image height in pixels.
     * 
     * @param height the height (must be multiple of 32, max 2048)
     */
    public void setHeight(Integer height) {
        this.height = height;
    }
    
    /**
     * Gets the output format.
     * 
     * @return the format ("jpeg" or "png")
     */
    public String getOutputFormat() {
        return outputFormat;
    }
    
    /**
     * Sets the output format.
     * 
     * @param outputFormat the format ("jpeg" or "png")
     */
    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }
    
    /**
     * Gets the safety tolerance level.
     * 
     * @return the safety tolerance (0-6, higher = more permissive)
     */
    public Integer getSafetyTolerance() {
        return safetyTolerance;
    }
    
    /**
     * Sets the safety tolerance level.
     * 
     * @param safetyTolerance the tolerance level (0-6)
     */
    public void setSafetyTolerance(Integer safetyTolerance) {
        this.safetyTolerance = safetyTolerance;
    }
    
    /**
     * Gets whether prompt upsampling is enabled.
     * 
     * @return true if prompt upsampling is enabled
     */
    public Boolean getPromptUpsampling() {
        return promptUpsampling;
    }
    
    /**
     * Sets whether to enable prompt upsampling.
     * 
     * @param promptUpsampling true to enable prompt upsampling
     */
    public void setPromptUpsampling(Boolean promptUpsampling) {
        this.promptUpsampling = promptUpsampling;
    }
    
    /**
     * Gets the random seed for reproducible generation.
     * 
     * @return the seed value, or null for random
     */
    public Long getSeed() {
        return seed;
    }
    
    /**
     * Sets the random seed for reproducible generation.
     * 
     * @param seed the seed value (null for random)
     */
    public void setSeed(Long seed) {
        this.seed = seed;
    }
    
    /**
     * Gets the aspect ratio (FLUX 3 sends it as {@code aspect_ratio}).
     * 
     * @return the aspect ratio (e.g., "16:9", "1:1")
     */
    public String getAspectRatio() {
        return aspectRatio;
    }
    
    /**
     * Sets the aspect ratio (FLUX 3 sends it as {@code aspect_ratio}).
     * 
     * @param aspectRatio the aspect ratio (e.g., "16:9", "1:1", "3:7" to "7:3")
     */
    public void setAspectRatio(String aspectRatio) {
        this.aspectRatio = aspectRatio;
    }
    
    /**
     * Gets the number of images to generate.
     * 
     * @return the image count (1-4)
     */
    public Integer getImageCount() {
        return imageCount;
    }
    
    /**
     * Sets the number of images to generate.
     * 
     * @param imageCount the count (1-4)
     */
    public void setImageCount(Integer imageCount) {
        this.imageCount = imageCount;
    }
    
    /**
     * Gets the style preset identifier.
     * 
     * @return the style preset identifier, or null if none
     */
    public String getStylePreset() {
        return stylePreset;
    }
    
    /**
     * Sets the style preset identifier.
     * 
     * @param stylePreset the style preset identifier
     */
    public void setStylePreset(String stylePreset) {
        this.stylePreset = stylePreset;
    }
    
    /**
     * Gets the first input image for image-to-image editing.
     *
     * @return the Base64 encoded input image, or null if not using image input
     */
    public String getInputImage() {
        return inputImages.isEmpty() ? null : inputImages.get(0);
    }

    /**
     * Sets the first input image for image-to-image editing, keeping the others.
     * This should be a Base64 encoded image string.
     *
     * @param inputImage the Base64 encoded image data, null or empty to remove the first image
     */
    public void setInputImage(String inputImage) {
        boolean present = inputImage != null && !inputImage.isEmpty();
        if (inputImages.isEmpty()) {
            if (present)
                inputImages.add(inputImage);
        } else if (present) {
            inputImages.set(0, inputImage);
        } else {
            inputImages.remove(0);
        }
    }

    /**
     * Checks if this request includes an image attachment.
     *
     * @return true if an input image is attached
     */
    public boolean hasInputImage() {
        String first = getInputImage();
        return first != null && !first.trim().isEmpty();
    }

    /**
     * Gets the input images in their order.
     *
     * @return the Base64 encoded images, empty without; not modifiable
     */
    public List<String> getInputImages() {
        return Collections.unmodifiableList(inputImages);
    }

    /**
     * Sets the input images from a list of Base64-encoded strings, replacing the ones set before; a null or empty
     * list keeps them, null and empty strings are left out.
     *
     * @param images list of Base64-encoded image strings (max 14 for Gemini, max 8 for FLUX.2)
     */
    public void setInputImages(List<String> images) {
        if (images == null || images.isEmpty()) return;
        inputImages.clear();
        for (String image : images)
            if (image != null && !image.isEmpty())
                inputImages.add(image);
    }

    /**
     * Gets the count of attached input images.
     *
     * @return number of input images (0-14)
     */
    public int getInputImageCount() {
        return inputImages.size();
    }

    /**
     * Gets the image size for Gemini API.
     *
     * @return the image size ("1K", "2K", "4K")
     */
    public String getImageSize() {
        return imageSize;
    }

    /**
     * Sets the image size for Gemini API.
     *
     * @param imageSize the image size ("1K", "2K", "4K")
     */
    public void setImageSize(String imageSize) {
        this.imageSize = imageSize;
    }

    /**
     * Gets whether Google Search grounding is enabled for Gemini API.
     *
     * @return true if Google Search grounding is enabled
     */
    public Boolean getEnableGoogleSearch() {
        return enableGoogleSearch;
    }

    /**
     * Sets whether to enable Google Search grounding for Gemini API.
     * Adds approximately $0.03 per request.
     *
     * @param enableGoogleSearch true to enable Google Search grounding
     */
    public void setEnableGoogleSearch(Boolean enableGoogleSearch) {
        this.enableGoogleSearch = enableGoogleSearch;
    }

    /**
     * Gets the thinking/reasoning mode for Gemini API.
     *
     * @return the thinking mode ("off", "low", "high")
     */
    public String getThinkingMode() {
        return thinkingMode;
    }

    /**
     * Sets the thinking/reasoning mode for Gemini API.
     * - "off": Disable thinking (Gemini 2.5 Flash only)
     * - "low": Low reasoning quality (faster, cheaper)
     * - "high": High reasoning quality (better results, more expensive)
     *
     * @param thinkingMode the thinking mode
     */
    public void setThinkingMode(String thinkingMode) {
        this.thinkingMode = thinkingMode;
    }

    /**
     * Gets the base cost per image for this generation request.
     *
     * @return the base model cost per image in USD
     */
    public Double getBaseCostPerImage() {
        return baseCostPerImage;
    }

    /**
     * Sets the base cost per image for this generation request.
     *
     * @param baseCostPerImage the base model cost per image in USD
     */
    public void setBaseCostPerImage(Double baseCostPerImage) {
        this.baseCostPerImage = baseCostPerImage;
    }

    // ===== FLUX.2 Parameters =====

    /**
     * Gets the guidance parameter for FLUX.2 [flex].
     * Controls how strongly the model follows the prompt.
     *
     * @return the guidance value (1.5-10.0), or null if not set
     */
    public Double getGuidance() {
        return guidance;
    }

    /**
     * Sets the guidance parameter for FLUX.2 [flex].
     * Higher values mean stronger prompt adherence.
     *
     * @param guidance the guidance value (1.5-10.0)
     */
    public void setGuidance(Double guidance) {
        this.guidance = guidance;
    }

    /**
     * Gets the steps parameter for FLUX.2 [flex].
     * Controls inference quality.
     *
     * @return the steps value (1-50), or null if not set
     */
    public Integer getSteps() {
        return steps;
    }

    /**
     * Sets the steps parameter for FLUX.2 [flex].
     * Higher values produce more detailed images.
     *
     * @param steps the steps value (1-50)
     */
    public void setSteps(Integer steps) {
        this.steps = steps;
    }

    // Note: getGroundingSearch() and setGroundingSearch() were removed
    // The grounding_search parameter does not exist in the BFL API

    // ===== Video Parameters =====

    public String getVideoResolution() { return videoResolution; }
    public void setVideoResolution(String videoResolution) { this.videoResolution = videoResolution; }

    public Integer getVideoDuration() { return videoDuration; }
    public void setVideoDuration(Integer videoDuration) { this.videoDuration = videoDuration; }

    @Override
    public String toString() {
        return String.format("ImageGenerationRequest{prompt='%s', model='%s', size=%dx%d, format='%s', count=%d, hasInputImage=%s}", 
            prompt != null ? prompt.substring(0, Math.min(50, prompt.length())) + "..." : null, 
            model, width, height, outputFormat, imageCount, hasInputImage());
    }
}