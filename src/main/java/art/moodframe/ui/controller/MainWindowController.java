package art.moodframe.ui.controller;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.service.ChatHistoryService;
import art.moodframe.util.I18nChangeListener;
import art.moodframe.util.I18nManager;
import art.moodframe.ui.component.MemoryStatusComponent;
import art.moodframe.ui.cell.ChatListCell;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * JavaFX controller for the main application window.
 * 
 * <p>
 * This controller implements the MVVM pattern and handles all user interactions
 * with the main window. It coordinates between the UI components and the
 * business
 * logic layer through the ServiceFactory.
 * </p>
 * 
 * <p>
 * <strong>FXML Integration:</strong> This controller is linked to
 * MainWindow.fxml
 * and uses JavaFX FXML annotations for component injection.
 * </p>
 * 
 * <p>
 * <strong>External References:</strong>
 * </p>
 * <ul>
 * <li><a href=
 * "https://openjfx.io/javadoc/21/javafx.fxml/javafx/fxml/doc-files/introduction_to_fxml.html">JavaFX
 * FXML Guide</a></li>
 * <li><a href=
 * "https://openjfx.io/javadoc/21/javafx.controls/module-summary.html">JavaFX
 * Controls API</a></li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class MainWindowController implements I18nChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(MainWindowController.class);

    /**
     * Maximum number of ChatEntryCard components to keep in memory.
     * When this limit is exceeded, oldest cards are disposed to prevent memory accumulation.
     * This provides 90% of ListView's memory benefits without cell recycling complexity.
     */
    private static final int MAX_VISIBLE_ENTRY_CARDS = 50;

    // Main Layout
    @FXML
    private BorderPane mainBorderPane;

    // Header Controls
    @FXML
    private Button settingsButton;
    @FXML
    private Label appVersionLabel;

    // Sidebar Controls
    @FXML
    private SplitPane mainSplitPane;
    @FXML
    private Button newChatButton;
    @FXML
    private ToggleButton trashVisibilityToggle;
    @FXML
    private ListView<Chat> chatListView;

    // Chat Area Controls
    @FXML
    private SplitPane chatInputSplitPane;
    @FXML
    private StackPane chatAreaStackPane;
    @FXML
    private ListView<ChatEntry> chatEntriesListView;
    @FXML
    private HBox welcomeContainer;
    @FXML
    private Label welcomeLabel;

    // Input Area Controls
    @FXML
    private TextArea promptTextArea;
    @FXML
    private Label characterCountLabel;

    // Parameter Panel Controls
    @FXML
    private Label parametersTitle;
    @FXML
    private CheckBox bflPromptUpsamplingCheckBox;
    @FXML
    private Label bflPromptUpsamplingHint;
    @FXML
    private VBox geminiThinkingLevelContainer;
    @FXML
    private Label geminiThinkingLevelLabel;
    @FXML
    private ChoiceBox<String> geminiThinkingLevelChoiceBox;
    @FXML
    private Label geminiThinkingLevelHint;
    @FXML
    private VBox flux1SafetyContainer;
    @FXML
    private Slider safetyToleranceSlider;
    @FXML
    private Label safetyToleranceLabel;
    @FXML
    private TextField seedTextField;
    @FXML
    private Button clearSeedButton;
    @FXML
    private Button sendButton;
    @FXML
    private Button brainstormButton;
    @FXML
    private Button uploadImageButton;
    @FXML
    private Button removeImageButton;
    @FXML
    private StackPane imagePreviewContainer;
    @FXML
    private VBox noImagePlaceholder;
    @FXML
    private ImageView attachmentPreviewImage;
    @FXML
    private ChoiceBox<String> modelComboBox;
    @FXML
    private Slider imageCountSlider;
    @FXML
    private Label imageCountLabel;
    @FXML
    private ChoiceBox<String> aspectRatioComboBox;
    @FXML
    private javafx.scene.control.ComboBox<art.moodframe.model.StylePresetItem> stylePresetsComboBox;
    @FXML
    private javafx.scene.control.ComboBox<art.moodframe.model.ColorStyleItem> colorStyleComboBox;
    @FXML
    private Label colorStyleLabel;

    // Parameter Labels for i18n
    @FXML
    private Label modelLabel;
    @FXML
    private Label styleLabel;
    @FXML
    private Label aspectRatioLabel;
    @FXML
    private ChoiceBox<String> modelFamilyChoiceBox;
    @FXML
    private VBox resolutionContainer;
    @FXML
    private Label resolutionLabel;
    @FXML
    private ChoiceBox<String> resolutionChoiceBox;
    private art.moodframe.ui.component.ModelFamilySelector modelFamilySelector;
    @FXML
    private Label seedLabel;
    @FXML
    private Label imageAttachmentLabel;
    @FXML
    private Label noImageLabel;
    @FXML
    private VBox bflParametersContainer;
    @FXML
    private VBox geminiAdvancedFeaturesContainer;
    @FXML
    private VBox promptContextContainer;
    @FXML
    private VBox multiImageAttachmentContainer;
    @FXML
    private VBox imageAttachmentSection;

    // FLUX.2 Specific UI Elements
    @FXML
    private VBox flux2ParametersContainer;
    @FXML
    private VBox flux2GroundingSearchContainer;
    // Note: flux2GroundingSearchCheckBox removed - grounding search is automatic in FLUX.2 [max]
    @FXML
    private Label flux2GroundingSearchHint;
    @FXML
    private VBox flux2GuidanceContainer;
    @FXML
    private Label flux2GuidanceLabel;
    @FXML
    private Slider flux2GuidanceSlider;
    @FXML
    private Label flux2GuidanceHint;
    @FXML
    private VBox flux2StepsContainer;
    @FXML
    private Label flux2StepsLabel;
    @FXML
    private Slider flux2StepsSlider;
    @FXML
    private Label flux2StepsHint;
    @FXML
    private VBox flux2SafetyContainer;
    @FXML
    private Label flux2SafetyLabel;
    @FXML
    private Slider flux2SafetySlider;
    @FXML
    private Label flux2SafetyValueLabel;

    // Video UI Elements
    @FXML
    private VBox videoParametersContainer;
    @FXML
    private VBox videoDurationContainer;
    @FXML
    private ChoiceBox<String> videoDurationChoiceBox;
    @FXML
    private Label videoDurationHint;
    @FXML
    private VBox videoAudioContainer;
    @FXML
    private CheckBox videoAudioCheckBox;
    @FXML
    private Label videoAudioHint;
    @FXML
    private VBox videoNegativePromptContainer;
    @FXML
    private TextArea videoNegativePromptTextArea;

    // Tooltips for i18n
    @FXML
    private Tooltip settingsTooltip;
    @FXML
    private Tooltip newChatTooltip;
    @FXML
    private Tooltip trashToggleTooltip;
    @FXML
    private Tooltip sendTooltip;
    @FXML
    private Tooltip promptUpsamplingTooltip;
    @FXML
    private Tooltip stopTooltip;
    @FXML
    private Tooltip clearSeedTooltip;
    @FXML
    private Tooltip uploadImageTooltip;
    @FXML
    private Tooltip removeImageTooltip;

    // @FXML private Button attachmentButton; // Removed - attachment now in right
    // sidebar

    // Dynamic UI components
    private Label costDisplayLabel;
    private MemoryStatusComponent memoryStatus;
    private art.moodframe.ui.component.GeminiAdvancedFeaturesSection geminiAdvancedFeaturesSection;

    // Status Bar Controls
    @FXML
    private Label statusLabel;
    @FXML
    private Label chatTitleLabel;
    @FXML
    private Label chatTotalCostLabel;
    @FXML
    private Label sessionInfoLabel;
    @FXML
    private FontIcon apiKeyStatusIcon;
    @FXML
    private Label apiKeyStatusLabel;

    // Chat Management
    private ObservableList<Chat> chatList;
    private FilteredList<Chat> filteredChatList;
    private Chat currentChat;
    private boolean suppressChatSelectionEvents = false; // Flag to prevent chat reload during filter updates
    private boolean wasScrolledToBottom = true; // Track if user was at bottom before update
    private int lastChatEntriesCount = 0; // Track number of entries to detect new ones

    // Services
    private art.moodframe.service.RequestResponseManager requestResponseManager;
    private ChatHistoryService chatHistory;
    private art.moodframe.service.TrashCleanupService trashCleanupService;
    private art.moodframe.config.ApplicationConfig config;

    // Image attachment for image-to-image generation
    private File attachedImageFile;

    // Drag and drop handler for image attachments
    private art.moodframe.service.DragDropHandler dragDropHandler;

    // Image attachment service (shared between DragDropHandler and MultiImagePanel)
    private art.moodframe.service.ImageAttachmentService imageAttachmentService;

    // Track the currently selected model to survive language changes
    private art.moodframe.model.GenerationModel currentSelectedModel;

    // Protect error messages from being overwritten for a minimum duration
    private long errorStatusProtectedUntil = 0;

    // Track the chat metadata info label for live language updates
    private Label chatMetadataInfoLabel;

    // Track other dynamic labels for live language updates
    private Label deletedWarningLabel;
    private Label emptyTitleLabel;
    private Label emptyHintLabel;

    // Image Preview Overlay
    private art.moodframe.ui.component.ImagePreviewOverlay imagePreviewOverlay;

    // Multi-Image Attachment Panel (FLUX.2 & Gemini)
    private art.moodframe.ui.component.MultiImageAttachmentPanel multiImagePanel;

    // Prompt Context Section (YouTube Video Analysis)
    private art.moodframe.ui.component.PromptContextSection promptContextSection;
    private String currentVideoContextContent; // Stores the video analysis result for prompt attachment
    private art.moodframe.ui.dialog.YouTubeAnalysisDialog.VideoDirectResult currentVideoSource; // Direct video-to-image source (gemini-3.1-flash-image)

    /**
     * Initializes the controller after FXML loading.
     * 
     * <p>
     * This method is automatically called by the JavaFX FXML loader after
     * all FXML elements have been processed and injected. Use this method to
     * set up event handlers, configure UI components, and establish connections
     * to business services.
     * </p>
     * 
     * @since 1.0.0
     */
    @FXML
    public void initialize() {
        // Initialize ApplicationConfig first
        config = art.moodframe.config.ServiceFactory.getInstance()
                .getService(art.moodframe.config.ApplicationConfig.class);

        if (config != null && config.getLoggingEnabled()) {
            logger.info("Initializing MainWindow controller");
        }

        // Register for I18n change notifications
        I18nManager.addChangeListener(this);

        // Set version label in header
        if (appVersionLabel != null) {
            appVersionLabel.setText("v" + art.moodframe.util.VersionManager.getVersion());
        }

        setupChatSystem();
        setupUI();
        setupImagePreviewOverlay();
        setupEventHandlers();
        setupDragAndDrop();
        setupMultiImageAttachmentPanel();
        setupPromptContextSection();
        checkApiKeyStatus();
        setupServices();
        setupMemoryMonitoring();

        // Apply initial localization
        onLanguageChanged();

        if (config != null && config.getLoggingEnabled()) {
            logger.info("MainWindow controller initialization complete");
        }
    }

    /**
     * Sets up drag and drop functionality for the image attachment area.
     */
    private void setupDragAndDrop() {
        if (imagePreviewContainer == null) {
            logger.warn("Cannot setup drag and drop: imagePreviewContainer is null");
            return;
        }

        // Create ImageAttachmentService instance directly (since it's not registered in
        // ServiceFactory)
        imageAttachmentService = new art.moodframe.service.ImageAttachmentService();

        // Create drag and drop handler
        dragDropHandler = new art.moodframe.service.DragDropHandler(
                imageAttachmentService,
                imagePreviewContainer,
                this::handleDroppedImage,
                this::showErrorMessage,
                () -> art.moodframe.model.GenerationModel.getMaxFileSize(
                    currentSelectedModel != null ? currentSelectedModel.getProvider()
                                                 : art.moodframe.model.ApiProvider.BFL));

        // Initialize drag and drop
        dragDropHandler.initializeDragAndDrop();

        logger.info("Drag and drop initialized for image attachments");
    }

    /**
     * Sets up the multi-image attachment panel for FLUX.2 and Gemini models.
     */
    private void setupMultiImageAttachmentPanel() {
        if (multiImageAttachmentContainer == null) {
            logger.warn("Cannot setup multi-image panel: container is null");
            return;
        }

        try {
            // Reuse shared ImageAttachmentService (or create if not yet initialized)
            if (imageAttachmentService == null)
                imageAttachmentService = new art.moodframe.service.ImageAttachmentService();

            // Create the multi-image panel
            multiImagePanel = new art.moodframe.ui.component.MultiImageAttachmentPanel();
            multiImagePanel.setAttachmentService(imageAttachmentService);
            multiImagePanel.setOnError(this::showErrorMessage);

            // Configure callbacks
            multiImagePanel.setOnFirstImageAttached(() -> {
                // When first image is attached, apply same logic as single image
                Platform.runLater(() -> {
                    updateAttachmentState();
                    imageCountSlider.setValue(1);
                    updateImageCountLabel(1);
                });
            });

            multiImagePanel.setOnAllImagesRemoved(() -> {
                // When all images removed, reset state
                Platform.runLater(() -> {
                    attachedImageFile = null;
                    updateAttachmentState();
                });
            });

            multiImagePanel.setOnImagesChanged(images -> {
                // Update attachedImageFile with first image for backward compatibility
                if (images != null && !images.isEmpty()) {
                    attachedImageFile = images.get(0).getImageFile();
                } else {
                    attachedImageFile = null;
                }
                // Filter models based on current image count
                int imageCount = images != null ? (int) images.stream().filter(img -> img != null).count() : 0;
                Platform.runLater(() -> {
                    filterModelsByImageCount(imageCount);
                    updateCostDisplay();
                });
            });

            // Set current model
            if (currentSelectedModel != null) {
                multiImagePanel.setCurrentModel(currentSelectedModel);
            }

            // Add to container
            multiImageAttachmentContainer.getChildren().clear();
            multiImageAttachmentContainer.getChildren().add(multiImagePanel);

            logger.info("Multi-image attachment panel initialized");

        } catch (Exception e) {
            logger.error("Failed to setup multi-image attachment panel", e);
        }
    }

    /**
     * Sets up the Prompt Context Section for YouTube video analysis.
     * This section is only visible when a Gemini API key is configured.
     */
    private void setupPromptContextSection() {
        if (promptContextContainer == null) {
            logger.warn("Cannot setup prompt context section: container is null");
            return;
        }

        try {
            // Create the prompt context section
            promptContextSection = new art.moodframe.ui.component.PromptContextSection();

            // Configure callbacks
            promptContextSection.setOnAddYouTubeClicked(() -> {
                openYouTubeAnalysisDialog(null);
            });

            promptContextSection.setOnEditYouTubeClicked(existingContent -> {
                openYouTubeAnalysisDialog(existingContent);
            });

            promptContextSection.setOnDeleteClicked(() -> {
                clearVideoContextAttachment();
            });

            // Add to container
            promptContextContainer.getChildren().clear();
            promptContextContainer.getChildren().add(promptContextSection);

            // Initial visibility check
            updatePromptContextVisibility();
            updateBrainstormButton();

            logger.info("Prompt context section initialized");

        } catch (Exception e) {
            logger.error("Failed to setup prompt context section", e);
        }
    }

    /**
     * Opens the YouTube video analysis dialog.
     *
     * @param existingContent existing content to edit (null for new analysis)
     */
    private void openYouTubeAnalysisDialog(String existingContent) {
        try {
            Stage ownerStage = (Stage) mainBorderPane.getScene().getWindow();

            art.moodframe.ui.dialog.YouTubeAnalysisDialog dialog =
                new art.moodframe.ui.dialog.YouTubeAnalysisDialog(ownerStage,
                    this::setVideoContextAttachment, this::setVideoDirectAttachment);

            if (existingContent != null) {
                dialog.setInitialResult(existingContent);
            }

            dialog.showAndWait();

        } catch (Exception e) {
            logger.error("Failed to open YouTube analysis dialog", e);
            setStatus(I18nManager.getText("error.unknown", "An error occurred"));
        }
    }

    /**
     * Sets the video context attachment from YouTube analysis.
     *
     * @param content the analysis result content
     */
    private void setVideoContextAttachment(String content) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }

        currentVideoContextContent = content.trim();
        currentVideoSource = null; // analysis text and direct video are mutually exclusive

        // Update the UI to show the attachment card
        if (promptContextSection != null) {
            // Extract a short title from the content (first 30 chars or first line)
            String title = content.trim();
            int firstLineEnd = title.indexOf('\n');
            if (firstLineEnd > 0) {
                title = title.substring(0, firstLineEnd);
            }
            if (title.length() > 30) {
                title = title.substring(0, 30);
            }

            promptContextSection.setYouTubeContext(title, content);
        }

        logger.info("Video context attachment set ({} characters)", content.length());
    }

    /**
     * Clears the video context attachment (both analysis text and direct video).
     */
    private void clearVideoContextAttachment() {
        currentVideoContextContent = null;
        currentVideoSource = null;

        if (promptContextSection != null) {
            promptContextSection.clearContext();
        }

        logger.info("Video context attachment cleared");
    }

    /**
     * Sets a direct video-to-image source from the YouTube dialog's "Direkt verwenden"
     * mode. The video itself is passed to gemini-3.1-flash-image, so this auto-switches
     * the model to Nano Banana 2 and (optionally) prefills the prompt with the chosen
     * task-preset instruction.
     *
     * @param result the direct video result (URL, time range, instruction)
     */
    private void setVideoDirectAttachment(art.moodframe.ui.dialog.YouTubeAnalysisDialog.VideoDirectResult result) {
        if (result == null || result.url() == null || result.url().trim().isEmpty()) {
            return;
        }

        currentVideoSource = result;
        currentVideoContextContent = null; // mutually exclusive with analysis text

        // Auto-switch to Nano Banana 2 unless the selected model (Nano Banana 2 or Lite) supports video-to-image.
        String nanoBanana2 = art.moodframe.model.GenerationModel.NANO_BANANA2_1K.getLocalizedDisplayName();
        art.moodframe.model.GenerationModel selectedForVideo = modelComboBox != null
                ? art.moodframe.model.GenerationModel.fromLocalizedDisplayName(modelComboBox.getValue()) : null;
        if (modelComboBox != null && (selectedForVideo == null || !selectedForVideo.supportsVideoSource())) {
            modelComboBox.setValue(nanoBanana2);
            setStatus(I18nManager.getText("video.direct.model.switched", "Modell auf Nano Banana 2 umgestellt (Video-zu-Bild)"));
        }

        // Show a direct-video card in the sidebar.
        if (promptContextSection != null) {
            String title = result.url();
            String detail = result.url();
            if (result.startSeconds() > 0 || result.endSeconds() > 0) {
                detail = detail + "\n" + result.startSeconds() + "s – "
                        + (result.endSeconds() > 0 ? result.endSeconds() + "s" : "Ende");
            }
            promptContextSection.setDirectVideoContext(title, detail);
        }

        logger.info("Direct video source set: {} ({}s - {}s)",
                result.url(), result.startSeconds(), result.endSeconds());
    }

    /**
     * Updates the visibility of the prompt context section.
     * The section is only visible when:
     * 1. A Gemini API key is configured
     * 2. A chat is selected
     */
    private void updatePromptContextVisibility() {
        if (promptContextContainer == null || promptContextSection == null) {
            return;
        }

        // Check if Gemini API key is configured
        boolean hasGeminiKey = false;
        if (config != null) {
            String geminiKey = config.getGeminiApiKey();
            hasGeminiKey = geminiKey != null && !geminiKey.trim().isEmpty();
        }

        // Check if a chat is selected
        boolean hasChatSelected = currentChat != null;

        // Show section only if both conditions are met
        boolean shouldBeVisible = hasGeminiKey && hasChatSelected;

        promptContextContainer.setVisible(shouldBeVisible);
        promptContextContainer.setManaged(shouldBeVisible);
        promptContextSection.setSectionVisible(shouldBeVisible);

        logger.debug("Prompt context section visibility: {} (geminiKey={}, chatSelected={})",
            shouldBeVisible, hasGeminiKey, hasChatSelected);
    }


    /**
     * Enables the brainstorm button only if Gemini (key and provider) or OpenAI (provider and language model) is usable and
     * a chat is selected (like the other input controls).
     */
    private void updateBrainstormButton() {
        if (brainstormButton == null) {
            return;
        }

        boolean available = config != null && (config.hasGeminiApiKey() && config.isGeminiProviderEnabled()
                || config.isOpenAiBrainstormAvailable());
        boolean hasChatSelected = currentChat != null && !currentChat.isDeleted();
        brainstormButton.setDisable(!available || !hasChatSelected);
        brainstormButton.setTooltip(new Tooltip(I18nManager.getText(!available ? "brainstorm.tooltip.no.key"
                : !hasChatSelected ? "brainstorm.tooltip.no.chat" : "brainstorm.tooltip")));
    }

    /**
     * Opens the brainstorm popup, a guided AI chat that develops the prompt and its settings.
     */
    @FXML
    private void onBrainstormClicked() {
        try {
            art.moodframe.config.ServiceFactory services = art.moodframe.config.ServiceFactory.getInstance();
            art.moodframe.service.StylePresetService styleService =
                    services.getService(art.moodframe.service.StylePresetService.class);
            art.moodframe.service.ColorStyleService colorService =
                    services.getService(art.moodframe.service.ColorStyleService.class);

            /*  The names stay as shown: the OpenAI models share one constant and differ only by their name  */
            java.util.List<String> modelNames = modelComboBox.getItems().stream()
                    .filter((name) -> art.moodframe.model.GenerationModel.fromLocalizedDisplayName(name) != null)
                    .toList();
            java.util.List<art.moodframe.model.GenerationModel> models = modelNames.stream()
                    .map(art.moodframe.model.GenerationModel::fromLocalizedDisplayName)
                    .toList();

            String styleName = stylePresetsComboBox.getValue() != null ? stylePresetsComboBox.getValue().getName() : "None";
            String colorName = colorStyleComboBox.getValue() != null ? colorStyleComboBox.getValue().getName() : "None";
            String currentState = "Target model: " + modelComboBox.getValue()
                    + (resolutionContainer.isVisible() ? "\nResolution: " + resolutionChoiceBox.getValue() : "")
                    + "\nStyle: " + styleName
                    + "\nColor style: " + colorName
                    + "\nAspect ratio: " + aspectRatioComboBox.getValue()
                    + "\nText currently in the input field: "
                    + (promptTextArea.getText().isBlank() ? "(empty)" : promptTextArea.getText().trim());

            art.moodframe.service.BrainstormContext brainstormContext = new art.moodframe.service.BrainstormContext(
                    styleService.getAllStyles(), colorService.getAllStyles(), models, currentState, modelNames);
            art.moodframe.service.BrainstormService brainstormService = art.moodframe.service.BrainstormService.create(config);

            Stage ownerStage = (Stage) mainBorderPane.getScene().getWindow();
            new art.moodframe.ui.dialog.BrainstormDialog(ownerStage, brainstormService, brainstormContext,
                    modelComboBox.getValue(), this::applyBrainstormResult).show();
        } catch (Exception e) {
            logger.error("Failed to open brainstorm dialog", e);
        }
    }

    /**
     * Applies the values adopted in the brainstorm popup. The target model is set first, as it
     * decides which aspect ratios and video parameters are available.
     *
     * @param result the adopted values; a {@code null} value is left unchanged
     */
    private void applyBrainstormResult(art.moodframe.ui.dialog.BrainstormDialog.Result result) {
        modelComboBox.setValue(result.targetModel());

        /*  The resolution of a model without resolution variants, and the remembered choice of the family  */
        String resolution = result.resolution() != null ? result.resolution()
                : art.moodframe.model.ModelFamilies.resolutionOf(result.targetModel());
        if (resolution != null) {
            modelFamilySelector.remember(result.targetModel(), resolution);
            if (resolutionChoiceBox.getItems().contains(resolution))
                resolutionChoiceBox.setValue(resolution);
        }
        promptTextArea.setText(result.prompt());

        if (result.style() != null) {
            stylePresetsComboBox.getItems().stream()
                    .filter(item -> result.style().equals(item.getName())).findFirst()
                    .ifPresent(stylePresetsComboBox::setValue);
        }
        if (result.colorStyle() != null) {
            colorStyleComboBox.getItems().stream()
                    .filter(item -> result.colorStyle().equals(item.getName())).findFirst()
                    .ifPresent(colorStyleComboBox::setValue);
        }
        if (result.aspectRatio() != null && aspectRatioComboBox.getItems().contains(result.aspectRatio())) {
            aspectRatioComboBox.setValue(result.aspectRatio());
        }

        if (result.durationSeconds() != null && videoDurationChoiceBox.getItems().contains(result.durationSeconds() + "s")) {
            videoDurationChoiceBox.setValue(result.durationSeconds() + "s");
        }
        promptTextArea.requestFocus();
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

                // Load and display the image
                Image image = new Image(imageFile.toURI().toString());
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

                setStatus(I18nManager.getText("status.image.attached") + ": " + imageFile.getName());
                logger.info("Image attached via drag and drop: {}", imageFile.getName());

            } catch (Exception e) {
                logger.error("Failed to process dropped image", e);
                showErrorMessage(I18nManager.getText("error.image.processing.failed"));
            }
        });
    }

    /**
     * Shows an error message to the user.
     * 
     * @param message the error message to display
     */
    private void showErrorMessage(String message) {
        Platform.runLater(() -> {
            setStatus("Error: " + message);
            if (config != null && config.getLoggingEnabled())
                logger.error("UI Error: {}", message);
        });
    }

    /**
     * Updates the prompt preview to show how style will affect final prompt.
     * Phase 2: Real-time preview of style combination
     */
    private void updatePromptPreview() {
        buildFinalPrompt();
        // For now, we could update a status or tooltip
        // In future, this could update a preview label or send button text
    }

    /**
     * Builds the final prompt by combining user input with selected styles
     * and video context attachment (if present).
     *
     * <p>Prompt composition order:</p>
     * <ol>
     * <li>User prompt</li>
     * <li>Color style (e.g., "Color 1 = #XXXXXX, Color 2 = #YYYYYY")</li>
     * <li>Style prompt (comma-separated)</li>
     * <li>Video context (newline-separated with header)</li>
     * </ol>
     */
    public String buildFinalPrompt() {
        String userPrompt = promptTextArea.getText().trim();

        if (userPrompt.isEmpty()) {
            return "";
        }

        StringBuilder finalPrompt = new StringBuilder(userPrompt);

        // Get selected color style from combo box and append color palette
        art.moodframe.model.ColorStyleItem selectedColorStyle = colorStyleComboBox != null ? colorStyleComboBox.getValue() : null;

        if (selectedColorStyle != null && !"None".equals(selectedColorStyle.getName())) {
            art.moodframe.service.ColorStyleService colorStyleService = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.service.ColorStyleService.class);

            if (colorStyleService != null) {
                String colorPrompt = colorStyleService.getColorPromptText(selectedColorStyle.getName());
                if (colorPrompt != null && !colorPrompt.trim().isEmpty()) {
                    // Insert color style after user prompt, before style preset
                    finalPrompt.append(", ").append(colorPrompt);
                }
            }
        }

        // Get selected style preset from combo box
        art.moodframe.model.StylePresetItem selectedStyleItem = stylePresetsComboBox != null ? stylePresetsComboBox.getValue() : null;
        String selectedStyle = selectedStyleItem != null ? selectedStyleItem.getName() : null;

        if (selectedStyle != null && !selectedStyle.equals("None")) {
            // Get style prompt text from backend
            art.moodframe.service.StylePresetService styleService = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.service.StylePresetService.class);

            if (styleService != null) {
                String stylePrompt = styleService.getStylePromptText(selectedStyle);
                if (stylePrompt != null && !stylePrompt.trim().isEmpty()) {
                    // Combine user prompt with style prompt
                    finalPrompt.append(", ").append(stylePrompt);
                }
            }
        }

        // Append video context if present (format: Prompt, ColorStyle, Style, Content: VideoAnalysis)
        if (currentVideoContextContent != null && !currentVideoContextContent.isEmpty()) {
            finalPrompt.append(", Content: ").append(currentVideoContextContent);
        }

        return finalPrompt.toString();
    }

    /**
     * Gets currently selected style.
     * 
     * @return selected style name, or null if none selected
     */
    public String getSelectedStyle() {
        if (stylePresetsComboBox != null && stylePresetsComboBox.getValue() != null) {
            return stylePresetsComboBox.getValue().getName();
        }
        return null;
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
     * Sets up the initial UI state and component configurations.
     */
    private void setupUI() {
        // Configure SplitPane with restored position
        try {
            art.moodframe.config.ApplicationConfig config = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.config.ApplicationConfig.class);

            if (config != null) {
                final double savedPosition = config.getSplitterPosition();
                // Set up scene listener for reliable restoration after window is shown
                mainSplitPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
                    if (newScene != null) {
                        newScene.windowProperty().addListener((winObs, oldWindow, newWindow) -> {
                            if (newWindow != null) {
                                newWindow.showingProperty().addListener((showObs, wasShowing, isShowing) -> {
                                    if (isShowing) {
                                        Platform.runLater(() -> {
                                            mainSplitPane.setDividerPosition(0, savedPosition);
                                        });
                                    }
                                });
                            }
                        });
                    }
                });

                // Immediate attempt (may not work if layout isn't ready)
                Platform.runLater(() -> {
                    mainSplitPane.setDividerPositions(savedPosition);
                });

                // Save position when user changes it
                mainSplitPane.getDividers().get(0).positionProperty().addListener((obs, oldPos, newPos) -> {
                    try {
                        config.setSplitterPosition(newPos.doubleValue());
                    } catch (Exception ex) {
                        logger.warn("Failed to save splitter position", ex);
                    }
                });
            } else {
                mainSplitPane.setDividerPositions(0.2); // Fallback
            }
        } catch (Exception e) {
            logger.warn("Failed to restore splitter position, using default", e);
            mainSplitPane.setDividerPositions(0.2); // Fallback
        }

        // Configure Chat-Input SplitPane with restored position
        try {
            art.moodframe.config.ApplicationConfig config = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.config.ApplicationConfig.class);

            if (config != null) {
                final double savedChatInputPosition = config.getChatInputSplitterPosition();

                // Set up scene listener for reliable restoration after window is shown
                chatInputSplitPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
                    if (newScene != null) {
                        newScene.windowProperty().addListener((winObs, oldWindow, newWindow) -> {
                            if (newWindow != null) {
                                newWindow.showingProperty().addListener((showObs, wasShowing, isShowing) -> {
                                    if (isShowing) {
                                        Platform.runLater(() -> {
                                            chatInputSplitPane.setDividerPosition(0, savedChatInputPosition);
                                        });
                                    }
                                });
                            }
                        });
                    }
                });

                // Immediate attempt (may not work if layout isn't ready)
                Platform.runLater(() -> {
                    chatInputSplitPane.setDividerPositions(savedChatInputPosition);
                });

                // Save position when user changes it
                chatInputSplitPane.getDividers().get(0).positionProperty().addListener((obs, oldPos, newPos) -> {
                    try {
                        config.setChatInputSplitterPosition(newPos.doubleValue());
                    } catch (Exception ex) {
                        logger.warn("Failed to save chat-input splitter position", ex);
                    }
                });
            } else {
                chatInputSplitPane.setDividerPositions(0.84); // Fallback
            }
        } catch (Exception e) {
            logger.warn("Failed to restore chat-input splitter position, using default", e);
            chatInputSplitPane.setDividerPositions(0.84); // Fallback
        }

        // Setup model selection based on enabled providers
        populateAvailableModels();

        // Setup aspect ratio selection
        aspectRatioComboBox.getItems().addAll(
                "21:9", "16:9", "3:2", "5:4", "1:1", "4:5", "2:3", "9:16", "9:21");

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

        // Add listener to update safety tolerance label
        safetyToleranceSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            // Round to nearest integer to ensure whole numbers only
            int roundedValue = (int) Math.round(newVal.doubleValue());
            // Force slider to snap to integer value if not already
            if (Math.abs(newVal.doubleValue() - roundedValue) > 0.01) {
                safetyToleranceSlider.setValue(roundedValue);
            }
            updateSafetyToleranceLabel(roundedValue);
        });

        // Add listener to update image count label
        imageCountSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            // Round to nearest integer to ensure whole numbers only
            int roundedValue = (int) Math.round(newVal.doubleValue());
            // Force slider to snap to integer value if not already
            if (Math.abs(newVal.doubleValue() - roundedValue) > 0.01) {
                imageCountSlider.setValue(roundedValue);
            }
            updateImageCountLabel(roundedValue);
        });

        // Initial label updates
        updateSafetyToleranceLabel((int) safetyToleranceSlider.getValue());
        updateImageCountLabel((int) imageCountSlider.getValue());

        // Configure video Duration ChoiceBox (the items follow the selected video model)
        if (videoDurationChoiceBox != null) {
            videoDurationChoiceBox.getItems().addAll("3s", "4s", "5s", "6s", "7s", "8s", "9s", "10s");
            String defaultDuration = (config != null) ? config.getDefaultVideoDuration() : "8s";
            videoDurationChoiceBox.setValue(defaultDuration);
            videoDurationChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> updateCostDisplay());
        }

        // Configure the video audio CheckBox
        if (videoAudioCheckBox != null) {
            boolean audioEnabled = (config != null) && config.isVideoAudioEnabled();
            videoAudioCheckBox.setSelected(audioEnabled);
            videoAudioCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> updateCostDisplay());
        }

        // Configure BFL Prompt Upsampling checkbox
        if (bflPromptUpsamplingCheckBox != null) {
            bflPromptUpsamplingCheckBox.setSelected(false); // Default: off
        }

        // Configure Gemini Thinking Level dropdown
        if (geminiThinkingLevelChoiceBox != null) {
            geminiThinkingLevelChoiceBox.getItems().addAll("Minimal", "High");
            geminiThinkingLevelChoiceBox.setValue("Minimal"); // API default
        }

        // Setup style presets selection first
        setupStylePresets();

        // Setup color styles selection
        setupColorStyles();

        // Setup Gemini Advanced Features section (Phase 2)
        setupGeminiAdvancedFeatures();

        // Setup FLUX.2 Parameters (guidance, steps, grounding search)
        setupFlux2Parameters();

        // Setup cost display programmatically
        setupCostDisplay();

        // Optimize ScrollPane for smooth scrolling performance
        optimizeScrollPane();

        // The model dropdown lists families, the resolution has its own choice
        modelFamilySelector = new art.moodframe.ui.component.ModelFamilySelector(modelComboBox, modelFamilyChoiceBox,
                resolutionChoiceBox, resolutionContainer, config);

        // Load saved values from configuration
        loadUISelections();

        // Setup persistence listeners for UI selections
        setupPersistenceListeners();

        // Initial UI state
        updateUIState();
        updateCostDisplay(); // Initial cost calculation
        updateInitialStatus();
    }

    /**
     * Sets up service dependencies and event handlers.
     */
    private void setupServices() {
        try {
            // Get RequestResponseManager from ServiceFactory
            requestResponseManager = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.service.RequestResponseManager.class);

            if (requestResponseManager != null) {
                // Set up event handlers for real-time UI updates
                requestResponseManager.setOnEntryUpdated(this::onChatEntryUpdated);
                requestResponseManager.setOnImageUpdated(this::onImageUpdated);

                if (config != null && config.getLoggingEnabled()) {
                    logger.info("RequestResponseManager configured with UI callbacks");
                }
            } else {
                logger.warn("RequestResponseManager not available - using mock mode");
            }

            // Set up TrashCleanupService callback for automatic UI refresh
            trashCleanupService = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.service.TrashCleanupService.class);

            if (trashCleanupService != null) {
                /*  Only the deleted chats leave the list: a full reload would replace the chat objects that running
                    requests still update  */
                trashCleanupService.setCleanupCallback(deletedIds -> javafx.application.Platform.runLater(
                        () -> removeChats(deletedIds)));
            } else {
                logger.warn("TrashCleanupService not available - no automatic UI refresh");
            }

            // NOTE: ViewportTracker removed - all images now load immediately for better performance
            // Lazy loading caused more overhead than benefit for chats with <100 entries

        } catch (Exception e) {
            logger.error("Failed to setup services", e);
        }
    }

    private String getActualImagePath(GeneratedImage image, boolean isChatDeleted) {
        return chatHistory.actualImagePath(image, isChatDeleted);
    }

    /**
     * Initializes the chat repository from the service factory.
     */
    private void initializeChatRepository() {
        try {
            chatHistory = art.moodframe.config.ServiceFactory.getInstance().getService(ChatHistoryService.class);
            if (chatHistory == null) {
                logger.warn("ChatRepository not available - chat persistence disabled");
            } else if (config != null && config.getLoggingEnabled()) {
                logger.info("ChatRepository initialized successfully");
            }

        } catch (Exception e) {
            logger.error("Failed to initialize ChatRepository", e);
        }
    }

    /**
     * Loads the chats from the database once at the start; what a restart left behind is repaired there.
     * Later changes (trash cleanup) update the list instead of reloading it, so running requests keep their
     * chat objects.
     */
    private void loadRealChatData() {
        try {
            if (chatHistory != null) {

                ChatHistoryService.History history = chatHistory.load();
                java.util.List<Chat> chatsToKeep = history.chats();

                chatList.clear();
                chatList.addAll(chatsToKeep);

                // Force UI refresh after loading to ensure correct status display
                Platform.runLater(() -> {
                    if (currentChat != null) {
                        updateChatView(currentChat);
                    }
                });

                // If no chats exist, create a default welcome chat
                if (history.nothingStored()) {
                    createWelcomeChat();
                }
            } else {
                logger.warn("ChatRepository not available - creating empty chat list");
                chatList.clear();
                createWelcomeChat();
            }

        } catch (Exception e) {
            logger.error("Failed to load chat data from database", e);
            // Fallback to empty list
            chatList.clear();
            createWelcomeChat();
        }
    }

    /** Removes chats that are gone from the database from the list; the open one is closed. */
    private void removeChats(java.util.List<String> chatIds) {
        if (currentChat != null && chatIds.contains(currentChat.getId())) {
            chatListView.getSelectionModel().clearSelection();
            currentChat = null;
            chatEntriesListView.setItems(null);
            welcomeContainer.setVisible(true);
            updateChatTotalCost(null);
        }
        chatList.removeIf((chat) -> chatIds.contains(chat.getId()));
    }

    /**
     * Creates a welcome chat for first-time users.
     */
    private void createWelcomeChat() {
        Chat welcomeChat = new Chat.Builder()
                .name("Welcome to MoodFrame")
                .createdAt(LocalDateTime.now())
                .build();

        // Save to database if repository is available
        if (chatHistory != null) {
            try {
                chatHistory.saveChat(welcomeChat);
                chatList.add(welcomeChat);
            } catch (Exception e) {
                logger.warn("Failed to save welcome chat to database", e);
                chatList.add(welcomeChat);
            }
        } else {
            chatList.add(welcomeChat);
        }
    }

    /**
     * Handles real-time ChatEntry updates from the RequestResponseManager.
     */
    private void onChatEntryUpdated(art.moodframe.model.ChatEntry chatEntry) {
        javafx.application.Platform.runLater(() -> {
            try {
                // Only update if this is the currently displayed chat and entry exists
                if (currentChat != null && currentChat.getEntries().contains(chatEntry)) {
                    // Find and update only the specific ChatEntryCard instead of reloading entire
                    // chat
                    updateSpecificChatEntryCard(chatEntry);

                } else {
                    // Entry not in current chat, no UI update needed
                }
            } catch (Exception e) {
                logger.error("Error updating UI for ChatEntry", e);
            }
        });
    }

    /**
     * Handles real-time GeneratedImage updates from the RequestResponseManager.
     */
    private void onImageUpdated(art.moodframe.model.GeneratedImage image) {
        javafx.application.Platform.runLater(() -> {
            try {
                // Only update if this image belongs to the currently displayed chat
                if (currentChat != null) {
                    // Find the ChatEntry that contains this image
                    art.moodframe.model.ChatEntry parentEntry = currentChat.getEntries().stream()
                            .filter(entry -> entry.getImages().stream()
                                    .anyMatch(img -> img.getId().equals(image.getId())))
                            .findFirst()
                            .orElse(null);

                    if (parentEntry != null) {
                        // Update only the specific ChatEntryCard instead of reloading entire chat
                        updateSpecificChatEntryCard(parentEntry);

                    } else {
                        // Image not found in current chat, no UI update needed
                    }
                } else {
                    // No current chat, no UI update needed
                }
            } catch (Exception e) {
                logger.error("Error updating UI for GeneratedImage", e);
            }
        });
    }

    /**
     * Updates a specific ChatEntryCard without reloading the entire chat view.
     * With virtualized ListView, we refresh the ListView items to trigger cell update.
     */
    private void updateSpecificChatEntryCard(art.moodframe.model.ChatEntry chatEntry) {
        if (chatEntriesListView == null || chatEntriesListView.getItems() == null) {
            return;
        }

        // Force refresh by updating the entry in the list
        // ListView will automatically re-render the visible cell
        chatEntriesListView.refresh();

        // Update total cost after entry update
        if (currentChat != null) {
            updateChatTotalCost(currentChat);
        }
    }

    // NOTE: enforceChatEntryCardLimit removed - ListView handles cell recycling automatically

    /**
     * Adds a new ChatEntry directly to the UI without reloading the entire chat.
     * With virtualized ListView, we add to the items list and scroll to bottom.
     */
    private void addNewChatEntryToUI(art.moodframe.model.ChatEntry chatEntry) {
        try {
            // Only add if this is the current chat
            if (currentChat != null && currentChat.equals(getCurrentChatFromEntry(chatEntry))) {
                if (chatEntriesListView.getItems() == null) {
                    chatEntriesListView.setItems(javafx.collections.FXCollections.observableArrayList());
                }

                // Hide welcome if visible
                welcomeContainer.setVisible(false);
                chatEntriesListView.setVisible(true);

                // Add new entry to the ListView items
                chatEntriesListView.getItems().add(chatEntry);

                // Update chat header to reflect new message count and cost
                updateChatHeader();

                // Auto-scroll to bottom for new entries
                scrollToBottom("new entry added");

                if (config.getLoggingEnabled()) {
                    logger.info("Added new ChatEntry to ListView - total entries: {}",
                            chatEntriesListView.getItems().size());
                }
            }
        } finally {
            // UI operation completed
        }
    }

    /**
     * Scrolls the chat scroll pane to the bottom with robust layout handling.
     * 
     * @param reason the reason for scrolling (for logging)
     */
    private void scrollToBottom(String reason) {
        // Multiple scroll attempts with different delays to handle dynamic content
        int[] delays = { 0, 50, 150, 300, 600 };

        for (int delay : delays) {
            if (delay == 0) {
                // Immediate attempt
                Platform.runLater(() -> {
                    performScroll(reason, "immediate");
                });
            } else {
                // Delayed attempts
                javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(javafx.util.Duration.millis(delay), e -> {
                            performScroll(reason, delay + "ms delay");
                        }));
                timeline.play();
            }
        }
    }

    /**
     * Performs the actual scroll operation.
     *
     * @param reason the reason for scrolling
     * @param timing timing description
     */
    private void performScroll(String reason, String timing) {
        if (chatEntriesListView != null && chatEntriesListView.getItems() != null
                && !chatEntriesListView.getItems().isEmpty()) {
            chatEntriesListView.scrollTo(chatEntriesListView.getItems().size() - 1);
        }
    }

    /**
     * Helper method to get the chat that contains a given entry.
     */
    private art.moodframe.model.Chat getCurrentChatFromEntry(art.moodframe.model.ChatEntry entry) {
        return chatList.stream()
                .filter(chat -> chat.getId().equals(entry.getChatId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Sets up the style presets selection from backend with single selection.
     */
    private void setupStylePresets() {
        if (stylePresetsComboBox == null) {
            logger.warn("stylePresetsComboBox is null, skipping style presets setup");
            return;
        }

        // Clear any existing items first
        stylePresetsComboBox.getItems().clear();

        // Set custom cell factory for dropdown rendering with icons
        stylePresetsComboBox.setCellFactory(listView -> new art.moodframe.ui.component.StylePresetListCell());
        stylePresetsComboBox.setButtonCell(new art.moodframe.ui.component.StylePresetListCell());

        // Load style presets from backend
        art.moodframe.service.StylePresetService styleService = art.moodframe.config.ServiceFactory.getInstance()
                .getService(art.moodframe.service.StylePresetService.class);

        if (styleService != null) {
            // Add "None" option first (not default, since it's a special option)
            stylePresetsComboBox.getItems().add(new art.moodframe.model.StylePresetItem("None", "", false));

            // Add all styles from service
            for (art.moodframe.model.StylePreset preset : styleService.getAllStyles()) {
                String name = preset.getName();
                if (name != null && !name.trim().isEmpty()) {
                    stylePresetsComboBox.getItems().add(new art.moodframe.model.StylePresetItem(
                            name.trim(), preset.getPromptText(), preset.isDefault()));
                }
            }

            // Select "None" by default
            if (!stylePresetsComboBox.getItems().isEmpty()) {
                stylePresetsComboBox.setValue(stylePresetsComboBox.getItems().get(0));
            }

        } else {
            logger.warn("StylePresetService not available, style combo box will be empty");
            art.moodframe.model.StylePresetItem noneItem = new art.moodframe.model.StylePresetItem("None", "", false);
            stylePresetsComboBox.getItems().add(noneItem);
            stylePresetsComboBox.setValue(noneItem);
        }
    }

    /**
     * Sets up the color styles selection from backend.
     *
     * <p>Color styles are palettes of 1-5 hex colors that get appended to the prompt
     * in the format "Color 1 = #XXXXXX, Color 2 = #YYYYYY, ...".</p>
     */
    private void setupColorStyles() {
        if (colorStyleComboBox == null) {
            logger.warn("colorStyleComboBox is null, skipping color styles setup");
            return;
        }

        // Clear any existing items first
        colorStyleComboBox.getItems().clear();

        // Load color styles from backend
        art.moodframe.service.ColorStyleService colorStyleService = art.moodframe.config.ServiceFactory.getInstance()
                .getService(art.moodframe.service.ColorStyleService.class);

        if (colorStyleService != null) {
            // Add "None" option first
            art.moodframe.model.ColorStyleItem noneItem = new art.moodframe.model.ColorStyleItem("None", java.util.Collections.emptyList(), true);
            colorStyleComboBox.getItems().add(noneItem);

            // Add all color style items from service
            for (art.moodframe.model.ColorStyle colorStyle : colorStyleService.getAllStyles()) {
                colorStyleComboBox.getItems().add(colorStyle.toColorStyleItem());
            }

            // Default to no color style
            colorStyleComboBox.setValue(noneItem);

            // Set up custom cell factory to show color boxes
            colorStyleComboBox.setCellFactory(listView -> new art.moodframe.ui.component.ColorStyleListCell());
            colorStyleComboBox.setButtonCell(new art.moodframe.ui.component.ColorStyleListCell());

        } else {
            logger.warn("ColorStyleService not available, color style combo box will be empty");
            art.moodframe.model.ColorStyleItem noneItem = new art.moodframe.model.ColorStyleItem("None", java.util.Collections.emptyList(), true);
            colorStyleComboBox.getItems().add(noneItem);
            colorStyleComboBox.setValue(noneItem);
        }
    }

    /**
     * Refreshes the color styles dropdown after changes in settings.
     */
    public void refreshColorStyles() {
        if (colorStyleComboBox == null) {
            return;
        }

        // Save current selection name
        art.moodframe.model.ColorStyleItem currentSelection = colorStyleComboBox.getValue();
        String currentName = currentSelection != null ? currentSelection.getName() : "None";

        // Clear and reload all color styles
        colorStyleComboBox.getItems().clear();

        // Load color styles from backend
        art.moodframe.service.ColorStyleService colorStyleService = art.moodframe.config.ServiceFactory.getInstance()
                .getService(art.moodframe.service.ColorStyleService.class);

        if (colorStyleService != null) {
            // Add "None" option first
            art.moodframe.model.ColorStyleItem noneItem = new art.moodframe.model.ColorStyleItem("None", java.util.Collections.emptyList(), true);
            colorStyleComboBox.getItems().add(noneItem);

            art.moodframe.model.ColorStyleItem selectedItem = noneItem;

            for (art.moodframe.model.ColorStyle colorStyle : colorStyleService.getAllStyles()) {
                art.moodframe.model.ColorStyleItem item = colorStyle.toColorStyleItem();
                colorStyleComboBox.getItems().add(item);
                if (item.getName().equals(currentName)) {
                    selectedItem = item;
                }
            }

            colorStyleComboBox.setValue(selectedItem);

            // Re-apply cell factory
            colorStyleComboBox.setCellFactory(listView -> new art.moodframe.ui.component.ColorStyleListCell());
            colorStyleComboBox.setButtonCell(new art.moodframe.ui.component.ColorStyleListCell());

        } else {
            logger.warn("ColorStyleService not available during refresh");
            art.moodframe.model.ColorStyleItem noneItem = new art.moodframe.model.ColorStyleItem("None", java.util.Collections.emptyList(), true);
            colorStyleComboBox.getItems().add(noneItem);
            colorStyleComboBox.setValue(noneItem);
        }
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
            art.moodframe.config.ApplicationConfig config = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.config.ApplicationConfig.class);

            if (config != null && geminiAdvancedFeaturesSection != null) {
                // Google Search Grounding
                geminiAdvancedFeaturesSection.getSearchGroundingCheckBox().selectedProperty()
                        .addListener((obs, oldVal, newVal) -> {
                            if (newVal != null) {
                                config.setDefaultGoogleSearchEnabled(newVal);
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
            art.moodframe.config.ApplicationConfig config = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.config.ApplicationConfig.class);

            if (config == null) {
                logger.warn("ApplicationConfig not available, FLUX.2 parameters will use defaults");
                return;
            }

            // Load saved values from config
            double savedGuidance = config.getFlux2FlexGuidance();
            int savedSteps = config.getFlux2FlexSteps();
            boolean savedGroundingSearch = config.getFlux2GroundingSearchEnabled();

            // Setup Guidance slider
            if (flux2GuidanceSlider != null && flux2GuidanceLabel != null) {
                flux2GuidanceSlider.setValue(savedGuidance);
                updateFlux2GuidanceLabel(savedGuidance);

                // Listener for label updates and config persistence
                flux2GuidanceSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                    double value = newVal.doubleValue();
                    updateFlux2GuidanceLabel(value);
                    config.setFlux2FlexGuidance(value);
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
                    config.setFlux2FlexSteps(value);
                    updateCostDisplay();
                });
            }

            // Setup FLUX.2 Safety Tolerance slider (0-5 scale)
            if (flux2SafetySlider != null && flux2SafetyValueLabel != null) {
                int savedFlux2Safety = config.getFlux2SafetyTolerance();
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
                    config.setFlux2SafetyTolerance(value);
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
     * Loads UI selections from configuration.
     */
    private void loadUISelections() {
        try {
            art.moodframe.config.ApplicationConfig config = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.config.ApplicationConfig.class);

            if (config != null) {
                // Load model selection (now using display name for unique identification)
                String savedModelDisplay = config.getDefaultModel();
                // Check if saved model display name is valid
                art.moodframe.model.GenerationModel savedModel = art.moodframe.model.GenerationModel.fromDisplayName(savedModelDisplay);
                if (savedModel == null) {
                    // Try to interpret as old API model ID format for backward compatibility
                    savedModel = art.moodframe.model.GenerationModel.fromApiModelId(savedModelDisplay);
                }

                if (art.moodframe.model.GenerationModel.openAiModelId(savedModelDisplay) != null
                        && modelComboBox.getItems().contains(savedModelDisplay)) {
                    currentSelectedModel = art.moodframe.model.GenerationModel.OPENAI_IMAGE;
                    modelComboBox.setValue(savedModelDisplay);
                } else if (savedModel != null) {
                    currentSelectedModel = savedModel; // Track the loaded model
                    // Use localized display name
                    String localizedName = savedModel.getLocalizedDisplayName();
                    if (modelComboBox.getItems().contains(localizedName)) {
                        modelComboBox.setValue(localizedName);
                    } else {
                        // Fallback to default if saved model not found
                        currentSelectedModel = art.moodframe.model.GenerationModel.getDefault();
                        modelComboBox.setValue(currentSelectedModel.getLocalizedDisplayName());
                    }
                } else {
                    // Fallback to default if saved model not found
                    currentSelectedModel = art.moodframe.model.GenerationModel.getDefault();
                    modelComboBox.setValue(currentSelectedModel.getLocalizedDisplayName());
                }

                // Load aspect ratio selection
                String savedAspectRatio = config.getDefaultAspectRatio();
                aspectRatioComboBox.setValue(savedAspectRatio);

                // Load safety tolerance value
                int savedSafetyTolerance = config.getDefaultSafetyTolerance();
                safetyToleranceSlider.setValue(savedSafetyTolerance);

                // Load image count selection
                int savedImageCount = config.getDefaultImageCount();
                imageCountSlider.setValue(savedImageCount);

                // Load style selection
                String savedStyle = config.getDefaultStyle();
                if (savedStyle != null && stylePresetsComboBox != null) {
                    // Find StylePresetItem by name
                    art.moodframe.model.StylePresetItem matchingItem = stylePresetsComboBox.getItems().stream()
                            .filter(item -> savedStyle.equals(item.getName()))
                            .findFirst()
                            .orElse(stylePresetsComboBox.getItems().isEmpty() ? null : stylePresetsComboBox.getItems().get(0));
                    if (matchingItem != null) {
                        stylePresetsComboBox.setValue(matchingItem);
                    }
                }

                // Load color style selection
                if (colorStyleComboBox != null) {
                    String savedColorStyle = config.getDefaultColorStyle();
                    if (savedColorStyle != null) {
                        for (art.moodframe.model.ColorStyleItem item : colorStyleComboBox.getItems()) {
                            if (savedColorStyle.equals(item.getName())) {
                                colorStyleComboBox.setValue(item);
                                break;
                            }
                        }
                    }
                }

                // Load BFL Prompt Upsampling setting
                if (bflPromptUpsamplingCheckBox != null) {
                    boolean savedBflUpsampling = config.getDefaultBflPromptUpsampling();
                    bflPromptUpsamplingCheckBox.setSelected(savedBflUpsampling);
                }

                // Load Gemini Thinking Level setting
                if (geminiThinkingLevelChoiceBox != null) {
                    // Only "minimal" and "high" exist; a saved "low" from earlier versions becomes "minimal"
                    geminiThinkingLevelChoiceBox.setValue(
                            "high".equalsIgnoreCase(config.getDefaultGeminiThinkingLevel()) ? "High" : "Minimal");
                }

                // Load Gemini Advanced Features settings (Phase 2)
                if (geminiAdvancedFeaturesSection != null) {
                    boolean savedGoogleSearch = config.getDefaultGoogleSearchEnabled();
                    geminiAdvancedFeaturesSection.setSearchGroundingEnabled(savedGoogleSearch);
                }

                // Update parameter visibility for the loaded model FIRST
                // This populates the aspect ratio combo box with provider-specific options
                if (currentSelectedModel != null) {
                    updateParameterVisibility(currentSelectedModel.getDisplayName());
                }

                // Re-apply saved aspect ratio AFTER visibility update
                // (updateParameterVisibility may have reset it to default)
                String savedAspectRatioFinal = config.getDefaultAspectRatio();
                if (savedAspectRatioFinal != null && aspectRatioComboBox.getItems().contains(savedAspectRatioFinal)) {
                    aspectRatioComboBox.setValue(savedAspectRatioFinal);
                    logger.debug("Restored saved aspect ratio: {}", savedAspectRatioFinal);
                }

                // Update cost display after loading all values from config
                updateCostDisplay();
            }
        } catch (Exception e) {
            logger.error("Failed to load UI selections from configuration", e);
            // Fall back to defaults
            currentSelectedModel = art.moodframe.model.GenerationModel.getDefault();
            modelComboBox.setValue(currentSelectedModel.getLocalizedDisplayName());
            aspectRatioComboBox.setValue("16:9");
            imageCountSlider.setValue(1);
            if (stylePresetsComboBox != null && !stylePresetsComboBox.getItems().isEmpty()) {
                stylePresetsComboBox.setValue(stylePresetsComboBox.getItems().get(0));
            }

            // Update cost display with default values
            updateCostDisplay();

            // Update parameter visibility for default model
            updateParameterVisibility(currentSelectedModel.getDisplayName());
        }
    }

    /**
     * Sets up persistence listeners for UI selections.
     */
    private void setupPersistenceListeners() {
        try {
            art.moodframe.config.ApplicationConfig config = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.config.ApplicationConfig.class);

            if (config != null) {
                // Save model selection changes (using display name for unique identification)
                modelComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        // Convert localized name back to original display name for storage
                        art.moodframe.model.GenerationModel model = art.moodframe.model.GenerationModel
                                .fromLocalizedDisplayName(newVal);
                        if (model != null) {
                            art.moodframe.model.GenerationModel previousModel = currentSelectedModel;
                            currentSelectedModel = model;
                            config.setDefaultModel(art.moodframe.model.GenerationModel.openAiModelId(newVal) != null
                                    ? newVal : model.getDisplayName());

                            // A direct video source only works with Nano Banana 2 and Lite;
                            // drop it (and its sidebar card) if the user switches to another model.
                            if (currentVideoSource != null && !model.supportsVideoSource()) {
                                clearVideoContextAttachment();
                                setStatus(I18nManager.getText("video.direct.cleared.model",
                                        "Direkt-Video entfernt (nur mit Nano Banana 2 und Lite möglich)"));
                            }

                            // Provider switch: Google → BFL → clear attachments
                            if (previousModel != null && multiImagePanel != null
                                    && multiImagePanel.hasAttachments()) {
                                art.moodframe.model.ApiProvider oldProvider = previousModel.getProvider();
                                art.moodframe.model.ApiProvider newProvider = model.getProvider();
                                if (oldProvider == art.moodframe.model.ApiProvider.GOOGLE_GEMINI
                                        && newProvider == art.moodframe.model.ApiProvider.BFL) {
                                    multiImagePanel.clearAllAttachments();
                                    attachedImageFile = null;
                                    setStatus(I18nManager.getText("status.attachments.cleared.provider"));
                                }
                            }

                            // Update file size limit based on provider
                            if (imageAttachmentService != null)
                                imageAttachmentService.setMaxFileSize(
                                    art.moodframe.model.GenerationModel.getMaxFileSize(model.getProvider()));
                        }

                        // Update parameter visibility based on provider
                        String modelName = model != null ? model.getDisplayName() : newVal;
                        updateParameterVisibility(modelName);

                        // Update multi-image panel with new model's max images
                        if (multiImagePanel != null && model != null) {
                            multiImagePanel.setCurrentModel(model);
                        }

                        // Update character count display with new limit
                        String currentText = promptTextArea != null ? promptTextArea.getText() : "";
                        updateCharacterCount(currentText);

                        // Update prompt placeholder when model changes
                        updatePromptPlaceholder();

                        // Update UI state when model changes (to check API key for new provider)
                        updateUIState();

                        // Refresh chat entries to update seed copy button visibility
                        // (button only visible when FLUX model selected for FLUX-generated images)
                        if (chatEntriesListView != null) {
                            chatEntriesListView.refresh();
                        }
                    }
                    updateCostDisplay();
                });

                // Save aspect ratio selection changes
                aspectRatioComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        config.setDefaultAspectRatio(newVal);
                    }
                    updateCostDisplay();
                });

                // Save safety tolerance value changes
                safetyToleranceSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        config.setDefaultSafetyTolerance(newVal.intValue());
                    }
                    updateCostDisplay();
                });

                // Save image count selection changes
                imageCountSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        config.setDefaultImageCount((int) newVal.doubleValue());
                    }
                    updateCostDisplay();
                });

                // Save style selection changes
                stylePresetsComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        config.setDefaultStyle(newVal.getName());
                    }
                });

                // Save color style selection changes
                if (colorStyleComboBox != null) {
                    colorStyleComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                        if (newVal != null) {
                            config.setDefaultColorStyle(newVal.getName());
                        }
                    });
                }

                // Save BFL Prompt Upsampling changes
                if (bflPromptUpsamplingCheckBox != null) {
                    bflPromptUpsamplingCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                        if (newVal != null) {
                            config.setDefaultBflPromptUpsampling(newVal);
                        }
                    });
                }

                // Save Gemini Thinking Level changes
                if (geminiThinkingLevelChoiceBox != null) {
                    geminiThinkingLevelChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                        if (newVal != null) {
                            config.setDefaultGeminiThinkingLevel(newVal.toLowerCase());
                        }
                    });
                }

                // Save Gemini Advanced Features settings (Phase 2)
                // Note: Listeners will be set up after geminiAdvancedFeaturesSection is created in setupGeminiAdvancedFeatures()
                // This is handled separately to avoid null pointer issues during initialization
            }
        } catch (Exception e) {
            logger.error("Failed to setup persistence listeners", e);
        }
    }

    /**
     * Sets up the chat system with mock data and event handlers.
     */
    private void setupChatSystem() {
        // Initialize chat list
        chatList = FXCollections.observableArrayList();

        // Create filtered list to hide/show trashed chats
        filteredChatList = new FilteredList<>(chatList, this::shouldShowChat);

        // Setup trash toggle button text and style
        if (trashVisibilityToggle != null) {
            trashVisibilityToggle.setText(I18nManager.getText("chat.trash.toggle"));
            trashVisibilityToggle.setStyle(
                    "-fx-border-color: transparent; -fx-border-width: 0; -fx-background-insets: 0; -fx-border-insets: 0;");
        }

        // Remove borders from all main buttons
        if (newChatButton != null) {
            newChatButton.setStyle(
                    "-fx-border-color: transparent; -fx-border-width: 0; -fx-background-insets: 0; -fx-border-insets: 0;");
        }
        if (settingsButton != null) {
            settingsButton.setStyle(
                    "-fx-border-color: transparent; -fx-border-width: 0; -fx-background-insets: 0; -fx-border-insets: 0;");
        }

        // Setup chat list view with filtered list
        chatListView.setItems(filteredChatList);
        chatListView.setCellFactory(listView -> {
            ChatListCell cell = new ChatListCell();

            // Set up chat action callbacks
            cell.setOnDeleteRequested(this::onChatDeleteRequested);
            cell.setOnRestoreRequested(this::onChatRestoreRequested);
            cell.setOnPermanentDeleteRequested(this::onChatPermanentDeleteRequested);
            cell.setOnChatRenamed(this::onChatRenamed);

            // Inject TrashCleanupService for accurate retention calculations
            cell.setTrashCleanupService(trashCleanupService);

            return cell;
        });

        // Setup chat selection handling
        chatListView.getSelectionModel().selectedItemProperty().addListener((obs, oldChat, newChat) -> {
            onChatSelected(newChat);
        });

        // Setup virtualized ListView for ChatEntries (replaces VBox for performance)
        setupChatEntriesListView();

        // Initialize repository and load real chat data
        initializeChatRepository();
        loadRealChatData();
    }

    /**
     * Sets up the virtualized ListView for ChatEntry display.
     * This replaces the previous VBox-based approach for better performance with large chats.
     */
    private void setupChatEntriesListView() {
        if (chatEntriesListView == null) {
            logger.warn("chatEntriesListView is null - skipping setup");
            return;
        }

        // Configure ListView for optimal performance
        chatEntriesListView.setFocusTraversable(false);
        chatEntriesListView.getStyleClass().add("chat-entries-list");

        // CRITICAL: Fix horizontal scrolling - cells must fill width
        // This makes cells take full width and prevents horizontal scroll
        chatEntriesListView.setFixedCellSize(-1); // Variable height cells


        // CellFactory creates ChatEntryCard for each visible cell
        chatEntriesListView.setCellFactory(listView -> new javafx.scene.control.ListCell<ChatEntry>() {
            private art.moodframe.ui.component.ChatEntryCard currentCard = null;

            {
                // CRITICAL: Cell must fill entire width to prevent horizontal scroll
                setMaxWidth(Double.MAX_VALUE);
                setPrefWidth(0); // Let it grow with ListView
                // Remove default padding that can cause overflow
                setPadding(javafx.geometry.Insets.EMPTY);
            }

            @Override
            protected void updateItem(ChatEntry entry, boolean empty) {
                super.updateItem(entry, empty);

                // Dispose previous card to prevent memory leaks
                if (currentCard != null) {
                    currentCard.dispose();
                    currentCard = null;
                }

                if (empty || entry == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    // Create new ChatEntryCard for this entry
                    // Pass isFluxModelSelected supplier to constructor so it's available during image card creation
                    boolean isChatDeleted = currentChat != null && currentChat.isDeleted();
                    java.util.function.Supplier<Boolean> fluxModelSupplier = () ->
                        modelComboBox != null &&
                        modelComboBox.getValue() != null &&
                        !modelComboBox.getValue().toLowerCase().contains("gemini");
                    currentCard = new art.moodframe.ui.component.ChatEntryCard(entry, isChatDeleted, fluxModelSupplier);

                    // CRITICAL: Card must also fill width
                    currentCard.setMaxWidth(Double.MAX_VALUE);

                    // Set up callbacks
                    currentCard.setOnSeedCopy(seed -> {
                        if (seedTextField != null) {
                            seedTextField.setText(String.valueOf(seed));
                        }
                    });

                    currentCard.setOnPromptCopy(prompt -> {
                        if (promptTextArea != null) {
                            promptTextArea.setText(prompt);
                        }
                    });

                    currentCard.setOnImageClick((clickedEntry, imageIndex) -> {
                        showImagePreview(clickedEntry, imageIndex);
                    });

                    currentCard.setOnImageDownload(filePath -> {
                        downloadImageFromPath(filePath);
                    });

                    currentCard.setOnImageEdit(filePath -> {
                        editImageFromPath(filePath);
                    });

                    currentCard.setOnEntryDelete(entryId -> {
                        handleDeleteEntry(entryId);
                    });

                    currentCard.setOnImageDelete((entryId, imageId) -> {
                        handleDeleteImage(entryId, imageId);
                    });

                    setGraphic(currentCard);
                    setText(null);
                }
            }
        });
    }

    /**
     * Determines if a chat should be shown based on current filter settings.
     * 
     * @param chat the chat to check
     * @return true if the chat should be visible
     */
    private boolean shouldShowChat(Chat chat) {
        // Show active chats always
        if (!chat.isDeleted()) {
            return true;
        }

        // Show trashed chats only when toggle is selected
        return trashVisibilityToggle != null && trashVisibilityToggle.isSelected();
    }

    /**
     * Handles chat selection changes.
     * 
     * @param chat the newly selected chat, or null if none selected
     */
    private void onChatSelected(Chat chat) {
        // Skip processing if selection events are suppressed (e.g., during filter
        // updates)
        if (suppressChatSelectionEvents) {
            return;
        }

        // Don't reload if it's the same chat
        if (currentChat == chat) {
            return;
        }

        currentChat = chat;

        if (chat != null) {
            updateChatView(chat);
        } else {
            updateChatView(null);
        }

        // Update UI state when chat selection changes
        updateUIState();
    }

    /**
     * Updates the chat view area with the selected chat's content.
     * 
     * @param chat the chat to display, or null to show welcome message
     */
    private void updateChatView(Chat chat) {
        // Update header labels
        if (chat != null) {
            if (chatTitleLabel != null) {
                chatTitleLabel.setText(chat.getName());
            }
            updateChatTotalCost(chat);
        } else {
            if (chatTitleLabel != null) {
                chatTitleLabel.setText(I18nManager.getText("chat.select"));
            }
            if (chatTotalCostLabel != null) {
                chatTotalCostLabel.setText("");
            }
        }

        // Check if we should auto-scroll to bottom
        boolean shouldAutoScroll = false;
        if (chat != null && chat == currentChat) {
            int currentEntriesCount = chat.getEntries().size();
            boolean hasNewEntries = currentEntriesCount > lastChatEntriesCount;
            shouldAutoScroll = hasNewEntries;
            lastChatEntriesCount = currentEntriesCount;
        } else if (chat != null) {
            shouldAutoScroll = true;
            lastChatEntriesCount = chat.getEntries().size();
        }

        // Clear label tracking
        chatMetadataInfoLabel = null;
        deletedWarningLabel = null;
        emptyTitleLabel = null;
        emptyHintLabel = null;

        if (chat == null) {
            // Show welcome message, hide ListView
            welcomeContainer.setVisible(true);
            welcomeLabel.setText(I18nManager.getText("app.welcome"));
            chatEntriesListView.setVisible(false);
            chatEntriesListView.setItems(null);
            lastChatEntriesCount = 0;
        } else {
            // Hide welcome, show ListView with chat entries
            welcomeContainer.setVisible(false);
            chatEntriesListView.setVisible(true);

            // Create ObservableList from chat entries for virtualized ListView
            javafx.collections.ObservableList<ChatEntry> entries =
                javafx.collections.FXCollections.observableArrayList(chat.getEntries());
            chatEntriesListView.setItems(entries);

            if (config.getLoggingEnabled()) {
                logger.info("Loading chat {} with {} entries (virtualized ListView)",
                        chat.getName(), entries.size());
            }

            // Auto-scroll to bottom if needed
            if (shouldAutoScroll && !entries.isEmpty()) {
                Platform.runLater(() -> {
                    chatEntriesListView.scrollTo(entries.size() - 1);
                });
            }
        }
    }

    // NOTE: loadChatEntriesInBatches removed - replaced by virtualized ListView

    /**
     * Creates a chat header with title and metadata.
     * 
     * @param chat the chat
     * @return the header VBox
     */
    private VBox createChatHeader(Chat chat) {
        VBox header = new VBox(4.0);
        header.setPadding(new javafx.geometry.Insets(0, 0, 16.0, 0));

        Label chatTitle = new Label(chat.getName());
        chatTitle.getStyleClass().add("title-2");

        // TOTAL COST = All images ever created (regardless of deletion status)
        double totalCost = chat.getEntries().stream()
                .mapToDouble(ChatEntry::getCost) // All entries, deleted or not
                .sum();

        // DELETED COST = Only deleted images
        // 1. From fully deleted entries
        double deletedCost = chat.getEntries().stream()
                .filter(entry -> entry.getDeletedAt() != null)
                .mapToDouble(ChatEntry::getCost)
                .sum();

        // 2. From deleted images within active entries
        deletedCost += chat.getEntries().stream()
                .filter(entry -> entry.getDeletedAt() == null)
                .mapToDouble(ChatEntry::getDeletedCost)
                .sum();

        // Create info line with message count and total cost
        int messageCount = chat.getEntries().size();
        String messagesText = messageCount == 1 ? I18nManager.getText("chat.messages.singular")
                : I18nManager.getText("chat.messages.plural");

        String infoText = String.format("%s: %s • %d %s",
                I18nManager.getText("chat.created"),
                chat.getCreatedAt().toLocalDate(),
                messageCount,
                messagesText);

        if (totalCost > 0) {
            String formattedTotal = String.format("%.2f", totalCost);

            if (deletedCost > 0) {
                // Show both total and deleted amount
                String formattedDeleted = String.format("%.2f", deletedCost);
                infoText += " • " + java.text.MessageFormat.format(
                        I18nManager.getText("chat.total.cost.with.deleted"),
                        formattedTotal, formattedDeleted);
            } else {
                // Show only total
                infoText += " • " + java.text.MessageFormat.format(
                        I18nManager.getText("chat.total.cost"), formattedTotal);
            }
        }

        Label chatInfo = new Label(infoText);
        chatInfo.getStyleClass().addAll("text-muted", "text-small");

        // Track this label for live language updates
        chatMetadataInfoLabel = chatInfo;

        if (chat.isDeleted()) {
            deletedWarningLabel = new Label(I18nManager.getText("chat.deleted.warning"));
            deletedWarningLabel.getStyleClass().addAll("text-warning", "text-small", "text-bold");
            header.getChildren().addAll(chatTitle, chatInfo, deletedWarningLabel);
        } else {
            header.getChildren().addAll(chatTitle, chatInfo);
        }

        return header;
    }

    /**
     * Updates the chat header (title, message count and cost) without reloading the
     * entire chat. With virtualized ListView, we just update the title label and cost.
     */
    private void updateChatHeader() {
        if (currentChat != null) {
            // Update title label if present
            if (chatTitleLabel != null) {
                chatTitleLabel.setText(currentChat.getName());
            }
            // Update cost
            updateChatTotalCost(currentChat);
        }
    }

    /**
     * Updates the title label in the chat header.
     */
    private void updateChatHeaderTitle(Label titleLabel) {
        if (currentChat != null && titleLabel != null) {
            titleLabel.setText(currentChat.getName());
        }
    }

    /**
     * Updates the info label in the chat header VBox.
     */
    private void updateChatHeaderInfo(VBox headerVBox) {
        // TOTAL COST OF ALL ENTRIES = Sum of all entry costs (each entry cost includes
        // its active + deleted images)
        double totalEntryCosts = currentChat.getEntries().stream()
                .mapToDouble(ChatEntry::getCost)
                .sum();

        // DELETED COST FROM ACTIVE ENTRIES = Cost of deleted images within still-active
        // entries
        double deletedFromActiveEntries = currentChat.getEntries().stream()
                .mapToDouble(ChatEntry::getDeletedCost)
                .sum();

        // CHAT-LEVEL DELETED COST = Cost from fully deleted entries
        double chatLevelDeletedCost = currentChat.getDeletedCost();

        // TOTAL COST = All entry costs + costs from deleted entries
        double totalCost = totalEntryCosts + chatLevelDeletedCost;

        // TOTAL DELETED COST = Deleted within entries + deleted entries
        double totalDeletedCost = deletedFromActiveEntries + chatLevelDeletedCost;

        // Create updated info text
        int messageCount = currentChat.getEntries().size();
        String messagesText = messageCount == 1 ? I18nManager.getText("chat.messages.singular")
                : I18nManager.getText("chat.messages.plural");

        String infoText = String.format("%s: %s • %d %s",
                I18nManager.getText("chat.created"),
                currentChat.getCreatedAt().toLocalDate(),
                messageCount,
                messagesText);

        if (totalCost > 0) {
            if (totalDeletedCost > 0) {
                // Show both total and deleted costs
                infoText += String.format(" • %s",
                        I18nManager.getText("chat.total.cost.with.deleted",
                                String.format("%.2f", totalCost),
                                String.format("%.2f", totalDeletedCost)));
            } else {
                // Show only total cost
                infoText += String.format(" • %s",
                        I18nManager.getText("chat.total.cost", String.format("%.2f", totalCost)));
            }
        }

        // Find and update the info label
        for (javafx.scene.Node child : headerVBox.getChildren()) {
            if (child instanceof Label) {
                Label label = (Label) child;
                if (label.getStyleClass().contains("text-muted") && label.getStyleClass().contains("text-small")) {
                    label.setText(infoText);
                    break;
                }
            }
        }
    }

    /**
     * Updates the total cost display in the chat header.
     * 
     * @param chat the chat to calculate cost for
     */
    private void updateChatTotalCost(Chat chat) {
        if (chatTotalCostLabel != null && chat != null) {
            // TOTAL COST OF ALL ENTRIES = Sum of all entry costs (each entry cost includes
            // its active + deleted images)
            double totalEntryCosts = chat.getEntries().stream()
                    .mapToDouble(ChatEntry::getCost)
                    .sum();

            // DELETED COST FROM ACTIVE ENTRIES = Cost of deleted images within still-active
            // entries
            double deletedFromActiveEntries = chat.getEntries().stream()
                    .mapToDouble(ChatEntry::getDeletedCost)
                    .sum();

            // CHAT-LEVEL DELETED COST = Cost from fully deleted entries
            double chatLevelDeletedCost = chat.getDeletedCost();

            // TOTAL COST = All entry costs + costs from deleted entries
            double totalCost = totalEntryCosts + chatLevelDeletedCost;

            // TOTAL DELETED COST = Deleted within entries + deleted entries
            double totalDeletedCost = deletedFromActiveEntries + chatLevelDeletedCost;

            if (totalCost > 0) {
                String formattedTotal = String.format("%.2f", totalCost);

                if (totalDeletedCost > 0) {
                    // Show both total and deleted amount
                    String formattedDeleted = String.format("%.2f", totalDeletedCost);
                    chatTotalCostLabel.setText(java.text.MessageFormat.format(
                            I18nManager.getText("chat.total.cost.with.deleted"),
                            formattedTotal, formattedDeleted));
                } else {
                    // Show only total
                    chatTotalCostLabel.setText(java.text.MessageFormat.format(
                            I18nManager.getText("chat.total.cost"), formattedTotal));
                }
            } else {
                chatTotalCostLabel.setText("");
            }
        }
    }

    /**
     * Creates an empty state component for chats with no entries.
     * 
     * @return the empty state VBox
     */
    private VBox createEmptyState() {
        VBox emptyState = new VBox(12.0);
        emptyState.setId("empty-state"); // Add ID so it can be removed when first entry is added
        emptyState.setAlignment(javafx.geometry.Pos.CENTER);
        emptyState.setPadding(new javafx.geometry.Insets(40.0));
        emptyState.getStyleClass().add("bg-inset");
        emptyState.setStyle("-fx-background-radius: 8px;");

        org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon("mdi2c-chat-plus-outline");
        icon.setIconSize(48);
        icon.getStyleClass().add("text-muted");

        emptyTitleLabel = new Label(I18nManager.getText("chat.empty.title"));
        emptyTitleLabel.getStyleClass().addAll("title-3", "text-muted");

        emptyHintLabel = new Label(I18nManager.getText("chat.empty.hint"));
        emptyHintLabel.getStyleClass().addAll("text-muted", "text-small");
        emptyHintLabel.setWrapText(true);
        emptyHintLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        emptyHintLabel.setMaxWidth(300);

        emptyState.getChildren().addAll(icon, emptyTitleLabel, emptyHintLabel);
        return emptyState;
    }

    /**
     * Creates a loading state for chat content.
     */
    private VBox createChatLoadingState() {
        VBox loadingState = new VBox(12.0);
        loadingState.setId("chat-loading-state"); // For easy removal later
        loadingState.setAlignment(javafx.geometry.Pos.CENTER);
        loadingState.setPadding(new javafx.geometry.Insets(40.0));
        loadingState.getStyleClass().add("bg-inset");
        loadingState.setStyle("-fx-background-radius: 8px;");

        javafx.scene.control.ProgressIndicator progressIndicator = new javafx.scene.control.ProgressIndicator();
        progressIndicator.setMaxSize(40, 40);

        Label loadingLabel = new Label(I18nManager.getText("status.loading.chat"));
        loadingLabel.getStyleClass().addAll("text-muted", "text-small");
        loadingLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        loadingState.getChildren().addAll(progressIndicator, loadingLabel);
        return loadingState;
    }

    // Chat action handlers

    /**
     * Handles chat delete requests (move to trash).
     * 
     * @param chat the chat to delete
     */
    private void onChatDeleteRequested(Chat chat) {
        // Move to trash without confirmation (user can restore from trash)

        // CRITICAL: Use softDeleteChat to move files to trash
        try {
            boolean success = chatHistory.softDeleteChat(chat.getId());

            // A new chat without any entry is stored in the database only with its first message, so there is
            // nothing to move to the trash: it is just removed from the list
            boolean unsavedEmptyChat = !success && chat.getEntries().isEmpty();
            if (success || unsavedEmptyChat) {
                // Update local model
                chat.markAsDeleted();

                // Remove empty chats from the list entirely when soft deleted
                // This prevents them from reappearing later
                if (chat.getEntries().isEmpty()) {
                    chatList.remove(chat);
                } else {
                    // Refresh filter to hide the trashed chat if toggle is off
                    filteredChatList.setPredicate(this::shouldShowChat);
                }

                // If this was the current chat, reload it to show images from trash
                if (currentChat == chat) {
                    // Force reload of chat content to update image paths
                    updateChatView(chat);
                }

                // Update UI state as input area should be disabled for deleted chats
                updateUIState();

                setStatus(I18nManager.getText("status.chat.moved.trash"));
            } else {
                logger.error("Failed to soft delete chat: {}", chat.getName());
                setStatus(I18nManager.getText("status.error.chat.deletion"));
            }
        } catch (Exception e) {
            logger.error("Failed to save chat deletion status to database: {}", chat.getName(), e);
            setStatus(I18nManager.getText("status.error.chat.deletion"));
        }
    }

    /**
     * Handles chat restore requests (restore from trash).
     * 
     * @param chat the chat to restore
     */
    private void onChatRestoreRequested(Chat chat) {
        // CRITICAL: Use restoreChat to restore files from trash
        try {
            boolean success = chatHistory.restoreChat(chat.getId());
            if (success) {
                // Update local model
                chat.restore();

                // If this was an empty chat that was removed from the list, add it back
                if (!chatList.contains(chat)) {
                    chatList.add(chat);
                }

                // Refresh filter to show the restored chat in active list
                filteredChatList.setPredicate(this::shouldShowChat);

                // If this was the current chat, reload it to show images from active directory
                if (currentChat == chat) {
                    // Force reload of chat content to update image paths
                    updateChatView(chat);
                }

                // Update UI state as input area should be enabled for restored chats
                updateUIState();

                setStatus(I18nManager.getText("status.chat.restored"));
            } else {
                logger.error("Failed to restore chat: {}", chat.getName());
                setStatus(I18nManager.getText("status.error.chat.restoration"));
            }
        } catch (Exception e) {
            logger.error("Failed to restore chat from database: {}", chat.getName(), e);
            setStatus(I18nManager.getText("status.error.chat.restoration"));
        }
    }

    /**
     * Handles permanent chat delete requests.
     * 
     * @param chat the chat to delete permanently
     */
    private void onChatPermanentDeleteRequested(Chat chat) {
        // Calculate how long the chat has been in trash
        long daysInTrash = java.time.temporal.ChronoUnit.DAYS.between(
                chat.getDeletedAt(), java.time.LocalDateTime.now());

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(I18nManager.getText("dialog.confirm.chat.delete.permanent.title"));
        alert.setHeaderText(I18nManager.getText("dialog.confirm.chat.delete.permanent.header"));
        alert.setContentText(I18nManager.getText("dialog.confirm.chat.delete.permanent.message",
                chat.getName(), String.valueOf(daysInTrash)));
        alert.initOwner(chatListView.getScene().getWindow());

        ButtonType deleteButton = new ButtonType(I18nManager.getText("dialog.confirm.chat.delete.permanent.button"),
                ButtonBar.ButtonData.YES);
        alert.getButtonTypes().setAll(deleteButton, ButtonType.CANCEL);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == deleteButton) {
            // CRITICAL: Permanently delete from database and disk
            try {
                boolean success = chatHistory.deleteChatPermanently(chat.getId());

                if (success) {
                    // Remove from UI list only after successful database deletion
                    chatList.remove(chat);

                    // If this was the current chat, deselect it
                    if (currentChat == chat) {
                        chatListView.getSelectionModel().clearSelection();
                        currentChat = null;
                        // Clear the chat view and update totals
                        chatEntriesListView.setItems(null);
                        welcomeContainer.setVisible(true);
                        updateChatTotalCost(null);
                    }

                    setStatus(I18nManager.getText("status.chat.deleted"));
                } else {
                    logger.warn("Permanent deletion failed for chat: {}", chat.getName());
                    setStatus(I18nManager.getText("status.error.chat.delete.partial"));
                }

            } catch (Exception e) {
                logger.error("Failed to permanently delete chat: {}", chat.getName(), e);
                setStatus(java.text.MessageFormat.format(
                        I18nManager.getText("status.error.chat.delete.failed"), e.getMessage()));
            }
        }
    }

    /**
     * Handles chat rename requests (save to database).
     * 
     * @param chat the chat that was renamed
     */
    private void onChatRenamed(Chat chat) {
        // Save the renamed chat to database
        try {
            chatHistory.saveChat(chat);

            // Update the chat header if this is the currently selected chat
            // Use ID comparison instead of object reference comparison for reliability
            if (currentChat != null && currentChat.getId().equals(chat.getId())) {
                // Update the current chat reference to ensure synchronization
                currentChat = chat;

                // Update the chat title label directly (top header)
                if (chatTitleLabel != null) {
                    chatTitleLabel.setText(chat.getName());
                } else {
                    logger.warn("chatTitleLabel is null, cannot update header title");
                }

                // Update the chat header inside the scrollable area
                updateChatHeader();

                updateChatTotalCost(chat);
            }

            setStatus(java.text.MessageFormat.format(
                    I18nManager.getText("status.chat.renamed"), chat.getName()));
        } catch (Exception e) {
            logger.error("Failed to save chat rename to database: {}", chat.getName(), e);
            setStatus(java.text.MessageFormat.format(
                    I18nManager.getText("status.error.chat.rename"), e.getMessage()));
        }
    }

    /**
     * Sets up event handlers for interactive components.
     */
    private void setupEventHandlers() {
        // Update UI state when prompt text changes
        promptTextArea.textProperty().addListener((obs, oldText, newText) -> {
            updateUIState();
            updatePromptPreview();
            updateCharacterCount(newText);
        });

        // Handle Enter key in prompt area (Enter for line break, Shift+Enter to send)
        promptTextArea.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                if (event.isShiftDown()) {
                    // Shift+Enter: Send message
                    if (!sendButton.isDisabled()) {
                        onSendClicked();
                    }
                    event.consume(); // Prevent default line break
                }
                // Plain Enter: Allow default TextArea behavior (inserts newline)
                // Do NOT consume the event
            }
        });
    }

    /**
     * Gets the maximum prompt length based on the current provider.
     *
     * BFL FLUX models: 512 tokens ≈ 2,000 characters
     * Google Gemini models: 65,536 tokens ≈ 250,000+ characters
     *
     * @return maximum character limit for current provider
     */
    private int getMaxPromptLength() {
        if (modelComboBox == null || modelComboBox.getValue() == null) {
            return 2000; // Default to BFL limit
        }

        String selectedModel = modelComboBox.getValue();

        // Check if it's a Gemini/NB2 model (Google API supports long prompts)
        art.moodframe.model.GenerationModel generationModel = art.moodframe.model.GenerationModel.fromLocalizedDisplayName(selectedModel);
        if (generationModel != null && generationModel.getProvider() == ApiProvider.GOOGLE_GEMINI) {
            return 250000; // Google models handle very long prompts (up to 65,536 tokens)
        }
        if (generationModel != null && generationModel.getProvider() == ApiProvider.OPENAI) {
            return art.moodframe.model.GenerationModel.openAiMaxPromptLength(
                    art.moodframe.model.GenerationModel.openAiModelId(selectedModel));
        }

        // BFL FLUX models have 512 token limit ≈ 2,000 characters
        return 2000;
    }

    /**
     * Updates the character count display and validates prompt length.
     *
     * @param text the current text in the prompt area
     */
    private void updateCharacterCount(String text) {
        if (characterCountLabel == null)
            return;

        int currentLength = text != null ? text.length() : 0;
        int maxLength = getMaxPromptLength();

        characterCountLabel.setText(currentLength + "/" + maxLength);

        // Update styling based on length
        characterCountLabel.getStyleClass().removeAll("text-warning", "text-danger", "text-muted");

        if (currentLength > maxLength) {
            characterCountLabel.getStyleClass().add("text-danger");
        } else if (currentLength > maxLength * 0.9) { // 90% warning threshold
            characterCountLabel.getStyleClass().add("text-warning");
        } else {
            characterCountLabel.getStyleClass().add("text-muted");
        }
    }

    /**
     * Updates the UI state based on current conditions (API key, text input, and
     * chat selection).
     */
    private void updateUIState() {
        String promptText = promptTextArea.getText();
        boolean hasText = promptText != null && !promptText.trim().isEmpty();
        boolean hasValidText = hasText && (promptText != null && promptText.length() <= getMaxPromptLength());

        // Check if the currently selected model's provider has a valid API key
        boolean hasApiKeyForSelectedModel = hasApiKeyForCurrentProvider();
        boolean hasChatSelected = currentChat != null && !currentChat.isDeleted();

        boolean hasModelSelected = modelComboBox != null && modelComboBox.getValue() != null;

        // Send button requires valid text, API key for selected provider, active chat selection and a model
        sendButton.setDisable(!hasValidText || !hasApiKeyForSelectedModel || !hasChatSelected || !hasModelSelected);

        // UI elements that require API key and chat selection to function
        updateChatDependentElements(hasApiKeyForSelectedModel, hasChatSelected);

        // Update prompt context (YouTube button) visibility - requires Gemini key AND chat selection
        updatePromptContextVisibility();
        updateBrainstormButton();
    }

    /**
     * Checks if the currently selected model's provider has a valid API key.
     *
     * @return true if the selected model's provider has a valid API key
     */
    private boolean hasApiKeyForCurrentProvider() {
        try {
            art.moodframe.config.ApplicationConfig config = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.config.ApplicationConfig.class);

            if (config == null) {
                return false;
            }

            // Get selected model
            String selectedModel = modelComboBox.getValue();
            if (selectedModel == null || selectedModel.trim().isEmpty()) {
                // No model selected - require at least one valid key
                return config.hasApiKey() || config.hasGeminiApiKey() || config.hasOpenAiApiKey();
            }

            // Determine provider based on selected model using GenerationModel enum
            art.moodframe.model.GenerationModel generationModel = art.moodframe.model.GenerationModel.fromLocalizedDisplayName(selectedModel);
            if (generationModel != null) {
                // Use accurate provider detection (handles NB2, Gemini, OpenAI etc.)
                return switch (generationModel.getProvider()) {
                    case GOOGLE_GEMINI -> config.hasGeminiApiKey();
                    case OPENAI -> config.hasOpenAiApiKey();
                    default -> config.hasApiKey();
                };
            } else if (selectedModel.toLowerCase().contains("gemini")) {
                return config.hasGeminiApiKey();
            } else {
                return config.hasApiKey();
            }
        } catch (Exception e) {
            logger.error("Error checking API key for current provider", e);
            return false;
        }
    }

    /**
     * Gets the dropdown items of the OpenAI models, empty while the provider is disabled or has no token.
     *
     * @return the items ({@link art.moodframe.model.GenerationModel#OPENAI_PREFIX} + model id)
     */
    private java.util.List<String> openAiModelItems() {
        art.moodframe.config.ApplicationConfig openAiConfig = art.moodframe.config.ServiceFactory.getInstance()
                .getService(art.moodframe.config.ApplicationConfig.class);
        if (openAiConfig == null || !openAiConfig.isOpenAiProviderEnabled() || !openAiConfig.hasOpenAiApiKey()) {
            return java.util.List.of();
        }
        return openAiConfig.getOpenAiModels().stream()
                .map(id -> art.moodframe.model.GenerationModel.OPENAI_PREFIX + id).toList();
    }

    /**
     * Checks if any provider has a valid API key.
     * Used for the "New Chat" button which should be enabled if any provider is usable.
     *
     * @return true if at least one provider has a valid API key
     */
    private boolean hasApiKeyForAnyProvider() {
        try {
            art.moodframe.config.ApplicationConfig config = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.config.ApplicationConfig.class);

            if (config == null) {
                return false;
            }

            return config.hasApiKey() || config.hasGeminiApiKey() || config.hasOpenAiApiKey();
        } catch (Exception e) {
            logger.error("Error checking API keys", e);
            return false;
        }
    }

    /**
     * Updates UI elements that depend on API key availability and chat selection.
     *
     * @param hasApiKey       true if a valid API key is configured for the selected provider
     * @param hasChatSelected true if an active chat is selected
     */
    private void updateChatDependentElements(boolean hasApiKey, boolean hasChatSelected) {
        // Check if any models are available (at least one provider enabled with valid API key)
        boolean hasModels = modelComboBox != null && !modelComboBox.getItems().isEmpty();

        boolean enableInput = hasApiKey && hasChatSelected && hasModels;

        // New chat button only requires API key for any provider (not just selected one)
        boolean hasAnyApiKey = hasApiKeyForAnyProvider();
        newChatButton.setDisable(!hasAnyApiKey);

        // Input area requires both API key and active chat selection
        promptTextArea.setDisable(!enableInput);
        if (bflPromptUpsamplingCheckBox != null) {
            bflPromptUpsamplingCheckBox.setDisable(!enableInput);
        }
        if (bflPromptUpsamplingHint != null) {
            bflPromptUpsamplingHint.setDisable(!enableInput);
        }

        // Gemini Thinking Level: disable if no chat OR if image attached
        // (thinking_level not supported with input images in Gemini API)
        boolean hasAttachment = attachedImageFile != null;
        boolean thinkingLevelEnabled = enableInput && !hasAttachment;
        if (geminiThinkingLevelChoiceBox != null) {
            geminiThinkingLevelChoiceBox.setDisable(!thinkingLevelEnabled);
        }
        if (geminiThinkingLevelLabel != null) {
            geminiThinkingLevelLabel.setDisable(!thinkingLevelEnabled);
            // Use opacity to ensure visual feedback (CSS might override disabled styling)
            geminiThinkingLevelLabel.setOpacity(thinkingLevelEnabled ? 1.0 : 0.4);
        }
        if (geminiThinkingLevelHint != null) {
            geminiThinkingLevelHint.setDisable(!thinkingLevelEnabled);
        }

        safetyToleranceSlider.setDisable(!enableInput);
        safetyToleranceLabel.setDisable(!enableInput);
        seedTextField.setDisable(!enableInput);
        clearSeedButton.setDisable(!enableInput);
        modelComboBox.setDisable(!enableInput);
        modelFamilyChoiceBox.setDisable(!enableInput);
        resolutionChoiceBox.setDisable(!enableInput);
        if (modelLabel != null)
            modelLabel.setDisable(!enableInput);
        imageCountSlider.setDisable(!enableInput);
        imageCountLabel.setDisable(!enableInput);
        aspectRatioComboBox.setDisable(!enableInput);
        if (aspectRatioLabel != null)
            aspectRatioLabel.setDisable(!enableInput);
        stylePresetsComboBox.setDisable(!enableInput);
        if (styleLabel != null)
            styleLabel.setDisable(!enableInput);
        if (colorStyleComboBox != null)
            colorStyleComboBox.setDisable(!enableInput);
        if (colorStyleLabel != null)
            colorStyleLabel.setDisable(!enableInput);
        if (seedLabel != null)
            seedLabel.setDisable(!enableInput);
        if (imageAttachmentLabel != null)
            imageAttachmentLabel.setDisable(!enableInput);
        uploadImageButton.setDisable(!enableInput);
        removeImageButton.setDisable(!enableInput);

        // Disable multi-image panel when no chat is selected
        if (multiImagePanel != null) {
            multiImagePanel.setDisable(!enableInput);
        }
        if (multiImageAttachmentContainer != null) {
            multiImageAttachmentContainer.setDisable(!enableInput);
        }

        // Disable image placeholder components when no chat is selected
        if (noImagePlaceholder != null)
            noImagePlaceholder.setDisable(!enableInput);
        if (noImageLabel != null)
            noImageLabel.setDisable(!enableInput);
        if (imagePreviewContainer != null)
            imagePreviewContainer.setDisable(!enableInput);

        // Disable Gemini Advanced Features when no chat is selected
        if (geminiAdvancedFeaturesContainer != null)
            geminiAdvancedFeaturesContainer.setDisable(!enableInput);

        // Disable the video parameters (duration) and the FLUX.2 parameters when no chat is selected
        if (videoParametersContainer != null)
            videoParametersContainer.setDisable(!enableInput);
        if (flux2ParametersContainer != null)
            flux2ParametersContainer.setDisable(!enableInput);

        // Update placeholder text to guide user
        if (!hasApiKey) {
            // Determine which provider is selected to show appropriate message
            boolean isGemini = modelComboBox != null &&
                             modelComboBox.getValue() != null &&
                             modelComboBox.getValue().toLowerCase().contains("gemini");

            String keyMessageKey = isGemini ? "status.error.api.key.missing.gemini"
                                            : "status.error.api.key.missing.bfl";
            promptTextArea.setPromptText(I18nManager.getText(keyMessageKey));
        } else if (!hasChatSelected) {
            promptTextArea.setPromptText(I18nManager.getText("input.prompt.placeholder.nochat"));
        } else if (attachedImageFile != null) {
            promptTextArea.setPromptText(I18nManager.getText("status.image.edit.attached"));
        } else {
            // Check if Gemini model is selected for enhanced placeholder
            boolean isGeminiSelected = modelComboBox != null &&
                                      modelComboBox.getValue() != null &&
                                      modelComboBox.getValue().toLowerCase().contains("gemini");

            if (isGeminiSelected) {
                promptTextArea.setPromptText(I18nManager.getText("input.prompt.placeholder.gemini"));
            } else {
                promptTextArea.setPromptText(I18nManager.getText("input.prompt.placeholder"));
            }
        }
    }

    /**
     * Checks and updates the API key status by reading from ApplicationConfig.
     * Only shows "valid" (green) if ALL enabled providers have valid API keys.
     */
    private void checkApiKeyStatus() {
        try {
            // Get ApplicationConfig from ServiceFactory
            art.moodframe.config.ApplicationConfig config = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.config.ApplicationConfig.class);

            if (config != null) {
                // Check if ALL enabled providers have valid keys
                boolean bflEnabled = config.isBflProviderEnabled();
                boolean geminiEnabled = config.isGeminiProviderEnabled();
                boolean bflHasKey = config.hasApiKey(); // BFL API key
                boolean geminiHasKey = config.hasGeminiApiKey(); // Gemini API key

                // API keys are only "valid" if all enabled providers have keys
                boolean hasValidKey = true;
                if (bflEnabled && !bflHasKey) {
                    hasValidKey = false;
                }
                if (geminiEnabled && !geminiHasKey) {
                    hasValidKey = false;
                }

                // If no providers are enabled, require at least one key
                if (!bflEnabled && !geminiEnabled) {
                    hasValidKey = bflHasKey || geminiHasKey;
                }

                updateApiKeyStatus(hasValidKey);
            } else {
                logger.warn("ApplicationConfig not available, assuming no API key");
                updateApiKeyStatus(false);
            }
        } catch (Exception e) {
            logger.error("Error checking API key status", e);
            updateApiKeyStatus(false);
        }
    }

    /**
     * Refreshes the API key status and updates the UI accordingly.
     * This method should be called after API key configuration changes.
     */
    public void refreshApiKeyStatus() {
        checkApiKeyStatus();
        // Also update prompt context visibility (depends on Gemini API key)
        updatePromptContextVisibility();
        updateBrainstormButton();
    }

    /**
     * Refreshes the style presets dropdown with updated list from backend.
     * This method should be called after new styles are added in settings.
     */
    private void refreshStylePresets() {
        if (stylePresetsComboBox == null) {
            return;
        }

        // Save current selection name
        art.moodframe.model.StylePresetItem currentItem = stylePresetsComboBox.getValue();
        String currentSelectionName = currentItem != null ? currentItem.getName() : null;

        // Clear and reload all styles
        stylePresetsComboBox.getItems().clear();

        // Load style presets from backend
        art.moodframe.service.StylePresetService styleService = art.moodframe.config.ServiceFactory.getInstance()
                .getService(art.moodframe.service.StylePresetService.class);

        if (styleService != null) {
            // Add "None" option first
            stylePresetsComboBox.getItems().add(new art.moodframe.model.StylePresetItem("None", "", false));

            // Add all styles from service
            for (art.moodframe.model.StylePreset preset : styleService.getAllStyles()) {
                String name = preset.getName();
                if (name != null && !name.trim().isEmpty()) {
                    stylePresetsComboBox.getItems().add(new art.moodframe.model.StylePresetItem(
                            name.trim(), preset.getPromptText(), preset.isDefault()));
                }
            }

            // Restore previous selection if it still exists
            if (currentSelectionName != null) {
                art.moodframe.model.StylePresetItem matchingItem = stylePresetsComboBox.getItems().stream()
                        .filter(item -> currentSelectionName.equals(item.getName()))
                        .findFirst()
                        .orElse(stylePresetsComboBox.getItems().isEmpty() ? null : stylePresetsComboBox.getItems().get(0));
                if (matchingItem != null) {
                    stylePresetsComboBox.setValue(matchingItem);
                }
            } else if (!stylePresetsComboBox.getItems().isEmpty()) {
                stylePresetsComboBox.setValue(stylePresetsComboBox.getItems().get(0));
            }

        } else {
            logger.warn("StylePresetService not available during refresh");
            art.moodframe.model.StylePresetItem noneItem = new art.moodframe.model.StylePresetItem("None", "", false);
            stylePresetsComboBox.getItems().add(noneItem);
            stylePresetsComboBox.setValue(noneItem);
        }
    }

    /**
     * Updates the API key status indicator with theme-aware colors.
     * 
     * @param hasValidKey true if a valid API key is configured
     */
    private void updateApiKeyStatus(boolean hasValidKey) {
        // Clear existing style classes
        apiKeyStatusIcon.getStyleClass().clear();
        apiKeyStatusLabel.getStyleClass().clear();

        // Add base classes
        apiKeyStatusIcon.getStyleClass().add("ikonli-font-icon");

        if (hasValidKey) {
            apiKeyStatusIcon.setIconLiteral("mdi2k-key");
            apiKeyStatusIcon.getStyleClass().add("success");
            apiKeyStatusLabel.setText(I18nManager.getText("status.api.key.valid"));
            apiKeyStatusLabel.setStyle("-fx-text-fill: -color-success-emphasis;");
        } else {
            apiKeyStatusIcon.setIconLiteral("mdi2k-key");
            apiKeyStatusIcon.getStyleClass().add("danger");
            apiKeyStatusLabel.setText(I18nManager.getText("status.api.key.none"));
            apiKeyStatusLabel.setStyle("-fx-text-fill: -color-danger-emphasis;");
        }

        updateUIState();
    }

    /**
     * Updates the initial status message based on API key availability.
     */
    private void updateInitialStatus() {
        try {
            art.moodframe.config.ApplicationConfig config = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.config.ApplicationConfig.class);

            if (config != null && config.hasApiKey()) {
                setStatus(I18nManager.getText("status.ready"));
            } else {
                setStatus(I18nManager.getText("status.ready"));
            }
        } catch (Exception e) {
            logger.warn("Error checking API key for initial status", e);
            setStatus(I18nManager.getText("status.ready"));
        }
    }

    /**
     * Updates the status message.
     * 
     * @param message the status message to display
     */
    private void setStatus(String message) {
        boolean isError = message.startsWith("Error") || message.startsWith("Fehler");

        // Don't overwrite error messages during protection period
        if (!isError && System.currentTimeMillis() < errorStatusProtectedUntil)
            return;

        // Error messages get 4s protection from being overwritten
        if (isError)
            errorStatusProtectedUntil = System.currentTimeMillis() + 4000;

        statusLabel.setText(message);
    }

    // FXML Event Handlers

    /**
     * Handles the settings button click event.
     */
    @FXML
    private void onSettingsClicked() {
        setStatus(I18nManager.getText("status.opening.settings"));

        // NOTE: ViewportTracker removed - no tracking to pause

        try {
            // Load the Settings Dialog FXML
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/fxml/SettingsDialog.fxml"));
            loader.setResources(I18nManager.getCurrentResourceBundle());
            DialogPane dialogPane = loader.load();

            // Get the controller
            SettingsController settingsController = loader.getController();

            // Load settings dialog CSS for header styling
            try {
                String settingsCss = getClass().getResource("/css/settings-dialog.css").toExternalForm();
                dialogPane.getStylesheets().add(settingsCss);
            } catch (Exception ex) {
                logger.warn("Failed to load settings dialog CSS", ex);
            }

            // Create and configure the dialog
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setDialogPane(dialogPane);
            dialog.setTitle(I18nManager.getText("settings.window.title"));
            dialog.initOwner(settingsButton.getScene().getWindow());

            // Make dialog resizable for user customization
            dialog.setResizable(true);

            // Set minimum and maximum size on the actual window (Stage)
            dialog.setOnShown(e -> {
                Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
                stage.setMinWidth(730);
                stage.setMinHeight(810);
                stage.setMaxWidth(1400);
                stage.setMaxHeight(1000);

                // Apply title-3 style class to header label
                Platform.runLater(() -> {
                    javafx.scene.Node header = dialogPane.lookup(".header-panel");
                    if (header instanceof javafx.scene.layout.Region headerRegion) {
                        javafx.scene.Node headerText = headerRegion.lookup(".label");
                        if (headerText instanceof javafx.scene.control.Label label) {
                            if (!label.getStyleClass().contains("title-3")) {
                                label.getStyleClass().add("title-3");
                            }
                        }
                    }
                });
            });

            // Reset status to ready after dialog is shown
            setStatus(I18nManager.getText("status.ready"));

            // Show the dialog and handle the result
            dialog.showAndWait();

            // Auto-save settings when dialog is closed (regardless of how it was closed)
            // Always apply settings and refresh UI after dialog closes
            settingsController.applySettings();

            // Refresh API key status in case it changed
            refreshApiKeyStatus();

            // Refresh style presets in case new ones were added
            refreshStylePresets();

            // Refresh color styles in case new ones were added
            refreshColorStyles();

            // Refresh available models based on enabled providers
            populateAvailableModels();

            setStatus(I18nManager.getText("status.ready"));

        } catch (IOException e) {
            logger.error("Failed to load Settings Dialog", e);
            setStatus(I18nManager.getText("status.error.settings.failed"));

            // Show error alert
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Settings Error");
            alert.setHeaderText("Could not open settings dialog");
            alert.setContentText("Error: " + e.getMessage());
            alert.initOwner(settingsButton.getScene().getWindow());
            alert.showAndWait();

        } catch (Exception e) {
            logger.error("Unexpected error opening Settings Dialog", e);
            setStatus(I18nManager.getText("status.error.settings.occurred"));
        }
        // NOTE: ViewportTracker removed - no tracking to resume
    }

    /**
     * Handles the new chat button click event.
     */
    @FXML
    private void onNewChatClicked() {
        setStatus(I18nManager.getText("status.creating.chat"));

        // Create new chat
        Chat newChat = new Chat.Builder()
                .name("New Chat " + (chatList.size() + 1))
                .build();

        // Add to beginning of list
        chatList.add(0, newChat);

        // Select the new chat
        chatListView.getSelectionModel().select(0);

        if (config != null && config.getLoggingEnabled()) {
            logger.info("Created new chat: {}", newChat.getName());
        }
        setStatus(I18nManager.getText("status.chat.created"));
    }

    /**
     * Handles the send button click event.
     */
    @FXML
    private void onSendClicked() {
        String userPrompt = promptTextArea.getText().trim();
        if (userPrompt.isEmpty() || userPrompt.length() > getMaxPromptLength()) {
            return;
        }

        // Build final prompt including style and YouTube content
        String prompt = buildFinalPrompt();
        if (prompt.isEmpty()) {
            return;
        }

        if (currentChat == null) {
            logger.warn("No chat selected for sending message");
            return;
        }

        // Check if API key is configured for current provider
        try {
            art.moodframe.config.ApplicationConfig config = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.config.ApplicationConfig.class);

            if (config == null) {
                setStatus(I18nManager.getText("status.error.api.key.missing"));
                logger.warn("Send request blocked - no config available");
                return;
            }

            // Check API key for currently selected provider
            String selectedModel = modelComboBox.getValue();
            art.moodframe.model.GenerationModel selectedFluxModel = selectedModel != null
                    ? art.moodframe.model.GenerationModel.fromLocalizedDisplayName(selectedModel) : null;
            boolean isGoogleProvider = (selectedFluxModel != null && selectedFluxModel.getProvider() == ApiProvider.GOOGLE_GEMINI)
                    || (selectedModel != null && selectedModel.toLowerCase().contains("gemini"));
            boolean isOpenAiProvider = selectedFluxModel != null && selectedFluxModel.getProvider() == ApiProvider.OPENAI;

            if (isOpenAiProvider) {
                if (!config.hasOpenAiApiKey()) {
                    setStatus(I18nManager.getText("status.error.api.key.missing"));
                    logger.warn("Send request blocked - no OpenAI API key configured");
                    return;
                }
            } else if (isGoogleProvider && !config.hasGeminiApiKey()) {
                setStatus(I18nManager.getText("status.error.api.key.missing"));
                logger.warn("Send request blocked - no Gemini API key configured");
                return;
            } else if (!isGoogleProvider && !config.hasApiKey()) {
                setStatus(I18nManager.getText("status.error.api.key.missing"));
                logger.warn("Send request blocked - no BFL API key configured");
                return;
            }
        } catch (Exception e) {
            setStatus(I18nManager.getText("status.error.api.key.check"));
            logger.error("Failed to check API key configuration", e);
            return;
        }

        setStatus(I18nManager.getText("status.processing.request"));

        // Calculate current cost for this generation
        String modelDisplayLocalized = modelComboBox.getValue() != null ? modelComboBox.getValue()
                : art.moodframe.model.GenerationModel.getDefault().getLocalizedDisplayName();
        art.moodframe.model.GenerationModel model = art.moodframe.model.GenerationModel.fromLocalizedDisplayName(modelDisplayLocalized);
        String modelDisplay = (model != null) ? model.getDisplayName() : "flux-pro";
        if (art.moodframe.model.GenerationModel.openAiModelId(modelDisplayLocalized) != null) {
            modelDisplay = modelDisplayLocalized; // the chat entry keeps the OpenAI model id
        }
        String aspectRatio = aspectRatioComboBox.getValue() != null ? aspectRatioComboBox.getValue() : "16:9";
        Integer imageCount = (int) imageCountSlider.getValue();

        double baseCost = art.moodframe.model.GenerationCost.estimate(model, selectedVideoSeconds());

        // Total cost = base cost × number of images (applies to both BFL and Gemini)
        double totalCost = baseCost * imageCount;

        // Get safety tolerance value from slider
        int safetyToleranceValue = (int) safetyToleranceSlider.getValue();

        // Get seed value from text field (optional)
        Long seedValue = null;
        String seedText = seedTextField.getText();
        boolean userProvidedSeed = false;

        if (seedText != null && !seedText.trim().isEmpty() && !seedText.trim().equals("random")) {
            try {
                seedValue = Long.parseLong(seedText.trim());
                userProvidedSeed = true;
            } catch (NumberFormatException e) {
                logger.warn("Invalid seed value entered: {}, using random generation", seedText);
                seedValue = null; // API will generate random seed
            }
        }

        // If no seed provided, leave null for true random generation via BFL API
        if (!userProvidedSeed) {
            seedValue = null; // BFL API will handle random generation
        }

        // Determine if prompt upsampling should be used (BFL only)
        boolean shouldEnhancePrompt = (bflPromptUpsamplingCheckBox != null && bflPromptUpsamplingCheckBox.isSelected());

        // Create new ChatEntry
        // Note: userPrompt stores only the raw user input (for copy button)
        // The full prompt with style + YouTube content is used for API calls
        ChatEntry newEntry = new ChatEntry.Builder()
                .chatId(currentChat.getId())
                .userPrompt(userPrompt)
                .enhancedPrompt(shouldEnhancePrompt
                        ? "Enhanced: " + prompt + ", professional quality, highly detailed"
                        : prompt)
                .status(ChatEntry.Status.PENDING)
                .model(modelDisplay)
                .size(aspectRatio)
                .style(stylePresetsComboBox.getValue() != null ? stylePresetsComboBox.getValue().getName() : "None")
                .safetyTolerance(safetyToleranceValue)
                .seed(seedValue != null ? seedValue : 0) // 0 indicates random generation
                .cost(totalCost)
                .build();

        // Save Chat and ChatEntry to database
        if (chatHistory != null) {
            try {
                // Ensure the chat exists in database first
                chatHistory.saveChat(currentChat);

                // Then save the chat entry
                chatHistory.saveChatEntry(newEntry);
                currentChat.getEntries().add(newEntry);
                if (config != null && config.getLoggingEnabled()) {
                    logger.info("Saved Chat and ChatEntry: {}", newEntry.getId());
                }
            } catch (Exception e) {
                logger.error("Failed to save ChatEntry to database", e);
                // Fallback to in-memory only
                currentChat.getEntries().add(newEntry);
            }
        } else {
            // No repository - add to memory only
            currentChat.getEntries().add(newEntry);
        }

        // Also update the chat's last updated timestamp
        if (chatHistory != null) {
            try {
                currentChat.setUpdatedAt(LocalDateTime.now());
                chatHistory.saveChat(currentChat);
            } catch (Exception e) {
                logger.warn("Failed to update chat timestamp", e);
            }
        }

        // Note: Placeholder images will be created in RequestResponseManager

        // Add new entry directly to UI without reloading entire chat
        addNewChatEntryToUI(newEntry);

        // Clear the prompt area
        promptTextArea.clear();

        // Note: YouTube context is NOT cleared after sending - user may want multiple attempts

        // Create ImageGenerationRequest
        art.moodframe.model.ImageGenerationRequest request = createImageGenerationRequest(prompt, newEntry,
                userProvidedSeed);

        // Submit to RequestResponseManager for real API processing
        if (requestResponseManager != null) {
            // Determine which API provider based on selected model
            String selectedModelName = modelComboBox.getValue();
            art.moodframe.model.GenerationModel resolvedModel = selectedModelName != null
                    ? art.moodframe.model.GenerationModel.fromLocalizedDisplayName(selectedModelName) : null;
            String providerName;
            if (resolvedModel != null && resolvedModel.getProvider() == ApiProvider.GOOGLE_GEMINI) {
                providerName = "Gemini";
            } else if (resolvedModel != null && resolvedModel.getProvider() == ApiProvider.OPENAI) {
                providerName = "OpenAI";
            } else {
                providerName = "BFL";
            }
            final String finalProviderName = providerName;

            requestResponseManager.submitImageGeneration(request, newEntry, attachedImageFile)
                    .thenRun(() -> {
                        javafx.application.Platform.runLater(() -> {
                            // Use provider-specific status message
                            String statusKey = "status.request.submitted." + finalProviderName.toLowerCase();
                            String statusMessage = I18nManager.getText(statusKey);
                            // Fallback to generic message if provider-specific key doesn't exist
                            if (statusMessage.equals(statusKey)) {
                                statusMessage = I18nManager.getText("status.request.submitted");
                            }
                            setStatus(statusMessage);
                            if (config != null && config.getLoggingEnabled()) {
                                logger.info("Real API request submitted to {} for prompt: {}", finalProviderName, prompt);
                            }
                        });
                    })
                    .exceptionally(throwable -> {
                        javafx.application.Platform.runLater(() -> {
                            setStatus(I18nManager.getText("status.request.failed"));
                            newEntry.setStatus(ChatEntry.Status.FAILED);
                            newEntry.setErrorMessage(throwable.getMessage());
                            updateChatView(currentChat);
                            logger.error("Failed to submit API request", throwable);
                        });
                        return null;
                    });
        } else {
            // No RequestResponseManager available - this should not happen with proper API
            // key
            setStatus(I18nManager.getText("status.error.service.unavailable"));
            newEntry.setStatus(ChatEntry.Status.FAILED);
            newEntry.setErrorMessage("RequestResponseManager not initialized");
            updateChatView(currentChat);
            logger.error("RequestResponseManager not available despite having API key");
        }
    }

    /**
     * Creates an ImageGenerationRequest from the current UI state.
     */
    private art.moodframe.model.ImageGenerationRequest createImageGenerationRequest(String prompt, ChatEntry chatEntry,
            boolean userProvidedSeed) {
        // Combine user prompt with style preset if selected
        String finalPrompt = combinePromptWithStyle(prompt);

        // IMPORTANT: Convert display name to API model ID
        // chatEntry.getModel() returns display name like "FLUX 3 Image 1K - Standard ($0.048)"
        // but API needs model ID like "flux.3-image"
        art.moodframe.model.GenerationModel generationModel = art.moodframe.model.GenerationModel.fromLocalizedDisplayName(chatEntry.getModel());
        String openAiModelId = art.moodframe.model.GenerationModel.openAiModelId(chatEntry.getModel());
        String apiModelId = openAiModelId != null ? openAiModelId
                : (generationModel != null) ? generationModel.getApiModelId() : chatEntry.getModel();

        if (config != null && config.getLoggingEnabled()) {
            logger.info("Creating request with model display: '{}' -> API ID: '{}'",
                    chatEntry.getModel(), apiModelId);
        }

        art.moodframe.model.ImageGenerationRequest request = new art.moodframe.model.ImageGenerationRequest(finalPrompt,
                apiModelId);

        // Note: Cost calculation is done at the end after all dimensions are set

        // Set parameters from UI and ChatEntry
        request.setImageCount((int) imageCountSlider.getValue());

        // Set prompt upsampling based on BFL checkbox (only for BFL models)
        boolean promptUpsampling = (bflPromptUpsamplingCheckBox != null && bflPromptUpsamplingCheckBox.isSelected());
        request.setPromptUpsampling(promptUpsampling);

        // Only use seed if user provided one, otherwise null for fully random
        // generation
        request.setSeed(userProvidedSeed ? chatEntry.getSeed() : null);

        // Set safety tolerance - FLUX.2 and FLUX 3 use the FLUX.2 slider (0-5), the others the standard value
        int safetyTolerance;
        if (generationModel != null && (generationModel.isFlux2Model() || generationModel.isFlux3Model())) {
            // FLUX.2: Use dedicated FLUX.2 safety slider value
            if (flux2SafetySlider != null) {
                safetyTolerance = (int) flux2SafetySlider.getValue();
            } else {
                // Fallback to config if slider not available
                safetyTolerance = config != null ? config.getFlux2SafetyTolerance() : 5;
            }
            logger.debug("Using FLUX.2 safety tolerance: {} for model: {}", safetyTolerance, generationModel.getApiModelId());
        } else {
            // Other models: Use standard safety slider from ChatEntry
            safetyTolerance = chatEntry.getSafetyTolerance();
        }
        request.setSafetyTolerance(safetyTolerance);

        // Set style preset if selected
        if (stylePresetsComboBox != null && stylePresetsComboBox.getValue() != null &&
                !"None".equals(stylePresetsComboBox.getValue().getName()) && !stylePresetsComboBox.getValue().getName().isEmpty()) {
            request.setStylePreset(stylePresetsComboBox.getValue().getName());
        }

        // Convert aspect ratio to width/height based on model capabilities
        String aspectRatio = aspectRatioComboBox.getValue();
        if (aspectRatio != null) {
            setDimensionsFromAspectRatio(request, aspectRatio, generationModel);
            // Also set the aspect ratio parameter itself for models that require it
            request.setAspectRatio(aspectRatio);
        } else {
            // Default square resolution respecting model limits and multiple of 32
            int maxResolution = (generationModel != null) ? generationModel.getMaxResolution() : 1024;
            int defaultSize = Math.min(maxResolution,
                    (generationModel != null && generationModel.getMaxMegapixels() == 4) ? 2048 : 1024);
            defaultSize = roundToMultipleOf32(defaultSize);
            request.setWidth(defaultSize);
            request.setHeight(defaultSize);
            request.setAspectRatio("1:1"); // Default square aspect ratio
        }

        if (generationModel == art.moodframe.model.GenerationModel.OPENAI_IMAGE && resolutionContainer != null
                && resolutionContainer.isVisible()) {
            request.setImageSize(resolutionChoiceBox.getValue());
        }

        // Set imageSize parameter for Gemini models based on model enum constant
        if (apiModelId != null && apiModelId.toLowerCase().contains("gemini")) {
            String imageSize = determineGeminiImageSize(generationModel);
            request.setImageSize(imageSize);

            if (config != null && config.getLoggingEnabled()) {
                logger.info("Setting Gemini imageSize parameter: {} for model: {}", imageSize, apiModelId);
            }

            // Gemini Phase 2 Advanced Features
            if (geminiAdvancedFeaturesSection != null) {
                // Google Search Grounding
                boolean searchEnabled = geminiAdvancedFeaturesSection.isSearchGroundingEnabled();
                request.setEnableGoogleSearch(searchEnabled);
            }

            // Thinking level: only Nano Banana 2 and Lite can be controlled (minimal or high), Nano Banana Pro
            // always thinks. The control is disabled while an image is attached, then the API default is used.
            boolean sendThinking = generationModel != null && generationModel.supportsThinkingLevel()
                    && geminiThinkingLevelChoiceBox != null && !geminiThinkingLevelChoiceBox.isDisabled()
                    && geminiThinkingLevelChoiceBox.getValue() != null;
            request.setThinkingMode(sendThinking ? geminiThinkingLevelChoiceBox.getValue().toLowerCase() : null);

            if (config != null && config.getLoggingEnabled()) {
                boolean searchEnabled = (geminiAdvancedFeaturesSection != null)
                        ? geminiAdvancedFeaturesSection.isSearchGroundingEnabled()
                        : false;
                logger.info("Gemini Phase 2 features - Search: {}, Thinking: always enabled (not controllable)",
                        searchEnabled);
            }

            // Gemini Multi-Image Support (up to 14 images)
            if (generationModel != null && generationModel.supportsMultiImage()) {
                java.util.List<String> base64Images = attachedBase64Images();
                if (!base64Images.isEmpty()) {
                    request.setInputImages(base64Images);
                    if (config != null && config.getLoggingEnabled()) {
                        logger.info("Gemini Multi-Image: attached {} images to request", base64Images.size());
                    }
                }
            }
        }

        // OpenAI Multi-Image Support (images are sent to /images/edits)
        if (generationModel == art.moodframe.model.GenerationModel.OPENAI_IMAGE) {
            java.util.List<String> base64Images = attachedBase64Images();
            if (!base64Images.isEmpty()) {
                request.setInputImages(base64Images);
            }
        }

        // Direct video-to-image source (only gemini-3.1-flash-image supports it)
        if (currentVideoSource != null && generationModel != null && generationModel.supportsVideoSource()) {
            request.setVideoSourceUrl(currentVideoSource.url());
            request.setVideoStartOffsetSeconds(currentVideoSource.startSeconds());
            request.setVideoEndOffsetSeconds(currentVideoSource.endSeconds());
            logger.info("Attached direct video source to request: {}", currentVideoSource.url());
        }

        // FLUX 3: resolution tier of the variant and up to 10 reference images
        if (generationModel != null && generationModel.isFlux3Model()) {
            request.setImageSize(generationModel.getFlux3Resolution());
            java.util.List<String> base64Images = attachedBase64Images();
            if (!base64Images.isEmpty()) {
                request.setInputImages(base64Images);
            }
        }

        // FLUX.2 Specific Parameters
        if (generationModel != null && generationModel.isFlux2Model()) {
            // Note: FLUX.2 [max] has built-in grounding search that activates automatically
            // based on prompt content (e.g., "current", "today", "latest")
            // No parameter needed - it's a model capability, not a toggle

            // FLUX.2 [flex] - Guidance and Steps parameters
            if (generationModel.isFlux2Flex()) {
                if (flux2GuidanceSlider != null) {
                    double guidance = flux2GuidanceSlider.getValue();
                    request.setGuidance(guidance);
                }
                if (flux2StepsSlider != null) {
                    int steps = (int) flux2StepsSlider.getValue();
                    request.setSteps(steps);
                }

                if (config != null && config.getLoggingEnabled()) {
                    logger.info("FLUX.2 [flex] parameters - Guidance: {}, Steps: {}",
                            request.getGuidance(), request.getSteps());
                }
            }

            // FLUX.2 Multi-Image Support (up to 8 images)
            if (generationModel.supportsMultiImage()) {
                java.util.List<String> base64Images = attachedBase64Images();
                if (!base64Images.isEmpty()) {
                    request.setInputImages(base64Images);
                    if (config != null && config.getLoggingEnabled()) {
                        logger.info("FLUX.2 Multi-Image: set {} images to request", base64Images.size());
                    }
                }
            }
        }

        // Video Parameters (Gemini Omni)
        if (generationModel != null && generationModel.isVideoModel()) {
            // Video resolution from model
            request.setVideoResolution(generationModel.getVideoResolution());

            // Duration from the choice, 8 seconds without one
            Integer seconds = selectedVideoSeconds();
            request.setVideoDuration(seconds != null ? seconds : 8);

            // Omni always generates audio (the flag only marks the video as having audio)
            request.setGenerateAudio(true);

            // Force image count to 1 for video
            request.setImageCount(1);

            if (config != null && config.getLoggingEnabled()) {
                logger.info("Video params - resolution: {}, duration: {}s, audio: {}, negPrompt: {}",
                        request.getVideoResolution(), request.getVideoDuration(),
                        request.getGenerateAudio(), request.getNegativePrompt() != null);
            }
        }

        // Final cost calculation - done at the end when all dimensions are known
        if (generationModel != null) {
            double costPerImage = art.moodframe.model.GenerationCost.perImage(generationModel, request.getWidth(),
                    request.getHeight(), request.getVideoDuration());
            request.setBaseCostPerImage(costPerImage);
            if (config != null && config.getLoggingEnabled()) {
                logger.info("Cost calculation: ${} per image for model {} ({}x{})", String.format("%.4f", costPerImage),
                        generationModel.getApiModelId(), request.getWidth(), request.getHeight());
            }
        }

        return request;
    }

    /**
     * Determines the imageSize parameter for Gemini API based on the model enum constant.
     * Maps UI model selection to Gemini API imageSize parameter (512, 1K, 2K, 4K).
     *
     * @param model the GenerationModel enum constant (may be null)
     * @return the imageSize parameter for Gemini API ("512", "1K", "2K", or "4K")
     */
    private String determineGeminiImageSize(art.moodframe.model.GenerationModel model) {
        if (model != null) {
            switch (model) {
                case GEMINI_FLASH_1K:
                    return "1K";
                case GEMINI_PRO_2K:
                    return "2K";
                case GEMINI_PRO_4K:
                    return "4K";
                // Nano Banana 2 models
                case NANO_BANANA2_512:
                    return "512";
                case NANO_BANANA2_1K:
                    return "1K";
                case NANO_BANANA2_2K:
                    return "2K";
                case NANO_BANANA2_4K:
                    return "4K";
                // Nano Banana 2 Lite is capped at 1K resolution
                case NANO_BANANA2_LITE:
                    return "1K";
                default:
                    return "1K"; // Default fallback
            }
        }

        return "1K"; // Default
    }

    /**
     * Combines the user prompt with the selected style preset prompt.
     *
     * @param userPrompt the user's original prompt
     * @return the combined prompt with style applied
     */
    private String combinePromptWithStyle(String userPrompt) {
        if (stylePresetsComboBox == null || stylePresetsComboBox.getValue() == null) {
            return userPrompt;
        }

        String selectedStyleName = stylePresetsComboBox.getValue().getName();
        if ("None".equals(selectedStyleName) || selectedStyleName.isEmpty()) {
            return userPrompt;
        }

        try {
            // Get the style preset service to find the prompt text
            art.moodframe.service.StylePresetService styleService = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.service.StylePresetService.class);

            if (styleService != null) {
                java.util.List<art.moodframe.model.StylePreset> presets = styleService.getAllStyles();
                for (art.moodframe.model.StylePreset preset : presets) {
                    if (selectedStyleName.equals(preset.getName())) {
                        String stylePrompt = preset.getPromptText();
                        if (stylePrompt != null && !stylePrompt.trim().isEmpty()) {
                            // Combine user prompt + comma with space + style prompt
                            return userPrompt + ", " + stylePrompt.trim();
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get style preset for combination: {}", selectedStyleName, e);
        }

        return userPrompt;
    }

    /**
     * Sets width and height based on aspect ratio selection and model capabilities.
     * Respects per-model resolution limits and ensures dimensions are multiples of
     * 16 (FLUX.2) or 32 (FLUX.1).
     */
    private void setDimensionsFromAspectRatio(art.moodframe.model.ImageGenerationRequest request, String aspectRatio,
            art.moodframe.model.GenerationModel model) {
        // Get model-specific resolution limits
        int maxResolution = (model != null) ? model.getMaxResolution() : 1024;
        int dimensionMultiple = art.moodframe.model.ImageDimensions.multipleFor(model);

        if (config != null && config.getLoggingEnabled()) {
            logger.info("Setting dimensions for aspect ratio: {} with maxResolution: {} (multiple of {}) for model: {}",
                    aspectRatio, maxResolution, dimensionMultiple, model != null ? model.getApiModelId() : "null");
        }

        int[] dimensions = art.moodframe.model.ImageDimensions.forAspectRatio(aspectRatio, maxResolution,
                dimensionMultiple);

        if (config != null && config.getLoggingEnabled()) {
            logger.info("Final dimensions: {}x{} for aspect ratio: {}",
                    dimensions[0], dimensions[1], aspectRatio);
        }

        request.setWidth(dimensions[0]);
        request.setHeight(dimensions[1]);
    }

    /**
     * Rounds a value to the nearest multiple of 32 (FLUX.1 API requirement).
     */
    private int roundToMultipleOf32(int value) {
        return ((value + 16) / 32) * 32; // Round to nearest multiple of 32
    }

    /**
     * Handles the attach image button click event.
     */
    @FXML
    private void onAttachImageClicked() {
        setStatus(I18nManager.getText("status.selecting.image"));

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
                    setStatus(String.format(I18nManager.getText("status.error.file.too.large"), maxMB));
                    return;
                }

                // Load and display the image
                Image image = new Image(selectedFile.toURI().toString());
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

                setStatus(I18nManager.getText("status.image.attached"));

            } catch (Exception e) {
                setStatus(I18nManager.getText("status.error.image.load"));
                logger.error("Failed to load selected image: {}", selectedFile.getPath(), e);
            }
        } else {
            setStatus(I18nManager.getText("status.error.no.image"));
        }
    }

    /**
     * Updates UI state when image attachment changes.
     */
    private void updateAttachmentState() {
        boolean hasAttachment = attachedImageFile != null;

        // Update remove button state (always visible, disabled when no attachment)
        removeImageButton.setDisable(!hasAttachment);

        // Handle model filtering based on attachment state
        updateModelSelectionForAttachment(hasAttachment);

        if (hasAttachment) {
            // Set style to NONE for image-to-image (image provides the style)
            if (stylePresetsComboBox != null && stylePresetsComboBox.getValue() != null
                    && !"None".equals(stylePresetsComboBox.getValue().getName())) {
                // Find and set the "None" item
                stylePresetsComboBox.getItems().stream()
                        .filter(item -> "None".equals(item.getName()))
                        .findFirst()
                        .ifPresent(stylePresetsComboBox::setValue);
            }

            // Auto-detect aspect ratio ONLY from the first attached image
            // When adding additional images, keep the aspect ratio from the first one
            int attachmentCount = multiImagePanel != null ? multiImagePanel.getAttachmentCount() : 1;
            if (attachmentCount == 1) {
                updateAspectRatioFromImage(attachedImageFile);
            }
        }

        // Update prompt placeholder and all UI elements to reflect attachment state
        // (includes disabling thinking level when image attached)
        updateUIState();
    }

    /**
     * Auto-detects the aspect ratio from an uploaded image and sets the closest
     * available option from the currently available ratios in the combo box.
     */
    private void updateAspectRatioFromImage(File imageFile) {
        if (imageFile == null || aspectRatioComboBox == null) {
            return;
        }

        try {
            // Load image to get dimensions
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(imageFile);
            if (image == null) {
                logger.warn("Could not read image dimensions from: {}", imageFile.getName());
                return;
            }

            int width = image.getWidth();
            int height = image.getHeight();
            double imageRatio = (double) width / height;

            // Use the currently available aspect ratios from the combo box
            // (already filtered by provider via updateAspectRatioOptions)
            var availableItems = aspectRatioComboBox.getItems();
            if (availableItems.isEmpty()) {
                return;
            }

            // Find closest matching aspect ratio
            String closestRatio = "1:1"; // Default fallback
            double smallestDifference = Double.MAX_VALUE;

            for (String ratioStr : availableItems) {
                double ratioValue = parseRatioToDouble(ratioStr);
                double difference = Math.abs(imageRatio - ratioValue);
                if (difference < smallestDifference) {
                    smallestDifference = difference;
                    closestRatio = ratioStr;
                }
            }

            // Set the closest aspect ratio
            String currentRatio = aspectRatioComboBox.getValue();
            if (!closestRatio.equals(currentRatio)) {
                aspectRatioComboBox.setValue(closestRatio);
                setStatus(java.text.MessageFormat.format(
                        I18nManager.getText("status.aspect.ratio.detected"), closestRatio));
            }

        } catch (Exception e) {
            logger.error("Failed to detect aspect ratio from image: {}", imageFile.getName(), e);
        }
    }

    /**
     * Parses an aspect ratio string (e.g., "16:9") to its numeric value.
     *
     * @param ratioStr the ratio string in format "W:H"
     * @return the numeric ratio value (width / height)
     */
    private double parseRatioToDouble(String ratioStr) {
        if (ratioStr == null || !ratioStr.contains(":")) {
            return 1.0;
        }
        try {
            String[] parts = ratioStr.split(":");
            double w = Double.parseDouble(parts[0]);
            double h = Double.parseDouble(parts[1]);
            return w / h;
        } catch (Exception e) {
            return 1.0;
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
            // Filter to only show models that take images
            modelComboBox.getItems().clear();

            for (art.moodframe.model.GenerationModel model : art.moodframe.model.GenerationModel.selectable()) {
                if (model.supportsImageAttachment()) {
                    modelComboBox.getItems().add(model.getLocalizedDisplayName());
                }
            }
            modelComboBox.getItems().addAll(openAiModelItems());

            // Check if current selection is compatible
            art.moodframe.model.GenerationModel currentModel = art.moodframe.model.GenerationModel
                    .fromLocalizedDisplayName(currentSelection);
            if (currentModel == null || !currentModel.supportsImageAttachment()) {
                // Auto-switch to the default model, which edits images (FLUX 3 Image)
                String defaultName = art.moodframe.model.GenerationModel.getDefault().getLocalizedDisplayName();

                // Make sure the item exists in the ComboBox before setting it
                if (modelComboBox.getItems().contains(defaultName)) {
                    modelComboBox.setValue(defaultName);
                    setStatus(I18nManager.getText("status.model.switched", defaultName));
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

        setStatus(I18nManager.getText("status.image.removed"));
    }

    /**
     * Handles the clear seed button click event.
     */
    @FXML
    private void onClearSeedClicked() {

        // Clear the seed text field
        seedTextField.clear();

        setStatus(I18nManager.getText("status.seed.cleared"));
    }

    /**
     * Handles the trash visibility toggle button click event.
     */
    @FXML
    private void onTrashVisibilityToggled() {
        boolean showTrashed = trashVisibilityToggle.isSelected();

        // Save the current selection before updating the filter
        Chat selectedChat = chatListView.getSelectionModel().getSelectedItem();

        // Suppress chat selection events during filter update
        suppressChatSelectionEvents = true;

        try {
            // Update filter predicate to refresh the view
            filteredChatList.setPredicate(this::shouldShowChat);

            // Restore the selection if the chat is still visible
            if (selectedChat != null && shouldShowChat(selectedChat)) {
                // Select the chat without triggering onChatSelected
                Platform.runLater(() -> {
                    chatListView.getSelectionModel().select(selectedChat);
                });
            }
        } finally {
            // Re-enable chat selection events after a short delay
            Platform.runLater(() -> {
                suppressChatSelectionEvents = false;
            });
        }

        // Update status message
        if (showTrashed) {
            long trashedCount = chatList.stream().filter(Chat::isDeleted).count();
            String itemsText = trashedCount == 1 ? I18nManager.getText("status.trash.items.singular")
                    : I18nManager.getText("status.trash.items.plural");
            setStatus(java.text.MessageFormat.format(
                    I18nManager.getText("status.trash.showing"), trashedCount, itemsText));
        } else {
            setStatus(I18nManager.getText("status.trash.hiding"));
        }

        // Update button icon based on state (eye open = showing trash, eye closed =
        // hiding trash)
        FontIcon icon = (FontIcon) trashVisibilityToggle.getGraphic();
        if (showTrashed) {
            icon.setIconLiteral("mdi2e-eye"); // Open eye when showing trash
            trashVisibilityToggle.setText(I18nManager.getText("chat.trash.toggle"));
            trashVisibilityToggle.getTooltip().setText(I18nManager.getText("chat.trash.tooltip"));
        } else {
            icon.setIconLiteral("mdi2e-eye-off"); // Closed eye when hiding trash
            trashVisibilityToggle.setText(I18nManager.getText("chat.trash.toggle"));
            trashVisibilityToggle.getTooltip().setText(I18nManager.getText("chat.trash.tooltip"));
        }
    }

    /**
     * Sets up the cost display by adding it programmatically to the UI.
     */
    private void setupCostDisplay() {
        try {
            // Find the parent container of the stylePresetsComboBox
            javafx.scene.Parent parent = stylePresetsComboBox.getParent();
            if (parent instanceof javafx.scene.layout.HBox hbox) {
                // Create cost display elements
                javafx.scene.control.Label costLabel = new javafx.scene.control.Label("Cost:");
                costLabel.getStyleClass().add("text-small");

                costDisplayLabel = new javafx.scene.control.Label("$0.04");
                costDisplayLabel.getStyleClass().addAll("text-small", "text-bold");

                // Add to the HBox
                hbox.getChildren().addAll(costLabel, costDisplayLabel);

                logger.warn("Could not find suitable parent container for cost display");
            }
        } catch (Exception e) {
            logger.warn("Failed to setup cost display", e);
        }
    }

    /**
     * Updates the cost display based on current parameter selections.
     * This shows mock pricing based on BFL API pricing structure.
     */
    private void updateCostDisplay() {
        // Note: costDisplayLabel is optional, we still update button even if label is
        // null

        try {
            // Get current selections
            String modelDisplayLocalized = modelComboBox.getValue();
            art.moodframe.model.GenerationModel model = art.moodframe.model.GenerationModel
                    .fromLocalizedDisplayName(modelDisplayLocalized);
            Integer imageCount = (int) imageCountSlider.getValue();

            // Video: the price is per second, the length comes from the duration choice
            double baseCost = art.moodframe.model.GenerationCost.estimate(model, selectedVideoSeconds());

            // Total cost = base cost × number of images (applies to both BFL and Gemini)
            double totalCost = baseCost * (imageCount != null ? imageCount : 1);

            // Format cost display (Omni: billed by the actual length of the video, so the price is an estimate)
            String costText = model == art.moodframe.model.GenerationModel.OPENAI_IMAGE
                    ? I18nManager.getText("cost.unknown")
                    : String.format(model != null && model.isOmniModel() ? "~$%.2f" : "$%.2f", totalCost);
            if (costDisplayLabel != null) {
                costDisplayLabel.setText(costText);
            }

            // Update send button text with cost info - show only price to save space
            if (sendButton != null) {
                // Just show the price, icon already indicates "send/generate"
                sendButton.setText(costText);
            }

        } catch (Exception e) {
            logger.warn("Error updating cost display", e);
            if (costDisplayLabel != null) {
                costDisplayLabel.setText("$0.00");
            }
            if (sendButton != null) {
                sendButton.setText("$0.00");
            }
        }
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
     * Sets up the image preview overlay component.
     */
    private void setupImagePreviewOverlay() {
        try {
            // Create and configure the image preview overlay
            imagePreviewOverlay = new art.moodframe.ui.component.ImagePreviewOverlay();

            // Add overlay to the main stack pane (covers entire window except header)
            StackPane mainStack = (StackPane) mainBorderPane.getParent();
            mainStack.getChildren().add(imagePreviewOverlay);

            // Set up close callback
            imagePreviewOverlay.setOnCloseRequested(aVoid -> {
                imagePreviewOverlay.setVisible(false);
            });

            // Set up chat navigation callbacks
            imagePreviewOverlay.setOnPrevChatRequested(() -> navigateToPreviousChatWithImages());
            imagePreviewOverlay.setOnNextChatRequested(() -> navigateToNextChatWithImages());

            // Set up edit this callback for overlay
            imagePreviewOverlay.setOnEditThisRequested(filePath -> {
                editImageFromPath(filePath);
            });

            // Forward overlay status messages (e.g. download feedback) to the footer
            imagePreviewOverlay.setOnStatusMessage(message -> setStatus(message));

        } catch (Exception e) {
            logger.error("Failed to setup image preview overlay", e);
        }
    }

    /**
     * Shows the image preview overlay with images from a chat entry.
     * 
     * @param chatEntry  the chat entry containing images to display
     * @param startIndex the index of the image to start with
     */
    public void showImagePreview(art.moodframe.model.ChatEntry chatEntry, int startIndex) {
        if (imagePreviewOverlay == null) {
            return;
        }

        if (chatEntry == null || chatEntry.getImages().isEmpty()) {
            return;
        }

        // Extract image paths from chat entry, checking both active and trash locations
        java.util.List<String> imagePaths = chatEntry.getImages().stream()
                .map(image -> getActualImagePath(image, currentChat != null && currentChat.isDeleted()))
                .filter(path -> path != null && !path.isEmpty())
                .filter(path -> new java.io.File(path).exists()) // Only include existing files
                .collect(java.util.stream.Collectors.toList());

        if (imagePaths.isEmpty()) {
            return;
        }

        // Ensure startIndex is within bounds of valid images
        int safeStartIndex = Math.max(0, Math.min(startIndex, imagePaths.size() - 1));

        // Show overlay with images
        imagePreviewOverlay.showImages(imagePaths, safeStartIndex);

        // Track the current chat entry for navigation
        currentlyDisplayedChatEntry = chatEntry;

        // Update chat navigation button visibility
        updateChatNavigationButtons();

    }

    /**
     * Shows the image preview overlay with a single image.
     * 
     * @param imagePath path to the image file
     */
    public void showImagePreview(String imagePath) {
        if (imagePreviewOverlay == null) {
            return;
        }

        if (imagePath == null || imagePath.isEmpty()) {
            return;
        }

        imagePreviewOverlay.showImage(imagePath);
    }

    /**
     * Navigates to the previous chat entry that has images.
     * Opens the first image of that chat entry in the preview.
     */
    private void navigateToPreviousChatWithImages() {
        if (currentChat == null) {
            return;
        }

        // Find the currently displayed chat entry
        ChatEntry currentEntry = findCurrentlyDisplayedChatEntry();
        if (currentEntry == null) {
            return;
        }

        // Work directly with chat entries from the current chat
        // The issue is not with deleted chat entries, but with the index calculation
        java.util.List<ChatEntry> chatEntries = currentChat.getEntries();

        int currentIndex = -1;

        // Find current entry index in the chat entries list
        for (int i = 0; i < chatEntries.size(); i++) {
            if (chatEntries.get(i).getId().equals(currentEntry.getId())) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            return;
        }

        // Search for previous chat entry with images (going backwards)
        for (int i = currentIndex - 1; i >= 0; i--) {
            ChatEntry entry = chatEntries.get(i);
            boolean hasImages = hasValidImages(entry);

            // Check if this entry has valid images (not failed/cancelled)
            if (hasImages) {
                // Find the first valid image index instead of always using 0
                int firstValidImageIndex = findFirstValidImageIndex(entry);
                showImagePreview(entry, firstValidImageIndex);
                return;
            }
        }
    }

    /**
     * Navigates to the next chat entry that has images.
     * Opens the first image of that chat entry in the preview.
     */
    private void navigateToNextChatWithImages() {
        if (currentChat == null) {
            return;
        }

        // Find the currently displayed chat entry
        ChatEntry currentEntry = findCurrentlyDisplayedChatEntry();
        if (currentEntry == null) {
            return;
        }

        // Work directly with chat entries from the current chat
        java.util.List<ChatEntry> chatEntries = currentChat.getEntries();
        int currentIndex = -1;

        // Find current entry index in the chat entries list
        for (int i = 0; i < chatEntries.size(); i++) {
            if (chatEntries.get(i).getId().equals(currentEntry.getId())) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            return;
        }

        // Search for next chat entry with images (going forwards)
        for (int i = currentIndex + 1; i < chatEntries.size(); i++) {
            ChatEntry entry = chatEntries.get(i);
            boolean hasImages = hasValidImages(entry);

            // Check if this entry has valid images (not failed/cancelled)
            if (hasImages) {
                // Find the first valid image index instead of always using 0
                int firstValidImageIndex = findFirstValidImageIndex(entry);
                showImagePreview(entry, firstValidImageIndex);
                return;
            }
        }
    }

    /**
     * Checks if a chat entry has valid images (not failed/cancelled and files
     * exist).
     * 
     * @param entry the chat entry to check
     * @return true if the entry has at least one valid image
     */
    private boolean hasValidImages(ChatEntry entry) {
        if (entry == null || entry.getImages().isEmpty()) {
            return false;
        }

        // Don't rely solely on chat entry status - check individual images
        // A chat entry might be COMPLETED but have some failed images

        // Check if at least one image is valid (downloaded successfully and file
        // exists)
        for (GeneratedImage image : entry.getImages()) {
            // Check image download status
            if (image.getDownloadStatus() != GeneratedImage.DownloadStatus.CACHED) {
                continue; // Skip non-cached images
            }

            // Check if file path exists and file is actually there (including trash)
            String actualPath = getActualImagePath(image, currentChat != null && currentChat.isDeleted());
            if (actualPath != null && !actualPath.isEmpty()) {
                java.io.File file = new java.io.File(actualPath);
                if (file.exists()) {
                    return true; // Found at least one valid image
                }
            }
        }

        return false;
    }

    /**
     * Finds the first valid image index in a chat entry.
     * Skips failed/missing images and returns the index of the first existing
     * image.
     * 
     * @param entry the chat entry to search
     * @return the index of the first valid image, or 0 if none found
     */
    private int findFirstValidImageIndex(ChatEntry entry) {
        if (entry == null || entry.getImages().isEmpty()) {
            return 0;
        }

        java.util.List<GeneratedImage> images = entry.getImages();
        for (int i = 0; i < images.size(); i++) {
            GeneratedImage image = images.get(i);

            // Check if image is valid (cached status and file exists)
            if (image.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED) {
                String actualPath = getActualImagePath(image, currentChat != null && currentChat.isDeleted());
                if (actualPath != null && !actualPath.isEmpty()) {
                    java.io.File file = new java.io.File(actualPath);
                    if (file.exists()) {
                        return i;
                    }
                }
            }
        }

        // Fallback to 0 if no valid images found (should not happen if hasValidImages()
        // was true)
        return 0;
    }

    /**
     * Finds the currently displayed chat entry in the preview overlay.
     * 
     * @return the currently displayed chat entry, or null if none
     */
    private ChatEntry findCurrentlyDisplayedChatEntry() {
        return currentlyDisplayedChatEntry;
    }

    // Track the currently displayed chat entry for navigation
    private ChatEntry currentlyDisplayedChatEntry;

    /**
     * Updates the visibility of chat navigation buttons based on available chat
     * entries.
     */
    private void updateChatNavigationButtons() {
        if (imagePreviewOverlay == null || currentlyDisplayedChatEntry == null) {
            return;
        }

        boolean hasPrevious = hasPreviousChatWithImages();
        boolean hasNext = hasNextChatWithImages();

        imagePreviewOverlay.updateChatNavigationButtons(hasPrevious, hasNext);
    }

    /**
     * Checks if there's a previous chat entry with valid images.
     *
     * @return true if there's a previous chat entry with images
     */
    private boolean hasPreviousChatWithImages() {
        if (currentChat == null || chatEntriesListView == null || chatEntriesListView.getItems() == null
                || currentlyDisplayedChatEntry == null) {
            return false;
        }

        java.util.List<ChatEntry> entries = chatEntriesListView.getItems();
        int currentIndex = entries.indexOf(currentlyDisplayedChatEntry);

        // Check for previous entries with images
        for (int i = currentIndex - 1; i >= 0; i--) {
            if (hasValidImages(entries.get(i))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if there's a next chat entry with valid images.
     *
     * @return true if there's a next chat entry with images
     */
    private boolean hasNextChatWithImages() {
        if (currentChat == null || chatEntriesListView == null || chatEntriesListView.getItems() == null
                || currentlyDisplayedChatEntry == null) {
            return false;
        }

        java.util.List<ChatEntry> entries = chatEntriesListView.getItems();
        int currentIndex = entries.indexOf(currentlyDisplayedChatEntry);

        // Check for next entries with images
        for (int i = currentIndex + 1; i < entries.size(); i++) {
            if (hasValidImages(entries.get(i))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Downloads an image from the given file path to the Downloads folder.
     */
    private void downloadImageFromPath(String filePath) {
        if (filePath == null || filePath.isEmpty() || !new File(filePath).exists()) {
            logger.warn("Image download requested but the file does not exist: {}", filePath);
            showErrorMessage(I18nManager.getText("download.image.notfound"));
            return;
        }

        try {
            String filename = art.moodframe.util.DownloadsFolder.copy(new File(filePath));
            Platform.runLater(() -> setStatus(
                    java.text.MessageFormat.format(I18nManager.getText("download.image.success"), filename)));
        } catch (Exception e) {
            logger.error("Failed to download image to Downloads folder", e);
            showErrorMessage(java.text.MessageFormat.format(
                    I18nManager.getText("download.image.error"), e.getMessage()));
        }
    }

    /**
     * Initiates edit mode for the image at the given file path.
     * Automatically attaches the image and adjusts parameters for image-to-image
     * workflow.
     */
    private void editImageFromPath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }

        File imageFile = new File(filePath);
        if (!imageFile.exists()) {
            return;
        }

        try {
            // Close image preview overlay if open
            if (imagePreviewOverlay != null && imagePreviewOverlay.isVisible()) {
                imagePreviewOverlay.setVisible(false);
            }

            // Auto-attach the image (bypassing file dialog)
            attachImageDirectly(imageFile);

            // Clear prompt text to prepare for editing instructions
            promptTextArea.clear();

            // Focus the prompt input for user to enter editing instructions
            promptTextArea.requestFocus();

            setStatus(I18nManager.getText("status.image.edit.attached"));

        } catch (Exception e) {
            logger.error("Failed to initiate edit mode for image: {}", filePath, e);
            setStatus(java.text.MessageFormat.format(
                    I18nManager.getText("status.error.image.edit"), e.getMessage()));
        }
    }

    /**
     * Attaches an image file directly without showing file dialog.
     * Used for Edit functionality where the image is already known.
     *
     * Behavior:
     * 1) If only one image attached with no empty slot: replace it
     * 2) If there's an empty slot: fill it with the new image
     * 3) If 2+ images and no empty slot: create new slot and attach (if under max limit)
     */
    private void attachImageDirectly(File imageFile) throws Exception {
        // Use the MultiImageAttachmentPanel for image attachment (it shrinks images above the size limit)
        if (multiImagePanel != null) {
            int targetSlot = multiImagePanel.findTargetSlotForNewImage();
            if (targetSlot >= 0) {
                multiImagePanel.attachImageToSlot(targetSlot, imageFile);
            } else {
                // Max images reached - show warning
                logger.warn("Cannot attach image: maximum number of images reached");
                setStatus(I18nManager.getText("attachment.max.reached",
                    multiImagePanel.getAttachmentCount()));
                return;
            }
        }

        // Store the file reference for API use (legacy support)
        attachedImageFile = imageFile;

        // Auto-set image count to 1 for image-to-image generation
        imageCountSlider.setValue(1);
        updateImageCountLabel(1);

        // Update UI to reflect attachment (auto-adjusts model, aspect ratio, style)
        updateAttachmentState();
    }

    /**
     * Optimizes ListView for better scrolling performance with GPU acceleration.
     * NOTE: ListView with virtualization is already optimized - this is minimal setup.
     */
    private void optimizeScrollPane() {
        if (chatEntriesListView == null) {
            return;
        }

        // Enable GPU caching for the ListView
        chatEntriesListView.setCache(true);
        chatEntriesListView.setCacheHint(javafx.scene.CacheHint.SPEED);

        // Add performance CSS class
        chatEntriesListView.getStyleClass().add("chat-entries-list");
    }

    @Override
    public void onLanguageChanged() {
        try {

            // Header and sidebar buttons are now handled by updateTooltipTexts()

            if (trashVisibilityToggle != null) {
                updateTrashButtonText();
            }

            // Update welcome label
            if (welcomeLabel != null) {
                welcomeLabel.setText(I18nManager.getText("app.welcome"));
            }

            // Update input area
            updateInputAreaTexts();

            // Update status bar
            updateStatusBarTexts();

            // Update parameter controls
            updateParameterTexts();

            // Update image attachment area
            updateImageAttachmentTexts();

            // Update FXML hardcoded texts
            updateFXMLTexts();

            // Update all tooltips
            updateTooltipTexts();

            // Update chat header
            updateChatHeaderTexts();

            // Update model names in ComboBox
            updateModelNames();

            // Update safety tolerance label with current value
            updateSafetyToleranceLabel((int) safetyToleranceSlider.getValue());

            // Update chat metadata if a chat is selected
            if (currentChat != null) {
                // Update both the info line in chat content AND the header cost label
                updateChatMetadata(currentChat); // Info line with "Created: ... • ... messages • Total: ..."
                updateChatTotalCost(currentChat); // Header cost label
            }

            // Update dynamic labels if they exist
            updateDynamicLabels();

        } catch (Exception e) {
            logger.error("Failed to update MainWindow UI texts", e);
        }
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

        // Check if we're in attachment mode to preserve filtering
        boolean hasAttachment = attachedImageFile != null;

        // Clear and rebuild with localized names
        String previousItem = modelComboBox.getValue();
        modelComboBox.getItems().clear();

        // Get ApplicationConfig to check provider status
        art.moodframe.config.ApplicationConfig config = art.moodframe.config.ServiceFactory.getInstance()
                .getService(art.moodframe.config.ApplicationConfig.class);

        if (hasAttachment) {
            // Only add models that take images from enabled providers
            for (art.moodframe.model.GenerationModel model : art.moodframe.model.GenerationModel.selectable()) {
                if (model.supportsImageAttachment()) {
                    // Check if model's provider is enabled
                    art.moodframe.model.ApiProvider provider = model.getProvider();
                    boolean isEnabled = false;

                    if (provider == art.moodframe.model.ApiProvider.BFL && config != null) {
                        isEnabled = config.isBflProviderEnabled() && config.hasApiKey();
                    } else if (provider == art.moodframe.model.ApiProvider.GOOGLE_GEMINI && config != null) {
                        isEnabled = config.isGeminiProviderEnabled() && config.hasGeminiApiKey();
                    }

                    if (isEnabled) {
                        modelComboBox.getItems().add(model.getLocalizedDisplayName());
                    }
                }
            }
        } else {
            // Add models from enabled providers only
            if (config != null) {
                boolean bflEnabled = config.isBflProviderEnabled() && config.hasApiKey();
                boolean geminiEnabled = config.isGeminiProviderEnabled() && config.hasGeminiApiKey();

                for (art.moodframe.model.GenerationModel model : art.moodframe.model.GenerationModel.selectable()) {
                    art.moodframe.model.ApiProvider provider = model.getProvider();

                    if (provider == art.moodframe.model.ApiProvider.BFL && bflEnabled) {
                        modelComboBox.getItems().add(model.getLocalizedDisplayName());
                    } else if (provider == art.moodframe.model.ApiProvider.GOOGLE_GEMINI && geminiEnabled) {
                        modelComboBox.getItems().add(model.getLocalizedDisplayName());
                    }
                }
            }
        }

        modelComboBox.getItems().addAll(openAiModelItems());

        // Restore selection using localized name (an OpenAI model keeps its item name)
        if (currentModel != null) {
            String localizedName = art.moodframe.model.GenerationModel.openAiModelId(previousItem) != null
                    ? previousItem : currentModel.getLocalizedDisplayName();

            if (modelComboBox.getItems().contains(localizedName)) {
                // Temporarily suppress selection events to avoid recursion
                modelComboBox.setValue(localizedName);
            } else if (!modelComboBox.getItems().isEmpty()) {
                // Only try to set default if list is not empty
                logger.warn("Localized model name '{}' not found in items: {}", localizedName,
                        modelComboBox.getItems());
                // Fallback to default if current model not available
                String defaultLocalized = art.moodframe.model.GenerationModel.getDefault().getLocalizedDisplayName();
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
     * Updates chat metadata info line (Created, messages count, total cost) in the
     * chat content area.
     */
    private void updateChatMetadata(Chat chat) {
        if (chat == null)
            return;

        // Use the tracked metadata label if available
        if (chatMetadataInfoLabel != null) {
            // Calculate total cost
            double totalCost = chat.getEntries().stream()
                    .mapToDouble(ChatEntry::getCost)
                    .sum();

            // Create updated info text using current language
            int messageCount = chat.getEntries().size();
            String messagesText = messageCount == 1 ? I18nManager.getText("chat.messages.singular")
                    : I18nManager.getText("chat.messages.plural");

            String infoText = String.format("%s: %s • %d %s",
                    I18nManager.getText("chat.created"),
                    chat.getCreatedAt().toLocalDate(),
                    messageCount,
                    messagesText);

            if (totalCost > 0) {
                infoText += String.format(" • %s",
                        I18nManager.getText("chat.total.cost", String.format("%.2f", totalCost)));
            }

            // Update the tracked label directly
            chatMetadataInfoLabel.setText(infoText);
        }
    }

    /**
     * Updates dynamic labels that are created at runtime.
     */
    private void updateDynamicLabels() {
        // Update deleted warning label
        if (deletedWarningLabel != null) {
            deletedWarningLabel.setText(I18nManager.getText("chat.deleted.warning"));
        }

        // Update empty chat labels
        if (emptyTitleLabel != null) {
            emptyTitleLabel.setText(I18nManager.getText("chat.empty.title"));
        }

        if (emptyHintLabel != null) {
            emptyHintLabel.setText(I18nManager.getText("chat.empty.hint"));
        }
    }

    /**
     * Updates trash button text based on current state.
     */
    private void updateTrashButtonText() {
        if (trashVisibilityToggle == null)
            return;

        boolean showingTrash = trashVisibilityToggle.isSelected();
        trashVisibilityToggle.setText(I18nManager.getText("chat.trash.toggle"));

        String tooltipKey = showingTrash ? "chat.trash.tooltip" : "chat.trash.tooltip";
        trashVisibilityToggle.setTooltip(new Tooltip(I18nManager.getText(tooltipKey)));
    }

    /**
     * Updates input area texts.
     */
    private void updateInputAreaTexts() {
        if (promptTextArea != null) {
            updatePromptPlaceholder();
        }

        // Update BFL Prompt Upsampling text
        if (bflPromptUpsamplingCheckBox != null) {
            bflPromptUpsamplingCheckBox.setText(I18nManager.getText("input.bfl.prompt.enhancement"));
        }
        if (bflPromptUpsamplingHint != null) {
            bflPromptUpsamplingHint.setText(I18nManager.getText("input.bfl.prompt.enhancement.hint"));
        }

        // Update Gemini Thinking Level text
        if (geminiThinkingLevelLabel != null) {
            geminiThinkingLevelLabel.setText(I18nManager.getText("input.gemini.thinking.level"));
        }
        if (geminiThinkingLevelHint != null) {
            geminiThinkingLevelHint.setText(I18nManager.getText("input.gemini.thinking.level.hint"));
        }

        if (characterCountLabel != null) {
            // Update character count with current text length and dynamic limit
            String currentText = promptTextArea != null ? promptTextArea.getText() : "";
            updateCharacterCount(currentText);
        }
    }

    /**
     * Populates the model ComboBox with models from enabled providers only.
     * Also updates UI state based on availability of models.
     */
    private void populateAvailableModels() {
        art.moodframe.config.ApplicationConfig config = art.moodframe.config.ServiceFactory.getInstance()
                .getService(art.moodframe.config.ApplicationConfig.class);
        if (config == null) {
            logger.error("ApplicationConfig not available, cannot populate models");
            return;
        }

        // Get current selection before clearing
        String currentSelection = modelComboBox.getValue();

        // Clear existing items
        modelComboBox.getItems().clear();

        // Check which providers are enabled and have valid API keys
        boolean bflEnabled = config.isBflProviderEnabled() && config.hasApiKey();
        boolean geminiEnabled = config.isGeminiProviderEnabled() && config.hasGeminiApiKey();

        logger.info("Provider status - BFL: enabled={}, hasKey={} | Gemini: enabled={}, hasKey={}",
                    config.isBflProviderEnabled(), config.hasApiKey(),
                    config.isGeminiProviderEnabled(), config.hasGeminiApiKey());

        // Add models from enabled providers
        for (art.moodframe.model.GenerationModel model : art.moodframe.model.GenerationModel.selectable()) {
            art.moodframe.model.ApiProvider provider = model.getProvider();

            if (provider == art.moodframe.model.ApiProvider.BFL && bflEnabled) {
                modelComboBox.getItems().add(model.getLocalizedDisplayName());
            } else if (provider == art.moodframe.model.ApiProvider.GOOGLE_GEMINI && geminiEnabled) {
                modelComboBox.getItems().add(model.getLocalizedDisplayName());
            }
        }

        modelComboBox.getItems().addAll(openAiModelItems());

        logger.info("Available models after filtering: {}", modelComboBox.getItems());

        // Update UI state based on model availability
        boolean hasModels = !modelComboBox.getItems().isEmpty();

        if (!hasModels) {
            // No models available - disable all input controls
            logger.warn("No models available - all providers disabled or missing API keys");
            updateUIState(); // This will update input state and disable everything
        } else {
            // Restore previous selection if still available
            if (currentSelection != null && modelComboBox.getItems().contains(currentSelection)) {
                modelComboBox.setValue(currentSelection);
            } else {
                // Select first available model as default
                modelComboBox.setValue(modelComboBox.getItems().get(0));
            }

            // Update input state to potentially enable controls
            updateUIState();
        }

        // Update cost display
        updateCostDisplay();
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

        // Get provider status
        boolean bflEnabled = config.isBflProviderEnabled() && config.hasApiKey();
        boolean geminiEnabled = config.isGeminiProviderEnabled() && config.hasGeminiApiKey();

        // Clear and rebuild with filtered models
        modelComboBox.getItems().clear();

        for (art.moodframe.model.GenerationModel model : art.moodframe.model.GenerationModel.selectable()) {
            art.moodframe.model.ApiProvider provider = model.getProvider();

            // Check if provider is enabled
            boolean providerEnabled = (provider == art.moodframe.model.ApiProvider.BFL && bflEnabled)
                    || (provider == art.moodframe.model.ApiProvider.GOOGLE_GEMINI && geminiEnabled);

            if (!providerEnabled) {
                continue;
            }

            // Check if model supports the current image count
            int maxImages = model.getMaxInputImages();
            if (maxImages >= imageCount) {
                modelComboBox.getItems().add(model.getLocalizedDisplayName());
            }
        }

        if (art.moodframe.model.GenerationModel.OPENAI_IMAGE.getMaxInputImages() >= imageCount) {
            modelComboBox.getItems().addAll(openAiModelItems());
        }

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
     * Updates prompt placeholder based on current state.
     */
    private void updatePromptPlaceholder() {
        if (promptTextArea == null)
            return;

        boolean hasApiKey = !I18nManager.getText("status.api.key.none").equals(apiKeyStatusLabel.getText());
        boolean hasChatSelected = currentChat != null && !currentChat.isDeleted();

        if (!hasApiKey) {
            // Determine which provider is selected to show appropriate message
            boolean isGemini = modelComboBox != null &&
                             modelComboBox.getValue() != null &&
                             modelComboBox.getValue().toLowerCase().contains("gemini");

            String keyMessageKey = isGemini ? "status.error.api.key.missing.gemini"
                                            : "status.error.api.key.missing.bfl";
            promptTextArea.setPromptText(I18nManager.getText(keyMessageKey));
        } else if (!hasChatSelected) {
            promptTextArea.setPromptText(I18nManager.getText("input.prompt.placeholder.nochat"));
        } else if (attachedImageFile != null) {
            promptTextArea.setPromptText(I18nManager.getText("status.image.edit.attached"));
        } else {
            // Check if Gemini model is selected for enhanced placeholder
            boolean isGeminiSelected = modelComboBox != null &&
                                      modelComboBox.getValue() != null &&
                                      modelComboBox.getValue().toLowerCase().contains("gemini");

            if (isGeminiSelected) {
                promptTextArea.setPromptText(I18nManager.getText("input.prompt.placeholder.gemini"));
            } else {
                promptTextArea.setPromptText(I18nManager.getText("input.prompt.placeholder"));
            }
        }
    }

    /**
     * Updates status bar texts.
     */
    private void updateStatusBarTexts() {
        if (apiKeyStatusLabel != null) {
            String currentText = apiKeyStatusLabel.getText();
            if (currentText.contains("API Key Valid") || currentText.contains("API-Schlüssel gültig")) {
                apiKeyStatusLabel.setText(I18nManager.getText("status.api.key.valid"));
            } else if (currentText.contains("No API Key") || currentText.contains("Kein API-Schlüssel")) {
                apiKeyStatusLabel.setText(I18nManager.getText("status.api.key.none"));
            } else if (currentText.contains("Invalid") || currentText.contains("ungültig")) {
                apiKeyStatusLabel.setText(I18nManager.getText("status.api.key.invalid"));
            }
        }

        if (statusLabel != null) {
            String currentText = statusLabel.getText();
            if (currentText.contains("Ready") || currentText.contains("Bereit")) {
                statusLabel.setText(I18nManager.getText("status.ready"));
            }
            // Note: Most status messages are set dynamically via setStatus() which already
            // uses i18n
        }
    }

    /**
     * Updates parameter control texts.
     */
    private void updateParameterTexts() {
        if (safetyToleranceLabel != null) {
            int currentValue = (int) safetyToleranceSlider.getValue();
            String safetyText = getSafetyText(currentValue);
            safetyToleranceLabel.setText(I18nManager.getText("params.safety", safetyText));
        }

        if (imageCountLabel != null) {
            int currentCount = (int) imageCountSlider.getValue();
            imageCountLabel.setText(I18nManager.getText("params.image.count", currentCount));
        }

        if (seedTextField != null && seedTextField.getPromptText() != null) {
            seedTextField.setPromptText(I18nManager.getText("params.seed.placeholder"));
        }

        if (clearSeedButton != null) {
            // Clear seed tooltip is now handled by updateTooltipTexts()
        }
    }

    /**
     * Updates image attachment area texts.
     */
    private void updateImageAttachmentTexts() {
        if (uploadImageButton != null) {
            uploadImageButton.setText(I18nManager.getText("params.image.attach"));
            // Upload and remove button tooltips are now handled by updateTooltipTexts()
        }
    }

    /**
     * Gets safety tolerance text for current value.
     */
    private String getSafetyText(int value) {
        return switch (value) {
            case 1 -> I18nManager.getText("params.safety.strict");
            case 2 -> I18nManager.getText("params.safety.moderate");
            case 3 -> I18nManager.getText("params.safety.relaxed");
            default -> I18nManager.getText("params.safety.moderate");
        };
    }

    /**
     * Updates FXML hardcoded texts that need to be replaced programmatically.
     */
    private void updateFXMLTexts() {
        // Update parameter section title
        if (parametersTitle != null) {
            parametersTitle.setText(I18nManager.getText("params.title"));
        }

        // Update trash toggle button text
        if (trashVisibilityToggle != null) {
            trashVisibilityToggle.setText(I18nManager.getText("chat.trash.toggle"));
        }

        // Update parameter labels that are hardcoded in FXML
        updateParameterLabels();
    }

    /**
     * Updates parameter labels that are hardcoded in FXML.
     */
    private void updateParameterLabels() {
        // Update all parameter labels with fx:id
        if (modelLabel != null) {
            modelLabel.setText(I18nManager.getText("params.model"));
        }

        if (styleLabel != null) {
            styleLabel.setText(I18nManager.getText("params.style"));
        }

        if (aspectRatioLabel != null) {
            aspectRatioLabel.setText(I18nManager.getText("params.aspect.ratio"));
        }

        if (resolutionLabel != null) {
            resolutionLabel.setText(I18nManager.getText("params.resolution"));
        }

        if (seedLabel != null) {
            seedLabel.setText(I18nManager.getText("params.seed"));
        }

        if (imageAttachmentLabel != null) {
            imageAttachmentLabel.setText(I18nManager.getText("params.image.attachment"));
        }

        if (noImageLabel != null) {
            noImageLabel.setText(I18nManager.getText("params.image.none"));
        }

        // Update the "No image attached" label in placeholder container
        if (noImagePlaceholder != null) {
            for (javafx.scene.Node child : noImagePlaceholder.getChildren()) {
                if (child instanceof Label label && "No image attached".equals(label.getText())) {
                    label.setText(I18nManager.getText("params.image.none"));
                    break;
                }
            }
        }
    }

    /**
     * Updates chat header texts.
     */
    private void updateChatHeaderTexts() {
        if (chatTitleLabel != null) {
            // Check if no chat is currently selected (either English or German text)
            String currentText = chatTitleLabel.getText();
            if ("Select a chat".equals(currentText) || "Chat auswählen".equals(currentText) ||
                    currentChat == null || currentChat.isDeleted()) {
                chatTitleLabel.setText(I18nManager.getText("chat.select"));
            }
        }
    }

    /**
     * Updates all tooltip texts for live language switching.
     */
    private void updateTooltipTexts() {
        if (settingsTooltip != null) {
            settingsTooltip.setText(I18nManager.getText("main.settings.tooltip"));
        }

        if (newChatTooltip != null) {
            newChatTooltip.setText(I18nManager.getText("chat.new.tooltip"));
        }

        if (trashToggleTooltip != null) {
            trashToggleTooltip.setText(I18nManager.getText("chat.trash.tooltip"));
        }

        if (sendTooltip != null) {
            sendTooltip.setText(I18nManager.getText("input.send.tooltip"));
        }

        if (brainstormButton != null) {
            brainstormButton.setText(I18nManager.getText("brainstorm.button"));
            updateBrainstormButton();
        }

        if (promptUpsamplingTooltip != null) {
            promptUpsamplingTooltip.setText(I18nManager.getText("input.prompt.upsampling.tooltip"));
        }

        if (stopTooltip != null) {
            stopTooltip.setText(I18nManager.getText("input.stop.tooltip"));
        }

        if (clearSeedTooltip != null) {
            clearSeedTooltip.setText(I18nManager.getText("params.seed.clear.tooltip"));
        }

        if (uploadImageTooltip != null) {
            uploadImageTooltip.setText(I18nManager.getText("params.image.attach.tooltip"));
        }

        if (removeImageTooltip != null) {
            removeImageTooltip.setText(I18nManager.getText("params.image.remove.tooltip"));
        }
    }

    /**
     * Sets up memory monitoring and integrates it into the UI.
     */
    private void setupMemoryMonitoring() {
        try {
            // Create and initialize memory status component
            memoryStatus = new MemoryStatusComponent();

            // Find the footer HBox and add memory status before the API key status
            Node footerContent = mainBorderPane.getBottom();
            if (footerContent instanceof HBox footerBox) {
                // Insert memory status component before the last element (API key status)
                int insertIndex = Math.max(0, footerBox.getChildren().size() - 1);
                footerBox.getChildren().add(insertIndex, memoryStatus);

                if (config.getLoggingEnabled()) {
                    logger.info("Memory monitoring integrated into UI footer");
                }
            } else {
                if (config.getLoggingEnabled()) {
                    logger.warn("Could not find footer HBox for memory status integration");
                }
            }

        } catch (Exception e) {
            logger.error("Failed to setup memory monitoring", e);
        }
    }

    /**
     * Gets the memory status component for external access.
     * 
     * @return the memory status component, or null if not initialized
     */
    public MemoryStatusComponent getMemoryStatus() {
        return memoryStatus;
    }

    /**
     * Handles the deletion of a chat entry after confirmation.
     * 
     * @param entryId the entry ID to delete
     */
    private void handleDeleteEntry(String entryId) {
        // Check if entry exists and has any successfully generated images
        if (this.chatHistory == null) {
            logger.error("ChatRepository is null - cannot delete entry");
            return;
        }

        try {
            Optional<ChatEntry> entryOpt = this.chatHistory.findChatEntryById(entryId);
            if (entryOpt.isEmpty()) {
                logger.warn("Entry not found for deletion: {}", entryId);
                return;
            }

            ChatEntry entry = entryOpt.get();

            // Check if at least one image was successfully generated (CACHED status)
            java.util.List<GeneratedImage> images = this.chatHistory.findGeneratedImagesByEntry(entryId);
            boolean hasAnySuccessfulImage = images.stream()
                    .anyMatch(img -> img.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED
                            && img.getLocalFilePath() != null
                            && !img.getLocalFilePath().isEmpty());

            // Only show confirmation dialog if at least one image was successfully generated
            boolean confirmed = true;
            if (hasAnySuccessfulImage) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle(I18nManager.getText("delete.entry.title"));
                alert.setHeaderText(I18nManager.getText("delete.entry.header"));
                alert.setContentText(I18nManager.getText("delete.entry.content"));

                Optional<ButtonType> result = alert.showAndWait();
                confirmed = result.isPresent() && result.get() == ButtonType.OK;
            }

            if (confirmed) {
            /*  The cost goes to the chat the entry was deleted in, even if another chat is shown meanwhile  */
            Chat owner = currentChat;
            CompletableFuture.runAsync(() -> {
                try {
                    // Only the active cost counts: already deleted images are in the chat's deleted cost already
                    double activeEntryCost = entry.getCost() - entry.getDeletedCost();
                    this.chatHistory.deleteEntry(entryId, images);

                    // Update UI
                    Platform.runLater(() -> {
                        if (owner != null) {
                            owner.getEntries().removeIf(e -> e.getId().equals(entryId));
                            owner.setDeletedCost(owner.getDeletedCost() + activeEntryCost);
                        }

                        // Remove the card from UI
                        removeEntryCardFromUI(entryId);

                        // Update cost display in both places
                        updateChatHeader(); // Updates the scrollable area header
                        if (currentChat != null) {
                            updateChatTotalCost(currentChat); // Updates the top header
                        }

                        // Show success notification
                        showNotification(I18nManager.getText("delete.entry.success"));
                    });

                } catch (Exception e) {
                    logger.error("Failed to delete entry: {}", entryId, e);
                    Platform.runLater(() -> {
                        showErrorMessage(I18nManager.getText("delete.entry.error") + ": " + e.getMessage());
                    });
                }
            });
            }
        } catch (Exception e) {
            logger.error("Failed to check entry for deletion: {}", entryId, e);
            showErrorMessage(I18nManager.getText("delete.entry.error") + ": " + e.getMessage());
        }
    }

    /**
     * Handles the deletion of a single image after confirmation.
     * 
     * @param entryId the entry ID containing the image
     * @param imageId the image ID to delete
     */
    private void handleDeleteImage(String entryId, String imageId) {
        logger.debug("handleDeleteImage called - entryId: {}, imageId: {} (length: {})",
                entryId, imageId, imageId != null ? imageId.length() : "null");

        // Check if image actually exists (was successfully generated)
        if (this.chatHistory == null) {
            logger.error("ChatRepository is null - cannot delete image");
            return;
        }

        try {
            Optional<GeneratedImage> imageOpt = this.chatHistory.findGeneratedImageById(imageId);
            if (imageOpt.isEmpty()) {
                logger.warn("Image not found for deletion - ID: {} (length: {})",
                        imageId, imageId != null ? imageId.length() : "null");
                return;
            }

            GeneratedImage image = imageOpt.get();

            // Check if image was successfully downloaded (CACHED status means image file exists)
            boolean imageExists = image.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED
                    && image.getLocalFilePath() != null
                    && !image.getLocalFilePath().isEmpty();

            // Only show confirmation dialog if image actually exists
            boolean confirmed = true;
            if (imageExists) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle(I18nManager.getText("delete.image.title"));
                alert.setHeaderText(I18nManager.getText("delete.image.header"));
                alert.setContentText(I18nManager.getText("delete.image.content"));

                Optional<ButtonType> result = alert.showAndWait();
                confirmed = result.isPresent() && result.get() == ButtonType.OK;
            }

            if (confirmed) {
            // Perform the deletion
            CompletableFuture.runAsync(() -> {
                try {
                    logger.info("Deleting image with ID: {} (length: {})",
                            imageId, imageId != null ? imageId.length() : "null");

                    ChatHistoryService.ImageDeletion deletion = this.chatHistory.deleteImage(entryId, image);
                    double updatedDeletedCost = deletion.entryDeletedCost();
                    final boolean entryDeleted = deletion.entryDeleted();

                    // Update UI
                    Platform.runLater(() -> {
                        // Find and update the entry in the ListView's items
                        ChatEntry targetEntry = null;
                        if (chatEntriesListView != null && chatEntriesListView.getItems() != null) {
                            for (ChatEntry entry : chatEntriesListView.getItems()) {
                                if (entry.getId().equals(entryId)) {
                                    targetEntry = entry;
                                    // Remove the deleted image from the entry's image list
                                    entry.getImages().removeIf(img -> img.getId().equals(imageId));
                                    // Update the deleted cost in the entry
                                    entry.setDeletedCost(updatedDeletedCost);
                                    break;
                                }
                            }
                        }

                        // Also update in currentChat's entries
                        if (currentChat != null) {
                            for (ChatEntry entry : currentChat.getEntries()) {
                                if (entry.getId().equals(entryId)) {
                                    entry.setDeletedCost(updatedDeletedCost);
                                    break;
                                }
                            }
                            // NOTE: We do NOT update currentChat.deletedCost here!
                            // Chat.deletedCost only tracks fully deleted entries, not individual deleted
                            // images.
                            // Individual deleted images are tracked in ChatEntry.deletedCost
                        }

                        // If the entry was soft-deleted (all images deleted), remove it from UI
                        if (entryDeleted && targetEntry != null) {
                            // Remove the entry from ListView immediately
                            chatEntriesListView.getItems().remove(targetEntry);

                            // Remove the entry from the in-memory list immediately
                            // This prevents it from reappearing when the chat is reloaded
                            if (currentChat != null) {
                                currentChat.getEntries().removeIf(e -> e.getId().equals(entryId));
                            }

                            // Update the UI immediately
                            updateChatHeader();
                            updateChatTotalCost(currentChat);
                        } else if (targetEntry != null) {
                            // Force ListView refresh by triggering item update
                            int index = chatEntriesListView.getItems().indexOf(targetEntry);
                            if (index >= 0) {
                                chatEntriesListView.getItems().set(index, targetEntry);
                            }
                        }

                        // Update cost display in both places
                        updateChatHeader(); // Updates the scrollable area header
                        if (currentChat != null) {
                            updateChatTotalCost(currentChat); // Updates the top header
                        }

                        // Show success notification
                        showNotification(I18nManager.getText("delete.image.success"));
                    });

                } catch (Exception e) {
                    logger.error("Failed to delete image: {}", imageId, e);
                    Platform.runLater(() -> {
                        showErrorMessage(I18nManager.getText("delete.image.error") + ": " + e.getMessage());
                    });
                }
            });
            }
        } catch (Exception e) {
            logger.error("Failed to check image for deletion: {}", imageId, e);
            showErrorMessage(I18nManager.getText("delete.image.error") + ": " + e.getMessage());
        }
    }

    /**
     * Removes an entry card from the UI.
     *
     * @param entryId the entry ID to remove
     */
    private void removeEntryCardFromUI(String entryId) {
        if (chatEntriesListView == null || chatEntriesListView.getItems() == null) {
            return;
        }

        // Find and remove the entry from ListView
        chatEntriesListView.getItems().removeIf(entry -> entry.getId().equals(entryId));
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
     * @param model the selected model name (used to detect provider)
     */
    private void updateParameterVisibility(String model) {
        if (model == null) {
            return; // Cannot determine provider without model
        }

        // Resolve GenerationModel for accurate provider detection
        art.moodframe.model.GenerationModel generationModel = art.moodframe.model.GenerationModel.fromLocalizedDisplayName(model);
        boolean isVideo = generationModel != null && generationModel.isVideoModel();

        // Use GenerationModel.getProvider() for accurate detection (handles NB2 etc.)
        ApiProvider provider;
        if (generationModel != null) {
            provider = generationModel.getProvider();
        } else {
            // Fallback to string matching for unknown models
            provider = model.toLowerCase().contains("gemini")
                    ? ApiProvider.GOOGLE_GEMINI
                    : ApiProvider.BFL;
        }

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
        if (bflPromptUpsamplingHint != null) {
            bflPromptUpsamplingHint.setVisible(showUpsampling);
            bflPromptUpsamplingHint.setManaged(showUpsampling);
        }

        // Common parameters (always visible for both BFL and Gemini)
        // - modelComboBox (always visible)
        // - aspectRatioComboBox (always visible - but with different options per provider)
        // - stylePresetsComboBox (always visible - style appended to prompt for both providers)

        // Update aspect ratio options based on provider
        updateAspectRatioOptions(provider);

        // Gemini Advanced Features (Phase 2) - only visible for Gemini models
        if (geminiAdvancedFeaturesContainer != null && geminiAdvancedFeaturesSection != null) {
            // The only advanced feature is Google Search grounding, which Nano Banana 2 Lite does not support
            boolean showAdvanced = isGemini && (generationModel == null || generationModel.supportsGroundingSearch());
            geminiAdvancedFeaturesContainer.setVisible(showAdvanced);
            geminiAdvancedFeaturesContainer.setManaged(showAdvanced);
        }

        // FLUX.2 Specific Parameters
        updateFlux2ParameterVisibility(model);

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

                if (videoDurationHint != null)
                    videoDurationHint.setText(I18nManager.getText("omni.duration.hint"));
            }
        }

        // Image count: disabled for video (always 1 video per request)
        if (imageCountSlider != null) {
            imageCountSlider.setDisable(isVideo);
            if (isVideo) {
                imageCountSlider.setValue(1);
            }
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
     * @param modelDisplayName the localized display name of the selected model
     */
    private void updateFlux2ParameterVisibility(String modelDisplayName) {
        if (flux2ParametersContainer == null) {
            return; // FXML not loaded yet
        }

        // Get GenerationModel from display name
        art.moodframe.model.GenerationModel generationModel = art.moodframe.model.GenerationModel.fromLocalizedDisplayName(modelDisplayName);

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

    /**
     * Updates the aspect ratio options based on the selected API provider.
     * <p>
     * FLUX.2 supports: 21:9, 16:9, 3:2, 5:4, 1:1, 4:5, 2:3, 9:16, 9:21; FLUX 3 adds 2:1, 7:5, 4:3, 3:4, 5:7, 1:2
     * Gemini supports: 1:1, 2:3, 3:2, 3:4, 4:3, 4:5, 5:4, 9:16, 16:9, 21:9
     * </p>
     * <p>
     * When switching to Gemini with unsupported ratio selected:
     * - 9:21 → 9:16 (closest vertical alternative)
     * </p>
     *
     * @param provider the selected API provider
     */
    private void updateAspectRatioOptions(ApiProvider provider) {
        if (aspectRatioComboBox == null) {
            return;
        }

        String currentSelection = aspectRatioComboBox.getValue();

        // Define aspect ratios per provider
        // FLUX.2: 21:9, 16:9, 3:2, 5:4, 1:1, 4:5, 2:3, 9:16, 9:21 (FLUX 3: AspectRatios.FLUX_3)
        // Gemini standard: 1:1, 2:3, 3:2, 3:4, 4:3, 4:5, 5:4, 9:16, 16:9, 21:9
        // Nano Banana 2 adds: 1:4, 4:1, 1:8, 8:1
        String[] bflRatios = art.moodframe.model.AspectRatios.BFL;
        String[] geminiRatios = art.moodframe.model.AspectRatios.GEMINI;
        String[] nanoBanana2Ratios = art.moodframe.model.AspectRatios.NANO_BANANA_2;

        String[] videoRatios = art.moodframe.model.AspectRatios.VIDEO;

        String[] ratiosToUse;
        if (currentSelectedModel != null && currentSelectedModel.isVideoModel()) {
            // Video models (Omni): Only landscape and portrait
            ratiosToUse = videoRatios;
        } else if (provider == ApiProvider.OPENAI) {
            ratiosToUse = art.moodframe.model.AspectRatios.forOpenAiModel(
                    art.moodframe.model.GenerationModel.openAiModelId(modelComboBox.getValue()));
        } else if (provider == ApiProvider.GOOGLE_GEMINI) {
            // Check if current model is Nano Banana 2 (extended ratios)
            ratiosToUse = (currentSelectedModel != null && currentSelectedModel.supportsExtendedAspectRatios())
                    ? nanoBanana2Ratios : geminiRatios;
        } else {
            ratiosToUse = currentSelectedModel != null && currentSelectedModel.isFlux3Model()
                    ? art.moodframe.model.AspectRatios.FLUX_3 : bflRatios;
        }

        // Update the combo box items
        aspectRatioComboBox.getItems().clear();
        aspectRatioComboBox.getItems().addAll(ratiosToUse);

        // Determine which value to select
        String newSelection = currentSelection;

        if (provider == ApiProvider.GOOGLE_GEMINI && currentSelection != null) {
            // Map unsupported BFL ratios to closest Gemini alternatives
            if ("9:21".equals(currentSelection)) {
                newSelection = "9:16"; // Closest vertical alternative
                logger.info("Aspect ratio 9:21 not supported by Gemini, switching to 9:16");
            }
        }

        // If the current selection is still valid, keep it; otherwise use default
        if (newSelection != null && aspectRatioComboBox.getItems().contains(newSelection)) {
            aspectRatioComboBox.setValue(newSelection);
        } else {
            // Default to 16:9 if previous selection not available
            aspectRatioComboBox.setValue("16:9");
        }
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
