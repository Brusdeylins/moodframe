package art.moodframe.model;

/**
 * Unified enumeration of all available image and video generation models
 * across providers: FLUX (BFL), Gemini / Nano Banana (Google) and Gemini Omni video (Google).
 *
 * <p>
 * This enum provides a mapping between user-friendly display names
 * and the actual API model identifiers, along with pricing information
 * and model capabilities. The originating provider is exposed via
 * {@link #getProvider()}.
 * </p>
 *
 * <p>
 * Based on BFL and Google Gemini API documentation.
 * Includes FLUX 3 and FLUX.2 multi-image models, Gemini image models and Gemini Omni video models.
 * </p>
 *
 * @since 1.0.0
 * @since 1.5.0 - Added FLUX.2 models (Pro, Max, Flex), removed FLUX.1 [dev]
 * @since 1.6.7 - Renamed from FluxModel; absorbed Gemini models (removed redundant GeminiModel enum)
 * @since 1.10.0 - Added FLUX 3 Image and FLUX 3 Image Klein, removed the FLUX.1 models
 */
public enum GenerationModel implements UnifiedModel {

    // ===== FLUX 3 IMAGE MODELS =====
    // One request field "resolution" picks an equal-area tier, the price is fixed per tier (bfl.ai/pricing).

    /**
     * FLUX 3 Image 768 - Square-sized drafts (768 x 768 area).
     */
    FLUX3_IMAGE_768(
            "FLUX 3 Image 768 - Draft ($0.041)",
            "flux.3-image",
            0.041,
            "Latest and strongest FLUX model for creating and editing, up to 10 reference images"),

    /**
     * FLUX 3 Image 1K - About 1 megapixel.
     */
    FLUX3_IMAGE_1K(
            "FLUX 3 Image 1K - Standard ($0.048)",
            "flux.3-image",
            0.048,
            "Latest and strongest FLUX model for creating and editing, up to 10 reference images"),

    /**
     * FLUX 3 Image 1.5K - About 2 megapixels.
     */
    FLUX3_IMAGE_1_5K(
            "FLUX 3 Image 1.5K - Detailed ($0.07)",
            "flux.3-image",
            0.07,
            "Latest and strongest FLUX model for creating and editing, up to 10 reference images"),

    /**
     * FLUX 3 Image 2K - About 4 megapixels.
     */
    FLUX3_IMAGE_2K(
            "FLUX 3 Image 2K - High Resolution ($0.10)",
            "flux.3-image",
            0.10,
            "Latest and strongest FLUX model for creating and editing, up to 10 reference images"),

    /**
     * FLUX 3 Image 4K - About 16 megapixels.
     */
    FLUX3_IMAGE_4K(
            "FLUX 3 Image 4K - Print ($0.607)",
            "flux.3-image",
            0.607,
            "Latest and strongest FLUX model for creating and editing, up to 10 reference images"),

    /**
     * FLUX 3 Image Klein 768 - Compact open-weights model, made for editing.
     */
    FLUX3_KLEIN_768(
            "FLUX 3 Image Klein 768 - Draft ($0.008)",
            "flux.3-image-klein",
            0.008,
            "Fast and cheap FLUX 3 model made for editing, up to 10 reference images"),

    /**
     * FLUX 3 Image Klein 1K - About 1 megapixel.
     */
    FLUX3_KLEIN_1K(
            "FLUX 3 Image Klein 1K - Fast ($0.009)",
            "flux.3-image-klein",
            0.009,
            "Fast and cheap FLUX 3 model made for editing, up to 10 reference images"),

    /**
     * FLUX 3 Image Klein 1.5K - About 2 megapixels.
     */
    FLUX3_KLEIN_1_5K(
            "FLUX 3 Image Klein 1.5K - Fast ($0.011)",
            "flux.3-image-klein",
            0.011,
            "Fast and cheap FLUX 3 model made for editing, up to 10 reference images"),

    /**
     * FLUX 3 Image Klein 2K - About 4 megapixels.
     */
    FLUX3_KLEIN_2K(
            "FLUX 3 Image Klein 2K - Fast ($0.014)",
            "flux.3-image-klein",
            0.014,
            "Fast and cheap FLUX 3 model made for editing, up to 10 reference images"),

    /**
     * FLUX 3 Image Klein 4K - About 16 megapixels.
     */
    FLUX3_KLEIN_4K(
            "FLUX 3 Image Klein 4K - Fast ($0.036)",
            "flux.3-image-klein",
            0.036,
            "Fast and cheap FLUX 3 model made for editing, up to 10 reference images"),

