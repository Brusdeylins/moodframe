package tools.moodframe.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Represents a request for image generation via the BFL API.
 * 
 * <p>This class encapsulates all parameters needed to generate images
 * using FLUX models including the prompt, model selection, dimensions,
 * and generation settings.</p>
 * 
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageGenerationRequest {
    
    @JsonProperty("prompt")
    private String prompt;
    
    // Note: Model is determined by endpoint, not a request parameter
    
    @JsonProperty("width")
    private Integer width;
    
    @JsonProperty("height")
    private Integer height;
    
    @JsonProperty("output_format")
    private String outputFormat;
    
    @JsonProperty("safety_tolerance")
    private Integer safetyTolerance;
    
    @JsonProperty("prompt_upsampling")
    private Boolean promptUpsampling;
    
    @JsonProperty("seed")
    private Long seed;
    
    @JsonProperty("aspect_ratio")
    private String aspectRatio;
    
    // For FLUX Pro Ultra
    @JsonProperty("raw")
    private Boolean raw;
    
    // Image count (handled at client level, not API level) - NOT part of BFL API
    private Integer imageCount = 1;
    
    // Model identifier (used for endpoint selection, not sent in request)
    private String model;
    
    // Style preset identifier (for UI tracking, not sent in request)
    private String stylePreset;
    
    // Image attachment for image-to-image editing (only for compatible models)
    @JsonProperty("input_image")
    private String inputImage; // Base64 encoded image data

    // FLUX.2 Multi-Image Support (up to 8 images total)
    @JsonProperty("input_image_2")
    private String inputImage2;

    @JsonProperty("input_image_3")
    private String inputImage3;

    @JsonProperty("input_image_4")
    private String inputImage4;

    @JsonProperty("input_image_5")
    private String inputImage5;

    @JsonProperty("input_image_6")
    private String inputImage6;

    @JsonProperty("input_image_7")
    private String inputImage7;

    @JsonProperty("input_image_8")
    private String inputImage8;

    // Additional images for Gemini (supports up to 14 total)
    private String inputImage9;
    private String inputImage10;
    private String inputImage11;
    private String inputImage12;
    private String inputImage13;
    private String inputImage14;

    // Gemini-specific fields (not used for BFL)
    private List<String> responseModalities; // e.g., ["IMAGE"] or ["TEXT", "IMAGE"]
    private String imageSize; // "1K", "2K", "4K" (Gemini only)

    // Gemini Phase 2 Advanced Features
    private Boolean enableGoogleSearch; // Google Search grounding (adds ~$0.03 per request)
    private String thinkingMode; // "off", "low", "high" - unified for both Gemini 2.5 and 3
    private Boolean includeThoughts; // Return AI reasoning summary

    // FLUX.2 specific parameters
    @JsonProperty("guidance")
    private Double guidance; // FLUX.2 [flex] only: 1.5-10.0, controls prompt adherence

    @JsonProperty("steps")
    private Integer steps; // FLUX.2 [flex] only: 1-50, inference steps

    // Note: grounding_search parameter was removed as it does not exist in the BFL API
    // The CLAUDE.md documentation incorrectly listed this feature for FLUX.2 [max]

    // Video-specific parameters (Gemini Omni)
    private String videoResolution;     // "360p", "720p", "1080p", "4k"
    private Integer videoDuration;      // 3 to 10 seconds
    private String negativePrompt;      // What NOT to generate
    private Boolean generateAudio;      // Enable native audio generation (default: false)
    private String personGeneration;    // "dont_allow" or "allow_adult"

    // Video-to-image source (gemini-3.1-flash-image only): a YouTube video passed
    // directly as multimodal context to generate thumbnails/posters/infographics.
    private String videoSourceUrl;           // YouTube URL used as file_data context
    private Integer videoStartOffsetSeconds; // optional start offset (seconds)
    private Integer videoEndOffsetSeconds;   // optional end offset (seconds, 0/null = end)

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
     * Gets whether to use raw mode (FLUX Pro Ultra only).
     * 
     * @return true for raw mode, false or null for standard
     */
    public Boolean getRaw() {
        return raw;
    }
    
    /**
     * Sets whether to use raw mode (FLUX Pro Ultra only).
     * 
     * @param raw true for raw mode, false for standard
     */
    public void setRaw(Boolean raw) {
        this.raw = raw;
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
     * Gets the input image for image-to-image editing.
     * 
     * @return the Base64 encoded input image, or null if not using image input
     */
    public String getInputImage() {
        return inputImage;
    }
    
    /**
     * Sets the input image for image-to-image editing.
     * This should be a Base64 encoded image string.
     * 
     * @param inputImage the Base64 encoded image data
     */
    public void setInputImage(String inputImage) {
        this.inputImage = inputImage;
    }
    
    /**
     * Checks if this request includes an image attachment.
     *
     * @return true if an input image is attached
     */
    public boolean hasInputImage() {
        return inputImage != null && !inputImage.trim().isEmpty();
    }

    // ===== FLUX.2 Multi-Image Getters/Setters =====

    public String getInputImage2() { return inputImage2; }
    public void setInputImage2(String inputImage2) { this.inputImage2 = inputImage2; }

    public String getInputImage3() { return inputImage3; }
    public void setInputImage3(String inputImage3) { this.inputImage3 = inputImage3; }

    public String getInputImage4() { return inputImage4; }
    public void setInputImage4(String inputImage4) { this.inputImage4 = inputImage4; }

    public String getInputImage5() { return inputImage5; }
    public void setInputImage5(String inputImage5) { this.inputImage5 = inputImage5; }

    public String getInputImage6() { return inputImage6; }
    public void setInputImage6(String inputImage6) { this.inputImage6 = inputImage6; }

    public String getInputImage7() { return inputImage7; }
    public void setInputImage7(String inputImage7) { this.inputImage7 = inputImage7; }

    public String getInputImage8() { return inputImage8; }
    public void setInputImage8(String inputImage8) { this.inputImage8 = inputImage8; }

    // Additional Gemini images (9-14)
    public String getInputImage9() { return inputImage9; }
    public void setInputImage9(String inputImage9) { this.inputImage9 = inputImage9; }

    public String getInputImage10() { return inputImage10; }
    public void setInputImage10(String inputImage10) { this.inputImage10 = inputImage10; }

    public String getInputImage11() { return inputImage11; }
    public void setInputImage11(String inputImage11) { this.inputImage11 = inputImage11; }

    public String getInputImage12() { return inputImage12; }
    public void setInputImage12(String inputImage12) { this.inputImage12 = inputImage12; }

    public String getInputImage13() { return inputImage13; }
    public void setInputImage13(String inputImage13) { this.inputImage13 = inputImage13; }

    public String getInputImage14() { return inputImage14; }
    public void setInputImage14(String inputImage14) { this.inputImage14 = inputImage14; }

    /**
     * Sets multiple input images from a list of Base64-encoded strings.
     * The first image goes to inputImage, second to inputImage2, etc.
     *
     * @param images list of Base64-encoded image strings (max 14 for Gemini, max 8 for FLUX.2)
     */
    public void setInputImages(List<String> images) {
        if (images == null || images.isEmpty()) return;

        // Clear all previous images first
        this.inputImage = null;
        this.inputImage2 = null;
        this.inputImage3 = null;
        this.inputImage4 = null;
        this.inputImage5 = null;
        this.inputImage6 = null;
        this.inputImage7 = null;
        this.inputImage8 = null;
        this.inputImage9 = null;
        this.inputImage10 = null;
        this.inputImage11 = null;
        this.inputImage12 = null;
        this.inputImage13 = null;
        this.inputImage14 = null;

        // Set images from list
        if (images.size() > 0) this.inputImage = images.get(0);
        if (images.size() > 1) this.inputImage2 = images.get(1);
        if (images.size() > 2) this.inputImage3 = images.get(2);
        if (images.size() > 3) this.inputImage4 = images.get(3);
        if (images.size() > 4) this.inputImage5 = images.get(4);
        if (images.size() > 5) this.inputImage6 = images.get(5);
        if (images.size() > 6) this.inputImage7 = images.get(6);
        if (images.size() > 7) this.inputImage8 = images.get(7);
        if (images.size() > 8) this.inputImage9 = images.get(8);
        if (images.size() > 9) this.inputImage10 = images.get(9);
        if (images.size() > 10) this.inputImage11 = images.get(10);
        if (images.size() > 11) this.inputImage12 = images.get(11);
        if (images.size() > 12) this.inputImage13 = images.get(12);
        if (images.size() > 13) this.inputImage14 = images.get(13);
    }

    /**
     * Gets the count of attached input images.
     *
     * @return number of non-null input images (0-14)
     */
    public int getInputImageCount() {
        int count = 0;
        if (inputImage != null && !inputImage.isEmpty()) count++;
        if (inputImage2 != null && !inputImage2.isEmpty()) count++;
        if (inputImage3 != null && !inputImage3.isEmpty()) count++;
        if (inputImage4 != null && !inputImage4.isEmpty()) count++;
        if (inputImage5 != null && !inputImage5.isEmpty()) count++;
        if (inputImage6 != null && !inputImage6.isEmpty()) count++;
        if (inputImage7 != null && !inputImage7.isEmpty()) count++;
        if (inputImage8 != null && !inputImage8.isEmpty()) count++;
        if (inputImage9 != null && !inputImage9.isEmpty()) count++;
        if (inputImage10 != null && !inputImage10.isEmpty()) count++;
        if (inputImage11 != null && !inputImage11.isEmpty()) count++;
        if (inputImage12 != null && !inputImage12.isEmpty()) count++;
        if (inputImage13 != null && !inputImage13.isEmpty()) count++;
        if (inputImage14 != null && !inputImage14.isEmpty()) count++;
        return count;
    }

    /**
     * Gets the response modalities for Gemini API.
     *
     * @return the list of response modalities (e.g., ["IMAGE"], ["TEXT", "IMAGE"])
     */
    public List<String> getResponseModalities() {
        return responseModalities;
    }

    /**
     * Sets the response modalities for Gemini API.
     *
     * @param responseModalities the list of modalities to request
     */
    public void setResponseModalities(List<String> responseModalities) {
        this.responseModalities = responseModalities;
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
     * Gets whether to include the AI's thought process in the response.
     *
     * @return true if thought summary should be included
     */
    public Boolean getIncludeThoughts() {
        return includeThoughts;
    }

    /**
     * Sets whether to include the AI's thought process in the response.
     *
     * @param includeThoughts true to include reasoning summary
     */
    public void setIncludeThoughts(Boolean includeThoughts) {
        this.includeThoughts = includeThoughts;
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

    public String getNegativePrompt() { return negativePrompt; }
    public void setNegativePrompt(String negativePrompt) { this.negativePrompt = negativePrompt; }

    public Boolean getGenerateAudio() { return generateAudio; }
    public void setGenerateAudio(Boolean generateAudio) { this.generateAudio = generateAudio; }

    public String getPersonGeneration() { return personGeneration; }
    public void setPersonGeneration(String personGeneration) { this.personGeneration = personGeneration; }

    public String getVideoSourceUrl() { return videoSourceUrl; }
    public void setVideoSourceUrl(String videoSourceUrl) { this.videoSourceUrl = videoSourceUrl; }

    public Integer getVideoStartOffsetSeconds() { return videoStartOffsetSeconds; }
    public void setVideoStartOffsetSeconds(Integer videoStartOffsetSeconds) { this.videoStartOffsetSeconds = videoStartOffsetSeconds; }

    public Integer getVideoEndOffsetSeconds() { return videoEndOffsetSeconds; }
    public void setVideoEndOffsetSeconds(Integer videoEndOffsetSeconds) { this.videoEndOffsetSeconds = videoEndOffsetSeconds; }

    /**
     * Checks whether a direct video source (YouTube URL) is attached for
     * video-to-image generation.
     *
     * @return true if a non-empty video source URL is present
     */
    public boolean hasVideoSource() {
        return videoSourceUrl != null && !videoSourceUrl.trim().isEmpty();
    }

    /**
     * Checks if a model id belongs to the OpenAI provider: an OpenAI image model, or any model of an OpenAI-compatible
     * gateway (e.g. {@code qwen-image-2.1}), as all BFL ids start with {@code flux.} and Gemini ids contain
     * {@code gemini}.
     *
     * @param model the model id (may be null)
     * @return true for OpenAI and gateway models
     */
    static boolean isOpenAiModel(String model) {
        if (model == null)
            return false;
        String lower = model.toLowerCase(java.util.Locale.ROOT);
        return GenerationModel.isOpenAiImageModelId(model) || (!lower.startsWith("flux.") && !lower.contains("gemini"));
    }

    /**
     * Validates the request parameters.
     *
     * @throws IllegalArgumentException if any parameters are invalid
     */
    public void validate() {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be empty");
        }

        // Determine provider from model name to apply correct prompt limit
        // Must match UI limits in MainWindowController.getMaxPromptLength()
        boolean isGeminiModel = model != null && model.toLowerCase().contains("gemini");
        boolean isOpenAiModel = isOpenAiModel(model);
        int maxPromptLength = isGeminiModel ? 250000 // Gemini: 250k
                : isOpenAiModel ? GenerationModel.openAiMaxPromptLength(model) // OpenAI: 1k-32k per model
                : 2000; // BFL: 2k

        if (prompt.length() > maxPromptLength) {
            throw new IllegalArgumentException(String.format(
                "Prompt cannot exceed %d characters (%s limit)",
                maxPromptLength,
                isGeminiModel ? "Gemini" : isOpenAiModel ? "OpenAI" : "BFL"
            ));
        }

        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("Model must be specified");
        }
        if (imageCount != null && (imageCount < 1 || imageCount > 4)) {
            throw new IllegalArgumentException("Image count must be between 1 and 4");
        }
        // Width/Height validation only for FLUX.2 (Gemini uses imageSize and videoResolution, FLUX 3 a resolution tier)
        boolean isGemini = model != null && (model.toLowerCase().contains("gemini") || isOpenAiModel);
        if (!isGemini && !model.toLowerCase().startsWith("flux.3-")) {
            // FLUX.2: min 64 and multiple of 16 (the schema has no other limit), else min 256 and multiple of 32
            boolean flux2 = model.toLowerCase().startsWith("flux.2-");
            int minDimension = flux2 ? 64 : 256;
            int multiple = flux2 ? 16 : 32;
            if (width != null && (width < minDimension || width > 2048 || width % multiple != 0)) {
                throw new IllegalArgumentException(
                        "Width must be between " + minDimension + "-2048 and multiple of " + multiple);
            }
            if (height != null && (height < minDimension || height > 2048 || height % multiple != 0)) {
                throw new IllegalArgumentException(
                        "Height must be between " + minDimension + "-2048 and multiple of " + multiple);
            }
        }
        
        // Validate input image for compatible models
        if (hasInputImage()) {
            // Check if model supports image input (FLUX 3, FLUX.2, Gemini and OpenAI models support input images)
            if (model != null) {
                String lowerModel = model.toLowerCase();
                boolean isFlux = lowerModel.startsWith("flux.3-") || lowerModel.startsWith("flux.2-");

                if (!isFlux && !lowerModel.contains("gemini") && !isOpenAiModel) {
                    throw new IllegalArgumentException("Input image is only supported with FLUX 3, FLUX.2, Gemini, and OpenAI models");
                }
            }
        }

        // Validate direct video source (only Nano Banana 2 and Lite support it)
        if (hasVideoSource()) {
            GenerationModel videoModel = GenerationModel.fromApiModelId(model);
            if (videoModel == null || !videoModel.supportsVideoSource()) {
                throw new IllegalArgumentException(
                        "Video source input is only supported with Nano Banana 2 and Nano Banana 2 Lite");
            }
        }

        // Validate FLUX.2 [flex] parameters
        if (guidance != null && (guidance < 1.5 || guidance > 10.0)) {
            throw new IllegalArgumentException("Guidance must be between 1.5 and 10.0");
        }
        if (steps != null && (steps < 1 || steps > 50)) {
            throw new IllegalArgumentException("Steps must be between 1 and 50");
        }

        // Validate Gemini Omni video parameters (the duration is put into the prompt)
        if (model != null && model.startsWith("gemini-omni")) {
            if (videoDuration != null && (videoDuration < 3 || videoDuration > 10)) {
                throw new IllegalArgumentException("Video duration must be between 3 and 10 seconds");
            }
        }
    }
    
    @Override
    public String toString() {
        return String.format("ImageGenerationRequest{prompt='%s', model='%s', size=%dx%d, format='%s', count=%d, hasInputImage=%s}", 
            prompt != null ? prompt.substring(0, Math.min(50, prompt.length())) + "..." : null, 
            model, width, height, outputFormat, imageCount, hasInputImage());
    }
}