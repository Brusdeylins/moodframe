package art.moodframe.ui.controller;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryFrame;
import art.moodframe.model.EntryGroup;
import art.moodframe.model.EntryMood;
import art.moodframe.model.FrameGroup;
import art.moodframe.model.Frame;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.Mood;
import art.moodframe.model.MoodGroup;
import art.moodframe.service.ChatHistoryService;
import art.moodframe.service.FrameService;
import art.moodframe.service.MoodService;
import art.moodframe.service.ThumbnailManager;
import art.moodframe.util.I18nChangeListener;
import art.moodframe.util.I18nManager;
import art.moodframe.ui.BoxInset;
import art.moodframe.ui.component.ChatEntryCard;
import art.moodframe.ui.component.FrameChoice;
import art.moodframe.ui.component.FrameListCell;
import art.moodframe.ui.component.ImageGrid;
import art.moodframe.ui.component.MemoryStatusComponent;
import art.moodframe.ui.component.MoodChoice;
import art.moodframe.ui.component.MoodListCell;
import art.moodframe.ui.cell.ChatListCell;
import art.moodframe.ui.cell.FrameGroupListCell;
import art.moodframe.ui.cell.ListReorder;
import art.moodframe.ui.cell.MoodGroupListCell;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
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
    private Button infoButton;
    @FXML
    private Tooltip infoTooltip;
    @FXML
    private Label appVersionLabel;
    @FXML
    private HBox appHeader;

    // Sidebar Controls
    @FXML
    private SplitPane mainSplitPane;
    @FXML
    private Button newChatButton;
    @FXML
    private Button trashButton;
    @FXML
    private ListView<Chat> chatListView;
    @FXML
    private HBox sidebarHeader;
    @FXML
    private Label sidebarTitleLabel;
    @FXML
    private Tooltip sidebarModeTooltip;
    @FXML
    private RadioMenuItem byChatMenuItem;
    @FXML
    private RadioMenuItem byMoodMenuItem;
    @FXML
    private RadioMenuItem byFrameMenuItem;
    @FXML
    private ListView<MoodGroup> moodGroupListView;
    @FXML
    private ListView<FrameGroup> frameGroupListView;

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

    /*  The images view beside the chat, the switch between them and the size of the images both share  */
    @FXML
    private HBox chatHeader;
    @FXML
    private Slider imageSizeSlider;
    @FXML
    private Tooltip imageSizeTooltip;
    @FXML
    private ToggleButton chatViewButton;
    @FXML
    private Tooltip chatViewTooltip;
    @FXML
    private ToggleButton imagesViewButton;
    @FXML
    private Tooltip imagesViewTooltip;
    private ImageGrid imageGrid;

    // Input Area Controls
    @FXML
    private VBox chatInputArea;
    @FXML
    private TextArea promptTextArea;
    @FXML
    private Label characterCountLabel;

    // Parameter Panel Controls
    @FXML
    private Label moodTitleLabel;
    @FXML
    private CheckBox moodApplyCheckBox;
    @FXML
    private Button moodFromImageButton;
    @FXML
    private Button saveMoodButton;
    @FXML
    private Button saveMoodAsButton;
    @FXML
    private Button manageMoodsButton;
    @FXML
    private javafx.scene.control.ComboBox<MoodChoice> moodComboBox;
    @FXML
    private Button moodIncompleteButton;
    @FXML
    private Label frameTitleLabel;
    @FXML
    private Button saveFrameButton;
    @FXML
    private Button saveFrameAsButton;
    @FXML
    private Button manageFramesButton;
    @FXML
    private javafx.scene.control.ComboBox<FrameChoice> frameComboBox;
    @FXML
    private Label aspectRatioHintLabel;
    @FXML
    private Label resolutionHintLabel;
    @FXML
    private Label generationTitleLabel;
    @FXML
    private TitledPane moodGroupPane;
    @FXML
    private TitledPane frameGroupPane;
    @FXML
    private TitledPane generationGroupPane;
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
    @FXML
    private javafx.scene.control.ComboBox<art.moodframe.model.StylePresetItem> lightComboBox;
    @FXML
    private Label lightLabel;
    @FXML
    private Label lightLayoutHint;
    @FXML
    private javafx.scene.control.ComboBox<art.moodframe.model.StylePresetItem> cameraComboBox;
    @FXML
    private Label cameraLabel;
    @FXML
    private Label cameraHint;
    @FXML
    private javafx.scene.control.ComboBox<art.moodframe.model.StylePresetItem> atmosphereComboBox;
    @FXML
    private Label atmosphereLabel;
    @FXML
    private ToggleButton atmosphereLockButton;
    @FXML
    private ToggleButton styleLockButton;
    @FXML
    private ToggleButton colorLockButton;
    @FXML
    private ToggleButton lightLockButton;
    @FXML
    private ToggleButton cameraLockButton;

    /*  The building block lists with their actions on top: style, atmosphere, light and camera, and the color  */
    private final java.util.Map<art.moodframe.model.PromptBlock.Type,
            art.moodframe.ui.component.BlockChoice<art.moodframe.model.StylePresetItem>> blockChoices =
            new java.util.EnumMap<>(art.moodframe.model.PromptBlock.Type.class);
    private art.moodframe.ui.component.BlockChoice<art.moodframe.model.ColorStyleItem> colorChoice;

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
    private Tooltip trashTooltip;
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

    /*  The chat keeps its end in view while the images of a new entry arrive: from sending until the user scrolls
        away from the end  */
    private boolean wasScrolledToBottom = false;
    private int lastChatEntriesCount = 0; // Track number of entries to detect new ones

    /*  A guard while the chats get a new order: the list keeps its selection without opening the chat again  */
    private boolean reorderingChats;

    // Services
    private art.moodframe.service.RequestResponseManager requestResponseManager;
    private ChatHistoryService chatHistory;
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

    /*  The moods, and a guard while the mood selection sets the fields; whether the chosen mood is changed is
        compared anew from the fields each time (see isMoodChanged)  */
    private MoodService moodService;
    private boolean updatingMood;

    /*  The frames: whether aspect ratio or resolution differ from the chosen frame, and a guard while the frame
        selection sets them  */
    private FrameService frameService;
    private boolean frameChanged;
    private boolean updatingFrame;

    /*  The left sidebar by mood or by frame: the group whose entries are shown (null while a chat is shown, which
        keeps taking the new prompts as the last active one) and a guard while the list is filled again  */
    private EntryGroup shownGroup;
    private boolean updatingGroups;

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
        setupPanelGroups();
        setupSidebarMode();
        setupCornerCloud();
        setupImagePreviewOverlay();
        setupEventHandlers();
        setupDragAndDrop();
        setupMultiImageAttachmentPanel();
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
            multiImagePanel.setOnMoodFromImage(this::openMoodFromImage);

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
            art.moodframe.service.PromptBlockService styleService =
                    services.getService(art.moodframe.service.PromptBlockService.class);
            art.moodframe.service.ColorStyleService colorService =
                    services.getService(art.moodframe.service.ColorStyleService.class);

            /*  The names stay as shown: the OpenAI models share one constant and differ only by their name  */
            java.util.List<String> modelNames = modelComboBox.getItems().stream()
                    .filter((name) -> art.moodframe.model.GenerationModel.fromLocalizedDisplayName(name) != null)
                    .toList();
            java.util.List<art.moodframe.model.GenerationModel> models = modelNames.stream()
                    .map(art.moodframe.model.GenerationModel::fromLocalizedDisplayName)
                    .toList();

            /*  the building blocks and the mood as chosen, a locked field keeps its choice on "Adopt"  */
            java.util.Set<Mood.Field> locked = lockedMoodFields();
            Mood mood = moodService != null ? chosenMood() : null;
            String currentState = "Target model: " + modelComboBox.getValue()
                    + (modelResolution() != null ? "\nResolution: " + modelResolution() : "")
                    + "\nMood: " + (mood != null ? mood.getName() + (isMoodChanged() ? " (changed)" : "") : "None")
                    + fieldState("Style", stylePresetsComboBox.getValue(), locked.contains(Mood.Field.STYLE))
                    + fieldState("Atmosphere", atmosphereComboBox.getValue(), locked.contains(Mood.Field.ATMOSPHERE))
                    + fieldState("Light", lightComboBox.getValue(), locked.contains(Mood.Field.LIGHT))
                    + fieldState("Color", colorStyleComboBox.getValue(), locked.contains(Mood.Field.COLOR))
                    + fieldState("Camera", cameraComboBox.getValue(), locked.contains(Mood.Field.CAMERA))
                    + "\nAspect ratio: " + modelAspectRatio()
                    + "\nText currently in the input field: "
                    + (promptTextArea.getText().isBlank() ? "(empty)" : promptTextArea.getText().trim());

            art.moodframe.service.BrainstormContext brainstormContext = new art.moodframe.service.BrainstormContext(
                    styleService.getAll(art.moodframe.model.PromptBlock.Type.STYLE),
                    styleService.getAll(art.moodframe.model.PromptBlock.Type.ATMOSPHERE),
                    styleService.getAll(art.moodframe.model.PromptBlock.Type.LIGHT),
                    styleService.getAll(art.moodframe.model.PromptBlock.Type.CAMERA), colorService.getAllStyles(),
                    moodService != null ? moodService.getAll() : List.of(), models, currentState, modelNames, locked);
            art.moodframe.service.BrainstormService brainstormService = art.moodframe.service.BrainstormService.create(config);

            Stage ownerStage = (Stage) mainBorderPane.getScene().getWindow();
            new art.moodframe.ui.dialog.BrainstormDialog(ownerStage, brainstormService, brainstormContext,
                    modelComboBox.getValue(), this::applyBrainstormResult).show();
        } catch (Exception e) {
            logger.error("Failed to open brainstorm dialog", e);
        }
    }

    /** One line of the current state for the brainstorm: a building block field, its entry and its lock. */
    private static String fieldState(String label, Object entry, boolean locked) {
        return "\n" + label + ": " + (entry != null ? entry.toString() : "None") + (locked ? " (locked)" : "");
    }

    /**
     * Applies the values adopted in the brainstorm popup. The target model is set first, as it
     * decides which aspect ratios and video parameters are available.
     *
     * @param result the adopted values; a {@code null} value is left unchanged
     */
    private void applyBrainstormResult(art.moodframe.ui.dialog.BrainstormDialog.Result result) {
        modelComboBox.setValue(result.targetModel());

        /*  The adopted resolution (or the one of a model without resolution variants) as remembered choice of the
            family and as target, of which the model takes the same; any other leaves the target as it is  */
        String resolution = result.resolution() != null ? result.resolution()
                : art.moodframe.model.ModelFamilies.resolutionOf(result.targetModel());
        if (resolution != null) {
            modelFamilySelector.remember(result.targetModel(), resolution);
            if (resolutionChoiceBox.getItems().contains(resolution))
                resolutionChoiceBox.setValue(resolution);
        }
        promptTextArea.setText(result.prompt());

        /*  the mood sets the fields that are not locked, an adopted block then replaces the block of its field and a
            field that is not adopted keeps its block; a locked field keeps its choice  */
        java.util.Map<Mood.Field, String> before = currentMoodFields();
        java.util.Set<Mood.Field> locked = lockedMoodFields();
        Mood mood = result.moodId() != null && moodService != null ? moodService.find(result.moodId()) : null;
        if (mood != null)
            chooseMood(mood);
        for (Mood.Field field : art.moodframe.model.BrainstormDraft.FIELDS) {
            if (locked.contains(field))
                continue;
            art.moodframe.model.BrainstormDraft.Block block = result.blocks().get(field);
            if (block != null)
                adoptBlock(field, block);
            else if (mood != null)
                selectField(field, before.get(field));
        }
        if (result.aspectRatio() != null && aspectRatioComboBox.getItems().contains(result.aspectRatio())) {
            aspectRatioComboBox.setValue(result.aspectRatio());
        }

        if (result.durationSeconds() != null && videoDurationChoiceBox.getItems().contains(result.durationSeconds() + "s")) {
            videoDurationChoiceBox.setValue(result.durationSeconds() + "s");
        }
        if (result.saveMood() && moodService != null)
            saveMood(true);
        promptTextArea.requestFocus();
    }

    /**
     * Sets a building block adopted in the brainstorm popup into its field. A new block is stored in the list of the
     * user first; a new style counts as photographic, like every style of the user, a new camera brings its settings.
     */
    private void adoptBlock(Mood.Field field, art.moodframe.model.BrainstormDraft.Block block) {
        if (!block.isNew()) {
            selectField(field, block.id());
            return;
        }

        boolean saved;
        if (field == Mood.Field.COLOR) {
            art.moodframe.service.ColorStyleService service = colorStyleService();
            saved = service != null && service.addCustomStyle(
                    new art.moodframe.model.ColorStyle(block.name(), block.colors(), false));
            if (saved)
                fillColorChoice(block.name());
        }
        else {
            art.moodframe.service.PromptBlockService service = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.service.PromptBlockService.class);
            art.moodframe.model.PromptBlock.Type type = art.moodframe.model.PromptBlock.Type.valueOf(field.name());
            saved = service != null && service.addCustom(type == art.moodframe.model.PromptBlock.Type.CAMERA
                    ? art.moodframe.service.PromptBlockService.camera(null, block.name(), block.settings())
                    : new art.moodframe.model.PromptBlock(null, type, block.name(), block.text(), null,
                            type == art.moodframe.model.PromptBlock.Type.STYLE));
            if (saved)
                fillBlockChoice(type, block.name());
        }
        if (!saved)
            setStatus(I18nManager.getText("block.error.save", block.name()));
    }

    /** Selects the entry with this id in the field of a building block, "None" if the field does not list it. */
    private void selectField(Mood.Field field, String id) {
        if (field != Mood.Field.COLOR) {
            selectBlock(art.moodframe.model.PromptBlock.Type.valueOf(field.name()), id);
            return;
        }
        art.moodframe.model.ColorStyle palette = colorStyleService().getStyleById(id);
        String paletteName = palette != null ? palette.getName() : "None";
        colorStyleComboBox.getItems().stream().filter((item) -> colorChoice.isEntry(item)
                && item.getName().equals(paletteName)).findFirst()
                .or(() -> colorStyleComboBox.getItems().stream().filter(colorChoice::isEntry).findFirst())
                .ifPresent(colorStyleComboBox::setValue);
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
     * Builds the final prompt from the user input and the selected building blocks,
     * see {@link art.moodframe.service.PromptComposer}.
     */
    public String buildFinalPrompt() {
        return promptComposer().compose(promptTextArea.getText(), selectedBlocks());
    }

    /** The composer for the length limit of the selected model. */
    private art.moodframe.service.PromptComposer promptComposer() {
        return new art.moodframe.service.PromptComposer(getMaxPromptLength());
    }

    /** The building blocks selected in the main window; none with a reference image unless "Apply mood" is on. */
    private art.moodframe.service.PromptComposer.Selection selectedBlocks() {
        if (attachedImageFile != null && moodApplyCheckBox != null && !moodApplyCheckBox.isSelected())
            return new art.moodframe.service.PromptComposer.Selection(null, null, null, null, null);

        art.moodframe.model.StylePresetItem styleItem = stylePresetsComboBox != null ? stylePresetsComboBox.getValue() : null;
        art.moodframe.model.StylePresetItem atmosphereItem = atmosphereComboBox != null ? atmosphereComboBox.getValue() : null;
        art.moodframe.model.StylePresetItem lightItem = lightComboBox != null ? lightComboBox.getValue() : null;
        art.moodframe.model.StylePresetItem cameraItem = cameraComboBox != null ? cameraComboBox.getValue() : null;
        art.moodframe.model.ColorStyleItem colorItem = colorStyleComboBox != null ? colorStyleComboBox.getValue() : null;
        art.moodframe.model.ColorStyle palette = colorItem != null && !"None".equals(colorItem.getName())
                ? art.moodframe.model.ColorStyle.fromColorStyleItem(colorItem) : null;
        return new art.moodframe.service.PromptComposer.Selection(styleItem != null ? styleItem.getBlock() : null,
                atmosphereItem != null ? atmosphereItem.getBlock() : null, lightItem != null ? lightItem.getBlock() : null,
                cameraItem != null ? cameraItem.getBlock() : null, palette);
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
     * Gives the input area below the chat its remembered height, or else the default one, which shows the button
     * column and about five lines of the prompt.
     *
     * @param config the configuration holding the remembered height, or null
     */
    private void restoreChatInputHeight(art.moodframe.config.ApplicationConfig config) {
        /*  The items of the SplitPane get their styles after its first layout, the default height needs them  */
        chatInputSplitPane.applyCss();
        double savedHeight = config != null ? config.getChatInputHeight() : 0;
        resizeChatInputArea(savedHeight > 0 ? savedHeight : chatInputArea.prefHeight(chatInputArea.getWidth()));
    }

    /**
     * Moves the divider above the input area below the chat, so that the input area gets the given height.
     *
     * @param height the height in pixels
     */
    private void resizeChatInputArea(double height) {
        /*  The divider position is a share of the SplitPane height: shift it by the missing pixels  */
        chatInputSplitPane.layout();
        javafx.geometry.Insets insets = chatInputSplitPane.getInsets();
        double size = chatInputSplitPane.getHeight() - insets.getTop() - insets.getBottom();
        if (size > 0)
            chatInputSplitPane.setDividerPosition(0,
                    chatInputSplitPane.getDividerPositions()[0] + (chatInputArea.getHeight() - height) / size);
    }

    /*  the given padding with another left inset  */
    private static javafx.geometry.Insets withLeft(javafx.geometry.Insets padding, double left) {
        return new javafx.geometry.Insets(padding.getTop(), padding.getRight(), padding.getBottom(), left);
    }

    /**
     * Sets up the initial UI state and component configurations.
     */
    private void setupUI() {
        /*  One inset for all boxes: the sidebar heading on the line of the names, the "M" of the logo 1 px further in
            for the side bearing of their letters (behind the 10 px padding of the sidebar), the chat title 1 px further
            out on the edge of the cards and of the first column of images, the prompt text behind the 1 px border  */
        appHeader.setPadding(withLeft(appHeader.getPadding(), 10.0 + BoxInset.LEFT + 1.0));
        sidebarHeader.setPadding(withLeft(sidebarHeader.getPadding(), BoxInset.LEFT));
        chatHeader.setPadding(withLeft(chatHeader.getPadding(), BoxInset.LEFT - 1.0));
        promptTextArea.setPadding(new javafx.geometry.Insets(2.0, 2.0, 2.0, BoxInset.LEFT - 1.0 - BoxInset.TEXT));

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

        // Configure Chat-Input SplitPane: the input area keeps its height in pixels, restored after the first layout
        try {
            art.moodframe.config.ApplicationConfig config = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.config.ApplicationConfig.class);

            // Restore the height once the SplitPane got its first size, after that layout pass
            chatInputSplitPane.heightProperty().addListener((obs, oldHeight, newHeight) -> {
                if (oldHeight.doubleValue() == 0 && newHeight.doubleValue() > 0)
                    Platform.runLater(() -> restoreChatInputHeight(config));
            });

            // Save the height when the user drags the divider
            if (config != null) {
                chatInputSplitPane.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, (event) -> {
                    Node node = event.getTarget() instanceof Node target ? target : null;
                    while (node != null && !node.getStyleClass().contains("split-pane-divider"))
                        node = node.getParent();
                    if (node != null) {
                        chatInputSplitPane.layout();
                        config.setChatInputHeight(chatInputArea.getHeight());
                    }
                });
            }
        } catch (Exception e) {
            logger.warn("Failed to set up the chat-input height, using default", e);
        }

        // Setup model selection based on enabled providers
        populateAvailableModels();

        // Setup aspect ratio selection: the targets of all models, of which the chosen model takes its closest one
        aspectRatioComboBox.getItems().addAll(art.moodframe.model.AspectRatios.ALL);
        aspectRatioComboBox.valueProperty().addListener((obs, oldVal, newVal) -> updateAspectRatioHint());

        // Setup resolution selection: the sizes of all models, of which the chosen model takes its closest one
        resolutionChoiceBox.getItems().addAll(art.moodframe.model.ModelFamilies.RESOLUTIONS);
        resolutionChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> updateResolutionHint());

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

        // Setup the building block lists: atmosphere, style, color and light
        setupBlockChoices();

        // Setup the locks of the building block fields and the "Apply mood" switch
        setupBlockLocks();

        // Setup Gemini Advanced Features section (Phase 2)
        setupGeminiAdvancedFeatures();

        // Setup FLUX.2 Parameters (guidance, steps, grounding search)
        setupFlux2Parameters();

        // Setup cost display programmatically
        setupCostDisplay();

        // Optimize ScrollPane for smooth scrolling performance
        optimizeScrollPane();

        // The model dropdown lists families, the target resolution of the frame chooses the variant
        modelFamilySelector = new art.moodframe.ui.component.ModelFamilySelector(modelComboBox, modelFamilyChoiceBox,
                resolutionChoiceBox, config);

        // Load saved values from configuration
        loadUISelections();

        // Setup persistence listeners for UI selections
        setupPersistenceListeners();

        // Setup the mood selection on the restored fields
        setupMoods();

        // Setup the frame selection on the restored fields
        setupFrames();

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

            // Listen to the central trash for automatic UI refresh
            art.moodframe.service.TrashCenter trashCenter = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.service.TrashCenter.class);

            if (trashCenter != null) {
                /*  Only the chats that left the trash are touched: a full reload would replace the chat objects
                    that running requests still update  */
                trashCenter.addListener(new art.moodframe.service.TrashCenter.Listener() {
                    @Override
                    public void restored(art.moodframe.service.TrashGroup.Kind kind, String id) {
                        if (kind == art.moodframe.service.TrashGroup.Kind.CHATS)
                            javafx.application.Platform.runLater(() -> showRestoredChat(id));
                        else if (kind == art.moodframe.service.TrashGroup.Kind.IMAGES)
                            javafx.application.Platform.runLater(() -> showRestoredImages(id));
                        else if (kind == art.moodframe.service.TrashGroup.Kind.BLOCKS)
                            javafx.application.Platform.runLater(() -> {
                                refreshBlockChoices();
                                refreshMoods();
                            });
                        else if (kind == art.moodframe.service.TrashGroup.Kind.FRAMES)
                            javafx.application.Platform.runLater(() -> refreshFrames());
                        else
                            javafx.application.Platform.runLater(() -> refreshMoods());
                    }

                    /*  A mood that lost its last block with these blocks may go to the trash as well  */
                    @Override
                    public void deleted(art.moodframe.service.TrashGroup.Kind kind, java.util.List<String> ids) {
                        if (kind == art.moodframe.service.TrashGroup.Kind.CHATS)
                            javafx.application.Platform.runLater(() -> removeChats(ids));
                        else if (kind == art.moodframe.service.TrashGroup.Kind.BLOCKS)
                            javafx.application.Platform.runLater(() -> {
                                askToDeleteEmptiedMoods();
                                refreshMoods();
                            });
                    }
                });
            } else {
                logger.warn("TrashCenter not available - no automatic UI refresh");
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
     * Later changes (what leaves the trash) update the list instead of reloading it, so running requests keep
     * their chat objects.
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

    /** Shows a chat again that was restored from the trash. */
    private void showRestoredChat(String chatId) {
        Chat chat = chatList.stream().filter((listed) -> listed.getId().equals(chatId)).findFirst().orElse(null);
        if (chat != null) {
            chat.restore();
        } else {
            /*  A chat without entries left the list when it was deleted, it comes back from the database  */
            try {
                chat = chatHistory.findChat(chatId).orElse(null);
            } catch (Exception e) {
                logger.error("Failed to load restored chat: {}", chatId, e);
            }
            if (chat == null)
                return;

            /*  It goes back to its place in the order  */
            int sortOrder = chat.getSortOrder();
            chatList.add((int) chatList.stream().filter((listed) -> listed.getSortOrder() <= sortOrder).count(),
                    chat);
        }

        // Refresh filter to show the restored chat in active list
        filteredChatList.setPredicate(this::shouldShowChat);

        // If this was the current chat, reload it to show images from active directory; an open group shows it again
        if (currentChat == chat || shownGroup != null) {
            updateChatView(currentChat);
        }
        refreshGroups();

        // Update UI state as input area should be enabled for restored chats
        updateUIState();

        setStatus(I18nManager.getText("status.chat.restored"));
    }

    /**
     * Shows an entry or an image again that came back from the trash, at its place in its chat. An entry in use
     * keeps its object, as running requests may hold it; a deleted entry is inserted again.
     *
     * @param id the ID of the entry or of the image
     */
    private void showRestoredImages(String id) {
        try {
            ChatEntry restored = chatHistory.findRestoredEntry(id).orElse(null);
            Chat chat = restored == null ? null : chatList.stream()
                    .filter((listed) -> listed.getId().equals(restored.getChatId())).findFirst().orElse(null);
            if (chat == null)
                return;

            java.util.Comparator<ChatEntry> order = java.util.Comparator.comparingInt(ChatEntry::getSortOrder)
                    .thenComparing(ChatEntry::getCreatedAt);
            ChatEntry entry = chat.getEntries().stream().filter((shown) -> shown.getId().equals(restored.getId()))
                    .findFirst().orElse(null);
            if (entry != null) {
                entry.getImages().setAll(restored.getImages());
                entry.setDeletedCost(restored.getDeletedCost());
            }
            else {
                entry = restored;
                chat.getEntries().add((int) chat.getEntries().stream()
                        .filter((other) -> order.compare(other, restored) <= 0).count(), entry);
            }
            chat.setDeletedCost(chatHistory.findChat(chat.getId()).map(Chat::getDeletedCost)
                    .orElse(chat.getDeletedCost()));

            /*  Setting the item again renders its card anew, also for an entry that got back a single image  */
            if (chat == currentChat && chatEntriesListView.getItems() != null) {
                ObservableList<ChatEntry> items = chatEntriesListView.getItems();
                int index = items.indexOf(entry);
                if (index >= 0)
                    items.set(index, entry);
                else
                    items.add((int) items.stream().filter((other) -> order.compare(other, restored) <= 0).count(),
                            entry);
                lastChatEntriesCount = chat.getEntries().size();
                updateChatHeader();
            }
            setStatus(I18nManager.getText(restored.getId().equals(id) ? "status.entry.restored"
                    : "status.image.restored"));
        } catch (Exception e) {
            logger.error("Failed to show restored images: {}", id, e);
        }
    }

    /** Removes chats that are gone from the database from the list; the open one is closed. */
    private void removeChats(java.util.List<String> chatIds) {
        if (currentChat != null && chatIds.contains(currentChat.getId())) {
            chatListView.getSelectionModel().clearSelection();
            currentChat = null;

            /*  An open group stays, it held no entry of a chat in the trash  */
            if (shownGroup == null) {
                chatEntriesListView.setItems(null);
                welcomeContainer.setVisible(true);
            }
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
                // Only update if this is the currently displayed chat and entry exists (an open group shows all chats)
                java.util.List<ChatEntry> shown = shownGroup != null ? chatEntriesListView.getItems()
                        : currentChat != null ? currentChat.getEntries() : null;
                if (shown != null && shown.contains(chatEntry)) {
                    // Find and update only the specific ChatEntryCard instead of reloading entire
                    // chat
                    updateSpecificChatEntryCard(chatEntry);

                } else {
                    // Entry not in current chat, no UI update needed
                }

                /*  A finished entry changes the image counts of the groups  */
                if (chatEntry.getStatus() != ChatEntry.Status.PENDING
                        && chatEntry.getStatus() != ChatEntry.Status.PROCESSING)
                    refreshGroups();
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
                // Only update if this image belongs to the currently displayed chat (an open group shows all chats)
                java.util.List<ChatEntry> shown = shownGroup != null ? chatEntriesListView.getItems()
                        : currentChat != null ? currentChat.getEntries() : null;
                if (shown != null) {
                    // Find the ChatEntry that contains this image
                    art.moodframe.model.ChatEntry parentEntry = shown.stream()
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
        if (wasScrolledToBottom)
            scrollToBottom("entry updated");

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
            /*  An open group shows the entry if it belongs to it, the chat takes it in any case  */
            if (shownGroup != null) {
                refreshGroups();
                if (shownGroup.holds(chatEntry)) {
                    chatEntriesListView.getItems().add(chatEntry);
                    wasScrolledToBottom = true;
                    scrollToBottom("new entry added");
                }
                return;
            }

            // Only add if this is the current chat
            if (currentChat != null && currentChat.equals(getCurrentChatFromEntry(chatEntry))) {
                if (chatEntriesListView.getItems() == null) {
                    chatEntriesListView.setItems(javafx.collections.FXCollections.observableArrayList());
                }

                // Hide welcome if visible
                welcomeContainer.setVisible(false);
                showEntries(true);

                // Add new entry to the ListView items
                chatEntriesListView.getItems().add(chatEntry);

                // Update chat header to reflect new message count and cost
                updateChatHeader();

                // Auto-scroll to bottom for new entries
                wasScrolledToBottom = true;
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
        /*  At position 1 the list lays its cells out from the end; scrollTo(last) misjudges cells not measured yet  */
        if (wasScrolledToBottom && chatEntriesListView != null
                && chatEntriesListView.lookup(".virtual-flow") instanceof VirtualFlow<?> flow)
            flow.setPosition(1.0);
    }

    /**
     * Whether the end of the last entry is in view, so the chat keeps it in view while images arrive.
     *
     * @return true if the end is in view or the list shows nothing yet
     */
    private boolean isChatAtEnd() {
        if (!(chatEntriesListView.lookup(".virtual-flow") instanceof VirtualFlow<?> flow))
            return true;
        IndexedCell<?> last = flow.getLastVisibleCell();
        return last == null || last.getIndex() == flow.getCellCount() - 1
                && last.getLayoutY() + last.getHeight() <= flow.getHeight() + 1.0;
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
     * Sets up the building block lists: style, atmosphere, light and camera from the blocks, the color from the
     * palettes. Each list starts with its actions "Edit…", "New…" and "Manage…" (see
     * {@link art.moodframe.ui.component.BlockChoice}), followed by "None" (the default) and the entries; the styles
     * are grouped into photo, art and layout.
     */
    private void setupBlockChoices() {
        java.util.Map<art.moodframe.model.PromptBlock.Type, ComboBox<art.moodframe.model.StylePresetItem>> fields =
                java.util.Map.of(art.moodframe.model.PromptBlock.Type.STYLE, stylePresetsComboBox,
                        art.moodframe.model.PromptBlock.Type.ATMOSPHERE, atmosphereComboBox,
                        art.moodframe.model.PromptBlock.Type.LIGHT, lightComboBox,
                        art.moodframe.model.PromptBlock.Type.CAMERA, cameraComboBox);
        fields.forEach((type, comboBox) -> {
            art.moodframe.ui.component.BlockChoice<art.moodframe.model.StylePresetItem> choice =
                    new art.moodframe.ui.component.BlockChoice<>(comboBox,
                            (name) -> new art.moodframe.model.StylePresetItem(name, "", false),
                            (item) -> item.getBlock() != null,
                            new art.moodframe.ui.component.BlockChoice.Actions<>() {
                                @Override
                                public void edit(art.moodframe.model.StylePresetItem entry) {
                                    editBlock(type, entry.getBlock());
                                }

                                @Override
                                public void create() {
                                    editBlock(type, null);
                                }

                                @Override
                                public void manage() {
                                    manageBlocks(type);
                                }
                            });
            comboBox.setCellFactory((listView) -> new art.moodframe.ui.component.StylePresetListCell(choice));
            comboBox.setButtonCell(new art.moodframe.ui.component.StylePresetListCell());
            blockChoices.put(type, choice);
            fillBlockChoice(type, null);
        });

        colorChoice = new art.moodframe.ui.component.BlockChoice<>(colorStyleComboBox,
                (name) -> new art.moodframe.model.ColorStyleItem(name, java.util.Collections.emptyList(), true),
                (item) -> !"None".equals(item.getName()),
                new art.moodframe.ui.component.BlockChoice.Actions<>() {
                    @Override
                    public void edit(art.moodframe.model.ColorStyleItem entry) {
                        editPalette(entry);
                    }

                    @Override
                    public void create() {
                        editPalette(null);
                    }

                    @Override
                    public void manage() {
                        managePalettes();
                    }
                });
        colorStyleComboBox.setCellFactory((listView) -> new art.moodframe.ui.component.ColorStyleListCell(colorChoice));
        colorStyleComboBox.setButtonCell(new art.moodframe.ui.component.ColorStyleListCell());
        fillColorChoice(null);
    }

    /**
     * Fills the list of a text block type with "None" and the blocks, the styles ordered by their groups.
     *
     * @param type the type of the blocks
     * @param name the name of the block to select, null for "None"
     */
    private void fillBlockChoice(art.moodframe.model.PromptBlock.Type type, String name) {
        List<art.moodframe.model.StylePresetItem> entries = new java.util.ArrayList<>();
        entries.add(new art.moodframe.model.StylePresetItem("None", "", false));
        art.moodframe.service.PromptBlockService service = art.moodframe.config.ServiceFactory.getInstance()
                .getService(art.moodframe.service.PromptBlockService.class);
        boolean grouped = type == art.moodframe.model.PromptBlock.Type.STYLE;
        if (service == null)
            logger.warn("PromptBlockService not available, {} list only offers None", type);
        else
            service.getAll(type).stream()
                    .map(art.moodframe.model.StylePresetItem::new)
                    .sorted(java.util.Comparator.comparingInt((art.moodframe.model.StylePresetItem item) ->
                            grouped ? STYLE_GROUPS.indexOf(styleGroupKey(item)) : 0))
                    .forEach(entries::add);
        blockChoices.get(type).setEntries(entries, grouped ? MainWindowController::styleGroupKey : null, name);
    }

    /*  The headers of the style groups: the two media photo and art, then layout  */
    private static final List<String> STYLE_GROUPS = List.of("block.group.photo", "block.group.art",
            "block.group.layout");

    /** The header of the group of a style; a style of the user without group goes by its photographic flag. */
    private static String styleGroupKey(art.moodframe.model.StylePresetItem item) {
        art.moodframe.model.PromptBlock block = item.getBlock();
        if (block == null)
            return null;
        return block.isLayout() ? "block.group.layout" : block.isPhotographic() ? "block.group.photo" : "block.group.art";
    }

    /**
     * Fills the color list with "None" and the palettes.
     *
     * @param name the name of the palette to select, null for "None"
     */
    private void fillColorChoice(String name) {
        List<art.moodframe.model.ColorStyleItem> entries = new java.util.ArrayList<>();
        entries.add(new art.moodframe.model.ColorStyleItem("None", java.util.Collections.emptyList(), true));
        art.moodframe.service.ColorStyleService service = art.moodframe.config.ServiceFactory.getInstance()
                .getService(art.moodframe.service.ColorStyleService.class);
        if (service == null)
            logger.warn("ColorStyleService not available, color list only offers None");
        else
            service.getAllStyles().forEach((palette) -> entries.add(palette.toColorStyleItem()));
        colorChoice.setEntries(entries, null, name);
    }

    /**
     * Loads all building block lists again, e.g. after an import or a restore from the trash; each keeps its
     * selection while its entry exists.
     */
    private void refreshBlockChoices() {
        blockChoices.forEach((type, choice) -> {
            ComboBox<art.moodframe.model.StylePresetItem> comboBox = blockComboBox(type);
            fillBlockChoice(type, comboBox.getValue() != null ? comboBox.getValue().getName() : null);
        });
        if (colorChoice != null)
            fillColorChoice(colorStyleComboBox.getValue() != null ? colorStyleComboBox.getValue().getName() : null);
    }

    private ComboBox<art.moodframe.model.StylePresetItem> blockComboBox(art.moodframe.model.PromptBlock.Type type) {
        return switch (type) {
            case STYLE -> stylePresetsComboBox;
            case ATMOSPHERE -> atmosphereComboBox;
            case LIGHT -> lightComboBox;
            case CAMERA -> cameraComboBox;
        };
    }

    /**
     * Creates or edits a text building block in its dialog and selects the stored block. A block of the user is
     * saved in place or as a new block, a standard block only as a new one, which keeps the group of its origin.
     * A camera has a dialog of its own (see {@link #editCamera}).
     *
     * @param type  the type of the block
     * @param block the block to edit, null for a new one
     */
    private String editBlock(art.moodframe.model.PromptBlock.Type type, art.moodframe.model.PromptBlock block) {
        art.moodframe.service.PromptBlockService service = art.moodframe.config.ServiceFactory.getInstance()
                .getService(art.moodframe.service.PromptBlockService.class);
        if (service == null)
            return null;
        if (type == art.moodframe.model.PromptBlock.Type.CAMERA)
            return editCamera(service, block);

        art.moodframe.ui.component.BlockEditDialog dialog = new art.moodframe.ui.component.BlockEditDialog(type, block);
        dialog.initOwner(activeWindow());
        String[] storedId = { null };
        dialog.showAndWait().ifPresent((result) -> {
            boolean inPlace = block != null && result.save() == art.moodframe.ui.component.BlockEditDialog.Save.SAVE;
            art.moodframe.model.PromptBlock stored = new art.moodframe.model.PromptBlock(
                    inPlace ? block.getId() : null, type, result.name(), result.text(),
                    block != null ? block.getGroup() : null, result.photographic());
            if (block != null) {
                stored.setParams(block.getParams());
                stored.setSortOrder(block.getSortOrder());
            }
            boolean saved = inPlace ? service.updateCustom(stored)
                    : result.save() == art.moodframe.ui.component.BlockEditDialog.Save.SAVE ? service.addCustom(stored)
                    : service.addCopy(stored);
            if (saved) {
                fillBlockChoice(type, stored.getName());
                storedId[0] = stored.getId();
            }
            else
                setStatus(I18nManager.getText("block.error.save", result.name()));
        });
        return storedId[0];
    }

    /**
     * Creates or edits a camera in its dialog of settings and selects the stored camera. A camera of the user is saved
     * in place or as a new one, a standard camera only as a new one.
     *
     * @param service the building blocks
     * @param block   the camera to edit, null for a new one
     */
    private String editCamera(art.moodframe.service.PromptBlockService service, art.moodframe.model.PromptBlock block) {
        art.moodframe.ui.component.CameraEditDialog dialog = new art.moodframe.ui.component.CameraEditDialog(block);
        dialog.initOwner(activeWindow());
        String[] storedId = { null };
        dialog.showAndWait().ifPresent((result) -> {
            boolean inPlace = block != null && result.save() == art.moodframe.ui.component.BlockEditDialog.Save.SAVE;
            art.moodframe.model.PromptBlock stored = art.moodframe.service.PromptBlockService.camera(
                    inPlace ? block.getId() : null, result.name(), result.settings());
            if (block != null)
                stored.setSortOrder(block.getSortOrder());
            boolean saved = inPlace ? service.updateCustom(stored)
                    : result.save() == art.moodframe.ui.component.BlockEditDialog.Save.SAVE ? service.addCustom(stored)
                    : service.addCopy(stored);
            if (saved) {
                fillBlockChoice(art.moodframe.model.PromptBlock.Type.CAMERA, stored.getName());
                storedId[0] = stored.getId();

                /*  a camera changed in place keeps its name, so the list reports no change of the selection  */
                updateUIState();
            }
            else
                setStatus(I18nManager.getText("block.error.save", result.name()));
        });
        return storedId[0];
    }

    /**
     * Creates or edits a palette in its dialog and selects the stored palette. A palette of the user is saved in
     * place or as a new one, a default palette only as a new one.
     *
     * @param item the palette to edit, null for a new one
     */
    private String editPalette(art.moodframe.model.ColorStyleItem item) {
        art.moodframe.service.ColorStyleService service = art.moodframe.config.ServiceFactory.getInstance()
                .getService(art.moodframe.service.ColorStyleService.class);
        if (service == null)
            return null;

        art.moodframe.model.ColorStyle palette = item != null ? service.getStyleByName(item.getName()) : null;
        art.moodframe.ui.component.ColorStyleEditDialog dialog =
                new art.moodframe.ui.component.ColorStyleEditDialog(palette);
        dialog.initOwner(activeWindow());
        String[] storedId = { null };
        dialog.showAndWait().ifPresent((result) -> {
            art.moodframe.model.ColorStyle stored = new art.moodframe.model.ColorStyle(result.name(), result.colors(),
                    false);
            boolean save = result.save() == art.moodframe.ui.component.BlockEditDialog.Save.SAVE;
            boolean saved = palette != null && save ? service.updateCustomStyle(palette.getName(), stored)
                    : save ? service.addCustomStyle(stored) : service.addCopy(stored);
            if (saved) {
                fillColorChoice(stored.getName());
                art.moodframe.model.ColorStyle now = service.getStyleByName(stored.getName());
                storedId[0] = now != null ? now.getId() : "";
            }
            else
                setStatus(I18nManager.getText("block.error.save", result.name()));
        });
        return storedId[0];
    }

    /*  the focused window of the app (a manage dialog, if open), so a nested dialog opens on top of it  */
    private javafx.stage.Window activeWindow() {
        return javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isFocused).findFirst()
                .orElse(mainBorderPane.getScene().getWindow());
    }

    /**
     * Opens the "Manage…" dialog for the text blocks of the user of a type and loads the lists again afterwards.
     *
     * @param type the type of the blocks
     */
    private void manageBlocks(art.moodframe.model.PromptBlock.Type type) {
        art.moodframe.service.PromptBlockService service = art.moodframe.config.ServiceFactory.getInstance()
                .getService(art.moodframe.service.PromptBlockService.class);
        if (service == null)
            return;

        String typeKey = type.name().toLowerCase(java.util.Locale.ROOT);
        new art.moodframe.ui.dialog.BlockManageDialog<>(mainBorderPane.getScene().getWindow(),
                I18nManager.getText("block.manage.title." + typeKey), "moodframe-" + typeKey + ".json",
                new art.moodframe.ui.dialog.BlockManageDialog.Library<art.moodframe.model.StylePresetItem>() {
                    @Override
                    public List<art.moodframe.model.StylePresetItem> list() {
                        return service.getCustom(type).stream().map(art.moodframe.model.StylePresetItem::new).toList();
                    }

                    @Override
                    public String id(art.moodframe.model.StylePresetItem entry) {
                        return entry.getBlock().getId();
                    }

                    @Override
                    public String name(art.moodframe.model.StylePresetItem entry) {
                        return entry.getName();
                    }

                    @Override
                    public boolean delete(art.moodframe.model.StylePresetItem entry) {
                        return service.deleteCustom(id(entry));
                    }

                    @Override
                    public boolean reorder(List<art.moodframe.model.StylePresetItem> entries) {
                        return service.reorderCustom(type, entries.stream().map(this::id).toList());
                    }

                    @Override
                    public void exportTo(File file) throws IOException {
                        service.exportCustom(type, file);
                    }

                    @Override
                    public int importFrom(File file) throws IOException {
                        return service.importCustom(type, file);
                    }

                    @Override
                    public String create() {
                        return editBlock(type, null);
                    }

                    @Override
                    public String edit(art.moodframe.model.StylePresetItem entry) {
                        return editBlock(type, entry.getBlock());
                    }

                    @Override
                    public boolean editable() {
                        return true;
                    }
                }, (listView) -> new art.moodframe.ui.component.StylePresetListCell()).showAndWait();
        refreshBlockChoices();
    }

    /**
     * Opens the "Manage…" dialog for the palettes of the user and loads the lists again afterwards.
     */
    private void managePalettes() {
        art.moodframe.service.ColorStyleService service = art.moodframe.config.ServiceFactory.getInstance()
                .getService(art.moodframe.service.ColorStyleService.class);
        if (service == null)
            return;

        new art.moodframe.ui.dialog.BlockManageDialog<>(mainBorderPane.getScene().getWindow(),
                I18nManager.getText("block.manage.title.palette"), "moodframe-palettes.json",
                new art.moodframe.ui.dialog.BlockManageDialog.Library<art.moodframe.model.ColorStyleItem>() {
                    @Override
                    public List<art.moodframe.model.ColorStyleItem> list() {
                        return service.getCustomStyles().stream()
                                .map(art.moodframe.model.ColorStyle::toColorStyleItem).toList();
                    }

                    @Override
                    public String id(art.moodframe.model.ColorStyleItem entry) {
                        art.moodframe.model.ColorStyle palette = service.getStyleByName(entry.getName());
                        return palette != null ? palette.getId() : "";
                    }

                    @Override
                    public String name(art.moodframe.model.ColorStyleItem entry) {
                        return entry.getName();
                    }

                    @Override
                    public boolean delete(art.moodframe.model.ColorStyleItem entry) {
                        return service.deleteCustomStyle(entry.getName());
                    }

                    @Override
                    public boolean reorder(List<art.moodframe.model.ColorStyleItem> entries) {
                        return service.reorderCustomStyles(entries.stream().map(this::id).toList());
                    }

                    @Override
                    public void exportTo(File file) throws IOException {
                        if (!service.exportStyles(file))
                            throw new IOException("Failed to write " + file);
                    }

                    @Override
                    public int importFrom(File file) {
                        return service.importStyles(file, true);
                    }

                    @Override
                    public String create() {
                        return editPalette(null);
                    }

                    @Override
                    public String edit(art.moodframe.model.ColorStyleItem entry) {
                        return editPalette(entry);
                    }

                    @Override
                    public boolean editable() {
                        return true;
                    }
                }, (listView) -> new art.moodframe.ui.component.ColorStyleListCell()).showAndWait();
        refreshBlockChoices();
    }

    /**
     * Sets up the locks of the building block fields and the "Apply mood" switch, both remembered. A locked field
     * keeps its choice when a mood or the brainstorm sets the fields; the lock shows while the pointer is over the
     * field, and always once locked (see main-window.css).
     */
    private void setupBlockLocks() {
        java.util.Map<ToggleButton, String> locks = java.util.Map.of(atmosphereLockButton, "atmosphere",
                styleLockButton, "style", colorLockButton, "palette", lightLockButton, "light", cameraLockButton,
                "camera");
        locks.forEach((lock, field) -> {
            lock.selectedProperty().addListener((obs, oldVal, newVal) -> {
                ((FontIcon) lock.getGraphic()).setIconLiteral(newVal ? "mdi2l-lock" : "mdi2l-lock-open-variant-outline");
                if (config != null)
                    config.setBlockLocked(field, newVal);
            });
            lock.setSelected(config != null && config.isBlockLocked(field));
        });

        moodApplyCheckBox.setSelected(config != null && config.isMoodAppliedToReference());
        moodApplyCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (config != null)
                config.setMoodAppliedToReference(newVal);
        });
    }

    /**
     * Sets up the mood selection: "No mood" and the moods; the actions sit as icons in the header of the group.
     * Choosing a mood sets the fields that are not locked; the mood shows as changed while a field differs from it. A
     * remembered former look style (1.x) is chosen as the standard mood of the same name.
     */
    private void setupMoods() {
        moodService = art.moodframe.config.ServiceFactory.getInstance().getService(MoodService.class);
        if (moodService == null || moodComboBox == null) {
            logger.warn("MoodService not available, no mood selection");
            return;
        }

        moodComboBox.setCellFactory((listView) -> new MoodListCell(this::isMoodIncomplete, null));
        moodComboBox.setButtonCell(new MoodListCell(this::isMoodIncomplete, this::isMoodChanged));
        moodComboBox.valueProperty().addListener((obs, oldVal, newVal) -> onMoodChoice(newVal));
        for (javafx.scene.control.ComboBox<?> field : List.of(atmosphereComboBox, stylePresetsComboBox,
                colorStyleComboBox, lightComboBox, cameraComboBox))
            field.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (!updatingMood)
                    updateMoodState();
            });

        Mood legacyMood = moodService.findForLegacyStyle(config != null ? config.getDefaultStyle() : null);
        reloadMoods(config != null ? config.getSelectedMood() : "");
        if (legacyMood != null) {
            chooseMood(legacyMood);

            /*  The former look style is taken over once, also while the style field is locked  */
            art.moodframe.model.StylePresetItem style = stylePresetsComboBox.getValue();
            config.setDefaultStyle(style != null ? style.getName() : "None");
        }
        else
            updateMoodState();

        /*  Moods that lost their last block while the window was closed, for example by the automatic cleanup  */
        Platform.runLater(this::askToDeleteEmptiedMoods);
    }

    /** The mood shown as chosen, null for "No mood". */
    private Mood chosenMood() {
        MoodChoice choice = moodComboBox != null ? moodComboBox.getValue() : null;
        return choice != null ? choice.mood() : null;
    }

    private boolean isMoodIncomplete(Mood mood) {
        return !moodService.trashedBlocks(mood).isEmpty();
    }

    /** "Save" is not available for a standard mood, which can only be saved as a new one. */
    private boolean isMoodSaveLocked() {
        Mood mood = chosenMood();
        return mood != null && mood.isDefault();
    }

    /** Lists "No mood" and the moods, and shows the mood with this id as chosen, without applying it. */
    private void reloadMoods(String selectId) {
        List<MoodChoice> items = MoodChoice.listOf(moodService.getAll());

        updatingMood = true;
        moodComboBox.getItems().setAll(items);
        moodComboBox.setValue(items.stream().filter((item) -> item.mood() != null && item.mood().getId().equals(selectId))
                .findFirst().orElse(items.get(0)));
        updatingMood = false;
    }

    /** Lists the moods again, keeping the choice, for example once a block or a mood left the trash. */
    private void refreshMoods() {
        if (moodService == null)
            return;
        Mood chosen = chosenMood();
        reloadMoods(chosen != null ? chosen.getId() : "");
        updateMoodState();
        refreshGroups();
    }

    /** Applies a chosen mood. */
    private void onMoodChoice(MoodChoice newVal) {
        if (!updatingMood && newVal != null)
            chooseMood(newVal.mood());
    }

    /** Sets the fields that are not locked to the blocks of a mood, null for "No mood", and shows it unchanged. */
    private void chooseMood(Mood mood) {
        chooseMood(mood, mood);
    }

    /**
     * Sets the fields that are not locked to the blocks of a combination and shows a mood as chosen, null for
     * "No mood"; the mood shows as changed if the fields then differ from it.
     */
    private void chooseMood(Mood combination, Mood mood) {
        java.util.Map<Mood.Field, String> fields = moodService.apply(combination, currentMoodFields(),
                lockedMoodFields());

        art.moodframe.model.ColorStyle palette = colorStyleService().getStyleById(fields.get(Mood.Field.COLOR));
        String paletteName = palette != null ? palette.getName() : "None";

        updatingMood = true;
        selectBlock(art.moodframe.model.PromptBlock.Type.ATMOSPHERE, fields.get(Mood.Field.ATMOSPHERE));
        selectBlock(art.moodframe.model.PromptBlock.Type.STYLE, fields.get(Mood.Field.STYLE));
        selectBlock(art.moodframe.model.PromptBlock.Type.LIGHT, fields.get(Mood.Field.LIGHT));
        selectBlock(art.moodframe.model.PromptBlock.Type.CAMERA, fields.get(Mood.Field.CAMERA));

        /*  only entries: the actions on top of the list ("Edit…", "New…", "Manage…") would open their dialogs  */
        colorStyleComboBox.getItems().stream().filter((item) -> colorChoice.isEntry(item)
                && item.getName().equals(paletteName)).findFirst()
                .or(() -> colorStyleComboBox.getItems().stream().filter(colorChoice::isEntry).findFirst())
                .ifPresent(colorStyleComboBox::setValue);
        moodComboBox.getItems().stream().filter((item) -> mood == null ? item.kind() == MoodChoice.Kind.NONE
                : item.mood() != null && item.mood().getId().equals(mood.getId()))
                .findFirst().ifPresent(moodComboBox::setValue);
        updatingMood = false;
        updateMoodState();
    }

    /**
     * Takes over the mood and the building blocks of a chat entry into the fields that are not locked. A block that is
     * not in use anymore can be created anew from its copy at the entry. With {@code save}, the blocks are stored as a
     * new mood first, under a name the user enters.
     *
     * @param entry the chat entry
     * @param save  whether to save the blocks as a new mood
     */
    private void takeOverEntryMood(ChatEntry entry, boolean save) {
        if (moodService == null)
            return;

        /*  An entry from before 2.0 with a former look style takes over the standard mood of its name  */
        EntryMood copy = entry.getMood();
        Mood combination = copy != null ? new Mood(copy.moodId(), copy.moodName())
                : moodService.findForLegacyStyle(entry.getStyle());
        if (combination == null)
            return;
        if (copy != null) {
            List<Mood.Field> missing = moodService.missingBlocks(copy);
            Boolean recreate = missing.isEmpty() ? Boolean.FALSE : askToRecreateBlocks(copy, missing);
            if (recreate == null)
                return;
            moodService.references(copy, recreate).forEach(combination::setReference);
            if (recreate)
                refreshBlockChoices();
        }

        /*  A combination of its own shows the mood it started from as chosen and changed  */
        Mood mood = moodService.find(copy != null && copy.moodId() == null ? copy.baseId() : combination.getId());
        if (save) {
            String name = art.moodframe.ui.dialog.MoodManageDialog.askName(mainBorderPane.getScene().getWindow(),
                    moodService, I18nManager.getText("mood.name.new"),
                    combination.getName() != null ? moodService.freeName(combination.getName()) : "", null);
            if (name == null)
                return;
            mood = new Mood(null, name);
            combination.getReferences().forEach(mood::setReference);
            if (!moodService.add(mood)) {
                showErrorMessage(I18nManager.getText("error.mood.save"));
                return;
            }
            reloadMoods(mood.getId());
            combination = mood;
        }
        chooseMood(combination, mood);
        setStatus(save ? I18nManager.getText("status.mood.saved", mood.getName())
                : I18nManager.getText("status.entry.mood"));
    }

    /**
     * Takes over prompt, model, frame and mood of a chat entry ("Reuse"), or one of them from the menu of the entry. The
     * model comes first, as it decides the aspect ratios and resolutions the frame can take.
     *
     * @param entry the chat entry
     * @param part  what to take over
     */
    private void reuseEntry(ChatEntry entry, ChatEntryCard.Reuse part) {
        boolean all = part == ChatEntryCard.Reuse.ALL;
        if (all || part == ChatEntryCard.Reuse.PROMPT)
            promptTextArea.setText(entry.getUserPrompt());
        if (all)
            reuseModel(entry);
        if (all || part == ChatEntryCard.Reuse.FRAME)
            reuseFrame(entry);
        if (all || part == ChatEntryCard.Reuse.MOOD)
            reuseMood(entry);
        if (all)
            setStatus(I18nManager.getText("status.entry.reused"));
        promptTextArea.requestFocus();
    }

    /** Chooses the model of a chat entry, as long as it is offered. */
    private void reuseModel(ChatEntry entry) {
        art.moodframe.model.GenerationModel model = art.moodframe.model.GenerationModel.fromDisplayName(entry.getModel());
        String name = model != null ? model.getLocalizedDisplayName() : entry.getModel();
        if (name != null && modelComboBox.getItems().contains(name))
            modelComboBox.setValue(name);
    }

    /**
     * Sets aspect ratio and resolution to the targets of a chat entry and shows its frame as chosen; a setting of its
     * own shows the frame it started from as changed. An entry from before the frames were copied takes the aspect
     * ratio of its model.
     */
    private void reuseFrame(ChatEntry entry) {
        if (frameService == null)
            return;
        EntryFrame copy = entry.getFrame();
        String aspectRatio = copy != null ? copy.aspectRatio() : entry.getSize();
        String resolution = copy != null ? copy.resolution() : null;
        Frame frame = copy != null ? frameService.find(copy.frameId() != null ? copy.frameId() : copy.baseId()) : null;

        updatingFrame = true;
        if (aspectRatio != null && aspectRatioComboBox.getItems().contains(aspectRatio))
            aspectRatioComboBox.setValue(aspectRatio);
        if (resolution != null && resolutionChoiceBox.getItems().contains(resolution))
            resolutionChoiceBox.setValue(resolution);
        frameComboBox.getItems().stream().filter((item) -> frame == null ? item.kind() == FrameChoice.Kind.NONE
                : item.frame() != null && item.frame().getId().equals(frame.getId()))
                .findFirst().ifPresent(frameComboBox::setValue);
        updatingFrame = false;
        updateFrameState();
        setStatus(I18nManager.getText("status.entry.frame"));
    }

    /**
     * Saves aspect ratio and resolution of a chat entry as a new frame under a name entered and chooses it, as "Save as
     * mood..." does with the mood of an entry.
     *
     * @param entry the chat entry
     */
    private void saveEntryFrame(ChatEntry entry) {
        EntryFrame copy = entry.getFrame();
        if (frameService == null || copy == null)
            return;
        String name = copy.frameName() != null ? copy.frameName() : copy.baseName();
        name = art.moodframe.ui.dialog.FrameManageDialog.askName(mainBorderPane.getScene().getWindow(), frameService,
                I18nManager.getText("frame.name.new"), name != null ? frameService.freeName(name) : "", null);
        if (name == null)
            return;
        Frame frame = new Frame(null, name);
        frame.setAspectRatio(copy.aspectRatio());
        frame.setResolution(copy.resolution());
        if (!frameService.add(frame)) {
            showErrorMessage(I18nManager.getText("error.frame.save"));
            return;
        }
        reloadFrames(frame.getId());
        chooseFrame(frame);
        setStatus(I18nManager.getText("status.frame.saved", frame.getName()));
    }

    /** Takes over the mood of a chat entry; an entry without building blocks sets "No mood". */
    private void reuseMood(ChatEntry entry) {
        if (moodService == null)
            return;
        if (entry.getMood() != null || moodService.findForLegacyStyle(entry.getStyle()) != null)
            takeOverEntryMood(entry, false);
        else
            chooseMood(null);
    }

    /**
     * Asks whether to create the blocks of a chat entry that are not in use anymore anew from their copies.
     *
     * @return true to create them, false to leave their fields empty, null to cancel
     */
    private Boolean askToRecreateBlocks(EntryMood copy, List<Mood.Field> missing) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(mainBorderPane.getScene().getWindow());
        alert.setTitle(I18nManager.getText("entry.mood.missing.title"));
        alert.setHeaderText(I18nManager.getText("entry.mood.missing.header",
                String.join(", ", missing.stream().map((field) -> copy.blocks().get(field).name()).toList())));
        alert.setContentText(I18nManager.getText("entry.mood.missing.content"));
        ButtonType recreate = new ButtonType(I18nManager.getText("entry.mood.missing.recreate"),
                ButtonBar.ButtonData.YES);
        ButtonType skip = new ButtonType(I18nManager.getText("entry.mood.missing.skip"), ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType(I18nManager.getText("button.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(recreate, skip, cancel);
        ButtonType answer = alert.showAndWait().orElse(cancel);
        return answer == cancel ? null : answer == recreate;
    }

    /**
     * Selects the item of a block in a field, "None" (the first entry) if the block is not listed. Only entries are
     * chosen: the actions on top of the list would open their dialogs.
     */
    private void selectBlock(art.moodframe.model.PromptBlock.Type type, String blockId) {
        javafx.scene.control.ComboBox<art.moodframe.model.StylePresetItem> comboBox = blockComboBox(type);
        art.moodframe.ui.component.BlockChoice<art.moodframe.model.StylePresetItem> choice = blockChoices.get(type);
        comboBox.getItems().stream().filter((item) -> choice.isEntry(item) && item.getBlock() != null
                && item.getBlock().getId().equals(blockId)).findFirst()
                .or(() -> comboBox.getItems().stream().filter(choice::isEntry).findFirst())
                .ifPresent(comboBox::setValue);
    }

    /** The block ids of the fields; an empty field is missing. */
    private java.util.Map<Mood.Field, String> currentMoodFields() {
        java.util.Map<Mood.Field, String> fields = new java.util.EnumMap<>(Mood.Field.class);
        java.util.Map.of(Mood.Field.ATMOSPHERE, atmosphereComboBox, Mood.Field.STYLE, stylePresetsComboBox,
                Mood.Field.LIGHT, lightComboBox, Mood.Field.CAMERA, cameraComboBox).forEach((field, comboBox) -> {
            art.moodframe.model.StylePresetItem item = comboBox.getValue();
            if (item != null && item.getBlock() != null)
                fields.put(field, item.getBlock().getId());
        });
        art.moodframe.model.ColorStyleItem colorItem = colorStyleComboBox.getValue();
        art.moodframe.model.ColorStyle palette = colorItem != null && !"None".equals(colorItem.getName())
                ? colorStyleService().getStyleByName(colorItem.getName()) : null;
        if (palette != null)
            fields.put(Mood.Field.COLOR, palette.getId());
        return fields;
    }

    /** The fields whose lock is closed. */
    private Set<Mood.Field> lockedMoodFields() {
        Set<Mood.Field> locked = java.util.EnumSet.noneOf(Mood.Field.class);
        java.util.Map.of(Mood.Field.ATMOSPHERE, atmosphereLockButton, Mood.Field.STYLE, styleLockButton,
                Mood.Field.COLOR, colorLockButton, Mood.Field.LIGHT, lightLockButton, Mood.Field.CAMERA,
                cameraLockButton).forEach((field, lock) -> {
            if (lock.isSelected())
                locked.add(field);
        });
        return locked;
    }

    private art.moodframe.service.ColorStyleService colorStyleService() {
        return art.moodframe.config.ServiceFactory.getInstance().getService(art.moodframe.service.ColorStyleService.class);
    }

    /** Whether a field differs from the chosen mood, compared anew each time; "No mood" is never changed. */
    private boolean isMoodChanged() {
        return moodService != null && moodService.isChanged(chosenMood(), currentMoodFields());
    }

    /**
     * Shows the chosen mood as changed while a field differs from it, remembers the choice, shows the warning of a
     * mood with a block in the trash and locks "Save" for a standard mood.
     */
    private void updateMoodState() {
        if (moodService == null)
            return;
        Mood mood = chosenMood();
        if (config != null)
            config.setSelectedMood(mood != null ? mood.getId() : "", isMoodChanged());

        boolean incomplete = mood != null && isMoodIncomplete(mood);
        moodIncompleteButton.setVisible(incomplete);
        moodIncompleteButton.setManaged(incomplete);
        moodIncompleteButton.setTooltip(incomplete ? new Tooltip(I18nManager.getText("params.mood.incomplete")) : null);
        saveMoodButton.setDisable(moodComboBox.isDisable() || isMoodSaveLocked());
        ((MoodListCell) moodComboBox.getButtonCell()).refresh();
    }

    /** Saves the fields over the chosen mood of the user, or as a new mood under a name the user enters. */
    private void saveMood(boolean asNew) {
        Mood chosen = chosenMood();
        Mood mood;
        if (!asNew && chosen != null && !chosen.isDefault())
            mood = chosen;
        else {
            String name = art.moodframe.ui.dialog.MoodManageDialog.askName(mainBorderPane.getScene().getWindow(),
                    moodService, I18nManager.getText("mood.name.new"),
                    chosen != null ? moodService.freeName(chosen.getName()) : "", null);
            if (name == null)
                return;
            mood = new Mood(null, name);
        }
        java.util.Map<Mood.Field, String> references = moodService.capture(chosen, currentMoodFields());
        for (Mood.Field field : Mood.Field.values())
            mood.setReference(field, references.get(field));

        if (!(mood.getId() == null ? moodService.add(mood) : moodService.update(mood))) {
            showErrorMessage(I18nManager.getText("error.mood.save"));
            return;
        }
        reloadMoods(mood.getId());
        updateMoodState();
        setStatus(I18nManager.getText("status.mood.saved", mood.getName()));
    }

    /** Opens the window for the moods of the user and lists them again afterwards. */
    private void manageMoods() {
        new art.moodframe.ui.dialog.MoodManageDialog(mainBorderPane.getScene().getWindow(), moodService).showAndWait();
        refreshMoods();
    }

    @FXML
    private void onSaveMoodClicked() {
        if (moodService != null)
            saveMood(false);
    }

    @FXML
    private void onSaveMoodAsClicked() {
        if (moodService != null)
            saveMood(true);
    }

    @FXML
    private void onManageMoodsClicked() {
        if (moodService != null)
            manageMoods();
    }

    @FXML
    private void onMoodFromImageClicked() {
        openMoodFromImage(null);
    }

    /**
     * Opens the dialog "Mood from image", which finds the building blocks of an image for the fields of the panel.
     *
     * @param imageFile the image to analyze at once, null to let the user give one
     */
    private void openMoodFromImage(File imageFile) {
        art.moodframe.service.PromptBlockService blockService = art.moodframe.config.ServiceFactory.getInstance()
                .getService(art.moodframe.service.PromptBlockService.class);
        art.moodframe.service.ColorStyleService colorService = colorStyleService();
        if (blockService == null || colorService == null)
            return;

        art.moodframe.service.BrainstormContext context = new art.moodframe.service.BrainstormContext(
                blockService.getAll(art.moodframe.model.PromptBlock.Type.STYLE),
                blockService.getAll(art.moodframe.model.PromptBlock.Type.ATMOSPHERE),
                blockService.getAll(art.moodframe.model.PromptBlock.Type.LIGHT),
                blockService.getAll(art.moodframe.model.PromptBlock.Type.CAMERA), colorService.getAllStyles(),
                List.of(), List.of(), "", List.of(), lockedMoodFields());
        new art.moodframe.ui.dialog.MoodImageDialog(mainBorderPane.getScene().getWindow(),
                art.moodframe.service.MoodAnalysisService.create(config), context, imageFile,
                this::applyMoodFromImage).show();
    }

    /**
     * Sets the building blocks adopted in the dialog "Mood from image" into the fields that are not locked, a new block
     * into the list of the user first, and saves the fields as a new mood if asked to.
     */
    private void applyMoodFromImage(art.moodframe.ui.dialog.MoodImageDialog.Result result) {
        Set<Mood.Field> locked = lockedMoodFields();
        result.blocks().forEach((field, block) -> {
            if (!locked.contains(field))
                adoptBlock(field, block);
        });
        if (result.saveMood() && moodService != null)
            saveMood(true);
    }

    /** Shows the first block of the chosen mood that lies in the trash. */
    @FXML
    private void onMoodIncompleteClicked() {
        Mood mood = chosenMood();
        List<String> trashed = mood != null ? moodService.trashedBlocks(mood) : List.of();
        if (!trashed.isEmpty())
            art.moodframe.ui.dialog.TrashWindow.open(mainBorderPane.getScene().getWindow(),
                    art.moodframe.service.TrashGroup.Kind.BLOCKS, trashed.get(0));
    }

    /** Asks for every mood that lost its last block whether to move it to the trash. */
    private void askToDeleteEmptiedMoods() {
        if (moodService == null)
            return;
        List<Mood> emptied = moodService.takeEmptied();
        for (Mood mood : emptied) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.initOwner(mainBorderPane.getScene() != null ? mainBorderPane.getScene().getWindow() : null);
            alert.setTitle(I18nManager.getText("mood.empty.title"));
            alert.setHeaderText(I18nManager.getText("mood.empty.header", mood.getName()));
            alert.setContentText(I18nManager.getText("mood.empty.content"));
            ButtonType delete = new ButtonType(I18nManager.getText("mood.empty.delete"), ButtonBar.ButtonData.YES);
            ButtonType keep = new ButtonType(I18nManager.getText("mood.empty.keep"), ButtonBar.ButtonData.NO);
            alert.getButtonTypes().setAll(delete, keep);
            if (alert.showAndWait().orElse(keep) == delete)
                moodService.moveToTrash(mood.getId());
        }
        if (!emptied.isEmpty())
            refreshMoods();
    }

    /*  the fold arrow and the paddings beside the header of a group of the right panel  */
    private static final double PANEL_GROUP_HEADER_INSETS = 28.0;

    /**
     * Folds the groups of the right panel (Mood, Frame, Generation) as the user left them. The header of a group spans
     * its width, so that its actions sit on the right; its content starts on the inset of the boxes, behind its 1 px
     * border.
     */
    private void setupPanelGroups() {
        java.util.Map<String, TitledPane> groups = java.util.Map.of("mood", moodGroupPane, "frame", frameGroupPane,
                "generation", generationGroupPane);
        groups.forEach((group, pane) -> {
            if (config != null)
                pane.setExpanded(config.isPanelGroupExpanded(group));
            pane.expandedProperty().addListener((obs, oldVal, newVal) -> {
                if (config != null)
                    config.setPanelGroupExpanded(group, newVal);
            });
            ((javafx.scene.layout.Region) pane.getGraphic()).prefWidthProperty()
                    .bind(pane.widthProperty().subtract(PANEL_GROUP_HEADER_INSETS));
            ((javafx.scene.layout.Region) pane.getContent()).setPadding(
                    new javafx.geometry.Insets(0, 0, 0, BoxInset.LEFT - 1.0));
        });
    }

    /**
     * Sets up the frame selection: "No frame" and the frames; the actions sit as icons in the header of the group.
     * Choosing a frame sets its target aspect ratio and resolution, of which the model takes the closest ones it can
     * do; either changed afterwards shows the frame as changed until it is saved or chosen again.
     */
    private void setupFrames() {
        frameService = art.moodframe.config.ServiceFactory.getInstance().getService(FrameService.class);
        if (frameService == null || frameComboBox == null) {
            logger.warn("FrameService not available, no frame selection");
            return;
        }

        frameComboBox.setCellFactory((listView) -> new FrameListCell(null));
        frameComboBox.setButtonCell(new FrameListCell(() -> frameChanged));
        frameComboBox.valueProperty().addListener((obs, oldVal, newVal) -> onFrameChoice(newVal));
        aspectRatioComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingFrame)
                updateFrameState();
        });
        resolutionChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingFrame)
                updateFrameState();
        });

        reloadFrames(config != null ? config.getSelectedFrame() : "");
        updateFrameState();
    }

    /** The frame shown as chosen, null for "No frame". */
    private Frame chosenFrame() {
        FrameChoice choice = frameComboBox != null ? frameComboBox.getValue() : null;
        return choice != null ? choice.frame() : null;
    }

    /** "Save" is not available for a standard frame, which can only be saved as a new one. */
    private boolean isFrameSaveLocked() {
        Frame frame = chosenFrame();
        return frame != null && frame.isDefault();
    }

    /** Lists "No frame" and the frames, and shows the frame with this id as chosen, without applying it. */
    private void reloadFrames(String selectId) {
        List<FrameChoice> items = FrameChoice.listOf(frameService.getAll());

        updatingFrame = true;
        frameComboBox.getItems().setAll(items);
        frameComboBox.setValue(items.stream().filter((item) -> item.frame() != null
                && item.frame().getId().equals(selectId)).findFirst().orElse(items.get(0)));
        updatingFrame = false;
    }

    /** Lists the frames again, keeping the choice, for example once a frame left the trash or was renamed. */
    private void refreshFrames() {
        if (frameService == null)
            return;
        Frame chosen = chosenFrame();
        reloadFrames(chosen != null ? chosen.getId() : "");
        updateFrameState();
        refreshGroups();
    }

    /** Applies a chosen frame. */
    private void onFrameChoice(FrameChoice newVal) {
        if (!updatingFrame && newVal != null)
            chooseFrame(newVal.frame());
    }

    /**
     * Sets aspect ratio and resolution to the targets of a frame and shows it unchanged; a frame without resolution
     * leaves the resolution, "No frame" (null) both as they are.
     */
    private void chooseFrame(Frame frame) {
        updatingFrame = true;
        if (frame != null) {
            aspectRatioComboBox.setValue(frame.getAspectRatio());
            if (frame.getResolution() != null)
                resolutionChoiceBox.setValue(frame.getResolution());
        }
        frameComboBox.getItems().stream().filter((item) -> frame == null ? item.kind() == FrameChoice.Kind.NONE
                : item.frame() != null && item.frame().getId().equals(frame.getId()))
                .findFirst().ifPresent(frameComboBox::setValue);
        updatingFrame = false;
        updateFrameState();
    }

    /**
     * Marks the chosen frame as changed while aspect ratio or resolution differ from it, remembers the choice and locks
     * "Save" for a standard frame.
     */
    private void updateFrameState() {
        if (frameService == null)
            return;
        Frame frame = chosenFrame();
        frameChanged = frame != null && (!frame.getAspectRatio().equals(aspectRatioComboBox.getValue())
                || frame.getResolution() != null && !frame.getResolution().equals(resolutionChoiceBox.getValue()));
        String id = frame != null ? frame.getId() : "";
        if (config != null && !id.equals(config.getSelectedFrame()))
            config.setSelectedFrame(id);
        saveFrameButton.setDisable(frameComboBox.isDisable() || isFrameSaveLocked());
        ((FrameListCell) frameComboBox.getButtonCell()).refresh();
    }

    /** Saves aspect ratio and resolution over the chosen frame of the user, or as a new frame under a name entered. */
    private void saveFrame(boolean asNew) {
        Frame chosen = chosenFrame();
        Frame frame;
        if (!asNew && chosen != null && !chosen.isDefault())
            frame = chosen;
        else {
            String name = art.moodframe.ui.dialog.FrameManageDialog.askName(mainBorderPane.getScene().getWindow(),
                    frameService, I18nManager.getText("frame.name.new"),
                    chosen != null ? frameService.freeName(chosen.getName()) : "", null);
            if (name == null)
                return;
            frame = new Frame(null, name);
        }
        frame.setAspectRatio(aspectRatioComboBox.getValue());
        frame.setResolution(resolutionChoiceBox.getValue());

        if (!(frame.getId() == null ? frameService.add(frame) : frameService.update(frame))) {
            showErrorMessage(I18nManager.getText("error.frame.save"));
            return;
        }
        reloadFrames(frame.getId());
        updateFrameState();
        setStatus(I18nManager.getText("status.frame.saved", frame.getName()));
    }

    /** Opens the window for the frames of the user and lists them again afterwards. */
    private void manageFrames() {
        new art.moodframe.ui.dialog.FrameManageDialog(mainBorderPane.getScene().getWindow(), frameService).showAndWait();
        refreshFrames();
    }

    @FXML
    private void onSaveFrameClicked() {
        if (frameService != null)
            saveFrame(false);
    }

    @FXML
    private void onSaveFrameAsClicked() {
        if (frameService != null)
            saveFrame(true);
    }

    @FXML
    private void onManageFramesClicked() {
        if (frameService != null)
            manageFrames();
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

                // Load the target resolution; before there was one, the one closest to the resolution of the model
                String savedResolution = config.getDefaultResolution();
                resolutionChoiceBox.setValue(savedResolution != null ? savedResolution
                        : art.moodframe.model.ModelFamilies.nearest(art.moodframe.model.ModelFamilies.resolutionOf(
                                modelComboBox.getValue()), art.moodframe.model.ModelFamilies.RESOLUTIONS));

                // Load aspect ratio selection
                String savedAspectRatio = config.getDefaultAspectRatio();
                aspectRatioComboBox.setValue(savedAspectRatio);

                // Load safety tolerance value
                int savedSafetyTolerance = config.getDefaultSafetyTolerance();
                safetyToleranceSlider.setValue(savedSafetyTolerance);

                // Load image count selection
                int savedImageCount = config.getDefaultImageCount();
                imageCountSlider.setValue(savedImageCount);

                // Load style selection; a merged former style resolves to the one it went into
                art.moodframe.service.PromptBlockService styleService = art.moodframe.config.ServiceFactory.getInstance()
                        .getService(art.moodframe.service.PromptBlockService.class);
                art.moodframe.model.PromptBlock savedBlock = styleService != null
                        ? styleService.find(art.moodframe.model.PromptBlock.Type.STYLE, config.getDefaultStyle()) : null;
                String savedStyle = savedBlock != null ? savedBlock.getName() : config.getDefaultStyle();
                if (savedStyle != null && stylePresetsComboBox != null) {
                    // Find StylePresetItem by name, else keep "None"
                    stylePresetsComboBox.getItems().stream()
                            .filter(item -> savedStyle.equals(item.getName()))
                            .findFirst()
                            .ifPresent(stylePresetsComboBox::setValue);
                }

                // Load color style selection; a renamed default palette resolves to its new name
                if (colorStyleComboBox != null) {
                    art.moodframe.service.ColorStyleService colorService = art.moodframe.config.ServiceFactory.getInstance()
                            .getService(art.moodframe.service.ColorStyleService.class);
                    art.moodframe.model.ColorStyle savedPalette = colorService != null
                            ? colorService.getStyleByName(config.getDefaultColorStyle()) : null;
                    String savedColorStyle = savedPalette != null ? savedPalette.getName() : config.getDefaultColorStyle();
                    if (savedColorStyle != null) {
                        for (art.moodframe.model.ColorStyleItem item : colorStyleComboBox.getItems()) {
                            if (savedColorStyle.equals(item.getName())) {
                                colorStyleComboBox.setValue(item);
                                break;
                            }
                        }
                    }
                }

                // Load light selection
                if (lightComboBox != null) {
                    String savedLight = config.getDefaultLight();
                    lightComboBox.getItems().stream()
                            .filter((item) -> item.getName().equals(savedLight))
                            .findFirst()
                            .ifPresent(lightComboBox::setValue);
                }

                // Load camera selection
                if (cameraComboBox != null) {
                    String savedCamera = config.getDefaultCamera();
                    cameraComboBox.getItems().stream()
                            .filter((item) -> item.getName().equals(savedCamera))
                            .findFirst()
                            .ifPresent(cameraComboBox::setValue);
                }

                // Load atmosphere selection
                if (atmosphereComboBox != null) {
                    String savedAtmosphere = config.getDefaultAtmosphere();
                    atmosphereComboBox.getItems().stream()
                            .filter((item) -> item.getName().equals(savedAtmosphere))
                            .findFirst()
                            .ifPresent(atmosphereComboBox::setValue);
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
            if (blockChoices.containsKey(art.moodframe.model.PromptBlock.Type.STYLE))
                fillBlockChoice(art.moodframe.model.PromptBlock.Type.STYLE, null);

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

                        // Update prompt placeholder when model changes
                        updatePromptPlaceholder();

                        // Update UI state when model changes (API key of the new provider, character count with its limit)
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

                // Save target resolution changes
                resolutionChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        config.setDefaultResolution(newVal);
                    }
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

                // Save style selection changes; an action of the list is no selection
                stylePresetsComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal != null && blockChoices.get(art.moodframe.model.PromptBlock.Type.STYLE).isEntry(newVal)) {
                        config.setDefaultStyle(newVal.getName());
                    }
                });

                // Save color style selection changes
                if (colorStyleComboBox != null) {
                    colorStyleComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                        if (newVal != null && colorChoice.isEntry(newVal)) {
                            config.setDefaultColorStyle(newVal.getName());
                        }
                    });
                }

                // Save light selection changes
                if (lightComboBox != null)
                    lightComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                        if (newVal != null && blockChoices.get(art.moodframe.model.PromptBlock.Type.LIGHT).isEntry(newVal))
                            config.setDefaultLight(newVal.getName());
                    });

                // Save camera selection changes
                if (cameraComboBox != null)
                    cameraComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                        if (newVal != null && blockChoices.get(art.moodframe.model.PromptBlock.Type.CAMERA).isEntry(newVal))
                            config.setDefaultCamera(newVal.getName());
                    });

                // Save atmosphere selection changes
                if (atmosphereComboBox != null)
                    atmosphereComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                        if (newVal != null
                                && blockChoices.get(art.moodframe.model.PromptBlock.Type.ATMOSPHERE).isEntry(newVal))
                            config.setDefaultAtmosphere(newVal.getName());
                    });

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

        // Create filtered list to hide trashed chats
        filteredChatList = new FilteredList<>(chatList, this::shouldShowChat);

        // Remove borders from all main buttons
        if (trashButton != null) {
            trashButton.setStyle(
                    "-fx-border-color: transparent; -fx-border-width: 0; -fx-background-insets: 0; -fx-border-insets: 0;");
        }
        if (newChatButton != null) {
            newChatButton.setStyle(
                    "-fx-border-color: transparent; -fx-border-width: 0; -fx-background-insets: 0; -fx-border-insets: 0;");
        }
        if (settingsButton != null) {
            settingsButton.setStyle(
                    "-fx-border-color: transparent; -fx-border-width: 0; -fx-background-insets: 0; -fx-border-insets: 0;");
        }
        if (infoButton != null) {
            infoButton.setStyle(
                    "-fx-border-color: transparent; -fx-border-width: 0; -fx-background-insets: 0; -fx-border-insets: 0;");
        }

        // Setup chat list view with filtered list
        chatListView.setItems(filteredChatList);
        ListReorder<Chat> reorder = new ListReorder<>(chatListView, this::moveChat);
        chatListView.setCellFactory(listView -> {
            ChatListCell cell = new ChatListCell();

            // Set up chat action callbacks
            cell.setOnDeleteRequested(this::onChatDeleteRequested);
            cell.setOnChatRenamed(this::onChatRenamed);
            cell.setTrashColumn(trashButton);
            reorder.attach(cell, cell.getGrip());

            return cell;
        });

        // Setup chat selection handling
        chatListView.getSelectionModel().selectedItemProperty().addListener((obs, oldChat, newChat) -> {
            if (!reorderingChats)
                onChatSelected(newChat);
        });

        // Setup virtualized ListView for ChatEntries (replaces VBox for performance)
        setupChatEntriesListView();
        setupImagesView();

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

        /*  Scrolling away from the end, by wheel or scroll bar, lets the chat stay where the user left it; while the
            user scrolls, the chat does not follow its end  */
        chatEntriesListView.addEventFilter(ScrollEvent.SCROLL, (event) -> {
            wasScrolledToBottom = false;
            Platform.runLater(() -> wasScrolledToBottom = isChatAtEnd());
        });
        chatEntriesListView.addEventFilter(MouseEvent.MOUSE_PRESSED, (event) -> wasScrolledToBottom = false);
        chatEntriesListView.addEventFilter(MouseEvent.MOUSE_RELEASED,
                (event) -> Platform.runLater(() -> wasScrolledToBottom = isChatAtEnd()));

        /*  Shortcut+C over an entry reuses it ("Reuse"), unless a text field has a selection to copy  */
        KeyCombination reuseKey = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
        mainBorderPane.addEventFilter(KeyEvent.KEY_PRESSED, (event) -> {
            if (!reuseKey.match(event)
                    || event.getTarget() instanceof TextInputControl text && !text.getSelectedText().isEmpty())
                return;
            for (Node cell : chatEntriesListView.lookupAll(".list-cell"))
                if (cell.isHover() && cell instanceof ListCell<?> listCell
                        && listCell.getItem() instanceof ChatEntry entry) {
                    reuseEntry(entry, ChatEntryCard.Reuse.ALL);
                    event.consume();
                    return;
                }
        });

        // CellFactory creates ChatEntryCard for each visible cell
        chatEntriesListView.setCellFactory(listView -> new javafx.scene.control.ListCell<ChatEntry>() {
            private art.moodframe.ui.component.ChatEntryCard currentCard = null;

            {
                // CRITICAL: Cell must fill entire width to prevent horizontal scroll
                setMaxWidth(Double.MAX_VALUE);
                setPrefWidth(0); // Let it grow with ListView
                // The cards start on the inset of the boxes, behind the 1 px border of the list
                setPadding(new javafx.geometry.Insets(5.0, 10.0, 5.0, BoxInset.LEFT - 1.0));
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
                    boolean isChatDeleted = shownGroup == null && currentChat != null && currentChat.isDeleted();
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

                    currentCard.setOnReuse((reusedEntry, part) -> reuseEntry(reusedEntry, part));

                    /*  The images share the width of the cell, which a reused cell knows from the start, each at most
                        as large as the image size of the chat header  */
                    currentCard.bindAvailableWidth(this);
                    currentCard.bindTileSize(imageSizeSlider.valueProperty());

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

                    currentCard.setOnMoodTakeOver((takenEntry, save) -> takeOverEntryMood(takenEntry, save));
                    currentCard.setOnFrameSave((savedEntry) -> saveEntryFrame(savedEntry));
                    currentCard.setOnMoodFromImage((imageFile) -> openMoodFromImage(imageFile));

                    /*  The entries of a group name their chat and lead to it  */
                    Chat origin = shownGroup != null ? getCurrentChatFromEntry(entry) : null;
                    if (origin != null)
                        currentCard.showOrigin(origin.getName(), () -> openOriginChat(entry));

                    setGraphic(currentCard);
                    setText(null);
                }
            }
        });
    }

    /*  The image size reaches from 100 px to the size of the thumbnails in screen pixels, so the images stay sharp;
        Cmd/Ctrl + and - change it in steps  */
    private static final double MIN_IMAGE_SIZE = 100.0;
    private static final double IMAGE_SIZE_STEP = 20.0;

    /**
     * Sets up the images view of the chat area, the switch between it and the chat in the chat header and the size
     * of the images both views share; view and size are remembered for all chats and groups.
     */
    private void setupImagesView() {
        imageGrid = new ImageGrid(this::shownImagePath);
        imageGrid.setVisible(false);
        chatAreaStackPane.getChildren().add(chatAreaStackPane.getChildren().indexOf(chatEntriesListView) + 1,
                imageGrid);
        imageGrid.entriesProperty().bind(chatEntriesListView.itemsProperty());

        imageGrid.setOnOpen(this::showGallery);
        imageGrid.setOnAction((item, action) -> {
            String path = shownImagePath(item.image());
            switch (action) {
                case VIEW -> showGallery(item);
                case EDIT -> editImageFromPath(path);
                case DOWNLOAD -> downloadImageFromPath(path);
                case MOOD -> {
                    if (path != null && new File(path).exists())
                        openMoodFromImage(new File(path));
                }
                case DELETE -> handleDeleteImage(item.entry().getId(), item.image().getId());
            }
        });
        imageGrid.setOnReuse((entry) -> reuseEntry(entry, ChatEntryCard.Reuse.ALL));
        imageGrid.setOnSeedCopy((seed) -> {
            if (seedTextField != null)
                seedTextField.setText(String.valueOf(seed));
        }, () -> modelComboBox != null && modelComboBox.getValue() != null
                && !modelComboBox.getValue().toLowerCase().contains("gemini"));

        /*  The largest size follows the scale of the screen the window is on  */
        imageSizeSlider.setMin(MIN_IMAGE_SIZE);
        imageSizeSlider.setMax(ThumbnailManager.MAX_THUMBNAIL_SIZE / Screen.getPrimary().getOutputScaleX());
        imageSizeSlider.setValue(config != null ? config.getImageTileSize() : 180.0);
        imageGrid.tileSizeProperty().bind(imageSizeSlider.valueProperty());
        imageGrid.maxTileSizeProperty().bind(imageSizeSlider.maxProperty());
        imageSizeSlider.valueProperty().addListener((obs, oldSize, size) -> {
            if (!imageSizeSlider.isValueChanging() && config != null)
                config.setImageTileSize(size.doubleValue());
        });
        imageSizeSlider.valueChangingProperty().addListener((obs, wasChanging, changing) -> {
            if (!changing && config != null)
                config.setImageTileSize(imageSizeSlider.getValue());
        });
        chatAreaStackPane.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene == null)
                return;
            scene.addEventFilter(KeyEvent.KEY_PRESSED, this::changeImageSize);
            scene.windowProperty().addListener((windowObs, oldWindow, window) -> {
                if (window != null)
                    imageSizeSlider.maxProperty().bind(Bindings.createDoubleBinding(
                            () -> ThumbnailManager.MAX_THUMBNAIL_SIZE / window.getOutputScaleX(),
                            window.outputScaleXProperty()));
            });
        });

        /*  One of the views stays chosen  */
        ToggleGroup views = new ToggleGroup();
        chatViewButton.setToggleGroup(views);
        imagesViewButton.setToggleGroup(views);
        imagesViewButton.setSelected(config != null && "images".equals(config.getChatView()));
        views.selectedToggleProperty().addListener((obs, oldView, view) -> {
            if (view == null) {
                oldView.setSelected(true);
                return;
            }
            if (config != null)
                config.setChatView(view == imagesViewButton ? "images" : "chat");
            showEntries(chatEntriesListView.isVisible() || imageGrid.isVisible());
        });
    }

    /**
     * Shows the entries of the chat or of the open group as chat or as images, as chosen in the chat header, or hides
     * both views.
     *
     * @param shown whether entries are shown
     */
    private void showEntries(boolean shown) {
        boolean images = imagesViewButton.isSelected();
        chatEntriesListView.setVisible(shown && !images);
        imageGrid.setVisible(shown && images);
    }

    /** Cmd/Ctrl + and - change the image size in steps, but not in the full view, where + and - zoom. */
    private void changeImageSize(KeyEvent event) {
        if (!event.isShortcutDown() || imagePreviewOverlay != null && imagePreviewOverlay.isVisible())
            return;
        switch (event.getCode()) {
            case PLUS, ADD, EQUALS -> imageSizeSlider.setValue(imageSizeSlider.getValue() + IMAGE_SIZE_STEP);
            case MINUS, SUBTRACT -> imageSizeSlider.setValue(imageSizeSlider.getValue() - IMAGE_SIZE_STEP);
            default -> {
                return;
            }
        }
        event.consume();
    }

    /** The file of an image of the shown entries, in the trash for a chat there. */
    private String shownImagePath(GeneratedImage image) {
        return getActualImagePath(image, shownGroup == null && currentChat != null && currentChat.isDeleted());
    }

    /**
     * Opens the full view on an image of the images view, paging through all images of the grid whose file is there,
     * each with its mood, frame and model.
     *
     * @param item the image to start with
     */
    private void showGallery(ImageGrid.Item item) {
        List<String> paths = new java.util.ArrayList<>();
        List<String> captions = new java.util.ArrayList<>();
        int start = -1;
        for (ImageGrid.Item image : imageGrid.getImages()) {
            String path = image.image().getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED
                    ? shownImagePath(image.image()) : null;
            if (path == null || !new File(path).exists())
                continue;
            if (image.equals(item))
                start = paths.size();
            paths.add(path);
            captions.add(ImageGrid.caption(image.entry()));
        }
        if (imagePreviewOverlay != null && start >= 0)
            imagePreviewOverlay.showGallery(paths, captions, start);
    }

    /**
     * Determines if a chat should be shown: trashed chats are only listed in the central trash.
     * 
     * @param chat the chat to check
     * @return true if the chat should be visible
     */
    private boolean shouldShowChat(Chat chat) {
        return !chat.isDeleted();
    }

    /**
     * Handles chat selection changes.
     * 
     * @param chat the newly selected chat, or null if none selected
     */
    private void onChatSelected(Chat chat) {
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
        /*  An open group stays in front, the chat only takes the new prompts  */
        if (shownGroup != null) {
            showGroup();
            return;
        }

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
            showEntries(false);
            chatEntriesListView.setItems(null);
            lastChatEntriesCount = 0;
        } else {
            // Hide welcome, show ListView with chat entries
            welcomeContainer.setVisible(false);
            showEntries(true);

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
        if (shownGroup != null)
            updateGroupHeader();
        else if (currentChat != null) {
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
        /*  An open group names the chat of the new prompts there instead  */
        if (shownGroup != null)
            updateGroupHeader();
        else if (chatTotalCostLabel != null && chat != null) {
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
                    // Refresh filter to hide the trashed chat
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
     * Moves a chat to a gap between the shown chats (0 above the first one) and stores the order of all chats. The
     * list sorts itself anew, keeping the selected chat without opening it again.
     *
     * @param chat the chat to move
     * @param gap  the gap it goes to
     */
    private void moveChat(Chat chat, int gap) {
        if (chatHistory == null)
            return;
        Chat before = gap < filteredChatList.size() ? filteredChatList.get(gap) : null;
        try {
            chatHistory.moveChat(chatList, chat, before);
        } catch (Exception e) {
            logger.error("Failed to store the order of the chats", e);
        }
        reorderingChats = true;
        FXCollections.sort(chatList, java.util.Comparator.comparingInt(Chat::getSortOrder));
        reorderingChats = false;
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
     * Sets up the menu of the left sidebar that switches between the chats and the moods or frames images were
     * generated with; the choice is remembered. Opening a mood or frame shows its entries from all chats on the right.
     */
    private void setupSidebarMode() {
        ToggleGroup modes = new ToggleGroup();
        byChatMenuItem.setToggleGroup(modes);
        byMoodMenuItem.setToggleGroup(modes);
        byFrameMenuItem.setToggleGroup(modes);
        byChatMenuItem.setUserData("chat");
        byMoodMenuItem.setUserData("mood");
        byFrameMenuItem.setUserData("frame");

        /*  One of the entries stays selected  */
        modes.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null)
                oldToggle.setSelected(true);
            else
                showSidebarMode(newToggle);
        });

        moodGroupListView.setCellFactory((listView) -> new MoodGroupListCell());
        moodGroupListView.getSelectionModel().selectedItemProperty().addListener((obs, oldGroup, newGroup) -> {
            if (!updatingGroups && newGroup != null)
                openMoodGroup(newGroup);
        });
        frameGroupListView.setCellFactory((listView) -> new FrameGroupListCell());
        frameGroupListView.getSelectionModel().selectedItemProperty().addListener((obs, oldGroup, newGroup) -> {
            if (!updatingGroups && newGroup != null)
                openFrameGroup(newGroup);
        });

        String mode = config != null ? config.getSidebarMode() : "chat";
        modes.getToggles().stream().filter((toggle) -> mode.equals(toggle.getUserData())).findFirst()
                .ifPresent((toggle) -> toggle.setSelected(true));
    }

    /** Lists the chats, the moods or the frames in the left sidebar and remembers the choice; an open group closes. */
    private void showSidebarMode(Toggle mode) {
        ListView<?> shownList = mode == byMoodMenuItem ? moodGroupListView
                : mode == byFrameMenuItem ? frameGroupListView : chatListView;
        if (shownList.isVisible())
            return;
        for (ListView<?> list : List.of(chatListView, moodGroupListView, frameGroupListView)) {
            list.setVisible(list == shownList);
            list.setManaged(list == shownList);
        }
        updateSidebarTitle();
        if (config != null)
            config.setSidebarMode((String) mode.getUserData());

        /*  The chat shows again, the other list holds the open group no more  */
        if (shownGroup != null) {
            shownGroup = null;
            moodGroupListView.getSelectionModel().clearSelection();
            frameGroupListView.getSelectionModel().clearSelection();
            updateChatView(currentChat);
            updateUIState();
        }
        refreshGroups();
    }

    /** Names the current view of the left sidebar in its header. */
    private void updateSidebarTitle() {
        sidebarTitleLabel.setText(I18nManager.getText(moodGroupListView.isVisible() ? "sidebar.moods"
                : frameGroupListView.isVisible() ? "sidebar.frames" : "sidebar.chats"));
    }

    /**
     * Opens a group of the list by mood: a mood in use goes into the fields that are not locked, and the entries of the
     * group from all chats are shown. The last active chat keeps taking the new prompts.
     */
    private void openMoodGroup(MoodGroup group) {
        Mood mood = group.kind() == MoodGroup.Kind.MOOD && moodService != null ? moodService.find(group.moodId()) : null;
        if (mood != null)
            chooseMood(mood);
        shownGroup = group;
        updateChatView(currentChat);
        updateUIState();
    }

    /**
     * Opens a group of the list by frame: a frame in use is chosen, and the entries of the group from all chats are
     * shown. The last active chat keeps taking the new prompts.
     */
    private void openFrameGroup(FrameGroup group) {
        Frame frame = group.kind() == FrameGroup.Kind.FRAME && frameService != null
                ? frameService.find(group.frameId()) : null;
        if (frame != null)
            chooseFrame(frame);
        shownGroup = group;
        updateChatView(currentChat);
        updateUIState();
    }

    /**
     * Shows the entries of the open group, the oldest first as in a chat. They are the entries of the loaded chats, so
     * running requests keep updating them.
     */
    private void showGroup() {
        java.util.Map<String, ChatEntry> loaded = new java.util.HashMap<>();
        chatList.stream().filter(this::shouldShowChat)
                .forEach((chat) -> chat.getEntries().forEach((entry) -> loaded.put(entry.getId(), entry)));
        ObservableList<ChatEntry> entries = FXCollections.observableArrayList();
        try {
            if (chatHistory != null)
                chatHistory.findChatEntryIds(shownGroup).stream().map(loaded::get)
                        .filter(java.util.Objects::nonNull).forEach(entries::add);
        } catch (Exception e) {
            logger.error("Failed to load the entries of the group {}", shownGroup, e);
        }

        /*  Back in a chat, the view scrolls to its latest entry  */
        lastChatEntriesCount = 0;
        updateGroupHeader();
        welcomeContainer.setVisible(false);
        showEntries(true);
        chatEntriesListView.setItems(entries);
        if (!entries.isEmpty())
            Platform.runLater(() -> chatEntriesListView.scrollTo(entries.size() - 1));
    }

    /** Shows the name of the open group above its entries and the chat that takes the new prompts. */
    private void updateGroupHeader() {
        chatTitleLabel.setText(switch (shownGroup) {
            case MoodGroup group -> MoodGroupListCell.nameOf(group);
            case FrameGroup group -> FrameGroupListCell.nameOf(group);
        });
        chatTotalCostLabel.setText(currentChat != null && !currentChat.isDeleted()
                ? I18nManager.getText("sidebar.group.target", currentChat.getName())
                : I18nManager.getText("sidebar.group.target.none"));
    }

    /**
     * Lists the moods or the frames again while the sidebar shows them, keeping the open group selected without opening
     * it again; the open group takes over its new name, for example once it was renamed or deleted.
     */
    private void refreshGroups() {
        if (chatHistory == null)
            return;
        try {
            if (moodGroupListView.isVisible())
                refreshGroups(moodGroupListView, chatHistory.findMoodGroups());
            else if (frameGroupListView.isVisible())
                refreshGroups(frameGroupListView, chatHistory.findFrameGroups());
        } catch (Exception e) {
            logger.error("Failed to load the groups of the entries", e);
            return;
        }
        if (shownGroup != null)
            updateGroupHeader();
    }

    /** Fills a list of groups, keeping the open one selected without opening it again. */
    private <T extends EntryGroup> void refreshGroups(ListView<T> listView, List<T> groups) {
        updatingGroups = true;
        listView.getItems().setAll(groups);
        groups.stream().filter((group) -> group.sameAs(shownGroup)).findFirst().ifPresent((group) -> {
            shownGroup = group;
            listView.getSelectionModel().select(group);
        });
        updatingGroups = false;
    }

    /** Opens the chat an entry of the open group was generated in, with the entry in view. */
    private void openOriginChat(ChatEntry entry) {
        Chat chat = getCurrentChatFromEntry(entry);
        if (chat == null)
            return;
        byChatMenuItem.setSelected(true);
        chatListView.getSelectionModel().select(chat);
        chatListView.scrollTo(chat);
        Platform.runLater(() -> chatEntriesListView.scrollTo(entry));
    }

    /**
     * Sets up event handlers for interactive components.
     */
    private void setupEventHandlers() {
        /*  Update UI state (character count of the final prompt) when the prompt text, a building block or
            the "Apply mood" switch changes  */
        promptTextArea.textProperty().addListener((obs, oldText, newText) -> updateUIState());
        for (javafx.beans.value.ObservableValue<?> value : List.<javafx.beans.value.ObservableValue<?>>of(
                atmosphereComboBox.valueProperty(), stylePresetsComboBox.valueProperty(),
                colorStyleComboBox.valueProperty(), lightComboBox.valueProperty(), cameraComboBox.valueProperty(),
                moodApplyCheckBox.selectedProperty()))
            value.addListener((obs, oldVal, newVal) -> updateUIState());

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
     * @param text the final prompt (the text of the user with the building blocks)
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

        /*  the limit of the model applies to the final prompt, which the counter counts  */
        String finalPrompt = buildFinalPrompt();
        updateCharacterCount(finalPrompt);
        boolean hasValidText = hasText && promptComposer().fits(finalPrompt);

        // Check if the currently selected model's provider has a valid API key
        boolean hasApiKeyForSelectedModel = hasApiKeyForCurrentProvider();
        boolean hasChatSelected = currentChat != null && !currentChat.isDeleted();

        boolean hasModelSelected = modelComboBox != null && modelComboBox.getValue() != null;

        // Send button requires valid text, API key for selected provider, active chat selection and a model
        sendButton.setDisable(!hasValidText || !hasApiKeyForSelectedModel || !hasChatSelected || !hasModelSelected);

        // UI elements that require API key and chat selection to function
        updateChatDependentElements(hasApiKeyForSelectedModel, hasChatSelected);

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
        frameComboBox.setDisable(!enableInput);
        saveFrameAsButton.setDisable(!enableInput);
        manageFramesButton.setDisable(!enableInput);
        saveFrameButton.setDisable(frameComboBox.isDisable() || isFrameSaveLocked());

        /*  The mood fields are greyed while a reference image is attached without "Apply mood"; the light also for
            a layout style, keeping its choice (the composer leaves it out)  */
        boolean moodApplies = enableInput && (!hasAttachment || moodApplyCheckBox.isSelected());
        art.moodframe.model.StylePresetItem styleItem = stylePresetsComboBox.getValue();
        boolean layoutStyle = styleItem != null && styleItem.getBlock() != null && styleItem.getBlock().isLayout();
        moodApplyCheckBox.setVisible(hasAttachment);
        moodApplyCheckBox.setManaged(hasAttachment);
        moodApplyCheckBox.setDisable(!enableInput);
        for (Node field : List.of(moodComboBox, moodFromImageButton, saveMoodAsButton, manageMoodsButton,
                atmosphereLabel, atmosphereComboBox, styleLabel, stylePresetsComboBox, colorStyleLabel,
                colorStyleComboBox))
            field.setDisable(!moodApplies);
        saveMoodButton.setDisable(moodComboBox.isDisable() || isMoodSaveLocked());
        lightLabel.setDisable(!moodApplies || layoutStyle);
        lightComboBox.setDisable(!moodApplies || layoutStyle);
        lightLayoutHint.setVisible(layoutStyle);
        lightLayoutHint.setManaged(layoutStyle);
        cameraLabel.setDisable(!moodApplies);
        cameraComboBox.setDisable(!moodApplies);
        updateCameraHint();

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
        updateBrainstormButton();
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

    /*  the colored cloud in the lower right corner, behind all panels and controls  */
    private javafx.scene.image.ImageView cornerCloud;
    private static final double CLOUD_WIDTH = 1000.0;
    private static final double CLOUD_HEIGHT = 747.0;

    /**
     * Puts the colored cloud that rises from the lower right corner (as on the splash screen) behind the whole
     * window content. The image is transparent around the cloud; the panels above it are transparent too
     * ({@code main-window.css}), while controls, the chat list and images keep their own background and cover it.
     */
    private void setupCornerCloud() {
        if (!(mainBorderPane.getParent() instanceof StackPane stack))
            return;
        cornerCloud = new javafx.scene.image.ImageView(cloudImage(true));
        cornerCloud.setMouseTransparent(true);
        cornerCloud.setFocusTraversable(false);
        cornerCloud.setManaged(false);
        stack.getChildren().add(0, cornerCloud);
        stack.layoutBoundsProperty().addListener((obs, old, bounds) -> placeCornerCloud(bounds));
        placeCornerCloud(stack.getLayoutBounds());

        /*  follow a theme change at once, not only when the settings close  */
        art.moodframe.util.ThemeManager.addThemeListener(this::updateCornerCloudTheme);

        /*  the theme colors resolve only once the window has its scene  */
        if (stack.getScene() != null)
            updateCornerCloudTheme();
        else
            stack.sceneProperty().addListener((obs, old, scene) -> {
                if (scene != null)
                    javafx.application.Platform.runLater(this::updateCornerCloudTheme);
            });
    }

    /*  the cloud for dark themes, and a deeper, more saturated one that stays visible on light themes  */
    private javafx.scene.image.Image cloudDark;
    private javafx.scene.image.Image cloudLight;

    private javafx.scene.image.Image cloudImage(boolean dark) {
        if (dark && cloudDark == null)
            cloudDark = new javafx.scene.image.Image(getClass().getResourceAsStream("/images/corner-cloud.png"),
                    CLOUD_WIDTH, CLOUD_HEIGHT, true, true);
        if (!dark && cloudLight == null)
            cloudLight = new javafx.scene.image.Image(getClass().getResourceAsStream("/images/corner-cloud-light.png"),
                    CLOUD_WIDTH, CLOUD_HEIGHT, true, true);
        return dark ? cloudDark : cloudLight;
    }

    private void placeCornerCloud(javafx.geometry.Bounds bounds) {
        cornerCloud.relocate(bounds.getWidth() - CLOUD_WIDTH, bounds.getHeight() - CLOUD_HEIGHT);
    }

    /**
     * Matches the strength of the corner cloud to the current theme.
     */
    private void updateCornerCloudTheme() {
        updateCornerCloudTheme(art.moodframe.util.ThemeManager.getCurrentTheme());
    }

    private void updateCornerCloudTheme(String themeName) {
        if (cornerCloud == null)
            return;
        boolean dark = art.moodframe.util.ThemeManager.isDarkTheme(themeName);
        List<Node> panels = new java.util.ArrayList<>(mainBorderPane.lookupAll(".see-through"));
        panels.addAll(mainBorderPane.lookupAll(".glass"));

        /*  switched off: no cloud, and fields with their normal, opaque theme colors  */
        boolean enabled = config == null || config.isBackgroundImageEnabled();
        cornerCloud.setVisible(enabled);
        if (!enabled) {
            for (Node panel : panels)
                panel.setStyle(panel.getStyle().replaceAll("-color-bg-(default|subtle): [^;]*;\\s*", "").trim());
            return;
        }
        cornerCloud.setImage(cloudImage(dark));
        cornerCloud.setOpacity(dark ? 0.55 : 0.75);

        /*  fields and buttons over the cloud: the theme colors of their background at 55 %  */
        String fields = "-color-bg-default: " + themeColor("-color-bg-default", CONTROL_ALPHA) + "; "
                + "-color-bg-subtle: " + themeColor("-color-bg-subtle", CONTROL_ALPHA) + ";";
        /*  ".glass": the chat area, whose entries draw their own (now translucent) background  */
        for (Node panel : panels)
            panel.setStyle(panel.getStyle().replaceAll("-color-bg-(default|subtle): [^;]*;\\s*", "") + " " + fields);
    }

    private static final double CONTROL_ALPHA = 0.55;

    /*  the current value of a looked-up theme color, as rgba with the given opacity  */
    private String themeColor(String token, double alpha) {
        var probe = new javafx.scene.layout.Region();
        probe.setStyle("-fx-background-color: " + token + ";");
        var stack = (StackPane) mainBorderPane.getParent();
        stack.getChildren().add(probe);
        probe.applyCss();
        var fills = probe.getBackground() != null ? probe.getBackground().getFills() : List.<javafx.scene.layout.BackgroundFill>of();
        stack.getChildren().remove(probe);
        var color = !fills.isEmpty() && fills.get(0).getFill() instanceof javafx.scene.paint.Color c ? c : javafx.scene.paint.Color.BLACK;
        return String.format(java.util.Locale.ROOT, "rgba(%d, %d, %d, %.2f)", Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255), Math.round(color.getBlue() * 255), alpha);
    }

    /**
     * Shows the splash screen again as an info window; it closes with Esc or a click.
     */
    @FXML
    private void onInfoClicked() {
        art.moodframe.ui.SplashScreen.showInfo(infoButton.getScene().getWindow());
    }

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
            updateCornerCloudTheme();

            // Auto-save settings when dialog is closed (regardless of how it was closed)
            // Always apply settings and refresh UI after dialog closes
            settingsController.applySettings();

            // Refresh API key status in case it changed
            refreshApiKeyStatus();

            // Refresh the building block lists in case a configuration import added blocks or palettes
            refreshBlockChoices();

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

        /*  The new chat shows in the list of chats  */
        byChatMenuItem.setSelected(true);

        // Create new chat
        Chat newChat = new Chat.Builder()
                .name("New Chat " + (chatList.size() + 1))
                .sortOrder(ChatHistoryService.sortOrderOnTop(chatList))
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

        // Build final prompt including the building blocks
        art.moodframe.service.PromptComposer composer = promptComposer();
        art.moodframe.service.PromptComposer.Selection blocks = selectedBlocks();
        String prompt = composer.compose(userPrompt, blocks);
        if (prompt.isEmpty()) {
            return;
        }
        if (!composer.fits(prompt)) {
            setStatus(I18nManager.getText("status.error.prompt.too.long", prompt.length(), getMaxPromptLength()));
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
        String aspectRatio = java.util.Objects.requireNonNullElse(modelAspectRatio(), "16:9");
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
        // The full prompt with the building blocks is used for API calls
        ChatEntry newEntry = new ChatEntry.Builder()
                .chatId(currentChat.getId())
                .userPrompt(userPrompt)
                .enhancedPrompt(shouldEnhancePrompt
                        ? "Enhanced: " + prompt + ", professional quality, highly detailed"
                        : prompt)
                .status(ChatEntry.Status.PENDING)
                .model(modelDisplay)
                .size(aspectRatio)
                .style(blocks.style() != null ? blocks.style().getName() : "None")
                .mood(moodService != null ? moodService.snapshot(chosenMood(), isMoodChanged(), blocks) : null)
                .frame(EntryFrame.of(chosenFrame(), frameChanged, aspectRatioComboBox.getValue(),
                        resolutionChoiceBox.getValue()))
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

        art.moodframe.model.ImageGenerationRequest request = new art.moodframe.model.ImageGenerationRequest(prompt,
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

        // Set style preset if selected (and applied: not with a reference image without "Apply mood")
        art.moodframe.model.PromptBlock style = selectedBlocks().style();
        if (style != null)
            request.setStylePreset(style.getName());

        // Convert aspect ratio to width/height based on model capabilities
        String aspectRatio = modelAspectRatio();
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

        // A model of an OpenAI-compatible gateway takes the free size closest to the target resolution
        java.util.List<String> freeResolutions =
                art.moodframe.model.ModelFamilies.freeResolutions(chatEntry.getModel());
        if (generationModel == art.moodframe.model.GenerationModel.OPENAI_IMAGE && freeResolutions != null) {
            request.setImageSize(art.moodframe.model.ModelFamilies.nearest(resolutionChoiceBox.getValue(),
                    freeResolutions));
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
            // Auto-detect aspect ratio ONLY from the first attached image
            // When adding additional images, keep the aspect ratio from the first one
            int attachmentCount = multiImagePanel != null ? multiImagePanel.getAttachmentCount() : 1;
            if (attachmentCount == 1) {
                updateAspectRatioFromImage(attachedImageFile);
            }
        }

        // Update prompt placeholder and all UI elements to reflect attachment state
        // (includes disabling thinking level and, without "Apply mood", the mood fields when image attached)
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

            // Use the aspect ratios of the combo box, the targets of all models
            // (the chosen model takes the closest one it can do)
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
     * Handles the trash button click event: opens the central trash.
     */
    @FXML
    private void onTrashClicked() {
        try {
            art.moodframe.ui.dialog.TrashWindow.open(mainBorderPane.getScene().getWindow(),
                    art.moodframe.service.TrashGroup.Kind.CHATS, null);
        } catch (Exception e) {
            logger.error("Failed to open trash window", e);
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
        if (currentChat == null && shownGroup == null) {
            return;
        }

        // Find the currently displayed chat entry
        ChatEntry currentEntry = findCurrentlyDisplayedChatEntry();
        if (currentEntry == null) {
            return;
        }

        // Work directly with chat entries from the current chat, or with those of an open group
        // The issue is not with deleted chat entries, but with the index calculation
        java.util.List<ChatEntry> chatEntries = shownGroup != null ? chatEntriesListView.getItems()
                : currentChat.getEntries();

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
        if (currentChat == null && shownGroup == null) {
            return;
        }

        // Find the currently displayed chat entry
        ChatEntry currentEntry = findCurrentlyDisplayedChatEntry();
        if (currentEntry == null) {
            return;
        }

        // Work directly with chat entries from the current chat, or with those of an open group
        java.util.List<ChatEntry> chatEntries = shownGroup != null ? chatEntriesListView.getItems()
                : currentChat.getEntries();
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
        if ((currentChat == null && shownGroup == null) || chatEntriesListView == null
                || chatEntriesListView.getItems() == null || currentlyDisplayedChatEntry == null) {
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
        if ((currentChat == null && shownGroup == null) || chatEntriesListView == null
                || chatEntriesListView.getItems() == null || currentlyDisplayedChatEntry == null) {
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

            /*  An open group keeps its name in the header  */
            if (shownGroup != null)
                updateGroupHeader();

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
            // Update character count with the length of the final prompt and dynamic limit
            updateCharacterCount(buildFinalPrompt());
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
        // Update the titles of the mood, frame and generation groups and the "Apply mood" switch
        if (moodTitleLabel != null)
            moodTitleLabel.setText(I18nManager.getText("params.mood.title"));
        if (frameTitleLabel != null)
            frameTitleLabel.setText(I18nManager.getText("params.frame.title"));
        if (generationTitleLabel != null)
            generationTitleLabel.setText(I18nManager.getText("params.generation.title"));
        if (moodApplyCheckBox != null) {
            moodApplyCheckBox.setText(I18nManager.getText("params.mood.apply"));
            moodApplyCheckBox.setTooltip(new Tooltip(I18nManager.getText("params.mood.apply.tooltip")));
        }
        if (moodFromImageButton != null)
            moodFromImageButton.setTooltip(new Tooltip(I18nManager.getText("params.mood.from.image.tooltip")));
        if (saveMoodButton != null)
            saveMoodButton.setTooltip(new Tooltip(I18nManager.getText("params.mood.save.tooltip")));
        if (saveMoodAsButton != null)
            saveMoodAsButton.setTooltip(new Tooltip(I18nManager.getText("params.mood.save.as.tooltip")));
        if (manageMoodsButton != null)
            manageMoodsButton.setTooltip(new Tooltip(I18nManager.getText("params.mood.manage.tooltip")));
        refreshMoods();
        if (saveFrameButton != null)
            saveFrameButton.setTooltip(new Tooltip(I18nManager.getText("params.frame.save.tooltip")));
        if (saveFrameAsButton != null)
            saveFrameAsButton.setTooltip(new Tooltip(I18nManager.getText("params.frame.save.as.tooltip")));
        if (manageFramesButton != null)
            manageFramesButton.setTooltip(new Tooltip(I18nManager.getText("params.frame.manage.tooltip")));
        refreshFrames();

        // Update parameter labels that are hardcoded in FXML
        updateParameterLabels();
    }

    /**
     * Shows the hint below the camera for a style that is not photographic, layout included: only perspective and shot
     * size apply (see {@link art.moodframe.service.PromptComposer}).
     */
    private void updateCameraHint() {
        art.moodframe.model.StylePresetItem styleItem = stylePresetsComboBox.getValue();
        boolean framingOnly = styleItem != null && styleItem.getBlock() != null
                && !styleItem.getBlock().isPhotographic();
        cameraHint.setText(I18nManager.getText("params.camera.framing.hint"));
        cameraHint.setVisible(framingOnly);
        cameraHint.setManaged(framingOnly);
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

        if (lightLabel != null)
            lightLabel.setText(I18nManager.getText("params.light"));
        if (lightLayoutHint != null)
            lightLayoutHint.setText(I18nManager.getText("params.light.layout.hint"));
        if (cameraLabel != null)
            cameraLabel.setText(I18nManager.getText("params.camera"));
        if (cameraHint != null)
            updateCameraHint();
        if (atmosphereLabel != null)
            atmosphereLabel.setText(I18nManager.getText("params.atmosphere"));
        if (colorStyleLabel != null)
            colorStyleLabel.setText(I18nManager.getText("params.colorstyle"));
        for (ToggleButton lock : java.util.Arrays.asList(atmosphereLockButton, styleLockButton, colorLockButton,
                lightLockButton, cameraLockButton))
            if (lock != null)
                lock.setTooltip(new Tooltip(I18nManager.getText("params.block.lock.tooltip")));

        if (aspectRatioLabel != null) {
            aspectRatioLabel.setText(I18nManager.getText("params.aspect.ratio"));
        }

        if (resolutionLabel != null) {
            resolutionLabel.setText(I18nManager.getText("params.resolution"));
        }
        updateResolutionHint();

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
        if (infoTooltip != null) {
            infoTooltip.setText(I18nManager.getText("main.info.tooltip"));
        }
        if (settingsTooltip != null) {
            settingsTooltip.setText(I18nManager.getText("main.settings.tooltip"));
        }

        if (newChatTooltip != null) {
            newChatTooltip.setText(I18nManager.getText("chat.new.tooltip"));
        }

        if (trashTooltip != null) {
            trashTooltip.setText(I18nManager.getText("chat.trash.tooltip"));
        }

        if (sidebarModeTooltip != null) {
            sidebarModeTooltip.setText(I18nManager.getText("sidebar.mode.tooltip"));
            byChatMenuItem.setText(I18nManager.getText("sidebar.by.chat"));
            byMoodMenuItem.setText(I18nManager.getText("sidebar.by.mood"));
            byFrameMenuItem.setText(I18nManager.getText("sidebar.by.frame"));
            updateSidebarTitle();
        }

        if (sendTooltip != null) {
            sendTooltip.setText(I18nManager.getText("input.send.tooltip"));
        }

        if (chatViewTooltip != null) {
            chatViewTooltip.setText(I18nManager.getText("chat.view.chat"));
            imagesViewTooltip.setText(I18nManager.getText("chat.view.images"));
            imageSizeTooltip.setText(I18nManager.getText("chat.image.size.tooltip"));
        }

        /*  The tiles of the images view carry texts of their own  */
        if (imageGrid != null)
            imageGrid.refresh();

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
            /*  The cost goes to the chat of the entry, even if another chat or a mood is shown meanwhile  */
            Chat owner = getCurrentChatFromEntry(entry);
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

                        refreshGroups();

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

                            // Remove the entry from the in-memory list of its chat immediately
                            // This prevents it from reappearing when the chat is reloaded
                            Chat owner = getCurrentChatFromEntry(targetEntry);
                            if (owner != null) {
                                owner.getEntries().removeIf(e -> e.getId().equals(entryId));
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

                        refreshGroups();

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
        // - aspectRatioComboBox (always visible - the target, of which each model takes its closest ratio)
        // - stylePresetsComboBox (always visible - style appended to prompt for both providers)

        // Show what the model makes of the target aspect ratio and resolution
        updateAspectRatioHint();
        updateResolutionHint();

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

    /*  the hint below aspect ratio or resolution while the chosen model cannot do the target  */
    private static final javafx.css.PseudoClass TARGET_CHANGED = javafx.css.PseudoClass.getPseudoClass("changed");

    /**
     * Shows below the aspect ratio what the chosen model makes of the target, for example "FLUX.2 [pro] → 5:4",
     * highlighted when it differs from the target.
     */
    private void updateAspectRatioHint() {
        String item = modelComboBox.getValue();
        String ratio = modelAspectRatio();
        if (aspectRatioHintLabel == null || item == null || ratio == null)
            return;
        aspectRatioHintLabel.setText(art.moodframe.model.ModelFamilies.familyOf(item) + " → " + ratio);
        aspectRatioHintLabel.pseudoClassStateChanged(TARGET_CHANGED, !ratio.equals(aspectRatioComboBox.getValue()));
    }

    /**
     * The aspect ratio the chosen model makes of the target: the target itself if the model can do it, else its
     * closest one.
     *
     * @return the aspect ratio, null without target
     */
    private String modelAspectRatio() {
        String target = aspectRatioComboBox.getValue();
        return target != null ? art.moodframe.model.AspectRatios.nearestForItem(target, modelComboBox.getValue()) : null;
    }

    /**
     * Shows below the resolution what the chosen model makes of the target, for example "Nano Banana 2 Lite → 1K",
     * highlighted when it differs from the target; a model without resolution choice keeps its fixed size.
     */
    private void updateResolutionHint() {
        String item = modelComboBox != null ? modelComboBox.getValue() : null;
        if (resolutionHintLabel == null || item == null)
            return;
        String resolution = modelResolution();
        resolutionHintLabel.setText(art.moodframe.model.ModelFamilies.familyOf(item) + " → "
                + (resolution != null ? resolution : I18nManager.getText("params.resolution.fixed")));
        resolutionHintLabel.pseudoClassStateChanged(TARGET_CHANGED, resolution != null
                && !resolution.equals(resolutionChoiceBox.getValue()));
    }

    /**
     * The resolution the chosen model generates: of free sizes the one closest to the target, else the one of its
     * variant, which the family took closest to the target unless it was set elsewhere (for example by brainstorm).
     *
     * @return the resolution, null for a model with a fixed size
     */
    private String modelResolution() {
        String item = modelComboBox.getValue();
        java.util.List<String> free = art.moodframe.model.ModelFamilies.freeResolutions(item);
        return free != null ? art.moodframe.model.ModelFamilies.nearest(resolutionChoiceBox.getValue(), free)
                : item != null ? art.moodframe.model.ModelFamilies.resolutionOf(item) : null;
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