    // ===== FLUX.2 MODELS =====

    /**
     * FLUX.2 [pro] - Production-grade image generation.
     * Supports multi-image input (up to 8 images). Price: $0.03 for the first megapixel plus $0.015 per
     * additional megapixel, see {@link #costForMegapixels(double)}.
     */
    FLUX2_PRO(
            "FLUX.2 [pro] - Production Grade ($0.03+)",
            "flux.2-pro",
            0.015, // Price per additional megapixel (base price: see costForMegapixels)
            "Production-grade image generation with multi-image support"),

    /**
     * FLUX.2 [max] - Highest quality with grounding search.
     * Supports multi-image input (up to 8 images) and web search grounding.
     * Price: $0.07 for the first megapixel plus $0.03 per additional megapixel.
     */
    FLUX2_MAX(
            "FLUX.2 [max] - Premium with Search ($0.07+)",
            "flux.2-max",
            0.03, // Price per additional megapixel (base price: see costForMegapixels)
            "Highest quality with grounding search and multi-image support"),

    /**
     * FLUX.2 [flex] - Text rendering with adjustable inference.
     * Supports guidance (1.5-10.0) and steps (1-50) parameters. Price: $0.05 per megapixel.
     */
    FLUX2_FLEX(
            "FLUX.2 [flex] - Text Rendering ($0.05/MP)",
            "flux.2-flex",
            0.05, // Price per megapixel
            "Specialized for text rendering with adjustable inference parameters"),

    /**
     * FLUX.2 [klein] 4B - Fast and cheap, up to 4 reference images.
     * Price: $0.014 for the first megapixel plus $0.001 per additional megapixel. The enum price is the
     * price per additional megapixel, see {@link #costForMegapixels(double)}. No prompt upsampling,
     * no grounding search, no guidance/steps controls.
     */
    FLUX2_KLEIN_4B(
            "FLUX.2 [klein] 4B - Fast & Cheap ($0.014+)",
            "flux.2-klein-4b",
            0.001, // Price per additional megapixel (base price: see costForMegapixels)
            "Fast and cheap generation with up to 4 reference images"),

    /**
     * FLUX.2 [klein] 9B - Fast, a bit higher quality than 4B, up to 4 reference images.
     * Price: $0.015 for the first megapixel plus $0.002 per additional megapixel.
     */
    FLUX2_KLEIN_9B(
            "FLUX.2 [klein] 9B - Fast ($0.015+)",
            "flux.2-klein-9b",
            0.002, // Price per additional megapixel (base price: see costForMegapixels)
            "Fast generation with higher quality than 4B and up to 4 reference images"),

    // ===== NANO BANANA MODELS =====

    /**
     * Nano Banana 1K - The original Nano Banana (Gemini 2.5 Flash based).
     * Fast and efficient image generation with multimodal capabilities.
     * Standard output 1024x1024 pixels.
     */
    GEMINI_FLASH_1K(
            "Nano Banana 1K - Fast & Multimodal ($0.039)",
            "gemini-2.5-flash-image",
            0.039, // $0.039 per image (1024x1024)
            "Fast generation with Google's multimodal AI, supports text+image input"),

    /**
     * Nano Banana Pro 2K - Professional quality with 2K resolution.
     * Based on Gemini 3 Pro. Enhanced prompt adherence and multimodal understanding.
     * Uses imageSize parameter "2K" to specify 2K resolution.
     */
    GEMINI_PRO_2K(
            "Nano Banana Pro 2K - Professional ($0.134)",
            "gemini-3-pro-image",
            0.134, // $0.134 per output image (2K resolution)
            "Professional quality with enhanced understanding of complex prompts"),

    /**
     * Nano Banana Pro 4K - Premium quality with 4K resolution.
     * Based on Gemini 3 Pro. Maximum quality for demanding applications.
     * Uses imageSize parameter "4K" to specify 4K resolution.
     */
    GEMINI_PRO_4K(
            "Nano Banana Pro 4K - Premium ($0.24)",
            "gemini-3-pro-image",
            0.24, // $0.24 per output image (4K resolution)
            "Premium quality images with maximum resolution and detail"),

    // ===== NANO BANANA 2 MODELS (Gemini 3.1 Flash Image) =====

