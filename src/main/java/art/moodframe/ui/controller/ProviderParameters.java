package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationModel;
import art.moodframe.ui.support.SafetyTolerance;
import art.moodframe.util.I18nManager;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The fields of the providers in the generation panel: the BFL safety tolerance, prompt upsampling and the FLUX.2
 * sliders of safety, guidance and steps (bounds from the parameter table, values remembered in the settings), the
 * thinking level and the Google Search of Gemini; shows those the chosen model takes.
 */
final class ProviderParameters {

    private static final Logger logger = LoggerFactory.getLogger(ProviderParameters.class);

    /**
     * The nodes of {@code GenerationPanel.fxml} the part works on.
     */
    record Nodes(VBox geminiAdvancedFeaturesContainer,
                 VBox geminiThinkingLevelContainer,
                 VBox bflParametersContainer,
                 VBox flux2GroundingSearchContainer,
                 CheckBox bflPromptUpsamplingCheckBox,
                 VBox flux1SafetyContainer,
                 Label safetyToleranceLabel,
                 VBox flux2ParametersContainer,
                 VBox flux2SafetyContainer,
                 Label flux2SafetyLabel,
                 Slider flux2SafetySlider,
                 Label flux2SafetyValueLabel,
                 VBox flux2GuidanceContainer,
                 Label flux2GuidanceLabel,
                 Slider flux2GuidanceSlider,
                 VBox flux2StepsContainer,
                 Label flux2StepsLabel,
                 Slider flux2StepsSlider) {
    }

    private final VBox geminiAdvancedFeaturesContainer;
    private final VBox geminiThinkingLevelContainer;
    private final VBox bflParametersContainer;
    private final VBox flux2GroundingSearchContainer;
    private final CheckBox bflPromptUpsamplingCheckBox;
    private final VBox flux1SafetyContainer;
    private final Label safetyToleranceLabel;
    private final VBox flux2ParametersContainer;
    private final VBox flux2SafetyContainer;
    private final Label flux2SafetyLabel;
    private final Slider flux2SafetySlider;
    private final Label flux2SafetyValueLabel;
    private final VBox flux2GuidanceContainer;
    private final Label flux2GuidanceLabel;
    private final Slider flux2GuidanceSlider;
    private final VBox flux2StepsContainer;
    private final Label flux2StepsLabel;
    private final Slider flux2StepsSlider;

    private ApplicationConfig config;
    private Runnable costChanged = () -> { };
    private art.moodframe.ui.component.GeminiAdvancedFeaturesSection geminiAdvancedFeaturesSection;

    /**
     * Takes the nodes of the part.
     *
     * @param nodes the nodes
     */
    ProviderParameters(Nodes nodes) {
        this.geminiAdvancedFeaturesContainer = nodes.geminiAdvancedFeaturesContainer();
        this.geminiThinkingLevelContainer = nodes.geminiThinkingLevelContainer();
        this.bflParametersContainer = nodes.bflParametersContainer();
        this.flux2GroundingSearchContainer = nodes.flux2GroundingSearchContainer();
        this.bflPromptUpsamplingCheckBox = nodes.bflPromptUpsamplingCheckBox();
        this.flux1SafetyContainer = nodes.flux1SafetyContainer();
        this.safetyToleranceLabel = nodes.safetyToleranceLabel();
        this.flux2ParametersContainer = nodes.flux2ParametersContainer();
        this.flux2SafetyContainer = nodes.flux2SafetyContainer();
        this.flux2SafetyLabel = nodes.flux2SafetyLabel();
        this.flux2SafetySlider = nodes.flux2SafetySlider();
        this.flux2SafetyValueLabel = nodes.flux2SafetyValueLabel();
        this.flux2GuidanceContainer = nodes.flux2GuidanceContainer();
        this.flux2GuidanceLabel = nodes.flux2GuidanceLabel();
        this.flux2GuidanceSlider = nodes.flux2GuidanceSlider();
        this.flux2StepsContainer = nodes.flux2StepsContainer();
        this.flux2StepsLabel = nodes.flux2StepsLabel();
        this.flux2StepsSlider = nodes.flux2StepsSlider();
    }

    /**
     * Adds the Google Search of Gemini and sets up the FLUX.2 sliders with their remembered values.
     *
     * @param config      remembers the values (may be null: the defaults)
     * @param costChanged updates the price once a value that may change it changed
     */
    void setUp(ApplicationConfig config, Runnable costChanged) {
        this.config = config;
        this.costChanged = costChanged;
        setupGeminiAdvancedFeatures();
        setupFlux2Parameters();
    }

