package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GenerationCost;
import art.moodframe.model.GenerationModel;
import art.moodframe.service.chat.ImageAttachmentService;
import art.moodframe.service.generation.GenerationService;
import art.moodframe.service.generation.ModelLabels;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.function.Consumer;

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
    @FXML
    private CheckBox videoAudioCheckBox;

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

    private art.moodframe.ui.component.GeminiAdvancedFeaturesSection geminiAdvancedFeaturesSection;

    // Image attachment for image-to-image generation, the first of the attached images
    private File attachedImageFile;

    // Drag and drop handler for image attachments
    private art.moodframe.ui.dnd.DragDropHandler dragDropHandler;

    // Multi-Image Attachment Panel (FLUX.2 & Gemini)
    private art.moodframe.ui.component.MultiImageAttachmentPanel multiImagePanel;

    /*  the price of the order, which the send button shows  */
    private final StringProperty costText = new SimpleStringProperty("$0.00");

    @FXML
    private void initialize() {
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
            updateSafetyToleranceLabel(roundedValue);
        });
        imageCountSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int roundedValue = (int) Math.round(newVal.doubleValue());
            if (Math.abs(newVal.doubleValue() - roundedValue) > 0.01)
                imageCountSlider.setValue(roundedValue);
            updateImageCountLabel(roundedValue);
        });
        updateSafetyToleranceLabel((int) safetyToleranceSlider.getValue());
        updateImageCountLabel((int) imageCountSlider.getValue());

        // The video durations (the items follow the selected video model)
        videoDurationChoiceBox.getItems().addAll("3s", "4s", "5s", "6s", "7s", "8s", "9s", "10s");
        videoDurationChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> updateCostDisplay());
        videoAudioCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> updateCostDisplay());

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
     * @param generationService tells the prompt limit of a model (may be null: the limits known without it)
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
        if (config != null) {
            videoDurationChoiceBox.setValue(config.generation().getDefaultVideoDuration());
            videoAudioCheckBox.setSelected(config.generation().isVideoAudioEnabled());
        }
        else
            videoDurationChoiceBox.setValue("8s");
        setupGeminiAdvancedFeatures();
        setupFlux2Parameters();
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

        setupDragAndDrop();
        setupMultiImageAttachmentPanel();
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
        return multiImagePanel;
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
        return attachedImageFile;
    }

    /** Whether an image is attached. */
    public boolean hasAttachment() {
        return attachedImageFile != null;
    }

    /**
     * Gets the prompt limit of the chosen model in characters, as its provider tells it.
     *
     * @return the limit
     */
    public int promptLimit() {
        String modelId = selectedModelId();
        return generationService != null ? generationService.promptLimit(modelId)
                : GenerationService.maxPromptLength(modelId);
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
        uploadImageButton.setDisable(!enableInput);
        removeImageButton.setDisable(!enableInput);
        if (multiImagePanel != null)
            multiImagePanel.setDisable(!enableInput);
        multiImageAttachmentContainer.setDisable(!enableInput);
        noImagePlaceholder.setDisable(!enableInput);
        noImageLabel.setDisable(!enableInput);
        imagePreviewContainer.setDisable(!enableInput);
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
        updateSafetyToleranceLabel((int) safetyToleranceSlider.getValue());
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
        return attachedBase64Images();
    }

    /**
     * Attaches an image as the edit pencil of an image does: into the next free square, else a new one; the count goes
     * to 1 and the model and the aspect ratio follow the attachment.
     *
     * @param imageFile the image
     * @return whether the image was attached, false if the maximum number of images is reached
     */
    public boolean attach(File imageFile) {
        return attachImageDirectly(imageFile);
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
        if (multiImagePanel != null)
            multiImagePanel.dispose();
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
            if (geminiAdvancedFeaturesSection != null)
                geminiAdvancedFeaturesSection.setSearchGroundingEnabled(
                        config.generation().getDefaultGoogleSearchEnabled());
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
                    if (previousModel != null && multiImagePanel != null && multiImagePanel.hasAttachments()
                            && previousModel.getProvider() == ApiProvider.GOOGLE_GEMINI
                            && model.getProvider() == ApiProvider.BFL) {
                        multiImagePanel.clearAllAttachments();
                        attachedImageFile = null;
                        status.error(I18nManager.getText("status.attachments.cleared.provider"));
                    }
                }
                updateParameterVisibility(model);

                // Update multi-image panel with new model's max images
                if (multiImagePanel != null && model != null)
                    multiImagePanel.setCurrentModel(model);
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
            Boolean googleSearch = geminiAdvancedFeaturesSection != null
                    ? geminiAdvancedFeaturesSection.isSearchGroundingEnabled() : null;
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
     * Sets up drag and drop on the square of the single attachment.
     */
    private void setupDragAndDrop() {
        if (imageAttachmentService == null) {
            logger.warn("Cannot set up drag and drop: no ImageAttachmentService");
            return;
        }
        dragDropHandler = new art.moodframe.ui.dnd.DragDropHandler(imageAttachmentService, imagePreviewContainer,
                this::handleDroppedImage, status::failure, this::maxFileSize);
        dragDropHandler.initializeDragAndDrop();
        logger.info("Drag and drop initialized for image attachments");
    }

    /*  the file size limit of the provider of the chosen model, that of BFL without  */
    private long maxFileSize() {
        return GenerationModel.getMaxFileSize(currentSelectedModel != null ? currentSelectedModel.getProvider()
                : ApiProvider.BFL);
    }

    /**
     * Sets up the panel of the attached images for FLUX.2 and Gemini.
     */
    private void setupMultiImageAttachmentPanel() {
        multiImagePanel = new art.moodframe.ui.component.MultiImageAttachmentPanel();
        multiImagePanel.setAttachmentService(imageAttachmentService);
        multiImagePanel.setOnError(status::failure);
        Consumer<File> moodFromImage = listener::moodFromImage;
        multiImagePanel.setOnMoodFromImage(moodFromImage);

        // When first image is attached, apply same logic as single image
        multiImagePanel.setOnFirstImageAttached(() -> Platform.runLater(() -> {
            updateAttachmentState();
            imageCountSlider.setValue(1);
            updateImageCountLabel(1);
        }));

        // When all images removed, reset state
        multiImagePanel.setOnAllImagesRemoved(() -> Platform.runLater(() -> {
            attachedImageFile = null;
            updateAttachmentState();
        }));

        multiImagePanel.setOnImagesChanged(images -> {
            // The first image is the one of the single attachment
            attachedImageFile = images != null && !images.isEmpty() ? images.get(0).getImageFile() : null;

            // Filter models based on current image count
            int imageCount = images != null ? (int) images.stream().filter(img -> img != null).count() : 0;
            Platform.runLater(() -> {
                filterModelsByImageCount(imageCount);
                updateCostDisplay();
                updateGenerationSummary();
            });
        });
        if (currentSelectedModel != null)
            multiImagePanel.setCurrentModel(currentSelectedModel);
        multiImageAttachmentContainer.getChildren().setAll(multiImagePanel);
        logger.info("Multi-image attachment panel initialized");
    }

    /**
     * Updates the group once the attachment changed: the remove button, the models that take images and the aspect
     * ratio of the first image (an added image keeps the aspect ratio of the first one).
     */
    private void updateAttachmentState() {
        boolean hasAttachment = attachedImageFile != null;
        removeImageButton.setDisable(!hasAttachment);
        updateModelSelectionForAttachment(hasAttachment);
        int attachmentCount = multiImagePanel != null ? multiImagePanel.getAttachmentCount() : 1;
        listener.attachmentsChanged(hasAttachment && attachmentCount == 1 ? attachedImageFile : null);
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

            if (config == null) {
                logger.warn("ApplicationConfig not available, FLUX.2 parameters will use defaults");
                return;
            }

            // Load saved values from config
            double savedGuidance = config.generation().getFlux2FlexGuidance();
            int savedSteps = config.generation().getFlux2FlexSteps();
            boolean savedGroundingSearch = config.generation().getFlux2GroundingSearchEnabled();

            // Setup Guidance slider
            if (flux2GuidanceSlider != null && flux2GuidanceLabel != null) {
                flux2GuidanceSlider.setValue(savedGuidance);
                updateFlux2GuidanceLabel(savedGuidance);

                // Listener for label updates and config persistence
                flux2GuidanceSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                    double value = newVal.doubleValue();
                    updateFlux2GuidanceLabel(value);
                    config.generation().setFlux2FlexGuidance(value);
                    updateCostDisplay(); // Cost may depend on parameters
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
                    updateCostDisplay();
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
                    flux2SafetyLabel.setText(art.moodframe.util.I18nManager.getText("params.flux2.safety"));
                }
            }

            // Note: Grounding Search checkbox removed - FLUX.2 [max] has built-in automatic grounding
            // that activates based on prompt content (e.g., "current", "today", "latest")

            // Initially hidden - visibility controlled by updateFlux2ParameterVisibility
            if (flux2ParametersContainer != null) {
                flux2ParametersContainer.setVisible(false);
                flux2ParametersContainer.setManaged(false);
            }

            logger.info("FLUX.2 parameters initialized: guidance={}, steps={}, groundingSearch={}",
                        savedGuidance, savedSteps, savedGroundingSearch);

        } catch (Exception e) {
            logger.error("Failed to setup FLUX.2 parameters", e);
        }
    }

    /**
     * Updates the FLUX.2 [flex] Guidance label with the current value.
     *
     * @param value the guidance value (1.5-10.0)
     */
    private void updateFlux2GuidanceLabel(double value) {
        if (flux2GuidanceLabel != null) {
            // Use getText with parameter - i18n uses MessageFormat {0} syntax
            String labelText = art.moodframe.util.I18nManager.getText(
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
            String labelText = art.moodframe.util.I18nManager.getText(
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
    private void updateSafetyToleranceLabel(int value) {
        if (safetyToleranceLabel != null) {
            String description = getSafetyToleranceDescription(value);
            safetyToleranceLabel.setText(I18nManager.getText("params.safety", description));
        }
    }

    /**
     * Gets a user-friendly description for safety tolerance values.
     *
     * @param value the safety tolerance value (0-6)
     * @return descriptive text for the tolerance level
     */
    private String getSafetyToleranceDescription(int value) {
        return switch (value) {
            case 0 -> I18nManager.getText("params.safety.most.strict");
            case 1 -> I18nManager.getText("params.safety.very.strict");
            case 2 -> I18nManager.getText("params.safety.strict");
            case 3 -> I18nManager.getText("params.safety.balanced");
            case 4 -> I18nManager.getText("params.safety.permissive");
            case 5 -> I18nManager.getText("params.safety.very.permissive");
            case 6 -> I18nManager.getText("params.safety.most.permissive");
            default -> I18nManager.getText("params.safety.unknown");
        };
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

        int attachments = multiImagePanel != null ? multiImagePanel.getAttachmentCount() : 0;
        generationSummaryAttachments.setText(switch (attachments) {
            case 0 -> I18nManager.getText("params.generation.summary.attachments.none");
            case 1 -> I18nManager.getText("params.generation.summary.attachments.one");
            default -> I18nManager.getText("params.generation.summary.attachments.many", attachments);
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
            modelComboBox.getItems().addAll(GenerationService.availableItems(config, attachedImageCount()));

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
        if (multiImagePanel != null) {
            String selectedModelName = modelComboBox.getValue();
            art.moodframe.model.GenerationModel selectedModel =
                    art.moodframe.model.GenerationModel.fromLocalizedDisplayName(selectedModelName);
            if (selectedModel != null) {
                multiImagePanel.setCurrentModel(selectedModel);
            }
        }
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
            modelComboBox.getItems().setAll(GenerationService.availableItems(config, Math.max(1, attachedImageCount())));

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

    /*  the number of attached images, which filters the models  */
    private int attachedImageCount() {
        if (attachedImageFile == null)
            return 0;
        return multiImagePanel != null ? Math.max(1, multiImagePanel.getAttachmentCount()) : 1;
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

    /**
     * Updates FLUX.2 specific parameter visibility based on the selected model.
     * <ul>
     *   <li><b>FLUX.2 [max]:</b> Shows Grounding Search checkbox</li>
     *   <li><b>FLUX.2 [flex]:</b> Shows Guidance and Steps sliders</li>
     *   <li><b>Other models:</b> Hides all FLUX.2 parameters</li>
     * </ul>
     *
     * @param generationModel the selected model, null for an unknown item
     */
    private void updateFlux2ParameterVisibility(art.moodframe.model.GenerationModel generationModel) {
        boolean isFlux2 = generationModel != null && (generationModel.isFlux2Model() || generationModel.isFlux3Model());
        boolean isFlux2Max = generationModel == art.moodframe.model.GenerationModel.FLUX2_MAX;
        boolean isFlux2Flex = generationModel != null && generationModel.isFlux2Flex();

        // Show main FLUX.2 container for FLUX.2 and FLUX 3 models (FLUX 3 only uses the safety slider)
        flux2ParametersContainer.setVisible(isFlux2);
        flux2ParametersContainer.setManaged(isFlux2);

        // FLUX.2 [max] Grounding Search - only for FLUX2_MAX
        if (flux2GroundingSearchContainer != null) {
            flux2GroundingSearchContainer.setVisible(isFlux2Max);
            flux2GroundingSearchContainer.setManaged(isFlux2Max);
        }

        // FLUX.2 [flex] Guidance slider - only for FLUX2_FLEX
        if (flux2GuidanceContainer != null) {
            flux2GuidanceContainer.setVisible(isFlux2Flex);
            flux2GuidanceContainer.setManaged(isFlux2Flex);
        }

        // FLUX.2 [flex] Steps slider - only for FLUX2_FLEX
        if (flux2StepsContainer != null) {
            flux2StepsContainer.setVisible(isFlux2Flex);
            flux2StepsContainer.setManaged(isFlux2Flex);
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

        logger.debug("FLUX.2 parameter visibility: isFlux2={}, isFlux2Max={}, isFlux2Flex={}",
                     isFlux2, isFlux2Max, isFlux2Flex);
    }

    /** The Base64 data of the attached images that are ready to be sent, in their order. */
    private java.util.List<String> attachedBase64Images() {
        if (multiImagePanel == null || multiImagePanel.getAttachedImages() == null)
            return java.util.List.of();
        return multiImagePanel.getAttachedImages().stream()
                .filter((img) -> img != null && img.isReadyForTransmission())
                .map(art.moodframe.model.AttachedImage::getBase64Data)
                .toList();
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
                geminiAdvancedFeaturesSection != null ? geminiAdvancedFeaturesSection.isSearchGroundingEnabled() : null,
                flux2GuidanceSlider != null ? flux2GuidanceSlider.getValue() : null,
                flux2StepsSlider != null ? (int) flux2StepsSlider.getValue() : null, selectedVideoSeconds());
    }

    /**
     * Handles a successfully dropped image.
     *
     * @param attachedImage the attached image with metadata
     */
    private void handleDroppedImage(art.moodframe.model.AttachedImage attachedImage) {
        if (attachedImage == null || attachedImage.getImageFile() == null) {
            logger.error("Invalid attached image received from drag and drop");
            return;
        }

        Platform.runLater(() -> {
            try {
                File imageFile = attachedImage.getImageFile();

                // Load and display the image, decoded in the background
                Image image = new Image(imageFile.toURI().toString(), true);
                attachmentPreviewImage.setImage(image);

                // Show image preview, hide placeholder
                attachmentPreviewImage.setVisible(true);
                attachmentPreviewImage.setManaged(true);
                noImagePlaceholder.setVisible(false);
                noImagePlaceholder.setManaged(false);

                // Store the file reference for API use
                attachedImageFile = imageFile;

                // Auto-set image count to 1 for image-to-image generation
                imageCountSlider.setValue(1);
                updateImageCountLabel(1);

                // Update UI to reflect attachment
                updateAttachmentState();

                status.status(I18nManager.getText("status.image.attached") + ": " + imageFile.getName());
                logger.info("Image attached via drag and drop: {}", imageFile.getName());

            } catch (Exception e) {
                logger.error("Failed to process dropped image", e);
                status.failure(I18nManager.getText("error.image.processing.failed"));
            }
        });
    }

    /**
     * Handles the attach image button click event.
     */
    @FXML
    private void onAttachImageClicked() {
        status.status(I18nManager.getText("status.selecting.image"));

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Image to Attach");

        // Set file filters for supported image formats
        FileChooser.ExtensionFilter imageFilter = new FileChooser.ExtensionFilter(
                "Image Files", "*.png", "*.jpg", "*.jpeg", "*.webp");
        fileChooser.getExtensionFilters().add(imageFilter);

        // Show the file picker dialog
        Stage stage = (Stage) uploadImageButton.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            try {
                // Validate file size against provider-dependent limit
                long maxSize = art.moodframe.model.GenerationModel.getMaxFileSize(
                    currentSelectedModel != null ? currentSelectedModel.getProvider()
                                                 : art.moodframe.model.ApiProvider.BFL);
                if (selectedFile.length() > maxSize) {
                    int maxMB = (int) (maxSize / (1024 * 1024));
                    status.error(String.format(I18nManager.getText("status.error.file.too.large"), maxMB));
                    return;
                }

                // Load and display the image, decoded in the background
                Image image = new Image(selectedFile.toURI().toString(), true);
                attachmentPreviewImage.setImage(image);

                // Show image preview, hide placeholder
                attachmentPreviewImage.setVisible(true);
                attachmentPreviewImage.setManaged(true);
                noImagePlaceholder.setVisible(false);
                noImagePlaceholder.setManaged(false);

                // Store the file reference for API use
                attachedImageFile = selectedFile;

                // Auto-set image count to 1 for image-to-image generation
                imageCountSlider.setValue(1);
                updateImageCountLabel(1);

                // Update UI to reflect attachment
                updateAttachmentState();

                status.status(I18nManager.getText("status.image.attached"));

            } catch (Exception e) {
                status.error(I18nManager.getText("status.error.image.load"));
                logger.error("Failed to load selected image: {}", selectedFile.getPath(), e);
            }
        } else {
            status.status(I18nManager.getText("status.error.no.image"));
        }
    }

    /**
     * Handles the remove image button click event.
     */
    @FXML
    private void onRemoveImageClicked() {

        // Clear the attached image
        attachedImageFile = null;
        attachmentPreviewImage.setImage(null);

        // Show placeholder, hide image preview
        attachmentPreviewImage.setVisible(false);
        attachmentPreviewImage.setManaged(false);
        noImagePlaceholder.setVisible(true);
        noImagePlaceholder.setManaged(true);

        // Update UI state
        updateAttachmentState();

        status.status(I18nManager.getText("status.image.removed"));
    }

    /**
     * Attaches an image file directly without showing file dialog.
     * Used for Edit functionality where the image is already known.
     *
     * Behavior:
     * 1) If only one image attached with no empty slot: replace it
     * 2) If there's an empty slot: fill it with the new image
     * 3) If 2+ images and no empty slot: create new slot and attach (if under max limit)
     *
     * @return whether the image was attached, false if the maximum number of images is reached
     */
    private boolean attachImageDirectly(File imageFile) {
        // Use the MultiImageAttachmentPanel for image attachment (it shrinks images above the size limit)
        if (multiImagePanel != null) {
            int targetSlot = multiImagePanel.findTargetSlotForNewImage();
            if (targetSlot >= 0) {
                multiImagePanel.attachImageToSlot(targetSlot, imageFile);
            } else {
                // Max images reached - show warning
                logger.warn("Cannot attach image: maximum number of images reached");
                status.status(I18nManager.getText("attachment.max.reached",
                    multiImagePanel.getAttachmentCount()));
                return false;
            }
        }

        // Store the file reference for API use (legacy support)
        attachedImageFile = imageFile;

        // Auto-set image count to 1 for image-to-image generation
        imageCountSlider.setValue(1);
        updateImageCountLabel(1);

        // Update UI to reflect attachment (auto-adjusts model, aspect ratio, style)
        updateAttachmentState();
        return true;
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

    /** Chooses the model of a chat entry, as long as it is offered. */
    private void reuseModel(ChatEntry entry) {
        art.moodframe.model.GenerationModel model = art.moodframe.model.GenerationModel.fromDisplayName(entry.getModel());
        String name = model != null ? ModelLabels.displayName(model) : entry.getModel();
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