    /**
     * Nano Banana 2 512px - Budget image generation.
     * Resolution: 512x512 pixels
     * Price: ~$0.045 per image
     */
    NANO_BANANA2_512(
            "Nano Banana 2 512px - Budget ($0.045)",
            "gemini-3.1-flash-image",
            0.045,
            "Fast budget image generation at 512px resolution"),

    /**
     * Nano Banana 2 1K - Fast image generation.
     * Resolution: Up to 1024x1024 pixels
     * Price: ~$0.067 per image
     */
    NANO_BANANA2_1K(
            "Nano Banana 2 1K - Fast ($0.067)",
            "gemini-3.1-flash-image",
            0.067,
            "Fast image generation at 1K resolution"),

    /**
     * Nano Banana 2 2K - Standard quality image generation.
     * Resolution: Up to 2048x2048 pixels
     * Price: ~$0.101 per image
     */
    NANO_BANANA2_2K(
            "Nano Banana 2 2K - Standard ($0.101)",
            "gemini-3.1-flash-image",
            0.101,
            "Standard quality image generation at 2K resolution"),

    /**
     * Nano Banana 2 4K - High quality image generation.
     * Resolution: Up to 4096x4096 pixels
     * Price: ~$0.151 per image
     */
    NANO_BANANA2_4K(
            "Nano Banana 2 4K - High Quality ($0.151)",
            "gemini-3.1-flash-image",
            0.151,
            "High quality image generation at 4K resolution"),

    // ===== NANO BANANA 2 LITE (Gemini 3.1 Flash Lite Image) =====

    /**
     * Nano Banana 2 Lite - Fastest, most cost-efficient model in the family.
     * Based on Gemini 3.1 Flash Lite Image. ~4s text-to-image, output capped at 1K.
     * No Google Search / Image Search grounding in the Lite tier.
     * Price: ~$0.034 per 1K image.
     */
    NANO_BANANA2_LITE(
            "Nano Banana 2 Lite - Fastest ($0.034)",
            "gemini-3.1-flash-lite-image",
            0.034,
            "Fastest, most cost-efficient image generation, capped at 1K resolution"),

    // ===== GEMINI OMNI VIDEO MODELS (replaced Veo 3.1, which Google shut down on 2026-10-22) =====
    // The enum price is the price per second of video. Calibrated with live requests: output tokens per second
    // are 1,931 (360p), 5,792 (720p), 8,688 (1080p) and 17,376 (4k) at $17.50 per 1M video tokens.

    /**
     * Gemini Omni 360p - Cheapest resolution, good for drafts. Duration 3-10 s (set in the prompt), audio always on.
     */
    OMNI_360P(
            "Gemini Omni 360p - Draft Video (~$0.034/s)",
            "gemini-omni-1.1-flash",
            0.034,
            "Gemini Omni video generation at 360p with audio, 3 to 10 seconds"),

    /**
     * Gemini Omni 720p - Standard resolution (the documented price of about $0.10 per second).
     */
    OMNI_720P(
            "Gemini Omni 720p - Standard Video (~$0.10/s)",
            "gemini-omni-1.1-flash",
            0.101,
            "Gemini Omni video generation at 720p with audio, 3 to 10 seconds"),

    /**
     * Gemini Omni 1080p - Full HD, upscaled by the model, 1.5 times the token rate of 720p.
     */
    OMNI_1080P(
            "Gemini Omni 1080p - Full HD Video (~$0.15/s)",
            "gemini-omni-1.1-flash",
            0.152,
            "Gemini Omni video generation at 1080p with audio, 3 to 10 seconds"),

    /**
     * Gemini Omni 4K - Ultra HD, upscaled by the model, 3 times the token rate of 720p.
     */
    OMNI_4K(
            "Gemini Omni 4K - Ultra HD Video (~$0.30/s)",
            "gemini-omni-1.1-flash",
            0.304,
            "Gemini Omni video generation at 4K with audio, 3 to 10 seconds"),

    // ===== OPENAI IMAGE MODELS (dynamic) =====
    // The models come from the API (GET /models), so there is one placeholder constant for all of them. The
    // dropdown item is OPENAI_PREFIX + model id, see openAiModelId(String). The price is unknown (0).

    OPENAI_IMAGE(
            "OpenAI Image",
            "openai-image",
            0.0,
            "Image models of the OpenAI Images API, listed from the connected API");

    /** Prefix of the dropdown items and chat entry names of OpenAI models. */
    public static final String OPENAI_PREFIX = "OpenAI \u00b7 ";