    /** @return whether Gemini grounds the image in a Google Search, null before the set-up */
    Boolean googleSearch() {
        return geminiAdvancedFeaturesSection != null ? geminiAdvancedFeaturesSection.isSearchGroundingEnabled() : null;
    }

    /**
     * Sets the Google Search of Gemini, e.g. as remembered.
     *
     * @param enabled whether Gemini grounds the image in a Google Search
     */
    void setGoogleSearch(boolean enabled) {
        if (geminiAdvancedFeaturesSection != null)
            geminiAdvancedFeaturesSection.setSearchGroundingEnabled(enabled);
    }

    /**
     * Shows the fields of the provider of a model: those of BFL with the FLUX.2 sliders the model takes, the thinking
     * level and the Google Search of Gemini.
     *
     * @param generationModel the selected model, null for an unknown item
     */
    void show(GenerationModel generationModel) {
        boolean isVideo = generationModel != null && generationModel.isVideoModel();
        ApiProvider provider = generationModel != null ? generationModel.getProvider() : ApiProvider.BFL;
        boolean isBFL = (provider == ApiProvider.BFL);
        boolean isGemini = (provider == ApiProvider.GOOGLE_GEMINI) && !isVideo;

        // BFL-specific parameters container (includes Prompt Upsampling checkbox)
        if (bflParametersContainer != null) {
            bflParametersContainer.setVisible(isBFL);
            bflParametersContainer.setManaged(isBFL);
        }

        // Gemini Thinking Level container: only Nano Banana 2 and Lite can be controlled (minimal or high),
        // Nano Banana Pro always thinks and cannot be controlled
        if (geminiThinkingLevelContainer != null) {
            boolean showThinking = isGemini && generationModel != null && generationModel.supportsThinkingLevel();
            geminiThinkingLevelContainer.setVisible(showThinking);
            geminiThinkingLevelContainer.setManaged(showThinking);
        }

        // FLUX.2 [klein] and FLUX 3 have no prompt upsampling switch (FLUX 3 always upsamples), hide the checkbox
        boolean showUpsampling = generationModel == null
                || (!generationModel.isFlux2Klein() && !generationModel.isFlux3Model());
        if (bflPromptUpsamplingCheckBox != null) {
            bflPromptUpsamplingCheckBox.setVisible(showUpsampling);
            bflPromptUpsamplingCheckBox.setManaged(showUpsampling);
        }

        // Common parameters (always visible for both BFL and Gemini)
        // - modelComboBox (always visible)
        // - the aspect ratio of the frame (always visible - the target, of which each model takes its closest ratio)
        // - stylePresetsComboBox (always visible - style appended to prompt for both providers)

        // Gemini Advanced Features (Phase 2) - only visible for Gemini models
        if (geminiAdvancedFeaturesContainer != null && geminiAdvancedFeaturesSection != null) {
            // The only advanced feature is Google Search grounding, which Nano Banana 2 Lite does not support
            boolean showAdvanced = isGemini && (generationModel == null || generationModel.supportsGroundingSearch());
            geminiAdvancedFeaturesContainer.setVisible(showAdvanced);
            geminiAdvancedFeaturesContainer.setManaged(showAdvanced);
        }

        // FLUX.2 Specific Parameters
        updateFlux2ParameterVisibility(generationModel);
    }

    /**
     * Sets up the Gemini Advanced Features section (Phase 2).
     * Creates the component and wires it to the model selection.
     */
    private void setupGeminiAdvancedFeatures() {
        if (geminiAdvancedFeaturesContainer == null) {
            logger.warn("geminiAdvancedFeaturesContainer is null, cannot setup Gemini advanced features");
            return;
        }

        // Create the Gemini advanced features section
        geminiAdvancedFeaturesSection = new art.moodframe.ui.component.GeminiAdvancedFeaturesSection();

        // Add it to the container
        geminiAdvancedFeaturesContainer.getChildren().add(geminiAdvancedFeaturesSection);

        // Initially hidden - only show when Gemini model is selected
        geminiAdvancedFeaturesContainer.setVisible(false);
        geminiAdvancedFeaturesContainer.setManaged(false);

        // Setup persistence listeners for Gemini Advanced Features
        setupGeminiPersistenceListeners();

        logger.info("Gemini Advanced Features section initialized");
    }

