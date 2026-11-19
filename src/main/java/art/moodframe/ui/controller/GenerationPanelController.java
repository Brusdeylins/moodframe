package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GenerationCost;
import art.moodframe.model.GenerationModel;
import art.moodframe.service.chat.ImageAttachmentService;
import art.moodframe.service.generation.GenerationService;
import art.moodframe.service.generation.ModelLabels;
import art.moodframe.ui.support.StatusSink;
import art.moodframe.util.I18nManager;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * The group Generation of the right panel of the main window ({@code GenerationPanel.fxml}): the model as family and
 * variant, the fields of its provider, count or video duration, the attached images and the price of the order, which
 * the send button shows.
 *
 * @since 2.1.3
 */
public class GenerationPanelController {

    private static final Logger logger = LoggerFactory.getLogger(GenerationPanelController.class);

    /** What the group asks of the window. */
    public interface Listener {

        /** The state of the order changed, e.g. the list of the models: the window updates what depends on it. */
        void changed();

        /** Another model is chosen. */
        void modelChanged();

        /**
         * The attached images changed.
         *
         * @param firstImage the image now attached as the only one, whose aspect ratio the frame may take; else null
         */
        void attachmentsChanged(File firstImage);

        /**
         * "Mood from image" at an attached image.
         *
         * @param image the image
         */
        void moodFromImage(File image);
    }

    @FXML
    private VBox generationGroupPane;
    @FXML
    private Label generationTitleLabel;
    @FXML
    private Label generationSummaryModel;
    @FXML
    private Label generationSummaryAttachments;
    @FXML
    private Label modelLabel;
    @FXML
    private ChoiceBox<String> modelFamilyChoiceBox;
    @FXML
    private ChoiceBox<String> modelComboBox;

    // Gemini
    @FXML
    private VBox geminiAdvancedFeaturesContainer;
    @FXML
    private VBox geminiThinkingLevelContainer;
    @FXML
    private Label geminiThinkingLevelLabel;
    @FXML
    private ChoiceBox<String> geminiThinkingLevelChoiceBox;
    @FXML
    private Tooltip geminiThinkingLevelTooltip;

    // BFL
    @FXML
    private VBox bflParametersContainer;
    @FXML
    private VBox flux2GroundingSearchContainer;
    @FXML
    private Label flux2GroundingSearchLabel;
    @FXML
    private Tooltip flux2GroundingSearchTooltip;
    @FXML
    private CheckBox bflPromptUpsamplingCheckBox;
    @FXML
    private Tooltip bflPromptUpsamplingTooltip;
    @FXML
    private VBox flux1SafetyContainer;
    @FXML
    private Label safetyToleranceLabel;
    @FXML
    private Slider safetyToleranceSlider;
    @FXML
    private VBox flux2ParametersContainer;
    @FXML
    private VBox flux2SafetyContainer;
    @FXML
    private Label flux2SafetyLabel;
    @FXML
    private Slider flux2SafetySlider;
    @FXML
    private Label flux2SafetyValueLabel;
    @FXML
    private VBox flux2GuidanceContainer;
    @FXML
    private Label flux2GuidanceLabel;
    @FXML
    private Slider flux2GuidanceSlider;
    @FXML
    private VBox flux2StepsContainer;
    @FXML
    private Label flux2StepsLabel;
    @FXML
    private Slider flux2StepsSlider;
    @FXML
    private Label seedLabel;
    @FXML
    private TextField seedTextField;
    @FXML
    private Button clearSeedButton;
    @FXML
    private Tooltip clearSeedTooltip;

    // Count and video
    @FXML
    private VBox imageCountContainer;
    @FXML
    private Label imageCountLabel;
    @FXML
    private Slider imageCountSlider;
    @FXML
    private VBox videoParametersContainer;
    @FXML
    private ChoiceBox<String> videoDurationChoiceBox;
    @FXML
    private Tooltip videoDurationTooltip;

    // Attachment
    @FXML
    private VBox multiImageAttachmentContainer;
    @FXML
    private Label imageAttachmentLabel;
    @FXML
    private StackPane imagePreviewContainer;
    @FXML
    private VBox noImagePlaceholder;
    @FXML
    private Label noImageLabel;
    @FXML
    private ImageView attachmentPreviewImage;
    @FXML
    private Button uploadImageButton;
    @FXML
    private Tooltip uploadImageTooltip;
    @FXML
    private Button removeImageButton;
    @FXML
    private Tooltip removeImageTooltip;

    private ApplicationConfig config;
    private GenerationService generationService;
    private ImageAttachmentService imageAttachmentService;
    private StatusSink status;
    private Listener listener;

    /*  The model dropdown lists families, the target resolution of the frame chooses the variant  */
    private art.moodframe.ui.component.ModelFamilySelector modelFamilySelector;

    // Track the currently selected model to survive language changes
    private GenerationModel currentSelectedModel;

    /*  the fields of BFL and Gemini  */
    private ProviderParameters parameters;