    private final String displayName;
    private final String apiModelId;
    private final double pricePerImage;
    private final String description;
    private final boolean supportsImageAttachment;
    private final int maxMegapixels;
    private final int maxResolution;

    GenerationModel(String displayName, String apiModelId, double pricePerImage, String description) {
        this.displayName = displayName;
        this.apiModelId = apiModelId;
        this.pricePerImage = pricePerImage;
        this.description = description;

        // Determine image attachment support based on model capabilities
        // BFL: FLUX 3 (up to 10 images) and FLUX.2 (up to 8 images) support image input
        // Gemini: ALL models support image input (up to 14 images, Omni: one start image)
        this.supportsImageAttachment = apiModelId.startsWith("flux.3-")
                                      || apiModelId.startsWith("flux.2-")
                                      || apiModelId.startsWith("gemini")
                                      || apiModelId.startsWith("openai-");

        // Determine max resolution based on model type
        if (apiModelId.startsWith("gemini-omni")) {
            // Gemini Omni video models - resolution based on enum name
            this.maxMegapixels = 0; // Video, not image
            String enumName = name();
            this.maxResolution = enumName.contains("4K") ? 3840 : enumName.contains("1080P") ? 1920
                    : enumName.contains("360P") ? 640 : 1280;
        } else if (apiModelId.startsWith("gemini")) {
            // Gemini models - distinguish resolution by enum name suffix
            String enumName = name();
            if (enumName.contains("4K")) {
                this.maxMegapixels = 16;
                this.maxResolution = 4096;
            } else if (enumName.contains("2K")) {
                this.maxMegapixels = 4;
                this.maxResolution = 2048;
            } else if (enumName.contains("512")) {
                this.maxMegapixels = 1;
                this.maxResolution = 512;
            } else {
                this.maxMegapixels = 1;
                this.maxResolution = 1024; // 1K default
            }
        } else if (apiModelId.startsWith("openai-")) {
            // OpenAI models - largest size of the Images API is 1792 px (dall-e-3)
            this.maxMegapixels = 4;
            this.maxResolution = 1792;
        } else if (apiModelId.startsWith("flux.3-")) {

            /*  FLUX 3: equal-area tiers (768sq, about 1, 2, 4 and 16 MP), the side of the square of the tier  */
            String enumName = name();
            this.maxMegapixels = enumName.endsWith("4K") ? 16 : enumName.endsWith("_2K") ? 4
                    : enumName.endsWith("1_5K") ? 2 : 1;
            this.maxResolution = enumName.endsWith("4K") ? 4096 : enumName.endsWith("_2K") ? 2048
                    : enumName.endsWith("1_5K") ? 1440 : enumName.endsWith("768") ? 768 : 1024;
        } else {
            // FLUX.2 models - all support up to 4MP at 2048x2048
            // Per BFL docs: "Minimum 64x64, maximum 4MP (e.g., 2048x2048), recommended up to 2MP"
            // Output dimensions are multiples of 16
            this.maxMegapixels = 4;
            this.maxResolution = 2048;
        }
    }

    /**
     * Gets the user-friendly display name with pricing.
     * 
     * @return the display name for UI components
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the API model identifier for BFL API calls.
     * 
     * @return the model ID to use in API requests
     */
    public String getApiModelId() {
        return apiModelId;
    }

    /**
     * Gets the price per image generation in USD.
     * 
     * @return the cost per image in dollars
     */
    public double getPricePerImage() {
        return pricePerImage;
    }

    /**
     * Gets a description of the model's capabilities.
     * 
     * @return the model description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this model supports image attachments for image-to-image editing.
     * 
     * @return true if the model supports image input for editing workflows
     */
    public boolean supportsImageAttachment() {
        return supportsImageAttachment;
    }

    /**
     * Gets the maximum file size for image attachments based on provider.
     * BFL models: 10MB, Google Gemini models: 20MB.
     *
     * @param provider the API provider
     * @return max file size in bytes
     */
    public static long getMaxFileSize(ApiProvider provider) {
        if (provider == ApiProvider.GOOGLE_GEMINI)
            return 20L * 1024 * 1024;
        if (provider == ApiProvider.OPENAI)
            return 50L * 1024 * 1024;
        return 10L * 1024 * 1024;
    }

    /**
     * Gets the maximum resolution supported by this model in megapixels.
     * 
     * @return maximum megapixels (1 for standard models, 4 for Ultra models)
     */
    public int getMaxMegapixels() {
        return maxMegapixels;
    }
    