    /**
     * Sets up persistence listeners for Gemini Advanced Features (Phase 2).
     * Must be called after geminiAdvancedFeaturesSection is created.
     */
    private void setupGeminiPersistenceListeners() {
        try {

            if (config != null && geminiAdvancedFeaturesSection != null) {
                // Google Search Grounding
                geminiAdvancedFeaturesSection.getSearchGroundingCheckBox().selectedProperty()
                        .addListener((obs, oldVal, newVal) -> {
                            if (newVal != null) {
                                config.generation().setDefaultGoogleSearchEnabled(newVal);
                            }
                        });

                // NOTE: Thinking Mode persistence is now handled by reasoningQualityChoiceBox listener above
                // (Reasoning Quality was moved from Gemini Advanced Features to general parameters)

                logger.info("Gemini Advanced Features persistence listeners configured");
            }
        } catch (Exception e) {
            logger.error("Failed to setup Gemini persistence listeners", e);
        }
    }

    /**
     * Sets up FLUX.2 specific parameters (Grounding Search, Guidance, Steps).
     * Configures sliders, labels, and persistence listeners.
     */
    private void setupFlux2Parameters() {
        try {
            /*  Bounds and defaults of guidance and steps come from the one table of the model parameters  */
            takeBounds(flux2GuidanceSlider, art.moodframe.model.ModelParameters.GUIDANCE);
            takeBounds(flux2StepsSlider, art.moodframe.model.ModelParameters.STEPS);

            if (config == null) {
                logger.warn("ApplicationConfig not available, FLUX.2 parameters will use defaults");
                return;
            }

            // Load saved values from config
            double savedGuidance = config.generation().getFlux2FlexGuidance();
            int savedSteps = config.generation().getFlux2FlexSteps();

            // Setup Guidance slider
            if (flux2GuidanceSlider != null && flux2GuidanceLabel != null) {
                flux2GuidanceSlider.setValue(savedGuidance);
                updateFlux2GuidanceLabel(savedGuidance);

                // Listener for label updates and config persistence
                flux2GuidanceSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                    double value = newVal.doubleValue();
                    updateFlux2GuidanceLabel(value);
                    config.generation().setFlux2FlexGuidance(value);
                    costChanged.run(); // Cost may depend on parameters
                });
            }