    /*  the attached images  */
    private GenerationAttachments attachments;

    /*  the price of the order, which the send button shows  */
    private final StringProperty costText = new SimpleStringProperty("$0.00");

    @FXML
    private void initialize() {
        attachments = new GenerationAttachments(new GenerationAttachments.Nodes(multiImageAttachmentContainer,
                imagePreviewContainer, noImagePlaceholder, attachmentPreviewImage, uploadImageButton,
                removeImageButton), () -> currentSelectedModel);
        parameters = new ProviderParameters(new ProviderParameters.Nodes(geminiAdvancedFeaturesContainer,
                geminiThinkingLevelContainer, bflParametersContainer, flux2GroundingSearchContainer,
                bflPromptUpsamplingCheckBox, flux1SafetyContainer, safetyToleranceLabel, flux2ParametersContainer,
                flux2SafetyContainer, flux2SafetyLabel, flux2SafetySlider, flux2SafetyValueLabel,
                flux2GuidanceContainer, flux2GuidanceLabel, flux2GuidanceSlider, flux2StepsContainer, flux2StepsLabel,
                flux2StepsSlider));

        // Configure image count slider
        imageCountSlider.setMin(1);
        imageCountSlider.setMax(4);
        imageCountSlider.setValue(1);
        imageCountSlider.setSnapToTicks(true);
        imageCountSlider.setShowTickLabels(true);
        imageCountSlider.setShowTickMarks(true);
        imageCountSlider.setMajorTickUnit(1.0);
        imageCountSlider.setMinorTickCount(0); // No minor ticks between major ticks
        imageCountSlider.setBlockIncrement(1.0); // Keyboard/scroll increments by 1

        // Configure safety tolerance slider (BFL API range: 0-6 for content moderation)
        safetyToleranceSlider.setMin(0);
        safetyToleranceSlider.setMax(6);
        safetyToleranceSlider.setValue(2);
        safetyToleranceSlider.setShowTickLabels(true);
        safetyToleranceSlider.setShowTickMarks(true);
        safetyToleranceSlider.setMajorTickUnit(1.0);
        safetyToleranceSlider.setSnapToTicks(true);
        safetyToleranceSlider.setMinorTickCount(0); // No minor ticks between major ticks
        safetyToleranceSlider.setBlockIncrement(1.0); // Keyboard/scroll increments by 1

        // Whole numbers only: the sliders snap to the integer value and their labels follow
        safetyToleranceSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int roundedValue = (int) Math.round(newVal.doubleValue());
            if (Math.abs(newVal.doubleValue() - roundedValue) > 0.01)
                safetyToleranceSlider.setValue(roundedValue);
            parameters.updateSafetyToleranceLabel(roundedValue);
        });
        imageCountSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int roundedValue = (int) Math.round(newVal.doubleValue());
            if (Math.abs(newVal.doubleValue() - roundedValue) > 0.01)
                imageCountSlider.setValue(roundedValue);
            updateImageCountLabel(roundedValue);
        });
        parameters.updateSafetyToleranceLabel((int) safetyToleranceSlider.getValue());
        updateImageCountLabel((int) imageCountSlider.getValue());

        // The video durations (the items follow the selected video model)
        videoDurationChoiceBox.getItems().addAll("3s", "4s", "5s", "6s", "7s", "8s", "9s", "10s");
        videoDurationChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> updateCostDisplay());

        // BFL prompt upsampling is off by default, the thinking level of Gemini minimal (the API default)
        bflPromptUpsamplingCheckBox.setSelected(false);
        geminiThinkingLevelChoiceBox.getItems().addAll("Minimal", "High");
        geminiThinkingLevelChoiceBox.setValue("Minimal");
    }

    /**
     * Lists the models of the enabled providers, restores the remembered choices of the group and from then on
     * remembers every choice.
     *
     * @param config            the configuration with the accounts and the remembered choices
     * @param generationService tells the prompt limit of a model
     * @param attachments       reads and encodes the attached images
     * @param resolution        the target resolution of the frame, which chooses the variant of a family
     * @param status            shows what the group did
     * @param listener          updates what depends on the group
     */
    public void init(ApplicationConfig config, GenerationService generationService, ImageAttachmentService attachments,
                     ObservableValue<String> resolution, StatusSink status, Listener listener) {
        this.config = config;
        this.generationService = generationService;
        this.imageAttachmentService = attachments;
        this.status = status;
        this.listener = listener;

        populateAvailableModels();
        if (config != null)
            videoDurationChoiceBox.setValue(config.generation().getDefaultVideoDuration());
        else
            videoDurationChoiceBox.setValue("8s");
        parameters.setUp(config, this::updateCostDisplay);
        modelFamilySelector = new art.moodframe.ui.component.ModelFamilySelector(modelComboBox,
                modelFamilyChoiceBox, resolution, config);

        /*  The summary of the group follows model, count, duration and attachments  */
        modelComboBox.valueProperty().addListener((obs, oldVal, newVal) -> updateGenerationSummary());
        imageCountSlider.valueProperty().addListener((obs, oldVal, newVal) -> updateGenerationSummary());
        videoDurationChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> updateGenerationSummary());

        loadSelections();
        setupPersistenceListeners();

        /*  The open lists start flush with their field and are as wide as it  */
        java.util.stream.Stream.of(modelFamilyChoiceBox, modelComboBox, videoDurationChoiceBox,
                geminiThinkingLevelChoiceBox).forEach(art.moodframe.ui.component.DropdownBounds::keepInWindow);

        this.attachments.setUp(imageAttachmentService, status, attachmentHost());
        updateCostDisplay();
        updateGenerationSummary();
    }

    /** The group, a card of the right panel. */
    public VBox group() {
        return generationGroupPane;
    }

    /** Lists the models of the enabled providers with a key anew, e.g. after the settings, keeping the choice. */
    public void refreshModels() {
        populateAvailableModels();
    }

    /** The item of the chosen model, as the hidden dropdown of the variants lists it. */
    public ReadOnlyObjectProperty<String> modelProperty() {
        return modelComboBox.valueProperty();
    }

    /** The field of the variants, for the tests. */
    ChoiceBox<String> modelChoice() {
        return modelComboBox;
    }

    /** The field of the families, for the tests. */
    ChoiceBox<String> modelFamilyChoice() {
        return modelFamilyChoiceBox;
    }

    /** The panel of the attached images, for the tests. */
    art.moodframe.ui.component.MultiImageAttachmentPanel attachmentPanel() {
        return attachments.panel();
    }

    /** The price of the order, e.g. "$0.12", "~$0.80" for a video or "unknown" for OpenAI. */
    public ReadOnlyStringProperty costTextProperty() {
        return costText;
    }

    /** The items of the models to choose from, as the hidden dropdown of the variants lists them. */
    public java.util.List<String> modelItems() {
        return java.util.List.copyOf(modelComboBox.getItems());
    }

    /** The item of the chosen model, null without. */
    public String modelItem() {
        return modelComboBox.getValue();
    }

    /** The chosen model, null without or for an unknown item. */
    public GenerationModel selectedModel() {
        return GenerationModel.fromLocalizedDisplayName(modelComboBox.getValue());
    }

    /** Whether a model can be chosen at all, which needs a provider with a key. */
    public boolean hasModels() {
        return !modelComboBox.getItems().isEmpty();
    }

    /**
     * Chooses a model by its item, as long as it is offered.
     *
     * @param item the item, as the hidden dropdown of the variants lists it
     */
    public void setModel(String item) {
        modelComboBox.setValue(item);
    }

    /**
     * Remembers the resolution of the family of a variant for the brainstorm popup, as if it was chosen.
     *
     * @param item       the item of the variant
     * @param resolution the resolution
     */
    public void rememberResolution(String item, String resolution) {
        modelFamilySelector.remember(item, resolution);
    }

    /**
     * Sets the video duration, if the chosen video model offers it.
     *
     * @param seconds the duration in seconds
     */
    public void setVideoSeconds(Integer seconds) {
        if (seconds != null && videoDurationChoiceBox.getItems().contains(seconds + "s"))
            videoDurationChoiceBox.setValue(seconds + "s");
    }

    /**
     * Takes over a seed, e.g. from an image; it applies to the models of BFL.
     *
     * @param seed the seed
     */
    public void setSeed(long seed) {
        seedTextField.setText(String.valueOf(seed));
    }

    /**
     * Sets the safety tolerance of FLUX.2, e.g. once the provider lowered it.
     *
     * @param value the value (0-5)
     */
    public void setFlux2Safety(int value) {
        flux2SafetySlider.setValue(value);
    }

    /** Whether a model of BFL is chosen, which takes a seed. */
    public boolean isSeedModelSelected() {
        GenerationModel model = selectedModel();
        return model != null && model.getProvider() == ApiProvider.BFL;
    }

    /** The number of images to generate. */
    public int imageCount() {
        return (int) imageCountSlider.getValue();
    }

    /** The first attached image, null without. */
    public File attachedImage() {
        return attachments.first();
    }

    /** Whether an image is attached. */
    public boolean hasAttachment() {
        return attachments.first() != null;
    }

    /**
     * Gets the prompt limit of the chosen model in characters, as its provider tells it.
     *
     * @return the limit
     */
    public int promptLimit() {
        String modelId = selectedModelId();
        return generationService.promptLimit(modelId);
    }

    /**
     * Checks if the provider of the chosen model has an API key; without a chosen model, any provider.
     *
     * @return true if the key is there
     */
    public boolean hasApiKeyForSelectedModel() {
        if (config == null)
            return false;
        String item = modelComboBox.getValue();
        if (item == null || item.isBlank())
            return config.accounts().hasAnyApiKey();
        GenerationModel model = GenerationModel.fromLocalizedDisplayName(item);
        return config.accounts().hasApiKey(model != null ? model.getProvider() : ApiProvider.BFL);
    }

    /**
     * Enables the fields while the window can take a prompt; the thinking level of Gemini also needs no attached
     * image (the Gemini API does not take it with input images).
     *
     * @param enableInput whether the fields can be used
     */
    public void setInputEnabled(boolean enableInput) {
        bflPromptUpsamplingCheckBox.setDisable(!enableInput);
        boolean thinkingLevelEnabled = enableInput && !hasAttachment();
        geminiThinkingLevelChoiceBox.setDisable(!thinkingLevelEnabled);
        geminiThinkingLevelLabel.setDisable(!thinkingLevelEnabled);
        // Use opacity to ensure visual feedback (CSS might override disabled styling)
        geminiThinkingLevelLabel.setOpacity(thinkingLevelEnabled ? 1.0 : 0.4);

        safetyToleranceSlider.setDisable(!enableInput);
        safetyToleranceLabel.setDisable(!enableInput);
        seedTextField.setDisable(!enableInput);
        clearSeedButton.setDisable(!enableInput);
        modelComboBox.setDisable(!enableInput);
        modelFamilyChoiceBox.setDisable(!enableInput);
        imageCountSlider.setDisable(!enableInput);
        imageCountLabel.setDisable(!enableInput);
        seedLabel.setDisable(!enableInput);
        imageAttachmentLabel.setDisable(!enableInput);
        attachments.setDisable(!enableInput);
        noImageLabel.setDisable(!enableInput);
        geminiAdvancedFeaturesContainer.setDisable(!enableInput);

        // The video parameters (duration) and the FLUX.2 parameters, with the web search of [max]
        videoParametersContainer.setDisable(!enableInput);
        flux2ParametersContainer.setDisable(!enableInput);
        flux2GroundingSearchContainer.setDisable(!enableInput);
    }

    /** Shows the texts of the group in the current language; the model list takes the localized names. */
    public void updateTexts() {
        bflPromptUpsamplingCheckBox.setText(I18nManager.getText("input.bfl.prompt.enhancement"));
        bflPromptUpsamplingTooltip.setText(I18nManager.getText("input.bfl.prompt.enhancement.hint"));
        geminiThinkingLevelLabel.setText(I18nManager.getText("input.gemini.thinking.level"));
        geminiThinkingLevelTooltip.setText(I18nManager.getText("input.gemini.thinking.level.hint"));
        flux2GroundingSearchLabel.setText(I18nManager.getText("params.grounding.search"));
        flux2GroundingSearchTooltip.setText(I18nManager.getText("params.grounding.search.hint"));
        updateImageCountLabel((int) imageCountSlider.getValue());
        if (seedTextField.getPromptText() != null)
            seedTextField.setPromptText(I18nManager.getText("params.seed.placeholder"));
        uploadImageButton.setText(I18nManager.getText("params.image.attach"));
        generationTitleLabel.setText(I18nManager.getText("params.generation.title"));
        modelLabel.setText(I18nManager.getText("params.model"));
        updateGenerationSummary();
        seedLabel.setText(I18nManager.getText("params.seed"));
        imageAttachmentLabel.setText(I18nManager.getText("params.image.attachment"));
        noImageLabel.setText(I18nManager.getText("params.image.none"));
        clearSeedTooltip.setText(I18nManager.getText("params.seed.clear.tooltip"));
        uploadImageTooltip.setText(I18nManager.getText("params.image.attach.tooltip"));
        removeImageTooltip.setText(I18nManager.getText("params.image.remove.tooltip"));
        updateModelNames();
        parameters.updateSafetyToleranceLabel((int) safetyToleranceSlider.getValue());
    }

    /**
     * The parameters of the model as set in the panel; a seed that is no number counts as random.
     *
     * @return the parameters
     */
    public art.moodframe.service.generation.GenerationOrder.Parameters parameters() {
        return modelParameters();
    }

    /** The Base64 data of the attached images that are ready to be sent, in their order. */
    public java.util.List<String> attachedImagesBase64() {
        return attachments.base64();
    }

    /**
     * Attaches an image as the edit pencil of an image does: into the next free square, else a new one; the count goes
     * to 1 and the model and the aspect ratio follow the attachment.
     *
     * @param imageFile the image
     * @return whether the image was attached, false if the maximum number of images is reached
     */
    public boolean attach(File imageFile) {
        return attachments.attach(imageFile);
    }

    /**
     * Chooses the model of a chat entry, as long as it is offered.
     *
     * @param entry the chat entry
     */
    public void reuse(ChatEntry entry) {
        reuseModel(entry);
    }

    /** Releases the attachment panel, which watches the clipboard. */
    public void dispose() {
        attachments.dispose();
    }

    /** Restores the remembered model and the remembered fields of the models. */
    private void loadSelections() {
        try {
            if (config == null)
                throw new IllegalStateException("No configuration");

            // Load model selection (now using display name for unique identification)
            String savedModelDisplay = config.generation().getDefaultModel();
            // Check if saved model display name is valid
            GenerationModel savedModel = GenerationModel.fromDisplayName(savedModelDisplay);
            if (savedModel == null) {
                // Try to interpret as old API model ID format for backward compatibility
                savedModel = GenerationModel.fromApiModelId(savedModelDisplay);
            }

            if (GenerationModel.openAiModelId(savedModelDisplay) != null
                    && modelComboBox.getItems().contains(savedModelDisplay)) {
                currentSelectedModel = GenerationModel.OPENAI_IMAGE;
                modelComboBox.setValue(savedModelDisplay);
            } else if (savedModel != null && modelComboBox.getItems().contains(ModelLabels.displayName(savedModel))) {
                currentSelectedModel = savedModel;
                modelComboBox.setValue(ModelLabels.displayName(savedModel));
            } else {
                // Fallback to default if saved model not found
                currentSelectedModel = GenerationModel.getDefault();
                modelComboBox.setValue(ModelLabels.displayName(currentSelectedModel));
            }

            safetyToleranceSlider.setValue(config.generation().getDefaultSafetyTolerance());
            imageCountSlider.setValue(config.generation().getDefaultImageCount());
            bflPromptUpsamplingCheckBox.setSelected(config.generation().getDefaultBflPromptUpsampling());

            // Only "minimal" and "high" exist; a saved "low" from earlier versions becomes "minimal"
            geminiThinkingLevelChoiceBox.setValue(
                    "high".equalsIgnoreCase(config.generation().getDefaultGeminiThinkingLevel()) ? "High" : "Minimal");
            parameters.setGoogleSearch(config.generation().getDefaultGoogleSearchEnabled());
        } catch (Exception e) {
            logger.error("Failed to load the choices of the generation from the configuration", e);
            currentSelectedModel = GenerationModel.getDefault();
            modelComboBox.setValue(ModelLabels.displayName(currentSelectedModel));
            imageCountSlider.setValue(1);
        }
        updateParameterVisibility(currentSelectedModel);
        updateCostDisplay();
    }

    /**
     * Remembers the choices of the group. Another model also clears the attachments when switching from Google to
     * BFL (the file size limits differ), shows its fields and tells the window.
     */
    private void setupPersistenceListeners() {
        modelComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                GenerationModel model = GenerationModel.fromLocalizedDisplayName(newVal);
                if (model != null) {
                    GenerationModel previousModel = currentSelectedModel;
                    currentSelectedModel = model;
                    if (config != null)
                        config.generation().setDefaultModel(GenerationModel.openAiModelId(newVal) != null
                                ? newVal : model.getDisplayName());

                    // Provider switch: Google → BFL → clear attachments
                    if (previousModel != null && previousModel.getProvider() == ApiProvider.GOOGLE_GEMINI
                            && model.getProvider() == ApiProvider.BFL && attachments.clearAll())
                        status.error(I18nManager.getText("status.attachments.cleared.provider"));
                }
                updateParameterVisibility(model);

                // Update multi-image panel with new model's max images
                attachments.setModel(model);
                listener.modelChanged();
            }
            updateCostDisplay();
        });
        safetyToleranceSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && config != null)
                config.generation().setDefaultSafetyTolerance(newVal.intValue());
            updateCostDisplay();
        });
        imageCountSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && config != null)
                config.generation().setDefaultImageCount((int) newVal.doubleValue());
            updateCostDisplay();
        });
        videoDurationChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && config != null)
                config.generation().setDefaultVideoDuration(newVal);
        });
        bflPromptUpsamplingCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && config != null)
                config.generation().setDefaultBflPromptUpsampling(newVal);
        });
        geminiThinkingLevelChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && config != null)
                config.generation().setDefaultGeminiThinkingLevel(newVal.toLowerCase());
        });
    }

    /**
     * Updates the price of the order: of the chosen model with Google Search grounding, times the count; a video is
     * billed by its actual length, so its price is an estimate.
     */
    private void updateCostDisplay() {
        try {
            GenerationModel model = GenerationModel.fromLocalizedDisplayName(modelComboBox.getValue());
            Boolean googleSearch = parameters.googleSearch();
            GenerationCost.Quote quote = GenerationCost.quote(model, selectedVideoSeconds(), googleSearch,
                    (int) imageCountSlider.getValue());
            costText.set(!quote.known() ? I18nManager.getText("cost.unknown")
                    : String.format(model != null && model.isOmniModel() ? "~$%.2f" : "$%.2f", quote.total()));
        } catch (Exception e) {
            logger.warn("Error updating cost display", e);
            costText.set("$0.00");
        }
    }

    /**
     * Updates the image count label with the current slider value (integer only).
     */
    private void updateImageCountLabel(int value) {
        if (imageCountLabel != null) {
            imageCountLabel.setText(I18nManager.getText("params.image.count", String.valueOf(value)));
        }
    }

    /**
     * Updates the summary of the Generation group: count and model, for a video its duration and model, then the number
     * of attached images.
     */
    private void updateGenerationSummary() {
        if (generationSummaryModel == null || generationSummaryAttachments == null)
            return;

        String model = modelComboBox.getValue();
        art.moodframe.model.GenerationModel generationModel =
                art.moodframe.model.GenerationModel.fromLocalizedDisplayName(model);
        String family = model != null ? art.moodframe.model.ModelFamilies.familyOf(model) : "";
        Integer seconds = selectedVideoSeconds();
        if (generationModel != null && generationModel.isVideoModel() && seconds != null)
            generationSummaryModel.setText(I18nManager.getText("params.generation.summary.duration", seconds, family));
        else
            generationSummaryModel.setText(I18nManager.getText("params.generation.summary.count",
                    (int) Math.round(imageCountSlider.getValue()), family));

        int attached = attachments.panelCount();
        generationSummaryAttachments.setText(switch (attached) {
            case 0 -> I18nManager.getText("params.generation.summary.attachments.none");
            case 1 -> I18nManager.getText("params.generation.summary.attachments.one");
            default -> I18nManager.getText("params.generation.summary.attachments.many", attached);
        });
    }

    /**
     * Populates the model ComboBox with models from enabled providers only.
     * Also updates UI state based on availability of models.
     */
    private void populateAvailableModels() {
        if (config == null) {
            logger.error("ApplicationConfig not available, cannot populate models");
            return;
        }

        // Get current selection before clearing
        String currentSelection = modelComboBox.getValue();

        // Clear existing items
        modelComboBox.getItems().clear();

        logger.info("Provider status - BFL: enabled={}, hasKey={} | Gemini: enabled={}, hasKey={}",
                    config.accounts().isEnabled(ApiProvider.BFL), config.accounts().hasApiKey(ApiProvider.BFL),
                    config.accounts().isEnabled(ApiProvider.GOOGLE_GEMINI),
                    config.accounts().hasApiKey(ApiProvider.GOOGLE_GEMINI));

        // Add models from enabled providers with valid API keys
        modelComboBox.getItems().addAll(GenerationService.availableItems(config));

        logger.info("Available models after filtering: {}", modelComboBox.getItems());

        // Update UI state based on model availability
        boolean hasModels = !modelComboBox.getItems().isEmpty();

        if (!hasModels) {
            // No models available - disable all input controls
            logger.warn("No models available - all providers disabled or missing API keys");
            listener.changed(); // This will update input state and disable everything
        } else {
            // Restore previous selection if still available
            if (currentSelection != null && modelComboBox.getItems().contains(currentSelection)) {
                modelComboBox.setValue(currentSelection);
            } else {
                // Select first available model as default
                modelComboBox.setValue(modelComboBox.getItems().get(0));
            }

            // Update input state to potentially enable controls
            listener.changed();
        }

        // Update cost display
        updateCostDisplay();
    }

    /**
     * Updates model names in the ComboBox to use localized names.
     */
    private void updateModelNames() {
        if (modelComboBox == null)
            return;

        // Use the tracked model instead of trying to parse the ComboBox value
        art.moodframe.model.GenerationModel currentModel = currentSelectedModel;

        // If no model is tracked, try to determine it from the current selection
        if (currentModel == null) {
            String currentSelection = modelComboBox.getValue();
            if (currentSelection != null) {

                // Try multiple approaches to find the current model
                currentModel = art.moodframe.model.GenerationModel.fromDisplayName(currentSelection);
                if (currentModel == null) {
                    currentModel = art.moodframe.model.GenerationModel.fromLocalizedDisplayName(currentSelection);
                }

                // If found, start tracking it
                if (currentModel != null) {
                    currentSelectedModel = currentModel;
                }
            }
        }

        // Clear and rebuild with localized names, keeping the filter of the attached images
        String previousItem = modelComboBox.getValue();
        modelComboBox.getItems().clear();
        if (config != null)
            modelComboBox.getItems().addAll(GenerationService.availableItems(config, attachments.count()));

        // Restore selection using localized name (an OpenAI model keeps its item name)
        if (currentModel != null) {
            String localizedName = art.moodframe.model.GenerationModel.openAiModelId(previousItem) != null
                    ? previousItem : ModelLabels.displayName(currentModel);

            if (modelComboBox.getItems().contains(localizedName)) {
                // Temporarily suppress selection events to avoid recursion
                modelComboBox.setValue(localizedName);
            } else if (!modelComboBox.getItems().isEmpty()) {
                // Only try to set default if list is not empty
                logger.warn("Localized model name '{}' not found in items: {}", localizedName,
                        modelComboBox.getItems());
                // Fallback to default if current model not available
                String defaultLocalized = ModelLabels.displayName(art.moodframe.model.GenerationModel.getDefault());
                if (modelComboBox.getItems().contains(defaultLocalized)) {
                    modelComboBox.setValue(defaultLocalized);
                } else {
                    // Default model not available, select first available model
                    modelComboBox.setValue(modelComboBox.getItems().get(0));
                }
            } else {
                // List is empty - no providers enabled
                logger.info("Model list is empty - no providers enabled");
                modelComboBox.setValue(null);
            }
        } else if (!modelComboBox.getItems().isEmpty()) {
            // No current model tracked, but list has items - select first available
            logger.info("No current model tracked, selecting first available model");
            modelComboBox.setValue(modelComboBox.getItems().get(0));
        } else {
            // List is empty - no providers enabled
            logger.info("Model list is empty - no providers enabled, cannot select default");
            modelComboBox.setValue(null);
        }
    }

    /**
     * Filters the model dropdown based on the number of attached images.
     * Models that don't support the current image count are removed.
     * If no compatible models remain, shows an error message.
     *
     * @param imageCount the current number of attached images
     */
    private void filterModelsByImageCount(int imageCount) {
        if (modelComboBox == null) {
            return;
        }

        // If no images attached, restore full model list
        if (imageCount == 0) {
            populateAvailableModels();
            return;
        }

        // Get current selection before filtering
        String currentSelection = modelComboBox.getValue();

        // Clear and rebuild with the models of the usable providers that take this many images
        modelComboBox.getItems().setAll(GenerationService.availableItems(config, imageCount));

        logger.info("Filtered models for {} images: {}", imageCount, modelComboBox.getItems());

        // Handle case when no compatible models remain
        if (modelComboBox.getItems().isEmpty()) {
            showNotification(I18nManager.getText("error.too.many.images"));
            // Restore previous model list so user can remove images
            populateAvailableModels();
            return;
        }

        // Try to keep current selection if still compatible
        if (currentSelection != null && modelComboBox.getItems().contains(currentSelection)) {
            modelComboBox.setValue(currentSelection);
        } else {
            // Current model not compatible - select first available
            String newModel = modelComboBox.getItems().get(0);
            modelComboBox.setValue(newModel);
            showNotification(java.text.MessageFormat.format(
                    I18nManager.getText("info.model.switched"), newModel));
        }

        // Update max images in the panel based on new model
        attachments.setModel(GenerationModel.fromLocalizedDisplayName(modelComboBox.getValue()));
    }

    /**
     * Updates model selection based on image attachment state.
     * When an image is attached, only compatible models are shown and incompatible
     * models are auto-switched.
     */
    private void updateModelSelectionForAttachment(boolean hasAttachment) {
        String currentSelection = modelComboBox.getValue();

        if (hasAttachment) {
            // Filter to only show the models of the usable providers that take the attached images
            modelComboBox.getItems().setAll(GenerationService.availableItems(config, Math.max(1, attachments.count())));

            // Check if current selection is compatible
            if (currentSelection == null || !modelComboBox.getItems().contains(currentSelection)) {
                // Auto-switch to the default model, which edits images (FLUX 3 Image)
                String defaultName = ModelLabels.displayName(art.moodframe.model.GenerationModel.getDefault());

                // Make sure the item exists in the ComboBox before setting it
                if (modelComboBox.getItems().contains(defaultName)) {
                    modelComboBox.setValue(defaultName);
                    status.status(I18nManager.getText("status.model.switched", defaultName));
                } else {
                    // Set first available compatible model
                    if (!modelComboBox.getItems().isEmpty()) {
                        modelComboBox.setValue(modelComboBox.getItems().get(0));
                    }
                }

                // Update cost display since model changed
                updateCostDisplay();
            } else {
                // Current model is compatible, keep it selected
                modelComboBox.setValue(currentSelection);
            }
        } else {
            // Restore models based on enabled providers when no attachment
            populateAvailableModels();
        }
    }

    /**
     * Updates parameter visibility based on the selected provider (BFL or Gemini).
     *
     * <p>Provider-specific parameters:</p>
     * <ul>
     *   <li><b>BFL-only parameters:</b> Safety Tolerance, Seed, Prompt Upsampling, Image Count, Style Presets</li>
     *   <li><b>Gemini-only parameters:</b> Currently none (imageSize is not exposed in UI)</li>
     *   <li><b>Common parameters:</b> Model, Aspect Ratio</li>
     * </ul>
     *
     * @param generationModel the selected model, null for an unknown item
     */
    private void updateParameterVisibility(art.moodframe.model.GenerationModel generationModel) {
        boolean isVideo = generationModel != null && generationModel.isVideoModel();

        // The provider of the model (an unknown item counts as BFL, the provider of the default model)
        ApiProvider provider = generationModel != null ? generationModel.getProvider() : ApiProvider.BFL;

        boolean isBFL = (provider == ApiProvider.BFL);
        boolean isGemini = (provider == ApiProvider.GOOGLE_GEMINI) && !isVideo;

        logger.info("Updating parameter visibility for provider: {}, isVideo: {}", provider, isVideo);

        // The fields of BFL and Gemini
        parameters.show(generationModel);

        // Video Parameters (the duration choices depend on the model)
        if (videoParametersContainer != null) {
            videoParametersContainer.setVisible(isVideo);
            videoParametersContainer.setManaged(isVideo);

            if (isVideo && videoDurationChoiceBox != null && generationModel != null) {
                java.util.List<String> choices = generationModel.getVideoDurationChoices().stream()
                        .map((Integer seconds) -> seconds + "s").toList();
                String selected = videoDurationChoiceBox.getValue();
                if (!choices.equals(videoDurationChoiceBox.getItems())) {
                    videoDurationChoiceBox.getItems().setAll(choices);
                }
                videoDurationChoiceBox.setValue(choices.contains(selected) ? selected
                        : choices.contains("8s") ? "8s" : choices.get(0));

                if (videoDurationTooltip != null)
                    videoDurationTooltip.setText(I18nManager.getText("omni.duration.hint"));
            }
        }

        // Image count: disabled for video (always 1 video per request)
        if (imageCountSlider != null) {
            imageCountSlider.setDisable(isVideo);
            if (isVideo) {
                imageCountSlider.setValue(1);
            }
        }

        /*  a video shows its duration in place of the count  */
        if (imageCountContainer != null) {
            imageCountContainer.setVisible(!isVideo);
            imageCountContainer.setManaged(!isVideo);
        }

        logger.debug("Parameter visibility updated: BFL={}, Gemini={}, Video={}", isBFL, isGemini, isVideo);
    }

    /** The video length chosen in the duration choice, or null if there is none. */
    private Integer selectedVideoSeconds() {
        if (videoDurationChoiceBox == null || videoDurationChoiceBox.getValue() == null)
            return null;
        return Integer.valueOf(videoDurationChoiceBox.getValue().replace("s", ""));
    }

    /**
     * The parameters of the model as set in the panel; a seed that is no number counts as random.
     */
    private art.moodframe.service.generation.GenerationOrder.Parameters modelParameters() {
        Long seed = null;
        String seedText = seedTextField.getText();
        if (seedText != null && !seedText.trim().isEmpty() && !seedText.trim().equals("random")) {
            try {
                seed = Long.parseLong(seedText.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid seed value entered: {}, using random generation", seedText);
            }
        }

        /*  The thinking level is disabled while an image is attached, then the API default is used  */
        String thinkingLevel = geminiThinkingLevelChoiceBox != null && !geminiThinkingLevelChoiceBox.isDisabled()
                ? geminiThinkingLevelChoiceBox.getValue() : null;
        return new art.moodframe.service.generation.GenerationOrder.Parameters(seed,
                (int) safetyToleranceSlider.getValue(),
                flux2SafetySlider != null ? (int) flux2SafetySlider.getValue() : null,
                bflPromptUpsamplingCheckBox != null && bflPromptUpsamplingCheckBox.isSelected(), thinkingLevel,
                parameters.googleSearch(),
                flux2GuidanceSlider != null ? flux2GuidanceSlider.getValue() : null,
                flux2StepsSlider != null ? (int) flux2StepsSlider.getValue() : null, selectedVideoSeconds());
    }

    /**
     * Handles the attach image button click event.
     */
    @FXML
    private void onAttachImageClicked() {
        attachments.choose();
    }

    /**
     * Handles the remove image button click event.
     */
    @FXML
    private void onRemoveImageClicked() {
        attachments.remove();
    }

    /**
     * Handles the clear seed button click event.
     */
    @FXML
    private void onClearSeedClicked() {

        // Clear the seed text field
        seedTextField.clear();

        status.status(I18nManager.getText("status.seed.cleared"));
    }

    /*  what the group does when the attached images change  */
    private GenerationAttachments.Host attachmentHost() {
        return new GenerationAttachments.Host() {
            @Override
            public void imageAttached() {
                imageCountSlider.setValue(1);
                updateImageCountLabel(1);
            }

            @Override
            public void attachmentChanged(boolean hasAttachment, File firstImage) {
                updateModelSelectionForAttachment(hasAttachment);
                listener.attachmentsChanged(firstImage);
            }

            @Override
            public void imagesChanged(int imageCount) {
                filterModelsByImageCount(imageCount);
                updateCostDisplay();
                updateGenerationSummary();
            }

            @Override
            public void moodFromImage(File image) {
                listener.moodFromImage(image);
            }
        };
    }

    /** Chooses the model of a chat entry, as long as it is offered. */
    private void reuseModel(ChatEntry entry) {
        String name = ModelLabels.entryName(entry.getModel());
        if (name != null && modelComboBox.getItems().contains(name))
            modelComboBox.setValue(name);
    }

    /** The fixed id of the selected model, null without selection. */
    public String selectedModelId() {
        return modelComboBox != null ? art.moodframe.model.GenerationModel.idOf(modelComboBox.getValue()) : null;
    }

    /**
     * Shows a notification message to the user.
     *
     * @param message the message to show
     */
    private void showNotification(String message) {
        // For now, just log it. In the future, could show a toast notification
        logger.info("Notification: {}", message);
    }

}