    /**
     * Gets the maximum single dimension (width or height) supported by this model.
     *
     * @return maximum pixels for width or height dimension
     */
    public int getMaxResolution() {
        return maxResolution;
    }

    /**
     * Checks if this model supports grounding search (web search integration).
     * Currently only FLUX.2 [max] and Gemini models support this feature.
     *
     * @return true if the model supports grounding search
     */
    public boolean supportsGroundingSearch() {
        // Nano Banana 2 Lite tier does NOT support Google Search / Image Search grounding
        if (apiModelId.equals("gemini-3.1-flash-lite-image") || isOmniModel()) {
            return false;
        }

        return this == FLUX2_MAX
            || apiModelId.startsWith("gemini");
    }

    /**
     * Checks if this model supports multi-image input.
     * FLUX 3 models support up to 10 images, FLUX.2 up to 8, Gemini up to 14.
     *
     * @return true if the model supports multiple input images
     */
    public boolean supportsMultiImage() {
        return apiModelId.startsWith("flux.3-")
            || apiModelId.startsWith("flux.2-")
            || apiModelId.startsWith("openai-")
            || (apiModelId.startsWith("gemini") && !isOmniModel());
    }

    /**
     * Gets the maximum number of input images this model supports.
     *
     * @return max input images (10 for FLUX 3, 8 for FLUX.2, 4 for FLUX.2 [klein], 14 for Gemini, 1 for Omni)
     */
    public int getMaxInputImages() {
        if (isFlux3Model()) {
            return 10; // FLUX 3 Image and Klein: one to 10 references
        } else if (isFlux2Klein()) {
            return 4; // FLUX.2 [klein] supports up to 4 reference images
        } else if (apiModelId.startsWith("flux.2-")) {
            return 8;
        } else if (apiModelId.startsWith("openai-")) {
            return 14; // gpt-image accepts up to 16 images, the request model carries 14
        } else if (isOmniModel()) {
            return 1; // Gemini Omni: one start image (image-to-video), more references are not documented
        } else if (apiModelId.startsWith("gemini")) {
            return 14; // 6 objects + 5 humans + some extra
        }
        return 0;
    }

    /**
     * Checks if this model is a FLUX.2 model.
     *
     * @return true if this is a FLUX.2 model
     */
    public boolean isFlux2Model() {
        return apiModelId.startsWith("flux.2-");
    }

    /**
     * Checks if this model is one of the FLUX.2 [klein] models.
     *
     * @return true if this is FLUX.2 [klein] 4B or 9B
     */
    public boolean isFlux2Klein() {
        return this == FLUX2_KLEIN_4B || this == FLUX2_KLEIN_9B;
    }

    /**
     * Checks if this model is one of the FLUX 3 image models (FLUX 3 Image or FLUX 3 Image Klein).
     *
     * @return true for the FLUX 3 variants
     * @since 1.10.0
     */
    public boolean isFlux3Model() {
        return apiModelId.startsWith("flux.3-");
    }

    /**
     * Checks if this model is one of the FLUX 3 Image Klein variants, which also take {@code seed}.
     *
     * @return true for FLUX 3 Image Klein
     * @since 1.10.0
     */
    public boolean isFlux3Klein() {
        return apiModelId.equals("flux.3-image-klein");
    }

    /**
     * Gets the value of the FLUX 3 request field {@code resolution} of this variant.
     *
     * @return {@code 768sq}, {@code 1k}, {@code 1.5k}, {@code 2k} or {@code 4k}, null for other models
     * @since 1.10.0
     */
    public String getFlux3Resolution() {
        if (!isFlux3Model())
            return null;
        String enumName = name();
        return enumName.endsWith("768") ? "768sq" : enumName.endsWith("1_5K") ? "1.5k"
                : enumName.endsWith("_2K") ? "2k" : enumName.endsWith("4K") ? "4k" : "1k";
    }

    /**
     * Calculates the price of one FLUX.2 image of the given size (bfl.ai/pricing): [pro], [max] and [klein] cost a
     * base price for the first megapixel plus the enum price per additional megapixel ([pro] $0.03, [max] $0.07,
     * [klein] 4B $0.014, 9B $0.015), [flex] costs the enum price per megapixel.
     *
     * @param megapixels the output size in megapixels
     * @return the price in dollars
     */
    public double costForMegapixels(double megapixels) {
        double base = switch (this) {
            case FLUX2_PRO -> 0.03;
            case FLUX2_MAX -> 0.07;
            case FLUX2_KLEIN_4B -> 0.014;
            case FLUX2_KLEIN_9B -> 0.015;
            default -> -1.0;
        };
        if (base < 0)
            return pricePerImage * megapixels;
        return base + Math.max(0.0, megapixels - 1.0) * pricePerImage;
    }

