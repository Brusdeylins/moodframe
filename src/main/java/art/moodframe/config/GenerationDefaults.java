package art.moodframe.config;

/**
 * The defaults of a generation as the main window leaves them: model, aspect ratio, resolution, image count, the
 * building blocks, and the parameters of the models (safety tolerance, FLUX.2 [flex], Gemini, video).
 */
public class GenerationDefaults {

    private final ConfigStore store;

    GenerationDefaults(ConfigStore store) {
        this.store = store;
    }

    /*  ===== Frame =====  */

    /** @return the id of the default model (default "flux.3-image") */
    public String getDefaultModel() {
        return store.get("model.default", "flux.3-image");
    }

    /** @param model the id of the default model */
    public void setDefaultModel(String model) {
        store.set("model.default", model);
    }

    /** @return the default aspect ratio (default "16:9") */
    public String getDefaultAspectRatio() {
        return store.get("model.default_aspect_ratio", "16:9");
    }

    /** @param aspectRatio the default aspect ratio */
    public void setDefaultAspectRatio(String aspectRatio) {
        store.set("model.default_aspect_ratio", aspectRatio);
    }

    /** @return the default resolution, or null if none is remembered */
    public String getDefaultResolution() {
        return store.get("model.default_resolution");
    }

    /** @param resolution the default resolution */
    public void setDefaultResolution(String resolution) {
        store.set("model.default_resolution", resolution);
    }

    /** @return the default number of images (default 2) */
    public int getDefaultImageCount() {
        return Integer.parseInt(store.get("model.default_image_count", "2"));
    }

    /** @param count the default number of images */
    public void setDefaultImageCount(int count) {
        store.set("model.default_image_count", String.valueOf(count));
    }

    /*  ===== Building blocks =====  */

    /** @return the name of the remembered style block, or "None" */
    public String getDefaultStyle() {
        return store.get("style.default", "None");
    }

    /** @param style the name of the style block */
    public void setDefaultStyle(String style) {
        store.set("style.default", style);
    }

    /** @return the name of the remembered palette, or "None" */
    public String getDefaultColorStyle() {
        return store.get("colorstyle.default", "None");
    }

    /** @param colorStyle the name of the palette */
    public void setDefaultColorStyle(String colorStyle) {
        store.set("colorstyle.default", colorStyle);
    }

    /** @return the name of the remembered light block, or "None" */
    public String getDefaultLight() {
        return store.get("light.default", "None");
    }

    /** @param light the name of the light block */
    public void setDefaultLight(String light) {
        store.set("light.default", light);
    }

    /** @return the name of the remembered camera block, or "None" */
    public String getDefaultCamera() {
        return store.get("camera.default", "None");
    }

    /** @param camera the name of the camera block */
    public void setDefaultCamera(String camera) {
        store.set("camera.default", camera);
    }

    /** @return the name of the remembered atmosphere block, or "None" */
    public String getDefaultAtmosphere() {
        return store.get("atmosphere.default", "None");
    }

    /** @param atmosphere the name of the atmosphere block */
    public void setDefaultAtmosphere(String atmosphere) {
        store.set("atmosphere.default", atmosphere);
    }

    /*  ===== BFL =====  */

    /** @return the safety tolerance of the FLUX.1 models (0-6), default 6 */
    public int getDefaultSafetyTolerance() {
        return Integer.parseInt(store.get("model.default_safety_tolerance", "6"));
    }

    /** @param safetyTolerance the safety tolerance of the FLUX.1 models (0-6) */
    public void setDefaultSafetyTolerance(int safetyTolerance) {
        store.set("model.default_safety_tolerance", String.valueOf(safetyTolerance));
    }

    /** @return the safety tolerance of the FLUX.2 models (0 strictest - 5 most permissive), default 2 like at BFL */
    public int getFlux2SafetyTolerance() {
        return Integer.parseInt(store.get("model.flux2_safety_tolerance", "2"));
    }

    /** @param safetyTolerance the safety tolerance of the FLUX.2 models, clamped to 0-5 */
    public void setFlux2SafetyTolerance(int safetyTolerance) {
        store.set("model.flux2_safety_tolerance", String.valueOf(Math.max(0, Math.min(5, safetyTolerance))));
    }

    /** @return the guidance of FLUX.2 [flex] (1.5-10), default 5.0 */
    public double getFlux2FlexGuidance() {
        return Double.parseDouble(store.get("flux2.flex.guidance", "5.0"));
    }

    /** @param guidance the guidance of FLUX.2 [flex], clamped to 1.5-10 */
    public void setFlux2FlexGuidance(double guidance) {
        store.set("flux2.flex.guidance", String.valueOf(Math.max(1.5, Math.min(10.0, guidance))));
    }

    /** @return the inference steps of FLUX.2 [flex] (1-50), default 50 */
    public int getFlux2FlexSteps() {
        return Integer.parseInt(store.get("flux2.flex.steps", "50"));
    }

    /** @param steps the inference steps of FLUX.2 [flex], clamped to 1-50 */
    public void setFlux2FlexSteps(int steps) {
        store.set("flux2.flex.steps", String.valueOf(Math.max(1, Math.min(50, steps))));
    }

    /** @return true if FLUX.2 searches the web for the prompt (default off) */
    public boolean getFlux2GroundingSearchEnabled() {
        return store.getBoolean("flux2.grounding_search", false);
    }

    /** @param enabled true to let FLUX.2 search the web for the prompt */
    public void setFlux2GroundingSearchEnabled(boolean enabled) {
        store.set("flux2.grounding_search", String.valueOf(enabled));
    }

    /** @return true if BFL upsamples the prompt (default off) */
    public boolean getDefaultBflPromptUpsampling() {
        return store.getBoolean("generation.bfl_prompt_upsampling", false);
    }

    /** @param enabled true to let BFL upsample the prompt */
    public void setDefaultBflPromptUpsampling(boolean enabled) {
        store.set("generation.bfl_prompt_upsampling", String.valueOf(enabled));
    }

    /*  ===== Gemini =====  */

    /** @return the thinking level of the Gemini 3 Pro models, "minimal" (default) or "high" */
    public String getDefaultGeminiThinkingLevel() {
        return store.get("generation.gemini_thinking_level", "minimal");
    }

    /** @param level the thinking level, "minimal" or "high" */
    public void setDefaultGeminiThinkingLevel(String level) {
        store.set("generation.gemini_thinking_level", level);
    }

    /** @return true if Gemini grounds the generation with Google Search (default off) */
    public boolean getDefaultGoogleSearchEnabled() {
        return store.getBoolean("gemini.default_google_search", false);
    }

    /** @param enabled true to ground the generation with Google Search */
    public void setDefaultGoogleSearchEnabled(boolean enabled) {
        store.set("gemini.default_google_search", String.valueOf(enabled));
    }

    /*  ===== Video (the keys keep the name `veo.` of the first video model, so saved settings stay) =====  */

    /** @return the duration of a video, like "8s" (default) */
    public String getDefaultVideoDuration() {
        return store.get("veo.default_duration", "8s");
    }

    /** @param duration the duration of a video, like "8s" */
    public void setDefaultVideoDuration(String duration) {
        store.set("veo.default_duration", duration);
    }

    /** @return true if a video gets sound (default off) */
    public boolean isVideoAudioEnabled() {
        return store.getBoolean("veo.audio_enabled", false);
    }

    /** @param enabled true to give a video sound */
    public void setVideoAudioEnabled(boolean enabled) {
        store.set("veo.audio_enabled", String.valueOf(enabled));
    }
}