            // Setup Steps slider
            if (flux2StepsSlider != null && flux2StepsLabel != null) {
                flux2StepsSlider.setValue(savedSteps);
                updateFlux2StepsLabel(savedSteps);

                // Listener for label updates and config persistence
                flux2StepsSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                    int value = newVal.intValue();
                    updateFlux2StepsLabel(value);
                    config.generation().setFlux2FlexSteps(value);
                    costChanged.run();
                });
            }

            // Setup FLUX.2 Safety Tolerance slider (0-5 scale)
            if (flux2SafetySlider != null && flux2SafetyValueLabel != null) {
                int savedFlux2Safety = config.generation().getFlux2SafetyTolerance();
                flux2SafetySlider.setValue(savedFlux2Safety);
                updateFlux2SafetyLabel(savedFlux2Safety);

                // Listener for label updates and config persistence
                flux2SafetySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                    int value = newVal.intValue();
                    // Snap to integer values
                    if (value != newVal.doubleValue()) {
                        flux2SafetySlider.setValue(value);
                    }
                    updateFlux2SafetyLabel(value);
                    config.generation().setFlux2SafetyTolerance(value);
                });

                // Set i18n labels
                if (flux2SafetyLabel != null) {
                    flux2SafetyLabel.setText(I18nManager.getText("params.flux2.safety"));
                }
            }

            /*  FLUX.2 [max] grounds on its own by the prompt ("current", "today", "latest"): the API takes no
                parameter for it, so the group only names it  */

            // Initially hidden - visibility controlled by updateFlux2ParameterVisibility
            if (flux2ParametersContainer != null) {
                flux2ParametersContainer.setVisible(false);
                flux2ParametersContainer.setManaged(false);
            }

            logger.info("FLUX.2 parameters initialized: guidance={}, steps={}", savedGuidance, savedSteps);

        } catch (Exception e) {
            logger.error("Failed to setup FLUX.2 parameters", e);
        }
    }

    /*  sets minimum, maximum and default of a slider from its model parameter  */
    private static void takeBounds(Slider slider, art.moodframe.model.ModelParameter parameter) {
        if (slider == null)
            return;
        slider.setMin(parameter.minimum().doubleValue());
        slider.setMax(parameter.maximum().doubleValue());
        slider.setValue(((Number) parameter.defaultValue()).doubleValue());
    }

    /**
     * Updates the FLUX.2 [flex] Guidance label with the current value.
     *
     * @param value the guidance value (1.5-10.0)
     */
    private void updateFlux2GuidanceLabel(double value) {
        if (flux2GuidanceLabel != null) {
            // Use getText with parameter - i18n uses MessageFormat {0} syntax
            String labelText = I18nManager.getText(
                    "params.flux2.guidance", String.format("%.1f", value));
            flux2GuidanceLabel.setText(labelText);
        }
    }

    /**
     * Updates the FLUX.2 [flex] Steps label with the current value.
     *
     * @param value the steps value (1-50)
     */
    private void updateFlux2StepsLabel(int value) {
        if (flux2StepsLabel != null) {
            // Use getText with parameter - i18n uses MessageFormat {0} syntax
            String labelText = I18nManager.getText(
                    "params.flux2.steps", value);
            flux2StepsLabel.setText(labelText);
        }
    }

    /**
     * Updates the FLUX.2 safety tolerance label with the current slider value and description.
     * Uses i18n keys params.flux2.safety.0 through params.flux2.safety.5.
     */
    private void updateFlux2SafetyLabel(int value) {
        if (flux2SafetyValueLabel != null) {
            String i18nKey = "params.flux2.safety." + value;
            String description = I18nManager.getText(i18nKey);
            flux2SafetyValueLabel.setText(description);
        }
    }

    /**
     * Updates the safety tolerance label with the current slider value and
     * description.
     */
    void updateSafetyToleranceLabel(int value) {
        if (safetyToleranceLabel != null) {
            safetyToleranceLabel.setText(SafetyTolerance.label(value));
        }
    }

    /**
     * Updates FLUX.2 specific parameter visibility based on the selected model.
     * <ul>
     *   <li><b>FLUX.2 [max]:</b> Names its web search, which it starts on its own</li>
     *   <li><b>FLUX.2 [flex]:</b> Shows Guidance and Steps sliders</li>
     *   <li><b>Other models:</b> Hides all FLUX.2 parameters</li>
     * </ul>
     *
     * @param generationModel the selected model, null for an unknown item
     */
    private void updateFlux2ParameterVisibility(GenerationModel generationModel) {
        boolean isFlux2 = generationModel != null && (generationModel.isFlux2Model() || generationModel.isFlux3Model());
        boolean searchesTheWeb = generationModel != null && generationModel.getProvider() == ApiProvider.BFL
                && generationModel.supportsGroundingSearch();
        boolean takesGuidance = generationModel != null
                && generationModel.parameter(art.moodframe.model.ModelParameters.GUIDANCE.name()) != null;
        boolean takesSteps = generationModel != null
                && generationModel.parameter(art.moodframe.model.ModelParameters.STEPS.name()) != null;

        // Show main FLUX.2 container for FLUX.2 and FLUX 3 models (FLUX 3 only uses the safety slider)
        flux2ParametersContainer.setVisible(isFlux2);
        flux2ParametersContainer.setManaged(isFlux2);

        // The web search of FLUX.2 [max], only for the BFL models that ground their prompt
        if (flux2GroundingSearchContainer != null) {
            flux2GroundingSearchContainer.setVisible(searchesTheWeb);
            flux2GroundingSearchContainer.setManaged(searchesTheWeb);
        }

        // Guidance and steps sliders - for the models that take them (FLUX.2 [flex])
        if (flux2GuidanceContainer != null) {
            flux2GuidanceContainer.setVisible(takesGuidance);
            flux2GuidanceContainer.setManaged(takesGuidance);
        }
        if (flux2StepsContainer != null) {
            flux2StepsContainer.setVisible(takesSteps);
            flux2StepsContainer.setManaged(takesSteps);
        }

        // FLUX.2 Safety Tolerance - for ALL FLUX.2 models (0-5 scale)
        // Show FLUX.2 safety slider, hide FLUX.1 safety slider (they are at the same position)
        if (flux2SafetyContainer != null) {
            flux2SafetyContainer.setVisible(isFlux2);
            flux2SafetyContainer.setManaged(isFlux2);
        }
        if (flux1SafetyContainer != null) {
            flux1SafetyContainer.setVisible(!isFlux2);
            flux1SafetyContainer.setManaged(!isFlux2);
        }

        logger.debug("FLUX.2 parameter visibility: isFlux2={}, webSearch={}, guidance={}, steps={}",
                     isFlux2, searchesTheWeb, takesGuidance, takesSteps);
    }
}