    /**
     * Checks if this model is FLUX.2 [flex] which has adjustable inference parameters.
     *
     * @return true if this is FLUX.2 [flex]
     */
    public boolean isFlux2Flex() {
        return this == FLUX2_FLEX;
    }

    /**
     * Checks if this model accepts the extended aspect ratios 1:4, 4:1, 1:8 and 8:1 in addition to the
     * standard Gemini ratios (Nano Banana 2 and Nano Banana 2 Lite, both confirmed with live requests).
     *
     * @return true for Nano Banana 2 and Nano Banana 2 Lite
     */
    public boolean supportsExtendedAspectRatios() {
        return apiModelId.equals("gemini-3.1-flash-image") || apiModelId.equals("gemini-3.1-flash-lite-image");
    }

    /**
     * Checks if the thinking level of this image model can be chosen (minimal or high). Nano Banana Pro
     * always thinks and cannot be controlled, the API rejects the level LOW for Nano Banana 2.
     *
     * @return true for Nano Banana 2 and Nano Banana 2 Lite
     */
    public boolean supportsThinkingLevel() {
        return supportsExtendedAspectRatios();
    }

    /**
     * Checks if a YouTube video can be passed directly as context for the image (video-to-image).
     *
     * @return true for Nano Banana 2 and Nano Banana 2 Lite
     */
    public boolean supportsVideoSource() {
        return supportsExtendedAspectRatios();
    }

    /**
     * Checks if this model generates video (as opposed to images).
     *
     * @return true if this is a video generation model
     */
    public boolean isVideoModel() {
        return isOmniModel();
    }

    /**
     * Checks if this model is a Gemini Omni video generation model.
     *
     * @return true for the Omni constants
     */
    public boolean isOmniModel() {
        return apiModelId.startsWith("gemini-omni");
    }

    /**
     * Gets the video resolution string for video API requests.
     *
     * @return the resolution string ("360p", "720p", "1080p" or "4k"), or null if not a video model
     */
    public String getVideoResolution() {
        if (!isVideoModel()) return null;
        String enumName = name();
        if (enumName.contains("4K")) return "4k";
        if (enumName.contains("1080P")) return "1080p";
        if (enumName.contains("360P")) return "360p";
        return "720p";
    }

    /**
     * Gets the video durations (in seconds) the user can choose: Gemini Omni 3 to 10 s (the duration is part of
     * the prompt there).
     *
     * @return the allowed durations, empty for models that are no video models
     */
    public java.util.List<Integer> getVideoDurationChoices() {
        if (isOmniModel()) {
            return java.util.List.of(3, 4, 5, 6, 7, 8, 9, 10);
        }
        return java.util.List.of();
    }

    /**
     * Calculates the price of a video of the given length. Google bills per second, the enum price of a
     * Gemini Omni model is the price per second.
     *
     * @param seconds the video length in seconds
     * @return the price in dollars
     */
    public double videoPrice(int seconds) {
        return pricePerImage * seconds;
    }

    /**
     * Finds a GenerationModel by its display name.
     * 
     * @param displayName the display name to search for
     * @return the corresponding GenerationModel, or null if not found
     */
    public static GenerationModel fromDisplayName(String displayName) {
        for (GenerationModel model : values()) {
            if (model.displayName.equals(displayName)) {
                return model;
            }
        }
        return null;
    }

    /**
     * Finds a GenerationModel by its localized display name.
     * 
     * @param localizedDisplayName the localized display name to search for
     * @return the corresponding GenerationModel, or null if not found
     */
    public static GenerationModel fromLocalizedDisplayName(String localizedDisplayName) {
        if (openAiModelId(localizedDisplayName) != null) {
            return OPENAI_IMAGE;
        }
        for (GenerationModel model : values()) {
            if (model.getLocalizedDisplayName().equals(localizedDisplayName)) {
                return model;
            }
        }
        // A family name stands for its first variant, the resolution choice picks the right one; an older
        // name with another tagline or price (e.g. "FLUX.2 [pro] - Production Grade ($0.03/MP)") counts as its family
        int tagline = localizedDisplayName == null ? -1 : localizedDisplayName.indexOf(" - ");
        String family = tagline > 0 ? localizedDisplayName.substring(0, tagline) : localizedDisplayName;
        for (GenerationModel model : values()) {
            if (model != OPENAI_IMAGE && model.getFamilyName().equals(family)) {
                return model;
            }
        }
        // Fallback: try original display name for backward compatibility
        return fromDisplayName(localizedDisplayName);
    }

    /**
     * Gets the OpenAI model id from a dropdown item or chat entry name.
     *
     * @param itemName the dropdown item or chat entry model name (may be null)
     * @return the model id (for example {@code gpt-image-1}), or null if the name is no OpenAI item
     */
    public static String openAiModelId(String itemName) {
        if (itemName != null && itemName.startsWith(OPENAI_PREFIX) && itemName.length() > OPENAI_PREFIX.length()) {
            return itemName.substring(OPENAI_PREFIX.length());
        }
        return null;
    }

    /**
     * Checks if a model id is an image model of the OpenAI Images API: {@code dall-e-*}, {@code gpt-image-*},
     * {@code chatgpt-image-*}.
     *
     * @param modelId the API model id (may be null)
     * @return true for OpenAI image models
     */
    public static boolean isOpenAiImageModelId(String modelId) {
        if (modelId == null)
            return false;
        String lower = modelId.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("dall-e") || lower.contains("gpt-image") || lower.contains("chatgpt-image");
    }

    /**
     * Gets the prompt limit in characters of an OpenAI image model.
     *
     * @param modelId the OpenAI model id
     * @return 32,000 for gpt-image models, 4,000 for dall-e-3 and 1,000 for dall-e-2
     */
    public static int openAiMaxPromptLength(String modelId) {
        String lower = modelId == null ? "" : modelId.toLowerCase(java.util.Locale.ROOT);
        if (lower.equals("dall-e-2"))
            return 1000;
        return lower.startsWith("dall-e") ? 4000 : 32000;
    }

    /**
     * Finds a GenerationModel by its API model ID.
     * 
     * @param apiModelId the API model ID to search for
     * @return the corresponding GenerationModel, or null if not found
     */
    public static GenerationModel fromApiModelId(String apiModelId) {
        for (GenerationModel model : values()) {
            if (model.apiModelId.equals(apiModelId)) {
                return model;
            }
        }
        return null;
    }

    /**
     * Whether the provider announced to shut this model down. Such models stay in the enum so that
     * existing chat entries can still be read, but they are no longer offered for selection.
     *
     * @return true for Nano Banana 1K (gemini-2.5-flash-image, shutdown 2026-10-02)
     */
    public boolean isDeprecated() {
        return this == GEMINI_FLASH_1K;
    }

    /**
     * Gets the models that can be selected (all models that are not deprecated). The OpenAI models are listed
     * from the API, see {@code OpenAiApiService#getImageModels()}.
     *
     * @return the selectable models in declaration order
     */
    public static java.util.List<GenerationModel> selectable() {
        return java.util.Arrays.stream(values()).filter(model -> !model.isDeprecated() && model != OPENAI_IMAGE).toList();
    }

    /**
     * Gets the default recommended model.
     * 
     * @return FLUX 3 Image 1K as the recommended default
     */
    public static GenerationModel getDefault() {
        return FLUX3_IMAGE_1K;
    }

    /**
     * Gets all available models as display names for UI components.
     * 
     * @return array of display names
     */
    public static String[] getDisplayNames() {
        GenerationModel[] models = values();
        String[] names = new String[models.length];
        for (int i = 0; i < models.length; i++) {
            names[i] = models[i].displayName;
        }
        return names;
    }

    /**
     * Gets all available models as localized display names for UI components.
     * 
     * @return array of localized display names
     */
    public static String[] getLocalizedDisplayNames() {
        GenerationModel[] models = values();
        String[] names = new String[models.length];
        for (int i = 0; i < models.length; i++) {
            names[i] = models[i].getLocalizedDisplayName();
        }
        return names;
    }

    /**
     * Gets the name of the model family: the variants that differ only by their resolution share it (for example
     * the Nano Banana 2 variants for 512, 1K, 2K and 4K). The model dropdown lists one entry per family and the
     * resolution has its own choice.
     *
     * @return the family name, the display name without tagline and price for a model without resolution variants
     */
    public String getFamilyName() {
        return switch (this) {
            case FLUX3_IMAGE_768, FLUX3_IMAGE_1K, FLUX3_IMAGE_1_5K, FLUX3_IMAGE_2K, FLUX3_IMAGE_4K -> "FLUX 3 Image";
            case FLUX3_KLEIN_768, FLUX3_KLEIN_1K, FLUX3_KLEIN_1_5K, FLUX3_KLEIN_2K, FLUX3_KLEIN_4K -> "FLUX 3 Image Klein";
            case GEMINI_FLASH_1K -> "Nano Banana";
            case GEMINI_PRO_2K, GEMINI_PRO_4K -> "Nano Banana Pro";
            case NANO_BANANA2_512, NANO_BANANA2_1K, NANO_BANANA2_2K, NANO_BANANA2_4K -> "Nano Banana 2";
            case OMNI_360P, OMNI_720P, OMNI_1080P, OMNI_4K -> "Gemini Omni";
            default -> {
                /*  Without variants the name is the display name up to the tagline: "FLUX.2 [pro] - ... ($0.03)"  */
                String name = getLocalizedDisplayName();
                int tagline = name.indexOf(" - ");
                yield tagline > 0 ? name.substring(0, tagline) : name;
            }
        };
    }

    /**
     * Gets the resolution of this variant, as shown in the resolution choice.
     *
     * @return for example {@code 2K} or {@code 1080p}, null for a model without resolution variants and without a
     *         known fixed size
     */
    public String getResolutionLabel() {
        return switch (this) {
            case FLUX3_IMAGE_768, FLUX3_KLEIN_768 -> "768";
            case FLUX3_IMAGE_1K, FLUX3_KLEIN_1K, GEMINI_FLASH_1K, NANO_BANANA2_1K, NANO_BANANA2_LITE -> "1K";
            case FLUX3_IMAGE_1_5K, FLUX3_KLEIN_1_5K -> "1.5K";
            case FLUX3_IMAGE_2K, FLUX3_KLEIN_2K -> "2K";
            case FLUX3_IMAGE_4K, FLUX3_KLEIN_4K -> "4K";
            case GEMINI_PRO_2K, NANO_BANANA2_2K -> "2K";
            case GEMINI_PRO_4K, NANO_BANANA2_4K, OMNI_4K -> "4K";
            case NANO_BANANA2_512 -> "512";
            case OMNI_360P -> "360p";
            case OMNI_720P -> "720p";
            case OMNI_1080P -> "1080p";
            default -> null;
        };
    }

    /**
     * Gets the localized display name for this model.
     * 
     * @return localized display name from i18n resources
     */
    public String getLocalizedDisplayName() {
        switch (this) {
            // FLUX.2 models
            case FLUX2_PRO:
                return art.moodframe.util.I18nManager.getText("model.flux2.pro");
            case FLUX2_MAX:
                return art.moodframe.util.I18nManager.getText("model.flux2.max");
            case FLUX2_FLEX:
                return art.moodframe.util.I18nManager.getText("model.flux2.flex");
            // FLUX.2 [klein] models use English display names
            case FLUX2_KLEIN_4B:
            case FLUX2_KLEIN_9B:
            // Nano Banana models use English display names
            case GEMINI_FLASH_1K:
            case GEMINI_PRO_2K:
            case GEMINI_PRO_4K:
            // Nano Banana 2 models use English display names
            case NANO_BANANA2_512:
            case NANO_BANANA2_1K:
            case NANO_BANANA2_2K:
            case NANO_BANANA2_4K:
            // Nano Banana 2 Lite uses English display name
            case NANO_BANANA2_LITE:
            // Gemini Omni video models use English display names
            case OMNI_360P:
            case OMNI_720P:
            case OMNI_1080P:
            case OMNI_4K:
                return displayName;
            default:
                return displayName; // Fallback to original
        }
    }

    /**
     * Gets the API provider for this model.
     * FLUX models use BFL, Gemini models use GOOGLE_GEMINI.
     *
     * @return ApiProvider.BFL or ApiProvider.GOOGLE_GEMINI
     */
    @Override
    public ApiProvider getProvider() {
        // Determine provider based on model type
        if (apiModelId.startsWith("gemini")) {
            return ApiProvider.GOOGLE_GEMINI;
        }
        if (apiModelId.startsWith("openai-")) {
            return ApiProvider.OPENAI;
        }
        return ApiProvider.BFL;
    }

    @Override
    public String toString() {
        return displayName;
    }
}